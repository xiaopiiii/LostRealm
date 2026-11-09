package com.lost_realm.network;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * 服务端 -&gt; 客户端：请求打开区块加载器界面，并带上当前设置。
 *
 * <h2>为什么不用 MenuProvider / AbstractContainerMenu</h2>
 * 这个界面里没有任何"格子"——没有物品槽、没有玩家背包、没有需要同步的容器内容。
 * 用 {@code AbstractContainerMenu} 就得注册 MenuType、写 {@code stillValid}、
 * 处理 20 格空槽同步，全是为了一个纯按钮面板付出容器框架的代价。
 *
 * <p>所以走轻量路线：服务端把"位置 + 尺寸 + 开关"直接发下来，客户端拿到就开一个
 * 普通 {@code Screen}，改动再通过 {@link PacketChunkLoaderSet} 发回去。
 * 少了整整一层容器同步，也少一个注册项。
 */
public class PacketChunkLoaderOpen {

    private final BlockPos pos;
    private final int size;
    private final boolean active;

    public PacketChunkLoaderOpen(BlockPos pos, int size, boolean active) {
        this.pos = pos;
        this.size = size;
        this.active = active;
    }

    public PacketChunkLoaderOpen(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.size = buf.readVarInt();
        this.active = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeVarInt(this.size);
        buf.writeBoolean(this.active);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lost_realm.client.ChunkLoaderScreen.open(pos, size, active)));
        context.setPacketHandled(true);
    }
}
