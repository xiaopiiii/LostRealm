package com.lost_realm.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;

/**
 * 「眩晕」状态效果。
 *
 * <p>Minecraft 没有这个原生状态，所以自己注册一个。它本身是个<b>纯标记</b>，
 * 真正的作用分三处施加（都在 {@code TankShieldHandler}）：
 * <ul>
 *   <li><b>动不了</b> —— 逐 tick 把水平速度清零、停掉寻路、清空攻击目标；</li>
 *   <li><b>打不出</b> —— 在伤害结算最前面拦截「带眩晕者造成的伤害」，直接取消；</li>
 *   <li><b>看得见</b> —— 头顶转圈的星星粒子。</li>
 * </ul>
 *
 * <p>为什么不做成「属性修饰符把移速砍到 0」：那样生物仍会转身、仍会挥拳，
 * 只是站着不动 —— 看着像卡住了，不像被打懵了。速度清零 + 目标清空才是那个味道。
 */
public class StunnedEffect extends MobEffect {

    /** 亮黄 —— 与缴械的暗红、缓慢的蓝都区分得开。 */
    private static final int COLOR = 0xFFCC33;

    public StunnedEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }

    /** 眩晕期间不接受任何属性加成以外的处理，全部逻辑在 handler 里。 */
    @Override
    public void removeAttributeModifiers(LivingEntity entity, AttributeMap attributes, int amplifier) {
        // 本效果不挂任何属性修饰符，无需清理。
    }
}
