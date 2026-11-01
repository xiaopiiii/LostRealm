package com.lost_realm.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;


/**
 * 玩家勋章<b>运行时状态</b>存档数据(每名玩家一份)。
 *
 * <h2>职责边界(dv8 起)</h2>
 * 熟练度(累计经验 + 等级)已<b>改存于勋章物品自身的 NBT</b>(见 {@link MedalNbt}),
 * 因此本类现在只管三样东西:
 * <ul>
 *   <li><b>主动技能冷却剩余 tick</b> —— key = skillId</li>
 *   <li><b>节点技能运行时状态</b>(如【星盾】层数、【格挡】格挡值) —— key = skillId</li>
 *   <li><b>时间戳</b> —— key = skillId,value = 游戏 tick</li>
 * </ul>
 *
 * <h2>为什么冷却不跟勋章走</h2>
 * 冷却属于「玩家<b>此刻</b>的状态」而非「勋章的属性」:
 * 把勋章取下来再戴上,冷却理应照旧计时,而不是随物品搬家。
 * 更实际的原因是性能 —— 战斗热路径每 tick 都要查冷却,
 * 若每次都走 {@code CuriosApi.getCuriosInventory(...).findFirstCurio(...)}
 * 会产生可观的无谓开销。
 *
 * <h2>序列化</h2>
 * 所有运行时的属性修饰符都由固定 UUID 施加(见 {@link com.lost_realm.util.ModifierIds}),
 * 因此本类只需持久化上述三类计数,修饰符在 tick 中被幂等地重新施加。
 */
public class PlayerMedals {

    /** 主动技能冷却剩余 tick,key = skillId。 */
    private final Map<String, Integer> cooldowns = new HashMap<>();

    /** 节点技能运行时状态,key = skillId,value = 任意数值。 */
    private final Map<String, Integer> skillState = new HashMap<>();

    /** 时间戳记录,key = skillId,value = 游戏 tick。 */
    private final Map<String, Long> timestamps = new HashMap<>();

    /**
     * 已打破的枷锁数量（0 / 1 / 2）。
     *
     * <p>这是<b>玩家级</b>进度，不是勋章级 —— 三个章节是整合包的流程，
     * 不是某一枚勋章的属性。破一次枷，五枚勋章一起受益。
     *
     * <p>放在玩家 capability 而不是勋章 NBT 还有一个原因：
     * 勋章可以交易、掉落、被抢，而「你走到第几章」不该跟着物品走。
     */
    private int shackleStage;

    // ================================================================
    // 冷却
    // ================================================================

    public boolean isOnCooldown(String skillId) {
        return cooldowns.getOrDefault(skillId, 0) > 0;
    }

    public int getCooldown(String skillId) {
        return cooldowns.getOrDefault(skillId, 0);
    }

    public void setCooldown(String skillId, int ticks) {
        cooldowns.put(skillId, Math.max(0, ticks));
    }

    /** 每 tick 递减冷却。 */
    public void tickCooldowns() {
        if (cooldowns.isEmpty()) {
            return;
        }
        cooldowns.replaceAll((k, v) -> Math.max(0, v - 1));
    }

    // 注：时间胶囊不再"缩短正在计时的剩余冷却"，而是固定降低技能冷却时长本身，
    //     因此这里没有批量扣减冷却的方法——扣减发生在技能结算冷却的那一刻，
    //     见 com.lost_realm.item.TimeCapsuleItem#applyReduction。

    // ================================================================
    // 技能运行时状态
    // ================================================================

    public int getState(String key) {
        return skillState.getOrDefault(key, 0);
    }

    public void setState(String key, int value) {
        skillState.put(key, value);
    }

    public void addState(String key, int delta) {
        skillState.merge(key, delta, Integer::sum);
    }

    public long getTimestamp(String key) {
        return timestamps.getOrDefault(key, 0L);
    }

    /** 已打破的枷锁数量（0 = 卡在 20 级，1 = 卡在 40 级，2 = 全开）。 */
    public int getShackleStage() {
        return this.shackleStage;
    }

    /** 设置已打破的枷锁数量（内部会夹到 0~2）。 */
    public void setShackleStage(int stage) {
        this.shackleStage = Math.max(0, Math.min(MedalProgression.SHACKLE_LEVELS.length, stage));
    }

    /** 当前可达到的最高等级（由枷锁阶段决定）。 */
    public int levelCap() {
        return MedalProgression.capFor(this.shackleStage);
    }

    public void setTimestamp(String key, long tick) {
        timestamps.put(key, tick);
    }

    // 注:遗落之主的「开场白已看过 / 已决斗」两个标记原本也存在这里,
    //     dv8 起改由勋章物品的 NBT 承载(见 MedalNbt#hasSeenLordIntro)。

    // ================================================================
    // 序列化
    // ================================================================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        CompoundTag cdTag = new CompoundTag();
        cooldowns.forEach(cdTag::putInt);
        tag.put("Cooldowns", cdTag);

        CompoundTag stateTag = new CompoundTag();
        skillState.forEach(stateTag::putInt);
        tag.put("State", stateTag);

        CompoundTag timeTag = new CompoundTag();
        timestamps.forEach(timeTag::putLong);
        tag.put("Time", timeTag);

        tag.putInt("ShackleStage", this.shackleStage);

        return tag;
    }

    public void load(CompoundTag tag) {
        cooldowns.clear();
        skillState.clear();
        timestamps.clear();

        CompoundTag cdTag = tag.getCompound("Cooldowns");
        for (String key : cdTag.getAllKeys()) {
            cooldowns.put(key, cdTag.getInt(key));
        }

        CompoundTag stateTag = tag.getCompound("State");
        for (String key : stateTag.getAllKeys()) {
            skillState.put(key, stateTag.getInt(key));
        }

        CompoundTag timeTag = tag.getCompound("Time");
        for (String key : timeTag.getAllKeys()) {
            timestamps.put(key, timeTag.getLong(key));
        }

        this.shackleStage = tag.getInt("ShackleStage");
    }

    /** 从玩家身上取出数据(客户端/服务端通用)。 */
    public static PlayerMedals of(Player player) {
        return player.getCapability(MedalsCapability.PLAYER_MEDALS).orElse(new PlayerMedals());
    }

    public static PlayerMedals of(ServerPlayer player) {
        return player.getCapability(MedalsCapability.PLAYER_MEDALS).orElse(new PlayerMedals());
    }

    /** 生成默认 UUID 用于调试。 */
    public static UUID dummy() {
        return new UUID(0L, 0L);
    }
}
