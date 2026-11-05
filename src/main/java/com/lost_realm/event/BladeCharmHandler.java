package com.lost_realm.event;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.lost_realm.Medals;
import com.lost_realm.attribute.RealmAttributes;
import com.lost_realm.config.MedalsConfig;
import com.lost_realm.data.PlayerMedals;
import com.lost_realm.effect.ModEffects;
import com.lost_realm.item.BladeCharmType;
import com.lost_realm.util.AttributeGuard;
import com.lost_realm.util.ModifierIds;

/**
 * 九枚「刃」类刃饰品的全部效果。
 *
 * <h2>分三大类</h2>
 * <ul>
 *   <li><b>属性类</b>（双持 / 暴击）—— 走属性修饰符，每 tick 检查一次状态，
 *       只有状态<b>发生变化</b>时才真正改属性，避免每 tick 重算属性触发无谓的网络同步。</li>
 *   <li><b>进攻结算类</b>（背刺 / 蓄能 / 运气 / 缴械）—— 挂在 {@link LivingHurtEvent} 上，
 *       改的是「你打出去多少」。</li>
 *   <li><b>防御结算类</b>（坚韧）—— 挂在 {@link LivingDamageEvent} 上，
 *       改的是「你挨到多少」。<b>注意是 Damage 而不是 Hurt</b>：前者护甲已结算完，
 *       在那里的减伤才是干净的百分比（详见 {@link #onLivingDamage}）。</li>
 * </ul>
 *
 * <h2>横扫与突进不在这里</h2>
 * 横扫要改原版 {@code Player#attack} 的判定（见 mixin），
 * 突进要客户端的按键序列（见 network/PacketDash 与 client/DashClientHandler）。
 */
@Mod.EventBusSubscriber(modid = Medals.MODID)
public final class BladeCharmHandler {

    /** 蓄能之刃：玩家上一次近战命中的游戏刻。 */
    public static final String STATE_LAST_HIT = "blade_charm_last_hit";

    /** 缴械冷却：记在目标身上的「下次可被缴械」游戏刻。 */
    private static final String TAG_DISARM_READY = "lr_disarm_ready";

    /** 双持 / 暴击当前是否生效（缓存，仅在翻转时改属性）。 */
    private static final Map<UUID, Boolean> DUAL_ACTIVE = new HashMap<>();
    private static final Map<UUID, Boolean> CRIT_ACTIVE = new HashMap<>();

    /**
     * 本 tick 内每个玩家已经打过的目标。
     *
     * <p>横扫之刃补扫时用它去重：原版已经扫过的那几个不能再扫一遍。
     * 早先是用「疾跑 / 非疾跑」去推断原版扫没扫 —— 那个推断在实机上不可靠
     * （患得患失的 sprinting 同步、以及原版横扫本身还有别的触发条件），
     * 改成「你打过的我就跳过」这种直接观测，才真的稳。
     */
    private static final Map<UUID, TickHits> TICK_HITS = new HashMap<>();

    private record TickHits(long tick, Set<UUID> victims) {}

    /** 记下「本 tick 这个玩家打过这个目标」。 */
    public static void recordHit(ServerPlayer attacker, LivingEntity victim) {
        long now = attacker.level().getGameTime();
        TickHits hits = TICK_HITS.get(attacker.getUUID());
        if (hits == null || hits.tick() != now) {
            hits = new TickHits(now, new HashSet<>());
            TICK_HITS.put(attacker.getUUID(), hits);
        }
        hits.victims().add(victim.getUUID());
    }

    /** 本 tick 这个玩家是否已经打过该目标。 */
    public static boolean hitThisTick(ServerPlayer attacker, LivingEntity victim) {
        TickHits hits = TICK_HITS.get(attacker.getUUID());
        if (hits == null || hits.tick() != attacker.level().getGameTime()) {
            return false;
        }
        return hits.victims().contains(victim.getUUID());
    }

    // ================================================================
    // 属性类：双持之刃 / 暴击之刃
    // ================================================================

    /**
     * 每 tick 检查两个「会随状态变化」的饰品。
     *
     * <p>双持要求主手与副手<b>同时</b>持有武器 —— 这是个会来回翻转的状态，
     * 所以必须每 tick 看，但只在翻转的那一次才动属性。
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        // 一次 Curios 遍历拿全，别让它每 tick 跑两遍
        Set<BladeCharmType> active = BladeCharmType.activeSet(player);
        refreshDual(player, active.contains(BladeCharmType.DUAL));
        refreshCrit(player, active.contains(BladeCharmType.CRIT));
    }

    /** 双持之刃：主副手都有武器时给伤害与攻速加成。 */
    private static void refreshDual(ServerPlayer player, boolean equipped) {
        boolean dual = equipped
                && isWeapon(player.getMainHandItem())
                && isWeapon(player.getOffhandItem());

        if (Objects.equals(DUAL_ACTIVE.get(player.getUUID()), dual)) {
            return;
        }
        DUAL_ACTIVE.put(player.getUUID(), dual);

        if (dual) {
            AttributeGuard.apply(player, Attributes.ATTACK_DAMAGE,
                    ModifierIds.BLADE_CHARM_DUAL_DAMAGE, "blade_charm_dual_damage",
                    MedalsConfig.pct(MedalsConfig.BLADE_DUAL_DAMAGE),
                    AttributeModifier.Operation.MULTIPLY_BASE);
            AttributeGuard.apply(player, Attributes.ATTACK_SPEED,
                    ModifierIds.BLADE_CHARM_DUAL_ATTACK_SPEED, "blade_charm_dual_attack_speed",
                    MedalsConfig.pct(MedalsConfig.BLADE_DUAL_ATTACK_SPEED),
                    AttributeModifier.Operation.MULTIPLY_BASE);
        } else {
            AttributeGuard.remove(player, Attributes.ATTACK_DAMAGE, ModifierIds.BLADE_CHARM_DUAL_DAMAGE);
            AttributeGuard.remove(player, Attributes.ATTACK_SPEED, ModifierIds.BLADE_CHARM_DUAL_ATTACK_SPEED);
        }
    }

    /** 暴击之刃：常驻的暴击率 / 暴击伤害加成。 */
    private static void refreshCrit(ServerPlayer player, boolean active) {
        if (Objects.equals(CRIT_ACTIVE.get(player.getUUID()), active)) {
            return;
        }
        CRIT_ACTIVE.put(player.getUUID(), active);

        // beta30 起两条属性由本模组自注册，永远存在，无需判空
        var critChance = RealmAttributes.critChance();
        var critDamage = RealmAttributes.critDamage();

        if (active) {
            AttributeGuard.apply(player, critChance, ModifierIds.BLADE_CHARM_CRIT_CHANCE,
                    "blade_charm_crit_chance", MedalsConfig.pct(MedalsConfig.BLADE_CRIT_CHANCE),
                    AttributeModifier.Operation.ADDITION);
            AttributeGuard.apply(player, critDamage, ModifierIds.BLADE_CHARM_CRIT_DAMAGE,
                    "blade_charm_crit_damage", MedalsConfig.pct(MedalsConfig.BLADE_CRIT_DAMAGE),
                    AttributeModifier.Operation.MULTIPLY_BASE);
        } else {
            AttributeGuard.remove(player, critChance, ModifierIds.BLADE_CHARM_CRIT_CHANCE);
            AttributeGuard.remove(player, critDamage, ModifierIds.BLADE_CHARM_CRIT_DAMAGE);
        }
    }

    /** 玩家退出时把缓存清掉，免得 UUID 表无限增长。 */
    @SubscribeEvent
    public static void onPlayerLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        DUAL_ACTIVE.remove(id);
        CRIT_ACTIVE.remove(id);
        TOUGHNESS.remove(id);
        TICK_HITS.remove(id);
    }

    // ================================================================
    // 坚韧之刃 —— 刃唯一的防御手段
    // ================================================================

    /** 同一敌人最近的连续打击记录。 */
    private record Toughness(UUID source, long lastAt, int hits) {}

    /**
     * 每个玩家当前正在挨的<b>同一来源</b>连击。
     *
     * <p>只存一条：换了对手或被时间隔断就该重来，没必要为每个曾经摸过你的敌人留位置。
     * 于是这张表的大小等于在线人数，不会因为"被一百只怪轮流摸过"而膨胀。
     */
    private static final Map<UUID, Toughness> TOUGHNESS = new HashMap<>();

    /**
     * 坚韧之刃：同一个敌人连续命中时，后面每一下都比前一下轻。
     *
     * <h2>为什么挂在 LivingDamageEvent 而不是 LivingHurtEvent</h2>
     * {@code LivingHurtEvent} 拿到的是<b>护甲结算之前</b>的原始伤害，
     * 在那里乘 0.85 会被后面的护甲再稀释一遍 —— 「减伤 15%」实际只剩几个点。
     * {@code LivingDamageEvent} 是护甲、附魔、抗性都算完的最终值，
     * 在这里减多少就是多少。这正是「减伤类效果必须挂对事件」的教科书例子。
     *
     * <h2>为什么低血只是「上限更高」而不是「叠得更快」</h2>
     * 这是刻意留白：满血三下就能压到 15%，而残血要扛七下才到 30% ——
     * 也就是说<b>血越少，你得越能熬</b>。天花板给你了，爬上去靠自己。
     */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!BladeCharmType.TOUGHNESS.isActive(player)) {
            return;
        }
        // 虚空、饥饿、中毒这类没有来源实体的伤害不计入连击
        Entity source = event.getSource().getEntity();
        if (source == null || event.getAmount() <= 0.0F) {
            return;
        }

        long now = player.level().getGameTime();
        long window = Math.max(1, MedalsConfig.BLADE_TOUGHNESS_WINDOW_SECONDS.get()) * 20L;
        UUID sourceId = source.getUUID();

        Toughness prev = TOUGHNESS.get(player.getUUID());
        // 换对手 / 超过窗口 → 从「第一下」重新数
        int hits = (prev != null && prev.source().equals(sourceId) && now - prev.lastAt() <= window)
                ? prev.hits() + 1
                : 1;
        TOUGHNESS.put(player.getUUID(), new Toughness(sourceId, now, hits));

        // 第 1 下全额,第 2 下 -5%,第 3 下 -10%…
        double reduce = (hits - 1) * MedalsConfig.pct(MedalsConfig.BLADE_TOUGHNESS_PER_HIT);

        // 每损失 25% 生命,上限 +5%
        double ratio = player.getMaxHealth() <= 0.0F
                ? 0.0D
                : player.getHealth() / player.getMaxHealth();
        int lostSteps = (int) Math.floor(Math.max(0.0D, 1.0D - ratio) / 0.25D);
        double cap = MedalsConfig.pct(MedalsConfig.BLADE_TOUGHNESS_BASE_CAP)
                + lostSteps * MedalsConfig.pct(MedalsConfig.BLADE_TOUGHNESS_CAP_PER_STEP);

        reduce = Math.min(reduce, cap);
        if (reduce <= 0.0D) {
            return;
        }
        event.setAmount((float) (event.getAmount() * (1.0D - reduce)));
    }

    // ================================================================
    // 进攻结算类：缴械拦截 / 背刺 / 蓄能 / 运气 / 缴械触发
    // ================================================================

    /**
     * 近战伤害结算。
     *
     * <p>优先级 {@code HIGHEST}：缴械的「取消伤害」必须发生在任何其它修正之前，
     * 否则别的处理器已经按原伤害算过一遍了。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        // ---------- 缴械：被缴械者打不出任何伤害 ----------
        if (event.getSource().getEntity() instanceof LivingEntity dealer
                && dealer.hasEffect(ModEffects.DISARMED.get())) {
            event.setCanceled(true);
            return;
        }

        // ---------- 声波伤害本身不再触发饰品（防止自触发递归） ----------
        if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.SONIC_BOOM)) {
            return;
        }

        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        // 先把「本 tick 打过谁」记下来 —— 横扫补扫要靠它去重(原版横扫也算)
        recordHit(attacker, event.getEntity());
        // 只处理近战:投射物的直接来源不是玩家自己
        if (event.getSource().getDirectEntity() != attacker) {
            return;
        }

        LivingEntity target = event.getEntity();
        Set<BladeCharmType> active = BladeCharmType.activeSet(attacker);
        if (active.isEmpty()) {
            return;
        }

        float amount = event.getAmount();
        double mult = 1.0D;

        // ---------- 背刺之刃 ----------
        if (active.contains(BladeCharmType.BACKSTAB) && isBehind(attacker, target)) {
            mult += MedalsConfig.pct(MedalsConfig.BLADE_BACKSTAB_BONUS);
        }

        // ---------- 蓄能之刃 ----------
        if (active.contains(BladeCharmType.CHARGING)) {
            mult += chargingBonus(attacker);
        }

        // ---------- 运气之刃 ----------
        // ★ 必须放在 setAmount 之前:它是个乘区,不是独立效果
        if (active.contains(BladeCharmType.LUCK)) {
            mult += luckRoll(attacker);
        }

        if (mult != 1.0D) {
            event.setAmount((float) (amount * mult));
        }

        // ---------- 缴械之刃 ----------
        if (active.contains(BladeCharmType.DISARM)) {
            tryDisarm(attacker, target);
        }
    }

    /**
     * 蓄能之刃的加成本次是多少，并顺手把「上次出手时刻」推进到现在。
     *
     * <p>同一 tick 内的多次命中（横扫、溅射）会算出 0 秒 → 0 加成，
     * 于是只有「主目标」吃满加成，天然防重复。
     */
    private static double chargingBonus(ServerPlayer attacker) {
        long now = attacker.level().getGameTime();
        PlayerMedals data = PlayerMedals.of(attacker);
        long last = data.getTimestamp(STATE_LAST_HIT);
        data.setTimestamp(STATE_LAST_HIT, now);

        if (last <= 0L) {
            return 0.0D;
        }
        double seconds = (now - last) / 20.0D;
        return Math.min(MedalsConfig.pct(MedalsConfig.BLADE_CHARGE_MAX),
                seconds * MedalsConfig.pct(MedalsConfig.BLADE_CHARGE_PER_SECOND));
    }

    /**
     * 判断攻击者是否位于目标<b>背后</b>。
     *
     * <p>取目标视线方向与「目标 → 攻击者」方向的夹角：
     * 点积越接近 -1 表示越在正后方。阈值取 -0.3（约 107°）——
     * 太宽（0）会把侧面也算作背刺，太严（-1）则几乎打不出来。
     */
    private static boolean isBehind(LivingEntity attacker, LivingEntity target) {
        Vec3 toAttacker = attacker.position().subtract(target.position());
        Vec3 look = target.getLookAngle();
        if (toAttacker.lengthSqr() < 1.0E-6D || look.lengthSqr() < 1.0E-6D) {
            return false;
        }
        return look.normalize().dot(toAttacker.normalize()) < -0.3D;
    }

    /**
     * 运气之刃：本次攻击的伤害浮动量。
     *
     * <p>区间是 {@code [-range, +range]}，中点恰好落在 0 —— 也就是说
     * <b>期望伤害与不戴它时完全一样</b>，只是把结果摊开了。这是设计，不是缺陷：
     * 它赌的是「运气」，不是白送的强度。
     *
     * <p>幸运值每 1 点把<b>下限</b>往上抬若干个百分点（读原版 {@code Attributes.LUCK}，
     * 默认 0，来源交给幸运药水或其它模组）。抬到 {@code floor_cap} 就封顶 ——
     * 幸运够高时区间会收缩成「只有好结果」。
     */
    private static double luckRoll(ServerPlayer attacker) {
        double range = MedalsConfig.pct(MedalsConfig.BLADE_LUCK_RANGE);
        if (range <= 0.0D) {
            return 0.0D;
        }
        int luck = (int) Math.floor(Math.max(0.0D, attacker.getAttributeValue(Attributes.LUCK)));
        double low = Math.min(MedalsConfig.pct(MedalsConfig.BLADE_LUCK_FLOOR_CAP),
                -range + luck * MedalsConfig.pct(MedalsConfig.BLADE_LUCK_PER_POINT));
        double high = range;
        // low 被配置成大于 high 时兜一手,免得 nextDouble() 乘出反向区间
        if (low >= high) {
            return high;
        }
        return low + attacker.getRandom().nextDouble() * (high - low);
    }

    /**
     * 缴械之刃：按概率缴械目标，并给<b>目标</b>记一个冷却。
     *
     * <p>冷却记在目标身上而不是佩戴者身上 —— 要防的是「同一个倒霉蛋被反复缴械」，
     * 而不是「佩戴者一次只能缴械一个人」。这样打一群怪时每个人各自计时。
     */
    private static void tryDisarm(ServerPlayer attacker, LivingEntity target) {
        long now = attacker.level().getGameTime();
        long ready = target.getPersistentData().getLong(TAG_DISARM_READY);
        if (now < ready) {
            return;
        }
        if (attacker.getRandom().nextDouble() >= MedalsConfig.pct(MedalsConfig.BLADE_DISARM_CHANCE)) {
            return;
        }

        int ticks = Math.max(1, MedalsConfig.BLADE_DISARM_SECONDS.get()) * 20;
        target.addEffect(new MobEffectInstance(ModEffects.DISARMED.get(), ticks, 0, false, true), attacker);
        target.getPersistentData().putLong(TAG_DISARM_READY,
                now + MedalsConfig.BLADE_DISARM_COOLDOWN.get() * 20L);

        if (target.level() instanceof ServerLevel level) {
            // 视觉：兵刃脱手的一小撮火星
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0D, target.getZ(),
                    14, 0.3D, 0.4D, 0.3D, 0.2D);
            level.playSound(null, target.blockPosition(), SoundEvents.ITEM_BREAK,
                    SoundSource.PLAYERS, 0.8F, 0.7F);
            if (target instanceof Player player) {
                player.displayClientMessage(
                        Component.translatable("message.lost_realm.disarmed"), true);
            }
        }
    }

    /** 该物品算不算「武器」:能提供近战攻击伤害修饰符的都算。 */
    public static boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.getAttributeModifiers(EquipmentSlot.MAINHAND)
                .containsKey(Attributes.ATTACK_DAMAGE);
    }

    private BladeCharmHandler() {}
}
