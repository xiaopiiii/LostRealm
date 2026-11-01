package com.lost_realm.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.lost_realm.Medals;

/**
 * 「盾」类坦克饰品的物品注册。
 *
 * <p>九枚普通盾 + 一枚九合一终极盾，共用一套流程
 * （{@link TankShieldType} 的枚举值直接当注册名），
 * 注册完成后把 {@link RegistryObject} 回填进枚举。
 *
 * <p>它们只能放进 {@code shield} 槽 —— 那个槽位默认 0 格、由坦克勋章撑开，
 * 因此「必须佩戴坦克勋章」这条约束不需要额外代码。
 */
public final class TankShieldRegistry {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Medals.MODID);

    public static final RegistryObject<Item> THORNS_SHIELD = charm(TankShieldType.THORNS, Rarity.RARE);
    public static final RegistryObject<Item> ADAPTIVE_SHIELD = charm(TankShieldType.ADAPTIVE, Rarity.RARE);
    public static final RegistryObject<Item> SHOCKWAVE_SHIELD = charm(TankShieldType.SHOCKWAVE, Rarity.RARE);
    public static final RegistryObject<Item> SACRIFICE_SHIELD = charm(TankShieldType.SACRIFICE, Rarity.RARE);
    public static final RegistryObject<Item> EARTH_SHIELD = charm(TankShieldType.EARTH, Rarity.RARE);
    public static final RegistryObject<Item> DEVOURING_SHIELD = charm(TankShieldType.DEVOURING, Rarity.RARE);
    public static final RegistryObject<Item> NOURISH_SHIELD = charm(TankShieldType.NOURISH, Rarity.RARE);
    public static final RegistryObject<Item> VITALITY_SHIELD = charm(TankShieldType.VITALITY, Rarity.RARE);
    public static final RegistryObject<Item> VANGUARD_SHIELD = charm(TankShieldType.VANGUARD, Rarity.RARE);
    /** 九合一终极盾 —— 唯一的 EPIC。 */
    public static final RegistryObject<Item> ETERNAL_SHIELD = charm(TankShieldType.ETERNAL, Rarity.EPIC);

    private static RegistryObject<Item> charm(TankShieldType type, Rarity rarity) {
        RegistryObject<Item> registered = ITEMS.register(type.id(),
                () -> new TankShieldItem(type,
                        new Item.Properties().stacksTo(1).rarity(rarity)));
        type.bind(registered);
        return registered;
    }

    public static void init(IEventBus bus) {
        ITEMS.register(bus);
    }

    private TankShieldRegistry() {}
}
