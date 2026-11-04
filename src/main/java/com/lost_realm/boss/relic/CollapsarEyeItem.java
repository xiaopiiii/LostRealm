package com.lost_realm.boss.relic;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * <b>坍星·湮灭之眼</b>（法师）—— 乌蠹诺斯遗物。
 *
 * <h2>原设计 → MC</h2>
 * 原设计"最多六个追踪核心，每个核心牵引普通敌人，每 30 帧放一次
 * 140% 伤害范围脉冲，共六次；不能强拉 Boss"。
 *
 * <p>MC 里的落点：右键在脚下放一个<b>引力核心</b>，它会在 6 秒内
 * 持续把周围敌人往中心拽，并每 1.5 秒放一次范围脉冲。
 * 核心数量上限 6 个（与原设计一致）。
 *
 * <h2>★ "不能强拉 Boss"怎么落实</h2>
 * 判断标准是<b>血量上限</b>：超过阈值的生物完全免疫牵引
 * （只吃脉冲伤害）。这样既保住了"清小怪很爽"，
 * 又不会出现"把整合包里的 Boss 拖来拖去"这种滑稽画面。
 */
public class CollapsarEyeItem extends Item {

    /** 同时在场的核心上限。原设计六个。 */
    private static final int MAX_CORES = 6;

    /** 牵引半径（格）。 */
    private static final double PULL_RADIUS = 9.0D;

    /** 牵引强度（每 tick 的位移）。 */
    private static final double PULL_STRENGTH = 0.06D;

    /** 脉冲间隔（tick）。原设计约 1.5 秒。 */
    private static final int PULSE_INTERVAL = 30;

    /** 脉冲伤害。 */
    private static final float PULSE_DAMAGE = 9.0F;

    /** 核心存活（tick）。6 秒。 */
    private static final int CORE_LIFE = 120;

    /**
     * 免疫牵引的血量上限。
     *
     * <p>用血量而不是"是不是 Boss 实体"来判断 —— 因为整合包里的 Boss
     * 来自各种模组，逐个兼容不现实；而"血量大于 200 的东西拖不动"
     * 这条规则对所有模组都成立，也不会误伤任何普通怪。
     */
    private static final float PULL_IMMUNE_HEALTH = 200.0F;

    /** 用组件记录已经放下的核心（世界无关，玩家维度切换会自动失效）。 */
    private static final java.util.Map<java.util.UUID, java.util.Deque<CoreRecord>> CORES =
            new java.util.concurrent.ConcurrentHashMap<>();

    private record CoreRecord(Vec3 pos, long placedAt, net.minecraft.resources.ResourceKey<Level> dim) {}

    public CollapsarEyeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server)) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        Vec3 spot = player.position().add(player.getLookAngle().scale(4.0D));
        java.util.Deque<CoreRecord> cores =
                CORES.computeIfAbsent(player.getUUID(), k -> new java.util.ArrayDeque<>());
        while (cores.size() >= MAX_CORES) {
            cores.pollFirst();
        }
        cores.addLast(new CoreRecord(spot, server.getGameTime(), server.dimension()));

        server.sendParticles(new net.minecraft.core.particles.DustParticleOptions(
                        new org.joml.Vector3f(0.7F, 0.45F, 1.0F), 1.4F),
                spot.x, spot.y + 1.0D, spot.z, 30, 1.6D, 1.6D, 1.6D, 0.05D);
        server.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN,
                SoundSource.PLAYERS, 1.4F, 0.8F);

        player.getCooldowns().addCooldown(this, 12);
        return InteractionResultHolder.success(stack);
    }

    public static void appendTooltip(List<Component> tooltip) {
        tooltip.add(Component.translatable("item.lost_realm.relic.legend")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("item.lost_realm.collapsar_eye.tip",
                        MAX_CORES, (int) PULL_RADIUS)
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        appendTooltip(tooltip);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    // ================================================================
    // 每 tick 的世界侧更新（由事件处理器驱动）
    // ================================================================

    /**
     * 推进所有引力核心。
     *
     * <p>由 {@code UdunosRelicHandler} 的世界 tick 事件每 tick 调用一次。
     * 把逻辑放在这里而不是事件里，是为了让"核心怎么运作"这件事
     * 和物品本身放在一起 —— 以后改数值不用满世界找。
     */
    public static void tickCores(ServerLevel level) {
        if (CORES.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        for (var entry : CORES.entrySet()) {
            java.util.Deque<CoreRecord> cores = entry.getValue();
            cores.removeIf(rec -> now - rec.placedAt() > CORE_LIFE
                    || rec.dim() != level.dimension());
            for (CoreRecord core : cores) {
                applyCore(level, entry.getKey(), core, now);
            }
        }
        CORES.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    private static void applyCore(ServerLevel level, java.util.UUID ownerId,
                                  CoreRecord core, long now) {
        Vec3 center = core.pos();
        AABB area = new AABB(center, center).inflate(PULL_RADIUS);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (target.getUUID().equals(ownerId) || !target.isAlive()) {
                continue;
            }
            if (target instanceof Player) {
                continue;   // 不拉玩家
            }
            Vec3 delta = center.subtract(target.position());
            double distance = delta.length();
            if (distance < 1.0E-3D) {
                continue;
            }
            // ★ 大块头拉不动 —— 用血量判断，天然兼容所有模组的 Boss
            if (target.getMaxHealth() <= PULL_IMMUNE_HEALTH) {
                Vec3 pull = delta.normalize().scale(PULL_STRENGTH);
                Vec3 motion = target.getDeltaMovement();
                target.setDeltaMovement(motion.x + pull.x, motion.y, motion.z + pull.z);
                target.hurtMarked = true;
            }
            // 脉冲伤害对所有目标一视同仁（包括 Boss）
            if ((now - core.placedAt()) % PULSE_INTERVAL == 0) {
                target.hurt(level.damageSources().magic(), PULSE_DAMAGE);
            }
        }

        if ((now - core.placedAt()) % 4 == 0) {
            level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(
                            new org.joml.Vector3f(0.65F, 0.4F, 1.0F), 1.1F),
                    center.x, center.y + 0.8D, center.z, 6, 1.2D, 0.6D, 1.2D, 0.02D);
        }
    }
}
