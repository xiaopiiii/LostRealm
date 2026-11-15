package com.lost_realm.mixin.emi;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.config.SidebarType;

import com.lost_realm.qol.emi.EmiFold;

/**
 * 把折叠结果塞进 EMI 的"搜索面板"数据源。
 *
 * <h2>为什么还需要这一处</h2>
 * {@code ScreenSpace.getStacks()} 有两条路：
 * <pre>
 *   if (search &amp;&amp; type != CHESS) → EmiScreenManager.searchedStacks
 *   else                          → EmiSidebars.getStacks(type)
 * </pre>
 * 玩家平时看的那个物品面板走的是<b>第一条</b>。而 {@code searchedStacks} 是在
 * {@code recalculate()} 里被直接赋成 {@code EmiSearch.stacks} 的<b>同一个引用</b>，
 * 完全绕过了 {@link EmiSidebarsMixin} 里对 {@code EmiSidebars.getStacks} 的注入。
 *
 * <p>之前的表现就是：组建好了、文件也写了，但列表纹丝不动 ——
 * 折叠代码压根没被那个面板调用到。
 *
 * <h2>为什么改 recalculate 而不是 EmiSearch.stacks</h2>
 * {@code EmiSearch.stacks} 同时是搜索索引的底子，动它会让"搜什么给什么"失效。
 * 这里只是把 {@code searchedStacks} 这个<b>展示用的引用</b>换成折叠后的新列表，
 * 原列表一个字不动。
 */
@Mixin(targets = "dev.emi.emi.screen.EmiScreenManager", remap = false, priority = 500)
public class EmiScreenManagerMixin {

    @Shadow
    private static List<? extends EmiIngredient> searchedStacks;

    @Inject(method = "recalculate", at = @At("RETURN"), remap = false, require = 0)
    private static void lostrealm$foldSearchedStacks(CallbackInfo ci) {
        try {
            List<? extends EmiIngredient> current = searchedStacks;
            if (current == null || current.size() < 2) {
                return;
            }
            List<? extends EmiIngredient> folded = EmiFold.foldList(current, SidebarType.INDEX);
            if (folded != current) {
                searchedStacks = folded;
            }
        } catch (Throwable ignored) {
            // 折叠失败就让 EMI 用原列表，不影响它本身
        }
    }
}
