package com.lost_realm.boss;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.lost_realm.Medals;
import com.lost_realm.boss.block.LostAltarBlock;
import com.lost_realm.boss.block.LostAltarBlockEntity;
import com.lost_realm.boss.block.RealmPortalBlock;
import com.lost_realm.boss.block.VoidStoneBlock;
import com.lost_realm.boss.block.ArenaFrameBlock;

/**
 * Boss 系统的方块与方块物品注册。
 *
 * <h2>方块清单</h2>
 * | 方块 | 用途 |
 * |---|---|
 * | {@code lost_realm:realm_portal} | 遗落境传送门门面(不可拾取、无碰撞) |
 * | {@code lost_realm:void_stone} | 竞技场建材(虚空石) |
 * | {@code lost_realm:warden_altar} | 四相祭坛本体(可被点亮) |
 * | {@code lost_realm:arena_frame} | 竞技场建筑骨架(装饰) |
 *
 * <p>所有方块 id 统一 {@code lost_realm:} 前缀,与模组既有物品风格一致。
 */
public final class BossBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Medals.MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Medals.MODID);

    /** 方块实体类型（目前只有中央祭坛一个）。 */
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Medals.MODID);

    // ================================================================
    // 传送门门面
    // ================================================================

    /**
     * 遗落境传送门。
     *
     * <p>关键属性:
     * <ul>
     *   <li>{@code noCollission()} —— 玩家能走进去(否则会被挡住,永远无法传送)</li>
     *   <li>{@code noOcclusion()} —— 不遮挡光线,视觉上是半透明的裂隙</li>
     *   <li>{@code lightLevel(11)} —— 自带光源,与下界门一致</li>
     *   <li>{@code pushReaction(BLOCK)} —— 不可被活塞推动</li>
     * </ul>
     */
    public static final RegistryObject<Block> REALM_PORTAL = BLOCKS.register("realm_portal",
            () -> new RealmPortalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .noCollission()
                    .noOcclusion()
                    .lightLevel(s -> 11)
                    .strength(-1.0F, 3600000.0F)
                    .pushReaction(PushReaction.BLOCK)
                    .sound(SoundType.GLASS)));

    // ================================================================
    // 竞技场建材
    // ================================================================

    /** 虚空石 —— 竞技场主建材,深紫近黑,与「遗落境」的昏暗基调一致。 */
    public static final RegistryObject<Block> VOID_STONE = BLOCKS.register("void_stone",
            () -> new VoidStoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(50.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE)));

    /** 竞技场骨架 —— 带发光纹理的装饰性框架。 */
    public static final RegistryObject<Block> ARENA_FRAME = BLOCKS.register("arena_frame",
            () -> new ArenaFrameBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(50.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .lightLevel(s -> 7)
                    .sound(SoundType.DEEPSLATE)));

    // ================================================================
    // 四相祭坛
    // ================================================================

    /**
     * 四相祭坛 —— 战斗核心机制。
     *
     * <p>四个祭坛共用同一个方块类,通过 {@code ALTAR} 状态属性区分是哪一座。
     * 这样只需注册一个方块、一个方块实体,资产也只需一套。
     */
    // ================================================================
    // 遗落祭坛（终末竞技场核心）
    // ================================================================

    /**
     * 遗落祭坛 —— 中央那一座可右键召唤，十二根柱顶各一座作共鸣节点。
     *
     * <p>用 {@code CORE} 状态区分两种身份，而不是注册两个方块：
     * 一套代码、一套贴图、一套掉落；柱顶只作视觉与提示。
     *
     * <p>{@code lightLevel} 随 {@code ACTIVE} 变化 —— 仪式进行时整座祭坛亮起来，
     * 玩家在 100 格外也能看出"仪式在跑"。
     */
    public static final RegistryObject<Block> LOST_ALTAR = BLOCKS.register("lost_altar",
            () -> new LostAltarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(50.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .lightLevel(s -> s.getValue(LostAltarBlock.ACTIVE) ? 15 : 6)
                    .sound(SoundType.AMETHYST)));

    /** 中央祭坛的方块实体（仪式状态机）。 */
    public static final RegistryObject<BlockEntityType<LostAltarBlockEntity>> LOST_ALTAR_BE =
            BLOCK_ENTITIES.register("lost_altar",
                    () -> BlockEntityType.Builder
                            .of(LostAltarBlockEntity::new, LOST_ALTAR.get())
                            .build(null));

    // ================================================================
    // 方块物品
    // ================================================================
    /** 虚空石物品。 */
    public static final RegistryObject<Item> VOID_STONE_ITEM = ITEMS.register("void_stone",
            () -> new BlockItem(VOID_STONE.get(), new Item.Properties()));

    /** 竞技场骨架物品。 */
    public static final RegistryObject<Item> ARENA_FRAME_ITEM = ITEMS.register("arena_frame",
            () -> new BlockItem(ARENA_FRAME.get(), new Item.Properties()));

    /** 四相祭坛物品(可在创造模式放置,生存模式由竞技场生成)。 */

    /** 遗落祭坛物品（创造模式可用；生存模式由竞技场生成）。 */
    public static final RegistryObject<Item> LOST_ALTAR_ITEM = ITEMS.register("lost_altar",
            () -> new BlockItem(LOST_ALTAR.get(), new Item.Properties()));

    public static void init(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }

    private BossBlocks() {}
}
