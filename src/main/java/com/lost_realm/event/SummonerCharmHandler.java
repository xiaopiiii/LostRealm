package com.lost_realm.event;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.lost_realm.Medals;
import com.lost_realm.compat.goety.GoetyBridge;
import com.lost_realm.config.MedalsConfig;
import com.lost_realm.data.MedalSource;
import com.lost_realm.effect.ModEffects;
import com.lost_realm.item.ContractCharmType;
import com.lost_realm.item.ServantAttributes;
import com.lost_realm.item.TankAttributes;
import com.lost_realm.skill.MedalType;
import com.lost_realm.util.AttributeGuard;
import com.lost_realm.util.ModifierIds;

/**
 * 召唤师的全部仆从侧效果（勋章基础成长 + 六个节点技能 + 九枚契约 + 王者之主）。
 *
 * <h2>这一层在做什么</h2>
 * 本模组把「我想让仆从变强多少」抽象成四条挂在<b>主人</b>身上的属性
 * （见 {@code ServantAttributes}）。但 Goety 只认仆从自己身上的原版属性，
 * 所以需要一个翻译层：本类每个刷新周期读一遍主人那四条数值，
 * 再写到<b>每个仆从</b>的 {@code MAX_HEALTH / ATTACK_DAMAGE / MOVEMENT_SPEED}
 * 上（外加寿命）。这也是为什么这里有个「周期」而不是纯事件驱动 ——
 * 属性是持续状态，不是一次性触发。
 *
 * <h2>与法师的分工</h2>
 * 法师改的是<b>法术</b>（强度 / 冷却 / 半径），入口在
 * {@code CombatHandler#handleMageOnDamage}；召唤师改的是<b>仆从</b>，
 * 入口是「仆从造成的伤害」这一族事件。两枚勋章因此可以同时佩戴而不打架。
 *
 * <h2>软依赖</h2>
 * 本类全程通过 {@link GoetyBridge} 访问 Goety，桥接拿不到就整体静默禁用，
 * 未安装 Goety 的整合包里这些事件只是空跑。
 */
@Mod.EventBusSubscriber(modid = Medals.MODID)
public final class SummonerCharmHandler {

    // ================================================================
    // 运行时状态
    // ================================================================

    /** 仆从的<b>原始</b>寿命（第一次见到它时记下），用于按倍率重算延长量。 */
    private static final Map<UUID, Integer> BASE_LIFESPAN = new HashMap<>();

    /** 已经补给该仆从的寿命总量，用于只补差额、避免每轮无限续命。 */
    private static final Map<UUID, Integer> GRANTED_LIFESPAN = new HashMap<>();

    /** 已经施加过本模组加成的仆从（用于第一次施加后把血补满）。 */
    private static final Set<UUID> PATCHED = ConcurrentHashMap.newKeySet();

    /** 上一轮仍在生效的主人（用于「摘下勋章」时做一次归零）。 */
    private static final Set<UUID> ACTIVE_OWNERS = ConcurrentHashMap.newKeySet();

    /** 每个主人的上次刷新刻。 */
    private static final Map<UUID, Long> LAST_REFRESH = new HashMap<>();

    /** 每个仆从上次通过「灵魂纽带」回魂的刻。 */
    private static final Map<UUID, Long> LAST_SOUL_HIT = new HashMap<>();

    /** 敕令之契：玩家最近打过的目标。 */
    private static final Map<UUID, TargetMark> RECENT_TARGET = new HashMap<>();

    /** 每个仆从上次施加的四条系数 —— 数值没变就不必再动属性修饰符。 */
    private static final Map<UUID, double[]> LAST_APPLIED = new HashMap<>();

    /** 裂变之契复制出来的仆从的标记 —— 它们不再触发裂变，否则会指数繁殖。 */
    private static final String TAG_FISSION_CHILD = "lr_fission_child";

    /** 最近目标的有效期（刻）。 */
    private static final long TARGET_MEMORY_TICKS = 200L;

    private record TargetMark(UUID target, long tick) {}

    // ================================================================
    // 一、周期刷新 —— 把主人身上的四条仆从属性翻译到仆从身上
    // ================================================================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().isClientSide || !GoetyBridge.available()) {
            return;
        }
        long now = player.level().getGameTime();
        long interval = Math.max(1, MedalsConfig.CONTRACT_REFRESH_INTERVAL.get());
        Long last = LAST_REFRESH.get(player.getUUID());
        if (last != null && now - last < interval) {
            return;
        }
        LAST_REFRESH.put(player.getUUID(), now);

        Set<ContractCharmType> charms = ContractCharmType.activeSet(player);
        boolean wearingMedal = MedalSource.isWearing(player, MedalType.SUMMONER);
        boolean active = wearingMedal || !charms.isEmpty();

        // 不满足条件且以前生效过 → 做一次彻底归零，然后不再管他
        if (!active) {
            if (ACTIVE_OWNERS.remove(player.getUUID())) {
                resetOwner(player);
            }
            return;
        }
        ACTIVE_OWNERS.add(player.getUUID());

        refreshServants(player, charms, wearingMedal);
        refreshOwnerBonuses(player, charms);
    }

    /** 对一个主人的全部仆从重写属性与寿命。 */
    private static void refreshServants(ServerPlayer player, Set<ContractCharmType> charms,
                                        boolean wearingMedal) {
        GoetyBridge goety = GoetyBridge.get();
        if (goety == null) {
            return;
        }
        List<Entity> servants = goety.servantsOf(player);
        if (servants.isEmpty()) {
            return;
        }

        boolean overlord = charms.contains(ContractCharmType.OVERLORD);

        // ---- 四条基础数值（勋章等级已由 MedalRuntime 折算进属性）----
        double health = wearingMedal ? attr(player, ServantAttributes.SERVANT_HEALTH.get()) : 0.0D;
        double damage = wearingMedal ? attr(player, ServantAttributes.SERVANT_DAMAGE.get()) : 0.0D;
        double speed = wearingMedal ? attr(player, ServantAttributes.SERVANT_SPEED.get()) : 0.0D;
        double lifespan = wearingMedal ? attr(player, ServantAttributes.SERVANT_LIFESPAN.get()) : 0.0D;

        // ---- 契约饰品 ----
        if (charms.contains(ContractCharmType.DOMINION)) {
            health += MedalsConfig.pct(MedalsConfig.CONTRACT_DOMINION_HEALTH);
            damage += MedalsConfig.pct(MedalsConfig.CONTRACT_DOMINION_DAMAGE);
        }
        if (charms.contains(ContractCharmType.LEGION)) {
            lifespan += MedalsConfig.pct(MedalsConfig.CONTRACT_LEGION_LIFE);
        }
        if (charms.contains(ContractCharmType.OVERLORD)) {
            health += MedalsConfig.pct(MedalsConfig.CONTRACT_OVERLORD_HEALTH);
            damage += MedalsConfig.pct(MedalsConfig.CONTRACT_OVERLORD_DAMAGE);
            speed += MedalsConfig.pct(MedalsConfig.CONTRACT_OVERLORD_SPEED);
        }

        // ---- 节点技能 ----
        int level = MedalSource.level(player, MedalType.SUMMONER);
        if (level >= 60 && wearingMedal) {
            health += MedalsConfig.pct(MedalsConfig.S30_SERVANT_HEALTH);
        }
        boolean holdingLife = level >= 40 && SummonerState.commandActive(player);
        if (holdingLife) {
            damage += MedalsConfig.pct(MedalsConfig.S20_SERVANT_DAMAGE);
        }
        int interval = Math.max(1, MedalsConfig.CONTRACT_REFRESH_INTERVAL.get());

        for (Entity servant : servants) {
            if (servant instanceof LivingEntity living) {
                applyToServant(living, health, damage, speed, lifespan, overlord, holdingLife,
                        interval, goety);
            }
        }
    }

    /** 把一组系数写到单个仆从身上。 */
    private static void applyToServant(LivingEntity servant, double health, double damage,
                                       double speed, double lifespan, boolean overlord,
                                       boolean holdingLife, int interval, GoetyBridge goety) {
        UUID id = servant.getUUID();
        boolean first = PATCHED.add(id);

        // 数值没变就整个跳过 —— 王者之主允许上百个仆从,每秒几百次
        // 「删一条修饰符再加一条」是没必要的开销。寿命那部分在下面单独处理。
        double[] want = {health, damage, speed, lifespan};
        double[] last = LAST_APPLIED.get(id);
        boolean changed = last == null
                || last[0] != want[0] || last[1] != want[1]
                || last[2] != want[2] || last[3] != want[3];
        if (changed) {
            // 三条原版属性:全部用 MULTIPLY_BASE,即「相对该仆从自身基础值的百分比」
            AttributeGuard.apply(servant, Attributes.MAX_HEALTH, ModifierIds.SUMMONER_SERVANT_HEALTH,
                    "summoner_servant_health", health, AttributeModifier.Operation.MULTIPLY_BASE);
            AttributeGuard.apply(servant, Attributes.ATTACK_DAMAGE, ModifierIds.SUMMONER_SERVANT_DAMAGE,
                    "summoner_servant_damage", damage, AttributeModifier.Operation.MULTIPLY_BASE);
            AttributeGuard.apply(servant, Attributes.MOVEMENT_SPEED, ModifierIds.SUMMONER_SERVANT_SPEED,
                    "summoner_servant_speed", speed, AttributeModifier.Operation.MULTIPLY_BASE);

            // 生命上限刚提上去时把血补满 —— 否则新召唤出来的仆从会"一出生就残血"
            if ((first || last == null) && health > 0.0D) {
                servant.setHealth(servant.getMaxHealth());
            }
            LAST_APPLIED.put(id, want);
        }

        // ---- 寿命 ----
        if (overlord) {
            // 王者之主:不再有寿命限制
            goety.makeServantPermanent(servant);
            BASE_LIFESPAN.remove(id);
            GRANTED_LIFESPAN.remove(id);
            return;
        }
        // 军团号令生效期间:补上这一段流逝掉的寿命,等价于「寿命暂停」
        if (holdingLife && goety.hasLifespan(servant)) {
            goety.extendServantLife(servant, interval);
        }
        if (lifespan <= 0.0D || !goety.hasLifespan(servant)) {
            return;
        }
        Integer base = BASE_LIFESPAN.get(id);
        if (base == null) {
            base = goety.servantLifespan(servant);
            if (base <= 0) {
                return;
            }
            BASE_LIFESPAN.put(id, base);
        }
        // 目标延长量 = 原始寿命 × 倍率;只补差额,所以等级/饰品变化时也能自动收敛
        int target = (int) Math.round(base * lifespan);
        int granted = GRANTED_LIFESPAN.getOrDefault(id, 0);
        if (target != granted) {
            goety.extendServantLife(servant, target - granted);
            GRANTED_LIFESPAN.put(id, target);
        }
    }

    /** 魂链之契 / 骨甲之契:按存活仆从数量给主人加属性。 */
    private static void refreshOwnerBonuses(ServerPlayer player, Set<ContractCharmType> charms) {
        GoetyBridge goety = GoetyBridge.get();
        if (goety == null) {
            return;
        }
        int count = goety.servantsOf(player).size();

        // 魂链之契:每个仆从折算 %法术强度
        if (charms.contains(ContractCharmType.SOULCHAIN)) {
            int n = Math.min(count, MedalsConfig.CONTRACT_SOULCHAIN_MAX.get());
            goety.applySpellPotency(player, ModifierIds.SUMMONER_C_SOULCHAIN,
                    "summoner_c_soulchain",
                    MedalsConfig.pct(MedalsConfig.CONTRACT_SOULCHAIN_POTENCY) * n);
        } else {
            goety.applySpellPotency(player, ModifierIds.SUMMONER_C_SOULCHAIN,
                    "summoner_c_soulchain", 0.0D);
        }

        // 骨甲之契:每个仆从折算 %伤害减免
        if (charms.contains(ContractCharmType.BONE)) {
            int n = Math.min(count, MedalsConfig.CONTRACT_BONE_MAX.get());
            AttributeGuard.apply(player, TankAttributes.DAMAGE_REDUCTION.get(), ModifierIds.SUMMONER_C_BONE,
                    "summoner_c_bone",
                    MedalsConfig.pct(MedalsConfig.CONTRACT_BONE_REDUCTION) * n,
                    AttributeModifier.Operation.ADDITION);
        } else {
            AttributeGuard.remove(player, TankAttributes.DAMAGE_REDUCTION.get(), ModifierIds.SUMMONER_C_BONE);
        }
    }

    /** 玩家不再满足条件时，把仆从与主人身上的本模组加成全部收回。 */
    private static void resetOwner(ServerPlayer player) {
        GoetyBridge goety = GoetyBridge.get();
        if (goety != null) {
            List<Entity> servants = goety.servantsOf(player);
            for (Entity servant : servants) {
                if (servant instanceof LivingEntity living) {
                    clearServant(living);
                }
            }
            goety.applySpellPotency(player, ModifierIds.SUMMONER_C_SOULCHAIN,
                    "summoner_c_soulchain", 0.0D);
        }
        AttributeGuard.remove(player, TankAttributes.DAMAGE_REDUCTION.get(), ModifierIds.SUMMONER_C_BONE);
    }

    /** 清掉一个仆从身上全部本模组加成。 */
    private static void clearServant(LivingEntity servant) {
        AttributeGuard.clearByPrefix(servant, Attributes.MAX_HEALTH, "summoner_");
        AttributeGuard.clearByPrefix(servant, Attributes.ATTACK_DAMAGE, "summoner_");
        AttributeGuard.clearByPrefix(servant, Attributes.MOVEMENT_SPEED, "summoner_");
        AttributeGuard.clearByPrefix(servant, Attributes.ARMOR, "summoner_");
        AttributeGuard.clearByPrefix(servant, TankAttributes.DAMAGE_REDUCTION.get(), "summoner_");
        UUID id = servant.getUUID();
        PATCHED.remove(id);
        BASE_LIFESPAN.remove(id);
        GRANTED_LIFESPAN.remove(id);
        LAST_APPLIED.remove(id);
    }

    /**
     * 供 {@code MedalRuntime} 在卸下勋章时调用 —— 让下一轮刷新立刻发生。
     *
     * <p><b>刻意不动 {@link #ACTIVE_OWNERS}</b>：那张表记录的是「上一轮这个人的
     * 加成还在生效中」，归零正是靠它来判断「这次要执行一次回收」。
     * 顺手把它清掉的话，下一轮 {@code onPlayerTick} 会认为「本来就没生效过」，
     * 于是仆从身上的加成永远收不回来。
     */
    public static void forgetPlayer(UUID playerId) {
        LAST_REFRESH.remove(playerId);
        RECENT_TARGET.remove(playerId);
    }

    /**
     * 玩家下线时把与他相关的运行时记录一并清掉。
     *
     * <p>不清的话这几张表会随着「开服 - 关服」反复累积：玩家的 UUID 是永久的，
     * 下次上线会直接命中旧的那一条（{@code LAST_REFRESH} 尤其明显 ——
     * 留着旧刻会让刚上线的玩家第一轮刷新被节流掉）。
     */
    @SubscribeEvent
    public static void onPlayerLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        forgetPlayer(id);
        ACTIVE_OWNERS.remove(id);
    }

    /** 读一条仆从属性的当前值（不存在返回 0）。 */
    private static double attr(LivingEntity entity, net.minecraft.world.entity.ai.attributes.Attribute attribute) {
        var instance = entity.getAttribute(attribute);
        return instance == null ? 0.0D : instance.getValue();
    }

    // ================================================================
    // 二、仆从造成伤害 —— 熟练度 / 灵魂纽带 / 魂火 / 敕令
    // ================================================================

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServantDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        GoetyBridge goety = GoetyBridge.get();
        if (goety == null) {
            return;
        }
        Entity source = event.getSource().getEntity();
        if (source == null || source instanceof ServerPlayer) {
            return;
        }
        LivingEntity master = goety.masterOf(source);
        if (!(master instanceof ServerPlayer owner)) {
            return;
        }
        LivingEntity target = event.getEntity();
        int level = MedalSource.level(owner, MedalType.SUMMONER);

        // ---------- 【魂火】攻击附带额外伤害 ----------
        if (level >= 50 && MedalSource.isWearing(owner, MedalType.SUMMONER)) {
            double extra = MedalsConfig.S25_EXTRA_DAMAGE.get();
            if (extra > 0.0D) {
                event.setAmount((float) (event.getAmount() + extra));
                if (target.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                            6, 0.25D, 0.25D, 0.25D, 0.01D);
                }
            }
        }

        // ---------- 敕令之契:附上「脆弱」 ----------
        Set<ContractCharmType> charms = ContractCharmType.activeSet(owner);
        if (charms.contains(ContractCharmType.DECREE)) {
            target.addEffect(new MobEffectInstance(ModEffects.VULNERABLE.get(),
                    MedalsConfig.CONTRACT_DECREE_SECONDS.get() * 20, 0, false, true));
        }

        // ---------- 熟练度:仆从的有效伤害 ----------
        double effective = CombatHandler.effectiveDamage(event.getAmount(), target);
        double gain = effective * MedalsConfig.pct(MedalsConfig.SUMMONER_EXP_SERVANT_DAMAGE);
        CombatHandler.applyNeutralPenalty(target, gain, owner, MedalType.SUMMONER);

        // ---------- 【灵魂纽带】仆从造成伤害时回魂 ----------
        if (level >= 10 && MedalSource.isWearing(owner, MedalType.SUMMONER)) {
            long now = owner.level().getGameTime();
            long cooldown = MedalsConfig.S5_SOUL_COOLDOWN.get();
            Long last = LAST_SOUL_HIT.get(source.getUUID());
            if (last == null || now - last >= cooldown) {
                LAST_SOUL_HIT.put(source.getUUID(), now);
                goety.increaseSouls(owner, MedalsConfig.S5_SOUL_PER_HIT.get());
            }
        }
    }

    // ================================================================
    // 三、仆从受伤 —— 骸骨硬化
    // ================================================================

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onServantHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        GoetyBridge goety = GoetyBridge.get();
        if (goety == null) {
            return;
        }
        LivingEntity victim = event.getEntity();
        LivingEntity master = goety.masterOf(victim);
        if (!(master instanceof ServerPlayer owner)) {
            return;
        }
        // ---------- 【骸骨硬化】仆从获得额外减伤 ----------
        if (MedalSource.level(owner, MedalType.SUMMONER) >= 10) {
            double reduction = Math.min(0.9D, MedalsConfig.pct(MedalsConfig.S10_SERVANT_REDUCTION));
            if (reduction > 0.0D) {
                event.setAmount((float) (event.getAmount() * (1.0D - reduction)));
            }
        }
    }

    // ================================================================
    // 四、主人受伤 —— 共生之契
    // ================================================================

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onOwnerHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!ContractCharmType.SYMBIOSIS.isActive(player)) {
            return;
        }
        GoetyBridge goety = GoetyBridge.get();
        if (goety == null) {
            return;
        }
        LivingEntity decoy = nearestServant(player, goety);
        if (decoy == null) {
            return;
        }
        double ratio = Math.min(0.9D, MedalsConfig.pct(MedalsConfig.CONTRACT_SYMBIOSIS_RATIO));
        float moved = (float) (event.getAmount() * ratio);
        if (moved <= 0.0F) {
            return;
        }
        event.setAmount(event.getAmount() - moved);
        // 由仆从代扛:走 magic 伤害源,绕过护甲只吃抗性
        decoy.hurt(player.damageSources().magic(), moved);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SCULK_SOUL,
                    decoy.getX(), decoy.getY() + decoy.getBbHeight() * 0.6D, decoy.getZ(),
                    8, 0.3D, 0.3D, 0.3D, 0.01D);
        }
    }

    /** 主人附近最近的仆从。 */
    private static LivingEntity nearestServant(ServerPlayer owner, GoetyBridge goety) {
        double radius = MedalsConfig.CONTRACT_SYMBIOSIS_RADIUS.get();
        AABB box = owner.getBoundingBox().inflate(radius);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : owner.level().getEntities(owner, box)) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (!goety.isOwnedBy(living, owner)) {
                continue;
            }
            double distance = living.distanceToSqr(owner);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = living;
            }
        }
        return best;
    }

    // ================================================================
    // 五、仆从死亡 —— 亡者复苏 / 殉爆 / 血肉 / 统御
    // ================================================================

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onServantDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        GoetyBridge goety = GoetyBridge.get();
        if (goety == null) {
            return;
        }
        LivingEntity victim = event.getEntity();
        LivingEntity master = goety.masterOf(victim);
        if (!(master instanceof ServerPlayer owner)) {
            // 不是仆从,但可能「仆从击杀了一个生物」—— 那条线在下面处理
            handleServantKill(event, goety);
            return;
        }
        Set<ContractCharmType> charms = ContractCharmType.activeSet(owner);
        int level = MedalSource.level(owner, MedalType.SUMMONER);

        // ---------- 【亡者复苏】按概率原地复活 ----------
        if (level >= 30 && MedalSource.isWearing(owner, MedalType.SUMMONER)
                && owner.getRandom().nextDouble() < MedalsConfig.pct(MedalsConfig.S15_REVIVE_CHANCE)) {
            event.setCanceled(true);
            victim.setHealth(Math.max(1.0F,
                    (float) (victim.getMaxHealth() * MedalsConfig.pct(MedalsConfig.S15_REVIVE_HEALTH))));
            if (victim.level() instanceof ServerLevel level0) {
                level0.sendParticles(ParticleTypes.SOUL,
                        victim.getX(), victim.getY() + victim.getBbHeight() * 0.5D, victim.getZ(),
                        24, 0.4D, 0.6D, 0.4D, 0.02D);
                level0.playSound(null, victim.blockPosition(), SoundEvents.SOUL_ESCAPE,
                        SoundSource.NEUTRAL, 0.8F, 1.4F);
            }
            return;
        }

        // ---------- 【殉爆之契】仆从死亡时爆炸 ----------
        if (charms.contains(ContractCharmType.MARTYR) && victim.level() instanceof ServerLevel level1) {
            martyrExplode(owner, victim, level1, goety);
        }

        // ---------- 【血肉之契】仆从死亡时治疗主人 ----------
        if (charms.contains(ContractCharmType.FLESH)) {
            float heal = (float) (victim.getMaxHealth() * MedalsConfig.pct(MedalsConfig.CONTRACT_FLESH_RATIO));
            if (heal > 0.0F) {
                owner.heal(heal);
                if (owner.level() instanceof ServerLevel level2) {
                    level2.sendParticles(ParticleTypes.HEART,
                            owner.getX(), owner.getY() + 2.0D, owner.getZ(),
                            6, 0.4D, 0.3D, 0.4D, 0.01D);
                }
            }
        }

        // 清掉运行时记录
        UUID id = victim.getUUID();
        PATCHED.remove(id);
        BASE_LIFESPAN.remove(id);
        GRANTED_LIFESPAN.remove(id);
        LAST_APPLIED.remove(id);
        LAST_SOUL_HIT.remove(id);
    }

    /** 仆从击杀目标时的奖励：熟练度 + 统御之契的灵魂。 */
    private static void handleServantKill(LivingDeathEvent event, GoetyBridge goety) {
        Entity killer = event.getSource().getEntity();
        if (killer == null) {
            return;
        }
        LivingEntity master = goety.masterOf(killer);
        if (!(master instanceof ServerPlayer owner)) {
            return;
        }
        // 熟练度:按目标最大生命值折算
        double gain = CombatHandler.effectiveDamage(event.getEntity().getMaxHealth(), event.getEntity())
                * MedalsConfig.pct(MedalsConfig.SUMMONER_EXP_KILL_HP);
        CombatHandler.applyNeutralPenalty(event.getEntity(), gain, owner, MedalType.SUMMONER);

        // 统御之契:仆从击杀也返还灵魂
        if (ContractCharmType.DOMINION.isActive(owner)) {
            goety.increaseSouls(owner, MedalsConfig.CONTRACT_DOMINION_SOUL.get());
        }
    }

    /** 殉爆之契的爆炸（不破坏方块、不伤及主人与友军）。 */
    private static void martyrExplode(ServerPlayer owner, LivingEntity victim,
                                      ServerLevel level, GoetyBridge goety) {
        double radius = MedalsConfig.CONTRACT_MARTYR_RADIUS.get();
        float damage = (float) (victim.getMaxHealth() * MedalsConfig.pct(MedalsConfig.CONTRACT_MARTYR_RATIO));
        AABB box = victim.getBoundingBox().inflate(radius);
        for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (hit == owner || goety.isOwnedBy(hit, owner) || hit == victim) {
                continue;
            }
            hit.hurt(owner.damageSources().magic(), damage);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                victim.getX(), victim.getY() + victim.getBbHeight() * 0.5D, victim.getZ(),
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                victim.getX(), victim.getY() + victim.getBbHeight() * 0.5D, victim.getZ(),
                30, radius * 0.4D, radius * 0.3D, radius * 0.4D, 0.02D);
        level.playSound(null, victim.blockPosition(), SoundEvents.GENERIC_EXPLODE,
                SoundSource.NEUTRAL, 1.0F, 0.8F);
    }

    // ================================================================
    // 六、新仆从加入 —— 裂变之契
    // ================================================================

    @SubscribeEvent
    public static void onServantJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof Mob servant)) {
            return;
        }
        if (servant.getTags().contains(TAG_FISSION_CHILD)) {
            return;
        }
        GoetyBridge goety = GoetyBridge.get();
        if (goety == null || !goety.isServant(servant)) {
            return;
        }
        LivingEntity master = goety.masterOf(servant);
        if (!(master instanceof ServerPlayer owner)) {
            return;
        }
        if (!ContractCharmType.FISSION.isActive(owner)) {
            return;
        }
        if (owner.getRandom().nextDouble() >= MedalsConfig.pct(MedalsConfig.CONTRACT_FISSION_CHANCE)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        Entity spawned = servant.getType().create(level);
        if (!(spawned instanceof Mob copy)) {
            return;
        }
        copy.moveTo(servant.getX(), servant.getY(), servant.getZ(), servant.getYRot(), servant.getXRot());
        copy.addTag(TAG_FISSION_CHILD);
        if (!goety.adoptServant(copy, owner)) {
            return;
        }

        double ratio = Math.min(1.0D, MedalsConfig.pct(MedalsConfig.CONTRACT_FISSION_RATIO));
        // 血量只有一半:挂一条负向修饰符,而不是简单地扣当前血量
        AttributeGuard.apply(copy, Attributes.MAX_HEALTH, ModifierIds.SUMMONER_C_FISSION_HP,
                "summoner_c_fission_hp", ratio - 1.0D, AttributeModifier.Operation.MULTIPLY_BASE);
        // 寿命同样只有一半
        int base = goety.servantLifespan(servant);
        if (base > 0) {
            goety.setServantLifespan(copy, (int) Math.round(base * ratio));
        }
        level.addFreshEntity(copy);
        copy.setHealth(copy.getMaxHealth());

        level.sendParticles(ParticleTypes.SOUL,
                copy.getX(), copy.getY() + copy.getBbHeight() * 0.5D, copy.getZ(),
                16, 0.3D, 0.4D, 0.3D, 0.02D);
        level.playSound(null, copy.blockPosition(), SoundEvents.EVOKER_CAST_SPELL,
                SoundSource.NEUTRAL, 0.7F, 1.3F);
    }

    // ================================================================
    // 七、脆弱 —— 受伤加成
    // ================================================================

    /**
     * 「脆弱」的受伤增幅。
     *
     * <p>做成全局的（而不是只对仆从的攻击生效）：效果挂在目标身上，
     * 那它就是「这个人现在更脆」——队友打、你自己打，都该更疼。
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onVulnerableHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) {
            return;
        }
        MobEffectInstance instance = target.getEffect(ModEffects.VULNERABLE.get());
        if (instance == null) {
            return;
        }
        double bonus = MedalsConfig.pct(MedalsConfig.CONTRACT_DECREE_VULNERABLE);
        if (bonus > 0.0D) {
            event.setAmount((float) (event.getAmount() * (1.0D + bonus)));
        }
    }

    // ================================================================
    // 八、敕令之契 —— 记录玩家最近打过的目标
    // ================================================================

    /**
     * 记下玩家最近攻击过的目标。
     *
     * <p>「仆从优先攻击你最近打过的目标」需要一个「你最近打了谁」的记录。
     * 直接写在这里而不是去改仆从的 AI 目标选择 —— Goety 的目标逻辑很复杂
     * （优先级目标 / 巡逻 / 守卫点各有一套），插进去容易打架。
     * 本类只负责记住这件事，供将来查询。
     */
    @SubscribeEvent
    public static void onOwnerAttack(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer owner)) {
            return;
        }
        if (!ContractCharmType.DECREE.isActive(owner)) {
            return;
        }
        RECENT_TARGET.put(owner.getUUID(),
                new TargetMark(event.getEntity().getUUID(), owner.level().getGameTime()));
    }

    /** 玩家最近攻击过的目标；已过期或未记录时返回 {@code null}。 */
    public static UUID recentTarget(ServerPlayer owner) {
        TargetMark mark = RECENT_TARGET.get(owner.getUUID());
        if (mark == null) {
            return null;
        }
        if (owner.level().getGameTime() - mark.tick() > TARGET_MEMORY_TICKS) {
            RECENT_TARGET.remove(owner.getUUID());
            return null;
        }
        return mark.target();
    }
}
