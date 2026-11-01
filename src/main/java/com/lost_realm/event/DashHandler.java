package com.lost_realm.event;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.lost_realm.Medals;
import com.lost_realm.config.MedalsConfig;
import com.lost_realm.data.PlayerMedals;
import com.lost_realm.item.WarriorCharmType;

/**
 * 突进之刃 —— 服务端的位移驱动与路径伤害。
 *
 * <h2>为什么不用「瞬移」</h2>
 * 直接 {@code setPos(pos + dir*4)} 会穿墙（服务端的碰撞检测根本没参与），
 * 而且客户端不会跟随，玩家视角里会看到自己突然被拽走再被服务器拉回。
 *
 * <p>这里改成<b>速度驱动</b>：连续几 tick 强制设置玩家的水平速度并置
 * {@code hurtMarked}，让客户端接受这个速度自行移动。好处是
 * <ul>
 *   <li>碰撞交给原版的移动流程处理 —— <b>撞墙自然停下</b>，不会穿过去；</li>
 *   <li>客户端自己算位置，视角平滑，没有拉扯感；</li>
 *   <li>每 tick 把 {@code fallDistance} 清零，突进途中不会摔伤。</li>
 * </ul>
 *
 * <p>路径伤害在位移的每一 tick 扫一次玩家周围的实体，同一目标只吃一次。
 */
@Mod.EventBusSubscriber(modid = Medals.MODID)
public final class DashHandler {

    /** 冷却：存在玩家数据里的「下次可用」游戏刻。 */
    private static final String STATE_READY = "warrior_dash_ready";

    /** 突进持续几 tick —— 太短会像瞬移，太长会像滑冰。 */
    private static final int DASH_TICKS = 4;

    /** 玩家基础攻速（原版常量）:武器自身的攻速 = 这个值 + 主手修饰符。 */
    private static final double BASE_ATTACK_SPEED = 4.0D;

    /** 攻速比较的容差（见 {@link #tryDash} 里的说明）。 */
    private static final double SPEED_EPSILON = 1.0E-3D;

    /** 提示限流:UUID → 上次提示时刻。 */
    private static final Map<UUID, Long> WARN_COOLDOWN = new HashMap<>();

    /** 正在突进中的玩家。 */
    private static final Map<UUID, Dash> ACTIVE = new HashMap<>();

    /** 一次突进的运行时状态。 */
    private static final class Dash {
        final ServerLevel level;
        final Vec3 dir;
        final double perTick;
        final float damage;
        int ticksLeft;
        final Set<UUID> hit = new HashSet<>();

        Dash(ServerLevel level, Vec3 dir, double perTick, float damage, int ticksLeft) {
            this.level = level;
            this.dir = dir;
            this.perTick = perTick;
            this.damage = damage;
            this.ticksLeft = ticksLeft;
        }
    }

    /**
     * 尝试发动突进（由 {@code PacketDash} 调用）。
     *
     * <p>所有校验都在这:佩戴、冷却、武器攻速、饥饿值。
     */
    public static void tryDash(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        // 没戴这枚饰品就彻底静默 —— 玩家每次双击都弹提示会很烦
        if (!WarriorCharmType.DASH.isActive(player)) {
            return;
        }
        if (ACTIVE.containsKey(player.getUUID())) {
            return;   // 已经在冲了
        }

        // ---------- 以下失败都会给一条 actionbar,方便玩家自己看出卡在哪 ----------
        double threshold = MedalsConfig.WARRIOR_DASH_SPEED_THRESHOLD.get();
        double speed = weaponAttackSpeed(player.getMainHandItem());
        // ⚠️ 必须留容差:剑的攻速修饰符是 -2.4F,转成 double 是 -2.4000000953674316,
        //    4.0 减完得到 1.5999999... —— 直接比 < 1.6 会把合格的剑判成不合格。
        if (speed + SPEED_EPSILON < threshold) {
            warn(player, "speed", threshold);
            return;
        }

        PlayerMedals data = PlayerMedals.of(player);
        long now = level.getGameTime();
        long ready = data.getTimestamp(STATE_READY);
        if (now < ready) {
            warn(player, "cooldown", (ready - now) / 20.0D);
            return;
        }
        if (player.getFoodData().getFoodLevel() <= 0) {
            warn(player, "hunger", null);
            return;
        }

        Vec3 look = player.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0.0D, look.z);
        if (dir.lengthSqr() < 1.0E-6D) {
            return;
        }
        dir = dir.normalize();

        double distance = MedalsConfig.WARRIOR_DASH_DISTANCE.get();
        float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE)
                * MedalsConfig.pct(MedalsConfig.WARRIOR_DASH_DAMAGE));

        ACTIVE.put(player.getUUID(), new Dash(level, dir, distance / DASH_TICKS, damage, DASH_TICKS));

        // 冷却 & 消耗
        int cooldown = MedalsConfig.WARRIOR_DASH_COOLDOWN.get();
        if (cooldown > 0) {
            data.setTimestamp(STATE_READY, now + cooldown * 20L);
        }
        double hunger = MedalsConfig.WARRIOR_DASH_HUNGER.get();
        if (hunger > 0.0D) {
            player.causeFoodExhaustion((float) hunger);
        }

        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    /** 逐 tick 推进位移与路径伤害。 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        Dash dash = ACTIVE.get(player.getUUID());
        if (dash == null) {
            return;
        }

        if (dash.ticksLeft <= 0 || player.isRemoved() || player.level() != dash.level) {
            ACTIVE.remove(player.getUUID());
            return;
        }

        // ---------- 位移 ----------
        player.setDeltaMovement(dash.dir.x * dash.perTick,
                player.getDeltaMovement().y,
                dash.dir.z * dash.perTick);
        player.hurtMarked = true;      // 让客户端接受这个速度自己走
        player.fallDistance = 0.0F;    // 突进途中不摔伤
        player.hasImpulse = true;

        // ---------- 路径伤害 ----------
        hurtAlongPath(player, dash);

        // ---------- 表现 ----------
        dash.level.sendParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY() + 0.2D, player.getZ(), 6, 0.2D, 0.1D, 0.2D, 0.02D);

        dash.ticksLeft--;
        if (dash.ticksLeft <= 0) {
            ACTIVE.remove(player.getUUID());
        }
    }

    /** 扫一遍玩家身边的敌人，把这次突进还没打到的打掉。 */
    private static void hurtAlongPath(ServerPlayer player, Dash dash) {
        double reach = 1.6D;
        for (LivingEntity victim : dash.level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(reach),
                e -> e != player && e.isAlive())) {
            if (!dash.hit.add(victim.getUUID())) {
                continue;   // 这次突进已经打过它了
            }
            victim.hurt(player.damageSources().playerAttack(player), dash.damage);
            Vec3 push = victim.position().subtract(player.position());
            if (push.lengthSqr() > 1.0E-6D) {
                Vec3 kb = push.normalize().scale(1.2D);
                victim.push(kb.x, 0.25D, kb.z);
                victim.hurtMarked = true;
            }
        }
    }

    /** 给玩家一条 actionbar 说明为什么冲不出去（1 秒内只提示一次）。 */
    private static void warn(ServerPlayer player, String reason, Object arg) {
        long now = player.level().getGameTime();
        Long last = WARN_COOLDOWN.get(player.getUUID());
        if (last != null && now - last < 20L) {
            return;
        }
        WARN_COOLDOWN.put(player.getUUID(), now);
        String key = "message.lost_realm.dash." + reason;
        player.displayClientMessage(arg == null
                ? net.minecraft.network.chat.Component.translatable(key)
                : net.minecraft.network.chat.Component.translatable(key, arg), true);
    }

    /**
     * 武器<b>本身</b>的攻速（不含玩家身上的各种加成）。
     *
     * <p>需求写的是「手持攻击速度大于等于 1.6 的武器」——
     * 若读玩家最终攻速，喝瓶药、戴个饰品就能把匕首凑成快剑，判定就失去意义了。
     * 所以这里只看物品自身的修饰符：原版基础 4.0 加上主手修饰符的加法部分。
     */
    public static double weaponAttackSpeed(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0D;
        }
        double speed = BASE_ATTACK_SPEED;
        for (var entry : stack.getAttributeModifiers(EquipmentSlot.MAINHAND).entries()) {
            if (entry.getKey() == Attributes.ATTACK_SPEED
                    && entry.getValue().getOperation() == AttributeModifier.Operation.ADDITION) {
                speed += entry.getValue().getAmount();
            }
        }
        return speed;
    }

    private DashHandler() {}
}
