package com.lost_realm.skill.blade;

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
 * Lv.40【寒霜之刃】
 *
 * <p>近战附加 +5 冰霜伤害;命中时有 10% 概率冰冻目标(以冰块视觉效果呈现)。
 *
 * <p>冰霜伤害使用 Apothic Attributes 的 {@code attributeslib:cold_damage}(自带减速效果)。
 * 冰冻通过施加缓慢 + 挖掘疲劳模拟,并播放冰系粒子。
 */
public class SkillBladeOfFrost implements Skill {

    public static final String ID = "b20_blade_of_frost";

    @Override
    public MedalType medal() {
        return MedalType.BLADE;
    }

    @Override
    public int unlockLevel() {
        return 40;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component displayName() {
        return Component.translatable("skill.lost_realm.b20_blade_of_frost");
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("skill.lost_realm.b20_blade_of_frost.desc",
                MedalsConfig.B20_COLD_DAMAGE.get(),
                MedalsConfig.B20_FREEZE_CHANCE.get()));
    }

    @Override
    public void applyPassive(ServerPlayer player, int level) {
        // 冰霜伤害为常驻属性
        AttributeGuard.apply(player, RealmAttributes.coldDamage(),
                ModifierIds.BLADE_W20_COLD_DAMAGE, "blade_w20_cold_damage",
                MedalsConfig.B20_COLD_DAMAGE.get(), AttributeModifier.Operation.ADDITION);
    }

    /** 命中时尝试冰冻目标。 */
    public static void tryFreeze(ServerPlayer player, LivingEntity target) {
        if (target.level().isClientSide) {
            return;
        }
        if (target.getRandom().nextDouble() >= MedalsConfig.pct(MedalsConfig.B20_FREEZE_CHANCE)) {
            return;
        }
        int ticks = MedalsConfig.B20_FREEZE_SECONDS.get() * 20;
        // 视觉冰冻:缓慢 + 挖掘疲劳 + 冰块粒子
        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, ticks, 4, false, false, true));
        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN, ticks, 2, false, false, true));
        if (target.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                    24, 0.4D, 0.6D, 0.4D, 0.02D);
        }
    }

    @Override
    public void clear(net.minecraft.world.entity.player.Player player) {
        AttributeGuard.clearByPrefix(player, RealmAttributes.coldDamage(), "blade_w20_");
    }
}
