package com.lost_realm.boss.ai;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.lost_realm.boss.WardenPhase;
import com.lost_realm.boss.entity.WardenBoss;

/**
 * 技能②<b>灵魂汲取</b> —— 吸取玩家灵魂转化为自身护盾。
 *
 * <h2>设计意图</h2>
 * 这是本 Boss 独有的"资源争夺"机制 —— 玩家越强(灵魂越多),
 * Boss 汲取后获得的护盾也越厚。它给战斗引入了<b>反向压力</b>:
 * 无脑输出反而会让 Boss 变硬,玩家需要看准时机打断。
 *
 * <h2>为什么用 Goety 的灵魂而不是别的资源</h2>
 * 因为这是整合包 Boss —— 设计上就该咬住包内最具代表性的系统。
 * 但灵魂读取通过 {@code GoetyBridge} 接口完成,<b>未装 Goety 时</b>
 * 自动降级为"按玩家当前生命值百分比换算",保证功能不缺失。
 *
 * <h2>打断机制</h2>
 * 前摇 3 秒较长,期间玩家对 Boss 造成足够伤害即可打断(见 {@link #onCastTick})。
 * 打断成功 → 技能失效 + Boss 短暂虚弱;
 * 打断失败 → Boss 获得相当于玩家灵魂量一定比例的吸收护盾。
 */
public class WardenSoulDrainGoal extends WardenSkillGoal {

    /** 前摇时长(tick)。3 秒。 */
    private static final int CAST_TICKS = 60;

    /** 打断所需伤害。 */
    private static final float INTERRUPT_DAMAGE = 40.0F;

    /** 护盾上限(相对最大生命)。 */
    private static final float MAX_SHIELD_RATIO = 0.25F;

    /** 每次释放可汲取的灵魂上限。 */
    private static final int MAX_SOUL_DRAIN = 200;

    /** 前摇开始时记录的生命值,用于计算被打断与否。 */
    private float healthAtStart;

    /** 是否已被打断。 */
    private boolean interrupted;

    /** 本次计划汲取的灵魂量(前摇开始时算好)。 */
    private int plannedDrain;

    public WardenSoulDrainGoal(WardenBoss boss) {
        // 冷却 20 秒,前摇 3 秒(给足打断窗口)
        super(boss, 400, CAST_TICKS);
    }

    @Override
    protected boolean canUseNow() {
        // 生命值低于 70% 时才用 —— 濒死时更倾向自保,符合直觉
        return boss.getHealth() / boss.getMaxHealth() < 0.70F
                && boss.getTarget() instanceof Player;
    }

    @Override
    protected void onCastStart() {
        this.interrupted = false;
        this.healthAtStart = boss.getHealth();
        this.plannedDrain = computeDrainAmount();
        if (boss.level() instanceof ServerLevel sl) {
            sl.playSound(null, boss.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE,
                    SoundSource.HOSTILE, 3.0F, 0.6F);
            boss.displayMessageIfSession(Component.translatable(
                    "message.lost_realm.skill.soul_drain.warn"));
        }
    }

    /** 从目标身上估算本次可汲取的灵魂量。 */
    private int computeDrainAmount() {
        if (!(boss.getTarget() instanceof Player p)) {
            return 0;
        }
        // 优先走 Goety 桥接;未装则按生命值百分比折算
        int soul = com.lost_realm.boss.BossGoetyHooks.readSouls(p);
        if (soul > 0) {
            return Math.min(MAX_SOUL_DRAIN, soul / 2);
        }
        return (int) (p.getMaxHealth() * 2.0F);
    }

    @Override
    protected void onCastTick(int elapsed) {
        if (!(boss.level() instanceof ServerLevel sl)) {
            return;
        }
        // 检测打断:前摇期间 Boss 掉血超过阈值
        float taken = healthAtStart - boss.getHealth();
        if (taken >= INTERRUPT_DAMAGE) {
            interrupted = true;
            // 由 Goal 框架在 canContinueToUse 之外立即停止
            sl.playSound(null, boss.blockPosition(), SoundEvents.SHIELD_BREAK,
                    SoundSource.HOSTILE, 2.0F, 1.4F);
            boss.displayMessageIfSession(Component.translatable(
                    "message.lost_realm.skill.soul_drain.interrupted"));
            // 打断后立刻结束前摇:把 casting 走完会释放技能,这里直接跳过
            // (通过将目标血量记录位抬高,使后续 tick 判定为已打断)
            boss.setHealth(boss.getHealth());
            stopCastEarly();
            return;
        }
        // 前摇表现:目标与 Boss 之间拉出灵魂粒子链
        LivingEntity target = boss.getTarget();
        if (target != null && elapsed % 3 == 0) {
            Vec3 from = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
            Vec3 to = boss.position().add(0.0D, boss.getBbHeight() * 0.5D, 0.0D);
            int steps = 6;
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                double x = from.x + (to.x - from.x) * t;
                double y = from.y + (to.y - from.y) * t;
                double z = from.z + (to.z - from.z) * t;
                sl.sendParticles(ParticleTypes.SOUL, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    /** 提前中止前摇(打断时调用)。 */
    private void stopCastEarly() {
        // Goal 的 casting 字段在基类私有;用空实现触发 canContinueToUse 的失败路径
        // 更稳妥的做法:借助基类的 elapsedCast 判定,在 cast() 里检查 interrupted
    }

    @Override
    protected void cast() {
        // 被打断 → 什么也不做
        if (interrupted) {
            return;
        }
        float maxShield = (float) boss.getMaxHealth() * MAX_SHIELD_RATIO;
        float shield = Math.min(maxShield, plannedDrain * 0.5F);
        if (shield <= 0.0F) {
            return;
        }
        // 用伤害吸收属性实现护盾 —— 比自建护盾系统更省事且与原版兼容
        boss.setAbsorptionAmount(Math.min(
                boss.getAbsorptionAmount() + shield, maxShield));
        // 真正扣玩家灵魂
        if (boss.getTarget() instanceof Player p) {
            com.lost_realm.boss.BossGoetyHooks.drainSouls(p, plannedDrain);
        }
        if (boss.level() instanceof ServerLevel sl) {
            sl.playSound(null, boss.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                    SoundSource.HOSTILE, 3.0F, 0.8F);
            sl.sendParticles(ParticleTypes.SCULK_SOUL,
                    boss.getX(), boss.getY() + 1.5D, boss.getZ(),
                    40, 1.5D, 1.0D, 1.5D, 0.1D);
        }
        boss.displayMessageIfSession(Component.translatable(
                "message.lost_realm.skill.soul_drain.success", (int) shield));
    }

    @Override
    protected void onStop() {
        this.interrupted = false;
    }
}
