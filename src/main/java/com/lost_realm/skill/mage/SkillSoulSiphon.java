package com.lost_realm.skill.mage;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import com.lost_realm.compat.goety.GoetyBridge;
import com.lost_realm.config.MedalsConfig;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;
import com.lost_realm.util.ModifierIds;

/**
 * Lv.30【灵魂虹吸】(被动)
 *
 * <ul>
 *   <li>法术命中敌人时,<b>30%</b> 概率返还该次法术消耗的 <b>50%</b> 灵魂</li>
 *   <li>若目标带有<b>负面效果</b>,概率提升至 <b>60%</b>,返还 <b>75%</b></li>
 *   <li>常驻属性:法术持续时间 <b>+10%</b></li>
 * </ul>
 *
 * <h2>「负面效果」的判定</h2>
 * 遍历目标的全部激活效果,只要存在任意一个
 * {@link MobEffectCategory} 不为 {@code BENEFICIAL} 的效果(即 HARMFUL 或 NEUTRAL),
 * 即视为「带负面效果」。这覆盖了原版的中毒 / 缓慢 / 虚弱,
 * 以及 Goety 与其它模组施加的减益,无需维护枚举清单。
 *
 * <h2>返还机制</h2>
 * Goety 未开放「取消消耗」钩子,因此沿用「先扣后补」策略:
 * 法术命中后按概率通过 {@link GoetyBridge#increaseSouls} 把消耗的一部分补回。
 */
public class SkillSoulSiphon implements Skill {

    public static final String ID = "m15_soul_siphon";

    @Override
    public MedalType medal() {
        return MedalType.MAGE;
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
        return Component.translatable("skill.lost_realm.m15_soul_siphon");
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("skill.lost_realm.m15_soul_siphon.desc",
                MedalsConfig.M15_REFUND_CHANCE.get(),
                MedalsConfig.M15_REFUND_RATIO.get(),
                MedalsConfig.M15_DEBUFF_REFUND_CHANCE.get(),
                MedalsConfig.M15_DEBUFF_REFUND_RATIO.get(),
                MedalsConfig.M15_SPELL_DURATION.get()));
    }

    @Override
    public void applyPassive(ServerPlayer player, int level) {
        if (level < 30) {
            return;
        }
        GoetyBridge goety = GoetyBridge.get();
        if (goety == null) {
            return;
        }
        // 常驻:法术持续时间 +10%
        goety.applySpellDuration(player, ModifierIds.MAGE_M15_SPELL_DURATION,
                "mage_m15_spell_duration", MedalsConfig.pct(MedalsConfig.M15_SPELL_DURATION));
    }

    /**
     * 结算一次「法术命中」的灵魂返还。
     *
     * @param player   施法者
     * @param target   被命中的目标
     * @param soulCost 本次施法的灵魂消耗
     * @return 实际返还的灵魂量(0 表示未触发)
     */
    public static int tryRefund(ServerPlayer player, LivingEntity target, int soulCost) {
        GoetyBridge goety = GoetyBridge.get();
        if (goety == null || soulCost <= 0) {
            return 0;
        }
        if (com.lost_realm.data.MedalSource.level(player, MedalType.MAGE) < 15) {
            return 0;
        }

        boolean debuffed = hasNegativeEffect(target);
        double chance = debuffed
                ? MedalsConfig.pct(MedalsConfig.M15_DEBUFF_REFUND_CHANCE)
                : MedalsConfig.pct(MedalsConfig.M15_REFUND_CHANCE);
        double ratio = debuffed
                ? MedalsConfig.pct(MedalsConfig.M15_DEBUFF_REFUND_RATIO)
                : MedalsConfig.pct(MedalsConfig.M15_REFUND_RATIO);

        if (player.getRandom().nextDouble() >= chance) {
            return 0;
        }
        int refund = (int) Math.round(soulCost * ratio);
        if (refund <= 0) {
            return 0;
        }
        goety.increaseSouls(player, refund);

        // 视觉反馈:颜色区分是否吃到「负面效果加成」
        if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            var particle = debuffed
                    ? net.minecraft.core.particles.ParticleTypes.SCULK_SOUL
                    : net.minecraft.core.particles.ParticleTypes.SOUL;
            sl.sendParticles(particle,
                    player.getX(), player.getY() + 1.2D, player.getZ(),
                    debuffed ? 14 : 8, 0.4D, 0.4D, 0.4D, 0.02D);
        }
        return refund;
    }

    /**
     * 判定目标是否带有任意负面效果。
     *
     * <p>只要存在一个非增益效果(HARMFUL / NEUTRAL)即返回 {@code true}。
     */
    public static boolean hasNegativeEffect(LivingEntity target) {
        if (target == null) {
            return false;
        }
        for (MobEffectInstance inst : target.getActiveEffects()) {
            if (inst.getEffect().getCategory() != MobEffectCategory.BENEFICIAL) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void clear(net.minecraft.world.entity.player.Player player) {
        GoetyBridge goety = GoetyBridge.get();
        if (goety == null) {
            return;
        }
        goety.applySpellDuration(player, ModifierIds.MAGE_M15_SPELL_DURATION,
                "mage_m15_spell_duration", 0.0D);
    }
}
