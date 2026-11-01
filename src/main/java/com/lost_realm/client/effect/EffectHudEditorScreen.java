package com.lost_realm.client.effect;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 状态效果 HUD 的<b>可视化编辑界面</b>。
 *
 * <h2>交互模型</h2>
 * 界面实时预览效果 HUD,所有改动即时可见:
 * <ul>
 *   <li><b>缩放</b> —— 一行四个按钮(25% / 50% / 75% / 100%),当前值高亮</li>
 *   <li><b>角落</b> —— 2×2 方位按钮组,直观对应屏幕四角</li>
 *   <li><b>方向</b> —— 横向 / 纵向两个按钮</li>
 *   <li><b>倒计时</b> —— 分钟 / 秒 / 进度条 / 关闭 四个按钮</li>
 * </ul>
 *
 * 底部:<b>保存</b> 写入配置并持久化;<b>取消</b> 丢弃全部改动;<b>重置</b> 恢复默认。
 *
 * <h2>为什么用「覆盖层 + 一次性提交」</h2>
 * Forge 的配置值在运行时只读,逐个 {@code set()} 会造成
 * 「点一下写一次盘」与「取消后无法回滚」。这里改为:编辑期间所有改动只写
 * {@link EffectHudOverrides},点「保存」才一次性落到配置并 {@code save()},
 * 点「取消」直接丢弃 —— 语义清晰且无副作用。
 */
public class EffectHudEditorScreen extends Screen {

    private static final int PANEL_BG = 0xC0101010;
    private static final int PANEL_BORDER = 0xFF808080;
    private static final int LABEL_COLOR = 0xFFE0E0E0;
    private static final int HINT_COLOR = 0xFFA0A0A0;

    private final Screen parent;

    /** 各种可选项的取值表,便于按钮循环切换与校验。 */
    private static final int[] SCALE_OPTIONS = { 25, 50, 75, 100 };
    private static final int[] TIMER_OPTIONS = { 0, 1, 2, 3 };

    /** 上一次布局所用的宽度,避免 resize 时按钮错位。 */
    private int laidOutWidth = -1;
    private int laidOutHeight = -1;

    public EffectHudEditorScreen(Screen parent) {
        super(Component.translatable("screen.lost_realm.effect_hud.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuild();
    }

    /** 依据当前屏幕尺寸重排全部控件。 */
    private void rebuild() {
        clearWidgets();
        laidOutWidth = width;
        laidOutHeight = height;

        int panelW = 320;
        int panelX = (width - panelW) / 2;
        int y = height - 132;

        // ---------------- 缩放 ----------------
        addRenderableWidget(Button.builder(label("scale"), b -> cycleScale())
                .bounds(panelX, y, 74, 20).build());
        for (int i = 0; i < SCALE_OPTIONS.length; i++) {
            int opt = SCALE_OPTIONS[i];
            addRenderableWidget(Button.builder(
                            Component.literal(opt + "%"),
                            b -> EffectHudOverrides.setScale(opt))
                    .bounds(panelX + 78 + i * 60, y, 56, 20).build());
        }

        // ---------------- 角落(2×2) ----------------
        int cornerY = y + 26;
        addRenderableWidget(Button.builder(label("corner"), b -> {})
                .bounds(panelX, cornerY, 74, 44).build());
        // 左上 / 右上
        addRenderableWidget(Button.builder(cornerLabel(0), b -> EffectHudOverrides.setCorner(0))
                .bounds(panelX + 78, cornerY, 88, 20).build());
        addRenderableWidget(Button.builder(cornerLabel(1), b -> EffectHudOverrides.setCorner(1))
                .bounds(panelX + 170, cornerY, 88, 20).build());
        // 左下 / 右下
        addRenderableWidget(Button.builder(cornerLabel(2), b -> EffectHudOverrides.setCorner(2))
                .bounds(panelX + 78, cornerY + 24, 88, 20).build());
        addRenderableWidget(Button.builder(cornerLabel(3), b -> EffectHudOverrides.setCorner(3))
                .bounds(panelX + 170, cornerY + 24, 88, 20).build());

        // ---------------- 方向 + 倒计时 ----------------
        int rowY = y + 78;
        addRenderableWidget(Button.builder(label("flow"), b -> {})
                .bounds(panelX, rowY, 74, 20).build());
        addRenderableWidget(Button.builder(flowLabel(0), b -> EffectHudOverrides.setFlow(0))
                .bounds(panelX + 78, rowY, 60, 20).build());
        addRenderableWidget(Button.builder(flowLabel(1), b -> EffectHudOverrides.setFlow(1))
                .bounds(panelX + 142, rowY, 60, 20).build());

        addRenderableWidget(Button.builder(label("timer"), b -> {})
                .bounds(panelX + 210, rowY, 40, 20).build());
        for (int i = 0; i < TIMER_OPTIONS.length; i++) {
            int opt = TIMER_OPTIONS[i];
            addRenderableWidget(Button.builder(
                            Component.translatable("screen.lost_realm.effect_hud.timer." + opt),
                            b -> EffectHudOverrides.setTimer(opt))
                    .bounds(panelX + 252 + i * 17, rowY, 16, 20).build());
        }

        // ---------------- 减益红框 ----------------
        // 放在缩放行的右端剩余空间,做成一个「开/关」双态按钮。
        int borderY = y + 26;
        addRenderableWidget(Button.builder(borderLabel(), b -> {
                    EffectHudOverrides.setHarmfulBorder(!EffectHudOverrides.harmfulBorder());
                    // 双态按钮文案要跟着变,直接重排一次最省事
                    rebuild();
                })
                .bounds(panelX + 266, borderY, 62, 20).build());

        // ---------------- 底部操作 ----------------
        int btnY = y + 104;
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.lost_realm.effect_hud.save"),
                        b -> {
                            EffectHudOverrides.commit();
                            onClose();
                        })
                .bounds(panelX, btnY, 100, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.lost_realm.effect_hud.cancel"),
                        b -> {
                            EffectHudOverrides.clear();
                            onClose();
                        })
                .bounds(panelX + 108, btnY, 100, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.lost_realm.effect_hud.reset"),
                        b -> {
                            // 重置 = 写回默认值(100% / 右上 / 纵向 / 秒 / 红框开)
                            EffectHudOverrides.setScale(100);
                            EffectHudOverrides.setCorner(1);
                            EffectHudOverrides.setFlow(1);
                            EffectHudOverrides.setTimer(2);
                            EffectHudOverrides.setHarmfulBorder(true);
                            rebuild();
                        })
                .bounds(panelX + 216, btnY, 100, 20).build());
    }

    /** 循环切换缩放档位。 */
    private void cycleScale() {
        int current = EffectHudOverrides.scale();
        int idx = 0;
        for (int i = 0; i < SCALE_OPTIONS.length; i++) {
            if (SCALE_OPTIONS[i] == current) {
                idx = i;
                break;
            }
        }
        EffectHudOverrides.setScale(SCALE_OPTIONS[(idx + 1) % SCALE_OPTIONS.length]);
    }

    private static Component label(String key) {
        return Component.translatable("screen.lost_realm.effect_hud.label." + key);
    }

    /** 角落按钮文案:当前选中项加「▶」前缀以示强调。 */
    private static Component cornerLabel(int corner) {
        Component base = Component.translatable("screen.lost_realm.effect_hud.corner." + corner);
        return EffectHudOverrides.corner() == corner
                ? Component.literal("▶ ").append(base)
                : base;
    }

    /**
     * 减益红框双态按钮文案。
     *
     * <p>开:显示「红框:开」;关:显示「红框:关」。
     * 文案随状态实时切换,玩家不必点开才知道当前值。
     */
    private static Component borderLabel() {
        return Component.translatable(
                EffectHudOverrides.harmfulBorder()
                        ? "screen.lost_realm.effect_hud.border.on"
                        : "screen.lost_realm.effect_hud.border.off");
    }

    private static Component flowLabel(int flow) {
        Component base = Component.translatable("screen.lost_realm.effect_hud.flow." + flow);
        return EffectHudOverrides.flow() == flow
                ? Component.literal("▶ ").append(base)
                : base;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // 尺寸变化时重排(全屏/窗口缩放)
        if (width != laidOutWidth || height != laidOutHeight) {
            rebuild();
        }

        // 半透明遮罩,让预览更清晰。
        // 注意:1.20.1 的 Screen#renderBackground 只接受 (GuiGraphics) 一个参数,
        // 带 mouseX/mouseY/partialTick 的 4 参重载是 1.20.5+ 才有的。这里必须用单参形式。
        renderBackground(gfx);

        // ---- 实时预览:直接调用渲染器,所见即所得 ----
        EffectHudRenderer.render(gfx, width, height);

        // ---- 底部面板 ----
        int panelW = 320;
        int panelX = (width - panelW) / 2;
        int panelY = height - 140;
        gfx.fill(panelX - 6, panelY - 6, panelX + panelW + 6, height - 2, PANEL_BG);
        gfx.renderOutline(panelX - 6, panelY - 6, panelW + 12, height - 4, PANEL_BORDER);

        super.render(gfx, mouseX, mouseY, partialTick);

        // ---- 标题与提示 ----
        gfx.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        gfx.drawCenteredString(font,
                Component.translatable("screen.lost_realm.effect_hud.hint"),
                width / 2, 20, HINT_COLOR);

        // ---- 高亮当前缩放档 ----
        gfx.drawString(font,
                Component.translatable("screen.lost_realm.effect_hud.current",
                        EffectHudOverrides.scale(),
                        Component.translatable("screen.lost_realm.effect_hud.corner."
                                + EffectHudOverrides.corner()),
                        Component.translatable("screen.lost_realm.effect_hud.flow."
                                + EffectHudOverrides.flow()),
                        Component.translatable("screen.lost_realm.effect_hud.timer."
                                + EffectHudOverrides.timer())),
                panelX, height - 16, LABEL_COLOR);
    }

    @Override
    public void onClose() {
        // ★ dv37：按 Esc / 点界面外关闭时，必须把未提交的覆盖丢弃。
        //
        //   EffectHudOverrides 是一组**静态变量**，渲染器读它的优先级高于配置。
        //   旧版这个 onClose 只切屏幕、不清覆盖 —— 于是玩家只要在这个界面里点过
        //   任意一档（比如把倒计时点成「×」），然后按 Esc 退出，那个值就会一直粘着：
        //   之后无论去正式配置界面把「倒计时显示」改成秒/分钟/进度条，
        //   渲染器都仍然读那个残留的覆盖值，看起来就是「设置根本不起作用」。
        //
        //   「保存」按钮内部已经 commit()（其中含 clear()），这里再清一次是幂等的。
        EffectHudOverrides.clear();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 供外部(快捷键)进入编辑界面。 */
    public static void open() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.setScreen(new EffectHudEditorScreen(mc.screen));
    }

    /** 便捷:取得当前所有可选项,便于单测/调试。 */
    public static List<Integer> scaleOptions() {
        return List.of(25, 50, 75, 100);
    }
}
