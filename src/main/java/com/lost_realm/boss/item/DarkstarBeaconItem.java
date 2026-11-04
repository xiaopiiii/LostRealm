package com.lost_realm.boss.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.lost_realm.boss.BossEntities;
import com.lost_realm.boss.udunos.UdunosBoss;
import com.lost_realm.boss.udunos.UdunosTuning;

/**
 * <b>黯星信标</b> —— 召唤遗落之主·乌蠹诺斯的道具。
 *
 * <h2>原设计 → MC 的转译</h2>
 * 泰拉瑞亚版是「月后夜晚在地表或太空使用」—— 那两个限制是泰拉瑞亚的世界观
 * （月后 = 进度门槛，夜晚/太空 = 氛围）。MC 里没有"月后"，所以门槛改由
 * <b>合成成本</b>承担（见配方）；使用地点则不做限制 ——
 * 玩家拿得出这东西，本身就说明他准备好了。
 *
 * <h2>为什么"不消耗"却还要加冷却</h2>
 * 原设计明确写着"不消耗的召唤物"。但真的不消耗又不管制，玩家右键连点
 * 就能刷出一屏幕的巨虫，服务器当场去世。所以这里：
 * <ul>
 *   <li>物品<b>不消耗</b>（保持原设计的意图）</li>
 *   <li>加一个冷却，防止连点</li>
 *   <li>并且检查附近<b>是否已经有一只</b> —— 重复召唤直接拒绝，不浪费玩家的操作</li>
 * </ul>
 */
public class DarkstarBeaconItem extends Item {

    /** 重复召唤的判定半径（格）。 */
    /**
     * 判定"是否已有乌蠹诺斯"的半径（格）。
     *
     * <h2>为什么是 512 而不是原来的 160</h2>
     * 这条虫是活的：巡游时会游出很远，实测轨迹总长能到 885 格。
     * 旧值 160 只查玩家脚边一小圈，于是玩家稍微走几步再右键，
     * 就能<b>再召一个</b> —— 多个 Boss 各自带 40 个部件实体，
     * 同步量成倍放大，最后表现出来是"身体渲染不出来、头卡在旧位置"。
     *
     * <p>512 覆盖了客户端追踪范围（256）的两倍，足够判断"这个维度里是否已经有它"。
     */
    private static final double EXISTING_CHECK_RADIUS = 512.0D;

    /** 使用冷却（tick）。 */
    private static final int COOLDOWN_TICKS = 200;

    /** 生成点与玩家的距离（格）。 */
    private static final double SPAWN_DISTANCE = 24.0D;

    public DarkstarBeaconItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!(level instanceof ServerLevel server)) {
            // 客户端只负责挥手动画
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        // ---- 是否已经有活着的乌蠹诺斯 ----
        AABB area = player.getBoundingBox().inflate(EXISTING_CHECK_RADIUS);
        List<UdunosBoss> existing = server.getEntitiesOfClass(UdunosBoss.class, area);

        // 取最近的那一个：提示里带上距离，玩家才知道它游到哪去了
        UdunosBoss nearest = null;
        double nearestSqr = Double.MAX_VALUE;
        for (UdunosBoss b : existing) {
            if (!b.isAlive() || b.isRemoved()) {
                continue;
            }
            double d = b.distanceToSqr(player);
            if (d < nearestSqr) {
                nearestSqr = d;
                nearest = b;
            }
        }
        if (nearest != null) {
            player.displayClientMessage(
                    Component.translatable("message.lost_realm.udunos.already_present")
                            .withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(" §7(约 " + (int) Math.sqrt(nearestSqr) + " 格外)")
                                    .withStyle(ChatFormatting.DARK_GRAY)),
                    true);
            return InteractionResultHolder.fail(stack);
        }

        // ---- 生成点：玩家视线方向前方、抬高一点，让"钻出"有向下俯冲的余地 ----
        Vec3 look = player.getLookAngle();
        Vec3 spot = player.position()
                .add(look.x * SPAWN_DISTANCE, 14.0D, look.z * SPAWN_DISTANCE);

        UdunosBoss boss = BossEntities.UDUNOS.get().create(server);
        if (boss == null) {
            return InteractionResultHolder.fail(stack);
        }
        // 面朝玩家 —— 玩家能第一眼看到头部的星核亮起来
        boss.moveTo(spot.x, spot.y, spot.z,
                (float) (Math.toDegrees(Math.atan2(
                        player.getX() - spot.x, player.getZ() - spot.z))), 0.0F);
        server.addFreshEntity(boss);

        // ---- 演出与音效 ----
        server.sendParticles(ParticleTypes.REVERSE_PORTAL,
                spot.x, spot.y, spot.z, 120, 2.5D, 2.5D, 2.5D, 0.35D);
        server.sendParticles(ParticleTypes.FLASH,
                spot.x, spot.y, spot.z, 4, 0.0D, 0.0D, 0.0D, 0.0D);
        server.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN,
                SoundSource.HOSTILE, 3.0F, 0.6F);

        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lost_realm.darkstar_beacon.tip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.lost_realm.darkstar_beacon.tip.2",
                        (int) UdunosTuning.MAX_HEALTH)
                .withStyle(ChatFormatting.DARK_PURPLE));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // 附魔光效：终局召唤物该有的样子
        return true;
    }
}
