package com.lost_realm.boss.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

/**
 * 遗落本源 —— Boss 掉落的核心成长材料。
 *
 * <h2>为什么它这么重要</h2>
 * 本模组的勋章等级上限是 30。玩家练满之后,整套熟练度系统就"结束"了 ——
 * Boss 若只是打一次拿个纪念品,内容就变成一次性的。
 *
 * <p>遗落本源提供了一条<b>持续成长</b>的路径:
 * 使用后把勋章等级上限从 30 提升到 <b>35</b>。
 * 这让"反复挑战终末之影"有了长期意义 ——
 * 每次通关都能让角色的上限更高一点。
 *
 * <h2>为什么是"提升上限"而不是"直接加等级"</h2>
 * 直接加等级会跳过熟练度系统的成长曲线,让练级变得没有意义。
 * 提升上限则是"解锁新的成长空间" —— 玩家仍需自己练上去,
 * 但天花板被抬高了。这更符合 RPG 的长期养成逻辑。
 *
 * <h2>使用限制</h2>
 * 必须在四勋章全部达到 30 级(当前上限)时才能使用 ——
 * 否则"提前囤积、一次爆涨"会破坏节奏。
 */
public class RealmSourceItem extends Item {

    /** 每次使用提升的等级上限。 */
    private static final int MAX_LEVEL_BONUS = 5;

    /** 绝对硬上限,防止无限叠加。 */
    private static final int ABSOLUTE_CAP = 50;

    public RealmSourceItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof net.minecraft.server.level.ServerLevel)
                || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        int currentCap = MedalsCapAccessor.getMedalLevelCap();
        // 已达硬上限 → 不可再用
        if (currentCap >= ABSOLUTE_CAP) {
            sp.displayClientMessage(Component.translatable(
                    "message.lost_realm.realm_source.at_cap", ABSOLUTE_CAP), true);
            return InteractionResultHolder.fail(stack);
        }

        // 要求四勋章全部满级(当前上限)—— 保证成长节奏不被跳过
        for (com.lost_realm.skill.MedalType t : com.lost_realm.skill.MedalType.values()) {
            int lv = com.lost_realm.boss.BossProgressionHooks.levelOf(sp, t);
            if (lv < currentCap) {
                sp.displayClientMessage(Component.translatable(
                        "message.lost_realm.realm_source.not_maxed",
                        Component.translatable(t.displayName() + ".name"),
                        lv, currentCap), true);
                return InteractionResultHolder.fail(stack);
            }
        }

        // 提升上限
        int next = Math.min(ABSOLUTE_CAP, currentCap + MAX_LEVEL_BONUS);
        MedalsCapAccessor.setMedalLevelCap(next);
        if (!sp.isCreative()) {
            stack.shrink(1);
        }
        level.playSound(null, sp.blockPosition(),
                net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.6F);
        sp.displayClientMessage(Component.translatable(
                "message.lost_realm.realm_source.used", next), false);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lost_realm.realm_source.tip.1",
                MAX_LEVEL_BONUS).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.lost_realm.realm_source.tip.2")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * 等级上限访问器 —— 与勋章系统的接口。
     *
     * <p>放在内部类是为了明确"这是 Bootstrap 对勋章系统的写入点"。
     * 实际实现委托给 {@code MedalsConfig} 中的等级上限配置项,
     * 运行期动态调整(而非硬编码常量)。
     */
    private static final class MedalsCapAccessor {

        /** 当前勋章等级上限。 */
        static int getMedalLevelCap() {
            // 从配置读取 —— 默认 30,可被遗落本源提升
            return com.lost_realm.boss.RealmCapConfig.currentCap();
        }

        /** 设置新的等级上限。 */
        static void setMedalLevelCap(int cap) {
            com.lost_realm.boss.RealmCapConfig.setCap(cap);
        }
    }
}
