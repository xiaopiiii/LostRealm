package com.lost_realm.item;

import com.lost_realm.config.MedalsConfig;
import com.lost_realm.data.PlayerMedals;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 时间胶囊 —— 一次性消耗品。
 *
 * <p>右键使用后，<b>固定降低主动技能的冷却时长</b>（默认每次 2 秒）：
 * 之后释放技能时，进入冷却的时间比配置值短。这是「冷却上限」的永久减免，
 * <b>不会</b>去扣减此刻正在倒计时的剩余冷却。
 *
 * <h2>三条判定规则</h2>
 * <ol>
 *   <li><b>累计次数用满 → 右键无效</b>，而且不消耗物品 —— 否则玩家会白扔一个。</li>
 *   <li>计数记在玩家数据里（{@link PlayerMedals}），不写在物品上 ——
 *       写物品上的话，把胶囊丢给别人就能重置次数，限制形同虚设。</li>
 * </ol>
 *
 * <h2>减免如何生效</h2>
 * 减免量不落任何存档字段，而是由「累计使用次数 × 每次秒数」实时算出
 * （见 {@link #reductionTicks}），在技能结算冷却的那一刻扣减
 * （见 {@link #applyReduction}）。这样改配置能立刻反映到已有存档上，
 * 也不会出现"减免量算了两遍"的漂移。
 */
public class TimeCapsuleItem extends Item {

    /** 累计生效次数在玩家数据里的键名。 */
    public static final String STATE_KEY = "time_capsule_uses";

    /** 用完后给自己上 10 tick 的物品冷却，避免按住右键连点瞬间把额度全烧光。 */
    private static final int SELF_COOLDOWN_TICKS = 10;

    public TimeCapsuleItem(Properties properties) {
        super(properties);
    }

    // ================================================================
    // 冷却减免（供技能结算处调用）
    // ================================================================

    /** 该玩家累计已获得的冷却减免 tick 数。 */
    public static int reductionTicks(PlayerMedals data) {
        if (data == null) {
            return 0;
        }
        int perUse = Math.max(0, MedalsConfig.TIME_CAPSULE_SHORTEN_SECONDS.get());
        return Math.max(0, data.getState(STATE_KEY)) * perUse * 20;
    }

    /** 该玩家累计已获得的冷却减免秒数（用于提示 / tooltip）。 */
    public static int reductionSeconds(PlayerMedals data) {
        return reductionTicks(data) / 20;
    }

    /**
     * 把技能的原始冷却折算成该玩家实际需要等待的冷却。
     *
     * <p>所有主动技能结算冷却时都应经过这里，否则时间胶囊的减免只对部分技能生效。
     *
     * @param data     玩家数据
     * @param baseTicks 技能配置里的原始冷却（tick）
     * @return 减免后的冷却，最低 0
     */
    public static int applyReduction(PlayerMedals data, int baseTicks) {
        if (baseTicks <= 0) {
            return 0;
        }
        return Math.max(0, baseTicks - reductionTicks(data));
    }

    // ================================================================
    // 使用
    // ================================================================

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!(player instanceof ServerPlayer server)) {
            // 客户端只负责挥手动画，真正的判定全在服务端
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        PlayerMedals data = PlayerMedals.of(server);

        int maxUses = Math.max(1, MedalsConfig.TIME_CAPSULE_MAX_USES.get());
        int used = data.getState(STATE_KEY);
        if (used >= maxUses) {
            server.displayClientMessage(
                    Component.translatable("item.lost_realm.time_capsule.limit", maxUses), true);
            return InteractionResultHolder.fail(stack);
        }

        data.setState(STATE_KEY, used + 1);
        stack.shrink(1);

        int totalSeconds = reductionSeconds(data);
        int capSeconds = maxUses * Math.max(0, MedalsConfig.TIME_CAPSULE_SHORTEN_SECONDS.get());

        level.playSound(null, server.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 1.0F, 1.5F);
        server.displayClientMessage(Component.translatable(
                "item.lost_realm.time_capsule.used", totalSeconds, capSeconds), true);
        server.getCooldowns().addCooldown(this, SELF_COOLDOWN_TICKS);

        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lost_realm.time_capsule.tip.1",
                MedalsConfig.TIME_CAPSULE_SHORTEN_SECONDS.get()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.lost_realm.time_capsule.tip.2",
                MedalsConfig.TIME_CAPSULE_MAX_USES.get()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.lost_realm.time_capsule.tip.3")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
