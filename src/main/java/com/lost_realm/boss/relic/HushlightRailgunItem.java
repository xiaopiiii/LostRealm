package com.lost_realm.boss.relic;

import java.util.List;
import java.util.function.Predicate;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

/**
 * <b>寂光·终末轨炮</b>（射手）—— 厄瑞玻斯遗物。
 *
 * <h2>原设计 → MC</h2>
 * 原设计"双轨穿透弹、65% 概率不消耗弹药、不使用蓄力机制"。
 * MC 里做成<b>弓的变体</b>，但把三条特性都保住：
 * <ul>
 *   <li><b>不蓄力</b>：{@code getUseDuration} 压到 1 —— 按下即射。</li>
 *   <li><b>穿透</b>：射出的箭强制带穿透附魔的效果（贯穿多个目标）。</li>
 *   <li><b>不消耗</b>：65% 概率返还箭矢。</li>
 * </ul>
 *
 * <h2>为什么它是"弓"而不是自造一把枪</h2>
 * MC 的箭矢有完整的弹道、重力、着火、药水效果、方块碰撞 ——
 * 自造弹道等于把这些全部重写一遍，而且和整合包里其它箭类模组不兼容。
 * 挂在弓这条线上，它天然吃到了所有现成的箭矢逻辑。
 */
public class HushlightRailgunItem extends BowItem {

    /** 不消耗箭矢的概率。原设计 65%。 */
    private static final float NO_AMMO_CHANCE = 0.65F;

    /** 拉弓耗时 —— 1 tick 就是"按下即射"，实现"不使用蓄力机制"。 */
    private static final int DRAW_TICKS = 1;

    public HushlightRailgunItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return DRAW_TICKS;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft) {
        if (!(user instanceof Player player)) {
            return;
        }
        // 从背包里找一支箭（不实际扣除，扣除与否由概率决定）
        ItemStack ammo = player.getProjectile(stack);
        if (ammo.isEmpty() && !player.getAbilities().instabuild) {
            return;
        }

        ArrowItem arrowItem = ammo.getItem() instanceof ArrowItem ai
                ? ai
                : (ArrowItem) net.minecraft.world.item.Items.ARROW;
        AbstractArrow arrow = arrowItem.createArrow(level, ammo.copy(), player);
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 3.6F, 0.4F);
        arrow.setCritArrow(true);
        // 穿透：让它能连穿几个目标 —— "终末轨炮"的名字该有贯穿的分量
        arrow.setPierceLevel((byte) 4);
        arrow.setBaseDamage(6.0D);

        if (!level.isClientSide) {
            level.addFreshEntity(arrow);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.6F, 0.7F);
        }

        // 65% 不消耗：不是"返还一支"，而是<b>根本不扣</b>——
        // 前者会让玩家的箭越用越多，后者才是原设计说的"概率不消耗"
        if (!player.getAbilities().instabuild
                && level.getRandom().nextFloat() > NO_AMMO_CHANCE) {
            ammo.shrink(1);
        }
        stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(player.getUsedItemHand()));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lost_realm.relic.legend")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("item.lost_realm.hushlight_railgun.tip",
                        (int) (NO_AMMO_CHANCE * 100))
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
