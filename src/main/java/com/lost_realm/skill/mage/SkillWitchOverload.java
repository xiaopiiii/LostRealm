package com.lost_realm.skill.mage;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import com.lost_realm.compat.goety.GoetyBridge;
import com.lost_realm.config.MedalsConfig;
import com.lost_realm.data.PlayerMedals;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;
import com.lost_realm.util.AttributeGuard;
import com.lost_realm.util.ModifierIds;

import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Lv.40【巫术过载】(主动)
 *
 * <p>开启后持续 <b>15 秒</b>,期间获得:
 * <ul>
 *   <li>施法速度 <b>+80%</b></li>
 *   <li>灵魂消耗 <b>-50%</b>(即消耗减半)</li>
 *   <li>法术强度 <b>+30%</b></li>
 *   <li>法术冷却 <b>-25%</b></li>
 *   <li>法术作用半径 <b>+20%</b></li>
 *   <li>移动速度 <b>+20%</b></li>
 * </ul>
 * 冷却 <b>70 秒</b>。
 *
 * <p>所有属性在持续期间每 tick 幂等刷新,结束时统一清除。
 */
public class SkillWitchOverload implements Skill {

    public static final String ID = "m20_witch_overload";
    /** 激活剩余 tick */
    public static final String STATE_REMAIN = ID + "_remain";

    @Override
    public MedalType medal() {
        return MedalType.MAGE;
    }

    @Override
    public int unlockLevel() {
        return 40;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component displayName() {
        return Component.translatable("skill.lost_realm.m20_witch_overload");
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("skill.lost_realm.m20_witch_overload.desc",
                MedalsConfig.M20_DURATION_SECONDS.get(),
                MedalsConfig.M20_CASTING_SPEED.get(),
                MedalsConfig.M20_SOUL_DISCOUNT.get(),
                MedalsConfig.M20_SPELL_POTENCY.get(),
                MedalsConfig.M20_COOLDOWN_DISCOUNT.get(),
                MedalsConfig.M20_SPELL_RADIUS.get(),
                MedalsConfig.M20_MOVE_SPEED.get(),
                MedalsConfig.M20_COOLDOWN_SECONDS.get()));
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public int cooldownTicks() {
        return MedalsConfig.M20_COOLDOWN_SECONDS.get() * 20;
    }

    public static boolean isActive(PlayerMedals data) {
        return data.getState(STATE_REMAIN) > 0;
    }

    @Override
    public boolean activate(ServerPlayer player, int level) {
        if (GoetyBridge.get() == null) {
            return false;
        }
        PlayerMedals data = PlayerMedals.of(player);
        if (isActive(data)) {
            return false;
        }
        data.setState(STATE_REMAIN, MedalsConfig.M20_DURATION_SECONDS.get() * 20);
        applyAll(player);
        return true;
    }

    @Override
    public void applyPassive(ServerPlayer player, int level) {
        PlayerMedals data = PlayerMedals.of(player);
        int remain = data.getState(STATE_REMAIN);
        if (remain <= 0) {
            return;
        }
        data.setState(STATE_REMAIN, remain - 1);
        applyAll(player);
        if (remain - 1 == 0) {
            clear(player);
        }
    }

    /** 施加全部加成修饰符。 */
    private static void applyAll(ServerPlayer player) {
        GoetyBridge goety = GoetyBridge.get();
        if (goety == null) {
            return;
        }
        goety.applyCastingSpeed(player, ModifierIds.MAGE_M20_CASTING_SPEED,
                "mage_m20_casting_speed", MedalsConfig.pct(MedalsConfig.M20_CASTING_SPEED));
        goety.applySoulDiscount(player, ModifierIds.MAGE_M20_SOUL_DISCOUNT,
                "mage_m20_soul_discount", MedalsConfig.pct(MedalsConfig.M20_SOUL_DISCOUNT));
        // 法术强度是 Goety 的绝对整数值(基础成长挂在同一属性上)。
        // 「+30%」应理解为「在玩家当前强度基础上的 30%」,
        // 因此按 (基础成长总值 - 本技能自身贡献) × 比例 计算,避免自我叠加。
        goety.applySpellPotency(player, ModifierIds.MAGE_M20_SPELL_POTENCY,
                "mage_m20_spell_potency", potencyPercentOf(player));
        goety.applyCooldownDiscount(player, ModifierIds.MAGE_M20_COOLDOWN_DISCOUNT,
                "mage_m20_cooldown_discount", MedalsConfig.pct(MedalsConfig.M20_COOLDOWN_DISCOUNT));
        goety.applySpellRadius(player, ModifierIds.MAGE_M20_SPELL_RADIUS,
                "mage_m20_spell_radius", MedalsConfig.pct(MedalsConfig.M20_SPELL_RADIUS));
        AttributeGuard.apply(player, Attributes.MOVEMENT_SPEED,
                ModifierIds.MAGE_M20_MOVE_SPEED, "mage_m20_move_speed",
                MedalsConfig.pct(MedalsConfig.M20_MOVE_SPEED),
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE);
    }

    /**
     * 计算 Lv.40 应提供的法术强度增量。
     *
     * <p>取「当前强度总值 − 本技能已贡献的量」作为基准,乘以配置比例,
     * 保证多次调用结果稳定(幂等),不会因为自身叠加而指数膨胀。
     *
     * <p>{@code SpellAttribute} 的强度是<b>绝对整数值</b>(基础 0,上限 2048),
     * 因此这里的「+30%」是「在玩家当前强度基础上的 30%」。
     */
    private static double potencyPercentOf(ServerPlayer player) {
        GoetyBridge goety = GoetyBridge.get();
        if (goety == null) {
            return 0.0D;
        }
        double total = goety.spellPotencyTotal(player);
        double own = goety.spellPotencyModifierAmount(player, ModifierIds.MAGE_M20_SPELL_POTENCY);
        double base = Math.max(0.0D, total - own);
        return base * MedalsConfig.pct(MedalsConfig.M20_SPELL_POTENCY);
    }

    @Override
    public void clear(net.minecraft.world.entity.player.Player player) {
        GoetyBridge goety = GoetyBridge.get();
        if (goety == null) {
            return;
        }
        // 逐条归零(用同 UUID 覆盖为 0)
        goety.applyCastingSpeed(player, ModifierIds.MAGE_M20_CASTING_SPEED, "mage_m20_casting_speed", 0.0D);
        goety.applySoulDiscount(player, ModifierIds.MAGE_M20_SOUL_DISCOUNT, "mage_m20_soul_discount", 0.0D);
        goety.applySpellPotency(player, ModifierIds.MAGE_M20_SPELL_POTENCY, "mage_m20_spell_potency", 0.0D);
        goety.applyCooldownDiscount(player, ModifierIds.MAGE_M20_COOLDOWN_DISCOUNT, "mage_m20_cooldown_discount", 0.0D);
        goety.applySpellRadius(player, ModifierIds.MAGE_M20_SPELL_RADIUS, "mage_m20_spell_radius", 0.0D);
        AttributeGuard.remove(player, Attributes.MOVEMENT_SPEED, ModifierIds.MAGE_M20_MOVE_SPEED);
        if (player instanceof ServerPlayer sp) {
            PlayerMedals.of(sp).setState(STATE_REMAIN, 0);
        }
    }
}
