package com.lost_realm.event;

import com.lost_realm.Medals;
import com.lost_realm.config.MedalsConfig;
import com.lost_realm.data.PlayerMedals;
import com.lost_realm.item.ArrowCharmType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 箭类饰品的全部效果（药水箭 / 爆炸箭 / 贯穿之箭 / 反重力之箭 / 星辰贯穿之箭）。
 *
 * <h2>「是本模组的箭」怎么认</h2>
 * 项目没有自定义箭矢实体（复用原版 {@code Arrow}），所以识别全靠<b>箭上的 NBT 标记</b>：
 * <ul>
 *   <li>{@link #TAG_PIERCE_HITS} —— 已命中目标数，贯穿衰减用</li>
 *   <li>{@link #TAG_FLY_START} —— 反重力箭的出生时刻，用来超时消散</li>
 *   <li>{@link #TAG_STAR} —— 星辰箭，放大渲染 / 万点伤害 / 破坏路径全靠它</li>
 *   <li>{@link #TAG_EXPLOSIVE} —— 发射时戴着爆炸箭，命中才会炸</li>
 *   <li>{@link #TAG_FULL_CHARGE} —— 射出时蓄力拉满，爆炸箭的硬门槛</li>
 * </ul>
 * 标记只在「箭生成的那一刻」按玩家当时佩戴的饰品打上，之后饰品被摘掉也不会改变
 * 已经飞出去的箭 —— 否则玩家可以在箭命中前临时换装。
 *
 * <h2>为什么没有实体 tick 钩子</h2>
 * Forge 1.20.1 <b>没有</b> {@code EntityTickEvent}（那是更高版本才有的）。
 * 需要逐 tick 驱动的两件事（反重力箭超时、星辰箭破坏+拖尾）统一由
 * {@link #onLevelTick} 每秒/每 tick 扫一遍实体来做，代价是每 tick 一次
 * {@code getAllEntities()} 遍历 —— 与 MC 自身的遍历频率同量级，可以接受。
 */
@Mod.EventBusSubscriber(modid = Medals.MODID)
public final class ArrowCharmHandler {

    /** 箭上记录「已经命中过几个目标」。 */
    private static final String TAG_PIERCE_HITS = "lr_pierce_hits";
    /** 反重力箭的出生游戏刻。 */
    private static final String TAG_FLY_START = "lr_fly_start";
    /** 星辰箭标记。 */
    public static final String TAG_STAR = "lr_star_arrow";
    /** 发射时戴着爆炸箭（命中才会炸）。 */
    private static final String TAG_EXPLOSIVE = "lr_explosive";
    /** 射出时蓄力拉满（爆炸箭的硬门槛）。 */
    private static final String TAG_FULL_CHARGE = "lr_full_charge";
    /** 星辰箭上一 tick 的飞行速度分量（撞停时用来恢复）。 */
    private static final String TAG_STAR_VX = "lr_star_vx";
    private static final String TAG_STAR_VY = "lr_star_vy";
    private static final String TAG_STAR_VZ = "lr_star_vz";
    /** 雷鸣箭：命中时召唤落雷。 */
    private static final String TAG_THUNDER = "lr_thunder";
    /** 双响之箭：命中时补射。 */
    private static final String TAG_TWIN = "lr_twin";
    /** 穿甲箭：命中时无视一部分护甲。 */
    private static final String TAG_ARMOR_PIERCE = "lr_armor_pierce";
    /** 定身箭：命中时按概率定身。 */
    private static final String TAG_HOLD = "lr_hold";
    /** 召唤箭：命中时召唤一只狼。 */
    private static final String TAG_SUMMON = "lr_summon";
    /** 召唤物标记（用来统计「我的」召唤物数量）。 */
    public static final String TAG_SUMMONED_PET = "lr_summoned_pet";

    /** 召唤箭冷却：存在玩家时间戳里的键（值是「可以再次召唤」的游戏刻）。 */
    private static final String STATE_SUMMON_READY = "lr_summon_ready";

    // ================================================================
    // 箭矢生成 —— 打标记
    // ================================================================

    /**
     * 箭矢刚进入世界时，按射手的饰品决定它这一路的特殊行为。
     *
     * <p>一次 {@link ArrowCharmType#activeSet} 拿全，避免为每枚饰品各扫一遍 Curios。
     */
    @SubscribeEvent
    public static void onArrowSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof AbstractArrow arrow)) {
            return;
        }
        if (!(arrow.getOwner() instanceof ServerPlayer shooter)) {
            return;
        }

        var active = ArrowCharmType.activeSet(shooter);
        if (active.isEmpty()) {
            return;
        }
        CompoundTag tag = arrow.getPersistentData();

        if (active.contains(ArrowCharmType.PIERCING)) {
            // 原版语义：pierceLevel = 还能再穿几个 → 2 表示总共可命中 3 个
            arrow.setPierceLevel((byte) MedalsConfig.ARROW_PIERCE_COUNT.get().intValue());
        }
        if (active.contains(ArrowCharmType.ANTIGRAVITY)) {
            arrow.setNoGravity(true);
            tag.putLong(TAG_FLY_START, shooter.level().getGameTime());
        }
        if (active.contains(ArrowCharmType.STAR)) {
            tag.putBoolean(TAG_STAR, true);
            // ★ 星辰箭必须无重力。
            //   否则原版箭的下坠会让它迅速落地 —— 破坏路径只剩脚下那一小截,
            //   命中生物后也立刻停下,「贯穿」二字名存实亡。
            arrow.setNoGravity(true);
            // 贯穿:命中多个目标后不消失(0 = 原版一碰即停)
            arrow.setPierceLevel((byte) MedalsConfig.ARROW_STAR_PIERCE.get().intValue());
            // 无重力的箭不会自己落地,必须记出生时刻,超时销毁
            tag.putLong(TAG_FLY_START, shooter.level().getGameTime());
        }
        if (active.contains(ArrowCharmType.EXPLOSIVE)) {
            // 与其它饰品一致：按「发射那一刻」的佩戴状态定死，飞行途中换装无效
            tag.putBoolean(TAG_EXPLOSIVE, true);
        }
        if (active.contains(ArrowCharmType.THUNDER)) {
            tag.putBoolean(TAG_THUNDER, true);
        }
        if (active.contains(ArrowCharmType.TWIN)) {
            tag.putBoolean(TAG_TWIN, true);
        }
        if (active.contains(ArrowCharmType.ARMOR_PIERCE)) {
            tag.putBoolean(TAG_ARMOR_PIERCE, true);
        }
        if (active.contains(ArrowCharmType.HOLD)) {
            tag.putBoolean(TAG_HOLD, true);
        }
        if (active.contains(ArrowCharmType.SUMMON)) {
            tag.putBoolean(TAG_SUMMON, true);
        }
    }

    // ================================================================
    // 拉弓 —— 星辰箭的满蓄力门槛
    // ================================================================

    /**
     * 未拉满就松手 → 取消发射。
     *
     * <p>{@code ArrowLooseEvent} 在原版计算蓄力、生成箭矢<b>之前</b>触发，
     * 取消它就等于「箭没射出去」—— 箭会留在弦上（消耗掉的箭矢也不会被扣）。
     *
     * <p>双端都判：客户端也会跑 {@code BowItem#releaseUsing}，只取消服务端的话
     * 玩家会听到放空弓的音效却没有箭，手感很怪。
     */
    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent event) {
        Player player = event.getEntity();
        if (player == null) {
            return;
        }

        // ---------- 星辰箭：未拉满就松手 → 整个发射取消 ----------
        if (ArrowCharmType.STAR.isActive(player)) {
            int required = Math.max(1, MedalsConfig.ARROW_STAR_CHARGE_SECONDS.get() * 20);
            if (event.getCharge() < required) {
                // 未拉满松手 → 这一发作废(箭留在弦上,不消耗)。
                // 刻意**不给提示**:星辰箭本来就要蓄满,玩家点一下就知道没射出去,
                // 弹 actionbar 只会每次都跳出来烦人。
                event.setCanceled(true);
                return;
            }
        }

        // ---------- 爆炸箭：记录「这一发是拉满射出的」 ----------
        // 蓄力满 = getPowerForTime >= 1.0（原版 20 tick 即为满蓄力，也就是原版暴击条件）。
        // 同样受「事件早于原版生成箭矢」的制约，必须延后到本 tick 的任务队列再打标记。
        if (player instanceof ServerPlayer server
                && !event.isCanceled()
                && ArrowCharmType.EXPLOSIVE.isActive(server)
                && BowItem.getPowerForTime(event.getCharge()) >= 1.0F) {
            server.serverLevel().getServer().execute(() -> markFullCharge(server));
        }
    }

    /**
     * 给「刚刚射出的那支原箭」打上满蓄力标记。
     *
     * <p>排除本模组补射的额外箭（三发 / 箭雨），只认原版生成的那一支。
     */
    private static void markFullCharge(ServerPlayer shooter) {
        AbstractArrow arrow = com.lost_realm.util.ArrowFinder.findFresh(shooter,
                a -> !a.getPersistentData().getBoolean(com.lost_realm.skill.archer.SkillArrowVolley.NBT_EXTRA));
        if (arrow != null) {
            arrow.getPersistentData().putBoolean(TAG_FULL_CHARGE, true);
        }
    }


    // ================================================================
    // 命中 —— 药水箭 / 爆炸箭 / 贯穿计数
    // ================================================================

    /**
     * 箭矢命中（实体或方块）时结算。
     *
     * <p>优先级 LOWEST：等其它模组先决定这支箭到底算不算命中，再决定要不要炸。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onProjectileHit(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }
        if (arrow.level().isClientSide()) {
            return;
        }
        if (!(arrow.getOwner() instanceof ServerPlayer shooter)) {
            return;
        }
        if (event.isCanceled()) {
            return;
        }

        // 贯穿计数：必须在伤害结算前累加，LivingHurtEvent 才知道这是第几个目标
        CompoundTag arrowTag = arrow.getPersistentData();
        arrowTag.putInt(TAG_PIERCE_HITS, arrowTag.getInt(TAG_PIERCE_HITS) + 1);

        var active = ArrowCharmType.activeSet(shooter);
        if (active.isEmpty()) {
            return;
        }

        if (active.contains(ArrowCharmType.EXPLOSIVE)
                && arrowTag.getBoolean(TAG_EXPLOSIVE)
                && arrowTag.getBoolean(TAG_FULL_CHARGE)) {
            Vec3 at = event.getRayTraceResult().getLocation();
            // ExplosionInteraction.NONE = 不破坏方块、不点燃
            arrow.level().explode(arrow, at.x, at.y, at.z,
                    MedalsConfig.ARROW_EXPLOSION_POWER.get().floatValue(),
                    Level.ExplosionInteraction.NONE);
        }

        if (active.contains(ArrowCharmType.POTION)
                && event.getRayTraceResult() instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity victim
                && victim != shooter) {
            applyPotionArrow(shooter, victim);
        }

        // ---------- 下面这几枚只认「命中活体」----------
        ServerLevel level = (ServerLevel) arrow.level();
        Vec3 at = event.getRayTraceResult().getLocation();
        LivingEntity struck = event.getRayTraceResult() instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof LivingEntity le && le != shooter ? le : null;

        if (arrowTag.getBoolean(TAG_THUNDER)) {
            thunderStrike(shooter, level, at);
        }
        // 双响:命中时按概率从射手的位置再补一发射击
        if (arrowTag.getBoolean(TAG_TWIN)
                && shooter.getRandom().nextDouble() < MedalsConfig.pct(MedalsConfig.ARROW_TWIN_CHANCE)) {
            twinShot(shooter, arrow, level);
        }
        if (struck != null) {
            if (arrowTag.getBoolean(TAG_HOLD)) {
                holdTarget(shooter, struck);
            }
            if (arrowTag.getBoolean(TAG_SUMMON)) {
                summonWolf(shooter, level, struck);
            }
        }
    }

    /** 药水箭：中毒 / 缓慢 / 凋零 三选一。 */
    private static void applyPotionArrow(ServerPlayer shooter, LivingEntity victim) {
        if (shooter.getRandom().nextDouble() >= MedalsConfig.pct(MedalsConfig.ARROW_POTION_CHANCE)) {
            return;
        }
        MobEffect[] pool = { MobEffects.POISON, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.WITHER };
        MobEffect chosen = pool[shooter.getRandom().nextInt(pool.length)];
        victim.addEffect(new MobEffectInstance(chosen,
                MedalsConfig.ARROW_POTION_SECONDS.get() * 20, 0), shooter);
    }

    // ================================================================
    // 雷鸣 / 束缚 / 定身 / 召唤
    // ================================================================

    /**
     * 雷鸣箭：在命中点劈一道雷，并对周围敌人结算一次范围雷伤 + 麻痹。
     *
     * <p>雷用 {@code setVisualOnly(true)}：原版真雷会点燃地面、把村民变女巫、
     * 还会自己打一次伤害 —— 那样数值就不可控了。这里只要它的视觉与音效，
     * 伤害完全由下面这圈自己算，配置里写多少就是多少。
     */
    private static void thunderStrike(ServerPlayer shooter, ServerLevel level, Vec3 at) {
        double radius = MedalsConfig.ARROW_THUNDER_RADIUS.get();
        float damage = MedalsConfig.ARROW_THUNDER_DAMAGE.get().floatValue();
        int paralyze = MedalsConfig.ARROW_THUNDER_PARALYZE_SECONDS.get() * 20;

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(at.x, at.y, at.z);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        level.playSound(null, BlockPos.containing(at), SoundEvents.LIGHTNING_BOLT_IMPACT,
                SoundSource.PLAYERS, 1.0F, 1.0F);

        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(at, at).inflate(radius),
                e -> e != shooter && e.isAlive())) {
            victim.hurt(level.damageSources().lightningBolt(), damage);
            if (paralyze > 0) {
                // 「麻痹」= 走不动 + 打不动 + 挖不动,三样一起才是麻的感觉
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, paralyze, 4), shooter);
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, paralyze, 1), shooter);
                victim.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, paralyze, 2), shooter);
            }
        }
    }

    /**
     * 双响之箭：命中时从射手的位置**再补一发**。
     *
     * <p>为什么是"从射手位置再来一发"，而不是"让这一发继续飞"：
     * 后者是穿透 —— 那是穿甲箭与星辰箭的活。双响要的是**两声**：
     * 你打出一发，紧接着同一个位置又来一发，听着像回声。
     *
     * <p>追加的那一发不带 {@link #TAG_TWIN}，所以它命中时不会再触发双响，
     * 不存在递归。
     */
    private static void twinShot(ServerPlayer shooter, AbstractArrow primary, ServerLevel level) {
        Vec3 motion = primary.getDeltaMovement();
        if (motion.lengthSqr() < 1.0E-6D) {
            motion = shooter.getLookAngle();
        }
        double speed = Math.max(1.5D, motion.length());
        Vec3 unit = motion.normalize();

        Arrow extra = new Arrow(level, shooter);
        // 与射手同高,但略微下压,免得和头部的碰撞箱打架
        extra.setPos(shooter.getX(), shooter.getEyeY() - 0.1D, shooter.getZ());
        extra.shoot(unit.x, unit.y, unit.z, (float) speed, 0.0F);
        extra.setBaseDamage(primary.getBaseDamage()
                * MedalsConfig.pct(MedalsConfig.ARROW_TWIN_DAMAGE));
        extra.setCritArrow(primary.isCritArrow());
        // 凭空补出来的一发,不给回收
        extra.pickup = AbstractArrow.Pickup.DISALLOWED;
        level.addFreshEntity(extra);

        level.playSound(null, shooter.blockPosition(), SoundEvents.ARROW_SHOOT,
                SoundSource.PLAYERS, 0.8F, 1.6F);
    }

    /**
     * 定身箭：按概率让目标「无法动弹」。
     *
     * <p>做法是极高等级的缓慢 + 极高等级的跳跃抑制：
     * 只给缓慢的话，生物仍然能一蹦一蹦地移动过去。
     */
    private static void holdTarget(ServerPlayer shooter, LivingEntity victim) {
        if (shooter.getRandom().nextDouble() >= MedalsConfig.pct(MedalsConfig.ARROW_HOLD_CHANCE)) {
            return;
        }
        int ticks = MedalsConfig.ARROW_HOLD_SECONDS.get() * 20;
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6, false, true), shooter);
        victim.addEffect(new MobEffectInstance(MobEffects.JUMP, ticks, 128, false, false), shooter);
    }

    /**
     * 召唤箭：在目标旁边召唤一只归射手所有的狼。
     *
     * <p>三条约束（上限 / 冷却 / 已死的不计数）都在这里判。
     * 冷却记在玩家数据的时间戳里 —— 记在箭上没用，换个角度连射就能绕过去。
     */
    private static void summonWolf(ServerPlayer shooter, ServerLevel level, LivingEntity victim) {
        PlayerMedals data = PlayerMedals.of(shooter);
        long now = level.getGameTime();
        if (now < data.getTimestamp(STATE_SUMMON_READY)) {
            return;
        }

        int max = MedalsConfig.ARROW_SUMMON_MAX.get();
        java.util.List<Wolf> mine = level.getEntitiesOfClass(Wolf.class,
                shooter.getBoundingBox().inflate(96.0D),
                w -> w.isAlive()
                        && w.getPersistentData().getBoolean(TAG_SUMMONED_PET)
                        && shooter.getUUID().equals(w.getOwnerUUID()));
        if (mine.size() >= max) {
            return;
        }

        Wolf wolf = EntityType.WOLF.create(level);
        if (wolf == null) {
            return;
        }
        BlockPos base = victim.blockPosition();
        double ox = (shooter.getRandom().nextDouble() - 0.5D) * 3.0D;
        double oz = (shooter.getRandom().nextDouble() - 0.5D) * 3.0D;
        wolf.moveTo(base.getX() + 0.5D + ox, base.getY(), base.getZ() + 0.5D + oz,
                shooter.getYRot(), 0.0F);
        wolf.setTame(true);
        wolf.setOwnerUUID(shooter.getUUID());
        // ★ 先改上限再回满血 —— 反过来的话 setHealth 会被旧上限夹住
        var health = wolf.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(MedalsConfig.ARROW_SUMMON_HEALTH.get());
        }
        wolf.setHealth(wolf.getMaxHealth());
        wolf.setPersistenceRequired();
        wolf.getPersistentData().putBoolean(TAG_SUMMONED_PET, true);
        level.addFreshEntity(wolf);

        int cooldown = MedalsConfig.ARROW_SUMMON_COOLDOWN_SECONDS.get();
        if (cooldown > 0) {
            data.setTimestamp(STATE_SUMMON_READY, now + cooldown * 20L);
        }
    }

    // ================================================================
    // 伤害结算 —— 贯穿衰减 / 星辰箭万点伤害
    // ================================================================

    /**
     * 命中伤害的修正。
     *
     * <p>用 {@link LivingHurtEvent}（护甲等减免<b>之前</b>）：贯穿衰减应当发生在
     * 减伤之前，否则「25% 伤害」会因为吃两次减伤而偏低。
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getSource().getDirectEntity() instanceof AbstractArrow arrow)) {
            return;
        }
        CompoundTag tag = arrow.getPersistentData();
        if (tag.getBoolean(TAG_STAR)) {
            return;   // 星辰箭走下面的万点结算，不参与贯穿衰减
        }
        // 第 2、3 个目标才衰减：命中计数在 ProjectileImpactEvent 里已经 +1
        if (tag.getInt(TAG_PIERCE_HITS) >= 2) {
            event.setAmount((float) (event.getAmount()
                    * MedalsConfig.pct(MedalsConfig.ARROW_PIERCE_FALLOFF)));
        }
        if (tag.getBoolean(TAG_ARMOR_PIERCE)) {
            applyArmorPierce(event);
        }
    }

    /**
     * 穿甲箭：无视目标一部分护甲。
     *
     * <p>这里的做法是<b>换算成等效伤害</b>，而不是真的去改目标的护甲属性 ——
     * 后者要加修饰符、还要记得在几秒后撤掉，任何异常路径（死亡 / 换维度 /
     * 客户端不同步）都会留下残留。换算则完全无状态：
     * 先用官方公式算出「按原护甲会吃多少」，再算「按打折护甲会吃多少」，
     * 两者之比就是本次伤害要放大的倍数；随后原版仍会按真实护甲结算一遍，
     * 于是最终结果正好等于「护甲少了 30%」。
     */
    private static void applyArmorPierce(LivingHurtEvent event) {
        double armor = event.getEntity().getArmorValue();
        if (armor <= 0.0D) {
            return;
        }
        var toughnessAttr = event.getEntity().getAttribute(Attributes.ARMOR_TOUGHNESS);
        float toughness = toughnessAttr == null ? 0.0F : (float) toughnessAttr.getValue();
        double pierce = MedalsConfig.pct(MedalsConfig.ARROW_ARMOR_PIERCE);
        if (pierce <= 0.0D) {
            return;
        }
        float amount = event.getAmount();
        float normal = CombatRules.getDamageAfterAbsorb(amount, (float) armor, toughness);
        float pierced = CombatRules.getDamageAfterAbsorb(
                amount, (float) (armor * (1.0D - pierce)), toughness);
        if (normal > 0.0F) {
            event.setAmount(amount * (pierced / normal));
        }
    }

    /**
     * 星辰箭的固定伤害。
     *
     * <p>用 {@link LivingDamageEvent}（护甲减免<b>之后</b>）：这是一把「毕业」武器，
     * 说 10000 就应该打出 10000，而不是被对方的护甲吃掉一大半。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getSource().getDirectEntity() instanceof AbstractArrow arrow)) {
            return;
        }
        if (!arrow.getPersistentData().getBoolean(TAG_STAR)) {
            return;
        }
        event.setAmount(MedalsConfig.ARROW_STAR_DAMAGE.get().floatValue());
    }

    // ================================================================
    // 逐 tick 驱动 —— 反重力箭超时 / 星辰箭破坏与拖尾
    // ================================================================

    /**
     * 服务端每 tick 扫一遍实体。
     *
     * <p>重活只在「箭身上带标记」时才做，普通箭在这个循环里只经历一次
     * {@code instanceof} 判断。星辰箭的破坏必须逐 tick 做：满蓄力的箭速接近
     * 3 格/tick，降到每 2 tick 就会从方块之间穿过去。
     */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide()) {
            return;
        }
        if (!(event.level instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();

        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof AbstractArrow arrow)) {
                continue;
            }
            CompoundTag tag = arrow.getPersistentData();
            if (tag.isEmpty()) {
                continue;
            }

            if (tag.contains(TAG_FLY_START)) {
                // 星辰箭飞得又远又快（30 秒能走 90 格），与反重力箭用各自的时长
                long seconds = tag.getBoolean(TAG_STAR)
                        ? MedalsConfig.ARROW_STAR_FLIGHT_SECONDS.get()
                        : MedalsConfig.ARROW_NO_GRAVITY_SECONDS.get();
                if (now - tag.getLong(TAG_FLY_START) > seconds * 20L) {
                    arrow.discard();
                    continue;
                }
            }

            // 「还在飞」用速度判断：插地/被墙挡住的箭速度归零，不必再挖它脚下的方块
            if (tag.getBoolean(TAG_STAR) && arrow.getDeltaMovement().lengthSqr() > 1.0E-6D) {
                starTick(level, arrow);
            }
        }
    }

    /**
     * 星辰箭在飞行中的表现：拖尾 + 犁开路径。
     *
     * <h2>两个曾经的坑</h2>
     * <ol>
     *   <li><b>只挖箭脚下，箭却撞在正前方</b> —— 原版箭速约 3 格/tick，
     *       而「挖掉当前位置的方块」永远慢一步：箭的碰撞箱先碰到前方那格，
     *       {@code onHitBlock} 就把速度改成回弹向量、把 {@code inGround} 置真。
     *       表现就是「穿几格就停下、然后贴着方块慢慢往前蹭」。
     *       修法：沿飞行方向<b>前瞻</b>若干个球，把前方的方块一起犁掉。</li>
     *   <li><b>被原版钉住后不会自愈</b> —— 一旦 {@code inGround} 变真，
     *       箭此后每 tick 都只在原地抖。修法：记住上一 tick 的速度，
     *       发现速度掉了就把 {@code inGround} 清掉、速度按原样塞回去。</li>
     * </ol>
     */
    private static void starTick(ServerLevel level, AbstractArrow arrow) {
        CompoundTag tag = arrow.getPersistentData();
        Vec3 pos = arrow.position();
        Vec3 vel = arrow.getDeltaMovement();

        // ---------- 速度维持 ----------
        if (vel.lengthSqr() > 1.0D) {
            // 正常飞行：记下这一 tick 的速度，撞停时照它恢复
            tag.putDouble(TAG_STAR_VX, vel.x);
            tag.putDouble(TAG_STAR_VY, vel.y);
            tag.putDouble(TAG_STAR_VZ, vel.z);
        } else if (tag.contains(TAG_STAR_VX)) {
            // 速度被什么东西清掉了 → 按上一 tick 的原速塞回去
            vel = new Vec3(tag.getDouble(TAG_STAR_VX),
                    tag.getDouble(TAG_STAR_VY), tag.getDouble(TAG_STAR_VZ));
            arrow.setDeltaMovement(vel);
        }

        // ---------- 拖尾：星尘主体 / 幽火余韵 / 电火花点缀 ----------
        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z,
                6, 0.12D, 0.12D, 0.12D, 0.01D);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z,
                4, 0.1D, 0.1D, 0.1D, 0.01D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z,
                3, 0.1D, 0.1D, 0.1D, 0.01D);

        if (!MedalsConfig.ARROW_STAR_BREAK_BLOCKS.get()) {
            return;
        }
        double radius = MedalsConfig.ARROW_STAR_BREAK_RADIUS.get();
        double sq = radius * radius;
        boolean drops = MedalsConfig.ARROW_STAR_BLOCK_DROPS.get();
        int r = (int) Math.ceil(radius);

        // ---------- 前瞻犁地 ----------
        destroySphere(level, pos, r, sq, drops);
        if (vel.lengthSqr() > 1.0E-6D) {
            Vec3 dir = vel.normalize();
            double lookahead = Math.min(3.0D, vel.length() * 2.0D + 1.0D);
            int steps = (int) Math.ceil(lookahead / 0.75D);
            for (int i = 1; i <= steps; i++) {
                destroySphere(level, pos.add(dir.scale(lookahead * i / steps)), r, sq, drops);
            }
        }
    }

    /** 摧毁以 {@code center} 为心、半径 {@code radius} 内「铁镐及以下能挖」的方块。 */
    private static void destroySphere(ServerLevel level, Vec3 center, int r, double sq, boolean drops) {
        BlockPos base = BlockPos.containing(center);
        for (BlockPos cursor : BlockPos.betweenClosed(
                base.offset(-r, -r, -r), base.offset(r, r, r))) {
            if (cursor.distToCenterSqr(center.x, center.y, center.z) > sq) {
                continue;
            }
            BlockState state = level.getBlockState(cursor);
            if (state.isAir()) {
                continue;
            }
            // 硬度为负 = 不可破坏（基岩、传送门框架、屏障…）
            if (state.getDestroySpeed(level, cursor) < 0.0F) {
                continue;
            }
            // 「需要钻石工具」= 铁镐及以下挖不动 → 按需求保留
            if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) {
                continue;
            }
            level.destroyBlock(cursor, drops);
        }
    }

    // ================================================================
    // 星辰箭 —— 拉弓蓄力的粒子演出
    // ================================================================

    /**
     * 拉弓时「能量从四面八方汇入箭头」的粒子，以及<b>拉满自动放出</b>。
     *
     * <h2>为什么必须由服务端主动释放</h2>
     * 原版弓只在玩家<b>松手</b>的那一刻才走 {@code releaseUsing} → 生成箭矢。
     * 而星辰箭要求蓄力 10 秒，于是：
     * <ul>
     *   <li>玩家一直按住右键不放 → 永远等不到松手 → 永远不射；</li>
     *   <li>「自动射击」QoL 哪怕开着，若按原版 1 秒的门槛松手，又会被服务端的
     *       {@code ArrowLooseEvent} 判定「蓄力不足」而取消 → 反复拉弓却射不出去。</li>
     * </ul>
     * 因此这里在蓄力达标的那一 tick 直接 {@link ServerPlayer#releaseUsingItem()}，
     * 走的是完全原版的释放流程，箭照常由原版创建、本模组的标记照常打上。
     *
     * <p>三层廉价的提前退出（是否在使用物品 → 主手是不是弓 → 有没有戴星辰箭）
     * 把 Curios 查询挡在最后：绝大多数玩家绝大多数时刻都在第一层就返回了。
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (!player.isUsingItem() || !(player.getUseItem().getItem() instanceof BowItem)) {
            return;
        }
        if (!ArrowCharmType.STAR.isActive(player)) {
            return;
        }

        int charge = player.getTicksUsingItem();
        int required = Math.max(1, MedalsConfig.ARROW_STAR_CHARGE_SECONDS.get() * 20);

        ServerLevel level = player.serverLevel();
        Vec3 focus = player.getEyePosition().add(player.getLookAngle().scale(1.6D));
        var random = player.getRandom();

        if (charge >= required) {
            // 拉满 → 先放演出，再主动释放（不必等玩家松手）
            emitStarBlast(level, player, focus);
            player.releaseUsingItem();
            return;
        }

        // ---------- 蓄力中：粒子本身充当进度条(越接近拉满越密、半径越收) ----------
        double progress = Math.min(1.0D, (double) charge / required);
        int density = 4 + (int) (progress * 14.0D);
        double radius = 3.8D - 2.2D * progress;

        for (int i = 0; i < density; i++) {
            double theta = random.nextDouble() * Math.PI * 2.0D;
            double phi = Math.acos(2.0D * random.nextDouble() - 1.0D);
            double r = radius * (0.75D + random.nextDouble() * 0.35D);
            double px = player.getX() + r * Math.sin(phi) * Math.cos(theta);
            double py = player.getY() + 1.0D + r * Math.cos(phi);
            double pz = player.getZ() + r * Math.sin(phi) * Math.sin(theta);
            Vec3 velocity = focus.subtract(px, py, pz).normalize()
                    .scale(0.45D + progress * 0.35D);

            // 三种粒子混着来:主轨迹(星尘) / 幽火 / 电火花
            double roll = random.nextDouble();
            if (roll < 0.62D) {
                level.sendParticles(ParticleTypes.END_ROD, px, py, pz, 1,
                        velocity.x, velocity.y, velocity.z, 0.9D);
            } else if (roll < 0.87D) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, 1,
                        velocity.x, velocity.y, velocity.z, 0.05D);
            } else {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, px, py, pz, 1,
                        velocity.x, velocity.y, velocity.z, 0.05D);
            }
        }

        // 箭头焦点处持续汇聚 + 每半秒一圈附魔微光
        level.sendParticles(ParticleTypes.END_ROD, focus.x, focus.y, focus.z, 3,
                0.06D, 0.06D, 0.06D, 0.0D);
        if (charge % 10 == 0) {
            level.sendParticles(ParticleTypes.ENCHANT, focus.x, focus.y, focus.z, 8,
                    0.4D, 0.4D, 0.4D, 0.45D);
        }
    }

    /** 拉满那一刻的爆发演出（紧接着就会自动放出）。 */
    private static void emitStarBlast(ServerLevel level, ServerPlayer player, Vec3 focus) {
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 1.2F, 1.5F);
        level.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN,
                SoundSource.PLAYERS, 0.7F, 1.9F);

        level.sendParticles(ParticleTypes.FLASH, focus.x, focus.y, focus.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        // 向外炸开的环形冲击
        level.sendParticles(ParticleTypes.END_ROD, focus.x, focus.y, focus.z,
                70, 0.55D, 0.55D, 0.55D, 0.42D);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, focus.x, focus.y, focus.z,
                45, 0.45D, 0.45D, 0.45D, 0.30D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, focus.x, focus.y, focus.z,
                35, 0.5D, 0.5D, 0.5D, 0.35D);
        // 沿视线方向拉出一道高速星尘
        level.sendParticles(ParticleTypes.END_ROD, focus.x, focus.y, focus.z,
                40, 0.15D, 0.15D, 0.15D, 0.9D);
    }

    private ArrowCharmHandler() {}
}
