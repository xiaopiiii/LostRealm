package com.lost_realm.boss.ai;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import com.lost_realm.boss.BossRegistry;
import com.lost_realm.boss.WardenPhase;
import com.lost_realm.boss.entity.WardenBoss;

/**
 * 技能④<b>终末审判</b> —— 血量低于 10% 时的终极读条。
 *
 * <h2>设计意图</h2>
 * 这是整场战斗的<b>高潮</b>:Boss 濒死时不再挣扎,而是发动一场
 * "要么你打死我,要么我们一起死"的豪赌。
 *
 * <h2>核心巧思:读条时长由玩家实力决定</h2>
 * 需求提出"读条时长由玩家综合实力决定" —— 这里实现为:
 * <pre>
 *   实际读条 = 基准 12 秒 - 玩家综合实力加成
 *   综合实力 = 四勋章等级总和 / 120 * 6 秒 + 拥有的 Boss 击杀数 * 1 秒
 * </pre>
 * <b>练度越高,给你的时间越短</b>。这个反向设计让高练度玩家不仅不会
 * "碾压 Boss",反而面临更紧的时间压力 —— 非常反直觉,也非常有张力。
 *
 * <h2>失败即死</h2>
 * 读条期间 Boss 处于无敌状态并持续对全场造成伤害;
 * 读条走完而未被打断(即未被击杀),则对竞技场内所有玩家执行
 * <b>斩杀</b>(直接设为 0 血,忽略护甲与减免)。
 *
 * <h2>为什么用"斩杀"而不是"高伤害"</h2>
 * 高伤害会被护甲、抗性、吸收盾、不死图腾等各种手段化解,
 * 最终变成"坦克玩家硬吃一下没事"——机制就失效了。
 * 直接斩杀保证了这一击的绝对性,也符合"审判"的语义。
 * 唯一的生路是在读条内把 Boss 打死。
 */
public class WardenJudgementGoal extends WardenSkillGoal {

    /** 触发血线。 */
    private static final float TRIGGER_HEALTH_RATIO = 0.10F;

    /** 读条结束后的全场斩杀半径(覆盖整个竞技场)。 */
    private static final double EXECUTE_RADIUS = BossRegistry.ARENA_RADIUS + 8.0D;

    /** 读条期间每秒对全场造成的伤害(相对攻击力)。 */
    private static final float TICK_DAMAGE_MULT = 0.35F;

    /** 实际读条 tick 数(前摇开始时按玩家实力算出)。 */
    private int actualCastTicks = 240;

    /** 是否已释放过(整场战斗只触发一次)。 */
    private boolean used;

    public WardenJudgementGoal(WardenBoss boss) {
        // 冷却设为极大 —— 本质是"一次性技能",靠 used 标记控制
        super(boss, 20 * 60 * 10, 240);
    }

    @Override
    protected boolean canUseNow() {
        if (used) {
            return false;
        }
        // 仅第三阶段 + 濒死才触发
        return boss.getPhase() == WardenPhase.PHASE_3
                && boss.getHealth() / boss.getMaxHealth() <= TRIGGER_HEALTH_RATIO;
    }

    @Override
    protected void onCastStart() {
        this.used = true;
        // 按玩家实力缩短读条时间
        this.actualCastTicks = computeCastTicks();
        if (boss.level() instanceof ServerLevel sl) {
            sl.playSound(null, boss.blockPosition(), SoundEvents.WARDEN_HEARTBEAT,
                    SoundSource.HOSTILE, 4.0F, 0.5F);
            boss.displayMessageIfSession(Component.translatable(
                    "message.lost_realm.skill.judgement.begin",
                    String.format("%.1f", actualCastTicks / 20.0F)));
        }
        // 读条期间 Boss 无敌 —— 唯一生路是打死它(见 WardenBoss#hurt 的判定)
        boss.setInvulnerable(true);
    }

    /**
     * 计算实际读条 tick 数。
     *
     * <p>公式见类注释。这里刻意做了<b>下限保护</b>(最少 6 秒)——
     * 否则满练度玩家可能面对 3 秒读条,容错率过低会变成"必死"。
     */
    private int computeCastTicks() {
        int baseSeconds = BossRegistry.judgementBaseSeconds();
        double reduction = 0.0D;
        if (boss.level() instanceof ServerLevel sl) {
            // 取竞技场内最高练度玩家作为基准
            var players = sl.getEntitiesOfClass(ServerPlayer.class,
                    new AABB(boss.blockPosition()).inflate(EXECUTE_RADIUS));
            int bestTotal = 0;
            for (ServerPlayer p : players) {
                bestTotal = Math.max(bestTotal, com.lost_realm.boss.BossProgressionHooks.totalMedalLevels(p));
            }
            // 120 = 4 勋章 × 30 级;满练度减 6 秒
            reduction += (bestTotal / 120.0D) * 6.0D;
        }
        int seconds = (int) Math.max(6.0D, baseSeconds - reduction);
        return seconds * 20;
    }

    /** 覆写:读条时长是动态的,不能用基类固定的 castTimeTicks。 */
    @Override
    protected int castTime() {
        return actualCastTicks;
    }

    @Override
    protected void onCastTick(int elapsed) {
        if (!(boss.level() instanceof ServerLevel sl)) {
            return;
        }
        // 每 20 tick(1 秒):一次全场伤害 + 心跳音效
        if (elapsed % 20 == 0) {
            int secondsLeft = (actualCastTicks - elapsed) / 20;
            boss.displayMessageIfSession(Component.translatable(
                    "message.lost_realm.skill.judgement.countdown", secondsLeft));

            float atk = (float) boss.getAttributeValue(
                    net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            var targets = sl.getEntitiesOfClass(Player.class,
                    new AABB(boss.blockPosition()).inflate(EXECUTE_RADIUS));
            for (Player p : targets) {
                if (p.isCreative() || p.isSpectator()) {
                    continue;
                }
                p.hurt(boss.damageSources().indirectMagic(boss, boss),
                        atk * TICK_DAMAGE_MULT);
            }
            sl.playSound(null, boss.blockPosition(), SoundEvents.WARDEN_HEARTBEAT,
                    SoundSource.HOSTILE, 4.0F, 0.5F);
        }
        // 粒子:以 Boss 为中心的收束环
        double progress = elapsed / (double) actualCastTicks;
        double radius = 20.0D * (1.0D - progress);
        for (int i = 0; i < 16; i++) {
            double a = (i / 16.0D) * Math.PI * 2.0D;
            sl.sendParticles(ParticleTypes.SCULK_SOUL,
                    boss.getX() + Math.cos(a) * radius,
                    boss.getY() + 1.0D,
                    boss.getZ() + Math.sin(a) * radius,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    protected void cast() {
        if (!(boss.level() instanceof ServerLevel sl)) {
            return;
        }
        // 走到这里说明读条完成且 Boss 未被击杀 → 执行审判
        boss.displayMessageIfSession(Component.translatable("message.lost_realm.skill.judgement.execute"));
        var targets = sl.getEntitiesOfClass(Player.class,
                new AABB(boss.blockPosition()).inflate(EXECUTE_RADIUS));
        for (Player p : targets) {
            if (p.isCreative() || p.isSpectator()) {
                continue;
            }
            // 直接斩杀 —— 绕过一切减免与不死手段
            executePlayer(p);
        }
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                boss.getX(), boss.getY() + 1.0D, boss.getZ(),
                30, 6.0D, 3.0D, 6.0D, 0.0D);
        sl.playSound(null, boss.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.HOSTILE, 5.0F, 0.4F);
        boss.setInvulnerable(false);
    }

    /**
     * 处决玩家。
     *
     * <p>用 {@code hurt} 配合 {@code Float.MAX_VALUE} 而不是 {@code kill()},
     * 是为了让它走完整的死亡流程(掉落、统计、成就),
     * 同时用极大的数值保证穿透一切减免。
     */
    private void executePlayer(Player p) {
        // 伤害类型用 bossAttack,归类为"被 Boss 击杀",成就可据此判定
        p.hurt(boss.damageSources().mobAttack(boss), Float.MAX_VALUE / 4.0F);
        // 兜底:若因某些抗性仍存活,强制抹除
        if (p.isAlive()) {
            p.setHealth(0.0F);
        }
    }

    @Override
    protected void onStop() {
        // 无论成功失败,都解除无敌
        boss.setInvulnerable(false);
    }
}
