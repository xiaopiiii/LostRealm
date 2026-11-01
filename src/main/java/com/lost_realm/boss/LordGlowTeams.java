package com.lost_realm.boss;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

import org.jetbrains.annotations.Nullable;

/**
 * 遗落之主的<b>发光描边配色</b> —— 无渲染器下实现"形态颜色区分"。
 *
 * <h2>为什么用计分板队伍</h2>
 * Minecraft 的发光效果({@code setGlowingTag(true)})会让实体显示一圈描边,
 * 而描边颜色由该实体所属<b>队伍</b>的颜色决定
 * ({@code PlayerTeam#setColor(ChatFormatting)})。
 * 这是纯原版机制,不需要写任何渲染代码,也不需要 Mixin ——
 * 完美契合本模组「零自定义渲染器」的现状。
 *
 * <h2>队伍命名</h2>
 * 三个队伍名固定为 {@code lost_realm_lord_1/2/3},前缀保证不会与
 * 其他模组的队伍冲突(原版队伍名通常是玩家名或模组自己的前缀)。
 *
 * <h2>清理</h2>
 * 战斗结束(或实体移除)时<b>必须</b>调用 {@link #clear(Entity)},
 * 否则遗落的队伍成员条目会一直留在计分板存档里。
 */
public final class LordGlowTeams {

    /** 三个形态对应的队伍名。 */
    private static final String[] TEAM_NAMES = {
            "lost_realm_lord_1",
            "lost_realm_lord_2",
            "lost_realm_lord_3",
    };

    /**
     * 按状态给实体套上对应颜色的描边队伍。
     *
     * <h2>⚠️ dv19：描边已全局关闭</h2>
     * 用户明确反馈「为什么要有高亮啊？那个边框？好烦啊，给我去掉，三个阶段都有」。
     * 因此本方法<b>不再调用 {@code setGlowingTag(true)}</b> ——
     * 实体不再显示任何发光描边。方法保留是为了：① 保持调用点不变；
     * ② 若将来想恢复，只需把下面那行注释解开。
     *
     * <p>同时也会把之前版本可能残留在存档里的描边<b>主动清掉</b>
     * （{@code setGlowingTag(false)} + 摘除队伍），防止旧存档继续显示边框。
     */
    public static void applyTo(Entity entity, LordState state) {
        // 描边已关闭：直接走清理路径，确保任何残留边框都被摘除。
        clear(entity);
    }

    /**
     * 【已弃用】原「按状态上描边色」的逻辑，保留备查。
     * dv16~dv18 用它给三形态套紫/红/白描边；dv19 起用户要求去掉。
     */
    @SuppressWarnings("unused")
    private static void applyToLegacy(Entity entity, LordState state) {
        if (state == LordState.DEFEATED) {
            clear(entity);
            return;
        }
        Scoreboard board = scoreboardOf(entity);
        if (board == null) {
            return;
        }
        // 先摘掉旧队伍,避免同时属于两个队伍(原版指挥板不允许)
        String member = entity.getStringUUID();
        PlayerTeam old = board.getPlayersTeam(member);
        if (old != null) {
            board.removePlayerFromTeam(member, old);
        }

        int idx = teamIndexFor(state);
        PlayerTeam team = ensureTeam(board, TEAM_NAMES[idx]);
        if (team == null) {
            return;
        }
        team.setColor(state.glowColor());
        board.addPlayerToTeam(member, team);

        // 亮起描边
        entity.setGlowingTag(true);
    }

    /** 把实体从所有遗落之主队伍中摘除并关掉描边。 */
    public static void clear(Entity entity) {
        Scoreboard board = scoreboardOf(entity);
        if (board == null) {
            entity.setGlowingTag(false);
            return;
        }
        String member = entity.getStringUUID();
        PlayerTeam team = board.getPlayersTeam(member);
        if (team != null && isOurs(team.getName())) {
            board.removePlayerFromTeam(member, team);
        }
        entity.setGlowingTag(false);
    }

    // ================================================================
    // 内部
    // ================================================================

    /**
     * 战斗形态按序号取队伍;其余状态一律用第一队(紫)。
     *
     * <p>⚠️ <b>锁血演出态用「即将变成的那个形态」的颜色</b>(`dv10` 起)。
     * 因为演出本身就是"变身中" —— 让它提前显出新形态的颜色,
     * 玩家才能在 30 秒里直观看到"它在朝红色/白色变",
     * 而不是看完演出才突然跳色。这是"换阶段要换建模"的观感补充。
     */
    private static int teamIndexFor(LordState state) {
        return switch (state) {
            case FORM_2, TRANSITION_1 -> 1;      // 红(TRANSITION_1 → FORM_2)
            case FORM_3, TRANSITION_2 -> 2;      // 白(TRANSITION_2 → FORM_3)
            default -> 0;                        // 紫
        };
    }

    /** 懒创建队伍(已存在则直接返回)。 */
    @Nullable
    private static PlayerTeam ensureTeam(Scoreboard board, String name) {
        PlayerTeam existing = board.getPlayerTeam(name);
        if (existing != null) {
            return existing;
        }
        PlayerTeam created = board.addPlayerTeam(name);
        if (created != null) {
            // 名字标签保持可见,并允许队友看见隐身单位 —— 与 Boss 无关但对玩家友好
            created.setNameTagVisibility(Team.Visibility.ALWAYS);
            created.setSeeFriendlyInvisibles(true);
        }
        return created;
    }

    private static boolean isOurs(String teamName) {
        for (String n : TEAM_NAMES) {
            if (n.equals(teamName)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static Scoreboard scoreboardOf(Entity entity) {
        MinecraftServer server = entity.getServer();
        return server == null ? null : server.getScoreboard();
    }

    /** 供指令 / 调试:读取某形态的描边色。 */
    public static net.minecraft.ChatFormatting colorOf(LordState state) {
        return state.glowColor();
    }

    /** 供 {@link ServerLevel} 侧的一次性清理(维度卸载等场景)。 */
    public static void clearAll(ServerLevel level) {
        // 计分板是服务器级的;这里只做无副作用的空实现占位,
        // 真正的清理由实体移除 / 战斗结束时逐个调用 #clear 完成。
        if (level == null) {
            return;
        }
    }

    private LordGlowTeams() {}
}
