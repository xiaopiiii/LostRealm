package com.lost_realm.event;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.lost_realm.Medals;
import com.lost_realm.compat.goety.GoetyBridge;
import com.lost_realm.config.MedalsConfig;
import com.lost_realm.item.MageCrystalType;
import com.lost_realm.util.AttributeGuard;
import com.lost_realm.util.ModifierIds;

/**
 * 九枚「晶石」法师饰品的全部效果（含九合一的主宰晶石）。
 *
 * <h2>法术命中怎么感知</h2>
 * Goety 没有开放「法术命中」事件，所以整套东西和本模组的法师技能一样，
 * 以<b>魔法伤害事件</b>作为命中信号 —— 入口是
 * {@code CombatHandler#handleMageOnDamage}，本类由它调用，不自己挂事件。
 * 这样法爆、虹吸、弹幕风暴与聚晶的顺序是确定的，不会互相打架。
 *
 * <h2>分三类</h2>
 * <ul>
 *   <li><b>命中时</b>（{@link #onSpellDamage}）—— 狙击改伤害；连锁 / 魂燃 / 咒钉 /
 *       余烬 / 回响做附加效果。</li>
 *   <li><b>状态维护</b>（{@link TickEvent.PlayerTickEvent}）—— 法阵、魔力透支、
 *       疾行、主宰的属性。</li>
 *   <li><b>其它事件</b> —— 咒钉引爆挂 {@link MobEffectEvent.Added}，
 *       余烬的持续伤害挂 LevelTick。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Medals.MODID)
public final class MageCrystalHandler {

    /** 咒钉层数（记在目标身上）。 */
    private static final String TAG_HEXNAIL = "lr_hexnail";

    // ================================================================
    // 运行时状态
    // ================================================================

    /** 余烬：一片持续灼烧的区域。 */
    private record Ember(ServerLevel level, Vec3 pos, double radius,
                         float damagePerSecond, long expireAt, UUID owner) {}

    private static final List<Ember> EMBERS = new ArrayList<>();

    /** 法阵：玩家脚下的阵地（含到期时刻）。 */
    private record Circle(ServerLevel level, Vec3 pos, long expireAt) {}

    private static final Map<UUID, Circle> CIRCLES = new HashMap<>();

    /** 魔力透支：本次透支施法的有效期截止刻。 */
    private static final Map<UUID, Long> OVERDRAFT_UNTIL = new HashMap<>();

    /** 属性是否已挂上（只在翻转时动，避免每 tick 刷属性）。 */
    private static final Map<UUID, Boolean> CIRCLE_ON = new HashMap<>();
    private static final Map<UUID, Boolean> OVERDRAFT_ON = new HashMap<>();
    private static final Map<UUID, Boolean> SWIFT_ON = new HashMap<>();
    private static final Map<UUID, Boolean> SOVEREIGN_ON = new HashMap<>();

    // ================================================================
    // 一、法术命中（由 CombatHandler 调用）
    // ================================================================

    /**
     * 一次法术命中时，所有聚晶饰品的效果。
     *
     * @param caster 施法者
     * @param target 被命中的目标
     * @param event  该次伤害事件（可在此修正伤害）
     */
    public static void onSpellDamage(ServerPlayer caster, LivingEntity target, LivingDamageEvent event) {
        Set<MageCrystalType> crystals = MageCrystalType.activeSet(caster);
        if (crystals.isEmpty()) {
            return;
        }
        if (!(caster.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();

        // ---------- 狙击：距离越远越强（先改伤害，后面的附加效果都按新值算）----------
        if (crystals.contains(MageCrystalType.SNIPER)) {
            double bonus = sniperBonus(caster, target);
            if (bonus > 0.0D) {
                event.setAmount((float) (event.getAmount() * (1.0D + bonus)));
            }
        }

        // ---------- 魔力透支：透支施法的那一发有额外强度 ----------
        Long until = OVERDRAFT_UNTIL.get(caster.getUUID());
        if (crystals.contains(MageCrystalType.OVERDRAFT) && until != null && now <= until) {
            OVERDRAFT_UNTIL.remove(caster.getUUID());
            event.setAmount((float) (event.getAmount()
                    * (1.0D + MedalsConfig.pct(MedalsConfig.CRYSTAL_OVERDRAFT_BONUS))));
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    caster.getX(), caster.getY() + 1.6D, caster.getZ(),
                    20, 0.4D, 0.4D, 0.4D, 0.02D);
        }

        float amount = event.getAmount();

        if (crystals.contains(MageCrystalType.SOULFIRE)) {
            soulfire(caster, target, event);
            amount = event.getAmount();
        }
        if (crystals.contains(MageCrystalType.HEXNAIL)) {
            addHexNail(target);
        }
        if (crystals.contains(MageCrystalType.CHAIN)) {
            chain(caster, level, target, amount);
        }
        if (crystals.contains(MageCrystalType.EMBER)) {
            spawnEmber(caster, level, target, amount, now);
        }
        if (crystals.contains(MageCrystalType.ECHO)
                && caster.getRandom().nextDouble() < MedalsConfig.pct(MedalsConfig.CRYSTAL_ECHO_CHANCE)) {
            echoBoom(caster, level, target);
        }
    }

    /**
     * 狙击晶石：每 {@code step} 格算一档，每档提升若干法术强度，封顶。
     *
     * <p>用「施法瞬间的直线距离」而不是弹道距离 —— 后者要跟踪投射物，
     * 而法术命中时投射物已经没了。
     */
    private static double sniperBonus(ServerPlayer caster, LivingEntity target) {
        double distance = Math.sqrt(caster.distanceToSqr(target));
        int step = Math.max(1, MedalsConfig.CRYSTAL_SNIPER_STEP.get());
        int tiers = (int) Math.floor(distance / step);
        return Math.min(MedalsConfig.pct(MedalsConfig.CRYSTAL_SNIPER_CAP),
                tiers * MedalsConfig.pct(MedalsConfig.CRYSTAL_SNIPER_PER_STEP));
    }

    /**
     * 魂燃晶石：吞噬目标身上的一个减益 —— 换回灵魂，并让这一发更疼。
     *
     * <p>和「咒钉」是一对：魂燃把减益<b>吃掉</b>换资源，咒钉把减益当<b>引信</b>。
     * 同一种东西（减益），两种用法。
     */
    private static void soulfire(ServerPlayer caster, LivingEntity target, LivingDamageEvent event) {
        MobEffectInstance victim = null;
        for (MobEffectInstance inst : target.getActiveEffects()) {
            if (inst.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
                victim = inst;
                break;
            }
        }
        if (victim == null) {
            return;
        }
        target.removeEffect(victim.getEffect());

        double ratio = MedalsConfig.pct(MedalsConfig.CRYSTAL_SOULFIRE_REFUND);
        if (ratio > 0.0D) {
            GoetyBridge goety = GoetyBridge.get();
            if (goety != null) {
                int cost = currentSpellCost(caster, goety);
                if (cost > 0) {
                    goety.increaseSouls(caster, (int) Math.round(cost * ratio));
                }
            }
        }
        event.setAmount((float) (event.getAmount()
                * (1.0D + MedalsConfig.pct(MedalsConfig.CRYSTAL_SOULFIRE_DAMAGE))));

        if (caster.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SOUL,
                    target.getX(), target.getY() + target.getBbHeight() * 0.6D, target.getZ(),
                    16, 0.3D, 0.4D, 0.3D, 0.02D);
            level.playSound(null, target.blockPosition(), SoundEvents.SOUL_ESCAPE,
                    SoundSource.PLAYERS, 0.6F, 1.4F);
        }
    }

    /**
     * 连锁晶石：法术在敌人之间跳。
     *
     * <p>每一跳都从<b>上一个落点</b>继续找最近的未命中目标，画一道电光 ——
     * 视觉上就是一条折线，而不是以施法者为圆心的散射。
     */
    private static void chain(ServerPlayer caster, ServerLevel level, LivingEntity first, float amount) {
        double radius = MedalsConfig.CRYSTAL_CHAIN_RADIUS.get();
        double ratio = MedalsConfig.pct(MedalsConfig.CRYSTAL_CHAIN_RATIO);
        int maxJumps = MedalsConfig.CRYSTAL_CHAIN_JUMPS.get();
        if (maxJumps <= 0 || ratio <= 0.0D) {
            return;
        }

        Set<UUID> already = new HashSet<>();
        already.add(first.getUUID());
        LivingEntity from = first;
        float damage = amount;

        for (int i = 0; i < maxJumps; i++) {
            LivingEntity next = nearestUnhit(caster, level, from, radius, already);
            if (next == null) {
                break;
            }
            damage *= ratio;
            already.add(next.getUUID());

            next.hurt(caster.damageSources().indirectMagic(caster, caster), damage);
            drawArc(level, from, next);
            from = next;
        }
    }

    /** 在 {@code from} 周围找最近的、还没被这次连锁打过的敌人。 */
    private static LivingEntity nearestUnhit(ServerPlayer caster, ServerLevel level,
                                             LivingEntity from, double radius, Set<UUID> already) {
        List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class,
                from.getBoundingBox().inflate(radius),
                e -> e != caster && e.isAlive() && !e.isAlliedTo(caster)
                        && !already.contains(e.getUUID()));
        return list.stream()
                .min(Comparator.comparingDouble(from::distanceToSqr))
                .orElse(null);
    }

    /** 两个实体之间的电弧。 */
    private static void drawArc(ServerLevel level, LivingEntity from, LivingEntity to) {
        Vec3 a = from.getEyePosition();
        Vec3 b = to.getEyePosition();
        Vec3 delta = b.subtract(a);
        double length = delta.length();
        if (length < 1.0E-4D) {
            return;
        }
        Vec3 step = delta.normalize().scale(0.35D);
        int count = (int) Math.ceil(length / 0.35D);
        for (int i = 0; i <= count; i++) {
            Vec3 p = a.add(step.scale(i));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        level.playSound(null, to.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT,
                SoundSource.PLAYERS, 0.25F, 1.8F);
    }

    /**
     * 余烬晶石：在命中处留下一片持续灼烧的元素余烬。
     *
     * <p>伤害按「这一发法术的 X%」每秒结算，所以余烬的威力跟着你的法强走 ——
     * 不需要单独配置一个数值，也不会在后期变成挠痒痒。
     */
    private static void spawnEmber(ServerPlayer caster, ServerLevel level,
                                   LivingEntity target, float amount, long now) {
        int seconds = MedalsConfig.CRYSTAL_EMBER_SECONDS.get();
        double ratio = MedalsConfig.pct(MedalsConfig.CRYSTAL_EMBER_RATIO);
        if (seconds <= 0 || ratio <= 0.0D) {
            return;
        }
        Vec3 pos = target.position();
        EMBERS.add(new Ember(level, pos, 2.0D, (float) (amount * ratio), now + seconds * 20L, caster.getUUID()));
        level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y + 0.2D, pos.z,
                24, 1.2D, 0.2D, 1.2D, 0.02D);
    }

    /**
     * 咒钉晶石：给目标钉上一枚咒钉。
     *
     * <p>记在目标的 {@code PersistentData} 上而不是另建一张表 ——
     * 目标死了数据跟着走，天然不需要清理。
     */
    private static void addHexNail(LivingEntity target) {
        int max = Math.max(1, MedalsConfig.CRYSTAL_HEXNAIL_MAX.get());
        var tag = target.getPersistentData();
        int now = tag.getInt(TAG_HEXNAIL);
        if (now < max) {
            tag.putInt(TAG_HEXNAIL, now + 1);
        }
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ENCHANT,
                    target.getX(), target.getY() + target.getBbHeight() + 0.3D, target.getZ(),
                    6, 0.3D, 0.1D, 0.3D, 0.4D);
        }
    }

    /**
     * 咒钉引爆 —— 当你给目标挂上任意减益时触发。
     *
     * <p>挂的是 {@link MobEffectEvent.Added}：它只在效果<b>真的加上去</b>之后触发，
     * 所以不会被"免疫负面效果"之类的拦截误报。
     */
    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        if (event.getEffectInstance() == null
                || event.getEffectInstance().getEffect().getCategory() != MobEffectCategory.HARMFUL) {
            return;
        }
        // 只有「是你上的减益」才算 —— 别的怪给你目标上的不算
        Entity source = event.getEffectSource();
        if (!(source instanceof ServerPlayer caster) || caster == target) {
            return;
        }
        if (!MageCrystalType.HEXNAIL.isActive(caster)) {
            return;
        }

        var tag = target.getPersistentData();
        int nails = tag.getInt(TAG_HEXNAIL);
        if (nails <= 0) {
            return;
        }
        tag.putInt(TAG_HEXNAIL, 0);

        double per = MedalsConfig.pct(MedalsConfig.CRYSTAL_HEXNAIL_DAMAGE);
        // 以「当前法术强度推算的基准伤害」为底 —— 用目标的已损生命做一个保底,
        // 免得咒钉在后期变成挠痒痒
        float base = Math.max(2.0F, target.getMaxHealth() * 0.02F);
        float damage = (float) (base * nails * per * 5.0D);
        target.hurt(caster.damageSources().indirectMagic(caster, caster), damage);

        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                    8 * nails, 0.4D, 0.5D, 0.4D, 0.05D);
            level.sendParticles(ParticleTypes.CRIT,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                    10 * nails, 0.5D, 0.5D, 0.5D, 0.2D);
            level.playSound(null, target.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK,
                    SoundSource.PLAYERS, 0.8F, 1.5F);
        }
    }

    /**
     * 回响晶石：朝目标方向打出一道坚守者音波，贯穿路径上的所有敌人。
     *
     * <p>方向取「施法者 → 目标」，射程固定 16 格。这是从射手那边搬过来的机制 ——
     * 「回响」这个名字本来就是为声音准备的。
     */
    private static void echoBoom(ServerPlayer caster, ServerLevel level, LivingEntity target) {
        Vec3 from = caster.getEyePosition();
        Vec3 delta = target.getEyePosition().subtract(from);
        if (delta.lengthSqr() < 1.0E-6D) {
            return;
        }
        Vec3 dir = delta.normalize();
        double range = 16.0D;
        float damage = MedalsConfig.CRYSTAL_ECHO_DAMAGE.get();
        Vec3 end = from.add(dir.scale(range));

        int count = (int) Math.ceil(range / 0.5D);
        for (int i = 0; i <= count; i++) {
            Vec3 p = from.add(dir.scale(i * 0.5D));
            level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        level.playSound(null, caster.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.PLAYERS, 1.0F, 1.0F);

        double hitRadius = 1.5D;
        AABB box = new AABB(from, end).inflate(hitRadius);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != caster && e.isAlive() && !e.isAlliedTo(caster))) {
            if (distanceToSegment(victim.getBoundingBox().getCenter(), from, end) > hitRadius) {
                continue;
            }
            victim.hurt(caster.damageSources().indirectMagic(caster, caster), damage);
            Vec3 kb = dir.scale(2.0D);
            victim.push(kb.x, 0.3D, kb.z);
            victim.hurtMarked = true;
        }
    }

    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double lenSqr = ab.lengthSqr();
        if (lenSqr < 1.0E-9D) {
            return p.distanceTo(a);
        }
        double t = Math.max(0.0D, Math.min(1.0D, p.subtract(a).dot(ab) / lenSqr));
        return p.distanceTo(a.add(ab.scale(t)));
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
        Set<MageCrystalType> crystals = MageCrystalType.activeSet(player);
        if (crystals.isEmpty() && !isTracked(player)) {
            return;
        }
        long now = player.level().getGameTime();

        updateCircle(player, crystals, now);
        updateOverdraft(player, crystals, now);
        updateSwift(player, crystals);
        updateSovereign(player, crystals);
    }

    private static boolean isTracked(ServerPlayer player) {
        UUID id = player.getUUID();
        return CIRCLES.containsKey(id) || OVERDRAFT_UNTIL.containsKey(id)
                || CIRCLE_ON.containsKey(id) || OVERDRAFT_ON.containsKey(id)
                || SWIFT_ON.containsKey(id) || SOVEREIGN_ON.containsKey(id);
    }

    /** 玩家是否正在施法（手持魔杖并使用中）。 */
    private static boolean isCasting(ServerPlayer player) {
        if (!player.isUsingItem()) {
            return false;
        }
        GoetyBridge goety = GoetyBridge.get();
        return goety != null && goety.isHoldingWand(player);
    }

    /**
     * 法阵晶石：施法时在脚下画一座法阵，站进去才吃加成。
     *
     * <p>这是「阵地」而不是「增益」—— 走出圈就立刻失效，
     * 所以要打输出就得先想好站哪，而不是边跑边放。
     */
    private static void updateCircle(ServerPlayer player, Set<MageCrystalType> crystals, long now) {
        UUID id = player.getUUID();
        boolean equipped = crystals.contains(MageCrystalType.CIRCLE);

        if (!equipped) {
            if (CIRCLES.remove(id) != null) {
                clearCircleAttributes(player);
                CIRCLE_ON.remove(id);
            }
            return;
        }

        if (isCasting(player)) {
            int seconds = Math.max(1, MedalsConfig.CRYSTAL_CIRCLE_SECONDS.get());
            CIRCLES.put(id, new Circle(player.serverLevel(), player.position(), now + seconds * 20L));
        }

        Circle circle = CIRCLES.get(id);
        boolean inside = circle != null && now <= circle.expireAt()
                && circle.pos().distanceToSqr(player.position()) <= Math.pow(
                        MedalsConfig.CRYSTAL_CIRCLE_RADIUS.get(), 2.0D);

        if (!Objects.equals(CIRCLE_ON.get(id), inside)) {
            CIRCLE_ON.put(id, inside);
            if (inside) {
                applyCircleAttributes(player);
            } else {
                clearCircleAttributes(player);
            }
        }

        // 阵纹粒子:让圈看得见
        if (circle != null && now <= circle.expireAt() && player.tickCount % 4 == 0) {
            double radius = MedalsConfig.CRYSTAL_CIRCLE_RADIUS.get();
            ServerLevel level = circle.level();
            for (int i = 0; i < 8; i++) {
                double angle = (player.tickCount * 0.06D) + i * (Math.PI / 4.0D);
                level.sendParticles(ParticleTypes.ENCHANT,
                        circle.pos().x + Math.cos(angle) * radius,
                        circle.pos().y + 0.1D,
                        circle.pos().z + Math.sin(angle) * radius,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    private static void applyCircleAttributes(ServerPlayer player) {
        GoetyBridge goety = GoetyBridge.get();
        if (goety == null) {
            return;
        }
        goety.applyCastingSpeed(player, ModifierIds.MAGE_CRYSTAL_CIRCLE_SPEED,
                "mage_crystal_circle_speed",
                MedalsConfig.pct(MedalsConfig.CRYSTAL_CIRCLE_SPEED));
        goety.applySpellPotency(player, ModifierIds.MAGE_CRYSTAL_CIRCLE_POTENCY,
                "mage_crystal_circle_potency",
                MedalsConfig.pct(MedalsConfig.CRYSTAL_CIRCLE_POTENCY));
    }

    private static void clearCircleAttributes(ServerPlayer player) {
        GoetyBridge goety = GoetyBridge.get();
        if (goety == null) {
            return;
        }
        goety.applyCastingSpeed(player, ModifierIds.MAGE_CRYSTAL_CIRCLE_SPEED,
                "mage_crystal_circle_speed", 0.0D);
        goety.applySpellPotency(player, ModifierIds.MAGE_CRYSTAL_CIRCLE_POTENCY,
                "mage_crystal_circle_potency", 0.0D);
    }

    /**
     * 魔力透支晶石：灵魂不够时，拿命来换。
     *
     * <p>做法是「补足差额」而不是「拦截施法」—— 后者要 hook Goety 内部的校验，
     * 既脆弱又容易和别的模组打架。这里直接在看得出你缺灵魂时把差的那部分
     * 用生命折价补上，效果一样，而且是纯服务端行为。
     */
    private static void updateOverdraft(ServerPlayer player, Set<MageCrystalType> crystals, long now) {
        UUID id = player.getUUID();
        boolean equipped = crystals.contains(MageCrystalType.OVERDRAFT);

        if (!equipped) {
            OVERDRAFT_ON.remove(id);
            OVERDRAFT_UNTIL.remove(id);
            return;
        }
        GoetyBridge goety = GoetyBridge.get();
        if (goety == null) {
            return;
        }
        ItemStack wand = holdingWand(player, goety);
        if (wand.isEmpty()) {
            OVERDRAFT_ON.remove(id);
            return;
        }
        int cost = goety.wandSoulCost(player, wand);
        if (cost <= 0) {
            OVERDRAFT_ON.remove(id);
            return;
        }
        int souls = goety.getSouls(player);
        if (souls >= cost) {
            OVERDRAFT_ON.remove(id);
            return;
        }

        int ratio = Math.max(1, MedalsConfig.CRYSTAL_OVERDRAFT_RATIO.get());
        int missing = cost - souls;
        int hpCost = (int) Math.ceil((double) missing / ratio);
        // 留 2 点血垫底,别让这枚饰品把人烧死
        if (player.getHealth() <= hpCost + 2.0F) {
            return;
        }

        player.hurt(player.damageSources().magic(), hpCost);
        goety.increaseSouls(player, missing);
        OVERDRAFT_ON.put(id, true);
        // 3 秒内打出法术算「透支施法」,吃那份额外加成
        OVERDRAFT_UNTIL.put(id, now + 60L);

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    player.getX(), player.getY() + 1.2D, player.getZ(),
                    8, 0.3D, 0.3D, 0.3D, 0.05D);
        }
    }

    /**
     * 疾行晶石：把施法时的减速砍掉一半。
     *
     * <p>实现方式是「给一份正向移速补偿」。原版的举盾减速在<b>客户端输入层</b>
     * （见 {@code LocalPlayerSlowdownMixin}），但 Goety 的施法减速走的是属性，
     * 所以这里用属性补偿是有效的 —— 如果实测发现无效，那说明它也改在了输入层，
     * 需要另做 Mixin。
     */
    private static void updateSwift(ServerPlayer player, Set<MageCrystalType> crystals) {
        UUID id = player.getUUID();
        boolean on = crystals.contains(MageCrystalType.SWIFT) && isCasting(player);

        if (Objects.equals(SWIFT_ON.get(id), on)) {
            return;
        }
        SWIFT_ON.put(id, on);
        if (on) {
            AttributeGuard.apply(player, Attributes.MOVEMENT_SPEED, ModifierIds.MAGE_CRYSTAL_SWIFT,
                    "mage_crystal_swift",
                    MedalsConfig.pct(MedalsConfig.CRYSTAL_SWIFT_REDUCTION) * 0.5D,
                    AttributeModifier.Operation.MULTIPLY_BASE);
        } else {
            AttributeGuard.remove(player, Attributes.MOVEMENT_SPEED, ModifierIds.MAGE_CRYSTAL_SWIFT);
        }
    }

    /** 主宰晶石：法强大幅提升，且不再消耗灵魂。 */
    private static void updateSovereign(ServerPlayer player, Set<MageCrystalType> crystals) {
        UUID id = player.getUUID();
        boolean on = crystals.contains(MageCrystalType.SOVEREIGN);

        if (Objects.equals(SOVEREIGN_ON.get(id), on)) {
            return;
        }
        SOVEREIGN_ON.put(id, on);

        GoetyBridge goety = GoetyBridge.get();
        if (goety == null) {
            return;
        }
        double potency = on ? MedalsConfig.pct(MedalsConfig.CRYSTAL_SOVEREIGN_POTENCY) : 0.0D;
        double casting = on ? MedalsConfig.pct(MedalsConfig.CRYSTAL_SOVEREIGN_CASTING) : 0.0D;
        double radius = on ? MedalsConfig.pct(MedalsConfig.CRYSTAL_SOVEREIGN_RADIUS) : 0.0D;
        // 灵魂折扣是「减免比例」,给满 100% 就是不消耗
        double soulOff = on ? MedalsConfig.pct(MedalsConfig.CRYSTAL_SOVEREIGN_SOUL) : 0.0D;

        goety.applySpellPotency(player, ModifierIds.MAGE_CRYSTAL_SOVEREIGN_POTENCY,
                "mage_crystal_sovereign_potency", potency);
        goety.applyCastingSpeed(player, ModifierIds.MAGE_CRYSTAL_SOVEREIGN_CASTING,
                "mage_crystal_sovereign_casting", casting);
        goety.applySpellRadius(player, ModifierIds.MAGE_CRYSTAL_SOVEREIGN_RADIUS,
                "mage_crystal_sovereign_radius", radius);
        goety.applySoulDiscount(player, ModifierIds.MAGE_CRYSTAL_SOVEREIGN_SOUL,
                "mage_crystal_sovereign_soul", soulOff);
    }

    // ================================================================
    // 三、余烬的持续伤害
    // ================================================================

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide()) {
            return;
        }
        if (!(event.level instanceof ServerLevel level)) {
            return;
        }
        if (EMBERS.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        var it = EMBERS.iterator();
        boolean damagedThisTick = false;
        while (it.hasNext()) {
            Ember ember = it.next();
            if (ember.level() != level) {
                continue;
            }
            if (now >= ember.expireAt()) {
                it.remove();
                continue;
            }
            // 每 20 tick 结算一次,并按当前伤害值补一发可见的火焰
            if (now % 20L == 0L) {
                for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(ember.pos(), ember.pos()).inflate(ember.radius()),
                        e -> e.isAlive() && !e.getUUID().equals(ember.owner()))) {
                    victim.hurt(level.damageSources().magic(), ember.damagePerSecond());
                }
            }
            level.sendParticles(ParticleTypes.FLAME,
                    ember.pos().x, ember.pos().y + 0.15D, ember.pos().z,
                    3, ember.radius() * 0.6D, 0.1D, ember.radius() * 0.6D, 0.01D);
            damagedThisTick = damagedThisTick || true;
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        CIRCLES.remove(id);
        OVERDRAFT_UNTIL.remove(id);
        OVERDRAFT_ON.remove(id);
        CIRCLE_ON.remove(id);
        SWIFT_ON.remove(id);
        SOVEREIGN_ON.remove(id);
    }

    // ================================================================
    // 工具
    // ================================================================

    /** 玩家手里的魔杖（主手优先）。 */
    private static ItemStack holdingWand(ServerPlayer player, GoetyBridge goety) {
        ItemStack main = player.getMainHandItem();
        if (goety.isWand(main)) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        return goety.isWand(off) ? off : ItemStack.EMPTY;
    }

    /** 当前手持魔杖那发法术的灵魂成本。 */
    private static int currentSpellCost(ServerPlayer player, GoetyBridge goety) {
        ItemStack wand = holdingWand(player, goety);
        return wand.isEmpty() ? 0 : goety.wandSoulCost(player, wand);
    }

    private MageCrystalHandler() {}
}
