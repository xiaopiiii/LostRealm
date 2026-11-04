package com.lost_realm.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * 服务端 -&gt; 客户端:同步「当前世界难度」。
 *
 * <p>客户端只拿它做显示(配置界面里的难度页)。真正的数值落点在服务端
 * {@code DifficultyManager},即使这个包丢了,战斗数值也不会错 ——
 * 最多是界面上的高亮标错一格,下次打开界面重新查询即可。
 */
public class PacketDifficultySync {

    private final int level;

    public PacketDifficultySync(int level) {
        this.level = level;
    }

    public PacketDifficultySync(FriendlyByteBuf buf) {
        this.level = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.level);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            com.lost_realm.client.ClientDifficulty.set(this.level);
            // 配置界面正开着难度页时,立刻重画高亮(否则要关掉再开才看得到)
            if (net.minecraft.client.Minecraft.getInstance()
                    .screen instanceof com.lost_realm.client.config.ConfigScreen screen) {
                screen.onDifficultySynced();
            }
        });
        context.setPacketHandled(true);
    }
}
