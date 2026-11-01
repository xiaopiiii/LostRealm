package com.lost_realm.item;

import net.minecraft.server.level.ServerPlayer;

import com.lost_realm.attribute.RealmAttributes;
import com.lost_realm.config.MedalsConfig;
import com.lost_realm.data.PlayerMedals;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;
import com.lost_realm.skill.SkillRegistry;
import com.lost_realm.util.AttributeGuard;
import com.lost_realm.util.ModifierIds;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 勋章运行时 —— 负责按等级把「基础成长」与「节点技能被动」翻译成属性修饰符。
 *
 * <p>全部使用固定 UUID 施加(见 {@link ModifierIds}),并由 {@link AttributeGuard}
 * 做防叠加保护,因此本方法可安全地每个 tick 幂等调用。
 */
public final class MedalRuntime {

    /**
     * 幂等刷新某枚勋章在当前等级下应提供的所有属性。
     *
     * <p><b>推荐用法</b>:传入佩戴中的 {@code stack},等级从勋章物品自身的 NBT 读取。
     * 这样属性完全跟随「玩家此刻实际佩戴的那枚勋章」,取下来属性立刻消失。
     *
     * @param player 服务端玩家
     * @param type   勋章类型
     * @param data   玩家数据(提供冷却 / 技能状态等玩家侧上下文)
     * @param stack  佩戴中的勋章物品栈(等级与经验来源)
     */
    public static void refresh(ServerPlayer player, MedalType type, PlayerMedals data, net.minecraft.world.item.ItemStack stack) {
        refreshWithLevel(player, type, com.lost_realm.data.MedalNbt.of(stack).getLevel(type));
    }

    /** 按指定等级施加全部属性与被动。 */
    public static void refreshWithLevel(ServerPlayer player, MedalType type, int level) {
        // 法师 / 召唤师勋章依赖 Goety;未安装时直接跳过,避免触碰 Goety 的类
        if ((type == MedalType.MAGE || type == MedalType.SUMMONER)
                && !com.lost_realm.compat.goety.GoetyCompat.isLoaded()) {
            return;
        }
        DIRTY.add(key(player, type));
        switch (type) {
            case ARCHER -> applyArcher(player, level);
            case WARRIOR -> applyWarrior(player, level);
            case TANK -> applyTank(player, level);
            case MAGE -> applyMage(player, level);
            case SUMMONER -> applySummoner(player, level);
        }

        // 节点技能被动(仅已解锁的)
        for (Skill skill : SkillRegistry.of(type)) {
            if (level >= skill.unlockLevel()) {
                skill.applyPassive(player, level);
            }
        }
    }

    /** 记录哪些玩家+勋章曾施加过修饰符,用于幂等清理 */
    private static final java.util.Set<String> DIRTY = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static String key(ServerPlayer player, MedalType type) {
        return player.getUUID() + ":" + type.id();
    }

    /** 仅在曾施加过时才清理,避免每 tick 无意义遍历。 */
    public static void clearIfNeeded(ServerPlayer player, MedalType type) {
        if (DIRTY.remove(key(player, type))) {
            clear(player, type);
        }
    }

    /** 卸下勋章时清理全部修饰符。 */
    public static void clear(ServerPlayer player, MedalType type) {
        DIRTY.remove(key(player, type));
        // 清理基础成长
        switch (type) {
            case ARCHER -> {
                AttributeGuard.clearOwned(player, RealmAttributes.arrowDamage());
                AttributeGuard.clearOwned(player, RealmAttributes.drawSpeed());
                AttributeGuard.clearOwned(player, Attributes.MOVEMENT_SPEED);
                AttributeGuard.clearOwned(player, RealmAttributes.dodgeChance());
                AttributeGuard.clearOwned(player, RealmAttributes.critDamage());
            }
            case WARRIOR -> {
                AttributeGuard.clearOwned(player, Attributes.ATTACK_DAMAGE);
                AttributeGuard.clearOwned(player, Attributes.ATTACK_SPEED);
                AttributeGuard.clearOwned(player, RealmAttributes.critChance());
                AttributeGuard.clearOwned(player, RealmAttributes.critDamage());
                AttributeGuard.clearOwned(player, RealmAttributes.armorPierce());
                AttributeGuard.clearOwned(player, RealmAttributes.lifeSteal());
                AttributeGuard.clearOwned(player, RealmAttributes.fireDamage());
                AttributeGuard.clearOwned(player, RealmAttributes.coldDamage());
            }
            case TANK -> {
                AttributeGuard.clearOwned(player, Attributes.ARMOR);
                AttributeGuard.clearOwned(player, Attributes.MAX_HEALTH);
                AttributeGuard.clearOwned(player, RealmAttributes.overheal());
                AttributeGuard.clearOwned(player, Attributes.KNOCKBACK_RESISTANCE);
            }
            case MAGE -> {
                // Goety 的法术属性由桥接统一清理
                if (com.lost_realm.compat.goety.GoetyCompat.isLoaded()) {
                    com.lost_realm.compat.goety.GoetyBridge bridge = com.lost_realm.compat.goety.GoetyBridge.get();
                    if (bridge != null) {
                        bridge.clearSpellModifiers(player);
                    }
                }
                // 法术暴击:本模组自注册属性,与 Goety 无关,需单独清理
                AttributeGuard.clearOwned(player, MageAttributes.SPELL_CRIT_CHANCE.get());
                AttributeGuard.clearOwned(player, MageAttributes.SPELL_CRIT_DAMAGE.get());
            }
            case SUMMONER -> {
                // 四条仆从属性都是本模组自注册的,直接清干净
                AttributeGuard.clearOwned(player, ServantAttributes.SERVANT_HEALTH.get());
                AttributeGuard.clearOwned(player, ServantAttributes.SERVANT_DAMAGE.get());
                AttributeGuard.clearOwned(player, ServantAttributes.SERVANT_SPEED.get());
                AttributeGuard.clearOwned(player, ServantAttributes.SERVANT_LIFESPAN.get());
                // 召唤冷却折扣等 Goety 侧的加成由各节点技能自行清理
                // 仆从身上的加成由 SummonerCharmHandler 在下一轮刷新时收回
                com.lost_realm.event.SummonerCharmHandler.forgetPlayer(player.getUUID());
            }
        }
        for (Skill skill : SkillRegistry.of(type)) {
            skill.clear(player);
        }
        // 生命值上限变化后需要修正当前血量
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    // ================================================================
    // 射手:Lv.2 箭伤+8% 拉弓+5% 移速+5% 闪避+5%
    //       1~29 级每级 箭伤+1.5% 拉弓+0.8% 移速+0.8% 闪避+0.5%
    // ================================================================
    private static void applyArcher(ServerPlayer player, int level) {
        // 每级增量按 (level - 1) 叠加
        int steps = Math.max(0, level - 1);

        double arrow = MedalsConfig.pct(MedalsConfig.ARCHER_L1_ARROW_DMG)
                + MedalsConfig.pctD(MedalsConfig.ARCHER_PER_ARROW_DMG) * steps;
        double draw = MedalsConfig.pct(MedalsConfig.ARCHER_L1_DRAW_SPEED)
                + MedalsConfig.pctD(MedalsConfig.ARCHER_PER_DRAW_SPEED) * steps;
        double move = MedalsConfig.pct(MedalsConfig.ARCHER_L1_MOVE_SPEED)
                + MedalsConfig.pctD(MedalsConfig.ARCHER_PER_MOVE_SPEED) * steps;
        double dodge = MedalsConfig.pct(MedalsConfig.ARCHER_L1_DODGE)
                + MedalsConfig.pctD(MedalsConfig.ARCHER_PER_DODGE) * steps;

        // 箭伤 / 拉弓速度:本模组自注册的百分比属性,基础 1.0,MULTIPLY_BASE 按比例加成
        apply(player, RealmAttributes.arrowDamage(), ModifierIds.ARCHER_ARROW_DAMAGE,
                "archer_arrow_damage", arrow, AttributeModifier.Operation.MULTIPLY_BASE);
        apply(player, RealmAttributes.drawSpeed(), ModifierIds.ARCHER_DRAW_SPEED,
                "archer_draw_speed", draw, AttributeModifier.Operation.MULTIPLY_BASE);

        // 移速:原版属性,使用 MULTIPLY_BASE
        apply(player, Attributes.MOVEMENT_SPEED, ModifierIds.ARCHER_MOVE_SPEED,
                "archer_move_speed", move, AttributeModifier.Operation.MULTIPLY_BASE);

        // 闪避:0~1 上限的属性,直接用加法
        apply(player, RealmAttributes.dodgeChance(), ModifierIds.ARCHER_DODGE,
                "archer_dodge", Math.min(1.0D, dodge), AttributeModifier.Operation.ADDITION);
    }

    // ================================================================
    // 战士:Lv.2 伤害+5% 攻速+5% 暴击率+5% 暴击伤害+5%
    //       1~29 级每级 伤害+0.75% 攻速+0.5% 暴击率+0.5% 暴击伤害+1%
    // ================================================================
    private static void applyWarrior(ServerPlayer player, int level) {
        int steps = Math.max(0, level - 1);

        double dmg = MedalsConfig.pct(MedalsConfig.WARRIOR_L1_DAMAGE)
                + MedalsConfig.pctD(MedalsConfig.WARRIOR_PER_DAMAGE) * steps;
        double atkSpeed = MedalsConfig.pct(MedalsConfig.WARRIOR_L1_ATTACK_SPEED)
                + MedalsConfig.pctD(MedalsConfig.WARRIOR_PER_ATTACK_SPEED) * steps;
        double critChance = MedalsConfig.pct(MedalsConfig.WARRIOR_L1_CRIT_CHANCE)
                + MedalsConfig.pctD(MedalsConfig.WARRIOR_PER_CRIT_CHANCE) * steps;
        double critDamage = MedalsConfig.pct(MedalsConfig.WARRIOR_L1_CRIT_DAMAGE)
                + MedalsConfig.pctD(MedalsConfig.WARRIOR_PER_CRIT_DAMAGE) * steps;

        // 近战伤害:原版 attack_damage,MULTIPLY_BASE 表示按基础值百分比提升
        apply(player, Attributes.ATTACK_DAMAGE, ModifierIds.WARRIOR_DAMAGE,
                "warrior_damage", dmg, AttributeModifier.Operation.MULTIPLY_BASE);

        apply(player, Attributes.ATTACK_SPEED, ModifierIds.WARRIOR_ATTACK_SPEED,
                "warrior_attack_speed", atkSpeed, AttributeModifier.Operation.MULTIPLY_BASE);

        // 暴击率:0~10 上限,基础 0.05,加法即为绝对百分点
        apply(player, RealmAttributes.critChance(), ModifierIds.WARRIOR_CRIT_CHANCE,
                "warrior_crit_chance", critChance, AttributeModifier.Operation.ADDITION);

        // 暴击伤害:基础 1.5,基础值百分比提升
        apply(player, RealmAttributes.critDamage(), ModifierIds.WARRIOR_CRIT_DAMAGE,
                "warrior_crit_damage", critDamage, AttributeModifier.Operation.MULTIPLY_BASE);
    }

    // ================================================================
    // 坦克:Lv.2 护甲+5% 最大生命+5% 过量治疗+10% 减免+5%
    //       1~29 级每级 护甲+0.75% 最大生命+1% 过量治疗+1% 减免+0.5%
    // ================================================================
    private static void applyTank(ServerPlayer player, int level) {
        int steps = Math.max(0, level - 1);

        double armor = MedalsConfig.pct(MedalsConfig.TANK_L1_ARMOR)
                + MedalsConfig.pctD(MedalsConfig.TANK_PER_ARMOR) * steps;
        double maxHp = MedalsConfig.pct(MedalsConfig.TANK_L1_MAX_HEALTH)
                + MedalsConfig.pctD(MedalsConfig.TANK_PER_MAX_HEALTH) * steps;
        double overheal = MedalsConfig.pct(MedalsConfig.TANK_L1_OVERHEAL)
                + MedalsConfig.pctD(MedalsConfig.TANK_PER_OVERHEAL) * steps;

        apply(player, Attributes.ARMOR, ModifierIds.TANK_ARMOR,
                "tank_armor", armor, AttributeModifier.Operation.MULTIPLY_BASE);

        apply(player, Attributes.MAX_HEALTH, ModifierIds.TANK_MAX_HEALTH,
                "tank_max_health", maxHp, AttributeModifier.Operation.MULTIPLY_BASE);

        // 过量治疗:0~10,直接加法
        apply(player, RealmAttributes.overheal(), ModifierIds.TANK_OVERHEAL,
                "tank_overheal", overheal, AttributeModifier.Operation.ADDITION);

        // 伤害减免:本模组自定义属性(TankAttributes),下一节定义
        apply(player, TankAttributes.DAMAGE_REDUCTION.get(), ModifierIds.TANK_DAMAGE_REDUCTION,
                "tank_damage_reduction",
                MedalsConfig.pct(MedalsConfig.TANK_L1_DAMAGE_REDUCTION)
                        + MedalsConfig.pctD(MedalsConfig.TANK_PER_DAMAGE_REDUCTION) * steps,
                AttributeModifier.Operation.ADDITION);
    }

    /** 统一入口:空属性安全跳过。 */
    private static void apply(ServerPlayer player, Attribute attr, java.util.UUID uuid, String name,
                              double amount, AttributeModifier.Operation op) {
        if (attr == null) {
            return;
        }
        AttributeGuard.apply(player, attr, uuid, name, amount, op);
    }

    // ================================================================
    // 法师:Lv.2 法术强度+2 施法速度+5% 灵魂折扣+2% 作用半径+5%
    //       1~29 级每级 强度+0.5 施法速度+0.5% 灵魂折扣+0.15% 半径+1%
    //
    // 全部作用于 Goety 自身注册的属性(goety:spell_potency 等),
    // 因此与 Goety 的聚晶法术天生联动,无需拦截施法流程。
    // ================================================================
    private static void applyMage(ServerPlayer player, int level) {
        com.lost_realm.compat.goety.GoetyBridge goety = com.lost_realm.compat.goety.GoetyBridge.get();
        if (goety == null) {
            return;
        }
        int steps = Math.max(0, level - 1);

        double potency = MedalsConfig.MAGE_L1_SPELL_POTENCY.get()
                + MedalsConfig.MAGE_PER_SPELL_POTENCY.get() * steps;
        double castingSpeed = MedalsConfig.pct(MedalsConfig.MAGE_L1_CASTING_SPEED)
                + MedalsConfig.pctD(MedalsConfig.MAGE_PER_CASTING_SPEED) * steps;
        double soulDiscount = MedalsConfig.pct(MedalsConfig.MAGE_L1_SOUL_DISCOUNT)
                + MedalsConfig.pctD(MedalsConfig.MAGE_PER_SOUL_DISCOUNT) * steps;
        double radius = MedalsConfig.pct(MedalsConfig.MAGE_L1_SPELL_RADIUS)
                + MedalsConfig.pctD(MedalsConfig.MAGE_PER_SPELL_RADIUS) * steps;

        // 法术强度:Goety 语义为绝对值,ADDITION
        goety.applySpellPotency(player, ModifierIds.MAGE_SPELL_POTENCY,
                "mage_spell_potency", potency);
        // 施法速度 / 作用半径:比例类,[-1,1] 区间,ADDITION 叠加
        goety.applyCastingSpeed(player, ModifierIds.MAGE_CASTING_SPEED,
                "mage_casting_speed", castingSpeed);
        goety.applySpellRadius(player, ModifierIds.MAGE_SPELL_RADIUS,
                "mage_spell_radius", radius);
        // 灵魂折扣:[-1,1] 区间,直接加法
        goety.applySoulDiscount(player, ModifierIds.MAGE_SOUL_DISCOUNT_BASE,
                "mage_soul_discount_base", soulDiscount);
    }

    // ================================================================
    // 召唤师:Lv.2 仆从生命+5% 伤害+5% 速度+5% 寿命+25%
    //         1~29 级每级 生命+1% 伤害+1% 速度+0.5% 寿命+2.5%
    //
    // 四条都是本模组自注册的属性(ServantAttributes),挂在**玩家**身上;
    // 真正把它们翻译成仆从自身属性的是 SummonerCharmHandler 的周期刷新。
    // 这样做的理由见 ServantAttributes 的类注释。
    // ================================================================
    private static void applySummoner(ServerPlayer player, int level) {
        if (!com.lost_realm.compat.goety.GoetyCompat.isLoaded()) {
            return;
        }
        int steps = Math.max(0, level - 1);

        double health = MedalsConfig.pct(MedalsConfig.SUMMONER_L1_SERVANT_HEALTH)
                + MedalsConfig.pctD(MedalsConfig.SUMMONER_PER_SERVANT_HEALTH) * steps;
        double damage = MedalsConfig.pct(MedalsConfig.SUMMONER_L1_SERVANT_DAMAGE)
                + MedalsConfig.pctD(MedalsConfig.SUMMONER_PER_SERVANT_DAMAGE) * steps;
        double speed = MedalsConfig.pct(MedalsConfig.SUMMONER_L1_SERVANT_SPEED)
                + MedalsConfig.pctD(MedalsConfig.SUMMONER_PER_SERVANT_SPEED) * steps;
        double lifespan = MedalsConfig.pct(MedalsConfig.SUMMONER_L1_SERVANT_LIFESPAN)
                + MedalsConfig.pctD(MedalsConfig.SUMMONER_PER_SERVANT_LIFESPAN) * steps;

        // 四条都是「系数」(0.30 = +30%),基础 0,直接加法叠加
        apply(player, ServantAttributes.SERVANT_HEALTH.get(), ModifierIds.SUMMONER_SERVANT_HEALTH,
                "summoner_servant_health", health, AttributeModifier.Operation.ADDITION);
        apply(player, ServantAttributes.SERVANT_DAMAGE.get(), ModifierIds.SUMMONER_SERVANT_DAMAGE,
                "summoner_servant_damage", damage, AttributeModifier.Operation.ADDITION);
        apply(player, ServantAttributes.SERVANT_SPEED.get(), ModifierIds.SUMMONER_SERVANT_SPEED,
                "summoner_servant_speed", speed, AttributeModifier.Operation.ADDITION);
        apply(player, ServantAttributes.SERVANT_LIFESPAN.get(), ModifierIds.SUMMONER_SERVANT_LIFESPAN,
                "summoner_servant_lifespan", lifespan, AttributeModifier.Operation.ADDITION);
    }

    private MedalRuntime() {}
}