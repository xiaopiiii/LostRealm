package com.lost_realm.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import com.lost_realm.Medals;

/**
 * 全模组的属性修饰符 UUID 常量表。
 *
 * <h2>防叠加保护</h2>
 * 所有 {@link AttributeModifier} 必须分配 <b>固定 UUID</b>。
 * 固定 UUID 让「同名修饰符」在 {@code AttributeInstance} 中被视为同一个修饰符,
 * 重复施加时会被覆盖而不是累加,从而杜绝怪物/技能反复触发导致修饰符无限堆叠、
 * 属性数值指数膨胀最终引发数值溢出与崩溃(StackOverflowError / 数值爆炸)。
 *
 * <p>生成方式使用 {@link UUID#nameUUIDFromBytes(byte[])} 的确定性命名 UUID,
 * 保证每次启动、每个存档、客户端与服务端得到完全相同的 UUID。
 */
public final class ModifierIds {

    // ============================ 射手勋章 基础成长 ============================
    public static final UUID ARCHER_ARROW_DAMAGE    = id("archer_arrow_damage");
    public static final UUID ARCHER_DRAW_SPEED      = id("archer_draw_speed");
    public static final UUID ARCHER_MOVE_SPEED      = id("archer_move_speed");
    public static final UUID ARCHER_DODGE           = id("archer_dodge");

    // ============================ 射手勋章 节点技能 ============================
    public static final UUID ARCHER_S30_MOVE_SPEED      = id("archer_s30_move_speed");
    public static final UUID ARCHER_S30_MOVE_SPEED_MOUNT= id("archer_s30_move_speed_mounted");
    public static final UUID ARCHER_S30_DRAW_SPEED      = id("archer_s30_draw_speed");
    public static final UUID ARCHER_S30_ARROW_DAMAGE    = id("archer_s30_arrow_damage");
    public static final UUID ARCHER_S15_CRIT_DAMAGE     = id("archer_s15_crit_damage");

    // ============================ 战士勋章 基础成长 ============================
    public static final UUID WARRIOR_DAMAGE         = id("warrior_damage");
    public static final UUID WARRIOR_ATTACK_SPEED   = id("warrior_attack_speed");
    public static final UUID WARRIOR_CRIT_CHANCE    = id("warrior_crit_chance");
    public static final UUID WARRIOR_CRIT_DAMAGE    = id("warrior_crit_damage");

    // ============================ 战士勋章 节点技能 ============================
    public static final UUID WARRIOR_W5_BLEED_ARMOR_PIERCE = id("warrior_w5_armor_pierce");
    public static final UUID WARRIOR_W5_BLEED_CRIT_CHANCE  = id("warrior_w5_crit_chance");
    public static final UUID WARRIOR_W10_ARMOR_PIERCE      = id("warrior_w10_armor_pierce");
    public static final UUID WARRIOR_W10_LIFESTEAL         = id("warrior_w10_lifesteal");
    public static final UUID WARRIOR_W15_FIRE_DAMAGE       = id("warrior_w15_fire_damage");
    public static final UUID WARRIOR_W15_ATTACK_DAMAGE     = id("warrior_w15_attack_damage");
    public static final UUID WARRIOR_W15_MOVE_SPEED        = id("warrior_w15_move_speed");
    public static final UUID WARRIOR_W20_COLD_DAMAGE       = id("warrior_w20_cold_damage");
    public static final UUID WARRIOR_W25_ATTACK_RANGE      = id("warrior_w25_attack_range");
    public static final UUID WARRIOR_W25_ATTACK_SPEED      = id("warrior_w25_attack_speed");
    public static final UUID WARRIOR_W25_MOVE_SPEED        = id("warrior_w25_move_speed");
    public static final UUID WARRIOR_W25_LIFESTEAL         = id("warrior_w25_lifesteal");
    public static final UUID WARRIOR_W30_CRIT_CHANCE       = id("warrior_w30_crit_chance");

    // ============================ 「晶石」类法师饰品 ============================
    public static final UUID MAGE_CRYSTAL_CIRCLE_SPEED   = id("mage_crystal_circle_speed");
    public static final UUID MAGE_CRYSTAL_CIRCLE_POTENCY = id("mage_crystal_circle_potency");
    public static final UUID MAGE_CRYSTAL_SWIFT          = id("mage_crystal_swift");
    public static final UUID MAGE_CRYSTAL_SOVEREIGN_POTENCY = id("mage_crystal_sovereign_potency");
    public static final UUID MAGE_CRYSTAL_SOVEREIGN_CASTING = id("mage_crystal_sovereign_casting");
    public static final UUID MAGE_CRYSTAL_SOVEREIGN_RADIUS  = id("mage_crystal_sovereign_radius");
    public static final UUID MAGE_CRYSTAL_SOVEREIGN_SOUL    = id("mage_crystal_sovereign_soul");

    // ============================ 「盾」类坦克饰品 ============================
    public static final UUID TANK_SHIELD_DEVOUR_HP          = id("tank_shield_devour_hp");
    public static final UUID TANK_SHIELD_VITALITY_HEAL      = id("tank_shield_vitality_heal");
    public static final UUID TANK_SHIELD_VANGUARD_ARMOR     = id("tank_shield_vanguard_armor");
    public static final UUID TANK_SHIELD_VANGUARD_SPEED     = id("tank_shield_vanguard_speed");
    public static final UUID TANK_SHIELD_EARTH_REDUCTION    = id("tank_shield_earth_reduction");
    public static final UUID TANK_SHIELD_EARTH_KNOCKBACK    = id("tank_shield_earth_knockback");
    public static final UUID TANK_SHIELD_ETERNAL_ARMOR      = id("tank_shield_eternal_armor");
    public static final UUID TANK_SHIELD_ETERNAL_HEALTH     = id("tank_shield_eternal_health");
    public static final UUID TANK_SHIELD_ETERNAL_TOUGHNESS  = id("tank_shield_eternal_toughness");
    public static final UUID TANK_SHIELD_ETERNAL_SPEED      = id("tank_shield_eternal_speed");
    public static final UUID TANK_SHIELD_ETERNAL_BLOCKSPEED = id("tank_shield_eternal_blockspeed");
    public static final UUID TANK_SHIELD_ETERNAL_BULWARK_ARMOR = id("tank_shield_eternal_bulwark_armor");
    public static final UUID TANK_SHIELD_ETERNAL_BULWARK_DR    = id("tank_shield_eternal_bulwark_dr");

    // ============================ 「刃」类战士饰品(dv49) ============================
    public static final UUID WARRIOR_CHARM_DUAL_DAMAGE       = id("warrior_charm_dual_damage");
    public static final UUID WARRIOR_CHARM_DUAL_ATTACK_SPEED = id("warrior_charm_dual_attack_speed");
    public static final UUID WARRIOR_CHARM_CRIT_CHANCE       = id("warrior_charm_crit_chance");
    public static final UUID WARRIOR_CHARM_CRIT_DAMAGE       = id("warrior_charm_crit_damage");

    // ============================ 坦克勋章 基础成长 ============================
    public static final UUID TANK_ARMOR             = id("tank_armor");
    public static final UUID TANK_MAX_HEALTH        = id("tank_max_health");
    public static final UUID TANK_OVERHEAL          = id("tank_overheal");
    public static final UUID TANK_DAMAGE_REDUCTION  = id("tank_damage_reduction");

    // ============================ 坦克勋章 节点技能 ============================
    public static final UUID TANK_T5_DAMAGE          = id("tank_t5_damage");
    public static final UUID TANK_T5_CRIT_CHANCE     = id("tank_t5_crit_chance");
    public static final UUID TANK_T5_CRIT_DAMAGE     = id("tank_t5_crit_damage");
    public static final UUID TANK_T10_ABSORB         = id("tank_t10_absorb");
    public static final UUID TANK_T20_ARMOR          = id("tank_t20_armor");
    public static final UUID TANK_T20_DAMAGE_REDUCTION = id("tank_t20_damage_reduction");
    public static final UUID TANK_T20_KNOCKBACK_RESIST = id("tank_t20_knockback_resist");
    public static final UUID TANK_T30_MAX_HEALTH     = id("tank_t30_max_health");
    public static final UUID TANK_T30_ABSORB         = id("tank_t30_absorb");
    public static final UUID TANK_T30_ARMOR          = id("tank_t30_armor");

    // ==================== 法师勋章(MAGE / Goety 底座) ====================
    /** Lv.2 基础:法术强度 */
    public static final UUID MAGE_SPELL_POTENCY      = id("mage_spell_potency");
    /** Lv.2 基础:施法速度 */
    public static final UUID MAGE_CASTING_SPEED      = id("mage_casting_speed");
    /** Lv.2 基础:灵魂消耗折扣 */
    public static final UUID MAGE_SOUL_DISCOUNT_BASE = id("mage_soul_discount_base");
    /** Lv.2 基础:法术作用半径 */
    public static final UUID MAGE_SPELL_RADIUS       = id("mage_spell_radius");

    // -------- Lv.10【元素共鸣】--------
    /** 每层法术强度 */
    public static final UUID MAGE_M5_RESONANCE_POTENCY     = id("mage_m5_resonance_potency");
    /** 每层灵魂消耗折扣 */
    public static final UUID MAGE_M5_RESONANCE_SOUL_DISCOUNT = id("mage_m5_resonance_soul_discount");

    // -------- Lv.20【法爆】--------
    /** 法术暴击率 */
    public static final UUID MAGE_M10_SPELL_CRIT_CHANCE = id("mage_m10_spell_crit_chance");
    /** 法术暴击伤害 */
    public static final UUID MAGE_M10_SPELL_CRIT_DAMAGE = id("mage_m10_spell_crit_damage");

    // -------- Lv.30【灵魂虹吸】--------
    /** 常驻:法术持续时间 */
    public static final UUID MAGE_M15_SPELL_DURATION = id("mage_m15_spell_duration");

    // -------- Lv.40【巫术过载】--------
    public static final UUID MAGE_M20_CASTING_SPEED      = id("mage_m20_casting_speed");
    public static final UUID MAGE_M20_SOUL_DISCOUNT      = id("mage_m20_soul_discount");
    public static final UUID MAGE_M20_SPELL_POTENCY      = id("mage_m20_spell_potency");
    public static final UUID MAGE_M20_COOLDOWN_DISCOUNT  = id("mage_m20_cooldown_discount");
    public static final UUID MAGE_M20_SPELL_RADIUS       = id("mage_m20_spell_radius");
    public static final UUID MAGE_M20_MOVE_SPEED         = id("mage_m20_move_speed");

    // -------- Lv.50【灵魂燃烧】--------
    public static final UUID MAGE_M25_SPELL_POTENCY      = id("mage_m25_spell_potency");
    public static final UUID MAGE_M25_CASTING_SPEED      = id("mage_m25_casting_speed");
    public static final UUID MAGE_M25_SPELL_CRIT_CHANCE  = id("mage_m25_spell_crit_chance");

    // -------- Lv.60【弹幕风暴】--------
    /** 常驻:法术作用半径 */
    public static final UUID MAGE_M30_SPELL_RADIUS       = id("mage_m30_spell_radius");
    /** 三连发待发时的法术强度补偿(非投射物法术降级用) */
    public static final UUID MAGE_M30_BARRAGE_POTENCY    = id("mage_m30_barrage_potency");

    // ==================== 召唤师勋章 基础成长 ====================
    // 这四条挂在**玩家**身上,表示「主人的仆从加成」;
    // 翻译成仆从自身属性的是 SummonerCharmHandler 的周期刷新。
    public static final UUID SUMMONER_SERVANT_HEALTH   = id("summoner_servant_health");
    public static final UUID SUMMONER_SERVANT_DAMAGE   = id("summoner_servant_damage");
    public static final UUID SUMMONER_SERVANT_SPEED    = id("summoner_servant_speed");
    public static final UUID SUMMONER_SERVANT_LIFESPAN = id("summoner_servant_lifespan");

    // ==================== 召唤师 节点技能 ====================
    // 下面这些是**施加到仆从身上**的:每个仆从各自持有独立的属性实例,
    // 因此固定 UUID 不会互相覆盖(不同仆从互不影响),又天然幂等。
    /** Lv.20【骸骨硬化】仆从减伤 */
    public static final UUID SUMMONER_S10_REDUCTION = id("summoner_s10_reduction");
    /** Lv.40【军团号令】期间仆从伤害加成 */
    public static final UUID SUMMONER_S20_DAMAGE    = id("summoner_s20_damage");
    /** Lv.40【军团号令】期间仆从速度加成 */
    public static final UUID SUMMONER_S20_SPEED     = id("summoner_s20_speed");
    /** Lv.60【亡者大军】召唤法术冷却折扣(挂在玩家身上) */
    public static final UUID SUMMONER_S30_COOLDOWN  = id("summoner_s30_cooldown");
    /** Lv.60【亡者大军】仆从生命加成(施加到仆从) */
    public static final UUID SUMMONER_S30_HEALTH    = id("summoner_s30_health");

    // ==================== 「契约」类召唤师饰品 ====================
    /** 魂链之契:按存活仆从数量给玩家的法术强度 */
    public static final UUID SUMMONER_C_SOULCHAIN        = id("summoner_c_soulchain");
    /** 骨甲之契:按存活仆从数量给玩家的伤害减免 */
    public static final UUID SUMMONER_C_BONE             = id("summoner_c_bone");
    /** 王者之主:仆从生命 */
    public static final UUID SUMMONER_C_OVERLORD_HEALTH  = id("summoner_c_overlord_health");
    /** 王者之主:仆从伤害 */
    public static final UUID SUMMONER_C_OVERLORD_DAMAGE  = id("summoner_c_overlord_damage");
    /** 王者之主:仆从速度 */
    public static final UUID SUMMONER_C_OVERLORD_SPEED   = id("summoner_c_overlord_speed");
    /** 裂变之契:复制出来的仆从「血量只有一半」的负向修饰符 */
    public static final UUID SUMMONER_C_FISSION_HP       = id("summoner_c_fission_hp");

    // ==================== 战士终极「我有一剑！」====================
    // 这四条挂在**物品自己**的默认属性修饰符里(MAINHAND),随装备自动生效/失效,
    // 因此不需要像徽章属性那样由技能每 tick 刷新 —— 但 UUID 仍必须固定。
    /** 暴击率 +100% */
    public static final UUID SWORD_CRIT_CHANCE   = id("i_have_a_sword_crit_chance");
    /** 暴击伤害 +100% */
    public static final UUID SWORD_CRIT_DAMAGE   = id("i_have_a_sword_crit_damage");
    /** 攻击伤害 +100% */
    public static final UUID SWORD_ATTACK_DAMAGE = id("i_have_a_sword_attack_damage");
    /** 经验获取 +1000% */
    public static final UUID SWORD_EXP_GAINED    = id("i_have_a_sword_exp_gained");

    // ==================== 体验改进(QoL)· 鞘翅属性 ====================
    /** 修改原版鞘翅:额外护甲值。 */
    public static final UUID QOL_ELYTRA_ARMOR = id("qol_elytra_armor");
    /** 修改原版鞘翅:额外盔甲韧性。 */
    public static final UUID QOL_ELYTRA_TOUGHNESS = id("qol_elytra_toughness");

    /**
     * 基于命名空间与路径生成确定性 UUID。
     *
     * @param path 稳定的语义化路径,例如 {@code "archer_arrow_damage"}
     */
    public static UUID id(String path) {
        return UUID.nameUUIDFromBytes((Medals.MODID + ":" + path).getBytes(StandardCharsets.UTF_8));
    }

    /** 由任意资源定位符生成确定性 UUID(用于按物品/来源区分的修饰符)。 */
    public static UUID id(ResourceLocation loc) {
        return id(loc.getNamespace() + "/" + loc.getPath());
    }

    /**
     * 构建固定 UUID 的修饰符。
     *
     * @param uuid  固定 UUID(必须来自本类常量表)
     * @param name  修饰符名称(仅用于展示)
     */
    public static AttributeModifier mod(UUID uuid, String name, double amount, AttributeModifier.Operation op) {
        return new AttributeModifier(uuid, Medals.MODID + ":" + name, amount, op);
    }

    private ModifierIds() {}
}
