package com.lost_realm.qol.jei;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
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
 * <b>JEI 变体折叠</b> —— 把同一个物品的不同 NBT 变体收成一个格子。
 *
 * <h2>「变体」怎么判定</h2>
 * 同一个 {@link Item}、但 {@link ItemStack#isSameItemSameTags} 不成立的两个栈。
 * 典型例子：不同附魔的附魔书、不同效果的药水、不同染色的皮革甲。
 * 完全相同的两个栈会被去重，不会被当成变体。
 *
 * <h2>折叠是怎么做的</h2>
 * 不碰 JEI 的布局代码 —— 它内部是固定槽位的分页网格，"把一格拆成多格"必须重排整页，
 * 等于重写 {@code IngredientListRenderer}。
 * 这里改用 JEI 的公开 API：
 * <ul>
 *   <li>收起 = {@code removeIngredientsAtRuntime} 把组内非首位变体从物品列表摘掉；</li>
 *   <li>展开 = {@code addIngredientsAtRuntime} 把它们加回来。</li>
 * </ul>
 * 列表由 JEI 自己重新排，搜索、翻页、滚动全部照常工作。
 *
 * <h2>点击冲突怎么处理</h2>
 * 点击折叠组的格子会切换展开状态，代价是那一格不能直接点开配方。
 * 这是需求本身决定的（"点击展开、再点收起"必然占用点击）。
 * 仍然可以选中后用 R / U 键看用途和配方，所以功能没有真正丢失。
 *
 * <p>只认"组内第一个"作为切换手柄 —— 展开后满屏都是变体，
 * 如果每一个都能点收起，用户点到第二个就收起会很困惑。
 */
@Mod.EventBusSubscriber(modid = Medals.MODID, value = Dist.CLIENT)
public final class JeiVariantFold {

    /** 框的配色。按物品 id 的哈希取模选一个，同一物品永远同一个颜色。 */
    private static final int[] FRAME_COLORS = {
            0xFF4FA3E0, 0xFFE0A24F, 0xFF6BD08A, 0xFFD06BC8,
            0xFFE05C5C, 0xFF8A6BE0, 0xFF4FD0C8, 0xFFD0C84F
    };

    private static IJeiRuntime runtime;

    /** 物品 → 该物品的全部变体（首位是代表）。只收录变体数 > 1 的。 */
    private static final Map<Item, List<ItemStack>> GROUPS = new LinkedHashMap<>();

    /** 当前处于展开状态的物品。 */
    private static final Set<Item> EXPANDED = new HashSet<>();

    private static boolean ready;
    private static boolean mouseDown;

    private JeiVariantFold() {}

    /** 由 {@code LostRealmJeiPlugin} 在 JEI 就绪时调用。 */
    public static void onRuntimeAvailable(IJeiRuntime r) {
        runtime = r;
        ready = false;
        GROUPS.clear();
        EXPANDED.clear();
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
            return;
        }
        boolean down = mc.mouseHandler.isLeftPressed();
        if (down && !mouseDown) {
            mouseDown = true;
            handleClick();
        } else if (!down) {
            mouseDown = false;
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
        EXPANDED.clear();
        var manager = runtime.getIngredientManager();
        if (manager == null) {
            return;
        }
        Map<Item, List<ItemStack>> tmp = new LinkedHashMap<>();
        for (ItemStack stack : manager.getAllIngredients(VanillaTypes.ITEM_STACK)) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            List<ItemStack> list = tmp.computeIfAbsent(stack.getItem(), k -> new ArrayList<>());
            // 只收标准数量为 1 的栈；同 NBT 的重复项要丢掉，否则"变体数"会虚高
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
        for (Map.Entry<Item, List<ItemStack>> e : tmp.entrySet()) {
            if (e.getValue().size() > 1) {
                GROUPS.put(e.getKey(), e.getValue());
            }
        }
    }

    /** 默认状态：全部收起，列表里每个物品只留一格。 */
    private static void collapseAll() {
        List<ItemStack> remove = new ArrayList<>();
        for (List<ItemStack> variants : GROUPS.values()) {
            for (int i = 1; i < variants.size(); i++) {
                remove.add(variants.get(i));
            }
        }
        if (!remove.isEmpty()) {
            runtime.getIngredientManager()
                    .removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, remove);
        }
    }

    private static void toggle(Item item) {
        if (EXPANDED.contains(item)) {
            collapse(item);
        } else {
            expand(item);
        }
    }

    private static void expand(Item item) {
        List<ItemStack> variants = GROUPS.get(item);
        if (variants == null || variants.size() < 2) {
            return;
        }
        runtime.getIngredientManager().addIngredientsAtRuntime(
                VanillaTypes.ITEM_STACK, variants.subList(1, variants.size()));
        EXPANDED.add(item);
    }

    private static void collapse(Item item) {
        List<ItemStack> variants = GROUPS.get(item);
        if (variants == null || variants.size() < 2) {
            return;
        }
        runtime.getIngredientManager().removeIngredientsAtRuntime(
                VanillaTypes.ITEM_STACK, variants.subList(1, variants.size()));
        EXPANDED.remove(item);
    }

    /** 功能被关掉时把摘掉的变体全部放回去，不能把玩家的物品列表永久弄短。 */
    private static void restoreAll() {
        List<ItemStack> add = new ArrayList<>();
        for (List<ItemStack> variants : GROUPS.values()) {
            for (int i = 1; i < variants.size(); i++) {
                add.add(variants.get(i));
            }
        }
        if (!add.isEmpty() && runtime != null) {
            runtime.getIngredientManager()
                    .addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, add);
        }
        GROUPS.clear();
        EXPANDED.clear();
        ready = false;
    }

    // ================================================================
    // 点击
    // ================================================================

    private static void handleClick() {
        IIngredientListOverlay overlay = runtime.getIngredientListOverlay();
        if (overlay == null || !overlay.isListDisplayed()) {
            return;
        }
        Optional<ITypedIngredient<?>> under = overlay.getIngredientUnderMouse();
        if (under.isEmpty()) {
            return;
        }
        Optional<ItemStack> stackOpt = under.get().getItemStack();
        if (stackOpt.isEmpty()) {
            return;
        }
        ItemStack stack = stackOpt.get();
        List<ItemStack> variants = GROUPS.get(stack.getItem());
        if (variants == null || variants.size() < 2) {
            return;
        }
        // 只有"代表"那一格能切换，否则展开后满屏变体每格都能点，很容易误收
        if (!ItemStack.isSameItemSameTags(variants.get(0), stack)) {
            return;
        }
        toggle(stack.getItem());
    }

    // ================================================================
    // 渲染（由 mixin 在 IngredientListRenderer.render 末尾调用）
    // ================================================================

    public static void renderFrames(GuiGraphics g, IngredientListRenderer renderer) {
        if (runtime == null || !enabled() || GROUPS.isEmpty()) {
            return;
        }
        List<IngredientListSlot> slots = renderer.getAllSlots();
        if (slots == null || slots.isEmpty()) {
            return;
        }
        var font = Minecraft.getInstance().font;
        for (IngredientListSlot slot : slots) {
            IElement<?> element = slot.getElement();
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
            Item item = stackOpt.get().getItem();
            List<ItemStack> variants = GROUPS.get(item);
            if (variants == null || variants.size() < 2) {
                continue;
            }
            boolean open = EXPANDED.contains(item);
            // 展开后每格都是组员，只有代表那格画"可收起"的框，其余画细框
            boolean anchor = ItemStack.isSameItemSameTags(variants.get(0), stackOpt.get());
            ImmutableRect2i area = slot.getArea();
            drawFrame(g, area, colorFor(item), anchor, open ? 2 : 1);

            if (anchor) {
                String badge = open ? "−" : String.valueOf(variants.size());
                int bx = area.getX() + area.getWidth() - font.width(badge) - 1;
                int by = area.getY() + area.getHeight() - 9;
                g.fill(bx - 1, by - 1,
                        area.getX() + area.getWidth(), area.getY() + area.getHeight(), 0xC0000000);
                g.drawString(font, badge, bx, by, 0xFFFFFFFF, true);
            }
        }
    }

    private static void drawFrame(GuiGraphics g, ImmutableRect2i area, int color,
                                  boolean thick, int weight) {
        int x0 = area.getX();
        int y0 = area.getY();
        int x1 = x0 + area.getWidth();
        int y1 = y0 + area.getHeight();
        for (int i = 0; i < weight; i++) {
            g.fill(x0 + i, y0 + i, x1 - i, y0 + i + 1, color);
            g.fill(x0 + i, y1 - i - 1, x1 - i, y1 - i, color);
            g.fill(x0 + i, y0 + i, x0 + i + 1, y1 - i, color);
            g.fill(x1 - i - 1, y0 + i, x1 - i, y1 - i, color);
        }
    }

    private static int colorFor(Item item) {
        int idx = Math.abs(item.hashCode()) % FRAME_COLORS.length;
        return FRAME_COLORS[idx];
    }
}
