package com.lost_realm.boss;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import com.lost_realm.Medals;

/**
 * 遗落境终局内容的统一注册表 —— 维度键与竞技场几何常量。
 *
 * <h2>为什么单独建一个 Boss 包</h2>
 * 终局内容自成一套体系,与 {@code skill/} 下的四职业技能<b>互不耦合</b>:
 * Boss 可以读取勋章数据(如任务链要求),但勋章系统<b>永远不该</b>引用 Boss。
 *
 * <h2>遗落境维度</h2>
 * 维度 id 与模组同名 —— {@code lost_realm:lost_realm}。这是刻意的:
 * 整合包的所有力量(灵魂、属性、勋章)都被设定为源自遗落境。
 *
 * <p>beta45 起,旧人形 Boss 的全部战斗数值常量(血量/阶段/审判/波次/决斗)
 * 已随该 Boss 一起删除;这里只剩「建城与传送」要用的几何参数。
 */
public final class BossRegistry {

    /** 遗落境维度的 Level 键。 */
    public static final ResourceKey<Level> REALM_LEVEL = ResourceKey.create(
            Registries.DIMENSION,
            new ResourceLocation(Medals.MODID, "lost_realm"));

    // ================================================================
    // 竞技场坐标常量
    // ================================================================

    /**
     * 遗落之都中心。
     *
     * <p>选在 <b>Y=64</b> 是因为虚空维度没有地面,需要给玩家一个明确的落脚层;
     * 64 也正好是主世界的海平面高度,玩家下意识会感到"踏实"。
     *
     * <p>X/Z 取 0 —— 因为遗落之都是维度内<b>唯一</b>的建筑(见任务需求),
     * 玩家无论从哪个坐标进入,传送逻辑都会把他们送到这里。
     */
    public static final int ARENA_CENTER_X = 0;
    public static final int ARENA_CENTER_Y = 64;
    public static final int ARENA_CENTER_Z = 0;

    /**
     * 遗落之都半径(直径 200 格)。
     *
     * <p>dv5 由 32 → 100，beta25 由 100 → <b>125（直径 250 格）</b>。
     *
     * <p><b>为什么不是半径 250</b>：直径 250 已经是一个 15×15 区块的巨构 ——
     * 地面就要铺 4.9 万个方块，首次进入维度要几十秒。若真要半径 250
     * （直径 500、19 万方块），改这一个数即可，但生成耗时会翻四倍，
     * 而且 12 根柱子会散到看不清。
     *
     * <p><b>连带效应</b>：{@code WardenBoss#targetInArena}、各技能 Goal 的
     * {@code inArena} 会随之放大 —— 行为正确且符合预期。
     */
    public static final int ARENA_RADIUS = 125;

    /** 落点:玩家从传送门进入时应站在外墙内侧的入口平台上。 */
    public static final int ARENA_ENTRY_Y = ARENA_CENTER_Y + 1;

    // ================================================================
    // 终末竞技场 —— 建造参数(beta25 重做)
    // ----------------------------------------------------------------
    // 设计从「遗落之都」改成「只为一个仪式存在的场地」:
    //   中央只有一座祭坛，十二根巨柱围成一圈，其余全部空出来。
    //   空旷本身就是尺度感 —— 旧的广场/露台/角塔把空间切碎了。
    // ================================================================

    /** 中央祭坛基座半径(三层圆台)。 */
    public static final int ALTAR_BASE_RADIUS = 26;

    /** 中央祭坛核心(可直接右键的那一块)相对中心的高度。 */
    public static final int ALTAR_CORE_HEIGHT = 4;

    /** 环绕巨柱数量。 */
    public static final int PILLAR_COUNT = 12;

    /** 巨柱所在的圆环半径。 */
    public static final int PILLAR_RING_RADIUS = 100;

    /** 巨柱高度（不含柱头平台）。 */
    public static final int PILLAR_HEIGHT = 42;

    /** 外墙半径。 */
    public static final int WALL_RADIUS = 123;

    /** 外墙高度。 */
    public static final int WALL_HEIGHT = 30;

    /** 外墙门洞半宽(四个正方向各一道)。 */
    public static final int GATE_HALF_WIDTH = 5;

    /** 地面同心环的间隔(格)。每圈一道发光环，用来讲清尺度。 */
    public static final int FLOOR_RING_STEP = 20;

    /** 召唤仪式时长（tick）。400 = 20 秒。 */
    public static final int RITUAL_TICKS = 400;

    private BossRegistry() {}
}
