package com.lost_realm.util;

import java.util.List;
import java.util.function.Predicate;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.AbstractArrow;

/**
 * 「玩家刚刚射出的那支箭」的查找工具。
 *
 * <h2>为什么需要它</h2>
 * {@code ArrowLooseEvent} 由 Forge 注入在 {@code BowItem#releaseUsing} 里
 * {@code player.getProjectile(...)} 之后、{@code arrowitem.createArrow(...)} <b>之前</b>
 * —— 也就是说事件触发的那一刻，原版箭矢<b>还没有被创建</b>。
 * 任何想「以原箭为模板」的技能（三发、满蓄力标记…）都必须先延后到本 tick 的任务队列
 * （{@code ServerPlayer#server.execute}），等原版把箭 {@code addFreshEntity} 之后再取。
 *
 * <h2>怎么认出「刚射出的」</h2>
 * 任务队列在实体 tick 结束后执行，此时新箭的 {@code tickCount} 还极小（0~1），
 * 而场上已有的旧箭只会更大。因此用 {@link #MAX_TICK_COUNT} 卡一个「年轻」阈值，
 * 再配合「主人是本人」与「离玩家最近」两层筛选，就能稳定命中那一支。
 * 即便如此仍可能撞上玩家贴身连射，所以调用方还应自行追加过滤条件（见 {@link #findFresh}）。
 */
public final class ArrowFinder {

    /** 视作「刚射出」的最大 tickCount。 */
    private static final int MAX_TICK_COUNT = 4;

    /** 默认搜索半径（格）。原版箭出生在玩家眼高附近，1~2 格足够。 */
    public static final double DEFAULT_RADIUS = 4.0D;

    /**
     * 找出该玩家刚刚射出的箭。
     *
     * @param player 射手
     * @param filter 额外过滤条件（可为 {@code null}）
     * @return 命中的箭，找不到返回 {@code null}
     */
    public static AbstractArrow findFresh(ServerPlayer player, Predicate<AbstractArrow> filter) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        List<AbstractArrow> list = level.getEntitiesOfClass(AbstractArrow.class,
                player.getBoundingBox().inflate(DEFAULT_RADIUS),
                arrow -> arrow.getOwner() == player
                        && arrow.tickCount <= MAX_TICK_COUNT
                        && (filter == null || filter.test(arrow)));

        AbstractArrow best = null;
        double bestSqr = Double.MAX_VALUE;
        for (AbstractArrow arrow : list) {
            double sqr = arrow.distanceToSqr(player);
            if (sqr < bestSqr) {
                bestSqr = sqr;
                best = arrow;
            }
        }
        return best;
    }

    private ArrowFinder() {}
}
