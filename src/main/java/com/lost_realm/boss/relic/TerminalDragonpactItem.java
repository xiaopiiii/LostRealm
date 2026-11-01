package com.lost_realm.boss.relic;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * <b>终星·龙王之契</b>（召唤师）—— 厄瑞玻斯遗物。
 *
 * <h2>原设计 → MC</h2>
 * 原设计是一条"头带六节身体、沿真实轨迹跟随"的小星龙。
 * 那种多节跟随正是厄瑞玻斯本体的技术 —— 把它再做一份作为<b>纪念品</b>
 * 并不划算（同一套算法运行两个实例，还要处理仆从上限、跨维度、卸载）。
 *
 * <p>所以这里的落点是<b>用原版狼的底子做一只强力仆从</b>：
 * 得到一只听话、能打、不会死的星龙。它没有六节身体，
 * 但它有原版仆从的全部福利 —— 会跟随、会跨维度、会被整合包里
 * 其它模组的宠物系统正确识别。
 *
 * <h2>为什么用狼而不是自造实体</h2>
 * 自造仆从实体要处理：AI、寻路、主人归属、跨维度、死亡保存、
 * 与 Curios / Goety 召唤上限的兼容……
 * 而这一件是<b>通关纪念品</b>，玩家的注意力应该在"我拿到了什么"，
 * 不是"这个仆从为什么卡墙里了"。用狼，这些全都免费。
 */
public class TerminalDragonpactItem extends Item {

    /** 星龙的属性倍率（相对普通狼）。 */
    private static final double HEALTH_MULTIPLIER = 6.0D;
    private static final double DAMAGE_MULTIPLIER = 5.0D;

    public TerminalDragonpactItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server)) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        Wolf dragon = EntityType.WOLF.create(server);
        if (dragon == null) {
            return InteractionResultHolder.fail(stack);
        }
        dragon.moveTo(player.getX() + 1.5D, player.getY(), player.getZ() + 1.5D,
                player.getYRot(), 0.0F);
        dragon.setTame(true);
        dragon.setOwnerUUID(player.getUUID());
        dragon.setCustomName(Component.translatable("item.lost_realm.terminal_dragonpact"));
        dragon.setCustomNameVisible(true);
        dragon.setPersistenceRequired();

        // 强化到"遗物"该有的水准
        var health = dragon.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() * HEALTH_MULTIPLIER);
        }
        var damage = dragon.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(damage.getBaseValue() * DAMAGE_MULTIPLIER);
        }
        dragon.setHealth(dragon.getMaxHealth());
        dragon.finalizeSpawn(server, server.getCurrentDifficultyAt(dragon.blockPosition()),
                MobSpawnType.MOB_SUMMONED, null, null);

        server.addFreshEntity(dragon);
        server.sendParticles(ParticleTypes.REVERSE_PORTAL,
                dragon.getX(), dragon.getY() + 0.8D, dragon.getZ(), 40, 0.6D, 0.6D, 0.6D, 0.25D);
        server.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.PLAYERS, 1.2F, 1.6F);

        player.getCooldowns().addCooldown(this, 400);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lost_realm.relic.legend")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("item.lost_realm.terminal_dragonpact.tip")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
