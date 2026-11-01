package com.lost_realm.skill.warrior;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeMod;

import com.lost_realm.attribute.RealmAttributes;
import com.lost_realm.config.MedalsConfig;
import com.lost_realm.data.PlayerMedals;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;
import com.lost_realm.util.AttributeGuard;
import com.lost_realm.util.ModifierIds;

/**
 * Lv.50【战迹血淋】(主动)
 *
 * <p>开启后 10 秒内:攻击距离 +1、攻速 +50%、移速 +10%、生命偷取 +20%。冷却 70 秒。
 */
public class SkillWarTrail implements Skill {

    public static final String ID = "w25_war_trail";
    public static final String STATE_REMAIN = ID + "_remain";

    @Override
    public MedalType medal() {
        return MedalType.WARRIOR;
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
        return Component.translatable("skill.lost_realm.w25_war_trail");
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("skill.lost_realm.w25_war_trail.desc",
                MedalsConfig.W25_DURATION_SECONDS.get(),
                MedalsConfig.W25_ATTACK_RANGE.get(),
                MedalsConfig.W25_ATTACK_SPEED.get(),
                MedalsConfig.W25_MOVE_SPEED.get(),
                MedalsConfig.W25_LIFESTEAL.get(),
                MedalsConfig.W25_COOLDOWN_SECONDS.get()));
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public int cooldownTicks() {
        return MedalsConfig.W25_COOLDOWN_SECONDS.get() * 20;
    }

    public static boolean isActive(PlayerMedals data) {
        return data.getState(STATE_REMAIN) > 0;
    }

    @Override
    public boolean activate(ServerPlayer player, int level) {
        PlayerMedals data = PlayerMedals.of(player);
        if (isActive(data)) {
            return false;
        }
        data.setState(STATE_REMAIN, MedalsConfig.W25_DURATION_SECONDS.get() * 20);
        return true;
    }

    @Override
    public void applyPassive(ServerPlayer player, int level) {
        PlayerMedals data = PlayerMedals.of(player);
        int remain = data.getState(STATE_REMAIN);
        if (remain <= 0) {
            clear(player);
            return;
        }
        data.setState(STATE_REMAIN, remain - 1);

        // 攻击距离
        AttributeGuard.apply(player, ForgeMod.ENTITY_REACH.get(),
                ModifierIds.WARRIOR_W25_ATTACK_RANGE, "warrior_w25_attack_range",
                MedalsConfig.W25_ATTACK_RANGE.get(), AttributeModifier.Operation.ADDITION);

        AttributeGuard.apply(player, Attributes.ATTACK_SPEED,
                ModifierIds.WARRIOR_W25_ATTACK_SPEED, "warrior_w25_attack_speed",
                MedalsConfig.pct(MedalsConfig.W25_ATTACK_SPEED), AttributeModifier.Operation.MULTIPLY_BASE);

        AttributeGuard.apply(player, Attributes.MOVEMENT_SPEED,
                ModifierIds.WARRIOR_W25_MOVE_SPEED, "warrior_w25_move_speed",
                MedalsConfig.pct(MedalsConfig.W25_MOVE_SPEED), AttributeModifier.Operation.MULTIPLY_BASE);

        AttributeGuard.apply(player, RealmAttributes.lifeSteal(),
                ModifierIds.WARRIOR_W25_LIFESTEAL, "warrior_w25_lifesteal",
                MedalsConfig.pct(MedalsConfig.W25_LIFESTEAL), AttributeModifier.Operation.ADDITION);
    }

    @Override
    public void clear(net.minecraft.world.entity.player.Player player) {
        AttributeGuard.clearByPrefix(player, ForgeMod.ENTITY_REACH.get(), "warrior_w25_");
        AttributeGuard.clearByPrefix(player, Attributes.ATTACK_SPEED, "warrior_w25_");
        AttributeGuard.clearByPrefix(player, Attributes.MOVEMENT_SPEED, "warrior_w25_");
        AttributeGuard.clearByPrefix(player, RealmAttributes.lifeSteal(), "warrior_w25_");
    }
}
