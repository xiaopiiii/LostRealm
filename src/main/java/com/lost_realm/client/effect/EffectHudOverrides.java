package com.lost_realm.client.effect;

import com.lost_realm.config.MedalsConfig;

/**
 * 状态效果 HUD 的<b>运行时覆盖层</b>。
 *
 * <h2>为什么需要这一层</h2>
 * Forge 的 {@code ForgeConfigSpec.ConfigValue} 在运行时是<b>只读</b>的 ——
 * 可以通过 {@code set()} 改内存值,但该改动不会自动重绘,而且若不做
 * {@code save()} 就会在退出后丢失;更麻烦的是直接 {@code set()} 会让
 * 「配置屏幕里显示的值」与「实际生效的值」出现耦合,编辑界面里反复点击会比较难管理。
 *
 * <p>因此这里加一层轻量覆盖:编辑界面把玩家的选择写进本类,
 * 渲染器优先读本类、读不到再回落到配置。玩家确认后一次性写入配置文件并持久化,
 * 中途取消则整个覆盖层丢弃,配置保持原样。
 *
 * <h2>取值优先级</h2>
 * <pre>
 *   编辑界面未打开  →  直接读配置
 *   编辑界面已打开  →  先读覆盖层(未设置则回落配置)
 * </pre>
 */
public final class EffectHudOverrides {

    /** 覆盖值哨兵:{@code null} 表示「未覆盖,使用配置值」。 */
    private static Integer scaleOverride;
    private static Integer cornerOverride;
    private static Integer flowOverride;
    private static Integer timerOverride;
    private static Boolean enabledOverride;
    private static Boolean harmfulBorderOverride;

    /** 是否存在任何未提交的覆盖(用于判断"有改动")。 */
    public static boolean hasOverrides() {
        return scaleOverride != null || cornerOverride != null
                || flowOverride != null || timerOverride != null
                || enabledOverride != null || harmfulBorderOverride != null;
    }

    // ================================================================
    // 读取:优先覆盖,其次配置
    // ================================================================

    public static boolean enabled() {
        return enabledOverride != null ? enabledOverride : MedalsConfig.EFFECT_HUD_ENABLED.get();
    }

    public static int scale() {
        return scaleOverride != null ? scaleOverride : MedalsConfig.EFFECT_HUD_SCALE.get();
    }

    public static int corner() {
        return cornerOverride != null ? cornerOverride : MedalsConfig.EFFECT_HUD_CORNER.get();
    }

    public static int flow() {
        return flowOverride != null ? flowOverride : MedalsConfig.EFFECT_HUD_FLOW.get();
    }

    public static int timer() {
        return timerOverride != null ? timerOverride : MedalsConfig.EFFECT_HUD_TIMER.get();
    }

    /** 是否为减益效果绘制红色外框。 */
    public static boolean harmfulBorder() {
        return harmfulBorderOverride != null
                ? harmfulBorderOverride
                : MedalsConfig.EFFECT_HUD_HARMFUL_BORDER.get();
    }

    // ================================================================
    // 写入:仅改覆盖层,不碰配置
    // ================================================================

    public static void setEnabled(boolean value) {
        enabledOverride = value;
    }

    public static void setScale(int value) {
        scaleOverride = value;
    }

    public static void setCorner(int value) {
        cornerOverride = value;
    }

    public static void setFlow(int value) {
        flowOverride = value;
    }

    public static void setTimer(int value) {
        timerOverride = value;
    }

    public static void setHarmfulBorder(boolean value) {
        harmfulBorderOverride = value;
    }

    /** 丢弃全部覆盖(取消编辑)。 */
    public static void clear() {
        scaleOverride = null;
        cornerOverride = null;
        flowOverride = null;
        timerOverride = null;
        enabledOverride = null;
        harmfulBorderOverride = null;
    }

    /**
     * 把当前覆盖提交到配置文件并持久化。
     *
     * <p>只有确实被覆盖过的项才会写入,避免「打开编辑界面又原样关掉」
     * 也触发一次无意义的配置写盘。
     */
    public static void commit() {
        if (enabledOverride != null) {
            MedalsConfig.EFFECT_HUD_ENABLED.set(enabledOverride);
        }
        if (scaleOverride != null) {
            MedalsConfig.EFFECT_HUD_SCALE.set(scaleOverride);
        }
        if (cornerOverride != null) {
            MedalsConfig.EFFECT_HUD_CORNER.set(cornerOverride);
        }
        if (flowOverride != null) {
            MedalsConfig.EFFECT_HUD_FLOW.set(flowOverride);
        }
        if (timerOverride != null) {
            MedalsConfig.EFFECT_HUD_TIMER.set(timerOverride);
        }
        if (harmfulBorderOverride != null) {
            MedalsConfig.EFFECT_HUD_HARMFUL_BORDER.set(harmfulBorderOverride);
        }
        MedalsConfig.CLIENT_SPEC.save();
        clear();
    }

    private EffectHudOverrides() {}
}
