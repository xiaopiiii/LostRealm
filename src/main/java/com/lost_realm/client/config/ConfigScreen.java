package com.lost_realm.client.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.common.ForgeConfigSpec;

import com.lost_realm.config.MedalsConfig;

/**
 * Lost Realm 的<b>可视化配置界面</b> —— 左侧分类、右侧选项。
 *
 * <h2>布局</h2>
 * <pre>
 * ┌──────────┬──────────────────────────────────────────┐
 * │ 分类列表  │  选项行(名称 + 控件)                      │
 * │ (可滚动)  │  Toggle : [ 开 ]  ← 点击直接翻转           │
 * │          │  Stepper: [ - ] 20 [ + ]   ← 长按可连发     │
 * │          │  Switch : [ 左上 | ▶右上 | 左下 | 右下 ]    │
 * ├──────────┴──────────────────────────────────────────┤
 * │ [ 保存 ]  [ 取消 ]  [ 重置本页 ]        共 N 项        │
 * └─────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>编辑模型</h2>
 * 所有改动只写进 {@link ConfigOption} 的编辑缓冲,**不碰真实配置**。
 * 点「保存」时:先把有改动的选项 {@code apply()} 到各自的
 * {@code ConfigValue},再对**确实被改过**的 spec 调一次 {@code save()} 落盘;
 * 点「取消」直接丢弃缓冲。这样才可能做到「预览 / 回滚」。
 *
 * <h2>为什么保存时要区分 spec</h2>
 * 选项分属 {@code CLIENT_SPEC} 与 {@code SPEC} 两份配置。
 * 无条件同时 save 两份会写两次盘;这里用集合记录"哪些 spec 被改过",
 * 只保存真正需要的那一份。
 */
public class ConfigScreen extends Screen {

    // ---- 配色 ----
    private static final int BG_DIM          = 0xC0101010;
    private static final int SIDEBAR_BG      = 0xFF1A1A1A;
    private static final int CONTENT_BG      = 0xFF101010;
    private static final int BORDER          = 0xFF505050;
    private static final int ROW_ALT         = 0x18FFFFFF;
    private static final int ROW_HOVER       = 0x30FFFFFF;
    private static final int ROW_DIRTY       = 0x30FFAA00;
    private static final int TEXT            = 0xFFE8E8E8;
    private static final int TEXT_DIM        = 0xFF909090;
    private static final int TEXT_DIRTY      = 0xFFFFC040;
    private static final int CAT_SELECTED    = 0xFF2D4A6B;

    // ---- 尺寸 ----
    private static final int SIDEBAR_W  = 132;
    private static final int ROW_H      = 22;
    private static final int FOOTER_H   = 30;
    private static final int PAD        = 6;

    /** 数值输入框的宽度。 */
    private static final int VALUE_BOX_W = 68;

    /** 数值框与右侧单位文字之间的间隙。 */
    private static final int UNIT_GAP   = 4;

    private final Screen parent;

    /** 当前选中的分类 id。 */
    private String categoryId = ConfigCategory.OVERVIEW.id();

    /** 选项区滚动偏移(像素)。 */
    private int scroll;

    /** 侧边栏滚动偏移。 */
    private int catScroll;

    /** 当前分类的选项列表(缓存,切换分类时重建)。 */
    private List<ConfigOption> current = List.of();

    /** 每行对应的控件(用于点击命中测试)。 */
    private final List<RowWidget> rows = new ArrayList<>();

    /** 鼠标按下期间正在拖动的滑杆行(用于连续调节)。 */
    private ConfigOption dragging;

    private int contentX() { return SIDEBAR_W + PAD; }

    private int contentW() { return width - SIDEBAR_W - PAD * 2; }

    private int contentTop() { return 30; }

    private int contentBottom() { return height - FOOTER_H; }

    public ConfigScreen(Screen parent) {
        super(Component.translatable("screen.lost_realm.config.title"));
        this.parent = parent;
    }

    /** 从任意界面打开配置界面。 */
    public static void open(Screen parent) {
        Minecraft.getInstance().setScreen(new ConfigScreen(parent));
    }

    @Override
    protected void init() {
        switchCategory(categoryId);
    }

    // ================================================================
    // 分类切换
    // ================================================================

    private void switchCategory(String id) {
        this.categoryId = id;
        this.scroll = 0;
        this.current = ConfigCatalog.ofCategory(id);
        rebuildRows();
    }

    /**
     * 重建右侧控件。
     *
     * <p>用 {@code clearWidgets()} 清空后重新生成 —— 与本项目
     * {@code EffectHudEditorScreen} 的做法一致,避免窗口缩放时控件错位。
     */
    private void rebuildRows() {
        clearWidgets();
        rows.clear();

        int y = contentTop() - scroll;
        for (ConfigOption opt : current) {
            // 只为本行【完整】可见时创建控件。
            //
            // ⚠️ 这里必须用「完整可见」而不是「部分可见」:
            // Minecraft 的控件(Button 等)一旦 addRenderableWidget 就会
            // 被原版按自己的坐标无条件绘制,无法裁剪到内容区。
            // 若为一半露在可视区外的行也建控件,它们就会溢到标题栏/侧边栏上,
            // 表现为界面顶部/底部出现一排错位的按钮和文字(dv9 修复)。
            if (y >= contentTop() && y + ROW_H <= contentBottom()) {
                rows.add(buildRow(opt, y));
            }
            y += ROW_H;
        }
        buildFooter();
    }

    /** 为单个选项创建控件组。 */
    private RowWidget buildRow(ConfigOption opt, int y) {
        RowWidget row = new RowWidget(opt, y);
        int cx = contentX();
        int cw = contentW();

        switch (opt.kind()) {
            case TOGGLE -> {
                int bw = 56;
                Button b = Button.builder(toggleLabel(opt),
                                btn -> {
                                    opt.setBool(!opt.asBool());
                                    btn.setMessage(toggleLabel(opt));
                                    markDirtyVisual(opt, btn);
                                })
                        .bounds(cx + cw - bw - PAD, y + 1, bw, ROW_H - 2)
                        .build();
                row.widgets.add(b);
                addRenderableWidget(b);
            }
            case STEPPER, DOUBLE_STEPPER -> {
                // ★ dv36：不再用 −/+ 两个按钮。
                //
                //   旧版把「−」放在 cx+cw-118、「+」放在 cx+cw-18，中间隔了整整
                //   100px（中间只是用来显示当前值），微调一格要两头来回点。
                //   现在改成一个**可直接输入**的数字框，单位画在框的右侧
                //   （见 renderContent）—— 想改 100→250 直接敲 250 回车即可。
                boolean isDouble = opt.kind() == ConfigOption.Kind.DOUBLE_STEPPER;
                EditBox box = new EditBox(font, valueBoxX(cx, cw, opt), y + 1,
                        VALUE_BOX_W, ROW_H - 2,
                        Component.translatable(opt.langKey()));
                box.setMaxLength(12);
                box.setValue(isDouble ? trimDouble(opt.asDouble()) : String.valueOf(opt.asInt()));
                // 只允许数字（小数项额外允许一个小数点与负号），避免敲进奇怪字符
                box.setFilter(s -> s.isEmpty() || "-".equals(s)
                        || (isDouble ? s.matches("-?\\d*\\.?\\d*") : s.matches("-?\\d*")));
                // 写入编辑缓冲即可，**不要**在这里 rebuildRows —— 那会每敲一个字符
                // 就重建控件、输入框立刻失去焦点，等于没法打字。
                box.setResponder(s -> {
                    if (s.isEmpty() || "-".equals(s) || ".".equals(s) || "-.".equals(s)) {
                        return; // 输入中途（只打了负号/小数点）解析必然失败，直接忽略
                    }
                    try {
                        if (isDouble) {
                            opt.setDouble(Double.parseDouble(s));
                        } else {
                            opt.setInt(Integer.parseInt(s));
                        }
                    } catch (NumberFormatException ignored) {
                        // 同上：中间态一律忽略，等玩家敲完再判定
                    }
                });
                row.widgets.add(box);
                addRenderableWidget(box);
            }
            case SWITCH -> {
                int[] vals = opt.switchValues();
                int bw = 42;
                for (int i = 0; i < vals.length; i++) {
                    final int v = vals[i];
                    Button b = Button.builder(switchLabel(opt, v), btn -> {
                                opt.setInt(v);
                                // 同组按钮高亮要一起刷新
                                rebuildRows();
                            })
                            .bounds(cx + cw - (vals.length - i) * (bw + 2) - PAD,
                                    y + 1, bw, ROW_H - 2).build();
                    row.widgets.add(b);
                    addRenderableWidget(b);
                }
            }
        }
        return row;
    }

    // ================================================================
    // 数值框与单位
    // ================================================================

    /**
     * 数值输入框的左边界。
     *
     * <p>右侧要依次留出：单位文字 → 间隙 → 内容区右内边距。
     * 没有单位时退化为「贴着右内边距」。
     */
    private int valueBoxX(int cx, int cw, ConfigOption opt) {
        return cx + cw - PAD - unitWidth(opt) - VALUE_BOX_W;
    }

    /**
     * 该选项的<b>单位</b>文字。
     *
     * <p>走语言键 {@code config.lost_realm.opt.<id>.unit}，因此中英文各自可译；
     * 未配置单位的选项返回空串，界面就不会画。
     *
     * <p>之所以不再把单位写进名称里（如旧版的「终末审判读条(秒)」）：
     * 单位跑到左侧名称里、和右侧数值隔着半个屏幕，读数时要来回找。
     */
    private String unitOf(ConfigOption opt) {
        String key = "config.lost_realm.opt." + opt.id() + ".unit";
        return I18n.exists(key) ? Component.translatable(key).getString() : "";
    }

    /** 单位占用的横向总宽（含它与数值框之间的间隙）。 */
    private int unitWidth(ConfigOption opt) {
        String u = unitOf(opt);
        return u.isEmpty() ? 0 : font.width(u) + UNIT_GAP;
    }

    private void markDirtyVisual(ConfigOption opt, Button btn) {
        // 开关的「已改动」提示靠名称变色体现,无需额外控件
    }

    private Component toggleLabel(ConfigOption opt) {
        return Component.translatable(opt.asBool()
                ? "screen.lost_realm.config.on"
                : "screen.lost_realm.config.off");
    }

    private Component switchLabel(ConfigOption opt, int value) {
        return Component.translatable(
                "config.lost_realm.opt." + opt.id() + "." + value);
    }

    // ================================================================
    // 底部按钮
    // ================================================================

    private void buildFooter() {
        int y = height - FOOTER_H + 5;
        int x = contentX();

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.lost_realm.config.save"),
                        b -> saveAll())
                .bounds(x, y, 80, 20).build());

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.lost_realm.config.cancel"),
                        b -> cancelAll())
                .bounds(x + 84, y, 80, 20).build());

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.lost_realm.config.reset_page"),
                        b -> {
                            for (ConfigOption o : current) {
                                o.resetToDefault();
                            }
                            rebuildRows();
                        })
                .bounds(x + 168, y, 90, 20).build());

        Button applyDefaults = Button.builder(
                        Component.translatable("screen.lost_realm.config.reset_all"),
                        b -> {
                            for (ConfigOption o : ConfigCatalog.options()) {
                                o.resetToDefault();
                            }
                            rebuildRows();
                        })
                .bounds(x + 262, y, 90, 20).build();
        addRenderableWidget(applyDefaults);
    }

    // ================================================================
    // 保存 / 取消
    // ================================================================

    /**
     * 把缓冲区的改动落到真实配置并持久化。
     *
     * <p>只保存<b>确实被改过</b>的 spec —— 两份都 save 会白白多写一次盘。
     */
    private void saveAll() {
        Set<ForgeConfigSpec> dirtySpecs = new LinkedHashSet<>();
        for (ConfigOption o : ConfigCatalog.options()) {
            if (o.isDirty()) {
                o.apply();
                if (o.ownerSpec() != null) {
                    dirtySpecs.add(o.ownerSpec());
                }
            }
        }
        for (ForgeConfigSpec spec : dirtySpecs) {
            spec.save();
        }
        for (ConfigOption o : ConfigCatalog.options()) {
            o.discard();
        }
        onClose();
    }

    private void cancelAll() {
        for (ConfigOption o : ConfigCatalog.options()) {
            o.discard();
        }
        onClose();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ================================================================
    // 渲染
    // ================================================================

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx);

        // ---- 面板底 ----
        int top = 24;
        gfx.fill(0, top, SIDEBAR_W, height, SIDEBAR_BG);
        gfx.fill(SIDEBAR_W, top, width, height, CONTENT_BG);
        gfx.fill(SIDEBAR_W, top, SIDEBAR_W + 1, height, BORDER);
        gfx.fill(0, top - 1, width, top, BORDER);

        // ---- 标题 ----
        gfx.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);

        // ---- 侧边栏 ----
        renderSidebar(gfx, mouseX, mouseY);

        // ---- 选项区 ----
        //
        // ⚠️ 必须开裁剪区(scissor)。
        // 滚动到中间位置时,首尾两行会「半露」在内容区边界外 ——
        // 若不裁剪,它们的名称文字会画到标题栏与页脚按钮上,
        // 看起来就像界面糊成一团(dv9 修复)。
        // 控件(Button)由原版自行绘制、不受 scissor 约束,
        // 因此 rebuildRows 那边另做了「只给完整可见的行建控件」的过滤。
        gfx.enableScissor(contentX() - PAD, contentTop(),
                width, contentBottom());
        renderContent(gfx, mouseX, mouseY);
        gfx.disableScissor();

        // ---- 控件与页脚 ----
        super.render(gfx, mouseX, mouseY, partialTick);
        renderFooterText(gfx);
    }

    private void renderSidebar(GuiGraphics gfx, int mouseX, int mouseY) {
        List<ConfigCategory> cats = ConfigCategory.sidebarOrder();
        int y = 30 - catScroll;

        // 「全部」总览固定在最上方,不参与滚动
        boolean sel = categoryId.equals(ConfigCategory.OVERVIEW.id());
        gfx.fill(0, 28, SIDEBAR_W, 28 + ROW_H, sel ? CAT_SELECTED : SIDEBAR_BG);
        gfx.drawString(font, Component.translatable("config.lost_realm.cat.overview"),
                PAD, 28 + 7, sel ? 0xFFFFFF : TEXT, false);
        y = 28 + ROW_H + 2;

        for (ConfigCategory c : cats) {
            if (y + ROW_H >= 24 && y <= height - FOOTER_H) {
                boolean selected = categoryId.equals(c.id());
                boolean hover = mouseX < SIDEBAR_W
                        && mouseY >= y && mouseY < y + ROW_H;
                int bg = selected ? CAT_SELECTED : (hover ? ROW_HOVER : SIDEBAR_BG);
                gfx.fill(0, y, SIDEBAR_W, y + ROW_H, bg);
                int count = ConfigCatalog.ofCategory(c.id()).size();
                gfx.drawString(font, c.displayName(), PAD, y + 7,
                        selected ? 0xFFFFFF : TEXT, false);
                String n = String.valueOf(count);
                gfx.drawString(font, n, SIDEBAR_W - PAD - font.width(n), y + 7,
                        TEXT_DIM, false);
            }
            y += ROW_H;
        }
    }

    private void renderContent(GuiGraphics gfx, int mouseX, int mouseY) {
        int cx = contentX();
        int cw = contentW();
        int y = contentTop() - scroll;

        for (ConfigOption opt : current) {
            if (y + ROW_H >= contentTop() && y <= contentBottom()) {
                boolean hover = mouseX >= cx && mouseX < cx + cw
                        && mouseY >= Math.max(y, contentTop())
                        && mouseY < Math.min(y + ROW_H, contentBottom());
                int bg = opt.isDirty() ? ROW_DIRTY : (hover ? ROW_HOVER : 0);
                if (bg != 0) {
                    gfx.fill(cx, Math.max(y, contentTop()), cx + cw,
                            Math.min(y + ROW_H, contentBottom()), bg);
                }
                // 名称(已改动时用醒目的琥珀色)
                gfx.drawString(font, Component.translatable(opt.langKey()),
                        cx + PAD, y + 7, opt.isDirty() ? TEXT_DIRTY : TEXT, false);

                // 数值型：数值本身由行内的输入框绘制，这里只补上右侧的单位
                if (opt.kind() == ConfigOption.Kind.STEPPER
                        || opt.kind() == ConfigOption.Kind.DOUBLE_STEPPER) {
                    String unit = unitOf(opt);
                    if (!unit.isEmpty()) {
                        int unitX = valueBoxX(cx, cw, opt) + VALUE_BOX_W + UNIT_GAP;
                        gfx.drawString(font, unit, unitX, y + 7,
                                opt.isDirty() ? TEXT_DIRTY : TEXT_DIM, false);
                    }
                } else if (opt.kind() == ConfigOption.Kind.SWITCH) {
                    // 枚举型:在当前选中按钮上加高亮边框
                    int selIdx = -1;
                    int[] vals = opt.switchValues();
                    for (int i = 0; i < vals.length; i++) {
                        if (vals[i] == opt.asInt()) { selIdx = i; break; }
                    }
                    if (selIdx >= 0) {
                        int bw = 42;
                        int bx = cx + cw - (vals.length - selIdx) * (bw + 2) - PAD;
                        gfx.renderOutline(bx - 1, y + 1, bw, ROW_H - 2, 0xFFFFC040);
                    }
                }
            }
            y += ROW_H;
        }

        // 滚动条
        renderScrollbar(gfx, y);
    }

    private void renderScrollbar(GuiGraphics gfx, int lastY) {
        int totalH = current.size() * ROW_H;
        int viewH = contentBottom() - contentTop();
        if (totalH <= viewH) {
            return;
        }
        int barX = width - 4;
        int barH = Math.max(20, viewH * viewH / totalH);
        int maxScroll = totalH - viewH;
        int barY = contentTop() + (int) ((long) scroll * (viewH - barH) / Math.max(1, maxScroll));
        gfx.fill(barX, contentTop(), barX + 3, contentBottom(), 0x40FFFFFF);
        gfx.fill(barX, barY, barX + 3, barY + barH, 0xFFAAAAAA);
    }

    private void renderFooterText(GuiGraphics gfx) {
        List<ConfigOption> all = ConfigCatalog.options();
        long dirty = all.stream().filter(ConfigOption::isDirty).count();
        Component status = dirty > 0
                ? Component.translatable("screen.lost_realm.config.unsaved", dirty)
                : Component.translatable("screen.lost_realm.config.saved");
        int color = dirty > 0 ? TEXT_DIRTY : TEXT_DIM;
        gfx.drawString(font, status, width - PAD - font.width(status),
                height - FOOTER_H + 11, color, false);
    }

    private static String trimDouble(double v) {
        if (v == Math.floor(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }

    // ================================================================
    // 输入
    // ================================================================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // 侧边栏点选
        if (mx < SIDEBAR_W && my >= 28) {
            if (my < 28 + ROW_H) {
                switchCategory(ConfigCategory.OVERVIEW.id());
                return true;
            }
            int y = 28 + ROW_H + 2 - catScroll;
            for (ConfigCategory c : ConfigCategory.sidebarOrder()) {
                if (my >= y && my < y + ROW_H) {
                    switchCategory(c.id());
                    return true;
                }
                y += ROW_H;
            }
            return true;
        }
        // ★ dv36：数值输入框是常驻控件，点击别处应当让它失焦 —— 否则输入焦点
        //   会一直粘在某个框上，之后按 E / 空格之类会继续往那个框里打字。
        if (getFocused() instanceof EditBox box && !box.isMouseOver(mx, my)) {
            setFocused(null);
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < SIDEBAR_W) {
            catScroll = scrollList(catScroll, ConfigCategory.sidebarOrder().size(),
                    height - 28 - FOOTER_H, delta);
            return true;
        }
        scroll = scrollList(scroll, current.size(),
                contentBottom() - contentTop(), -delta);
        rebuildRows();
        return true;
    }

    /** 通用滚动计算:把 offset 限制在 [0, 内容高 - 可视高]。 */
    private int scrollList(int offset, int count, int viewH, double delta) {
        int totalH = count * ROW_H;
        int max = Math.max(0, totalH - viewH);
        return Mth.clamp(offset + (int) (-delta * ROW_H), 0, max);
    }

    // ================================================================
    // 行控件容器
    // ================================================================

    private static final class RowWidget {
        final ConfigOption opt;
        final int y;
        final List<AbstractWidget> widgets = new ArrayList<>();

        RowWidget(ConfigOption opt, int y) {
            this.opt = opt;
            this.y = y;
        }
    }
}
