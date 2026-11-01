package com.lost_realm.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;

import com.lost_realm.client.effect.EffectHudOverrides;
import com.lost_realm.client.effect.EffectHudRenderer;

/**
 * 接管原版状态效果 HUD。
 *
 * <h2>为什么必须「取消后重画」</h2>
 * 原版 {@code Gui#renderEffects} 以绝对值计算坐标,且不修改 PoseStack。
 * 因此除非整段替换,否则无法实现「缩放」与「自定义停靠位置」——
 * 单独 push 一个缩放矩阵只会让图标变小,但绘制坐标不变,导致错位。
 *
 * <h2>跳过条件(与原版保持一致)</h2>
 * 原版在渲染前会检查:当前屏幕是否为 {@link EffectRenderingInventoryScreen}
 * 且它声明 {@code canSeeEffects()} —— 也就是「打开背包时右侧自带效果栏」的情况。
 * 此时原版<b>不</b>绘制右上角 HUD(效果已显示在背包里)。
 * 本注入必须复刻这一判断,否则会出现「背包里效果栏 + 右上角 HUD」重复显示。
 *
 * <h2>⚠️ 为什么这里绝对不能使用 {@code @Shadow} 取实例字段</h2>
 * 本类曾经用 {@code @Shadow} 去取 {@code Gui} 的 {@code minecraft} /
 * {@code screenWidth} / {@code screenHeight} 三个字段,结果<b>正式环境直接崩溃</b>:
 *
 * <pre>
 * InvalidMixinException: &#64;Shadow field minecraft was not located in
 * the target class net.minecraft.client.gui.Gui
 * </pre>
 *
 * 原因是这三个字段虽然确实存在于 {@code Gui} 的 SRG 映射里
 * ({@code minecraft -> t} / {@code screenWidth -> S} / {@code screenHeight -> T}),
 * 但注解处理器在 {@code reobfJar} 阶段<b>没有为它们生成 refmap 条目</b> ——
 * 产出的 {@code lost_realm.refmap.json} 里只有 {@code renderEffects} 一条映射,
 * 字段那三条完全缺失,于是运行时 Mixin 拿着字面量名字找不到目标而 APPLY 失败。
 *
 * 因此这里改为<b>全部走公开 API</b>,彻底不依赖任何字段映射:
 * <ul>
 *   <li>{@code minecraft} → {@link Minecraft#getInstance()}</li>
 *   <li>{@code screenWidth} → {@code getWindow().getGuiScaledWidth()}</li>
 *   <li>{@code screenHeight} → {@code getWindow().getGuiScaledHeight()}</li>
 * </ul>
 * 这同时消除了「继承字段在子类视角下 owner 解析失败」的隐患。
 */
@Mixin(Gui.class)
public abstract class EffectHudMixin {

    /**
     * 在 {@code renderEffects} 入口取消原版绘制,改由
     * {@link EffectHudRenderer} 接管。
     *
     * <p>注意:本方法内<b>不使用任何 {@code @Shadow} 字段</b>,实例与尺寸全部即时取值,
     * 保证 refmap 只需要 {@code renderEffects} 这一条映射(见上方说明)。
     */
    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void medals$replaceEffectHud(GuiGraphics gfx, CallbackInfo ci) {
        if (!EffectHudOverrides.enabled()) {
            return; // 关闭时完全回退原版
        }

        Minecraft mc = Minecraft.getInstance();

        // ---- 复刻原版的背包界面跳过逻辑 ----
        Screen screen = mc.screen;
        if (screen instanceof EffectRenderingInventoryScreen<?> invScreen && invScreen.canSeeEffects()) {
            // 此时原版也不会绘制右上角 HUD,直接取消即可
            ci.cancel();
            return;
        }

        // 无效果时不绘制(与原版一致:空集合直接返回)
        if (mc.player == null || mc.player.getActiveEffects().isEmpty()) {
            ci.cancel();
            return;
        }

        ci.cancel();
        EffectHudRenderer.render(gfx,
                mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight());
    }
}
