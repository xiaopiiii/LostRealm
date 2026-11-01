package com.lost_realm.compat.goety;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 法师勋章与【诡厄巫法 / Goety】之间的桥接契约。
 *
 * <h2>为什么用接口 + 实现分离</h2>
 * 法师勋章的技能必须读写 Goety 的灵魂能量、仆从、法术属性。但这些 API 在
 * <b>未安装 Goety 时根本不存在</b>——如果直接在技能类里 import
 * {@code com.Polarice3.Goety.*},未装 Goety 的玩家会在类加载阶段
 * 抛 {@link NoClassDefFoundError} 并导致整个模组崩溃。
 *
 * <p>因此这里定义纯接口(只依赖原版类型),真实实现
 * {@link GoetyBridgeImpl} 只在检测到 Goety 存在时才被加载。
 * 所有技能一律通过 {@link #get()} 拿桥接对象,拿不到就整个技能静默禁用。
 *
 * <p>这与本模组对 Configured 的处理方式一致,是本项目的既定兼容策略。
 */
public interface GoetyBridge {

    // ================================================================
    // 灵魂能量
    // ================================================================

    /** 玩家当前灵魂能量(无容器时为 0)。 */
    int getSouls(Player player);

    /** 玩家灵魂能量上限;无容器时返回 0。 */
    int getMaxSouls(Player player);

    /** 玩家是否持有可用的灵魂容器(图腾 / 已激活的灵魂方舟)。 */
    boolean hasSoulContainer(Player player);

    /**
     * 增加灵魂能量。
     *
     * @return 实际是否写入
     */
    boolean increaseSouls(Player player, int amount);

    /**
     * 减少灵魂能量。
     *
     * @return 实际是否扣成功(灵魂不足返回 false)
     */
    boolean decreaseSouls(Player player, int amount);

    /**
     * 击杀回馈的「基准灵魂量」。
     *
     * <p>直接复用 Goety 自己的计算(不同生物产出不同,如村民更高)。
     */
    int getSoulGiven(LivingEntity victim);

    // ================================================================
    // 法术属性(读 Goety 自身属性,用于咒术增幅等)
    // ================================================================

    /**
     * 目标持有的法术灵魂折扣(0~1,越小越省;Goety 语义为「倍率」)。
     */
    double soulDiscountMultiplier(LivingEntity entity);

    /**
     * 目标持有的法术冷却折扣(0~1,Goety 语义为「倍率」)。
     */
    double cooldownDiscountMultiplier(LivingEntity entity);

    /** 给实体挂上一条法术灵魂折扣修饰符(直接作用于 Goety 属性)。 */
    void applySoulDiscount(LivingEntity entity, java.util.UUID uuid, String name, double amount);

    /** 给实体挂上一条法术冷却折扣修饰符。 */
    void applyCooldownDiscount(LivingEntity entity, java.util.UUID uuid, String name, double amount);

    /** 给实体挂上一条法术强度修饰符。 */
    void applySpellPotency(LivingEntity entity, java.util.UUID uuid, String name, double amount);

    /**
     * 读取实体当前的<b>法术强度总值</b>(已包含全部来源的修饰符)。
     *
     * <p>对应 Goety {@code ModAttributes.SPELL_POTENCY} 的属性实例值。
     * 这是<b>绝对值</b>语义(基础 0,上限 2048),不是百分比。
     *
     * <p>之所以暴露到桥接层,是为了让技能能计算「除自己以外」的基准强度
     * (如 {@code SkillSoulBurn} 与 {@code SkillWitchOverload}),
     * 从而避免按比例施加强度时发生自我叠加放大。
     */
    double spellPotencyTotal(LivingEntity entity);

    /** 读取某条本模组修饰符在「法术强度」属性上的当前值(不存在返回 0)。 */
    double spellPotencyModifierAmount(LivingEntity entity, java.util.UUID uuid);

    /** 给实体挂上一条法术持续时间修饰符。 */
    void applySpellDuration(LivingEntity entity, java.util.UUID uuid, String name, double amount);

    /** 给实体挂上一条法术射程修饰符。 */
    void applySpellRange(LivingEntity entity, java.util.UUID uuid, String name, double amount);

    /**
     * 给实体挂上一条「施法速度」修饰符。
     *
     * <p>对应 Goety {@code ModAttributes.CASTING_SPEED},由 Goety 的施法流程读取,
     * 因此无需拦截施法行为。
     */
    void applyCastingSpeed(LivingEntity entity, java.util.UUID uuid, String name, double amount);

    /**
     * 给实体挂上一条「法术作用半径」修饰符。
     *
     * <p>对应 Goety {@code ModAttributes.SPELL_RADIUS},影响 AOE 类法术的范围。
     */
    void applySpellRadius(LivingEntity entity, java.util.UUID uuid, String name, double amount);

    /** 清理本模组挂上的全部 Goety 法术属性修饰符。 */
    void clearSpellModifiers(LivingEntity entity);

    // ================================================================
    // 法术识别
    // ================================================================

    /**
     * 读取玩家当前手持魔杖所施法术的「流派」。
     *
     * <p>对应 Goety {@code IWand#getSpellType()},返回 9 个 {@code SpellType} 之一。
     * 未持魔杖 / 无法读取时返回 {@code null}。
     *
     * <p>返回值为 {@code Object} 而非直接的 {@code SpellType} 类型,
     * 是为了让调用方(不依赖 Goety 的代码)无需 import Goety 的类即可比较流派,
     * 比较时用 {@code String.valueOf(...)} 或 {@code toString()} 即可。
     */
    Object currentSpellType(Player player);

    /**
     * 读取当前法术流派的枚举序号(供只能存 int 的状态系统使用)。
     *
     * @return {@code SpellType.ordinal()};未持魔杖或无法识别时返回 {@code -1}
     */
    int currentSpellTypeOrdinal(Player player);

    /**
     * 立刻刷新玩家当前手持魔杖所施法术的冷却。
     *
     * <p>对应 Goety {@code FocusCooldown#removeCooldown(player, level, item)},
     * 直接清空该法术的冷却计时。
     *
     * @return 是否实际刷新了某个冷却
     */
    boolean refreshWandCooldown(Player player);

    // ================================================================
    // 仆从
    // ================================================================

    /** 该实体是否为 Goety 的「可归属仆从」。 */
    boolean isServant(net.minecraft.world.entity.Entity entity);

    /** 该仆从是否属于指定玩家(含使魔)。 */
    boolean isOwnedBy(net.minecraft.world.entity.Entity servant, Player owner);

    /**
     * 取仆从的主人；不是仆从或找不到主人时返回 {@code null}。
     *
     * <p>召唤师的多数效果都是「仆从打到人 → 反哺主人」这种方向，
     * 所以每次都得从仆从那一侧回到主人身上。
     */
    LivingEntity masterOf(net.minecraft.world.entity.Entity servant);

    /**
     * 把一个刚生成/复制出来的仆从登记到指定玩家名下。
     *
     * <p>只给裂变之契用：它凭空多召一个，那个新实体身上没有任何归属信息，
     * 不认领的话会立刻变成野生怪。
     */
    boolean adoptServant(net.minecraft.world.entity.Entity servant, Player owner);

    /** 统计玩家当前存活的仆从数量。 */
    int countServants(Player owner);

    /**
     * 玩家当前附近、属于他的全部仆从。
     *
     * <p>召唤师的整套体系都要「对每一个仆从单独施加属性」，只有计数是不够的。
     * 返回的是活着的实体（已移除的不在其中）。
     */
    java.util.List<net.minecraft.world.entity.Entity> servantsOf(Player owner);

    /** 该仆从是否受寿命限制（永久存在的仆从返回 {@code false}）。 */
    boolean hasLifespan(net.minecraft.world.entity.Entity servant);

    /** 读取仆从剩余寿命（tick）；不受寿命限制时返回 {@code -1}。 */
    int servantLifespan(net.minecraft.world.entity.Entity servant);

    /** 直接设定仆从剩余寿命（tick）。 */
    void setServantLifespan(net.minecraft.world.entity.Entity servant, int ticks);

    /**
     * 让仆从不再有寿命限制（终极「王者之主」）。
     *
     * <p>与 {@link #extendServantLife} 的区别是质变：那条是「多活一会儿」，
     * 这条是「永远不会因为寿命而消散」。
     */
    void makeServantPermanent(net.minecraft.world.entity.Entity servant);

    /** 给玩家的仆从施加一次「军团强化」。 */
    void buffServant(net.minecraft.world.entity.Entity servant, int durationTicks,
                     double damageBonus, double speedBonus);

    /** 延长仆从寿命(若有寿命限制)。 */
    void extendServantLife(net.minecraft.world.entity.Entity servant, int ticks);

    // ================================================================
    // 魔杖 / 法术识别
    // ================================================================

    /** 该物品是否为 Goety 的魔杖 / 法杖。 */
    boolean isWand(ItemStack stack);

    /** 该物品是否为 Goety 的聚晶(法术载体)。 */
    boolean isFocus(ItemStack stack);

    /** 玩家是否正在持握魔杖(主手或副手)。 */
    boolean isHoldingWand(Player player);

    /**
     * 读取「当前手持魔杖所施法术」的实际灵魂消耗。
     *
     * <p>对应 Goety {@code IWand#SoulUse(LivingEntity, ItemStack)},
     * 返回值已包含施法者自身的灵魂折扣,因此可直接用于【虚空回响】的精确返还。
     * 非法杖 / 无法读取时返回 0。
     */
    int wandSoulCost(LivingEntity caster, ItemStack wand);

    // ================================================================
    // 效果
    // ================================================================

    /** 给目标施加 Goety 的「诅咒」效果(禁止回血)。 */
    boolean applyCurse(LivingEntity target, int durationTicks, int amplifier);

    // ================================================================
    // 桥接实例
    // ================================================================

    /** 当前桥接实例;Goety 未安装时为 {@code null}。 */
    static GoetyBridge get() {
        return GoetyCompat.bridge();
    }

    /** Goety 是否已加载且桥接可用。 */
    static boolean available() {
        return get() != null;
    }
}
