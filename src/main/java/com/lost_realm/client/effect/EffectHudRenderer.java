package com.lost_realm.client.effect;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import com.lost_realm.config.MedalsConfig;

/**
 * 更好的状态效果 HUD —— 渲染器。
 *
 * <h2>为什么要「接管」而不是「微调」</h2>
 * 原版 {@code Gui#renderEffects} 直接以<b>绝对值</b>计算图标坐标
 * (从 {@code screenWidth} 往左递减,每个占 25px),且全程<b>不触碰 PoseStack</b>。
 * 因此无法用「push 一个缩放矩阵」的方式改大小 —— 那样图标会缩放,
 * 但它的绘制坐标不会跟着变,结果是图标缩小却仍按原位置摆,整体错位。
 *
 * <p>所以本类采取<b>整体接管</b>:由 {@link com.lost_realm.mixin.client.EffectHudMixin}
 * 取消原版渲染,再由本类按配置重新布局与绘制。这样缩放、方位、方向、倒计时
 * 四件事都能精确控制。
 *
 * <h2>布局模型</h2>
 * 四种参数正交组合:
 * <ul>
 *   <li><b>缩放</b> —— 25% / 50% / 75% / 100%,以原版 24px 图标为 100%</li>
 *   <li><b>角落</b> —— 左上 / 右上 / 左下 / 右下,决定整体停靠位置</li>
 *   <li><b>方向</b> —— 横向(排成一行)/ 纵向(排成一列)</li>
 *   <li><b>倒计时</b> —— 不显示 / 分钟(mm:ss) / 秒 / 进度条,三选一</li>
 * </ul>
 *
 * 「角落」只决定<b>锚点</b>与<b>生长方向</b>,「方向」独立决定横排还是竖排,
 * 二者正交 —— 例如「右下 + 横向」表示一行图标贴着右下角往左延伸。
 */
public final class EffectHudRenderer {

    /** 原版图标格子的边长(px),作为 100% 缩放的基准。 */
    private static final int BASE_SLOT = 24;

    /**
     * 原版图标在本格内的实际边长(px)。
     *
     * <p>原版把 18×18 的图标画在 24×24 的格子里，四周各留 3px 内边距。
     * 若直接把图标按整格 24px 绘制，图标会顶满边框、和红框贴在一起。
     */
    private static final int ICON_SIZE = 18;

    /** 原版图标在本格内的内边距(px)。 */
    private static final int ICON_INSET = 3;

    /** 原版格子之间的间隔(px)。 */
    private static final int BASE_GAP = 1;

    /** 边缘留白(px)。 */
    private static final int MARGIN = 2;

    /** 进度条宽度占格子宽度的比例。 */
    private static final float BAR_WIDTH_RATIO = 0.8F;

    /** 进度条高度(px,不受缩放影响的下限)。 */
    private static final int BAR_HEIGHT = 2;

    /**
     * 每份效果的「历史最长剩余量」，进度条用它做满格基准。
     *
     * <p>键是 {@link MobEffect} 本体，值是该效果出现以来最大的 duration（tick）。
     * 效果消失后由 {@link #render} 统一回收，避免 Map 越积越大。
     */
    private static final java.util.Map<MobEffect, Integer> DURATION_PEAK = new java.util.HashMap<>();

    /**
     * 减益效果外框的颜色(不透明红)。
     *
     * <p>原版对增益与减益用同一套灰白底框,只能靠图标本身区分,
     * 危急时刻容易看漏。这里给减益加一圈红框,让「我中毒了 / 我着火了」
     * 一眼可见。
     */
    private static final int HARMFUL_BORDER_COLOR = 0xFFFF3030;

    /**
     * 减益效果 1px 内衬圈的颜色(暗一档的红,用于做出立体感)。
     *
     * <p>纯色描边在浅色背景上偏平,叠一圈暗红内衬后轮廓更实,
     * 并且暗红那侧朝向图标,不会抢走图标本身的视觉重心。
     */
    private static final int HARMFUL_INNER_COLOR = 0xFF7A0E0E;

    // 角落枚举(与配置的 0~3 对应)
    private static final int CORNER_TOP_LEFT = 0;
    private static final int CORNER_TOP_RIGHT = 1;
    private static final int CORNER_BOTTOM_LEFT = 2;
    private static final int CORNER_BOTTOM_RIGHT = 3;

    // 倒计时模式(与配置的 0~3 对应)
    private static final int TIMER_NONE = 0;
    private static final int TIMER_MINUTES = 1;
    private static final int TIMER_SECONDS = 2;
    private static final int TIMER_BAR = 3;

    /**
     * 渲染入口。
     *
     * @param gfx    绘制上下文
     * @param width  当前 GUI 逻辑宽度
     * @param height 当前 GUI 逻辑高度
     */
    public static void render(GuiGraphics gfx, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        List<MobEffectInstance> effects = collectVisible(player);
        if (effects.isEmpty()) {
            return;
        }

        // 回收已消失效果的进度条峰值，防止 Map 无限膨胀
        DURATION_PEAK.keySet().retainAll(
                effects.stream().map(MobEffectInstance::getEffect).collect(java.util.stream.Collectors.toSet()));

        float scale = EffectHudOverrides.scale() / 100.0F;
        int slot = Math.max(8, Math.round(BASE_SLOT * scale));
        int gap = Math.max(0, Math.round(BASE_GAP * scale));
        int step = slot + gap;

        int corner = EffectHudOverrides.corner();
        boolean vertical = EffectHudOverrides.flow() == 1;
        int timer = EffectHudOverrides.timer();

        // ---- 统计「增益 / 减益」两组,以便两组各占一档 ----
        // 原版会把增益与减益分开:增益在上、减益在下。这里保留该语义,
        // 但改为沿「生长方向」自然分流。
        List<MobEffectInstance> beneficial = new ArrayList<>();
        List<MobEffectInstance> harmful = new ArrayList<>();
        for (MobEffectInstance inst : effects) {
            if (inst.getEffect().isBeneficial()) {
                beneficial.add(inst);
            } else {
                harmful.add(inst);
            }
        }

        // ---- 计算锚点 ----
        // 横向:增益一行,减益另起一行(纵向堆叠两组)
        // 纵向:增益一列,减益另起一列(横向并排两组)
        int groupCount = (beneficial.isEmpty() || harmful.isEmpty()) ? 1 : 2;

        int maxPerGroup = Math.max(beneficial.size(), harmful.size());
        int totalW = vertical ? groupCount * step - gap : maxPerGroup * step - gap;
        int totalH = vertical ? maxPerGroup * step - gap : groupCount * step - gap;
        totalW = Math.max(0, totalW);
        totalH = Math.max(0, totalH);

        int originX = switch (corner) {
            case CORNER_TOP_LEFT, CORNER_BOTTOM_LEFT -> MARGIN;
            default -> width - MARGIN - totalW;
        };
        int originY = switch (corner) {
            case CORNER_TOP_LEFT, CORNER_TOP_RIGHT -> MARGIN;
            default -> height - MARGIN - totalH;
        };

        // ---- 逐组绘制 ----
        drawGroup(gfx, beneficial, originX, originY,
                corner, vertical, step, slot, scale, timer, 0);
        if (!harmful.isEmpty()) {
            // 第二组:沿「组间轴」偏移一整档
            int offset = beneficial.isEmpty() ? 0 : step;
            int gx = vertical ? originX + offset : originX;
            int gy = vertical ? originY : originY + offset;
            drawGroup(gfx, harmful, gx, gy,
                    corner, vertical, step, slot, scale, timer, 1);
        }
    }

    /**
     * 绘制一组效果(增益组或减益组)。
     *
     * @param groupIndex 0 = 增益组,1 = 减益组(仅用于定位,不影响样式)
     */
    private static void drawGroup(GuiGraphics gfx, List<MobEffectInstance> group,
                                  int startX, int startY, int corner, boolean vertical,
                                  int step, int slot, float scale, int timer, int groupIndex) {
        if (group.isEmpty()) {
            return;
        }
        // 右上 / 右下角:生长方向取反,才能真正「贴住角落」
        boolean reverse = (corner == CORNER_TOP_RIGHT || corner == CORNER_BOTTOM_RIGHT);

        for (int i = 0; i < group.size(); i++) {
            // 沿生长方向的索引:反向时从末端往回排
            int idx = reverse ? (group.size() - 1 - i) : i;
            int x = vertical ? startX : startX + idx * step;
            int y = vertical ? startY + idx * step : startY;
            drawOne(gfx, group.get(i), x, y, slot, scale, timer);
        }
    }

    /** 绘制单个效果:底框 + 图标 + 增幅等级 + 倒计时。 */
    private static void drawOne(GuiGraphics gfx, MobEffectInstance inst,
                                int x, int y, int slot, float scale, int timer) {
        Minecraft mc = Minecraft.getInstance();
        MobEffect effect = inst.getEffect();
        boolean beneficial = effect.isBeneficial();

        // ---- 底框 ----
        // ★ dv36：底框必须**跟着 scale 一起缩放**。
        //   旧的写法是固定画 24×24，而格子间距 slot 已经按比例缩到
        //   round(24*scale) —— 调到 50% 以下时相邻格子只剩 12/6px，
        //   固定 24px 的底框会互相叠成一团糊。这里用 pose 缩放解决。
        boolean ambient = inst.isAmbient();
        gfx.pose().pushPose();
        gfx.pose().translate(x, y, 0.0F);
        gfx.pose().scale(scale, scale, 1.0F);
        gfx.blit(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.INVENTORY_LOCATION,
                0, 0, 0, ambient ? 165 : 141, 166, BASE_SLOT, BASE_SLOT, 256, 256);
        gfx.pose().popPose();

        // ---- 减益红框 ----
        // 画在底框之上、图标之下:这样红框能盖住原版底框的灰边,
        // 但不会压到图标本体。
        // 红框仍用**绝对像素**绘制:它的目的就是「1px 清晰描边」,
        // 丢进缩放矩阵会让线宽随缩放变成 0.x px 而画不出来。
        if (!beneficial && EffectHudOverrides.harmfulBorder()) {
            drawHarmfulBorder(gfx, x, y, slot);
        }

        // ---- 图标 ----
        TextureAtlasSprite sprite = mc.getMobEffectTextures().get(effect);
        // 原版用闪烁 alpha 表示「即将结束」,这里保留该表现
        float alpha = 1.0F;
        if (inst.endsWithin(200)) {
            alpha = computeBlinkAlpha(inst);
        }
        final float iconAlpha = alpha;
        gfx.pose().pushPose();
        // ★ dv36 修复「图标整个不见了」★
        //
        //   坐标系此刻已经平移到本格左上角 (x, y)，所以 blit 必须用**相对坐标**。
        //   旧版这里又传了一次绝对坐标 (iconX, iconY)，等于把图标在 x/y 上各多推
        //   一个格子位置 —— 停靠右上/右下角时 (x 接近屏幕右缘) 直接被推到屏幕外，
        //   表现就是底框还在、图标却「消失了」。左上角因为 x≈2 只偏 2px 才不明显。
        gfx.pose().translate(x, y, 0.0F);
        gfx.pose().scale(scale, scale, 1.0F);
        gfx.setColor(1.0F, 1.0F, 1.0F, iconAlpha);
        // 18×18 居中于本格（与原版 renderEffects 的画法一致）
        gfx.blit(ICON_INSET, ICON_INSET, 0, ICON_SIZE, ICON_SIZE, sprite);
        gfx.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        gfx.pose().popPose();

        // ---- 增幅等级 ----
        if (inst.getAmplifier() >= 0 && scale >= 0.5F) {
            String lvl = String.valueOf(inst.getAmplifier() + 1);
            gfx.drawString(mc.font, lvl,
                    x + slot - mc.font.width(lvl) - 1,
                    y + slot - (int) (mc.font.lineHeight * scale) + 1,
                    0xFFFFFFFF, true);
        }

        // ---- 倒计时 ----
        drawTimer(gfx, inst, x, y, slot, scale, timer);
    }

    /**
     * 为减益效果绘制红色描边。
     *
     * <p>之所以画「一圈边框」而不是「整块红底」:
     * 红底会让下方的药水图标(本身多为深色+彩色)对比度骤降,
     * 而描边只在最外圈占 1px,既醒目又完全不遮图标。
     *
     * <p>实现上用 4 个 {@code fill} 画四条边,而不是 {@code renderOutline} ——
     * 后者在 1.20.1 里画的是 1px 内描边且颜色固定为半透明黑,
     * 无法指定颜色,也不适合做「外扩」边框。
     *
     * <p>缩放较小时({@code slot <= 8})边框会吃掉过多像素,
     * 此时只画 1px 纯红单线,保证小尺寸下依然能看清轮廓。
     */
    private static void drawHarmfulBorder(GuiGraphics gfx, int x, int y, int slot) {
        int right = x + slot;
        int bottom = y + slot;

        boolean thin = slot <= 10;
        if (thin) {
            // 小尺寸:单线红框
            gfx.fill(x, y, right, y + 1, HARMFUL_BORDER_COLOR);          // 上
            gfx.fill(x, bottom - 1, right, bottom, HARMFUL_BORDER_COLOR); // 下
            gfx.fill(x, y + 1, x + 1, bottom - 1, HARMFUL_BORDER_COLOR);  // 左
            gfx.fill(right - 1, y + 1, right, bottom - 1, HARMFUL_BORDER_COLOR); // 右
            return;
        }

        // 常规尺寸:1px 亮红外圈 + 1px 暗红内衬,做出立体轮廓
        gfx.fill(x, y, right, y + 1, HARMFUL_BORDER_COLOR);
        gfx.fill(x, bottom - 1, right, bottom, HARMFUL_BORDER_COLOR);
        gfx.fill(x, y + 1, x + 1, bottom - 1, HARMFUL_BORDER_COLOR);
        gfx.fill(right - 1, y + 1, right, bottom - 1, HARMFUL_BORDER_COLOR);

        gfx.fill(x + 1, y + 1, right - 1, y + 2, HARMFUL_INNER_COLOR);
        gfx.fill(x + 1, bottom - 2, right - 1, bottom - 1, HARMFUL_INNER_COLOR);
        gfx.fill(x + 1, y + 2, x + 2, bottom - 2, HARMFUL_INNER_COLOR);
        gfx.fill(right - 2, y + 2, right - 1, bottom - 2, HARMFUL_INNER_COLOR);
    }

    /** 按配置的三种方式之一绘制倒计时。 */
    private static void drawTimer(GuiGraphics gfx, MobEffectInstance inst,
                                  int x, int y, int slot, float scale, int timer) {
        Minecraft mc = Minecraft.getInstance();

        // 无限时长的效果(如 /effect 给 infinite)不显示倒计时
        if (inst.isInfiniteDuration()) {
            return;
        }
        int durationTicks = inst.getDuration();

        switch (timer) {
            case TIMER_MINUTES -> {
                String text = formatMinutes(durationTicks);
                drawTimerText(gfx, text, x, y, slot);
            }
            case TIMER_SECONDS -> {
                String text = String.valueOf(durationTicks / 20);
                drawTimerText(gfx, text, x, y, slot);
            }
            case TIMER_BAR -> {
                drawTimerBar(gfx, inst, x, y, slot);
            }
            default -> {
                // TIMER_NONE:不绘制
            }
        }
    }

    /** 以 mm:ss 显示剩余时间。 */
    public static String formatMinutes(int durationTicks) {
        int totalSeconds = Math.max(0, durationTicks / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    /** 倒计时文本:画在图标下方居中。 */
    private static void drawTimerText(GuiGraphics gfx, String text, int x, int y, int slot) {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.font.width(text);
        // 文本略微上移贴住格子底边;横向排布时也必须居中于本格
        int tx = x + (slot - w) / 2;
        int ty = y + slot - mc.font.lineHeight + 1;
        // 描边提高可读性
        gfx.drawString(mc.font, text, tx, ty, 0xFFFFFFFF, true);
    }

    /**
     * 进度条:画在图标下方。
     *
     * <p>进度 = 剩余时间 / 总时间。但 {@link MobEffectInstance} 只暴露<b>剩余</b>
     * duration，拿不到「施加时的总时长」—— 所以这里自己记一份「历史最长剩余量」
     * 作为满格基准：效果刚上身时记下峰值，之后每帧拿当前剩余 / 峰值，
     * 就是一条单调递减、会动的进度条。
     *
     * <p>峰值会在效果消失后清除（避免残留），也能承受「续杯」导致的剩余量
     * 变大的情况（峰值会被抬到新的最大值，进度条相应重新拉满）。
     */
    private static void drawTimerBar(GuiGraphics gfx, MobEffectInstance inst,
                                     int x, int y, int slot) {
        int duration = Math.max(0, inst.getDuration());
        // 满格基准 = 这份效果出现以来见过的最大剩余量。
        //
        // ★ beta44 去掉了原来的 `Math.max(peak, 600)` 封底。
        //   那行注释写的是「让 30 秒以内的效果能用满格表达」，实际效果正好相反：
        //   封底把分母顶到 600，10 秒的 buff 一上手就只显示 33%，一辈子满不了。
        //   峰值本身就是「刚上身时的剩余量」，拿它当分母才是真正会从满格开始掉。
        int peak = DURATION_PEAK.merge(inst.getEffect(), duration, Math::max);
        int full = Math.max(peak, 1);
        float progress = duration <= 0 ? 0.0F : Math.min(1.0F, duration / (float) full);

        int barW = Math.max(4, Math.round(slot * BAR_WIDTH_RATIO));
        int barX = x + (slot - barW) / 2;
        int barY = y + slot - BAR_HEIGHT - 1;

        // 槽底色
        gfx.fill(barX, barY, barX + barW, barY + BAR_HEIGHT, 0xC0000000);
        // 前景:按效果是否为增益/减益取色(增益偏青绿,减益偏红)
        int color = inst.getEffect().isBeneficial() ? 0xFF55FF55 : 0xFFFF5555;
        int filled = Math.round(barW * progress);
        if (filled > 0) {
            gfx.fill(barX, barY, barX + filled, barY + BAR_HEIGHT, color);
        }
    }

    /**
     * 复刻原版的「即将结束」闪烁 alpha。
     *
     * <p>原版在剩余 10 秒内让图标以正弦规律明暗闪烁,提示快结束了。
     */
    private static float computeBlinkAlpha(MobEffectInstance inst) {
        int duration = inst.getDuration();
        int secondsLeft = duration / 20;
        int f = Math.max(0, 10 - secondsLeft);
        return net.minecraft.util.Mth.clamp(
                duration / 10.0F / 5.0F * 0.5F, 0.0F, 0.5F)
                + net.minecraft.util.Mth.clamp(
                (float) Math.cos(duration * Math.PI / 5.0F) * f / 10.0F * 0.25F, 0.0F, 0.25F);
    }

    /** 收集应显示的效果,过滤规则与原版保持一致。 */
    private static List<MobEffectInstance> collectVisible(LocalPlayer player) {
        List<MobEffectInstance> out = new ArrayList<>();
        for (MobEffectInstance inst : player.getActiveEffects()) {
            // Forge 扩展点:模组可声明某效果不在 GUI 显示
            try {
                var ext = net.minecraftforge.client.extensions.common.IClientMobEffectExtensions.of(inst);
                if (ext != null && !ext.isVisibleInGui(inst)) {
                    continue;
                }
            } catch (Throwable ignored) {
                // 扩展点不可用时按可见处理
            }
            if (!inst.showIcon()) {
                continue;
            }
            out.add(inst);
        }
        // 与原版一致:按效果自身的比较顺序倒序(越"重"的排越前)
        out.sort((a, b) -> com.google.common.collect.Ordering.natural()
                .reverse().compare(a, b));
        return out;
    }

    private EffectHudRenderer() {}
}
