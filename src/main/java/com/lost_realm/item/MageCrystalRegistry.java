package com.lost_realm.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.lost_realm.Medals;

/**
 * 「晶石」类法师饰品的物品注册（九枚 + 一枚九合一终极）。
 *
 * <p>它们只能放进 {@code crystal} 槽 —— 该槽位默认 0 格、由法师勋章撑开，
 * 因此「必须佩戴法师勋章」这条约束不需要额外代码。
 */
public final class MageCrystalRegistry {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Medals.MODID);

    public static final RegistryObject<Item> CHAIN_CRYSTAL = charm(MageCrystalType.CHAIN, Rarity.RARE);
    public static final RegistryObject<Item> SNIPER_CRYSTAL = charm(MageCrystalType.SNIPER, Rarity.RARE);
    public static final RegistryObject<Item> CIRCLE_CRYSTAL = charm(MageCrystalType.CIRCLE, Rarity.RARE);
    public static final RegistryObject<Item> SOULFIRE_CRYSTAL = charm(MageCrystalType.SOULFIRE, Rarity.RARE);
    public static final RegistryObject<Item> OVERDRAFT_CRYSTAL = charm(MageCrystalType.OVERDRAFT, Rarity.RARE);
    public static final RegistryObject<Item> EMBER_CRYSTAL = charm(MageCrystalType.EMBER, Rarity.RARE);
    public static final RegistryObject<Item> HEXNAIL_CRYSTAL = charm(MageCrystalType.HEXNAIL, Rarity.RARE);
    public static final RegistryObject<Item> ECHO_CRYSTAL = charm(MageCrystalType.ECHO, Rarity.RARE);
    public static final RegistryObject<Item> SWIFT_CRYSTAL = charm(MageCrystalType.SWIFT, Rarity.RARE);
    /** 九合一终极晶石。 */
    public static final RegistryObject<Item> SOVEREIGN_CRYSTAL = charm(MageCrystalType.SOVEREIGN, Rarity.EPIC);

    private static RegistryObject<Item> charm(MageCrystalType type, Rarity rarity) {
        RegistryObject<Item> registered = ITEMS.register(type.id(),
                () -> new MageCrystalItem(type,
                        new Item.Properties().stacksTo(1).rarity(rarity)));
        type.bind(registered);
        return registered;
    }

    public static void init(IEventBus bus) {
        ITEMS.register(bus);
    }

    private MageCrystalRegistry() {}
}
