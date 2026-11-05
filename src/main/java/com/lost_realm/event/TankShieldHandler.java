package com.lost_realm.event;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.lost_realm.Medals;
import com.lost_realm.attribute.RealmAttributes;
import com.lost_realm.config.MedalsConfig;
import com.lost_realm.data.PlayerMedals;
import com.lost_realm.effect.ModEffects;
import com.lost_realm.item.TankShieldItem;
import com.lost_realm.item.TankShieldType;
import com.lost_realm.util.AttributeGuard;
import com.lost_realm.util.ModifierIds;

import top.theillusivec4.curios.api.CuriosApi;

/**
 * 九枚「盾」类坦克饰品的全部效果（含九合一终极盾）。
 *
 * <h2>分成几条互不干扰的线</h2>
 * <ul>
 *   <li><b>受伤结算</b>（{@link LivingDamageEvent}）—— 荆棘 / 适应 / 震荡 / 牺牲 /
 *       反哺 / 生机 / 大地 / 不朽的减伤。全部集中在这一个方法里，
 *       因为<b>减伤必须汇总后一次性乘</b>：分散成多个 handler 各自乘一遍，
 *       结果会随执行顺序变化，玩家调配置时对不上账。</li>
 *   <li><b>状态维护</b>（{@link TickEvent.PlayerTickEvent}）—— 大地盾的静止计时、
 *       吞噬盾与先锋盾的属性、不朽神盾的常驻效果。</li>
 *   <li><b>专项拦截</b> —— 格挡（{@link ShieldBlockEvent}）、击退免疫
 *       （{@link LivingKnockBackEvent}）、负面效果免疫（{@link MobEffectEvent.Applicable}）、
 *       击杀计数（{@link LivingDeathEvent}）。</li>
 * </ul>
 *
 * <h2>为什么减伤挂在 LivingDamageEvent</h2>
 * 与刃的坚韧之刃同理：{@code LivingHurtEvent} 拿到的还是护甲结算<b>之前</b>的伤害，
 * 在那里乘系数会被护甲再稀释一遍。{@code LivingDamageEvent} 是最终值，
 * 减多少就是多少。
 */
@Mod.EventBusSubscriber(modid = Medals.MODID)
public final class TankShieldHandler {

    /** 牺牲盾冷却：玩家数据里的「下次可用」游戏刻。 */
    private static final String STATE_SACRIFICE_READY = "shield_sacrifice_ready";

    // ================================================================
    // 运行时状态（全部按玩家 UUID 索引，登出即清）
    // ================================================================

    /** 适应盾：伤害类型 → 连续命中记录。每个玩家最多留 {@code SHIELD_ADAPT_TYPES} 条。 */
    private static final Map<UUID, Map<String, Adapt>> ADAPT = new HashMap<>();

    private record Adapt(long at, int hits) {}

    /** 大地盾：静止起点与当前位置。 */
    private static final Map<UUID, Still> STILL = new HashMap<>();

    private record Still(Vec3 pos, long since, boolean active) {}

    /** 生机盾：层数与最近一次受击时刻。 */
    private static final Map<UUID, Vigor> VIGOR = new HashMap<>();

    private record Vigor(long at, int layers) {}

    /** 吞噬盾 / 生机盾等属性的「上次应用值」，只有变化时才真正改属性。 */
    private static final Map<UUID, Double> APPLIED_DEVOUR = new HashMap<>();
    private static final Map<UUID, Integer> APPLIED_VIGOR = new HashMap<>();

    /** 先锋盾两条属性的生效状态（同理，翻转时才动）。 */
    private static final Map<UUID, Boolean> VANGUARD_ARMOR_ON = new HashMap<>();
    private static final Map<UUID, Boolean> VANGUARD_SPEED_ON = new HashMap<>();

    /** 不朽神盾的常驻属性是否已挂上。 */
    private static final Map<UUID, Boolean> ETERNAL_ON = new HashMap<>();

    // ================================================================
    // 一、受伤结算
    // ================================================================

    /**
     * 所有「挨打时」的盾效果。
     *
     * <p>优先级 {@code LOW}：让它跑在其它减伤（技能、护甲）之后，
     * 这样各家的减伤是连续相乘的关系，谁先谁后都不影响最终结果。
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Set<TankShieldType> shields = TankShieldType.activeSet(player);
        if (shields.isEmpty()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();

        // ---------- 不朽神盾：免疫环境伤害（虚空除外）----------
        if (shields.contains(TankShieldType.ETERNAL) && isEnvironmental(event.getSource())) {
            event.setCanceled(true);
            return;
        }

        float amount = event.getAmount();
        if (amount <= 0.0F) {
            return;
        }

        // ---------- 减伤汇总（加算，然后一次性乘）----------
        double reduction = 0.0D;
        if (shields.contains(TankShieldType.EARTH) && isEarthActive(player)) {
            reduction += MedalsConfig.pct(MedalsConfig.SHIELD_EARTH_REDUCTION);
        }
        if (shields.contains(TankShieldType.ADAPTIVE)) {
            reduction += adaptReduction(player, event.getSource(), now);
        }
        if (shields.contains(TankShieldType.ETERNAL)) {
            reduction += MedalsConfig.pct(MedalsConfig.SHIELD_ETERNAL_REDUCTION);
        }
        if (reduction > 0.0D) {
            // 封顶 95%:再高就该无敌了,留一点让玩家仍需关注血线
            reduction = Math.min(0.95D, reduction);
            amount = (float) (amount * (1.0D - reduction));
            event.setAmount(amount);
        }
        if (amount <= 0.0F) {
            return;
        }

        // ---------- 荆棘盾：反弹 ----------
        if (shields.contains(TankShieldType.THORNS)) {
            reflectThorns(player, event, amount);
        }

        // ---------- 震荡盾：冲击波 ----------
        if (shields.contains(TankShieldType.SHOCKWAVE)) {
            shockwave(player, level);
        }

        // ---------- 牺牲盾：分摊 ----------
        if (shields.contains(TankShieldType.SACRIFICE)) {
            amount = sacrifice(player, level, event, amount, now);
        }

        // ---------- 反哺盾：以饱食度抵扣 ----------
        if (shields.contains(TankShieldType.NOURISH)) {
            amount = nourish(player, event, amount);
        }

        // ---------- 生机盾：叠层 ----------
        if (shields.contains(TankShieldType.VITALITY)) {
            stackVigor(player, now);
        }
    }

    /**
     * 荆棘盾：把伤害的一部分还给攻击者。
     *
     * <p>用 {@code thorns} 伤害源 —— 它天然不会被再次判定为"生物攻击"，
     * 所以两个都戴荆棘盾的玩家互殴时不会无限反弹（递归在第二下就停）。
     */
    private static void reflectThorns(ServerPlayer player, LivingDamageEvent event, float amount) {
        if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.THORNS)) {
            return;
        }
        double ratio = MedalsConfig.pct(MedalsConfig.SHIELD_THORNS_REFLECT);
        if (ratio <= 0.0D) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == player) {
            return;
        }
        attacker.hurt(player.damageSources().thorns(player), (float) (amount * ratio));
    }

    /**
     * 适应盾：同一种伤害类型连着挨，后面每一下都轻一点。
     *
     * <p>与刃「坚韧之刃」的差别就在这个<b>计数键</b>：
     * 坚韧按「哪个敌人」记，被三个僵尸围时各自从零开始；
     * 适应按「伤害类型」记，三只僵尸都算"mob"这一种，所以叠得快得多。
     *
     * <p>只保留最近 {@code SHIELD_ADAPT_TYPES} 种类型，超出时淘汰最旧的 ——
     * 否则被各种伤害轮一遍就等于永久减伤了。
     */
    private static double adaptReduction(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source, long now) {
        long window = Math.max(1, MedalsConfig.SHIELD_ADAPT_WINDOW.get()) * 20L;
        Map<String, Adapt> map = ADAPT.computeIfAbsent(player.getUUID(), k -> new HashMap<>());

        // 先清过期的
        map.entrySet().removeIf(e -> now - e.getValue().at() > window);

        String key = source.getMsgId();
        Adapt prev = map.get(key);
        int hits = (prev != null) ? prev.hits() + 1 : 1;
        map.put(key, new Adapt(now, hits));

        // 超出容量时淘汰最久未命中的那一类
        int maxTypes = Math.max(1, MedalsConfig.SHIELD_ADAPT_TYPES.get());
        while (map.size() > maxTypes) {
            String oldest = map.entrySet().stream()
                    .min(Comparator.comparingLong(e -> e.getValue().at()))
                    .map(Map.Entry::getKey).orElse(null);
            if (oldest == null) {
                break;
            }
            map.remove(oldest);
        }

        double cap = MedalsConfig.pct(MedalsConfig.SHIELD_ADAPT_CAP);
        // 第 1 下全额,第 2 下起每次叠加
        return Math.min(cap, (hits - 1) * MedalsConfig.pct(MedalsConfig.SHIELD_ADAPT_PER_HIT));
    }

    /**
     * 震荡盾：以自身为中心炸一圈，把周围敌人掀开并打懵。
     *
     * <p>眩晕走的是自建的 {@code lost_realm:stunned} 效果 ——
     * 它是个纯标记，真正的「动不了 + 打不出」由本类在别处完成。
     */
    private static void shockwave(ServerPlayer player, ServerLevel level) {
        double radius = MedalsConfig.SHIELD_SHOCKWAVE_RADIUS.get();
        int stunTicks = MedalsConfig.SHIELD_SHOCKWAVE_STUN.get() * 20;

        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                e -> e != player && e.isAlive() && !e.isAlliedTo(player));

        for (LivingEntity victim : victims) {
            Vec3 dir = victim.position().subtract(player.position());
            Vec3 flat = new Vec3(dir.x, 0.0D, dir.z);
            if (flat.lengthSqr() < 1.0E-6D) {
                flat = new Vec3(1.0D, 0.0D, 0.0D);
            }
            flat = flat.normalize().scale(1.2D);
            victim.push(flat.x, 0.4D, flat.z);
            victim.hurtMarked = true;
            if (stunTicks > 0) {
                victim.addEffect(new MobEffectInstance(ModEffects.STUNNED.get(),
                        stunTicks, 0, false, true, true), player);
            }
        }

        // 特效:一圈向外扩散的灰白尘埃 + 低沉的盾击声
        for (int i = 0; i < 60; i++) {
            double angle = i / 60.0D * Math.PI * 2.0D;
            double r = radius * (0.35D + level.getRandom().nextDouble() * 0.65D);
            level.sendParticles(ParticleTypes.CLOUD,
                    player.getX() + Math.cos(angle) * r,
                    player.getY() + 0.35D,
                    player.getZ() + Math.sin(angle) * r,
                    1, 0.0D, 0.02D, 0.0D, 0.01D);
        }
        level.sendParticles(ParticleTypes.CRIT,
                player.getX(), player.getY() + 0.9D, player.getZ(),
                24, 0.5D, 0.5D, 0.5D, 0.2D);
        level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK,
                SoundSource.PLAYERS, 1.0F, 0.55F);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, 0.5F, 1.6F);
    }

    /**
     * 牺牲盾：把一部分伤害分摊给周围的敌人。
     *
     * <p>转移量是<b>总额</b>（伤害 × 比例），在圈内敌人之间平分 ——
     * 而不是"每个敌人都替你吃 20%"。后者在怪堆里等于完全免伤。
     *
     * @return 减免后的伤害
     */
    private static float sacrifice(ServerPlayer player, ServerLevel level,
                                   LivingDamageEvent event, float amount, long now) {
        double ratio = MedalsConfig.pct(MedalsConfig.SHIELD_SACRIFICE_RATIO);
        if (ratio <= 0.0D) {
            return amount;
        }
        PlayerMedals data = PlayerMedals.of(player);
        if (now < data.getTimestamp(STATE_SACRIFICE_READY)) {
            return amount;
        }
        double radius = MedalsConfig.SHIELD_SACRIFICE_RADIUS.get();
        int max = Math.max(1, MedalsConfig.SHIELD_SACRIFICE_MAX.get());

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                e -> e != player && e.isAlive() && !e.isAlliedTo(player));
        if (targets.isEmpty()) {
            return amount;
        }
        targets.sort(Comparator.comparingDouble(player::distanceToSqr));
        if (targets.size() > max) {
            targets = targets.subList(0, max);
        }

        double transfer = amount * ratio;
        float each = (float) (transfer / targets.size());
        for (LivingEntity target : targets) {
            target.hurt(player.damageSources().playerAttack(player), each);
        }

        int cooldown = MedalsConfig.SHIELD_SACRIFICE_COOLDOWN.get();
        if (cooldown > 0) {
            data.setTimestamp(STATE_SACRIFICE_READY, now + cooldown * 20L);
        }
        level.sendParticles(ParticleTypes.SOUL,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                14, 0.6D, 0.5D, 0.6D, 0.02D);
        level.playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE,
                SoundSource.PLAYERS, 0.7F, 1.3F);
        return (float) (amount - transfer);
    }

    /**
     * 反哺盾：命悬一线时，用饱食度替你挨这一下。
     *
     * <p>饱食度是按「点」算的（鸡腿图标一格 = 2 点），
     * 所以 2 点饱食度大致是"一格鸡腿"。这个定价是刻意偏贵的 ——
     * 它是<b>应急</b>，不是续航手段：真要靠它活着，你会饿得打不动架。
     *
     * @return 减免后的伤害
     */
    private static float nourish(ServerPlayer player, LivingDamageEvent event, float amount) {
        double threshold = MedalsConfig.pct(MedalsConfig.SHIELD_NOURISH_HP_THRESHOLD);
        double health = player.getHealth() / Math.max(1.0F, player.getMaxHealth());
        if (health >= threshold) {
            return amount;
        }
        FoodData food = player.getFoodData();
        int available = food.getFoodLevel();
        int usable = Math.min(available, Math.max(0, MedalsConfig.SHIELD_NOURISH_MAX_FOOD.get()));
        if (usable <= 0) {
            return amount;
        }
        double perFood = MedalsConfig.SHIELD_NOURISH_PER_FOOD.get();
        if (perFood <= 0.0D) {
            return amount;
        }

        // 需要几点就吃几点,不够则只用得起多少算多少
        int needed = (int) Math.ceil(amount / perFood);
        int used = Math.min(usable, needed);
        double absorbed = Math.min(amount, used * perFood);

        food.setFoodLevel(available - used);
        // 抵消掉的那部分不进伤害,顺带给个提示音让玩家知道是盾救的
        player.level().playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT,
                SoundSource.PLAYERS, 0.6F, 1.5F);
        return (float) (amount - absorbed);
    }

    /** 生机盾：每次受伤叠一层，最多若干层。 */
    private static void stackVigor(ServerPlayer player, long now) {
        int max = Math.max(1, MedalsConfig.SHIELD_VITALITY_MAX_LAYERS.get());
        Vigor prev = VIGOR.get(player.getUUID());
        int layers = (prev != null) ? Math.min(max, prev.layers() + 1) : 1;
        VIGOR.put(player.getUUID(), new Vigor(now, layers));
    }

    // ================================================================
    // 二、每 tick 的状态维护
    // ================================================================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        // 先做一次廉价判断:没戴任何盾且上一 tick 也没戴,直接走人
        Set<TankShieldType> shields = TankShieldType.activeSet(player);
        if (shields.isEmpty() && !isTracked(player)) {
            return;
        }

        long now = player.level().getGameTime();
        updateEarth(player, shields, now);
        updateDevouring(player, shields);
        updateVigor(player, shields, now);
        updateVanguard(player, shields);
        updateEternal(player, shields);
    }

    /** 该玩家身上是否还留着需要清理的状态。 */
    private static boolean isTracked(ServerPlayer player) {
        UUID id = player.getUUID();
        return APPLIED_DEVOUR.containsKey(id) || APPLIED_VIGOR.containsKey(id)
                || VANGUARD_ARMOR_ON.containsKey(id) || VANGUARD_SPEED_ON.containsKey(id)
                || ETERNAL_ON.containsKey(id) || STILL.containsKey(id);
    }

    /** 大地盾：连续静止够久就进入「扎根」状态。 */
    private static void updateEarth(ServerPlayer player, Set<TankShieldType> shields, long now) {
        boolean equipped = shields.contains(TankShieldType.EARTH);
        UUID id = player.getUUID();

        if (!equipped) {
            if (STILL.remove(id) != null) {
                clearEarthAttributes(player);
            }
            return;
        }

        Still prev = STILL.get(id);
        Vec3 pos = player.position();
        // 水平位移超过半格就算动过 —— 用 0.5 而不是 0,免得被浮点抖动反复打断
        boolean moved = prev == null
                || prev.pos().distanceToSqr(pos.x, prev.pos().y, pos.z) > 0.25D;

        long since = moved ? now : prev.since();
        long need = Math.max(1, MedalsConfig.SHIELD_EARTH_SECONDS.get()) * 20L;
        boolean active = (now - since) >= need;

        STILL.put(id, new Still(pos, since, active));

        if (active) {
            AttributeGuard.apply(player, Attributes.KNOCKBACK_RESISTANCE,
                    ModifierIds.TANK_SHIELD_EARTH_KNOCKBACK, "tank_shield_earth_kb",
                    1.0D, AttributeModifier.Operation.ADDITION);
        } else {
            AttributeGuard.remove(player, Attributes.KNOCKBACK_RESISTANCE,
                    ModifierIds.TANK_SHIELD_EARTH_KNOCKBACK);
        }
    }

    private static void clearEarthAttributes(ServerPlayer player) {
        AttributeGuard.remove(player, Attributes.KNOCKBACK_RESISTANCE,
                ModifierIds.TANK_SHIELD_EARTH_KNOCKBACK);
    }

    /** 大地盾此刻是否处于生效状态（供受伤结算读取）。 */
    private static boolean isEarthActive(ServerPlayer player) {
        Still still = STILL.get(player.getUUID());
        return still != null && still.active();
    }

    /** 吞噬盾：把「已吞噬物种数」换算成最大生命加成。 */
    private static void updateDevouring(ServerPlayer player, Set<TankShieldType> shields) {
        UUID id = player.getUUID();
        double bonus = 0.0D;
        if (shields.contains(TankShieldType.DEVOURING)) {
            bonus = TankShieldItem.devourBonus(findShield(player, TankShieldType.DEVOURING));
        }

        Double applied = APPLIED_DEVOUR.get(id);
        if (applied != null && Math.abs(applied - bonus) < 1.0E-4D) {
            return;
        }
        if (bonus <= 0.0D) {
            APPLIED_DEVOUR.remove(id);
            AttributeGuard.remove(player, Attributes.MAX_HEALTH, ModifierIds.TANK_SHIELD_DEVOUR_HP);
            return;
        }

        boolean grew = applied == null || bonus > applied;
        APPLIED_DEVOUR.put(id, bonus);
        AttributeGuard.apply(player, Attributes.MAX_HEALTH,
                ModifierIds.TANK_SHIELD_DEVOUR_HP, "tank_shield_devour_hp",
                bonus, AttributeModifier.Operation.ADDITION);

        // ★ 增加上限时把多出来的那截血一并补上 ——
        //   否则玩家只会看到"血条上限涨了但血还是半管",像是没生效。
        if (grew) {
            player.setHealth(Math.min(player.getMaxHealth(),
                    player.getHealth() + (float) bonus));
        }
    }

    /** 生机盾：层数 → 治疗加成，超时清空。 */
    private static void updateVigor(ServerPlayer player, Set<TankShieldType> shields, long now) {
        UUID id = player.getUUID();
        int layers = 0;
        if (shields.contains(TankShieldType.VITALITY)) {
            long window = Math.max(1, MedalsConfig.SHIELD_VITALITY_WINDOW.get()) * 20L;
            Vigor vigor = VIGOR.get(id);
            if (vigor != null && now - vigor.at() <= window) {
                layers = vigor.layers();
            } else if (vigor != null) {
                VIGOR.remove(id);
            }
        }

        if (java.util.Objects.equals(APPLIED_VIGOR.get(id), layers)) {
            return;
        }
        APPLIED_VIGOR.put(id, layers);

        // beta30 起 healing_received 由本模组自注册，永远存在，无需判空
        var healing = RealmAttributes.healingReceived();
        if (layers <= 0) {
            AttributeGuard.remove(player, healing, ModifierIds.TANK_SHIELD_VITALITY_HEAL);
            return;
        }
        AttributeGuard.apply(player, healing, ModifierIds.TANK_SHIELD_VITALITY_HEAL,
                "tank_shield_vitality_heal",
                layers * MedalsConfig.pct(MedalsConfig.SHIELD_VITALITY_PER_LAYER),
                AttributeModifier.Operation.ADDITION);
    }

    /** 先锋盾：副手持盾给护甲、举盾给移速、举盾前进时开路。 */
    private static void updateVanguard(ServerPlayer player, Set<TankShieldType> shields) {
        UUID id = player.getUUID();
        boolean has = shields.contains(TankShieldType.VANGUARD);

        boolean armorOn = has && isHoldingShield(player);
        if (!java.util.Objects.equals(VANGUARD_ARMOR_ON.get(id), armorOn)) {
            VANGUARD_ARMOR_ON.put(id, armorOn);
            if (armorOn) {
                AttributeGuard.apply(player, Attributes.ARMOR, ModifierIds.TANK_SHIELD_VANGUARD_ARMOR,
                        "tank_shield_vanguard_armor",
                        MedalsConfig.pct(MedalsConfig.SHIELD_VANGUARD_ARMOR),
                        AttributeModifier.Operation.MULTIPLY_BASE);
            } else {
                AttributeGuard.remove(player, Attributes.ARMOR, ModifierIds.TANK_SHIELD_VANGUARD_ARMOR);
            }
        }

        boolean speedOn = has && player.isBlocking();
        if (!java.util.Objects.equals(VANGUARD_SPEED_ON.get(id), speedOn)) {
            VANGUARD_SPEED_ON.put(id, speedOn);
            if (speedOn) {
                AttributeGuard.apply(player, Attributes.MOVEMENT_SPEED, ModifierIds.TANK_SHIELD_VANGUARD_SPEED,
                        "tank_shield_vanguard_speed",
                        MedalsConfig.pct(MedalsConfig.SHIELD_VANGUARD_SPEED),
                        AttributeModifier.Operation.MULTIPLY_BASE);
            } else {
                AttributeGuard.remove(player, Attributes.MOVEMENT_SPEED, ModifierIds.TANK_SHIELD_VANGUARD_SPEED);
            }
        }

        // 举盾前进 → 把撞到的敌人推开,替身后的人撞出一条路
        if (has && player.isBlocking() && !player.isShiftKeyDown()) {
            pushAhead(player);
        }
    }

    /** 副手是否拿着盾牌（原版盾或任何实现了盾牌语义的物品）。 */
    private static boolean isHoldingShield(ServerPlayer player) {
        ItemStack off = player.getOffhandItem();
        if (off.isEmpty()) {
            return false;
        }
        return off.is(net.minecraft.world.item.Items.SHIELD)
                || off.getUseAnimation() == net.minecraft.world.item.UseAnim.BLOCK;
    }

    /** 先锋盾的开路：把正前方贴脸的敌人推开。 */
    private static void pushAhead(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        double radius = MedalsConfig.SHIELD_VANGUARD_PUSH_RADIUS.get();
        double power = MedalsConfig.SHIELD_VANGUARD_PUSH.get();
        if (power <= 0.0D) {
            return;
        }
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        if (flat.lengthSqr() < 1.0E-6D) {
            return;
        }
        flat = flat.normalize();

        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                e -> e != player && e.isAlive() && !e.isAlliedTo(player))) {
            // 只推"前面"的:目标方向与朝向的点积为正才算挡路
            Vec3 to = victim.position().subtract(player.position());
            Vec3 toFlat = new Vec3(to.x, 0.0D, to.z);
            if (toFlat.lengthSqr() < 1.0E-6D || toFlat.normalize().dot(flat) < 0.35D) {
                continue;
            }
            victim.push(flat.x * power, 0.12D, flat.z * power);
            victim.hurtMarked = true;
        }
    }

    /** 不朽神盾：五项常驻属性 + 不屈壁垒永久生效。 */
    private static void updateEternal(ServerPlayer player, Set<TankShieldType> shields) {
        UUID id = player.getUUID();
        boolean on = shields.contains(TankShieldType.ETERNAL);

        if (!java.util.Objects.equals(ETERNAL_ON.get(id), on)) {
            ETERNAL_ON.put(id, on);
            if (on) {
                AttributeGuard.apply(player, Attributes.ARMOR, ModifierIds.TANK_SHIELD_ETERNAL_ARMOR,
                        "tank_shield_eternal_armor",
                        MedalsConfig.pct(MedalsConfig.SHIELD_ETERNAL_ARMOR),
                        AttributeModifier.Operation.MULTIPLY_BASE);
                AttributeGuard.apply(player, Attributes.MAX_HEALTH, ModifierIds.TANK_SHIELD_ETERNAL_HEALTH,
                        "tank_shield_eternal_health",
                        MedalsConfig.pct(MedalsConfig.SHIELD_ETERNAL_HEALTH),
                        AttributeModifier.Operation.MULTIPLY_BASE);
                AttributeGuard.apply(player, Attributes.ARMOR_TOUGHNESS, ModifierIds.TANK_SHIELD_ETERNAL_TOUGHNESS,
                        "tank_shield_eternal_toughness",
                        MedalsConfig.pct(MedalsConfig.SHIELD_ETERNAL_TOUGHNESS),
                        AttributeModifier.Operation.MULTIPLY_BASE);
                AttributeGuard.apply(player, Attributes.MOVEMENT_SPEED, ModifierIds.TANK_SHIELD_ETERNAL_SPEED,
                        "tank_shield_eternal_speed",
                        MedalsConfig.pct(MedalsConfig.SHIELD_ETERNAL_SPEED),
                        AttributeModifier.Operation.MULTIPLY_BASE);
                // 最大生命涨了,顺手把血补满,免得面板显示 30/40 让人以为没生效
                player.setHealth(player.getMaxHealth());
            } else {
                AttributeGuard.remove(player, Attributes.ARMOR, ModifierIds.TANK_SHIELD_ETERNAL_ARMOR);
                AttributeGuard.remove(player, Attributes.MAX_HEALTH, ModifierIds.TANK_SHIELD_ETERNAL_HEALTH);
                AttributeGuard.remove(player, Attributes.ARMOR_TOUGHNESS, ModifierIds.TANK_SHIELD_ETERNAL_TOUGHNESS);
                AttributeGuard.remove(player, Attributes.MOVEMENT_SPEED, ModifierIds.TANK_SHIELD_ETERNAL_SPEED);
                AttributeGuard.remove(player, Attributes.ARMOR,
                        ModifierIds.TANK_SHIELD_ETERNAL_BULWARK_ARMOR);
                var damageReduction = com.lost_realm.item.TankAttributes.DAMAGE_REDUCTION.get();
                AttributeGuard.remove(player, damageReduction,
                        ModifierIds.TANK_SHIELD_ETERNAL_BULWARK_DR);
            }
        }

        if (!on) {
            return;
        }

        // 「举盾不降速」不在这里做 —— 原版的减速发生在<b>客户端输入层</b>
        // （LocalPlayer#aiStep 里把 leftImpulse / forwardImpulse 乘 0.2），
        // 服务端怎么调移速属性都盖不住。见 mixin/client/LocalPlayerSlowdownMixin。

        // ---------- 不屈壁垒常驻 ----------
        // 复用 Lv.40 主动技能的三个部分:护甲 +20%、减伤 +20%、生命恢复 III。
        AttributeGuard.apply(player, Attributes.ARMOR, ModifierIds.TANK_SHIELD_ETERNAL_BULWARK_ARMOR,
                "tank_shield_eternal_bulwark_armor",
                MedalsConfig.pct(MedalsConfig.T20_ARMOR_BONUS),
                AttributeModifier.Operation.MULTIPLY_BASE);
        AttributeGuard.apply(player, com.lost_realm.item.TankAttributes.DAMAGE_REDUCTION.get(),
                ModifierIds.TANK_SHIELD_ETERNAL_BULWARK_DR, "tank_shield_eternal_bulwark_dr",
                MedalsConfig.pct(MedalsConfig.T20_DAMAGE_REDUCTION),
                AttributeModifier.Operation.ADDITION);
        if (player.tickCount % 20 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 2, false, false, true));
        }
    }

    // ================================================================
    // 三、专项拦截
    // ================================================================

    /**
     * 先锋盾：格挡成功时把攻击者顶回去。
     *
     * <p>Forge 提供了 {@link ShieldBlockEvent}，不必自己去比对朝向与角度。
     */
    @SubscribeEvent
    public static void onShieldBlock(ShieldBlockEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!TankShieldType.VANGUARD.isActive(player)) {
            return;
        }
        if (!(event.getDamageSource().getEntity() instanceof LivingEntity attacker) || attacker == player) {
            return;
        }
        Vec3 dir = attacker.position().subtract(player.position());
        Vec3 flat = new Vec3(dir.x, 0.0D, dir.z);
        if (flat.lengthSqr() < 1.0E-6D) {
            return;
        }
        double power = MedalsConfig.SHIELD_VANGUARD_KNOCKBACK.get();
        flat = flat.normalize().scale(power * 0.6D);
        attacker.push(flat.x, 0.25D, flat.z);
        attacker.hurtMarked = true;
        player.level().playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK,
                SoundSource.PLAYERS, 1.0F, 1.4F);
    }

    /** 不朽神盾：免疫一切击退。 */
    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        if (TankShieldType.ETERNAL.isActive(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * 不朽神盾：免疫所有负面效果。
     *
     * <p>用 {@code Applicable} 而不是 {@code Added} —— 前者在效果<b>即将加上去</b>时
     * 就把它按掉，不会有"加了一帧再删掉"的闪烁，也不会触发一次性的附加逻辑。
     */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null || instance.getEffect().getCategory() != MobEffectCategory.HARMFUL) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player
                && TankShieldType.ETERNAL.isActive(player)) {
            event.setResult(Event.Result.DENY);
        }
    }

    /**
     * 眩晕：让带效果的目标动不了、也打不出。
     *
     * <p>逐 tick 处理而不是靠属性修饰符：只砍移速的话，生物仍会转身、仍会挥拳，
     * 只是站着不动 —— 那看着像卡住了，不像被打懵了。清速度 + 停寻路 + 清目标
     * 三件事一起做，才有"被打晕"的样子。
     */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide()) {
            return;
        }
        if (!(event.level instanceof ServerLevel level)) {
            return;
        }
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                continue;
            }
            if (!living.hasEffect(ModEffects.STUNNED.get())) {
                continue;
            }
            // 动不了
            Vec3 motion = living.getDeltaMovement();
            living.setDeltaMovement(0.0D, motion.y, 0.0D);
            living.hurtMarked = true;
            if (living instanceof Mob mob) {
                mob.getNavigation().stop();
                mob.setTarget(null);
            }
            // 看得见:头顶转圈的小星星
            if (living.tickCount % 4 == 0) {
                level.sendParticles(ParticleTypes.END_ROD,
                        living.getX(), living.getY() + living.getBbHeight() + 0.35D, living.getZ(),
                        3, 0.25D, 0.1D, 0.25D, 0.01D);
            }
        }
    }

    /** 吞噬盾：击杀新物种时把它的 id 记在盾上。 */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getEntity() instanceof Enemy)) {
            return;   // 只记敌对生物
        }
        ItemStack shield = findShield(player, TankShieldType.DEVOURING);
        if (shield.isEmpty()) {
            return;
        }
        String species = EntityType.getKey(event.getEntity().getType()).toString();
        Set<String> known = TankShieldItem.storedSpecies(shield);
        if (known.add(species)) {
            TankShieldItem.storeSpecies(shield, known);
            // 属性会在下一 tick 的 updateDevouring 里刷新,这里不重复动手
        }
    }

    /** 玩家退出时清空运行时状态。 */
    @SubscribeEvent
    public static void onPlayerLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        ADAPT.remove(id);
        STILL.remove(id);
        VIGOR.remove(id);
        APPLIED_DEVOUR.remove(id);
        APPLIED_VIGOR.remove(id);
        VANGUARD_ARMOR_ON.remove(id);
        VANGUARD_SPEED_ON.remove(id);
        ETERNAL_ON.remove(id);
    }

    // ================================================================
    // 工具
    // ================================================================

    /** 从饰品栏里取出指定类型的盾（没有则返回空）。 */
    private static ItemStack findShield(ServerPlayer player, TankShieldType type) {
        return CuriosApi.getCuriosInventory(player).map(handler ->
                handler.findCurios(s -> s.getItem() instanceof TankShieldItem shield
                                && shield.shieldType() == type)
                        .stream()
                        .map(found -> found.stack())
                        .findFirst()
                        .orElse(ItemStack.EMPTY)
        ).orElse(ItemStack.EMPTY);
    }

    /**
     * 是不是「环境伤害」。
     *
     * <p>涵盖火、岩浆、溺水、摔落、仙人掌、浆果丛、冻结、窒息、饥饿。
     * <b>刻意不含虚空</b> —— 掉进虚空不死会一直下落，玩家只能自杀或退游戏，
     * 那不是"爽"，那是把存档卡住。
     */
    private static boolean isEnvironmental(net.minecraft.world.damagesource.DamageSource source) {
        return source.is(net.minecraft.world.damagesource.DamageTypes.IN_FIRE)
                || source.is(net.minecraft.world.damagesource.DamageTypes.ON_FIRE)
                || source.is(net.minecraft.world.damagesource.DamageTypes.LAVA)
                || source.is(net.minecraft.world.damagesource.DamageTypes.HOT_FLOOR)
                || source.is(net.minecraft.world.damagesource.DamageTypes.DROWN)
                || source.is(net.minecraft.world.damagesource.DamageTypes.FALL)
                || source.is(net.minecraft.world.damagesource.DamageTypes.CACTUS)
                || source.is(net.minecraft.world.damagesource.DamageTypes.SWEET_BERRY_BUSH)
                || source.is(net.minecraft.world.damagesource.DamageTypes.FREEZE)
                || source.is(net.minecraft.world.damagesource.DamageTypes.STARVE)
                || source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)
                || source.is(DamageTypeTags.IS_FIRE);
    }

    private TankShieldHandler() {}
}
