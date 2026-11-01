package com.lost_realm.skill.summoner;

import java.util.List;

import net.minecraft.network.chat.Component;

import com.lost_realm.config.MedalsConfig;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;

/**
 * Lv.20【骸骨硬化】(被动)
 *
 * <p>你的全部仆从获得 {@code S10_SERVANT_REDUCTION} 的额外减伤。
 *
 * <p>减伤作用在「仆从受到伤害」这一步，判定与执行见
 * {@code SummonerCharmHandler#onServantHurt}。
 */
public class SkillBoneHardening implements Skill {

    public static final String ID = "su10_bone_hardening";

    @Override
    public MedalType medal() {
        return MedalType.SUMMONER;
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
        return Component.translatable("skill.lost_realm." + ID);
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("skill.lost_realm." + ID + ".desc",
                MedalsConfig.S10_SERVANT_REDUCTION.get()));
    }
}
