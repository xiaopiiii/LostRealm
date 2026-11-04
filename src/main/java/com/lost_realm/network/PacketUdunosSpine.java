package com.lost_realm.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * 服务端 → 客户端：<b>整条脊线</b>（40 节的坐标）。
 *
 * <h2>为什么要走这条路</h2>
 * 原本的设计和原版末影龙一样：服务端只同步头部位置，客户端<b>自己</b>跑一遍
 * 路径跟随算出 40 节。这样零额外带宽，理论上也很优雅。
 *
 * <p>但实测撞上一个绕不过去的现实：<b>客户端的位置根本收不到更新</b> ——
 * {@code /lrdebug} 显示轨迹永远只有 1 个点、三节坐标完全重合，
 * 而同一时刻服务端那边身体是正常铺开的（三节相距 40 格以上）。
 * 也就是"客户端自己算"这条路在当前这套环境（手机端 + 200 模组）里是断的。
 *
 * <p>与其继续深挖 {@code ClientboundMoveEntityPacket} 为什么没生效
 * （不知道是哪个模组动了实体 tick），不如<b>换一条可靠的链路</b>：
 * 服务端直接把算好的脊线发下来，客户端照着画。塌缩就不可能再发生。
 *
 * <h2>成本</h2>
 * 40 节 × 3 个 float = 480 字节；每 2 tick 一次 ≈ <b>4.8 KB/s</b>。
 * 对比现在 40 个部件实体各自 {@code updateInterval} 发的位置包（约 16 KB/s），
 * 反而更省 —— 因为一个包承载了全部信息，没有逐实体的包头开销。
 *
 * <p>客户端收到后做前后帧插值（见 {@code UdunosSpineHandler}），
 * 所以 10 Hz 的更新频率在 60fps 下依然平滑。
 */
public class PacketUdunosSpine {

    private final int entityId;

    /** 扁平数组，长度 = 节数 × 3（x, y, z 依次排列）。 */
    private final float[] points;

    /**
     * 这一帧是否发生了<b>瞬移</b>（星门穿门），客户端收到后要「硬切」，
     * 不做 prev→now 插值 —— 否则会在 A/B 门之间拉出一条超长身体。
     */
    private final boolean teleport;

    public PacketUdunosSpine(int entityId, float[] points) {
        this(entityId, points, false);
    }

    public PacketUdunosSpine(int entityId, float[] points, boolean teleport) {
        this.entityId = entityId;
        this.points = points;
        this.teleport = teleport;
    }

    public PacketUdunosSpine(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        this.teleport = buf.readBoolean();
        int n = buf.readVarInt();
        this.points = new float[n];
        for (int i = 0; i < n; i++) {
            this.points[i] = buf.readFloat();
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeBoolean(teleport);
        buf.writeVarInt(points.length);
        for (float f : points) {
            buf.writeFloat(f);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        // 包只从服务端发往客户端，enqueueWork 保证在主线程执行
        context.enqueueWork(() ->
                com.lost_realm.client.UdunosSpineHandler.accept(entityId, points, teleport));
        context.setPacketHandled(true);
    }
}
