package com.lost_realm.boss.ai;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import com.lost_realm.boss.WardenPhase;
import com.lost_realm.boss.entity.WardenBoss;

/**
 * 技能③<b>相位跳跃</b> —— 瞬移并留下残影,残影引爆。
 *
 * <h2>设计意图</h2>
 * 解决 Boss 战最常见的枯燥感:"追着 Boss 跑"。
 * 瞬移把战斗从"站桩对拼"变成"位置博弈" ——
 * 玩家必须预判 Boss 会跳到哪、并且不能站在残影上。
 *
 * <h2>残影的伤害逻辑</h2>
 * 瞬移瞬间,在原位置留下一个"残影标记"(用粒子表现),
 * 1 秒后引爆,对 4 格内所有玩家造成伤害 + 击退。
 * 这形成一个"看到残影就往外跑"的条件反射。
 *
 * <h2>为什么瞬移到玩家身后</h2>
 * 瞬移到目标<b>背后</b>而不是随机位置 —— 这让玩家有明确的对抗手段:
 * 转身即可应对。随机瞬移会让战斗变成纯粹的运气游戏。
 */
public class WardenPhaseShiftGoal extends WardenSkillGoal {

    /** 残影引爆延迟(tick)。1 秒。 */
    private static final int DETONATE_DELAY = 20;

    /** 残影爆炸半径。 */
    private static final double BLAST_RADIUS = 4.0D;

    /** 爆炸伤害倍率(相对攻击力)。 */
    private static final float BLAST_DAMAGE_MULT = 0.9F;

    /** 残影位置(前摇结束时记录)。 */
    private Vec3 ghostPos;

    /** 引爆倒计时(-1 表示无待引爆残影)。 */
    private int detonateTimer = -1;

    public WardenPhaseShiftGoal(WardenBoss boss) {
        // 冷却 11 秒,前摇 0.75 秒(瞬移要快,否则失去突然性)
        super(boss, 220, 15);
    }

    @Override
    protected boolean canUseNow() {
        // 只有阶段Ⅱ/Ⅲ 使用 —— 阶段Ⅰ 是"教学阶段",不该有位移
        return boss.getPhase() != WardenPhase.PHASE_1
                && boss.distanceToSqr(boss.getTarget()) > 9.0D;   // 离得太近就不跳
    }

    @Override
    protected void onCastStart() {
        if (boss.level() instanceof ServerLevel sl) {
            sl.playSound(null, boss.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                    SoundSource.HOSTILE, 3.0F, 0.7F);
        }
    }

    @Override
    protected void cast() {
        if (!(boss.level() instanceof ServerLevel sl)) {
            return;
        }
        LivingEntity target = boss.getTarget();
        if (target == null) {
            return;
        }

        // ① 记录残影位置(当前所在处)
        this.ghostPos = boss.position();
        this.detonateTimer = DETONATE_DELAY;

        // ② 瞬移到目标背后
        Vec3 behind = behindTarget(target);
        // 落点必须仍在竞技场内 —— 否则 Boss 会跳出场地
        if (!inArena(behind)) {
            behind = target.position();   // 兜底:直接贴脸
        }
        boss.teleportTo(behind.x, behind.y, behind.z);
        boss.facePoint(target.position());

        // ③ 瞬移特效
        sl.sendParticles(ParticleTypes.PORTAL,
                ghostPos.x, ghostPos.y + 1.0D, ghostPos.z, 60, 0.6D, 1.0D, 0.6D, 0.5D);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL,
                behind.x, behind.y + 1.0D, behind.z, 60, 0.6D, 1.0D, 0.6D, 0.5D);
        sl.playSound(null, net.minecraft.core.BlockPos.containing(behind),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 3.0F, 1.3F);
    }

    /** 目标背后 3 格、同高度。 */
    private Vec3 behindTarget(LivingEntity target) {
        Vec3 look = target.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        if (flat.lengthSqr() < 1.0E-6D) {
            flat = new Vec3(0.0D, 0.0D, 1.0D);
        }
        flat = flat.normalize().scale(-3.0D);
        return target.position().add(flat);
    }

    private boolean inArena(Vec3 p) {
        var c = boss.arenaCenter();
        double dx = p.x - (c.getX() + 0.5D);
        double dz = p.z - (c.getZ() + 0.5D);
        int r = com.lost_realm.boss.BossRegistry.ARENA_RADIUS - 4;
        return dx * dx + dz * dz <= (double) (r * r);
    }

    @Override
    protected void onCastTick(int elapsed) {
        // 残影引爆倒计时在前摇期间推进(瞬移后立刻开始计时)
        tickDetonation();
    }

    @Override
    protected void onCastFinish() {
        // 技能释放后仍要继续跑引爆计时 —— 交给 aiStep 驱动更合适,
        // 这里用 stop() 后的持续 tick 见 onStop
    }

    @Override
    public void tick() {
        super.tick();
        // Goal 未激活时也要推进引爆(残影是独立于 Goal 生命周期的)
        if (detonateTimer < 0) {
            tickDetonation();
        }
    }

    /** 推进并执行残影引爆。 */
    private void tickDetonation() {
        if (detonateTimer < 0) {
            return;
        }
        if (!(boss.level() instanceof ServerLevel sl)) {
            detonateTimer = -1;
            return;
        }
        // 残影汇聚粒子
        if (detonateTimer % 4 == 0 && ghostPos != null) {
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    ghostPos.x, ghostPos.y + 1.0D, ghostPos.z,
                    6, 0.5D, 0.8D, 0.5D, 0.02D);
        }
        detonateTimer--;
        if (detonateTimer <= 0) {
            detonate(sl);
            detonateTimer = -1;
            ghostPos = null;
        }
    }

    /** 残影爆炸:对范围内玩家造成伤害 + 击退。 */
    private void detonate(ServerLevel sl) {
        if (ghostPos == null) {
            return;
        }
        float atk = (float) boss.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        var hits = sl.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,
                new net.minecraft.world.phys.AABB(
                        ghostPos.x - BLAST_RADIUS, ghostPos.y - BLAST_RADIUS, ghostPos.z - BLAST_RADIUS,
                        ghostPos.x + BLAST_RADIUS, ghostPos.y + BLAST_RADIUS, ghostPos.z + BLAST_RADIUS));
        for (var p : hits) {
            if (p.isCreative() || p.isSpectator()) {
                continue;
            }
            // 伤害源归因给 Boss,便于统计与勋章结算
            var src = boss.damageSources().mobAttack(boss);
            p.hurt(src, atk * BLAST_DAMAGE_MULT);
            // 向外击退
            Vec3 dir = p.position().subtract(ghostPos);
            Vec3 flat = new Vec3(dir.x, 0.0D, dir.z);
            if (flat.lengthSqr() > 1.0E-6D) {
                flat = flat.normalize().scale(0.9D);
                p.push(flat.x, 0.45D, flat.z);
                p.hurtMarked = true;
            }
        }
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                ghostPos.x, ghostPos.y + 0.5D, ghostPos.z, 2, 0.0D, 0.0D, 0.0D, 0.0D);
        sl.playSound(null, net.minecraft.core.BlockPos.containing(ghostPos),
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.5F, 0.9F);
    }

    @Override
    protected void onStop() {
        // 残影继续留在场上(不随 Goal 结束清除)—— 这是刻意的:
        // 玩家可能因为残影而调整走位,提前清除会削弱机制
    }
}
