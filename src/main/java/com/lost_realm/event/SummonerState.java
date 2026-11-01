package com.lost_realm.event;

import net.minecraft.world.entity.player.Player;

import com.lost_realm.data.PlayerMedals;

/**
 * 召唤师的<b>跨类共享运行时状态</b>。
 *
 * <h2>为什么单独拎出来</h2>
 * 「军团号令」的激活状态需要被三处同时读到：
 * <ul>
 *   <li>技能自身（Lv.40，负责开启/到期）；</li>
 *   <li>{@code SummonerCharmHandler}（每帧把加成刷到仆从身上）；</li>
 *   <li>{@code ServantLimit}（召唤上限判定，被 Mixin 从 Goety 内部回调）。</li>
 * </ul>
 * 三者互不持有对方的引用，所以把状态放在这里，谁都能问一句「现在开着吗」。
 *
 * <p>状态存在玩家身上（{@link PlayerMedals}），随存档走，下线重连不会丢。
 */
public final class SummonerState {

    /** 军团号令的到期刻（0 表示未开启）。 */
    public static final String KEY_COMMAND_UNTIL = "summoner_s20_until";

    /** 开启军团号令。 */
    public static void startCommand(Player player, int seconds) {
        if (player == null) {
            return;
        }
        PlayerMedals.of(player).setTimestamp(KEY_COMMAND_UNTIL,
                player.level().getGameTime() + Math.max(1, seconds) * 20L);
    }

    /** 军团号令是否仍在生效。 */
    public static boolean commandActive(Player player) {
        if (player == null) {
            return false;
        }
        long until = PlayerMedals.of(player).getTimestamp(KEY_COMMAND_UNTIL);
        return until > 0L && player.level().getGameTime() < until;
    }

    /** 立即结束军团号令（卸下勋章 / 技能清空时用）。 */
    public static void stopCommand(Player player) {
        if (player == null) {
            return;
        }
        PlayerMedals.of(player).setTimestamp(KEY_COMMAND_UNTIL, 0L);
    }

    private SummonerState() {}
}
