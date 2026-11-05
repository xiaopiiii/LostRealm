package com.lost_realm.skill;

/**
 * 五枚勋章类型。
 *
 * <p>阶位划分:
 * <ul>
 *   <li>射手:1~10 游射 / 11~20 游侠 / 21~30 箭神</li>
 *   <li>法师:1~10 学徒 / 11~20 术士 / 21~30 大巫</li>
 *   <li>召唤师:1~10 唤灵者 / 11~20 结契者 / 21~30 深渊召唤者</li>
 *   <li>刃、坦克:1~10 见习 / 11~20 精锐 / 21~30 宗师</li>
 * </ul>
 */
public enum MedalType {

    /** 射手勋章:远程伤害与机动 */
    ARCHER("archer", "射手勋章", 1),

    /** 刃勋章:近战输出与暴击 */
    BLADE("blade", "刃勋章", 2),

    /** 坦克勋章:防御与续航 */
    TANK("tank", "坦克勋章", 3),

    /**
     * 法师勋章:以【诡厄巫法 / Goety】为底座的法术与召唤。
     *
     * <p>该勋章的所有节点技能都依赖 Goety 的官方 API(灵魂能量 / 聚晶法术 / 仆从)。
     * 未安装 Goety 时,法师勋章会被软禁用(技能不触发、物品不参与配方),
     * 其余勋章不受影响。
     */
    MAGE("mage", "法师勋章", 4),

    /**
     * 召唤师勋章:以【诡厄巫法 / Goety】的仆从体系为底座。
     *
     * <p>与法师勋章同源 —— 两者都依赖 Goety。区别在关注的维度:
     * 法师改造的是<b>法术本身</b>(强度 / 冷却 / 半径),召唤师改造的是
     * <b>仆从</b>(血量 / 伤害 / 速度 / 寿命)。这也解释了两枚勋章为什么能
     * 同时存在而机制不重叠。
     *
     * <p>未安装 Goety 时同样被软禁用。
     */
    SUMMONER("summoner", "召唤师勋章", 5);

    private final String id;
    private final String displayName;
    /** 饰品槽位优先级 */
    private final int priority;

    MedalType(String id, String displayName, int priority) {
        this.id = id;
        this.displayName = displayName;
        this.priority = priority;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public int priority() {
        return priority;
    }

    /**
     * 该勋章解锁的<b>职业专属饰品槽</b>标识。
     *
     * <p>佩戴勋章时，{@code ItemMedal#getAttributeModifiers} 会给对应的
     * Curios {@code SlotAttribute} 挂一个 {@code +N} 的修饰符，
     * 于是槽位从「默认 0 格」变成「N 格」；摘下勋章立刻收回。
     * 这样不玩某个职业的人，饰品界面上不会平白多出几格占位置。
     */
    public String jobSlot() {
        return switch (this) {
            case ARCHER -> "arrow";
            case BLADE -> "blade";
            case TANK -> "shield";
            case MAGE -> "crystal";
            case SUMMONER -> "contract";
        };
    }

    public static MedalType byId(String id) {
        for (MedalType t : values()) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }
}
