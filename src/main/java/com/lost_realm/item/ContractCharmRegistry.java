package com.lost_realm.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.lost_realm.Medals;

/**
 * 「契约」类召唤师饰品的物品注册（九枚 + 一枚九合一终极「王者之主」）。
 *
 * <p>它们只能放进 {@code contract} 槽 —— 该槽位默认 0 格、由召唤师勋章撑开，
 * 因此「必须佩戴召唤师勋章」这条约束不需要额外代码。
 */
public final class ContractCharmRegistry {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Medals.MODID);

    public static final RegistryObject<Item> DOMINION_CONTRACT = charm(ContractCharmType.DOMINION, Rarity.RARE);
    public static final RegistryObject<Item> LEGION_CONTRACT = charm(ContractCharmType.LEGION, Rarity.RARE);
    public static final RegistryObject<Item> SYMBIOSIS_CONTRACT = charm(ContractCharmType.SYMBIOSIS, Rarity.RARE);
    public static final RegistryObject<Item> MARTYR_CONTRACT = charm(ContractCharmType.MARTYR, Rarity.RARE);
    public static final RegistryObject<Item> SOULCHAIN_CONTRACT = charm(ContractCharmType.SOULCHAIN, Rarity.RARE);
    public static final RegistryObject<Item> FLESH_CONTRACT = charm(ContractCharmType.FLESH, Rarity.RARE);
    public static final RegistryObject<Item> BONE_CONTRACT = charm(ContractCharmType.BONE, Rarity.RARE);
    public static final RegistryObject<Item> FISSION_CONTRACT = charm(ContractCharmType.FISSION, Rarity.RARE);
    public static final RegistryObject<Item> DECREE_CONTRACT = charm(ContractCharmType.DECREE, Rarity.RARE);
    /** 九合一终极。 */
    public static final RegistryObject<Item> OVERLORD_CONTRACT = charm(ContractCharmType.OVERLORD, Rarity.EPIC);

    private static RegistryObject<Item> charm(ContractCharmType type, Rarity rarity) {
        RegistryObject<Item> registered = ITEMS.register(type.id(),
                () -> new ContractCharmItem(type,
                        new Item.Properties().stacksTo(1).rarity(rarity)));
        type.bind(registered);
        return registered;
    }

    public static void init(IEventBus bus) {
        ITEMS.register(bus);
    }

    private ContractCharmRegistry() {}
}
