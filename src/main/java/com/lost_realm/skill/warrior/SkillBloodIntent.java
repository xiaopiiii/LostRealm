package com.lost_realm.skill.warrior;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import com.lost_realm.attribute.RealmAttributes;
import com.lost_realm.config.MedalsConfig;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;
import com.lost_realm.util.AttributeGuard;
import com.lost_realm.util.ModifierIds;

/**
 * Lv.60【血意】
 *
 * <p>暴击时附带 20% 已损生命值伤害;当自身血量低于 50% 时,
 * 该伤害提升为 40% 已损生命值,且额外 +10% 暴击率。
 *
 * <p>「已损生命值」指<b>目标</b>损失的生命值(最大值 - 当前值)。
 */
public class SkillBloodIntent implements Skill {

    public static final String ID = "w30_blood_intent";

    @Override
    public MedalType medal() {
        return MedalType.WARRIOR;
    }

    @Override
    public int unlockLevel() {
        return 60;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component displayName() {
        return Component.translatable("skill.lost_realm.w30_blood_intent");
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("skill.lost_realm.w30_blood_intent.desc",
                MedalsConfig.W30_LOST_HP_DAMAGE.get(),
                MedalsConfig.W30_LOST_HP_DAMAGE_LOW.get(),
                MedalsConfig.W30_LOW_HP_THRESHOLD.get(),
                MedalsConfig.W30_LOW_HP_CRIT_CHANCE.get()));
    }

    @Override
    public void applyPassive(ServerPlayer player, int level) {
        double ratio = player.getMaxHealth() <= 0 ? 1.0D
                : player.getHealth() / player.getMaxHealth();
        boolean low = ratio < MedalsConfig.pct(MedalsConfig.W30_LOW_HP_THRESHOLD);

        if (low) {
            // 低血量:额外暴击率
            AttributeGuard.apply(player, RealmAttributes.critChance(),
                    ModifierIds.WARRIOR_W30_CRIT_CHANCE, "warrior_w30_crit_chance",
                    MedalsConfig.pct(MedalsConfig.W30_LOW_HP_CRIT_CHANCE),
                    AttributeModifier.Operation.ADDITION);
        } else {
            AttributeGuard.remove(player, RealmAttributes.critChance(), ModifierIds.WARRIOR_W30_CRIT_CHANCE);
        }
    }

    /**
     * 计算暴击时额外附带的对「目标已损生命值」伤害。
     *
     * @param attacker 攻击者(判断是否处于低血量)
     * @param target   被击目标
     * @return 额外伤害值
     */
    public static double bonusDamage(ServerPlayer attacker, LivingEntity target) {
        double lostHp = Math.max(0.0D, target.getMaxHealth() - target.getHealth());
        double ratio = attacker.getMaxHealth() <= 0 ? 1.0D
                : attacker.getHealth() / attacker.getMaxHealth();
        boolean low = ratio < MedalsConfig.pct(MedalsConfig.W30_LOW_HP_THRESHOLD);

        double pct = low
                ? MedalsConfig.pct(MedalsConfig.W30_LOST_HP_DAMAGE_LOW)
                : MedalsConfig.pct(MedalsConfig.W30_LOST_HP_DAMAGE);
        return lostHp * pct;
    }

    /** 攻击者是否处于「低血量」的【血意】强化状态。 */
    public static boolean isLowHp(ServerPlayer attacker) {
        double ratio = attacker.getMaxHealth() <= 0 ? 1.0D
                : attacker.getHealth() / attacker.getMaxHealth();
        return ratio < MedalsConfig.pct(MedalsConfig.W30_LOW_HP_THRESHOLD);
    }

    @Override
    public void clear(net.minecraft.world.entity.player.Player player) {
        AttributeGuard.clearByPrefix(player, RealmAttributes.critChance(), "warrior_w30_");
    }
}
