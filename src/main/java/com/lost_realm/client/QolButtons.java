package com.lost_realm.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.lost_realm.Medals;
import com.lost_realm.config.MedalsConfig;
import com.lost_realm.network.MedalsNetwork;
import com.lost_realm.network.PacketRefreshEnchant;
import com.lost_realm.network.PacketRefreshTrades;

/**
 * 「体验改进」的界面按钮注入(客户端)。
 *
 * <ul>
 *   <li><b>F16 交易刷新</b> —— {@link MerchantScreen} 上追加「刷新交易」按钮。</li>
 *   <li><b>F17 附魔刷新</b> —— {@link EnchantmentScreen} 上追加「刷新附魔」按钮。</li>
 * </ul>
 *
 * <p>按钮通过 {@link ScreenEvent.Init.Post#addListener} 挂到界面上,
 * 无需 Mixin 即可实现。
 */
@Mod.EventBusSubscriber(modid = Medals.MODID, value = Dist.CLIENT)
public final class QolButtons {

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();

        if (screen instanceof MerchantScreen merchantScreen
                && MedalsConfig.QOL_REFRESH_TRADES.get()) {
            event.addListener(Button.builder(
                            Component.translatable("button.lost_realm.refresh_trades"),
                            b -> MedalsNetwork.sendToServer(
                                    new PacketRefreshTrades(merchantScreen.getMenu().containerId)))
                    .bounds(screen.width - 100, screen.height - 50, 90, 20)
                    .build());
        }

        if (screen instanceof EnchantmentScreen enchantScreen
                && MedalsConfig.QOL_REFRESH_ENCHANT.get()) {
            event.addListener(Button.builder(
                            Component.translatable("button.lost_realm.refresh_enchant"),
                            b -> MedalsNetwork.sendToServer(
                                    new PacketRefreshEnchant(enchantScreen.getMenu().containerId)))
                    .bounds(screen.width - 100, screen.height - 50, 90, 20)
                    .build());
        }
    }

    private QolButtons() {}
}
