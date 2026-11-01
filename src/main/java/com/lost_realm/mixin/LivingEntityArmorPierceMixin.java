package com.lost_realm.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

import com.lost_realm.attribute.RealmAttributes;

/**
 * 护甲穿透（{@code lost_realm:armor_pierce}）。
 *
 * <h2>为什么必须是 Mixin，不能在事件里做</h2>
 * 原版伤害流程是
 * {@code hurt()} → {@code LivingHurtEvent}（此时还是<b>原始伤害</b>）
 * → {@code actuallyHurt()} → {@code getDamageAfterArmorAbsorb()}（护甲在这里扣）
 * → {@code getDamageAfterMagicAbsorb()}。
 * Forge 没有「护甲结算完、附魔减伤开始前」的事件，
 * 所以想在护甲公式里动手，只能注入 {@code getDamageAfterArmorAbsorb}。
 *
 * <h2>做法</h2>
 * 在方法 {@code RETURN} 处拿原始伤害<b>重算一遍</b>：
 * 先把目标护甲减掉穿透值，再走原版 {@link CombatRules#getDamageAfterAbsorb}。
 * 不是「在结果上加一段」，是「换一个护甲值重算」—— 两者在护甲接近 0 时差别很大，
 * 前者会让破甲在裸装目标身上凭空多出伤害。
 *
 * <h2>两个必须的例外</h2>
 * <ol>
 *   <li>{@code BYPASSES_ARMOR}（虚空伤害、/kill、饥饿等）本来就无视护甲，
 *       重算会把它们算进护甲公式，反而<b>降低</b>了伤害。</li>
 *   <li>攻击者不是活体（陷阱、环境伤害）时没有属性可读。</li>
 * </ol>
 *
 * <p>⚠️ 本 Mixin 已登记在 {@code lost_realm.mixins.json} 的 {@code mixins}（服务端侧）。
 * 新增/改名后忘了登记 = 静默不生效，不会报错。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityArmorPierceMixin {

    @Inject(method = "getDamageAfterArmorAbsorb", at = @At("RETURN"), cancellable = true)
    private void lostrealm$applyArmorPierce(DamageSource source, float amount,
                                            CallbackInfoReturnable<Float> cir) {
        // 本来就不吃护甲的伤害，维持原样
        if (source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return;
        }
        if (!(source.getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        double pierce = attacker.getAttributeValue(RealmAttributes.armorPierce());
        if (pierce <= 0.001D) {
            return;
        }

        LivingEntity self = (LivingEntity) (Object) this;
        double armor = Math.max(0.0D, self.getArmorValue() - pierce);
        double toughness = self.getAttributeValue(Attributes.ARMOR_TOUGHNESS);

        cir.setReturnValue(CombatRules.getDamageAfterAbsorb(amount, (float) armor, (float) toughness));
    }
}
