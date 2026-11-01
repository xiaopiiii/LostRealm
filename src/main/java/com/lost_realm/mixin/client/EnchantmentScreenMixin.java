package com.lost_realm.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.enchantment.Enchantment;

import com.lost_realm.config.MedalsConfig;

/**
 * 客户端 {@link EnchantmentScreen} 注入:<b>F20 附魔台一次预览全部附魔</b>。
 *
 * <p>原版服务端已在 {@code slotsChanged} 中把 3 个候选槽的附魔(注册表 id + 等级)
 * 写入 {@code enchantClue/levelClue} 并同步;但客户端仅在鼠标<b>悬停</b>某个槽时
 * 才显示真实附魔名,其余槽统一画随机占位名「???」。
 *
 * <p>本注入在界面渲染完成后,把 3 个槽的真实附魔名与等级一并绘制出来,
 * 从而「一次预览全部」。坐标取自
 * {@link AbstractContainerScreenAccessor}(父类字段访问器)。
 */
@Mixin(EnchantmentScreen.class)
public abstract class EnchantmentScreenMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void medals$drawAllEnchantNames(GuiGraphics gfx, int mouseX, int mouseY, float partialTick,
                                            CallbackInfo ci) {
        if (!MedalsConfig.QOL_ENCHANT_PREVIEW_ALL.get()) {
            return;
        }

        // 通过父类访问器读取界面左上角坐标
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) (Object) this;
        int leftPos = acc.medals$getLeftPos();
        int topPos = acc.medals$getTopPos();

        EnchantmentMenu menu = ((EnchantmentScreen) (Object) this).getMenu();
        Minecraft mc = Minecraft.getInstance();
        if (menu == null || mc.font == null) {
            return;
        }

        int baseY = topPos + 14;
        for (int slot = 0; slot < 3; slot++) {
            int clue = menu.enchantClue[slot];
            int lvl = menu.levelClue[slot];
            if (clue < 0 || menu.costs[slot] <= 0) {
                continue;
            }
            Enchantment ench = BuiltInRegistries.ENCHANTMENT.byId(clue);
            if (ench == null) {
                continue;
            }
            Component name = ench.getFullname(lvl);
            int w = mc.font.width(name);
            int x = leftPos + 60 - w / 2;
            int y = baseY + 19 * slot + 5;
            gfx.drawString(mc.font, name, x, y, 0x2A2A2A, false);
        }
    }
}
