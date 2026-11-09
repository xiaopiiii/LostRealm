package com.lost_realm.machine;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.lost_realm.Medals;

/**
 * 「实用方块」的注册 —— 目前只有区块加载器和镇魔火把两件。
 *
 * <p>单独开一个类而不是塞进 {@code BossBlocks}：那两个类是 Boss 系统的资产，
 * 玩家日常用得着的工具方块混进去，以后想找"这个方块是哪个系统加的"就得翻两个文件。
 *
 * <p>注册 id 统一 {@code lost_realm:} 前缀，与模组其余内容一致：
 * <ul>
 *   <li>{@code lost_realm:chunk_loader} —— 区块加载器</li>
 *   <li>{@code lost_realm:anti_spawn_torch} —— 镇魔火把</li>
 * </ul>
 */
public final class MachineBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Medals.MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Medals.MODID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Medals.MODID);

    // ================================================================
    // 区块加载器
    // ================================================================

    /**
     * 区块加载器。
     *
     * <p>亮度跟着开关走：运行时 15 级，待机时只留 4 级微光。
     * 这样在远处一眼就能看出"这台还在工作"，不用凑近右键。
     */
    public static final RegistryObject<Block> CHUNK_LOADER = BLOCKS.register("chunk_loader",
            () -> new ChunkLoaderBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(8.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .lightLevel(s -> s.getValue(ChunkLoaderBlock.ACTIVE) ? 15 : 4)
                    .sound(SoundType.STONE)));

    public static final RegistryObject<BlockEntityType<ChunkLoaderBlockEntity>> CHUNK_LOADER_BE =
            BLOCK_ENTITIES.register("chunk_loader",
                    () -> BlockEntityType.Builder
                            .of(ChunkLoaderBlockEntity::new, CHUNK_LOADER.get())
                            .build(null));

    // ================================================================
    // 镇魔火把
    // ================================================================

    /**
     * 镇魔火把。
     *
     * <p>{@code noOcclusion()} 必须加：火把是细柱子，不遮挡光线，
     * 否则它会把自己所在的那一格算成"被完整方块挡住"，周围亮度判断出错。
     */
    public static final RegistryObject<Block> ANTI_SPAWN_TORCH = BLOCKS.register("anti_spawn_torch",
            () -> new AntiSpawnTorchBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(0.0F)
                    .lightLevel(s -> 14)
                    .noOcclusion()
                    .sound(SoundType.WOOD)));

    public static final RegistryObject<BlockEntityType<AntiSpawnTorchBlockEntity>> ANTI_SPAWN_TORCH_BE =
            BLOCK_ENTITIES.register("anti_spawn_torch",
                    () -> BlockEntityType.Builder
                            .of(AntiSpawnTorchBlockEntity::new, ANTI_SPAWN_TORCH.get())
                            .build(null));

    // ================================================================
    // 方块物品
    // ================================================================

    public static final RegistryObject<Item> CHUNK_LOADER_ITEM = ITEMS.register("chunk_loader",
            () -> new BlockItem(CHUNK_LOADER.get(), new Item.Properties()));

    public static final RegistryObject<Item> ANTI_SPAWN_TORCH_ITEM = ITEMS.register("anti_spawn_torch",
            () -> new BlockItem(ANTI_SPAWN_TORCH.get(), new Item.Properties()));

    public static void init(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }

    private MachineBlocks() {}
}
