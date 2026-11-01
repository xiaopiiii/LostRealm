package com.lost_realm.skill.summoner;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import com.lost_realm.config.MedalsConfig;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;

/**
 * Lv.10【灵魂纽带】(被动)
 *
 * <p>仆从每次造成伤害时，为召唤师补充 {@code S5_SOUL_PER_HIT} 点灵魂；
 * 同一个仆从每 {@code S5_SOUL_COOLDOWN} tick 最多触发一次。
 *
 * <h2>为什么这个技能类几乎是空的</h2>
 * 它没有任何「状态」需要维护 —— 触发条件是「仆从造成了伤害」，
 * 那是一个事件，不是持续状态。所以判定与执行都写在
 * {@code SummonerCharmHandler#onServantDamage} 里，本类只承担
 * <b>声明</b>（解锁等级 / 名字 / 说明）与等级回归时的清理。
 *
 * <p>这也是本项目对「纯事件驱动型被动」的统一处理方式：技能表负责"说"，事件层负责"做"。
 */
public class SkillSoulBond implements Skill {

    public static final String ID = "su5_soul_bond";

    @Override
    public MedalType medal() {
        return MedalType.SUMMONER;
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
        return Component.translatable("skill.lost_realm." + ID);
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("skill.lost_realm." + ID + ".desc",
                MedalsConfig.S5_SOUL_PER_HIT.get(),
                MedalsConfig.S5_SOUL_COOLDOWN.get() / 20));
    }
}
