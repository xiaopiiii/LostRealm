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
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
import dev.emi.emi.registry.EmiStackList;
import dev.emi.emi.screen.ConfigScreen;
import dev.emi.emi.screen.EmiScreenBase;
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
 *   <li><b>Ctrl + Shift + 左键拖拽</b>（F6 可切成常开）：在 EMI 列表上拉框，松手累积选中；
 *       F7 成组，F8 清空。</li>
 *   <li><b>左键点折叠格右下角的角标</b>：展开 / 收起。角标上的 {@code <} 表示收起、
 *       {@code >} 表示已展开，展开后成员紧跟在代表格后面，整组连成一条外框。</li>
 *   <li><b>Ctrl + Shift + 左键单击折叠格</b>：解除这一组。</li>
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

    /** 展开状态或组定义变化时自增，用来让折叠缓存失效。 */
    private static long stateVersion;

    /** 有待结算的重排（见 {@link #flushPendingRefresh()}）。 */
    private static boolean pendingRefresh;

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

    /** 每个组首次展开时打一条诊断，避免刷屏。 */
    private static final Set<String> loggedExpand = new HashSet<>();

    /** 全量索引缓存：当前页找不到成员时用它捞。 */
    private static List<EmiStack> globalSrc;
    private static Map<String, EmiStack> globalIndex;

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
        if (EmiFoldStore.isEmpty() || type != SidebarType.INDEX) {
            return in;
        }
        if (hasQuery()) {
            return in;                     // 搜索时不折，否则被收起的成员搜不到
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
            out.add(ing);

            if (!EXPANDED.contains(group)) {
                continue;
            }
            // 展开：按组定义顺序把成员紧接在代表格后面铺开
            int n = 1;
            for (String mid : EmiFoldStore.membersOf(group)) {
                if (mid.equals(id)) {
                    continue;
                }
                EmiStack m = byId.get(mid);
                if (m == null) {
                    // 当前页没有（可能被搜索过滤了），再去全量索引里捞一次。
                    // 少了这一步，跨版本存活下来的组会"展开后什么都不显示"。
                    m = findGlobal(mid);
                }
                if (m == null || !emittedId.add(mid)) {
                    continue;
                }
                out.add(m);
                n++;
            }
            if (loggedExpand.add(group)) {
                Medals.LOGGER.info("[Lost Realm] 展开组「{}」：定义 {} 个成员，实际铺开 {} 个",
                        group, EmiFoldStore.membersOf(group).size(), n);
            }
        }

        cacheIn = in;
        cacheVersion = stateVersion;
        cacheOut = out;
        if (!loggedFold) {
            loggedFold = true;
            Medals.LOGGER.info("[Lost Realm] EMI 折叠生效：{} 格 → {} 格", in.size(), out.size());
        }
        return out;
    }

    /**
     * 从全量索引里找一个堆栈。
     *
     * <p>折叠列表是"当前页/当前搜索"的子集，组里某些成员可能不在其中。
     * 用全量索引兜底，展开时就不会缺件。
     */
    private static EmiStack findGlobal(String id) {
        try {
            List<EmiStack> src = EmiStackList.stacks;
            if (src == null) {
                return null;
            }
            if (src != globalSrc) {
                globalSrc = src;
                globalIndex = null;
            }
            if (globalIndex == null) {
                Map<String, EmiStack> m = new HashMap<>();
                for (EmiStack st : src) {
                    String k = EmiFoldStore.idOf(st);
                    if (k != null) {
                        m.putIfAbsent(k, st);
                    }
                }
                globalIndex = m;
            }
            return globalIndex.get(id);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 折叠编辑开关被改过（EMI 配置界面里那一行点的）。
     *
     * <p>关掉时把编辑状态收干净：框选中的、待成组的、刚点过的组，全部作废，
     * 免得留个"半截编辑"在那儿。
     */
    public static void editToggled() {
        boolean on = EmiFoldStore.isEditEnabled();
        if (!on) {
            PENDING.clear();
            selecting = false;
            lastClickedGroup = null;
        }
        refresh();
        Medals.LOGGER.info("[Lost Realm] EMI 折叠编辑已{}（配置界面）", on ? "开启" : "关闭");
    }

    /** 展开/收起/改组之后调用，让 EMI 重排列表。 */
    private static void refresh() {
        stateVersion++;
        cacheIn = null;
        pendingRefresh = true;
        try {
            EmiScreenManager.recalculate();
        } catch (Throwable t) {
            Medals.LOGGER.debug("[Lost Realm] EMI 重排失败", t);
        }
    }

    /**
     * 下一帧再排一次。
     *
     * <p>{@link #refresh()} 是在鼠标事件里调用的，那一刻 EMI 可能正处在
     * 自己的点击处理中间（面板还没结算完），{@code recalculate()} 排出来的东西
     * 会被它随后的处理覆盖掉 —— 表现就是"点了没反应、再点还是没反应"。
     * 渲染前那一帧 EMI 已经收工，这时候再排一次就一定生效。
     */
    private static void flushPendingRefresh() {
        if (!pendingRefresh) {
            return;
        }
        pendingRefresh = false;
        try {
            EmiScreenManager.recalculate();
        } catch (Throwable t) {
            Medals.LOGGER.debug("[Lost Realm] EMI 延迟重排失败", t);
        }
    }

    private static void toggle(String group) {
        if (group == null) {
            return;
        }
        boolean open;
        if (!EXPANDED.remove(group)) {
            EXPANDED.add(group);
            open = true;
        } else {
            open = false;
        }
        refresh();
        Medals.LOGGER.info("[Lost Realm] EMI 折叠：{}组「{}」，{} 个成员",
                open ? "展开" : "收起", group, EmiFoldStore.membersOf(group).size());
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
    /**
     * 只有<b>物品索引</b>这一种空间参与折叠：角标、边框、框选、展开全都只认它。
     *
     * <p>EMI 的侧栏分页不止一种：收藏夹、查询记录、可合成、历史……它们共用同一套
     * 格子坐标，但列表来源不同。之前这里只排除了 CHESS，于是"查询记录"里也画出角标、
     * 点了还真能点开 —— 可那页的列表根本没折过（折叠只作用于 INDEX），
     * 结果就是点了个寂寞：数字变了，物品一件没铺开。
     */
    private static boolean usableSpace(ScreenSpace space) {
        return space != null && space.getType() == SidebarType.INDEX && !hasQuery();
    }

    /**
     * 命中的格子。
     *
     * <p>badge 是右下角那个小角标的矩形 —— <b>只有点这块才展开/收起</b>。
     * 之前整格都能切换，等于把"点开这个物品看配方"吃掉了；
     * 现在点格子其他地方照常交给 EMI。
     */
    private record Cell(int x, int y, EmiStack stack, String group, boolean anchor,
                        int bx, int by, int bw, int bh) {
        boolean onBadge(double mx, double my) {
            return bw > 0 && mx >= bx && mx < bx + bw && my >= by && my < by + bh;
        }
    }

    /** 列表里第 i 格所属的组名（不在任何组里返回 null）。 */
    private static String groupAt(List<? extends EmiIngredient> list, int i) {
        if (list == null || i < 0 || i >= list.size()) {
            return null;
        }
        if (!(list.get(i) instanceof EmiStack st)) {
            return null;
        }
        return EmiFoldStore.groupOf(EmiFoldStore.idOf(st));
    }

    /**
     * 第 i 格是不是它所属组的代表格 —— <b>该组在当前列表里第一次出现的那一格</b>。
     *
     * <p>这里每次现算，不再依赖 {@code foldList} 留下的缓存表。
     * 缓存表只在折叠真正跑过一趟之后才有内容：万一某次列表没经过折叠
     * （比如合成表界面里走的是另一条取列表的路），角标就点不动、
     * 表现成"折叠起来了却展开不了"。现算就没有这个空窗期。
     */
    private static boolean isAnchorAt(List<? extends EmiIngredient> list, int i, String group) {
        if (group == null) {
            return false;
        }
        for (int j = 0; j < i; j++) {
            if (group.equals(groupAt(list, j))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 该组的物品总数。
     *
     * <p>不用"这一屏铺开了几格"来算：EMI 是分页的，一页只给一部分格子，
     * 跨页的组会显示成 3、5 这种随翻页乱跳的数。角标要说的是"这组一共几件"。
     */
    private static int sizeOf(String group) {
        return group == null ? 0 : EmiFoldStore.membersOf(group).size();
    }

    /**
     * 角标矩形（<b>同时是热区</b>）。
     *
     * <p>尺寸按数字实际宽度算，紧贴格子右下角，不再固定 8×8 —— 固定尺寸
     * 对一位数是"一块大方块糊在图标上"，对三位数又不够宽。
     *
     * <p>热区比画出来的框大一圈（下面 {@link #HIT}），但仍然<b>从格子右下角
     * 往里 10px 起</b>：18×18 格的图标中心在 (9,9)，从 10 起就永远碰不到中心，
     * 点物品本体不会被当成"展开/收起"。
     */
    private static final float BADGE_SCALE = 0.5F;
    private static final int BADGE_H = 5;
    private static final int BADGE_HIT = 10;

    /**
     * 角标上写什么：数字 + 一个方向符 —— 收起 {@code <}，展开 {@code >}。
     *
     * <p>直接当字符串写，让 MC 字体去排版：字距、笔画粗细都是原版的，
     * 三位数也不会像点阵那样一格一格撑开。
     */
    private static String badgeText(String group, int count) {
        return Math.max(count, 0) + (EXPANDED.contains(group) ? ">" : "<");
    }

    /**
     * 角标的位置。整串按 {@link #BADGE_SCALE} 缩放后<b>右端贴着图标右缘、
     * 底边贴着图标下缘</b>，能塞多靠角就多靠角。
     *
     * <p>返回 {@code {bx, by, bw, bh, 热区x, 热区y, 热区w, 热区h}}。
     * 热区比字大一丁点，但仍然<b>从格子右下角往里 10px 起</b>：18×18 格的
     * 图标中心在 (9,9)，从 10 起就永远碰不到中心，点物品本体不会被当成展开/收起。
     */
    private static int[] badgeRect(int cellX, int cellY, String group, int count) {
        var font = Minecraft.getInstance().font;
        int bw = Math.max(2, Math.round(font.width(badgeText(group, count)) * BADGE_SCALE));
        int bx = cellX + CELL - 1 - bw;                 // 右端 = 图标右缘 x+17
        int by = cellY + CELL - 2 - (BADGE_H - 1);      // 底边 = 图标下缘 y+16
        int hx = Math.min(bx, cellX + BADGE_HIT);
        int hy = Math.min(by, cellY + BADGE_HIT);
        int hw = Math.max(bx + bw, cellX + CELL) - hx;
        int hh = Math.max(by + BADGE_H, cellY + CELL) - hy;
        return new int[]{bx, by, bw, BADGE_H, hx, hy, hw, hh};
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
            String group = groupAt(list, i);
            boolean anchor = isAnchorAt(list, i, group);
            if (anchor) {
                int count = sizeOf(group);
                int[] r = badgeRect(x, y, group, count);
                return new Cell(x, y, st, group, true, r[4], r[5], r[6], r[7]);
            }
            return new Cell(x, y, st, group, false, 0, 0, 0, 0);
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
                    if (owner != null && isAnchorAt(list, i, owner)) {
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

    /**
     * 搜索框里有没有输内容。
     *
     * <p>折叠的列表就是最终展示的那一份，组员被收起后搜索也搜不到 ——
     * 用户搜"白羊毛"结果只看到组代表格，等于搜丢了。
     * 所以只要在搜索，就完全不折叠，保证"搜什么给什么"。
     */
    private static boolean hasQuery() {
        try {
            var box = EmiScreenManager.search;
            if (box == null) {
                return false;
            }
            String v = box.getValue();
            return v != null && !v.isBlank();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 这个控件是否被鼠标压住（且可见）。 */
    private static boolean hitWidget(Object w, double mx, double my) {
        if (!(w instanceof net.minecraft.client.gui.components.AbstractWidget aw) || !aw.visible) {
            return false;
        }
        return mx >= aw.getX() && mx < aw.getX() + aw.getWidth()
                && my >= aw.getY() && my < aw.getY() + aw.getHeight();
    }

    /**
     * 是否点在面板自带的按钮上（翻页箭头、类型循环按钮）。
     *
     * <p>这些按钮在面板 bounds 之内。常开框选模式下如果不把它们排除，
     * 一按就变成"框选起手"并取消了事件，翻页按钮永远点不到。
     */
    private static boolean onPanelButton(double mx, double my) {
        for (SidebarPanel p : allPanels()) {
            if (p == null) {
                continue;
            }
            if (hitWidget(p.pageLeft, mx, my) || hitWidget(p.pageRight, mx, my)
                    || hitWidget(p.cycle, mx, my)) {
                return true;
            }
        }
        return false;
    }

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

        // ① 配置界面的"折叠编辑"开关。这个开关自己关着时也必须能点开，
        //    所以放在最前面、不看 editEnabled。
        if (inRect(CONFIG_BTN, mx, my)) {
            boolean now = EmiFoldStore.toggleEdit();
            if (!now) {
                PENDING.clear();
                selecting = false;
                lastClickedGroup = null;
            }
            stateVersion++;
            cacheIn = null;
            Medals.LOGGER.info("[Lost Realm] EMI 折叠编辑已{}", now ? "开启" : "关闭");
            try {
                EmiScreenManager.recalculate();
            } catch (Throwable ignored) {
                // 界面还没建好时忽略
            }
            return true;
        }

        // ② 折叠格右下角的角标：展开 / 收起。
        //    <b>这一步不受"折叠编辑"开关影响</b> —— 关掉编辑只是不能改分组，
        //    已经折好的东西照样得能翻开看。之前把整个入口挂在总开关后面，
        //    一关就既摊开了列表又点不动角标。
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

        // 以下都是"编辑"动作：改分组、拉框、按钮。编辑开关关着就一律不做，
        // 让点击原样交给 EMI（取物品、看配方）。
        if (!EmiFoldStore.isEditEnabled()) {
            return false;
        }

        // ③ 底部自绘按钮
        for (Btn b : BUTTONS) {
            if (b.hit(mx, my)) {
                if (b.enabled()) {
                    b.action().run();
                }
                return true;
            }
        }

        // ④ 面板自带的按钮（翻页 / 循环）不参与框选。
        //    常开模式下一按就抢，翻页箭头会完全失灵。
        if (onPanelButton(mx, my)) {
            return false;
        }

        // ⑤ 框选起手：按住 Ctrl+Shift，或者开了"常开框选"模式
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
     * <p>只在"折叠编辑"开着、且"有待折叠项"或"刚点过某个组"时出现 ——
     * 平时不占地方。编辑关掉之后这些按钮就没有意义了（它们全是改分组的动作）。
     */
    private static void rebuildButtons() {
        BUTTONS.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.screen == null || !EmiFoldStore.isEditEnabled()) {
            return;
        }
        boolean hasPending = !PENDING.isEmpty();
        boolean hasGroup = lastClickedGroup != null
                && EmiFoldStore.groupNames().contains(lastClickedGroup);
        if (!hasPending && !hasGroup) {
            return;
        }
        final int bw = 66;
        final int bh = 14;
        final int gap = 6;
        int count = (hasPending ? 2 : 0) + (hasGroup ? 1 : 0);
        int total = count * bw + (count - 1) * gap;
        int x = (mc.screen.width - total) / 2;
        // 贴屏幕最底边：EMI 的列表一直铺到接近底部，
        // 之前放在 height-46 正好压在列表上，点物品会误触按钮
        int y = mc.screen.height - bh - 1;

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
        // 编辑关掉时这几个键全部让路（F6 是框选模式，F7/F8 是改分组）
        if (!EmiFoldStore.isEditEnabled()) {
            keyF6Down = f6;
            keyF7Down = f7;
            keyF8Down = f8;
            return;
        }
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

        // 配方界面顶上的"配方类型第几页/共几页"
        EmiRecipeTabs.draw(g, screen);

        // EMI 的配置界面：别的都不画。
        // 开关本身已经作为一行正经设置挂进列表里了（见 EmiFoldConfig）；
        // 只有在那一行没挂上（EMI 内部结构变了）时才退回左下角手画的那个。
        if (screen != null
                && "dev.emi.emi.screen.ConfigScreen".equals(screen.getClass().getName())) {
            BUTTONS.clear();
            // 每帧确认一次那一行还在（列表可能会被重建），失败了才画左下角兜底
            try {
                EmiFoldConfig.ensure((ConfigScreen) screen);
            } catch (Throwable ignored) {
                // 拿不到 ConfigScreen 就当没插进去
            }
            if (EmiFoldConfig.installed()) {
                CONFIG_BTN = null;
            } else {
                drawConfigSwitch(g, screen);
            }
            return;
        }
        CONFIG_BTN = null;

        // 不在 EMI 的渲染上下文里就彻底收手。
        //
        // Render.Post 是全局事件，每个界面每帧都会触发。只判断"面板列表非空"
        // 还不够 —— EmiScreenManager 的面板是静态持有的，界面关掉之后仍然能取到，
        // 于是主菜单、聊天框上照旧把边框和数字画出来。
        // EmiScreenBase.getCurrent() 才是"当前有没有界面在让 EMI 渲染"的权威判断。
        //
        // 两类界面要放行：
        //   ① 容器界面（背包 / 创造物品栏 / 箱子…）—— 走 EmiScreenBase 判断；
        //   ② EMI 自己的配方界面（RecipeScreen / BoMScreen）—— 它继承的是 Screen
        //      而不是 AbstractContainerScreen，之前被 ① 的条件挡在外面，
        //      一打开合成表，所有边框和角标就全没了。
        boolean rendering = screen instanceof AbstractContainerScreen;
        if (!rendering && screen != null && isEmiOwnScreen(screen)) {
            rendering = true;      // EMI 自己的界面：面板可能挂在不同空间，不看 EmiScreenBase
        }
        if (rendering && screen instanceof AbstractContainerScreen) {
            try {
                EmiScreenBase base = EmiScreenBase.getCurrent();
                rendering = base != null && !base.isEmpty();
            } catch (Throwable t) {
                rendering = false;
            }
        }
        if (!rendering) {
            BUTTONS.clear();
            return;
        }

        rebuildButtons();
        flushPendingRefresh();
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

    /** 是不是 EMI 自己的界面（配方表 / 书之类）。配置界面不算。 */
    private static boolean isEmiOwnScreen(Screen screen) {
        String n = screen.getClass().getName();
        return n.startsWith("dev.emi.emi.screen.") && !n.startsWith("dev.emi.emi.screen.Config");
    }

    /** EMI 配置界面左下角的"折叠编辑"开关。 */
    private static void drawConfigSwitch(GuiGraphics g, Screen screen) {
        var font = Minecraft.getInstance().font;
        boolean on = EmiFoldStore.isEditEnabled();
        String label = "[遗落境] 折叠编辑：" + (on ? "开" : "关");
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
        int y = mc.screen.height - 34;
        g.fill(x - 5, y - 4, x + w + 5, y + 10, 0xC8000000);
        g.drawString(font, tip, x, y, 0xFFFFE066, true);
    }

    private static void drawFolders(GuiGraphics g) {
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
                drawPage(g, list, space);
            }
        }
    }

    /**
     * 一屏的组：<b>一个组合成一条外轮廓</b>。
     *
     * <p>之前是按"行内连续段"各画一个框，跨行的一个组就变成上下两个互不相干的方框，
     * 同时展开好几个组时根本分不清哪几格是一家。现在把该组的所有格子并在一起，
     * 只画并集的<b>外边界</b>：同一行相邻的格之间、上下相邻的行之间的公共边不画，
     * 于是整组连成一条闭合折线 —— 方的一整块就是一个大方框，跨行错位就是一个
     * 阶梯形，怎么算都不会框进中间夹着的外人。
     */
    private static void drawPage(GuiGraphics g, List<? extends EmiIngredient> list, ScreenSpace space) {
        Map<String, List<int[]>> byGroup = new HashMap<>();
        List<String> order = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            String group = groupAt(list, i);
            if (group == null) {
                continue;
            }
            if (!byGroup.containsKey(group)) {
                order.add(group);
            }
            byGroup.computeIfAbsent(group, k -> new ArrayList<>())
                    .add(new int[]{i, space.getRawX(i), space.getRawY(i)});
        }
        for (String group : order) {
            List<int[]> cells = byGroup.get(group);
            drawGroupOutline(g, cells, colorFor(group));
            // 代表格 = 该组在这一屏里索引最小的那一格（也就是收起时露出来的那格）
            int[] first = cells.get(0);
            drawBadge(g, first[1], first[2], group, sizeOf(group));
        }
    }

    /**
     * 一组格子的并集外轮廓（1px）。相邻格之间的公共边不画，所以是连成一条线。
     *
     * <p>四条边一律压在<b>格子的边界线</b>上（上边 y、下边 y+CELL、左边 x、右边 x+CELL），
     * 而不是贴着图标内缩 1px。差别在于转角：
     * 内缩画法下，阶梯拐弯处两条线各差 1px，交汇的那个像素谁也不盖 ——
     * 就是截图里"转角缺了一个像素"。压在格线上、两端多画 1px 把角补满，
     * 拐角就永远是连着的。
     */
    private static void drawGroupOutline(GuiGraphics g, List<int[]> cells, int color) {
        if (cells == null || cells.isEmpty()) {
            return;
        }
        Set<Long> set = new HashSet<>();
        for (int[] c : cells) {
            set.add(cellKey(c[1], c[2]));
        }
        for (int[] c : cells) {
            int x = c[1];
            int y = c[2];
            if (!set.contains(cellKey(x, y - CELL))) {
                g.fill(x, y, x + CELL + 1, y + 1, color);                       // 上
            }
            if (!set.contains(cellKey(x, y + CELL))) {
                g.fill(x, y + CELL, x + CELL + 1, y + CELL + 1, color);          // 下
            }
            if (!set.contains(cellKey(x - CELL, y))) {
                g.fill(x, y, x + 1, y + CELL + 1, color);                        // 左
            }
            if (!set.contains(cellKey(x + CELL, y))) {
                g.fill(x + CELL, y, x + CELL + 1, y + CELL + 1, color);          // 右
            }
        }
    }

    private static long cellKey(int x, int y) {
        return ((long) x << 32) ^ (y & 0xFFFFFFFFL);
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

    /**
     * 右下角的角标：<b>数字 + 方向符</b>，没有边框、没有底色、也不画影子。
     *
     * <p>一路缩下来的结论：
     * <ul>
     *   <li>边框最先去掉 —— 16px 见方里 1px 的框一圈就吃掉两行两列，框本身还自带厚度感；</li>
     *   <li>点阵字体也去掉 —— 3×5 的笔画太粗，三位数一格一格撑开，比原版字体还占地方；</li>
     *   <li>现在就是原版字体缩到 0.5，字距、笔画都交给它自己排，整串右端贴图标右缘、
     *       底边贴图标下缘，能靠多角就靠多角。</li>
     * </ul>
     */
    private static void drawBadge(GuiGraphics g, int x, int y, String group, int count) {
        var font = Minecraft.getInstance().font;
        int[] r = badgeRect(x, y, group, count);
        String text = badgeText(group, count);

        g.pose().pushPose();
        // 抬 z：EMI 的物品是批量绘制、最后统一 flush 的，
        // 光靠"事件更靠后"压不住，数字会钻到图标底下
        g.pose().translate(0.0F, 0.0F, 200.0F);
        g.pose().translate(r[0], r[1], 0.0F);
        g.pose().scale(BADGE_SCALE, BADGE_SCALE, 1.0F);
        g.drawString(font, text, 0, 0, 0xFFFFFFFF, false);
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
