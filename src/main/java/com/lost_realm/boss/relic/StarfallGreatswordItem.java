package com.lost_realm.boss.relic;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * <b>断轨·裂星巨剑</b>（刃）—— 乌蠹诺斯遗物。
 *
 * <h2>原设计 → MC</h2>
 * 原设计是"发射三道穿透追踪星刃，每道首次命中追加三颗 65% 伤害追击星陨"。
 * 那套需要三套追踪实体，而这一件是<b>通关纪念品</b>：
 * 拿到它的人已经打完了整个整合包，此时需要的不是又一套要学的新机制，
 * 而是"一剑下去，周围什么都没了"的那种收尾快感。
 *
 * <p>所以这里的落点是<b>把星刃的意象压进一次挥击</b>：
 * 命中时对周围一圈敌人追加星陨伤害，并掀起冲击波把血少的怪直接打飞。
 *
 * <h2>为什么保留"裂"这个字</h2>
 * 它的追加伤害<b>只对同一个目标触发一次</b>（用无敌帧天然实现），
 * 所以"劈开"的感觉来自<b>单次的高爆发</b>，而不是"多段小伤害"。
 * 这与名字里的"断轨"是一致的 —— 一剑下去，轨道断了。
 */
public class StarfallGreatswordItem extends SwordItem {

    /** 基础攻击力 —— 明显高于下界合金剑（8）。 */
    private static final int ATTACK_DAMAGE = 22;

    /** 挥击速度：比原版剑略慢，体现"巨剑"的重量。 */
    private static final float ATTACK_SPEED = -2.6F;

    /** 星陨追加伤害（对周围敌人）。 */
    private static final float NOVA_DAMAGE = 14.0F;

    /** 星陨半径（格）。 */
    private static final double NOVA_RADIUS = 4.5D;

    public StarfallGreatswordItem(Properties properties) {
        super(Tiers.NETHERITE, ATTACK_DAMAGE, ATTACK_SPEED, properties);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel level) {
            // 星陨：对<b>周围其它</b>敌人追加一次范围伤害。
            // 对目标本身不再追加 —— 否则一剑两段伤害，手感会变成"两次小砍"
            AABB area = target.getBoundingBox().inflate(NOVA_RADIUS);
            for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, area)) {
                if (nearby == target || nearby == attacker || !nearby.isAlive()) {
                    continue;
                }
                if (attacker instanceof Player player && !player.canAttack(nearby)) {
                    continue;
                }
                nearby.hurt(level.damageSources().mobAttack(attacker), NOVA_DAMAGE);
                // 被星陨命中的小怪会被掀起来 —— 体量感来自这一下
                if (nearby.getMaxHealth() <= NOVA_DAMAGE * 1.5F) {
                    nearby.push(0.0D, 0.85D, 0.0D);
                }
            }
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    /** 手持时给一点点抗性 —— 巨剑也是盾，"断轨"这个名字该有挡一下的分量。 */
    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity,
                              int slot, boolean selected) {
        if (selected && entity instanceof Player player
                && level.getGameTime() % 40 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0,
                    true, false, false));
        }
        super.inventoryTick(stack, level, entity, slot, selected);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lost_realm.relic.legend")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("item.lost_realm.starfall_greatsword.tip",
                        (int) NOVA_DAMAGE, (int) NOVA_RADIUS)
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
