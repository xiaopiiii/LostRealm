package com.lost_realm.skill.blade;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import com.lost_realm.attribute.RealmAttributes;
import com.lost_realm.config.MedalsConfig;
import com.lost_realm.effect.ModEffects;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;

/**
 * Lv.10【撕裂】
 *
 * <p>近战攻击有 20% 概率使目标流血 5 秒。
 * 攻击处于流血状态的目标时:额外 +4 护甲穿透,+10% 暴击率。
 *
 * <p>流血使用本模组自注册的 {@code lost_realm:bleeding}(beta30 之前是
 * Apothic Attributes 的 {@code attributeslib:bleeding})。
 * 对流血目标的加成由 {@code CombatHandler} 在近战命中时动态施加(每 tick 重新计算,固定 UUID)。
 */
public class SkillRend implements Skill {

    public static final String ID = "b5_rend";

    @Override
    public MedalType medal() {
        return MedalType.BLADE;
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
        return Component.translatable("skill.lost_realm.b5_rend");
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("skill.lost_realm.b5_rend.desc",
                MedalsConfig.B5_BLEED_CHANCE.get(),
                MedalsConfig.B5_BLEED_SECONDS.get(),
                MedalsConfig.B5_BLEED_ARMOR_PIERCE.get(),
                MedalsConfig.B5_BLEED_CRIT_CHANCE.get()));
    }

    @Override
    public void applyPassive(ServerPlayer player, int level) {
        // 施加流血在命中时处理;对流血目标的加成在命中前动态结算
    }

    /** 目标是否正在流血。 */
    public static boolean isBleeding(LivingEntity target) {
        return target.hasEffect(ModEffects.BLEEDING.get());
    }

    /** 尝试对目标施加流血效果。 */
    public static void tryApplyBleeding(LivingEntity target, int level) {
        if (target.level().isClientSide) {
            return;
        }
        if (target.getRandom().nextDouble() >= MedalsConfig.pct(MedalsConfig.B5_BLEED_CHANCE)) {
            return;
        }
        target.addEffect(new MobEffectInstance(
                ModEffects.BLEEDING.get(),
                MedalsConfig.B5_BLEED_SECONDS.get() * 20,
                0, false, true, true));
    }
}
