package com.lost_realm.mixin.goety;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.Polarice3.Goety.api.entities.IOwned;
import com.Polarice3.Goety.common.ritual.RitualRequirements;

import net.minecraft.world.entity.LivingEntity;

import com.lost_realm.compat.goety.ServantLimit;

/**
 * 让<b>仪式召唤</b>（暗黑祭坛那一套）也认本模组的仆从上限加成。
 *
 * <p>与 {@link SummonSpellLimitMixin} 是同一件事的另一半：法术召唤走那条路，
 * 仪式召唤走这条路（{@code RitualRequirements#canSummon}）。两边都是
 * 「数一数附近同类仆从，超过上限就拒绝」，只是取上限的途径不同 ——
 * 这里取的是仆从实体自己的 {@code IOwned#getSummonLimit}。
 *
 * <p>两条路都做，才不会有「用法杖能多召、用祭坛不能」这种诡异的不一致。
 *
 * <p>{@code remap = false} 的理由同 {@link SummonSpellLimitMixin}：
 * Goety 的 jar 没有经过反混淆，运行时的名字就是源码名。
 */
@Pseudo
@Mixin(RitualRequirements.class)
public abstract class RitualSummonLimitMixin {

    @Redirect(
            method = "canSummon",
            require = 0,
            remap = false,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lcom/Polarice3/Goety/api/entities/IOwned;getSummonLimit(Lnet/minecraft/world/entity/LivingEntity;)I"))
    private static int lostrealm$summonLimit(IOwned servant, LivingEntity owner) {
        return servant.getSummonLimit(owner) + ServantLimit.bonusFor(owner);
    }
}
