package com.lost_realm.mixin.jei;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphics;

import mezz.jei.gui.overlay.ingredients.IngredientListRenderer;

import com.lost_realm.qol.jei.JeiVariantFold;

/**
 * 在 JEI 物品列表渲染完之后，给"有多个变体"的格子加彩色边框和数量角标。
 *
 * <h2>为什么注入在 TAIL</h2>
 * 要画在物品图标<b>之上</b>，否则边框会被图标盖掉一半。
 * HEAD 注入只能拿到"画之前"的时机，画上去会被后面覆盖。
 *
 * <h2>为什么 remap = false</h2>
 * 目标类来自 JEI（第三方模组），不在 Minecraft 的混淆映射表里，
 * 让 Mixin 去做 SRG 重映射只会把类名和方法名映射坏。
 *
 * <h2>为什么 require = 0</h2>
 * JEI 是可选依赖。虽然这个配置本身是 {@code required: false}（没装 JEI 就不会加载），
 * 但万一你换了别的 JEI 小版本、方法签名变了，{@code require = 0} 能让它安静跳过，
 * 而不是把游戏拦在启动画面。边框只是装饰，丢失不影响任何玩法。
 */
@Mixin(targets = "mezz.jei.gui.overlay.ingredients.IngredientListRenderer", remap = false)
public class IngredientListRendererMixin {

    @Inject(method = "render", at = @At("TAIL"), remap = false, require = 0)
    private void lostrealm$drawVariantFrames(GuiGraphics guiGraphics, CallbackInfo ci) {
        JeiVariantFold.renderFrames(guiGraphics, (IngredientListRenderer) (Object) this);
    }
}
