package com.lost_realm.attribute;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.lost_realm.Medals;

/**
 * 让 {@link RealmAttributes} 的 12 条属性<b>真的生效</b>的地方。
 *
 * <h2>为什么必须有这个类</h2>
 * 属性（{@code Attribute}）只是一块「玩家身上的数字」，本身不做任何事。
 * beta29 之前这些数字是 Apothic Attributes 在替我们消费的
 * （暴击在它的 {@code LivingHurtEvent} 里掷骰、吸血在同一个事件里回血、
 * 护甲穿透在它的 Mixin 里减护甲）。拆掉前置而不补这一层，
 * 结果就是<b>属性还在、效果全没了</b> —— 属于本项目最忌讳的「静默失效」。
 *
 * <h2>与 attributeslib 1.3.7 的对齐情况</h2>
 * 逐条照抄了它的判定顺序与公式，唯一两处<b>有意的偏离</b>：
 * <ol>
 *   <li><b>暴击限定物理伤害</b>：attributeslib 对任意 {@code LivingHurtEvent} 掷骰，
 *       于是法师的魔法伤害也会吃战士的暴击率，和法师自己的
 *       {@code spell_crit_chance} 叠成双重暴击。这里限定
 *       {@link #isPhysical(DamageSource)}，把两套暴击彻底分开。</li>
 *   <li><b>火焰/冰霜附加伤害并入本次结算</b>：attributeslib 会另起一次
 *       {@code hurt()}（自带一套自定义伤害类型，绕过无敌帧与护甲）。
 *       本模组不新增伤害类型，改为直接在 {@code LivingHurtEvent} 里加算，
 *       因此这部分伤害<b>会吃护甲减伤</b>。数值量级只有个位数，
 *       观感上是「刀上带的元素附魔」，不是「额外再打一拳」。</li>
 * </ol>
 *
 * <h2>护甲穿透不在这里</h2>
 * 破甲必须插进原版 {@code getDamageAfterArmorAbsorb} 才能影响护甲公式，
 * 事件层拿不到那个时机，见 {@code mixin/LivingEntityArmorPierceMixin}。
 *
 * <h2>排查顺序（出问题先看这里）</h2>
 * 属性没生效时按此顺序查：<b>属性是否挂到该实体类型</b>（{@code EntityAttributeModificationEvent}）
 * → <b>修饰符 UUID 是否真的加上了</b>（{@code AttributeGuard}）→ <b>事件是否被别的模组取消</b>。
 */
@Mod.EventBusSubscriber(modid = Medals.MODID)
public final class RealmAttributeEffects {

    /** 箭矢已处理标记，防止同一根箭重复叠伤害。 */
    private static final String TAG_ARROW_DONE = Medals.MODID + ".arrow.done";

    // ================================================================
    // 暴击
    // ================================================================

    /**
     * 属性暴击。
     *
     * <p>与 {@code crit_chance} 的「连暴」语义一致：基础暴击概率是 0.05，
     * 每命中一次就从概率里扣掉 1.0（即 100%）再掷一次，
     * 而每多暴一次，暴击倍率乘以 0.85 —— 所以「100% 暴击率」只保证暴一次，
     * 想连暴两次需要 200% 的暴击率。
     *
     * <p>历史数值：战士 60 级 + 终极剑的 +100% 正好凑到 105%，即必定单暴。
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurtCrit(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        // 只对物理伤害生效 —— 魔法有法师自己的 spell_crit_chance
        if (!isPhysical(event.getSource())) {
            return;
        }
        // 必须有一个「直接实体」：流血这类持续伤害没有直接实体，
        // 拿它去掷暴击会让每 2 秒一跳的伤害也暴击。
        if (event.getSource().getDirectEntity() == null) {
            return;
        }

        double chance = attacker.getAttributeValue(RealmAttributes.critChance());
        double critDamage = attacker.getAttributeValue(RealmAttributes.critDamage());
        if (chance <= 0.0D || critDamage <= 1.0D) {
            return;
        }

        RandomSource random = victim.getRandom();
        float multiplier = 1.0F;
        while (random.nextFloat() <= chance && critDamage > 1.0D) {
            chance -= 1.0D;
            multiplier *= (float) critDamage;
            critDamage *= 0.85D;
        }
        if (multiplier <= 1.0F) {
            return;
        }

        event.setAmount(event.getAmount() * multiplier);

        if (victim.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT,
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.6D, victim.getZ(),
                    14, 0.35D, 0.4D, 0.35D, 0.1D);
        }
    }

    /**
     * 原版跳劈暴击的倍率覆盖。
     *
     * <p>原版暴击固定 ×1.5，{@code crit_damage} 属性对它<b>不生效</b> ——
     * 所以这里在 {@code CriticalHitEvent} 里把倍率抬到属性的值。
     * 只抬不降：玩家自己（或别的模组）给的更高倍率保留。
     */
    @SubscribeEvent
    public static void onVanillaCrit(CriticalHitEvent event) {
        if (!event.isVanillaCritical()) {
            return;
        }
        float critDamage = (float) event.getEntity().getAttributeValue(RealmAttributes.critDamage());
        event.setDamageModifier(Math.max(event.getDamageModifier(), critDamage));
    }

    /**
     * 给「打非 LivingEntity 目标」补一次跳劈暴击判定。
     *
     * <p>原版跳劈暴击在 {@code Player.attack()} 里硬性要求
     * {@code target instanceof LivingEntity}，所以打 {@code ErebosPart}（纯 Entity）
     * 这类碰撞部件时，那一刀不吃 ×1.5 暴击倍率。本方法复刻原版的判定条件
     * （空中、非疾跑、非攀爬/水中/失明/骑乘），命中则调
     * {@code ForgeHooks.getCriticalHit} 触发一次 {@code CriticalHitEvent}
     * —— 这样 {@link #onVanillaCrit} 会把倍率抬到 {@code crit_damage} 属性值，
     * 与打头部本体完全一致。
     *
     * @return 乘过暴击倍率后的伤害（未暴击则原样返回）
     */
    public static float applyJumpCrit(Player player, net.minecraft.world.entity.Entity target, float amount) {
        // 复刻原版 Player.attack 里计算 critical 的物理条件
        boolean canCrit = !player.onGround()
                && !player.onClimbable()
                && !player.isInWater()
                && !player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)
                && !player.isPassenger()
                && !player.isSprinting();
        if (!canCrit) {
            return amount;
        }
        // 触发一次 CriticalHitEvent：vanillaCritical=true、默认倍率 1.5，
        // onVanillaCrit 会在事件里把倍率抬到 crit_damage 属性值。
        var event = net.minecraftforge.common.ForgeHooks.getCriticalHit(
                player, target, true, 1.5F);
        if (event == null) {
            return amount * 1.5F;   // 事件被取消（罕见），退回默认倍率
        }
        float mult = event.isVanillaCritical() ? event.getDamageModifier() : 1.0F;
        return amount * mult;
    }

    // ================================================================
    // 吸血 / 过量治疗 / 元素附加伤害
    // ================================================================

    /**
     * 命中后的吸血、过量治疗与火焰/冰霜附加伤害。
     *
     * <p>优先级 NORMAL：暴击（HIGH）已经把伤害放大过，这里读到的才是最终值；
     * 而熟练度结算在 LOWEST，所以这部分伤害也会正常计入熟练度。
     */
    @SubscribeEvent
    public static void onHurtPost(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        if (!(event.getSource().getDirectEntity() instanceof LivingEntity attacker)) {
            return;
        }
        if (!isPhysical(event.getSource())) {
            return;
        }

        // ---- 火焰 / 冰霜附加伤害（近战限定）----
        boolean melee = !(event.getSource().getDirectEntity() instanceof Projectile);
        if (melee) {
            // 与原版一致的「攻击蓄力」门槛：轻点一下不触发元素效果
            float strength = attacker instanceof Player player
                    ? player.getAttackStrengthScale(0.5F) : 1.0F;

            float fire = (float) attacker.getAttributeValue(RealmAttributes.fireDamage());
            if (fire > 0.001F && strength >= 0.55F) {
                event.setAmount(event.getAmount() + fire);
                victim.setRemainingFireTicks(
                        victim.getRemainingFireTicks() + (int) (10.0F * fire));
            }

            float cold = (float) attacker.getAttributeValue(RealmAttributes.coldDamage());
            if (cold > 0.001F && strength >= 0.55F) {
                event.setAmount(event.getAmount() + cold);
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                        (int) (15.0F * cold), Mth.floor(cold / 5.0F)), attacker);
            }
        }

        // ---- 吸血 / 过量治疗 ----
        // 按「目标实际还剩多少血」封顶，避免一刀砍死时回满
        float dealt = Math.min(event.getAmount(), victim.getHealth());

        float lifeSteal = (float) attacker.getAttributeValue(RealmAttributes.lifeSteal());
        if (lifeSteal > 0.001F) {
            attacker.heal(dealt * lifeSteal);
        }

        float overheal = (float) attacker.getAttributeValue(RealmAttributes.overheal());
        if (overheal > 0.001F) {
            float cap = attacker.getMaxHealth() * 0.5F;
            if (attacker.getAbsorptionAmount() < cap) {
                attacker.setAbsorptionAmount(Math.min(cap,
                        attacker.getAbsorptionAmount() + dealt * overheal));
            }
        }
    }

    // ================================================================
    // 受疗加成
    // ================================================================

    /**
     * 受到治疗时的倍率修正。
     *
     * <p>优先级 HIGHEST：先把倍率乘好，让 {@code CombatHandler} 在 LOWEST 统计坦克熟练度时
     * 拿到的是「最终治疗量」，而不是乘之前的值。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        float mult = (float) entity.getAttributeValue(RealmAttributes.healingReceived());
        if (mult == 1.0F) {
            return;
        }
        event.setAmount(event.getAmount() * mult);
        if (event.getAmount() <= 0.0F) {
            event.setCanceled(true);
        }
    }

    // ================================================================
    // 经验倍率
    // ================================================================

    /** 生物掉落经验的经验倍率（战士终极「我有一剑！」的 +1000% 挂在这里）。 */
    @SubscribeEvent
    public static void onXpDrop(LivingExperienceDropEvent event) {
        Player player = event.getAttackingPlayer();
        if (player == null || player.level().isClientSide) {
            return;
        }
        double gained = player.getAttributeValue(RealmAttributes.experienceGained());
        if (gained == 1.0D) {
            return;
        }
        event.setDroppedExperience((int) (event.getDroppedExperience() * gained));
    }

    // ================================================================
    // 箭矢：伤害倍率
    // ================================================================

    /**
     * 箭矢生成时按 {@code arrow_damage} 放大基础伤害。
     *
     * <p>只处理一次：生成事件在客户端也会触发，所以既判 {@code isClientSide}
     * 又写持久标记 —— 光判客户端不够，箭从服务端同步到客户端时还会再触发一次。
     */
    @SubscribeEvent
    public static void onArrowJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof AbstractArrow arrow)) {
            return;
        }
        var data = arrow.getPersistentData();
        if (data.getBoolean(TAG_ARROW_DONE)) {
            return;
        }
        if (arrow.getOwner() instanceof LivingEntity owner) {
            double arrowDamage = owner.getAttributeValue(RealmAttributes.arrowDamage());
            if (arrowDamage != 1.0D) {
                arrow.setBaseDamage(arrow.getBaseDamage() * arrowDamage);
            }
        }
        data.putBoolean(TAG_ARROW_DONE, true);
    }

    // ================================================================
    // 拉弓速度
    // ================================================================

    /**
     * 拉弓速度。
     *
     * <h2>为什么是「往回加时间」而不是「减少时间」</h2>
     * 这个事件里 {@code getDuration()} 是<b>还剩多少 tick</b>，原版每 tick 自己减 1。
     * 想加速就必须把递减抵消掉一部分 —— 所以真正的做法是
     * <b>按比例每隔几 tick 把时长加回去 1</b>：
     * <pre>
     *   draw_speed = 1.08 → 每 12 tick 补 1 tick → 净减 11/12 ≈ 快 8%
     *   draw_speed = 2.00 → 每 tick 补 1 tick  → 完全抵消 → 快 100%
     * </pre>
     * 直接写成「每 tick 加 1」会让拉弓条永远不动（净变化为 0），这是最容易写错的地方。
     */
    @SubscribeEvent
    public static void onUseItemTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        double draw = player.getAttributeValue(RealmAttributes.drawSpeed()) - 1.0D;
        if (draw == 0.0D || !canBenefitFromDrawSpeed(event.getItem())) {
            return;
        }

        int sign = 1;
        if (draw < 0.0D) {
            sign = -1;
            draw = -draw;
        }

        // 每满 1.0 就每 tick 补一次
        while (draw > 1.0D) {
            event.setDuration(event.getDuration() + sign);
            draw -= 1.0D;
        }
        // 半档：隔 tick 补一次
        if (draw > 0.5D) {
            if (player.tickCount % 2 == 0) {
                event.setDuration(event.getDuration() + sign);
            }
            draw -= 0.5D;
        }
        // 小于半档：每 1/draw tick 补一次
        double rate = Math.min(1.0D, draw);
        if (rate > 0.0D) {
            int period = (int) Math.floor(1.0D / rate);
            if (period > 0 && player.tickCount % period == 0) {
                event.setDuration(event.getDuration() + sign);
            }
        }
    }

    /** 只有能「蓄力」的物品吃拉弓速度：弓 / 弩 / 三叉戟。 */
    private static boolean canBenefitFromDrawSpeed(ItemStack stack) {
        return stack.getItem() instanceof ProjectileWeaponItem
                || stack.getItem() instanceof TridentItem;
    }

    // ================================================================
    // 闪避
    // ================================================================

    /**
     * 近战闪避。
     *
     * <p>优先级 HIGHEST：闪避要<b>先于一切伤害修正</b>判定并取消事件，
     * 否则别的模组已经按「打中了」做了处理。
     *
     * <p>判定要求「攻击者确实够得着」—— 否则隔着 30 格的远程单体技能
     * 也会被闪避掉，玩家会觉得莫名其妙。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackDodge(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        if (!(event.getSource().getDirectEntity() instanceof LivingEntity attacker)) {
            return;
        }
        if (attacker.distanceToSqr(victim) > attackReachSqr(attacker, victim)) {
            return;
        }
        if (!isDodging(victim)) {
            return;
        }
        event.setCanceled(true);
    }

    /**
     * 弹射物闪避。
     *
     * <p>这里<b>不</b>做距离判定：投掷物已经打在身上了。
     * 取消事件 = 这一次命中直接不算，箭会按原版规则插在地上或消失。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileDodge(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) {
            return;
        }
        if (!(hit.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        if (victim.level().isClientSide) {
            return;
        }
        if (!isDodging(victim)) {
            return;
        }
        event.setCanceled(true);
    }

    /**
     * 是否闪避成功。
     *
     * <p>用「实体 tick + UUID」拼种子，好处是<b>同一次攻击双端算出来结论一致</b>，
     * 不会出现「客户端看到掉血、服务端说没掉」的错位。
     */
    public static boolean isDodging(LivingEntity entity) {
        double chance = entity.getAttributeValue(RealmAttributes.dodgeChance());
        if (chance <= 0.0D) {
            return false;
        }
        if (chance >= 1.0D) {
            return true;
        }
        int seed = entity.tickCount + entity.getUUID().hashCode();
        int mixed = -1640531527 + seed + (seed << 6) + (seed >> 2);
        return new java.util.Random(mixed).nextFloat() <= chance;
    }

    /**
     * 攻击者的可及距离平方。
     *
     * <p>玩家固定按 3 格（原版近战触及距离）算；其它生物按体型推，
     * 与它们 AI 里 {@code MeleeAttackGoal} 的判定口径接近。
     */
    private static double attackReachSqr(LivingEntity attacker, LivingEntity victim) {
        double reach = attacker instanceof Player
                ? 3.0D
                : attacker.getBbWidth() * 2.0D + victim.getBbWidth() + 1.0D;
        return reach * reach;
    }

    // ================================================================
    // 公共判定
    // ================================================================

    /**
     * 是否「物理伤害」。
     *
     * <p>判定口径与 attributeslib 一致：魔法、间接魔法、火焰标签、爆炸标签
     * <b>都不算</b>物理。吸血、元素附加、属性暴击都以此为准。
     */
    public static boolean isPhysical(DamageSource source) {
        return !(source.is(DamageTypes.MAGIC)
                || source.is(DamageTypes.INDIRECT_MAGIC)
                || source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypeTags.IS_EXPLOSION));
    }

    private RealmAttributeEffects() {}
}
