package com.lost_realm.skill.archer;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import com.lost_realm.config.MedalsConfig;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;

/**
 * Lv.30【破绽】
 *
 * <p>距离越近,双倍伤害概率越高。2 格内 45%,每远 1 格降低 5%,最低 15%。
 */
public class SkillWeaknessInsight implements Skill {

    public static final String ID = "s15_weakness_insight";

    @Override
    public MedalType medal() {
        return MedalType.ARCHER;
    }

    @Override
    public int unlockLevel() {
        return 30;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component displayName() {
        return Component.translatable("skill.lost_realm.s15_weakness_insight");
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("skill.lost_realm.s15_weakness_insight.desc",
                MedalsConfig.S15_NEAR_CHANCE.get(),
                MedalsConfig.S15_CHANCE_DROP_PER_BLOCK.get(),
                MedalsConfig.S15_MIN_CHANCE.get()));
    }

    @Override
    public void applyPassive(ServerPlayer player, int level) {
        // 命中时结算
    }

    /**
     * 依据攻击者与目标的距离,给出双倍伤害概率。
     *
     * @param distance 实际距离(格)
     * @return 概率 0.0~1.0
     */
    public static double doubleDamageChance(double distance) {
        double near = MedalsConfig.pct(MedalsConfig.S15_NEAR_CHANCE);
        double drop = MedalsConfig.pct(MedalsConfig.S15_CHANCE_DROP_PER_BLOCK);
        double min = MedalsConfig.pct(MedalsConfig.S15_MIN_CHANCE);
        double nearDist = MedalsConfig.S15_NEAR_DISTANCE.get();

        if (distance <= nearDist) {
            return near;
        }
        // 每超出 1 格降低 drop,但以 min 为下限
        double extraBlocks = Math.floor(distance - nearDist);
        double chance = near - drop * extraBlocks;
        return Math.max(min, chance);
    }
}
