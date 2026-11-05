package com.lost_realm.item;

import java.util.ArrayList;
import java.util.List;

import com.lost_realm.config.MedalsConfig;
import com.lost_realm.skill.MedalType;

/**
 * 把勋章的<b>基础成长</b>翻译成「名称 + 数值」的数据。
 *
 * <h2>为什么单独一个类</h2>
 * 真正"施加"属性的逻辑在 {@link MedalRuntime},但那个类为了调用
 * {@code RealmAttributes} / {@code GoetyBridge} 而牵进了一大串可选依赖,
 * 不适合在客户端渲染 tooltip 时触碰。这里只做一件事:
 * <b>读配置、算数值</b> —— 不引用任何属性对象,因此客户端可安全调用。
 *
 * <h2>这里不负责排版</h2>
 * 只产出 {@link Attr}（语言键 + 数值文本）,<b>不带缩进、不带颜色、不换行</b> ——
 * 怎么对齐、每行放几项由 {@link ItemMedal} 决定。属性名长短不齐（「护甲」对
 * 「法术作用半径」），要做得像一张对齐的表格,必须在拿到**整组**之后才知道
 * 该补多少空白,所以对齐只能放在排版侧完成。
 *
 * <h2>显示的是"当前拿到什么",不是"每级加多少"</h2>
 * 玩家打开 tooltip 想知道的是「我这枚勋章现在给了我多少」，而不是成长公式。
 * 所以这里只按 {@code level} 算出一个最终值（例如 Lv.24 的箭伤 = 8% + 1.5%×11），
 * 不写「每级 +1.5%」。
 *
 * <h2>与属性施加逻辑保持同步</h2>
 * 每一项的算法都必须与 {@link MedalRuntime} 对应方法逐字一致，
 * 否则会出现「tooltip 写的和实际生效的不一样」——那比不显示更糟。
 */
public final class MedalAttributeText {

    /**
     * 单条属性。
     *
     * @param key   属性名的语言键（{@code attr.lost_realm.*}）
     * @param value 已带正负号的数值文本（如 {@code +24.5%}）
     */
    public record Attr(String key, String value) {}

    /** 该勋章在 {@code level} 级时提供的全部基础属性（不含节点技能被动）。 */
    public static List<Attr> of(MedalType type, int level) {
        int steps = Math.max(0, level - 1);
        List<Attr> out = new ArrayList<>();
        switch (type) {
            // ---- 射手:Lv.2 箭伤+8% 拉弓+5% 移速+5% 闪避+5% ----
            case ARCHER -> {
                percent(out, "arrow_damage",
                        MedalsConfig.pct(MedalsConfig.ARCHER_L1_ARROW_DMG)
                                + MedalsConfig.pctD(MedalsConfig.ARCHER_PER_ARROW_DMG) * steps, false);
                percent(out, "draw_speed",
                        MedalsConfig.pct(MedalsConfig.ARCHER_L1_DRAW_SPEED)
                                + MedalsConfig.pctD(MedalsConfig.ARCHER_PER_DRAW_SPEED) * steps, false);
                percent(out, "move_speed",
                        MedalsConfig.pct(MedalsConfig.ARCHER_L1_MOVE_SPEED)
                                + MedalsConfig.pctD(MedalsConfig.ARCHER_PER_MOVE_SPEED) * steps, false);
                // 闪避本身有 100% 硬上限,这里一并夹住,免得显示 100% 以上
                percent(out, "dodge_chance",
                        MedalsConfig.pct(MedalsConfig.ARCHER_L1_DODGE)
                                + MedalsConfig.pctD(MedalsConfig.ARCHER_PER_DODGE) * steps, true);
            }

            // ---- 刃:Lv.2 伤害+5% 攻速+5% 暴击率+5% 暴击伤害+5% ----
            case BLADE -> {
                percent(out, "attack_damage",
                        MedalsConfig.pct(MedalsConfig.BLADE_L1_DAMAGE)
                                + MedalsConfig.pctD(MedalsConfig.BLADE_PER_DAMAGE) * steps, false);
                percent(out, "attack_speed",
                        MedalsConfig.pct(MedalsConfig.BLADE_L1_ATTACK_SPEED)
                                + MedalsConfig.pctD(MedalsConfig.BLADE_PER_ATTACK_SPEED) * steps, false);
                percent(out, "crit_chance",
                        MedalsConfig.pct(MedalsConfig.BLADE_L1_CRIT_CHANCE)
                                + MedalsConfig.pctD(MedalsConfig.BLADE_PER_CRIT_CHANCE) * steps, false);
                percent(out, "crit_damage",
                        MedalsConfig.pct(MedalsConfig.BLADE_L1_CRIT_DAMAGE)
                                + MedalsConfig.pctD(MedalsConfig.BLADE_PER_CRIT_DAMAGE) * steps, false);
            }

            // ---- 坦克:Lv.2 护甲+5% 最大生命+5% 过量治疗+10% 减免+5% ----
            case TANK -> {
                percent(out, "armor",
                        MedalsConfig.pct(MedalsConfig.TANK_L1_ARMOR)
                                + MedalsConfig.pctD(MedalsConfig.TANK_PER_ARMOR) * steps, false);
                percent(out, "max_health",
                        MedalsConfig.pct(MedalsConfig.TANK_L1_MAX_HEALTH)
                                + MedalsConfig.pctD(MedalsConfig.TANK_PER_MAX_HEALTH) * steps, false);
                percent(out, "overheal",
                        MedalsConfig.pct(MedalsConfig.TANK_L1_OVERHEAL)
                                + MedalsConfig.pctD(MedalsConfig.TANK_PER_OVERHEAL) * steps, false);
                percent(out, "damage_reduction",
                        MedalsConfig.pct(MedalsConfig.TANK_L1_DAMAGE_REDUCTION)
                                + MedalsConfig.pctD(MedalsConfig.TANK_PER_DAMAGE_REDUCTION) * steps, false);
            }

            // ---- 法师:Lv.2 强度+2 施法速度+5% 灵魂折扣+2% 半径+5% ----
            case MAGE -> {
                // 法术强度是绝对值(Goety 语义),不是百分比
                flat(out, "spell_potency",
                        MedalsConfig.MAGE_L1_SPELL_POTENCY.get()
                                + MedalsConfig.MAGE_PER_SPELL_POTENCY.get() * steps);
                percent(out, "casting_speed",
                        MedalsConfig.pct(MedalsConfig.MAGE_L1_CASTING_SPEED)
                                + MedalsConfig.pctD(MedalsConfig.MAGE_PER_CASTING_SPEED) * steps, false);
                percent(out, "soul_discount",
                        MedalsConfig.pct(MedalsConfig.MAGE_L1_SOUL_DISCOUNT)
                                + MedalsConfig.pctD(MedalsConfig.MAGE_PER_SOUL_DISCOUNT) * steps, false);
                percent(out, "spell_radius",
                        MedalsConfig.pct(MedalsConfig.MAGE_L1_SPELL_RADIUS)
                                + MedalsConfig.pctD(MedalsConfig.MAGE_PER_SPELL_RADIUS) * steps, false);
            }

            // ---- 召唤师:Lv.2 仆从生命+5% 伤害+5% 速度+5% 寿命+25% ----
            case SUMMONER -> {
                percent(out, "servant_health",
                        MedalsConfig.pct(MedalsConfig.SUMMONER_L1_SERVANT_HEALTH)
                                + MedalsConfig.pctD(MedalsConfig.SUMMONER_PER_SERVANT_HEALTH) * steps, false);
                percent(out, "servant_damage",
                        MedalsConfig.pct(MedalsConfig.SUMMONER_L1_SERVANT_DAMAGE)
                                + MedalsConfig.pctD(MedalsConfig.SUMMONER_PER_SERVANT_DAMAGE) * steps, false);
                percent(out, "servant_speed",
                        MedalsConfig.pct(MedalsConfig.SUMMONER_L1_SERVANT_SPEED)
                                + MedalsConfig.pctD(MedalsConfig.SUMMONER_PER_SERVANT_SPEED) * steps, false);
                percent(out, "servant_lifespan",
                        MedalsConfig.pct(MedalsConfig.SUMMONER_L1_SERVANT_LIFESPAN)
                                + MedalsConfig.pctD(MedalsConfig.SUMMONER_PER_SERVANT_LIFESPAN) * steps, false);
            }
        }
        return out;
    }

    private static void percent(List<Attr> out, String key, double ratio, boolean clampAtOne) {
        double v = clampAtOne ? Math.min(1.0D, ratio) : ratio;
        out.add(new Attr(key, signed(v * 100.0D) + "%"));
    }

    private static void flat(List<Attr> out, String key, double value) {
        out.add(new Attr(key, signed(value)));
    }

    /** 带正负号的数字:整数不带小数点(Lv.24 的箭伤显示 {@code +24.5%},攻击速度显示 {@code +10%})。 */
    private static String signed(double v) {
        String s = (Math.abs(v - Math.rint(v)) < 1.0E-6D)
                ? String.valueOf((long) Math.rint(v))
                : String.format(java.util.Locale.ROOT, "%.1f", v);
        return v > 0 ? "+" + s : s;
    }

    private MedalAttributeText() {}
}
