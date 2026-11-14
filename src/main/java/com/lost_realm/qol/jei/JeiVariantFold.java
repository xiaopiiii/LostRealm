package com.lost_realm.qol.jei;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientListOverlay;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.ingredients.IngredientListRenderer;
import mezz.jei.gui.overlay.ingredients.IngredientListSlot;

import com.lost_realm.Medals;
import com.lost_realm.config.MedalsConfig;

/**
 * <b>JEI 变体折叠</b> —— 把"同一件东西的不同形态"收成一个格子。
 *
 * <h2>什么会被折叠（两类，缺一不可）</h2>
 * <ol>
 *   <li><b>同一物品的不同 NBT 变体</b>：不同附魔的附魔书、不同效果的药水、
 *       不同染色的皮革甲。判定靠 {@link ItemStack#isSameItemSameTags}。</li>
 *   <li><b>同一基名的不同颜色</b>：羊毛、陶瓦、混凝土、染色玻璃、床、旗帜……
 *       注意这些在 MC 里<b>不是同一个物品</b>，而是 16 个独立物品
 *       （{@code white_wool} / {@code red_wool} ……），所以只按 Item 分组永远抓不到。
 *       判定靠"注册名去掉颜色词之后的基名"：
 *       {@code light_blue_concrete_powder} → {@code concrete_powder}。</li>
 * </ol>
 *
 * <h2>为什么颜色组要求至少 3 个成员</h2>
 * 按基名分组会误伤只有两个成员的组合：{@code sand} / {@code red_sand}、
 * {@code ice} / {@code blue_ice}、{@code brown_mushroom} / {@code red_mushroom}。
 * 这些其实是不同东西，折起来会把其中一个藏掉。真正的颜色套件（羊毛、陶瓦、
 * 混凝土、染料、床）都是 16 个，门槛设 3 足够安全。
 *
 * <h2>方框怎么画</h2>
 * 画在 {@link IngredientListSlot#getRenderArea()} 上（= 槽位外框内缩 padding，
 * 正好贴着图标），线宽固定 1px —— 画在 {@code getArea()} 上会大一圈，
 * 相邻格子几乎挨在一起，很挤。
 *
 * <p>颜色组画的是<b>环</b>：沿周长把成员各自的颜色依次铺开，一眼就能看出
 * "这格里装的是一堆颜色"。NBT 变体组没有颜色可言，画单色。
 *
 * <p>绘制时机上，边框画在 {@code IngredientListRenderer.render} 开头
 * （JEI 先铺槽位底色、再画图标，开头正好在两者之间），数量角标画在
 * {@code ScreenEvent.Render$Post} 末尾（否则会被后画的图标盖住）。
 *
 * <h2>点击冲突怎么处理</h2>
 * 点击折叠组的格子会切换展开状态，代价是那一格不能直接点开配方。
 * 这是需求本身决定的（"点击展开、再点收起"必然占用点击）。
 * 实现上是在 {@code ScreenEvent.MouseButtonPressed.Pre} 里取消这次点击，
 * 这样 JEI 不会同时弹出配方界面。仍然可以选中后用 R / U 键看用途和配方。
 */
@Mod.EventBusSubscriber(modid = Medals.MODID, value = Dist.CLIENT)
public final class JeiVariantFold {

    /** NBT 变体组用的单色（按组键哈希取，同组永远同色）。 */
    private static final int[] FRAME_COLORS = {
            0xFF4FA3E0, 0xFFE0A24F, 0xFF6BD08A, 0xFFD06BC8,
            0xFFE05C5C, 0xFF8A6BE0, 0xFF4FD0C8, 0xFFD0C84F,
            0xFF7EC8E0, 0xFFE08A5C, 0xFFA0D86B, 0xFFC86BD0
    };

    /** 16 个染料色在注册名里的词。多词的要写在前面（light_blue 先于 blue）。 */
    private static final String[] COLOR_WORDS = {
            "light_blue", "light_gray", "white", "orange", "magenta", "yellow",
            "lime", "pink", "gray", "cyan", "purple", "blue", "brown", "green",
            "red", "black"
    };

    /** 对应的原版染料色，用来铺彩色环。 */
    private static final Map<String, Integer> DYE_RGB = Map.ofEntries(
            Map.entry("white", 0xFFF0F0F0), Map.entry("orange", 0xFFF9801D),
            Map.entry("magenta", 0xFFC74EBD), Map.entry("light_blue", 0xFF3AB3DA),
            Map.entry("yellow", 0xFFFED83D), Map.entry("lime", 0xFF80C71F),
            Map.entry("pink", 0xFFF5B0CB), Map.entry("gray", 0xFF5B5B5B),
            Map.entry("light_gray", 0xFFA8A8A8), Map.entry("cyan", 0xFF169C9C),
            Map.entry("purple", 0xFF8932B8), Map.entry("blue", 0xFF3C44AA),
            Map.entry("brown", 0xFF835432), Map.entry("green", 0xFF5E7C16),
            Map.entry("red", 0xFFB02E26), Map.entry("black", 0xFF2A2A2A)
    );

    /** 一组折叠项。ring 是画框用的颜色序列，长度 1 就是单色。 */
    private record Group(List<ItemStack> members, int[] ring) {}

    /** 需要叠在图标之上的角标，等 JEI 画完这一帧再补。 */
    private record Badge(int x, int y, int w, String text, int border) {}

    private static IJeiRuntime runtime;

    private static final Map<String, Group> GROUPS = new LinkedHashMap<>();
    /** 物品 → 所属组的键。只收录"有组"的物品。 */
    private static final Map<Item, String> ITEM_GROUP = new HashMap<>();
    private static final Set<String> EXPANDED = new HashSet<>();

    private static final List<Badge> BADGES = new ArrayList<>();

    private static boolean ready;

    /** 渲染出过一次异常就永久停手 —— 边框是装饰，不能为了它每帧刷日志、拖慢游戏。 */
    private static boolean renderBroken;

    private JeiVariantFold() {}

    /** 由 {@code LostRealmJeiPlugin} 在 JEI 就绪时调用。 */
    public static void onRuntimeAvailable(IJeiRuntime r) {
        runtime = r;
        ready = false;
        GROUPS.clear();
        ITEM_GROUP.clear();
        EXPANDED.clear();
        BADGES.clear();
    }

    // ================================================================
    // 主循环
    // ================================================================

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || runtime == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (!enabled()) {
            if (!GROUPS.isEmpty()) {
                restoreAll();
            }
            return;
        }
        if (!ready) {
            buildGroups();
            ready = true;
            collapseAll();
        }
    }

    private static boolean enabled() {
        return MedalsConfig.QOL_JEI_FOLD_VARIANTS != null
                && MedalsConfig.QOL_JEI_FOLD_VARIANTS.get();
    }

    // ================================================================
    // 分组
    // ================================================================

    private static void buildGroups() {
        GROUPS.clear();
        ITEM_GROUP.clear();
        EXPANDED.clear();

        var manager = runtime.getIngredientManager();
        if (manager == null) {
            return;
        }

        // 先按物品归拢，同 NBT 的重复项丢掉（否则"变体数"会虚高）
        Map<Item, List<ItemStack>> byItem = new LinkedHashMap<>();
        for (ItemStack stack : manager.getAllIngredients(VanillaTypes.ITEM_STACK)) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            List<ItemStack> list = byItem.computeIfAbsent(stack.getItem(), k -> new ArrayList<>());
            boolean dup = false;
            for (ItemStack exist : list) {
                if (ItemStack.isSameItemSameTags(exist, stack)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                list.add(stack.copyWithCount(1));
            }
        }

        // ① 同一物品的多 NBT 变体
        Set<Item> claimed = new HashSet<>();
        for (Map.Entry<Item, List<ItemStack>> e : byItem.entrySet()) {
            if (e.getValue().size() < 2) {
                continue;
            }
            Item item = e.getKey();
            String key = "nbt|" + BuiltInRegistries.ITEM.getKey(item);
            int color = FRAME_COLORS[Math.floorMod(key.hashCode(), FRAME_COLORS.length)];
            GROUPS.put(key, new Group(List.copyOf(e.getValue()), new int[]{color}));
            ITEM_GROUP.put(item, key);
            claimed.add(item);
        }

        // ② 同一基名的不同颜色（羊毛 / 陶瓦 / 混凝土 / 染料 / 床 / 旗帜……）
        Map<String, List<ItemStack>> byBase = new LinkedHashMap<>();
        Map<String, List<Integer>> paletteByBase = new LinkedHashMap<>();
        for (Map.Entry<Item, List<ItemStack>> e : byItem.entrySet()) {
            Item item = e.getKey();
            if (claimed.contains(item)) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            String path = id.getPath();
            String[] split = splitColorWord(path);
            if (split == null) {
                continue;
            }
            String base = id.getNamespace() + "|" + split[1];
            byBase.computeIfAbsent(base, k -> new ArrayList<>()).add(e.getValue().get(0));
            paletteByBase.computeIfAbsent(base, k -> new ArrayList<>())
                    .add(DYE_RGB.getOrDefault(split[0], 0xFFB0B0B0));
        }
        for (Map.Entry<String, List<ItemStack>> e : byBase.entrySet()) {
            List<ItemStack> members = e.getValue();
            if (members.size() < 3) {
                continue;      // 见类注释：2 个的多半是 sand/red_sand 这类误判
            }
            members.sort((a, b) -> BuiltInRegistries.ITEM.getKey(a.getItem()).toString()
                    .compareTo(BuiltInRegistries.ITEM.getKey(b.getItem()).toString()));
            String key = "color|" + e.getKey();
            GROUPS.put(key, new Group(List.copyOf(members), distinct(paletteByBase.get(e.getKey()))));
            for (ItemStack s : members) {
                ITEM_GROUP.putIfAbsent(s.getItem(), key);
            }
        }

        // 打一行统计：出问题时能直接从日志看出"羊毛/混凝土到底有没有被折进去"
        int colorGroups = 0;
        int nbtGroups = 0;
        int hidden = 0;
        var samples = new StringBuilder();
        for (Map.Entry<String, Group> e : GROUPS.entrySet()) {
            if (e.getKey().startsWith("color|")) {
                colorGroups++;
                if (colorGroups <= 6) {
                    samples.append(' ').append(e.getKey().substring(6))
                            .append('(').append(e.getValue().members().size()).append(')');
                }
            } else {
                nbtGroups++;
            }
            hidden += e.getValue().members().size() - 1;
        }
        Medals.LOGGER.info("[Lost Realm] JEI 变体折叠：{} 组（颜色 {} / NBT {}），收起 {} 项；颜色组示例:{}",
                GROUPS.size(), colorGroups, nbtGroups, hidden, samples);
    }

    /** 注册名去掉颜色词：返回 {@code {颜色词, 基名}}，没有颜色词返回 null。 */
    private static String[] splitColorWord(String path) {
        for (String c : COLOR_WORDS) {
            if (path.startsWith(c + "_") && path.length() > c.length() + 1) {
                return new String[]{c, path.substring(c.length() + 1)};
            }
            if (path.endsWith("_" + c) && path.length() > c.length() + 1) {
                return new String[]{c, path.substring(0, path.length() - c.length() - 1)};
            }
        }
        return null;
    }

    private static int[] distinct(List<Integer> colors) {
        List<Integer> out = new ArrayList<>();
        for (int c : colors) {
            if (!out.contains(c)) {
                out.add(c);
            }
        }
        int[] arr = new int[out.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = out.get(i);
        }
        return arr;
    }

    /** 默认状态：全部收起，每个组只留一个代表格。 */
    private static void collapseAll() {
        List<ItemStack> remove = new ArrayList<>();
        for (Group g : GROUPS.values()) {
            for (int i = 1; i < g.members().size(); i++) {
                remove.add(g.members().get(i));
            }
        }
        if (!remove.isEmpty()) {
            runtime.getIngredientManager()
                    .removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, remove);
        }
    }

    private static void toggle(String key) {
        if (EXPANDED.contains(key)) {
            collapse(key);
        } else {
            expand(key);
        }
    }

    private static void expand(String key) {
        Group g = GROUPS.get(key);
        if (g == null || g.members().size() < 2) {
            return;
        }
        runtime.getIngredientManager().addIngredientsAtRuntime(
                VanillaTypes.ITEM_STACK, g.members().subList(1, g.members().size()));
        EXPANDED.add(key);
    }

    private static void collapse(String key) {
        Group g = GROUPS.get(key);
        if (g == null || g.members().size() < 2) {
            return;
        }
        runtime.getIngredientManager().removeIngredientsAtRuntime(
                VanillaTypes.ITEM_STACK, g.members().subList(1, g.members().size()));
        EXPANDED.remove(key);
    }

    /** 功能被关掉时把摘掉的成员全部放回去，不能把玩家的物品列表永久弄短。 */
    private static void restoreAll() {
        List<ItemStack> add = new ArrayList<>();
        for (Group g : GROUPS.values()) {
            for (int i = 1; i < g.members().size(); i++) {
                add.add(g.members().get(i));
            }
        }
        if (!add.isEmpty() && runtime != null) {
            runtime.getIngredientManager()
                    .addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, add);
        }
        GROUPS.clear();
        ITEM_GROUP.clear();
        EXPANDED.clear();
        ready = false;
    }

    // ================================================================
    // 点击
    // ================================================================

    /**
     * 点击折叠组的代表槽 = 切换展开 / 收起。
     *
     * <h2>为什么挂在 {@code MouseButtonPressed.Pre} 并且要取消</h2>
     * JEI 自己也是在这个事件里处理"点一下看配方"的
     * （{@code mezz.jei.forge.startup.EventRegistration#registerClientInputHandler}，
     * 注册时 {@code receiveCanceled = false}）。所以用 HIGHEST 抢在它前面，
     * 处理完立刻取消 —— JEI 看不到这次点击，不会同时弹出配方界面。
     * 如果只是轮询鼠标按键，取消不了 JEI 的处理，会"既收起又打开配方"。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0) {
            return;
        }
        String key = anchorUnderMouse();
        if (key == null) {
            return;
        }
        event.setCanceled(true);
        try {
            toggle(key);
        } catch (Throwable t) {
            Medals.LOGGER.warn("[Lost Realm] JEI 变体折叠切换失败", t);
        }
    }

    /** 鼠标下的那个物品若是某组的代表格，返回组键。 */
    private static String anchorUnderMouse() {
        if (runtime == null || !enabled() || GROUPS.isEmpty()) {
            return null;
        }
        try {
            IIngredientListOverlay overlay = runtime.getIngredientListOverlay();
            if (overlay == null || !overlay.isListDisplayed()) {
                return null;
            }
            Optional<ITypedIngredient<?>> under = overlay.getIngredientUnderMouse();
            if (under.isEmpty()) {
                return null;
            }
            Optional<ItemStack> stackOpt = under.get().getItemStack();
            if (stackOpt.isEmpty()) {
                return null;
            }
            ItemStack stack = stackOpt.get();
            String key = ITEM_GROUP.get(stack.getItem());
            if (key == null) {
                return null;
            }
            Group g = GROUPS.get(key);
            if (g == null || g.members().size() < 2) {
                return null;
            }
            // 只有"代表"那一格能切换，否则展开后满屏成员每格都能点，很容易误收
            if (!ItemStack.isSameItemSameTags(g.members().get(0), stack)) {
                return null;
            }
            return key;
        } catch (Throwable t) {
            return null;
        }
    }

    // ================================================================
    // 渲染
    // ================================================================

    /**
     * 由 mixin 在 {@code IngredientListRenderer.render} 开头调用。
     *
     * <p>此时 JEI 已经铺好每个槽位的底色、还没画图标
     * （见 {@code IngredientGrid.drawContents}：先 slot 贴图，再 renderer.render）。
     */
    public static void captureAndDraw(GuiGraphics g, IngredientListRenderer renderer) {
        BADGES.clear();
        if (renderBroken || runtime == null || !enabled() || GROUPS.isEmpty()) {
            return;
        }
        try {
            captureAndDraw0(g, renderer);
        } catch (Throwable t) {
            // 画边框只是装饰。出一次错就把自己关掉，绝不能让它每帧抛异常把游戏拖死。
            renderBroken = true;
            BADGES.clear();
            Medals.LOGGER.warn("[Lost Realm] JEI 变体折叠边框绘制失败，已停用边框（折叠本身不受影响）", t);
        }
    }

    private static void captureAndDraw0(GuiGraphics g, IngredientListRenderer renderer) {
        List<IngredientListSlot> slots = renderer.getAllSlots();
        if (slots == null || slots.isEmpty()) {
            return;
        }
        var font = Minecraft.getInstance().font;
        for (IngredientListSlot slot : slots) {
            // 用 getOptionalElement 而不是 getElement：空槽（翻页后未填充）上
            // getElement 会抛异常，一旦抛了就被 renderBroken 永久关掉边框
            var elementOpt = slot.getOptionalElement();
            if (elementOpt.isEmpty()) {
                continue;
            }
            IElement<?> element = elementOpt.get();
            if (element == null) {
                continue;
            }
            ITypedIngredient<?> typed = element.getTypedIngredient();
            if (typed == null) {
                continue;
            }
            Optional<ItemStack> stackOpt = typed.getItemStack();
            if (stackOpt.isEmpty()) {
                continue;
            }
            ItemStack stack = stackOpt.get();
            String key = ITEM_GROUP.get(stack.getItem());
            if (key == null) {
                continue;
            }
            Group group = GROUPS.get(key);
            if (group == null || group.members().size() < 2) {
                continue;
            }
            boolean anchor = ItemStack.isSameItemSameTags(group.members().get(0), stack);
            // 贴着图标画，不用 getArea()（那会大一圈，相邻格子几乎挨在一起）
            ImmutableRect2i area = slot.getRenderArea();
            drawRing(g, area, group.ring());

            if (anchor) {
                String badge = EXPANDED.contains(key) ? "-" : String.valueOf(group.members().size());
                int w = font.width(badge);
                int bx = area.getX() + area.getWidth() - w;
                int by = area.getY() + area.getHeight() - 8;
                BADGES.add(new Badge(bx, by, w, badge, group.ring()[0]));
            }
        }
    }

    /**
     * 沿周长把颜色依次铺开画一圈 1px 的框。
     *
     * <p>颜色只有一种时就是普通单色框；颜色组的成员色有十几种，
     * 铺出来就是一段一段的彩色环 —— 一眼能看出"这格里是一堆颜色"。
     */
    private static void drawRing(GuiGraphics g, ImmutableRect2i area, int[] ring) {
        int n = ring.length;
        if (n == 0) {
            return;
        }
        int x0 = area.getX();
        int y0 = area.getY();
        int x1 = x0 + area.getWidth() - 1;
        int y1 = y0 + area.getHeight() - 1;
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        int perimeter = 2 * (x1 - x0) + 2 * (y1 - y0);
        int i = 0;

        for (int x = x0; x <= x1; x++) {                       // 上边 →
            g.fill(x, y0, x + 1, y0 + 1, ring[(int) ((long) i++ * n / perimeter) % n]);
        }
        for (int y = y0 + 1; y <= y1; y++) {                   // 右边 ↓
            g.fill(x1, y, x1 + 1, y + 1, ring[(int) ((long) i++ * n / perimeter) % n]);
        }
        for (int x = x1 - 1; x >= x0; x--) {                   // 下边 ←
            g.fill(x, y1, x + 1, y1 + 1, ring[(int) ((long) i++ * n / perimeter) % n]);
        }
        for (int y = y1 - 1; y > y0; y--) {                    // 左边 ↑
            g.fill(x0, y, x0 + 1, y + 1, ring[(int) ((long) i++ * n / perimeter) % n]);
        }
    }

    /**
     * 这一帧画完后再补数量角标。
     *
     * <p>用 {@link EventPriority#LOWEST} 排在 JEI 自己的 {@code Render.Post}
     * （NORMAL 优先级）之后，才画得到图标上面。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        if (BADGES.isEmpty()) {
            return;
        }
        try {
            GuiGraphics g = event.getGuiGraphics();
            var font = Minecraft.getInstance().font;
            for (Badge b : BADGES) {
                // 实心黑底 + 1px 彩边：原来用半透明底，压在亮色图标上（羊毛、白色混凝土
                // 这种）根本读不出来，看着就像"数字躲在贴图下面"。
                int x0 = b.x() - 1;
                int y0 = b.y() - 1;
                int x1 = b.x() + b.w() + 1;
                int y1 = b.y() + 8;
                g.fill(x0, y0, x1, y1, 0xFF000000);
                g.fill(x0, y0, x1, y0 + 1, b.border());
                g.fill(x0, y1 - 1, x1, y1, b.border());
                g.fill(x0, y0, x0 + 1, y1, b.border());
                g.fill(x1 - 1, y0, x1, y1, b.border());
                g.drawString(font, b.text(), b.x(), b.y(), 0xFFFFFFFF, true);
            }
        } catch (Throwable t) {
            renderBroken = true;
            Medals.LOGGER.warn("[Lost Realm] JEI 变体折叠角标绘制失败，已停用边框", t);
        } finally {
            BADGES.clear();
        }
    }
}
