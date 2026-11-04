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
 * <b>尾刃刻痕</b> —— 第三阶段，把"整条身体都是炮口"这件事讲明白。
 *
 * <h2>它教的是"别只看头"</h2>
 * 前面所有招式的起手都在头部，玩家自然会养成"盯着头"的习惯。
 * 而这一招的激光从<b>身体各处</b>发出 —— 你以为躲开了头，
 * 结果尾刃已经在背后画线了。
 *
 * <h2>流程</h2>
 * <pre>
 *   每 0.6 秒，从身体随机一节发出一道紫色长激光
 *      ↓ 1.5~2 秒的预警（原设计"保留预警，适度预判玩家"）
 *   生效 0.9 秒，期间的持续伤害与可见核心同生共死
 *      ↓ 共 10 道，放完收招
 * </pre>
 *
 * <h2>为什么单道激光要"预警 + 生效"分两段</h2>
 * 原设计要求"视觉宽度和持续伤害判定一致"、"持续伤害一直保留到可见核心消失"。
 * 这意味着玩家可以在激光里"擦一下就跑"—— 只要跑出去就不再受伤。
 * 如果做成"碰到即死一次的瞬间判定"，就变成了纯反应测试，
 * 而这个游戏想要的显然是<b>看懂了就能躲</b>。
 */
public final class MoveTailLaser extends UdunosMove {

    private int fired;
    private int timer;

    public MoveTailLaser(UdunosBoss boss) {
        super(boss);
    }

    @Override
    public MoveKind kind() {
        return MoveKind.TAIL_LASER;
    }

    @Override
    public String id() {
        return "tail_laser";
    }

    @Override
    public String langKey() {
        return "move.lost_realm.udunos.tail_laser";
    }

    @Override
    public boolean availableIn(UdunosPhase phase) {
        return phase == UdunosPhase.THREE;
    }

    @Override
    public void begin(@Nullable Player target) {
        this.fired = 0;
        // 置满，第一道立刻就来
        this.timer = UdunosTuning.TAIL_LASER_INTERVAL;
    }

    @Override
    public Vec3 tick(@Nullable Player target) {
        this.ticks++;
        this.timer++;

        tickHeadMotion(target);

        this.boss.syncMove(kind());
        this.boss.syncAim(this.velocity.lengthSqr() > 1.0E-6D
                ? this.velocity.normalize() : new Vec3(0, 0, 1));
        this.boss.syncWarning(0);

        if (this.fired < UdunosTuning.TAIL_LASER_COUNT
                && this.timer >= UdunosTuning.TAIL_LASER_INTERVAL) {
            this.timer = 0;
            this.fired++;
            fireLaser(target);
        }
        // 最后一道也要飞完 —— 不能放完立刻走，那样最后一道等于没放
        if (this.fired >= UdunosTuning.TAIL_LASER_COUNT
                && this.timer >= UdunosTuning.TAIL_LASER_INTERVAL + 20) {
            finish();
        }
        return this.velocity;
    }

    /** 头部维持中速巡游 —— 这一招的主角是激光，头别抢戏。 */
    private void tickHeadMotion(@Nullable Player target) {
        if (target == null) {
            this.velocity = this.velocity.scale(0.94D);
            return;
        }
        double speed = UdunosTuning.CRUISE_SPEED;
        Vec3 from = this.boss.position().subtract(target.position());
        if (from.lengthSqr() < 1.0E-6D) {
            from = new Vec3(1, 0, 0);
        }
        from = from.normalize();
        double distance = distanceTo(target);
        Vec3 desired = distance < 18.0D
                ? from.scale(speed)
                : new Vec3(-from.z, 0.0D, from.x).normalize().scale(speed);
        this.velocity = turnTowards(this.velocity, desired,
                UdunosTuning.CRUISE_TURN, speed * 0.6D);
    }

    /** 从身体随机一节，朝玩家预判位置射出一道长激光。 */
    private void fireLaser(@Nullable Player target) {
        if (!(this.boss.level() instanceof ServerLevel level)) {
            return;
        }
        var random = this.boss.getRandom();
        int segment;
        if (this.fired % 3 == 0) {
            // 每三道里有一道从尾巴附近发 —— 保持"尾刃"这个名字的诚实
            segment = UdunosTuning.SEGMENTS - 2 - random.nextInt(4);
        } else {
            segment = random.nextInt(UdunosTuning.SEGMENTS);
        }
        Vec3 origin = this.boss.segmentPosition(Math.max(0, segment));

        Vec3 aim;
        if (target != null) {
            // 适度预判：0.5 秒的提前量，走直线会被打，变向能甩掉
            Vec3 center = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D)
                    .add(target.getDeltaMovement().scale(10.0D));
            aim = center.subtract(origin);
        } else {
            aim = this.velocity;
        }
        if (aim.lengthSqr() < 1.0E-6D) {
            return;
        }
        aim = aim.normalize();

        float yaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
        float pitch = (float) Math.toDegrees(-Math.asin(
                net.minecraft.util.Mth.clamp(aim.y, -1.0D, 1.0D)));

        UdunosEffect laser = UdunosEffect.laser(level, this.boss, origin,
                yaw, pitch,
                UdunosTuning.TAIL_LASER_LENGTH,
                UdunosTuning.TAIL_LASER_HALF,
                UdunosTuning.TAIL_LASER_WARN,
                UdunosTuning.TAIL_LASER_ACTIVE,
                UdunosTuning.BOLT_DAMAGE * 1.3F,
                0.0D);
        level.addFreshEntity(laser);
        UdunosFx.playShot(level, origin);
    }
}
