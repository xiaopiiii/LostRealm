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
 * 箭类饰品（药水箭 / 爆炸箭 / 贯穿之箭 / 反重力之箭 / 星辰贯穿之箭）。
 *
 * <p>纯被动饰品：「效果」全部在 {@code event/ArrowCharmHandler} 里按需判定，
 * 物品本身不施加任何属性修饰符，所以不需要 {@code onEquip}/{@code onUnequip}/
 * {@code curioTick} —— 那三个方法在这里重写只会是空壳。
 *
 * <p>唯一需要自己管的是 {@link #canEquip}：同一种箭只能戴一枚。
 */
public class ArrowCharmItem extends Item implements ICurioItem {

    private final ArrowCharmType type;

    public ArrowCharmItem(ArrowCharmType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public ArrowCharmType charmType() {
        return type;
    }

    /**
     * 是否允许装备。
     *
     * <p>只做一件事：阻止同一枚饰品戴两遍。同种箭叠着戴没有任何额外收益，
     * 却会让「命中时触发」这类判定在两侧各算一次（或让效果判定变得含糊）。
     * 「已佩戴的那一枚是它自己」这种情况必须放行，否则取下来再戴上会被拒绝。
     */
    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        var living = slotContext.entity();
        if (living == null) {
            return true;
        }
        return CuriosApi.getCuriosInventory(living).map(handler ->
                handler.findCurios(s -> s.getItem() instanceof ArrowCharmItem charm
                                && charm.charmType() == type)
                        .stream()
                        .allMatch(found -> found.stack() == stack
                                || found.stack().getItem() == stack.getItem())
        ).orElse(true);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.lost_realm.arrow.require_archer")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.lost_realm." + type.id() + ".tip", type.tipArgs())
                .withStyle(ChatFormatting.GRAY));
    }
}
