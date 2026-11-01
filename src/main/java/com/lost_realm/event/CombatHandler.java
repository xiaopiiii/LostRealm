package com.lost_realm.event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.lost_realm.Medals;
import com.lost_realm.config.MedalsConfig;
import com.lost_realm.data.MedalSource;
import com.lost_realm.data.PlayerMedals;
import com.lost_realm.item.TankAttributes;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.archer.SkillArrowRain;
import com.lost_realm.skill.archer.SkillArrowVolley;
import com.lost_realm.skill.archer.SkillChargeUp;
import com.lost_realm.skill.archer.SkillGaleStorm;
import com.lost_realm.skill.archer.SkillWeaknessInsight;
import com.lost_realm.skill.tank.SkillBlock;
import com.lost_realm.skill.tank.SkillOffensiveDrain;
import com.lost_realm.skill.tank.SkillStarShield;
import com.lost_realm.skill.warrior.SkillBladeOfFrost;
import com.lost_realm.skill.warrior.SkillBloodIntent;
import com.lost_realm.skill.warrior.SkillBurningSelf;

/**
 * 战斗事件处理 —— 熟练度获取与节点技能的战斗结算。
 *
 * <h2>熟练度溢出修正(核心规则)</h2>
 * 获取熟练度时以<b>实际造成的有效伤害</b>为准:
 * <pre>
 *   有效伤害 = min(本次施加的伤害, 目标当前生命值 + 目标当前伤害吸收)
 *   溢出伤害 = 本次施加的伤害 - 有效伤害   → 不计入熟练度
 * </pre>
 * 例如目标只剩 3 点血,一刀打出 20 点伤害,则仅 3 点计入熟练度,溢出的 17 点忽略。
 *
 * <h2>其它规则</h2>
 * <ul>
 *   <li>击杀奖励按<b>目标最大生命值</b>计算</li>
 *   <li><b>中立生物</b>收益减半</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Medals.MODID)
public class CombatHandler {

    /** 记录每名玩家本次攻击的「有效伤害」,用于击杀奖励判定 */
    private static final Map<UUID, Double> LAST_EFFECTIVE_DAMAGE = new HashMap<>();

    // ================================================================
    // 弓弦释放 —— 【三发】额外箭矢
    // ================================================================

    /**
     * 玩家松开弓弦时触发【三发】,补射额外箭矢。
     *
     * <p>注意:本事件在原版生成原箭矢「之前」触发,因此本技能只负责补射与打标记,
     * 原箭仍由原版逻辑生成与结算。
     */
    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerMedals data = PlayerMedals.of(player);
        if (MedalSource.level(player, MedalType.ARCHER) < 60) {
            return;
        }
        SkillArrowVolley.onArrowLoose(player, data, event.getCharge(), event.getBow());
    }

    // ================================================================
    // 弹射物命中 —— 【箭雨】
    // ================================================================

    /**
     * 箭矢命中目标时触发【箭雨】。
     *
     * <p>使用 {@link EventPriority#LOWEST} 以确保读取到的是最终命中结果(未被其它模组取消)。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onProjectileHit(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() == null) {
            return;
        }
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }
        if (event.getProjectile().level().isClientSide) {
            return;
        }
        if (!(arrow.getOwner() instanceof ServerPlayer shooter)) {
            return;
        }
        // 只有击中活体才触发箭雨
        if (!(event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult ehr)) {
            return;
        }
        if (!(ehr.getEntity() instanceof LivingEntity victim)) {
            return;
        }

        PlayerMedals data = PlayerMedals.of(shooter);
        SkillArrowRain.trigger(shooter, data, victim, arrow);
    }

    // ================================================================
    // 造成伤害 —— 播放技能效果 + 结算熟练度
    // ================================================================

    /**
     * 在伤害实际生效后(优先级 LOWEST)读取变化量,确保拿到的是扣血真实值。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }

        float applied = event.getAmount();

        // ---------- 射手:远程伤害 ----------
        boolean ranged = event.getSource().getDirectEntity() instanceof AbstractArrow
                || event.getSource().getDirectEntity() instanceof Projectile;
        if (ranged) {
            int lv = MedalSource.level(attacker, MedalType.ARCHER);
            double effective = effectiveDamage(applied, target);
            double gain = effective * MedalsConfig.pct(MedalsConfig.ARCHER_EXP_DAMAGE);
            applyNeutralPenalty(target, gain, attacker, MedalType.ARCHER);
        } else {
            // ---------- 战士:近战伤害 ----------
            int lv = MedalSource.level(attacker, MedalType.WARRIOR);
            double effective = effectiveDamage(applied, target);
            double gain = effective * MedalsConfig.pct(MedalsConfig.WARRIOR_EXP_DAMAGE);

            // 近战附带节点技能
            SkillBladeOfFrost.tryFreeze(attacker, target);
            SkillBurningSelf.igniteTarget(attacker, target);

            applyNeutralPenalty(target, gain, attacker, MedalType.WARRIOR);
        }

        // 记录有效伤害,供击杀奖励使用
        LAST_EFFECTIVE_DAMAGE.put(attacker.getUUID(), effectiveDamage(applied, target));

        // ---------- 法师:抑制咒施加 + 魔法易伤 ----------
        handleMageOnDamage(attacker, target, event, ranged);
    }

    /**
     * 法师相关的伤害结算。
     *
     * <h2>Lv.20【法爆】—— 法术暴击</h2>
     * 读取本模组自注册的 {@code spell_crit_chance} / {@code spell_crit_damage} 属性,
     * 在<b>魔法伤害</b>分支内掷骰。命中则把伤害乘以暴击倍率。
     *
     * <p>之所以不复用 Apothic 的 {@code crit_chance} / {@code crit_damage},
     * 是因为那两个属性只作用于<b>近战</b>伤害,对 {@code magic} 完全无效。
     *
     * <h2>Lv.30【灵魂虹吸】/ Lv.60【弹幕风暴】</h2>
     * 两者都由「法术命中」驱动:虹吸按概率返还灵魂,弹幕风暴按概率刷新冷却。
     * 由于 Goety 未开放「法术命中」事件,这里以<b>魔法伤害事件</b>作为命中信号,
     * 并用「手持魔杖的当前法术消耗」作为该次施法的近似成本。
     */
    private static void handleMageOnDamage(ServerPlayer attacker, LivingEntity target,
                                           LivingDamageEvent event, boolean ranged) {
        if (!com.lost_realm.compat.goety.GoetyCompat.isLoaded()) {
            return;
        }
        int mageLevel = MedalSource.level(attacker, MedalType.MAGE);

        // 魔法伤害判定:Goety 与多数魔法模组均使用 magic 伤害类型
        boolean magic = event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FIRE) == false
                && isMagicDamage(event.getSource());

        if (!magic) {
            return;
        }

        // ---------- Lv.20【法爆】:掷骰并放大伤害 ----------
        if (mageLevel >= 20) {
            double chance = com.lost_realm.skill.mage.SkillSpellCrit.spellCritChance(attacker);
            if (chance > 0.0D && attacker.getRandom().nextDouble() < chance) {
                double critMult = com.lost_realm.skill.mage.SkillSpellCrit.spellCritDamage(attacker);
                event.setAmount((float) (event.getAmount() * critMult));
                if (attacker.level() instanceof ServerLevel sl) {
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                            target.getX(), target.getY() + target.getBbHeight() * 0.6D, target.getZ(),
                            12, 0.35D, 0.35D, 0.35D, 0.08D);
                }
            }
        }

        // ---------- 聚晶饰品:命中时的全部附加效果 ----------
        // 放在熟练度结算之前,让狙击 / 魂燃 / 魔力透支那些伤害修正也能计进熟练度
        MageCrystalHandler.onSpellDamage(attacker, target, event);

        // ---------- 魔法熟练度(以暴击后的最终伤害为准) ----------
        double effective = effectiveDamage(event.getAmount(), target);
        double gain = effective * MedalsConfig.pct(MedalsConfig.MAGE_EXP_SPELL_DAMAGE);
        applyNeutralPenalty(target, gain, attacker, MedalType.MAGE);

        // ---------- 法术命中信号:虹吸 + 弹幕风暴 ----------
        int soulCost = soulCostOfCurrentSpell(attacker);
        if (soulCost > 0) {
            if (mageLevel >= 60) {
                com.lost_realm.skill.mage.SkillSoulSiphon.tryRefund(attacker, target, soulCost);
            }
            if (mageLevel >= 30) {
                com.lost_realm.skill.mage.SkillBarrageStorm.onSpellHit(attacker);
            }

            // ---------- 灵魂消耗转熟练度 ----------
            // 施法消耗灵魂即视为「在练习巫术」。这里以本次法术的
            // 灵魂消耗量折算计分(默认 5 点灵魂 = 1 熟练度,即配置填 20)。
            double soulGain = soulCost * MedalsConfig.pct(MedalsConfig.MAGE_EXP_SOUL);
            if (soulGain > 0.0D) {
                MedalSource.addExp(attacker, MedalType.MAGE, soulGain);
            }
        }
    }

    /**
     * 估算玩家当前手持魔杖所施法术的灵魂消耗,供【灵魂虹吸】返还与
     * 灵魂熟练度计算使用。
     *
     * <p>优先读取 Goety {@code IWand.SoulUse(施法者, 魔杖)} —— 该值已包含
     * 玩家自身的灵魂折扣,是「实际被扣除」的数值,最精确。
     * 若手上不是魔杖则返回 0(无法判定施法行为)。
     */
    private static int soulCostOfCurrentSpell(ServerPlayer attacker) {
        com.lost_realm.compat.goety.GoetyBridge goety = com.lost_realm.compat.goety.GoetyBridge.get();
        if (goety == null) {
            return 0;
        }
        try {
            net.minecraft.world.item.ItemStack main = attacker.getMainHandItem();
            net.minecraft.world.item.ItemStack off = attacker.getOffhandItem();
            net.minecraft.world.item.ItemStack wand = goety.isWand(main) ? main
                    : (goety.isWand(off) ? off : net.minecraft.world.item.ItemStack.EMPTY);
            if (wand.isEmpty()) {
                return 0;
            }
            return goety.wandSoulCost(attacker, wand);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 判断伤害来源是否为「魔法伤害」。 */
    private static boolean isMagicDamage(net.minecraft.world.damagesource.DamageSource source) {
        try {
            // 原版魔法:药水 / 间接魔法
            if (source.is(net.minecraft.tags.DamageTypeTags.WITCH_RESISTANT_TO)) {
                return true;
            }
            // Goety 等模组的自定义魔法伤害类型,按注册名兜底判断
            var key = source.getMsgId();
            return key != null && (key.contains("magic") || key.contains("spell"));
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 近战命中前(优先级 HIGH)应用【撕裂】等「攻击瞬间」增益,
     * 以及【攻势汲取】的伤害吸收获取。
     *
     * <p>同时处理额外箭矢的伤害倍率与无敌帧突破。
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttackPre(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }

        // ---------- 额外箭矢:伤害倍率 + 无敌帧突破(不要求攻击者是玩家,箭雨同理) ----------
        if (event.getSource().getDirectEntity() instanceof AbstractArrow arrow) {
            applyArrowModifiers(event, arrow);
        }

        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        LivingEntity target = event.getEntity();

        boolean melee = !(event.getSource().getDirectEntity() instanceof Projectile);
        if (!melee) {
            return;
        }

        // 【撕裂】施加流血
        int warriorLv = MedalSource.level(attacker, MedalType.WARRIOR);
        if (warriorLv >= 5) {
            com.lost_realm.skill.warrior.SkillRend.tryApplyBleeding(target, warriorLv);
        }

        // 【攻势汲取】获得伤害吸收
        if (MedalSource.level(attacker, MedalType.TANK) >= 10) {
            SkillOffensiveDrain.tryTrigger(attacker);
        }
    }

    /**
     * 对「由勋章生成的额外箭矢 / 箭雨箭矢 / 强化原箭」应用伤害倍率与无敌帧突破。
     *
     * <p>倍率记录在箭矢持久数据 {@code medals_volley_mult} 中:
     * <ul>
     *   <li>【三发】额外箭:0.25 / 0.125 / 0.0625</li>
     *   <li>兼容多重射击的原箭:1.25</li>
     *   <li>【箭雨】箭矢:1.0(底伤已在生成时打过折)</li>
     * </ul>
     * 所有带标记的箭矢都会把目标的无敌帧归零,保证多根箭各自独立结算,
     * 不会因为「受击无敌帧」而只生效一根。
     */
    private static void applyArrowModifiers(LivingHurtEvent event, AbstractArrow arrow) {
        var pd = arrow.getPersistentData();
        double mult = pd.getDouble(SkillArrowVolley.NBT_DAMAGE_MULT);
        if (mult <= 0.0D) {
            return;
        }
        if (mult != 1.0D) {
            event.setAmount((float) (event.getAmount() * mult));
        }

        // 无敌帧突破:仅对勋章机制生成的箭矢生效,原版箭矢不受影响
        if (pd.getBoolean(SkillArrowVolley.NBT_EXTRA) || mult != 1.0D) {
            LivingEntity target = event.getEntity();
            if (target.invulnerableTime > 0) {
                target.invulnerableTime = 0;
            }
        }
    }

    // ================================================================
    // 受到伤害 —— 坦克熟练度、伤害减免、星盾、格挡、射手【闪避】
    // ================================================================

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        PlayerMedals data = PlayerMedals.of(player);
        float original = event.getAmount();

        // ---------- 坦克伤害减免属性 ----------
        AttributeInstance drInst = player.getAttribute(TankAttributes.DAMAGE_REDUCTION.get());
        if (drInst != null) {
            double dr = Math.min(0.9D, drInst.getValue());
            if (dr > 0) {
                original = (float) (original * (1.0D - dr));
                event.setAmount(original);
            }
        }

        // ---------- 【星盾】逐层抵挡 ----------
        if (MedalSource.level(player, MedalType.TANK) >= 15) {
            double absorbed = SkillStarShield.absorb(player, original);
            if (absorbed > 0) {
                original = (float) Math.max(0.0D, original - absorbed);
                event.setAmount(original);
            }
        }

        // ---------- 【格挡】 ----------
        if (MedalSource.level(player, MedalType.TANK) >= 25) {
            SkillBlock.tryGainBlock(player);
            double absorbed = SkillBlock.absorb(player, original);
            if (absorbed > 0) {
                original = (float) Math.max(0.0D, original - absorbed);
                event.setAmount(original);
            }
        }

        // ---------- 坦克熟练度:承受伤害 ----------
        double effective = effectiveDamage(original, player);
        double tankGain = effective * MedalsConfig.pct(MedalsConfig.TANK_EXP_DAMAGE);
        MedalSource.addExp(player, MedalType.TANK, tankGain);

        // ---------- 射手【闪避】 ----------
        if (MedalSource.level(player, MedalType.ARCHER) >= 5) {
            handleDodge(player, data, event);
        }
    }

    /** 【闪避】:拉弓被攻击时 60% 概率免疫并传送,冷却 20 秒。 */
    private static void handleDodge(ServerPlayer player, PlayerMedals data, LivingHurtEvent event) {
        boolean drawing = player.isUsingItem()
                && player.getUseItem().getItem() instanceof net.minecraft.world.item.BowItem;
        if (!drawing) {
            return;
        }
        if (com.lost_realm.skill.archer.SkillDodge.onCooldown(data)
                || com.lost_realm.skill.archer.SkillDodge.usedThisDraw(data)) {
            return;
        }
        if (player.getRandom().nextDouble() >= MedalsConfig.pct(MedalsConfig.S5_DODGE_IMMUNE_CHANCE)) {
            return;
        }

        // 寻找同 Y 轴、6 格内的合法落点(至少 2 格高空间)
        var dest = findTeleportSpot(player);
        if (dest == null) {
            return;
        }

        event.setAmount(0.0F);
        event.setCanceled(true);

        player.teleportTo(dest.getX() + 0.5D, dest.getY(), dest.getZ() + 0.5D);
        com.lost_realm.skill.archer.SkillDodge.markUsed(data);
        com.lost_realm.skill.archer.SkillDodge.startCooldown(data);

        if (player.level() instanceof ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,
                    player.getX(), player.getY() + 1.0D, player.getZ(),
                    20, 0.3D, 0.5D, 0.3D, 0.02D);
            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.7F, 1.4F);
        }
    }

    /** 在同 Y 轴 5 格内寻找至少 2 格高的可站立空间。 */
    private static net.minecraft.core.BlockPos findTeleportSpot(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        net.minecraft.core.BlockPos origin = player.blockPosition();
        int range = MedalsConfig.S5_TELEPORT_RANGE.get();
        int minHeight = MedalsConfig.S5_TELEPORT_MIN_HEIGHT.get();

        java.util.List<net.minecraft.core.BlockPos> candidates = new java.util.ArrayList<>();
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                net.minecraft.core.BlockPos base = origin.offset(dx, 0, dz);
                if (hasSpace(level, base, minHeight)) {
                    candidates.add(base);
                }
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(player.getRandom().nextInt(candidates.size()));
    }

    private static boolean hasSpace(ServerLevel level, net.minecraft.core.BlockPos base, int height) {
        // 脚下需为可站立方块,上方 height 格需为空气
        if (level.getBlockState(base.below()).isAir()) {
            return false;
        }
        for (int i = 0; i < height; i++) {
            if (!level.getBlockState(base.above(i)).isAir()) {
                return false;
            }
        }
        return true;
    }

    // ================================================================
    // 击杀 —— 熟练度奖励
    // ================================================================

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) {
            return;
        }

        PlayerMedals data = PlayerMedals.of(killer);
        double maxHp = victim.getMaxHealth();
        boolean neutral = isNeutral(victim);

        // 射手击败奖励
        double archerGain = maxHp * MedalsConfig.pct(MedalsConfig.ARCHER_EXP_KILL_HP);
        // 战士击败奖励
        double warriorGain = maxHp * MedalsConfig.pct(MedalsConfig.WARRIOR_EXP_KILL_HP);
        // 坦克击败奖励
        double tankGain = maxHp * MedalsConfig.pct(MedalsConfig.TANK_EXP_KILL_HP);

        // 中立生物收益减半
        if (neutral) {
            archerGain *= 0.5D;
            warriorGain *= 0.5D;
            tankGain *= 0.5D;
        }

        // 依据击杀方式决定主要归属:远程击杀给射手,近战给战士
        boolean ranged = event.getSource().getDirectEntity() instanceof Projectile;
        if (ranged) {
            MedalSource.addExp(killer, MedalType.ARCHER, archerGain);
        } else {
            MedalSource.addExp(killer, MedalType.WARRIOR, warriorGain);
        }
        // 坦克无论方式都获得击杀奖励
        MedalSource.addExp(killer, MedalType.TANK, tankGain);

        // ---------- 法师:击杀奖励 ----------
        if (com.lost_realm.compat.goety.GoetyCompat.isLoaded()) {
            double mageGain = maxHp * MedalsConfig.pct(MedalsConfig.MAGE_EXP_KILL_HP);
            if (neutral) {
                mageGain *= 0.5D;
            }
            MedalSource.addExp(killer, MedalType.MAGE, mageGain);

            // 「魔法击杀」给法师额外判定:若伤害来源为魔法,再加一份
            if (isMagicDamage(event.getSource())) {
                MedalSource.addExp(killer, MedalType.MAGE, mageGain * 0.5D);
            }
        }

        // 【疾风骤雨】击杀延长
        SkillGaleStorm.onKill(killer, data);

        // 清理记录
        LAST_EFFECTIVE_DAMAGE.remove(killer.getUUID());

        // 等级变化时刷新属性
        com.lost_realm.event.LevelSync.refreshIfNeeded(killer, data);
    }

    // ================================================================
    // 治疗 —— 坦克熟练度
    // ================================================================

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        double amount = event.getAmount();
        if (amount <= 0) {
            return;
        }
        double gain = amount * MedalsConfig.pct(MedalsConfig.TANK_EXP_HEAL);
        MedalSource.addExp(player, MedalType.TANK, gain);
    }

    // ================================================================
    // 溢出修正工具
    // ================================================================

    /**
     * 计算「实际造成的有效伤害」。
     *
     * <p>有效伤害 = min(本次伤害, 目标当前生命值 + 目标当前伤害吸收)。
     * 超出部分即为溢出伤害,不计入熟练度。
     *
     * @param rawDamage 本次施加的伤害
     * @param target    承受伤害的目标
     */
    public static double effectiveDamage(double rawDamage, LivingEntity target) {
        if (rawDamage <= 0) {
            return 0.0D;
        }
        double pool = target.getHealth() + target.getAbsorptionAmount();
        return Math.min(rawDamage, Math.max(0.0D, pool));
    }

    /** 应用「中立生物收益减半」并写入经验。 */
    public static void applyNeutralPenalty(LivingEntity target, double gain, ServerPlayer player, MedalType type) {
        if (isNeutral(target)) {
            gain *= 0.5D;
        }
        MedalSource.addExp(player, type, gain);
    }

    /**
     * 判断是否为中立生物。
     *
     * <p>判定优先级:
     * <ol>
     *   <li>实现了 {@link NeutralMob} 接口(如末影人、蜜蜂、羊驼等)</li>
     *   <li>被标记为中立(tag {@code lost_realm:neutral} 或原版 {@code neutral_entities})</li>
     * </ol>
     */
    public static boolean isNeutral(LivingEntity entity) {
        if (entity instanceof NeutralMob) {
            return true;
        }
        // 数据包标签支持
        try {
            return entity.getType().is(com.lost_realm.common.MedalTags.NEUTRAL_MOBS);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 供外部读取最近一次有效伤害(调试用)。 */
    public static double lastEffective(Player player) {
        return LAST_EFFECTIVE_DAMAGE.getOrDefault(player.getUUID(), 0.0D);
    }
}
