package com.lost_realm.qol;

/**
 * 「体验改进」板块中<b>不可调节</b>的常量集中存放处。
 *
 * <p>需求中明确写死、不允许玩家修改的数值(如凋零骷髅头掉落率 15%、
 * 村民交易次数 64)在此以常量形式定义,配置文件中不暴露对应项,
 * 仅保留「功能总开关」以便整体禁用。
 */
public final class QolConstants {

    /** 凋零骷髅头掉落概率(需求:15%,不可调节)。 */
    public static final float WITHER_SKULL_DROP_CHANCE = 0.15F;

    /** 村民每项交易的最大次数(需求:64,不可调节,原版为 12)。 */
    public static final int VILLAGER_MAX_USES = 64;

    /** 死亡掉落物集中:所有掉落物统一落在此半径内(格),避免散落。 */
    public static final double DEATH_DROP_RADIUS = 0.5D;

    /** 睡觉跳过时间:1 小时 = 1000 tick(原版一天 24000 tick = 24h)。 */
    public static final long TICKS_PER_HOUR = 1000L;

    /** 共享 buff 的刷新间隔(tick)。 */
    public static final int BUFF_SHARE_INTERVAL = 20;

    /** 共享 buff 的发放时长(秒),持续刷新以维持「陪伴」效果。 */
    public static final int BUFF_SHARE_DURATION_SECONDS = 10;

    private QolConstants() {}
}
