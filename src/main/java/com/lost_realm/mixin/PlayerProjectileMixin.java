package com.lost_realm.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import com.lost_realm.config.MedalsConfig;

/**
 * {@link Player} 注入:<b>G24 无限附魔的弓在背包无箭时也能射击</b>,
 * 且射出的箭为<b>不可拾取</b>的"创造模式箭"。
 *
 * <h2>为什么必须钩 {@code getProjectile}</h2>
 * 看 {@code BowItem.releaseUsing} 的字节码顺序:
 * <pre>
 *   ItemStack ammo = player.getProjectile(stack);   // ① 取箭
 *   if (ammo.isEmpty()) { ...不射,直接返回... }      // ② 无箭就射不出去
 *   ArrowItem.isInfinite(ammo, bow, player)         // ③ 有箭时才据此决定是否消耗
 * </pre>
 * 原版的「无限附魔」只作用在 <b>③</b> —— 它阻止的是<b>箭矢被消耗</b>,
 * 前提是<b>本来就得有箭</b>。所以"没箭也能射"这件事,
 * 必须在 <b>①</b> 上解决:让本方法在「无限弓 + 无箭」时返回一枚非空哨兵。
 *
 * <h2>哨兵为什么取普通箭 —— 两层机制协同工作</h2>
 * 需求要求:背包没箭也能射,且射出的箭是<b>创造模式箭</b>(落地即消失、不可捡回)。
 * 经过对字节码的完整推演,这里需要两个机制<b>配合</b>才成立:
 *
 * <h3>第一层:哨兵本身必须是"普通箭"({@code Items.ARROW})</h3>
 * 因为 {@code ArrowItem.isInfinite} 的字节码是这样的:
 * <pre>
 *   int lvl = EnchantmentHelper.getItemEnchantmentLevel(INFINITY_ARROWS, bow);
 *   if (lvl > 0 &amp;&amp; this.getClass() == ArrowItem.class) return true;
 *   return false;
 * </pre>
 * 注意 {@code this.getClass() == ArrowItem.class} —— 要求<b>严格等于</b> {@code ArrowItem}。
 * 所以 {@code ArrowItem.isInfinite} 只在 {@code Items.ARROW} 上返回 true,
 * 在 {@code SpectralArrowItem} 等子类上恒为 false。
 *
 * <p>这一层很关键:它意味着<b>本次射击不会被原版判为"无限箭"</b>,
 * 因而 {@code BowItem} 会走消耗分支,把哨兵箭 {@code shrink} 掉。
 * 而哨兵是<b>刚 new 出来的独立对象</b>,不属于玩家背包里任何格子,
 * 所以这次 {@code shrink} 不产生任何实际损耗 ——
 * 既不会凭空多出箭,也不会误伤玩家的真实箭矢。
 *
 * <h3>第二层:射出的箭要变成"不可拾取"</h3>
 * 上一步"没被判定为无限箭"带来了一个副作用:
 * {@code BowItem.releaseUsing} 偏移 373~411 的原版判定是
 * <pre>
 *   if (instabuild || ammo.is(SPECTRAL_ARROW) || ammo.is(TIPPED_ARROW))
 *       arrow.pickup = CREATIVE_ONLY;
 * </pre>
 * 哨兵是普通箭、玩家又不是创造模式,因此这个 if <b>不成立</b>,
 * 箭会落到默认的 {@code ALLOWED} —— 也就是<b>可被捡回</b>,
 * 与"落地即消失"不符。
 *
 * <p>该 {@code putfield} 是<b>内联</b>在 {@code BowItem} 里的,
 * 并不经过 {@code AbstractArrow.setOwner},所以钩 {@code setOwner} 不会触发;
 * 而 {@code AbstractArrow#getPickupItem()} 在 1.20.1 的 SRG 表里只映射为
 * 单字符 {@code p},属 Forge/Parchment 追加成员,钩它会让 refmap 解析不可靠。
 * 因此改由 {@link BowItemMixin} 在 {@code shootFromRotation} 返回处
 * 统一把这枚箭的 {@code pickup} 置为 {@code DISALLOWED}。
 *
 * <p><b>为什么判定条件是"背包无箭"</b>:只要玩家背包里还有普通箭,
 * {@code getProjectile} 就会命中原版分支、返回真实箭矢,
 * 本注入连哨兵都不会创建。因此"背包无箭"恰好等价于"这一枚是哨兵箭",
 * 无需额外打标记。
 */
@Mixin(Player.class)
public abstract class PlayerProjectileMixin {

    /** 判定「当前手持的这件武器是否带无限附魔」。 */
    private static boolean medals$hasInfinity(ItemStack weapon) {
        if (weapon.isEmpty()) {
            return false;
        }
        return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, weapon) > 0;
    }

    @Inject(method = "getProjectile", at = @At("RETURN"), cancellable = true)
    private void medals$infiniteBowNoArrow(ItemStack weapon, CallbackInfoReturnable<ItemStack> cir) {
        if (!MedalsConfig.QOL_INFINITE_BOW_NO_ARROW.get()) {
            return;
        }
        // 原版已经找到箭了 —— 什么都不用做,保持原样。
        // 这也保证了"背包里有箭时"的行为与原版完全一致。
        ItemStack original = cir.getReturnValue();
        if (original != null && !original.isEmpty()) {
            return;
        }
        // 背包无箭:仅当这件武器带「无限」附魔时才放行
        if (!medals$hasInfinity(weapon)) {
            return;
        }
        // 哨兵必须恰好是 Items.ARROW 的实例(见类注释"第一层"),
        // 这样本次射击仍走消耗分支,而消耗的只是这个游离对象。
        cir.setReturnValue(new ItemStack(Items.ARROW));
    }
}
