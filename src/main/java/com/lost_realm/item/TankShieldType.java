package com.lost_realm.item;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

import com.lost_realm.config.MedalsConfig;

import top.theillusivec4.curios.api.CuriosApi;

/**
 * 九枚「盾」类坦克饰品 + 一枚九合一终极盾。
 *
 * <h2>为什么不需要检查「有没有戴坦克勋章」</h2>
 * 它们只能待在 {@code shield} 槽里，而那个槽位的基础容量是 <b>0</b> ——
 * 只有佩戴坦克勋章时才会被 {@code SlotAttribute} 撑开（见 {@code ItemMedal}）。
 * 能放进去就说明此刻正戴着坦克勋章，判定天然成立。
 */
public enum TankShieldType {

    /** 荆棘盾：受到伤害时反弹一部分给攻击者。 */
    THORNS("thorns_shield"),
    /** 适应盾：连续受到同<b>类型</b>的伤害时逐次减免。 */
    ADAPTIVE("adaptive_shield"),
    /** 震荡盾：受到伤害时释放冲击波，击退并眩晕周围敌人。 */
    SHOCKWAVE("shockwave_shield"),
    /** 牺牲盾：受到伤害时把一部分分摊给周围的敌人。 */
    SACRIFICE("sacrifice_shield"),
    /** 大地盾：静止一段时间后获得额外减伤与击退免疫。 */
    EARTH("earth_shield"),
    /** 吞噬盾：击杀新物种永久提升最大生命。 */
    DEVOURING("devouring_shield"),
    /** 反哺盾：濒死时以饱食度抵扣伤害。 */
    NOURISH("nourish_shield"),
    /** 生机盾：每次受伤叠加治疗强化。 */
    VITALITY("vitality_shield"),
    /** 先锋盾：持盾强化 + 举盾推进。 */
    VANGUARD("vanguard_shield"),
    /** 不朽神盾：九合一终极盾。 */
    ETERNAL("eternal_shield");

    private final String id;
    private RegistryObject<Item> item;

    TankShieldType(String id) {
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

    /** 是否为九合一终极盾。 */
    public boolean isEternal() {
        return this == ETERNAL;
    }

    /** 物品名语言键。 */
    public String langKey() {
        return "item.lost_realm." + id;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    /**
     * 这枚盾的完整 tooltip 行。
     *
     * <p>数值全部取实时配置 —— 玩家改了配置，tooltip 立刻跟着变。
     * 终极盾额外多四行（绝对免疫 / 举盾精通 / 免疫负面 / 不屈壁垒常驻），
     * 所以这里直接返回一组 {@link Component}，而不是让调用方去数 {@code %s}。
     */
    public List<Component> tips() {
        String base = langKey() + ".tip";
        return switch (this) {
            case THORNS -> List.of(tr(base, MedalsConfig.SHIELD_THORNS_REFLECT.get()));

            case ADAPTIVE -> List.of(tr(base,
                    MedalsConfig.SHIELD_ADAPT_PER_HIT.get(),
                    MedalsConfig.SHIELD_ADAPT_CAP.get(),
                    MedalsConfig.SHIELD_ADAPT_TYPES.get(),
                    MedalsConfig.SHIELD_ADAPT_WINDOW.get()));

            case SHOCKWAVE -> List.of(tr(base,
                    MedalsConfig.SHIELD_SHOCKWAVE_RADIUS.get(),
                    MedalsConfig.SHIELD_SHOCKWAVE_STUN.get()));

            case SACRIFICE -> List.of(tr(base,
                    MedalsConfig.SHIELD_SACRIFICE_RATIO.get(),
                    MedalsConfig.SHIELD_SACRIFICE_RADIUS.get(),
                    MedalsConfig.SHIELD_SACRIFICE_MAX.get(),
                    MedalsConfig.SHIELD_SACRIFICE_COOLDOWN.get()));

            case EARTH -> List.of(tr(base,
                    MedalsConfig.SHIELD_EARTH_SECONDS.get(),
                    MedalsConfig.SHIELD_EARTH_REDUCTION.get()));

            case DEVOURING -> List.of(tr(base,
                    MedalsConfig.SHIELD_DEVOUR_PER_KILL.get(),
                    MedalsConfig.SHIELD_DEVOUR_MAX.get().intValue()));

            case NOURISH -> List.of(tr(base,
                    MedalsConfig.SHIELD_NOURISH_HP_THRESHOLD.get(),
                    MedalsConfig.SHIELD_NOURISH_PER_FOOD.get().intValue(),
                    MedalsConfig.SHIELD_NOURISH_MAX_FOOD.get()));

            case VITALITY -> List.of(tr(base,
                    MedalsConfig.SHIELD_VITALITY_PER_LAYER.get(),
                    MedalsConfig.SHIELD_VITALITY_MAX_LAYERS.get(),
                    MedalsConfig.SHIELD_VITALITY_WINDOW.get()));

            case VANGUARD -> List.of(tr(base,
                    MedalsConfig.SHIELD_VANGUARD_ARMOR.get(),
                    MedalsConfig.SHIELD_VANGUARD_SPEED.get(),
                    MedalsConfig.SHIELD_VANGUARD_KNOCKBACK.get().intValue()));

            case ETERNAL -> List.of(
                    tr(base + ".1",
                            MedalsConfig.SHIELD_ETERNAL_ARMOR.get(),
                            MedalsConfig.SHIELD_ETERNAL_HEALTH.get(),
                            MedalsConfig.SHIELD_ETERNAL_REDUCTION.get(),
                            MedalsConfig.SHIELD_ETERNAL_TOUGHNESS.get(),
                            MedalsConfig.SHIELD_ETERNAL_SPEED.get()),
                    tr(base + ".2"),
                    tr(base + ".3"),
                    tr(base + ".4"),
                    tr(base + ".5"));
        };
    }

    /** 这枚盾此刻是否在「盾牌」槽里。 */
    public boolean isActive(LivingEntity entity) {
        return activeSet(entity).contains(this);
    }

    /** 一次查询取回该实体此刻全部在槽的坦克盾。 */
    public static Set<TankShieldType> activeSet(LivingEntity entity) {
        if (entity == null) {
            return Set.of();
        }
        return CuriosApi.getCuriosInventory(entity).map(handler ->
                handler.findCurios(s -> s.getItem() instanceof TankShieldItem).stream()
                        .map(result -> ((TankShieldItem) result.stack().getItem()).shieldType())
                        .collect(Collectors.toSet())
        ).orElse(Set.of());
    }
}
