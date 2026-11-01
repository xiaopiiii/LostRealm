package com.lost_realm.boss;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraftforge.common.ForgeConfigSpec;

import com.lost_realm.Medals;

/**
 * 终末之影(Boss)系统的统一注册表 —— 维度键、Boss 常量集中于此。
 *
 * <h2>为什么单独建一个 Boss 包</h2>
 * 本 Boss 的定位是<b>整合包的终局内容</b>,而非「四勋章系统的考试」。
 * 因此它自成一套体系(id 前缀统一 {@code lost_realm:boss_*}),
 * 与 {@code skill/} 下的四职业技能<b>互不耦合</b>,两者的依赖方向是单向的:
 * Boss 可以读取勋章数据(如任务链要求),但勋章系统<b>永远不该</b>引用 Boss。
 *
 * <h2>遗落境维度</h2>
 * 维度 id 与模组同名 —— {@code lost_realm:lost_realm}。这是刻意的:
 * 整合包的所有力量(灵魂、属性、勋章)都被设定为源自遗落境,
 * 终末之影是这个源头的守卫者。
 */
public final class BossRegistry {

    /** 遗落境维度的 Level 键。 */
    public static final ResourceKey<Level> REALM_LEVEL = ResourceKey.create(
            Registries.DIMENSION,
            new ResourceLocation(Medals.MODID, "lost_realm"));

    /** 遗落境维度的 DimensionType 键(参数见 data/lost_realm/dimension_type/lost_realm.json)。 */
    public static final ResourceKey<DimensionType> REALM_TYPE = ResourceKey.create(
            Registries.DIMENSION_TYPE,
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

    /**
     * 幂等哨兵方块的埋深(相对城中心)。
     *
     * <p>旧版用「城中心方块是否为空气」判断是否已生成 ——
     * 一旦在中心放了 Boss 高台就永久失效。
     * 新方案在城中心地下 {@value #SENTINEL_DEPTH} 格埋一块
     * {@code ARENA_FRAME}:任何正常建造都不会碰到该位置,
     * 稳定且零额外状态存储。
     */
    public static final int SENTINEL_DEPTH = 3;

    // ================================================================
    // 数值 —— 全部可从配置读取
    // ----------------------------------------------------------------
    // 为什么用「方法」而不是「常量」:
    // 这些数值要出现在可视化配置界面里,必须运行期可读。
    // 但 Forge 的 ConfigValue 在配置加载完成前为 null,
    // 而本类的常量可能被属性注册(游戏启动早期)读取 ——
    // 因此每个访问器都做 null 兜底,返回硬编码的默认值。
    // 这样"配置没加载好"也绝不会崩,只是暂时用默认数值。
    // ================================================================

    /** Boss 最大生命值的默认值(配置缺省时使用)。 */
    public static final double DEFAULT_BOSS_MAX_HEALTH = 800.0D;

    /** 阶段Ⅱ血量阈值默认值(0~1)。 */
    public static final float DEFAULT_PHASE_2_THRESHOLD = 0.60F;

    /** 阶段Ⅲ血量阈值默认值(0~1)。 */
    public static final float DEFAULT_PHASE_3_THRESHOLD = 0.25F;

    /** 终末审判读条基准秒数默认值。 */
    public static final int DEFAULT_JUDGEMENT_BASE_SECONDS = 12;

    /** 第二阶段的分身数量(含本体)。 */
    public static final int PHASE_2_CLONES = 3;

    /** Boss 最大生命值(配置优先,未加载时回退默认)。 */
    public static double bossMaxHealth() {
        try {
            Double v = com.lost_realm.config.MedalsConfig.BOSS_MAX_HEALTH == null
                    ? null : com.lost_realm.config.MedalsConfig.BOSS_MAX_HEALTH.get();
            return v == null ? DEFAULT_BOSS_MAX_HEALTH : v;
        } catch (Throwable t) {
            return DEFAULT_BOSS_MAX_HEALTH;
        }
    }

    /** 阶段Ⅱ血量阈值(0~1)。 */
    public static float phase2Threshold() {
        Integer pct = readInt("phase_2", null);
        int v = pct == null ? Math.round(DEFAULT_PHASE_2_THRESHOLD * 100.0F) : pct;
        return Math.max(0.01F, Math.min(0.99F, v / 100.0F));
    }

    /** 阶段Ⅲ血量阈值(0~1)。 */
    public static float phase3Threshold() {
        Integer pct = readInt("phase_3", null);
        int v = pct == null ? Math.round(DEFAULT_PHASE_3_THRESHOLD * 100.0F) : pct;
        return Math.max(0.01F, Math.min(0.99F, v / 100.0F));
    }

    /** 终末审判读条基准秒数。 */
    public static int judgementBaseSeconds() {
        try {
            Integer v = com.lost_realm.config.MedalsConfig.BOSS_JUDGEMENT_SECONDS == null
                    ? null : com.lost_realm.config.MedalsConfig.BOSS_JUDGEMENT_SECONDS.get();
            return v == null ? DEFAULT_JUDGEMENT_BASE_SECONDS : v;
        } catch (Throwable t) {
            return DEFAULT_JUDGEMENT_BASE_SECONDS;
        }
    }

    /** Boss 系统是否启用。 */
    public static boolean enabled() {
        try {
            Boolean v = com.lost_realm.config.MedalsConfig.BOSS_ENABLED == null
                    ? null : com.lost_realm.config.MedalsConfig.BOSS_ENABLED.get();
            return v == null || v;
        } catch (Throwable t) {
            return true;
        }
    }

    // ================================================================
    // dv5 新流程数值 —— 波次 / 决斗 / 锁血演出
    // ----------------------------------------------------------------
    // 一律「方法 + null 兜底」:启动早期事件(EntityAttributeCreationEvent 等)
    // 会在配置加载之前读取本类,此时 ConfigValue 为 null。
    // ================================================================

    /** 波数默认值。 */
    public static final int DEFAULT_WAVE_COUNT = 5;

    /** 每波怪数默认值。 */
    public static final int DEFAULT_WAVE_MOB_COUNT = 5;

    /** 波间隔秒数默认值。 */
    public static final int DEFAULT_WAVE_INTERVAL_SECONDS = 8;

    /** 波次怪种默认序号(0 僵尸 / 1 骷髅 / 2 蜘蛛 / 3 掠夺者 / 4 凋灵骷髅)。 */
    public static final int DEFAULT_WAVE_MOB_TYPE = 0;

    /** 决斗一击扣除玩家最大生命值的百分比默认值。 */
    public static final int DEFAULT_DUEL_HIT_PERCENT = 50;

    /** 每次锁血后回满的耗时秒数默认值。 */
    public static final int DEFAULT_TRANSITION_HEAL_SECONDS = 30;

    /** 波数(配置优先)。 */
    public static int waveCount() {
        return clamp(readIntConfig(com.lost_realm.config.MedalsConfig.BOSS_WAVE_COUNT,
                DEFAULT_WAVE_COUNT), 1, 10);
    }

    /** 每波怪数(配置优先)。 */
    public static int waveMobCount() {
        return clamp(readIntConfig(com.lost_realm.config.MedalsConfig.BOSS_WAVE_MOB_COUNT,
                DEFAULT_WAVE_MOB_COUNT), 1, 20);
    }

    /** 波间隔秒数(配置优先)。 */
    public static int waveIntervalSeconds() {
        return clamp(readIntConfig(com.lost_realm.config.MedalsConfig.BOSS_WAVE_INTERVAL_SECONDS,
                DEFAULT_WAVE_INTERVAL_SECONDS), 1, 60);
    }

    /**
     * 波次怪种(配置优先)。
     *
     * <p>默认全部使用原版怪 —— 用户明确要求「先全部用原版的代替,
     * 配置选项里面可以更改」。
     *
     * <p>序号映射:0 僵尸 / 1 骷髅 / 2 蜘蛛 / 3 掠夺者 / 4 凋灵骷髅。
     * 越界的配置值会被夹回 [0,4],绝不返回 null。
     */
    public static net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.monster.Monster>
            waveMobType() {
        int idx = clamp(readIntConfig(com.lost_realm.config.MedalsConfig.BOSS_WAVE_MOB_TYPE,
                DEFAULT_WAVE_MOB_TYPE), 0, 4);
        return switch (idx) {
            case 1 -> net.minecraft.world.entity.EntityType.SKELETON;
            case 2 -> net.minecraft.world.entity.EntityType.SPIDER;
            case 3 -> net.minecraft.world.entity.EntityType.PILLAGER;
            case 4 -> net.minecraft.world.entity.EntityType.WITHER_SKELETON;
            default -> net.minecraft.world.entity.EntityType.ZOMBIE;
        };
    }

    /** 波次怪种的显示名语言键后缀(供配置界面使用)。 */
    public static String waveMobTypeKey(int idx) {
        return switch (clamp(idx, 0, 4)) {
            case 1 -> "skeleton";
            case 2 -> "spider";
            case 3 -> "pillager";
            case 4 -> "wither_skeleton";
            default -> "zombie";
        };
    }

    /** 决斗一击扣除玩家最大生命值的比例(0~1)。 */
    public static float duelHitPercent() {
        int pct = clamp(readIntConfig(com.lost_realm.config.MedalsConfig.BOSS_DUEL_HIT_PERCENT,
                DEFAULT_DUEL_HIT_PERCENT), 1, 100);
        return pct / 100.0F;
    }

    /**
     * 每次锁血后回满的耗时秒数。
     *
     * <p>这段时间是给玩家的<b>准备时间</b>(喝药、拉开距离、等技能 CD),
     * 因此默认给足 30 秒。上限放到 120 秒是给整合包作者留的余地 ——
     * 硬核包可以拉到 2 分钟慢慢演出,速通包可以压到 5 秒。
     */
    public static int transitionHealSeconds() {
        return clamp(readIntConfig(com.lost_realm.config.MedalsConfig.BOSS_TRANSITION_HEAL_SECONDS,
                DEFAULT_TRANSITION_HEAL_SECONDS), 5, 120);
    }

    /** 内部辅助:安全读取一个 IntValue(带 null 兜底)。 */
    private static int readIntConfig(ForgeConfigSpec.IntValue value, int fallback) {
        try {
            if (value == null) {
                return fallback;
            }
            Integer v = value.get();
            return v == null ? fallback : v;
        } catch (Throwable t) {
            return fallback;
        }
    }

    /** 内部辅助:夹取到闭区间。 */
    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** 内部辅助:统一的安全读取。 */
    private static Integer readInt(String which, Integer fallback) {
        try {
            switch (which) {
                case "phase_2":
                    return com.lost_realm.config.MedalsConfig.BOSS_PHASE_2_PERCENT == null
                            ? fallback : com.lost_realm.config.MedalsConfig.BOSS_PHASE_2_PERCENT.get();
                case "phase_3":
                    return com.lost_realm.config.MedalsConfig.BOSS_PHASE_3_PERCENT == null
                            ? fallback : com.lost_realm.config.MedalsConfig.BOSS_PHASE_3_PERCENT.get();
                default:
                    return fallback;
            }
        } catch (Throwable t) {
            return fallback;
        }
    }

    private BossRegistry() {}
}
