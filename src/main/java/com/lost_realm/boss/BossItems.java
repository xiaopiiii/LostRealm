package com.lost_realm.boss;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.BlockItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.lost_realm.Medals;
import com.lost_realm.boss.item.RealmKeyItem;
import com.lost_realm.boss.item.FragmentItem;
import com.lost_realm.boss.item.RealmSourceItem;
import com.lost_realm.boss.item.EchoItem;
import com.lost_realm.boss.item.RealmCrownItem;
import com.lost_realm.boss.item.ScytheItem;
import com.lost_realm.boss.item.DarkstarBeaconItem;

/**
 * Boss 系统的物品注册 —— 任务链材料、门钥匙、掉落物。
 *
 * <h2>物品体系(dv5)</h2>
 * <table>
 *   <tr><th>物品</th><th>获取</th><th>用途</th></tr>
 *   <tr><td>{@code lost_realm_fragment} 遗落碎片</td>
 *       <td>任务链①击杀 3 个整合包 Boss</td><td>合成门钥匙</td></tr>
 *   <tr><td>{@code altar_core} 祭坛激活核心</td>
 *       <td>击杀守望者仆从 / 波次怪</td><td>合成门钥匙</td></tr>
 *   <tr><td>{@code warden_core} 遗落之主·核心</td>
 *       <td>Boss 掉落(100%)</td><td>合成门钥匙 / 遗落境冠冕</td></tr>
 *   <tr><td>{@code realm_source} 遗落本源</td>
 *       <td>Boss 掉落(100%)</td><td>突破勋章等级上限(30→35)</td></tr>
 *   <tr><td>{@code warden_echo} 遗落之主的残响</td>
 *       <td>Boss 掉落(30%)</td><td>装饰性战利品</td></tr>
 *   <tr><td>{@code realm_crown} 遗落境冠冕</td>
 *       <td>Boss 掉落(8%)</td><td>毕业饰品</td></tr>
 *   <tr><td>{@code realm_key} 遗落之境门钥匙</td>
 *       <td>任务链③ / 合成</td><td>点燃传送门</td></tr>
 * </table>
 *
 * <h2>dv5 移除</h2>
 * 旧版的 {@code warden_summon}(召唤符)已整体删除 ——
 * 新流程改为「进维度即可见 Boss」,取而代之的是门钥匙配方里
 * 并入了原召唤符的核心材料,体现「召唤物 → 门钥匙」的语义迁移。
 */
public final class BossItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Medals.MODID);

    // ================================================================
    // 任务链材料
    // ================================================================

    /**
     * 遗落碎片 —— 任务链①的产物。
     *
     * <p>击杀整合包内 3 个指定 Boss 各掉 1 块。
     * 用 {@link FragmentItem} 而非普通 Item,是为了让它能记录
     * "来自哪个 Boss"(用 NBT 或子类型),供任务链提示玩家进度。
     */
    public static final RegistryObject<Item> FRAGMENT = ITEMS.register("lost_realm_fragment",
            () -> new FragmentItem(new Item.Properties().rarity(Rarity.RARE).stacksTo(16)));

    /**
     * 祭坛激活核心 —— 任务链④的产物。
     *
     * <p>击败守望者仆从掉落。3 个仆从各掉 1 个,集齐 3 个才能继续。
     */
    public static final RegistryObject<Item> ALTAR_CORE = ITEMS.register("altar_core",
            () -> new Item(new Item.Properties().rarity(Rarity.RARE).stacksTo(3)));

    /**
     * 遗落之主的死神镰 —— <b>dv30 新增</b>：Boss 手中的兵刃。
     *
     * <p>★ 它不是模型几何：dv29 用长方体拼了十几版都不像新月弧，
     * 现在改为独立物品，贴图直接取自参考图，由
     * {@code client/boss/WardenItemInHandLayer} 挂到 {@code weapon} 骨骼上。
     *
     * <p>{@code fireResistant()} + {@code stacksTo(1)}：终局兵刃，
     * 不掉进岩浆消失、不可堆叠。
     */
    public static final RegistryObject<Item> WARDEN_SCYTHE = ITEMS.register("warden_scythe",
            () -> new ScytheItem(ScytheItem.TIER, 64, -3.0F, new Item.Properties()
                    .rarity(Rarity.EPIC)
                    .fireResistant()
                    .stacksTo(1)));

    /**
     * 遗落之境门钥匙 —— 任务链③的产物。
     *
     * <p>{@link RealmKeyItem}:右键点燃传送门。不消耗。
     */
    public static final RegistryObject<Item> REALM_KEY = ITEMS.register("realm_key",
            () -> new RealmKeyItem(new Item.Properties()
                    .rarity(Rarity.EPIC)
                    .stacksTo(1)));

    /**
     * 遗落之主的召唤符 —— <b>dv5 已删除</b>。
     *
     * <p>新流程无需召唤物:玩家进入遗落境即可在城中心见到遗落之主。
     * 原本的召唤符材料已并入门钥匙配方。此处保留注释以记录这次迁移,
     * 避免后来者疑惑「任务链里怎么少了一环」。
     */

    // ================================================================
    // Boss 掉落
    // ================================================================

    /**
     * 遗落之主·核心 —— 必然掉落。
     *
     * <p>用于合成门钥匙与遗落境冠冕。
     */
    public static final RegistryObject<Item> WARDEN_CORE = ITEMS.register("warden_core",
            () -> new Item(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));

    /**
     * 遗落本源 —— 必然掉落,2~5 个。
     *
     * <p><b>关键设计</b>:用于突破勋章等级上限(30 → 35)。
     * 它给了满级玩家一条继续成长的路,也让打 Boss 有实质收益 ——
     * 否则熟练度练满之后,Boss 就只是"一次性的观赏内容"。
     */
    public static final RegistryObject<Item> REALM_SOURCE = ITEMS.register("realm_source",
            () -> new RealmSourceItem(new Item.Properties()
                    .rarity(Rarity.EPIC)
                    .fireResistant()
                    .stacksTo(16)));

    /**
     * 终末之影的残响 —— 30% 掉落的装饰性战利品。
     */
    public static final RegistryObject<Item> WARDEN_ECHO = ITEMS.register("warden_echo",
            () -> new EchoItem(new Item.Properties()
                    .rarity(Rarity.RARE)
                    .fireResistant()
                    .stacksTo(1)));

    /**
     * 遗落境冠冕 —— 8% 掉落的毕业饰品。
     *
     * <p>提供全面小幅加成;因涉及 Curios 集成,单独成类。
     */
    public static final RegistryObject<Item> REALM_CROWN = ITEMS.register("realm_crown",
            () -> new RealmCrownItem(new Item.Properties()
                    .rarity(Rarity.EPIC)
                    .fireResistant()
                    .stacksTo(1)));

    /**
     * 黯星信标 —— <b>dv58 新增</b>：召唤晦星之主·厄瑞玻斯。
     *
     * <p>与旧 Boss 的"进维度即见"不同，厄瑞玻斯需要主动召唤 ——
     * 它是整合包的<b>真·终局</b>，值得一个明确的仪式动作，
     * 而不是走到固定坐标撞见。使用后物品不消耗（原设计要求），
     * 靠冷却与"附近已有本体则拒绝"两条来防刷。
     */
    public static final RegistryObject<Item> DARKSTAR_BEACON = ITEMS.register("darkstar_beacon",
            () -> new DarkstarBeaconItem(new Item.Properties()
                    .rarity(Rarity.EPIC)
                    .fireResistant()
                    .stacksTo(1)));

    public static void init(IEventBus bus) {
        ITEMS.register(bus);
    }

    private BossItems() {}
}
