package com.lost_realm.mixin.client;

import net.minecraft.client.player.LocalPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.lost_realm.item.TankShieldType;

/**
 * 不朽神盾的「举盾精通」—— 举着盾不再被砍成龟速。
 *
 * <h2>原版到底在哪减速（找错方向的代价）</h2>
 * 这个减速<b>不在服务端的实体逻辑里</b>，所以翻 {@code Player} / {@code LivingEntity}
 * 的 {@code travel} / {@code aiStep} 永远找不到。它在<b>客户端的输入层</b>：
 *
 * <pre>{@code
 * // LocalPlayer#aiStep
 * if (this.isUsingItem() && !this.isPassenger()) {
 *     this.input.leftImpulse    *= 0.2F;
 *     this.input.forwardImpulse *= 0.2F;
 * }
 * }</pre>
 *
 * <p>也就是说举盾时你的移动输入只剩两成 —— 手感和"走了五分之一"完全吻合。
 *
 * <h2>为什么不能用属性弥补</h2>
 * 这一步砍的是<b>输入</b>，不是速度属性。哪怕把 {@code MOVEMENT_SPEED}
 * 拉高十倍，客户端也只输入两成的移动量，结果还是两成。
 * <b>唯一的办法就是让这个判断在这段逻辑里失效。</b>
 *
 * <h2>为什么必须写 ordinal = 0</h2>
 * {@code aiStep} 里 {@code isUsingItem()} 被调用了<b>两次</b>：
 * 第一处（序号 0）是上面这段减速；第二处是"使用物品时的视角/瞄准"逻辑。
 * 不写 ordinal 的话 Mixin 会因为匹配到两处而注入失败，
 * 写错序号则会去动视角那段 —— 这里只要第一处。
 *
 * <h2>只放行「举盾」</h2>
 * 判据里带上了 {@code isBlocking()} —— 所以戴着不朽神盾<b>吃东西、喝药水</b>
 * 时依旧正常减速（那本来就该慢），只有真正举盾才解除。
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerSlowdownMixin {

    @Redirect(
            method = "aiStep",
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z")
    )
    private boolean medals$noBlockingSlowdown(LocalPlayer player) {
        if (player.isBlocking() && TankShieldType.ETERNAL.isActive(player)) {
            return false;
        }
        return player.isUsingItem();
    }
}
