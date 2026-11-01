package com.lost_realm.boss.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

import com.lost_realm.boss.BossRegistry;

/**
 * <b>遗落祭坛</b> —— 终末竞技场的核心方块，也是整场召唤仪式的开关。
 *
 * <h2>一个方块，两种身份</h2>
 * 用 {@link #CORE} 区分：
 * <ul>
 *   <li><b>核心祭坛</b>（{@code core=true}）—— 竞技场正中央那一座，唯一可以右键的。
 *       右键后开始 20 秒仪式，结束时把厄瑞玻斯召唤出来。</li>
 *   <li><b>柱顶祭坛</b>（{@code core=false}）—— 十二根巨柱顶端各一座，
 *       是仪式的「共鸣节点」。右键只会提示你去中央；仪式期间它们会持续向上喷粒子。</li>
 * </ul>
 *
 * <h2>为什么柱顶祭坛不各自独立触发</h2>
 * 如果十二座都能右键，玩家会以为是「集齐十二座」的玩法，而实际设计是
 * 「一座主祭坛 + 十二个共鸣点」。让柱顶只作提示，语义最清楚。
 *
 * <h2>仪式为什么放在方块实体里</h2>
 * 20 秒的进度必须能跨 tick 保存，而且中途卸载区块后再加载应该继续 ——
 * 这两条都要求状态挂在 {@link BlockEntity} 上，方块状态只用来控制贴图发光。
 */
public class LostAltarBlock extends Block implements EntityBlock {

    /** 是否是中央核心祭坛。 */
    public static final BooleanProperty CORE = BooleanProperty.create("core");

    /** 仪式是否正在进行（仅视觉：贴图发光 + 亮度变化）。 */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    /** 核心祭坛：整块。 */
    private static final VoxelShape SHAPE_CORE = Shapes.or(
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D),
            Block.box(1.0D, 4.0D, 1.0D, 15.0D, 12.0D, 15.0D),
            Block.box(3.0D, 12.0D, 3.0D, 13.0D, 16.0D, 13.0D));

    /** 柱顶祭坛：矮一截，看起来是「摆在柱头上的石台」。 */
    private static final VoxelShape SHAPE_SMALL = Shapes.or(
            Block.box(1.0D, 0.0D, 1.0D, 15.0D, 3.0D, 15.0D),
            Block.box(2.0D, 3.0D, 2.0D, 14.0D, 10.0D, 14.0D),
            Block.box(4.0D, 10.0D, 4.0D, 12.0D, 12.0D, 12.0D));

    public LostAltarBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any()
                .setValue(CORE, false)
                .setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CORE, ACTIVE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(CORE) ? SHAPE_CORE : SHAPE_SMALL;
    }

    // ================================================================
    // 右键
    // ================================================================

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server)) {
            // 客户端只负责挥手；真正的判定全在服务端
            return InteractionResult.SUCCESS;
        }

        if (!state.getValue(CORE)) {
            player.displayClientMessage(
                    Component.translatable("message.lost_realm.altar.node"), true);
            return InteractionResult.CONSUME;
        }

        BlockEntity be = server.getBlockEntity(pos);
        if (!(be instanceof LostAltarBlockEntity altar)) {
            return InteractionResult.PASS;
        }

        // ---- 仪式进行中 ----
        if (altar.isRitualRunning()) {
            int left = altar.ritualTicksLeft();
            player.displayClientMessage(
                    Component.translatable("message.lost_realm.altar.in_progress",
                            (left + 19) / 20), true);
            return InteractionResult.CONSUME;
        }

        // ---- 已经有它了 ----
        if (altar.bossAlreadyPresent(server)) {
            player.displayClientMessage(
                    Component.translatable("message.lost_realm.erebos.already_present"), true);
            server.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.get(),
                    SoundSource.BLOCKS, 1.0F, 0.5F);
            return InteractionResult.CONSUME;
        }

        // ---- 开始 ----
        altar.startRitual(player);
        server.setBlock(pos, state.setValue(ACTIVE, true), 3);

        server.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE,
                SoundSource.BLOCKS, 4.0F, 0.5F);
        server.playSound(null, pos, SoundEvents.BEACON_ACTIVATE,
                SoundSource.BLOCKS, 3.0F, 0.45F);
        player.displayClientMessage(
                Component.translatable("message.lost_realm.altar.begin",
                        BossRegistry.RITUAL_TICKS / 20), false);
        return InteractionResult.CONSUME;
    }

    // ================================================================
    // 方块实体
    // ================================================================

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // 只有核心祭坛需要 tick；柱顶祭坛返回 null，省掉 12 个空方块实体的开销
        return state.getValue(CORE) ? new LostAltarBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide || !state.getValue(CORE)) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof LostAltarBlockEntity altar) {
                altar.serverTick();
            }
        };
    }
}
