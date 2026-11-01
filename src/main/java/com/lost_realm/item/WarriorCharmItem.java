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
 * 「刃」类战士饰品（横扫 / 双持 / 暴击 / 背刺 / 缴械 / 幽匿 / 蓄能 / 突进）。
 *
 * <p>纯被动饰品：效果全部在 {@code event/WarriorCharmHandler} 或对应 Mixin 里按需判定，
 * 物品本身不施加任何属性修饰符，所以这里不重写 {@code onEquip} / {@code curioTick}。
 *
 * <p>唯一自己管的是 {@link #canEquip}：同一种刃只能戴一枚。
 */
public class WarriorCharmItem extends Item implements ICurioItem {

    private final WarriorCharmType type;

    public WarriorCharmItem(WarriorCharmType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public WarriorCharmType charmType() {
        return type;
    }

    /**
     * 阻止同一枚饰品戴两遍。
     *
     * <p>「已佩戴的那一枚是它自己」必须放行，否则取下来再戴上会被拒绝。
     */
    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        var living = slotContext.entity();
        if (living == null) {
            return true;
        }
        return CuriosApi.getCuriosInventory(living).map(handler ->
                handler.findCurios(s -> s.getItem() instanceof WarriorCharmItem charm
                                && charm.charmType() == type)
                        .stream()
                        .allMatch(found -> found.stack() == stack
                                || found.stack().getItem() == stack.getItem())
        ).orElse(true);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.lost_realm.charm.require_warrior")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.lost_realm." + type.id() + ".tip", type.tipArgs())
                .withStyle(ChatFormatting.GRAY));
    }
}
