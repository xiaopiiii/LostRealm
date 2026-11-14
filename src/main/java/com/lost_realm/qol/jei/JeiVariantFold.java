package com.lost_realm.qol.jei;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import net.minecraftforge.fml.loading.FMLPaths;

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
 * <b>JEI 变体折叠</b> —— 把同一类东西收成一个格子。
 *
 * <h2>折叠规则（只有一条）</h2>
 * 取物品注册名<b>最后一个下划线之后的部分</b>当分类依据，同一命名空间内
 * 同后缀的成员 ≥ {@value #MIN_GROUP} 个就折成一组：
 *
 * <pre>
 * iron_ingot / gold_ingot / copper_ingot    → ingot    → 一组
 * iron_dust  / gold_dust  / iron_small_dust → dust     → 一组
 * white_wool / red_wool   / black_wool      → wool     → 一组
 * white_concrete / red_concrete             → concrete → 一组
 * </pre>
 *
 * 这样羊毛、陶瓦、混凝土、染料、格雷的锭/粉/小粉/齿轮/板/粒全部由这一条规则覆盖，
 * 不需要为任何具体类型再写特判。没有下划线的物品（stone、dirt）不参与。
 *
 * <p>另有一条独立的分组：<b>同一物品的不同 NBT 变体</b>（不同附魔的附魔书、
 * 不同效果的药水）。这类物品先被 NBT 组认领，不再进后缀组。
 *
 * <h2>代表格是哪一个</h2>
 * 成员<b>按 JEI 给的顺序</b>原样保留，不排序。所以收起时显示的就是 JEI 列表里
 * 排在最前面的那一个（羊毛是白色羊毛），展开后它也仍是第一格，点它就能收起。
 * （曾经按注册名排序，结果收起时显示 black_wool、展开后却要去找黑羊毛点，很别扭。）
 *
 * <h2>点击</h2>
 * 只有<b>右下角那个小角标</b>是折叠开关；点格子其他地方仍然是 JEI 原本的
 * "左键看配方"。之前整格都能切换，等于把那一格的配方查看吃掉了。
 *
 * <h2>展开后成员为什么不在代表格后面</h2>
 * 收起 = 把非代表成员从 JEI 列表里摘掉，展开 = 加回去。JEI 的公开接口
 * （{@code addIngredientsAtRuntime}）<b>没有"插到指定位置"的能力</b>，
 * 加回去的物品会按当前排序规则落到它们自己的位置。这是这套方案固有的限制，
 * 换来的是搜索、翻页、滚动、拖拽全部照常工作。
 */
@Mod.EventBusSubscriber(modid = Medals.MODID, value = Dist.CLIENT)
public final class JeiVariantFold {

    /** 同后缀成员少于这个数就不折 —— 两个的多半是 sand/red_sand 这类不同东西。 */
    private static final int MIN_GROUP = 3;

    /** 单色框的候选色（NBT 组、以及成员数少于 6 的后缀组用）。 */
    private static final int[] FRAME_COLORS = {
            0xFF4FA3E0, 0xFFE0A24F, 0xFF6BD08A, 0xFFD06BC8,
            0xFFE05C5C, 0xFF8A6BE0, 0xFF4FD0C8, 0xFFD0C84F
    };

    /** 成员多的组沿周长铺这个色环，一眼看出"这格里是一堆东西"。 */
    private static final int[] RING = {
            0xFFE05C5C, 0xFFE0A24F, 0xFFD8D05C, 0xFF6BD08A,
            0xFF4FD0C8, 0xFF4FA3E0, 0xFF8A6BE0, 0xFFD06BC8
    };

    /** 成员数达到这个值才画彩色环，否则单色 —— 三四个的小组画彩虹太吵。 */
    private static final int RING_MIN = 6;

    /** 排除表：这些后缀不参与折叠。用户在 config/lost_realm/jei_fold_exclude.txt 里自己加。 */
    private static final Set<String> EXCLUDED = new HashSet<>();

    private record Group(List<ItemStack> members, int[] ring) {}

    /**
     * 画出来的角标，同时也是唯一的点击热区（{@code x0..x1, y0..y1}）。
     * 每帧重新收集；不做每帧清空之外的回收，因为它要在两帧之间的点击事件里用。
     */
    private record Marker(int x0, int y0, int x1, int y1, String key) {}

    private static IJeiRuntime runtime;

    private static final Map<String, Group> GROUPS = new LinkedHashMap<>();
    /** 物品 → 所属组的键 */
    private static final Map<Item, String> ITEM_GROUP = new HashMap<>();
    private static final Set<String> EXPANDED = new HashSet<>();
    private static final List<Marker> MARKERS = new ArrayList<>();

    private static boolean ready;

    /** 渲染出过一次异常就永久停手 —— 边框是装饰，不能为了它每帧刷日志、拖慢游戏。 */
    private static boolean renderBroken;

    /** 上一次重排的输入/输出。JEI 会缓存列表实例，命中就直接返回，避免每帧重排上万条。 */
    private static List<IElement<?>> lastIn;
    private static List<IElement<?>> lastOut;

    private JeiVariantFold() {}

    /** 由 {@code LostRealmJeiPlugin} 在 JEI 就绪时调用。 */
    public static void onRuntimeAvailable(IJeiRuntime r) {
        runtime = r;
        ready = false;
        GROUPS.clear();
        ITEM_GROUP.clear();
        EXPANDED.clear();
        MARKERS.clear();
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
        lastIn = null;
        lastOut = null;
        loadExclusions();

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

        // ① 同一物品的多 NBT 变体（附魔书、药水……）
        Set<Item> claimed = new HashSet<>();
        for (Map.Entry<Item, List<ItemStack>> e : byItem.entrySet()) {
            if (e.getValue().size() < 2) {
                continue;
            }
            Item item = e.getKey();
            String key = "nbt|" + BuiltInRegistries.ITEM.getKey(item);
            GROUPS.put(key, new Group(List.copyOf(e.getValue()), single(key)));
            ITEM_GROUP.put(item, key);
            claimed.add(item);
        }

        // ② 同后缀（唯一的一条通用规则）
        Map<String, List<ItemStack>> bySuffix = new LinkedHashMap<>();
        for (Map.Entry<Item, List<ItemStack>> e : byItem.entrySet()) {
            Item item = e.getKey();
            if (claimed.contains(item)) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            String path = id.getPath();
            int cut = path.lastIndexOf('_');
            if (cut <= 0 || cut >= path.length() - 1) {
                continue;                      // 没有下划线，或下划线在末尾
            }
            String suffix = path.substring(cut + 1);
            if (suffix.length() < 2 || EXCLUDED.contains(suffix)) {
                continue;
            }
            String key = id.getNamespace() + "|" + suffix;
            bySuffix.computeIfAbsent(key, k -> new ArrayList<>()).add(e.getValue().get(0));
        }
        for (Map.Entry<String, List<ItemStack>> e : bySuffix.entrySet()) {
            List<ItemStack> members = e.getValue();
            if (members.size() < MIN_GROUP) {
                continue;
            }
            String key = "tok|" + e.getKey();
            int[] ring = members.size() >= RING_MIN ? RING : single(key);
            GROUPS.put(key, new Group(List.copyOf(members), ring));
            for (ItemStack s : members) {
                ITEM_GROUP.put(s.getItem(), key);
            }
        }

        logSummary();
    }

    /** 单色：同一个组键永远同一个颜色。 */
    private static int[] single(String key) {
        return new int[]{FRAME_COLORS[Math.floorMod(key.hashCode(), FRAME_COLORS.length)]};
    }

    /**
     * 读排除表 {@code config/lost_realm/jei_fold_exclude.txt}，没有就生成一份带说明的。
     *
     * <p>后缀规则是"一刀切"的：{@code copper_wire} 和 {@code fine_copper_wire} 都以后缀
     * {@code wire} 结尾，会被并成一组，但它们的语义并不一样（一个是导线、一个是细导线）。
     * 这类"看起来同后缀、其实不同东西"的情况没法靠规则自动分辨，所以留给用户一份表，
     * 想排除哪个后缀就加一行，不必改代码。
     */
    private static void loadExclusions() {
        EXCLUDED.clear();
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve("lost_realm");
            Files.createDirectories(dir);
            Path file = dir.resolve("jei_fold_exclude.txt");
            if (!Files.exists(file)) {
                Files.write(file, List.of(
                        "# JEI 折叠排除表：每行一个后缀，命中就不折叠。",
                        "# 后缀 = 注册名最后一个下划线之后的部分。",
                        "# 例：wire 会让 copper_wire / fine_copper_wire 都不参与折叠；",
                        "#     想恢复折叠就把那一行删掉或在前面加 #。",
                        "# 改完重新进入世界（或重开游戏）生效。",
                        "wire"
                ), StandardCharsets.UTF_8);
            }
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                EXCLUDED.add(line.toLowerCase());
            }
        } catch (Throwable t) {
            Medals.LOGGER.warn("[Lost Realm] 读取 JEI 折叠排除表失败，本次按不排除处理", t);
        }
    }

    private static void logSummary() {
        int token = 0;
        int nbt = 0;
        int hidden = 0;
        List<Map.Entry<String, Group>> big = new ArrayList<>();
        for (Map.Entry<String, Group> e : GROUPS.entrySet()) {
            if (e.getKey().startsWith("tok|")) {
                token++;
                big.add(e);
            } else {
                nbt++;
            }
            hidden += e.getValue().members().size() - 1;
        }
        big.sort((a, b) -> b.getValue().members().size() - a.getValue().members().size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(8, big.size()); i++) {
            sb.append(' ').append(big.get(i).getKey().substring(4))
                    .append('(').append(big.get(i).getValue().members().size()).append(')');
        }
        Medals.LOGGER.info("[Lost Realm] JEI 折叠：{} 组（后缀 {} / NBT {}），收起 {} 项，排除后缀 {}；最大的组:{}",
                GROUPS.size(), token, nbt, hidden, EXCLUDED, sb);
    }

    /** 默认状态：全部收起，每个组只留代表格（= JEI 顺序里的第一个）。 */
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
        MARKERS.clear();
        lastIn = null;
        lastOut = null;
        ready = false;
    }

    // ================================================================
    // 列表重排（由 mixin 在 IngredientFilter.getElements 返回前调用）
    // ================================================================

    /**
     * 把每个折叠组的成员挪到代表格<b>紧后面</b>。
     *
     * <p>折叠靠"摘掉 / 加回去"实现，而 JEI 加回去的物品会按自己的排序落位，
     * 于是展开后成员散落各处。JEI 最终用的顺序就是这份列表，在返回前重排
     * 一遍即可，分页 / 点击 / 拖拽 / 搜索都跟着这份顺序走。
     *
     * <p>结果按输入列表实例缓存：JEI 会缓存 {@code ingredientListCached}，
     * 同一实例反复传入时直接返回上次的结果，避免每帧重排上万条。
     */
    public static List<IElement<?>> reorderForFold(List<IElement<?>> in) {
        if (in == null || in.size() < 2 || GROUPS.isEmpty() || !enabled()) {
            return in;
        }
        if (in == lastIn) {
            return lastOut;
        }
        List<IElement<?>> out;
        try {
            out = reorder0(in);
        } catch (Throwable t) {
            Medals.LOGGER.warn("[Lost Realm] JEI 列表重排失败，已跳过（不影响折叠）", t);
            out = in;
        }
        lastIn = in;
        lastOut = out;
        return out;
    }

    private static List<IElement<?>> reorder0(List<IElement<?>> in) {
        int n = in.size();
        String[] keys = new String[n];
        // 组键 → 该组元素在列表里的下标（保持原有先后）
        Map<String, List<Integer>> index = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String k = keyOf(in.get(i));
            keys[i] = k;
            if (k != null) {
                index.computeIfAbsent(k, x -> new ArrayList<>(4)).add(i);
            }
        }
        if (index.isEmpty()) {
            return in;
        }

        List<IElement<?>> out = new ArrayList<>(n);
        Set<String> emitted = new HashSet<>();
        for (int i = 0; i < n; i++) {
            String k = keys[i];
            if (k == null) {
                out.add(in.get(i));
                continue;
            }
            if (!emitted.add(k)) {
                continue;                      // 这一组的成员已经在代表格后面排过了
            }
            List<Integer> slots = index.get(k);
            // 代表元素排最前，其余按原顺序跟上
            int rep = -1;
            Group g = GROUPS.get(k);
            if (g != null) {
                ItemStack want = g.members().get(0);
                for (int j : slots) {
                    Optional<ItemStack> s = stackOf(in.get(j));
                    if (s.isPresent() && ItemStack.isSameItemSameTags(want, s.get())) {
                        rep = j;
                        break;
                    }
                }
            }
            if (rep >= 0) {
                out.add(in.get(rep));
            }
            for (int j : slots) {
                if (j != rep) {
                    out.add(in.get(j));
                }
            }
        }
        return out;
    }

    private static String keyOf(IElement<?> element) {
        Optional<ItemStack> s = stackOf(element);
        if (s.isEmpty()) {
            return null;
        }
        return ITEM_GROUP.get(s.get().getItem());
    }

    private static Optional<ItemStack> stackOf(IElement<?> element) {
        if (element == null) {
            return Optional.empty();
        }
        ITypedIngredient<?> typed = element.getTypedIngredient();
        if (typed == null) {
            return Optional.empty();
        }
        return typed.getItemStack();
    }

    // ================================================================
    // 点击
    // ================================================================

    /**
     * 只有点在<b>角标</b>上才切换折叠。
     *
     * <p>点格子其他地方一律放行，交给 JEI 处理"左键看配方" ——
     * 之前整格都吃掉点击，那一格就再也点不开配方了。
     *
     * <p>用 HIGHEST 优先级是为了能抢在 JEI 前面取消；JEI 注册
     * {@code MouseButtonPressed.Pre} 时 {@code receiveCanceled = false}，
     * 所以取消之后它不会再去开配方。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0 || MARKERS.isEmpty()) {
            return;
        }
        if (runtime == null || !enabled()) {
            return;
        }
        try {
            IIngredientListOverlay overlay = runtime.getIngredientListOverlay();
            if (overlay == null || !overlay.isListDisplayed()) {
                return;
            }
        } catch (Throwable t) {
            return;
        }
        double mx = event.getMouseX();
        double my = event.getMouseY();
        for (Marker m : MARKERS) {
            if (mx >= m.x0() && mx < m.x1() && my >= m.y0() && my < m.y1()) {
                event.setCanceled(true);
                try {
                    toggle(m.key());
                } catch (Throwable t) {
                    Medals.LOGGER.warn("[Lost Realm] JEI 变体折叠切换失败", t);
                }
                return;
            }
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
        MARKERS.clear();
        if (renderBroken || runtime == null || !enabled() || GROUPS.isEmpty()) {
            return;
        }
        try {
            captureAndDraw0(g, renderer);
        } catch (Throwable t) {
            // 画边框只是装饰。出一次错就把自己关掉，绝不能让它每帧抛异常把游戏拖死。
            renderBroken = true;
            MARKERS.clear();
            Medals.LOGGER.warn("[Lost Realm] JEI 变体折叠绘制失败，已停用（折叠本身不受影响）", t);
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
            // getElement 会抛异常，一旦抛了就被 renderBroken 永久关掉
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
            // 贴着图标画；getArea() 会大一圈，相邻格子几乎挨在一起
            ImmutableRect2i area = slot.getRenderArea();
            drawRing(g, area, group.ring());

            if (anchor) {
                String text = EXPANDED.contains(key) ? "-" : String.valueOf(group.members().size());
                int w = font.width(text);
                int tx = area.getX() + area.getWidth() - w;
                int ty = area.getY() + area.getHeight() - 8;
                // 和原版堆叠数字一个做法：只写字、带阴影，不铺黑底。
                // 铺底会变成一个 8x10 的方块，压在 16px 的图标上太大了。
                // z 抬 400：JEI 的物品是批量绘制的，靠事件顺序压不住深度排序；
                // 这里画在图标之前、但深度更靠前，所以能压住图标；
                // 提示框是之后画的、z 同样是 400，会正常盖住这个数字。
                g.pose().pushPose();
                g.pose().translate(0.0F, 0.0F, 400.0F);
                g.drawString(font, text, tx, ty, 0xFFFFFFFF, true);
                g.pose().popPose();

                MARKERS.add(new Marker(tx - 1, ty - 1, tx + w + 1, ty + 8, key));
            }
        }
    }

    /**
     * 沿周长把颜色依次铺开画一圈 1px 的框。
     *
     * <p>颜色只有一种时是普通单色框；成员多的组铺出来就是彩色环。
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
        for (int x = x0; x <= x1; x++) {
            g.fill(x, y0, x + 1, y0 + 1, ring[(int) ((long) i++ * n / perimeter) % n]);
        }
        for (int y = y0 + 1; y <= y1; y++) {
            g.fill(x1, y, x1 + 1, y + 1, ring[(int) ((long) i++ * n / perimeter) % n]);
        }
        for (int x = x1 - 1; x >= x0; x--) {
            g.fill(x, y1, x + 1, y1 + 1, ring[(int) ((long) i++ * n / perimeter) % n]);
        }
        for (int y = y1 - 1; y > y0; y--) {
            g.fill(x0, y, x0 + 1, y + 1, ring[(int) ((long) i++ * n / perimeter) % n]);
        }
    }

}
