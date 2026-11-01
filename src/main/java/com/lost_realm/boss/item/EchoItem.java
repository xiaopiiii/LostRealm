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
 * 终末之影的残响 —— 30% 掉落的纪念性战利品。
 *
 * <h2>定位:情绪价值 > 数值价值</h2>
 * 它不提供任何战斗加成,只是"我打赢了终末之影"的证明物。
 * 在终局内容里,这种纪念品的价值往往高于边际收益递减的数值装备 ——
 * 因为它承载的是<b>体验的凭证</b>,而非又一次的属性增长。
 *
 * <h2>为什么不做成可放置的装饰方块</h2>
 * 那需要额外的方块、方块实体与渲染器。当前的物品形态
 * 配合 tooltip 已足够表达"战利品"的语义;
 * 若后续需要,可以把它改造为 BlockItem(接口已经留好)。
 */
public class EchoItem extends Item {

    public EchoItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lost_realm.warden_echo.tip.1")
                .withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("item.lost_realm.warden_echo.tip.2")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    /** 残响带附魔光效 —— 纯视觉,凸显其纪念品地位。 */
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
