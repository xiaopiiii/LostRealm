package com.lost_realm.event;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.lost_realm.Medals;
import com.lost_realm.config.MedalsConfig;

/**
 * 战士终极「我有一剑！」的那一剑 —— <b>一道巨大的剑气横扫而出</b>。
 *
 * <h2>一次斩击由三件事组成</h2>
 * <ol>
 *   <li><b>伤敌</b>：把玩家前方 {@code 长 × 宽 × 高} 的长方体范围里的敌人各打一次
 *       （每个都是整整一个 {@code sword_damage}，不随距离衰减 —— 这是爽点，
 *       不是平衡点）；</li>
 *   <li><b>断物</b>：同一个长方体里的方块尽数崩碎，<b>不产生掉落物</b>；
 *       硬度为负的方块（基岩 / 屏障 / 传送门框架）原样保留；</li>
 *   <li><b>剑气</b>：一道弧形巨刃从玩家身前推出，{@code sword_anim_ticks} tick
 *       后抵达射程尽头 —— 纯粹是视觉，但少了它，这一剑就只剩「方块凭空消失」。</li>
 * </ol>
 *
 * <h2>为什么判定用局部坐标而不是 AABB</h2>
 * 剑气的范围是一个<b>沿朝向躺着的长方体</b>。轴对齐包围盒要包住它，
 * 在玩家斜着挥剑时得比实际范围大出一大截（45° 时面积翻倍），
 * 于是「明明没站在剑气里却被斩中」。所以这里先把候选实体从一个大 AABB 里捞出来，
 * 再把它的位置投影到剑气的三个轴上（前 / 侧 / 高）逐个精确判定。
 *
 * <h2>为什么动画要单独维护一张表</h2>
 * 剑气飞行需要跨 tick 推进，而这一剑的结算在<b>一瞬间</b>就完成了。
 * 两者节奏不同，分开更清楚：{@link #perform} 一次算完伤害与方块，
 * {@link #ACTIVE} 里的记录只负责接下来十几 tick 把画面演完。
 */
@Mod.EventBusSubscriber(modid = Medals.MODID)
public final class SlashHandler {

    /** 一道正在飞行的剑气。 */
    private record Slash(ServerLevel level, Vec3 origin, Vec3 forward, Vec3 right,
                         double length, double halfWidth, double height,
                         long startTick, int duration, UUID owner) {}

    private static final List<Slash> ACTIVE = new ArrayList<>();

    /** 横向采样点数（单侧），决定月牙有多"圆"。 */
    private static final int ARC_SAMPLES = 7;

    /** 拖尾段数。越多越长，但粒子数按倍数涨。 */
    private static final int TRAIL_SEGMENTS = 3;

    // ================================================================
    // 一、斩
    // ================================================================

    /**
     * 斩出这一剑。只应在服务端调用。
     *
     * @param player 挥剑的人
     */
    public static void perform(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 forward = levelForward(player);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3 origin = player.position();

        double reach = Math.max(1.0D, MedalsConfig.SWORD_REACH.get());
        double halfWidth = Math.max(0.5D, MedalsConfig.SWORD_WIDTH.get() / 2.0D);
        double height = Math.max(1.0D, MedalsConfig.SWORD_HEIGHT.get());

        // ---- 1. 先伤敌。必须在断物之前：否则目标会先掉进坑里,判不到 ----
        for (LivingEntity target : collectTargets(level, player, origin, forward, right,
                reach, halfWidth, height)) {
            // 清掉无敌帧,否则同一只怪在短时间内第二次被斩会免疫
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().playerAttack(player), MedalsConfig.SWORD_DAMAGE.get());
        }

        // ---- 2. 再断物 ----
        if (MedalsConfig.SWORD_BREAK_BLOCKS.get()) {
            breakBlocks(level, player, origin, forward, right, reach, halfWidth, height);
        }

        // ---- 3. 剑气起飞 ----
        int duration = Math.max(1, MedalsConfig.SWORD_ANIM_TICKS.get());
        ACTIVE.add(new Slash(level, origin, forward, right, reach, halfWidth, height,
                level.getGameTime(), duration, player.getUUID()));

        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 2.5F, 0.55F);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP,
                SoundSource.PLAYERS, 1.2F, 0.6F);
    }

    /**
     * 剑气朝向：把视线方向<b>水平化</b>。
     *
     * <p>「横扫」的关键在于它是横着扫的 —— 玩家仰头看天时不该朝天捅一剑。
     * 所以这里丢掉视线的竖直分量，只保留朝向。
     *
     * <p>唯一的例外是眼睛与准星几乎垂直向下（玩家在俯视脚下）：此时水平分量
     * 趋近于零，归一化会炸，于是回退到玩家的朝向（{@code getYRot}）。
     */
    private static Vec3 levelForward(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        if (flat.lengthSqr() < 1.0E-6D) {
            float yaw = player.getYRot() * ((float) Math.PI / 180.0F);
            flat = new Vec3(-(double) net.minecraft.util.Mth.sin(yaw), 0.0D,
                    (double) net.minecraft.util.Mth.cos(yaw));
        }
        return flat.normalize();
    }

    /** 落在剑气长方体里的所有目标。 */
    private static List<LivingEntity> collectTargets(ServerLevel level, ServerPlayer player,
                                                     Vec3 origin, Vec3 forward, Vec3 right,
                                                     double reach, double halfWidth, double height) {
        boolean hitPlayers = MedalsConfig.SWORD_HIT_PLAYERS.get();
        AABB box = new AABB(origin, origin).inflate(reach, height + 2.0D, reach);
        List<LivingEntity> out = new ArrayList<>();

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (entity == player || entity.isSpectator() || !entity.isAlive()) {
                continue;
            }
            // 玩家默认不斩 —— 自己的队友、路过的邻居,一刀 5000 谁都受不了
            if (entity instanceof Player other) {
                if (!hitPlayers || other.isCreative() || player.isAlliedTo(other)) {
                    continue;
                }
            }
            Vec3 rel = entity.position().subtract(origin);
            double ahead = rel.dot(forward);
            double side = rel.dot(right);
            // 给实体自身体积留一点容差,否则"贴着剑气边缘站着"会判不中
            double pad = entity.getBbWidth() * 0.5D;
            if (ahead < -1.0D || ahead > reach + pad) {
                continue;
            }
            if (Math.abs(side) > halfWidth + pad) {
                continue;
            }
            if (rel.y < -1.2D || rel.y > height + 0.5D) {
                continue;
            }
            out.add(entity);
        }
        return out;
    }

    /**
     * 沿剑气路径清掉方块。
     *
     * <h2>用 setBlock 而不是 destroyBlock</h2>
     * 两个原因：其一，{@code destroyBlock} 会为每个方块播一遍破坏音效并喷粒子 ——
     * 一千八百个方块就是一千八百次音效，耳朵先炸；其二，
     * {@code destroyBlock(pos, false)} 只控制"不掉落"，而 {@code setBlock} 连
     * 容器掉落、冰融化这类副作用一并跳过，正好就是「不产生掉落物」。
     *
     * <h2>标志位为什么是 2 而不是 3</h2>
     * {@code 3 = 更新邻居 + 同步客户端}，看着更"物理"（沙会塌、水会流过来），
     * 但一次斩击要动近两千个方块，每次都去跑一遍邻居更新（红石信号、
     * 方块更新抑制器、流体扩散…）会明显卡一下。{@code 2 = 只同步客户端}
     * 把这批更新省掉，代价是悬空的沙砾不会当场落下 —— 换来的是按下去不卡。
     */
    private static void breakBlocks(ServerLevel level, ServerPlayer player, Vec3 origin,
                                    Vec3 forward, Vec3 right, double reach,
                                    double halfWidth, double height) {
        Set<Long> visited = new HashSet<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();

        for (double ahead = 0.0D; ahead <= reach; ahead += 0.5D) {
            Vec3 center = origin.add(forward.scale(ahead));
            for (double side = -halfWidth; side <= halfWidth; side += 0.5D) {
                for (double up = 0.0D; up <= height; up += 0.5D) {
                    double x = center.x + right.x * side;
                    double z = center.z + right.z * side;
                    cursor.set(x, center.y + up, z);

                    long key = cursor.asLong();
                    if (!visited.add(key)) {
                        continue;
                    }
                    if (!level.isLoaded(cursor)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) {
                        continue;
                    }
                    // 流体不动:否则在海上劈一剑会连整片水一起抽干
                    if (!state.getFluidState().isEmpty()) {
                        continue;
                    }
                    // ★ 硬度为负 = 不可破坏(基岩 / 屏障 / 传送门框架 / 命令方块…),原样留着
                    if (state.getDestroySpeed(level, cursor) < 0.0F) {
                        continue;
                    }
                    level.setBlock(cursor, air, 2);
                }
            }
        }
    }

    // ================================================================
    // 二、剑气飞行
    // ================================================================

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) {
            return;
        }
        if (!(event.level instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();

        Iterator<Slash> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Slash slash = it.next();
            if (slash.level() != level) {
                continue;
            }
            long elapsed = now - slash.startTick();
            if (elapsed > slash.duration()) {
                it.remove();
                continue;
            }
            drawBlade(slash, Math.min(1.0D, elapsed / (double) slash.duration()));
        }
    }

    /**
     * 画出某一帧的剑气。
     *
     * <p>形状是一道<b>弯月</b>：横向两端相对中心向后收（{@code curve}），
     * 于是从上方看是"（"而不是"|" —— 这才像刀锋划过空气留下的弧。
     * 再叠 {@link #TRAIL_SEGMENTS} 段逐渐变淡的拖尾，让 10 tick 的飞行不至于像瞬移。
     *
     * @param progress 0.0 = 刚挥出，1.0 = 抵达射程尽头
     */
    private static void drawBlade(Slash slash, double progress) {
        double head = slash.length() * progress;
        double step = Math.max(0.6D, slash.length() / (slash.duration() * 3.5D));

        for (int segment = 0; segment < TRAIL_SEGMENTS; segment++) {
            double back = head - segment * step * 2.0D;
            if (back < -2.0D) {
                continue;
            }
            boolean isEdge = segment == 0;

            for (int i = -ARC_SAMPLES; i <= ARC_SAMPLES; i++) {
                double side = slash.halfWidth() * i / (double) ARC_SAMPLES;
                // 两端向后弯,形成月牙
                double curve = -Math.abs(side) * Math.abs(side) / Math.max(1.0E-3D, slash.halfWidth()) * 0.45D;
                double ahead = back + curve;
                if (ahead < 0.0D || ahead > slash.length() + 1.5D) {
                    continue;
                }
                Vec3 pos = slash.origin()
                        .add(slash.forward().scale(ahead))
                        .add(slash.right().scale(side));

                for (double up = 0.25D; up <= slash.height(); up += Math.max(0.8D, slash.height() / 3.0D)) {
                    // 白弧:本体
                    slash.level().sendParticles(ParticleTypes.SWEEP_ATTACK,
                            pos.x, pos.y + up, pos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                    if (isEdge) {
                        // 刃口再补一道亮芯
                        slash.level().sendParticles(ParticleTypes.END_ROD,
                                pos.x, pos.y + up, pos.z, 1, 0.05D, 0.05D, 0.05D, 0.0D);
                    }
                }
            }
        }

        // 推进中的风声:每 3 tick 一次,避免连续音效糊成一团
        if (slash.level().getGameTime() % 3L == 0L) {
            Vec3 at = slash.origin().add(slash.forward().scale(head));
            slash.level().playSound(null, BlockPos.containing(at), SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.PLAYERS, 1.6F, 1.4F);
        }
    }

    private SlashHandler() {}
}
