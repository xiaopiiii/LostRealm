package com.lost_realm.boss.udunos.move;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.lost_realm.boss.udunos.UdunosBolt;
import com.lost_realm.boss.udunos.UdunosBoss;
import com.lost_realm.boss.udunos.UdunosEffect;
import com.lost_realm.boss.udunos.UdunosFx;
import com.lost_realm.boss.udunos.UdunosPhase;
import com.lost_realm.boss.udunos.UdunosTuning;

/**
 * <b>星核脉冲</b> —— 第三阶段，唯一一个"没有预警线"的招式。
 *
 * <h2>为什么它可以不给预警</h2>
 * 因为它<b>慢</b>。0.47 格/tick 的扩散速度，从圈心到圈边要 60 多 tick，
 * 玩家有整整三秒看着它过来。这一招考的不是反应速度，是
 * <b>"算好时间、沿着环转的方向绕到缺口去"</b>。
 *
 * <h2>流程</h2>
 * <pre>
 *   从限制圈的<b>世界坐标圆心</b>发射（不跟玩家、不跟镜头）
 *      ↓ 共五波，每波 12 发排成一圈，只留一个缺口
 *   每波的旋转方向与速度都随机（1~3 度/tick，顺逆皆可）
 *      ↓ 上一波飞过圈半径 3/4 之后，才发下一波
 * </pre>
 *
 * <h2>★ 为什么从圆心发射，而不是从 Boss 身上</h2>
 * 原设计强调这一点。Boss 在圈边盘旋、玩家在圈里跑，如果弹幕从 Boss 身上出，
 * 那"环"就会跟着转，缺口位置也会跟着飘 —— 玩家没法预判。
 * 钉在圆心之后，每一波的缺口在哪里，玩家看一眼就知道该往哪跑。
 *
 * <h2>旋转怎么做出"环"的手感</h2>
 * 每发弹幕在径向速度之外，额外加一个很小的切向分量，
 * 于是它们飞出来时是<b>弧线</b>而不是直线 —— 视觉上就像一圈正在旋转的环。
 */
public final class MoveCorePulse extends UdunosMove {

    private int wavesLeft;
    private int timer;
    private Vec3 center = Vec3.ZERO;
    private double orbitDir = 1.0D;

    @Nullable
    private UdunosEffect ring;

    public MoveCorePulse(UdunosBoss boss) {
        super(boss);
    }

    @Override
    public MoveKind kind() {
        return MoveKind.PULSE;
    }

    @Override
    public String id() {
        return "pulse";
    }

    @Override
    public String langKey() {
        return "move.lost_realm.udunos.pulse";
    }

    @Override
    public boolean availableIn(UdunosPhase phase) {
        return phase == UdunosPhase.THREE;
    }

    @Override
    public boolean exposesCore() {
        return true;
    }

    @Override
    public void begin(@Nullable Player target) {
        this.wavesLeft = UdunosTuning.PULSE_WAVES;
        this.timer = UdunosTuning.PULSE_WAVE_INTERVAL;
        this.orbitDir = this.boss.getRandom().nextBoolean() ? 1.0D : -1.0D;

        Vec3 anchor = target != null ? target.position() : this.boss.position();
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
        this.timer++;

        double speed = UdunosTuning.CRUISE_SPEED * 0.95D;
        this.velocity = orbitTowards(this.center, UdunosTuning.ARENA_ORBIT_RADIUS,
                speed, this.orbitDir);

        this.boss.syncMove(kind());
        this.boss.syncAim(this.velocity.lengthSqr() > 1.0E-6D
                ? this.velocity.normalize() : new Vec3(0, 0, 1));
        this.boss.syncWarning(0);

        if (this.wavesLeft > 0 && this.timer >= UdunosTuning.PULSE_WAVE_INTERVAL) {
            this.timer = 0;
            this.wavesLeft--;
            spawnPulse();
        }
        if (this.wavesLeft <= 0 && this.timer >= UdunosTuning.PULSE_WAVE_INTERVAL) {
            finish();
        }
        return this.velocity;
    }

    /** 一圈 12 发，留一个缺口，整体带一点切向速度形成旋转感。 */
    private void spawnPulse() {
        if (!(this.boss.level() instanceof ServerLevel level)) {
            return;
        }
        if (boltBudgetLeft() < UdunosTuning.PULSE_BOLTS) {
            return;
        }
        int count = UdunosTuning.PULSE_BOLTS;
        var random = this.boss.getRandom();

        double gapIndex = random.nextInt(count);
        double spin = Mth.lerp(random.nextDouble(),
                UdunosTuning.PULSE_SPIN_MIN, UdunosTuning.PULSE_SPIN_MAX)
                * (random.nextBoolean() ? 1.0D : -1.0D);

        Vec3 origin = this.center.add(0.0D, 1.2D, 0.0D);
        for (int i = 0; i < count; i++) {
            if (i == (int) gapIndex) {
                continue;   // 唯一的缺口
            }
            double angle = Math.PI * 2.0D * i / count;
            Vec3 radial = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
            Vec3 tangent = new Vec3(-radial.z, 0.0D, radial.x);
            // 切向分量 = 旋转感。0.42 是试出来的：再大就不像环了
            Vec3 dir = radial.add(tangent.scale(spin * 0.42D)).normalize();
            UdunosBolt bolt = UdunosBolt.plain(level, this.boss, origin, dir,
                            UdunosTuning.PULSE_SPEED)
                    .withLife(UdunosTuning.PULSE_LIFE);
            level.addFreshEntity(bolt);
        }
        UdunosFx.playEffectFire(level, origin);
    }

    @Override
    protected void onFinished() {
        if (this.ring != null) {
            this.ring.discard();
            this.ring = null;
        }
    }
}
