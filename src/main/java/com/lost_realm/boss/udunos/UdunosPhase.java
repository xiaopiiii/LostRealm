package com.lost_realm.boss.udunos;

import net.minecraft.world.BossEvent;

/**
 * Udunos 的四个阶段。
 *
 * <h2>与旧「终末之影」的三形态完全不同</h2>
 * 旧 Boss 的形态切换是<b>换模型</b>（人形 → 触手 → 巨大化），
 * 本质上还是同一个战斗框架。Udunos 的四阶段不是换外观，
 * 而是<b>换招式池</b> —— 血量阈值一到就立刻切阶段、清场、转场，
 * 然后启用一批新的招式。本体始终是那条虫。
 *
 * <h2>阶段与血线的对应</h2>
 * <table border="1">
 *   <tr><th>阶段</th><th>血线</th><th>性质</th><th>冲锋预警</th></tr>
 *   <tr><td>Ⅰ</td><td>100% ~ 80%</td><td>教学：只有三种招，压力最低</td><td>0.9 s</td></tr>
 *   <tr><td>Ⅱ</td><td>80% ~ 60%</td><td>加入星门与引力残星</td><td>0.8 s</td></tr>
 *   <tr><td>Ⅲ</td><td>60% ~ 15%</td><td><b>战斗主体</b>，招式最多（八种）</td><td>0.7 s</td></tr>
 *   <tr><td>Ⅳ</td><td>15% ~ 0%</td><td>高压收尾，<b>没有虚弱阶段</b></td><td>0.7 s</td></tr>
 * </table>
 *
 * <p>血条颜色逐阶段推进（紫 → 粉 → 红 → 白）——
 * 玩家不看数字也能从颜色判断打到哪一步了。
 */
public enum UdunosPhase {

    /** 第一阶段：碎星播撒 / 噬光冲锋 / 星弧巡猎。 */
    ONE(0, "boss.lost_realm.udunos.phase.1", BossEvent.BossBarColor.PURPLE),

    /** 第二阶段：加入星门贯穿、引力残星。 */
    TWO(1, "boss.lost_realm.udunos.phase.2", BossEvent.BossBarColor.PINK),

    /** 第三阶段：招式最多的战斗主体。 */
    THREE(2, "boss.lost_realm.udunos.phase.3", BossEvent.BossBarColor.RED),

    /** 第四阶段：高压收尾，无巡游、无虚弱。 */
    FOUR(3, "boss.lost_realm.udunos.phase.4", BossEvent.BossBarColor.WHITE);

    private final int index;
    private final String langKey;
    private final BossEvent.BossBarColor barColor;

    UdunosPhase(int index, String langKey, BossEvent.BossBarColor barColor) {
        this.index = index;
        this.langKey = langKey;
        this.barColor = barColor;
    }

    public int index() {
        return index;
    }

    public String langKey() {
        return langKey;
    }

    public BossEvent.BossBarColor barColor() {
        return barColor;
    }

    /** 本阶段的噬光冲锋预警时长（tick）。 */
    public int chargeWarningTicks() {
        int[] t = UdunosTuning.CHARGE_WARNING_TICKS;
        return t[Math.min(index, t.length - 1)];
    }

    /** 本阶段两次招式之间的巡游时长（tick）。第四阶段为 0（不给喘息）。 */
    public int cruiseTicks() {
        int[] t = UdunosTuning.CRUISE_TICKS;
        return t[Math.min(index, t.length - 1)];
    }

    /** 该阶段是否在血线以下（已进入或更靠后）。 */
    public boolean isReachedBy(float healthRatio) {
        return healthRatio <= thresholdOf(this);
    }

    /** 各阶段的<b>进入</b>阈值（血量比例）。第一阶段恒为 1.0。 */
    public static float thresholdOf(UdunosPhase phase) {
        return switch (phase) {
            case ONE -> 1.0F;
            case TWO -> UdunosTuning.PHASE_1_END;
            case THREE -> UdunosTuning.PHASE_2_END;
            case FOUR -> UdunosTuning.PHASE_3_END;
        };
    }

    /**
     * 按当前血量比例算出应当处于哪个阶段。
     *
     * <p>注意是「应当」而不是「将要」—— 换阶段必须是<b>即时</b>的（原设计：
     * "达到血量阈值立即切阶段，这一条优先于'技能放完再换招'"），
     * 所以每 tick 都会用这个方法重新判定，而不是等招式结束。
     */
    public static UdunosPhase forHealth(float ratio) {
        if (ratio <= UdunosTuning.PHASE_3_END) {
            return FOUR;
        }
        if (ratio <= UdunosTuning.PHASE_2_END) {
            return THREE;
        }
        if (ratio <= UdunosTuning.PHASE_1_END) {
            return TWO;
        }
        return ONE;
    }

    /** 按序号取（用于存档与同步）。越界自动夹取。 */
    public static UdunosPhase byIndex(int i) {
        UdunosPhase[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }

    /** 下一阶段；已是最后阶段则返回自身。 */
    public UdunosPhase next() {
        return byIndex(index + 1);
    }
}
