package com.lost_realm.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;

import com.lost_realm.config.MedalsConfig;

/**
 * {@link Item} 注入:<b>G21~G23 放宽若干物品的堆叠上限</b>。
 *
 * <p>已实现:药水 → 16、不死图腾 → 16、末影珍珠 → 64。
 * <p><b>附魔书堆叠已按需求取消</b> —— 原版判断"两本能否合并"用的是静态方法
 * {@code ItemStack.isSameItemSameTags},它表达的是整块 NBT「完全相等」的语义,
 * 被"丢弃重复物品"等多个功能共用;改写它会造成连带影响,故不做。
 *
 * <h2>为什么选无参的 {@code getMaxStackSize()}</h2>
 * {@code Item} 上有两个同名方法:
 * <ul>
 *   <li>{@code getMaxStackSize()} —— <b>原版</b>方法,SRG 名为 {@code m_41753_},
 *       返回构造时写死的 {@code maxStackSize} 字段。虽然带 {@code final},
 *       但 <b>Mixin 的 {@code @Inject} 是改写方法体字节码</b>,不受 final 限制
 *       (受限的只有 {@code @Overwrite}),因此可以安全注入。</li>
 *   <li>{@code getMaxStackSize(ItemStack)} —— <b>Forge 新增</b> 的虚方法。
 *       在 1.20.1 的 SRG 映射表里 <b>查不到对应名称</b>,写进 Mixin
 *       会导致 refmap 无法解析、正式环境注入失败,因此不采用。</li>
 * </ul>
 *
 * <p>注入 {@code m_41753_()I} 之后,{@code ItemStack.getMaxStackSize()}
 * (它内部会再调一次同签名方法)与 {@code ItemStack.isStackable()}
 * (内部即 {@code getMaxStackSize() > 1})都会自动跟着变,无需另外处理。
 *
 * <h2>为何不用 @ModifyReturnValue</h2>
 * {@code @Inject(HEAD) + cir.setReturnValue(...)} 与 Mixin 版本、refmap 配置
 * 的耦合最小,在 ForgeGradle 6 环境下行为最可预期。
 */
@Mixin(Item.class)
public abstract class ItemMaxStackSizeMixin {

    @Inject(method = "getMaxStackSize()I", at = @At("HEAD"), cancellable = true)
    private void medals$customMaxStackSize(CallbackInfoReturnable<Integer> cir) {
        // 无参方法拿不到具体 ItemStack,只能按「物品类型」判定。
        // 本类所有目标都是「整类统一处理」的物品,因此足够精确。
        Item self = (Item) (Object) this;

        // ---- G21 药水 → 16 ----
        // PotionItem 同时覆盖普通 / 喷溅 / 滞留三种药水形态,一次判定即可。
        if (MedalsConfig.QOL_STACK_POTION.get() && self instanceof PotionItem) {
            cir.setReturnValue(MedalsConfig.STACK_POTION_MAX);
            return;
        }

        // ---- G22 不死图腾 → 16 ----
        if (MedalsConfig.QOL_STACK_TOTEM.get() && self == Items.TOTEM_OF_UNDYING) {
            cir.setReturnValue(MedalsConfig.STACK_TOTEM_MAX);
            return;
        }

        // ---- G23 末影珍珠 → 64 ----
        if (MedalsConfig.QOL_STACK_ENDER_PEARL.get() && self == Items.ENDER_PEARL) {
            cir.setReturnValue(MedalsConfig.STACK_ENDER_PEARL_MAX);
        }
    }
}
