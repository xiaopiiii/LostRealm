package com.lost_realm.mixin.jei;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphics;

import mezz.jei.gui.overlay.ingredients.IngredientListRenderer;

import com.lost_realm.qol.jei.JeiVariantFold;

/**
 * 在 JEI 物品列表渲染时，给"有多个变体"的格子加彩色边框和数量角标。
 *
 * <h2>⚠️ 注入点为什么必须是 HEAD，不能是 TAIL</h2>
 * 这是踩过的坑，务必别改回去。{@code IngredientListRenderer.render} 的字节码是：
 *
 * <pre>
 *   62: aload_0                       // this
 *   63: getfield renderOverlays       // 第一批循环画完之后
 *   71: astore_0                      // ← 0 号槽被 Iterator 覆盖，this 没了
 *   99: return                        // ← TAIL 注入点，此处 aload_0 拿到的是 Iterator
 * </pre>
 *
 * javac 在方法后半段已经把 {@code this} 所在的 0 号局部变量槽复用给了
 * 第二个 for 循环的迭代器。在 TAIL 注入、并在处理函数里访问 {@code this}，
 * 生成出来的字节码就是 {@code invokespecial} 作用在 {@code Iterator} 上，
 * 类加载时抛：
 *
 * <pre>
 * java.lang.VerifyError: Bad type on operand stack
 *   ... IngredientListRenderer.render @112: invokespecial
 *   Type 'java/util/Iterator' is not assignable to 'IngredientListRenderer'
 * </pre>
 *
 * 后果不是"边框不显示"，而是 {@code IngredientGrid} 构造失败 →
 * {@code ForgeGuiPlugin.registerRuntime} 抛异常 → <b>整个 JEI 界面起不来</b>。
 *
 * <p>HEAD 注入在方法入口，局部变量只有 {@code (this, guiGraphics)}，由方法描述符
 * 保证类型，不存在任何帧计算风险。且 {@code renderBatch} 只画物品本身、
 * 不铺槽位底色，槽位还有 padding，所以先画的边框不会被后画的图标盖住。
 *
 * <h2>为什么 remap = false</h2>
 * 目标类来自 JEI（第三方模组），不在 Minecraft 的混淆映射表里，
 * 让 Mixin 去做 SRG 重映射只会把类名方法名映射坏。
 *
 * <h2>为什么 require = 0</h2>
 * 换 JEI 小版本、方法签名变了就安静跳过，而不是把游戏拦在启动画面。
 * 边框只是装饰，丢了不影响任何玩法。（但也正因为如此，上面的坑不能靠 require 兜底：
 * 注入成功但字节码坏了，游戏照样崩。）
 */
@Mixin(targets = "mezz.jei.gui.overlay.ingredients.IngredientListRenderer", remap = false)
public class IngredientListRendererMixin {

    @Inject(method = "render", at = @At("HEAD"), remap = false, require = 0)
    private void lostrealm$drawVariantFrames(GuiGraphics guiGraphics, CallbackInfo ci) {
        JeiVariantFold.captureAndDraw(guiGraphics, (IngredientListRenderer) (Object) this);
    }
}
