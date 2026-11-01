package com.lost_realm.skill.archer;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import com.lost_realm.config.MedalsConfig;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;

/**
 * Lv.20【蓄势】
 *
 * <p>箭矢每飞行 1.25 格,伤害 +1%。
 *
 * <p>该技能在箭矢命中时按飞行距离结算(见 {@code CombatHandler} 对箭矢伤害的处理):
 * 倍率 = 1 + floor(距离 / 1.25) × 1%。
 */
public class SkillChargeUp implements Skill {

    public static final String ID = "s10_charge_up";

    @Override
    public MedalType medal() {
        return MedalType.ARCHER;
    }

    @Override
    public int unlockLevel() {
        return 20;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component displayName() {
        return Component.translatable("skill.lost_realm.s10_charge_up");
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("skill.lost_realm.s10_charge_up.desc",
                MedalsConfig.S10_DAMAGE_PER_1_25_BLOCK.get()));
    }

    @Override
    public void applyPassive(ServerPlayer player, int level) {
        // 纯命中时结算,无持续状态
    }

    /**
     * 计算飞行距离带来的伤害倍率。
     *
     * @param distance 箭矢飞行的总距离(格)
     */
    public static double damageMultiplier(double distance) {
        if (distance <= 0) {
            return 1.0D;
        }
        double perBlock = MedalsConfig.S10_DAMAGE_PER_1_25_BLOCK.get() / 100.0D;
        int steps = (int) Math.floor(distance / 1.25D);
        return 1.0D + perBlock * steps;
    }
}
