package com.lost_realm.boss.erebos.move;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.lost_realm.boss.erebos.ErebosBoss;
import com.lost_realm.boss.erebos.ErebosFx;
import com.lost_realm.boss.erebos.ErebosPhase;
import com.lost_realm.boss.erebos.ErebosTuning;

/**
 * <b>噬光冲锋</b> —— 厄瑞玻斯的招牌动作，四个阶段都在用。
 *
 * <h2>三个阶段</h2>
 * <pre>
 *   WARN    头部减速、长红线亮起、方向<b>每 tick 跟随玩家</b>     ← 0.9/0.8/0.7 秒，逐阶段缩短
 *   DASH    方向<b>锁定</b>，加速到 2.6 格/tick 直线贯穿         ← 撞到玩家也不停
 *   RECOVER 保留惯性减速，同时把机头掰向下一次冲锋方向          ← 再进 WARN，共三次
 * </pre>
 *
 * <h2>两条不能动的规矩</h2>
 * <ol>
 *   <li><b>预警时才预判，发射时锁定。</b>原设计第 3.6 条。如果冲锋途中还追着玩家转，
 *       那条红线就失去了意义 —— 玩家没法相信"线指哪就打哪"，
 *       整场战斗的可读性会塌掉。所以 {@code aim} 只在 WARN 阶段更新一次，
 *       DASH 开始后它就是常量。</li>
 *   <li><b>穿过玩家所在区域后继续前进。</b>原设计第 3.4 条特意强调"不要撞到玩家位置就停住"。
 *       原因是这条虫有 96 格长：头部撞到玩家就刹车的话，尾部永远扫不过来，
 *       整个冲锋就变成"头轻轻顶你一下"。所以冲锋距离取
 *       {@code 起跑时与玩家的距离 + 40 格}，保证整条身体都从玩家身上碾过去。</li>
 * </ol>
 *
 * <h2>为什么预判要打 0.6 折</h2>
 * 全额预判会让高速冲锋变成必中 —— 玩家再快的走位都没用，那不叫难，叫不讲理。
 * 0.6 的效果正好："直着跑会被预判到，变向就能甩开"。
 */
public final class MoveCharge extends ErebosMove {

    private enum Step {
        /** 预警。 */
        WARN,
        /** 冲锋。 */
        DASH,
        /** 回转。 */
        RECOVER
    }

    private Step step = Step.WARN;
    private int stepTicks;
    private int chargesLeft;
    private double travelled;
    private double distance;
    private Vec3 aim = new Vec3(1.0D, 0.0D, 0.0D);

    public MoveCharge(ErebosBoss boss) {
        super(boss);
    }

    @Override
    public MoveKind kind() {
        return MoveKind.CHARGE;
    }

    @Override
    public String id() {
        return "charge";
    }

    @Override
    public String langKey() {
        return "move.lost_realm.erebos.charge";
    }

    /** 全阶段可用 —— 它是这条虫最基础的移动方式，也是"逃不掉的压迫感"来源。 */
    @Override
    public boolean availableIn(ErebosPhase phase) {
        return true;
    }

    @Override
    public void begin(@Nullable Player target) {
        this.chargesLeft = ErebosTuning.CHARGES_PER_ROUND;
        // 起手先吼一声：玩家此刻必须开始找横向拉开的方向，音效得能穿过背景音乐
        if (this.boss.level() instanceof ServerLevel level) {
            ErebosFx.playChargeCue(level, this.boss.position());
        }
        enterWarn(target);
    }

    private void enterWarn(@Nullable Player target) {
        this.step = Step.WARN;
        this.stepTicks = 0;
        Vec3 a = aimAt(target);
        if (a != null) {
            this.aim = a;
        }
    }

    private void enterDash(@Nullable Player target) {
        this.step = Step.DASH;
        this.stepTicks = 0;
        this.travelled = 0.0D;
        this.distance = Math.max(24.0D,
                distanceTo(target) + ErebosTuning.CHARGE_OVERSHOOT);
    }

    private void enterRecover() {
        this.step = Step.RECOVER;
        this.stepTicks = 0;
    }

    @Override
    public Vec3 tick(@Nullable Player target) {
        this.ticks++;
        this.stepTicks++;

        switch (this.step) {
            case WARN -> tickWarn(target);
            case DASH -> tickDash(target);
            case RECOVER -> tickRecover(target);
        }
        return this.velocity;
    }

    private void tickWarn(@Nullable Player target) {
        // 减速但不完全停住 —— 40 节的巨虫"稳稳悬停"看着很假
        this.velocity = this.velocity.scale(0.88D);

        // 预警期间持续预判（这就是红线"跟着你走"的那一段）
        Vec3 a = predictAim(target);
        if (a != null) {
            this.aim = a;
        }

        int warn = this.boss.phase().chargeWarningTicks();
        this.boss.syncMove(kind());
        this.boss.syncAim(this.aim);
        this.boss.syncWarning(Math.max(0, warn - this.stepTicks));

        if (this.stepTicks >= warn) {
            enterDash(target);
        }
    }

    private void tickDash(@Nullable Player target) {
        double speed = Math.min(ErebosTuning.CHARGE_SPEED,
                this.velocity.length() + ErebosTuning.CHARGE_ACCEL);
        this.velocity = this.aim.scale(speed);
        this.travelled += speed;

        // 冲锋途中不再同步方向 —— 客户端也就不会把红线继续跟着玩家扭
        this.boss.syncMove(kind());
        this.boss.syncAim(this.aim);
        this.boss.syncWarning(0);

        if (this.travelled >= this.distance) {
            this.chargesLeft--;
            if (this.chargesLeft > 0) {
                enterRecover();
            } else {
                finish();
            }
        }
    }

    private void tickRecover(@Nullable Player target) {
        this.velocity = this.velocity.scale(0.94D);

        // 前半段只减速（让惯性把身体带出去），后半段才开始掰机头
        int half = Math.max(6, ErebosTuning.CHARGE_RECOVER_TICKS / 2);
        if (this.stepTicks > half) {
            Vec3 a = predictAim(target);
            if (a != null) {
                this.velocity = turnTowards(this.velocity,
                        a.scale(ErebosTuning.CRUISE_SPEED),
                        ErebosTuning.RECOVER_TURN,
                        ErebosTuning.CRUISE_SPEED * 0.5D);
            }
        }

        this.boss.syncMove(kind());
        this.boss.syncAim(this.velocity.lengthSqr() > 1.0E-6D
                ? this.velocity.normalize() : this.aim);
        this.boss.syncWarning(0);

        if (this.stepTicks >= ErebosTuning.CHARGE_RECOVER_TICKS) {
            enterWarn(target);
        }
    }

    /**
     * 预判玩家的位置，返回从头部指向"预测点"的单位向量。
     *
     * <p>预判量按"以冲锋速度飞到那儿要多久"估算，再打 0.6 折（见类注释）。
     */
    @Nullable
    private Vec3 predictAim(@Nullable Player target) {
        if (target == null) {
            return null;
        }
        Vec3 head = this.boss.position();
        Vec3 center = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        double travel = Math.max(0.0D,
                (head.distanceTo(center) + ErebosTuning.CHARGE_OVERSHOOT)
                        / ErebosTuning.CHARGE_SPEED);
        Vec3 forecast = center.add(target.getDeltaMovement().scale(travel * 0.6D));
        Vec3 dir = forecast.subtract(head);
        return dir.lengthSqr() < 1.0E-6D ? null : dir.normalize();
    }
}
