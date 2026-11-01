package com.lost_realm.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.lost_realm.Medals;

/**
 * 「刃」类战士饰品的物品注册。
 *
 * <p>九枚共用一套注册流程（{@link WarriorCharmType} 的枚举值直接当注册名），
 * 注册完成后把 {@link RegistryObject} 回填进枚举，别处想取物品实例时不必再写字符串 id。
 *
 * <p>它们只能放进 {@code weapon} 槽 —— 那个槽位默认 0 格、由战士勋章撑开，
 * 因此「必须佩戴战士勋章」这条约束不需要额外代码。
 */
public final class WarriorCharmRegistry {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Medals.MODID);

    public static final RegistryObject<Item> SWEEPING_BLADE = charm(WarriorCharmType.SWEEPING);
    public static final RegistryObject<Item> DUAL_BLADE = charm(WarriorCharmType.DUAL);
    public static final RegistryObject<Item> CRIT_BLADE = charm(WarriorCharmType.CRIT);
    public static final RegistryObject<Item> BACKSTAB_BLADE = charm(WarriorCharmType.BACKSTAB);
    public static final RegistryObject<Item> DISARM_BLADE = charm(WarriorCharmType.DISARM);
    public static final RegistryObject<Item> LUCK_BLADE = charm(WarriorCharmType.LUCK);
    public static final RegistryObject<Item> CHARGING_BLADE = charm(WarriorCharmType.CHARGING);
    public static final RegistryObject<Item> DASH_BLADE = charm(WarriorCharmType.DASH);
    public static final RegistryObject<Item> TOUGHNESS_BLADE = charm(WarriorCharmType.TOUGHNESS);

    private static RegistryObject<Item> charm(WarriorCharmType type) {
        RegistryObject<Item> registered = ITEMS.register(type.id(),
                () -> new WarriorCharmItem(type,
                        new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
        type.bind(registered);
        return registered;
    }

    public static void init(IEventBus bus) {
        ITEMS.register(bus);
    }

    private WarriorCharmRegistry() {}
}
