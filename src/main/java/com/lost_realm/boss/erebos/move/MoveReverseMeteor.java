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
 * <b>逆轨流星</b> —— 第三阶段，唯一一个"用几何本身出题"的招式。
 *
 * <h2>它考的不是反应，是站位</h2>
 * 两组平行弹幕从夹角 60° 或 90° 的方向同时压过来，中间留出一块
 * <b>平行四边形或正方形的安全区</b>。玩家的任务是在预警的 2 秒里
 * 找到那块地方并跑进去 —— 之后什么都不用做，站着就行。
 *
 * <h2>流程</h2>
 * <pre>
 *   随机一个方向，再取与它成 60°/90° 的第二个方向
 *      ↓ 两组平行弹幕各自的预警线平移 2 秒（只平移，不旋转）
 *   两组同时发射，每组 10 发，速度一致 → 安全区形状保持不变
 *      ↓ 间隔 2 秒
 *   下一波 …… 共 5~6 波
 * </pre>
 *
 * <h2>为什么预警线"只平移不旋转"</h2>
 * 原设计强调这一条。平移时两组线的相对位置保持不变，
 * 玩家能在两秒里从容看出"那块空隙在哪"；如果线会转，
 * 安全区就会动态变形，2 秒根本来不及算 —— 那就变成运气了。
 *
 * <h2>★ 10 发而不是 34 发</h2>
 * 原设计每方向 34 条弹道。MC 里 34×2×6 波 = 四百多个实体，
 * 服务器扛不住。砍到 10 发之后，通道宽度从 7 格变成约 5 格 ——
 * 仍然远大于玩家身位（0.6 格），躲闪体验基本不变。
 */
public final class MoveReverseMeteor extends ErebosMove {

    private int wavesLeft;
    private int timer;

    public MoveReverseMeteor(ErebosBoss boss) {
        super(boss);
    }

    @Override
    public MoveKind kind() {
        return MoveKind.METEOR;
    }

    @Override
    public String id() {
        return "meteor";
    }

    @Override
    public String langKey() {
        return "move.lost_realm.erebos.meteor";
    }

    @Override
    public boolean availableIn(ErebosPhase phase) {
        return phase == ErebosPhase.THREE;
    }

    @Override
    public void begin(@Nullable Player target) {
        var random = this.boss.getRandom();
        this.wavesLeft = ErebosTuning.METEOR_WAVES_MIN
                + random.nextInt(Math.max(1,
                        ErebosTuning.METEOR_WAVES_MAX - ErebosTuning.METEOR_WAVES_MIN + 1));
        this.timer = ErebosTuning.METEOR_WAVE_INTERVAL;
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

        if (this.wavesLeft > 0 && this.timer >= ErebosTuning.METEOR_WAVE_INTERVAL) {
            this.timer = 0;
            this.wavesLeft--;
            fireWave(target);
        }
        if (this.wavesLeft <= 0 && this.timer >= ErebosTuning.METEOR_WAVE_INTERVAL) {
            finish();
        }
        return this.velocity;
    }

    private void tickHeadMotion(@Nullable Player target) {
        if (target == null) {
            this.velocity = this.velocity.scale(0.94D);
            return;
        }
        double speed = ErebosTuning.CRUISE_SPEED;
        Vec3 from = this.boss.position().subtract(target.position());
        if (from.lengthSqr() < 1.0E-6D) {
            from = new Vec3(1, 0, 0);
        }
        from = from.normalize();
        Vec3 desired = distanceTo(target) < 18.0D
                ? from.scale(speed)
                : new Vec3(-from.z, 0.0D, from.x).normalize().scale(speed);
        this.velocity = turnTowards(this.velocity, desired,
                ErebosTuning.CRUISE_TURN, speed * 0.6D);
    }

    /**
     * 发射一波：两组平行弹幕，夹角 60° 或 90°。
     *
     * <p>两组的生成中心都取玩家当前位置 —— 这样安全区必然落在玩家附近，
     * 而不是飘到场地另一头去（那样玩家只能干看着）。
     */
    private void fireWave(@Nullable Player target) {
        if (!(this.boss.level() instanceof ServerLevel level)) {
            return;
        }
        if (boltBudgetLeft() < ErebosTuning.METEOR_PER_SIDE * 2) {
            return;
        }
        var random = this.boss.getRandom();
        Vec3 anchor = target != null ? target.position() : this.boss.position();

        double base = random.nextDouble() * Math.PI * 2.0D;
        double second = base + (random.nextBoolean()
                ? Math.toRadians(60.0D) : Math.toRadians(90.0D));

        fireOneSide(level, anchor, base);
        fireOneSide(level, anchor, second);
        ErebosFx.playEffectFire(level, anchor);
    }

    /**
     * 沿方向 {@code travel} 飞的一排平行弹幕。
     *
     * <p>排列方向是 {@code travel} 的水平垂线。整排的<b>中点</b>对准玩家 ——
     * 于是玩家永远站在"通道正中"，一组安全区必然包含他当前所在的位置附近，
     * 他只需要决定"往左还是往右挪一点"。
     */
    private void fireOneSide(ServerLevel level, Vec3 anchor, double travel) {
        int count = ErebosTuning.METEOR_PER_SIDE;
        double spacing = ErebosTuning.JUDGE_SPACING;

        Vec3 dir = new Vec3(Math.cos(travel), 0.0D, Math.sin(travel)).normalize();
        Vec3 side = new Vec3(-dir.z, 0.0D, dir.x).normalize();
        // 出发点在玩家"上游" 46 格处
        Vec3 origin = anchor.subtract(dir.scale(46.0D)).add(0.0D, 2.0D, 0.0D);

        for (int i = 0; i < count; i++) {
            double offset = (i - (count - 1) / 2.0D) * spacing;
            Vec3 pos = origin.add(side.scale(offset));
            ErebosBolt bolt = ErebosBolt.plain(level, this.boss, pos,
                            dir, ErebosTuning.METEOR_SPEED)
                    .withLife(ErebosTuning.METEOR_LIFE);
            level.addFreshEntity(bolt);
        }
    }
}
