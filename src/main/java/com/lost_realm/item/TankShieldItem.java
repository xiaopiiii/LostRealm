package com.lost_realm.item;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import com.lost_realm.config.MedalsConfig;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * 「盾」类坦克饰品（九枚 + 九合一终极盾）。
 *
 * <p>纯被动饰品：效果全部在 {@code event/TankShieldHandler} 里按需判定，
 * 物品本身不施加任何属性修饰符 —— 所以这里不重写 {@code onEquip} / {@code curioTick}，
 * 唯一自己管的是 {@link #canEquip}：同一种盾只能戴一枚。
 *
 * <p>唯一的例外是「吞噬盾」：它的成长记在<b>物品自己的 NBT</b> 上
 * （摘下不清空；换一面新盾则从头开始），见 {@link #storedSpecies}。
 */
public class TankShieldItem extends Item implements ICurioItem {

    /** 吞噬盾：已击杀过的生物种类，存成一行字符串表。 */
    public static final String TAG_SPECIES = "lr_devoured_species";

    private final TankShieldType type;

    public TankShieldItem(TankShieldType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public TankShieldType shieldType() {
        return type;
    }

    /**
     * 阻止同一枚盾戴两遍。
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
                handler.findCurios(s -> s.getItem() instanceof TankShieldItem shield
                                && shield.shieldType() == type)
                        .stream()
                        .allMatch(found -> found.stack() == stack
                                || found.stack().getItem() == stack.getItem())
        ).orElse(true);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.lost_realm.charm.require_tank")
                .withStyle(ChatFormatting.DARK_GRAY));

        // 终极盾用金色，普通盾用灰 —— 一眼分出哪面是九合一
        ChatFormatting color = type.isEternal() ? ChatFormatting.GOLD : ChatFormatting.GRAY;
        for (Component line : type.tips()) {
            tooltip.add(line.copy().withStyle(color));
        }

        if (type == TankShieldType.DEVOURING) {
            double bonus = devourBonus(stack);
            tooltip.add(Component.translatable("item.lost_realm.devouring_shield.progress",
                    storedSpecies(stack).size(),
                    (int) bonus,
                    MedalsConfig.SHIELD_DEVOUR_MAX.get().intValue())
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    /**
     * 这面盾当前提供的最大生命加成。
     *
     * <p><b>不单独存一份</b>，而是「已吞噬的物种数 × 每次加成」实时算出来 ——
     * 这样改配置能对已有存档立刻生效，也不会出现"数字和列表对不上"的漂移。
     */
    public static double devourBonus(ItemStack stack) {
        int species = storedSpecies(stack).size();
        double raw = species * MedalsConfig.SHIELD_DEVOUR_PER_KILL.get();
        return Math.min(raw, MedalsConfig.SHIELD_DEVOUR_MAX.get());
    }

    /** 读取这面盾上记录的「已吞噬物种」（entity type id）。 */
    public static Set<String> storedSpecies(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_SPECIES, Tag.TAG_LIST)) {
            return new HashSet<>();
        }
        Set<String> out = new HashSet<>();
        var list = tag.getList(TAG_SPECIES, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            out.add(list.getString(i));
        }
        return out;
    }

    /** 写回已吞噬物种。 */
    public static void storeSpecies(ItemStack stack, Set<String> species) {
        ListTag list = new ListTag();
        species.stream().sorted().forEach(s -> list.add(StringTag.valueOf(s)));
        stack.getOrCreateTag().put(TAG_SPECIES, list);
    }
}
