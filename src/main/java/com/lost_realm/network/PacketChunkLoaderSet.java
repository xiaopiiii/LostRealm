package com.lost_realm.network;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import com.lost_realm.machine.ChunkLoaderBlock;
import com.lost_realm.machine.ChunkLoaderBlockEntity;

/**
 * 客户端 -&gt; 服务端：应用区块加载器的设置。
 *
 * <h2>服务端必须重新校验</h2>
 * 客户端发上来的 {@code size} 可以任意伪造，{@code pos} 也可以是脚下的空气方块。
 * 做服务端逻辑的第一条规矩就是：**不信任客户端送来的任何数值**。
 * 这里做三件事：
 * <ol>
 *   <li>距离校验 —— 超过 8 格直接丢弃，防止改包远程操纵别人的加载器；</li>
 *   <li>方块实体校验 —— 那个坐标上必须真有加载器；</li>
 *   <li>数值夹取 —— 由 {@code setSize} 内部把边长收进 1/3/5/7/9。</li>
 * </ol>
 */
public class PacketChunkLoaderSet {

    /** 允许的操作距离（格）。方块交互本身是 4.5 格内，这里放宽一档给高延迟。 */
    private static final double MAX_DISTANCE_SQR = 8.0D * 8.0D;

    private final BlockPos pos;
    private final int size;
    private final boolean active;

    public PacketChunkLoaderSet(BlockPos pos, int size, boolean active) {
        this.pos = pos;
        this.size = size;
        this.active = active;
    }

    public PacketChunkLoaderSet(FriendlyByteBuf buf) {
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
        ServerPlayer player = context.getSender();
        if (player == null) {
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> {
            if (!player.isAlive()) {
                return;
            }
            if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                    > MAX_DISTANCE_SQR) {
                return;
            }
            if (!(player.level() instanceof ServerLevel server)) {
                return;
            }
            BlockEntity be = server.getBlockEntity(pos);
            if (!(be instanceof ChunkLoaderBlockEntity loader)) {
                return;
            }
            loader.setSize(this.size);
            loader.setActive(this.active);
            ChunkLoaderBlock.syncActive(server, pos, loader.isActive());
        });
        context.setPacketHandled(true);
    }
}
