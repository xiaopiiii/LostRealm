package com.lost_realm.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.lost_realm.Medals;
import com.lost_realm.config.MedalsConfig;

/**
 * 生命 / 饥饿 / 护甲的<b>自定义 HUD</b>：原版图标 / 数值 / 进度条 / 进度条+数值。
 *
 * <h2>为什么"先画再取消"，而不是"先取消再画"</h2>
 * 最早的写法是：在 {@code RenderGuiOverlayEvent.Pre} 里取消原版，再统一到
 * {@code RenderGuiEvent.Post} 里画自己的。问题是一旦自己那段抛异常（配置没读到、
 * 坐标算错…），原版已经被取消、新的又没画出来 —— 玩家看到的一条 HUD 都不剩。
 * 现在改成：<b>当场把这一条画出来，画成功了才取消</b>。
 * 画挂了就什么都不取消，原版照旧显示 —— 最差也只是"没自定义成功"，不会"什么都没有"。
 *
 * <h2>位置</h2>
 * 全部沿用原版锚点：生命左端 {@code 宽/2-91}、饥饿右端 {@code 宽/2+91}、
 * 基线 {@code 高-39}，护甲在生命行上方。换画法之后和快捷栏、经验条仍然对齐。
 *
 * <h2>护甲的两个特殊规则</h2>
 * <ul>
 *   <li>一条进度条只吃 <b>20 点</b>护甲；超过 20 时<b>整条</b>换成另一种颜色
 *       （不是画两段，是整条换色），一眼能看出"已经溢出了"；</li>
 *   <li>护甲上限随装备变化，所以数值才是主显示，进度条只是个大概。</li>
 * </ul>
 *
 * <h2>配色</h2>
 * 三种元素各一个明确的颜色（红 / 橙 / 蓝），条子上不出现白色：
 * 条内数字用深色，边框用深色，顶部的"高光"也去掉了 —— 之前那个
 * {@code brighten()} 会把浅色拉到接近纯白，看着像一条白杠。
 */
@Mod.EventBusSubscriber(modid = Medals.MODID, value = Dist.CLIENT)
public final class VitalsHud {

    /** 没有贴图时的兜底尺寸（贴图存在就以贴图为准，永远 1:1，不缩放）。 */
    private static final int DEF_BAR_W = 70;
    private static final int DEF_BAR_H = 7;
    private static final int DEF_PART_W = 30;

    /**
     * 条子相对生命行顶端的下移量。
     *
     * <p>生命行顶端 -39 到护甲图标底边 -40 只隔 1px，条子带 1px 外框就会顶到图标上
     * （看着就像"血条把护甲压住了"）。往下让 3px，中间留出干净的一条缝，
     * 同时不会碰到下面的经验条。
     */
    private static final int BAR_DY = 0;

    private static final int COLOR_HEALTH = 0xFFE03A3A;
    private static final int COLOR_HUNGER = 0xFFD98B3A;
    private static final int COLOR_ARMOR = 0xFF4E8FE0;
    /** 饱和度：金黄，和饥饿的橙色区分开。 */
    private static final int COLOR_SATURATION = 0xFFFFC83A;
    /** 护甲韧性：跟护甲区分开，用青绿。 */
    private static final int COLOR_TOUGHNESS = 0xFF3FBFB0;
    /** 伤害吸收 / 饱和度：原版黄。 */
    private static final int COLOR_EXTRA = 0xFFFFC83A;

    private static final int BG_BORDER = 0xB0000000;
    private static final int BG_TRACK = 0x70202020;

    /** 所有数字统一亮白。 */
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    /** 经验数字：原版经验条那抹亮绿。 */
    private static final int TEXT_XP = 0xFF80FF20;

    /** 这一帧的原版覆盖层是不是已经被我们替换过了（见 {@link #onRenderPost}）。 */
    private static boolean preDrewThisFrame;

    /** F3+T 重载资源时把贴图尺寸缓存清掉，免得换了图还是按旧尺寸裁。 */
    @SubscribeEvent
    public static void onReload(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((barrier, manager, prepProfiler, reloadProfiler, bg, game) -> {
            game.execute(HudTextures::clearCache);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        });
    }

    /** 图标宽度 = 贴图宽度（不缩放）。 */
    private static int iw(String key, int def) {
        HudTextures.Img img = HudTextures.get(key + "_icon");
        return img == null ? 0 : img.w();
    }

    private VitalsHud() {}

    @SubscribeEvent
    public static void onOverlayPre(RenderGuiOverlayEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui || skipAll(player)) {
            return;
        }
        ResourceLocation id = event.getOverlay().id();
        diag("pre:" + id.getPath(), player);
        GuiGraphics g = event.getGuiGraphics();
        try {
            boolean drawn = false;
            if (id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id())) {
                int mode = MedalsConfig.HUD_HEALTH_MODE.get();
                if (mode != 0) {
                    drawHealth(g, player, mode);
                    drawn = true;
                }
            } else if (id.equals(VanillaGuiOverlay.FOOD_LEVEL.id())) {
                int mode = MedalsConfig.HUD_HUNGER_MODE.get();
                if (mode != 0) {
                    drawHunger(g, player, mode);
                    drawn = true;
                }
            } else if (id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id())) {
                int mode = MedalsConfig.HUD_ARMOR_MODE.get();
                if (mode != 0) {
                    drawn = drawArmor(g, player, mode);
                }
            }
            if (drawn) {
                preDrewThisFrame = true;
                event.setCanceled(true);
            }
        } catch (Throwable t) {
            // 画不出来就不取消 —— 让原版接着画，绝不能出现"两条都没了"
            Medals.LOGGER.debug("[Lost Realm] 自定义 HUD 绘制失败，本次交回原版", t);
        }
    }

    /**
     * 兜底渲染。
     *
     * <p>创造模式下原版<b>根本不渲染</b>生存 HUD —— 那几个覆盖层不进渲染流程，
     * 也就不会有 {@link RenderGuiOverlayEvent.Pre}，光靠上面那条路会一片空白。
     * 所以这里再挂一个总 HUD 的 Post：只有当这一帧 Pre 一条都没画过时才补画。
     */
    @SubscribeEvent
    public static void onRenderPost(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
        boolean preDrew = preDrewThisFrame;
        preDrewThisFrame = false;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui || skipAll(player)) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        // 原版覆盖层没被替换过（创造模式 / 某些版本不触发 Pre）时补画
        if (!preDrew) {
            diag("post-fallback", player);
            drawFallback(g, player);
        }
        // 经验数字跟上面三条互不影响，每帧都画
        try {
            drawXp(g, player);
        } catch (Throwable t) {
            Medals.LOGGER.debug("[Lost Realm] 经验数字绘制失败", t);
        }
    }

    /**
     * 经验数字：{@code 总经验 / 下一级所需}，比如 {@code 5/7}。
     *
     * <p>画在经验条<b>右边</b>：那一条横带上除了经验条本身没有别的东西，
     * 不会和等级数字、快捷栏、上面的血条抢位置。
     */
    private static void drawXp(GuiGraphics g, Player player) {
        if (!MedalsConfig.HUD_SHOW_XP.get()) {
            return;
        }
        var font = Minecraft.getInstance().font;
        final float k = 0.5F;                 // 半号字，正好塞进 5px 高的条里
        int x0 = g.guiWidth() / 2 - 91;
        int x1 = g.guiWidth() / 2 + 91;
        int y = g.guiHeight() - 29;           // 上移 1px

        // 左：累计总经验
        text(g, x0 + 2, y, String.valueOf(player.totalExperience), TEXT_WHITE, k);

        // 右：本等级已获得 / 升到下一级所需，右对齐
        int need = Math.max(1, player.getXpNeededForNextLevel());
        int cur = Math.round(player.experienceProgress * need);
        String right = cur + "/" + need;
        text(g, x1 - 2 - Math.round(font.width(right) * k), y, right, TEXT_WHITE, k);
    }

    /** 兜底那部分（创造模式不在这里，`skipAll` 已经挡掉了）。 */
    private static void drawFallback(GuiGraphics g, Player player) {
        try {
            int health = MedalsConfig.HUD_HEALTH_MODE.get();
            if (health != 0) {
                drawHealth(g, player, health);
            }
            int hunger = MedalsConfig.HUD_HUNGER_MODE.get();
            if (hunger != 0) {
                drawHunger(g, player, hunger);
            }
            int armor = MedalsConfig.HUD_ARMOR_MODE.get();
            if (armor != 0) {
                drawArmor(g, player, armor);
            }
        } catch (Throwable t) {
            Medals.LOGGER.debug("[Lost Realm] 自定义 HUD 兜底渲染失败", t);
        }
    }

    // ================================================================
    // 三条 HUD
    // ================================================================

    private static void drawHealth(GuiGraphics g, Player player, int mode) {
        int left = g.guiWidth() / 2 - 91;
        int barW = DEF_BAR_W;
        int barH = DEF_BAR_H;
        // 生命/饥饿同一行；底边压在经验条上沿（-32）之上
        int rowY = g.guiHeight() - 33 - barH;
        double cur = player.getHealth();
        int max = Math.max(1, Mth.ceil(player.getMaxHealth()));
        int absorb = Mth.ceil(player.getAbsorptionAmount());

        float ratio = (float) (cur / (float) max);
        Float extra = absorb > 0 ? (float) absorb : null;   // 吸收段占整条的比例
        String label = fmt(cur) + "/" + max;
        String suffix = absorb > 0 ? "+" + absorb : null;
        vitalsRow(g, mode, "health", left, rowY, barW, barH, ratio, label,
                COLOR_HEALTH, suffix, extra, 0.0F, false, 1);
    }

    /**
     * 饥饿那一行：<b>左半饥饿、右半饱和度</b>，两个条都是从右往左扣。
     *
     * <p>以前饱和度只是叠在饥饿条上的一层半透明金色，看不出具体数值；
     * 现在给它单独一条，和自己的图标（原版"饥饿"效果图）。
     */
    /**
     * 饥饿那一行：<b>左边饱和度、右边饥饿</b>，两个条都从右往左扣。
     *
     * <p>两个槽宽度不一样：饱和度图标是 12×12，图标多大就占多大，
     * 条宽保持 30px 不变（贴图 60×14），所以它的槽是 12+1+30=43。
     */
    /**
     * 饥饿那一行：<b>左边饱和度、右边饥饿</b>，两个条都从右往左扣。
     *
     * <p>每段的宽度 = 条的贴图宽 + 图标贴图宽 + 间隔，全部按像素算，
     * 所以换 1 倍图就整体变小、换 2 倍图就整体变大，永远 1:1 不缩放。
     */
    private static void drawHunger(GuiGraphics g, Player player, int mode) {
        int right = g.guiWidth() / 2 + 91;
        int barH = DEF_BAR_H;
        int rowY = g.guiHeight() - 33 - barH;
        int cur = player.getFoodData().getFoodLevel();
        float sat = player.getFoodData().getSaturationLevel();

        int hungerW = DEF_PART_W;
        int satW = DEF_PART_W;
        int hungerSlot = hungerW + iw("hunger", 9) + 1;
        int satSlot = satW + iw("saturation", 10) + 1;

        vitalsRow(g, mode, "saturation", right - hungerSlot - satSlot, rowY, satSlot, barH,
                Math.min(sat, 20.0F) / 20.0F, fmt(sat), COLOR_SATURATION,
                null, null, 0.0F, true, 1);
        vitalsRow(g, mode, "hunger", right - hungerSlot, rowY, hungerSlot, barH,
                cur / 20.0F, cur + "/20", COLOR_HUNGER,
                null, null, 0.0F, true, 1);
    }

    /**
     * 护甲那一行：<b>左半护甲、右半韧性</b>。
     *
     * <p>宽度是固定的（整行仍占 80px，和生命/饥饿对齐），所以是"各占一半"
     * 而不是谁多谁少：图标 9 + 间隔 1 + 条 25，两段各 35px。
     * 韧性跟护甲共用同一个显示模式（数值 / 条 / 条+数值）。
     */
    /**
     * 护甲那一行：<b>左半护甲、右半韧性</b>。
     *
     * <p>宽度是固定的（整行仍占 80px，和生命/饥饿对齐），所以是"各占一半"。
     * 韧性跟护甲共用同一个显示模式（数值 / 条 / 条+数值），
     * 但<b>满条按 12 点算</b>—— 原版韧性能到的上限就那么高，按 20 算永远差一截。
     */
    private static boolean drawArmor(GuiGraphics g, Player player, int mode) {
        int left = g.guiWidth() / 2 - 91;
        int barH = DEF_BAR_H;
        int rowY = g.guiHeight() - 33 - barH;
        double armor = player.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        double toughness = player.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);
        if (armor <= 0 && toughness <= 0) {
            return false;
        }

        int armorY = rowY - barH - 2 + MedalsConfig.HUD_ARMOR_OFFSET.get();
        logArmorOnce(mode, (int) armor, armorY);

        int armorW = DEF_PART_W;
        int toughW = DEF_PART_W + 1;
        int armorSlot = armorW + iw("armor", 9) + 1;
        int toughSlot = toughW + iw("armor_toughness", 9);

        vitalsRow(g, mode, "armor", left, armorY, armorSlot, barH,
                (float) Math.min(armor, 20) / 20.0F, fmt(armor), COLOR_ARMOR,
                null, null, 0.0F, false, 1);
        vitalsRow(g, mode, "armor_toughness", left + armorSlot, armorY, toughSlot, barH,
                (float) Math.min(toughness, 12) / 12.0F, fmt(toughness), COLOR_TOUGHNESS,
                null, null, 0.0F, false, 0);
        return true;
    }

    /**
     * 数值格式：<b>保留两位小数，但小数位为 0 时不显示</b>。
     *
     * <p>例：{@code 18.5 → 18.50}、{@code 18.0 → 18}、{@code 7.25 → 7.25}。
     */
    private static String fmt(double v) {
        double r = Math.round(v * 100.0) / 100.0;
        if (Math.abs(r - Math.round(r)) < 1e-6) {
            return String.valueOf((long) Math.round(r));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", r);
    }

    private static long lastDiag;

    /** 总诊断：哪些覆盖层到底有没有走到、配置值是多少、护甲点数是多少。 */
    private static void diag(String tag, Player p) {
        long now = System.currentTimeMillis();
        if (now - lastDiag < 5_000L) {
            return;
        }
        lastDiag = now;
        try {
            Medals.LOGGER.info("[HUD] {} 生命={} 饥饿={} 护甲={} 护甲点数={} 创造={} 最大生命={}",
                    tag, MedalsConfig.HUD_HEALTH_MODE.get(), MedalsConfig.HUD_HUNGER_MODE.get(),
                    MedalsConfig.HUD_ARMOR_MODE.get(), p == null ? -1 : p.getArmorValue(),
                    p != null && p.isCreative(), p == null ? -1 : Mth.ceil(p.getMaxHealth()));
        } catch (Throwable ignored) {
            // 诊断本身不能炸
        }
    }

    private static long lastArmorLog;
    private static String lastArmorState;

    /** 护甲这条一旦出问题最难查（画没画、画在哪全靠猜），留一行诊断日志。 */
    private static void logArmorOnce(int mode, int armor, int armorY) {
        String state = mode + "/" + armor + "/" + armorY;
        long now = System.currentTimeMillis();
        if (state.equals(lastArmorState) && now - lastArmorLog < 10_000L) {
            return;
        }
        lastArmorState = state;
        lastArmorLog = now;
        Medals.LOGGER.info("[Lost Realm] HUD 护甲：mode={} 护甲点数={} 绘制y={} 最大生命={}",
                mode, armor, armorY, Mth.ceil(Minecraft.getInstance().player.getMaxHealth()));
    }

    // ================================================================
    // 通用绘制
    // ================================================================

    /**
     * 一条（或一条+数字）。
     *
     * @param extraRatio 条尾再接一段的比例（伤害吸收），{@code null} 表示没有
     * @param overlay    盖在已填充部分上的比例（饱和度），0 表示不画
     */
    /**
     * 一条（或一条 + 数字），<b>先给图标让出位置</b>。
     *
     * <p>三条前面/后面都留一个 9px 的图标位，没有图标贴图时不留空 ——
     * 否则会平白少一截。
     * 生命、护甲图标在左，饥饿在右（跟原版"心在左、鸡腿在右"的习惯一致）。
     *
     * @param iconRight 图标是否放在右端（饥饿用 true）
     */
    private static void vitalsRow(GuiGraphics g, int mode, String key, int x, int y,
                                  int width, int height, float ratio,
                                  String label, int color, String suffix,
                                  Float extraRatio, float overlay, boolean iconRight, int iconGap) {
        var font = Minecraft.getInstance().font;

        HudTextures.Img icon = HudTextures.get(key + "_icon");
        int iw = icon == null ? 0 : icon.w();
        int ih = icon == null ? 0 : icon.h();
        int reserve = icon == null ? 0 : iw + iconGap;
        int bx = iconRight ? x : x + reserve;
        int bw = width - reserve;

        if (icon != null) {
            int ix = iconRight ? x + width - iw : x;
            int iy = y + (height - ih) / 2;        // 竖直居中对齐条子
            g.blit(icon.id(), ix, iy, iw, ih, 0, 0, iw, ih, iw, ih);
        }

        if (mode == 2 || mode == 3) {
            bar(g, key, bx, y, bw, height, ratio, color, iconRight);
            if (overlay > 0.0F) {
                int ow = Math.round(bw * Math.min(Mth.clamp(ratio, 0.0F, 1.0F), overlay));
                if (ow > 0) {
                    int ox = iconRight ? bx + bw - ow : bx;
                    g.fill(ox, y, ox + ow, y + height, 0x66FFD24A);
                }
            }
            if (extraRatio != null && extraRatio > 0.0F) {
                int base = Math.round(bw * Mth.clamp(ratio, 0.0F, 1.0F));
                int add = Math.min(bw - base, Math.round(bw * extraRatio));
                if (add > 0) {
                    int ex = iconRight ? bx + bw - base - add : bx + base;
                    g.fill(ex, y, ex + add, y + height, COLOR_EXTRA);
                }
            }
        }

        if (mode == 1) {
            // 数值模式：竖直居中对齐条子
            int ty = y + (height - font.lineHeight) / 2;
            text(g, bx, ty, label, TEXT_WHITE, 1.0F);
            if (suffix != null) {
                text(g, bx + font.width(label) + 3, ty, suffix, TEXT_WHITE, 1.0F);
            }
        } else if (mode == 3) {
            float k = 0.6F;
            int wLabel = Math.round(font.width(label) * k);
            int wExtra = suffix == null ? 0 : Math.round(font.width(suffix) * k) + 2;
            int tx = bx + (bw - wLabel - wExtra) / 2;
            int ty = y + (height - Math.round(8 * k)) / 2;
            text(g, tx, ty, label, TEXT_WHITE, k);
            if (suffix != null) {
                text(g, tx + wLabel + 2, ty, suffix, TEXT_WHITE, k);
            }
        }
    }

    /**
     * 一条：直接用 {@code <key>_bg.png} / {@code <key>_fill.png}。
     *
     * <p><b>贴图按目标尺寸的整数倍画</b>，不做任何缩放拉伸：
     * 生命/饥饿条 80×7 → 贴图 160×14；护甲/韧性条 30×7 → 贴图 60×14。
     * 这样边框粗细永远等于你画的像素，不会出现"上边粗下边细"或者糊掉。
     *
     * <p>填充图按"满"画，这里只取左边 {@code 当前比例} 那一段（镜像条取右边）。
     */
    private static void bar(GuiGraphics g, String key, int x, int y, int w, int h,
                            float ratio, int color, boolean mirror) {
        float r = Mth.clamp(ratio, 0.0F, 1.0F);

        HudTextures.Img bg = HudTextures.get(key + "_bg");
        if (bg != null) {
            g.blit(bg.id(), x, y, w, h, 0, 0, bg.w(), bg.h(), bg.w(), bg.h());
        } else {
            g.fill(x - 1, y - 1, x + w + 1, y + h + 1, BG_BORDER);
            g.fill(x, y, x + w, y + h, BG_TRACK);
        }

        int filled = Math.round(w * r);
        if (filled <= 0) {
            return;
        }
        HudTextures.Img fill = HudTextures.get(key + "_fill");
        if (fill != null) {
            int sw = Math.max(1, Math.round(fill.w() * r));
            int u = mirror ? fill.w() - sw : 0;
            int fx = mirror ? x + w - filled : x;
            g.blit(fill.id(), fx, y, filled, h, u, 0, sw, fill.h(), fill.w(), fill.h());
        } else {
            int fx = mirror ? x + w - filled : x;
            g.fill(fx, y, fx + filled, y + h, color);
        }
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

    /**
     * 观察者 / 骑乘：整块交给原版。
     *
     * <p>创造模式也整块让位：那三条数据在创造下没有意义（原版也不画），
     * 全满的血条/护甲条只会挡视线。
     */
    private static boolean skipAll(Player player) {
        return player.isCreative() || player.isSpectator() || player.isPassenger();
    }
}
