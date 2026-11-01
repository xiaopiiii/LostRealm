package com.lost_realm.item;

import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

import com.lost_realm.config.MedalsConfig;

import top.theillusivec4.curios.api.CuriosApi;

/**
 * 九枚「刃」类战士饰品的类型。
 *
 * <h2>为什么不需要检查「有没有戴战士勋章」</h2>
 * 它们只能待在 {@code weapon} 槽里，而那个槽位的基础容量是 <b>0</b> ——
 * 只有佩戴战士勋章时才会被 {@code SlotAttribute} 撑开（见 {@code ItemMedal}）。
 * 换句话说：能把这枚饰品放进槽里，就说明此刻正戴着战士勋章；
 * 摘下勋章的瞬间槽位连同饰品一起被收回。判定天然成立，不必重复检查。
 */
public enum WarriorCharmType {

    /** 横扫之刃：横扫范围更大，且疾跑时也能触发横扫。 */
    SWEEPING("sweeping_blade"),
    /** 双持之刃：双手都持武器时，伤害与攻速各 +10%。 */
    DUAL("dual_blade"),
    /** 暴击之刃：暴击率 +10%、暴击伤害 +20%。 */
    CRIT("crit_blade"),
    /** 背刺之刃：从背后攻击时伤害 +15%。 */
    BACKSTAB("backstab_blade"),
    /** 缴械之刃：攻击时按概率缴械目标，使其一段时间内无法攻击。 */
    DISARM("disarm_blade"),
    /** 运气之刃：攻击伤害在正负区间内随机浮动，幸运值越高、最差的那一档越好。 */
    LUCK("luck_blade"),
    /** 蓄能之刃：停手越久，下一次攻击的加成越高。 */
    CHARGING("charging_blade"),
    /** 突进之刃：双击攻击键朝面朝方向突进，路径上的目标受到伤害。 */
    DASH("dash_blade"),
    /** 坚韧之刃：同一个敌人连续命中时伤害递减，血越少上限越高（战士唯一的防御手段）。 */
    TOUGHNESS("toughness_blade");

    private final String id;
    private RegistryObject<Item> item;

    WarriorCharmType(String id) {
        this.id = id;
    }

    /** 注册名（同时也是贴图与语言键的尾段）。 */
    public String id() {
        return id;
    }

    void bind(RegistryObject<Item> registered) {
        this.item = registered;
    }

    public RegistryObject<Item> item() {
        return item;
    }

    /** 物品名语言键。 */
    public String langKey() {
        return "item.lost_realm." + id;
    }

    /**
     * 该饰品 tooltip 的参数（顺序必须与语言键里的 {@code %s} 一致）。
     *
     * <p>数值全部取实时配置 —— 玩家改了配置，tooltip 立刻跟着变。
     */
    public Object[] tipArgs() {
        return switch (this) {
            case SWEEPING -> new Object[] { MedalsConfig.WARRIOR_SWEEP_RADIUS.get() };
            case DUAL -> new Object[] { MedalsConfig.WARRIOR_DUAL_DAMAGE.get(),
                    MedalsConfig.WARRIOR_DUAL_ATTACK_SPEED.get() };
            case CRIT -> new Object[] { MedalsConfig.WARRIOR_CRIT_CHANCE.get(),
                    MedalsConfig.WARRIOR_CRIT_DAMAGE.get() };
            case BACKSTAB -> new Object[] { MedalsConfig.WARRIOR_BACKSTAB_BONUS.get() };
            case DISARM -> new Object[] { MedalsConfig.WARRIOR_DISARM_CHANCE.get(),
                    MedalsConfig.WARRIOR_DISARM_SECONDS.get(),
                    MedalsConfig.WARRIOR_DISARM_COOLDOWN.get() };
            case LUCK -> new Object[] { MedalsConfig.WARRIOR_LUCK_RANGE.get(),
                    MedalsConfig.WARRIOR_LUCK_PER_POINT.get(),
                    MedalsConfig.WARRIOR_LUCK_FLOOR_CAP.get() };
            case CHARGING -> new Object[] { MedalsConfig.WARRIOR_CHARGE_PER_SECOND.get(),
                    MedalsConfig.WARRIOR_CHARGE_MAX.get() };
            case TOUGHNESS -> new Object[] { MedalsConfig.WARRIOR_TOUGHNESS_PER_HIT.get(),
                    MedalsConfig.WARRIOR_TOUGHNESS_BASE_CAP.get(),
                    MedalsConfig.WARRIOR_TOUGHNESS_CAP_PER_STEP.get(),
                    MedalsConfig.WARRIOR_TOUGHNESS_WINDOW_SECONDS.get() };
            case DASH -> new Object[] { MedalsConfig.WARRIOR_DASH_SPEED_THRESHOLD.get(),
                    MedalsConfig.WARRIOR_DASH_DISTANCE.get(),
                    MedalsConfig.WARRIOR_DASH_DAMAGE.get(),
                    MedalsConfig.WARRIOR_DASH_COOLDOWN.get() };
        };
    }

    /** 这枚饰品此刻是否在「武器」槽里。 */
    public boolean isActive(LivingEntity entity) {
        return activeSet(entity).contains(this);
    }

    /** 一次查询取回该实体此刻全部在槽的战士饰品。 */
    public static Set<WarriorCharmType> activeSet(LivingEntity entity) {
        if (entity == null) {
            return Set.of();
        }
        return CuriosApi.getCuriosInventory(entity).map(handler ->
                handler.findCurios(s -> s.getItem() instanceof WarriorCharmItem).stream()
                        .map(result -> ((WarriorCharmItem) result.stack().getItem()).charmType())
                        .collect(Collectors.toSet())
        ).orElse(Set.of());
    }
}
