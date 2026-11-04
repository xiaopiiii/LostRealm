package com.lost_realm.boss.relic;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * <b>末光壁垒</b>（坦克）—— 乌蠹诺斯遗物，<b>本模组补的第五件</b>。
 *
 * <h2>为什么必须有这一件</h2>
 * 原设计掉落的是泰拉瑞亚的四职业武器。但 MC 这边的 Lost Realm 有
 * <b>五个</b>职业，照搬四件会让坦克成为唯一一个"打完最终 Boss
 * 什么都没拿到"的职业 —— 那不是"这个职业不需要"，
 * 而是"这个职业被忘了"。
 *
 * <h2>它和其他四件的定位差别</h2>
 * 其他四件都是"更强的输出"，而坦克要的从来不是输出 ——
 * 是<b>站着不动也打不死</b>。所以这一件干脆不给任何攻击加成，
 * 只做一件事：把举盾这件事做到极致。
 *
 * <h2>怎么实现"举盾时极高减伤"</h2>
 * 原版盾牌格挡已经能免掉大部分正面伤害，但挡不住爆炸与背后袭击。
 * 这里在 {@code inventoryTick} 里给举盾者持续挂抗性 ——
 * 于是四面八方的伤害都被削，而不只是举盾那一个方向。
 * 用"持续 buff"而不是"事件拦截"，是为了不干扰整合包里其它
 * 处理伤害的模组（它们看到的是一个老老实实加了抗性的玩家）。
 */
public class BulwarkItem extends ShieldItem {

    /** 抗性等级（0 = 抗性 I，约 20% 减伤；1 = 抗性 II）。 */
    private static final int RESIST_AMPLIFIER = 1;

    public BulwarkItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity,
                              int slot, boolean selected) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        // 只在"正在举着这块盾"的时候生效 —— 放在背包里不该给抗性
        boolean blocking = living.isBlocking()
                && (living.getUseItem().getItem() == this);
        if (blocking && level.getGameTime() % 10 == 0) {
            living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE,
                    16, RESIST_AMPLIFIER, true, false, false));
            living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED,
                    16, 0, true, false, false));
        }
        super.inventoryTick(stack, level, entity, slot, selected);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lost_realm.relic.legend")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("item.lost_realm.bulwark_of_the_last_light.tip")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
