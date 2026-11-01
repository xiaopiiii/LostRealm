package com.lost_realm.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.lost_realm.config.MedalsConfig;
import com.lost_realm.util.VanillaXpCurve;

import net.minecraft.world.entity.player.Player;

/**
 * 体验改进 · 第 1 项:接管<b>Minecraft 原版经验等级</b>的「升级所需经验」。
 *
 * <h2>注入点</h2>
 * {@code Player#getXpNeededForNextLevel()} —— 返回「当前等级升到下一级所需经验」。
 *
 * <h2>为什么只注入这一个方法就够</h2>
 * 原版 {@code Player#giveExperiencePoints(int)} 内部执行:
 * <pre>
 *   this.experienceProgress += (float) amount / (float) this.getXpNeededForNextLevel();
 * </pre>
 * 也就是说<b>进度条的百分比本身就读取本方法</b>。因此替换本方法的返回值后,
 * 进度条显示、升级判定、附魔台/铁砧的经验消耗会<b>自动全部一致</b>,
 * 无需触碰任何其它逻辑 —— 这正好满足「只改升级所需值」的要求。
 *
 * <h2>回退</h2>
 * 当配置项 {@code experience.enabled = false} 时,本注入<b>直接返回</b>
 * (不设置返回值),原版公式照常执行,行为与原版完全一致(零副作用回退)。
 *
 * <h2>兼容性说明</h2>
 * 本注入使用 {@code @At("HEAD")} + {@code cancellable},优先级为默认值。
 * 若同时安装了其它「修改经验需求」的模组,可能产生覆盖冲突;
 * 此时可通过关闭本项的开关来让出控制权。
 */
@Mixin(Player.class)
public abstract class PlayerXpMixin {

    /**
     * 在 {@code getXpNeededForNextLevel()} 方法入口替换返回值。
     *
     * <p>{@code require = 1} 由 {@code medals.mixins.json} 的
     * {@code defaultRequire} 提供,注入失败会直接报错而非静默失效。
     */
    @Inject(method = "getXpNeededForNextLevel", at = @At("HEAD"), cancellable = true)
    private void medals$overrideXpCurve(CallbackInfoReturnable<Integer> cir) {
        // 开关关闭 → 不干预,走原版公式
        if (!MedalsConfig.VX_ENABLED.get()) {
            return;
        }
        Player self = (Player) (Object) this;
        cir.setReturnValue(VanillaXpCurve.xpForNextLevel(self.experienceLevel));
    }
}
