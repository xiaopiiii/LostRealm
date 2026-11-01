package com.lost_realm.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;

import com.lost_realm.config.MedalsConfig;

/**
 * {@link EnchantmentMenu} 注入:<b>F18 附魔台保留青金石</b>。
 *
 * <p>原版 {@code removed(Player)} 会把未使用的青金石退还/丢弃。这里在方法入口
 * 先把青金石槽(slot 1)的物品塞回玩家背包,避免其随界面关闭而流失。
 *
 * <p>注:F20「预览全部附魔」在<b>客户端</b> {@code EnchantmentScreenMixin} 中实现,
 * 因为原版服务端已把 3 个槽的 {@code enchantClue/levelClue} 全部算好并同步,
 * 限制仅存在于客户端「非悬停槽只画 ???」的渲染逻辑。
 *
 * <h2>⚠️ 为什么不在这里写 {@code @Shadow},改用 {@link EnchantmentMenuAccessor}</h2>
 * 本类原先声明了 {@code @Shadow private Container enchantSlots;},结果<b>正式环境启动即崩</b>:
 *
 * <pre>
 * [FATAL]: Mixin apply failed ...:EnchantmentMenuMixin
 *   -&gt; net.minecraft.world.inventory.EnchantmentMenu:
 *   InvalidMixinException: &#64;Shadow field enchantSlots was not located in
 *   the target class net.minecraft.world.inventory.EnchantmentMenu
 * </pre>
 *
 * 需要强调:{@code enchantSlots} <b>确实是 {@code EnchantmentMenu} 自己声明的字段</b>
 * (SRG 名 {@code n},见 {@code client_mappings.txt}),
 * 所以这<b>不是</b>「继承字段找不到 owner」的问题。
 * 真正的原因是:本项目的 Mixin 注解处理器
 * <b>只为 {@code @Accessor} / {@code @Invoker} 生成字段映射,对 {@code @Shadow} 字段一律不生成</b> ——
 * 产出的 {@code lost_realm.refmap.json} 里该 Mixin 只有 {@code removed} 一条方法映射,
 * {@code enchantSlots} 完全缺失,于是运行时 Mixin 拿着字面量名字找不到目标而 APPLY 失败。
 *
 * <p>佐证:本项目其余三处字段访问
 * ({@link EnchantmentMenuAccessor}、{@link MerchantMenuAccessor}、
 * {@code client.AbstractContainerScreenAccessor}) <b>全部使用 {@code @Accessor}</b>,
 * 它们的 refmap 条目都完整存在。因此约定:
 * <b>本项目一律用 {@code @Accessor} / {@code @Invoker} 访问目标类成员,禁用 {@code @Shadow}</b>。
 *
 * <p>调用方式:{@code ((EnchantmentMenuAccessor) (Object) this).medals$getEnchantSlots()}
 * —— 因为本类的超类是 {@code Object}(Mixin 的 {@code abstract class} 形态),
 * 不能直接 {@code this.} 调用接口方法,必须先转 {@code Object} 再转访问器接口。
 */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin {

    @Inject(method = "removed", at = @At("HEAD"))
    private void medals$keepLapis(Player player, CallbackInfo ci) {
        if (!MedalsConfig.QOL_KEEP_LAPIS.get()) {
            return;
        }
        // 经由 @Accessor 拿容器 —— 不使用 @Shadow,见类注释说明
        net.minecraft.world.Container enchantSlots =
                ((EnchantmentMenuAccessor) (Object) this).medals$getEnchantSlots();

        // 把青金石槽(slot 1)物品归还玩家背包
        ItemStack lapis = enchantSlots.getItem(1);
        if (!lapis.isEmpty()) {
            if (!player.getInventory().add(lapis)) {
                player.drop(lapis, false);
            }
            enchantSlots.setItem(1, ItemStack.EMPTY);
        }
    }
}
