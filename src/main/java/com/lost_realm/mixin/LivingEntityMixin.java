package com.lost_realm.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.lost_realm.compat.backpack.BackpackCompat;
import com.lost_realm.config.MedalsConfig;

/**
 * {@link LivingEntity} 注入:承载两项「体验改进」功能。
 *
 * <ul>
 *   <li><b>B3 更好的不死图腾</b> —— {@link #medals$totemFromInventory}:
 *       原版 {@code checkTotemDeathProtection} 只检索主/副手,
 *       这里在方法入口先扫描玩家背包(含精妙背包),找到图腾则自行触发不死效果。</li>
 *   <li><b>B5 死亡保留 Buff</b> —— {@link #medals$snapshotBuffs} /
 *       {@link #medals$restoreBuffs}:原版 {@code die} 内会 {@code removeAllEffects()},
 *       这里在进入时快照、结束时恢复(仅玩家)。</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    /** 死亡帧的效果快照(B5)。每帧仅一个实体在 die,故用实例字段即可。 */
    @Unique
    private List<MobEffectInstance> medals$buffSnapshot;

    @Unique
    private boolean medals$captureBuffs;

    // ==================== B3 更好的不死图腾 ====================

    @Inject(method = "checkTotemDeathProtection", at = @At("HEAD"), cancellable = true)
    private void medals$totemFromInventory(net.minecraft.world.damagesource.DamageSource source,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (!MedalsConfig.QOL_TOTEM_BACKPACK.get()) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof ServerPlayer player)) {
            return;
        }

        ItemStack totem = medals$findTotem(player);
        if (totem.isEmpty()) {
            return; // 背包里没有,交回原版(主/副手)逻辑处理
        }

        // 复刻原版不死图腾效果
        player.setHealth(1.0F);
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
        player.level().broadcastEntityEvent(player, EntityEvent_TOTEM);
        totem.shrink(1);

        cir.setReturnValue(true);
    }

    /** 原版不死图腾触发时的实体事件 id(见 {@code Entity#TOTEM_USE_EVENT})。 */
    @Unique
    private static final byte EntityEvent_TOTEM = 35;

    /** 扫描玩家背包 + 副手 + 精妙背包,返回第一个不死图腾(可消耗)。 */
    @Unique
    private static ItemStack medals$findTotem(Player player) {
        // 主背包(含快捷栏、主栏)
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(Items.TOTEM_OF_UNDYING)) {
                return s;
            }
        }
        // 精妙背包(未装该模组时返回空列表)
        for (ItemStack s : BackpackCompat.collectItems(player)) {
            if (s.is(Items.TOTEM_OF_UNDYING)) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    // ==================== B5 死亡保留 Buff ====================

    @Inject(method = "die", at = @At("HEAD"))
    private void medals$snapshotBuffs(net.minecraft.world.damagesource.DamageSource source, CallbackInfo ci) {
        this.medals$captureBuffs = false;
        LivingEntity self = (LivingEntity) (Object) this;
        if (MedalsConfig.QOL_KEEP_BUFFS_ON_DEATH.get() && self instanceof ServerPlayer) {
            this.medals$buffSnapshot = new ArrayList<>(self.getActiveEffects());
            this.medals$captureBuffs = true;
        }
    }

    @Inject(method = "die", at = @At("TAIL"))
    private void medals$restoreBuffs(net.minecraft.world.damagesource.DamageSource source, CallbackInfo ci) {
        if (!this.medals$captureBuffs || this.medals$buffSnapshot == null) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        for (MobEffectInstance inst : this.medals$buffSnapshot) {
            // 用 forceAddEffect 绕过复发校验与事件,确保死亡后完整保留
            self.forceAddEffect(new MobEffectInstance(inst), self);
        }
        this.medals$buffSnapshot = null;
        this.medals$captureBuffs = false;
    }
}
