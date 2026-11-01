package com.lost_realm.boss;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/**
 * 一场 Boss 战的<b>会话状态</b> —— 血条、参战者、波次调度、计时。
 *
 * <h2>为什么需要"会话"这个概念</h2>
 * 战斗状态既不属于 Boss 实体(实体可能被卸载/重载),
 * 也不属于玩家(多人共战要共享进度),更不属于方块(方块状态会被存档污染)。
 * 因此独立成一个对象,由 {@link WardenFightManager} 按维度持有,
 * 每场战斗一份,战斗结束即销毁。
 *
 * <h2>与旧版的区别(dv5 重构)</h2>
 * <ul>
 *   <li><b>移除</b>四相祭坛的全部进度 / 点亮 / 护盾逻辑 ——
 *       新流程改由 {@link LordState} 状态机驱动,
 *       祭坛降级为城市里的<b>纯装饰</b>方块</li>
 *   <li><b>移除</b>血量阈值驱动的 {@code computePhase} —— 形态推进改为「锁血」</li>
 *   <li><b>新增</b> {@link #waveManager()} —— 5 波怪物的调度器</li>
 *   <li><b>新增</b> {@link #setBarVisible(boolean)} / {@link #applyBarColor} ——
 *       血条只在战斗形态出现</li>
 * </ul>
 */
public final class WardenFightSession {

    private final ServerLevel level;
    private final UUID bossId;

    /** 参战玩家(用于血条显示与结算)。 */
    private final Set<UUID> participants = new LinkedHashSet<>();

    /** Boss 血条。 */
    private final ServerBossEvent bossBar;

    /** 5 波怪物的调度器(会话周期内一份)。 */
    private final WaveManager waveManager = new WaveManager();

    /** 战斗开始时间(tick),用于统计用时。 */
    private final long startTick;

    /** 战斗是否已结束(结束后等待清理)。 */
    private boolean finished;

    /** 血条当前是否应当可见(非战斗形态隐藏)。 */
    private boolean barVisible = true;

    public WardenFightSession(ServerLevel level, UUID bossId, Component bossName) {
        this.level = level;
        this.bossId = bossId;
        this.startTick = level.getGameTime();
        this.bossBar = new ServerBossEvent(bossName, BossEvent.BossBarColor.PURPLE,
                BossEvent.BossBarOverlay.NOTCHED_10);
        this.bossBar.setDarkenScreen(true);
        this.bossBar.setCreateWorldFog(false);
        // 血条初始隐藏 —— 只有进入第一形态(可打)时才亮起
        this.bossBar.setVisible(false);
    }

    // ================================================================
    // 血条
    // ================================================================

    /**
     * 控制血条显隐。
     *
     * <p>新流程里 Boss 在零阶段/幽灵态/波次态都<b>没有血条</b> ——
     * 这是需求明确要求的("他是无敌的,没有血条")。
     * 只有进入 {@link LordState#FORM_1} 之后血条才出现。
     */
    public void setBarVisible(boolean visible) {
        this.barVisible = visible;
        this.bossBar.setVisible(visible);
    }

    public boolean isBarVisible() {
        return barVisible;
    }

    /** 切换血条颜色(与形态描边色呼应)。 */
    public void applyBarColor(BossEvent.BossBarColor color) {
        if (color != null) {
            this.bossBar.setColor(color);
        }
    }

    public void addParticipant(ServerPlayer player) {
        if (participants.add(player.getUUID())) {
            bossBar.addPlayer(player);
        }
    }

    public void removeParticipant(ServerPlayer player) {
        participants.remove(player.getUUID());
        bossBar.removePlayer(player);
    }

    public Set<UUID> participants() {
        return participants;
    }

    public boolean isParticipant(ServerPlayer player) {
        return participants.contains(player.getUUID());
    }

    public ServerBossEvent bossBar() {
        return bossBar;
    }

    /** 把血条推向所有参战者。 */
    public void syncBar(float healthRatio) {
        bossBar.setProgress(Math.max(0.0F, Math.min(1.0F, healthRatio)));
    }

    /** 设置血条名(由 Boss 侧传入当前形态名)。 */
    public void setBarName(Component name) {
        bossBar.setName(name);
    }

    public void broadcast(Component msg) {
        for (UUID id : participants) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                p.displayClientMessage(msg, false);
            }
        }
    }

    // ================================================================
    // 波次
    // ================================================================

    public WaveManager waveManager() {
        return waveManager;
    }

    // ================================================================
    // 生命周期
    // ================================================================

    public ServerLevel level() {
        return level;
    }

    public UUID bossId() {
        return bossId;
    }

    public long elapsedTicks() {
        return level.getGameTime() - startTick;
    }

    public boolean isFinished() {
        return finished;
    }

    public void finish() {
        this.finished = true;
        bossBar.removeAllPlayers();
        waveManager.clear(level);
    }
}
