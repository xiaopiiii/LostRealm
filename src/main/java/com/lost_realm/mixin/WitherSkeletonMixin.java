package com.lost_realm.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.lost_realm.config.MedalsConfig;
import com.lost_realm.qol.QolConstants;

/**
 * {@link WitherSkeleton} 注入:<b>D11 凋零骷髅头掉落率提升至 15%</b>。
 *
 * <p>验证结论:原版 {@code dropCustomDeathLoot} 内<b>硬编码</b>掉落凋零骷髅头
 * (与爬行者爆头逻辑耦合),并非由战利品表驱动,因此 {@code LootTableLoadEvent} 无效,
 * 必须在此方法内注入。概率固定为 {@link QolConstants#WITHER_SKULL_DROP_CHANCE}(不可调节)。
 */
@Mixin(WitherSkeleton.class)
public abstract class WitherSkeletonMixin {

    @Inject(method = "dropCustomDeathLoot", at = @At("HEAD"))
    private void medals$extraSkullDrop(DamageSource source, int looting, boolean recentlyHit, CallbackInfo ci) {
        if (!MedalsConfig.QOL_WITHER_SKULL.get()) {
            return;
        }
        WitherSkeleton self = (WitherSkeleton) (Object) this;
        // 只有被玩家击杀时才追加(与原版爆头一致的条件)
        if (source.getEntity() instanceof net.minecraft.world.entity.player.Player
                || source.getDirectEntity() instanceof net.minecraft.world.entity.player.Player) {
            float chance = QolConstants.WITHER_SKULL_DROP_CHANCE
                    + looting * 0.01F; // 每级抢夺 +1%
            if (self.getRandom().nextFloat() < chance) {
                self.spawnAtLocation(new ItemStack(Items.WITHER_SKELETON_SKULL));
            }
        }
    }
}
