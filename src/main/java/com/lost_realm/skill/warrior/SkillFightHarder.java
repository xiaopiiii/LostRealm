package com.lost_realm.skill.warrior;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import com.lost_realm.attribute.RealmAttributes;
import com.lost_realm.config.MedalsConfig;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;
import com.lost_realm.util.AttributeGuard;
import com.lost_realm.util.ModifierIds;

/**
 * Lv.20【愈战愈勇】
 *
 * <p>生命值低于 60% 时,每损失 10% 最大生命值,获得 +2 护甲穿透与 +3% 生命偷取。
 *
 * <p>例:血量 30%(损失 70%)时,损失档位 = 7,获得 +14 护甲穿透、+21% 生命偷取。
 * 所有修饰符固定 UUID,幂等刷新。
 */
public class SkillFightHarder implements Skill {

    public static final String ID = "w10_fight_harder";

    @Override
    public MedalType medal() {
        return MedalType.WARRIOR;
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
        return Component.translatable("skill.lost_realm.w10_fight_harder");
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("skill.lost_realm.w10_fight_harder.desc",
                MedalsConfig.W10_HP_THRESHOLD.get(),
                MedalsConfig.W10_ARMOR_PIERCE_PER_STEP.get(),
                MedalsConfig.W10_LIFESTEAL_PER_STEP.get()));
    }

    @Override
    public void applyPassive(ServerPlayer player, int level) {
        double maxHp = player.getMaxHealth();
        double hp = player.getHealth();
        double ratio = maxHp <= 0 ? 1.0D : hp / maxHp;
        double threshold = MedalsConfig.pct(MedalsConfig.W10_HP_THRESHOLD);

        if (ratio >= threshold) {
            // 未触发:清除
            AttributeGuard.remove(player, RealmAttributes.armorPierce(), ModifierIds.WARRIOR_W10_ARMOR_PIERCE);
            AttributeGuard.remove(player, RealmAttributes.lifeSteal(), ModifierIds.WARRIOR_W10_LIFESTEAL);
            return;
        }

        // 损失的生命百分比 / 10 => 档位
        double lostPct = (1.0D - ratio) * 100.0D;
        int steps = (int) Math.floor(lostPct / 10.0D);
        if (steps <= 0) {
            return;
        }

        double pierce = steps * MedalsConfig.W10_ARMOR_PIERCE_PER_STEP.get();
        double lifesteal = steps * MedalsConfig.pct(MedalsConfig.W10_LIFESTEAL_PER_STEP);

        AttributeGuard.apply(player, RealmAttributes.armorPierce(),
                ModifierIds.WARRIOR_W10_ARMOR_PIERCE, "warrior_w10_armor_pierce",
                pierce, AttributeModifier.Operation.ADDITION);

        AttributeGuard.apply(player, RealmAttributes.lifeSteal(),
                ModifierIds.WARRIOR_W10_LIFESTEAL, "warrior_w10_lifesteal",
                lifesteal, AttributeModifier.Operation.ADDITION);
    }
}
