package com.lost_realm.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;

/**
 * {@link MerchantMenu} 访问器 Mixin:暴露私有字段 {@code trader},
 * 以便 F16「交易刷新」在服务端取回关联的村民实体。
 */
@Mixin(MerchantMenu.class)
public interface MerchantMenuAccessor {

    @Accessor("trader")
    Merchant medals$getTrader();
}
