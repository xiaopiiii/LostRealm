package com.lost_realm.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import com.lost_realm.data.MedalProgression;
import com.lost_realm.data.PlayerMedals;

/**
 * <b>破枷印记</b> —— 打开勋章熟练度枷锁的道具。
 *
 * <h2>它解决什么问题</h2>
 * 整合包分三章，熟练度上限 60 级。如果一路能刷到 60，玩家会在第一章
 * 就把整条成长曲线走完，后两章只剩跑图。所以在 <b>20</b> 与 <b>40</b>
 * 各设一道枷锁，等级到此为止，必须用这枚印记才能继续。
 *
 * <h2>为什么"使用"而不是"合成时消耗"</h2>
 * 印记是可交易的实体道具：任务奖励、Boss 掉落、商店购买都能给。
 * 做成"右键使用"意味着它可以被囤、被骗、被抢 —— 这对一个
 * 有章节节奏的整合包来说是好事，玩家会认真对待它。
 *
 * <h2>判定只有一个入口</h2>
 * 真正能不能破，全看 {@link MedalProgression#tryBreak(ServerPlayer)}；
 * 本类只负责扣物品、放音效、发消息。这样命令与道具走同一条判定，
 * 不会出现"命令能开、道具不能开"的偏差。
 */
public class MedalShackleItem extends Item {

    public MedalShackleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!(level instanceof net.minecraft.server.level.ServerLevel server)
                || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        PlayerMedals pm = PlayerMedals.of(sp);
        int stage = pm.getShackleStage();
        int need = MedalProgression.nextShackleLevel(stage);

        MedalProgression.BreakResult result = MedalProgression.tryBreak(sp);

        switch (result) {
            case SUCCESS -> {
                if (!sp.isCreative()) {
                    stack.shrink(1);
                }
                int newCap = pm.levelCap();

                server.playSound(null, sp.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                        SoundSource.PLAYERS, 2.0F, 0.8F);
                server.playSound(null, sp.blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
                        SoundSource.PLAYERS, 1.6F, 1.3F);
                server.sendParticles(ParticleTypes.END_ROD,
                        sp.getX(), sp.getY() + 1.0D, sp.getZ(), 40, 0.6D, 1.0D, 0.6D, 0.08D);
                server.sendParticles(ParticleTypes.REVERSE_PORTAL,
                        sp.getX(), sp.getY() + 1.0D, sp.getZ(), 60, 1.0D, 1.2D, 1.0D, 0.25D);

                sp.displayClientMessage(
                        Component.translatable("message.lost_realm.shackle.broken", newCap)
                                .withStyle(ChatFormatting.LIGHT_PURPLE),
                        false);
                return InteractionResultHolder.success(stack);
            }
            case NOT_READY -> {
                sp.displayClientMessage(
                        Component.translatable("message.lost_realm.shackle.not_ready", need)
                                .withStyle(ChatFormatting.GRAY),
                        true);
                return InteractionResultHolder.fail(stack);
            }
            default -> {
                sp.displayClientMessage(
                        Component.translatable("message.lost_realm.shackle.already_open")
                                .withStyle(ChatFormatting.GRAY),
                        true);
                return InteractionResultHolder.fail(stack);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lost_realm.medal_shackle.tip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.lost_realm.medal_shackle.tip.2")
                .withStyle(ChatFormatting.DARK_PURPLE));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
