package com.lost_realm.item;

import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

import com.lost_realm.config.MedalsConfig;
import com.lost_realm.data.MedalSource;
import com.lost_realm.skill.MedalType;

import top.theillusivec4.curios.api.CuriosApi;

/**
 * 五枚「箭」饰品的类型。
 *
 * <h2>「射手专属」是怎么成立的</h2>
 * 每一枚都要求<b>同时</b>满足两件事才算生效：
 * <ol>
 *   <li>玩家佩戴着<b>射手勋章</b>（{@link MedalSource#isWearing}）；</li>
 *   <li>这枚箭饰品本身戴在「箭」饰品槽里（{@code arrow} 槽，默认 2 格）。</li>
 * </ol>
 * 也就是说，拆掉射手勋章时，戴着的箭饰品会立刻"哑火" —— 不需要在装备/卸下
 * 的时候做任何数据搬运，判定完全由 {@link #isActive} 在用到的那一刻现算。
 *
 * <p>之所以用「现算」而不是「装备时打标记」：玩家可能在背包里挪动勋章、
 * 死亡掉落、被其它模组改变装备，任何缓存都会在这些场景下过期。
 * 一次 {@code isActive} 只有两次 Curios 查询，而它只在箭矢发射/命中时被调用，
 * 完全不在每 tick 的热路径上。
 */
public enum ArrowCharmType {

    /** 药水箭：命中时按概率施加中毒/缓慢/凋零之一。 */
    POTION("potion_arrow"),
    /** 爆炸箭：<b>拉满蓄力</b>射出的箭，命中时产生一次不破坏方块的小范围爆炸。 */
    EXPLOSIVE("explosive_arrow"),
    /** 贯穿之箭：可穿透两个目标，被穿透的目标只吃部分伤害。 */
    PIERCING("piercing_arrow"),
    /** 反重力之箭：射出的箭不再下坠。 */
    ANTIGRAVITY("antigravity_arrow"),
    /** 雷鸣箭：命中时召唤一道小型落雷，对周围敌人造成雷伤并短暂麻痹。 */
    THUNDER("thunder_arrow"),
    /** 双响之箭：命中时按概率从射手位置再补一发射击。 */
    TWIN("twin_arrow"),
    /** 穿甲箭：命中时无视目标一部分护甲。 */
    ARMOR_PIERCE("armor_pierce_arrow"),
    /** 定身箭：命中时按概率让目标无法动弹。 */
    HOLD("hold_arrow"),
    /** 召唤箭：命中时召唤一只以射手为主人的狼。 */
    SUMMON("summon_arrow"),
    /** 星辰贯穿之箭：毕业饰品，拉满 10 秒才能发射。 */
    STAR("star_arrow");

    private final String id;
    private RegistryObject<Item> item;

    ArrowCharmType(String id) {
        this.id = id;
    }

    /** 注册名（同时也是贴图与语言键的尾段）。 */
    public String id() {
        return id;
    }

    /** 注册时回填，用于在别处取到物品实例。 */
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
     * <p>数值全部取实时配置，玩家改了配置后 tooltip 会跟着变 ——
     * 写死数字迟早会和实际效果对不上。
     */
    public Object[] tipArgs() {
        return switch (this) {
            case POTION -> new Object[] {
                    MedalsConfig.ARROW_POTION_CHANCE.get(),
                    MedalsConfig.ARROW_POTION_SECONDS.get() };
            case EXPLOSIVE -> new Object[] {
                    MedalsConfig.ARROW_EXPLOSION_POWER.get() };
            case PIERCING -> new Object[] {
                    MedalsConfig.ARROW_PIERCE_COUNT.get(),
                    MedalsConfig.ARROW_PIERCE_FALLOFF.get() };
            case ANTIGRAVITY -> new Object[] {
                    MedalsConfig.ARROW_NO_GRAVITY_SECONDS.get() };
            case THUNDER -> new Object[] {
                    MedalsConfig.ARROW_THUNDER_DAMAGE.get().longValue(),
                    MedalsConfig.ARROW_THUNDER_RADIUS.get(),
                    MedalsConfig.ARROW_THUNDER_PARALYZE_SECONDS.get() };
            case TWIN -> new Object[] {
                    MedalsConfig.ARROW_TWIN_CHANCE.get(),
                    MedalsConfig.ARROW_TWIN_DAMAGE.get() };
            case ARMOR_PIERCE -> new Object[] {
                    MedalsConfig.ARROW_ARMOR_PIERCE.get() };
            case HOLD -> new Object[] {
                    MedalsConfig.ARROW_HOLD_CHANCE.get(),
                    MedalsConfig.ARROW_HOLD_SECONDS.get() };
            case SUMMON -> new Object[] {
                    MedalsConfig.ARROW_SUMMON_HEALTH.get().longValue(),
                    MedalsConfig.ARROW_SUMMON_MAX.get(),
                    MedalsConfig.ARROW_SUMMON_COOLDOWN_SECONDS.get() };
            case STAR -> new Object[] {
                    MedalsConfig.ARROW_STAR_CHARGE_SECONDS.get(),
                    // DoubleValue.get() 返回的是包装类 Double，直接 (long) 转不过去；
                    // 10000.0 显示成 "10000" 才好看，所以取整。
                    MedalsConfig.ARROW_STAR_DAMAGE.get().longValue() };
        };
    }

    /**
     * 这枚饰品此刻是否对该实体生效。
     *
     * @return 佩戴了射手勋章「且」这枚箭在箭槽里时为 {@code true}
     */
    public boolean isActive(LivingEntity entity) {
        return activeSet(entity).contains(this);
    }

    /**
     * 一次查询取回该实体此刻<b>全部生效</b>的箭饰品。
     *
     * <p>箭矢生成的瞬间要同时判断好几种箭（贯穿 / 反重力 / 星辰），
     * 逐个调 {@link #isActive} 就等于把同一次 Curios 遍历做上三遍。
     * 这里一次拿全，调用方自己 {@code contains}。
     *
     * <p>未佩戴射手勋章时直接返回空集 —— 这就是「射手专属」的全部实现。
     */
    public static Set<ArrowCharmType> activeSet(LivingEntity entity) {
        if (entity == null || !MedalSource.isWearing(entity, MedalType.ARCHER)) {
            return Set.of();
        }
        return CuriosApi.getCuriosInventory(entity).map(handler ->
                handler.findCurios(s -> s.getItem() instanceof ArrowCharmItem).stream()
                        .map(result -> ((ArrowCharmItem) result.stack().getItem()).charmType())
                        .collect(Collectors.toSet())
        ).orElse(Set.of());
    }
}
