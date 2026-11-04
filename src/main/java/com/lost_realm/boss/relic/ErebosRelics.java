package com.lost_realm.boss.relic;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.lost_realm.Medals;

/**
 * <b>乌蠹诺斯的五件遗物</b> —— 每个职业一件。
 *
 * <h2>★ 为什么是五件，不是四件</h2>
 * 原设计写的是"掉落的四职业武器"，因为泰拉瑞亚只有四个职业。
 * 但 <b>MC 这边的 Lost Realm 有五个</b>：射手 / 战士 / 坦克 / 法师 / 召唤师。
 * 照搬四件的话，<b>坦克会是唯一一个"打完了最终 Boss 却什么都没拿到"的职业</b> ——
 * 那是设计事故，不是设计选择。所以这里补齐第五件
 * （{@link #BULWARK_OF_THE_LAST_LIGHT 末光壁垒}，坦克的盾）。
 *
 * <h2>它们有多强</h2>
 * 这几件是<b>通关纪念品</b>，不是平衡单位。原设计给它们的定位是
 * "超模武器"—— 整合包到此已经结束，玩家该带着它们去下一个周目，
 * 而不是在这里被反复削弱。所以数值给得很大方。
 *
 * <h2>为什么不掉落合成材料</h2>
 * 原设计明确要求"四把武器直接掉落，不需要再次合成"。
 * 这是对的：打了一小时最终 Boss，还要玩家回去翻配方，那口气就断了。
 * 直接给成品，拿到手里那一刻就是奖励。
 */
public final class ErebosRelics {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Medals.MODID);

    /** 战士：断轨·裂星巨剑。 */
    public static final RegistryObject<Item> STARFALL_GREATSWORD =
            ITEMS.register("starfall_greatsword",
                    () -> new StarfallGreatswordItem(new Item.Properties()
                            .rarity(Rarity.EPIC).fireResistant().stacksTo(1).durability(4000)));

    /** 射手：寂光·终末轨炮。 */
    public static final RegistryObject<Item> HUSHLIGHT_RAILGUN =
            ITEMS.register("hushlight_railgun",
                    () -> new HushlightRailgunItem(new Item.Properties()
                            .rarity(Rarity.EPIC).fireResistant().stacksTo(1).durability(3000)));

    /** 法师：坍星·湮灭之眼。 */
    public static final RegistryObject<Item> COLLAPSAR_EYE =
            ITEMS.register("collapsar_eye",
                    () -> new CollapsarEyeItem(new Item.Properties()
                            .rarity(Rarity.EPIC).fireResistant().stacksTo(1)));

    /** 召唤师：终星·龙王之契。 */
    public static final RegistryObject<Item> TERMINAL_DRAGONPACT =
            ITEMS.register("terminal_dragonpact",
                    () -> new TerminalDragonpactItem(new Item.Properties()
                            .rarity(Rarity.EPIC).fireResistant().stacksTo(1)));

    /** 坦克：末光壁垒（我补的第五件，原因见类注释）。 */
    public static final RegistryObject<Item> BULWARK_OF_THE_LAST_LIGHT =
            ITEMS.register("bulwark_of_the_last_light",
                    () -> new BulwarkItem(new Item.Properties()
                            .rarity(Rarity.EPIC).fireResistant().stacksTo(1).durability(5000)));

    /** 事件视界 —— 纪念饰品。 */
    public static final RegistryObject<Item> EVENT_HORIZON =
            ITEMS.register("event_horizon",
                    () -> new Item(new Item.Properties()
                            .rarity(Rarity.EPIC).fireResistant().stacksTo(1)));

    public static void init(IEventBus bus) {
        ITEMS.register(bus);
    }

    private ErebosRelics() {}

    /** 全部掉落物（顺序即掉落顺序）。 */
    public static Item[] allDrops() {
        return new Item[]{
                STARFALL_GREATSWORD.get(),
                HUSHLIGHT_RAILGUN.get(),
                COLLAPSAR_EYE.get(),
                TERMINAL_DRAGONPACT.get(),
                BULWARK_OF_THE_LAST_LIGHT.get(),
                EVENT_HORIZON.get()
        };
    }
}
