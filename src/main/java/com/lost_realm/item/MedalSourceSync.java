package com.lost_realm.item;

import net.minecraft.server.level.ServerPlayer;

import com.lost_realm.network.MedalsNetwork;
import com.lost_realm.network.PacketSyncMedals;

/**
 * 勋章物品 NBT 变更后的同步工具。
 *
 * <h2>为什么需要显式 sync</h2>
 * 熟练度写在勋章物品的 NBT 里,而物品 NBT 只有在**容器内容变化**时
 * 才会被原版自动推送给客户端。像「打开对话标记已看过」这种
 * 只改 NBT、不改容器槽位的操作,原版不会察觉。
 *
 * <p>因此凡是在服务端直接改勋章 NBT 的地方,都要调一次本类,
 * 把承载该勋章的容器槽位标脏,让客户端拿到最新 tooltip 数据。
 */
public final class MedalSourceSync {

    /**
     * 把玩家身上（含 Curios 饰品栏）承载勋章的槽位标脏并推送。
     *
     * <p>实现:直接发一次轻量同步包 + 让 Curios 重发饰品栏。
     * 无需遍历具体是哪个槽位 —— Curios 的同步粒度是整个饰品栏。
     */
    public static void flush(ServerPlayer player) {
        if (player == null) {
            return;
        }
        // 通知客户端「可以重读佩戴中的勋章了」
        MedalsNetwork.sendToPlayer(player, new PacketSyncMedals());
        // 让 Curios 把饰品栏再推一次,确保 NBT 变更落到达客户端
        top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).ifPresent(handler ->
                handler.getCurios().values().forEach(h -> {
                    for (int i = 0; i < h.getSlots(); i++) {
                        net.minecraft.world.item.ItemStack st = h.getStacks().getStackInSlot(i);
                        if (!st.isEmpty()) {
                            st.setPopTime(5);
                        }
                    }
                }));
    }

    private MedalSourceSync() {}
}
