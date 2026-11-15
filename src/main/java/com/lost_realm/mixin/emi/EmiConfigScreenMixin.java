package com.lost_realm.mixin.emi;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.emi.emi.screen.ConfigScreen;
import dev.emi.emi.screen.widget.config.ListWidget;

import com.lost_realm.qol.emi.EmiFoldConfig;

/**
 * 在 EMI 配置界面建好之后，往「开发」分组里加一行「折叠编辑（遗落境）」。
 *
 * <h2>为什么挑 {@code addJumpButtons}</h2>
 * 想加进列表，就得等 {@code init()} 把 {@code list} 建完、把各分组和子项都填好。
 * 但 {@code init()} 是被 MC 改名的方法（官方名 {@code init} / 运行时 {@code m_7856_}），
 * 在不同映射下名字不一致，写死在 mixin 里很容易在某一侧匹配不上。
 * {@code addJumpButtons} 是 EMI 自己的私有方法，名字永远不变，而且正好在
 * {@code init()} 末尾调用 —— 在它返回时插进去，列表已经完整、尺寸也已经排好。
 *
 * <p>{@code require = 0}：EMI 哪天改了这个方法就安静跳过，
 * {@link EmiFoldConfig#install} 没成功时外面会自动退回左下角那个手画开关。
 */
@Mixin(targets = "dev.emi.emi.screen.ConfigScreen", remap = false, priority = 500)
public class EmiConfigScreenMixin {

    @Shadow
    public ListWidget list;

    @Inject(method = "addJumpButtons", at = @At("RETURN"), remap = false, require = 0)
    private void lostrealm$addFoldEditEntry(CallbackInfo ci) {
        try {
            EmiFoldConfig.install((ConfigScreen) (Object) this, list);
        } catch (Throwable ignored) {
            // 加不进去就退回左下角那个手画开关，不影响 EMI 本身
        }
    }
}
