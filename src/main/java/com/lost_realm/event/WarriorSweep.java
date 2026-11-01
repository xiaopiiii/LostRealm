package com.lost_realm.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;

import com.lost_realm.config.MedalsConfig;
import com.lost_realm.item.WarriorCharmType;

/**
 * 横扫之刃 —— 由 {@code PlayerMixin} 在原版 {@code Player#attack} 返回后调用。
 *
 * <h2>为什么不去改原版的横扫判定</h2>
 * 原版的横扫写死在 {@code Player#attack} 里：<b>疾跑时不横扫</b>（改成击退攻击）、
 * 半径固定 3 格。想改这两条，得去动那一坨局部变量组合的判定 ——
 * 既脆弱（别的模组也在改同一个方法），又容易把原版手感弄坏。
 *
 * <p>这里改用「<b>打补丁而不是改代码</b>」的思路：原版该怎么扫就怎么扫，
 * 我们只在它扫完之后，把<b>本 tick 还没受伤的目标</b>补上一次 ——
 * 于是无论原版这次扫了还是没扫（疾跑、离地、刚起步…都算），结果都正确。
 *
 * <p>早先是按「疾跑 / 非疾跑」去推断原版扫没扫、再决定补内圈还是外圈，
 * 实机上不可靠：疾跑状态是客户端同步过来的，攻击那一帧未必是最新值。
 * 现在改成直接观测 {@link WarriorCharmHandler#hitThisTick} —— 谁被原版扫过就跳过谁。
 *
 * <p>伤害公式沿用原版的 {@code 1.0 + 横扫附魔比率 × 攻击伤害}，
 * 因此有没有这枚饰品，原版那部分的行为完全不变。
 */
public final class WarriorSweep {

    /**
     * 原版 {@code attack} 结束后调用。
     *
     * @param player       攻击者
     * @param target       本次的主目标
     * @param wasSprinting 攻击发起时是否在疾跑（仅用于日志/调试，判定不再依赖它）
     */
    public static void onAttackFinished(Player player, Entity target, boolean wasSprinting) {
        if (!(player instanceof ServerPlayer server) || target == null) {
            return;
        }
        if (!WarriorCharmType.SWEEPING.isActive(server)) {
            return;
        }

        double radius = Math.max(1.0D, MedalsConfig.WARRIOR_SWEEP_RADIUS.get());
        double radiusSqr = radius * radius;

        float attackDamage = (float) server.getAttributeValue(Attributes.ATTACK_DAMAGE);
        // 与原版一致的横扫伤害公式(无横扫附魔时 = 1.0)
        float sweepDamage = 1.0F + EnchantmentHelper.getSweepingDamageRatio(server) * attackDamage;

        AABB box = target.getBoundingBox().inflate(radius);
        for (LivingEntity victim : server.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (victim == server || victim == target || server.isAlliedTo(victim)) {
                continue;
            }
            if (victim instanceof ArmorStand stand && stand.isMarker()) {
                continue;
            }
            if (server.distanceToSqr(victim) > radiusSqr) {
                continue;
            }
            // ★ 原版这一下已经打过它了（横扫或主目标），别再补一刀
            if (com.lost_realm.event.WarriorCharmHandler.hitThisTick(server, victim)) {
                continue;
            }
            // 与原版横扫一致的击退方向:以玩家为原点推出去
            double dx = victim.getX() - server.getX();
            double dz = victim.getZ() - server.getZ();
            victim.knockback(0.4F, dx, dz);
            victim.hurt(server.damageSources().playerAttack(server), sweepDamage);
        }
    }

    private WarriorSweep() {}
}
