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
 * 竖直方向是 {@code y - 24} 到 {@code y}（{@code x}/{@code y} 是配方面板左上角，
 * 面板基本贴着屏幕顶端，所以标签条就在最上面那一条）。
 * {@code x .. x + 16} 这 16px 是标签条左边的空隙 —— 页码就画在这里，
 * 不压任何标签。
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
            int w = Math.max(1, Math.round(font.width(text) * 0.5F));
            int bx = x + 15 - w;             // 右对齐到标签条左空隙的右缘
            int by = y - 11;                 // 竖直居中在那条 24px 高的标签带上
            g.fill(bx - 1, by - 1, bx + w + 1, by + 6, 0xC0101010);
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 200.0F);
            g.pose().translate(bx, by, 0.0F);
            g.pose().scale(0.5F, 0.5F, 1.0F);
            g.drawString(font, text, 0, 0, 0xFFFFFFFF, false);
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
