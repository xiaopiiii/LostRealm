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
 * 九枚「晶石」法师饰品 + 一枚九合一终极晶石。
 *
 * <h2>为什么不需要检查「有没有戴法师勋章」</h2>
 * 它们只能待在 {@code crystal} 槽里，而那个槽位的基础容量是 <b>0</b> ——
 * 只有佩戴法师勋章时才会被 {@code SlotAttribute} 撑开（见 {@code ItemMedal}）。
 */
public enum MageCrystalType {

    /** 连锁晶石：法术命中后弹跳到附近的另一个敌人。 */
    CHAIN("chain_crystal"),
    /** 狙击晶石：离目标越远，法术越强。 */
    SNIPER("sniper_crystal"),
    /** 法阵晶石：施法时脚下展开法阵，站内施法更快更强。 */
    CIRCLE("circle_crystal"),
    /** 魂燃晶石：法术命中时吞噬目标身上的一个减益。 */
    SOULFIRE("soulfire_crystal"),
    /** 魔力透支晶石：灵魂不足时改用生命支付。 */
    OVERDRAFT("overdraft_crystal"),
    /** 余烬晶石：法术命中处留下持续灼烧的元素余烬。 */
    EMBER("ember_crystal"),
    /** 咒钉晶石：法术叠咒钉，目标被上减益时全部引爆。 */
    HEXNAIL("hexnail_crystal"),
    /** 回响晶石：施法时按概率触发坚守者音波。 */
    ECHO("echo_crystal"),
    /** 疾行晶石：施法时的减速大幅减免。 */
    SWIFT("swift_crystal"),
    /** 主宰晶石：九合一终极。 */
    SOVEREIGN("sovereign_crystal");

    private final String id;
    private RegistryObject<Item> item;

    MageCrystalType(String id) {
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

    public boolean isSovereign() {
        return this == SOVEREIGN;
    }

    public String langKey() {
        return "item.lost_realm." + id;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    /**
     * 这枚晶石的完整 tooltip 行。
     *
     * <p>数值全部取实时配置；终极晶石额外多一行说明标志效果。
     */
    public List<Component> tips() {
        String base = langKey() + ".tip";
        return switch (this) {
            case CHAIN -> List.of(tr(base,
                    MedalsConfig.CRYSTAL_CHAIN_RADIUS.get(),
                    MedalsConfig.CRYSTAL_CHAIN_RATIO.get(),
                    MedalsConfig.CRYSTAL_CHAIN_JUMPS.get()));

            case SNIPER -> List.of(tr(base,
                    MedalsConfig.CRYSTAL_SNIPER_STEP.get(),
                    MedalsConfig.CRYSTAL_SNIPER_PER_STEP.get(),
                    MedalsConfig.CRYSTAL_SNIPER_CAP.get()));

            case CIRCLE -> List.of(tr(base,
                    MedalsConfig.CRYSTAL_CIRCLE_RADIUS.get(),
                    MedalsConfig.CRYSTAL_CIRCLE_SECONDS.get(),
                    MedalsConfig.CRYSTAL_CIRCLE_SPEED.get(),
                    MedalsConfig.CRYSTAL_CIRCLE_POTENCY.get()));

            case SOULFIRE -> List.of(tr(base,
                    MedalsConfig.CRYSTAL_SOULFIRE_REFUND.get(),
                    MedalsConfig.CRYSTAL_SOULFIRE_DAMAGE.get()));

            case OVERDRAFT -> List.of(tr(base,
                    MedalsConfig.CRYSTAL_OVERDRAFT_RATIO.get(),
                    MedalsConfig.CRYSTAL_OVERDRAFT_BONUS.get()));

            case EMBER -> List.of(tr(base,
                    MedalsConfig.CRYSTAL_EMBER_SECONDS.get(),
                    MedalsConfig.CRYSTAL_EMBER_RATIO.get()));

            case HEXNAIL -> List.of(tr(base,
                    MedalsConfig.CRYSTAL_HEXNAIL_MAX.get(),
                    MedalsConfig.CRYSTAL_HEXNAIL_DAMAGE.get()));

            case ECHO -> List.of(tr(base,
                    MedalsConfig.CRYSTAL_ECHO_CHANCE.get(),
                    MedalsConfig.CRYSTAL_ECHO_DAMAGE.get()));

            case SWIFT -> List.of(tr(base, MedalsConfig.CRYSTAL_SWIFT_REDUCTION.get()));

            case SOVEREIGN -> List.of(
                    tr(base + ".1",
                            MedalsConfig.CRYSTAL_SOVEREIGN_POTENCY.get(),
                            MedalsConfig.CRYSTAL_SOVEREIGN_CASTING.get(),
                            MedalsConfig.CRYSTAL_SOVEREIGN_RADIUS.get()),
                    tr(base + ".2"));
        };
    }

    public boolean isActive(LivingEntity entity) {
        return activeSet(entity).contains(this);
    }

    public static Set<MageCrystalType> activeSet(LivingEntity entity) {
        if (entity == null) {
            return Set.of();
        }
        return CuriosApi.getCuriosInventory(entity).map(handler ->
                handler.findCurios(s -> s.getItem() instanceof MageCrystalItem).stream()
                        .map(result -> ((MageCrystalItem) result.stack().getItem()).crystalType())
                        .collect(Collectors.toSet())
        ).orElse(Set.of());
    }
}
