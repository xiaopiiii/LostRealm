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
    private static final int BAR_H = 5;
    /**
     * 条子相对生命行顶端的下移量。
     *
     * <p>生命行顶端 -39 到护甲图标底边 -40 只隔 1px，条子带 1px 外框就会顶到图标上
     * （看着就像"血条把护甲压住了"）。往下让 3px，中间留出干净的一条缝，
     * 同时不会碰到下面的经验条。
     */
    private static final int BAR_DY = 3;

    private static final int COLOR_HEALTH = 0xFFE03A3A;
    private static final int COLOR_HUNGER = 0xFFD98B3A;
    private static final int COLOR_ARMOR = 0xFF9EA9B8;
    /** 伤害吸收：原版是黄色的心，这里沿用同一个黄。 */
    private static final int COLOR_ABSORB = 0xFFFFC83A;

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
        // 原版基线：生命/饥饿这一行的顶端是 height-39
        int rowY = g.guiHeight() - 39;

        try {
            int healthMode = MedalsConfig.HUD_HEALTH_MODE.get();
            int hungerMode = MedalsConfig.HUD_HUNGER_MODE.get();
            int armorMode = MedalsConfig.HUD_ARMOR_MODE.get();

            int absorption = Mth.ceil(player.getAbsorptionAmount());

            // 护甲数值的 y：
            //   1) 生命行顶端 -12，给 8px 高的字和阴影留缝；
            //   2) 生命还是原版图标、且最大生命超过 20 时，原版会把心往上叠排，
            //      护甲得跟着让上去，否则会被上面那排心压住；
            //   3) 再叠一个手动微调，方便按实际观感对齐。
            int heartRows = Math.max(1, Mth.ceil(Mth.ceil(player.getMaxHealth()) / 20.0F));
            int vanillaStack = (MedalsConfig.HUD_HEALTH_MODE.get() == 0) ? (heartRows - 1) * 10 : 0;
            int armorY = rowY - 12 - vanillaStack + MedalsConfig.HUD_ARMOR_OFFSET.get();

            // ---- 护甲：先画，且在生命行上方留出空档 ----
            // 原版护甲图标顶端在 height-49（比生命行高 10px）。
            // 文字只有 8px 高、又带阴影，贴着 -49 会压到生命那行的字；
            // 抬太高（试过 -16）又飘在半空。取 -12：比原版高 2px，正好留出一道缝。
            if (armorMode != 0) {
                int armor = player.getArmorValue();
                // 没穿甲就不画 —— 原版此时也是空的，凭空多个 0 很怪
                if (armor > 0) {
                    text(g, left, armorY, String.valueOf(armor), COLOR_ARMOR, 1.0F);
                }
            }

            // ---- 生命 ----
            if (healthMode != 0) {
                int cur = Mth.ceil(player.getHealth());
                int max = Math.max(1, Mth.ceil(player.getMaxHealth()));
                healthRow(g, healthMode, left, rowY, cur, max, absorption);
            }

            // ---- 饥饿：顺带把饱和度也画出来 ----
            // 原版从不显示饱和度，但它决定了"这一口吃下去能不能回血"，
            // 所以条上用一层金色覆盖表示饱和度占了前多少格，数字后面跟 +饱和度。
            if (hungerMode != 0) {
                int cur = player.getFoodData().getFoodLevel();
                float sat = player.getFoodData().getSaturationLevel();
                int satInt = Mth.ceil(sat);
                vitalsRow(g, hungerMode, right - BAR_W, rowY, BAR_W,
                        cur / 20.0F, cur + "/20", COLOR_HUNGER,
                        satInt > 0 ? "+" + satInt : null, null,
                        Mth.clamp(sat / 20.0F, 0.0F, 1.0F));
            }
        } catch (Throwable ignored) {
            // HUD 画挂了不能影响游戏
        }
    }

    /**
     * 生命这一行：除了红色部分，还要把<b>伤害吸收</b>画出来。
     *
     * <p>我们一旦关掉原版的 {@code PLAYER_HEALTH} 覆盖层，原版的黄色吸收心也一起没了，
     * 玩家会以为"吸收盾凭空消失"。所以：
     * <ul>
     *   <li>进度条：吸收部分接在红色后面，用金色画；</li>
     *   <li>数值：在 {@code 18/20} 后面追加金色的 {@code +4}。</li>
     * </ul>
     */
    private static void healthRow(GuiGraphics g, int mode, int x, int y,
                                  int cur, int max, int absorption) {
        float ratio = cur / (float) max;
        float absorbRatio = absorption / (float) max;
        vitalsRow(g, mode, x, y, BAR_W, ratio, cur + "/" + max, COLOR_HEALTH,
                absorption > 0 ? "+" + absorption : null, absorbRatio, 0.0F);
    }

    /** mode: 1 = 数值，2 = 进度条，3 = 进度条 + 数值。 */
    private static void vitalsRow(GuiGraphics g, int mode, int x, int y, int w,
                                  float ratio, String label, int color,
                                  String extra, Float extraRatio, float overlayRatio) {
        var font = Minecraft.getInstance().font;

        if (mode == 2 || mode == 3) {
            bar(g, x, y + BAR_DY, w, BAR_H, ratio, color);
            if (overlayRatio > 0.0F) {
                // 饱和度：在已填充的部分上盖一层半透明金色
                int ow = Math.round(w * Math.min(Mth.clamp(ratio, 0.0F, 1.0F), overlayRatio));
                if (ow > 0) {
                    g.fill(x, y + BAR_DY, x + ow, y + BAR_DY + BAR_H, 0x66FFD24A);
                }
            }
            if (extraRatio != null && extraRatio > 0.0F) {
                int base = Math.round(w * Mth.clamp(ratio, 0.0F, 1.0F));
                int add = Math.min(w - base, Math.round(w * extraRatio));
                if (add > 0) {
                    g.fill(x + base, y + BAR_DY, x + base + add, y + BAR_DY + BAR_H, COLOR_ABSORB);
                    g.fill(x + base, y + BAR_DY, x + base + add, y + BAR_DY + 1, brighten(COLOR_ABSORB));
                }
            }
        }

        if (mode == 1) {
            int w1 = font.width(label);
            text(g, x, y, label, color, 1.0F);
            if (extra != null) {
                text(g, x + w1 + 3, y, extra, COLOR_ABSORB, 1.0F);
            }
        } else if (mode == 3) {
            // 数字压在条上：0.5 缩放，白字带阴影
            float k = 0.5F;
            int wLabel = Math.round(font.width(label) * k);
            int wExtra = extra == null ? 0 : Math.round(font.width(extra) * k) + 2;
            int total = wLabel + wExtra;
            int tx = x + (w - total) / 2;
            int ty = y + BAR_DY + (BAR_H - Math.round(8 * k)) / 2;
            text(g, tx, ty, label, 0xFFFFFFFF, k);
            if (extra != null) {
                text(g, tx + wLabel + 2, ty, extra, COLOR_ABSORB, k);
            }
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
