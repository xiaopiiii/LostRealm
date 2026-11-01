package com.lost_realm.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * 竞技场构建的<b>兼容转发层</b>。
 *
 * <h2>为什么保留这个类</h2>
 * dv5 把建造逻辑整体迁到了 {@link LostCityBuilder}(遗落之都,半径 100)。
 * 但 {@code BossPortalHandler}、{@code WardenBoss}、技能 Goal 等
 * 多处仍引用 {@code ArenaBuilder.arenaCenter()},且语义上「城市中心」
 * 与「竞技场中心」是同一个坐标。
 *
 * <p>与其全仓替换、冒着漏改的风险,不如保留这个瘦转发类 ——
 * 它的所有方法都直接委托给 {@link LostCityBuilder},零逻辑、零状态。
 * 新代码请直接使用 {@link LostCityBuilder};本类只作过渡。
 *
 * <h2>已移除的能力</h2>
 * 旧的 {@code buildFloor / buildWalls / buildCentralPlatform / buildPillars /
 * buildAltars} 与 {@code ensureGenerated} 已全部迁走 ——
 * 需要建造请调用 {@link LostCityBuilder#ensureGenerated(ServerLevel)}。
 */
public final class ArenaBuilder {

    /** 遗落之都中心(委托给 {@link LostCityBuilder#arenaCenter()})。 */
    public static BlockPos arenaCenter() {
        return LostCityBuilder.arenaCenter();
    }

    /** 确保遗落之都已生成(幂等),委托给 {@link LostCityBuilder}。 */
    public static boolean ensureGenerated(ServerLevel realm) {
        return LostCityBuilder.ensureGenerated(realm);
    }

    /** 开一道入口传送门,委托给 {@link LostCityBuilder}。 */
    public static void placePortal(ServerLevel level, BlockPos bottomLeft, Direction.Axis axis) {
        LostCityBuilder.placePortal(level, bottomLeft, axis);
    }

    /** 找一块可放门的空地,委托给 {@link LostCityBuilder}。 */
    public static BlockPos findPortalSpot(ServerLevel level, BlockPos near) {
        return LostCityBuilder.findPortalSpot(level, near);
    }

    private ArenaBuilder() {}
}
