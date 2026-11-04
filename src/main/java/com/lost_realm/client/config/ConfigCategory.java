package com.lost_realm.client.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.network.chat.Component;

/**
 * 可视化配置界面的<b>分类(侧边栏条目)</b>。
 *
 * <p>每个分类对应配置文件里的一组相关选项,例如「药水与图腾」「附魔」「堆叠」。
 * 侧边栏按 {@link #all()} 的顺序显示,点击后右侧列表刷新为该分类的选项。
 *
 * <p>分类名与说明都走语言键,因此这里只存键名不存文案 ——
 * 便于后续加英文/其它语言而不改代码。
 */
public final class ConfigCategory {

    private static final List<ConfigCategory> ALL = new ArrayList<>();
    private static final Map<String, ConfigCategory> BY_ID = new LinkedHashMap<>();

    // ================================================================
    // 分类定义
    // ================================================================

    /** 全部选项(不分组的综合视图)。 */
    public static final ConfigCategory OVERVIEW =
            reg("overview", "config.lost_realm.cat.overview");

    /**
     * 世界难度(beta38)。
     *
     * <p>⚠️ 这一页<b>不对应任何配置项</b> —— 难度是存档数据,不是 {@code .toml}。
     * 它放在侧边栏第一位的理由:玩家最想改的往往是「这世界多难」,
     * 而它藏在指令里( {@code /lr difficulty} )没人找得到。
     * {@link ConfigScreen} 对这一页做了专门渲染(见 {@code isDifficultyPage}),
     * 数据来自服务端同步,保存动作走网络包而不是 {@code spec.save()}。
     */
    public static final ConfigCategory DIFFICULTY =
            reg("difficulty", "config.lost_realm.cat.difficulty");

    // ---- 体验改进(QoL)----
    // 说明:原先把 QoL 拆成 10 个碎片化分类(药水/图腾/鞘翅/睡眠/种植/
    // 自动战斗/村民/附魔/堆叠/死亡),侧边栏过长且难找。现合并为 1 类,
    // 组内仍按功能分节排列(见 ConfigCatalog 的注释分组)。
    public static final ConfigCategory QOL =
            reg("qol", "config.lost_realm.cat.qol");

    // ---- 客户端显示 ----
    public static final ConfigCategory EFFECT_HUD =
            reg("effect_hud", "config.lost_realm.cat.effect_hud");

    // ---- 原作系统数值:熟练度与经验 ----
    public static final ConfigCategory EXP =
            reg("exp", "config.lost_realm.cat.exp");

    // ---- 原作系统数值:四个职业各自一类 ----
    public static final ConfigCategory ARCHER =
            reg("archer", "config.lost_realm.cat.archer");
    public static final ConfigCategory WARRIOR =
            reg("warrior", "config.lost_realm.cat.warrior");
    public static final ConfigCategory TANK =
            reg("tank", "config.lost_realm.cat.tank");
    public static final ConfigCategory MAGE =
            reg("mage", "config.lost_realm.cat.mage");

    // ---- 召唤师勋章(同样以 Goety 为底座)----
    public static final ConfigCategory SUMMONER =
            reg("summoner", "config.lost_realm.cat.summoner");

    // ---- 箭类饰品与时间胶囊(dv42)----
    public static final ConfigCategory ARROWS =
            reg("arrows", "config.lost_realm.cat.arrows");

    // ---- 「刃」类战士饰品(dv49)----
    public static final ConfigCategory WARRIOR_CHARMS =
            reg("warrior_charms", "config.lost_realm.cat.warrior_charms");

    // ---- 「晶石」类法师饰品 ----
    public static final ConfigCategory CRYSTALS =
            reg("crystals", "config.lost_realm.cat.crystals");

    // ---- 「契约」类召唤师饰品 ----
    public static final ConfigCategory CONTRACTS =
            reg("contracts", "config.lost_realm.cat.contracts");

    // ---- 战士终极武器 ----
    public static final ConfigCategory SWORD =
            reg("sword", "config.lost_realm.cat.sword");

    // ---- 「盾」类坦克饰品 ----
    public static final ConfigCategory SHIELDS =
            reg("shields", "config.lost_realm.cat.shields");

    // ---- 职业专属饰品槽(dv48)----
    public static final ConfigCategory SLOTS =
            reg("slots", "config.lost_realm.cat.slots");

    // ---- 乌蠹诺斯(beta41):当前终局 Boss ----
    public static final ConfigCategory EREBOS =
            reg("erebos", "config.lost_realm.cat.erebos");

    /**
     * ⚠️ 必须放在所有 {@code reg(...)} 静态初始化之后。
     *
     * <p>Java 的静态初始化按源码顺序执行,把本字段写在最前面会得到空列表。
     */
    private static final List<ConfigCategory> ORDER = List.copyOf(ALL);

    private final String id;
    private final String langKey;

    private ConfigCategory(String id, String langKey) {
        this.id = id;
        this.langKey = langKey;
    }

    private static ConfigCategory reg(String id, String langKey) {
        ConfigCategory c = new ConfigCategory(id, langKey);
        ALL.add(c);
        BY_ID.put(id, c);
        return c;
    }

    public String id() { return id; }

    public String langKey() { return langKey; }

    public Component displayName() {
        return Component.translatable(langKey);
    }

    /** 侧边栏顺序(不含 OVERVIEW,它在界面里单独放在顶部)。 */
    public static List<ConfigCategory> sidebarOrder() {
        List<ConfigCategory> out = new ArrayList<>();
        for (ConfigCategory c : ORDER) {
            if (c != OVERVIEW) {
                out.add(c);
            }
        }
        return out;
    }

    public static List<ConfigCategory> all() { return ORDER; }

    public static ConfigCategory byId(String id) { return BY_ID.get(id); }
}
