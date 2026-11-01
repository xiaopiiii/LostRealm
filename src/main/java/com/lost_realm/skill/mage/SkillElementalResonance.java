package com.lost_realm.skill.mage;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import com.lost_realm.compat.goety.GoetyBridge;
import com.lost_realm.config.MedalsConfig;
import com.lost_realm.data.PlayerMedals;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;
import com.lost_realm.util.ModifierIds;

/**
 * Lv.10【元素共鸣】(被动)
 *
 * <p>在 {@code 15 秒}窗口内,每次施放<b>不同流派</b>的法术叠 1 层「共鸣」(上限 5 层)。
 * 每层提供:法术强度 <b>+7.5%</b>、灵魂消耗 <b>-7.5%</b>。
 * 一旦施放与上次<b>相同流派</b>的法术,层数立刻清零。
 *
 * <h2>流派识别</h2>
 * 通过 {@link GoetyBridge#currentSpellType} 读取 Goety 的 {@code SpellType}
 * (死灵 / 下界 / 霜冻 / 地卜 / 风 / 风暴 / 深渊 / 荒野 / 虚空)。
 * 比较时统一转为 {@code String} 避免依赖 Goety 类型。
 *
 * <h2>为什么用「手持法杖流派变化」而非施法事件</h2>
 * Goety 未对外暴露「施法成功」事件。本技能采用「每 tick 检测手持法杖的
 * 当前流派是否变化」来近似:玩家切换聚晶或换法杖即视为「换了流派」。
 * 这是一种工程折中,好处是零侵入 Goety 施法流程。
 */
public class SkillElementalResonance implements Skill {

    public static final String ID = "m5_elemental_resonance";
    /** 当前层数 */
    public static final String STATE_STACKS = ID + "_stacks";
    /** 层数保留窗口剩余 tick */
    public static final String STATE_WINDOW = ID + "_window";
    /** 上次施放的流派(存 SpellType 的 ordinal + 1;0 = 无) */
    public static final String STATE_LAST_TYPE = ID + "_last_type";

    @Override
    public MedalType medal() {
        return MedalType.MAGE;
    }

    @Override
    public int unlockLevel() {
        return 10;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component displayName() {
        return Component.translatable("skill.lost_realm.m5_elemental_resonance");
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("skill.lost_realm.m5_elemental_resonance.desc",
                MedalsConfig.M5_WINDOW_SECONDS.get(),
                MedalsConfig.M5_MAX_STACKS.get(),
                fmt(MedalsConfig.M5_POTENCY_PER_STACK.get()),
                fmt(MedalsConfig.M5_SOUL_DISCOUNT_PER_STACK.get())));
    }

    @Override
    public void applyPassive(ServerPlayer player, int level) {
        GoetyBridge goety = GoetyBridge.get();
        if (goety == null) {
            return;
        }
        PlayerMedals data = PlayerMedals.of(player);

        // ---- 窗口递减 ----
        int window = data.getState(STATE_WINDOW);
        if (window > 0) {
            window--;
            data.setState(STATE_WINDOW, window);
            if (window == 0) {
                // 窗口到期:层数清零
                setStacks(player, data, 0, 0);
                return;
            }
        }

        // ---- 读取当前手持法杖的流派 ----
        int current = goety.currentSpellTypeOrdinal(player);
        if (current < 0) {
            // 未持法杖 / 无法识别流派:窗口继续走,不改变层数
            return;
        }
        int last = data.getState(STATE_LAST_TYPE);   // 0 = 从未施放

        if (last == 0) {
            // 首次施法 → 起手 1 层
            setStacks(player, data, 1, current);
        } else if (last != current) {
            // 换了流派 → 叠一层
            int stacks = Math.min(data.getState(STATE_STACKS) + 1, MedalsConfig.M5_MAX_STACKS.get());
            setStacks(player, data, stacks, current);
        } else {
            // 相同流派:仅刷新窗口,不改层数,保持层数
            data.setState(STATE_WINDOW, Math.max(window, windowTicks()));
        }

        applyAttributes(player, data);
    }

    /** 层数变化 / 窗口到期时统一写状态。 */
    private static void setStacks(ServerPlayer player, PlayerMedals data, int stacks, int typeOrdinal) {
        data.setState(STATE_STACKS, stacks);
        data.setState(STATE_WINDOW, stacks > 0 ? windowTicks() : 0);
        // 存 ordinal + 1,使 0 可作为「无」的哨兵值
        data.setState(STATE_LAST_TYPE, stacks > 0 ? typeOrdinal + 1 : 0);
        applyAttributes(player, data);
    }

    private static int windowTicks() {
        return MedalsConfig.M5_WINDOW_SECONDS.get() * 20;
    }

    /** 按当前层数施加/刷新属性修饰符。 */
    private static void applyAttributes(ServerPlayer player, PlayerMedals data) {
        GoetyBridge goety = GoetyBridge.get();
        if (goety == null) {
            return;
        }
        int stacks = data.getState(STATE_STACKS);
        double potency = MedalsConfig.M5_POTENCY_PER_STACK.get() / 100.0D * stacks;
        double discount = MedalsConfig.M5_SOUL_DISCOUNT_PER_STACK.get() / 100.0D * stacks;

        goety.applySpellPotency(player, ModifierIds.MAGE_M5_RESONANCE_POTENCY,
                "mage_m5_resonance_potency", potency);
        goety.applySoulDiscount(player, ModifierIds.MAGE_M5_RESONANCE_SOUL_DISCOUNT,
                "mage_m5_resonance_soul_discount", discount);
    }

    /** 当前层数(供 HUD 显示)。 */
    public static int stacks(PlayerMedals data) {
        return data.getState(STATE_STACKS);
    }

    @Override
    public void clear(net.minecraft.world.entity.player.Player player) {
        GoetyBridge goety = GoetyBridge.get();
        if (goety == null) {
            return;
        }
        goety.applySpellPotency(player, ModifierIds.MAGE_M5_RESONANCE_POTENCY,
                "mage_m5_resonance_potency", 0.0D);
        goety.applySoulDiscount(player, ModifierIds.MAGE_M5_RESONANCE_SOUL_DISCOUNT,
                "mage_m5_resonance_soul_discount", 0.0D);
    }

    @Override
    public void onLevelChanged(ServerPlayer player, int oldLevel, int newLevel) {
        if (newLevel < 10) {
            PlayerMedals data = PlayerMedals.of(player);
            setStacks(player, data, 0, 0);
        }
    }

    /** 把 double 百分比格式化为去掉多余小数点的字符串。 */
    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
