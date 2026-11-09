package com.lost_realm.machine;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import com.lost_realm.network.MedalsNetwork;
import com.lost_realm.network.PacketChunkLoaderOpen;

/**
 * <b>区块加载器</b>。
 *
 * <p>放下后右键打开界面，用加减按钮把强加载范围在 1×1 / 3×3 / 5×5 / 7×7 / 9×9
 * 之间切换，另有一个总开关。方块状态只有 {@link #ACTIVE} 一个属性 ——
 * 它只负责切换贴图（运行时亮，待机时暗），真正的尺寸存在方块实体的 NBT 里。
 *
 * <h2>为什么半径不放进方块状态</h2>
 * 方块状态每多一个值，区块存储里这个方块的调色板就多一份，
 * 而且改状态要走 {@code level.setBlock}，会触发邻居更新和光照重算。
 * 尺寸只是个数字，没有"被红石或观察者读到"的需求，放 NBT 最省事。
 *
 * <h2>破坏时为什么要显式释放</h2>
 * 方块实体被移除时会调 {@code setRemoved}，但那个回调在<b>区块卸载</b>时也会触发。
 * 在那里释放会把强加载全关掉，导致区块一卸载加载器就自废。
 * 所以释放挂在 {@link Block#onRemove} 上，只在方块真的被换掉时执行。
 */
public class ChunkLoaderBlock extends Block implements EntityBlock {

    /** 是否正在强加载。仅用于切换贴图与发光。 */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public ChunkLoaderBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof ChunkLoaderBlockEntity be) {
            MedalsNetwork.sendToPlayer(serverPlayer,
                    new PacketChunkLoaderOpen(pos, be.size(), be.isActive()));
        }
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChunkLoaderBlockEntity(pos, state);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                         boolean isMoving) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof ChunkLoaderBlockEntity be) {
            be.onBlockedRemoved();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    /** 界面里改了开关之后，把方块状态同步过去，贴图才会跟着变。 */
    public static void syncActive(Level level, BlockPos pos, boolean active) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChunkLoaderBlock && state.hasProperty(ACTIVE)
                && state.getValue(ACTIVE) != active) {
            level.setBlock(pos, state.setValue(ACTIVE, active), 3);
        }
    }
}
