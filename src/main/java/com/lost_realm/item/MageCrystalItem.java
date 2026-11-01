package com.lost_realm.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * 「晶石」类法师饰品（九枚 + 九合一终极）。
 *
 * <p>纯被动饰品：效果全部在 {@code event/MageCrystalHandler} 里按需判定，
 * 物品本身不施加任何属性修饰符 —— 唯一自己管的是 {@link #canEquip}：同一种晶石只能戴一枚。
 */
public class MageCrystalItem extends Item implements ICurioItem {

    private final MageCrystalType type;

    public MageCrystalItem(MageCrystalType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public MageCrystalType crystalType() {
        return type;
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        var living = slotContext.entity();
        if (living == null) {
            return true;
        }
        return CuriosApi.getCuriosInventory(living).map(handler ->
                handler.findCurios(s -> s.getItem() instanceof MageCrystalItem crystal
                                && crystal.crystalType() == type)
                        .stream()
                        .allMatch(found -> found.stack() == stack
                                || found.stack().getItem() == stack.getItem())
        ).orElse(true);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.lost_realm.charm.require_mage")
                .withStyle(ChatFormatting.DARK_GRAY));

        ChatFormatting color = type.isSovereign() ? ChatFormatting.GOLD : ChatFormatting.GRAY;
        for (Component line : type.tips()) {
            tooltip.add(line.copy().withStyle(color));
        }
    }
}
