package com.lost_realm.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.lost_realm.Medals;

/**
 * 箭类饰品与时间胶囊的物品注册。
 *
 * <p>五个箭饰品共用一个注册流程（{@link ArrowCharmType} 的枚举值直接当注册名），
 * 注册完成后把 {@link RegistryObject} 回填进枚举 —— 这样别处想拿某个箭饰品的
 * 物品实例时，不用再写一遍字符串 id。
 *
 * <p>创造标签页的条目在 {@link MedalRegistry} 里统一添加，本类只管注册。
 */
public final class ArrowRegistry {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Medals.MODID);

    /** 时间胶囊：可堆叠的消耗品。 */
    public static final RegistryObject<Item> TIME_CAPSULE = ITEMS.register("time_capsule",
            () -> new TimeCapsuleItem(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> POTION_ARROW = charm(ArrowCharmType.POTION, Rarity.RARE);
    public static final RegistryObject<Item> EXPLOSIVE_ARROW = charm(ArrowCharmType.EXPLOSIVE, Rarity.RARE);
    public static final RegistryObject<Item> PIERCING_ARROW = charm(ArrowCharmType.PIERCING, Rarity.RARE);
    public static final RegistryObject<Item> ANTIGRAVITY_ARROW = charm(ArrowCharmType.ANTIGRAVITY, Rarity.RARE);

    public static final RegistryObject<Item> THUNDER_ARROW = charm(ArrowCharmType.THUNDER, Rarity.RARE);
    public static final RegistryObject<Item> TWIN_ARROW = charm(ArrowCharmType.TWIN, Rarity.RARE);
    public static final RegistryObject<Item> ARMOR_PIERCE_ARROW = charm(ArrowCharmType.ARMOR_PIERCE, Rarity.RARE);
    public static final RegistryObject<Item> HOLD_ARROW = charm(ArrowCharmType.HOLD, Rarity.RARE);
    public static final RegistryObject<Item> SUMMON_ARROW = charm(ArrowCharmType.SUMMON, Rarity.RARE);

    /**
     * 星辰贯穿之箭 —— 毕业饰品，稀有度单独提到 EPIC。
     *
     * <p>它是「毕业」定位，所以用 EPIC 让它在背包里一眼能认出来。
     */
    public static final RegistryObject<Item> STAR_ARROW = charm(ArrowCharmType.STAR, Rarity.EPIC);

    /** 注册一枚箭饰品，并把 {@link RegistryObject} 回填进枚举。 */
    private static RegistryObject<Item> charm(ArrowCharmType type, Rarity rarity) {
        RegistryObject<Item> registered = ITEMS.register(type.id(),
                () -> new ArrowCharmItem(type,
                        new Item.Properties().stacksTo(1).rarity(rarity)));
        type.bind(registered);
        return registered;
    }

    public static void init(IEventBus bus) {
        ITEMS.register(bus);
    }

    private ArrowRegistry() {}
}
