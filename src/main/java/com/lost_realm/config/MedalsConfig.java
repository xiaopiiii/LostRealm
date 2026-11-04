package com.lost_realm.config;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;

/**
 * 模组配置文件。
 *
 * <h2>单位约定(重要)</h2>
 * <b>所有百分比数值在配置文件中一律写成整数</b>(例如玩家写 {@code 20} 表示 20%)。
 * 代码读取后通过 {@link #pct(IntValue)} 除以 100 转换为小数系数再参与运算,
 * 从而避免浮点配置在屏幕上出现难以阅读的 {@code 0.2000000001}。
 *
 * <p>仅「非百分比」的物理量(如秒数、格数、数量、角度、经验基础值)使用浮点或整数原值。
 */
public final class MedalsConfig {

    // ==================== 静态字段引用(外部通过 MedalsConfig.XXX 访问) ====================
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    public static IntValue MAX_LEVEL;
    public static IntValue BASE_EXP;
    public static IntValue EXP_GROWTH_1_10;
    public static IntValue EXP_GROWTH_11_20;
    public static IntValue EXP_GROWTH_21_30;
    public static IntValue EXP_GROWTH_31_40;
    public static IntValue EXP_GROWTH_41_50;
    public static IntValue EXP_GROWTH_51_60;
    public static IntValue MAX_MODIFIERS_PER_UUID;
    public static IntValue ARCHER_EXP_DAMAGE;
    public static IntValue ARCHER_EXP_KILL_HP;
    public static IntValue ARCHER_L1_ARROW_DMG;
    public static IntValue ARCHER_L1_DRAW_SPEED;
    public static IntValue ARCHER_L1_MOVE_SPEED;
    public static IntValue ARCHER_L1_DODGE;
    public static DoubleValue ARCHER_PER_ARROW_DMG;
    public static DoubleValue ARCHER_PER_DRAW_SPEED;
    public static DoubleValue ARCHER_PER_MOVE_SPEED;
    public static DoubleValue ARCHER_PER_DODGE;
    public static IntValue S5_DODGE_IMMUNE_CHANCE;
    public static IntValue S5_TELEPORT_RANGE;
    public static IntValue S5_TELEPORT_MIN_HEIGHT;
    public static IntValue S5_COOLDOWN_SECONDS;
    public static DoubleValue S10_DAMAGE_PER_1_25_BLOCK;
    public static IntValue S15_NEAR_CHANCE;
    public static IntValue S15_CHANCE_DROP_PER_BLOCK;
    public static IntValue S15_MIN_CHANCE;
    public static IntValue S15_NEAR_DISTANCE;
    public static IntValue S20_SPREAD_MIN_DEG;
    public static IntValue S20_SPREAD_MAX_DEG;
    public static IntValue S20_EXTRA_ARROW_DAMAGE;
    public static IntValue S20_MULTISHOT_BONUS;
    public static IntValue S25_RAIN_CHANCE;
    public static IntValue S25_RAIN_HEIGHT;
    public static IntValue S25_RAIN_AREA;
    public static IntValue S25_RAIN_MIN_ARROWS;
    public static IntValue S25_RAIN_MAX_ARROWS;
    public static IntValue S30_DURATION_SECONDS;
    public static IntValue S30_MOVE_SPEED;
    public static IntValue S30_MOVE_SPEED_MOUNTED;
    public static IntValue S30_DRAW_SPEED;
    public static IntValue S30_ARROW_DAMAGE;
    public static IntValue S30_MAX_EXTEND;
    public static IntValue S30_COOLDOWN_SECONDS;
    public static IntValue WARRIOR_EXP_DAMAGE;
    public static IntValue WARRIOR_EXP_KILL_HP;
    public static IntValue WARRIOR_L1_DAMAGE;
    public static IntValue WARRIOR_L1_ATTACK_SPEED;
    public static IntValue WARRIOR_L1_CRIT_CHANCE;
    public static IntValue WARRIOR_L1_CRIT_DAMAGE;
    public static DoubleValue WARRIOR_PER_DAMAGE;
    public static DoubleValue WARRIOR_PER_ATTACK_SPEED;
    public static DoubleValue WARRIOR_PER_CRIT_CHANCE;
    public static DoubleValue WARRIOR_PER_CRIT_DAMAGE;
    public static IntValue W5_BLEED_CHANCE;
    public static IntValue W5_BLEED_SECONDS;
    public static IntValue W5_BLEED_ARMOR_PIERCE;
    public static IntValue W5_BLEED_CRIT_CHANCE;
    public static IntValue W10_HP_THRESHOLD;
    public static IntValue W10_ARMOR_PIERCE_PER_STEP;
    public static IntValue W10_LIFESTEAL_PER_STEP;
    public static IntValue W15_DURATION_SECONDS;
    public static IntValue W15_HP_COST_PER_SECOND;
    public static IntValue W15_FIRE_DAMAGE;
    public static IntValue W15_TARGET_FIRE_SECONDS;
    public static IntValue W15_ATTACK_DAMAGE;
    public static IntValue W15_MOVE_SPEED;
    public static IntValue W20_COLD_DAMAGE;
    public static IntValue W20_FREEZE_CHANCE;
    public static IntValue W20_FREEZE_SECONDS;
    public static IntValue W25_DURATION_SECONDS;
    public static IntValue W25_ATTACK_RANGE;
    public static IntValue W25_ATTACK_SPEED;
    public static IntValue W25_MOVE_SPEED;
    public static IntValue W25_LIFESTEAL;
    public static IntValue W25_COOLDOWN_SECONDS;
    public static IntValue W30_LOST_HP_DAMAGE;
    public static IntValue W30_LOST_HP_DAMAGE_LOW;
    public static IntValue W30_LOW_HP_THRESHOLD;
    public static IntValue W30_LOW_HP_CRIT_CHANCE;
    public static IntValue TANK_EXP_DAMAGE;
    public static IntValue TANK_EXP_KILL_HP;
    public static IntValue TANK_EXP_HEAL;
    public static IntValue TANK_L1_ARMOR;
    public static IntValue TANK_L1_MAX_HEALTH;
    public static IntValue TANK_L1_OVERHEAL;
    public static IntValue TANK_L1_DAMAGE_REDUCTION;
    public static DoubleValue TANK_PER_ARMOR;
    public static DoubleValue TANK_PER_MAX_HEALTH;
    public static DoubleValue TANK_PER_OVERHEAL;
    public static DoubleValue TANK_PER_DAMAGE_REDUCTION;
    public static DoubleValue T5_SLOW_WEAPON_ATTACK_SPEED;
    public static IntValue T5_DAMAGE;
    public static IntValue T5_CRIT_CHANCE;
    public static IntValue T5_CRIT_DAMAGE;
    public static IntValue T10_ABSORB_CHANCE;
    public static IntValue T10_ABSORB_AMOUNT;
    public static IntValue T10_INTERVAL_SECONDS;
    public static IntValue T15_SHIELD_INTERVAL;
    public static IntValue T15_SHIELD_MAX_BLOCK;
    public static IntValue T15_SHIELD_MAX_STACKS;
    public static IntValue T20_DURATION_SECONDS;
    public static IntValue T20_ARMOR_BONUS;
    public static IntValue T20_DAMAGE_REDUCTION;
    public static IntValue T20_KNOCKBACK_RADIUS;
    public static IntValue T20_KNOCKBACK_DISTANCE;
    public static IntValue T20_BURST_DAMAGE;
    public static IntValue T20_SLOWNESS_SECONDS;
    public static IntValue T20_COOLDOWN_SECONDS;
    public static IntValue T25_BLOCK_CHANCE;
    public static IntValue T25_BLOCK_VALUE;
    public static IntValue T25_INTERVAL_SECONDS;
    public static DoubleValue T25_BLOCK_PER_VALUE;
    public static IntValue T25_BLOCK_CAP;
    public static IntValue T25_RESET_SECONDS;
    public static IntValue T30_HP_THRESHOLD;
    public static IntValue T30_DURATION_SECONDS;
    public static IntValue T30_HEAL_PER_SECOND;
    public static IntValue T30_ABSORB;
    public static IntValue T30_COOLDOWN_SECONDS;

    // ---------------- 法师勋章(MAGE / Goety 底座) ----------------
    public static IntValue MAGE_EXP_SPELL_DAMAGE;
    public static IntValue MAGE_EXP_KILL_HP;
    public static IntValue MAGE_EXP_SOUL;
    public static IntValue MAGE_L1_SPELL_POTENCY;
    public static IntValue MAGE_L1_CASTING_SPEED;
    public static IntValue MAGE_L1_SOUL_DISCOUNT;
    public static IntValue MAGE_L1_SPELL_RADIUS;
    public static DoubleValue MAGE_PER_SPELL_POTENCY;
    public static DoubleValue MAGE_PER_CASTING_SPEED;
    public static DoubleValue MAGE_PER_SOUL_DISCOUNT;
    public static DoubleValue MAGE_PER_SPELL_RADIUS;

    // Lv.10【元素共鸣】
    public static IntValue M5_MAX_STACKS;
    public static IntValue M5_WINDOW_SECONDS;
    public static DoubleValue M5_POTENCY_PER_STACK;
    public static DoubleValue M5_SOUL_DISCOUNT_PER_STACK;

    // Lv.20【法爆】
    public static IntValue M10_BASE_CRIT_CHANCE;
    public static IntValue M10_BASE_CRIT_DAMAGE;

    // Lv.30【灵魂虹吸】
    public static IntValue M15_REFUND_CHANCE;
    public static IntValue M15_REFUND_RATIO;
    public static IntValue M15_DEBUFF_REFUND_CHANCE;
    public static IntValue M15_DEBUFF_REFUND_RATIO;
    public static IntValue M15_SPELL_DURATION;

    // Lv.40【巫术过载】
    public static IntValue M20_DURATION_SECONDS;
    public static IntValue M20_CASTING_SPEED;
    public static IntValue M20_SOUL_DISCOUNT;
    public static IntValue M20_SPELL_POTENCY;
    public static IntValue M20_COOLDOWN_DISCOUNT;
    public static IntValue M20_SPELL_RADIUS;
    public static IntValue M20_MOVE_SPEED;
    public static IntValue M20_COOLDOWN_SECONDS;

    // Lv.50【灵魂燃烧】
    public static IntValue M25_SOUL_PER_SECOND;
    public static IntValue M25_STACK_INTERVAL_SECONDS;
    public static IntValue M25_MAX_STACKS;
    public static IntValue M25_POTENCY_PER_STACK;
    public static IntValue M25_CASTING_SPEED_PER_STACK;
    public static IntValue M25_CRIT_CHANCE_PER_STACK;
    public static IntValue M25_COOLDOWN_SECONDS;

    // Lv.60【弹幕风暴】
    public static IntValue M30_REFRESH_CHANCE;
    public static IntValue M30_PITY_INCREMENT;
    public static IntValue M30_BARRAGE_POTENCY;
    public static IntValue M30_BARRAGE_ANGLE;
    public static IntValue M30_SPELL_RADIUS;

    // ---------------- 召唤师勋章(SUMMONER / 同样以 Goety 为底座) ----------------
    // 与法师勋章的关注点互补:法师改造法术,召唤师改造仆从。
    public static IntValue SUMMONER_EXP_SERVANT_DAMAGE;
    public static IntValue SUMMONER_EXP_KILL_HP;
    public static IntValue SUMMONER_L1_SERVANT_HEALTH;
    public static IntValue SUMMONER_L1_SERVANT_DAMAGE;
    public static IntValue SUMMONER_L1_SERVANT_SPEED;
    public static IntValue SUMMONER_L1_SERVANT_LIFESPAN;
    public static DoubleValue SUMMONER_PER_SERVANT_HEALTH;
    public static DoubleValue SUMMONER_PER_SERVANT_DAMAGE;
    public static DoubleValue SUMMONER_PER_SERVANT_SPEED;
    public static DoubleValue SUMMONER_PER_SERVANT_LIFESPAN;

    // Lv.10【灵魂纽带】
    public static IntValue S5_SOUL_PER_HIT;
    public static IntValue S5_SOUL_COOLDOWN;

    // Lv.20【骸骨硬化】
    public static IntValue S10_SERVANT_REDUCTION;

    // Lv.30【亡者复苏】
    public static IntValue S15_REVIVE_CHANCE;
    public static IntValue S15_REVIVE_HEALTH;

    // Lv.40【军团号令】
    public static IntValue S20_DURATION_SECONDS;
    public static IntValue S20_SERVANT_DAMAGE;
    public static IntValue S20_SERVANT_LIMIT;
    public static IntValue S20_COOLDOWN_SECONDS;

    // Lv.50【魂火】
    public static DoubleValue S25_EXTRA_DAMAGE;

    // Lv.60【亡者大军】
    public static IntValue S30_SERVANT_LIMIT;
    public static IntValue S30_COOLDOWN_DISCOUNT;
    public static IntValue S30_SERVANT_HEALTH;

    // ---------------- 「契约」类召唤师饰品 ----------------
    // 九枚 XX之契 + 终极「王者之主」。
    public static IntValue CONTRACT_DOMINION_HEALTH;
    public static IntValue CONTRACT_DOMINION_DAMAGE;
    public static IntValue CONTRACT_DOMINION_SOUL;
    public static IntValue CONTRACT_LEGION_LIMIT;
    public static IntValue CONTRACT_LEGION_LIFE;
    public static IntValue CONTRACT_SYMBIOSIS_RATIO;
    public static DoubleValue CONTRACT_SYMBIOSIS_RADIUS;
    public static DoubleValue CONTRACT_MARTYR_RADIUS;
    public static IntValue CONTRACT_MARTYR_RATIO;
    public static IntValue CONTRACT_SOULCHAIN_POTENCY;
    public static IntValue CONTRACT_SOULCHAIN_MAX;
    public static IntValue CONTRACT_FLESH_RATIO;
    public static IntValue CONTRACT_BONE_REDUCTION;
    public static IntValue CONTRACT_BONE_MAX;
    public static IntValue CONTRACT_FISSION_CHANCE;
    public static IntValue CONTRACT_FISSION_RATIO;
    public static IntValue CONTRACT_DECREE_VULNERABLE;
    public static IntValue CONTRACT_DECREE_SECONDS;
    public static IntValue CONTRACT_OVERLORD_HEALTH;
    public static IntValue CONTRACT_OVERLORD_DAMAGE;
    public static IntValue CONTRACT_OVERLORD_SPEED;
    public static IntValue CONTRACT_OVERLORD_LIMIT;
    public static IntValue CONTRACT_REFRESH_INTERVAL;

    // ---------------- 战士终极「我有一剑！」 ----------------
    // 这是一把剑(不是饰品):常驻属性挂在 STR 物品的 MAINHAND 修饰符上,
    // 主动技是按住 Shift + 右键蓄力 10 秒后的一道巨型剑气。
    public static IntValue SWORD_CRIT_CHANCE;
    public static IntValue SWORD_CRIT_DAMAGE;
    public static IntValue SWORD_ATTACK_DAMAGE;
    public static IntValue SWORD_EXP_GAINED;
    public static IntValue SWORD_DAMAGE;
    public static IntValue SWORD_CHARGE_SECONDS;
    public static IntValue SWORD_COOLDOWN_SECONDS;
    public static DoubleValue SWORD_REACH;
    public static DoubleValue SWORD_WIDTH;
    public static DoubleValue SWORD_HEIGHT;
    public static IntValue SWORD_ANIM_TICKS;
    public static BooleanValue SWORD_BREAK_BLOCKS;
    public static BooleanValue SWORD_HIT_PLAYERS;

    // 注:原 SHOW_HUD / HUD_X / HUD_Y 已移除。
    // 熟练度不再走屏幕左上角的浮层,而是直接写在勋章物品的 tooltip 里
    // (见 ItemMedal#appendHoverText),操作上更直观、也不占屏幕。

    // ---- 更好的状态效果 HUD ----
    public static BooleanValue EFFECT_HUD_ENABLED;
    public static IntValue EFFECT_HUD_SCALE;
    public static IntValue EFFECT_HUD_CORNER;
    public static IntValue EFFECT_HUD_FLOW;
    public static IntValue EFFECT_HUD_TIMER;
    /** 是否给减益(负面)效果加红色外框。 */
    public static BooleanValue EFFECT_HUD_HARMFUL_BORDER;

    // ================================================================
    // 体验改进(QoL)—— 与原版玩家体验相关的一系列可选增强
    // ----------------------------------------------------------------
    // 所有功能默认开启(true);关闭后完全回退原版行为。
    // 需求写明「不可调节」的数值(凋零头 15%、交易 64 次)不在此暴露,
    // 仅保留总开关,数值定义于 com.lost_realm.qol.QolConstants。
    // ================================================================
    /** A1 药水无限续杯总开关。 */
    public static BooleanValue QOL_POTION_INFINITE;
    /** A1 是否同时扫描精妙背包内的药水。 */
    public static BooleanValue QOL_POTION_SCAN_BACKPACK;
    /** A2 无限 buff 共享给附近玩家总开关。 */
    public static BooleanValue QOL_BUFF_SHARE;
    /** A2 共享半径(格,最大 32)。 */
    public static IntValue QOL_BUFF_SHARE_RADIUS;
    /** B3 不死图腾在背包中亦可生效总开关。 */
    public static BooleanValue QOL_TOTEM_BACKPACK;
    /** B4 鞘翅加属性总开关。 */
    public static BooleanValue QOL_ELYTRA_ARMOR;
    /** B4 鞘翅额外护甲值。 */
    public static IntValue QOL_ELYTRA_ARMOR_VALUE;
    /** B4 鞘翅额外盔甲韧性。 */
    public static IntValue QOL_ELYTRA_TOUGHNESS_VALUE;
    /** B5 死亡后保留 Buff 总开关。 */
    public static BooleanValue QOL_KEEP_BUFFS_ON_DEATH;
    /** C6 任意时间均可睡觉总开关。 */
    public static BooleanValue QOL_SLEEP_ANYTIME;
    /** C7 每次睡觉跳过的小时数。 */
    public static IntValue QOL_SLEEP_SKIP_HOURS;
    /** C8 联机时一人睡觉即可跳过时间总开关。 */
    public static BooleanValue QOL_ONE_PLAYER_SLEEP;
    /** D9 耕地保护总开关。 */
    public static BooleanValue QOL_FARMLAND_PROTECT;
    /** D10 饱食度满仍可进食总开关。 */
    public static BooleanValue QOL_EAT_WHEN_FULL;
    /** D11 凋零骷髅头 15% 掉落总开关(概率不可调节)。 */
    public static BooleanValue QOL_WITHER_SKULL;
    /** D12 死亡掉落物集中总开关。 */
    public static BooleanValue QOL_DEATH_DROP_CENTERED;
    /** E13 自动攻击总开关。 */
    public static BooleanValue QOL_AUTO_ATTACK;
    /** E13 自动攻击是否要求准星指向目标。 */
    public static BooleanValue QOL_AUTO_ATTACK_REQUIRE_TARGET;
    /** E14 自动射击总开关。 */
    public static BooleanValue QOL_AUTO_SHOOT;
    /** F15 村民交易次数 64 总开关(次数不可调节)。 */
    public static BooleanValue QOL_VILLAGER_TRADES_64;
    /** F16 村民交易刷新按钮总开关。 */
    public static BooleanValue QOL_REFRESH_TRADES;
    /** F17 附魔台刷新按钮总开关。 */
    public static BooleanValue QOL_REFRESH_ENCHANT;
    /** F17 刷新附魔消耗的经验等级。 */
    public static IntValue QOL_REFRESH_ENCHANT_COST_LEVELS;
    /** F17 刷新附魔消耗的青金石数量。 */
    public static IntValue QOL_REFRESH_ENCHANT_COST_LAPIS;
    /** F18 附魔台保留青金石总开关。 */
    public static BooleanValue QOL_KEEP_LAPIS;
    /** F19 无限与经验修补不再冲突总开关。 */
    public static BooleanValue QOL_INFINITY_MENDING;
    /** F20 附魔台预览全部附魔总开关。 */
    public static BooleanValue QOL_ENCHANT_PREVIEW_ALL;

    // ---- 体验改进 · 堆叠类(G 组) ----
    /** G21 药水可堆叠至 16 总开关。 */
    public static BooleanValue QOL_STACK_POTION;
    /** G22 不死图腾可堆叠至 16 总开关。 */
    public static BooleanValue QOL_STACK_TOTEM;
    /** G23 末影珍珠可堆叠至 64 总开关。 */
    public static BooleanValue QOL_STACK_ENDER_PEARL;
    /** G24 无限附魔的弓缺箭亦可射击总开关。 */
    public static BooleanValue QOL_INFINITE_BOW_NO_ARROW;

    // ================================================================
    // 终末之影(Boss)—— 静态引用
    // ================================================================
    /** Boss 总开关。 */
    public static BooleanValue BOSS_ENABLED;
    /** Boss 最大生命值。 */
    public static DoubleValue BOSS_MAX_HEALTH;
    /** 阶段Ⅱ血量阈值(%)。 */
    public static IntValue BOSS_PHASE_2_PERCENT;
    /** 阶段Ⅲ血量阈值(%)。 */
    public static IntValue BOSS_PHASE_3_PERCENT;
    /** 终末审判基础读条秒数。 */
    public static IntValue BOSS_JUDGEMENT_SECONDS;
    /** 仆从数量上限。 */
    public static IntValue BOSS_SENTINEL_LIMIT;
    /** 任务链要求的勋章等级。 */
    public static IntValue BOSS_MEDAL_REQUIREMENT;

    // ---- dv5:遗落之主新流程 ----
    /** 波数(1~10)。 */
    public static IntValue BOSS_WAVE_COUNT;
    /** 每波怪物数量(1~20)。 */
    public static IntValue BOSS_WAVE_MOB_COUNT;
    /** 波间隔秒数(1~60)。 */
    public static IntValue BOSS_WAVE_INTERVAL_SECONDS;
    /** 波次怪物种类(0 僵尸 / 1 骷髅 / 2 蜘蛛 / 3 掠夺者 / 4 凋灵骷髅)。 */
    public static IntValue BOSS_WAVE_MOB_TYPE;
    /** 决斗一击扣除玩家最大生命值的百分比(1~100)。 */
    public static IntValue BOSS_DUEL_HIT_PERCENT;
    /** 每次锁血后回满的耗时秒数(1~30)。 */
    public static IntValue BOSS_TRANSITION_HEAL_SECONDS;

    // ================================================================
    // 体验改进 · 堆叠类(G 组)—— 数值常量
    // ----------------------------------------------------------------
    // 需求写「最大 16 / 改为 64」,是固定值而非可调区间,因此不暴露具体数字,
    // 只保留总开关。此处定义为常量,便于日后统一调整。
    // ================================================================
    /** 药水 / 不死图腾的堆叠上限。 */
    public static final int STACK_POTION_MAX = 16;
    /** 不死图腾的堆叠上限。 */
    public static final int STACK_TOTEM_MAX = 16;
    /** 末影珍珠的堆叠上限。 */
    public static final int STACK_ENDER_PEARL_MAX = 64;

    // ================================================================
    // 体验改进 · 第 1 项:原版经验等级大改
    // ----------------------------------------------------------------
    // 注意:本组配置作用于【Minecraft 原版玩家经验等级】(屏幕下方绿条 /
    // 附魔台消耗的那个),与勋章熟练度(ExpTable,Lv.2~30)完全无关,是两套独立系统。
    // ================================================================
    /** 总开关:false 时完全回退原版经验公式。 */
    public static BooleanValue VX_ENABLED;
    /** 曲线起点:从 0 级升到 1 级所需经验(原版为 7)。 */
    public static IntValue VX_BASE_EXP;
    /** 1~40 级增长率(整数百分比)。10 = +10% */
    public static IntValue VX_GROWTH_1_40;
    /** 40~70 级增长率(整数百分比)。8 = +8% */
    public static IntValue VX_GROWTH_40_70;
    /** 70~100 级增长率(整数百分比)。4 = +4% */
    public static IntValue VX_GROWTH_70_100;
    /** 101 级及以上的基础增长率(整数百分比)。2 = +2% */
    public static IntValue VX_GROWTH_AFTER_100;
    /** 每跨越一个台阶(默认 25 级)时,增长率乘以的百分比。95 = ×0.95 */
    public static IntValue VX_DECAY_PERCENT;
    /** 递减台阶跨度(级)。25 表示 125/150/175… */
    public static IntValue VX_DECAY_STEP;
    /** 增长率下限,放大 1000 倍存储:5 表示 0.005%(低于此值后固定不再变)。 */
    public static IntValue VX_MIN_GROWTH_MILLI;

    // ---------- 箭类饰品与时间胶囊(dv42) ----------
    public static IntValue ARROW_POTION_CHANCE;
    public static IntValue ARROW_POTION_SECONDS;
    public static DoubleValue ARROW_EXPLOSION_POWER;
    public static IntValue ARROW_PIERCE_COUNT;
    public static IntValue ARROW_PIERCE_FALLOFF;
    public static IntValue ARROW_NO_GRAVITY_SECONDS;
    public static DoubleValue ARROW_THUNDER_DAMAGE;
    public static IntValue ARROW_THUNDER_RADIUS;
    public static IntValue ARROW_THUNDER_PARALYZE_SECONDS;
    public static IntValue ARROW_TWIN_CHANCE;
    public static IntValue ARROW_TWIN_DAMAGE;
    public static IntValue ARROW_ARMOR_PIERCE;
    public static IntValue ARROW_HOLD_CHANCE;
    public static IntValue ARROW_HOLD_SECONDS;
    public static IntValue ARROW_SUMMON_MAX;
    public static IntValue ARROW_SUMMON_COOLDOWN_SECONDS;
    public static DoubleValue ARROW_SUMMON_HEALTH;
    public static DoubleValue ARROW_STAR_DAMAGE;
    public static IntValue ARROW_STAR_CHARGE_SECONDS;
    public static IntValue ARROW_STAR_PIERCE;
    public static IntValue ARROW_STAR_FLIGHT_SECONDS;
    public static DoubleValue ARROW_STAR_BREAK_RADIUS;
    public static BooleanValue ARROW_STAR_BREAK_BLOCKS;
    public static BooleanValue ARROW_STAR_BLOCK_DROPS;
    // ---------- 「刃」类战士饰品(dv49) ----------
    public static DoubleValue WARRIOR_SWEEP_RADIUS;
    public static IntValue WARRIOR_DUAL_DAMAGE;
    public static IntValue WARRIOR_DUAL_ATTACK_SPEED;
    public static IntValue WARRIOR_CRIT_CHANCE;
    public static IntValue WARRIOR_CRIT_DAMAGE;
    public static IntValue WARRIOR_BACKSTAB_BONUS;
    public static IntValue WARRIOR_DISARM_CHANCE;
    public static IntValue WARRIOR_DISARM_SECONDS;
    public static IntValue WARRIOR_DISARM_COOLDOWN;
    public static IntValue WARRIOR_LUCK_RANGE;
    public static IntValue WARRIOR_LUCK_PER_POINT;
    public static IntValue WARRIOR_LUCK_FLOOR_CAP;
    public static IntValue WARRIOR_TOUGHNESS_PER_HIT;
    public static IntValue WARRIOR_TOUGHNESS_BASE_CAP;
    public static IntValue WARRIOR_TOUGHNESS_CAP_PER_STEP;
    public static IntValue WARRIOR_TOUGHNESS_WINDOW_SECONDS;
    public static IntValue SHIELD_THORNS_REFLECT;
    public static IntValue SHIELD_ADAPT_PER_HIT;
    public static IntValue SHIELD_ADAPT_CAP;
    public static IntValue SHIELD_ADAPT_TYPES;
    public static IntValue SHIELD_ADAPT_WINDOW;
    public static IntValue SHIELD_SHOCKWAVE_RADIUS;
    public static IntValue SHIELD_SHOCKWAVE_STUN;
    public static IntValue SHIELD_SACRIFICE_RATIO;
    public static IntValue SHIELD_SACRIFICE_RADIUS;
    public static IntValue SHIELD_SACRIFICE_MAX;
    public static IntValue SHIELD_SACRIFICE_COOLDOWN;
    public static IntValue SHIELD_EARTH_SECONDS;
    public static IntValue SHIELD_EARTH_REDUCTION;
    public static DoubleValue SHIELD_DEVOUR_PER_KILL;
    public static DoubleValue SHIELD_DEVOUR_MAX;
    public static IntValue SHIELD_NOURISH_HP_THRESHOLD;
    public static DoubleValue SHIELD_NOURISH_PER_FOOD;
    public static IntValue SHIELD_NOURISH_MAX_FOOD;
    public static IntValue SHIELD_VITALITY_PER_LAYER;
    public static IntValue SHIELD_VITALITY_MAX_LAYERS;
    public static IntValue SHIELD_VITALITY_WINDOW;
    public static IntValue SHIELD_VANGUARD_ARMOR;
    public static IntValue SHIELD_VANGUARD_SPEED;
    public static DoubleValue SHIELD_VANGUARD_KNOCKBACK;
    public static DoubleValue SHIELD_VANGUARD_PUSH;
    public static DoubleValue SHIELD_VANGUARD_PUSH_RADIUS;
    public static IntValue SHIELD_ETERNAL_ARMOR;
    public static IntValue SHIELD_ETERNAL_HEALTH;
    public static IntValue SHIELD_ETERNAL_REDUCTION;
    public static IntValue SHIELD_ETERNAL_TOUGHNESS;
    public static IntValue SHIELD_ETERNAL_SPEED;
    public static IntValue CRYSTAL_CHAIN_RADIUS;
    public static IntValue CRYSTAL_CHAIN_RATIO;
    public static IntValue CRYSTAL_CHAIN_JUMPS;
    public static IntValue CRYSTAL_SNIPER_STEP;
    public static IntValue CRYSTAL_SNIPER_PER_STEP;
    public static IntValue CRYSTAL_SNIPER_CAP;
    public static IntValue CRYSTAL_CIRCLE_RADIUS;
    public static IntValue CRYSTAL_CIRCLE_SECONDS;
    public static IntValue CRYSTAL_CIRCLE_SPEED;
    public static IntValue CRYSTAL_CIRCLE_POTENCY;
    public static IntValue CRYSTAL_SOULFIRE_REFUND;
    public static IntValue CRYSTAL_SOULFIRE_DAMAGE;
    public static IntValue CRYSTAL_OVERDRAFT_RATIO;
    public static IntValue CRYSTAL_OVERDRAFT_BONUS;
    public static IntValue CRYSTAL_EMBER_SECONDS;
    public static IntValue CRYSTAL_EMBER_RATIO;
    public static IntValue CRYSTAL_HEXNAIL_MAX;
    public static IntValue CRYSTAL_HEXNAIL_DAMAGE;
    public static IntValue CRYSTAL_ECHO_CHANCE;
    public static IntValue CRYSTAL_ECHO_DAMAGE;
    public static IntValue CRYSTAL_SWIFT_REDUCTION;
    public static IntValue CRYSTAL_SOVEREIGN_POTENCY;
    public static IntValue CRYSTAL_SOVEREIGN_CASTING;
    public static IntValue CRYSTAL_SOVEREIGN_RADIUS;
    public static IntValue CRYSTAL_SOVEREIGN_SOUL;
    public static IntValue WARRIOR_CHARGE_PER_SECOND;
    public static IntValue WARRIOR_CHARGE_MAX;
    public static DoubleValue WARRIOR_DASH_SPEED_THRESHOLD;
    public static DoubleValue WARRIOR_DASH_DISTANCE;
    public static IntValue WARRIOR_DASH_DAMAGE;
    public static IntValue WARRIOR_DASH_COOLDOWN;
    public static DoubleValue WARRIOR_DASH_HUNGER;
    public static IntValue WARRIOR_DASH_DOUBLE_TAP_TICKS;
    public static IntValue JOB_SLOT_SIZE;
    public static IntValue TIME_CAPSULE_MAX_USES;
    public static IntValue TIME_CAPSULE_SHORTEN_SECONDS;

    /**
     * 内部标记:勋章「每级成长」的 0 值迁移是否已执行过。
     *
     * <p>见 {@link #migrateGrowthDefaults()}。加这个标记是为了让迁移<b>只做一次</b> ——
     * 没有它的话，迁移会在每次启动时把 0 改回设计值；而玩家一旦想手动把某项成长
     * 关掉（填 0），下次进游戏就会被打回原样，那种"改了没用"的行为比不改还难查。
     */
    public static BooleanValue GROWTH_MIGRATED;

    /**
     * 内部标记：等级上限 30 → 60 的迁移是否已执行过。
     *
     * <p>只在 {@code max_level == 30} 时才改 —— 那正好是 beta25 及更早的默认值。
     * 玩家若手动设过别的数（比如 45），迁移不碰它。
     */
    public static BooleanValue LEVEL_CAP_MIGRATED;

    /**
     * 内部标记：经验成长曲线 10/8/7/6/5/3 → 7/6/5/4/3/2 的迁移是否已执行过。
     */
    public static BooleanValue EXP_CURVE_MIGRATED;

    static {
        Pair<Common, ForgeConfigSpec> common = new ForgeConfigSpec.Builder().configure(Common::new);
        SPEC = common.getRight();
        Common c = common.getLeft();

        Pair<Client, ForgeConfigSpec> clientPair = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT_SPEC = clientPair.getRight();
        Client cl = clientPair.getLeft();

        MAX_LEVEL = c.MAX_LEVEL;
        BASE_EXP = c.BASE_EXP;
        EXP_GROWTH_1_10 = c.EXP_GROWTH_1_10;
        EXP_GROWTH_11_20 = c.EXP_GROWTH_11_20;
        EXP_GROWTH_21_30 = c.EXP_GROWTH_21_30;
        EXP_GROWTH_31_40 = c.EXP_GROWTH_31_40;
        EXP_GROWTH_41_50 = c.EXP_GROWTH_41_50;
        EXP_GROWTH_51_60 = c.EXP_GROWTH_51_60;
        MAX_MODIFIERS_PER_UUID = c.MAX_MODIFIERS_PER_UUID;
        ARCHER_EXP_DAMAGE = c.ARCHER_EXP_DAMAGE;
        ARCHER_EXP_KILL_HP = c.ARCHER_EXP_KILL_HP;
        ARCHER_L1_ARROW_DMG = c.ARCHER_L1_ARROW_DMG;
        ARCHER_L1_DRAW_SPEED = c.ARCHER_L1_DRAW_SPEED;
        ARCHER_L1_MOVE_SPEED = c.ARCHER_L1_MOVE_SPEED;
        ARCHER_L1_DODGE = c.ARCHER_L1_DODGE;
        ARCHER_PER_ARROW_DMG = c.ARCHER_PER_ARROW_DMG;
        ARCHER_PER_DRAW_SPEED = c.ARCHER_PER_DRAW_SPEED;
        ARCHER_PER_MOVE_SPEED = c.ARCHER_PER_MOVE_SPEED;
        ARCHER_PER_DODGE = c.ARCHER_PER_DODGE;
        S5_DODGE_IMMUNE_CHANCE = c.S5_DODGE_IMMUNE_CHANCE;
        S5_TELEPORT_RANGE = c.S5_TELEPORT_RANGE;
        S5_TELEPORT_MIN_HEIGHT = c.S5_TELEPORT_MIN_HEIGHT;
        S5_COOLDOWN_SECONDS = c.S5_COOLDOWN_SECONDS;
        S10_DAMAGE_PER_1_25_BLOCK = c.S10_DAMAGE_PER_1_25_BLOCK;
        S15_NEAR_CHANCE = c.S15_NEAR_CHANCE;
        S15_CHANCE_DROP_PER_BLOCK = c.S15_CHANCE_DROP_PER_BLOCK;
        S15_MIN_CHANCE = c.S15_MIN_CHANCE;
        S15_NEAR_DISTANCE = c.S15_NEAR_DISTANCE;
        S20_SPREAD_MIN_DEG = c.S20_SPREAD_MIN_DEG;
        S20_SPREAD_MAX_DEG = c.S20_SPREAD_MAX_DEG;
        S20_EXTRA_ARROW_DAMAGE = c.S20_EXTRA_ARROW_DAMAGE;
        S20_MULTISHOT_BONUS = c.S20_MULTISHOT_BONUS;
        S25_RAIN_CHANCE = c.S25_RAIN_CHANCE;
        S25_RAIN_HEIGHT = c.S25_RAIN_HEIGHT;
        S25_RAIN_AREA = c.S25_RAIN_AREA;
        S25_RAIN_MIN_ARROWS = c.S25_RAIN_MIN_ARROWS;
        S25_RAIN_MAX_ARROWS = c.S25_RAIN_MAX_ARROWS;
        S30_DURATION_SECONDS = c.S30_DURATION_SECONDS;
        S30_MOVE_SPEED = c.S30_MOVE_SPEED;
        S30_MOVE_SPEED_MOUNTED = c.S30_MOVE_SPEED_MOUNTED;
        S30_DRAW_SPEED = c.S30_DRAW_SPEED;
        S30_ARROW_DAMAGE = c.S30_ARROW_DAMAGE;
        S30_MAX_EXTEND = c.S30_MAX_EXTEND;
        S30_COOLDOWN_SECONDS = c.S30_COOLDOWN_SECONDS;
        WARRIOR_EXP_DAMAGE = c.WARRIOR_EXP_DAMAGE;
        WARRIOR_EXP_KILL_HP = c.WARRIOR_EXP_KILL_HP;
        WARRIOR_L1_DAMAGE = c.WARRIOR_L1_DAMAGE;
        WARRIOR_L1_ATTACK_SPEED = c.WARRIOR_L1_ATTACK_SPEED;
        WARRIOR_L1_CRIT_CHANCE = c.WARRIOR_L1_CRIT_CHANCE;
        WARRIOR_L1_CRIT_DAMAGE = c.WARRIOR_L1_CRIT_DAMAGE;
        WARRIOR_PER_DAMAGE = c.WARRIOR_PER_DAMAGE;
        WARRIOR_PER_ATTACK_SPEED = c.WARRIOR_PER_ATTACK_SPEED;
        WARRIOR_PER_CRIT_CHANCE = c.WARRIOR_PER_CRIT_CHANCE;
        WARRIOR_PER_CRIT_DAMAGE = c.WARRIOR_PER_CRIT_DAMAGE;
        W5_BLEED_CHANCE = c.W5_BLEED_CHANCE;
        W5_BLEED_SECONDS = c.W5_BLEED_SECONDS;
        W5_BLEED_ARMOR_PIERCE = c.W5_BLEED_ARMOR_PIERCE;
        W5_BLEED_CRIT_CHANCE = c.W5_BLEED_CRIT_CHANCE;
        W10_HP_THRESHOLD = c.W10_HP_THRESHOLD;
        W10_ARMOR_PIERCE_PER_STEP = c.W10_ARMOR_PIERCE_PER_STEP;
        W10_LIFESTEAL_PER_STEP = c.W10_LIFESTEAL_PER_STEP;
        W15_DURATION_SECONDS = c.W15_DURATION_SECONDS;
        W15_HP_COST_PER_SECOND = c.W15_HP_COST_PER_SECOND;
        W15_FIRE_DAMAGE = c.W15_FIRE_DAMAGE;
        W15_TARGET_FIRE_SECONDS = c.W15_TARGET_FIRE_SECONDS;
        W15_ATTACK_DAMAGE = c.W15_ATTACK_DAMAGE;
        W15_MOVE_SPEED = c.W15_MOVE_SPEED;
        W20_COLD_DAMAGE = c.W20_COLD_DAMAGE;
        W20_FREEZE_CHANCE = c.W20_FREEZE_CHANCE;
        W20_FREEZE_SECONDS = c.W20_FREEZE_SECONDS;
        W25_DURATION_SECONDS = c.W25_DURATION_SECONDS;
        W25_ATTACK_RANGE = c.W25_ATTACK_RANGE;
        W25_ATTACK_SPEED = c.W25_ATTACK_SPEED;
        W25_MOVE_SPEED = c.W25_MOVE_SPEED;
        W25_LIFESTEAL = c.W25_LIFESTEAL;
        W25_COOLDOWN_SECONDS = c.W25_COOLDOWN_SECONDS;
        W30_LOST_HP_DAMAGE = c.W30_LOST_HP_DAMAGE;
        W30_LOST_HP_DAMAGE_LOW = c.W30_LOST_HP_DAMAGE_LOW;
        W30_LOW_HP_THRESHOLD = c.W30_LOW_HP_THRESHOLD;
        W30_LOW_HP_CRIT_CHANCE = c.W30_LOW_HP_CRIT_CHANCE;
        TANK_EXP_DAMAGE = c.TANK_EXP_DAMAGE;
        TANK_EXP_KILL_HP = c.TANK_EXP_KILL_HP;
        TANK_EXP_HEAL = c.TANK_EXP_HEAL;
        TANK_L1_ARMOR = c.TANK_L1_ARMOR;
        TANK_L1_MAX_HEALTH = c.TANK_L1_MAX_HEALTH;
        TANK_L1_OVERHEAL = c.TANK_L1_OVERHEAL;
        TANK_L1_DAMAGE_REDUCTION = c.TANK_L1_DAMAGE_REDUCTION;
        TANK_PER_ARMOR = c.TANK_PER_ARMOR;
        TANK_PER_MAX_HEALTH = c.TANK_PER_MAX_HEALTH;
        TANK_PER_OVERHEAL = c.TANK_PER_OVERHEAL;
        TANK_PER_DAMAGE_REDUCTION = c.TANK_PER_DAMAGE_REDUCTION;
        T5_SLOW_WEAPON_ATTACK_SPEED = c.T5_SLOW_WEAPON_ATTACK_SPEED;
        T5_DAMAGE = c.T5_DAMAGE;
        T5_CRIT_CHANCE = c.T5_CRIT_CHANCE;
        T5_CRIT_DAMAGE = c.T5_CRIT_DAMAGE;
        T10_ABSORB_CHANCE = c.T10_ABSORB_CHANCE;
        T10_ABSORB_AMOUNT = c.T10_ABSORB_AMOUNT;
        T10_INTERVAL_SECONDS = c.T10_INTERVAL_SECONDS;
        T15_SHIELD_INTERVAL = c.T15_SHIELD_INTERVAL;
        T15_SHIELD_MAX_BLOCK = c.T15_SHIELD_MAX_BLOCK;
        T15_SHIELD_MAX_STACKS = c.T15_SHIELD_MAX_STACKS;
        T20_DURATION_SECONDS = c.T20_DURATION_SECONDS;
        T20_ARMOR_BONUS = c.T20_ARMOR_BONUS;
        T20_DAMAGE_REDUCTION = c.T20_DAMAGE_REDUCTION;
        T20_KNOCKBACK_RADIUS = c.T20_KNOCKBACK_RADIUS;
        T20_KNOCKBACK_DISTANCE = c.T20_KNOCKBACK_DISTANCE;
        T20_BURST_DAMAGE = c.T20_BURST_DAMAGE;
        T20_SLOWNESS_SECONDS = c.T20_SLOWNESS_SECONDS;
        T20_COOLDOWN_SECONDS = c.T20_COOLDOWN_SECONDS;
        T25_BLOCK_CHANCE = c.T25_BLOCK_CHANCE;
        T25_BLOCK_VALUE = c.T25_BLOCK_VALUE;
        T25_INTERVAL_SECONDS = c.T25_INTERVAL_SECONDS;
        T25_BLOCK_PER_VALUE = c.T25_BLOCK_PER_VALUE;
        T25_BLOCK_CAP = c.T25_BLOCK_CAP;
        T25_RESET_SECONDS = c.T25_RESET_SECONDS;
        T30_HP_THRESHOLD = c.T30_HP_THRESHOLD;
        T30_DURATION_SECONDS = c.T30_DURATION_SECONDS;
        T30_HEAL_PER_SECOND = c.T30_HEAL_PER_SECOND;
        T30_ABSORB = c.T30_ABSORB;
        T30_COOLDOWN_SECONDS = c.T30_COOLDOWN_SECONDS;
        MAGE_EXP_SPELL_DAMAGE = c.MAGE_EXP_SPELL_DAMAGE;
        MAGE_EXP_KILL_HP = c.MAGE_EXP_KILL_HP;
        MAGE_EXP_SOUL = c.MAGE_EXP_SOUL;
        MAGE_L1_SPELL_POTENCY = c.MAGE_L1_SPELL_POTENCY;
        MAGE_L1_CASTING_SPEED = c.MAGE_L1_CASTING_SPEED;
        MAGE_L1_SOUL_DISCOUNT = c.MAGE_L1_SOUL_DISCOUNT;
        MAGE_L1_SPELL_RADIUS = c.MAGE_L1_SPELL_RADIUS;
        MAGE_PER_SPELL_POTENCY = c.MAGE_PER_SPELL_POTENCY;
        MAGE_PER_CASTING_SPEED = c.MAGE_PER_CASTING_SPEED;
        MAGE_PER_SOUL_DISCOUNT = c.MAGE_PER_SOUL_DISCOUNT;
        MAGE_PER_SPELL_RADIUS = c.MAGE_PER_SPELL_RADIUS;
        M5_MAX_STACKS = c.M5_MAX_STACKS;
        M5_WINDOW_SECONDS = c.M5_WINDOW_SECONDS;
        M5_POTENCY_PER_STACK = c.M5_POTENCY_PER_STACK;
        M5_SOUL_DISCOUNT_PER_STACK = c.M5_SOUL_DISCOUNT_PER_STACK;
        M10_BASE_CRIT_CHANCE = c.M10_BASE_CRIT_CHANCE;
        M10_BASE_CRIT_DAMAGE = c.M10_BASE_CRIT_DAMAGE;
        M15_REFUND_CHANCE = c.M15_REFUND_CHANCE;
        M15_REFUND_RATIO = c.M15_REFUND_RATIO;
        M15_DEBUFF_REFUND_CHANCE = c.M15_DEBUFF_REFUND_CHANCE;
        M15_DEBUFF_REFUND_RATIO = c.M15_DEBUFF_REFUND_RATIO;
        M15_SPELL_DURATION = c.M15_SPELL_DURATION;
        M20_DURATION_SECONDS = c.M20_DURATION_SECONDS;
        M20_CASTING_SPEED = c.M20_CASTING_SPEED;
        M20_SOUL_DISCOUNT = c.M20_SOUL_DISCOUNT;
        M20_SPELL_POTENCY = c.M20_SPELL_POTENCY;
        M20_COOLDOWN_DISCOUNT = c.M20_COOLDOWN_DISCOUNT;
        M20_SPELL_RADIUS = c.M20_SPELL_RADIUS;
        M20_MOVE_SPEED = c.M20_MOVE_SPEED;
        M20_COOLDOWN_SECONDS = c.M20_COOLDOWN_SECONDS;
        M25_SOUL_PER_SECOND = c.M25_SOUL_PER_SECOND;
        M25_STACK_INTERVAL_SECONDS = c.M25_STACK_INTERVAL_SECONDS;
        M25_MAX_STACKS = c.M25_MAX_STACKS;
        M25_POTENCY_PER_STACK = c.M25_POTENCY_PER_STACK;
        M25_CASTING_SPEED_PER_STACK = c.M25_CASTING_SPEED_PER_STACK;
        M25_CRIT_CHANCE_PER_STACK = c.M25_CRIT_CHANCE_PER_STACK;
        M25_COOLDOWN_SECONDS = c.M25_COOLDOWN_SECONDS;
        M30_REFRESH_CHANCE = c.M30_REFRESH_CHANCE;
        M30_PITY_INCREMENT = c.M30_PITY_INCREMENT;
        M30_BARRAGE_POTENCY = c.M30_BARRAGE_POTENCY;
        M30_BARRAGE_ANGLE = c.M30_BARRAGE_ANGLE;
        M30_SPELL_RADIUS = c.M30_SPELL_RADIUS;
        SUMMONER_EXP_SERVANT_DAMAGE = c.SUMMONER_EXP_SERVANT_DAMAGE;
        SUMMONER_EXP_KILL_HP = c.SUMMONER_EXP_KILL_HP;
        SUMMONER_L1_SERVANT_HEALTH = c.SUMMONER_L1_SERVANT_HEALTH;
        SUMMONER_L1_SERVANT_DAMAGE = c.SUMMONER_L1_SERVANT_DAMAGE;
        SUMMONER_L1_SERVANT_SPEED = c.SUMMONER_L1_SERVANT_SPEED;
        SUMMONER_L1_SERVANT_LIFESPAN = c.SUMMONER_L1_SERVANT_LIFESPAN;
        SUMMONER_PER_SERVANT_HEALTH = c.SUMMONER_PER_SERVANT_HEALTH;
        SUMMONER_PER_SERVANT_DAMAGE = c.SUMMONER_PER_SERVANT_DAMAGE;
        SUMMONER_PER_SERVANT_SPEED = c.SUMMONER_PER_SERVANT_SPEED;
        SUMMONER_PER_SERVANT_LIFESPAN = c.SUMMONER_PER_SERVANT_LIFESPAN;
        S5_SOUL_PER_HIT = c.S5_SOUL_PER_HIT;
        S5_SOUL_COOLDOWN = c.S5_SOUL_COOLDOWN;
        S10_SERVANT_REDUCTION = c.S10_SERVANT_REDUCTION;
        S15_REVIVE_CHANCE = c.S15_REVIVE_CHANCE;
        S15_REVIVE_HEALTH = c.S15_REVIVE_HEALTH;
        S20_DURATION_SECONDS = c.S20_DURATION_SECONDS;
        S20_SERVANT_DAMAGE = c.S20_SERVANT_DAMAGE;
        S20_SERVANT_LIMIT = c.S20_SERVANT_LIMIT;
        S20_COOLDOWN_SECONDS = c.S20_COOLDOWN_SECONDS;
        S25_EXTRA_DAMAGE = c.S25_EXTRA_DAMAGE;
        S30_SERVANT_LIMIT = c.S30_SERVANT_LIMIT;
        S30_COOLDOWN_DISCOUNT = c.S30_COOLDOWN_DISCOUNT;
        S30_SERVANT_HEALTH = c.S30_SERVANT_HEALTH;
        CONTRACT_DOMINION_HEALTH = c.CONTRACT_DOMINION_HEALTH;
        CONTRACT_DOMINION_DAMAGE = c.CONTRACT_DOMINION_DAMAGE;
        CONTRACT_DOMINION_SOUL = c.CONTRACT_DOMINION_SOUL;
        CONTRACT_LEGION_LIMIT = c.CONTRACT_LEGION_LIMIT;
        CONTRACT_LEGION_LIFE = c.CONTRACT_LEGION_LIFE;
        CONTRACT_SYMBIOSIS_RATIO = c.CONTRACT_SYMBIOSIS_RATIO;
        CONTRACT_SYMBIOSIS_RADIUS = c.CONTRACT_SYMBIOSIS_RADIUS;
        CONTRACT_MARTYR_RADIUS = c.CONTRACT_MARTYR_RADIUS;
        CONTRACT_MARTYR_RATIO = c.CONTRACT_MARTYR_RATIO;
        CONTRACT_SOULCHAIN_POTENCY = c.CONTRACT_SOULCHAIN_POTENCY;
        CONTRACT_SOULCHAIN_MAX = c.CONTRACT_SOULCHAIN_MAX;
        CONTRACT_FLESH_RATIO = c.CONTRACT_FLESH_RATIO;
        CONTRACT_BONE_REDUCTION = c.CONTRACT_BONE_REDUCTION;
        CONTRACT_BONE_MAX = c.CONTRACT_BONE_MAX;
        CONTRACT_FISSION_CHANCE = c.CONTRACT_FISSION_CHANCE;
        CONTRACT_FISSION_RATIO = c.CONTRACT_FISSION_RATIO;
        CONTRACT_DECREE_VULNERABLE = c.CONTRACT_DECREE_VULNERABLE;
        CONTRACT_DECREE_SECONDS = c.CONTRACT_DECREE_SECONDS;
        CONTRACT_OVERLORD_HEALTH = c.CONTRACT_OVERLORD_HEALTH;
        CONTRACT_OVERLORD_DAMAGE = c.CONTRACT_OVERLORD_DAMAGE;
        CONTRACT_OVERLORD_SPEED = c.CONTRACT_OVERLORD_SPEED;
        CONTRACT_OVERLORD_LIMIT = c.CONTRACT_OVERLORD_LIMIT;
        CONTRACT_REFRESH_INTERVAL = c.CONTRACT_REFRESH_INTERVAL;
        SWORD_CRIT_CHANCE = c.SWORD_CRIT_CHANCE;
        SWORD_CRIT_DAMAGE = c.SWORD_CRIT_DAMAGE;
        SWORD_ATTACK_DAMAGE = c.SWORD_ATTACK_DAMAGE;
        SWORD_EXP_GAINED = c.SWORD_EXP_GAINED;
        SWORD_DAMAGE = c.SWORD_DAMAGE;
        SWORD_CHARGE_SECONDS = c.SWORD_CHARGE_SECONDS;
        SWORD_COOLDOWN_SECONDS = c.SWORD_COOLDOWN_SECONDS;
        SWORD_REACH = c.SWORD_REACH;
        SWORD_WIDTH = c.SWORD_WIDTH;
        SWORD_HEIGHT = c.SWORD_HEIGHT;
        SWORD_ANIM_TICKS = c.SWORD_ANIM_TICKS;
        SWORD_BREAK_BLOCKS = c.SWORD_BREAK_BLOCKS;
        SWORD_HIT_PLAYERS = c.SWORD_HIT_PLAYERS;
        EFFECT_HUD_ENABLED = cl.EFFECT_HUD_ENABLED;
        EFFECT_HUD_SCALE = cl.EFFECT_HUD_SCALE;
        EFFECT_HUD_CORNER = cl.EFFECT_HUD_CORNER;
        EFFECT_HUD_FLOW = cl.EFFECT_HUD_FLOW;
        EFFECT_HUD_TIMER = cl.EFFECT_HUD_TIMER;
        EFFECT_HUD_HARMFUL_BORDER = cl.EFFECT_HUD_HARMFUL_BORDER;

        VX_ENABLED = c.VX_ENABLED;
        VX_BASE_EXP = c.VX_BASE_EXP;
        VX_GROWTH_1_40 = c.VX_GROWTH_1_40;
        VX_GROWTH_40_70 = c.VX_GROWTH_40_70;
        VX_GROWTH_70_100 = c.VX_GROWTH_70_100;
        VX_GROWTH_AFTER_100 = c.VX_GROWTH_AFTER_100;
        VX_DECAY_PERCENT = c.VX_DECAY_PERCENT;
        VX_DECAY_STEP = c.VX_DECAY_STEP;
        VX_MIN_GROWTH_MILLI = c.VX_MIN_GROWTH_MILLI;

        QOL_POTION_INFINITE = c.QOL_POTION_INFINITE;
        QOL_POTION_SCAN_BACKPACK = c.QOL_POTION_SCAN_BACKPACK;
        QOL_BUFF_SHARE = c.QOL_BUFF_SHARE;
        QOL_BUFF_SHARE_RADIUS = c.QOL_BUFF_SHARE_RADIUS;
        QOL_TOTEM_BACKPACK = c.QOL_TOTEM_BACKPACK;
        QOL_ELYTRA_ARMOR = c.QOL_ELYTRA_ARMOR;
        QOL_ELYTRA_ARMOR_VALUE = c.QOL_ELYTRA_ARMOR_VALUE;
        QOL_ELYTRA_TOUGHNESS_VALUE = c.QOL_ELYTRA_TOUGHNESS_VALUE;
        QOL_KEEP_BUFFS_ON_DEATH = c.QOL_KEEP_BUFFS_ON_DEATH;
        QOL_SLEEP_ANYTIME = c.QOL_SLEEP_ANYTIME;
        QOL_SLEEP_SKIP_HOURS = c.QOL_SLEEP_SKIP_HOURS;
        QOL_ONE_PLAYER_SLEEP = c.QOL_ONE_PLAYER_SLEEP;
        QOL_FARMLAND_PROTECT = c.QOL_FARMLAND_PROTECT;
        QOL_EAT_WHEN_FULL = c.QOL_EAT_WHEN_FULL;
        QOL_WITHER_SKULL = c.QOL_WITHER_SKULL;
        QOL_DEATH_DROP_CENTERED = c.QOL_DEATH_DROP_CENTERED;
        QOL_AUTO_ATTACK = c.QOL_AUTO_ATTACK;
        QOL_AUTO_ATTACK_REQUIRE_TARGET = c.QOL_AUTO_ATTACK_REQUIRE_TARGET;
        QOL_AUTO_SHOOT = c.QOL_AUTO_SHOOT;
        QOL_VILLAGER_TRADES_64 = c.QOL_VILLAGER_TRADES_64;
        QOL_REFRESH_TRADES = c.QOL_REFRESH_TRADES;
        QOL_REFRESH_ENCHANT = c.QOL_REFRESH_ENCHANT;
        QOL_REFRESH_ENCHANT_COST_LEVELS = c.QOL_REFRESH_ENCHANT_COST_LEVELS;
        QOL_REFRESH_ENCHANT_COST_LAPIS = c.QOL_REFRESH_ENCHANT_COST_LAPIS;
        QOL_KEEP_LAPIS = c.QOL_KEEP_LAPIS;
        QOL_INFINITY_MENDING = c.QOL_INFINITY_MENDING;
        QOL_ENCHANT_PREVIEW_ALL = c.QOL_ENCHANT_PREVIEW_ALL;
        QOL_STACK_POTION = c.QOL_STACK_POTION;
        QOL_STACK_TOTEM = c.QOL_STACK_TOTEM;
        QOL_STACK_ENDER_PEARL = c.QOL_STACK_ENDER_PEARL;
        QOL_INFINITE_BOW_NO_ARROW = c.QOL_INFINITE_BOW_NO_ARROW;

        // ---- 终末之影 ----
        BOSS_ENABLED = c.BOSS_ENABLED;
        BOSS_MAX_HEALTH = c.BOSS_MAX_HEALTH;
        BOSS_PHASE_2_PERCENT = c.BOSS_PHASE_2_PERCENT;
        BOSS_PHASE_3_PERCENT = c.BOSS_PHASE_3_PERCENT;
        BOSS_JUDGEMENT_SECONDS = c.BOSS_JUDGEMENT_SECONDS;
        BOSS_SENTINEL_LIMIT = c.BOSS_SENTINEL_LIMIT;
        BOSS_MEDAL_REQUIREMENT = c.BOSS_MEDAL_REQUIREMENT;
        BOSS_WAVE_COUNT = c.BOSS_WAVE_COUNT;
        BOSS_WAVE_MOB_COUNT = c.BOSS_WAVE_MOB_COUNT;
        BOSS_WAVE_INTERVAL_SECONDS = c.BOSS_WAVE_INTERVAL_SECONDS;
        BOSS_WAVE_MOB_TYPE = c.BOSS_WAVE_MOB_TYPE;
        BOSS_DUEL_HIT_PERCENT = c.BOSS_DUEL_HIT_PERCENT;
        BOSS_TRANSITION_HEAL_SECONDS = c.BOSS_TRANSITION_HEAL_SECONDS;
        ARROW_POTION_CHANCE = c.ARROW_POTION_CHANCE;
        ARROW_POTION_SECONDS = c.ARROW_POTION_SECONDS;
        ARROW_EXPLOSION_POWER = c.ARROW_EXPLOSION_POWER;
        ARROW_PIERCE_COUNT = c.ARROW_PIERCE_COUNT;
        ARROW_PIERCE_FALLOFF = c.ARROW_PIERCE_FALLOFF;
        ARROW_NO_GRAVITY_SECONDS = c.ARROW_NO_GRAVITY_SECONDS;
        ARROW_THUNDER_DAMAGE = c.ARROW_THUNDER_DAMAGE;
        ARROW_THUNDER_RADIUS = c.ARROW_THUNDER_RADIUS;
        ARROW_THUNDER_PARALYZE_SECONDS = c.ARROW_THUNDER_PARALYZE_SECONDS;
        ARROW_TWIN_CHANCE = c.ARROW_TWIN_CHANCE;
        ARROW_TWIN_DAMAGE = c.ARROW_TWIN_DAMAGE;
        ARROW_ARMOR_PIERCE = c.ARROW_ARMOR_PIERCE;
        ARROW_HOLD_CHANCE = c.ARROW_HOLD_CHANCE;
        ARROW_HOLD_SECONDS = c.ARROW_HOLD_SECONDS;
        ARROW_SUMMON_MAX = c.ARROW_SUMMON_MAX;
        ARROW_SUMMON_COOLDOWN_SECONDS = c.ARROW_SUMMON_COOLDOWN_SECONDS;
        ARROW_SUMMON_HEALTH = c.ARROW_SUMMON_HEALTH;
        ARROW_STAR_DAMAGE = c.ARROW_STAR_DAMAGE;
        ARROW_STAR_CHARGE_SECONDS = c.ARROW_STAR_CHARGE_SECONDS;
        ARROW_STAR_PIERCE = c.ARROW_STAR_PIERCE;
        ARROW_STAR_FLIGHT_SECONDS = c.ARROW_STAR_FLIGHT_SECONDS;
        ARROW_STAR_BREAK_RADIUS = c.ARROW_STAR_BREAK_RADIUS;
        ARROW_STAR_BREAK_BLOCKS = c.ARROW_STAR_BREAK_BLOCKS;
        ARROW_STAR_BLOCK_DROPS = c.ARROW_STAR_BLOCK_DROPS;
        WARRIOR_SWEEP_RADIUS = c.WARRIOR_SWEEP_RADIUS;
        WARRIOR_DUAL_DAMAGE = c.WARRIOR_DUAL_DAMAGE;
        WARRIOR_DUAL_ATTACK_SPEED = c.WARRIOR_DUAL_ATTACK_SPEED;
        WARRIOR_CRIT_CHANCE = c.WARRIOR_CRIT_CHANCE;
        WARRIOR_CRIT_DAMAGE = c.WARRIOR_CRIT_DAMAGE;
        WARRIOR_BACKSTAB_BONUS = c.WARRIOR_BACKSTAB_BONUS;
        WARRIOR_DISARM_CHANCE = c.WARRIOR_DISARM_CHANCE;
        WARRIOR_DISARM_SECONDS = c.WARRIOR_DISARM_SECONDS;
        WARRIOR_DISARM_COOLDOWN = c.WARRIOR_DISARM_COOLDOWN;
        WARRIOR_LUCK_RANGE = c.WARRIOR_LUCK_RANGE;
        WARRIOR_LUCK_PER_POINT = c.WARRIOR_LUCK_PER_POINT;
        WARRIOR_LUCK_FLOOR_CAP = c.WARRIOR_LUCK_FLOOR_CAP;
        WARRIOR_TOUGHNESS_PER_HIT = c.WARRIOR_TOUGHNESS_PER_HIT;
        WARRIOR_TOUGHNESS_BASE_CAP = c.WARRIOR_TOUGHNESS_BASE_CAP;
        WARRIOR_TOUGHNESS_CAP_PER_STEP = c.WARRIOR_TOUGHNESS_CAP_PER_STEP;
        WARRIOR_TOUGHNESS_WINDOW_SECONDS = c.WARRIOR_TOUGHNESS_WINDOW_SECONDS;
        SHIELD_THORNS_REFLECT = c.SHIELD_THORNS_REFLECT;
        SHIELD_ADAPT_PER_HIT = c.SHIELD_ADAPT_PER_HIT;
        SHIELD_ADAPT_CAP = c.SHIELD_ADAPT_CAP;
        SHIELD_ADAPT_TYPES = c.SHIELD_ADAPT_TYPES;
        SHIELD_ADAPT_WINDOW = c.SHIELD_ADAPT_WINDOW;
        SHIELD_SHOCKWAVE_RADIUS = c.SHIELD_SHOCKWAVE_RADIUS;
        SHIELD_SHOCKWAVE_STUN = c.SHIELD_SHOCKWAVE_STUN;
        SHIELD_SACRIFICE_RATIO = c.SHIELD_SACRIFICE_RATIO;
        SHIELD_SACRIFICE_RADIUS = c.SHIELD_SACRIFICE_RADIUS;
        SHIELD_SACRIFICE_MAX = c.SHIELD_SACRIFICE_MAX;
        SHIELD_SACRIFICE_COOLDOWN = c.SHIELD_SACRIFICE_COOLDOWN;
        SHIELD_EARTH_SECONDS = c.SHIELD_EARTH_SECONDS;
        SHIELD_EARTH_REDUCTION = c.SHIELD_EARTH_REDUCTION;
        SHIELD_DEVOUR_PER_KILL = c.SHIELD_DEVOUR_PER_KILL;
        SHIELD_DEVOUR_MAX = c.SHIELD_DEVOUR_MAX;
        SHIELD_NOURISH_HP_THRESHOLD = c.SHIELD_NOURISH_HP_THRESHOLD;
        SHIELD_NOURISH_PER_FOOD = c.SHIELD_NOURISH_PER_FOOD;
        SHIELD_NOURISH_MAX_FOOD = c.SHIELD_NOURISH_MAX_FOOD;
        SHIELD_VITALITY_PER_LAYER = c.SHIELD_VITALITY_PER_LAYER;
        SHIELD_VITALITY_MAX_LAYERS = c.SHIELD_VITALITY_MAX_LAYERS;
        SHIELD_VITALITY_WINDOW = c.SHIELD_VITALITY_WINDOW;
        SHIELD_VANGUARD_ARMOR = c.SHIELD_VANGUARD_ARMOR;
        SHIELD_VANGUARD_SPEED = c.SHIELD_VANGUARD_SPEED;
        SHIELD_VANGUARD_KNOCKBACK = c.SHIELD_VANGUARD_KNOCKBACK;
        SHIELD_VANGUARD_PUSH = c.SHIELD_VANGUARD_PUSH;
        SHIELD_VANGUARD_PUSH_RADIUS = c.SHIELD_VANGUARD_PUSH_RADIUS;
        SHIELD_ETERNAL_ARMOR = c.SHIELD_ETERNAL_ARMOR;
        SHIELD_ETERNAL_HEALTH = c.SHIELD_ETERNAL_HEALTH;
        SHIELD_ETERNAL_REDUCTION = c.SHIELD_ETERNAL_REDUCTION;
        SHIELD_ETERNAL_TOUGHNESS = c.SHIELD_ETERNAL_TOUGHNESS;
        SHIELD_ETERNAL_SPEED = c.SHIELD_ETERNAL_SPEED;
        CRYSTAL_CHAIN_RADIUS = c.CRYSTAL_CHAIN_RADIUS;
        CRYSTAL_CHAIN_RATIO = c.CRYSTAL_CHAIN_RATIO;
        CRYSTAL_CHAIN_JUMPS = c.CRYSTAL_CHAIN_JUMPS;
        CRYSTAL_SNIPER_STEP = c.CRYSTAL_SNIPER_STEP;
        CRYSTAL_SNIPER_PER_STEP = c.CRYSTAL_SNIPER_PER_STEP;
        CRYSTAL_SNIPER_CAP = c.CRYSTAL_SNIPER_CAP;
        CRYSTAL_CIRCLE_RADIUS = c.CRYSTAL_CIRCLE_RADIUS;
        CRYSTAL_CIRCLE_SECONDS = c.CRYSTAL_CIRCLE_SECONDS;
        CRYSTAL_CIRCLE_SPEED = c.CRYSTAL_CIRCLE_SPEED;
        CRYSTAL_CIRCLE_POTENCY = c.CRYSTAL_CIRCLE_POTENCY;
        CRYSTAL_SOULFIRE_REFUND = c.CRYSTAL_SOULFIRE_REFUND;
        CRYSTAL_SOULFIRE_DAMAGE = c.CRYSTAL_SOULFIRE_DAMAGE;
        CRYSTAL_OVERDRAFT_RATIO = c.CRYSTAL_OVERDRAFT_RATIO;
        CRYSTAL_OVERDRAFT_BONUS = c.CRYSTAL_OVERDRAFT_BONUS;
        CRYSTAL_EMBER_SECONDS = c.CRYSTAL_EMBER_SECONDS;
        CRYSTAL_EMBER_RATIO = c.CRYSTAL_EMBER_RATIO;
        CRYSTAL_HEXNAIL_MAX = c.CRYSTAL_HEXNAIL_MAX;
        CRYSTAL_HEXNAIL_DAMAGE = c.CRYSTAL_HEXNAIL_DAMAGE;
        CRYSTAL_ECHO_CHANCE = c.CRYSTAL_ECHO_CHANCE;
        CRYSTAL_ECHO_DAMAGE = c.CRYSTAL_ECHO_DAMAGE;
        CRYSTAL_SWIFT_REDUCTION = c.CRYSTAL_SWIFT_REDUCTION;
        CRYSTAL_SOVEREIGN_POTENCY = c.CRYSTAL_SOVEREIGN_POTENCY;
        CRYSTAL_SOVEREIGN_CASTING = c.CRYSTAL_SOVEREIGN_CASTING;
        CRYSTAL_SOVEREIGN_RADIUS = c.CRYSTAL_SOVEREIGN_RADIUS;
        CRYSTAL_SOVEREIGN_SOUL = c.CRYSTAL_SOVEREIGN_SOUL;
        WARRIOR_CHARGE_PER_SECOND = c.WARRIOR_CHARGE_PER_SECOND;
        WARRIOR_CHARGE_MAX = c.WARRIOR_CHARGE_MAX;
        WARRIOR_DASH_SPEED_THRESHOLD = c.WARRIOR_DASH_SPEED_THRESHOLD;
        WARRIOR_DASH_DISTANCE = c.WARRIOR_DASH_DISTANCE;
        WARRIOR_DASH_DAMAGE = c.WARRIOR_DASH_DAMAGE;
        WARRIOR_DASH_COOLDOWN = c.WARRIOR_DASH_COOLDOWN;
        WARRIOR_DASH_HUNGER = c.WARRIOR_DASH_HUNGER;
        WARRIOR_DASH_DOUBLE_TAP_TICKS = c.WARRIOR_DASH_DOUBLE_TAP_TICKS;
        JOB_SLOT_SIZE = c.JOB_SLOT_SIZE;
        TIME_CAPSULE_MAX_USES = c.TIME_CAPSULE_MAX_USES;
        TIME_CAPSULE_SHORTEN_SECONDS = c.TIME_CAPSULE_SHORTEN_SECONDS;
        GROWTH_MIGRATED = c.GROWTH_MIGRATED;
        LEVEL_CAP_MIGRATED = c.LEVEL_CAP_MIGRATED;
        EXP_CURVE_MIGRATED = c.EXP_CURVE_MIGRATED;
    }

    /**
     * 通用配置(COMMON)。
     *
     * <p>采用「内部类持有字段 + 静态常量引用」的标准 Forge 模式:
     * Forge 的 {@code Builder.configure()} 需要一个可实例化的配置类,
     * 字段必须是非 final 实例字段,再由静态块把实例字段引用暴露为静态常量,
     * 这样外部仍可用 {@code MedalsConfig.ARCHER_L1_ARROW_DMG} 直接访问。
     */
    public static class Common {

        public IntValue MAX_LEVEL;
        public IntValue BASE_EXP;
        public IntValue EXP_GROWTH_1_10;
        public IntValue EXP_GROWTH_11_20;
        public IntValue EXP_GROWTH_21_30;
        public IntValue EXP_GROWTH_31_40;
        public IntValue EXP_GROWTH_41_50;
        public IntValue EXP_GROWTH_51_60;
        public IntValue MAX_MODIFIERS_PER_UUID;
        public IntValue ARCHER_EXP_DAMAGE;
        public IntValue ARCHER_EXP_KILL_HP;
        public IntValue ARCHER_L1_ARROW_DMG;
        public IntValue ARCHER_L1_DRAW_SPEED;
        public IntValue ARCHER_L1_MOVE_SPEED;
        public IntValue ARCHER_L1_DODGE;
        public DoubleValue ARCHER_PER_ARROW_DMG;
        public DoubleValue ARCHER_PER_DRAW_SPEED;
        public DoubleValue ARCHER_PER_MOVE_SPEED;
        public DoubleValue ARCHER_PER_DODGE;
        public IntValue S5_DODGE_IMMUNE_CHANCE;
        public IntValue S5_TELEPORT_RANGE;
        public IntValue S5_TELEPORT_MIN_HEIGHT;
        public IntValue S5_COOLDOWN_SECONDS;
        public DoubleValue S10_DAMAGE_PER_1_25_BLOCK;
        public IntValue S15_NEAR_CHANCE;
        public IntValue S15_CHANCE_DROP_PER_BLOCK;
        public IntValue S15_MIN_CHANCE;
        public IntValue S15_NEAR_DISTANCE;
        public IntValue S20_SPREAD_MIN_DEG;
        public IntValue S20_SPREAD_MAX_DEG;
        public IntValue S20_EXTRA_ARROW_DAMAGE;
        public IntValue S20_MULTISHOT_BONUS;
        public IntValue S25_RAIN_CHANCE;
        public IntValue S25_RAIN_HEIGHT;
        public IntValue S25_RAIN_AREA;
        public IntValue S25_RAIN_MIN_ARROWS;
        public IntValue S25_RAIN_MAX_ARROWS;
        public IntValue S30_DURATION_SECONDS;
        public IntValue S30_MOVE_SPEED;
        public IntValue S30_MOVE_SPEED_MOUNTED;
        public IntValue S30_DRAW_SPEED;
        public IntValue S30_ARROW_DAMAGE;
        public IntValue S30_MAX_EXTEND;
        public IntValue S30_COOLDOWN_SECONDS;
        public IntValue WARRIOR_EXP_DAMAGE;
        public IntValue WARRIOR_EXP_KILL_HP;
        public IntValue WARRIOR_L1_DAMAGE;
        public IntValue WARRIOR_L1_ATTACK_SPEED;
        public IntValue WARRIOR_L1_CRIT_CHANCE;
        public IntValue WARRIOR_L1_CRIT_DAMAGE;
        public DoubleValue WARRIOR_PER_DAMAGE;
        public DoubleValue WARRIOR_PER_ATTACK_SPEED;
        public DoubleValue WARRIOR_PER_CRIT_CHANCE;
        public DoubleValue WARRIOR_PER_CRIT_DAMAGE;
        public IntValue W5_BLEED_CHANCE;
        public IntValue W5_BLEED_SECONDS;
        public IntValue W5_BLEED_ARMOR_PIERCE;
        public IntValue W5_BLEED_CRIT_CHANCE;
        public IntValue W10_HP_THRESHOLD;
        public IntValue W10_ARMOR_PIERCE_PER_STEP;
        public IntValue W10_LIFESTEAL_PER_STEP;
        public IntValue W15_DURATION_SECONDS;
        public IntValue W15_HP_COST_PER_SECOND;
        public IntValue W15_FIRE_DAMAGE;
        public IntValue W15_TARGET_FIRE_SECONDS;
        public IntValue W15_ATTACK_DAMAGE;
        public IntValue W15_MOVE_SPEED;
        public IntValue W20_COLD_DAMAGE;
        public IntValue W20_FREEZE_CHANCE;
        public IntValue W20_FREEZE_SECONDS;
        public IntValue W25_DURATION_SECONDS;
        public IntValue W25_ATTACK_RANGE;
        public IntValue W25_ATTACK_SPEED;
        public IntValue W25_MOVE_SPEED;
        public IntValue W25_LIFESTEAL;
        public IntValue W25_COOLDOWN_SECONDS;
        public IntValue W30_LOST_HP_DAMAGE;
        public IntValue W30_LOST_HP_DAMAGE_LOW;
        public IntValue W30_LOW_HP_THRESHOLD;
        public IntValue W30_LOW_HP_CRIT_CHANCE;
        public IntValue TANK_EXP_DAMAGE;
        public IntValue TANK_EXP_KILL_HP;
        public IntValue TANK_EXP_HEAL;
        public IntValue TANK_L1_ARMOR;
        public IntValue TANK_L1_MAX_HEALTH;
        public IntValue TANK_L1_OVERHEAL;
        public IntValue TANK_L1_DAMAGE_REDUCTION;
        public DoubleValue TANK_PER_ARMOR;
        public DoubleValue TANK_PER_MAX_HEALTH;
        public DoubleValue TANK_PER_OVERHEAL;
        public DoubleValue TANK_PER_DAMAGE_REDUCTION;
        public DoubleValue T5_SLOW_WEAPON_ATTACK_SPEED;
        public IntValue T5_DAMAGE;
        public IntValue T5_CRIT_CHANCE;
        public IntValue T5_CRIT_DAMAGE;
        public IntValue T10_ABSORB_CHANCE;
        public IntValue T10_ABSORB_AMOUNT;
        public IntValue T10_INTERVAL_SECONDS;
        public IntValue T15_SHIELD_INTERVAL;
        public IntValue T15_SHIELD_MAX_BLOCK;
        public IntValue T15_SHIELD_MAX_STACKS;
        public IntValue T20_DURATION_SECONDS;
        public IntValue T20_ARMOR_BONUS;
        public IntValue T20_DAMAGE_REDUCTION;
        public IntValue T20_KNOCKBACK_RADIUS;
        public IntValue T20_KNOCKBACK_DISTANCE;
        public IntValue T20_BURST_DAMAGE;
        public IntValue T20_SLOWNESS_SECONDS;
        public IntValue T20_COOLDOWN_SECONDS;
        public IntValue T25_BLOCK_CHANCE;
        public IntValue T25_BLOCK_VALUE;
        public IntValue T25_INTERVAL_SECONDS;
        public DoubleValue T25_BLOCK_PER_VALUE;
        public IntValue T25_BLOCK_CAP;
        public IntValue T25_RESET_SECONDS;
        public IntValue T30_HP_THRESHOLD;
        public IntValue T30_DURATION_SECONDS;
        public IntValue T30_HEAL_PER_SECOND;
        public IntValue T30_ABSORB;
        public IntValue T30_COOLDOWN_SECONDS;
        // ---- 法师勋章 ----
        public IntValue MAGE_EXP_SPELL_DAMAGE;
        public IntValue MAGE_EXP_KILL_HP;
        public IntValue MAGE_EXP_SOUL;
        public IntValue MAGE_L1_SPELL_POTENCY;
        public IntValue MAGE_L1_CASTING_SPEED;
        public IntValue MAGE_L1_SOUL_DISCOUNT;
        public IntValue MAGE_L1_SPELL_RADIUS;
        public DoubleValue MAGE_PER_SPELL_POTENCY;
        public DoubleValue MAGE_PER_CASTING_SPEED;
        public DoubleValue MAGE_PER_SOUL_DISCOUNT;
        public DoubleValue MAGE_PER_SPELL_RADIUS;

        // 箭类饰品与时间胶囊(dv42)
        public IntValue ARROW_POTION_CHANCE;
        public IntValue ARROW_POTION_SECONDS;
        public DoubleValue ARROW_EXPLOSION_POWER;
        public IntValue ARROW_PIERCE_COUNT;
        public IntValue ARROW_PIERCE_FALLOFF;
        public IntValue ARROW_NO_GRAVITY_SECONDS;
        public DoubleValue ARROW_THUNDER_DAMAGE;
        public IntValue ARROW_THUNDER_RADIUS;
        public IntValue ARROW_THUNDER_PARALYZE_SECONDS;
        public IntValue ARROW_TWIN_CHANCE;
        public IntValue ARROW_TWIN_DAMAGE;
        public IntValue ARROW_ARMOR_PIERCE;
        public IntValue ARROW_HOLD_CHANCE;
        public IntValue ARROW_HOLD_SECONDS;
        public IntValue ARROW_SUMMON_MAX;
        public IntValue ARROW_SUMMON_COOLDOWN_SECONDS;
        public DoubleValue ARROW_SUMMON_HEALTH;
        public DoubleValue ARROW_STAR_DAMAGE;
        public IntValue ARROW_STAR_CHARGE_SECONDS;
        public IntValue ARROW_STAR_PIERCE;
        public IntValue ARROW_STAR_FLIGHT_SECONDS;
        public DoubleValue ARROW_STAR_BREAK_RADIUS;
        public BooleanValue ARROW_STAR_BREAK_BLOCKS;
        public BooleanValue ARROW_STAR_BLOCK_DROPS;
        public DoubleValue WARRIOR_SWEEP_RADIUS;
        public IntValue WARRIOR_DUAL_DAMAGE;
        public IntValue WARRIOR_DUAL_ATTACK_SPEED;
        public IntValue WARRIOR_CRIT_CHANCE;
        public IntValue WARRIOR_CRIT_DAMAGE;
        public IntValue WARRIOR_BACKSTAB_BONUS;
        public IntValue WARRIOR_DISARM_CHANCE;
        public IntValue WARRIOR_DISARM_SECONDS;
        public IntValue WARRIOR_DISARM_COOLDOWN;
        public IntValue WARRIOR_LUCK_RANGE;
        public IntValue WARRIOR_LUCK_PER_POINT;
        public IntValue WARRIOR_LUCK_FLOOR_CAP;
        public IntValue WARRIOR_TOUGHNESS_PER_HIT;
        public IntValue WARRIOR_TOUGHNESS_BASE_CAP;
        public IntValue WARRIOR_TOUGHNESS_CAP_PER_STEP;
        public IntValue WARRIOR_TOUGHNESS_WINDOW_SECONDS;
        public IntValue SHIELD_THORNS_REFLECT;
        public IntValue SHIELD_ADAPT_PER_HIT;
        public IntValue SHIELD_ADAPT_CAP;
        public IntValue SHIELD_ADAPT_TYPES;
        public IntValue SHIELD_ADAPT_WINDOW;
        public IntValue SHIELD_SHOCKWAVE_RADIUS;
        public IntValue SHIELD_SHOCKWAVE_STUN;
        public IntValue SHIELD_SACRIFICE_RATIO;
        public IntValue SHIELD_SACRIFICE_RADIUS;
        public IntValue SHIELD_SACRIFICE_MAX;
        public IntValue SHIELD_SACRIFICE_COOLDOWN;
        public IntValue SHIELD_EARTH_SECONDS;
        public IntValue SHIELD_EARTH_REDUCTION;
        public DoubleValue SHIELD_DEVOUR_PER_KILL;
        public DoubleValue SHIELD_DEVOUR_MAX;
        public IntValue SHIELD_NOURISH_HP_THRESHOLD;
        public DoubleValue SHIELD_NOURISH_PER_FOOD;
        public IntValue SHIELD_NOURISH_MAX_FOOD;
        public IntValue SHIELD_VITALITY_PER_LAYER;
        public IntValue SHIELD_VITALITY_MAX_LAYERS;
        public IntValue SHIELD_VITALITY_WINDOW;
        public IntValue SHIELD_VANGUARD_ARMOR;
        public IntValue SHIELD_VANGUARD_SPEED;
        public DoubleValue SHIELD_VANGUARD_KNOCKBACK;
        public DoubleValue SHIELD_VANGUARD_PUSH;
        public DoubleValue SHIELD_VANGUARD_PUSH_RADIUS;
        public IntValue SHIELD_ETERNAL_ARMOR;
        public IntValue SHIELD_ETERNAL_HEALTH;
        public IntValue SHIELD_ETERNAL_REDUCTION;
        public IntValue SHIELD_ETERNAL_TOUGHNESS;
        public IntValue SHIELD_ETERNAL_SPEED;
        public IntValue CRYSTAL_CHAIN_RADIUS;
        public IntValue CRYSTAL_CHAIN_RATIO;
        public IntValue CRYSTAL_CHAIN_JUMPS;
        public IntValue CRYSTAL_SNIPER_STEP;
        public IntValue CRYSTAL_SNIPER_PER_STEP;
        public IntValue CRYSTAL_SNIPER_CAP;
        public IntValue CRYSTAL_CIRCLE_RADIUS;
        public IntValue CRYSTAL_CIRCLE_SECONDS;
        public IntValue CRYSTAL_CIRCLE_SPEED;
        public IntValue CRYSTAL_CIRCLE_POTENCY;
        public IntValue CRYSTAL_SOULFIRE_REFUND;
        public IntValue CRYSTAL_SOULFIRE_DAMAGE;
        public IntValue CRYSTAL_OVERDRAFT_RATIO;
        public IntValue CRYSTAL_OVERDRAFT_BONUS;
        public IntValue CRYSTAL_EMBER_SECONDS;
        public IntValue CRYSTAL_EMBER_RATIO;
        public IntValue CRYSTAL_HEXNAIL_MAX;
        public IntValue CRYSTAL_HEXNAIL_DAMAGE;
        public IntValue CRYSTAL_ECHO_CHANCE;
        public IntValue CRYSTAL_ECHO_DAMAGE;
        public IntValue CRYSTAL_SWIFT_REDUCTION;
        public IntValue CRYSTAL_SOVEREIGN_POTENCY;
        public IntValue CRYSTAL_SOVEREIGN_CASTING;
        public IntValue CRYSTAL_SOVEREIGN_RADIUS;
        public IntValue CRYSTAL_SOVEREIGN_SOUL;
        public IntValue WARRIOR_CHARGE_PER_SECOND;
        public IntValue WARRIOR_CHARGE_MAX;
        public DoubleValue WARRIOR_DASH_SPEED_THRESHOLD;
        public DoubleValue WARRIOR_DASH_DISTANCE;
        public IntValue WARRIOR_DASH_DAMAGE;
        public IntValue WARRIOR_DASH_COOLDOWN;
        public DoubleValue WARRIOR_DASH_HUNGER;
        public IntValue WARRIOR_DASH_DOUBLE_TAP_TICKS;
        public IntValue JOB_SLOT_SIZE;
        public IntValue TIME_CAPSULE_MAX_USES;
        public IntValue TIME_CAPSULE_SHORTEN_SECONDS;

        /** 内部迁移标记，见 {@link MedalsConfig#GROWTH_MIGRATED}。 */
        public BooleanValue GROWTH_MIGRATED;

        /** 内部迁移标记，见 {@link MedalsConfig#LEVEL_CAP_MIGRATED}。 */
        public BooleanValue LEVEL_CAP_MIGRATED;

        /** 内部迁移标记，见 {@link MedalsConfig#EXP_CURVE_MIGRATED}。 */
        public BooleanValue EXP_CURVE_MIGRATED;
        public IntValue M5_MAX_STACKS;
        public IntValue M5_WINDOW_SECONDS;
        public DoubleValue M5_POTENCY_PER_STACK;
        public DoubleValue M5_SOUL_DISCOUNT_PER_STACK;
        public IntValue M10_BASE_CRIT_CHANCE;
        public IntValue M10_BASE_CRIT_DAMAGE;
        public IntValue M15_REFUND_CHANCE;
        public IntValue M15_REFUND_RATIO;
        public IntValue M15_DEBUFF_REFUND_CHANCE;
        public IntValue M15_DEBUFF_REFUND_RATIO;
        public IntValue M15_SPELL_DURATION;
        public IntValue M20_DURATION_SECONDS;
        public IntValue M20_CASTING_SPEED;
        public IntValue M20_SOUL_DISCOUNT;
        public IntValue M20_SPELL_POTENCY;
        public IntValue M20_COOLDOWN_DISCOUNT;
        public IntValue M20_SPELL_RADIUS;
        public IntValue M20_MOVE_SPEED;
        public IntValue M20_COOLDOWN_SECONDS;
        public IntValue M25_SOUL_PER_SECOND;
        public IntValue M25_STACK_INTERVAL_SECONDS;
        public IntValue M25_MAX_STACKS;
        public IntValue M25_POTENCY_PER_STACK;
        public IntValue M25_CASTING_SPEED_PER_STACK;
        public IntValue M25_CRIT_CHANCE_PER_STACK;
        public IntValue M25_COOLDOWN_SECONDS;
        public IntValue M30_REFRESH_CHANCE;
        public IntValue M30_PITY_INCREMENT;
        public IntValue M30_BARRAGE_POTENCY;
        public IntValue M30_BARRAGE_ANGLE;
        public IntValue M30_SPELL_RADIUS;
        // ---- 召唤师勋章 ----
        public IntValue SUMMONER_EXP_SERVANT_DAMAGE;
        public IntValue SUMMONER_EXP_KILL_HP;
        public IntValue SUMMONER_L1_SERVANT_HEALTH;
        public IntValue SUMMONER_L1_SERVANT_DAMAGE;
        public IntValue SUMMONER_L1_SERVANT_SPEED;
        public IntValue SUMMONER_L1_SERVANT_LIFESPAN;
        public DoubleValue SUMMONER_PER_SERVANT_HEALTH;
        public DoubleValue SUMMONER_PER_SERVANT_DAMAGE;
        public DoubleValue SUMMONER_PER_SERVANT_SPEED;
        public DoubleValue SUMMONER_PER_SERVANT_LIFESPAN;
        public IntValue S5_SOUL_PER_HIT;
        public IntValue S5_SOUL_COOLDOWN;
        public IntValue S10_SERVANT_REDUCTION;
        public IntValue S15_REVIVE_CHANCE;
        public IntValue S15_REVIVE_HEALTH;
        public IntValue S20_DURATION_SECONDS;
        public IntValue S20_SERVANT_DAMAGE;
        public IntValue S20_SERVANT_LIMIT;
        public IntValue S20_COOLDOWN_SECONDS;
        public DoubleValue S25_EXTRA_DAMAGE;
        public IntValue S30_SERVANT_LIMIT;
        public IntValue S30_COOLDOWN_DISCOUNT;
        public IntValue S30_SERVANT_HEALTH;
        public IntValue CONTRACT_DOMINION_HEALTH;
        public IntValue CONTRACT_DOMINION_DAMAGE;
        public IntValue CONTRACT_DOMINION_SOUL;
        public IntValue CONTRACT_LEGION_LIMIT;
        public IntValue CONTRACT_LEGION_LIFE;
        public IntValue CONTRACT_SYMBIOSIS_RATIO;
        public DoubleValue CONTRACT_SYMBIOSIS_RADIUS;
        public DoubleValue CONTRACT_MARTYR_RADIUS;
        public IntValue CONTRACT_MARTYR_RATIO;
        public IntValue CONTRACT_SOULCHAIN_POTENCY;
        public IntValue CONTRACT_SOULCHAIN_MAX;
        public IntValue CONTRACT_FLESH_RATIO;
        public IntValue CONTRACT_BONE_REDUCTION;
        public IntValue CONTRACT_BONE_MAX;
        public IntValue CONTRACT_FISSION_CHANCE;
        public IntValue CONTRACT_FISSION_RATIO;
        public IntValue CONTRACT_DECREE_VULNERABLE;
        public IntValue CONTRACT_DECREE_SECONDS;
        public IntValue CONTRACT_OVERLORD_HEALTH;
        public IntValue CONTRACT_OVERLORD_DAMAGE;
        public IntValue CONTRACT_OVERLORD_SPEED;
        public IntValue CONTRACT_OVERLORD_LIMIT;
        public IntValue CONTRACT_REFRESH_INTERVAL;
        // ---- 战士终极「我有一剑！」 ----
        public IntValue SWORD_CRIT_CHANCE;
        public IntValue SWORD_CRIT_DAMAGE;
        public IntValue SWORD_ATTACK_DAMAGE;
        public IntValue SWORD_EXP_GAINED;
        public IntValue SWORD_DAMAGE;
        public IntValue SWORD_CHARGE_SECONDS;
        public IntValue SWORD_COOLDOWN_SECONDS;
        public DoubleValue SWORD_REACH;
        public DoubleValue SWORD_WIDTH;
        public DoubleValue SWORD_HEIGHT;
        public IntValue SWORD_ANIM_TICKS;
        public BooleanValue SWORD_BREAK_BLOCKS;
        public BooleanValue SWORD_HIT_PLAYERS;

        // 体验改进 · 第 1 项:原版经验等级(与勋章熟练度无关)
        public BooleanValue VX_ENABLED;
        public IntValue VX_BASE_EXP;
        public IntValue VX_GROWTH_1_40;
        public IntValue VX_GROWTH_40_70;
        public IntValue VX_GROWTH_70_100;
        public IntValue VX_GROWTH_AFTER_100;
        public IntValue VX_DECAY_PERCENT;
        public IntValue VX_DECAY_STEP;
        public IntValue VX_MIN_GROWTH_MILLI;

        // 体验改进(QoL)系列
        public BooleanValue QOL_POTION_INFINITE;
        public BooleanValue QOL_POTION_SCAN_BACKPACK;
        public BooleanValue QOL_BUFF_SHARE;
        public IntValue QOL_BUFF_SHARE_RADIUS;
        public BooleanValue QOL_TOTEM_BACKPACK;
        public BooleanValue QOL_ELYTRA_ARMOR;
        public IntValue QOL_ELYTRA_ARMOR_VALUE;
        public IntValue QOL_ELYTRA_TOUGHNESS_VALUE;
        public BooleanValue QOL_KEEP_BUFFS_ON_DEATH;
        public BooleanValue QOL_SLEEP_ANYTIME;
        public IntValue QOL_SLEEP_SKIP_HOURS;
        public BooleanValue QOL_ONE_PLAYER_SLEEP;
        public BooleanValue QOL_FARMLAND_PROTECT;
        public BooleanValue QOL_EAT_WHEN_FULL;
        public BooleanValue QOL_WITHER_SKULL;
        public BooleanValue QOL_DEATH_DROP_CENTERED;
        public BooleanValue QOL_AUTO_ATTACK;
        public BooleanValue QOL_AUTO_ATTACK_REQUIRE_TARGET;
        public BooleanValue QOL_AUTO_SHOOT;
        public BooleanValue QOL_VILLAGER_TRADES_64;
        public BooleanValue QOL_REFRESH_TRADES;
        public BooleanValue QOL_REFRESH_ENCHANT;
        public IntValue QOL_REFRESH_ENCHANT_COST_LEVELS;
        public IntValue QOL_REFRESH_ENCHANT_COST_LAPIS;
        public BooleanValue QOL_KEEP_LAPIS;
        public BooleanValue QOL_INFINITY_MENDING;
        public BooleanValue QOL_ENCHANT_PREVIEW_ALL;
        public BooleanValue QOL_STACK_POTION;
        public BooleanValue QOL_STACK_TOTEM;
        public BooleanValue QOL_STACK_ENDER_PEARL;
        public BooleanValue QOL_INFINITE_BOW_NO_ARROW;

        // ---- 终末之影(Boss)----
        public BooleanValue BOSS_ENABLED;
        public DoubleValue BOSS_MAX_HEALTH;
        public IntValue BOSS_PHASE_2_PERCENT;
        public IntValue BOSS_PHASE_3_PERCENT;
        public IntValue BOSS_JUDGEMENT_SECONDS;
        public IntValue BOSS_SENTINEL_LIMIT;
        public IntValue BOSS_MEDAL_REQUIREMENT;

        // ---- dv5:遗落之主新流程 ----
        public IntValue BOSS_WAVE_COUNT;
        public IntValue BOSS_WAVE_MOB_COUNT;
        public IntValue BOSS_WAVE_INTERVAL_SECONDS;
        public IntValue BOSS_WAVE_MOB_TYPE;
        public IntValue BOSS_DUEL_HIT_PERCENT;
        public IntValue BOSS_TRANSITION_HEAL_SECONDS;

        public Common(ForgeConfigSpec.Builder b) {

        b.comment("单位说明:本文件中所有百分比数值均写整数(20 表示 20%),代码会除以 100 后使用。").push("general");

        MAX_LEVEL = b.comment("熟练度最高等级(beta26 起 60;旧存档会自动迁移 30→60)").defineInRange("max_level", 60, 1, 1000);
        BASE_EXP = b.comment("Lv.1 -> Lv.2 所需经验").defineInRange("base_exp", 100, 1, Integer.MAX_VALUE);
        // 六段成长曲线：每 10 级一档，逐段收窄。
        // 前期涨得快（让前 10 级很快有反馈），后期涨得慢（60 级不至于变成天文数字）。
        EXP_GROWTH_1_10 = b.comment("1~10 级:每级所需经验比上一级多出的百分比(整数)。7 = +7%")
                .defineInRange("exp_growth_1_10", 7, 0, 1000);
        EXP_GROWTH_11_20 = b.comment("11~20 级:每级所需经验比上一级多出的百分比(整数)。6 = +6%")
                .defineInRange("exp_growth_11_20", 6, 0, 1000);
        EXP_GROWTH_21_30 = b.comment("21~30 级:每级所需经验比上一级多出的百分比(整数)。5 = +5%")
                .defineInRange("exp_growth_21_30", 5, 0, 1000);
        EXP_GROWTH_31_40 = b.comment("31~40 级:每级所需经验比上一级多出的百分比(整数)。4 = +4%")
                .defineInRange("exp_growth_31_40", 4, 0, 1000);
        EXP_GROWTH_41_50 = b.comment("41~50 级:每级所需经验比上一级多出的百分比(整数)。3 = +3%")
                .defineInRange("exp_growth_41_50", 3, 0, 1000);
        EXP_GROWTH_51_60 = b.comment("51~60 级:每级所需经验比上一级多出的百分比(整数)。2 = +2%")
                .defineInRange("exp_growth_51_60", 2, 0, 1000);
        MAX_MODIFIERS_PER_UUID = b.comment("防叠加保护:同名修饰符(UUID)允许的最大并存数量,超出将被拒绝")
                .defineInRange("max_modifiers_per_uuid", 8, 1, 64);
        b.pop();

        // ---------------------------------- 射手 ----------------------------------
        b.comment("射手勋章 Lv.1 及每级成长(整数百分比)").push("archer");
        ARCHER_EXP_DAMAGE = pctDef(b, "exp_damage", 20, "远程伤害转化为熟练度的百分比");
        ARCHER_EXP_KILL_HP = pctDef(b, "exp_kill_hp", 15, "击杀时按目标最大生命值转化的百分比");

        ARCHER_L1_ARROW_DMG = pctDef(b, "lv1_arrow_damage", 8, "Lv.1 箭伤加成");
        ARCHER_L1_DRAW_SPEED = pctDef(b, "lv1_draw_speed", 5, "Lv.1 拉弓速度加成");
        ARCHER_L1_MOVE_SPEED = pctDef(b, "lv1_move_speed", 5, "Lv.1 移速加成");
        ARCHER_L1_DODGE = pctDef(b, "lv1_dodge", 5, "Lv.1 闪避加成");

        ARCHER_PER_ARROW_DMG = pctDDef(b, "per_level_arrow_damage", 1.5D, "1~29 级每级箭伤加成");
        ARCHER_PER_DRAW_SPEED = pctDDef(b, "per_level_draw_speed", 0.8D, "1~29 级每级拉弓速度加成(配置为 0.8 时请填 1 并调整)");
        ARCHER_PER_MOVE_SPEED = pctDDef(b, "per_level_move_speed", 0.8D, "1~29 级每级移速加成");
        ARCHER_PER_DODGE = pctDDef(b, "per_level_dodge", 0.5D, "1~29 级每级闪避加成");
        b.pop();

        b.push("archer_skills");
        S5_DODGE_IMMUNE_CHANCE = pctDef(b, "s5_immune_chance", 60, "【闪避】免疫概率");
        S5_TELEPORT_RANGE = b.comment("【闪避】同 Y 轴传送范围(格)").defineInRange("s5_teleport_range", 6, 1, 64);
        S5_TELEPORT_MIN_HEIGHT = b.comment("【闪避】必需的最小空高(格)").defineInRange("s5_min_height", 2, 1, 16);
        S5_COOLDOWN_SECONDS = b.comment("【闪避】冷却(秒)").defineInRange("s5_cooldown", 20, 0, 3600);

        S10_DAMAGE_PER_1_25_BLOCK = b.comment("【蓄势】每飞行 1.25 格增加的伤害百分比(整数, 1 = +1%)")
                .defineInRange("s10_damage_per_1_25_block", 1.0, 0.0, 1000.0);

        S15_NEAR_CHANCE = pctDef(b, "s15_near_chance", 45, "【破绽】2 格内双倍伤害概率");
        S15_CHANCE_DROP_PER_BLOCK = pctDef(b, "s15_chance_drop", 5, "【破绽】每远 1 格降低的概率");
        S15_MIN_CHANCE = pctDef(b, "s15_min_chance", 15, "【破绽】最低概率");
        S15_NEAR_DISTANCE = b.comment("【破绽】判定为“近”的距离(格)").defineInRange("s15_near_distance", 2, 0, 64);

        S20_SPREAD_MIN_DEG = b.comment("【三发】额外箭矢最小偏角(度)").defineInRange("s20_spread_min", 1, 0, 45);
        S20_SPREAD_MAX_DEG = b.comment("【三发】额外箭矢最大偏角(度)").defineInRange("s20_spread_max", 3, 0, 45);
        S20_EXTRA_ARROW_DAMAGE = pctDef(b, "s20_extra_arrow_damage", 25, "【三发】额外箭矢伤害(1/4)");
        S20_MULTISHOT_BONUS = pctDef(b, "s20_multishot_bonus", 25, "【三发】兼容多重射击时每根箭的额外伤害");

        S25_RAIN_CHANCE = pctDef(b, "s25_rain_chance", 20, "【箭雨】触发概率");
        S25_RAIN_HEIGHT = b.comment("【箭雨】落点(目标头顶)上方高度(格)").defineInRange("s25_rain_height", 6, 1, 64);
        S25_RAIN_AREA = b.comment("【箭雨】落点范围(边长, 3 表示 3x3)").defineInRange("s25_rain_area", 3, 1, 16);
        S25_RAIN_MIN_ARROWS = b.comment("【箭雨】最少箭数").defineInRange("s25_rain_min", 4, 1, 64);
        S25_RAIN_MAX_ARROWS = b.comment("【箭雨】最多箭数").defineInRange("s25_rain_max", 6, 1, 64);

        S30_DURATION_SECONDS = b.comment("【疾风骤雨】持续(秒)").defineInRange("s30_duration", 10, 1, 3600);
        S30_MOVE_SPEED = pctDef(b, "s30_move_speed", 20, "【疾风骤雨】移速加成");
        S30_MOVE_SPEED_MOUNTED = pctDef(b, "s30_move_speed_mounted", 30, "【疾风骤雨】骑马时移速加成");
        S30_DRAW_SPEED = pctDef(b, "s30_draw_speed", 80, "【疾风骤雨】拉弓速度加成");
        S30_ARROW_DAMAGE = pctDef(b, "s30_arrow_damage", 10, "【疾风骤雨】箭矢伤害加成");
        S30_MAX_EXTEND = b.comment("【疾风骤雨】击杀延长上限(秒)").defineInRange("s30_max_extend", 5, 0, 3600);
        S30_COOLDOWN_SECONDS = b.comment("【疾风骤雨】冷却(秒)").defineInRange("s30_cooldown", 70, 0, 36000);
        b.pop();

        // ---------------------------------- 战士 ----------------------------------
        b.comment("战士勋章 Lv.1 及每级成长(整数百分比)").push("warrior");
        WARRIOR_EXP_DAMAGE = pctDef(b, "exp_damage", 10, "近战伤害转化为熟练度的百分比");
        WARRIOR_EXP_KILL_HP = pctDef(b, "exp_kill_hp", 10, "击杀时按目标最大生命值转化的百分比");

        WARRIOR_L1_DAMAGE = pctDef(b, "lv1_damage", 5, "Lv.1 伤害加成");
        WARRIOR_L1_ATTACK_SPEED = pctDef(b, "lv1_attack_speed", 5, "Lv.1 攻速加成");
        WARRIOR_L1_CRIT_CHANCE = pctDef(b, "lv1_crit_chance", 5, "Lv.1 暴击率加成");
        WARRIOR_L1_CRIT_DAMAGE = pctDef(b, "lv1_crit_damage", 5, "Lv.1 暴击伤害加成");

        WARRIOR_PER_DAMAGE = pctDDef(b, "per_level_damage", 0.75D, "1~29 级每级伤害加成");
        WARRIOR_PER_ATTACK_SPEED = pctDDef(b, "per_level_attack_speed", 0.5D, "1~29 级每级攻速加成");
        WARRIOR_PER_CRIT_CHANCE = pctDDef(b, "per_level_crit_chance", 0.5D, "1~29 级每级暴击率加成");
        WARRIOR_PER_CRIT_DAMAGE = pctDDef(b, "per_level_crit_damage", 1.0D, "1~29 级每级暴击伤害加成");
        b.pop();

        b.push("warrior_skills");
        W5_BLEED_CHANCE = pctDef(b, "w5_bleed_chance", 20, "【撕裂】施加流血概率");
        W5_BLEED_SECONDS = b.comment("【撕裂】流血持续(秒)").defineInRange("w5_bleed_seconds", 5, 1, 3600);
        W5_BLEED_ARMOR_PIERCE = b.comment("【撕裂】攻击流血目标时的护甲穿透").defineInRange("w5_armor_pierce", 4, 0, 1000);
        W5_BLEED_CRIT_CHANCE = pctDef(b, "w5_crit_chance", 10, "【撕裂】攻击流血目标时的暴击率加成");

        W10_HP_THRESHOLD = pctDef(b, "w10_hp_threshold", 60, "【愈战愈勇】触发血量阈值");
        W10_ARMOR_PIERCE_PER_STEP = b.comment("【愈战愈勇】每损失 10% 最大生命值获得的护甲穿透")
                .defineInRange("w10_armor_pierce_per_step", 2, 0, 1000);
        W10_LIFESTEAL_PER_STEP = pctDef(b, "w10_lifesteal_per_step", 3, "【愈战愈勇】每损失 10% 最大生命值获得的生命偷取");

        W15_DURATION_SECONDS = b.comment("【燃烧自我】持续(秒)").defineInRange("w15_duration", 12, 1, 3600);
        W15_HP_COST_PER_SECOND = pctDef(b, "w15_hp_cost", 4, "【燃烧自我】每秒扣除最大生命值百分比(无视火焰免疫)");
        W15_FIRE_DAMAGE = b.comment("【燃烧自我】附加火焰伤害").defineInRange("w15_fire_damage", 5, 0, 1000);
        W15_TARGET_FIRE_SECONDS = b.comment("【燃烧自我】使目标着火时长(秒)").defineInRange("w15_target_fire_seconds", 10, 0, 3600);
        W15_ATTACK_DAMAGE = pctDef(b, "w15_attack_damage", 10, "【燃烧自我】攻击伤害加成");
        W15_MOVE_SPEED = pctDef(b, "w15_move_speed", 15, "【燃烧自我】移速加成");

        W20_COLD_DAMAGE = b.comment("【寒霜之刃】冰霜伤害").defineInRange("w20_cold_damage", 5, 0, 1000);
        W20_FREEZE_CHANCE = pctDef(b, "w20_freeze_chance", 10, "【寒霜之刃】冰冻概率");
        W20_FREEZE_SECONDS = b.comment("【寒霜之刃】冰冻持续(秒)").defineInRange("w20_freeze_seconds", 3, 1, 3600);

        W25_DURATION_SECONDS = b.comment("【战迹血淋】持续(秒)").defineInRange("w25_duration", 10, 1, 3600);
        W25_ATTACK_RANGE = b.comment("【战迹血淋】攻击距离加成(格)").defineInRange("w25_attack_range", 1, 0, 16);
        W25_ATTACK_SPEED = pctDef(b, "w25_attack_speed", 50, "【战迹血淋】攻速加成");
        W25_MOVE_SPEED = pctDef(b, "w25_move_speed", 10, "【战迹血淋】移速加成");
        W25_LIFESTEAL = pctDef(b, "w25_lifesteal", 20, "【战迹血淋】生命偷取加成");
        W25_COOLDOWN_SECONDS = b.comment("【战迹血淋】冷却(秒)").defineInRange("w25_cooldown", 70, 0, 36000);

        W30_LOST_HP_DAMAGE = pctDef(b, "w30_lost_hp_damage", 20, "【血意】暴击附带已损生命值伤害比例");
        W30_LOST_HP_DAMAGE_LOW = pctDef(b, "w30_lost_hp_damage_low", 40, "【血意】低血量时的已损生命值伤害比例");
        W30_LOW_HP_THRESHOLD = pctDef(b, "w30_low_hp_threshold", 50, "【血意】判定低血量的阈值");
        W30_LOW_HP_CRIT_CHANCE = pctDef(b, "w30_low_hp_crit_chance", 10, "【血意】低血量时额外暴击率");
        b.pop();

        // ---------------------------------- 坦克 ----------------------------------
        b.comment("坦克勋章 Lv.1 及每级成长(整数百分比)").push("tank");
        TANK_EXP_DAMAGE = pctDef(b, "exp_damage", 50, "承受伤害转化为熟练度的百分比");
        TANK_EXP_KILL_HP = pctDef(b, "exp_kill_hp", 10, "击杀时按目标最大生命值转化的百分比");
        TANK_EXP_HEAL = pctDef(b, "exp_heal", 35, "恢复生命转化为熟练度的百分比");
        TANK_L1_ARMOR = pctDef(b, "lv1_armor", 5, "Lv.1 护甲加成");
        TANK_L1_MAX_HEALTH = pctDef(b, "lv1_max_health", 5, "Lv.1 最大生命值加成");
        TANK_L1_OVERHEAL = pctDef(b, "lv1_overheal", 10, "Lv.1 过量治疗加成");
        TANK_L1_DAMAGE_REDUCTION = pctDef(b, "lv1_damage_reduction", 5, "Lv.1 伤害减免加成");

        TANK_PER_ARMOR = pctDDef(b, "per_level_armor", 0.75D, "1~29 级每级护甲加成");
        TANK_PER_MAX_HEALTH = pctDDef(b, "per_level_max_health", 1.0D, "1~29 级每级最大生命值加成");
        TANK_PER_OVERHEAL = pctDDef(b, "per_level_overheal", 1.0D, "1~29 级每级过量治疗加成");
        TANK_PER_DAMAGE_REDUCTION = pctDDef(b, "per_level_damage_reduction", 0.5D, "1~29 级每级伤害减免加成");
        b.pop();

        b.push("tank_skills");
        T5_SLOW_WEAPON_ATTACK_SPEED = b.comment("【重击】判定为“慢速武器”的攻速上限")
                .defineInRange("t5_slow_weapon_attack_speed", 1.0, 0.0, 20.0);
        T5_DAMAGE = pctDef(b, "t5_damage", 20, "【重击】伤害加成");
        T5_CRIT_CHANCE = pctDef(b, "t5_crit_chance", 20, "【重击】暴击率加成");
        T5_CRIT_DAMAGE = pctDef(b, "t5_crit_damage", 20, "【重击】暴击伤害加成");

        T10_ABSORB_CHANCE = pctDef(b, "t10_absorb_chance", 35, "【攻势汲取】触发概率");
        T10_ABSORB_AMOUNT = b.comment("【攻势汲取】获得的伤害吸收").defineInRange("t10_absorb_amount", 4, 0, 1000);
        T10_INTERVAL_SECONDS = b.comment("【攻势汲取】触发间隔(秒)").defineInRange("t10_interval", 3, 0, 3600);

        T15_SHIELD_INTERVAL = b.comment("【星盾】生成间隔(秒)").defineInRange("t15_shield_interval", 45, 1, 36000);
        T15_SHIELD_MAX_BLOCK = b.comment("【星盾】每面盾最多抵挡伤害").defineInRange("t15_shield_max_block", 50, 1, 100000);
        T15_SHIELD_MAX_STACKS = b.comment("【星盾】最大层数").defineInRange("t15_shield_max_stacks", 3, 1, 64);

        T20_DURATION_SECONDS = b.comment("【不屈壁垒】持续(秒)").defineInRange("t20_duration", 15, 1, 3600);
        T20_ARMOR_BONUS = pctDef(b, "t20_armor_bonus", 20, "【不屈壁垒】护甲加成");
        T20_DAMAGE_REDUCTION = pctDef(b, "t20_damage_reduction", 20, "【不屈壁垒】减伤加成");
        T20_KNOCKBACK_RADIUS = b.comment("【不屈壁垒】释放时击退半径(格)").defineInRange("t20_knockback_radius", 5, 0, 64);
        T20_KNOCKBACK_DISTANCE = b.comment("【不屈壁垒】击退距离(格)").defineInRange("t20_knockback_distance", 2, 0, 64);
        T20_BURST_DAMAGE = b.comment("【不屈壁垒】释放时造成的伤害").defineInRange("t20_burst_damage", 12, 0, 100000);
        T20_SLOWNESS_SECONDS = b.comment("【不屈壁垒】施加缓慢 I 的持续(秒)").defineInRange("t20_slowness_seconds", 5, 0, 3600);
        T20_COOLDOWN_SECONDS = b.comment("【不屈壁垒】冷却(秒)").defineInRange("t20_cooldown", 75, 0, 36000);

        T25_BLOCK_CHANCE = pctDef(b, "t25_block_chance", 30, "【格挡】获得格挡值的概率");
        T25_BLOCK_VALUE = b.comment("【格挡】每次获得的格挡值").defineInRange("t25_block_value", 20, 0, 100000);
        T25_INTERVAL_SECONDS = b.comment("【格挡】触发间隔(秒)").defineInRange("t25_interval", 2, 0, 3600);
        T25_BLOCK_PER_VALUE = b.comment("【格挡】每 1 点格挡值可抵消的伤害")
                .defineInRange("t25_block_per_value", 1.5, 0.0, 1000.0);
        T25_BLOCK_CAP = pctDef(b, "t25_block_cap", 20, "【格挡】单次抵消不超过攻击伤害的百分比");
        T25_RESET_SECONDS = b.comment("【格挡】未获得格挡值后的清零时间(秒)").defineInRange("t25_reset", 10, 1, 3600);

        T30_HP_THRESHOLD = pctDef(b, "t30_hp_threshold", 10, "【生生不息】触发血量阈值");
        T30_DURATION_SECONDS = b.comment("【生生不息】持续(秒)").defineInRange("t30_duration", 5, 1, 3600);
        T30_HEAL_PER_SECOND = pctDef(b, "t30_heal_per_second", 10, "【生生不息】每秒恢复最大生命值百分比");
        T30_ABSORB = b.comment("【生生不息】获得的伤害吸收").defineInRange("t30_absorb", 20, 0, 100000);
        T30_COOLDOWN_SECONDS = b.comment("【生生不息】冷却(秒)").defineInRange("t30_cooldown", 90, 0, 36000);

        // ==================== 法师勋章(MAGE / Goety 底座) ====================
        b.comment("------------------------------ 法师勋章 ------------------------------").push("mage");
        MAGE_EXP_SPELL_DAMAGE = pctDef(b, "exp_spell_damage", 20, "每点有效魔法伤害获得的熟练度(溢出伤害不计)");
        MAGE_EXP_KILL_HP = pctDef(b, "exp_kill_hp", 20, "击杀奖励(按目标最大生命值转化;中立生物减半)");
        MAGE_EXP_SOUL = pctDef(b, "exp_soul", 20, "每点灵魂能量消耗获得的熟练度(5 点灵魂 = 1 熟练度即填 20)");
        MAGE_L1_SPELL_POTENCY = b.comment("Lv.1 基础:法术强度").defineInRange("l1_spell_potency", 2, 0, 100000);
        MAGE_L1_CASTING_SPEED = pctDef(b, "l1_casting_speed", 5, "Lv.1 基础:施法速度");
        MAGE_L1_SOUL_DISCOUNT = pctDef(b, "l1_soul_discount", 2, "Lv.1 基础:灵魂消耗折扣");
        MAGE_L1_SPELL_RADIUS = pctDef(b, "l1_spell_radius", 5, "Lv.1 基础:法术作用半径");
        MAGE_PER_SPELL_POTENCY = b.comment("每级成长:法术强度(可小数)").defineInRange("per_spell_potency", 0.5D, 0.0D, 1000.0D);
        MAGE_PER_CASTING_SPEED = b.comment("每级成长:施法速度(百分比,可小数)").defineInRange("per_casting_speed", 0.5D, 0.0D, 100.0D);
        MAGE_PER_SOUL_DISCOUNT = b.comment("每级成长:灵魂消耗折扣(百分比,可小数)").defineInRange("per_soul_discount", 0.15D, 0.0D, 100.0D);
        MAGE_PER_SPELL_RADIUS = b.comment("每级成长:法术作用半径(百分比,可小数)").defineInRange("per_spell_radius", 1.0D, 0.0D, 100.0D);
        M5_MAX_STACKS = b.comment("【元素共鸣】最大层数").defineInRange("m5_max_stacks", 5, 1, 100);
        M5_WINDOW_SECONDS = b.comment("【元素共鸣】层数保留窗口(秒)").defineInRange("m5_window_seconds", 15, 1, 600);
        M5_POTENCY_PER_STACK = b.comment("【元素共鸣】每层法术强度(百分比,可小数)").defineInRange("m5_potency_per_stack", 7.5D, 0.0D, 100.0D);
        M5_SOUL_DISCOUNT_PER_STACK = b.comment("【元素共鸣】每层灵魂折扣(百分比,可小数)").defineInRange("m5_soul_discount_per_stack", 7.5D, 0.0D, 100.0D);
        M10_BASE_CRIT_CHANCE = pctDef(b, "m10_base_crit_chance", 10, "【法爆】初始法术暴击率");
        M10_BASE_CRIT_DAMAGE = pctDef(b, "m10_base_crit_damage", 135, "【法爆】初始法术暴击伤害(135 = 135%)");
        M15_REFUND_CHANCE = pctDef(b, "m15_refund_chance", 30, "【灵魂虹吸】普通命中返还灵魂概率");
        M15_REFUND_RATIO = pctDef(b, "m15_refund_ratio", 50, "【灵魂虹吸】普通命中返还比例");
        M15_DEBUFF_REFUND_CHANCE = pctDef(b, "m15_debuff_refund_chance", 60, "【灵魂虹吸】目标带负面效果时的返还概率");
        M15_DEBUFF_REFUND_RATIO = pctDef(b, "m15_debuff_refund_ratio", 75, "【灵魂虹吸】目标带负面效果时的返还比例");
        M15_SPELL_DURATION = pctDef(b, "m15_spell_duration", 10, "【灵魂虹吸】常驻法术持续时间加成");
        M20_DURATION_SECONDS = b.comment("【巫术过载】持续时间(秒)").defineInRange("m20_duration", 15, 1, 3600);
        M20_CASTING_SPEED = pctDef(b, "m20_casting_speed", 80, "【巫术过载】施法速度加成");
        M20_SOUL_DISCOUNT = pctDef(b, "m20_soul_discount", 50, "【巫术过载】灵魂消耗折扣(50 = 消耗减半)");
        M20_SPELL_POTENCY = pctDef(b, "m20_spell_potency", 30, "【巫术过载】法术强度加成");
        M20_COOLDOWN_DISCOUNT = pctDef(b, "m20_cooldown_discount", 25, "【巫术过载】法术冷却折扣");
        M20_SPELL_RADIUS = pctDef(b, "m20_spell_radius", 20, "【巫术过载】法术作用半径加成");
        M20_MOVE_SPEED = pctDef(b, "m20_move_speed", 20, "【巫术过载】移动速度加成");
        M20_COOLDOWN_SECONDS = b.comment("【巫术过载】冷却(秒)").defineInRange("m20_cooldown", 70, 0, 36000);
        M25_SOUL_PER_SECOND = b.comment("【灵魂燃烧】每秒消耗的灵魂").defineInRange("m25_soul_per_second", 50, 1, 100000);
        M25_STACK_INTERVAL_SECONDS = b.comment("【灵魂燃烧】叠层间隔(秒)").defineInRange("m25_stack_interval", 2, 1, 600);
        M25_MAX_STACKS = b.comment("【灵魂燃烧】最大层数").defineInRange("m25_max_stacks", 10, 1, 100);
        M25_POTENCY_PER_STACK = pctDef(b, "m25_potency_per_stack", 10, "【灵魂燃烧】每层法术强度加成");
        M25_CASTING_SPEED_PER_STACK = pctDef(b, "m25_casting_speed_per_stack", 5, "【灵魂燃烧】每层施法速度加成");
        M25_CRIT_CHANCE_PER_STACK = pctDef(b, "m25_crit_chance_per_stack", 2, "【灵魂燃烧】每层法术暴击率加成");
        M25_COOLDOWN_SECONDS = b.comment("【灵魂燃烧】关闭后冷却(秒)").defineInRange("m25_cooldown", 30, 0, 36000);
        M30_REFRESH_CHANCE = pctDef(b, "m30_refresh_chance", 20, "【弹幕风暴】刷新法术冷却的基础概率");
        M30_PITY_INCREMENT = pctDef(b, "m30_pity_increment", 15, "【弹幕风暴】未触发时的概率递增幅度");
        M30_BARRAGE_POTENCY = pctDef(b, "m30_barrage_potency", 35, "【弹幕风暴】副法术强度(相对主法术)");
        M30_BARRAGE_ANGLE = b.comment("【弹幕风暴】三连发偏移角度(度)").defineInRange("m30_barrage_angle", 5, 1, 90);
        M30_SPELL_RADIUS = pctDef(b, "m30_spell_radius", 25, "【弹幕风暴】常驻法术作用半径加成");
        b.pop();

        // ==================== 召唤师勋章(SUMMONER / Goety 仆从底座) ====================
        b.comment("------------------------------ 召唤师勋章 ------------------------------").push("summoner");
        SUMMONER_EXP_SERVANT_DAMAGE = pctDef(b, "exp_servant_damage", 20, "仆从每点有效伤害获得的熟练度(溢出伤害不计)");
        SUMMONER_EXP_KILL_HP = pctDef(b, "exp_kill_hp", 20, "仆从击杀奖励(按目标最大生命值转化;中立生物减半)");
        SUMMONER_L1_SERVANT_HEALTH = pctDef(b, "l1_servant_health", 5, "Lv.1 基础:仆从最大生命");
        SUMMONER_L1_SERVANT_DAMAGE = pctDef(b, "l1_servant_damage", 5, "Lv.1 基础:仆从伤害");
        SUMMONER_L1_SERVANT_SPEED = pctDef(b, "l1_servant_speed", 5, "Lv.1 基础:仆从移动速度");
        SUMMONER_L1_SERVANT_LIFESPAN = pctDef(b, "l1_servant_lifespan", 25, "Lv.1 基础:仆从寿命延长");
        SUMMONER_PER_SERVANT_HEALTH = b.comment("每级成长:仆从最大生命(百分比,可小数)").defineInRange("per_servant_health", 1.0D, 0.0D, 100.0D);
        SUMMONER_PER_SERVANT_DAMAGE = b.comment("每级成长:仆从伤害(百分比,可小数)").defineInRange("per_servant_damage", 1.0D, 0.0D, 100.0D);
        SUMMONER_PER_SERVANT_SPEED = b.comment("每级成长:仆从移动速度(百分比,可小数)").defineInRange("per_servant_speed", 0.5D, 0.0D, 100.0D);
        SUMMONER_PER_SERVANT_LIFESPAN = b.comment("每级成长:仆从寿命延长(百分比,可小数)").defineInRange("per_servant_lifespan", 2.5D, 0.0D, 1000.0D);
        S5_SOUL_PER_HIT = b.comment("【灵魂纽带】仆从每次造成伤害获得的灵魂").defineInRange("su5_soul_per_hit", 2, 0, 100000);
        S5_SOUL_COOLDOWN = b.comment("【灵魂纽带】同一仆从的回魂间隔(tick)").defineInRange("su5_soul_cooldown", 20, 0, 72000);
        S10_SERVANT_REDUCTION = pctDef(b, "su10_servant_reduction", 10, "【骸骨硬化】仆从常驻减伤");
        S15_REVIVE_CHANCE = pctDef(b, "su15_revive_chance", 25, "【亡者复苏】仆从死亡时的复活概率");
        S15_REVIVE_HEALTH = pctDef(b, "su15_revive_health", 25, "【亡者复苏】复活时的血量比例");
        S20_DURATION_SECONDS = b.comment("【军团号令】持续(秒)").defineInRange("su20_duration", 20, 1, 3600);
        S20_SERVANT_DAMAGE = pctDef(b, "su20_servant_damage", 30, "【军团号令】仆从伤害加成");
        S20_SERVANT_LIMIT = b.comment("【军团号令】仆从召唤上限增加").defineInRange("su20_servant_limit", 3, 0, 1000);
        S20_COOLDOWN_SECONDS = b.comment("【军团号令】冷却(秒)").defineInRange("su20_cooldown", 60, 0, 36000);
        S25_EXTRA_DAMAGE = b.comment("【魂火】仆从攻击附带的额外法术伤害(可小数)").defineInRange("su25_extra_damage", 6.0D, 0.0D, 100000.0D);
        S30_SERVANT_LIMIT = b.comment("【亡者大军】仆从召唤上限增加").defineInRange("su30_servant_limit", 5, 0, 1000);
        S30_COOLDOWN_DISCOUNT = pctDef(b, "su30_cooldown_discount", 20, "【亡者大军】召唤法术冷却折扣");
        S30_SERVANT_HEALTH = pctDef(b, "su30_servant_health", 10, "【亡者大军】仆从最大生命加成");
        b.pop();

        // ==================== 「契约」类召唤师饰品 ====================
        b.comment("------------------------------ 契约饰品(召唤师) ------------------------------").push("contracts");
        CONTRACT_REFRESH_INTERVAL = b.comment("仆从属性刷新间隔(tick)。越小越实时,越大越省性能")
                .defineInRange("refresh_interval", 20, 1, 200);
        CONTRACT_DOMINION_HEALTH = pctDef(b, "dominion_health", 30, "【统御之契】仆从最大生命加成");
        CONTRACT_DOMINION_DAMAGE = pctDef(b, "dominion_damage", 25, "【统御之契】仆从伤害加成");
        CONTRACT_DOMINION_SOUL = b.comment("【统御之契】仆从击杀获得的灵魂").defineInRange("dominion_soul", 10, 0, 100000);
        CONTRACT_LEGION_LIMIT = b.comment("【军团之契】仆从召唤上限增加").defineInRange("legion_limit", 3, 0, 1000);
        CONTRACT_LEGION_LIFE = pctDef(b, "legion_life", 100, "【军团之契】仆从寿命延长");
        CONTRACT_SYMBIOSIS_RATIO = pctDef(b, "symbiosis_ratio", 20, "【共生之契】由最近仆从替你承受的伤害比例");
        CONTRACT_SYMBIOSIS_RADIUS = b.comment("【共生之契】寻找替身仆从的半径(格,可小数)").defineInRange("symbiosis_radius", 16.0D, 1.0D, 128.0D);
        CONTRACT_MARTYR_RADIUS = b.comment("【殉爆之契】仆从死亡爆炸半径(格,可小数)").defineInRange("martyr_radius", 4.0D, 1.0D, 64.0D);
        CONTRACT_MARTYR_RATIO = pctDef(b, "martyr_ratio", 50, "【殉爆之契】爆炸伤害(按仆从最大生命值折算)");
        CONTRACT_SOULCHAIN_POTENCY = pctDef(b, "soulchain_potency", 8, "【魂链之契】每个存活仆从提供的法术强度");
        CONTRACT_SOULCHAIN_MAX = b.comment("【魂链之契】计入的仆从数量上限").defineInRange("soulchain_max", 5, 1, 100);
        CONTRACT_FLESH_RATIO = pctDef(b, "flesh_ratio", 50, "【血肉之契】仆从死亡时按最大生命值比例治疗你");
        CONTRACT_BONE_REDUCTION = pctDef(b, "bone_reduction", 4, "【骨甲之契】每个存活仆从提供的伤害减免");
        CONTRACT_BONE_MAX = b.comment("【骨甲之契】计入的仆从数量上限").defineInRange("bone_max", 5, 1, 100);
        CONTRACT_FISSION_CHANCE = pctDef(b, "fission_chance", 20, "【裂变之契】召唤时额外多召一个的概率");
        CONTRACT_FISSION_RATIO = pctDef(b, "fission_ratio", 50, "【裂变之契】额外仆从的血量与寿命比例");
        CONTRACT_DECREE_VULNERABLE = pctDef(b, "decree_vulnerable", 10, "【敕令之契】目标「脆弱」期间的受伤加成");
        CONTRACT_DECREE_SECONDS = b.comment("【敕令之契】「脆弱」持续(秒)").defineInRange("decree_seconds", 5, 1, 600);
        CONTRACT_OVERLORD_HEALTH = pctDef(b, "overlord_health", 100, "【王者之主】仆从最大生命加成");
        CONTRACT_OVERLORD_DAMAGE = pctDef(b, "overlord_damage", 100, "【王者之主】仆从伤害加成");
        CONTRACT_OVERLORD_SPEED = pctDef(b, "overlord_speed", 30, "【王者之主】仆从移动速度加成");
        CONTRACT_OVERLORD_LIMIT = b.comment("【王者之主】仆从召唤上限增加").defineInRange("overlord_limit", 100, 0, 10000);
        b.pop();

        // ==================== 战士终极:「我有一剑！」 ====================
        // 这是一把剑,不是饰品 —— 常驻属性直接挂在物品的 MAINHAND 修饰符上,
        // 主动技是 Shift + 右键蓄力 10 秒后斩出的一道巨型剑气。
        b.comment("------------------------------ 我有一剑！ ------------------------------").push("i_have_a_sword");
        SWORD_CRIT_CHANCE = pctDef(b, "sword_crit_chance", 100, "常驻:暴击率加成(100 = 刀刀暴击)");
        SWORD_CRIT_DAMAGE = pctDef(b, "sword_crit_damage", 100, "常驻:暴击伤害加成(100 = 翻倍)");
        SWORD_ATTACK_DAMAGE = pctDef(b, "sword_attack_damage", 100, "常驻:攻击伤害加成(100 = 翻倍)");
        SWORD_EXP_GAINED = pctDef(b, "sword_exp_gained", 1000, "常驻:经验获取加成(1000 = ×11)");
        SWORD_DAMAGE = b.comment("剑气对每个敌人造成的伤害(每个目标各算一份,不随距离衰减)")
                .defineInRange("sword_slash_damage", 5000, 0, 1000000);
        SWORD_CHARGE_SECONDS = b.comment("蓄力时长(秒),满时自动斩出").defineInRange("sword_charge_seconds", 10, 1, 600);
        SWORD_COOLDOWN_SECONDS = b.comment("斩出后的冷却(秒)。0 = 只靠蓄力限制").defineInRange("sword_cooldown_seconds", 0, 0, 36000);
        SWORD_REACH = b.comment("剑气扫出的距离(格,可小数)").defineInRange("sword_reach", 50.0D, 1.0D, 512.0D);
        SWORD_WIDTH = b.comment("剑气横向宽度(格,可小数)").defineInRange("sword_width", 9.0D, 1.0D, 128.0D);
        SWORD_HEIGHT = b.comment("剑气高度(格,可小数)").defineInRange("sword_height", 4.0D, 1.0D, 64.0D);
        SWORD_ANIM_TICKS = b.comment("剑气飞完全程所需 tick(纯视觉)").defineInRange("sword_anim_ticks", 10, 1, 200);
        SWORD_BREAK_BLOCKS = b.comment("是否破坏剑气路径上的方块(硬度为负的方块始终保留)")
                .define("sword_break_blocks", true);
        SWORD_HIT_PLAYERS = b.comment("是否连同玩家一起斩(默认 false,免得误伤队友)")
                .define("sword_hit_players", false);
        b.pop();

        // ==================== 体验改进 · 第 1 项:原版经验等级大改 ====================
        // 作用对象:Minecraft【原版玩家经验等级】,与勋章熟练度(ExpTable)无关。
        b.comment("------------------------------ 体验改进:原版经验等级 ------------------------------")
                .push("experience");
        VX_ENABLED = b.comment("总开关:false 时完全回退原版经验公式")
                .define("enabled", true);
        VX_BASE_EXP = b.comment("曲线起点:从 0 级升到 1 级所需经验(原版为 7)")
                .defineInRange("base_exp", 7, 1, 100000);
        VX_GROWTH_1_40 = pctDef(b, "growth_1_40", 10, "1~40 级增长率(百分比)。10 = 每级 ×1.10");
        VX_GROWTH_40_70 = pctDef(b, "growth_40_70", 8, "40~70 级增长率(百分比)。8 = 每级 ×1.08");
        VX_GROWTH_70_100 = pctDef(b, "growth_70_100", 4, "70~100 级增长率(百分比)。4 = 每级 ×1.04");
        VX_GROWTH_AFTER_100 = pctDef(b, "growth_after_100", 2, "101 级及以上基础增长率(百分比)。2 = 2%");
        VX_DECAY_PERCENT = b.comment("每跨越一个台阶时,增长率乘以的百分比。95 = ×0.95")
                .defineInRange("decay_percent", 95, 1, 100);
        VX_DECAY_STEP = b.comment("递减台阶跨度(级)。25 表示在 125/150/175… 级递减")
                .defineInRange("decay_step", 25, 1, 1000);
        VX_MIN_GROWTH_MILLI = b.comment("增长率下限(放大 1000 倍存储):5 表示 0.005%,低于此值后固定不再递减")
                .defineInRange("min_growth_milli", 5, 0, 100000);
        b.pop();

        // ==================== 体验改进(QoL)============================
        b.comment("------------------------------ 体验改进(QoL) ------------------------------")
                .push("qol");

        b.comment("A1 药水无限续杯:背包内**同效药水总数 >= 16 瓶**时,持续获得该效果的无限时长;跌破 16 瓶立即失去。"
                + "注意:该门槛依赖「药水堆叠 16」开启,否则原版上限为 1、永远凑不满。").push("potion_infinite");
        QOL_POTION_INFINITE = b.comment("总开关").define("enabled", true);
        QOL_POTION_SCAN_BACKPACK = b.comment("是否同时扫描【精妙背包】内的药水(未装该模组时此项无效果)").define("scan_backpack", true);
        b.pop();

        b.comment("A2 无限 buff 共享:持有【药水无限续杯】无限效果的玩家,把该效果共享给附近玩家。").push("buff_share");
        QOL_BUFF_SHARE = b.comment("总开关").define("enabled", true);
        QOL_BUFF_SHARE_RADIUS = b.comment("共享半径(格,上限 32)").defineInRange("radius", 32, 1, 32);
        b.pop();

        b.comment("B3 更好的不死图腾:图腾放在背包也能生效(兼容精妙背包)。").push("totem_backpack");
        QOL_TOTEM_BACKPACK = b.comment("总开关").define("enabled", true);
        b.pop();

        b.comment("B4 修改原版鞘翅:额外提供护甲与盔甲韧性。").push("elytra_armor");
        QOL_ELYTRA_ARMOR = b.comment("总开关").define("enabled", true);
        QOL_ELYTRA_ARMOR_VALUE = b.comment("额外护甲值").defineInRange("armor", 10, 0, 1000);
        QOL_ELYTRA_TOUGHNESS_VALUE = b.comment("额外盔甲韧性").defineInRange("toughness", 4, 0, 1000);
        b.pop();

        b.comment("B5 死亡保留 Buff:死亡后不清空身上效果。").push("keep_buffs_on_death");
        QOL_KEEP_BUFFS_ON_DEATH = b.comment("总开关").define("enabled", true);
        b.pop();

        b.comment("C6 任意时间睡觉:取消原版仅夜晚/雷雨才能入睡的限制。").push("sleep_anytime");
        QOL_SLEEP_ANYTIME = b.comment("总开关").define("enabled", true);
        b.pop();

        b.comment("C7 睡觉跳过时间:每次睡眠成功把时间向前推进若干小时。").push("sleep_skip");
        QOL_SLEEP_SKIP_HOURS = b.comment("跳过的小时数(1 小时 = 1000 tick)").defineInRange("hours", 6, 1, 24);
        b.pop();

        b.comment("C8 一人睡觉全场跳过时间(原版需全体玩家就寝)。").push("one_player_sleep");
        QOL_ONE_PLAYER_SLEEP = b.comment("总开关").define("enabled", true);
        b.pop();

        b.comment("D9 耕地保护:在耕地上跳跃不再踩坏耕地。").push("farmland_protect");
        QOL_FARMLAND_PROTECT = b.comment("总开关").define("enabled", true);
        b.pop();

        b.comment("D10 饱食度满后仍可继续进食。").push("eat_when_full");
        QOL_EAT_WHEN_FULL = b.comment("总开关").define("enabled", true);
        b.pop();

        b.comment("D11 凋零骷髅头掉落率提升至 15%(概率固定,不可调节)。").push("wither_skull");
        QOL_WITHER_SKULL = b.comment("总开关(关闭则回退原版 2.5% 概率)").define("enabled", true);
        b.pop();

        b.comment("D12 死亡掉落物集中:所有掉落物落在死亡点,不再四散。").push("death_drop_centered");
        QOL_DEATH_DROP_CENTERED = b.comment("总开关").define("enabled", true);
        b.pop();

        b.comment("E13 自动攻击:手持武器按住左键,攻击冷却满且指向目标时自动挥出。").push("auto_attack");
        QOL_AUTO_ATTACK = b.comment("总开关").define("enabled", true);
        QOL_AUTO_ATTACK_REQUIRE_TARGET = b.comment("是否要求准星指向目标(关闭则空挥也触发)").define("require_target", true);
        b.pop();

        b.comment("E14 自动射击:拉满弓后自动发射。").push("auto_shoot");
        QOL_AUTO_SHOOT = b.comment("总开关").define("enabled", true);
        b.pop();

        b.comment("F15 村民交易次数 12 -> 64(次数固定,不可调节)。").push("villager_trades_64");
        QOL_VILLAGER_TRADES_64 = b.comment("总开关").define("enabled", true);
        b.pop();

        b.comment("F16 村民交易界面【刷新交易】按钮。").push("refresh_trades");
        QOL_REFRESH_TRADES = b.comment("总开关").define("enabled", true);
        b.pop();

        b.comment("F17 附魔台【刷新附魔】按钮。").push("refresh_enchant");
        QOL_REFRESH_ENCHANT = b.comment("总开关").define("enabled", true);
        QOL_REFRESH_ENCHANT_COST_LEVELS = b.comment("每次刷新消耗的经验等级").defineInRange("cost_levels", 1, 0, 100);
        QOL_REFRESH_ENCHANT_COST_LAPIS = b.comment("每次刷新消耗的青金石数量").defineInRange("cost_lapis", 1, 0, 64);
        b.pop();

        b.comment("F18 附魔台保留青金石:关闭界面后青金石留在台内。").push("keep_lapis");
        QOL_KEEP_LAPIS = b.comment("总开关").define("enabled", true);
        b.pop();

        b.comment("F19 无限与经验修补不再冲突(可共存于同一件物品)。").push("infinity_mending");
        QOL_INFINITY_MENDING = b.comment("总开关").define("enabled", true);
        b.pop();

        b.comment("F20 附魔台一次预览全部附魔选项(原版仅展示一条)。").push("enchant_preview_all");
        QOL_ENCHANT_PREVIEW_ALL = b.comment("总开关").define("enabled", true);
        b.pop();

        // ============================================================
        // G 组 · 堆叠类
        // ------------------------------------------------------------
        // 四种「放宽堆叠上限」的改动。数值固定(16 / 16 / 64 / 16),
        // 不暴露具体数字,只给总开关。
        // ============================================================
        b.comment("G21 药水可堆叠:同种药水的堆叠上限由 1 放宽至 16。").push("stack_potion");
        QOL_STACK_POTION = b.comment("总开关").define("enabled", true);
        b.pop();

        b.comment("G22 不死图腾可堆叠:堆叠上限由 1 放宽至 16。").push("stack_totem");
        QOL_STACK_TOTEM = b.comment("总开关").define("enabled", true);
        b.pop();

        b.comment("G23 末影珍珠可堆叠:堆叠上限由 16 放宽至 64。").push("stack_ender_pearl");
        QOL_STACK_ENDER_PEARL = b.comment("总开关").define("enabled", true);
        b.pop();

        b.comment("G24 无限附魔的弓无需箭矢:背包内没有箭也能拉弓射击,",
                "射出的箭为创造模式箭(落地即消失,不可捡回)。").push("infinite_bow_no_arrow");
        QOL_INFINITE_BOW_NO_ARROW = b.comment("总开关").define("enabled", true);
        b.pop();

        b.pop();

        // ============================================================
        // 终末之影(整合包终局 Boss)
        // ============================================================
        b.comment("终末之影 —— 整合包的最终 Boss。",
                "位于独立的『遗落境』维度,需点亮四相祭坛才能破除终末护盾。",
                "注意:修改本节的数值只影响新开的战斗,进行中的战斗不会实时生效。")
                .push("boss");

        BOSS_ENABLED = b.comment("总开关:关闭后召唤符失效,已有战斗仍可正常结束。")
                .define("enabled", true);

        BOSS_MAX_HEALTH = b.comment("Boss 最大生命值").defineInRange("max_health", 800.0D, 100.0D, 10000.0D);

        BOSS_PHASE_2_PERCENT = b.comment("进入阶段Ⅱ(崩解)的血量阈值(%)")
                .defineInRange("phase_2_percent", 60, 1, 99);
        BOSS_PHASE_3_PERCENT = b.comment("进入阶段Ⅲ(终末)的血量阈值(%)")
                .defineInRange("phase_3_percent", 25, 1, 99);

        BOSS_JUDGEMENT_SECONDS = b.comment("终末审判的基础读条时长(秒,下限 6 秒)",
                "玩家四勋章等级总和越高,读条越短(最多缩短 6 秒)。")
                .defineInRange("judgement_seconds", 12, 6, 60);

        BOSS_SENTINEL_LIMIT = b.comment("竞技场内同时存在的守望者仆从上限")
                .defineInRange("sentinel_limit", 6, 0, 32);

        BOSS_MEDAL_REQUIREMENT = b.comment("任务链要求四枚勋章各达到的等级(用于授予『四相共鸣』成就)")
                .defineInRange("medal_requirement", 20, 1, 100);

        // ---- dv5:遗落之主新流程 ----
        b.comment("决斗演出 —— 玩家在零阶段选择「决斗」后,遗落之主会以幽灵姿态冲来一击,",
                "扣除玩家最大生命值的一个固定百分比,随后消失并刷出波次怪物。").push("duel");
        BOSS_DUEL_HIT_PERCENT = b.comment("决斗一击扣除玩家最大生命值的百分比(%)",
                "默认 50 → 无论装备多好,这一击都会带走一半血。")
                .defineInRange("hit_percent", 50, 1, 100);
        b.pop();

        b.comment("波次 —— 决斗之后刷出的小怪浪潮,全部清空后遗落之主才会真正登场。",
                "默认使用原版怪物,可通过「怪物种类」项切换。").push("wave");
        BOSS_WAVE_COUNT = b.comment("波数(1~10)")
                .defineInRange("count", 5, 1, 10);
        BOSS_WAVE_MOB_COUNT = b.comment("每波怪物数量(1~20)")
                .defineInRange("mob_count", 5, 1, 20);
        BOSS_WAVE_INTERVAL_SECONDS = b.comment("两波之间的间隔(秒)")
                .defineInRange("interval_seconds", 8, 1, 60);
        b.pop();

        BOSS_WAVE_MOB_TYPE = b.comment("波次怪物种类。0 = 僵尸,1 = 骷髅,2 = 蜘蛛,3 = 掠夺者,4 = 凋灵骷髅。")
                .defineInRange("wave_mob_type", 0, 0, 4);

        b.comment("形态转换 —— 血量将降到 1 时锁定为 1,随后逐渐回满并换一个形态。",
                "回血的同时 Boss 体型会从小到大平滑长大,并伴随灵魂柱/冲击波/心跳声演出。").push("form");
        BOSS_TRANSITION_HEAL_SECONDS = b.comment("每次锁血后回满所需的秒数(默认 30,给玩家准备时间)")
                .defineInRange("transition_heal_seconds", 30, 5, 120);
        b.pop();

        // ---------- 箭类饰品(dv42) ----------
        // 五枚箭饰品都要求同时佩戴「射手勋章」才生效(见 ArrowCharmType#isActive)。
        b.comment("箭类饰品 —— 需要同时佩戴射手勋章,否则完全不生效。",
                "五枚共用「箭」饰品槽(默认 2 格),同一种只能戴一枚。").push("arrows");

        ARROW_POTION_CHANCE = pctDef(b, "potion_chance", 35, "药水箭:命中时触发负面效果的概率");
        ARROW_POTION_SECONDS = b.comment("药水箭:负面效果持续时间(秒)")
                .defineInRange("potion_seconds", 5, 1, 120);

        ARROW_EXPLOSION_POWER = b.comment("爆炸箭:爆炸威力(原版苦力怕为 3.0;仅「拉满蓄力」射出的箭才会爆炸)",
                "该爆炸永远不破坏方块。")
                .defineInRange("explosion_power", 1.8D, 0.0D, 20.0D);

        ARROW_PIERCE_COUNT = b.comment("贯穿之箭:能穿透的目标数量(2 表示总计可命中 3 个)")
                .defineInRange("pierce_count", 2, 1, 20);
        ARROW_PIERCE_FALLOFF = pctDef(b, "pierce_falloff", 25, "贯穿之箭:被穿透目标的伤害保留比例");

        ARROW_NO_GRAVITY_SECONDS = b.comment("反重力之箭:箭矢最长飞行时间(秒),超时自动消失",
                "不再下坠之后必须给个上限,否则会一直飞到世界边界。")
                .defineInRange("no_gravity_seconds", 8, 1, 60);

        ARROW_THUNDER_DAMAGE = b.comment("雷鸣箭:落雷对范围目标的雷电伤害")
                .defineInRange("thunder_damage", 6.0D, 0.0D, 10000.0D);
        ARROW_THUNDER_RADIUS = b.comment("雷鸣箭:落雷伤害半径(格,以命中点为中心)")
                .defineInRange("thunder_radius", 3, 1, 16);
        ARROW_THUNDER_PARALYZE_SECONDS = b.comment("雷鸣箭:麻痹(缓慢+虚弱)持续时间(秒)")
                .defineInRange("thunder_paralyze_seconds", 2, 0, 30);


        ARROW_TWIN_CHANCE = pctDef(b, "twin_chance", 15, "双响之箭:命中时追加一次射击的概率");
        ARROW_TWIN_DAMAGE = pctDef(b, "twin_damage", 25, "双响之箭:追加那一发的伤害强度");

        ARROW_ARMOR_PIERCE = pctDef(b, "armor_pierce", 30, "穿甲箭:命中时无视的护甲值比例");

        ARROW_HOLD_CHANCE = pctDef(b, "hold_chance", 50, "定身箭:使目标无法动弹的概率");
        ARROW_HOLD_SECONDS = b.comment("定身箭:定身持续时间(秒)")
                .defineInRange("hold_seconds", 2, 1, 30);

        ARROW_SUMMON_MAX = b.comment("召唤箭:同一玩家可同时存在的召唤物上限")
                .defineInRange("summon_max", 5, 1, 64);
        ARROW_SUMMON_COOLDOWN_SECONDS = b.comment("召唤箭:召唤冷却(秒)")
                .defineInRange("summon_cooldown_seconds", 3, 0, 300);
        ARROW_SUMMON_HEALTH = b.comment("召唤箭:召唤物的最大生命值(2 = 一颗心)")
                .defineInRange("summon_health", 2.0D, 1.0D, 100.0D);

        ARROW_STAR_DAMAGE = b.comment("星辰贯穿之箭:单发伤害(默认 10000)")
                .defineInRange("star_damage", 10000.0D, 1.0D, 1.0E7D);
        ARROW_STAR_CHARGE_SECONDS = b.comment("星辰贯穿之箭:必须拉满的蓄力时间(秒);拉满后自动射出,未拉满松手不发射")
                .defineInRange("star_charge_seconds", 10, 1, 60);
        ARROW_STAR_BREAK_BLOCKS = b.comment("星辰贯穿之箭:是否摧毁飞行路径上的方块",
                "只摧毁「铁镐及以下能挖」的方块 —— 基岩/黑曜石/远古残骸会保留。")
                .define("star_break_blocks", true);
        ARROW_STAR_PIERCE = b.comment("星辰贯穿之箭:能穿透的目标数量(8 表示总计可命中 9 个)")
                .defineInRange("star_pierce", 8, 0, 64);
        ARROW_STAR_FLIGHT_SECONDS = b.comment("星辰贯穿之箭:最长飞行时间(秒),超时自动消失",
                "星辰箭不再下坠,必须给个上限,否则会一路飞到世界边界。")
                .defineInRange("star_flight_seconds", 30, 1, 120);
        ARROW_STAR_BREAK_RADIUS = b.comment("星辰贯穿之箭:破坏半径(格,以箭矢为中心)")
                .defineInRange("star_break_radius", 1.5D, 0.5D, 8.0D);
        ARROW_STAR_BLOCK_DROPS = b.comment("星辰贯穿之箭:被摧毁的方块是否掉落物品",
                "默认关闭 —— 一箭能犁掉几百格,全掉落会直接把世界卡住。")
                .define("star_block_drops", false);
        b.pop();

        // ---------- 「刃」类战士饰品(dv49) ----------
        b.comment("「刃」类战士饰品 —— 只能放进「武器」槽(该槽位由战士勋章撑开)。").push("warrior_charms");

        WARRIOR_SWEEP_RADIUS = b.comment("横扫之刃:横扫范围(格,原版约 3 格)",
                "佩戴后疾跑时同样能触发横扫。")
                .defineInRange("sweep_radius", 4.5D, 1.0D, 12.0D);

        WARRIOR_DUAL_DAMAGE = pctDef(b, "dual_damage", 10, "双持之刃:主副手都有武器时的伤害加成");
        WARRIOR_DUAL_ATTACK_SPEED = pctDef(b, "dual_attack_speed", 10, "双持之刃:主副手都有武器时的攻速加成");

        WARRIOR_CRIT_CHANCE = pctDef(b, "crit_chance", 10, "暴击之刃:暴击率加成");
        WARRIOR_CRIT_DAMAGE = pctDef(b, "crit_damage", 20, "暴击之刃:暴击伤害加成");

        WARRIOR_BACKSTAB_BONUS = pctDef(b, "backstab_bonus", 15, "背刺之刃:从背后攻击时的伤害加成");

        WARRIOR_DISARM_CHANCE = pctDef(b, "disarm_chance", 15, "缴械之刃:触发缴械的概率");
        WARRIOR_DISARM_SECONDS = b.comment("缴械之刃:缴械持续时间(秒),期间目标无法造成伤害")
                .defineInRange("disarm_seconds", 2, 1, 30);
        WARRIOR_DISARM_COOLDOWN = b.comment("缴械之刃:同一个目标两次被缴械之间的最短间隔(秒)")
                .defineInRange("disarm_cooldown", 8, 0, 300);

        WARRIOR_LUCK_RANGE = pctDef(b, "luck_range", 20, "运气之刃:单次攻击的伤害浮动幅度(上下各该值)");
        WARRIOR_LUCK_PER_POINT = pctDef(b, "luck_per_point", 4,
                "运气之刃:每 1 点幸运值抬升「最低伤害」的比例");
        WARRIOR_LUCK_FLOOR_CAP = pctDef(b, "luck_floor_cap", 20,
                "运气之刃:最低伤害最多能抬升多少(到达此值后伤害只会正收益)");

        WARRIOR_TOUGHNESS_PER_HIT = pctDef(b, "toughness_per_hit", 5,
                "坚韧之刃:同一个敌人每再命中你一次,该次伤害额外降低的比例");
        WARRIOR_TOUGHNESS_BASE_CAP = pctDef(b, "toughness_base_cap", 15,
                "坚韧之刃:满血时的减伤上限");
        WARRIOR_TOUGHNESS_CAP_PER_STEP = pctDef(b, "toughness_cap_per_step", 5,
                "坚韧之刃:每损失 25% 生命,减伤上限额外提高的比例");
        WARRIOR_TOUGHNESS_WINDOW_SECONDS = b.comment("坚韧之刃:多久没再挨同一个敌人的打就重新计数(秒)")
                .defineInRange("toughness_window_seconds", 5, 1, 60);

        // ---------- 「盾」类坦克饰品 ----------
        b.comment("「盾」类坦克饰品 —— 只能放进「盾牌」槽(该槽位由坦克勋章撑开)。").push("shields");

        SHIELD_THORNS_REFLECT = pctDef(b, "thorns_reflect", 20, "荆棘盾：反弹伤害的比例");
        SHIELD_ADAPT_PER_HIT = pctDef(b, "adapt_per_hit", 5, "适应盾：同类型伤害每再中一次减免的比例");
        SHIELD_ADAPT_CAP = pctDef(b, "adapt_cap", 20, "适应盾：单类型减免上限");
        SHIELD_ADAPT_TYPES = b.comment("适应盾：最多同时记录几种伤害类型").defineInRange("adapt_types", 2, 1, 8);
        SHIELD_ADAPT_WINDOW = b.comment("适应盾：记录保留时间(秒)").defineInRange("adapt_window", 5, 1, 60);
        SHIELD_SHOCKWAVE_RADIUS = b.comment("震荡盾：冲击波半径(格)").defineInRange("shockwave_radius", 4, 1, 32);
        SHIELD_SHOCKWAVE_STUN = b.comment("震荡盾：眩晕时长(秒)").defineInRange("shockwave_stun", 2, 0, 60);
        SHIELD_SACRIFICE_RATIO = pctDef(b, "sacrifice_ratio", 20, "牺牲盾：转移给周围敌人的伤害比例");
        SHIELD_SACRIFICE_RADIUS = b.comment("牺牲盾：转移范围(格)").defineInRange("sacrifice_radius", 3, 1, 32);
        SHIELD_SACRIFICE_MAX = b.comment("牺牲盾：最多转移给几个敌人").defineInRange("sacrifice_max", 5, 1, 32);
        SHIELD_SACRIFICE_COOLDOWN = b.comment("牺牲盾：冷却(秒)").defineInRange("sacrifice_cooldown", 8, 0, 300);
        SHIELD_EARTH_SECONDS = b.comment("大地盾：需要静止几秒才生效").defineInRange("earth_seconds", 5, 1, 60);
        SHIELD_EARTH_REDUCTION = pctDef(b, "earth_reduction", 15, "大地盾：生效后的额外减伤");
        SHIELD_DEVOUR_PER_KILL = b.comment("吞噬盾：每发现一个新物种提升的最大生命").defineInRange("devour_per_kill", 0.5D, 0.0D, 100000.0D);
        SHIELD_DEVOUR_MAX = b.comment("吞噬盾：最大生命提升的上限").defineInRange("devour_max", 20.0D, 0.0D, 100000.0D);
        SHIELD_NOURISH_HP_THRESHOLD = pctDef(b, "nourish_hp_threshold", 15, "反哺盾：生命低于该比例时开始生效");
        SHIELD_NOURISH_PER_FOOD = b.comment("反哺盾：每 1 点饱食度可抵挡的伤害").defineInRange("nourish_per_food", 5.0D, 0.0D, 100000.0D);
        SHIELD_NOURISH_MAX_FOOD = b.comment("反哺盾：单次最多消耗几点饱食度").defineInRange("nourish_max_food", 2, 0, 20);
        SHIELD_VITALITY_PER_LAYER = pctDef(b, "vitality_per_layer", 10, "生机盾：每层提升的治疗量");
        SHIELD_VITALITY_MAX_LAYERS = b.comment("生机盾：最多叠加几层").defineInRange("vitality_max_layers", 3, 1, 16);
        SHIELD_VITALITY_WINDOW = b.comment("生机盾：层数的保留时间(秒)").defineInRange("vitality_window", 5, 1, 60);
        SHIELD_VANGUARD_ARMOR = pctDef(b, "vanguard_armor", 10, "先锋盾：副手持有盾牌时的护甲加成");
        SHIELD_VANGUARD_SPEED = pctDef(b, "vanguard_speed", 20, "先锋盾：举盾时的移动速度加成");
        SHIELD_VANGUARD_KNOCKBACK = b.comment("先锋盾：格挡后把攻击者击退的距离(格)").defineInRange("vanguard_knockback", 1.0D, 0.0D, 100000.0D);
        SHIELD_VANGUARD_PUSH = b.comment("先锋盾：举盾前进时推开敌人的力度").defineInRange("vanguard_push", 0.6D, 0.0D, 100000.0D);
        SHIELD_VANGUARD_PUSH_RADIUS = b.comment("先锋盾：推开敌人的判定半径(格)").defineInRange("vanguard_push_radius", 1.5D, 0.0D, 100000.0D);
        SHIELD_ETERNAL_ARMOR = pctDef(b, "eternal_armor", 100, "不朽神盾：护甲加成");
        SHIELD_ETERNAL_HEALTH = pctDef(b, "eternal_health", 80, "不朽神盾：最大生命加成");
        SHIELD_ETERNAL_REDUCTION = pctDef(b, "eternal_reduction", 50, "不朽神盾：伤害减免");
        SHIELD_ETERNAL_TOUGHNESS = pctDef(b, "eternal_toughness", 100, "不朽神盾：护甲韧性加成");
        SHIELD_ETERNAL_SPEED = pctDef(b, "eternal_speed", 20, "不朽神盾：移动速度加成");
        b.pop();


        WARRIOR_CHARGE_PER_SECOND = pctDef(b, "charge_per_second", 5, "蓄能之刃:每停手 1 秒积攒的伤害加成");
        WARRIOR_CHARGE_MAX = pctDef(b, "charge_max", 25, "蓄能之刃:积攒上限");

        WARRIOR_DASH_SPEED_THRESHOLD = b.comment("突进之刃:触发所需的武器攻速下限(武器本身的攻速,不含加成)")
                .defineInRange("dash_speed_threshold", 1.6D, 0.1D, 10.0D);
        WARRIOR_DASH_DISTANCE = b.comment("突进之刃:突进距离(格,撞墙即停)")
                .defineInRange("dash_distance", 4.0D, 1.0D, 16.0D);
        WARRIOR_DASH_DAMAGE = pctDef(b, "dash_damage", 150, "突进之刃:路径上目标承受的武器伤害比例");
        WARRIOR_DASH_COOLDOWN = b.comment("突进之刃:冷却(秒)")
                .defineInRange("dash_cooldown", 5, 0, 300);
        WARRIOR_DASH_HUNGER = b.comment("突进之刃:每次消耗的饥饿值(以消耗度计,4 点消耗度约等于 1 格饥饿)")
                .defineInRange("dash_hunger", 0.5D, 0.0D, 20.0D);
        WARRIOR_DASH_DOUBLE_TAP_TICKS = b.comment("突进之刃:判定「双击」的时间窗口(tick)")
                .defineInRange("dash_double_tap_ticks", 15, 2, 100);
        b.pop();

        // ---------- 「晶石」类法师饰品 ----------
        b.comment("「晶石」类法师饰品 —— 只能放进「聚晶」槽(该槽位由法师勋章撑开)。").push("crystals");

        CRYSTAL_CHAIN_RADIUS = pctDef(b, "chain_radius", 8, "连锁晶石:弹跳搜索半径(格)");
        CRYSTAL_CHAIN_RATIO = pctDef(b, "chain_ratio", 60, "连锁晶石:每一跳的伤害比例");
        CRYSTAL_CHAIN_JUMPS = pctDef(b, "chain_jumps", 3, "连锁晶石:最多弹跳几次");
        CRYSTAL_SNIPER_STEP = pctDef(b, "sniper_step", 3, "狙击晶石:每隔几格算一档");
        CRYSTAL_SNIPER_PER_STEP = pctDef(b, "sniper_per_step", 4, "狙击晶石:每档提升的法术强度");
        CRYSTAL_SNIPER_CAP = pctDef(b, "sniper_cap", 36, "狙击晶石:强度提升的上限");
        CRYSTAL_CIRCLE_RADIUS = pctDef(b, "circle_radius", 4, "法阵晶石:法阵半径(格)");
        CRYSTAL_CIRCLE_SECONDS = pctDef(b, "circle_seconds", 8, "法阵晶石:法阵持续时间(秒)");
        CRYSTAL_CIRCLE_SPEED = pctDef(b, "circle_speed", 30, "法阵晶石:站内施法速度加成");
        CRYSTAL_CIRCLE_POTENCY = pctDef(b, "circle_potency", 20, "法阵晶石:站内法术强度加成");
        CRYSTAL_SOULFIRE_REFUND = pctDef(b, "soulfire_refund", 50, "魂燃晶石:吞噬一个减益返还的灵魂比例");
        CRYSTAL_SOULFIRE_DAMAGE = pctDef(b, "soulfire_damage", 15, "魂燃晶石:每吞噬一个减益追加的伤害比例");
        CRYSTAL_OVERDRAFT_RATIO = pctDef(b, "overdraft_ratio", 20, "魔力透支晶石:1 点生命可换多少灵魂");
        CRYSTAL_OVERDRAFT_BONUS = pctDef(b, "overdraft_bonus", 30, "魔力透支晶石:透支施法时的法术强度加成");
        CRYSTAL_EMBER_SECONDS = pctDef(b, "ember_seconds", 3, "余烬晶石:余烬持续时间(秒)");
        CRYSTAL_EMBER_RATIO = pctDef(b, "ember_ratio", 20, "余烬晶石:余烬每秒造成的伤害比例");
        CRYSTAL_HEXNAIL_MAX = pctDef(b, "hexnail_max", 5, "咒钉晶石:最多叠加几枚咒钉");
        CRYSTAL_HEXNAIL_DAMAGE = pctDef(b, "hexnail_damage", 20, "咒钉晶石:每枚咒钉引爆时的伤害比例");
        CRYSTAL_ECHO_CHANCE = pctDef(b, "echo_chance", 15, "回响晶石:触发音波的概率");
        CRYSTAL_ECHO_DAMAGE = pctDef(b, "echo_damage", 20, "回响晶石:音波造成的固定伤害");
        CRYSTAL_SWIFT_REDUCTION = pctDef(b, "swift_reduction", 50, "疾行晶石:施法减速的减免比例");
        CRYSTAL_SOVEREIGN_POTENCY = pctDef(b, "sovereign_potency", 150, "主宰晶石:法术强度加成");
        CRYSTAL_SOVEREIGN_CASTING = pctDef(b, "sovereign_casting", 80, "主宰晶石:施法速度加成");
        CRYSTAL_SOVEREIGN_RADIUS = pctDef(b, "sovereign_radius", 50, "主宰晶石:法术半径加成");
        CRYSTAL_SOVEREIGN_SOUL = pctDef(b, "sovereign_soul", 100, "主宰晶石:灵魂消耗折扣(100 = 完全不消耗)");
        b.pop();

        b.comment("职业专属饰品槽 —— 佩戴对应勋章才会解锁,摘下立刻收回。",
                "槽位默认 0 格,靠勋章上的 SlotAttribute 修饰符临时加上去。").push("job_slots");
        JOB_SLOT_SIZE = b.comment("每个职业专属槽位的数量(射手=箭 / 战士=武器 / 坦克=盾牌 / 法师=聚晶)")
                .defineInRange("size", 3, 1, 9);
        b.pop();

        b.comment("时间胶囊 —— 一次性消耗品,永久降低主动技能的冷却时长(不去扣减正在倒计时的冷却)。").push("time_capsule");
        TIME_CAPSULE_MAX_USES = b.comment("每个玩家累计最多生效次数(超出后右键无效、也不消耗)")
                .defineInRange("max_uses", 5, 1, 100);
        TIME_CAPSULE_SHORTEN_SECONDS = b.comment("每次使用固定缩短的技能冷却时长(秒)")
                .defineInRange("shorten_seconds", 2, 1, 600);
        GROWTH_MIGRATED = b.comment(
                        "内部标记:勋章每级成长的 0 值迁移是否已执行过。请勿手动修改。")
                .define("_growth_migrated_v1", false);
        LEVEL_CAP_MIGRATED = b.comment(
                        "内部标记:等级上限 30→60 的迁移是否已执行过。请勿手动修改。")
                .define("_level_cap_migrated_v1", false);
        EXP_CURVE_MIGRATED = b.comment(
                        "内部标记:经验成长曲线 10/8/7/6/5/3→7/6/5/4/3/2 的迁移是否已执行过。请勿手动修改。")
                .define("_exp_curve_migrated_v1", false);
        b.pop();

        b.pop();

        b.pop();
    
        }
    }

    /** 客户端配置(CLIENT)。 */
    public static class Client {
        // 熟练度 HUD 相关配置已移除(见类顶注释)。

        // ---- 更好的状态效果 HUD ----
        public BooleanValue EFFECT_HUD_ENABLED;
        public IntValue EFFECT_HUD_SCALE;
        public IntValue EFFECT_HUD_CORNER;
        public IntValue EFFECT_HUD_FLOW;
        public IntValue EFFECT_HUD_TIMER;
        public BooleanValue EFFECT_HUD_HARMFUL_BORDER;

        public Client(ForgeConfigSpec.Builder b) {

        b.comment("客户端显示选项").push("client");
        // ============================================================
        // 更好的状态效果 HUD
        // ============================================================
        b.comment(
                "更好的状态效果 HUD —— 接管原版右上角的状态效果图标,支持缩放、",
                "自定义位置、排列方向与倒计时显示方式。",
                "关闭后完全回退原版渲染。")
                .push("effect_hud");
        EFFECT_HUD_ENABLED = b.comment("总开关").define("enabled", true);
        EFFECT_HUD_SCALE = b.comment(
                "缩放百分比。可选 100 / 75 / 50 / 25,以原版图标大小(24px)为 100%。")
                .defineInRange("scale", 100, 25, 100);
        EFFECT_HUD_CORNER = b.comment(
                "停靠角落。0 = 左上,1 = 右上,2 = 左下,3 = 右下。")
                .defineInRange("corner", 1, 0, 3);
        EFFECT_HUD_FLOW = b.comment(
                "排列方向。0 = 横向(图标排成一行),1 = 纵向(图标排成一列)。")
                .defineInRange("flow", 1, 0, 1);
        EFFECT_HUD_TIMER = b.comment(
                "倒计时显示方式。0 = 不显示,1 = 分钟(mm:ss),2 = 秒,3 = 进度条。")
                .defineInRange("timer", 2, 0, 3);
        EFFECT_HUD_HARMFUL_BORDER = b.comment(
                "给减益(负面)效果加一圈红色外框,便于一眼辨认。")
                .define("harmful_border", true);
        b.pop();

        b.pop();
    
        }
    }

    /* ---------------------------------------------------------------
     * 配置辅助方法
     * --------------------------------------------------------------- */

    /** 定义一个「整数百分比」配置项。玩家填写 20 即代表 20%。 */
    private static IntValue pctDef(ForgeConfigSpec.Builder b, String path, int def, String comment) {
        return b.comment(comment + " [整数百分比: " + def + " 表示 " + def + "%]")
                .defineInRange(path, def, 0, 100000);
    }

    /**
     * 定义一个「允许小数的百分比」配置项。玩家填写 0.75 即代表 0.75%。
     *
     * <h2>为什么必须存在这一版</h2>
     * 勋章的「每级成长」有很多是 0.75% / 0.5% 这类小数
     * （战士每级 +0.75% 伤害、坦克每级 +0.5% 减免…）。
     * 早期这些项误用了 {@link #pctDef}（整数版）——
     * 「0.75」写不进 {@code defineInRange(int)}，于是默认值只能填 0，
     * <b>整个成长项被静默吞掉</b>：徽章升到满级，这几条加成都还停在 Lv.2 的值。
     *
     * <p>症状极具迷惑性 —— 日志里没有任何报错，只是"升级不加属性"。
     * 实战反馈就是："满级战士徽章只有暴击伤害涨了，其它三项还是 +5%"。
     * （暴击伤害的成长恰好是整数 1%，是全组里唯一活下来的那个。）
     *
     * <p>凡是「每级成长」都应当用本方法，不要用 {@link #pctDef}。
     */
    private static DoubleValue pctDDef(ForgeConfigSpec.Builder b, String path, double def, String comment) {
        return b.comment(comment + " [百分比,可小数: " + def + " 表示 " + def + "%]")
                .defineInRange(path, def, 0.0D, 100000.0D);
    }

    /** 读取整数百分比配置,除以 100 转换为小数系数。 */
    public static double pct(IntValue value) {
        return value.get() / 100.0D;
    }

    /** 读取整数百分比配置(指定默认值),除以 100 转换为小数系数。 */
    public static double pct(IntValue value, int fallback) {
        try {
            return value.get() / 100.0D;
        } catch (Throwable t) {
            return fallback / 100.0D;
        }
    }

    /**
     * 读取「以百分比书写、但允许小数」的配置,除以 100 转换为小数系数。
     *
     * <p>用于每级成长这类需要比 0.1% 更细粒度的数值
     * (如 {@code 0.15} 表示每级 +0.15%)。
     */
    public static double pctD(DoubleValue value) {
        return value.get() / 100.0D;
    }

    /** 便捷读取:某配置项的整数值。 */
    public static int intOf(IntValue value) {
        return value.get();
    }

    /**
     * 一次性修正「每级成长」被旧版整数配置吞成 0 的历史遗留。
     *
     * <h2>问题从哪来</h2>
     * beta11 之前，「每级成长」错用了整数版 {@link #pctDef}。
     * 像"每级 +0.75%"这种值写不进 {@code defineInRange(int)}，默认值只能填 0 ——
     * 于是玩家把勋章练到 Lv.60，这几项加成还停在 Lv.2 的数值上。
     *
     * <h2>为什么只改代码不够</h2>
     * Forge 的配置<b>一旦生成就不再套用新默认值</b>：老玩家的
     * {@code lost_realm-common.toml} 里那几个键已经写成 0 了，
     * 光把代码里的默认值改成 0.75 没用 —— 读到的仍然是文件里的 0。
     * 所以必须在配置加载之后主动改一次、并写回文件。
     *
     * <h2>判定依据</h2>
     * 「成长量 = 0」在语义上就是"这枚勋章升级不加任何属性"，
     * 没有任何正常配置长这样 —— 因此见到 0 即认定是历史遗留，改回设计值。
     * 代价是玩家没法再用配置把成长彻底关掉；考虑那样会让整枚勋章失去成长性，
     * 这个取舍是划算的（真想关，填 0.01 即可）。
     *
     * @return 实际修正的项数（0 表示不需要迁移）
     */
    public static int migrateGrowthDefaults() {
        // 只做一次 —— 之后玩家手动设成 0 就随他去（见 GROWTH_MIGRATED 的说明）
        if (GROWTH_MIGRATED == null || GROWTH_MIGRATED.get()) {
            return 0;
        }
        DoubleValue[] targets = {
                ARCHER_PER_ARROW_DMG, ARCHER_PER_DRAW_SPEED, ARCHER_PER_MOVE_SPEED, ARCHER_PER_DODGE,
                WARRIOR_PER_DAMAGE, WARRIOR_PER_ATTACK_SPEED, WARRIOR_PER_CRIT_CHANCE, WARRIOR_PER_CRIT_DAMAGE,
                TANK_PER_ARMOR, TANK_PER_MAX_HEALTH, TANK_PER_OVERHEAL, TANK_PER_DAMAGE_REDUCTION,
        };
        // 与定义处一一对应（顺序必须一致）
        double[] designed = {
                1.5D, 0.8D, 0.8D, 0.5D,
                0.75D, 0.5D, 0.5D, 1.0D,
                0.75D, 1.0D, 1.0D, 0.5D,
        };
        int fixed = 0;
        for (int i = 0; i < targets.length; i++) {
            DoubleValue v = targets[i];
            if (v == null || designed[i] <= 0.0D) {
                continue;
            }
            if (v.get() <= 0.0D) {
                v.set(designed[i]);
                fixed++;
            }
        }
        GROWTH_MIGRATED.set(true);
        SPEC.save();
        return fixed;
    }

    /**
     * 把旧存档的等级上限从 30 迁到 60（beta26）。
     *
     * <p>Forge 不会给已存在的配置套用新默认值 —— 老玩家的
     * {@code lost_realm-common.toml} 里写着 {@code max_level = 30}，
     * 光把代码默认值改成 60 是没用的，等级上限会一直卡在 30。
     *
     * <p>只改 {@code == 30} 的情况：那正是 beta25 及更早的默认值。
     * 玩家若手动调成 45，说明他有自己的打算，迁移不该覆盖。
     *
     * @return 是否真的改了
     */
    public static boolean migrateLevelCap() {
        if (LEVEL_CAP_MIGRATED == null || LEVEL_CAP_MIGRATED.get()) {
            return false;
        }
        boolean changed = false;
        if (MAX_LEVEL != null && MAX_LEVEL.get() == 30) {
            MAX_LEVEL.set(60);
            changed = true;
        }
        LEVEL_CAP_MIGRATED.set(true);
        SPEC.save();
        return changed;
    }

    /**
     * 把经验成长曲线从 10/8/7/6/5/3 迁到 7/6/5/4/3/2（beta28）。
     *
     * <p>同样是因为 Forge 不会给已存在的配置套用新默认值 —— 老玩家的
     * {@code lost_realm-common.toml} 里写着上一版的六个值，光改代码默认值没用。
     *
     * <p><b>只有六项恰好等于旧默认组合时才迁移</b>：只要有一项被玩家改过，
     * 就认为他有自己的打算，整组不动。这比逐项对比更安全 ——
     * 逐项改会把「只想调 1~10 级」的玩家后面五项一起覆盖掉。
     *
     * @return 是否真的改了
     */
    public static boolean migrateExpCurve() {
        if (EXP_CURVE_MIGRATED == null || EXP_CURVE_MIGRATED.get()) {
            return false;
        }
        boolean changed = false;
        if (EXP_GROWTH_1_10 != null
                && EXP_GROWTH_1_10.get() == 10
                && EXP_GROWTH_11_20.get() == 8
                && EXP_GROWTH_21_30.get() == 7
                && EXP_GROWTH_31_40.get() == 6
                && EXP_GROWTH_41_50.get() == 5
                && EXP_GROWTH_51_60.get() == 3) {
            EXP_GROWTH_1_10.set(7);
            EXP_GROWTH_11_20.set(6);
            EXP_GROWTH_21_30.set(5);
            EXP_GROWTH_31_40.set(4);
            EXP_GROWTH_41_50.set(3);
            EXP_GROWTH_51_60.set(2);
            changed = true;
        }
        EXP_CURVE_MIGRATED.set(true);
        SPEC.save();
        return changed;
    }

    public static List<String> medalIds() {
        return List.of("archer", "warrior", "tank", "mage");
    }
}