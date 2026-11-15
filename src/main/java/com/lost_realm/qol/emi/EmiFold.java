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
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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
    /** 按键诊断日志最多打这么多次，避免刷屏。 */
    private static int pressLogCount;

    /**
     * 待折叠的成员。<b>框选只往这里累积，不立即成组</b> ——
     * 一次框不完两排、或者想跨页挑选时，累加才是对的。
     * 按 F7 才真正落成一个组，F8 清空。
     */
    private static final java.util.LinkedHashSet<String> PENDING = new java.util.LinkedHashSet<>();

    /** 折叠生效日志只打一次，避免刷屏。 */
    private static boolean loggedFold;

    /** 最近一次点击（展开/收起）过的组，供"拆开"按钮使用。 */
    private static String lastClickedGroup;

    /** 自绘按钮。位置每帧按屏幕尺寸重算，点击时用上一帧的结果判断。 */
    private record Btn(int x, int y, int w, int h, String label, boolean enabled, Runnable action) {
        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private static final List<Btn> BUTTONS = new ArrayList<>();

    /** EMI 配置界面上那个自绘总开关的矩形；不在配置界面时为 null。 */
    private static volatile int[] CONFIG_BTN;

    private static boolean inRect(int[] r, double mx, double my) {
        return r != null && r.length == 4
                && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    /** F7 / F8 上一帧是否按下，用来做边沿检测。 */
    private static boolean keyF6Down;
    private static boolean keyF7Down;
    private static boolean keyF8Down;

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
        if (!EmiFoldStore.isEnabled() || EmiFoldStore.isEmpty() || type != SidebarType.INDEX) {
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
        if (!loggedFold) {
            loggedFold = true;
            Medals.LOGGER.info("[Lost Realm] EMI 折叠生效：{} 格 → {} 格，命中 {} 个组",
                    in.size(), out.size(), ANCHOR.size());
        }
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

    /**
     * 收集所有可能承载物品列表的面板。
     *
     * <p>两条路都试：按方位（LEFT/RIGHT/…）和按类型（INDEX）。EMI 的面板不一定
     * 挂在侧栏方位上 —— 配置不同时 {@code getPanelFor(SidebarSide)} 可能全是 null，
     * 而按类型查还能拿到。之前只走方位这一条，一旦拿不到就整条链路静默失效。
     */
    private static List<SidebarPanel> allPanels() {
        List<SidebarPanel> out = new ArrayList<>(2);
        for (SidebarSide side : SidebarSide.values()) {
            if (side == SidebarSide.NONE) {
                continue;
            }
            try {
                SidebarPanel p = EmiScreenManager.getPanelFor(side);
                if (p != null && !out.contains(p)) {
                    out.add(p);
                }
            } catch (Throwable ignored) {
                // 换下一条路
            }
        }
        try {
            SidebarPanel p = EmiScreenManager.getPanelFor(SidebarType.INDEX);
            if (p != null && !out.contains(p)) {
                out.add(p);
            }
        } catch (Throwable ignored) {
            // 拿不到就只用方位那几个
        }
        return out;
    }

    /**
     * 这个网格能不能参与折叠/框选。
     *
     * <p>只排除象棋彩蛋那个。早先限制成"必须是 INDEX"，但 EMI 在不同配置下
     * 可能把物品列表挂在别的类型上（搜索态、收藏等），条件太窄会导致
     * 鼠标明明在格子上却一个都选不中。
     */
    private static boolean usableSpace(ScreenSpace space) {
        return space != null && space.getType() != SidebarType.CHESS;
    }

    /**
     * 命中的格子。
     *
     * <p>badge 是右下角那个数字小块的矩形 —— <b>只有点这块才展开/收起</b>。
     * 之前整格都能切换，等于把"点开这个物品看配方"吃掉了；
     * 现在点格子其他地方照常交给 EMI。
     */
    private record Cell(int x, int y, EmiStack stack, String group, boolean anchor, int count,
                        int bx, int by, int bw, int bh) {
        boolean onBadge(double mx, double my) {
            return bw > 0 && mx >= bx && mx < bx + bw && my >= by && my < by + bh;
        }
    }

    /** 数字角标的矩形（x, y, w, h）。 */
    private static int[] badgeRect(int cellX, int cellY, String group) {
        var font = Minecraft.getInstance().font;
        String text = EXPANDED.contains(group) ? "−" : String.valueOf(COUNT.getOrDefault(group, 0));
        int tw = font.width(text);
        int w = Math.max(9, tw + 3);
        int h = 9;
        return new int[]{cellX + CELL - w, cellY + CELL - h, w, h};
    }

    /** 屏幕坐标落在哪个格子上；不在 EMI 物品列表里返回 null。 */
    private static Cell cellAt(double mx, double my) {
        // 不按 panel.getBounds() 预过滤：一旦那个边界和格子坐标不在同一坐标空间，
        // 就会"鼠标明明在格子上却判定不在面板内"，整条链路静默失效。
        // 格子坐标本身是精确的，直接拿它判断。
        for (SidebarPanel panel : allPanels()) {
            List<ScreenSpace> spaces;
            try {
                spaces = panel.getSpaces();
            } catch (Throwable t) {
                continue;
            }
            if (spaces == null) {
                continue;
            }
            for (ScreenSpace space : spaces) {
                if (!usableSpace(space)) {
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
        for (int i = 0; i < list.size(); i++) {
            // 坐标必须走 getRawX/getRawY：EMI 的网格**每行列数不同**（侧栏按钮会挤窄某些行），
            // 不能用 i % cols 算。这两个方法内部按每行实际列数累加得出 (col,row)。
            int x = space.getRawX(i);
            int y = space.getRawY(i);
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
            if (anchor) {
                int[] r = badgeRect(x, y, group);
                return new Cell(x, y, st, group, true, count, r[0], r[1], r[2], r[3]);
            }
            return new Cell(x, y, st, group, false, count, 0, 0, 0, 0);
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
        for (SidebarPanel panel : allPanels()) {
            List<ScreenSpace> spaces;
            try {
                spaces = panel.getSpaces();
            } catch (Throwable t) {
                continue;
            }
            if (spaces == null) {
                continue;
            }
            for (ScreenSpace space : spaces) {
                if (!usableSpace(space)) {
                    continue;
                }
                List<? extends EmiIngredient> list = space.getPage(panel.page);
                if (list == null) {
                    continue;
                }
                for (int i = 0; i < list.size(); i++) {
                    int x = space.getRawX(i);
                    int y = space.getRawY(i);
                    // 改为"矩形与格子相交即算"：原来要求格子中心落在框内，
                    // 手稍微差一点就漏掉边上的那一排，手感偏紧。
                    if (x + CELL <= lx || x >= hx || y + CELL <= ly || y >= hy) {
                        continue;
                    }
                    if (!(list.get(i) instanceof EmiStack st)) {
                        continue;
                    }
                    String id = EmiFoldStore.idOf(st);
                    if (id == null) {
                        continue;
                    }
                    // 已折叠的代表格不参与框选：它代表的是整组，
                    // 把"代表"选进去语义混乱，而且它会连带影响原来那一组
                    String owner = EmiFoldStore.groupOf(id);
                    if (owner != null && id.equals(ANCHOR.get(owner))) {
                        continue;
                    }
                    if (seen.add(id)) {
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

    /** 某个物理键是否按下（区分左右）。 */
    private static boolean key(int glfwKey) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return false;
        }
        long win = mc.getWindow().getWindow();
        return win != 0L && InputConstants.isKeyDown(win, glfwKey);
    }

    /**
     * 框选修饰键：<b>Ctrl + Shift</b>（左右不限）。
     *
     * <p>为什么不单用 Ctrl：EMI 自己占用了 Ctrl+左键（取整组 / 拖拽），
     * 抢它会连带影响原有操作。再加上 Shift 之后这个组合没被占用。
     *
     * <p>为什么不再死认右 Ctrl：右 Ctrl 在笔记本键盘和紧凑配列上常常不存在
     * 或者位置很别扭，只认右边反而更难点。Ctrl+Shift 本身不会和 EMI 冲突，
     * 所以不分左右更实用。
     *
     * <p>必须直接查 GLFW：MC 的 {@code Screen.hasControlDown()} 把左右 Control
     * 视为同一个键，虽然这里不限左右，但诊断日志要区分是哪一个。
     */
    private static boolean isModifierDown() {
        return (key(GLFW.GLFW_KEY_LEFT_CONTROL) || key(GLFW.GLFW_KEY_RIGHT_CONTROL))
                && (key(GLFW.GLFW_KEY_LEFT_SHIFT) || key(GLFW.GLFW_KEY_RIGHT_SHIFT));
    }

    /**
     * 诊断用：把四个修饰键的真实状态打出来。
     *
     * <p>"按了没反应"最难查的就是这一步 —— 到底是键没被识别，还是事件没走到。
     * 只在前 20 次按下时记录，不会刷屏。
     */
    public static void logModifierState() {
        if (pressLogCount++ > 20) {
            return;
        }
        Medals.LOGGER.info("[Lost Realm] EMI 折叠 · 按键诊断：左Ctrl={} 右Ctrl={} 左Shift={} 右Shift={} → 组合成立={}",
                key(GLFW.GLFW_KEY_LEFT_CONTROL), key(GLFW.GLFW_KEY_RIGHT_CONTROL),
                key(GLFW.GLFW_KEY_LEFT_SHIFT), key(GLFW.GLFW_KEY_RIGHT_SHIFT),
                isModifierDown());
    }

    /** 鼠标当前的 GUI 坐标。 */
    private static double[] guiMouse() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || mc.mouseHandler == null) {
            return null;
        }
        var w = mc.getWindow();
        // xpos()/ypos() 是窗口像素坐标，界面缩放不是 100% 时必须换算成 GUI 坐标
        double sx = mc.mouseHandler.xpos() * (double) w.getGuiScaledWidth()
                / Math.max(1, w.getScreenWidth());
        double sy = mc.mouseHandler.ypos() * (double) w.getGuiScaledHeight()
                / Math.max(1, w.getScreenHeight());
        return new double[]{sx, sy};
    }

    /** 左键按下。返回 true 表示这次按下已被接管。 */
    public static boolean beginSelection() {
        double[] p = guiMouse();
        if (p == null) {
            return false;
        }
        double mx = p[0];
        double my = p[1];

        // ① 配置界面的总开关。功能关着的时候也必须能点开，所以放在最前面、不看 isEnabled。
        if (inRect(CONFIG_BTN, mx, my)) {
            boolean now = EmiFoldStore.toggleEnabled();
            if (!now) {
                PENDING.clear();
                lastClickedGroup = null;
            }
            stateVersion++;
            cacheIn = null;
            Medals.LOGGER.info("[Lost Realm] EMI 物品折叠已{}", now ? "开启" : "关闭");
            try {
                EmiScreenManager.recalculate();
            } catch (Throwable ignored) {
                // 界面还没建好时忽略
            }
            return true;
        }

        if (!EmiFoldStore.isEnabled()) {
            return false;
        }

        // ② 底部自绘按钮
        for (Btn b : BUTTONS) {
            if (b.hit(mx, my)) {
                if (b.enabled()) {
                    b.action().run();
                }
                return true;
            }
        }

        // ③ 折叠格右下角那个数字小块：只有点它才展开/收起。
        //    点格子其他地方一律放行，让 EMI 正常取物品 / 看配方。
        Cell cell = null;
        try {
            cell = cellAt(mx, my);
        } catch (Throwable ignored) {
            // 命中失败就当没点中
        }
        if (cell != null && cell.anchor() && cell.onBadge(mx, my)) {
            toggle(cell.group());
            lastClickedGroup = cell.group();
            return true;
        }

        // ④ 框选起手：按住 Ctrl+Shift，或者开了"常开框选"模式
        if (!isModifierDown() && !EmiFoldStore.isStickySelect()) {
            return false;
        }
        if (!inEmiPanel(mx, my)) {
            logPanelDiag(mx, my);
            return false;
        }
        pressedAnchor = (cell != null && cell.anchor()) ? cell.group() : null;
        selecting = true;
        selX0 = mx;
        selY0 = my;
        selX1 = mx;
        selY1 = my;
        Medals.LOGGER.info("[Lost Realm] EMI 折叠：开始框选 @({}, {}){}", (int) mx, (int) my,
                pressedAnchor == null ? "" : "，压住折叠格「" + pressedAnchor + "」");
        return true;
    }

    /** 左键松开：结算框选，或按位移判定为单击。返回 true 表示这次松开已被接管。 */
    public static boolean endSelection() {
        if (!selecting) {
            return false;
        }
        selecting = false;
        double[] p = guiMouse();
        double mx = p == null ? selX0 : p[0];
        double my = p == null ? selY0 : p[1];
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
            } else {
                Medals.LOGGER.info("[Lost Realm] EMI 折叠：单击（未拖动），没有成组");
            }
            pressedAnchor = null;
            return true;
        }

        List<String> ids = collectRect(selX0, selY0, mx, my);
        pressedAnchor = null;
        if (ids.isEmpty()) {
            Medals.LOGGER.info("[Lost Realm] EMI 折叠：框内没有物品");
            return true;
        }
        int before = PENDING.size();
        PENDING.addAll(ids);
        Medals.LOGGER.info("[Lost Realm] EMI 折叠：本次框住 {} 个，累计已选 {} 个（新增 {}）· F7 折叠 / F8 清空",
                ids.size(), PENDING.size(), PENDING.size() - before);
        return true;
    }

    /** F7：把当前选中的物品真正确认成一个折叠组。 */
    private static void confirmFold() {
        if (PENDING.size() < 2) {
            Medals.LOGGER.info("[Lost Realm] EMI 折叠：只选了 {} 个，至少 2 个才能成组", PENDING.size());
            return;
        }
        List<String> ids = new ArrayList<>(PENDING);
        String name = EmiFoldStore.nextName();
        EmiFoldStore.put(name, ids);
        PENDING.clear();
        Medals.LOGGER.info("[Lost Realm] EMI 折叠：新建组「{}」，{} 个成员", name, ids.size());
        refresh();
    }

    /** 拆开最近点击过的那个组。 */
    private static void dissolveLast() {
        String g = lastClickedGroup;
        if (g == null) {
            return;
        }
        EmiFoldStore.remove(g);
        EXPANDED.remove(g);
        lastClickedGroup = null;
        Medals.LOGGER.info("[Lost Realm] EMI 折叠：已拆开组「{}」", g);
        refresh();
    }

    /**
     * 重建按钮列表。每帧算一次，因为位置依赖当前屏幕尺寸。
     *
     * <p>只在功能开启、且"有待折叠项"或"刚点过某个组"时出现 ——
     * 平时不占地方。
     */
    private static void rebuildButtons() {
        BUTTONS.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.screen == null || !EmiFoldStore.isEnabled()) {
            return;
        }
        boolean hasPending = !PENDING.isEmpty();
        boolean hasGroup = lastClickedGroup != null
                && EmiFoldStore.groupNames().contains(lastClickedGroup);
        if (!hasPending && !hasGroup) {
            return;
        }
        final int bw = 66;
        final int bh = 18;
        final int gap = 6;
        int count = (hasPending ? 2 : 0) + (hasGroup ? 1 : 0);
        int total = count * bw + (count - 1) * gap;
        int x = (mc.screen.width - total) / 2;
        int y = mc.screen.height - 46;

        if (hasPending) {
            BUTTONS.add(new Btn(x, y, bw, bh, "折叠 " + PENDING.size(),
                    PENDING.size() >= 2, EmiFold::confirmFold));
            x += bw + gap;
        }
        if (hasGroup) {
            BUTTONS.add(new Btn(x, y, bw, bh, "拆开", true, EmiFold::dissolveLast));
            x += bw + gap;
        }
        if (hasPending) {
            BUTTONS.add(new Btn(x, y, bw, bh, "清空", true, EmiFold::clearPending));
        }
    }

    private static void drawButtons(GuiGraphics g) {
        if (BUTTONS.isEmpty()) {
            return;
        }
        var font = Minecraft.getInstance().font;
        for (Btn b : BUTTONS) {
            g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(),
                    b.enabled() ? 0xE0202030 : 0x90181820);
            int border = b.enabled() ? 0xFF55E0FF : 0xFF505060;
            g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + 1, border);
            g.fill(b.x(), b.y() + b.h() - 1, b.x() + b.w(), b.y() + b.h(), border);
            g.fill(b.x(), b.y(), b.x() + 1, b.y() + b.h(), border);
            g.fill(b.x() + b.w() - 1, b.y(), b.x() + b.w(), b.y() + b.h(), border);
            int tw = font.width(b.label());
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 300.0F);
            g.drawString(font, b.label(), b.x() + (b.w() - tw) / 2, b.y() + (b.h() - 8) / 2,
                    b.enabled() ? 0xFFFFFFFF : 0xFF909098, true);
            g.pose().popPose();
        }
    }

    /** F8：清空当前选择（不动已有的组）。 */
    private static void clearPending() {
        int n = PENDING.size();
        PENDING.clear();
        Medals.LOGGER.info("[Lost Realm] EMI 折叠：已清空 {} 个待折叠项", n);
    }

    /**
     * 每帧检查 F7 / F8 的边沿。
     *
     * <p>为什么不监听 {@code ScreenEvent.KeyPressed}：EMI 对键盘的处理也在很前面，
     * 而且搜索框有焦点时按键会被它消费掉。直接查 GLFW 的物理键状态最省事，
     * 也不会被任何模组吞掉。
     */
    private static void pollConfirmKeys() {
        boolean f6 = key(GLFW.GLFW_KEY_F6);
        boolean f7 = key(GLFW.GLFW_KEY_F7);
        boolean f8 = key(GLFW.GLFW_KEY_F8);
        if (f6 && !keyF6Down) {
            boolean now = EmiFoldStore.toggleStickySelect();
            Medals.LOGGER.info("[Lost Realm] 框选方式：{}",
                    now ? "常开（直接左键拖拽）" : "按住 Ctrl+Shift 拖拽");
        }
        if (f7 && !keyF7Down && !PENDING.isEmpty()) {
            confirmFold();
        }
        if (f8 && !keyF8Down && !PENDING.isEmpty()) {
            clearPending();
        }
        keyF6Down = f6;
        keyF7Down = f7;
        keyF8Down = f8;
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
            // 落到下面的判定
        }
        for (SidebarSide side : SidebarSide.values()) {
            if (side == SidebarSide.NONE) {
                continue;
            }
            try {
                SidebarPanel p = EmiScreenManager.getPanelFor(side);
                if (p == null) {
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
        // 最后一道兜底：只要鼠标压在某个格子上就算在面板内。
        // 前两层都依赖 EMI 自己的边界数据，一旦那套坐标空间和应用的对不上，
        // 这一层还能靠精确的格子坐标救回来。
        return cellAt(mx, my) != null;
    }

    /**
     * 走到"组合键成立但没起框"这一步时打一条完整的面板诊断。
     *
     * <p>这一层如果静默失败，外面看到的就是"按了没反应"，没有任何线索。
     * 把坐标换算的中间量、每个面板的可见性与边界一次性打出来，一眼能看出是哪一环。
     */
    private static void logPanelDiag(double mx, double my) {
        if (pressLogCount > 40) {
            return;
        }
        StringBuilder sb = new StringBuilder("[Lost Realm] EMI 折叠 · 面板诊断：");
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getWindow() != null && mc.mouseHandler != null) {
            var w = mc.getWindow();
            sb.append("xpos=").append(mc.mouseHandler.xpos())
                    .append(" ypos=").append(mc.mouseHandler.ypos())
                    .append(" screen=").append(w.getScreenWidth()).append('x').append(w.getScreenHeight())
                    .append(" gui=").append(w.getGuiScaledWidth()).append('x').append(w.getGuiScaledHeight())
                    .append(" → guiMouse=(").append((int) mx).append(',').append((int) my).append(") | ");
        }
        for (SidebarSide side : SidebarSide.values()) {
            if (side == SidebarSide.NONE) {
                continue;
            }
            try {
                SidebarPanel p = EmiScreenManager.getPanelFor(side);
                sb.append(side).append('=');
                if (p == null) {
                    sb.append("null ");
                    continue;
                }
                Bounds b = p.getBounds();
                sb.append("vis:").append(p.isVisible()).append(" bounds:")
                        .append(b == null ? "null"
                                : (b.left() + "," + b.top() + " " + b.width() + "x" + b.height()));
                List<ScreenSpace> ss = p.getSpaces();
                sb.append(" spaces:").append(ss == null ? -1 : ss.size());
                if (ss != null) {
                    for (ScreenSpace sp : ss) {
                        if (sp == null) {
                            continue;
                        }
                        // 把每个 space 的"第 0 个格子坐标"也打出来，
                        // 好和上面的 guiMouse 对比是不是同一个坐标空间
                        sb.append(" [").append(sp.getType())
                                .append(" 首格:").append(sp.getRawX(0)).append(',').append(sp.getRawY(0))
                                .append(" th:").append(sp.th)
                                .append(" pageSize:").append(sp.pageSize).append(']');
                    }
                }
                sb.append(' ');
            } catch (Throwable t) {
                sb.append(side).append("=异常 ");
            }
        }
        sb.append("| allPanels:").append(allPanels().size());
        Medals.LOGGER.info(sb.toString());
    }

    // ================================================================
    // 绘制
    // ================================================================

    private static void render(ScreenEvent.Render.Post event) {
        pollConfirmKeys();
        GuiGraphics g = event.getGuiGraphics();
        if (g == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;

        // EMI 的配置界面：只画那个总开关，别的一概不画
        if (screen != null
                && "dev.emi.emi.screen.ConfigScreen".equals(screen.getClass().getName())) {
            BUTTONS.clear();
            drawConfigSwitch(g, screen);
            return;
        }
        CONFIG_BTN = null;

        // 不在 EMI 面板上就彻底收手。
        // Render.Post 是全局事件 —— 不判断这一下，边框会画到主菜单、
        // 聊天框、以及所有别的 GUI 上。
        if (allPanels().isEmpty()) {
            BUTTONS.clear();
            return;
        }

        rebuildButtons();
        boolean hasGroups = !EmiFoldStore.isEmpty();
        boolean hasPending = !PENDING.isEmpty();
        if (!hasGroups && !hasPending && !selecting && BUTTONS.isEmpty()) {
            return;
        }
        if (hasPending) {
            drawPending(g);
        }
        if (hasGroups) {
            drawFolders(g);
        }
        if (selecting) {
            selX1 = event.getMouseX();
            selY1 = event.getMouseY();
            drawSelection(g);
        }
        if (!BUTTONS.isEmpty()) {
            drawButtons(g);
        }
        if (hasPending) {
            drawHint(g);
        }
    }

    /** EMI 配置界面左下角的总开关。 */
    private static void drawConfigSwitch(GuiGraphics g, Screen screen) {
        var font = Minecraft.getInstance().font;
        boolean on = EmiFoldStore.isEnabled();
        String label = "[遗落境] 物品折叠：" + (on ? "开" : "关");
        int w = font.width(label) + 10;
        int h = 16;
        int x = 6;
        int y = screen.height - 22;
        CONFIG_BTN = new int[]{x, y, w, h};
        g.fill(x, y, x + w, y + h, 0xE0202030);
        int border = on ? 0xFF55E0FF : 0xFF707080;
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w - 1, y, x + w, y + h, border);
        g.drawString(font, label, x + 5, y + 4, on ? 0xFFFFFFFF : 0xFF909098, true);
    }

    /** 待折叠的格子：半透明亮青底 + 亮青框，和已成的组（彩色框）区分开。 */
    private static void drawPending(GuiGraphics g) {
        for (SidebarPanel panel : allPanels()) {
            List<ScreenSpace> spaces;
            try {
                spaces = panel.getSpaces();
            } catch (Throwable t) {
                continue;
            }
            if (spaces == null) {
                continue;
            }
            for (ScreenSpace space : spaces) {
                if (!usableSpace(space)) {
                    continue;
                }
                List<? extends EmiIngredient> list;
                try {
                    list = space.getPage(panel.page);
                } catch (Throwable t) {
                    continue;
                }
                if (list == null) {
                    continue;
                }
                for (int i = 0; i < list.size(); i++) {
                    if (!(list.get(i) instanceof EmiStack st)) {
                        continue;
                    }
                    String id = EmiFoldStore.idOf(st);
                    if (id == null || !PENDING.contains(id)) {
                        continue;
                    }
                    int x = space.getRawX(i);
                    int y = space.getRawY(i);
                    g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0x5055E0FF);
                    drawFrame(g, x, y, 0xFF55E0FF);
                }
            }
        }
    }

    /** 屏幕底部提示，告诉玩家当前选了多少、按什么确认。 */
    private static void drawHint(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.screen == null) {
            return;
        }
        String tip = "已选 " + PENDING.size() + " 个物品　·　F7 折叠　·　F8 清空　·　"
                + (EmiFoldStore.isStickySelect() ? "框选常开（F6 关）" : "F6 开常开框选");
        var font = mc.font;
        int w = font.width(tip);
        int x = (mc.screen.width - w) / 2;
        int y = mc.screen.height - 66;
        g.fill(x - 5, y - 4, x + w + 5, y + 10, 0xC8000000);
        g.drawString(font, tip, x, y, 0xFFFFE066, true);
    }

    private static void drawFolders(GuiGraphics g) {
        // 有 tooltip 正在显示时不画角标 —— 数字压在提示框上比看不到更烦人
        boolean noBadge = EmiScreenManager.lastStackTooltipRendered != null;
        for (SidebarPanel panel : allPanels()) {
            List<ScreenSpace> spaces;
            try {
                spaces = panel.getSpaces();
            } catch (Throwable t) {
                continue;
            }
            if (spaces == null) {
                continue;
            }
            for (ScreenSpace space : spaces) {
                if (!usableSpace(space)) {
                    continue;
                }
                List<? extends EmiIngredient> list;
                try {
                    list = space.getPage(panel.page);
                } catch (Throwable t) {
                    continue;
                }
                if (list == null) {
                    continue;
                }
                // 展开出来的成员：收集坐标，最后统一画一个大框，
                // 而不是每个物品各套一个小框（那样一展开满屏碎格子）
                Map<String, List<int[]>> spread = new java.util.HashMap<>();
                for (int i = 0; i < list.size(); i++) {
                    if (!(list.get(i) instanceof EmiStack st)) {
                        continue;
                    }
                    String id = EmiFoldStore.idOf(st);
                    String group = EmiFoldStore.groupOf(id);
                    if (group == null) {
                        continue;
                    }
                    if (id.equals(ANCHOR.get(group))) {
                        int x = space.getRawX(i);
                        int y = space.getRawY(i);
                        drawFrame(g, x, y, colorFor(group));
                        if (!noBadge) {
                            drawBadge(g, x, y, group);
                        }
                    } else if (EXPANDED.contains(group)) {
                        spread.computeIfAbsent(group, k -> new ArrayList<>())
                                .add(new int[]{space.getRawX(i), space.getRawY(i)});
                    }
                }
                for (Map.Entry<String, List<int[]>> e : spread.entrySet()) {
                    drawSpreadBox(g, e.getValue(), colorFor(e.getKey()));
                }
            }
        }
    }

    /**
     * 展开出来的那一坨成员，用一个整体大框围起来。
     *
     * <p>不含代表格 —— 代表格自己有小框和数字，套进大框反而看不清楚。
     * 用了 2px，比代表格那圈 1px 稍重，视觉上明确"这是一整块"。
     */
    private static void drawSpreadBox(GuiGraphics g, List<int[]> cells, int color) {
        if (cells == null || cells.isEmpty()) {
            return;
        }
        int x0 = Integer.MAX_VALUE;
        int y0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int y1 = Integer.MIN_VALUE;
        for (int[] c : cells) {
            x0 = Math.min(x0, c[0]);
            y0 = Math.min(y0, c[1]);
            x1 = Math.max(x1, c[0] + CELL);
            y1 = Math.max(y1, c[1] + CELL);
        }
        x0 += 1;
        y0 += 1;
        x1 -= 1;
        y1 -= 1;
        for (int t = 0; t < 2; t++) {
            g.fill(x0 + t, y0 + t, x1 - t, y0 + t + 1, color);
            g.fill(x0 + t, y1 - t - 1, x1 - t, y1 - t, color);
            g.fill(x0 + t, y0 + t, x0 + t + 1, y1 - t, color);
            g.fill(x1 - t - 1, y0 + t, x1 - t, y1 - t, color);
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
    /**
     * 右下角的数字，带一个小方框。
     *
     * <p>这个方框不只是装饰 —— 它是<b>唯一能展开/收起的热区</b>。
     * 之前整格都能切换，导致折叠后的第一个物品点不开、看不了配方。
     */
    private static void drawBadge(GuiGraphics g, int x, int y, String group) {
        var font = Minecraft.getInstance().font;
        int[] r = badgeRect(x, y, group);
        String text = EXPANDED.contains(group) ? "−" : String.valueOf(COUNT.getOrDefault(group, 0));
        int tw = font.width(text);
        g.pose().pushPose();
        // 抬 z：EMI 的物品是批量绘制、最后统一 flush 的，
        // 光靠"事件更靠后"压不住，数字会钻到图标底下
        g.pose().translate(0.0F, 0.0F, 200.0F);
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0xF0101018);
        g.fill(r[0], r[1], r[0] + r[2], r[1] + 1, 0xFF55E0FF);
        g.fill(r[0], r[1] + r[3] - 1, r[0] + r[2], r[1] + r[3], 0xFF55E0FF);
        g.fill(r[0], r[1], r[0] + 1, r[1] + r[3], 0xFF55E0FF);
        g.fill(r[0] + r[2] - 1, r[1], r[0] + r[2], r[1] + r[3], 0xFF55E0FF);
        g.drawString(font, text, r[0] + (r[2] - tw) / 2, r[1] + 1, 0xFFFFFFFF, true);
        g.pose().popPose();
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

        // 按下/松开不在这里监听。EMI 是在 MouseHandler.onPress 里就把事件 cancel 掉的，
        // Screen.mouseClicked 根本不会被调用，所以 ScreenEvent.MouseButtonPressed 永不触发。
        // 那两个动作由 MouseHandlerMixin 在同一层、更早地拦下来。
    }
}
