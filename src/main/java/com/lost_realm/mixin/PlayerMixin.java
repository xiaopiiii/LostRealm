package com.lost_realm.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.lost_realm.config.MedalsConfig;
import com.lost_realm.qol.QolConstants;

/**
 * {@link Player} 注入:承载两项「体验改进」功能。
 *
 * <ul>
 *   <li><b>D10 饱食度满仍可进食</b> —— {@link #medals$canEatWhenFull}:
 *       原版 {@code canEat} 在饥饿值满且非创造时返回 false,这里覆写为 true(仅食物)。</li>
 *   <li><b>D12 死亡掉落物集中</b> —— {@link #medals$dropAtDeathPos}:
 *       {@code drop} 生成的 {@link ItemEntity} 在返回前统一移动到死亡点。</li>
 * </ul>
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

    /** 记录本次 drop 调用是否发生在「玩家死亡掉落」帧(D12)。 */
    @Unique
    private boolean medals$deathDropFrame;

    /** 本次 attack 发起时玩家是否在疾跑（横扫之刃要用它判断原版扫没扫）。 */
    @Unique
    private boolean medals$sprintingAtAttack;

    // ==================== 横扫之刃(dv49) ====================

    @Inject(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void medals$beforeAttack(Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        this.medals$sprintingAtAttack = self.isSprinting();
    }

    /**
     * 在原版横扫结算完之后，补上它没扫到的那部分（更大半径 / 疾跑时也扫）。
     *
     * <p>注入 {@code RETURN} 而不是改判定：原版逻辑一行不动，
     * 没有这枚饰品时行为与纯原版完全一致。
     */
    @Inject(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At("RETURN"))
    private void medals$afterAttack(Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        com.lost_realm.event.BladeSweep.onAttackFinished(self, target, this.medals$sprintingAtAttack);
    }

    // ==================== D10 满腹进食 ====================

    @Inject(method = "canEat", at = @At("HEAD"), cancellable = true)
    private void medals$canEatWhenFull(boolean ignoreHunger, CallbackInfoReturnable<Boolean> cir) {
        if (MedalsConfig.QOL_EAT_WHEN_FULL.get()) {
            cir.setReturnValue(true);
        }
    }

    // ==================== D12 死亡掉落集中 ====================

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"))
    private void medals$markDeathDrop(ItemStack stack, boolean dropAround, boolean includeThrowerName,
                                      CallbackInfoReturnable<ItemEntity> cir) {
        Player self = (Player) (Object) this;
        this.medals$deathDropFrame = MedalsConfig.QOL_DEATH_DROP_CENTERED.get()
                && self instanceof ServerPlayer sp
                && sp.isDeadOrDying();
    }

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("RETURN"))
    private void medals$dropAtDeathPos(ItemStack stack, boolean dropAround, boolean includeThrowerName,
                                       CallbackInfoReturnable<ItemEntity> cir) {
        if (!this.medals$deathDropFrame) {
            return;
        }
        this.medals$deathDropFrame = false;
        ItemEntity entity = cir.getReturnValue();
        if (entity == null) {
            return;
        }
        Player self = (Player) (Object) this;
        // 统一落到死亡点附近的小范围内(默认半径 0.5 格),避免四散
        entity.setPos(
                self.getX() + (self.getRandom().nextDouble() - 0.5D) * QolConstants.DEATH_DROP_RADIUS,
                self.getY() + 0.25D,
                self.getZ() + (self.getRandom().nextDouble() - 0.5D) * QolConstants.DEATH_DROP_RADIUS);
        entity.setDeltaMovement(0.0D, 0.0D, 0.0D);
    }
}
