package com.lost_realm.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import com.lost_realm.skill.MedalType;
import com.lost_realm.util.ExpTable;

/**
 * 勋章熟练度 NBT 读写器。
 *
 * <h2>为什么存在</h2>
 * 熟练度(经验 + 等级)不再存储在玩家身上,而是**存储在勋章物品自身**。
 * 因此玩家把勋章取下来放进箱子、丢给别人、放进背包,
 * 熟练度都会跟着勋章走 —— 这才是「勋章」应有的语义。
 *
 * <h2>与 {@link PlayerMedals} 的关系</h2>
 * 本类刻意提供与 {@link PlayerMedals} **完全同形的 API**
 * ({@code getExp}/{@code getLevel}/{@code addExp}/{@code setLevel}/{@code getProgress}),
 * 从而把 ~22 个消费方的改造从「逻辑重写」降级为「取数点替换」:
 * <pre>
 *   旧:PlayerMedals.of(player).getLevel(type)
 *   新:MedalNbt.of(stack).getLevel(type)
 * </pre>
 *
 * <h2>哪些数据留在玩家身上</h2>
 * <ul>
 *   <li>✅ 进本类(物品 NBT):累计经验、等级缓存、遗落之主通关标记</li>
 *   <li>❌ 留在 {@link PlayerMedals}(玩家 capability):主动技能冷却、节点技能运行时状态</li>
 * </ul>
 * 冷却不搬进物品的原因:战斗热路径每 tick 都要查询冷却,
 * 若每次都走 {@code CuriosApi.getCuriosInventory(...).findFirstCurio(...)} 会有可观开销。
 * 冷却是「玩家此刻的状态」而非「勋章的属性」,语义上也更应留在玩家侧。
 *
 * <h2>NBT 结构</h2>
 * <pre>
 * { MedalOwner: "archer",       // 绑定勋章类型(防串号)
 *   MedalExp: 1234L,            // 该勋章的累计经验
 *   LordIntro: 1b,              // 遗落之主开场白已看过
 *   LordDuel: 1b }              // 遗落之主决斗已进行
 * </pre>
 *
 * <p>等级不落盘 —— 它完全由经验推导({@link ExpTable#levelFromExp(long)}),
 * 存了反而有一致性风险。
 */
public final class MedalNbt {

    /** 绑定勋章类型的键。 */
    private static final String KEY_OWNER = "MedalOwner";

    /** 累计经验的键。 */
    private static final String KEY_EXP = "MedalExp";

    /** 遗落之主开场白标记。 */
    private static final String KEY_LORD_INTRO = "LordIntro";

    /** 遗落之主决斗标记。 */
    private static final String KEY_LORD_DUEL = "LordDuel";

    private final ItemStack stack;

    private MedalNbt(ItemStack stack) {
        this.stack = stack;
    }

    /**
     * 取出某个物品栈上的熟练度读写器。
     *
     * <p>{@code stack} 为 {@code null} 或空时返回一个**只读的哑实例**
     * (所有读取返回默认值,所有写入静默丢弃),避免到处判空。
     */
    public static MedalNbt of(ItemStack stack) {
        return new MedalNbt(stack);
    }

    /** 是否为可用的物品栈。 */
    private boolean valid() {
        return stack != null && !stack.isEmpty();
    }

    /** 取到可写的根标签(为空栈时返回 null)。 */
    private CompoundTag writable() {
        if (!valid()) {
            return null;
        }
        return stack.getOrCreateTag();
    }

    /** 取到只读的根标签(为空栈或未初始化时返回 null)。 */
    private CompoundTag readable() {
        if (!valid() || !stack.hasTag()) {
            return null;
        }
        return stack.getTag();
    }

    // ================================================================
    // 初始化 / 绑定
    // ================================================================

    /**
     * 在物品被创建时绑定勋章类型。
     *
     * <p>幂等:已绑定过同样类型时不做任何事。
     * 在 {@code ItemMedal#onCraftedBy} / 创造标签页取出时调用。
     */
    public void bind(MedalType type) {
        CompoundTag tag = writable();
        if (tag == null) {
            return;
        }
        if (!type.id().equals(tag.getString(KEY_OWNER))) {
            tag.putString(KEY_OWNER, type.id());
        }
    }

    /**
     * 该物品栈绑定的勋章类型。
     *
     * <p>未绑定时返回 {@code null}(例如旧存档中遗留的勋章)。
     */
    public MedalType owner() {
        CompoundTag tag = readable();
        if (tag == null) {
            return null;
        }
        String id = tag.getString(KEY_OWNER);
        return id.isEmpty() ? null : MedalType.byId(id);
    }

    // ================================================================
    // 经验与等级(与 PlayerMedals 同形)
    // ================================================================

    /** 获取累计经验。 */
    public long getExp(MedalType type) {
        CompoundTag tag = readable();
        return tag == null ? 0L : tag.getLong(KEY_EXP);
    }

    /** 获取当前等级(至少 1 级,最高由配置决定)。 */
    public int getLevel(MedalType type) {
        return ExpTable.levelFromExp(getExp(type));
    }

    /**
     * 增加熟练度经验（上限 = 配置的最高等级）。
     *
     * @param amount 经验值(浮点,内部累计为整数,末尾四舍五入)
     * @return 增加的等级数
     */
    public int addExp(MedalType type, double amount) {
        return addExp(type, amount, ExpTable.maxLevel());
    }

    /**
     * 增加熟练度经验，并在 {@code levelCap} 处封顶。
     *
     * <h2>为什么不直接截断经验值</h2>
     * 枷锁是「不能<b>继续</b>升级」，不是「把已有等级扣掉」。
     * 旧存档里可能已经有人刷到 30 级，加上 20 级枷锁后如果强行回落，
     * 玩家会认为自己的进度被吃了。所以这里只在<b>写入</b>时封顶：
     * <ul>
     *   <li>未到枷锁 → 正常写入，最多写到「刚好停在 cap 级」的那一点经验；</li>
     *   <li>已在枷锁之上 → 直接返回 0，经验与等级都保持原样。</li>
     * </ul>
     *
     * <p>「刚好停在 cap 级」= {@code totalExpForLevel(cap + 1) - 1}：
     * 比升到 cap+1 少 1 点，等级解算就不会越过去。
     */
    public int addExp(MedalType type, double amount, int levelCap) {
        if (amount <= 0) {
            return 0;
        }
        CompoundTag tag = writable();
        if (tag == null) {
            return 0;
        }

        long oldExp = getExp(type);
        int oldLevel = ExpTable.levelFromExp(oldExp);

        int cap = Math.max(1, Math.min(levelCap, ExpTable.maxLevel()));
        long capExp = (cap >= ExpTable.maxLevel())
                ? Long.MAX_VALUE
                : Math.max(0L, ExpTable.totalExpForLevel(cap + 1) - 1L);

        // 已经顶在枷锁上（或老存档的等级比枷锁还高）—— 不再增长
        if (oldExp >= capExp) {
            return 0;
        }

        long newExp = Math.min(capExp, oldExp + Math.round(amount));
        tag.putLong(KEY_EXP, newExp);
        return Math.max(0, ExpTable.levelFromExp(newExp) - oldLevel);
    }

    /** 直接设置等级(调试 / 指令用),同时把经验对齐到该等级起点。 */
    public void setLevel(MedalType type, int lv) {
        CompoundTag tag = writable();
        if (tag == null) {
            return;
        }
        int clamped = Math.max(1, Math.min(ExpTable.maxLevel(), lv));
        tag.putLong(KEY_EXP, ExpTable.totalExpForLevel(clamped));
    }

    /** 该勋章当前等级下的经验进度(0.0~1.0)。 */
    public double getProgress(MedalType type) {
        return ExpTable.progress(getExp(type), getLevel(type));
    }

    // ================================================================
    // 遗落之主通关标记(随勋章走)
    // ================================================================

    /** 是否已经看过遗落之主的开场白长台词。 */
    public boolean hasSeenLordIntro() {
        CompoundTag tag = readable();
        return tag != null && tag.getBoolean(KEY_LORD_INTRO);
    }

    /** 标记开场白已看过。 */
    public void markLordIntroSeen() {
        CompoundTag tag = writable();
        if (tag != null) {
            tag.putBoolean(KEY_LORD_INTRO, true);
        }
    }

    /** 是否已经历过「决斗」演出。 */
    public boolean hasDueledLord() {
        CompoundTag tag = readable();
        return tag != null && tag.getBoolean(KEY_LORD_DUEL);
    }

    /** 标记已进行过决斗。 */
    public void markLordDueled() {
        CompoundTag tag = writable();
        if (tag != null) {
            tag.putBoolean(KEY_LORD_DUEL, true);
        }
    }

    // ================================================================
    // 调试
    // ================================================================

    /** 清空本物品栈上的熟练度数据(调试用)。 */
    public void clear() {
        CompoundTag tag = readable();
        if (tag == null) {
            return;
        }
        tag.remove(KEY_EXP);
        tag.remove(KEY_LORD_INTRO);
        tag.remove(KEY_LORD_DUEL);
    }
}
