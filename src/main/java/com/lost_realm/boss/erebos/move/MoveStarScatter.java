package com.lost_realm.boss.erebos.move;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.lost_realm.boss.erebos.ErebosBolt;
import com.lost_realm.boss.erebos.ErebosBoss;
import com.lost_realm.boss.erebos.ErebosFx;
import com.lost_realm.boss.erebos.ErebosPhase;
import com.lost_realm.boss.erebos.ErebosTuning;

/**
 * <b>碎星播撒</b> —— 第一阶段的开场课：悬停、八个出口、扇形齐射。
 *
 * <h2>为什么它是"教学招"</h2>
 * 它是整场战斗里<b>唯一一个头部完全停住</b>的招式。玩家的注意力第一次
 * 不需要在"躲弹幕"和"躲身体"之间分配 —— 可以专心学会读那根长红线。
 * 后面所有更狠的招式，读法都是从这里学的。
 *
 * <h2>流程</h2>
 * <pre>
 *   暂停头部位移
 *      ↓ 每波
 *   从 8 个候选出口里随机挑 <b>2 个</b>（从头到尾沿身体均匀分布）
 *      ↓ 长红线预警 2 秒
 *   每个出口撒出 11 发扇形弹幕（正中那一发<b>必定瞄准玩家</b>）
 *      ↓ 间隔 2.75 秒
 *   下一波 …… 共 3~5 波，最后一波放完即收招
 * </pre>
 *
 * <h2>两个刻意的设计</h2>
 * <ol>
 *   <li><b>为什么是"两个出口同时"而不是一个接一个。</b>
 *       一个出口的扇形只有一侧有威胁，玩家横着走就出去了；
 *       两个<b>位于身体不同位置</b>的出口会形成夹击，逼玩家判断"该往哪边躲"。</li>
 *   <li><b>为什么最后一波放完就结束，不等弹幕飞完。</b>
 *       原设计要求"每一招都必须完成规定波数"，但没要求等弹幕消失 ——
 *       留着弹幕飞反而是好事：玩家要在残余弹幕里接着应付下一招，
 *       这正是压力阶梯该有的样子。</li>
 * </ol>
 */
public final class MoveStarScatter extends ErebosMove {

    private enum Step {
        /** 预警中。 */
        WARN,
        /** 波间间隔。 */
        GAP
    }

    private Step step = Step.WARN;
    private int stepTicks;
    private int waves;
    private int waveIndex;
    private int exitA = -1;
    private int exitB = -1;
    private Vec3 aim = new Vec3(1.0D, 0.0D, 0.0D);

    public MoveStarScatter(ErebosBoss boss) {
        super(boss);
    }

    @Override
    public MoveKind kind() {
        return MoveKind.SCATTER;
    }

    @Override
    public String id() {
        return "scatter";
    }

    @Override
    public String langKey() {
        return "move.lost_realm.erebos.scatter";
    }

    /** 第一阶段专属。 */
    @Override
    public boolean availableIn(ErebosPhase phase) {
        return phase == ErebosPhase.ONE;
    }

    @Override
    public void begin(@Nullable Player target) {
        this.waves = between(ErebosTuning.SCATTER_WAVE_MIN, ErebosTuning.SCATTER_WAVE_MAX);
        this.waveIndex = 0;
        pickExits();
        this.step = Step.WARN;
        this.stepTicks = 0;
        // 第一波立刻就有方向，否则预警的第一帧红线指不出东西
        Vec3 a = aimAt(target);
        if (a != null) {
            this.aim = a;
        }
    }

    @Override
    public Vec3 tick(@Nullable Player target) {
        this.ticks++;
        this.stepTicks++;

        // ★ 本招让头部停下（原设计："Boss 暂停头部位移"）
        this.velocity = Vec3.ZERO;

        // ---- 同步给客户端（渲染器靠这些画红线）----
        this.boss.syncMove(kind());
        this.boss.syncParams(this.exitA, this.exitB);
        this.boss.syncAim(this.aim);
        this.boss.syncWarning(this.step == Step.WARN
                ? Math.max(0, ErebosTuning.SCATTER_WARN - this.stepTicks) : 0);

        if (this.step == Step.WARN) {
            // 预警期间方向跟着玩家走 —— 红线会缓慢"转向你"
            Vec3 a = aimAt(target);
            if (a != null) {
                this.aim = a;
            }
            if (this.stepTicks >= ErebosTuning.SCATTER_WARN) {
                fire();
                this.waveIndex++;
                if (this.waveIndex >= this.waves) {
                    finish();
                } else {
                    this.step = Step.GAP;
                    this.stepTicks = 0;
                }
            }
        } else {
            if (this.stepTicks >= ErebosTuning.SCATTER_WAVE_INTERVAL) {
                pickExits();
                this.step = Step.WARN;
                this.stepTicks = 0;
            }
        }
        return this.velocity;
    }

    /** 随机挑两个<b>互不相同</b>的出口。 */
    private void pickExits() {
        var random = this.boss.getRandom();
        int n = Math.max(2, ErebosTuning.SCATTER_EXITS);
        this.exitA = random.nextInt(n);
        int b;
        do {
            b = random.nextInt(n);
        } while (b == this.exitA);
        this.exitB = b;
    }

    /** 两个出口，各自撒出一片扇形。 */
    private void fire() {
        if (!(this.boss.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 aimDir = this.aim.lengthSqr() < 1.0E-6D
                ? new Vec3(1.0D, 0.0D, 0.0D) : this.aim.normalize();

        int perPoint = Math.max(3, Math.min(ErebosTuning.SCATTER_PER_POINT,
                ErebosTuning.MAX_BOLTS_PER_VOLLEY / 2));
        // 两个出口 × 每点弹数 = 本轮开销。场上装不下就整轮跳过，
        // 而不是少发几颗 —— 预警线已经铺开了，稀稀拉拉的弹幕只会让玩家以为是自己卡了。
        if (boltBudgetLeft() < perPoint * 2) {
            return;
        }
        double spread = Math.toRadians(ErebosTuning.SCATTER_SPREAD_DEG);

        for (int exit : new int[]{this.exitA, this.exitB}) {
            int segment = ErebosTuning.scatterExitSegment(exit);
            Vec3 origin = this.boss.segmentPosition(segment);

            for (int i = 0; i < perPoint; i++) {
                // 扇形以瞄准方向为中心展开：
                // i = 中间那一发时偏移为 0，也就是"至少一发瞄准玩家"
                double offset = perPoint <= 1
                        ? 0.0D
                        : spread * i / (perPoint - 1.0D) - spread * 0.5D;
                Vec3 dir = fanAround(aimDir, offset);
                ErebosBolt bolt = ErebosBolt.plain(level, this.boss, origin, dir,
                        ErebosTuning.BOLT_SPEED);
                level.addFreshEntity(bolt);
            }
            ErebosFx.playShot(level, origin);
        }
    }

    private int between(int min, int max) {
        if (max <= min) {
            return Math.max(1, min);
        }
        return min + this.boss.getRandom().nextInt(max - min + 1);
    }
}
