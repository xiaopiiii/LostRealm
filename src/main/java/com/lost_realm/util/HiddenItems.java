package com.lost_realm.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 「彩蛋物品」登记表 —— 注册了，但任何地方都找不到，只能合成。
 *
 * <h2>怎么用</h2>
 * 物品照常 {@code DeferredRegister} 注册，<b>不要</b>往创造标签页里 {@code accept}；
 * 然后在注册阶段调一次：
 *
 * <pre>{@code
 * HiddenItems.hide(BossItems.EASTER_EGG, "easter_egg");
 * }</pre>
 *
 * 第二个参数是配方路径（{@code data/lost_realm/recipes/<这里>.json}），
 * 传 {@code null} 表示只藏物品、不藏配方。
 *
 * <h2>它到底做了什么</h2>
 * <ul>
 *   <li><b>创造栏</b>：本模组的标签页是手写 {@code displayItems} 的，不写就不出现，
 *       不需要这个类配合；</li>
 *   <li><b>JEI</b>：{@code compat/jei/LostRealmJeiPlugin} 会读这里，
 *       调 {@code removeIngredientsAtRuntime} 把物品从 JEI 的物品列表摘掉，
 *       再把对应配方 {@code hideRecipes} 掉 —— 否则会留下"产物空白"的合成表；</li>
 *   <li><b>其它查看器</b>（EMI / REI）：走的是各自的 API，这个类管不到。
 *       若整合包换用它们，需要另写插件。</li>
 * </ul>
 *
 * <h2>注意</h2>
 * 类里<b>不能</b>在静态初始化时构造 {@link ItemStack} —— 物品注册表那时还没好，
 * 而且这个类在服务端也会被加载。{@link #stacks()} 只在 JEI 的客户端阶段调用。
 */
public final class HiddenItems {

    /** 用 Supplier 而不是 Item：注册阶段的物品可能还没被创建。 */
    private static final List<Supplier<? extends Item>> ITEMS = new ArrayList<>();
    private static final List<ResourceLocation> RECIPES = new ArrayList<>();

    private HiddenItems() {}

    /**
     * 登记一个"查不到"的物品。
     *
     * @param item       物品的 RegistryObject / Supplier
     * @param recipePath 配方 id 的路径部分（命名空间固定 lost_realm），没有配方就传 null
     */
    public static void hide(Supplier<? extends Item> item, String recipePath) {
        if (item == null) {
            return;
        }
        ITEMS.add(item);
        if (recipePath != null && !recipePath.isEmpty()) {
            RECIPES.add(new ResourceLocation("lost_realm", recipePath));
        }
    }

    /** JEI 插件用：要摘掉的物品。调用时机是客户端注册阶段，此时物品已就绪。 */
    public static List<ItemStack> stacks() {
        List<ItemStack> out = new ArrayList<>(ITEMS.size());
        for (Supplier<? extends Item> supplier : ITEMS) {
            Item item = supplier.get();
            if (item != null) {
                out.add(new ItemStack(item));
            }
        }
        return out;
    }

    /** JEI 插件用：要藏掉的配方 id。 */
    public static List<ResourceLocation> recipeIds() {
        return Collections.unmodifiableList(RECIPES);
    }
}
