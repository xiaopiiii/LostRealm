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
 * <b>囚星天环</b> —— 第三阶段的围圈招，第一个真正"把你关起来"的招式。
 *
 * <h2>限制圈不是惩罚，是舞台</h2>
 * 原设计特意写清楚："限制圈没有伤害、持续扣血或激怒惩罚。"
 * 它的唯一作用是<b>把可躲范围收窄</b>，让原本"跑远点就行"的弹幕
 * 突然变得必须认真处理。所以这里只做位置纠正，一点伤害都不给。
 *
 * <h2>流程</h2>
 * <pre>
 *   在你脚下立起限制圈，虫身绕圈盘旋（约 3 秒绕到位，过程可见）
 *      ↓ 共四波
 *   每波从圆周上生成 18 发星弹，朝圈内飘去，留一个通道
 *      ↓ 最后一波放完收招
 * </pre>
 *
 * <h2>为什么这一招让星核暴露</h2>
 * 围圈时虫身是"摊开"的，物理上没有藏住核心的余地。这给了第三阶段
 * 一个明确的节奏反转：<b>看到圈立起来，就该停止逃跑、转为输出</b>。
 */
public final class MoveStarRing extends ErebosMove {

    private int wavesLeft;
    private int timer;
    private Vec3 center = Vec3.ZERO;
    private double orbitDir = 1.0D;

    @Nullable
    private ErebosEffect ring;

    public MoveStarRing(ErebosBoss boss) {
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
        return "move.lost_realm.erebos.ring";
    }

    /** 第三阶段的核心招式之一。 */
    @Override
    public boolean availableIn(ErebosPhase phase) {
        return phase == ErebosPhase.THREE;
    }

    @Override
    public boolean exposesCore() {
        return true;
    }

    @Override
    public void begin(@Nullable Player target) {
        this.wavesLeft = ErebosTuning.RING_WAVES;
        this.timer = ErebosTuning.RING_WAVE_INTERVAL;
        this.orbitDir = this.boss.getRandom().nextBoolean() ? 1.0D : -1.0D;

        Vec3 anchor = target != null ? target.position() : this.boss.position();
        // 原设计："在玩家当时所在位置建立固定世界坐标的限制圈" —— 落地即钉死
        this.center = new Vec3(anchor.x, anchor.y, anchor.z);

        if (this.boss.level() instanceof ServerLevel level) {
            this.ring = ErebosEffect.ring(level, this.boss, this.center, ErebosTuning.ARENA_RADIUS);
            level.addFreshEntity(this.ring);
            ErebosFx.playPhaseShift(level, this.center);
        }
    }

    @Override
    public Vec3 tick(@Nullable Player target) {
        this.ticks++;
        this.timer++;

        // 沿圈盘旋 —— 不是瞬间摆成圆，而是"飞着飞着就围上了"
        double speed = ErebosTuning.CRUISE_SPEED * 1.15D;
        this.velocity = orbitTowards(this.center, ErebosTuning.ARENA_ORBIT_RADIUS,
                speed, this.orbitDir);

        this.boss.syncMove(kind());
        this.boss.syncAim(this.velocity.lengthSqr() > 1.0E-6D
                ? this.velocity.normalize() : new Vec3(0, 0, 1));
        this.boss.syncWarning(0);

        if (this.wavesLeft > 0 && this.timer >= ErebosTuning.RING_WAVE_INTERVAL) {
            this.timer = 0;
            this.wavesLeft--;
            spawnWave();
        }
        if (this.wavesLeft <= 0 && this.timer >= ErebosTuning.RING_WAVE_INTERVAL) {
            finish();
        }
        return this.velocity;
    }

    /** 从圆周上生成一波星弹，朝圈内飘去，留一个通道。 */
    private void spawnWave() {
        if (!(this.boss.level() instanceof ServerLevel level)) {
            return;
        }
        if (boltBudgetLeft() < ErebosTuning.RING_BOLTS) {
            return;
        }
        int count = ErebosTuning.RING_BOLTS;
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
            Vec3 pos = core.add(Math.cos(angle) * ErebosTuning.RING_SPAWN_RADIUS,
                    2.5D, Math.sin(angle) * ErebosTuning.RING_SPAWN_RADIUS);
            Vec3 inward = core.add(0.0D, 1.5D, 0.0D).subtract(pos);
            if (inward.lengthSqr() < 1.0E-6D) {
                continue;
            }
            ErebosBolt bolt = ErebosBolt.plain(level, this.boss, pos,
                            inward.normalize(), ErebosTuning.RING_BOLT_SPEED)
                    .withLife(140);
            level.addFreshEntity(bolt);
        }
        ErebosFx.playShot(level, core.add(0.0D, 1.0D, 0.0D));
    }

    @Override
    protected void onFinished() {
        if (this.ring != null) {
            this.ring.discard();
            this.ring = null;
        }
    }
}
