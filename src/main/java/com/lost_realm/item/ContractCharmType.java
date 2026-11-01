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
 * 九枚「契约」召唤师饰品 + 一枚九合一终极「王者之主」。
 *
 * <h2>为什么不需要检查「有没有戴召唤师勋章」</h2>
 * 它们只能待在 {@code contract} 槽里，而那个槽位的基础容量是 <b>0</b> ——
 * 只有佩戴召唤师勋章时才会被 {@code SlotAttribute} 撑开（见 {@code ItemMedal}）。
 *
 * <h2>命名</h2>
 * 九枚统一叫「XX之契」，终极叫「王者之主」。名字里都带一个「契」字，
 * 是为了让玩家在饰品栏里一眼认出这一套属于召唤师。
 */
public enum ContractCharmType {

    /** 统御之契：仆从更硬更疼，且仆从击杀也回灵魂。 */
    DOMINION("dominion_contract"),
    /** 军团之契：仆从数量上限提升，寿命翻倍。 */
    LEGION("legion_contract"),
    /** 共生之契：你受伤时最近的仆从替你扛一部分。 */
    SYMBIOSIS("symbiosis_contract"),
    /** 殉爆之契：仆从死亡时爆炸。 */
    MARTYR("martyr_contract"),
    /** 魂链之契：每个存活仆从给你法术强度。 */
    SOULCHAIN("soulchain_contract"),
    /** 血肉之契：仆从死亡时治疗你。 */
    FLESH("flesh_contract"),
    /** 骨甲之契：每个存活仆从给你减伤。 */
    BONE("bone_contract"),
    /** 裂变之契：召唤时按概率多召一个（血少寿短）。 */
    FISSION("fission_contract"),
    /** 敕令之契：仆从优先打你最近打过的目标，并附带「脆弱」。 */
    DECREE("decree_contract"),
    /** 王者之主：九合一终极。 */
    OVERLORD("overlord_contract");

    private final String id;
    private RegistryObject<Item> item;

    ContractCharmType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    void bind(RegistryObject<Item> registered) {
        this.item = registered;
    }

    public RegistryObject<Item> item() {
        return item;
    }

    public boolean isOverlord() {
        return this == OVERLORD;
    }

    public String langKey() {
        return "item.lost_realm." + id;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    /**
     * 这枚契约的完整 tooltip 行。
     *
     * <p>数值全部取实时配置；终极多一行说明标志效果（仆从不再有寿命限制）。
     */
    public List<Component> tips() {
        String base = langKey() + ".tip";
        return switch (this) {
            case DOMINION -> List.of(tr(base,
                    MedalsConfig.CONTRACT_DOMINION_HEALTH.get(),
                    MedalsConfig.CONTRACT_DOMINION_DAMAGE.get(),
                    MedalsConfig.CONTRACT_DOMINION_SOUL.get()));

            case LEGION -> List.of(tr(base,
                    MedalsConfig.CONTRACT_LEGION_LIMIT.get(),
                    MedalsConfig.CONTRACT_LEGION_LIFE.get()));

            case SYMBIOSIS -> List.of(tr(base,
                    fmt(MedalsConfig.CONTRACT_SYMBIOSIS_RADIUS.get()),
                    MedalsConfig.CONTRACT_SYMBIOSIS_RATIO.get()));

            case MARTYR -> List.of(tr(base,
                    fmt(MedalsConfig.CONTRACT_MARTYR_RADIUS.get()),
                    MedalsConfig.CONTRACT_MARTYR_RATIO.get()));

            case SOULCHAIN -> List.of(tr(base,
                    MedalsConfig.CONTRACT_SOULCHAIN_POTENCY.get(),
                    MedalsConfig.CONTRACT_SOULCHAIN_MAX.get()));

            case FLESH -> List.of(tr(base, MedalsConfig.CONTRACT_FLESH_RATIO.get()));

            case BONE -> List.of(tr(base,
                    MedalsConfig.CONTRACT_BONE_REDUCTION.get(),
                    MedalsConfig.CONTRACT_BONE_MAX.get()));

            case FISSION -> List.of(tr(base,
                    MedalsConfig.CONTRACT_FISSION_CHANCE.get(),
                    MedalsConfig.CONTRACT_FISSION_RATIO.get()));

            case DECREE -> List.of(tr(base,
                    MedalsConfig.CONTRACT_DECREE_VULNERABLE.get(),
                    MedalsConfig.CONTRACT_DECREE_SECONDS.get()));

            case OVERLORD -> List.of(
                    tr(base + ".1",
                            MedalsConfig.CONTRACT_OVERLORD_HEALTH.get(),
                            MedalsConfig.CONTRACT_OVERLORD_DAMAGE.get(),
                            MedalsConfig.CONTRACT_OVERLORD_SPEED.get()),
                    tr(base + ".2", MedalsConfig.CONTRACT_OVERLORD_LIMIT.get()));
        };
    }

    /** 去掉多余小数点的数值文本。 */
    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v)
                : String.valueOf(Math.round(v * 10.0D) / 10.0D);
    }

    public boolean isActive(LivingEntity entity) {
        return activeSet(entity).contains(this);
    }

    public static Set<ContractCharmType> activeSet(LivingEntity entity) {
        if (entity == null) {
            return Set.of();
        }
        return CuriosApi.getCuriosInventory(entity).map(handler ->
                handler.findCurios(s -> s.getItem() instanceof ContractCharmItem).stream()
                        .map(result -> ((ContractCharmItem) result.stack().getItem()).charmType())
                        .collect(Collectors.toSet())
        ).orElse(Set.of());
    }
}
