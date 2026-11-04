package com.lost_realm.boss.udunos.move;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.lost_realm.boss.udunos.UdunosBolt;
import com.lost_realm.boss.udunos.UdunosBoss;
import com.lost_realm.boss.udunos.UdunosEffect;
import com.lost_realm.boss.udunos.UdunosFx;
import com.lost_realm.boss.udunos.UdunosPhase;
import com.lost_realm.boss.udunos.UdunosTuning;

/**
 * <b>囚星天环</b> —— 第三阶段的围圈招，第一个真正"把你关起来"的招式。
 *
 * <h2>限制圈不是惩罚，是舞台</h2>
 * 原设计特意写清楚："限制圈没有伤害、持续扣血或激怒惩罚。"
 * 它的唯一作用是<b>把可躲范围收窄</b>，让原本"跑远点就行"的弹幕
 * 突然变得必须认真处理。所以这里只做位置纠正，一点伤害都不给。
 *
 * <h2>流程（beta31 起真的分两段了）</h2>
 * <pre>
 *   ① 环布：在你脚下立起限制圈，虫身用 60 tick（≈3 秒）
 *      以 3.43 格/tick 沿圈掠过，把 50 节身体逐节铺到圈上 → 过程可见
 *      ↓（此段不开火、不压接触伤害，属于演出）
 *   ② 围成后：贴着圈持续盘旋，共四波
 *      每波从圆周上生成弹幕，朝圈内飘去，留一个通道
 *      ↓ 最后一波放完收招
 * </pre>
 *
 * <h2>beta30 及以前是"瞬间成形"，原因有三</h2>
 * <ol>
 *   <li>轨道半径 32×0.72 = 23 格，限制圈却在 32 格 —— 身体缩在圈内 9 格，
 *       玩家看到的是"一圈墙 + 里面一条虫转圈"，而不是"虫把场地围起来"；</li>
 *   <li>周长 2π×23 ≈ 145 格 &lt; 身体 206 格，缠 1.4 圈必然自我压叠；</li>
 *   <li>用的是巡航速度 1.44 格/tick，走完 206 格要 143 tick（7.2 秒），
 *       而且转向限幅 0.07 跟不上 0.107 的角速度，绕出来的是螺旋不是圆。</li>
 * </ol>
 * 现在半径与圈同值（周长 201 格 ≈ 身体 1.02 圈，正好贴成一整圈），
 * 环布阶段单独给速度和转向限幅，三条一起解决。
 *
 * <h2>为什么这一招让星核暴露</h2>
 * 围圈时虫身是"摊开"的，物理上没有藏住核心的余地。这给了第三阶段
 * 一个明确的节奏反转：<b>看到圈立起来，就该停止逃跑、转为输出</b>。
 */
public final class MoveStarRing extends UdunosMove {

    private int wavesLeft;

    /** 围好之后的波次计时，环布阶段不累加。 */
    private int timer;

    /** 环布已进行的 tick。 */
    private int wrapTicks;

    private Vec3 center = Vec3.ZERO;
    private double orbitDir = 1.0D;

    @Nullable
    private UdunosEffect ring;

    public MoveStarRing(UdunosBoss boss) {
        super(boss);
    }

    @Override
    public MoveKind kind() {
        return MoveKind.RING;
    }

    @Override
    public String id() {
        return "ring";
    }

    @Override
    public String langKey() {
        return "move.lost_realm.udunos.ring";
    }

    /** 第三阶段的核心招式之一。 */
    @Override
    public boolean availableIn(UdunosPhase phase) {
        return phase == UdunosPhase.THREE;
    }

    @Override
    public boolean exposesCore() {
        return true;
    }

    /** 环布期间是演出，不压接触伤害 —— 原设计「准备期间保护玩家」。 */
    @Override
    public boolean suppressesContact() {
        return isWrapping();
    }

    /** 还在把身体铺上圈的那一段。 */
    private boolean isWrapping() {
        return this.wrapTicks < UdunosTuning.RING_WRAP_TICKS;
    }

    @Override
    public void begin(@Nullable Player target) {
        this.wavesLeft = UdunosTuning.RING_WAVES;
        this.timer = 0;
        this.wrapTicks = 0;
        this.orbitDir = this.boss.getRandom().nextBoolean() ? 1.0D : -1.0D;

        Vec3 anchor = target != null ? target.position() : this.boss.position();
        // 原设计："在玩家当时所在位置建立固定世界坐标的限制圈" —— 落地即钉死
        this.center = new Vec3(anchor.x, anchor.y, anchor.z);

        if (this.boss.level() instanceof ServerLevel level) {
            this.ring = UdunosEffect.ring(level, this.boss, this.center, UdunosTuning.ARENA_RADIUS);
            level.addFreshEntity(this.ring);
            UdunosFx.playPhaseShift(level, this.center);
        }
    }

    @Override
    public Vec3 tick(@Nullable Player target) {
        this.ticks++;

        boolean wrapping = isWrapping();
        if (wrapping) {
            this.wrapTicks++;
        }

        // 环布段：快、允许大转角，把身体铺上去
        // 围成后：回到巡航速度，贴着圈慢慢转
        double speed = wrapping ? UdunosTuning.RING_WRAP_SPEED : UdunosTuning.CRUISE_SPEED * 1.15D;
        double maxTurn = wrapping ? UdunosTuning.RING_WRAP_TURN : UdunosTuning.CRUISE_TURN;
        this.velocity = orbitTowards(this.center, UdunosTuning.RING_ORBIT_RADIUS,
                speed, this.orbitDir, maxTurn);

        this.boss.syncMove(kind());
        this.boss.syncAim(this.velocity.lengthSqr() > 1.0E-6D
                ? this.velocity.normalize() : new Vec3(0, 0, 1));
        this.boss.syncWarning(0);

        // 环布阶段不开火 —— 一边铺身体一边撒弹幕，玩家只会觉得"莫名其妙就挨打"
        if (wrapping) {
            return this.velocity;
        }

        this.timer++;
        if (this.wavesLeft > 0 && this.timer >= UdunosTuning.RING_WAVE_INTERVAL) {
            this.timer = 0;
            this.wavesLeft--;
            spawnWave();
        }
        if (this.wavesLeft <= 0 && this.timer >= UdunosTuning.RING_WAVE_INTERVAL) {
            finish();
        }
        return this.velocity;
    }

    /** 从圆周上生成一波星弹，朝圈内飘去，留一个通道。 */
    private void spawnWave() {
        if (!(this.boss.level() instanceof ServerLevel level)) {
            return;
        }
        if (boltBudgetLeft() < UdunosTuning.RING_BOLTS) {
            return;
        }
        int count = UdunosTuning.RING_BOLTS;
        var random = this.boss.getRandom();
        double base = random.nextDouble() * Math.PI * 2.0D;
        // 有 1/4 的概率把通道开得宽一点 —— 原设计说"留出通道"，但没说多宽，
        // 偶尔放宽能让这一招不至于每次都一模一样
        int gap = random.nextInt(4) == 0 ? 2 : 1;

        Vec3 core = this.center;
        for (int i = 0; i < count; i++) {
            if (i < gap) {
                continue;   // 缺口
            }
            double angle = base + Math.PI * 2.0D * i / count;
            Vec3 pos = core.add(Math.cos(angle) * UdunosTuning.RING_SPAWN_RADIUS,
                    2.5D, Math.sin(angle) * UdunosTuning.RING_SPAWN_RADIUS);
            Vec3 inward = core.add(0.0D, 1.5D, 0.0D).subtract(pos);
            if (inward.lengthSqr() < 1.0E-6D) {
                continue;
            }
            UdunosBolt bolt = UdunosBolt.plain(level, this.boss, pos,
                            inward.normalize(), UdunosTuning.RING_BOLT_SPEED)
                    .withLife(140);
            level.addFreshEntity(bolt);
        }
        UdunosFx.playShot(level, core.add(0.0D, 1.0D, 0.0D));
    }

    @Override
    protected void onFinished() {
        if (this.ring != null) {
            this.ring.discard();
            this.ring = null;
        }
    }
}
