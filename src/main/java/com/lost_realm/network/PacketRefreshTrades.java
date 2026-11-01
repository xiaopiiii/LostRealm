package com.lost_realm.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;

import net.minecraftforge.network.NetworkEvent;

/**
 * 客户端 -> 服务端:请求刷新当前打开的交易界面(村民交易刷新按钮 F16)。
 *
 * <p>服务端收到后,把该村民的所有交易项重置为「未使用」状态,从而恢复交易次数。
 */
public class PacketRefreshTrades {

    private final int containerId;

    public PacketRefreshTrades(int containerId) {
        this.containerId = containerId;
    }

    public PacketRefreshTrades(FriendlyByteBuf buf) {
        this.containerId = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.containerId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        if (player == null) {
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> {
            if (!(player.containerMenu instanceof MerchantMenu menu)) {
                return;
            }
            if (menu.containerId != this.containerId) {
                return;
            }
            Merchant merchant = ((com.lost_realm.mixin.MerchantMenuAccessor) menu).medals$getTrader();
            if (!(merchant instanceof AbstractVillager villager)) {
                return;
            }
            // 重置所有交易项的使用次数(恢复可交易)
            for (MerchantOffer offer : villager.getOffers()) {
                offer.resetUses();
            }
            // 触发一次补货判定
            villager.setTradingPlayer(player);
            villager.setTradingPlayer(null);
        });
        context.setPacketHandled(true);
    }
}
