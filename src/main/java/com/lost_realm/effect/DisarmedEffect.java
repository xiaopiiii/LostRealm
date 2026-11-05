package com.lost_realm.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 「缴械」状态效果。
 *
 * <p>Minecraft 里没有「无法攻击」这种原生状态（虚弱只是降低伤害，不是不能打），
 * 所以这里自己注册一个纯标记效果：真正的作用点在
 * {@code BladeCharmHandler} 的伤害拦截里 —— 谁身上带着它，他造成的伤害直接被取消。
 *
 * <p>效果本身不带任何属性修饰符，图标也只是一个提示用的符号。
 */
public class DisarmedEffect extends MobEffect {

    /** 深红 —— 与控制类负面效果（缓慢的蓝、中毒的绿）明显区分。 */
    private static final int COLOR = 0x8B1A1A;

    public DisarmedEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }
}
