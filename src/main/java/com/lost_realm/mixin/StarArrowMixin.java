package com.lost_realm.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.BlockHitResult;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让【星辰贯穿之箭】撞到方块时<b>不停下来</b>。
 *
 * <h2>为什么必须动这里</h2>
 * 即使 {@code setNoGravity(true)} 解决了下坠，原版箭一旦撞到方块仍会走
 * {@code onHitBlock}：把速度改成回弹向量、把 {@code inGround} 置真 ——
 * 于是箭就"钉"在那里，之后每 tick 只在原地微微抖动。
 * 这正是「穿几格就停下、然后在空中慢慢往前蹭」的来源。
 *
 * <p>靠"提前把方块挖掉"是治标：箭速约 3 格/tick，而碰撞箱永远先一步碰到方块，
 * 挖得再超前也总会有漏网（树叶、楼梯这类形状不规则的方块尤其明显）。
 * 这里直接取消落地处理才是治本。
 *
 * <p>破坏方块仍然由 {@code ArrowCharmHandler} 逐 tick 前瞻完成，
 * 所以视觉上箭是"犁"出一条通道飞出去，而不是凭空穿过岩层。
 *
 * <p>只有带着星辰箭标记的箭会走这条捷径 —— 普通箭、其他饰品的箭一律不受影响。
 */
@Mixin(AbstractArrow.class)
public abstract class StarArrowMixin {

    /** 与 {@code ArrowCharmHandler.TAG_STAR} 保持一致。 */
    private static final String MEDALS$TAG_STAR = "lr_star_arrow";

    @Inject(method = "onHitBlock", at = @At("HEAD"), cancellable = true)
    private void medals$starArrowNeverLands(BlockHitResult result, CallbackInfo ci) {
        AbstractArrow self = (AbstractArrow) (Object) this;
        if (self.getPersistentData().getBoolean(MEDALS$TAG_STAR)) {
            ci.cancel();
        }
    }
}
