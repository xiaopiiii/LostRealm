package com.lost_realm.boss.erebos.move;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.lost_realm.boss.erebos.ErebosBoss;
import com.lost_realm.boss.erebos.ErebosEffect;
import com.lost_realm.boss.erebos.ErebosFx;
import com.lost_realm.boss.erebos.ErebosPhase;
import com.lost_realm.boss.erebos.ErebosTuning;

/**
 * <b>崩星审判</b> —— 第三阶段，纯站位考试。
 *
 * <h2>它和逆轨流星的区别</h2>
 * 逆轨流星是"两块弹幕墙夹出一个安全区"，玩家要<b>找到</b>它；
 * 崩星审判是"一半地面会炸、另一半安全"，玩家要<b>连续换边</b>。
 * 前者考眼力，后者考执行 —— 你得真的跑得动、跑得及时。
 *
 * <h2>流程</h2>
 * <pre>
 *   随机一个方向，沿它摆两排交错的星柱（A 列 / B 列）
 *      ↓
 *   A 组预警 1.3 秒 → 爆发 0.6 秒
 *      ↓ 转移窗口 1 秒（足够 70 mph 的玩家换个位置）
 *   B 组预警 1.3 秒 → 爆发 0.6 秒
 *      ↓
 *   换一个方向，下一轮 …… 共三轮
 * </pre>
 *
 * <h2>为什么是"一列A、一列B交替"</h2>
 * 原设计特意写："相邻列采用'一列A、一列B交替'，不要两列一组。"
 * 两列一组的话，玩家只要站在"组与组之间"就能永远安全；
 * 交错之后，A 组的空隙正好是 B 组的柱子 ——
 * <b>站哪都要换位置，而且是每次都换</b>。这才是这一招的核心。
 */
public final class MoveStarJudgement extends ErebosMove {

    private enum Step {
        /** A 组预警。 */
        WARN_A,
        /** A 组爆发。 */
        FIRE_A,
        /** 转移窗口。 */
        TRANSFER,
        /** B 组预警。 */
        WARN_B,
        /** B 组爆发。 */
        FIRE_B,
        /** 两轮之间。 */
        GAP
    }

    private Step step = Step.WARN_A;
    private int stepTicks;
    private int roundsLeft;
    private double angle;

    public MoveStarJudgement(ErebosBoss boss) {
        super(boss);
    }

    @Override
    public MoveKind kind() {
        return MoveKind.JUDGEMENT;
    }

    @Override
    public String id() {
        return "judgement";
    }

    @Override
    public String langKey() {
        return "move.lost_realm.erebos.judgement";
    }

    @Override
    public boolean availableIn(ErebosPhase phase) {
        return phase == ErebosPhase.THREE;
    }

    @Override
    public void begin(@Nullable Player target) {
        this.roundsLeft = ErebosTuning.JUDGE_ROUNDS;
        this.step = Step.WARN_A;
        this.stepTicks = 0;
        this.angle = this.boss.getRandom().nextDouble() * Math.PI * 2.0D;
    }

    @Override
    public Vec3 tick(@Nullable Player target) {
        this.ticks++;
        this.stepTicks++;

        tickHeadMotion(target);
        this.boss.syncMove(kind());
        this.boss.syncAim(this.velocity.lengthSqr() > 1.0E-6D
                ? this.velocity.normalize() : new Vec3(0, 0, 1));
        this.boss.syncWarning(this.step == Step.WARN_A || this.step == Step.WARN_B
                ? Math.max(0, ErebosTuning.JUDGE_WARN - this.stepTicks) : 0);

        switch (this.step) {
            case WARN_A -> {
                if (this.stepTicks >= ErebosTuning.JUDGE_WARN) {
                    fireColumns(target, true);
                    this.step = Step.FIRE_A;
                    this.stepTicks = 0;
                }
            }
            case FIRE_A -> {
                if (this.stepTicks >= ErebosTuning.JUDGE_ACTIVE) {
                    this.step = Step.TRANSFER;
                    this.stepTicks = 0;
                }
            }
            case TRANSFER -> {
                if (this.stepTicks >= ErebosTuning.JUDGE_TRANSFER) {
                    this.step = Step.WARN_B;
                    this.stepTicks = 0;
                }
            }
            case WARN_B -> {
                if (this.stepTicks >= ErebosTuning.JUDGE_WARN) {
                    fireColumns(target, false);
                    this.step = Step.FIRE_B;
                    this.stepTicks = 0;
                }
            }
            case FIRE_B -> {
                if (this.stepTicks >= ErebosTuning.JUDGE_ACTIVE) {
                    this.roundsLeft--;
                    if (this.roundsLeft <= 0) {
                        finish();
                    } else {
                        this.step = Step.GAP;
                        this.stepTicks = 0;
                    }
                }
            }
            case GAP -> {
                if (this.stepTicks >= 26) {
                    // 换一个方向 —— 原设计："每轮改变整体方向，不能永远从固定方向出现"
                    this.angle += Math.toRadians(40.0D + this.boss.getRandom().nextDouble() * 50.0D);
                    this.step = Step.WARN_A;
                    this.stepTicks = 0;
                }
            }
        }
        return this.velocity;
    }

    private void tickHeadMotion(@Nullable Player target) {
        if (target == null) {
            this.velocity = this.velocity.scale(0.94D);
            return;
        }
        double speed = ErebosTuning.CRUISE_SPEED * 0.9D;
        Vec3 from = this.boss.position().subtract(target.position());
        if (from.lengthSqr() < 1.0E-6D) {
            from = new Vec3(1, 0, 0);
        }
        from = from.normalize();
        Vec3 desired = distanceTo(target) < 20.0D
                ? from.scale(speed)
                : new Vec3(-from.z, 0.0D, from.x).normalize().scale(speed);
        this.velocity = turnTowards(this.velocity, desired,
                ErebosTuning.CRUISE_TURN, speed * 0.6D);
    }

    /**
     * 立起一排交错的星柱。
     *
     * @param groupA true = 立 A 组（偶数位），false = 立 B 组（奇数位）。
     *               <b>两组的位置严格错开</b>，这就是"站哪都要换"的来源。
     */
    private void fireColumns(@Nullable Player target, boolean groupA) {
        if (!(this.boss.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 anchor = target != null ? target.position() : this.boss.position();
        Vec3 dir = new Vec3(Math.cos(this.angle), 0.0D, Math.sin(this.angle)).normalize();
        Vec3 side = new Vec3(-dir.z, 0.0D, dir.x).normalize();

        int columns = ErebosTuning.JUDGE_COLUMNS;
        // A 组占偶数位，B 组占奇数位 —— 于是两组的空隙互为对方的柱子
        for (int i = 0; i < columns; i++) {
            boolean isA = (i % 2 == 0);
            if (isA != groupA) {
                continue;
            }
            double offset = (i - (columns - 1) / 2.0D) * ErebosTuning.JUDGE_SPACING;
            Vec3 pos = anchor.add(side.scale(offset));
            ErebosEffect column = ErebosEffect.column(level, this.boss, pos,
                    ErebosTuning.JUDGE_RADIUS,
                    2,                       // 柱子自己只留 2 tick 的"预警"，
                    ErebosTuning.JUDGE_ACTIVE,
                    ErebosTuning.BOLT_DAMAGE * 1.5F);
            level.addFreshEntity(column);
        }
        ErebosFx.playEffectFire(level, anchor);
    }
}
