package com.lost_realm.mixin.emi;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;

import net.minecraftforge.fml.ModList;

import com.lost_realm.Medals;
import com.lost_realm.qol.emi.EmiFold;

/**
 * 在 EMI 之前把"框选"这个手势抢下来。
 *
 * <h2>为什么必须在 MouseHandler 这一层</h2>
 * EMI 的 {@code dev.emi.emi.mixin.MouseMixin} 直接注入 {@code MouseHandler.onPress}，
 * 只要鼠标压在它的面板上就会：
 * <pre>
 *   if (EmiScreenManager.mouseClicked(mx, my, button)) {
 *       ci.cancel();          // 整个 onPress 被取消
 *   }
 * </pre>
 * 一旦 cancel，{@code Screen.mouseClicked} 不会被调用，
 * Forge 的 {@code ScreenEvent.MouseButtonPressed} 自然也不会触发 ——
 * 所以之前挂在那个事件上的处理器<b>一次都没执行过</b>，表现就是"按了没反应"。
 *
 * <p>这里把 priority 设成 500（Mixin 默认 1000，数值小的先执行），
 * 就能赶在 EMI 之前判断：只要是框选手势，直接 cancel，EMI 完全不知道有这次点击。
 *
 * <h2>为什么要 cancel 而不是放行</h2>
 * 不 cancel 的话 EMI 会把这串动作当成"拖一个物品"，松手时东西就被丢到世界里了。
 *
 * <h2>安全性</h2>
 * 只在 {@code action == PRESS} 且 {@code button == 0} 且修饰键按下且鼠标在 EMI 面板上
 * 才会 cancel；其余情况一律放行。整个函数体包在 try/catch 里，
 * 任何异常都只是"这次不拦"，不会把游戏卡住。
 */
@Mixin(value = MouseHandler.class, priority = 500)
public class MouseHandlerMixin {

    /** 这次按下是否被我拦下了 —— 只有拦过才需要拦对应的松开。 */
    @Unique
    private boolean lostrealm$swallowed;

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void lostrealm$emiFoldSelect(long window, int button, int action, int mods,
                                         CallbackInfo ci) {
        if (button != 0) {
            return;
        }
        try {
            if (action == GLFW.GLFW_PRESS) {
                if (!ModList.get().isLoaded("emi")) {
                    return;
                }
                // 诊断：把四个修饰键的真实状态记下来，好确认"按了没反应"卡在哪一步
                if (Minecraft.getInstance().screen != null) {
                    EmiFold.logModifierState();
                }
                if (EmiFold.beginSelection()) {
                    lostrealm$swallowed = true;
                    ci.cancel();
                }
            } else if (action == GLFW.GLFW_RELEASE && lostrealm$swallowed) {
                lostrealm$swallowed = false;
                EmiFold.endSelection();
                ci.cancel();
            }
        } catch (Throwable t) {
            lostrealm$swallowed = false;
            Medals.LOGGER.warn("[Lost Realm] EMI 折叠拦截鼠标失败（本次放行）", t);
        }
    }
}
