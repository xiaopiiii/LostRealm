package com.lost_realm.skill.archer;

/**
 * 额外箭矢的生成模式(存放在箭矢持久数据的 {@code medals_volley_mode} 键中)。
 *
 * <p>用于区分同名「额外箭矢」的不同来源与结算规则。
 */
public enum ExtraArrowMode {

    /** 【三发】额外箭矢:按序号递减结算 */
    VOLLEY,

    /** 兼容多重射击时,是被强化的原版箭(数量不变,单根加伤) */
    MULTISHOT_BONUS,

    /** 【箭雨】掉落箭矢:独立结算,不参与三发递减 */
    RAIN;

    public static final String NBT_KEY = "medals_extra_volley_mode";

    /** 安全解析,未知返回 {@code null}。 */
    public static ExtraArrowMode parse(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
