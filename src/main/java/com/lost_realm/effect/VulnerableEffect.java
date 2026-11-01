package com.lost_realm.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;

/**
 * 「脆弱」状态效果 —— 敕令之契的标记。
 *
 * <p>被标记者在持续时间内<b>受到的伤害会增加</b>（幅度见配置
 * {@code CONTRACT_DECREE_VULNERABLE}）。
 *
 * <h2>为什么是纯标记</h2>
 * 与 {@code 眩晕} / {@code 缴械} 同一套做法：效果本身不挂任何属性修饰符，
 * 只在 {@code SummonerCharmHandler} 的受伤结算里被读到一次。
 * 这样增幅能精确作用在「一次伤害」上，也不会因为多来源叠加而失控。
 */
public class VulnerableEffect extends MobEffect {

    /** 暗紫 —— 与缴械的暗红、眩晕的亮黄、缓慢的蓝都不撞。 */
    private static final int COLOR = 0xB04CD8;

    public VulnerableEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }

    @Override
    public void removeAttributeModifiers(LivingEntity entity, AttributeMap attributes, int amplifier) {
        // 本效果不挂任何属性修饰符，无需清理。
    }
}
