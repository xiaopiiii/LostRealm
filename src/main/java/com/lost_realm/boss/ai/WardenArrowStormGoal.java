package com.lost_realm.boss.ai;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.lost_realm.boss.BossRegistry;
import com.lost_realm.boss.WardenPhase;
import com.lost_realm.boss.entity.WardenBoss;

/**
 * 技能①<b>箭雨反噬</b> —— 全屏落下箭矢,落点带预警。
 *
 * <h2>设计意图</h2>
 * 这是 Boss 的"走位考试":箭矢落点提前 1.5 秒显示预警圈(粒子),
 * 玩家必须持续移动才能规避。它同时承担<b>节奏调节</b>的作用 ——
 * 当玩家在地面正面硬刚时,这个技能逼迫他们离开舒适区。
 *
 * <h2>复用现有基建</h2>
 * 箭矢的生成与伤害结算复用了本模组 {@code skill/archer/SkillArrowRain}
 * 的思路(标记 + 倍率 + 突破无敌帧),但<b>不共享代码</b> ——
 * 因为那个类依赖勋章玩家的状态,而 Boss 的箭雨属于怪物行为,
 * 数据来源完全不同。这里是平行实现,避免耦合。
 *
 * <h2>与觉醒箭矢的差异</h2>
 * 玩家技能箭雨是"命中后概率触发",Boss 版是"定时主动释放"。
 * 前者是被动奖励,后者是主动施压。
 */
public class WardenArrowStormGoal extends WardenSkillGoal {

    /** 每根箭的伤害倍率(Boss 攻击力的比例)。 */
    private static final float ARROW_DAMAGE_MULT = 0.45F;

    /** 预警到落箭的延迟(tick)。1.5 秒 = 30 tick。 */
    private static final int WARN_DELAY = 30;

    /** 箭雨覆盖的圆半径。 */
    private static final int STORM_RADIUS = 12;

    /** 待落箭的落点(前摇期间标记,释放时执行)。 */
    private final List<BlockPos> pendingSpots = new ArrayList<>();

    public WardenArrowStormGoal(WardenBoss boss) {
        // 冷却 14 秒,前摇 2 秒(足够玩家看到预警并开始移动)
        super(boss, 280, 40);
    }

    @Override
    protected boolean canUseNow() {
        // 阶段Ⅱ之后才会使用,阶段Ⅰ 让玩家先熟悉基础招式
        return boss.getPhase() != WardenPhase.PHASE_1
                || boss.getHealth() / boss.getMaxHealth() < 0.85F;
    }

    @Override
    protected void onCastStart() {
        computeSpots();
        if (boss.level() instanceof ServerLevel sl) {
            sl.playSound(null, boss.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE,
                    SoundSource.HOSTILE, 3.0F, 1.2F);
        }
    }

    /** 在目标周围选一批落点。 */
    private void computeSpots() {
        pendingSpots.clear();
        LivingEntity target = boss.getTarget();
        if (target == null || !(boss.level() instanceof ServerLevel sl)) {
            return;
        }
        var random = sl.getRandom();
        // 以目标为中心撒点,越靠近中心越密 —— 玩家停下就会被砸
        for (int i = 0; i < 22; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double dist = Math.sqrt(random.nextDouble()) * STORM_RADIUS;
            double x = target.getX() + Math.cos(angle) * dist;
            double z = target.getZ() + Math.sin(angle) * dist;
            // 用 MOTION_BLOCKING 找地面高度,避免箭落在半空
            BlockPos ground = sl.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    BlockPos.containing(x, target.getY(), z));
            pendingSpots.add(ground);
        }
    }

    @Override
    protected void onCastTick(int elapsed) {
        // 前摇期间持续画预警圈(红色粉尘)
        if (!(boss.level() instanceof ServerLevel sl)) {
            return;
        }
        // 每 4 tick 画一次,减少粒子量
        if (elapsed % 4 != 0) {
            return;
        }
        for (BlockPos p : pendingSpots) {
            for (int i = 0; i < 4; i++) {
                double a = (i / 4.0D) * Math.PI * 2.0D;
                sl.sendParticles(ParticleTypes.CRIT,
                        p.getX() + 0.5D + Math.cos(a) * 1.2D,
                        p.getY() + 1.2D,
                        p.getZ() + 0.5D + Math.sin(a) * 1.2D,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    @Override
    protected void cast() {
        if (!(boss.level() instanceof ServerLevel sl)) {
            return;
        }
        float atk = (float) boss.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);

        for (BlockPos p : pendingSpots) {
            Arrow arrow = new Arrow(sl, boss);
            arrow.setPos(p.getX() + 0.5D, p.getY() + 8.0D, p.getZ() + 0.5D);
            // 垂直向下 —— 从上往下砸
            arrow.shoot(0.0D, -1.0D, 0.0D, 1.4F, 0.0F);
            arrow.setBaseDamage(atk * ARROW_DAMAGE_MULT);
            arrow.setCritArrow(false);
            // 不可拾取:否则玩家可以捡 Boss 的箭,失去紧张感
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            // 标记为 Boss 的箭,防止与玩家箭矢判定混淆
            arrow.addTag("lost_realm_boss_arrow");
            sl.addFreshEntity(arrow);
        }
        sl.playSound(null, boss.blockPosition(), SoundEvents.ARROW_SHOOT,
                SoundSource.HOSTILE, 3.0F, 0.7F);
        pendingSpots.clear();
    }

    @Override
    protected void onCastFinish() {
        pendingSpots.clear();
    }

    @Override
    protected void onStop() {
        pendingSpots.clear();
    }
}
