package com.lost_realm.boss;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

/**
 * Boss 战会话的<b>管理器</b> —— 按维度持有当前进行的战斗。
 *
 * <h2>为什么按维度而不是全局</h2>
 * 遗落境是单人挑战维度,同一时刻只应有一场战斗。
 * 用 {@code Map<ResourceKey<Level>, WardenFightSession>} 而不是单例,
 * 是为了将来若扩展出多个 Boss 维度(或允许测试环境并行)时无需重构。
 *
 * <h2>进入限制</h2>
 * {@link #canEnter} 是传送门的准入闸口:
 * <ul>
 *   <li>无战斗进行 → 放行(玩家来启动挑战或查看场地)</li>
 *   <li>有战斗进行 & 该玩家是参战者 → 放行(死了回来继续)</li>
 *   <li>有战斗进行 & 该玩家不是参战者 → 拒绝(防止无关玩家闯入搅局)</li>
 * </ul>
 */
public final class WardenFightManager {

    private static final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>,
            WardenFightSession> SESSIONS = new HashMap<>();

    /** 查询某维度当前的战斗会话。 */
    @Nullable
    public static WardenFightSession session(ServerLevel level) {
        return SESSIONS.get(level.dimension());
    }

    /** 开一场新战斗。若已有会话则先结束旧的。 */
    public static WardenFightSession start(ServerLevel level, UUID bossId,
                                           net.minecraft.network.chat.Component bossName) {
        WardenFightSession old = SESSIONS.get(level.dimension());
        if (old != null) {
            old.finish();
        }
        WardenFightSession s = new WardenFightSession(level, bossId, bossName);
        SESSIONS.put(level.dimension(), s);
        return s;
    }

    /** 结束并移除某维度的战斗会话。 */
    public static void end(ServerLevel level) {
        WardenFightSession s = SESSIONS.remove(level.dimension());
        if (s != null) {
            s.finish();
        }
    }

    /** 该维度是否正有战斗进行。 */
    public static boolean inFight(ServerLevel level) {
        WardenFightSession s = SESSIONS.get(level.dimension());
        return s != null && !s.isFinished();
    }

    /**
     * 传送门准入判定 —— 见类注释的三种情形。
     */
    public static boolean canEnter(ServerPlayer player) {
        ServerLevel realm = player.server.getLevel(BossRegistry.REALM_LEVEL);
        if (realm == null) {
            return true;
        }
        WardenFightSession s = session(realm);
        if (s == null || s.isFinished()) {
            return true;
        }
        return s.isParticipant(player);
    }

    private WardenFightManager() {}
}
