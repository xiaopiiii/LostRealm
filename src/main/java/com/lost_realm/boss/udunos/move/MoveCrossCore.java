package com.lost_realm.boss.udunos.move;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.lost_realm.boss.udunos.UdunosBoss;
import com.lost_realm.boss.udunos.UdunosEffect;
import com.lost_realm.boss.udunos.UdunosFx;
import com.lost_realm.boss.udunos.UdunosPhase;
import com.lost_realm.boss.udunos.UdunosTuning;

/**
 * <b>裂星十字核</b> —— 第三阶段，唯一一个"会追着你跑"的东西。
 *
 * <h2>它替代了旧的「星座裁线」</h2>
 * 原设计特意注明这一点。区别在于：旧的是一堆固定方向的裁线，
 * 玩家只要记住"哪几个角度不能站"就行；而这一颗会<b>朝着你飞过来</b>，
 * 逼你一边跑一边看它，最后在它减速滑行的那几秒里决定"往哪边闪"。
 *
 * <h2>流程</h2>
 * <pre>
 *   发射一颗十字核，朝你 1 秒后的位置直线飞（预判）
 *      ↓ 飞行中持续计时
 *   满足任一条件即引爆：
 *     · 飞够 1.25 秒，且已经进入你周围 24 格
 *     · 或者飞满 3 秒（最后期限，防止它一直在场上飘）
 *      ↓
 *   原地炸开四道激光，4 秒内一起转 360 度
 * </pre>
 *
 * <h2>预判代替追踪</h2>
 * 原设计写的是"持续追踪"。这里改成<b>发射时预判一次</b>，理由是两者
 * 在玩家体感上几乎一样（都会被追上），但预判不需要每 tick 改朝向量，
 * 也就不会出现原设计亲自警告过的"贴身吸附、绕着玩家掉头"那种恶心手感。
 */
public final class MoveCrossCore extends UdunosMove {

    private enum Step {
        /** 十字核飞行中。 */
        FLY,
        /** 已引爆，四道激光在转。 */
        BURST
    }

    private Step step = Step.FLY;
    private int stepTicks;
    private Vec3 dir = new Vec3(1.0D, 0.0D, 0.0D);

    public MoveCrossCore(UdunosBoss boss) {
        super(boss);
    }

    @Override
    public MoveKind kind() {
        return MoveKind.CROSS;
    }

    @Override
    public String id() {
        return "cross";
    }

    @Override
    public String langKey() {
        return "move.lost_realm.udunos.cross";
    }

    @Override
    public boolean availableIn(UdunosPhase phase) {
        return phase == UdunosPhase.THREE;
    }

    @Override
    public void begin(@Nullable Player target) {
        this.step = Step.FLY;
        this.stepTicks = 0;

        Vec3 head = this.boss.position();
        Vec3 aim;
        if (target != null) {
            // 预判 1 秒后的位置 —— "可以追上高速玩家"靠的就是这一步
            Vec3 center = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D)
                    .add(target.getDeltaMovement().scale(20.0D));
            aim = center.subtract(head);
        } else {
            aim = this.boss.brain().velocity();
        }
        this.dir = aim.lengthSqr() < 1.0E-6D ? new Vec3(1, 0, 0) : aim.normalize();

        if (this.boss.level() instanceof ServerLevel level) {
            UdunosFx.playChargeCue(level, head);
        }
    }

    @Override
    public Vec3 tick(@Nullable Player target) {
        this.ticks++;
        this.stepTicks++;

        if (this.step == Step.FLY) {
            tickFly(target);
        } else {
            tickBurst();
        }
        return this.velocity;
    }

    private void tickFly(@Nullable Player target) {
        this.velocity = this.dir.scale(UdunosTuning.CROSS_SPEED);

        this.boss.syncMove(kind());
        this.boss.syncAim(this.dir);
        // 客户端据此画那四道"还在转的预警线"的紧迫度
        this.boss.syncWarning(Math.max(0, UdunosTuning.CROSS_MAX_WARN - this.stepTicks));

        double distance = distanceTo(target);
        boolean inRange = distance < UdunosTuning.CROSS_LOCK_RANGE;
        boolean mayFire = this.stepTicks >= UdunosTuning.CROSS_MIN_WARN;
        boolean mustFire = this.stepTicks >= UdunosTuning.CROSS_MAX_WARN;

        if ((inRange && mayFire) || mustFire) {
            enterBurst();
        }
    }

    /** 引爆：原地立起四道一起旋转的激光。 */
    private void enterBurst() {
        this.step = Step.BURST;
        this.stepTicks = 0;

        if (this.boss.level() instanceof ServerLevel level) {
            Vec3 origin = this.boss.position();
            // 4 秒转满 360 度（原设计"四向激光在 4 秒内旋转 360 度"）
            double spin = 360.0D / Math.max(1, UdunosTuning.CROSS_LASER_LIFE);
            for (int i = 0; i < 4; i++) {
                UdunosEffect laser = UdunosEffect.laser(level, this.boss, origin,
                        i * 90.0F, 0.0F,
                        UdunosTuning.CROSS_LASER_LENGTH,
                        UdunosTuning.CROSS_LASER_HALF,
                        0, UdunosTuning.CROSS_LASER_LIFE,
                        UdunosTuning.BOLT_DAMAGE * 1.4F,
                        spin);
                level.addFreshEntity(laser);
            }
            UdunosFx.playBurst(level, origin);
        }
    }

    private void tickBurst() {
        // 爆炸后沿最后方向惯性滑行、逐渐停下 —— 不是硬刹车
        this.velocity = this.velocity.scale(0.97D);
        this.boss.syncMove(kind());
        this.boss.syncAim(this.dir);
        this.boss.syncWarning(0);

        if (this.stepTicks > 26) {
            finish();
        }
    }
}
