package com.lost_realm.boss.udunos.move;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.lost_realm.boss.udunos.UdunosBolt;
import com.lost_realm.boss.udunos.UdunosBoss;
import com.lost_realm.boss.udunos.UdunosFx;
import com.lost_realm.boss.udunos.UdunosPhase;
import com.lost_realm.boss.udunos.UdunosTuning;

/**
 * <b>星弧巡猎</b> —— 第一阶段三招里唯一"有延迟"的一招。
 *
 * <h2>它教的是另一件事</h2>
 * 碎星播撒教玩家读红线，噬光冲锋教玩家横向拉开，而这一招教的是
 * <b>"记住刚才那里有什么"</b>：种子先飘过去、静静停住、蓄力一秒才炸。
 * 玩家真正要躲的不是炸弹，是<b>自己上一秒站的位置</b>。
 *
 * <h2>流程</h2>
 * <pre>
 *   从体节随机位置射出 2~3 颗种子（速度自适应，约 1.5 秒后抵达玩家附近）
 *      ↓ 停在半空
 *   蓄力光环收拢 1 秒（越接近爆散，环越紧、粒子越密）
 *      ↓
 *   向四面八方爆散 14 发弹幕（带随机抖动 + 上下散布）
 *      ↓ 间隔 3.5 秒
 *   下一轮 …… 共 3~4 轮
 * </pre>
 *
 * <h2>三个细节</h2>
 * <ol>
 *   <li><b>种子速度是算出来的，不是常量。</b>固定速度的话，"距离玩家 10 格"
 *       和"距离 40 格"射出的种子会停在完全不同的地方 —— 前者炸在脸上，
 *       后者在半空中自爆给玩家看。所以按距离反推速度，保证它总是停在玩家附近。</li>
 *   <li><b>爆散方向带随机抖动。</b>完全均匀的一圈会让玩家背出"安全角"；
 *       抖动让每次缺口位置都不同，只能临场看。</li>
 *   <li><b>上下也要撒开。</b>只在水平面爆散的话，玩家往上一跳就躲完了 ——
 *       MC 是 3D，这条得用上。</li>
 * </ol>
 */
public final class MoveStarHunt extends UdunosMove {

    /** 两轮之间的间隔（tick）。要足够长，让上一轮的弹幕先飞离原地（原设计要求）。 */
    private static final int ROUND_GAP = 70;

    /** 种子应停在距玩家多近的地方（用于反推速度）。 */
    private static final double SEED_STANDOFF = 4.0D;

    private int rounds;
    private int roundIndex;
    private int timer;
    private double orbitDir;

    public MoveStarHunt(UdunosBoss boss) {
        super(boss);
    }

    @Override
    public MoveKind kind() {
        return MoveKind.HUNT;
    }

    @Override
    public String id() {
        return "hunt";
    }

    @Override
    public String langKey() {
        return "move.lost_realm.udunos.hunt";
    }

    @Override
    public boolean availableIn(UdunosPhase phase) {
        // 设计稿：「第二阶段加入星门贯穿、引力残星，<b>继续使用冲锋与星弧巡猎</b>」
        // —— 所以阶段Ⅱ 也要保留这一招。
        // 旧写法 phase == ONE 让阶段Ⅱ只剩 3 招（冲锋/星门/引力），
        // 而调度类的注释自己写的是「阶段二：4 种」，代码和注释对不上。
        return phase == UdunosPhase.ONE || phase == UdunosPhase.TWO;
    }

    @Override
    public void begin(@Nullable Player target) {
        this.rounds = between(UdunosTuning.HUNT_ROUNDS_MIN, UdunosTuning.HUNT_ROUNDS_MAX);
        this.roundIndex = 0;
        // 置成满值，于是"第一轮"在下一 tick 立刻触发，不用干等
        this.timer = ROUND_GAP;
        this.orbitDir = this.boss.getRandom().nextBoolean() ? 1.0D : -1.0D;
    }

    @Override
    public Vec3 tick(@Nullable Player target) {
        this.ticks++;
        this.timer++;

        tickHeadMotion(target);

        this.boss.syncMove(kind());
        this.boss.syncAim(this.velocity.lengthSqr() > 1.0E-6D
                ? this.velocity.normalize() : new Vec3(0.0D, 0.0D, 1.0D));
        this.boss.syncWarning(0);

        if (this.roundIndex >= this.rounds) {
            finish();
            return this.velocity;
        }

        if (this.timer >= ROUND_GAP) {
            this.timer = 0;
            spawnSeeds(target);
            this.roundIndex++;
        }
        return this.velocity;
    }

    /**
     * 头部动作：绕着玩家做<b>切向绕行</b>，距离太近时才拉开。
     *
     * <p>为什么不直接"远离玩家"：那样它会一路飞走，几轮之后就到视距外了，
     * 玩家看着一条虫慢慢退场，压迫感全无。切向绕行让它始终保持在视野内
     * 打转 —— 与巡游的"不规则随机移动"是两种不同的味道，这里是刻意的规律感，
     * 因为这一招的重点在种子那边，头部该是背景。
     */
    private void tickHeadMotion(@Nullable Player target) {
        if (target == null) {
            this.velocity = this.velocity.scale(0.92D);
            return;
        }
        double speed = UdunosTuning.CRUISE_SPEED * 0.85D;
        double distance = distanceTo(target);

        Vec3 fromPlayer = this.boss.position().subtract(target.position());
        if (fromPlayer.lengthSqr() < 1.0E-6D) {
            fromPlayer = new Vec3(1.0D, 0.0D, 0.0D);
        }
        fromPlayer = fromPlayer.normalize();

        Vec3 desired;
        if (distance < 22.0D) {
            // 太近了：先拉开
            desired = fromPlayer.scale(speed);
        } else {
            // 切向绕行（水平面），再带一点点向内的分量防止越飘越远
            Vec3 tangent = new Vec3(-fromPlayer.z, 0.0D, fromPlayer.x)
                    .normalize().scale(this.orbitDir);
            desired = tangent.add(fromPlayer.scale(-0.18D)).normalize().scale(speed);
        }
        this.velocity = turnTowards(this.velocity, desired,
                UdunosTuning.CRUISE_TURN, speed * 0.6D);
    }

    /** 射出一轮种子。 */
    private void spawnSeeds(@Nullable Player target) {
        if (!(this.boss.level() instanceof ServerLevel level)) {
            return;
        }
        // 种子的"真实开销"是它们爆散后的那一片 —— 按爆散量预留预算
        if (boltBudgetLeft() < UdunosTuning.HUNT_BURST_COUNT) {
            return;
        }
        int count = between(UdunosTuning.HUNT_SEED_MIN, UdunosTuning.HUNT_SEED_MAX);
        var random = this.boss.getRandom();

        for (int i = 0; i < count; i++) {
            int segment = random.nextInt(UdunosTuning.SEGMENTS);
            Vec3 origin = this.boss.segmentPosition(segment);

            Vec3 dir;
            double speed;
            if (target != null) {
                Vec3 center = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
                Vec3 to = center.subtract(origin);
                double distance = to.length();
                if (distance < 1.0E-3D) {
                    continue;
                }
                // 偏移一下，让几颗种子不都落在同一个点
                Vec3 jitter = new Vec3(
                        random.nextDouble() - 0.5D,
                        random.nextDouble() - 0.35D,
                        random.nextDouble() - 0.5D).scale(0.5D);
                dir = to.normalize().add(jitter).normalize();
                // 反推速度：飞满这么多 tick 后正好停在玩家附近
                double want = Math.max(1.0D, distance - SEED_STANDOFF);
                speed = Mth.clamp(want / UdunosTuning.HUNT_SEED_FLIGHT, 0.30D, 0.95D);
            } else {
                dir = new Vec3(random.nextDouble() - 0.5D,
                        random.nextDouble() - 0.2D,
                        random.nextDouble() - 0.5D).normalize();
                speed = UdunosTuning.HUNT_SEED_SPEED;
            }

            UdunosBolt seed = UdunosBolt.seed(level, this.boss, origin, dir, speed,
                    UdunosTuning.HUNT_BURST_COUNT,
                    UdunosTuning.HUNT_BURST_SPEED,
                    UdunosTuning.HUNT_BURST_LIFE);
            level.addFreshEntity(seed);
        }
        UdunosFx.playShot(level, this.boss.position());
    }

    private int between(int min, int max) {
        if (max <= min) {
            return Math.max(1, min);
        }
        return min + this.boss.getRandom().nextInt(max - min + 1);
    }
}
