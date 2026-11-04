package com.lost_realm.network;

import java.util.function.Supplier;

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import com.lost_realm.difficulty.DifficultyLevel;
import com.lost_realm.difficulty.DifficultyManager;

/**
 * 客户端 -&gt; 服务端:查询 / 切换世界难度(配置界面的「世界难度」页用)。
 *
 * <h2>为什么 level 允许是 0</h2>
 * {@code 0} 表示<b>只查询</b>:界面一打开就发一次,服务端回一个
 * {@link PacketDifficultySync}。这样不用为「查询」再开一个包类,
 * 而 0 又不可能是一个合法难度(合法值 1~5),语义上没有歧义。
 *
 * <h2>权限</h2>
 * 和 {@code /lr difficulty} 同一套判定:OP 等级 2,单机存档的房主直接放行
 * (单机关掉「允许作弊」时 hasPermission 会失败,但房主显然应该能改自己世界的难度)。
 * 拒绝时回一条聊天提示,不会静默失败 —— 否则玩家只会看到按钮点了没反应。
 */
public class PacketDifficultySet {

    private final int level;

    public PacketDifficultySet(int level) {
        this.level = level;
    }

    public PacketDifficultySet(FriendlyByteBuf buf) {
        this.level = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.level);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        if (player == null) {
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> {
            // 查询:直接回当前值
            if (this.level <= 0) {
                MedalsNetwork.sendToPlayer(player,
                        new PacketDifficultySync(DifficultyManager.level().id()));
                return;
            }
            if (!canSwitch(player)) {
                player.displayClientMessage(Component
                        .literal("[遗落境] 只有 OP 能切换世界难度。")
                        .withStyle(ChatFormatting.RED), false);
                // 回一次当前值,免得界面上停在一个永远不会生效的选择上
                MedalsNetwork.sendToPlayer(player,
                        new PacketDifficultySync(DifficultyManager.level().id()));
                return;
            }
            DifficultyLevel next = DifficultyLevel.clamp(this.level);
            DifficultyManager.set(player.server, next);
            // set() 内部已广播给所有在线玩家(含发起者)
        });
        context.setPacketHandled(true);
    }

    private static boolean canSwitch(ServerPlayer player) {
        return player.hasPermissions(2) || player.server.isSingleplayer();
    }
}
