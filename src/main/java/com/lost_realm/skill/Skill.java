package com.lost_realm.skill;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 节点技能契约。
 *
 * <p>每个节点技能对应一个解锁等级。技能的「被动」部分通过 {@link #applyPassive} 每一
 * 服务端 tick(或状态刷新时)施加固定 UUID 的属性修饰符;
 * 「主动」部分通过 {@link #activate} 触发,并由 {@link #cooldownTicks} 提供冷却。
 */
public interface Skill {

    /** 所属勋章。 */
    MedalType medal();

    /** 解锁等级(5/10/15/20/25/30)。 */
    int unlockLevel();

    /** 技能内部 ID,例如 {@code "s5_dodge"}。 */
    String id();

    /** 本地化名,例如「闪避」。 */
    Component displayName();

    /** 技能描述(用于提示)。 */
    List<Component> description();

    /** 是否为主动技能(有 CD,需要玩家按键触发)。 */
    default boolean isActive() {
        return false;
    }

    /** 主动技能冷却(tick)。被动技能返回 0。 */
    default int cooldownTicks() {
        return 0;
    }

    /**
     * 是否为「开关型」主动技能(按一次开启,再按一次关闭)。
     *
     * <p>开关型技能依然需要 {@link #isActive()} 返回 {@code true} 才会被网络包转发;
     * 其冷却仅在「关闭」时结算,因此 {@link #cooldownTicks()} 表示关闭后的冷却。
     */
    default boolean isToggleable() {
        return false;
    }

    /**
     * 被动属性施加 / 状态维护。每服务端 tick 调用一次(仅在玩家已解锁该技能时)。
     *
     * @param player 服务端玩家
     * @param level  当前熟练度等级
     */
    default void applyPassive(ServerPlayer player, int level) {}

    /**
     * 主动技能触发。返回是否成功触发(成功则进入冷却)。
     */
    default boolean activate(ServerPlayer player, int level) {
        return false;
    }

    /**
     * 关闭「开关型」主动技能(仅 {@link #isToggleable()} 为 {@code true} 的技能会收到)。
     *
     * @return 是否确实执行了关闭(用于避免重复结算冷却)
     */
    default boolean deactivate(ServerPlayer player, int level) {
        return false;
    }

    /**
     * 玩家下线 / 卸下勋章时清理自身产生的临时状态与修饰符。
     */
    default void clear(Player player) {}

    /**
     * 当熟练度等级变化时回调,用于等级回落时撤销修饰符。
     */
    default void onLevelChanged(ServerPlayer player, int oldLevel, int newLevel) {}
}
