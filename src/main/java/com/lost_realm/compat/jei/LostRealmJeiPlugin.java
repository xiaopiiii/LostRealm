package com.lost_realm.compat.jei;

import java.util.ArrayList;
import java.util.List;

import com.lost_realm.util.HiddenItems;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * 把「彩蛋物品」从 JEI 里彻底摘掉。
 *
 * <h2>为什么只做物品隐藏不够</h2>
 * JEI 的物品列表来自「创造标签页 + Item 注册表 + Block 注册表」，
 * 与本模组手写的标签页无关 —— 所以光是不往创造栏里 accept，JEI 照样能搜到。
 * 反过来，只藏物品不藏配方，又会留下"产物是空白的合成表"，
 * 玩家点进去只会觉得这模组坏了。
 *
 * <h2>两个时机</h2>
 * <ul>
 *   <li>{@code registerRecipes}：物品列表还没冻结，在这里摘最干净；</li>
 *   <li>{@code onRuntimeAvailable}：配方要等世界加载后才有实例，只能在这里藏。</li>
 * </ul>
 *
 * <p>本类只在 JEI 存在时被加载（{@code @JeiPlugin} 由 JEI 自己扫描），
 * 未安装 JEI 不会触发任何类加载，也就不需要额外做兼容判断。
 */
@JeiPlugin
public final class LostRealmJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID =
            new ResourceLocation("lost_realm", "hidden_items");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<ItemStack> hidden = HiddenItems.stacks();
        if (hidden.isEmpty()) {
            return;
        }
        // 从物品列表里摘掉 —— 之后按名字搜索也搜不到
        registration.getIngredientManager()
                .removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, hidden);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        // 把 runtime 交给变体折叠器 —— 它需要 ingredient manager 做增删、
        // 需要 ingredient list overlay 做点击命中判定。null 检查在那边做。
        com.lost_realm.qol.jei.JeiVariantFold.onRuntimeAvailable(runtime);

        List<ResourceLocation> ids = HiddenItems.recipeIds();
        if (ids.isEmpty()) {
            return;
        }
        if (Minecraft.getInstance().level == null) {
            return;
        }
        RecipeManager manager = Minecraft.getInstance().level.getRecipeManager();

        // 彩蛋配方一律走工作台；不是工作台类型的直接跳过，
        // 否则泛型对不上（hideRecipes 要求 Collection<R> 与 RecipeType<R> 一致）。
        List<CraftingRecipe> recipes = new ArrayList<>(ids.size());
        for (ResourceLocation id : ids) {
            manager.byKey(id)
                    .filter(r -> r.getType() == net.minecraft.world.item.crafting.RecipeType.CRAFTING)
                    .map(r -> (CraftingRecipe) r)
                    .ifPresent(recipes::add);
        }
        if (recipes.isEmpty()) {
            return;
        }
        // 注意：hideRecipes 要的是 JEI 自己的 RecipeType，不是原版那个同名类
        runtime.getRecipeManager().hideRecipes(mezz.jei.api.constants.RecipeTypes.CRAFTING, recipes);
    }
}
