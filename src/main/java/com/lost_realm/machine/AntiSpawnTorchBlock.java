package com.lost_realm.machine;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * <b>镇魔火把</b> —— 放下后，以它为中心 64×64 方块范围内不再自然刷怪。
 *
 * <p>它继承普通方块而不是 {@code TorchBlock}：原版火把带一整套"必须附着在
 * 完整方块顶/侧"的状态机（{@code StandingAndWallBlockItem}、六个方向的 shape、
 * 掉落判定），这些对一支纯功能性的火把都是负担，而且会让放置手感变得挑剔 ——
 * 你想把它插在墙角，它说不行。
 *
 * <p>所以这里只要三样：火把的外形（矮柱子）、14 级光照、一个方块实体。
 * 贴图用原版 {@code template_torch} 模型套自己的材质，视觉上仍然是火把。
 */
public class AntiSpawnTorchBlock extends Block implements EntityBlock {

    private static final VoxelShape SHAPE = Block.box(6.0D, 0.0D, 6.0D, 10.0D, 10.0D, 10.0D);

    public AntiSpawnTorchBlock(Properties props) {
        super(props);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext ctx) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AntiSpawnTorchBlockEntity(pos, state);
    }
}
