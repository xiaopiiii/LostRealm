package com.lost_realm.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import com.lost_realm.event.DashHandler;

/**
 * 客户端 -> 服务端：请求发动【突进之刃】。
 *
 * <p>客户端只负责「检测到两次攻击键点击」这一件事，
 * 所有资格校验（有没有戴饰品、冷却、武器攻速、饥饿值）都在服务端做 ——
 * 客户端发包是可以伪造的，判定绝不能放在那一侧。
 */
public class PacketDash {

    public PacketDash() {}

    public PacketDash(FriendlyByteBuf buf) {
        // 无字段
    }

    public void encode(FriendlyByteBuf buf) {
        // 无字段
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                DashHandler.tryDash(player);
            }
        });
        context.setPacketHandled(true);
    }
}
