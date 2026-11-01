package com.lost_realm.skill.summoner;

import java.util.List;

import net.minecraft.network.chat.Component;

import com.lost_realm.config.MedalsConfig;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;

/**
 * Lv.50【魂火】(被动)
 *
 * <p>仆从的攻击额外附带 {@code S25_EXTRA_DAMAGE} 点伤害，并在命中处溅出魂火粒子。
 *
 * <p>追加的时机在 {@code LivingDamageEvent} 之后（{@code LOWEST} 优先级），
 * 此时护甲已经算完 —— 也就是说这笔伤害<b>不吃护甲减免</b>，符合「法术伤害」的手感。
 */
public class SkillSoulFire implements Skill {

    public static final String ID = "su25_soul_fire";

    @Override
    public MedalType medal() {
        return MedalType.SUMMONER;
    }

    @Override
    public int unlockLevel() {
        return 50;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component displayName() {
        return Component.translatable("skill.lost_realm." + ID);
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("skill.lost_realm." + ID + ".desc",
                fmt(MedalsConfig.S25_EXTRA_DAMAGE.get())));
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
