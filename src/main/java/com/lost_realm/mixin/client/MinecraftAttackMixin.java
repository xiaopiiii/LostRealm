package com.lost_realm.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.lost_realm.item.TankShieldType;

/**
 * 不朽神盾的「举盾精通」—— 举着盾也能正常攻击。
 *
 * <h2>原版到底哪里不让攻击</h2>
 * 找了很久，结论有点反直觉：<b>单击</b>左键时举盾并不影响攻击
 * （{@code Minecraft#startAttack} 里没有任何 {@code isUsingItem} 检查）；
 * 真正被挡住的是<b>按住左键连续攻击</b> ——
 * {@code Minecraft#continueAttack(boolean)} 开头有这么一句：
 *
 * <pre>{@code
 * if (pLeftDown && this.missTime <= 0 && !this.player.isUsingItem()) {
 *     this.startAttack();
 * }
 * }</pre>
 *
 * <p>于是举盾时你能点出一刀，但按住不放就砍不动了 —— 手感上就是"盾一举起来就打不了架"。
 * 这里把那个判断<b>按掉</b>：只要玩家举着盾且戴着不朽神盾，就当作"没在使用物品"，
 * 让攻击流程照常走。
 *
 * <h2>为什么改的是 LocalPlayer 的那个调用</h2>
 * {@code @Redirect} 要求目标写的是字节码里<b>实际出现的 owner</b>。
 * {@code this.player} 的静态类型是 {@code LocalPlayer}，
 * 所以即使 {@code isUsingItem()} 定义在 {@code LivingEntity}，
 * 字节码里标的也是 {@code LocalPlayer.isUsingItem}。写错 owner 会导致注入失败。
 *
 * <h2>安全边界</h2>
 * 只对<b>举着盾 + 戴着不朽神盾</b>的玩家放行，其余情况原样返回 ——
 * 不吃药的普通玩家、以及戴别的盾的玩家，行为与纯原版完全一致。
 */
@Mixin(Minecraft.class)
public abstract class MinecraftAttackMixin {

    @Redirect(
            method = "continueAttack(Z)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z")
    )
    private boolean medals$attackWhileBlocking(LocalPlayer player) {
        if (player.isBlocking() && TankShieldType.ETERNAL.isActive(player)) {
            return false;
        }
        return player.isUsingItem();
    }
}
