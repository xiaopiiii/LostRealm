package com.lost_realm.util;

import com.lost_realm.config.MedalsConfig;

/**
 * 体验改进 · 第 1 项:<b>Minecraft 原版经验等级</b>曲线。
 *
 * <h2>重要区分</h2>
 * 本类管的是<b>原版玩家经验等级</b>(屏幕下方的绿条、附魔台消耗的那个),
 * <b>与勋章熟练度({@link ExpTable},Lv.2~30)完全是两套独立系统</b>,请勿混淆。
 *
 * <h2>曲线规则(可由配置调整)</h2>
 * <ul>
 *   <li>Lv.2 → 40:每级 = 上一级 × 1.10</li>
 *   <li>Lv.40 → 70:每级 = 上一级 × 1.08</li>
 *   <li>Lv.70 → 100:每级 = 上一级 × 1.04</li>
 *   <li>Lv.101 及以上:基础增长率 2%;每跨越 25 级(125/150/175…)增长率 ×0.95;
 *       当增长率低于 0.005% 后固定为 0.005%,不再变化</li>
 * </ul>
 *
 * <h2>索引语义(与原版一致)</h2>
 * {@code table[level]} = 从 {@code level} 级升到 {@code level+1} 级所需经验,即
 * {@link net.minecraft.world.entity.player.Player#getXpNeededForNextLevel()} 的返回值。
 * 原版参照:{@code table[0] = 7}(0→1 级)、{@code table[1] = 9}(1→2 级)……
 *
 * <h2>实现要点</h2>
 * 采用<b>逐级累乘</b>而非闭式解 —— 因为界值是「每级 = 上一级 × 系数」,
 * 累乘能保证与规则描述<b>逐位一致</b>,不会因取整时机不同产生偏差。
 *
 * <h2>溢出安全性</h2>
 * 因增长率递减极快,即便到 Lv.4000 也不会超过 {@link Integer#MAX_VALUE},
 * 因此全程用 {@code int} 安全。
 */
public final class VanillaXpCurve {

    /** 缓存参数(全部与配置一一对应) */
    private static int cBase = -1;
    private static int cG1 = -1;
    private static int cG2 = -1;
    private static int cG3 = -1;
    private static int cG100 = -1;
    private static int cDecay = -1;
    private static int cStep = -1;
    private static int cMinMilli = -1;

    /** 预建上限:远超实际玩法所需,同时避免频繁外推。 */
    private static final int BUILD_TO = 10_000;

    /** table[level] = 从 level 升到 level+1 所需经验(与原版索引一致)。 */
    private static long[] table = new long[0];

    /**
     * 从 {@code level} 级升到 {@code level + 1} 级所需经验。
     *
     * <p>等价于原版 {@code Player#getXpNeededForNextLevel()} 的返回值语义。
     *
     * @param level 当前原版经验等级(0 起)
     * @return 所需经验(至少 1)
     */
    public static int xpForNextLevel(int level) {
        rebuildIfNeeded();
        if (level < 0) {
            return (int) table[0];
        }
        if (level <= BUILD_TO) {
            return (int) table[level];
        }
        return extrapolate(level);
    }

    /** 当前缓存覆盖到的最大等级(含)。主要用于测试与调试。 */
    public static int cachedUpTo() {
        rebuildIfNeeded();
        return BUILD_TO;
    }

    // ================================================================
    // 增长率
    // ================================================================

    /**
     * 计算「从 {@code level} 升到 {@code level+1} 之后,再往下一级」所用的增长率(比率,非百分比)。
     *
     * <p>即决定 {@code table[level+1] = table[level] × (1 + growth)} 中的 growth。
     */
    private static double growthFor(int level) {
        if (level < 40) {
            return cG1 / 100.0D;
        }
        if (level < 70) {
            return cG2 / 100.0D;
        }
        if (level < 100) {
            return cG3 / 100.0D;
        }
        // 101 级及以上:基础增长率按台阶递减
        double growth = cG100 / 100.0D;
        int steps = (level - 100) / Math.max(1, cStep);
        if (steps > 0) {
            growth *= Math.pow(cDecay / 100.0D, steps);
        }
        // min_growth 放大 1000 倍存的「百分比」,先 /1000 还原百分比,再 /100 变比率
        double floor = cMinMilli / 1000.0D / 100.0D;
        return Math.max(growth, floor);
    }

    /** 超出缓存范围时,用末级值 + 增长率逐级外推。 */
    private static int extrapolate(int level) {
        double cur = table[BUILD_TO] > 0 ? table[BUILD_TO] : Math.max(1, cBase);
        for (int lv = BUILD_TO; lv < level; lv++) {
            cur = cur * (1.0D + growthFor(lv));
            if (cur > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }
        return (int) Math.max(1.0D, Math.round(cur));
    }

    // ================================================================
    // 缓存
    // ================================================================

    private static synchronized void rebuildIfNeeded() {
        int base = MedalsConfig.VX_BASE_EXP.get();
        int g1 = MedalsConfig.VX_GROWTH_1_40.get();
        int g2 = MedalsConfig.VX_GROWTH_40_70.get();
        int g3 = MedalsConfig.VX_GROWTH_70_100.get();
        int g100 = MedalsConfig.VX_GROWTH_AFTER_100.get();
        int decay = MedalsConfig.VX_DECAY_PERCENT.get();
        int step = MedalsConfig.VX_DECAY_STEP.get();
        int minMilli = MedalsConfig.VX_MIN_GROWTH_MILLI.get();

        if (base == cBase && g1 == cG1 && g2 == cG2 && g3 == cG3
                && g100 == cG100 && decay == cDecay && step == cStep && minMilli == cMinMilli) {
            return;
        }

        cBase = base;
        cG1 = g1;
        cG2 = g2;
        cG3 = g3;
        cG100 = g100;
        cDecay = decay;
        cStep = step;
        cMinMilli = minMilli;

        long[] t = new long[BUILD_TO + 1];

        // t[0] = 从 0 级升到 1 级 = 曲线起点
        // 之后 t[lv+1] = t[lv] × (1 + growth(lv))
        double cur = base;
        t[0] = Math.max(1L, Math.round(cur));
        for (int lv = 0; lv < BUILD_TO; lv++) {
            cur = cur * (1.0D + growthFor(lv));
            if (cur > Integer.MAX_VALUE) {
                cur = Integer.MAX_VALUE;
            }
            t[lv + 1] = Math.max(1L, Math.round(cur));
        }
        table = t;
    }

    private VanillaXpCurve() {}
}
