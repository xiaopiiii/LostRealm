package com.lost_realm.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.item.trading.MerchantOffer;

import com.lost_realm.config.MedalsConfig;
import com.lost_realm.qol.QolConstants;

/**
 * {@link MerchantOffer} 注入:<b>F15 村民交易次数 12 → 64</b>。
 *
 * <p>{@code maxUses} 为 {@code private final} 且无 setter,故直接覆写只读方法
 * {@code getMaxUses} 的返回值。次数固定为 {@link QolConstants#VILLAGER_MAX_USES}(不可调节)。
 */
@Mixin(MerchantOffer.class)
public abstract class MerchantOfferMixin {

    @Inject(method = "getMaxUses", at = @At("HEAD"), cancellable = true)
    private void medals$maxUses64(CallbackInfoReturnable<Integer> cir) {
        if (MedalsConfig.QOL_VILLAGER_TRADES_64.get()) {
            cir.setReturnValue(QolConstants.VILLAGER_MAX_USES);
        }
    }
}
