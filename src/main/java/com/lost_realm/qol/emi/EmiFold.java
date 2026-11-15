package com.lost_realm.qol.emi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import org.lwjgl.glfw.GLFW;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.config.SidebarSide;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.EmiScreenManager.ScreenSpace;
import dev.emi.emi.screen.EmiScreenManager.SidebarPanel;

import com.lost_realm.Medals;

/**
 * <b>EMI 自定义折叠</b> —— 玩家自己框选一组物品，它们就只占一格。
 *
 * <h2>为什么是"自定义"而不是"自动分类"</h2>
 * 之前试过按注册名后缀、按物品标签、按显示名、以及三者的交集，每种都能被举出反例：
 * {@code end_rod} / {@code blaze_rod} 同后缀但毫无关系；标签会把导线的粗细混在一起；
 * 显示名随语言变化。分类是语义问题，规则猜不全 —— 交给玩家框一下最直接。
 *
 * <h2>怎么用</h2>
 * <ul>
 *   <li><b>右 Ctrl + 右 Shift + 左键拖拽</b>：在 EMI 物品列表上拉一个框，松手即成组。</li>
 *   <li><b>左键点折叠格</b>：展开 / 收起。展开后成员紧跟在它后面。</li>
 *   <li><b>右 Ctrl + 右 Shift + 左键单击折叠格</b>：解除这一组。</li>
 * </ul>
 *
 * <p>修饰键刻意选右 Ctrl + 右 Shift：EMI 自己占用了左 Ctrl + 左键
 * （取整组 / 拖拽），抢那个组合会连带影响原有操作。
 *
 * <h2>折叠的是哪一份列表</h2>
 * 注入 {@code EmiSidebars.getStacks(SidebarType)} 的返回值 —— 那正是列表渲染用的
 * 最终列表。EMI 的索引 {@code EmiStackList.stacks} 一个字都不动，搜索和配方查找
 * 完全不受影响（搜什么都能搜到，只是浏览时被收起来了）。
 *
 * <p>之所以不注入 {@code EmiSearch.stacks}：{@code ScreenSpace.getStacks()} 在非搜索
 * 面板下走的是 {@code EmiSidebars.getStacks(type)}，跟 {@code EmiSearch} 无关 ——
 * 上一版注入错地方了，所以"没有任何效果"。
 */
public final class EmiFold {

    /** 格子边长。EMI 的列表是 18px 网格（16px 图标 + 1px 内边距）。 */
    private static final int CELL = 18;

    /** 组名 → 展开状态。 */
    private static final Set<String> EXPANDED = new HashSet<>();
    /** 组名 → 收起/展开时充当代表的那一格对应的成员 id。 */
    private static final Map<String, String> ANCHOR = new HashMap<>();
    /** 组名 → 当前实际显示的成员数（可能少于组定义，因为有些被搜索过滤掉了）。 */
    private static final Map<String, Integer> COUNT = new HashMap<>();

    /** 展开状态或组定义变化时自增，用来让折叠缓存失效。 */
    private static long stateVersion;

    private static List<? extends EmiIngredient> cacheIn;
    private static long cacheVersion = -1L;
    private static List<EmiIngredient> cacheOut;

    /** 框选：按下点、当前点。 */
    private static boolean selecting;
    private static double selX0;
    private static double selY0;
    private static double selX1;
    private static double selY1;
    /** 按下时若正好压在折叠格上，记下组名 —— 松手时位移很小就当作"解除"。 */
    private static String pressedAnchor;

    /** 组的边框配色。按组名散列取，保证同一个组每次颜色一致。 */
    private static final int[] PALETTE = {
            0xFFE85D5D, 0xFFF2A63B, 0xFFE8D44D, 0xFF7BC96F,
            0xFF4DB8C4, 0xFF5B8DEF, 0xFF9B6BEF, 0xFFE86BAE,
            0xFFC98F5B, 0xFF8FA35B, 0xFF5BBFA0, 0xFF8D8D8D,
    };

    private EmiFold() {}

    // ================================================================
    // 折叠列表
    // ================================================================

    /**
     * 把列表里的组成员收起来。只在 {@link SidebarType#INDEX}（主物品列表）上生效，
     * 收藏/合成/历史这些侧栏保持原样。
     *
     * @return 原列表（不需要折叠时原样返回，调用方据此判断是否要替换）
     */
    public static List<? extends EmiIngredient> foldList(List<? extends EmiIngredient> in,
                                                         SidebarType type) {
        if (in == null || in.size() < 2) {
            return in;
        }
        EmiFoldStore.reloadIfChanged();
        if (EmiFoldStore.isEmpty() || type != SidebarType.INDEX) {
            return in;
        }
        if (in == cacheIn && cacheVersion == stateVersion) {
            return cacheOut;
        }

        // 先建 id → 堆栈索引：展开时要把组内成员从列表里捞出来插到代表格后面
        Map<String, EmiStack> byId = new HashMap<>();
        for (EmiIngredient ing : in) {
            if (ing instanceof EmiStack s) {
                String id = EmiFoldStore.idOf(s);
                if (id != null) {
                    byId.putIfAbsent(id, s);
                }
            }
        }

        List<EmiIngredient> out = new ArrayList<>(in.size());
        Set<String> emittedId = new HashSet<>();
        Set<String> emittedGroup = new HashSet<>();
        ANCHOR.clear();
        COUNT.clear();

        for (EmiIngredient ing : in) {
            if (!(ing instanceof EmiStack s)) {
                out.add(ing);              // 复合 ingredient（标签之类）原样放行
                continue;
            }
            String id = EmiFoldStore.idOf(s);
            if (id == null) {
                out.add(ing);
                continue;
            }
            if (emittedId.contains(id)) {
                continue;                  // 这一格已经输出过了
            }
            String group = EmiFoldStore.groupOf(id);
            if (group == null) {
                emittedId.add(id);
                out.add(ing);
                continue;
            }

            boolean first = emittedGroup.add(group);
            if (!first) {
                continue;                  // 该组的代表格已经出现过，本格隐藏
            }
            emittedId.add(id);
            ANCHOR.put(group, id);
            out.add(ing);

            if (!EXPANDED.contains(group)) {
                COUNT.put(group, EmiFoldStore.membersOf(group).size());
                continue;
            }
            // 展开：按组定义顺序把成员紧接在代表格后面铺开
            int n = 1;
            for (String mid : EmiFoldStore.membersOf(group)) {
                if (mid.equals(id)) {
                    continue;
                }
                EmiStack m = byId.get(mid);
                if (m == null || !emittedId.add(mid)) {
                    continue;              // 不在当前列表里（被过滤掉了）
                }
                out.add(m);
                n++;
            }
            COUNT.put(group, n);
        }

        cacheIn = in;
        cacheVersion = stateVersion;
        cacheOut = out;
        return out;
    }

    /** 展开/收起/改组之后调用，让 EMI 重排列表。 */
    private static void refresh() {
        stateVersion++;
        cacheIn = null;
        try {
            EmiScreenManager.recalculate();
        } catch (Throwable t) {
            Medals.LOGGER.debug("[Lost Realm] EMI 重排失败", t);
        }
    }

    private static void toggle(String group) {
        if (group == null) {
            return;
        }
        if (!EXPANDED.remove(group)) {
            EXPANDED.add(group);
        }
        refresh();
    }

    // ================================================================
    // 格子命中
    // ================================================================

    private record Cell(int x, int y, EmiStack stack, String group, boolean anchor, int count) {}

    /** 屏幕坐标落在哪个格子上；不在 EMI 物品列表里返回 null。 */
    private static Cell cellAt(double mx, double my) {
        for (SidebarSide side : SidebarSide.values()) {
            if (side == SidebarSide.NONE) {
                continue;
            }
            SidebarPanel panel = EmiScreenManager.getPanelFor(side);
            if (panel == null || !panel.isVisible()) {
                continue;
            }
            Bounds b = panel.getBounds();
            if (b == null || !b.contains((int) mx, (int) my)) {
                continue;
            }
            for (ScreenSpace space : panel.getSpaces()) {
                if (space == null || space.getType() != SidebarType.INDEX) {
                    continue;
                }
                Cell c = cellIn(space, panel.page, mx, my);
                if (c != null) {
                    return c;
                }
            }
        }
        return null;
    }

    private static Cell cellIn(ScreenSpace space, int page, double mx, double my) {
        List<? extends EmiIngredient> list = space.getPage(page);
        if (list == null || list.isEmpty()) {
            return null;
        }
        int cols = Math.max(1, space.getWidth(space.pageSize));
        for (int i = 0; i < list.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int x = space.getX(col, space.pageSize);
            int y = space.getY(col, row);
            if (mx < x || mx >= x + CELL || my < y || my >= y + CELL) {
                continue;
            }
            if (!(list.get(i) instanceof EmiStack st)) {
                return null;
            }
            String id = EmiFoldStore.idOf(st);
            String group = EmiFoldStore.groupOf(id);
            boolean anchor = group != null && id != null && id.equals(ANCHOR.get(group));
            int count = group == null ? 0 : COUNT.getOrDefault(group, 0);
            return new Cell(x, y, st, group, anchor, count);
        }
        return null;
    }

    /** 矩形内的所有成员 id（按格子顺序，去重）。 */
    private static List<String> collectRect(double x0, double y0, double x1, double y1) {
        double lx = Math.min(x0, x1);
        double ly = Math.min(y0, y1);
        double hx = Math.max(x0, x1);
        double hy = Math.max(y0, y1);
        List<String> ids = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SidebarSide side : SidebarSide.values()) {
            if (side == SidebarSide.NONE) {
                continue;
            }
            SidebarPanel panel = EmiScreenManager.getPanelFor(side);
            if (panel == null || !panel.isVisible()) {
                continue;
            }
            for (ScreenSpace space : panel.getSpaces()) {
                if (space == null || space.getType() != SidebarType.INDEX) {
                    continue;
                }
                List<? extends EmiIngredient> list = space.getPage(panel.page);
                if (list == null) {
                    continue;
                }
                int cols = Math.max(1, space.getWidth(space.pageSize));
                for (int i = 0; i < list.size(); i++) {
                    int col = i % cols;
                    int row = i / cols;
                    int x = space.getX(col, space.pageSize);
                    int y = space.getY(col, row);
                    // 格子中心落在矩形内才算选中
                    double cx = x + CELL / 2.0;
                    double cy = y + CELL / 2.0;
                    if (cx < lx || cx > hx || cy < ly || cy > hy) {
                        continue;
                    }
                    if (!(list.get(i) instanceof EmiStack st)) {
                        continue;
                    }
                    String id = EmiFoldStore.idOf(st);
                    if (id != null && seen.add(id)) {
                        ids.add(id);
                    }
                }
            }
        }
        return ids;
    }

    // ================================================================
    // 事件
    // ================================================================

    /**
     * 框选修饰键：<b>右 Ctrl + 右 Shift</b>。
     *
     * <p>为什么不用左 Ctrl：EMI 自己占用了 Ctrl+左键（取整组 / 拖拽），
     * 抢它的点击会连带影响原有操作。右 Ctrl 与右 Shift 在 EMI 里没有绑定，
     * 而且要求两个键同时按下，平时误触的概率极低。
     *
     * <p>必须直接查 GLFW 的按键状态：MC 的 {@code Screen.hasControlDown()}
     * 把左右 Control 算作同一个键，区分不出来。
     */
    private static boolean isModifierDown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return false;
        }
        long win = mc.getWindow().getWindow();
        if (win == 0L) {
            return false;
        }
        return InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT_CONTROL)
                && InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    /** 按下。返回 true 表示消费掉这次点击。 */
    private static boolean onPress(double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        boolean mod = isModifierDown();
        Cell cell = cellAt(mx, my);

        if (mod && inEmiPanel(mx, my)) {
            // 右Ctrl+右Shift+左键统一从这里起手：可能是框选，也可能是点折叠格（松手时按位移区分）
            pressedAnchor = (cell != null && cell.anchor()) ? cell.group() : null;
            selecting = true;
            selX0 = mx;
            selY0 = my;
            selX1 = mx;
            selY1 = my;
            return true;                   // 吃掉，免得 EMI 顺手开始拖物品
        }
        if (cell != null && cell.anchor()) {
            toggle(cell.group());
            return true;
        }
        return false;
    }

    /** 松开。返回 true 表示消费掉。 */
    private static boolean onRelease(double mx, double my, int button) {
        if (button != 0 || !selecting) {
            return false;
        }
        selecting = false;
        selX1 = mx;
        selY1 = my;

        double dx = Math.abs(mx - selX0);
        double dy = Math.abs(my - selY0);
        if (dx < 5 && dy < 5) {
            // 位移太小 → 是一次单击
            if (pressedAnchor != null) {
                EmiFoldStore.remove(pressedAnchor);
                Medals.LOGGER.info("[Lost Realm] EMI 折叠：已解除组「{}」", pressedAnchor);
                refresh();
            }
            pressedAnchor = null;
            return true;
        }

        List<String> ids = collectRect(selX0, selY0, mx, my);
        pressedAnchor = null;
        if (ids.size() < 2) {
            Medals.LOGGER.info("[Lost Realm] EMI 折叠：框内只有 {} 个物品，至少 2 个才能成组", ids.size());
            return true;
        }
        String name = EmiFoldStore.nextName();
        EmiFoldStore.put(name, ids);
        Medals.LOGGER.info("[Lost Realm] EMI 折叠：新建组「{}」，{} 个成员", name, ids.size());
        refresh();
        return true;
    }

    /**
     * 鼠标是否落在某个 EMI 面板上。
     *
     * <p>先问 EMI 自己的命中结果；万一那个方法在某个版本里返回 null（它算的是
     * "悬停中的面板"，鼠标停在面板边角时偶尔不认），再退回直接比对各面板的边界。
     * 少了这层回退，框选会表现为"按了没反应"，很难查。
     */
    private static boolean inEmiPanel(double mx, double my) {
        try {
            if (EmiScreenManager.getHoveredPanel((int) mx, (int) my) != null) {
                return true;
            }
        } catch (Throwable ignored) {
            // 落到下面的边界比对
        }
        for (SidebarSide side : SidebarSide.values()) {
            if (side == SidebarSide.NONE) {
                continue;
            }
            try {
                SidebarPanel p = EmiScreenManager.getPanelFor(side);
                if (p == null || !p.isVisible()) {
                    continue;
                }
                Bounds b = p.getBounds();
                if (b != null && b.contains((int) mx, (int) my)) {
                    return true;
                }
            } catch (Throwable ignored) {
                // 单个面板取不到就算了
            }
        }
        return false;
    }

    // ================================================================
    // 绘制
    // ================================================================

    private static void render(ScreenEvent.Render.Post event) {
        boolean hasGroups = !EmiFoldStore.isEmpty();
        if (!hasGroups && !selecting) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        if (g == null) {
            return;
        }
        if (hasGroups) {
            drawFolders(g);
        }
        if (selecting) {
            selX1 = event.getMouseX();
            selY1 = event.getMouseY();
            drawSelection(g);
        }
    }

    private static void drawFolders(GuiGraphics g) {
        // 有 tooltip 正在显示时不画角标 —— 数字压在提示框上比看不到更烦人
        boolean noBadge = EmiScreenManager.lastStackTooltipRendered != null;
        var font = Minecraft.getInstance().font;
        for (SidebarSide side : SidebarSide.values()) {
            if (side == SidebarSide.NONE) {
                continue;
            }
            SidebarPanel panel = EmiScreenManager.getPanelFor(side);
            if (panel == null || !panel.isVisible()) {
                continue;
            }
            for (ScreenSpace space : panel.getSpaces()) {
                if (space == null || space.getType() != SidebarType.INDEX) {
                    continue;
                }
                List<? extends EmiIngredient> list = space.getPage(panel.page);
                if (list == null) {
                    continue;
                }
                int cols = Math.max(1, space.getWidth(space.pageSize));
                for (int i = 0; i < list.size(); i++) {
                    if (!(list.get(i) instanceof EmiStack st)) {
                        continue;
                    }
                    String id = EmiFoldStore.idOf(st);
                    String group = EmiFoldStore.groupOf(id);
                    if (group == null || !id.equals(ANCHOR.get(group))) {
                        continue;
                    }
                    int x = space.getX(i % cols, space.pageSize);
                    int y = space.getY(i % cols, i / cols);
                    drawFrame(g, x, y, colorFor(group));
                    if (!noBadge) {
                        drawBadge(g, font, x, y, group);
                    }
                }
            }
        }
    }

    /** 贴着图标画 1px 彩框（不占用格子外的空间，相邻格的框不会连成一片）。 */
    private static void drawFrame(GuiGraphics g, int x, int y, int color) {
        int x0 = x + 1;
        int y0 = y + 1;
        int x1 = x + CELL - 1;
        int y1 = y + CELL - 1;
        g.fill(x0, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x1, y1, color);
        g.fill(x0, y0, x0 + 1, y1, color);
        g.fill(x1 - 1, y0, x1, y1, color);
    }

    /** 右下角数量。只写字带阴影，不加底色 —— 16px 的图标经不起一个方块压上去。 */
    private static void drawBadge(GuiGraphics g, net.minecraft.client.gui.Font font,
                                  int x, int y, String group) {
        int n = COUNT.getOrDefault(group, 0);
        String text = EXPANDED.contains(group) ? "−" : String.valueOf(n);
        int w = font.width(text);
        int tx = x + CELL - 2 - w;
        int ty = y + CELL - 10;
        g.drawString(font, text, tx, ty, 0xFFFFFFFF, true);
    }

    private static void drawSelection(GuiGraphics g) {
        int x0 = (int) Math.min(selX0, selX1);
        int y0 = (int) Math.min(selY0, selY1);
        int x1 = (int) Math.max(selX0, selX1);
        int y1 = (int) Math.max(selY0, selY1);
        if (x1 - x0 < 2 || y1 - y0 < 2) {
            return;
        }
        g.fill(x0, y0, x1, y1, 0x303FB0FF);
        g.fill(x0, y0, x1, y0 + 1, 0xFF3FB0FF);
        g.fill(x0, y1 - 1, x1, y1, 0xFF3FB0FF);
        g.fill(x0, y0, x0 + 1, y1, 0xFF3FB0FF);
        g.fill(x1 - 1, y0, x1, y1, 0xFF3FB0FF);
    }

    private static int colorFor(String group) {
        return PALETTE[Math.floorMod(group.hashCode(), PALETTE.length)];
    }

    // ================================================================
    // Forge 事件订阅
    // ================================================================

    /**
     * 单独一层壳，只做"EMI 在不在"的判断再转发。
     *
     * <p>这样 EMI 没装时，{@link EmiFold} 以及它引用的 EMI 类一次都不会被加载，
     * 不会出现 NoClassDefFoundError。
     */
    @Mod.EventBusSubscriber(modid = Medals.MODID, value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Events {

        private static boolean available() {
            return ModList.get().isLoaded("emi");
        }

        @SubscribeEvent
        public static void onRender(ScreenEvent.Render.Post event) {
            if (!available()) {
                return;
            }
            try {
                EmiFold.render(event);
            } catch (Throwable t) {
                Medals.LOGGER.warn("[Lost Realm] EMI 折叠绘制失败（本次跳过）", t);
            }
        }

        @SubscribeEvent
        public static void onPress(ScreenEvent.MouseButtonPressed.Pre event) {
            if (!available()) {
                return;
            }
            try {
                if (EmiFold.onPress(event.getMouseX(), event.getMouseY(), event.getButton())) {
                    event.setCanceled(true);
                }
            } catch (Throwable t) {
                Medals.LOGGER.warn("[Lost Realm] EMI 折叠点击处理失败", t);
            }
        }

        @SubscribeEvent
        public static void onRelease(ScreenEvent.MouseButtonReleased.Pre event) {
            if (!available()) {
                return;
            }
            try {
                if (EmiFold.onRelease(event.getMouseX(), event.getMouseY(), event.getButton())) {
                    event.setCanceled(true);
                }
            } catch (Throwable t) {
                Medals.LOGGER.warn("[Lost Realm] EMI 折叠框选结算失败", t);
            }
        }
    }
}
