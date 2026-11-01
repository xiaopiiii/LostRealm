package com.lost_realm.boss;

import net.minecraft.util.StringRepresentable;

/**
 * 四相祭坛的种类枚举。
 *
 * <p>四种祭坛分别对应整合包的四大内容体系 —— 这是「整合包 Boss」定位的直接体现:
 * 装了这些模组却从不用它们能力的玩家,会在这里碰壁。
 *
 * <h2>为什么用中文注释写清"判定依据"</h2>
 * 判定逻辑分散在 {@code WardenFightHandler} 的伤害事件里,枚举本身只存元数据。
 * 在这里写清每种祭坛"靠什么点亮",是为了将来调平衡时不必翻事件代码。
 */
public enum WardenAltar implements StringRepresentable {

    /**
     * 灵魂祭坛 —— 用<b>诡厄巫法(Goety)的法术</b>命中 Boss。
     *
     * <p>判定依据:伤害类型为 {@code goety:magic} 或伤害源直接实体是 Goety 法术投射物。
     * 未安装 Goety 时,本祭坛改为「用任意魔法伤害点亮」,避免死局。
     */
    SOUL("soul", "message.lost_realm.altar.soul.hint"),

    /**
     * 神化祭坛 —— 触发一次 <b>属性暴击</b>。
     *
     * <p>判定依据:该次伤害「显著高于该玩家近期均值」。
     * 暴击由本模组的 {@code RealmAttributeEffects#onHurtCrit} 在
     * {@code LivingHurtEvent} 内部掷骰，<b>没有对外发布「本次是否暴击」的信号</b>，
     * 所以这里沿用伤害均值的启发式（见 {@code WardenFightHandler}）。
     *
     * <p>要改成精确判定，只需在 onHurtCrit 命中时给受害者打一个 tick 标记，
     * 让 WardenFightHandler 优先读标记 —— 属于后续可选优化，非缺陷。
     */
    APOTHEOSIS("apotheosis", "message.lost_realm.altar.apotheosis.hint"),

    /**
     * 勋章祭坛 —— 用<b>四勋章主动技能</b>命中 Boss。
     *
     * <p>判定依据:伤害结算时玩家处于任一勋章主动技能的生效窗口内
     * (读取 {@code PlayerMedals} 的技能状态)。
     * 这是与 {@code skill/} 系统唯一的耦合点,且为单向只读。
     */
    MEDAL("medal", "message.lost_realm.altar.medal.hint"),

    /**
     * 纯净祭坛 —— <b>不依赖任何加成</b>造成伤害(徒手 / 原版武器)。
     *
     * <p>判定依据:该次伤害未携带任何属性修饰符加成,
     * 且玩家当前未佩戴勋章、未持有附魔武器。
     * 这条最反直觉 —— 却也最能逼玩家"卸下装备重新审视"。
     */
    PURE("pure", "message.lost_realm.altar.pure.hint");

    private final String id;
    private final String hintKey;

    WardenAltar(String id, String hintKey) {
        this.id = id;
        this.hintKey = hintKey;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public String id() {
        return id;
    }

    /** 祭坛名称语言键。 */
    public String langKey() {
        return "altar.lost_realm." + id;
    }

    /** 激活条件提示语言键。 */
    public String hintKey() {
        return hintKey;
    }
}
