package com.lost_realm.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.players.SleepStatus;

import com.lost_realm.config.MedalsConfig;

/**
 * {@link SleepStatus} 注入:<b>C8 联机时一人睡觉即可跳过时间</b>。
 *
 * <p>原版要求「有一定比例的玩家就寝」才推进时间。本注入让
 * {@code areEnoughSleeping} 恒返回 {@code true},从而只要有玩家躺下即触发全局跳过。
 */
@Mixin(SleepStatus.class)
public abstract class SleepStatusMixin {

    @Inject(method = "areEnoughSleeping", at = @At("HEAD"), cancellable = true)
    private void medals$onePlayerEnough(int needed, CallbackInfoReturnable<Boolean> cir) {
        if (MedalsConfig.QOL_ONE_PLAYER_SLEEP.get()) {
            cir.setReturnValue(true);
        }
    }
}
