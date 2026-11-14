package com.lost_realm.mixin.jei;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import mezz.jei.gui.ingredients.IngredientFilter;
import mezz.jei.gui.overlay.elements.IElement;

import com.lost_realm.qol.jei.JeiVariantFold;

/**
 * 让同一折叠组的成员在 JEI 列表里<b>紧跟着代表格</b>排列。
 *
 * <h2>为什么必须在这里做</h2>
 * 折叠是"收起时把成员从列表摘掉、展开时加回来"（JEI 公开 API 只有这套）。
 * 但 {@code addIngredientsAtRuntime} 加回来的物品会按当前排序落到各自的位置，
 * 不会跟在代表格后面 —— 这就是"展开后东西跑到别处去了"的原因。
 *
 * <p>JEI 最终给列表用的就是 {@link IngredientFilter#getElements()} 的返回值，
 * 在它返回前把顺序重排一遍，分页、点击、拖拽、搜索全部基于这份新顺序，
 * 行为一致，只是成员被挪到了代表格后面。
 *
 * <h2>为什么注入 RETURN 是安全的</h2>
 * 这个方法体里 0 号槽全程是 {@code this}（没有循环复用槽位，见字节码：
 * 只有 astore_1 / astore_2）。beta54 那次事故的教训是"注入点必须选
 * this 一定还在的位置"，RETURN 在这里满足。
 *
 * <p>返回值是 {@code Stream.toList()} 产生的<b>不可变列表</b>，所以必须
 * {@code setReturnValue} 换一个新列表，不能原地改。
 *
 * <p>{@code require = 0}：JEI 换版本、方法签名变了就安静跳过，不拦启动。
 */
@Mixin(targets = "mezz.jei.gui.ingredients.IngredientFilter", remap = false)
public class IngredientFilterMixin {

    @Inject(method = "getElements", at = @At("RETURN"), cancellable = true,
            remap = false, require = 0)
    private void lostrealm$reorderForFold(CallbackInfoReturnable<List<IElement<?>>> cir) {
        List<IElement<?>> in = cir.getReturnValue();
        List<IElement<?>> out = JeiVariantFold.reorderForFold(in);
        if (out != null && out != in) {
            cir.setReturnValue(out);
        }
    }
}
