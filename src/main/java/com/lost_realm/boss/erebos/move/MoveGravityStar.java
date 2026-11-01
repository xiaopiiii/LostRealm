package com.lost_realm.boss.erebos.move;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.lost_realm.boss.erebos.ErebosBolt;
import com.lost_realm.boss.erebos.ErebosBoss;
import com.lost_realm.boss.erebos.ErebosFx;
import com.lost_realm.boss.erebos.ErebosPhase;
import com.lost_realm.boss.erebos.ErebosTuning;

/**
 * <b>引力残星</b> —— 第二阶段引入，整场战斗里最"安静"的一招。
 *
 * <h2>它是来换气的</h2>
 * 第二阶段开始，玩家要同时应付星门貫穿的"方向不可预测"和这一招的
 * "慢速逼近"。这一招的弹幕慢到<b>可以走过去躲</b> —— 它的作用不是杀伤，
 * 而是逼玩家在忙着躲快招的时候，还得留心脚下那些正在飘近的东西。
 *
 * <h2>流程</h2>
 * <pre>
 *   六次投放，每次一颗，左右交替（原设计："左右位置轮换"）
 *      ↓ 炸弹落到你侧后方
 *   停留 1 秒（很短，原设计特意说明"不要把该招预警画成贯穿全屏的长线"）
 *      ↓
 *   向四周撒出 10 发慢速放射弹（0.42 格/tick，比玩家跑步还慢）
 * </pre>
 *
 * <h2>为什么炸弹落在"侧后方"而不是头顶</h2>
 * 落头顶的炸弹，玩家往前跑就出范围了；落侧后方才会截住退路，
 * 把"你要往哪跑"这个问题真正摆到玩家面前。
 */
public final class MoveGravityStar extends ErebosMove {

    private int dropsLeft;
    private int timer;
    private boolean leftSide = true;

    public MoveGravityStar(ErebosBoss boss) {
        super(boss);
    }

    @Override
    public MoveKind kind() {
        return MoveKind.GRAVITY;
    }

    @Override
    public String id() {
        return "gravity";
    }

    @Override
    public String langKey() {
        return "move.lost_realm.erebos.gravity";
    }

    @Override
    public boolean availableIn(ErebosPhase phase) {
        return phase == ErebosPhase.TWO;
    }

    @Override
    public void begin(@Nullable Player target) {
        this.dropsLeft = ErebosTuning.GRAVITY_DROPS;
        // 置满，让第一投在下一 tick 立刻发生
        this.timer = ErebosTuning.GRAVITY_INTERVAL;
        this.leftSide = this.boss.getRandom().nextBoolean();
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

        if (this.dropsLeft > 0 && this.timer >= ErebosTuning.GRAVITY_INTERVAL) {
            this.timer = 0;
            this.dropsLeft--;
            dropBomb(target);
        }
        if (this.dropsLeft <= 0 && this.timer >= ErebosTuning.GRAVITY_INTERVAL) {
            finish();
        }
        return this.velocity;
    }

    /** 投放期间头部维持中速巡游 —— 不冲锋，但也不能停，否则战场会太安静。 */
    private void tickHeadMotion(@Nullable Player target) {
        if (target == null) {
            this.velocity = this.velocity.scale(0.93D);
            return;
        }
        double speed = ErebosTuning.CRUISE_SPEED * 0.9D;
        Vec3 from = this.boss.position().subtract(target.position());
        if (from.lengthSqr() < 1.0E-6D) {
            from = new Vec3(1, 0, 0);
        }
        from = from.normalize();
        // 距离维持在 20~30 格之间：太近没法玩，太远看着像退场
        double distance = distanceTo(target);
        Vec3 desired = distance < 20.0D
                ? from.scale(speed)
                : new Vec3(-from.z, 0.0D, from.x).normalize().scale(speed);
        this.velocity = turnTowards(this.velocity, desired,
                ErebosTuning.CRUISE_TURN, speed * 0.6D);
    }

    private void dropBomb(@Nullable Player target) {
        if (!(this.boss.level() instanceof ServerLevel level)) {
            return;
        }
        if (boltBudgetLeft() < ErebosTuning.GRAVITY_BOLTS) {
            return;
        }
        var random = this.boss.getRandom();
        Vec3 origin = this.boss.segmentPosition(random.nextInt(ErebosTuning.SEGMENTS));
        Vec3 center = target != null ? target.position() : this.boss.position();

        Vec3 towardAway = center.subtract(origin);
        Vec3 horizontal = new Vec3(towardAway.x, 0.0D, towardAway.z);
        if (horizontal.lengthSqr() < 1.0E-6D) {
            horizontal = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 side = new Vec3(-horizontal.z, 0.0D, horizontal.x).normalize()
                .scale(this.leftSide ? 13.0D : -13.0D);
        this.leftSide = !this.leftSide;

        Vec3 spot = center.add(side).add(0.0D, 7.0D, 0.0D);
        Vec3 toSpot = spot.subtract(origin);
        double distance = toSpot.length();
        if (distance < 1.0E-3D) {
            return;
        }
        // 速度反推：约 1.1 秒后正好抵达落点
        double speed = Mth.clamp(distance / 22.0D, 0.35D, 1.10D);
        ErebosBolt bolt = ErebosBolt.seed(level, this.boss, origin,
                toSpot.normalize(), speed,
                ErebosTuning.GRAVITY_BOLTS,
                ErebosTuning.GRAVITY_BOLT_SPEED,
                ErebosTuning.GRAVITY_BOLT_LIFE);
        level.addFreshEntity(bolt);
        ErebosFx.playShot(level, origin);
    }
}
