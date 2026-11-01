package com.lost_realm.skill.summoner;

import java.util.List;

import net.minecraft.network.chat.Component;

import com.lost_realm.config.MedalsConfig;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;

/**
 * Lv.30【亡者复苏】(被动)
 *
 * <p>仆从死亡时有 {@code S15_REVIVE_CHANCE} 的概率原地复活，
 * 并以 {@code S15_REVIVE_HEALTH} 的血量比例站起来。
 *
 * <p>实现要点：直接取消 {@code LivingDeathEvent}，再把血量顶回去。
 * 不这么做的话，实体虽然没死，血量仍是 0 —— 下一 tick 又会被判死一次。
 */
public class SkillDeadRevival implements Skill {

    public static final String ID = "su15_dead_revival";

    @Override
    public MedalType medal() {
        return MedalType.SUMMONER;
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
        return Component.translatable("skill.lost_realm." + ID);
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("skill.lost_realm." + ID + ".desc",
                MedalsConfig.S15_REVIVE_CHANCE.get(),
                MedalsConfig.S15_REVIVE_HEALTH.get()));
    }
}
