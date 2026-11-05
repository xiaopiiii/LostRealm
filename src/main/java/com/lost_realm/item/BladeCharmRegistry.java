package com.lost_realm.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.lost_realm.Medals;

/**
 * 「刃」类刃饰品的物品注册。
 *
 * <p>九枚共用一套注册流程（{@link BladeCharmType} 的枚举值直接当注册名），
 * 注册完成后把 {@link RegistryObject} 回填进枚举，别处想取物品实例时不必再写字符串 id。
 *
 * <p>它们只能放进 {@code weapon} 槽 —— 那个槽位默认 0 格、由刃勋章撑开，
 * 因此「必须佩戴刃勋章」这条约束不需要额外代码。
 */
public final class BladeCharmRegistry {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Medals.MODID);

    public static final RegistryObject<Item> SWEEPING_BLADE = charm(BladeCharmType.SWEEPING);
    public static final RegistryObject<Item> DUAL_BLADE = charm(BladeCharmType.DUAL);
    public static final RegistryObject<Item> CRIT_BLADE = charm(BladeCharmType.CRIT);
    public static final RegistryObject<Item> BACKSTAB_BLADE = charm(BladeCharmType.BACKSTAB);
    public static final RegistryObject<Item> DISARM_BLADE = charm(BladeCharmType.DISARM);
    public static final RegistryObject<Item> LUCK_BLADE = charm(BladeCharmType.LUCK);
    public static final RegistryObject<Item> CHARGING_BLADE = charm(BladeCharmType.CHARGING);
    public static final RegistryObject<Item> DASH_BLADE = charm(BladeCharmType.DASH);
    public static final RegistryObject<Item> TOUGHNESS_BLADE = charm(BladeCharmType.TOUGHNESS);

    private static RegistryObject<Item> charm(BladeCharmType type) {
        RegistryObject<Item> registered = ITEMS.register(type.id(),
                () -> new BladeCharmItem(type,
                        new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
        type.bind(registered);
        return registered;
    }

    public static void init(IEventBus bus) {
        ITEMS.register(bus);
    }

    private BladeCharmRegistry() {}
}
