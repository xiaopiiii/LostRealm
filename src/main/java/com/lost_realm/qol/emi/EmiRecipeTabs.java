package com.lost_realm.qol.emi;

import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import dev.emi.emi.screen.RecipeScreen;

/**
 * 配方界面顶上那排"配方类型"标签的<b>页码指示</b>（{@code 3/7}）。
 *
 * <h2>为什么需要它</h2>
 * 这个包里的配方类型多到一屏放不下，EMI 自己会把标签分页
 * （{@code tabPage} / {@code tabPageSize}），但界面上<b>没有任何提示</b>：
 * 看起来就像"我只有这几类配方"，剩下的一直翻不到。
 * JEI 那种会给出页数的做法在这里很关键。
 *
 * <h2>标签栏在哪</h2>
 * 反推 {@code getTabAt}：标签条从 {@code x + 16 + tabOff} 起，到 {@code x + backgroundWidth}，
 * 竖直方向是 {@code y - 24} 到 {@code y}（{@code x}/{@code y} 是配方面板左上角）。
 * 页码画在<b>面板正上方居中</b>（{@code x + backgroundWidth/2}）、标签条再往上一点的位置：
 * 那里是整块界面最空的地方，也最容易被一眼看到。
 * 曾经放在标签条左端那 16px 空隙里，结果紧贴翻页箭头，看着像按钮的一部分。
 *
 * <p>只读不写：所有字段都通过反射读取（{@code tabPage} 等是 EMI 的私有字段），
 * 任何一步失败就当没有这个功能，绝不影响配方界面本身。
 */
public final class EmiRecipeTabs {

    /** 画一次页码：{@code 当前页/总页数}。 */
    public static void draw(GuiGraphics g, Screen screen) {
        if (!(screen instanceof RecipeScreen rs) || !EmiFoldStore.isShowRecipePages()) {
            return;
        }
        try {
            List<?> tabs = (List<?>) field("tabs").get(rs);
            int page = field("tabPage").getInt(rs);
            int size = field("tabPageSize").getInt(rs);
            if (tabs == null || size <= 0) {
                return;
            }
            int total = (tabs.size() + size - 1) / size;
            if (total <= 1) {
                return;                      // 一页放得下就没什么好显示的
            }
            int x = field("x").getInt(rs);
            int y = field("y").getInt(rs);

            var font = Minecraft.getInstance().font;
            String text = (Math.max(0, Math.min(page, total - 1)) + 1) + "/" + total;
            // 原尺寸（1.0），不加底衬 —— 之前 0.5 + 深色块，既小又像块补丁
            int w = Math.max(1, font.width(text));
            // 位置：配方面板正上方居中，压在标签条上面那条空带上
            int panelW = field("backgroundWidth").getInt(rs);
            int bx = x + panelW / 2 - w / 2;
            int by = Math.max(0, y - 24 - 9);    // 标签条高 24，再往上 9
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 200.0F);
            g.drawString(font, text, bx, by, 0xFFFFFFFF, true);
            g.pose().popPose();
        } catch (Throwable ignored) {
            // EMI 换了字段名就安静地不画
        }
    }

    private static Field field(String name) throws NoSuchFieldException {
        Class<?> c = RecipeScreen.class;
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
