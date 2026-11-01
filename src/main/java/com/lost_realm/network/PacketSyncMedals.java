package com.lost_realm.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import net.minecraftforge.network.NetworkEvent;

/**
 * 服务端 -&gt; 客户端:通知「佩戴中的勋章数据已更新」。
 *
 * <h2>为什么这个包现在几乎是空的</h2>
 * 熟练度存放在勋章物品自身的 NBT 里(见 {@code MedalNbt}),
 * 而物品栈的 NBT 由原版容器同步机制自动推给客户端 ——
 * 换装、背包移动、经验增长都会随之同步。
 * 因此这里不再需要搬运「勋章 -> 经验/等级」的映射表,
 * 只需要发一个轻量信号让客户端知道「可以重读饰品栏了」。
 *
 * <p>保留该包而非常量删除的原因:客户端仍有 {@code MedalKeybinds} 这类
 * 需要在等级变化后立刻重算的地方,一个明确的信号比让它们每帧轮询更干净。
 */
public class PacketSyncMedals {

    public PacketSyncMedals() {
        // 无载荷
    }

    public PacketSyncMedals(FriendlyByteBuf buf) {
        // 无载荷可读
    }

    public void encode(FriendlyByteBuf buf) {
        // 无载荷可写
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            // 客户端:物品 NBT 已由原版同步,这里只是让缓存的派生值失效
            com.lost_realm.client.ClientMedalCache.clear();
        });
        context.setPacketHandled(true);
    }

    /** 发送给指定玩家。 */
    public static void send(ServerPlayer player) {
        MedalsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PacketSyncMedals());
    }
}
