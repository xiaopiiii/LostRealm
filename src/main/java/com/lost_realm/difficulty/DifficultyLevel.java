package com.lost_realm.difficulty;

import net.minecraft.ChatFormatting;

/**
 * 五档难度定义 —— 全部数值集中在这一个枚举里。
 *
 * <h2>为什么是一个枚举而不是配置项</h2>
 * 难度是<b>一整组互相咬合的数值</b>(怪物血量/护甲/攻击、玩家攻击/护甲、
 * 回血、熟练度、中立生物、饰品栏位)。拆成七个独立配置项让玩家自己调,
 * 只会得到「血量 +60% 但攻击没变」这种半成品难度。所以这里把它们
 * 绑成一个不可分的选择,由指令 {@code /lr difficulty <1-5>} 切换。
 *
 * <h2>倍率的含义</h2>
 * 所有 {@code xxxMultiplier} 都是<b>最终倍率</b>:{@code 1.2} 表示「变成 120%」,
 * 也就是 +20%。{@code 1.0} 表示该档不改这项。落到属性上统一用
 * {@link net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation#MULTIPLY_TOTAL},
 * 即「在最终数值上再乘一次」,这样玩家看到的面板数字与难度描述永远对得上。
 *
 * <h2>五档一览</h2>
 * <pre>
 *   1 简单   敌对生物 血/甲/攻 -20%
 *   2 普通   原版数值
 *   3 困难   敌对生物 血/甲/攻 +20%
 *   4 噩梦   敌对生物 血/攻 +40%、甲 +30%;玩家 攻/甲 -10%、回血 -15%、熟练度 +7%
 *   5 深渊   敌对生物 血 +60%、攻 +50%、甲 +40%;玩家 攻/甲 -15%、回血 -25%、
 *            熟练度 +15%、中立生物主动攻击、职业饰品栏 +1
 * </pre>
 */
public enum DifficultyLevel {

    /** 1 · 简单:让刚进整合包的玩家先熟悉系统。 */
    EASY(1, "简单", ChatFormatting.GREEN,
            0.80D, 0.80D, 0.80D,
            1.00D, 1.00D, 1.00D, 1.00D,
            false, 0),

    /** 2 · 普通:原版数值,模组的设计基准。 */
    NORMAL(2, "普通", ChatFormatting.WHITE,
            1.00D, 1.00D, 1.00D,
            1.00D, 1.00D, 1.00D, 1.00D,
            false, 0),

    /** 3 · 困难:纯加怪物,不给玩家任何补偿。 */
    HARD(3, "困难", ChatFormatting.YELLOW,
            1.20D, 1.20D, 1.20D,
            1.00D, 1.00D, 1.00D, 1.00D,
            false, 0),

    /**
     * 4 · 噩梦:开始向玩家收税 —— 攻击/护甲/回血三项同时下调,
     * 换回 7% 熟练度。血量涨得比攻击多,战斗被拉长,失误成本变高。
     */
    NIGHTMARE(4, "噩梦", ChatFormatting.RED,
            1.40D, 1.30D, 1.40D,
            0.90D, 0.90D, 0.85D, 1.07D,
            false, 0),

    /**
     * 5 · 深渊:满档。怪物血/攻/甲全面上调,玩家攻击/护甲/回血同时下调,
     * 中立生物转为主动敌对,熟练度 15% 补偿,并额外给一格职业饰品栏
     * —— 越难越需要饰品配装,这一格正是「熬过去」的回报。
     */
    ABYSS(5, "深渊", ChatFormatting.DARK_PURPLE,
            1.60D, 1.40D, 1.50D,
            0.85D, 0.85D, 0.75D, 1.15D,
            true, 1);

    /** 指令里用的编号(1~5),同时是存档里保存的值。 */
    private final int id;
    /** 中文显示名(不带编号)。 */
    private final String displayName;
    /** 聊天栏提示色。 */
    private final ChatFormatting color;

    /** 敌对生物({@code Enemy} 标记,含史莱姆/恶魂等非 Monster)最大生命倍率。 */
    private final double mobHealth;
    /** 敌对生物护甲倍率。 */
    private final double mobArmor;
    /** 敌对生物攻击伤害倍率。 */
    private final double mobAttack;

    /** 玩家攻击伤害倍率。 */
    private final double playerAttack;
    /** 玩家护甲倍率。 */
    private final double playerArmor;
    /** 玩家受到的治疗量倍率(回血效果)。 */
    private final double heal;
    /** 熟练度获取倍率。 */
    private final double proficiency;

    /** 中立生物是否主动攻击玩家。 */
    private final boolean neutralAggressive;
    /** 额外赠送的职业饰品栏格数。 */
    private final int extraJobSlots;

    DifficultyLevel(int id, String displayName, ChatFormatting color,
                    double mobHealth, double mobArmor, double mobAttack,
                    double playerAttack, double playerArmor, double heal, double proficiency,
                    boolean neutralAggressive, int extraJobSlots) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.mobHealth = mobHealth;
        this.mobArmor = mobArmor;
        this.mobAttack = mobAttack;
        this.playerAttack = playerAttack;
        this.playerArmor = playerArmor;
        this.heal = heal;
        this.proficiency = proficiency;
        this.neutralAggressive = neutralAggressive;
        this.extraJobSlots = extraJobSlots;
    }

    public int id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public ChatFormatting color() {
        return color;
    }

    public double mobHealth() {
        return mobHealth;
    }

    public double mobArmor() {
        return mobArmor;
    }

    public double mobAttack() {
        return mobAttack;
    }

    public double playerAttack() {
        return playerAttack;
    }

    public double playerArmor() {
        return playerArmor;
    }

    public double heal() {
        return heal;
    }

    public double proficiency() {
        return proficiency;
    }

    public boolean neutralAggressive() {
        return neutralAggressive;
    }

    public int extraJobSlots() {
        return extraJobSlots;
    }

    /** 该档是否需要改动玩家侧的属性/行为(纯怪物档可以少做几次判断)。 */
    public boolean touchesPlayer() {
        return playerAttack != 1.0D || playerArmor != 1.0D || heal != 1.0D
                || extraJobSlots > 0;
    }

    /** 形如 {@code 深渊(5/5)},用于聊天栏提示。 */
    public String label() {
        return displayName + "(" + id + "/5)";
    }

    /** 按编号取难度;越界时回落到 {@link #NORMAL},避免坏存档直接崩。 */
    public static DifficultyLevel byId(int id) {
        for (DifficultyLevel lv : values()) {
            if (lv.id == id) {
                return lv;
            }
        }
        return NORMAL;
    }

    /** 把任意整数夹到 1~5,再解算成难度。 */
    public static DifficultyLevel clamp(int id) {
        return byId(Math.max(1, Math.min(5, id)));
    }
}
