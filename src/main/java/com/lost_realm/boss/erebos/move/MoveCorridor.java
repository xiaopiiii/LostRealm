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
 * <b>裂界回廊</b> —— 第三阶段最难的一招："转 + 射"同时进行。
 *
 * <h2>为什么它比星核脉冲难</h2>
 * 星核脉冲只有一个变量（环上的缺口在哪）；这一招有两个：
 * <b>四道激光在转</b>，同时<b>散射弹在撒</b>。玩家必须一边跟着激光转、
 * 一边避开慢速弹幕，两个动作的节奏还不一样。
 *
 * <h2>流程</h2>
 * <pre>
 *   围圈，圈心立起四向紫色激光
 *      ↓ 预警 1.25 秒
 *   激光开始转（1.4 度/tick，方向随机），持续 8 秒
 *      ↓ 期间从圈心分六轮撒出散射弹（每轮 16 发）
 *   激光停止、消散，收招
 * </pre>
 *
 * <h2>★ 为什么激光留了 8 秒</h2>
 * 原设计是 706 帧 ≈ 12 秒。压到 8 秒的理由是<b>体感</b>：
 * 在 MC 里，玩家"贴着激光走 12 秒"只做一件事，后半段会开始觉得无聊；
 * 8 秒刚好是"紧张但还没腻"的长度。散射弹那边六轮撑住了场面。
 */
public final class MoveCorridor extends ErebosMove {

    private int wavesLeft;
    private int timer;
    private Vec3 center = Vec3.ZERO;
    private double orbitDir = 1.0D;

    @Nullable
    private ErebosEffect ring;

    public MoveCorridor(ErebosBoss boss) {
        super(boss);
    }

    @Override
    public MoveKind kind() {
        return MoveKind.CORRIDOR;
    }

    @Override
    public String id() {
        return "corridor";
    }

    @Override
    public String langKey() {
        return "move.lost_realm.erebos.corridor";
    }

    @Override
    public boolean availableIn(ErebosPhase phase) {
        return phase == ErebosPhase.THREE;
    }

    @Override
    public boolean exposesCore() {
        // 激光旋转期间虫身在圈边盘旋，核心同样没有遮蔽 —— 但这一招压力已经很大，
        // 给玩家一个"边躲边打"的窗口，避免它变成纯挨打
        return true;
    }

    @Override
    public void begin(@Nullable Player target) {
        this.wavesLeft = ErebosTuning.CORRIDOR_WAVES;
        this.timer = 0;
        this.orbitDir = this.boss.getRandom().nextBoolean() ? 1.0D : -1.0D;

        Vec3 anchor = target != null ? target.position() : this.boss.position();
        this.center = new Vec3(anchor.x, anchor.y, anchor.z);

        if (this.boss.level() instanceof ServerLevel level) {
            this.ring = ErebosEffect.ring(level, this.boss, this.center, ErebosTuning.ARENA_RADIUS);
            level.addFreshEntity(this.ring);

            // 四向激光：从圈心发出，整体绕 Y 轴转
            double spin = ErebosTuning.CORRIDOR_SPIN
                    * (this.boss.getRandom().nextBoolean() ? 1.0D : -1.0D);
            Vec3 origin = this.center.add(0.0D, 1.2D, 0.0D);
            for (int i = 0; i < 4; i++) {
                ErebosEffect laser = ErebosEffect.laser(level, this.boss, origin,
                        i * 90.0F, 0.0F,
                        ErebosTuning.ARENA_RADIUS + 6.0D,
                        ErebosTuning.TAIL_LASER_HALF,
                        ErebosTuning.CORRIDOR_WARN,
                        ErebosTuning.CORRIDOR_ACTIVE,
                        ErebosTuning.BOLT_DAMAGE * 1.3F,
                        spin);
                level.addFreshEntity(laser);
            }
            ErebosFx.playPhaseShift(level, this.center);
        }
    }

    @Override
    public Vec3 tick(@Nullable Player target) {
        this.ticks++;
        this.timer++;

        double speed = ErebosTuning.CRUISE_SPEED * 1.05D;
        this.velocity = orbitTowards(this.center, ErebosTuning.ARENA_ORBIT_RADIUS,
                speed, this.orbitDir);

        this.boss.syncMove(kind());
        this.boss.syncAim(this.velocity.lengthSqr() > 1.0E-6D
                ? this.velocity.normalize() : new Vec3(0, 0, 1));
        this.boss.syncWarning(0);

        // 六轮散射，把 8 秒铺满
        int totalLifetime = ErebosTuning.CORRIDOR_WARN + ErebosTuning.CORRIDOR_ACTIVE;
        int interval = Math.max(1, totalLifetime / Math.max(1, ErebosTuning.CORRIDOR_WAVES));
        if (this.wavesLeft > 0 && this.timer >= interval) {
            this.timer = 0;
            this.wavesLeft--;
            scatter();
        }
        if (this.ticks >= totalLifetime + 20) {
            finish();
        }
        return this.velocity;
    }

    /** 从圈心向四周撒一轮中等密度的散射弹，留出可读的通道。 */
    private void scatter() {
        if (!(this.boss.level() instanceof ServerLevel level)) {
            return;
        }
        if (boltBudgetLeft() < ErebosTuning.CORRIDOR_BOLTS) {
            return;
        }
        int count = ErebosTuning.CORRIDOR_BOLTS;
        var random = this.boss.getRandom();
        double base = random.nextDouble() * Math.PI * 2.0D;
        // 留一个宽度随机的缺口，让每轮的可躲位置都不同
        int gapStart = random.nextInt(count);
        int gapSize = 2;

        Vec3 origin = this.center.add(0.0D, 1.2D, 0.0D);
        for (int i = 0; i < count; i++) {
            if ((i - gapStart + count) % count < gapSize) {
                continue;
            }
            double angle = base + Math.PI * 2.0D * i / count;
            Vec3 dir = new Vec3(Math.cos(angle), (random.nextDouble() - 0.5D) * 0.25D,
                    Math.sin(angle)).normalize();
            ErebosBolt bolt = ErebosBolt.plain(level, this.boss, origin, dir,
                            ErebosTuning.CORRIDOR_BOLT_SPEED)
                    .withLife(120);
            level.addFreshEntity(bolt);
        }
    }

    @Override
    protected void onFinished() {
        if (this.ring != null) {
            this.ring.discard();
            this.ring = null;
        }
    }
}
