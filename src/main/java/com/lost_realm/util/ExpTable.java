package com.lost_realm.util;

import java.util.Arrays;

import com.lost_realm.config.MedalsConfig;

/**
 * 熟练度经验表。
 *
 * <h2>规则</h2>
 * <ul>
 *   <li>最高 60 级（beta26 起；配置 {@code max_level}）</li>
 *   <li>Lv.1 → Lv.2 需要 {@code base_exp}（默认 100）</li>
 *   <li><b>六段成长率</b>：每 10 级一档，逐段收窄</li>
 * </ul>
 *
 * <pre>
 *   1–10 级   +7%       11–20 级  +6%      21–30 级  +5%
 *   31–40 级  +4%       41–50 级  +3%      51–60 级  +2%
 * </pre>
 *
 * <p>即「升到 Lv.2 需 100，升到 Lv.3 需 107……」；到 11 级时改用 6%，
 * 21 级改 5%，以此类推。
 *
 * <p>为什么逐段收窄而不是固定一个百分比：固定 8% 走到 60 级需要 13.9 万经验
 * （约 4600 次击杀），最后 10 级一级就要 1 万，那是纯刷。按 7/6/5/4/3/2 收窄后
 * 总量 3.6 万（约 1200 次击杀），最后一级 1,338 —— 长线但不劝退。
 * 前期高、后期低，是长线成长曲线的常见形状。
 *
 * <p>表中 {@code table[level]} 表示「从 level 升到 level+1」所需经验。
 */
public final class ExpTable {

    private static int cachedMax = -1;
    private static int cachedBase = -1;
    /** 六段成长率缓存：每 10 级一档。 */
    private static final int[] cachedGrowth = new int[6];
    private static long[] table = new long[0];

    /** 获取「从 level 升到 level+1」所需经验。level 超出范围时返回 Long.MAX_VALUE。 */
    public static long expToNext(int level) {
        rebuildIfNeeded();
        if (level < 1 || level >= cachedMax) {
            return Long.MAX_VALUE;
        }
        return table[level];
    }

    /** 某等级区间的累计所需总经验。 */
    public static long totalExpForLevel(int level) {
        rebuildIfNeeded();
        long sum = 0;
        for (int i = 1; i < level && i < table.length; i++) {
            sum += table[i];
        }
        return sum;
    }

    public static int maxLevel() {
        rebuildIfNeeded();
        return cachedMax;
    }

    /**
     * 依据当前经验值计算等级。
     *
     * @param exp 已累计的总经验
     */
    public static int levelFromExp(long exp) {
        rebuildIfNeeded();
        long acc = 0;
        for (int lv = 1; lv < cachedMax; lv++) {
            acc += table[lv];
            if (exp < acc) {
                return lv;
            }
        }
        return cachedMax;
    }

    /** 当前等级内的经验进度(0.0~1.0)。 */
    public static double progress(long exp, int level) {
        rebuildIfNeeded();
        if (level >= cachedMax) {
            return 1.0D;
        }
        long base = totalExpForLevel(level);
        long need = table[level];
        if (need <= 0) {
            return 1.0D;
        }
        double p = (double) (exp - base) / (double) need;
        return Math.max(0.0D, Math.min(1.0D, p));
    }

    /**
     * 阶位分界线：与三章节、两道枷锁完全对齐。
     *
     * <pre>
     *   1–20   第一章（枷锁在 20）
     *   21–40  第二章（枷锁在 40）
     *   41–60  第三章
     * </pre>
     *
     * <p>以前是 10/20/30 三档 —— 那时上限只有 30，每 10 级一档刚好。
     * 上限拉到 60 之后如果还按 10 级分，会出现六档而只有三个名字；
     * 按 20 级分，正好一章一个称谓。
     */
    public static final int TIER_1_END = 20;
    public static final int TIER_2_END = 40;

    /** 射手勋章阶位：1~20 游射 / 21~40 游侠 / 41~60 箭神。 */
    public static String archerTierOf(int level) {
        if (level <= TIER_1_END) {
            return "游射";
        }
        if (level <= TIER_2_END) {
            return "游侠";
        }
        return "箭神";
    }

    /** 阶位：1~20 见习 / 21~40 精锐 / 41~60 宗师（战士与坦克使用）。 */
    public static String tierOf(int level) {
        if (level <= TIER_1_END) {
            return "见习";
        }
        if (level <= TIER_2_END) {
            return "精锐";
        }
        return "宗师";
    }

    /** 法师勋章阶位：1~20 学徒 / 21~40 术士 / 41~60 大巫。 */
    public static String mageTierOf(int level) {
        if (level <= TIER_1_END) {
            return "学徒";
        }
        if (level <= TIER_2_END) {
            return "术士";
        }
        return "大巫";
    }

    /** 召唤师勋章阶位：1~20 唤灵者 / 21~40 结契者 / 41~60 深渊召唤者。 */
    public static String summonerTierOf(int level) {
        if (level <= TIER_1_END) {
            return "唤灵者";
        }
        if (level <= TIER_2_END) {
            return "结契者";
        }
        return "深渊召唤者";
    }

    /** 按勋章类型返回对应阶位名称。 */
    public static String tierOf(com.lost_realm.skill.MedalType type, int level) {
        return switch (type) {
            case ARCHER -> archerTierOf(level);
            case MAGE -> mageTierOf(level);
            case SUMMONER -> summonerTierOf(level);
            default -> tierOf(level);
        };
    }

    /**
     * 阶位对应的<b>语言键</b>（{@code tier.lost_realm.*}）。
     *
     * <p>本类那几个 {@code tierOf} 返回的是硬编码中文，只适合日志/调试；
     * 凡是<b>要显示给玩家</b>的地方（tooltip、聊天）都应当走这个键，
     * 否则英文客户端会看到中文阶位。
     */
    public static String tierLangKey(com.lost_realm.skill.MedalType type, int level) {
        String prefix = switch (type) {
            case ARCHER -> "tier.lost_realm.archer.";
            case MAGE -> "tier.lost_realm.mage.";
            case SUMMONER -> "tier.lost_realm.summoner.";
            default -> "tier.lost_realm.normal.";
        };
        // 1~20 / 21~40 / 41~60 三档 —— 与章节、枷锁对齐
        return prefix + (level <= TIER_1_END ? 1 : (level <= TIER_2_END ? 2 : 3));
    }

    private static synchronized void rebuildIfNeeded() {
        int max = MedalsConfig.MAX_LEVEL.get();
        int base = MedalsConfig.BASE_EXP.get();
        int[] growth = {
                MedalsConfig.EXP_GROWTH_1_10.get(),
                MedalsConfig.EXP_GROWTH_11_20.get(),
                MedalsConfig.EXP_GROWTH_21_30.get(),
                MedalsConfig.EXP_GROWTH_31_40.get(),
                MedalsConfig.EXP_GROWTH_41_50.get(),
                MedalsConfig.EXP_GROWTH_51_60.get(),
        };

        boolean same = (max == cachedMax && base == cachedBase);
        if (same) {
            for (int i = 0; i < growth.length; i++) {
                if (growth[i] != cachedGrowth[i]) {
                    same = false;
                    break;
                }
            }
        }
        if (same) {
            return;
        }

        cachedMax = max;
        cachedBase = base;
        System.arraycopy(growth, 0, cachedGrowth, 0, growth.length);

        long[] t = new long[max + 1];
        double cur = base;
        for (int lv = 1; lv < max; lv++) {
            // table[lv] = 从 lv 升到 lv+1 所需经验
            t[lv] = Math.max(1L, Math.round(cur));
            // 决定「lv 升 lv+1」这一段用哪一档成长率：
            // 1~10 用第 0 档、11~20 用第 1 档 …… 51~60 用第 5 档。
            int band = Math.max(0, Math.min(growth.length - 1, (lv - 1) / 10));
            cur = cur * (1.0D + growth[band] / 100.0D);
        }
        table = t;
        Arrays.fill(t, 0, Math.min(1, t.length), 0L);
    }

    private ExpTable() {}
}
