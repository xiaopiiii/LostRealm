package com.lost_realm.boss.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.lost_realm.boss.BossPortalHandler;

/**
 * 遗落境传送门方块。
 *
 * <h2>⚠️ 为什么不能实现 {@code Portal} 接口</h2>
 * MC 1.20.1 <b>没有</b> {@code net.minecraft.world.level.block.Portal} 接口 ——
 * 那是 1.20.5 才引入的抽象(下界门/末地门在 1.20.1 里各自硬编码)。
 * 因此本类继承普通 {@link Block},在 {@link #entityInside} 中
 * 自行发起跨维度传送。
 *
 * <h2>传送方式</h2>
 * 走 {@code Entity#changeDimension(ServerLevel, ITeleporter)} ——
 * 这是 1.20.1 唯一稳定的跨维度 API,Forge 环境下等价于原版行为但
 * 允许我们自定义落点({@link BossPortalHandler} 提供 ITeleporter 实现)。
 *
 * <h2>为什么继承 Block 而不是 NetherPortalBlock</h2>
 * 下界门方块把目标维度硬编码为 {@code Level.NETHER},
 * 且 {@code updateShape} 依赖 {@code PortalShape} 的下界框架判定 ——
 * 无法复用。继承普通方块可完全掌控"站进去就传送"的语义。
 */
public class RealmPortalBlock extends Block {

    /** 传送门朝向:沿 X 轴或 Z 轴展开。与下界门保持一致。 */
    public static final EnumProperty<Direction.Axis> AXIS =
            BlockStateProperties.HORIZONTAL_AXIS;

    protected static final VoxelShape X_SHAPE =
            Block.box(0.0D, 0.0D, 6.0D, 16.0D, 16.0D, 10.0D);
    protected static final VoxelShape Z_SHAPE =
            Block.box(6.0D, 0.0D, 0.0D, 10.0D, 16.0D, 16.0D);

    public RealmPortalBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext ctx) {
        return state.getValue(AXIS) == Direction.Axis.Z ? Z_SHAPE : X_SHAPE;
    }

    /**
     * 实体进入传送门 → 发起传送。
     *
     * <p>判定条件与原版一致:排除乘客与载具(否则会出现载具分离问题)。
     * 冷却由 {@code Entity#portalCooldown} 维护,见
     * {@link BossPortalHandler#tryTeleport}。
     */
    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level instanceof ServerLevel server
                && !entity.isPassenger()
                && !entity.isVehicle()
                && entity.canChangeDimensions()) {
            BossPortalHandler.tryTeleport(server, entity);
        }
    }

    /**
     * 邻居变化时检查框架是否完整 —— 不完整则消解门面。
     *
     * <p>与下界门行为一致:挖掉框架,门面自动消失,避免"悬空门"。
     * <b>注意</b>:这里刻意<b>不</b>调用 {@code super.updateShape} 的
     * 下界门逻辑,只做自己的框架检查。
     */
    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!BossPortalHandler.isFrameworkIntact(level, pos, state.getValue(AXIS))) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    /** 传送门的粒子效果 —— 灵魂火粒子呼应「遗落境」的设定。 */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(100) == 0) {
            level.playLocalSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS,
                    0.5F, random.nextFloat() * 0.4F + 0.8F, false);
        }
        for (int i = 0; i < 4; i++) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + random.nextDouble();
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z,
                    (random.nextDouble() - 0.5D) * 0.5D,
                    (random.nextDouble() - 0.5D) * 0.5D,
                    (random.nextDouble() - 0.5D) * 0.5D);
        }
    }

    /** 传送门方块不可拾取(与原版下界门一致)。 */
    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }
}
