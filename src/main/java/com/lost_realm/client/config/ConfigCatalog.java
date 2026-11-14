package com.lost_realm.client.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraftforge.common.ForgeConfigSpec;

import com.lost_realm.config.MedalsConfig;

/**
 * <b>全部配置项的登记表</b> —— 可视化界面的数据源。
 *
 * <h2>用途</h2>
 * 把 {@link MedalsConfig} 里散落的两份 spec(客户端 / 通用)的每一个
 * {@code ConfigValue} 包装成 {@link ConfigOption},并按分类归位,
 * 供 {@link ConfigScreen} 渲染。
 *
 * <h2>为什么手写而不用反射</h2>
 * 反射虽能自动枚举 spec 里的条目,但拿不到三样关键信息:
 * <ol>
 *   <li><b>取值范围</b> —— {@code defineInRange} 的 min/max 不暴露在公开 API 上;</li>
 *   <li><b>可读的中文标签</b> —— 需要人工起名,自动生成会变成一堆下划线标识符;</li>
 *   <li><b>合理的分类归属</b> —— 例如 {@code QOL_REFRESH_ENCHANT_COST_LEVELS}
 *       应归入「附魔」而不是它字面所在的 {@code refresh_enchant} 段。</li>
 * </ol>
 * 因此这里显式登记。代价是新增配置项时要同步加一行 —— 已写入
 * {@code .codebuddy/项目规则.md} 作为约定。
 */
public final class ConfigCatalog {

    private static List<ConfigOption> OPTIONS;
    private static Map<String, List<ConfigOption>> BY_CATEGORY;

    /** 客户端配置的 spec(用于 save)。 */
    private static ForgeConfigSpec clientSpec() {
        return MedalsConfig.CLIENT_SPEC;
    }

    /** 通用 / 服务端配置的 spec(用于 save)。 */
    private static ForgeConfigSpec commonSpec() {
        return MedalsConfig.SPEC;
    }

    // ================================================================
    // 构建
    // ================================================================

    /** 惰性构建一次(配置值在游戏启动后才可用,不能在静态块里读)。 */
    public static synchronized List<ConfigOption> options() {
        if (OPTIONS == null) {
            build();
        }
        return OPTIONS;
    }

    public static List<ConfigOption> ofCategory(String categoryId) {
        options();
        if (ConfigCategory.OVERVIEW.id().equals(categoryId)) {
            return OPTIONS;
        }
        return BY_CATEGORY.getOrDefault(categoryId, List.of());
    }

    private static void add(ConfigOption o) {
        OPTIONS.add(o);
        BY_CATEGORY.computeIfAbsent(o.categoryId(), k -> new ArrayList<>()).add(o);
    }

    // ---- 便捷包装 ----

    private static void toggle(String id, ConfigCategory cat,
                               ForgeConfigSpec.BooleanValue v,
                               boolean def, ForgeConfigSpec spec) {
        add(ConfigOption.toggle(id, cat.id(),
                "config.lost_realm.opt." + id, spec, v, def));
    }

    private static void step(String id, ConfigCategory cat,
                             ForgeConfigSpec.IntValue v, int lo, int hi, int def,
                             ForgeConfigSpec spec) {
        ConfigOption o = new ConfigOption.Builder()
                .id(id).kind(ConfigOption.Kind.STEPPER)
                .langKey("config.lost_realm.opt." + id)
                .categoryId(cat.id())
                .intRange(v, lo, hi, spec)
                .build();
        o.setDefaultValue(def);
        add(o);
    }

    /**
     * 登记一个<b>小数</b>步进项。
     *
     * <p>注意与上面的 {@code step} 区分:{@code ConfigOption.Builder} 的
     * {@code intRange} 与 {@code doubleRange} 都返回 {@code Builder},
     * 由 {@code build()} 统一产出 {@code ConfigOption} —— 别把
     * {@code IntValue}/{@code DoubleValue} 混用。
     */
    private static void stepD(String id, ConfigCategory cat,
                              ForgeConfigSpec.DoubleValue v, double lo, double hi, double def,
                              ForgeConfigSpec spec) {
        ConfigOption o = new ConfigOption.Builder()
                .id(id).kind(ConfigOption.Kind.DOUBLE_STEPPER)
                .langKey("config.lost_realm.opt." + id)
                .categoryId(cat.id())
                .doubleRange(v, lo, hi, spec)
                .build();
        o.setDefaultValue(def);
        add(o);
    }

    // ================================================================
    // 登记
    // ================================================================

    private static void build() {
        OPTIONS = new ArrayList<>();
        BY_CATEGORY = new LinkedHashMap<>();

        final ForgeConfigSpec C = clientSpec();
        final ForgeConfigSpec S = commonSpec();

        // ============================================================
        // 体验改进 —— 以下全部归入同一个「体验改进」分类(ConfigCategory.QOL)
        // ------------------------------------------------------------
        // 组内按功能分节排列,沿用原先的分组边界以便对照旧版。
        // ------------------------------------------------------------
        // · 药水与增益
        // ============================================================

        toggle("potion_infinite", ConfigCategory.QOL,
                MedalsConfig.QOL_POTION_INFINITE, true, S);
        toggle("potion_scan_backpack", ConfigCategory.QOL,
                MedalsConfig.QOL_POTION_SCAN_BACKPACK, true, S);
        toggle("buff_share", ConfigCategory.QOL,
                MedalsConfig.QOL_BUFF_SHARE, true, S);
        step("buff_share_radius", ConfigCategory.QOL,
                MedalsConfig.QOL_BUFF_SHARE_RADIUS, 1, 32, 32, S);

        toggle("totem_backpack", ConfigCategory.QOL,
                MedalsConfig.QOL_TOTEM_BACKPACK, true, S);
        toggle("stack_totem", ConfigCategory.QOL,
                MedalsConfig.QOL_STACK_TOTEM, true, S);
        toggle("jei_fold_variants", ConfigCategory.QOL,
                MedalsConfig.QOL_JEI_FOLD_VARIANTS, true, S);

        toggle("elytra_armor", ConfigCategory.QOL,
                MedalsConfig.QOL_ELYTRA_ARMOR, true, S);
        step("elytra_armor_value", ConfigCategory.QOL,
                MedalsConfig.QOL_ELYTRA_ARMOR_VALUE, 0, 1000, 10, S);
        step("elytra_toughness_value", ConfigCategory.QOL,
                MedalsConfig.QOL_ELYTRA_TOUGHNESS_VALUE, 0, 1000, 4, S);

        // ============================================================
        // · 睡眠
        // ============================================================

        toggle("sleep_anytime", ConfigCategory.QOL,
                MedalsConfig.QOL_SLEEP_ANYTIME, true, S);
        step("sleep_skip_hours", ConfigCategory.QOL,
                MedalsConfig.QOL_SLEEP_SKIP_HOURS, 1, 24, 6, S);
        toggle("one_player_sleep", ConfigCategory.QOL,
                MedalsConfig.QOL_ONE_PLAYER_SLEEP, true, S);

        // ============================================================
        // · 种植与掉落
        // ============================================================

        toggle("farmland_protect", ConfigCategory.QOL,
                MedalsConfig.QOL_FARMLAND_PROTECT, true, S);
        toggle("eat_when_full", ConfigCategory.QOL,
                MedalsConfig.QOL_EAT_WHEN_FULL, true, S);
        toggle("wither_skull", ConfigCategory.QOL,
                MedalsConfig.QOL_WITHER_SKULL, true, S);

        // ============================================================
        // · 自动战斗
        // ============================================================

        toggle("auto_attack", ConfigCategory.QOL,
                MedalsConfig.QOL_AUTO_ATTACK, true, S);
        toggle("auto_attack_require_target", ConfigCategory.QOL,
                MedalsConfig.QOL_AUTO_ATTACK_REQUIRE_TARGET, true, S);
        toggle("auto_shoot", ConfigCategory.QOL,
                MedalsConfig.QOL_AUTO_SHOOT, true, S);
        toggle("infinite_bow_no_arrow", ConfigCategory.QOL,
                MedalsConfig.QOL_INFINITE_BOW_NO_ARROW, true, S);
        toggle("infinity_mending", ConfigCategory.QOL,
                MedalsConfig.QOL_INFINITY_MENDING, true, S);

        // ============================================================
        // · 村民交易
        // ============================================================

        toggle("villager_trades_64", ConfigCategory.QOL,
                MedalsConfig.QOL_VILLAGER_TRADES_64, true, S);
        toggle("refresh_trades", ConfigCategory.QOL,
                MedalsConfig.QOL_REFRESH_TRADES, true, S);

        // ============================================================
        // · 附魔
        // ============================================================

        toggle("refresh_enchant", ConfigCategory.QOL,
                MedalsConfig.QOL_REFRESH_ENCHANT, true, S);
        step("refresh_enchant_cost_levels", ConfigCategory.QOL,
                MedalsConfig.QOL_REFRESH_ENCHANT_COST_LEVELS, 0, 100, 1, S);
        step("refresh_enchant_cost_lapis", ConfigCategory.QOL,
                MedalsConfig.QOL_REFRESH_ENCHANT_COST_LAPIS, 0, 64, 1, S);
        toggle("keep_lapis", ConfigCategory.QOL,
                MedalsConfig.QOL_KEEP_LAPIS, true, S);
        toggle("enchant_preview_all", ConfigCategory.QOL,
                MedalsConfig.QOL_ENCHANT_PREVIEW_ALL, true, S);

        // ============================================================
        // · 堆叠上限
        // ============================================================

        toggle("stack_potion", ConfigCategory.QOL,
                MedalsConfig.QOL_STACK_POTION, true, S);
        toggle("stack_ender_pearl", ConfigCategory.QOL,
                MedalsConfig.QOL_STACK_ENDER_PEARL, true, S);
        // 注:stack_totem 归在图腾相关选项旁,因为对玩家来说它就是图腾调整

        // ============================================================
        // · 死亡处理
        // ============================================================

        toggle("keep_buffs_on_death", ConfigCategory.QOL,
                MedalsConfig.QOL_KEEP_BUFFS_ON_DEATH, true, S);
        toggle("death_drop_centered", ConfigCategory.QOL,
                MedalsConfig.QOL_DEATH_DROP_CENTERED, true, S);

        // ============================================================
        // 客户端 · 状态效果 HUD
        // ============================================================

        toggle("effect_hud_enabled", ConfigCategory.EFFECT_HUD,
                MedalsConfig.EFFECT_HUD_ENABLED, true, C);
        // ★ dv36：这里**必须**是 step 而不是 switchOpt。
        //
        //   switchOpt 的语义是「把 [lo, hi] 上的每个整数都渲染成一个按钮」——
        //   原先写成 switchOpt(25, 100) 会生成 76 个按钮，横排铺满整屏并溢出到
        //   侧边栏之外；而语言文件里只配了 25/50/75/100 四个后缀，
        //   其余 72 个按钮就退化成显示原始键名（玩家看到一排 “.lost_realm”）。
        //   缩放本身是连续数值，改用 STEPPER（界面里可直接输入数值）。
        step("effect_hud_scale", ConfigCategory.EFFECT_HUD,
                MedalsConfig.EFFECT_HUD_SCALE, 25, 100, 100, C);
        add(switchOpt("effect_hud_corner", ConfigCategory.EFFECT_HUD,
                MedalsConfig.EFFECT_HUD_CORNER, 0, 3, 1, C));
        add(switchOpt("effect_hud_flow", ConfigCategory.EFFECT_HUD,
                MedalsConfig.EFFECT_HUD_FLOW, 0, 1, 1, C));
        add(switchOpt("effect_hud_timer", ConfigCategory.EFFECT_HUD,
                MedalsConfig.EFFECT_HUD_TIMER, 0, 3, 2, C));
        toggle("effect_hud_harmful_border", ConfigCategory.EFFECT_HUD,
                MedalsConfig.EFFECT_HUD_HARMFUL_BORDER, true, C);

        // ============================================================
        // 客户端 · 视角相关
        // ------------------------------------------------------------
        // 注:原先的「勋章 HUD」分类已随屏幕左上角熟练度 HUD 一并移除 ——
        //    熟练度现在直接画在勋章物品的 tooltip 上(见 ItemMedal),
        //    因此不再需要 show_hud / hud_x / hud_y 三个配置项。
        // ============================================================

        // ============================================================
        // 原作数值 · 通用 / 经验
        // ============================================================

        step("max_level", ConfigCategory.EXP, MedalsConfig.MAX_LEVEL, 1, 1000, 60, S);
        step("base_exp", ConfigCategory.EXP, MedalsConfig.BASE_EXP,
                1, Integer.MAX_VALUE, 100, S);
        // 六段成长率：每 10 级一档
        step("exp_growth_1_10", ConfigCategory.EXP,
                MedalsConfig.EXP_GROWTH_1_10, 0, 1000, 7, S);
        step("exp_growth_11_20", ConfigCategory.EXP,
                MedalsConfig.EXP_GROWTH_11_20, 0, 1000, 6, S);
        step("exp_growth_21_30", ConfigCategory.EXP,
                MedalsConfig.EXP_GROWTH_21_30, 0, 1000, 5, S);
        step("exp_growth_31_40", ConfigCategory.EXP,
                MedalsConfig.EXP_GROWTH_31_40, 0, 1000, 4, S);
        step("exp_growth_41_50", ConfigCategory.EXP,
                MedalsConfig.EXP_GROWTH_41_50, 0, 1000, 3, S);
        step("exp_growth_51_60", ConfigCategory.EXP,
                MedalsConfig.EXP_GROWTH_51_60, 0, 1000, 2, S);
        step("max_modifiers_per_uuid", ConfigCategory.EXP,
                MedalsConfig.MAX_MODIFIERS_PER_UUID, 1, 64, 8, S);

        toggle("vx_enabled", ConfigCategory.EXP, MedalsConfig.VX_ENABLED, true, S);
        step("vx_base_exp", ConfigCategory.EXP, MedalsConfig.VX_BASE_EXP, 1, 100000, 7, S);
        step("vx_decay_percent", ConfigCategory.EXP,
                MedalsConfig.VX_DECAY_PERCENT, 1, 100, 95, S);
        step("vx_decay_step", ConfigCategory.EXP, MedalsConfig.VX_DECAY_STEP, 1, 1000, 25, S);
        step("vx_min_growth_milli", ConfigCategory.EXP,
                MedalsConfig.VX_MIN_GROWTH_MILLI, 0, 100000, 5, S);

        // ============================================================
        // 原作数值 · 射手
        // ============================================================

        step("s5_teleport_range", ConfigCategory.ARCHER,
                MedalsConfig.S5_TELEPORT_RANGE, 1, 64, 6, S);
        step("s5_min_height", ConfigCategory.ARCHER,
                MedalsConfig.S5_TELEPORT_MIN_HEIGHT, 1, 16, 2, S);
        step("s5_cooldown", ConfigCategory.ARCHER,
                MedalsConfig.S5_COOLDOWN_SECONDS, 0, 3600, 20, S);
        stepD("s10_damage_per_block", ConfigCategory.ARCHER,
                MedalsConfig.S10_DAMAGE_PER_1_25_BLOCK, 0.0, 1000.0, 1.0, S);
        step("s15_near_distance", ConfigCategory.ARCHER,
                MedalsConfig.S15_NEAR_DISTANCE, 0, 64, 2, S);
        step("s20_spread_min", ConfigCategory.ARCHER,
                MedalsConfig.S20_SPREAD_MIN_DEG, 0, 45, 1, S);
        step("s20_spread_max", ConfigCategory.ARCHER,
                MedalsConfig.S20_SPREAD_MAX_DEG, 0, 45, 3, S);
        step("s25_rain_height", ConfigCategory.ARCHER,
                MedalsConfig.S25_RAIN_HEIGHT, 1, 64, 6, S);
        step("s25_rain_area", ConfigCategory.ARCHER,
                MedalsConfig.S25_RAIN_AREA, 1, 16, 3, S);
        step("s25_rain_min", ConfigCategory.ARCHER,
                MedalsConfig.S25_RAIN_MIN_ARROWS, 1, 64, 4, S);
        step("s25_rain_max", ConfigCategory.ARCHER,
                MedalsConfig.S25_RAIN_MAX_ARROWS, 1, 64, 6, S);
        step("s30_duration", ConfigCategory.ARCHER,
                MedalsConfig.S30_DURATION_SECONDS, 1, 3600, 10, S);
        step("s30_max_extend", ConfigCategory.ARCHER,
                MedalsConfig.S30_MAX_EXTEND, 0, 3600, 5, S);
        step("s30_cooldown", ConfigCategory.ARCHER,
                MedalsConfig.S30_COOLDOWN_SECONDS, 0, 36000, 70, S);

        // ============================================================
        // 原作数值 · 刃
        // ============================================================

        step("b5_bleed_seconds", ConfigCategory.BLADE,
                MedalsConfig.B5_BLEED_SECONDS, 1, 3600, 5, S);
        step("b5_armor_pierce", ConfigCategory.BLADE,
                MedalsConfig.B5_BLEED_ARMOR_PIERCE, 0, 1000, 4, S);
        step("b10_armor_pierce_per_step", ConfigCategory.BLADE,
                MedalsConfig.B10_ARMOR_PIERCE_PER_STEP, 0, 1000, 2, S);
        step("b15_duration", ConfigCategory.BLADE,
                MedalsConfig.B15_DURATION_SECONDS, 1, 3600, 12, S);
        step("b15_fire_damage", ConfigCategory.BLADE,
                MedalsConfig.B15_FIRE_DAMAGE, 0, 1000, 5, S);
        step("b15_target_fire_seconds", ConfigCategory.BLADE,
                MedalsConfig.B15_TARGET_FIRE_SECONDS, 0, 3600, 10, S);
        step("b20_cold_damage", ConfigCategory.BLADE,
                MedalsConfig.B20_COLD_DAMAGE, 0, 1000, 5, S);
        step("b20_freeze_seconds", ConfigCategory.BLADE,
                MedalsConfig.B20_FREEZE_SECONDS, 1, 3600, 3, S);
        step("b25_duration", ConfigCategory.BLADE,
                MedalsConfig.B25_DURATION_SECONDS, 1, 3600, 10, S);
        step("b25_attack_range", ConfigCategory.BLADE,
                MedalsConfig.B25_ATTACK_RANGE, 0, 16, 1, S);
        step("b25_cooldown", ConfigCategory.BLADE,
                MedalsConfig.B25_COOLDOWN_SECONDS, 0, 36000, 70, S);

        // ============================================================
        // 原作数值 · 坦克
        // ============================================================

        stepD("t5_slow_weapon_attack_speed", ConfigCategory.TANK,
                MedalsConfig.T5_SLOW_WEAPON_ATTACK_SPEED, 0.0, 20.0, 1.0, S);
        step("t10_absorb_amount", ConfigCategory.TANK,
                MedalsConfig.T10_ABSORB_AMOUNT, 0, 1000, 4, S);
        step("t10_interval", ConfigCategory.TANK,
                MedalsConfig.T10_INTERVAL_SECONDS, 0, 3600, 3, S);
        step("t15_shield_interval", ConfigCategory.TANK,
                MedalsConfig.T15_SHIELD_INTERVAL, 1, 36000, 45, S);
        step("t15_shield_max_block", ConfigCategory.TANK,
                MedalsConfig.T15_SHIELD_MAX_BLOCK, 1, 100000, 50, S);
        step("t15_shield_max_stacks", ConfigCategory.TANK,
                MedalsConfig.T15_SHIELD_MAX_STACKS, 1, 64, 3, S);
        step("t20_duration", ConfigCategory.TANK,
                MedalsConfig.T20_DURATION_SECONDS, 1, 3600, 15, S);
        step("t20_knockback_radius", ConfigCategory.TANK,
                MedalsConfig.T20_KNOCKBACK_RADIUS, 0, 64, 5, S);
        step("t20_knockback_distance", ConfigCategory.TANK,
                MedalsConfig.T20_KNOCKBACK_DISTANCE, 0, 64, 2, S);
        step("t20_burst_damage", ConfigCategory.TANK,
                MedalsConfig.T20_BURST_DAMAGE, 0, 100000, 12, S);
        step("t20_slowness_seconds", ConfigCategory.TANK,
                MedalsConfig.T20_SLOWNESS_SECONDS, 0, 3600, 5, S);
        step("t20_cooldown", ConfigCategory.TANK,
                MedalsConfig.T20_COOLDOWN_SECONDS, 0, 36000, 75, S);
        step("t25_block_value", ConfigCategory.TANK,
                MedalsConfig.T25_BLOCK_VALUE, 0, 100000, 20, S);
        step("t25_interval", ConfigCategory.TANK,
                MedalsConfig.T25_INTERVAL_SECONDS, 0, 3600, 2, S);
        stepD("t25_block_per_value", ConfigCategory.TANK,
                MedalsConfig.T25_BLOCK_PER_VALUE, 0.0, 1000.0, 1.5, S);
        step("t25_reset", ConfigCategory.TANK,
                MedalsConfig.T25_RESET_SECONDS, 1, 3600, 10, S);
        step("t30_duration", ConfigCategory.TANK,
                MedalsConfig.T30_DURATION_SECONDS, 1, 3600, 5, S);
        step("t30_absorb", ConfigCategory.TANK,
                MedalsConfig.T30_ABSORB, 0, 100000, 20, S);
        step("t30_cooldown", ConfigCategory.TANK,
                MedalsConfig.T30_COOLDOWN_SECONDS, 0, 36000, 90, S);

        // ============================================================
        // 原作数值 · 法师
        // ============================================================

        step("l1_spell_potency", ConfigCategory.MAGE,
                MedalsConfig.MAGE_L1_SPELL_POTENCY, 0, 100000, 2, S);
        stepD("per_spell_potency", ConfigCategory.MAGE,
                MedalsConfig.MAGE_PER_SPELL_POTENCY, 0.0, 1000.0, 0.5, S);
        stepD("per_casting_speed", ConfigCategory.MAGE,
                MedalsConfig.MAGE_PER_CASTING_SPEED, 0.0, 100.0, 0.5, S);
        stepD("per_soul_discount", ConfigCategory.MAGE,
                MedalsConfig.MAGE_PER_SOUL_DISCOUNT, 0.0, 100.0, 0.15, S);
        stepD("per_spell_radius", ConfigCategory.MAGE,
                MedalsConfig.MAGE_PER_SPELL_RADIUS, 0.0, 100.0, 1.0, S);
        step("m5_max_stacks", ConfigCategory.MAGE,
                MedalsConfig.M5_MAX_STACKS, 1, 100, 5, S);
        step("m5_window_seconds", ConfigCategory.MAGE,
                MedalsConfig.M5_WINDOW_SECONDS, 1, 600, 15, S);
        stepD("m5_potency_per_stack", ConfigCategory.MAGE,
                MedalsConfig.M5_POTENCY_PER_STACK, 0.0, 100.0, 7.5, S);
        stepD("m5_soul_discount_per_stack", ConfigCategory.MAGE,
                MedalsConfig.M5_SOUL_DISCOUNT_PER_STACK, 0.0, 100.0, 7.5, S);
        step("m20_duration", ConfigCategory.MAGE,
                MedalsConfig.M20_DURATION_SECONDS, 1, 3600, 15, S);
        step("m20_cooldown", ConfigCategory.MAGE,
                MedalsConfig.M20_COOLDOWN_SECONDS, 0, 36000, 70, S);
        step("m25_soul_per_second", ConfigCategory.MAGE,
                MedalsConfig.M25_SOUL_PER_SECOND, 1, 100000, 50, S);
        step("m25_stack_interval", ConfigCategory.MAGE,
                MedalsConfig.M25_STACK_INTERVAL_SECONDS, 1, 600, 2, S);
        step("m25_max_stacks", ConfigCategory.MAGE,
                MedalsConfig.M25_MAX_STACKS, 1, 100, 10, S);
        step("m25_cooldown", ConfigCategory.MAGE,
                MedalsConfig.M25_COOLDOWN_SECONDS, 0, 36000, 30, S);
        step("m30_barrage_angle", ConfigCategory.MAGE,
                MedalsConfig.M30_BARRAGE_ANGLE, 1, 90, 5, S);

        // ============================================================
        // 原作数值 · 召唤师
        // ------------------------------------------------------------
        // 与法师同一底座(Goety),但改造的对象是仆从而不是法术。
        // 「仆从四条属性」是本模组自注册的 —— Goety 自己不带仆从侧的强度概念。
        // ============================================================
        step("exp_servant_damage", ConfigCategory.SUMMONER,
                MedalsConfig.SUMMONER_EXP_SERVANT_DAMAGE, 0, 1000, 20, S);
        step("exp_kill_hp", ConfigCategory.SUMMONER,
                MedalsConfig.SUMMONER_EXP_KILL_HP, 0, 1000, 20, S);
        step("l1_servant_health", ConfigCategory.SUMMONER,
                MedalsConfig.SUMMONER_L1_SERVANT_HEALTH, 0, 1000, 5, S);
        step("l1_servant_damage", ConfigCategory.SUMMONER,
                MedalsConfig.SUMMONER_L1_SERVANT_DAMAGE, 0, 1000, 5, S);
        step("l1_servant_speed", ConfigCategory.SUMMONER,
                MedalsConfig.SUMMONER_L1_SERVANT_SPEED, 0, 1000, 5, S);
        step("l1_servant_lifespan", ConfigCategory.SUMMONER,
                MedalsConfig.SUMMONER_L1_SERVANT_LIFESPAN, 0, 10000, 25, S);
        stepD("per_servant_health", ConfigCategory.SUMMONER,
                MedalsConfig.SUMMONER_PER_SERVANT_HEALTH, 0.0, 100.0, 1.0, S);
        stepD("per_servant_damage", ConfigCategory.SUMMONER,
                MedalsConfig.SUMMONER_PER_SERVANT_DAMAGE, 0.0, 100.0, 1.0, S);
        stepD("per_servant_speed", ConfigCategory.SUMMONER,
                MedalsConfig.SUMMONER_PER_SERVANT_SPEED, 0.0, 100.0, 0.5, S);
        stepD("per_servant_lifespan", ConfigCategory.SUMMONER,
                MedalsConfig.SUMMONER_PER_SERVANT_LIFESPAN, 0.0, 1000.0, 2.5, S);
        step("su5_soul_per_hit", ConfigCategory.SUMMONER,
                MedalsConfig.S5_SOUL_PER_HIT, 0, 100000, 2, S);
        step("su5_soul_cooldown", ConfigCategory.SUMMONER,
                MedalsConfig.S5_SOUL_COOLDOWN, 0, 72000, 20, S);
        step("su10_servant_reduction", ConfigCategory.SUMMONER,
                MedalsConfig.S10_SERVANT_REDUCTION, 0, 90, 10, S);
        step("su15_revive_chance", ConfigCategory.SUMMONER,
                MedalsConfig.S15_REVIVE_CHANCE, 0, 100, 25, S);
        step("su15_revive_health", ConfigCategory.SUMMONER,
                MedalsConfig.S15_REVIVE_HEALTH, 1, 100, 25, S);
        step("su20_duration", ConfigCategory.SUMMONER,
                MedalsConfig.S20_DURATION_SECONDS, 1, 3600, 20, S);
        step("su20_servant_damage", ConfigCategory.SUMMONER,
                MedalsConfig.S20_SERVANT_DAMAGE, 0, 1000, 30, S);
        step("su20_servant_limit", ConfigCategory.SUMMONER,
                MedalsConfig.S20_SERVANT_LIMIT, 0, 1000, 3, S);
        step("su20_cooldown", ConfigCategory.SUMMONER,
                MedalsConfig.S20_COOLDOWN_SECONDS, 0, 36000, 60, S);
        stepD("su25_extra_damage", ConfigCategory.SUMMONER,
                MedalsConfig.S25_EXTRA_DAMAGE, 0.0, 10000.0, 6.0, S);
        step("su30_servant_limit", ConfigCategory.SUMMONER,
                MedalsConfig.S30_SERVANT_LIMIT, 0, 1000, 5, S);
        step("su30_cooldown_discount", ConfigCategory.SUMMONER,
                MedalsConfig.S30_COOLDOWN_DISCOUNT, 0, 1000, 20, S);
        step("su30_servant_health", ConfigCategory.SUMMONER,
                MedalsConfig.S30_SERVANT_HEALTH, 0, 1000, 10, S);

        // ============================================================
        // 契约饰品(召唤师) · 九枚 + 王者之主
        // ============================================================
        step("refresh_interval", ConfigCategory.CONTRACTS,
                MedalsConfig.CONTRACT_REFRESH_INTERVAL, 1, 200, 20, S);
        step("dominion_health", ConfigCategory.CONTRACTS,
                MedalsConfig.CONTRACT_DOMINION_HEALTH, 0, 1000, 30, S);
        step("dominion_damage", ConfigCategory.CONTRACTS,
                MedalsConfig.CONTRACT_DOMINION_DAMAGE, 0, 1000, 25, S);
        step("dominion_soul", ConfigCategory.CONTRACTS,
                MedalsConfig.CONTRACT_DOMINION_SOUL, 0, 100000, 10, S);
        step("legion_limit", ConfigCategory.CONTRACTS,
                MedalsConfig.CONTRACT_LEGION_LIMIT, 0, 1000, 3, S);
        step("legion_life", ConfigCategory.CONTRACTS,
                MedalsConfig.CONTRACT_LEGION_LIFE, 0, 10000, 100, S);
        step("symbiosis_ratio", ConfigCategory.CONTRACTS,
                MedalsConfig.CONTRACT_SYMBIOSIS_RATIO, 1, 90, 20, S);
        stepD("symbiosis_radius", ConfigCategory.CONTRACTS,
                MedalsConfig.CONTRACT_SYMBIOSIS_RADIUS, 1.0, 128.0, 16.0, S);
        stepD("martyr_radius", ConfigCategory.CONTRACTS,
                MedalsConfig.CONTRACT_MARTYR_RADIUS, 1.0, 64.0, 4.0, S);
        step("martyr_ratio", ConfigCategory.CONTRACTS,
                MedalsConfig.CONTRACT_MARTYR_RATIO, 0, 1000, 50, S);
        step("soulchain_potency", ConfigCategory.CONTRACTS,
                MedalsConfig.CONTRACT_SOULCHAIN_POTENCY, 0, 1000, 8, S);
        step("soulchain_max", ConfigCategory.CONTRACTS,
                MedalsConfig.CONTRACT_SOULCHAIN_MAX, 1, 100, 5, S);
        step("flesh_ratio", ConfigCategory.CONTRACTS,
                MedalsConfig.CONTRACT_FLESH_RATIO, 0, 1000, 50, S);
        step("bone_reduction", ConfigCategory.CONTRACTS,
                MedalsConfig.CONTRACT_BONE_REDUCTION, 0, 90, 4, S);
        step("bone_max", ConfigCategory.CONTRACTS,
                MedalsConfig.CONTRACT_BONE_MAX, 1, 100, 5, S);
        step("fission_chance", ConfigCategory.CONTRACTS,
                MedalsConfig.CONTRACT_FISSION_CHANCE, 0, 100, 20, S);
        step("fission_ratio", ConfigCategory.CONTRACTS,
                MedalsConfig.CONTRACT_FISSION_RATIO, 1, 100, 50, S);
        step("decree_vulnerable", ConfigCategory.CONTRACTS,
                MedalsConfig.CONTRACT_DECREE_VULNERABLE, 0, 1000, 10, S);
        step("decree_seconds", ConfigCategory.CONTRACTS,
                MedalsConfig.CONTRACT_DECREE_SECONDS, 1, 600, 5, S);
        step("overlord_health", ConfigCategory.CONTRACTS,
                MedalsConfig.CONTRACT_OVERLORD_HEALTH, 0, 10000, 100, S);
        step("overlord_damage", ConfigCategory.CONTRACTS,
                MedalsConfig.CONTRACT_OVERLORD_DAMAGE, 0, 10000, 100, S);
        step("overlord_speed", ConfigCategory.CONTRACTS,
                MedalsConfig.CONTRACT_OVERLORD_SPEED, 0, 10000, 30, S);
        step("overlord_limit", ConfigCategory.CONTRACTS,
                MedalsConfig.CONTRACT_OVERLORD_LIMIT, 0, 10000, 100, S);

        // ============================================================
        // 刃终极「我有一剑！」
        // ------------------------------------------------------------
        // 前四项是常驻属性(百分比),后九项是那道剑气本身的形状与威力。
        // reach / width / height 用 stepD —— 它们是格数,允许小数。
        // ============================================================
        step("sword_crit_chance", ConfigCategory.SWORD,
                MedalsConfig.SWORD_CRIT_CHANCE, 0, 10000, 100, S);
        step("sword_crit_damage", ConfigCategory.SWORD,
                MedalsConfig.SWORD_CRIT_DAMAGE, 0, 10000, 100, S);
        step("sword_attack_damage", ConfigCategory.SWORD,
                MedalsConfig.SWORD_ATTACK_DAMAGE, 0, 10000, 100, S);
        step("sword_exp_gained", ConfigCategory.SWORD,
                MedalsConfig.SWORD_EXP_GAINED, 0, 100000, 1000, S);
        step("sword_slash_damage", ConfigCategory.SWORD,
                MedalsConfig.SWORD_DAMAGE, 0, 1000000, 5000, S);
        step("sword_charge_seconds", ConfigCategory.SWORD,
                MedalsConfig.SWORD_CHARGE_SECONDS, 1, 600, 10, S);
        step("sword_cooldown_seconds", ConfigCategory.SWORD,
                MedalsConfig.SWORD_COOLDOWN_SECONDS, 0, 36000, 0, S);
        stepD("sword_reach", ConfigCategory.SWORD,
                MedalsConfig.SWORD_REACH, 1.0, 512.0, 50.0, S);
        stepD("sword_width", ConfigCategory.SWORD,
                MedalsConfig.SWORD_WIDTH, 1.0, 128.0, 9.0, S);
        stepD("sword_height", ConfigCategory.SWORD,
                MedalsConfig.SWORD_HEIGHT, 1.0, 64.0, 4.0, S);
        step("sword_anim_ticks", ConfigCategory.SWORD,
                MedalsConfig.SWORD_ANIM_TICKS, 1, 200, 10, S);
        toggle("sword_break_blocks", ConfigCategory.SWORD,
                MedalsConfig.SWORD_BREAK_BLOCKS, true, S);
        toggle("sword_hit_players", ConfigCategory.SWORD,
                MedalsConfig.SWORD_HIT_PLAYERS, false, S);

        // ============================================================
        // 终末之影(整合包终局 Boss)
        // ------------------------------------------------------------
        // 全部登记在 COMMON spec 下。注意 doubleValue 用 stepD 而不是 step,
        // 否则会被当作整数处理 —— 800.0 的生命值会被截断。
        // ============================================================





        // ------------------------------------------------------------
        // dv5 新增:决斗演出 / 波次 / 形态转换
        // ------------------------------------------------------------




        // ============================================================
        // 箭类饰品 / 时间胶囊(dv42)
        // ------------------------------------------------------------
        // 五枚箭饰品都要求同时佩戴射手勋章,数值都在这里调。
        // ============================================================
        step("arrow_potion_chance", ConfigCategory.ARROWS,
                MedalsConfig.ARROW_POTION_CHANCE, 1, 100, 35, S);
        step("arrow_potion_seconds", ConfigCategory.ARROWS,
                MedalsConfig.ARROW_POTION_SECONDS, 1, 120, 5, S);
        stepD("arrow_explosion_power", ConfigCategory.ARROWS,
                MedalsConfig.ARROW_EXPLOSION_POWER, 0.0, 20.0, 1.8, S);
        step("arrow_pierce_count", ConfigCategory.ARROWS,
                MedalsConfig.ARROW_PIERCE_COUNT, 1, 20, 2, S);
        step("arrow_pierce_falloff", ConfigCategory.ARROWS,
                MedalsConfig.ARROW_PIERCE_FALLOFF, 1, 100, 25, S);
        step("arrow_no_gravity_seconds", ConfigCategory.ARROWS,
                MedalsConfig.ARROW_NO_GRAVITY_SECONDS, 1, 60, 8, S);

        stepD("arrow_thunder_damage", ConfigCategory.ARROWS,
                MedalsConfig.ARROW_THUNDER_DAMAGE, 0.0, 10000.0, 6.0, S);
        step("arrow_thunder_radius", ConfigCategory.ARROWS,
                MedalsConfig.ARROW_THUNDER_RADIUS, 1, 16, 3, S);
        step("arrow_thunder_paralyze_seconds", ConfigCategory.ARROWS,
                MedalsConfig.ARROW_THUNDER_PARALYZE_SECONDS, 0, 30, 2, S);
        step("arrow_twin_chance", ConfigCategory.ARROWS,
                MedalsConfig.ARROW_TWIN_CHANCE, 1, 100, 15, S);
        step("arrow_twin_damage", ConfigCategory.ARROWS,
                MedalsConfig.ARROW_TWIN_DAMAGE, 1, 500, 25, S);
        step("arrow_armor_pierce", ConfigCategory.ARROWS,
                MedalsConfig.ARROW_ARMOR_PIERCE, 1, 100, 30, S);
        step("arrow_hold_chance", ConfigCategory.ARROWS,
                MedalsConfig.ARROW_HOLD_CHANCE, 1, 100, 50, S);
        step("arrow_hold_seconds", ConfigCategory.ARROWS,
                MedalsConfig.ARROW_HOLD_SECONDS, 1, 30, 2, S);
        step("arrow_summon_max", ConfigCategory.ARROWS,
                MedalsConfig.ARROW_SUMMON_MAX, 1, 64, 5, S);
        step("arrow_summon_cooldown_seconds", ConfigCategory.ARROWS,
                MedalsConfig.ARROW_SUMMON_COOLDOWN_SECONDS, 0, 300, 3, S);
        stepD("arrow_summon_health", ConfigCategory.ARROWS,
                MedalsConfig.ARROW_SUMMON_HEALTH, 1.0, 100.0, 2.0, S);

        stepD("arrow_star_damage", ConfigCategory.ARROWS,
                MedalsConfig.ARROW_STAR_DAMAGE, 1.0, 1000000.0, 10000.0, S);
        step("arrow_star_charge_seconds", ConfigCategory.ARROWS,
                MedalsConfig.ARROW_STAR_CHARGE_SECONDS, 1, 60, 10, S);
        step("arrow_star_pierce", ConfigCategory.ARROWS,
                MedalsConfig.ARROW_STAR_PIERCE, 0, 64, 8, S);
        step("arrow_star_flight_seconds", ConfigCategory.ARROWS,
                MedalsConfig.ARROW_STAR_FLIGHT_SECONDS, 1, 120, 30, S);
        stepD("arrow_star_break_radius", ConfigCategory.ARROWS,
                MedalsConfig.ARROW_STAR_BREAK_RADIUS, 0.5, 8.0, 1.5, S);
        toggle("arrow_star_break_blocks", ConfigCategory.ARROWS,
                MedalsConfig.ARROW_STAR_BREAK_BLOCKS, true, S);
        toggle("arrow_star_block_drops", ConfigCategory.ARROWS,
                MedalsConfig.ARROW_STAR_BLOCK_DROPS, false, S);

        // ---------- 「刃」类刃饰品(dv49) ----------
        stepD("blade_sweep_radius", ConfigCategory.BLADE_CHARMS,
                MedalsConfig.BLADE_SWEEP_RADIUS, 1.0, 12.0, 4.5, S);
        step("blade_dual_damage", ConfigCategory.BLADE_CHARMS,
                MedalsConfig.BLADE_DUAL_DAMAGE, 0, 500, 10, S);
        step("blade_dual_attack_speed", ConfigCategory.BLADE_CHARMS,
                MedalsConfig.BLADE_DUAL_ATTACK_SPEED, 0, 500, 10, S);
        step("blade_crit_chance", ConfigCategory.BLADE_CHARMS,
                MedalsConfig.BLADE_CRIT_CHANCE, 0, 100, 10, S);
        step("blade_crit_damage", ConfigCategory.BLADE_CHARMS,
                MedalsConfig.BLADE_CRIT_DAMAGE, 0, 1000, 20, S);
        step("blade_backstab_bonus", ConfigCategory.BLADE_CHARMS,
                MedalsConfig.BLADE_BACKSTAB_BONUS, 0, 1000, 15, S);
        step("blade_disarm_chance", ConfigCategory.BLADE_CHARMS,
                MedalsConfig.BLADE_DISARM_CHANCE, 1, 100, 15, S);
        step("blade_disarm_seconds", ConfigCategory.BLADE_CHARMS,
                MedalsConfig.BLADE_DISARM_SECONDS, 1, 30, 2, S);
        step("blade_disarm_cooldown", ConfigCategory.BLADE_CHARMS,
                MedalsConfig.BLADE_DISARM_COOLDOWN, 0, 300, 8, S);
        step("blade_luck_range", ConfigCategory.BLADE_CHARMS,
                MedalsConfig.BLADE_LUCK_RANGE, 0, 500, 20, S);
        step("blade_luck_per_point", ConfigCategory.BLADE_CHARMS,
                MedalsConfig.BLADE_LUCK_PER_POINT, 0, 100, 4, S);
        step("blade_luck_floor_cap", ConfigCategory.BLADE_CHARMS,
                MedalsConfig.BLADE_LUCK_FLOOR_CAP, 0, 500, 20, S);
        step("blade_charge_per_second", ConfigCategory.BLADE_CHARMS,
                MedalsConfig.BLADE_CHARGE_PER_SECOND, 1, 100, 5, S);
        step("blade_charge_max", ConfigCategory.BLADE_CHARMS,
                MedalsConfig.BLADE_CHARGE_MAX, 1, 1000, 25, S);
        stepD("blade_dash_speed_threshold", ConfigCategory.BLADE_CHARMS,
                MedalsConfig.BLADE_DASH_SPEED_THRESHOLD, 0.1, 10.0, 1.6, S);
        stepD("blade_dash_distance", ConfigCategory.BLADE_CHARMS,
                MedalsConfig.BLADE_DASH_DISTANCE, 1.0, 16.0, 4.0, S);
        step("blade_dash_damage", ConfigCategory.BLADE_CHARMS,
                MedalsConfig.BLADE_DASH_DAMAGE, 0, 2000, 150, S);
        step("blade_dash_cooldown", ConfigCategory.BLADE_CHARMS,
                MedalsConfig.BLADE_DASH_COOLDOWN, 0, 300, 5, S);
        stepD("blade_dash_hunger", ConfigCategory.BLADE_CHARMS,
                MedalsConfig.BLADE_DASH_HUNGER, 0.0, 20.0, 0.5, S);
        step("blade_dash_double_tap_ticks", ConfigCategory.BLADE_CHARMS,
                MedalsConfig.BLADE_DASH_DOUBLE_TAP_TICKS, 2, 100, 15, S);

        step("blade_toughness_per_hit", ConfigCategory.BLADE_CHARMS,
                MedalsConfig.BLADE_TOUGHNESS_PER_HIT, 0, 100, 5, S);
        step("blade_toughness_base_cap", ConfigCategory.BLADE_CHARMS,
                MedalsConfig.BLADE_TOUGHNESS_BASE_CAP, 0, 100, 15, S);
        step("blade_toughness_cap_per_step", ConfigCategory.BLADE_CHARMS,
                MedalsConfig.BLADE_TOUGHNESS_CAP_PER_STEP, 0, 100, 5, S);
        step("blade_toughness_window_seconds", ConfigCategory.BLADE_CHARMS,
                MedalsConfig.BLADE_TOUGHNESS_WINDOW_SECONDS, 1, 60, 5, S);

        // ---------- 「盾」类坦克饰品 ----------
        step("thorns_reflect", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_THORNS_REFLECT, 0, 500, 20, S);
        step("adapt_per_hit", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_ADAPT_PER_HIT, 0, 100, 5, S);
        step("adapt_cap", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_ADAPT_CAP, 0, 100, 20, S);
        step("adapt_types", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_ADAPT_TYPES, 1, 8, 2, S);
        step("adapt_window", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_ADAPT_WINDOW, 1, 60, 5, S);
        step("shockwave_radius", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_SHOCKWAVE_RADIUS, 1, 32, 4, S);
        step("shockwave_stun", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_SHOCKWAVE_STUN, 0, 60, 2, S);
        step("sacrifice_ratio", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_SACRIFICE_RATIO, 0, 100, 20, S);
        step("sacrifice_radius", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_SACRIFICE_RADIUS, 1, 32, 3, S);
        step("sacrifice_max", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_SACRIFICE_MAX, 1, 32, 5, S);
        step("sacrifice_cooldown", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_SACRIFICE_COOLDOWN, 0, 300, 8, S);
        step("earth_seconds", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_EARTH_SECONDS, 1, 60, 5, S);
        step("earth_reduction", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_EARTH_REDUCTION, 0, 100, 15, S);
        step("nourish_hp_threshold", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_NOURISH_HP_THRESHOLD, 1, 100, 15, S);
        step("nourish_max_food", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_NOURISH_MAX_FOOD, 0, 20, 2, S);
        step("vitality_per_layer", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_VITALITY_PER_LAYER, 0, 500, 10, S);
        step("vitality_max_layers", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_VITALITY_MAX_LAYERS, 1, 16, 3, S);
        step("vitality_window", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_VITALITY_WINDOW, 1, 60, 5, S);
        step("vanguard_armor", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_VANGUARD_ARMOR, 0, 500, 10, S);
        step("vanguard_speed", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_VANGUARD_SPEED, 0, 500, 20, S);
        step("eternal_armor", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_ETERNAL_ARMOR, 0, 1000, 100, S);
        step("eternal_health", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_ETERNAL_HEALTH, 0, 1000, 80, S);
        step("eternal_reduction", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_ETERNAL_REDUCTION, 0, 100, 50, S);
        step("eternal_toughness", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_ETERNAL_TOUGHNESS, 0, 1000, 100, S);
        step("eternal_speed", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_ETERNAL_SPEED, 0, 500, 20, S);
        stepD("devour_per_kill", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_DEVOUR_PER_KILL, 0.0, 100.0, 0.5, S);
        stepD("devour_max", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_DEVOUR_MAX, 0.0, 1000.0, 20.0, S);
        stepD("nourish_per_food", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_NOURISH_PER_FOOD, 0.0, 100.0, 5.0, S);
        stepD("vanguard_knockback", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_VANGUARD_KNOCKBACK, 0.0, 16.0, 1.0, S);
        stepD("vanguard_push", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_VANGUARD_PUSH, 0.0, 8.0, 0.6, S);
        stepD("vanguard_push_radius", ConfigCategory.SHIELDS, MedalsConfig.SHIELD_VANGUARD_PUSH_RADIUS, 0.5, 8.0, 1.5, S);

        // ---------- 「晶石」类法师饰品 ----------
        step("chain_radius", ConfigCategory.CRYSTALS, MedalsConfig.CRYSTAL_CHAIN_RADIUS, 1, 32, 8, S);
        step("chain_ratio", ConfigCategory.CRYSTALS, MedalsConfig.CRYSTAL_CHAIN_RATIO, 1, 100, 60, S);
        step("chain_jumps", ConfigCategory.CRYSTALS, MedalsConfig.CRYSTAL_CHAIN_JUMPS, 1, 10, 3, S);
        step("sniper_step", ConfigCategory.CRYSTALS, MedalsConfig.CRYSTAL_SNIPER_STEP, 1, 32, 3, S);
        step("sniper_per_step", ConfigCategory.CRYSTALS, MedalsConfig.CRYSTAL_SNIPER_PER_STEP, 0, 100, 4, S);
        step("sniper_cap", ConfigCategory.CRYSTALS, MedalsConfig.CRYSTAL_SNIPER_CAP, 0, 500, 36, S);
        step("circle_radius", ConfigCategory.CRYSTALS, MedalsConfig.CRYSTAL_CIRCLE_RADIUS, 1, 16, 4, S);
        step("circle_seconds", ConfigCategory.CRYSTALS, MedalsConfig.CRYSTAL_CIRCLE_SECONDS, 1, 60, 8, S);
        step("circle_speed", ConfigCategory.CRYSTALS, MedalsConfig.CRYSTAL_CIRCLE_SPEED, 0, 500, 30, S);
        step("circle_potency", ConfigCategory.CRYSTALS, MedalsConfig.CRYSTAL_CIRCLE_POTENCY, 0, 500, 20, S);
        step("soulfire_refund", ConfigCategory.CRYSTALS, MedalsConfig.CRYSTAL_SOULFIRE_REFUND, 0, 500, 50, S);
        step("soulfire_damage", ConfigCategory.CRYSTALS, MedalsConfig.CRYSTAL_SOULFIRE_DAMAGE, 0, 500, 15, S);
        step("overdraft_ratio", ConfigCategory.CRYSTALS, MedalsConfig.CRYSTAL_OVERDRAFT_RATIO, 1, 1000, 20, S);
        step("overdraft_bonus", ConfigCategory.CRYSTALS, MedalsConfig.CRYSTAL_OVERDRAFT_BONUS, 0, 500, 30, S);
        step("ember_seconds", ConfigCategory.CRYSTALS, MedalsConfig.CRYSTAL_EMBER_SECONDS, 1, 60, 3, S);
        step("ember_ratio", ConfigCategory.CRYSTALS, MedalsConfig.CRYSTAL_EMBER_RATIO, 0, 500, 20, S);
        step("hexnail_max", ConfigCategory.CRYSTALS, MedalsConfig.CRYSTAL_HEXNAIL_MAX, 1, 20, 5, S);
        step("hexnail_damage", ConfigCategory.CRYSTALS, MedalsConfig.CRYSTAL_HEXNAIL_DAMAGE, 0, 500, 20, S);
        step("echo_chance", ConfigCategory.CRYSTALS, MedalsConfig.CRYSTAL_ECHO_CHANCE, 1, 100, 15, S);
        step("echo_damage", ConfigCategory.CRYSTALS, MedalsConfig.CRYSTAL_ECHO_DAMAGE, 0, 10000, 20, S);
        step("swift_reduction", ConfigCategory.CRYSTALS, MedalsConfig.CRYSTAL_SWIFT_REDUCTION, 0, 100, 50, S);
        step("sovereign_potency", ConfigCategory.CRYSTALS, MedalsConfig.CRYSTAL_SOVEREIGN_POTENCY, 0, 1000, 150, S);
        step("sovereign_casting", ConfigCategory.CRYSTALS, MedalsConfig.CRYSTAL_SOVEREIGN_CASTING, 0, 1000, 80, S);
        step("sovereign_radius", ConfigCategory.CRYSTALS, MedalsConfig.CRYSTAL_SOVEREIGN_RADIUS, 0, 1000, 50, S);
        step("sovereign_soul", ConfigCategory.CRYSTALS, MedalsConfig.CRYSTAL_SOVEREIGN_SOUL, 0, 100, 100, S);

        step("job_slot_size", ConfigCategory.SLOTS,
                MedalsConfig.JOB_SLOT_SIZE, 1, 9, 3, S);

        step("time_capsule_max_uses", ConfigCategory.ARROWS,
                MedalsConfig.TIME_CAPSULE_MAX_USES, 1, 100, 5, S);
        step("time_capsule_shorten_seconds", ConfigCategory.ARROWS,
                MedalsConfig.TIME_CAPSULE_SHORTEN_SECONDS, 1, 600, 2, S);

        // ============================================================
        // 乌蠹诺斯(beta41)—— 新版终局 Boss
        // ------------------------------------------------------------
        // 这十项由 UdunosTuning.applyConfig() 在配置加载后灌进静态字段;
        // 界面里改完保存后要重启(或 /reload)才作用到 Boss 身上。
        // ============================================================

        stepD("udunos_health", ConfigCategory.UDUNOS, MedalsConfig.UDUNOS_HEALTH, 1.0, 100000.0, 500.0, S);
        stepD("udunos_cruise_speed", ConfigCategory.UDUNOS, MedalsConfig.UDUNOS_CRUISE_SPEED, 0.05, 10.0, 1.25, S);
        stepD("udunos_charge_speed", ConfigCategory.UDUNOS, MedalsConfig.UDUNOS_CHARGE_SPEED, 0.5, 20.0, 4.5, S);
        step("udunos_phase_1", ConfigCategory.UDUNOS, MedalsConfig.UDUNOS_PHASE_1, 1, 99, 80, S);
        step("udunos_phase_2", ConfigCategory.UDUNOS, MedalsConfig.UDUNOS_PHASE_2, 1, 99, 60, S);
        step("udunos_phase_3", ConfigCategory.UDUNOS, MedalsConfig.UDUNOS_PHASE_3, 1, 99, 15, S);
        stepD("udunos_contact_head", ConfigCategory.UDUNOS, MedalsConfig.UDUNOS_CONTACT_HEAD, 0.0, 200.0, 14.0, S);
        stepD("udunos_contact_body", ConfigCategory.UDUNOS, MedalsConfig.UDUNOS_CONTACT_BODY, 0.0, 200.0, 8.0, S);
        step("udunos_reduction_cap", ConfigCategory.UDUNOS, MedalsConfig.UDUNOS_REDUCTION_CAP, 0, 95, 75, S);
        step("udunos_leash", ConfigCategory.UDUNOS, MedalsConfig.UDUNOS_LEASH, 32, 1024, 256, S);

        // ============================================================
        // 补齐:此前只在配置文件里存在、界面上看不到的选项(beta39)
        // ------------------------------------------------------------
        // 共 97 项,全部是代码里确实在读的数值(没有死配置)。
        // 分类沿用字段前缀:ARCHER_/S* → 射手,W*/BLADE_ → 刃,
        // T*/TANK_ → 坦克,M*/MAGE_ → 法师,VX_ → 熟练度与经验。
        // ============================================================

        // ---- 原作数值 · 射手(补齐:熟练度转化 / 等级成长 / 技能细参) ----
        step("archer_exp_damage", ConfigCategory.ARCHER, MedalsConfig.ARCHER_EXP_DAMAGE, 0, 1000, 20, S);
        step("archer_exp_kill_hp", ConfigCategory.ARCHER, MedalsConfig.ARCHER_EXP_KILL_HP, 0, 1000, 15, S);
        step("archer_lv1_arrow_damage", ConfigCategory.ARCHER, MedalsConfig.ARCHER_L1_ARROW_DMG, 0, 1000, 8, S);
        step("archer_lv1_dodge", ConfigCategory.ARCHER, MedalsConfig.ARCHER_L1_DODGE, 0, 1000, 5, S);
        step("archer_lv1_draw_speed", ConfigCategory.ARCHER, MedalsConfig.ARCHER_L1_DRAW_SPEED, 0, 1000, 5, S);
        step("archer_lv1_move_speed", ConfigCategory.ARCHER, MedalsConfig.ARCHER_L1_MOVE_SPEED, 0, 1000, 5, S);
        stepD("archer_per_level_arrow_damage", ConfigCategory.ARCHER, MedalsConfig.ARCHER_PER_ARROW_DMG, 0, 1000, 1.5, S);
        stepD("archer_per_level_dodge", ConfigCategory.ARCHER, MedalsConfig.ARCHER_PER_DODGE, 0, 1000, 0.5, S);
        stepD("archer_per_level_draw_speed", ConfigCategory.ARCHER, MedalsConfig.ARCHER_PER_DRAW_SPEED, 0, 1000, 0.8, S);
        stepD("archer_per_level_move_speed", ConfigCategory.ARCHER, MedalsConfig.ARCHER_PER_MOVE_SPEED, 0, 1000, 0.8, S);
        step("s15_chance_drop", ConfigCategory.ARCHER, MedalsConfig.S15_CHANCE_DROP_PER_BLOCK, 0, 1000, 5, S);
        step("s15_min_chance", ConfigCategory.ARCHER, MedalsConfig.S15_MIN_CHANCE, 0, 1000, 15, S);
        step("s15_near_chance", ConfigCategory.ARCHER, MedalsConfig.S15_NEAR_CHANCE, 0, 1000, 45, S);
        step("s20_extra_arrow_damage", ConfigCategory.ARCHER, MedalsConfig.S20_EXTRA_ARROW_DAMAGE, 0, 1000, 25, S);
        step("s20_multishot_bonus", ConfigCategory.ARCHER, MedalsConfig.S20_MULTISHOT_BONUS, 0, 1000, 25, S);
        step("s25_rain_chance", ConfigCategory.ARCHER, MedalsConfig.S25_RAIN_CHANCE, 0, 1000, 20, S);
        step("s30_arrow_damage", ConfigCategory.ARCHER, MedalsConfig.S30_ARROW_DAMAGE, 0, 1000, 10, S);
        step("s30_draw_speed", ConfigCategory.ARCHER, MedalsConfig.S30_DRAW_SPEED, 0, 1000, 80, S);
        step("s30_move_speed", ConfigCategory.ARCHER, MedalsConfig.S30_MOVE_SPEED, 0, 1000, 20, S);
        step("s30_move_speed_mounted", ConfigCategory.ARCHER, MedalsConfig.S30_MOVE_SPEED_MOUNTED, 0, 1000, 30, S);
        step("s5_immune_chance", ConfigCategory.ARCHER, MedalsConfig.S5_DODGE_IMMUNE_CHANCE, 0, 1000, 60, S);

        // ---- 原作数值 · 刃(补齐) ----
        step("b10_hp_threshold", ConfigCategory.BLADE, MedalsConfig.B10_HP_THRESHOLD, 0, 1000, 60, S);
        step("b10_lifesteal_per_step", ConfigCategory.BLADE, MedalsConfig.B10_LIFESTEAL_PER_STEP, 0, 1000, 3, S);
        step("b15_attack_damage", ConfigCategory.BLADE, MedalsConfig.B15_ATTACK_DAMAGE, 0, 1000, 10, S);
        step("b15_hp_cost", ConfigCategory.BLADE, MedalsConfig.B15_HP_COST_PER_SECOND, 0, 1000, 4, S);
        step("b15_move_speed", ConfigCategory.BLADE, MedalsConfig.B15_MOVE_SPEED, 0, 1000, 15, S);
        step("b20_freeze_chance", ConfigCategory.BLADE, MedalsConfig.B20_FREEZE_CHANCE, 0, 1000, 10, S);
        step("b25_attack_speed", ConfigCategory.BLADE, MedalsConfig.B25_ATTACK_SPEED, 0, 1000, 50, S);
        step("b25_lifesteal", ConfigCategory.BLADE, MedalsConfig.B25_LIFESTEAL, 0, 1000, 20, S);
        step("b25_move_speed", ConfigCategory.BLADE, MedalsConfig.B25_MOVE_SPEED, 0, 1000, 10, S);
        step("b30_lost_hp_damage", ConfigCategory.BLADE, MedalsConfig.B30_LOST_HP_DAMAGE, 0, 1000, 20, S);
        step("b30_lost_hp_damage_low", ConfigCategory.BLADE, MedalsConfig.B30_LOST_HP_DAMAGE_LOW, 0, 1000, 40, S);
        step("b30_low_hp_crit_chance", ConfigCategory.BLADE, MedalsConfig.B30_LOW_HP_CRIT_CHANCE, 0, 1000, 10, S);
        step("b30_low_hp_threshold", ConfigCategory.BLADE, MedalsConfig.B30_LOW_HP_THRESHOLD, 0, 1000, 50, S);
        step("b5_bleed_chance", ConfigCategory.BLADE, MedalsConfig.B5_BLEED_CHANCE, 0, 1000, 20, S);
        step("b5_crit_chance", ConfigCategory.BLADE, MedalsConfig.B5_BLEED_CRIT_CHANCE, 0, 1000, 10, S);
        step("blade_exp_damage", ConfigCategory.BLADE, MedalsConfig.BLADE_EXP_DAMAGE, 0, 1000, 10, S);
        step("blade_exp_kill_hp", ConfigCategory.BLADE, MedalsConfig.BLADE_EXP_KILL_HP, 0, 1000, 10, S);
        step("blade_lv1_attack_speed", ConfigCategory.BLADE, MedalsConfig.BLADE_L1_ATTACK_SPEED, 0, 1000, 5, S);
        step("blade_lv1_crit_chance", ConfigCategory.BLADE, MedalsConfig.BLADE_L1_CRIT_CHANCE, 0, 1000, 5, S);
        step("blade_lv1_crit_damage", ConfigCategory.BLADE, MedalsConfig.BLADE_L1_CRIT_DAMAGE, 0, 1000, 5, S);
        step("blade_lv1_damage", ConfigCategory.BLADE, MedalsConfig.BLADE_L1_DAMAGE, 0, 1000, 5, S);
        stepD("blade_per_level_attack_speed", ConfigCategory.BLADE, MedalsConfig.BLADE_PER_ATTACK_SPEED, 0, 1000, 0.5, S);
        stepD("blade_per_level_crit_chance", ConfigCategory.BLADE, MedalsConfig.BLADE_PER_CRIT_CHANCE, 0, 1000, 0.5, S);
        stepD("blade_per_level_crit_damage", ConfigCategory.BLADE, MedalsConfig.BLADE_PER_CRIT_DAMAGE, 0, 1000, 1.0, S);
        stepD("blade_per_level_damage", ConfigCategory.BLADE, MedalsConfig.BLADE_PER_DAMAGE, 0, 1000, 0.75, S);

        // ---- 原作数值 · 坦克(补齐) ----
        step("t10_absorb_chance", ConfigCategory.TANK, MedalsConfig.T10_ABSORB_CHANCE, 0, 1000, 35, S);
        step("t20_armor_bonus", ConfigCategory.TANK, MedalsConfig.T20_ARMOR_BONUS, 0, 1000, 20, S);
        step("t20_damage_reduction", ConfigCategory.TANK, MedalsConfig.T20_DAMAGE_REDUCTION, 0, 1000, 20, S);
        step("t25_block_cap", ConfigCategory.TANK, MedalsConfig.T25_BLOCK_CAP, 0, 1000, 20, S);
        step("t25_block_chance", ConfigCategory.TANK, MedalsConfig.T25_BLOCK_CHANCE, 0, 1000, 30, S);
        step("t30_heal_per_second", ConfigCategory.TANK, MedalsConfig.T30_HEAL_PER_SECOND, 0, 1000, 10, S);
        step("t30_hp_threshold", ConfigCategory.TANK, MedalsConfig.T30_HP_THRESHOLD, 0, 1000, 10, S);
        step("t5_crit_chance", ConfigCategory.TANK, MedalsConfig.T5_CRIT_CHANCE, 0, 1000, 20, S);
        step("t5_crit_damage", ConfigCategory.TANK, MedalsConfig.T5_CRIT_DAMAGE, 0, 1000, 20, S);
        step("t5_damage", ConfigCategory.TANK, MedalsConfig.T5_DAMAGE, 0, 1000, 20, S);
        step("tank_exp_damage", ConfigCategory.TANK, MedalsConfig.TANK_EXP_DAMAGE, 0, 1000, 50, S);
        step("tank_exp_heal", ConfigCategory.TANK, MedalsConfig.TANK_EXP_HEAL, 0, 1000, 35, S);
        step("tank_exp_kill_hp", ConfigCategory.TANK, MedalsConfig.TANK_EXP_KILL_HP, 0, 1000, 10, S);
        step("tank_lv1_armor", ConfigCategory.TANK, MedalsConfig.TANK_L1_ARMOR, 0, 1000, 5, S);
        step("tank_lv1_damage_reduction", ConfigCategory.TANK, MedalsConfig.TANK_L1_DAMAGE_REDUCTION, 0, 1000, 5, S);
        step("tank_lv1_max_health", ConfigCategory.TANK, MedalsConfig.TANK_L1_MAX_HEALTH, 0, 1000, 5, S);
        step("tank_lv1_overheal", ConfigCategory.TANK, MedalsConfig.TANK_L1_OVERHEAL, 0, 1000, 10, S);
        stepD("tank_per_level_armor", ConfigCategory.TANK, MedalsConfig.TANK_PER_ARMOR, 0, 1000, 0.75, S);
        stepD("tank_per_level_damage_reduction", ConfigCategory.TANK, MedalsConfig.TANK_PER_DAMAGE_REDUCTION, 0, 1000, 0.5, S);
        stepD("tank_per_level_max_health", ConfigCategory.TANK, MedalsConfig.TANK_PER_MAX_HEALTH, 0, 1000, 1.0, S);
        stepD("tank_per_level_overheal", ConfigCategory.TANK, MedalsConfig.TANK_PER_OVERHEAL, 0, 1000, 1.0, S);

        // ---- 原作数值 · 法师(补齐) ----
        step("m10_base_crit_chance", ConfigCategory.MAGE, MedalsConfig.M10_BASE_CRIT_CHANCE, 0, 1000, 10, S);
        step("m10_base_crit_damage", ConfigCategory.MAGE, MedalsConfig.M10_BASE_CRIT_DAMAGE, 0, 1000, 135, S);
        step("m15_debuff_refund_chance", ConfigCategory.MAGE, MedalsConfig.M15_DEBUFF_REFUND_CHANCE, 0, 1000, 60, S);
        step("m15_debuff_refund_ratio", ConfigCategory.MAGE, MedalsConfig.M15_DEBUFF_REFUND_RATIO, 0, 1000, 75, S);
        step("m15_refund_chance", ConfigCategory.MAGE, MedalsConfig.M15_REFUND_CHANCE, 0, 1000, 30, S);
        step("m15_refund_ratio", ConfigCategory.MAGE, MedalsConfig.M15_REFUND_RATIO, 0, 1000, 50, S);
        step("m15_spell_duration", ConfigCategory.MAGE, MedalsConfig.M15_SPELL_DURATION, 0, 1000, 10, S);
        step("m20_casting_speed", ConfigCategory.MAGE, MedalsConfig.M20_CASTING_SPEED, 0, 1000, 80, S);
        step("m20_cooldown_discount", ConfigCategory.MAGE, MedalsConfig.M20_COOLDOWN_DISCOUNT, 0, 1000, 25, S);
        step("m20_move_speed", ConfigCategory.MAGE, MedalsConfig.M20_MOVE_SPEED, 0, 1000, 20, S);
        step("m20_soul_discount", ConfigCategory.MAGE, MedalsConfig.M20_SOUL_DISCOUNT, 0, 1000, 50, S);
        step("m20_spell_potency", ConfigCategory.MAGE, MedalsConfig.M20_SPELL_POTENCY, 0, 1000, 30, S);
        step("m20_spell_radius", ConfigCategory.MAGE, MedalsConfig.M20_SPELL_RADIUS, 0, 1000, 20, S);
        step("m25_casting_speed_per_stack", ConfigCategory.MAGE, MedalsConfig.M25_CASTING_SPEED_PER_STACK, 0, 1000, 5, S);
        step("m25_crit_chance_per_stack", ConfigCategory.MAGE, MedalsConfig.M25_CRIT_CHANCE_PER_STACK, 0, 1000, 2, S);
        step("m25_potency_per_stack", ConfigCategory.MAGE, MedalsConfig.M25_POTENCY_PER_STACK, 0, 1000, 10, S);
        step("m30_barrage_potency", ConfigCategory.MAGE, MedalsConfig.M30_BARRAGE_POTENCY, 0, 1000, 35, S);
        step("m30_pity_increment", ConfigCategory.MAGE, MedalsConfig.M30_PITY_INCREMENT, 0, 1000, 15, S);
        step("m30_refresh_chance", ConfigCategory.MAGE, MedalsConfig.M30_REFRESH_CHANCE, 0, 1000, 20, S);
        step("m30_spell_radius", ConfigCategory.MAGE, MedalsConfig.M30_SPELL_RADIUS, 0, 1000, 25, S);
        step("mage_exp_kill_hp", ConfigCategory.MAGE, MedalsConfig.MAGE_EXP_KILL_HP, 0, 1000, 20, S);
        step("mage_exp_soul", ConfigCategory.MAGE, MedalsConfig.MAGE_EXP_SOUL, 0, 1000, 20, S);
        step("mage_exp_spell_damage", ConfigCategory.MAGE, MedalsConfig.MAGE_EXP_SPELL_DAMAGE, 0, 1000, 20, S);
        step("mage_lv1_casting_speed", ConfigCategory.MAGE, MedalsConfig.MAGE_L1_CASTING_SPEED, 0, 1000, 5, S);
        step("mage_lv1_soul_discount", ConfigCategory.MAGE, MedalsConfig.MAGE_L1_SOUL_DISCOUNT, 0, 1000, 2, S);
        step("mage_lv1_spell_radius", ConfigCategory.MAGE, MedalsConfig.MAGE_L1_SPELL_RADIUS, 0, 1000, 5, S);

        // ---- 原作数值 · 经验曲线(补齐) ----
        step("vx_growth_1_40", ConfigCategory.EXP, MedalsConfig.VX_GROWTH_1_40, 0, 1000, 10, S);
        step("vx_growth_40_70", ConfigCategory.EXP, MedalsConfig.VX_GROWTH_40_70, 0, 1000, 8, S);
        step("vx_growth_70_100", ConfigCategory.EXP, MedalsConfig.VX_GROWTH_70_100, 0, 1000, 4, S);
        step("vx_growth_after_100", ConfigCategory.EXP, MedalsConfig.VX_GROWTH_AFTER_100, 0, 1000, 2, S);
    }

    // ================================================================
    // 枚举型选项(用连续整数做候选值)
    // ================================================================

    /**
     * 构造一个「枚举多选」选项。
     *
     * <p>候选值就是 {@code [lo, hi]} 上的全部整数,语言键后缀即该整数本身 ——
     * 例如 {@code effect_hud_corner} 的候选是 0~3,文案取
     * {@code config.lost_realm.opt.effect_hud_corner.0} ~ {@code .3}。
     */
    private static ConfigOption switchOpt(String id, ConfigCategory cat,
                                          ForgeConfigSpec.IntValue v,
                                          int lo, int hi, int def,
                                          ForgeConfigSpec spec) {
        // ★ dv36 防呆：枚举按钮只能表示「少数几个离散档位」。
        //   一旦范围过大（例如误把 25~100 的连续量当成枚举），按钮会横排铺满
        //   整个屏幕并溢出到侧边栏外——玩家看到一排无法辨认的按钮。
        //   这类量应当用 step() 注册。上限 8 足够覆盖角落(4)/方向(2)/倒计时(4)。
        if (hi - lo + 1 > 8) {
            throw new IllegalArgumentException(
                    "switchOpt 的取值过多：" + id + " 有 " + (hi - lo + 1)
                            + " 个值（上限 8）。连续数值请改用 step()。");
        }
        int[] values = new int[hi - lo + 1];
        String[] suffixes = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = lo + i;
            suffixes[i] = String.valueOf(lo + i);
        }
        ConfigOption o = new ConfigOption.Builder()
                .id(id).kind(ConfigOption.Kind.SWITCH)
                .langKey("config.lost_realm.opt." + id)
                .categoryId(cat.id())
                .intRange(v, lo, hi, spec)
                .switchValues(values, suffixes)
                .build();
        o.setDefaultValue(def);
        return o;
    }

    /** 该分类下的选项是否已全部为默认值(用于「重置本页」按钮是否可点)。 */
    public static boolean hasAnyDirty(String categoryId) {
        for (ConfigOption o : ofCategory(categoryId)) {
            if (o.isDirty()) {
                return true;
            }
        }
        return false;
    }

    /** 全表是否有未保存的改动。 */
    public static boolean hasAnyDirty() {
        for (ConfigOption o : options()) {
            if (o.isDirty()) {
                return true;
            }
        }
        return false;
    }

    private ConfigCatalog() {}
}
