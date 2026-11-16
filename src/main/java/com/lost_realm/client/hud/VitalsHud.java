package com.lost_realm.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.lost_realm.Medals;
import com.lost_realm.config.MedalsConfig;

/**
 * 生命 / 饥饿 / 护甲的<b>自定义 HUD</b>：数值 / 进度条 / 原版 / 进度条+数值。
 *
 * <h2>做法</h2>
 * 两步，缺一不可：
 * <ol>
 *   <li>{@link RenderGuiOverlayEvent.Pre} 里把原版那条图标<b>取消掉</b>
 *       （{@code setCanceled(true)}），否则新画的和旧图标会叠在一起；</li>
 *   <li>{@link RenderGuiEvent.Post} 里按配置把我们自己的画上去 ——
 *       放在 Post 是因为 Pre 被取消后对应的 Post 不会再来，
 *       而且 Post 在所有原版 HUD 之后，压得住血条、经验条这些邻居。</li>
 * </ol>
 *
 * <h2>位置</h2>
 * 直接沿用原版的锚点，别自己发明坐标：
 * <ul>
 *   <li>生命：左端 {@code width/2 - 91}，基线 {@code height - 39}</li>
 *   <li>饥饿：右端 {@code width/2 + 91}（原版是从右往左排的）</li>
 *   <li>护甲：生命那一行<b>再往上 10px</b>，同一个左端</li>
 * </ul>
 * 这样换成数值或进度条之后，和旁边的经验条、快捷栏仍然对齐。
 *
 * <h2>护甲为什么不给进度条</h2>
 * 生命和饥饿的上限是固定的 20，进度条的分母是确定的；
 * 护甲点数上限取决于穿了什么，一条"总是快满"的条看不出任何信息，
 * 所以护甲只有「原版 / 数值」两种。
 *
 * <h2>什么时候完全不管</h2>
 * 创造 / 观察者（原版本来就不画这些）、以及骑乘时（原版那三条是坐骑的血），
 * 一律原样交给原版，避免把坐骑血条顶掉。
 */
@Mod.EventBusSubscriber(modid = Medals.MODID, value = Dist.CLIENT)
public final class VitalsHud {

    private static final int BAR_W = 80;
    private static final int BAR_H = 6;

    private static final int COLOR_HEALTH = 0xFFE03A3A;
    private static final int COLOR_HUNGER = 0xFFD98B3A;
    private static final int COLOR_ARMOR = 0xFF9EA9B8;

    private static final int BG = 0x90000000;
    private static final int BG_BORDER = 0xB0000000;
    private static final int BG_TRACK = 0x70202020;

    private VitalsHud() {}

    // ================================================================
    // 1. 关掉原版
    // ================================================================

    @SubscribeEvent
    public static void hideVanilla(RenderGuiOverlayEvent.Pre event) {
        try {
            if (skipAll()) {
                return;
            }
            ResourceLocation id = event.getOverlay().id();
            if (id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id())
                    && MedalsConfig.HUD_HEALTH_MODE.get() != 0) {
                event.setCanceled(true);
            } else if (id.equals(VanillaGuiOverlay.FOOD_LEVEL.id())
                    && MedalsConfig.HUD_HUNGER_MODE.get() != 0) {
                event.setCanceled(true);
            } else if (id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id())
                    && MedalsConfig.HUD_ARMOR_MODE.get() != 0) {
                event.setCanceled(true);
            }
        } catch (Throwable ignored) {
            // 配置没就绪就当没这回事，原版照画
        }
    }

    // ================================================================
    // 2. 画自己的
    // ================================================================

    @SubscribeEvent
    public static void draw(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui || skipAll()) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int left = g.guiWidth() / 2 - 91;
        int right = g.guiWidth() / 2 + 91;
        int rowY = g.guiHeight() - 39;
        int armorY = rowY - 10;

        try {
            int healthMode = MedalsConfig.HUD_HEALTH_MODE.get();
            int hungerMode = MedalsConfig.HUD_HUNGER_MODE.get();
            int armorMode = MedalsConfig.HUD_ARMOR_MODE.get();

            if (healthMode != 0) {
                int cur = Mth.ceil(player.getHealth());
                int max = Math.max(1, Mth.ceil(player.getMaxHealth()));
                String text = cur + "/" + max;
                render(g, healthMode, left, rowY, BAR_W, cur / (float) max, text, COLOR_HEALTH);
            }
            if (hungerMode != 0) {
                int cur = player.getFoodData().getFoodLevel();
                int max = 20;
                String text = cur + "/" + max;
                render(g, hungerMode, right - BAR_W, rowY, BAR_W, cur / (float) max, text, COLOR_HUNGER);
            }
            if (armorMode != 0) {
                int cur = player.getArmorValue();
                // 护甲只做到数值：没有分母，进度条无从谈起。
                // 没穿甲就不画 —— 原版此时也是空的，凭空多个 0 很怪
                if (cur > 0) {
                    text(g, left, armorY, String.valueOf(cur), COLOR_ARMOR, 1.0F);
                }
            }
        } catch (Throwable ignored) {
            // HUD 画挂了不能影响游戏
        }
    }

    /** mode: 1 = 数值，2 = 进度条，3 = 进度条 + 数值。 */
    private static void render(GuiGraphics g, int mode, int x, int y, int w,
                               float ratio, String label, int color) {
        if (mode == 2 || mode == 3) {
            bar(g, x, y + 2, w, BAR_H, ratio, color);
        }
        if (mode == 1) {
            text(g, x, y, label, color, 1.0F);
        } else if (mode == 3) {
            // 数字压在条上，0.5 缩放，白字带阴影
            float k = 0.5F;
            int tw = Math.round(Minecraft.getInstance().font.width(label) * k);
            int tx = x + (w - tw) / 2;
            int ty = y + 2 + (BAR_H - Math.round(8 * k)) / 2;
            text(g, tx, ty, label, 0xFFFFFFFF, k);
        }
    }

    private static void bar(GuiGraphics g, int x, int y, int w, int h, float ratio, int color) {
        float r = Mth.clamp(ratio, 0.0F, 1.0F);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, BG_BORDER);
        g.fill(x, y, x + w, y + h, BG);
        g.fill(x, y, x + w, y + h, BG_TRACK);
        int filled = Math.round(w * r);
        if (filled > 0) {
            g.fill(x, y, x + filled, y + h, color);
            // 顶部一条更亮的边，让条子有厚度感
            g.fill(x, y, x + filled, y + 1, brighten(color));
        }
    }

    private static int brighten(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 50);
        int gg = Math.min(255, ((argb >> 8) & 0xFF) + 50);
        int b = Math.min(255, (argb & 0xFF) + 50);
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }

    private static void text(GuiGraphics g, int x, int y, String s, int color, float scale) {
        var font = Minecraft.getInstance().font;
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        if (scale != 1.0F) {
            g.pose().scale(scale, scale, 1.0F);
        }
        RenderSystem.enableBlend();
        g.drawString(font, s, 0, 0, color, true);
        g.pose().popPose();
    }

    /** 创造 / 观察者 / 骑乘：原版怎么画就怎么画，我们不插手。 */
    private static boolean skipAll() {
        Player player = Minecraft.getInstance().player;
        return player == null || player.isCreative() || player.isSpectator() || player.isPassenger();
    }
}
