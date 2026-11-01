package com.lost_realm.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.item.enchantment.ArrowInfiniteEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import com.lost_realm.config.MedalsConfig;

/**
 * {@link ArrowInfiniteEnchantment} 注入:<b>F19 无限与经验修补不再冲突</b>。
 *
 * <p>「无限」附魔重写了 {@code checkCompatibility},把 {@code MENDING} 判为不兼容。
 * 这里在方法入口:若对方是经验修补,则直接判定为兼容。
 */
@Mixin(ArrowInfiniteEnchantment.class)
public abstract class ArrowInfiniteEnchantmentMixin {

    @Inject(method = "checkCompatibility", at = @At("HEAD"), cancellable = true)
    private void medals$allowMending(Enchantment other, CallbackInfoReturnable<Boolean> cir) {
        if (!MedalsConfig.QOL_INFINITY_MENDING.get()) {
            return;
        }
        if (other == Enchantments.MENDING) {
            cir.setReturnValue(true);
        }
    }
}
