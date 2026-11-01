package com.lost_realm.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.EnchantmentMenu;

/**
 * {@link EnchantmentMenu} 访问器 Mixin。
 *
 * <h2>为什么本项目必须用 {@code @Accessor} 而不是 {@code @Shadow}</h2>
 * 本项目的 Mixin 注解处理器<b>只为 {@code @Accessor} / {@code @Invoker} 生成字段映射,
 * 对 {@code @Shadow} 字段一律不生成</b>。用 {@code @Shadow} 的类在
 * {@code reobfJar} 后 refmap 里会缺少对应条目,运行时 Mixin 拿字面量名字找不到目标,
 * 直接抛 {@code InvalidMixinException} → <b>启动崩溃</b>。
 *
 * <p>因此全项目约定:访问目标类字段一律走 {@code @Accessor} / {@code @Invoker}。
 *
 * <ul>
 *   <li>{@link #medals$getEnchantmentSeedSlot()} —— F17「刷新附魔」改写附魔种子并重算候选</li>
 *   <li>{@link #medals$getEnchantSlots()} —— F18「附魔台保留青金石」取输入容器</li>
 * </ul>
 */
@Mixin(EnchantmentMenu.class)
public interface EnchantmentMenuAccessor {

    @Accessor("enchantmentSeed")
    DataSlot medals$getEnchantmentSeedSlot();

    /**
     * 附魔菜单的输入容器(槽 0 = 待附魔物品,槽 1 = 青金石)。
     *
     * <p>原 {@code EnchantmentMenuMixin} 用 {@code @Shadow} 取此字段导致启动崩溃,
     * 现统一改由本访问器暴露(详见 {@link EnchantmentMenuMixin} 类注释)。
     */
    @Accessor("enchantSlots")
    Container medals$getEnchantSlots();
}
