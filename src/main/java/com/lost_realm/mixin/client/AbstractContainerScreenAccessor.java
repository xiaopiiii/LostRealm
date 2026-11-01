package com.lost_realm.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * 访问器 Mixin:暴露 {@link AbstractContainerScreen} 的受保护字段
 * {@code leftPos} / {@code topPos},供 {@link EnchantmentScreenMixin} 计算绘制坐标。
 *
 * <p>之所以不直接在 {@code EnchantmentScreenMixin} 上写 {@code @Shadow}:
 * 这两个字段实际声明在父类 {@link AbstractContainerScreen} 中,
 * 直接 {@code @Shadow} 会导致 Mixin 注解处理器无法解析其 SRG 映射(产生告警并在
 * {@code defaultRequire=1} 下可能导致启动期失败)。改为在父类上生成访问器最为稳妥。
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("leftPos")
    int medals$getLeftPos();

    @Accessor("topPos")
    int medals$getTopPos();
}
