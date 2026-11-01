package com.lost_realm.skill.tank;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import com.lost_realm.config.MedalsConfig;
import com.lost_realm.data.PlayerMedals;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;
import com.lost_realm.util.AttributeGuard;
import com.lost_realm.util.ModifierIds;

/**
 * Lv.60【生生不息】
 *
 * <p>血量低于 10% 时自动触发:5 秒内每秒恢复 10% 最大生命值,
 * 并获得 20 点伤害吸收与生命提升(Health Boost)Buff。冷却 90 秒。
 *
 * <p>「生命提升」使用原版 {@code HEALTH_BOOST} 效果实现。
 */
public class SkillEndlessLife implements Skill {

    public static final String ID = "t30_endless_life";
    public static final String STATE_REMAIN = ID + "_remain";
    /** 冷却剩余 tick */
    public static final String STATE_CD = ID + "_cd";

    @Override
    public MedalType medal() {
        return MedalType.TANK;
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
        return Component.translatable("skill.lost_realm.t30_endless_life");
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("skill.lost_realm.t30_endless_life.desc",
                MedalsConfig.T30_HP_THRESHOLD.get(),
                MedalsConfig.T30_DURATION_SECONDS.get(),
                MedalsConfig.T30_HEAL_PER_SECOND.get(),
                MedalsConfig.T30_ABSORB.get(),
                MedalsConfig.T30_COOLDOWN_SECONDS.get()));
    }

    public static boolean isActive(PlayerMedals data) {
        return data.getState(STATE_REMAIN) > 0;
    }

    @Override
    public void applyPassive(ServerPlayer player, int level) {
        PlayerMedals data = PlayerMedals.of(player);

        // 冷却递减
        int cd = data.getState(STATE_CD);
        if (cd > 0) {
            data.setState(STATE_CD, cd - 1);
        }

        int remain = data.getState(STATE_REMAIN);

        if (remain <= 0) {
            // 检查触发条件:血量低于阈值且不在冷却
            double ratio = player.getMaxHealth() <= 0 ? 1.0D
                    : player.getHealth() / player.getMaxHealth();
            if (ratio < MedalsConfig.pct(MedalsConfig.T30_HP_THRESHOLD) && cd <= 0) {
                // 触发
                remain = MedalsConfig.T30_DURATION_SECONDS.get() * 20;
                data.setState(STATE_REMAIN, remain);
                data.setState(STATE_CD, com.lost_realm.item.TimeCapsuleItem.applyReduction(
                        data, MedalsConfig.T30_COOLDOWN_SECONDS.get() * 20));

                // 伤害吸收
                player.setAbsorptionAmount(player.getAbsorptionAmount() + MedalsConfig.T30_ABSORB.get());
                // 生命提升(给予相当于 4 颗心的额外生命)
                player.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST,
                        MedalsConfig.T30_DURATION_SECONDS.get() * 20 + 20, 4, false, true, true));
                // 视觉反馈
                if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,
                            player.getX(), player.getY() + 1.2D, player.getZ(),
                            6, 0.4D, 0.4D, 0.4D, 0.02D);
                }
            }
            // 未触发或刚触发:本 tick 无需继续处理
            return;
        }

        // 持续中:每秒恢复 10% 最大生命值
        data.setState(STATE_REMAIN, remain - 1);
        if ((remain - 1) % 20 == 0 && player.getHealth() < player.getMaxHealth()) {
            double heal = player.getMaxHealth() * MedalsConfig.pct(MedalsConfig.T30_HEAL_PER_SECOND);
            player.heal((float) heal);
        }
    }

    @Override
    public void clear(net.minecraft.world.entity.player.Player player) {
        PlayerMedals data = PlayerMedals.of(player);
        data.setState(STATE_REMAIN, 0);
        data.setState(STATE_CD, 0);
        AttributeGuard.clearByPrefix(player, Attributes.MAX_HEALTH, "tank_t30_");
    }
}
