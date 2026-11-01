package com.lost_realm.boss.erebos.move;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.lost_realm.boss.erebos.ErebosBolt;
import com.lost_realm.boss.erebos.ErebosBoss;
import com.lost_realm.boss.erebos.ErebosEffect;
import com.lost_realm.boss.erebos.ErebosFx;
import com.lost_realm.boss.erebos.ErebosPhase;
import com.lost_realm.boss.erebos.ErebosTuning;

/**
 * <b>星门贯穿</b> —— 第二阶段引入，之后一直是主力招式。
 *
 * <h2>它和第 1 阶段的冲锋有什么不同</h2>
 * 噬光冲锋是"看着它冲过来，你横着走开"；星门贯穿是
 * <b>"它从你背后冒出来"</b> —— 玩家面对的是同一套横向躲闪，
 * 但方向不再可预测，因为出口的位置每次都不同。
 *
 * <h2>流程</h2>
 * <pre>
 *   在头部前方开入口门 A，在你附近开出口门 B
 *      ↓ 0.7 秒预警（长红线 + 音效）
 *   头部扎进 A，下一秒从 B 钻出（身体逐节跟进）
 *      ↓ 出口位置立刻射出 7 颗星弹
 *   从 B 继续高速贯穿 → 1.5 秒后开下一对门 …… 共四次
 * </pre>
 *
 * <h2>★ 跨门的那个坑</h2>
 * 头部是"从 A 消失、在 B 出现"，中间那段根本不是它走过的路。
 * 如果不处理，路径跟随会把 A→B 的<b>直线距离</b>当成走过的弧长，
 * 于是整条虫在那一瞬间被拉成一条横跨战场的长条 —— 原设计特意警告过这件事
 * （"跨星门时必须标记轨迹断点，禁止将入口到出口之间插值成一条超长身体"）。
 *
 * <p>处理方式见 {@code ErebosTrail#markBreak()}：在跳转前给轨迹打一个断点标记，
 * 采样回溯到那里就停，剩下的节全部堆在断点处 ——
 * 看起来就是"身体后半截还留在门里"，正是该有的样子。
 */
public final class MoveStarGate extends ErebosMove {

    private enum Step {
        /** 开门并预警。 */
        WARN,
        /** 已经穿门，正从出口继续贯穿。 */
        THROUGH,
        /** 两次穿门之间的回收。 */
        COOLDOWN
    }

    private Step step = Step.WARN;
    private int stepTicks;
    private int chargesLeft;
    private Vec3 gateA = Vec3.ZERO;
    private Vec3 gateB = Vec3.ZERO;
    private Vec3 dir = new Vec3(1.0D, 0.0D, 0.0D);

    @Nullable
    private ErebosEffect visualA;
    @Nullable
    private ErebosEffect visualB;

    public MoveStarGate(ErebosBoss boss) {
        super(boss);
    }

    @Override
    public MoveKind kind() {
        return MoveKind.GATE;
    }

    @Override
    public String id() {
        return "gate";
    }

    @Override
    public String langKey() {
        return "move.lost_realm.erebos.gate";
    }

    /** 第一阶段不出现 —— 它需要玩家先学会"横向拉开"，再引入方向不可预测。 */
    @Override
    public boolean availableIn(ErebosPhase phase) {
        return phase != ErebosPhase.ONE;
    }

    @Override
    public void begin(@Nullable Player target) {
        this.chargesLeft = ErebosTuning.GATE_CHARGES;
        enterWarn(target);
    }

    private void enterWarn(@Nullable Player target) {
        this.step = Step.WARN;
        this.stepTicks = 0;

        Vec3 head = this.boss.position();
        Vec3 aim = aimAt(target);
        if (aim != null) {
            this.dir = aim;
        } else if (this.boss.brain().velocity().lengthSqr() > 1.0E-6D) {
            this.dir = this.boss.brain().velocity().normalize();
        }

        // 入口：头部正前方
        this.gateA = head.add(this.dir.scale(ErebosTuning.GATE_DISTANCE * 0.65D));
        // 出口：玩家周围随机方向 —— "不可预测"正是这一招的核心体验
        Vec3 around = target != null ? target.position() : head;
        double angle = this.boss.getRandom().nextDouble() * Math.PI * 2.0D;
        this.gateB = around.add(Math.cos(angle) * 15.0D, 3.5D, Math.sin(angle) * 15.0D);

        spawnGateVisuals();
        if (this.boss.level() instanceof ServerLevel level) {
            ErebosFx.playChargeCue(level, head);
        }
    }

    private void spawnGateVisuals() {
        clearGateVisuals();
        if (!(this.boss.level() instanceof ServerLevel level)) {
            return;
        }
        this.visualA = spawnGate(level, this.gateA);
        this.visualB = spawnGate(level, this.gateB);
    }

    private ErebosEffect spawnGate(ServerLevel level, Vec3 pos) {
        ErebosEffect gate = ErebosEffect.gate(level, this.boss, pos, ErebosTuning.GATE_RADIUS);
        level.addFreshEntity(gate);
        return gate;
    }

    private void clearGateVisuals() {
        if (this.visualA != null) {
            this.visualA.discard();
            this.visualA = null;
        }
        if (this.visualB != null) {
            this.visualB.discard();
            this.visualB = null;
        }
    }

    @Override
    public Vec3 tick(@Nullable Player target) {
        this.ticks++;
        this.stepTicks++;

        switch (this.step) {
            case WARN -> tickWarn(target);
            case THROUGH -> tickThrough();
            case COOLDOWN -> tickCooldown(target);
        }
        return this.velocity;
    }

    private void tickWarn(@Nullable Player target) {
        // 朝入口加速
        this.velocity = this.dir.scale(ErebosTuning.GATE_SPEED * 0.75D);

        Vec3 aim = aimAt(target);
        if (aim != null) {
            this.dir = aim;
        }
        this.boss.syncMove(kind());
        this.boss.syncAim(this.dir);
        this.boss.syncWarning(Math.max(0, ErebosTuning.GATE_WARN - this.stepTicks));

        // 到时间、或者头已经贴到门上了 —— 两种触发都认，免得"明明撞上门了却还在等"
        if (this.stepTicks >= ErebosTuning.GATE_WARN
                || this.boss.position().distanceTo(this.gateA) < ErebosTuning.GATE_RADIUS + 1.5D) {
            enterGate();
        }
    }

    /** 穿门的那一瞬间。 */
    private void enterGate() {
        this.step = Step.THROUGH;
        this.stepTicks = 0;

        // ★ beta35 起改用「整体平移」，不再 markBreak + 逐节抽出。
        //
        //   旧做法（markBreak）让穿门后身体 205 格全部堆在 A 门一个点，
        //   头部孤零零在 B 门 —— 而星门特效又在这一帧被 clearGateVisuals 清掉，
        //   玩家看到的就是"整条虫缩成一个点、凭空消失"，要 2 秒才慢慢抽出来。
        //   这就是"穿门后消失"的根因。
        //
        //   现在改成 trail.translate()：把所有历史采样点整体平移到 B 门，
        //   身体形状原封不动地跟着头一起过去 —— 视觉上是干净的"整条虫瞬移"，
        //   绝不会出现"身体消失"。
        double dx = this.gateB.x - this.boss.getX();
        double dy = this.gateB.y - this.boss.getY();
        double dz = this.gateB.z - this.boss.getZ();
        this.boss.trail().translate(dx, dy, dz);
        this.boss.refreshSegments();
        // ★ 通知客户端「这一帧瞬移了」—— 客户端据此硬切脊线插值。
        this.boss.markTeleport();
        this.boss.setPos(this.gateB.x, this.gateB.y, this.gateB.z);

        if (this.boss.level() instanceof ServerLevel level) {
            ErebosFx.playBurst(level, this.gateA);
            ErebosFx.playBurst(level, this.gateB);
            // 从<b>出口</b>发射，不是从头部 —— 原设计特意强调过这一点
            fireFromGate(level);
        }
        // ★ 星门特效不要在这一帧清掉 —— 否则玩家看到的是"门凭空消失 + 虫凭空消失"。
        //   让它自然走到寿命结束（ErebosEffect.gate 的 activeTicks=200）。
        //   clearGateVisuals() 改由 finish() 里统一清理。
    }

    /** 出口位置射出 7 颗较稀疏的星弹。 */
    private void fireFromGate(ServerLevel level) {
        int count = Math.max(3, Math.min(ErebosTuning.GATE_BOLTS,
                ErebosTuning.MAX_BOLTS_PER_VOLLEY / 2));
        double spread = Math.toRadians(70.0D);
        Vec3 aimDir = this.dir.lengthSqr() < 1.0E-6D ? new Vec3(1, 0, 0) : this.dir.normalize();
        for (int i = 0; i < count; i++) {
            double offset = count <= 1 ? 0.0D
                    : spread * i / (count - 1.0D) - spread * 0.5D;
            Vec3 d = fanAround(aimDir, offset);
            ErebosBolt bolt = ErebosBolt.plain(level, this.boss, this.gateB, d, 1.15D);
            level.addFreshEntity(bolt);
        }
        ErebosFx.playShot(level, this.gateB);
    }

    private void tickThrough() {
        this.velocity = this.dir.scale(ErebosTuning.GATE_SPEED);
        this.boss.syncMove(kind());
        this.boss.syncAim(this.dir);
        this.boss.syncWarning(0);

        // ★ 结束条件不是"固定 10 tick"，而是「整条身体从门里出来」。
        //
        //   旧实现固定 10 tick：以 3.2 格/tick 算只走 32 格，
        //   而身体有 210 格 —— 身体根本没跟过来就进下一轮了，
        //   视觉上就成了"头瞬移过去、身体凭空消失"。
        //   玩家反馈的"完全是瞬移"就是这个原因。
        //
        //   现在：GATE_SPEED=5.0，210 格约 42 tick 走完。
        //   超时 70 tick 兜底（万一轨迹因故长不够，也不能卡在穿门状态）。
        boolean bodyThrough = this.stepTicks > 4 && this.boss.isFullyEmerged();
        if (bodyThrough || this.stepTicks > 70) {
            this.chargesLeft--;
            if (this.chargesLeft > 0) {
                this.step = Step.COOLDOWN;
                this.stepTicks = 0;
            } else {
                finish();
            }
        }
    }

    private void tickCooldown(@Nullable Player target) {
        // 慢慢减速回转，再开下一对门
        this.velocity = this.velocity.scale(0.93D);
        Vec3 aim = aimAt(target);
        if (aim != null) {
            this.velocity = turnTowards(this.velocity, aim.scale(ErebosTuning.CRUISE_SPEED),
                    ErebosTuning.RECOVER_TURN, ErebosTuning.CRUISE_SPEED * 0.5D);
        }
        this.boss.syncMove(kind());
        this.boss.syncAim(this.velocity.lengthSqr() > 1.0E-6D
                ? this.velocity.normalize() : this.dir);
        this.boss.syncWarning(0);

        // 原设计："相邻冲撞约间隔 1.5 秒"
        if (this.stepTicks >= 30) {
            enterWarn(target);
        }
    }

    @Override
    protected void onFinished() {
        clearGateVisuals();
    }
}
