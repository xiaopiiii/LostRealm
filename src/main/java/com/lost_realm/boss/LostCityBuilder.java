package com.lost_realm.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import com.lost_realm.boss.block.RealmPortalBlock;

/**
 * 遗落境建筑入口 —— 现在只做两件事：<b>转调竞技场建造</b>与<b>传送门</b>。
 *
 * <h2>为什么这个类还叫 LostCityBuilder</h2>
 * dv5 时这里建的是「遗落之都」：广场、露台、角塔、柱廊、四座装饰祭坛。
 * beta25 按玩家要求整体重做成「终末竞技场」—— 中央一座祭坛，
 * 十二根巨柱围成一圈，中间全部空出来。
 *
 * <p>类名没改，因为 {@code BossPortalHandler}、旧 Boss 的技能 Goal、
 * 存档里可能存在的调用点都还引用它。改名收益为零，风险却是漏改一处就崩。
 * 真正的建造逻辑全部在 {@link UdunosArenaBuilder}。
 */
public final class LostCityBuilder {

    /** 竞技场中心。 */
    public static BlockPos arenaCenter() {
        return new BlockPos(BossRegistry.ARENA_CENTER_X,
                BossRegistry.ARENA_CENTER_Y,
                BossRegistry.ARENA_CENTER_Z);
    }

    /**
     * 确保竞技场已生成（幂等）。
     *
     * @return 本次调用是否真的执行了生成
     */
    public static boolean ensureGenerated(ServerLevel level) {
        return UdunosArenaBuilder.ensureGenerated(level);
    }

    // ================================================================
    // 传送门
    // ================================================================

    /**
     * 在指定位置开一道<b>入口传送门</b>（玩家搭好框架点燃后调用）。
     *
     * <p>框架为中空矩形：外框 4×5，内部 2×3 的门面。
     * 与下界门一样支持沿 X 或 Z 轴展开。
     */
    public static void placePortal(ServerLevel level, BlockPos bottomLeft, Direction.Axis axis) {
        BlockState portal = BossBlocks.REALM_PORTAL.get().defaultBlockState()
                .setValue(RealmPortalBlock.AXIS, axis);
        for (int h = 0; h < 3; h++) {
            for (int w = 0; w < 2; w++) {
                BlockPos p = axis == Direction.Axis.X
                        ? bottomLeft.offset(w, h, 0)
                        : bottomLeft.offset(0, h, w);
                level.setBlockAndUpdate(p, portal);
            }
        }
    }

    /** 找一块可放门的空地（用于自动化测试或命令调用）。 */
    public static BlockPos findPortalSpot(ServerLevel level, BlockPos near) {
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near);
    }

    /** 玩家从传送门进来的落点（外墙内侧，正对中央祭坛）。 */
    public static BlockPos entrySpot() {
        return UdunosArenaBuilder.entrySpot();
    }

    /** 判定某坐标是否在竞技场范围内（技能 / 波次判定用）。 */
    public static boolean insideCity(double x, double z) {
        double dx = x - (BossRegistry.ARENA_CENTER_X + 0.5D);
        double dz = z - (BossRegistry.ARENA_CENTER_Z + 0.5D);
        return Mth.sqrt((float) (dx * dx + dz * dz)) <= BossRegistry.ARENA_RADIUS;
    }

    private LostCityBuilder() {}
}
