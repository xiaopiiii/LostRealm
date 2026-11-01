package com.lost_realm.boss.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

/**
 * 遗落碎片 —— 任务链第一阶段的产物。
 *
 * <h2>为什么要单独成类</h2>
 * 它需要一条<b>说明性 tooltip</b>:告诉玩家"这块碎片该用在哪"。
 * 集成包的任务链若没有明确指引,玩家会拿着一堆不知道用途的材料。
 * tooltip 是成本最低的引导方式。
 *
 * <h2>碎片来源的三种变体</h2>
 * 用 {@link Variant} 区分"来自哪个 Boss",仅在 tooltip 上体现差异,
 * 不影响合成 —— 这样既满足叙事需要,又不增加合成的复杂度。
 */
public class FragmentItem extends Item {

    /** 碎片来源。 */
    public enum Variant {
        /** 来自整合包的"深渊"系 Boss。 */
        ABYSS("abyss"),
        /** 来自"天穹"系 Boss。 */
        SKY("sky"),
        /** 来自"终末"系 Boss。 */
        END("end");

        private final String id;

        Variant(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public FragmentItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lost_realm.lost_realm_fragment.tip")
                .withStyle(ChatFormatting.GRAY));
    }

    /** tooltip 用的语言键(按变体)。 */
    public static String tooltipKey(Variant v) {
        return "item.lost_realm.lost_realm_fragment.variant." + v.id();
    }
}
