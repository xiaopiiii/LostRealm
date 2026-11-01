package com.lost_realm.event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;

import com.lost_realm.data.PlayerMedals;
import com.lost_realm.item.MedalRuntime;
import com.lost_realm.skill.MedalType;
import com.lost_realm.network.MedalsNetwork;
import com.lost_realm.network.PacketSyncMedals;

/**
 * 等级变化同步 —— 当熟练度提升导致等级变化时,刷新属性并同步客户端。
 */
public final class LevelSync {

    /** 记录上次同步的等级,避免每 tick 都发包 */
    private static final Map<UUID, int[]> LAST_LEVELS = new HashMap<>();

    /** 若等级发生变化则刷新属性 + 发包。 */
    public static void refreshIfNeeded(ServerPlayer player, PlayerMedals data) {
        // 数组长度与 MedalType 数量保持一致(新增勋章时自动适配,避免越界)
        int[] last = LAST_LEVELS.computeIfAbsent(player.getUUID(),
                k -> newLevelCache());
        boolean changed = false;

        MedalType[] types = MedalType.values();
        for (int i = 0; i < types.length; i++) {
            int lv = com.lost_realm.data.MedalSource.level(player, types[i]);
            if (last[i] != lv) {
                last[i] = lv;
                changed = true;
                // 触发一次属性重算(等级取自佩戴中的勋章物品)
                net.minecraft.world.item.ItemStack worn =
                        com.lost_realm.data.MedalSource.stackOf(player, types[i]);
                MedalRuntime.refresh(player, types[i], data, worn);
            }
        }

        if (changed) {
            MedalsNetwork.sendToPlayer(player, new PacketSyncMedals());
            // 生命值上限可能变化,修正当前血量
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
        }
    }

    /** 新建一个「全部勋章等级未知(-1)」的缓存数组,长度随 MedalType 自动扩展。 */
    private static int[] newLevelCache() {
        int[] arr = new int[MedalType.values().length];
        java.util.Arrays.fill(arr, -1);
        return arr;
    }

    /** 玩家下线时清理缓存。 */
    public static void forget(UUID uuid) {
        LAST_LEVELS.remove(uuid);
    }

    private LevelSync() {}
}
