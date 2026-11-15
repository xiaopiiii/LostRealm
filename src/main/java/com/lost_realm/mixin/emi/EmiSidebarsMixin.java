package com.lost_realm.mixin.emi;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.runtime.EmiSidebars;

import com.lost_realm.qol.emi.EmiFold;

/**
 * 在 EMI 取侧栏列表时把那批自定义折叠组收起来。
 *
 * <h2>为什么注入这里</h2>
 * {@code EmiSidebars.getStacks(SidebarType)} 就是列表渲染时真正遍历的那份数据：
 * <pre>
 *   INDEX → EmiStackList.stacks (编辑模式) / filteredStacks
 *   CRAFTABLES / FAVORITES / 历史 → 各自那一个 List
 * </pre>
 * 在这里<b>替换返回值</b>而不是去改 {@code EmiStackList.stacks}，原文一个字都不用动，
 * 所以搜索索引和配方查找完全不受影响 —— 搜什么都能搜到，只是浏览时同类被收起来了。
 *
 * <p>上一版注入的 {@code EmiSearch.apply} 是错的：{@code ScreenSpace.getStacks()}
 * 在非搜索面板下根本不经过 {@code EmiSearch}，所以改了也不生效。
 *
 * <h2>安全性</h2>
 * 目标是静态方法，注入点在 RETURN，handler 也是静态的 —— 不涉及 {@code this}
 * 所在局部变量槽的复用，不会被 JVM 校验器拦下。
 * {@code require = 0}：EMI 换版本、方法签名变了就安静跳过，不拦启动。
 */
@Mixin(targets = "dev.emi.emi.runtime.EmiSidebars", remap = false)
public class EmiSidebarsMixin {

    @Inject(method = "getStacks", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void lostrealm$foldCustomGroups(SidebarType type,
                                                   CallbackInfoReturnable<List<? extends EmiIngredient>> cir) {
        List<? extends EmiIngredient> original = cir.getReturnValue();
        List<? extends EmiIngredient> folded = EmiFold.foldList(original, type);
        if (folded != original) {
            cir.setReturnValue(folded);
        }
    }
}
