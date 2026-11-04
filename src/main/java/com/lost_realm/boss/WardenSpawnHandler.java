package com.lost_realm.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.lost_realm.Medals;
import com.lost_realm.boss.entity.WardenBoss;
import com.lost_realm.boss.entity.WardenSentinel;

/**
 * 守望者仆从的<b>生成与战斗联动</b>。
 *
 * <h2>仆从什么时候出现</h2>
 * 需求是"重装版"整合包 Boss,仆从承担两个职责:
 * <ol>
 *   <li><b>任务链④的产出源</b> —— 击败仆从掉落「祭坛激活核心」,
 *       是合成召唤符的必要材料。这逼玩家在进竞技场之前先打一场小规模战斗。</li>
 *   <li><b>Boss 战的干扰项</b> —— 阶段Ⅱ起,Boss 每次释放技能会召唤 1 个仆从,
 *       让战斗从"打单体"变成"处理场面",避免玩家靠站桩输出磨死 Boss。</li>
 * </ol>
 *
 * <h2>为什么仆从生成要限制数量</h2>
 * 若无上限,长时间拉锯战会让仆从无限累积,最终变成"打仆从模拟器"。
 * {@link #MAX_SENTINELS} 设了硬上限,并优先让 Boss 侧的召唤逻辑
 * 在接近上限时跳过 —— 这是"有节奏的压迫"而非"无限堆怪"。
 */
@Mod.EventBusSubscriber(modid = Medals.MODID)
public final class WardenSpawnHandler {

    /** 竞技场内同时存在的仆从上限(默认值)。 */
    public static final int MAX_SENTINELS = 6;

    /** 仆从数量上限(旧版固定值,beta41 起不再读配置)。 */
    public static int maxSentinels() {
        return MAX_SENTINELS;
    }

    /**
     * 在竞技场内生成一个仆从(供 {@link WardenBoss} 的技能调用)。
     *
     * @param level   遗落境维度
     * @param around  生成点参照(通常是 Boss 或玩家位置)
     * @return 是否成功生成(已达上限或找不到落点则返回 false)
     */
    public static boolean spawnSentinel(ServerLevel level, BlockPos around) {
        if (countSentinel(level) >= maxSentinels()) {
            return false;
        }
        WardenSentinel sentinel = BossEntities.SENTINEL.get().create(level);
        if (sentinel == null) {
            return false;
        }
        BlockPos spot = findSpawnSpot(level, around);
        if (spot == null) {
            return false;
        }
        sentinel.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        sentinel.finalizeSpawn(level, level.getCurrentDifficultyAt(spot),
                MobSpawnType.MOB_SUMMONED, null, null);
        level.addFreshEntity(sentinel);

        // 生成演出:与 Boss 的召唤主题呼应
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
                spot.getX() + 0.5D, spot.getY() + 1.0D, spot.getZ() + 0.5D,
                30, 0.6D, 0.8D, 0.6D, 0.05D);
        level.playSound(null, spot, net.minecraft.sounds.SoundEvents.SCULK_SHRIEKER_SHRIEK,
                net.minecraft.sounds.SoundSource.HOSTILE, 1.2F, 1.4F);
        return true;
    }

    /**
     * 统计当前维度内的仆从数量。
     *
     * <p>用 {@code level.getEntitiesOfClass} 而不是自维护计数器:
     * 计数器会在实体被卸载、死亡、清除时失同步,
     * 而实体查询天然反映真实状态,且仆从数量极少(≤6),开销可忽略。
     */
    public static int countSentinel(ServerLevel level) {
        return level.getEntitiesOfClass(WardenSentinel.class,
                net.minecraft.world.phys.AABB.ofSize(
                        net.minecraft.world.phys.Vec3.atCenterOf(ArenaBuilder.arenaCenter()),
                        BossRegistry.ARENA_RADIUS * 2.0D + 16.0D,
                        256.0D,
                        BossRegistry.ARENA_RADIUS * 2.0D + 16.0D)).size();
    }

    /**
     * 在参照点周围找一个可站立的落点。
     *
     * <p>从参照点起,按 8 个方向 × 半径 3~6 螺旋探测,
     * 找到第一个「脚下是实体方块 且 自身两格为空」的位置。
     * 找不到则返回 {@code null}(调用方跳过本次生成,不会引发异常)。
     *
     * <p>dv5 起由 {@link WaveManager} 复用 —— 因此提升为 public。
     */
    public static BlockPos findSpawnSpot(ServerLevel level, BlockPos around) {
        for (int r = 3; r <= 6; r++) {
            for (int i = 0; i < 8; i++) {
                double angle = (Math.PI / 4.0D) * i;
                int dx = (int) Math.round(Math.cos(angle) * r);
                int dz = (int) Math.round(Math.sin(angle) * r);
                BlockPos p = around.offset(dx, 0, dz);
                if (isStandable(level, p)) {
                    return p;
                }
                // 上下各试 2 格,适应台阶地形
                for (int dy = 1; dy <= 2; dy++) {
                    if (isStandable(level, p.above(dy))) {
                        return p.above(dy);
                    }
                    if (isStandable(level, p.below(dy))) {
                        return p.below(dy);
                    }
                }
            }
        }
        return null;
    }

    private static boolean isStandable(ServerLevel level, BlockPos p) {
        if (p.getY() < level.getMinBuildHeight() + 2 || p.getY() > level.getMaxBuildHeight() - 3) {
            return false;
        }
        boolean feet = level.getBlockState(p).getCollisionShape(level, p).isEmpty();
        boolean head = level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty();
        boolean ground = !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty();
        return feet && head && ground;
    }

    // ================================================================
    // 死亡联动:仆从全灭推动任务链
    // ================================================================

    /**
     * 仆从死亡时播报剩余数量。
     *
     * <p>用途:任务链④要求击杀 3 个仆从,而玩家一次进竞技场可能只遇到一两个
     * (上限 6 但实际由 Boss 技能逐步召唤)。播报让玩家知道"还差几个",
     * 而不是盲目地站在原地等刷新。
     */
    @SubscribeEvent
    public static void onSentinelDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof WardenSentinel sentinel)) {
            return;
        }
        if (!(sentinel.level() instanceof ServerLevel level)) {
            return;
        }
        int remaining = countSentinel(level);
        // 只有参战者收到播报,避免无关玩家被打扰
        WardenFightSession session = WardenFightManager.session(level);
        if (session == null || session.isFinished()) {
            return;
        }
        for (java.util.UUID id : session.participants()) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                p.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.lost_realm.sentinel.down", remaining), true);
            }
        }
    }

    private WardenSpawnHandler() {}
}
