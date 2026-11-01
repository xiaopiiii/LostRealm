package com.lost_realm.skill.tank;

import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.lost_realm.config.MedalsConfig;
import com.lost_realm.data.PlayerMedals;
import com.lost_realm.item.TankAttributes;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;
import com.lost_realm.util.AttributeGuard;
import com.lost_realm.util.ModifierIds;

/**
 * Lv.40【不屈壁垒】(主动)
 *
 * <p>持续 15 秒:护甲 +20%、减伤 +20%、生命恢复 III、免疫击退。
 * 释放瞬间击退周围 5 格内的生物 2 格,造成 12 点伤害并施加缓慢 I(5 秒)。冷却 75 秒。
 */
public class SkillUnyieldingBulwark implements Skill {

    public static final String ID = "t20_unyielding_bulwark";
    public static final String STATE_REMAIN = ID + "_remain";

    @Override
    public MedalType medal() {
        return MedalType.TANK;
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
        return Component.translatable("skill.lost_realm.t20_unyielding_bulwark");
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("skill.lost_realm.t20_unyielding_bulwark.desc",
                MedalsConfig.T20_DURATION_SECONDS.get(),
                MedalsConfig.T20_ARMOR_BONUS.get(),
                MedalsConfig.T20_DAMAGE_REDUCTION.get(),
                MedalsConfig.T20_KNOCKBACK_RADIUS.get(),
                MedalsConfig.T20_BURST_DAMAGE.get(),
                MedalsConfig.T20_COOLDOWN_SECONDS.get()));
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public int cooldownTicks() {
        return MedalsConfig.T20_COOLDOWN_SECONDS.get() * 20;
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
        data.setState(STATE_REMAIN, MedalsConfig.T20_DURATION_SECONDS.get() * 20);

        // 释放瞬间的范围效果
        releaseBurst(player);
        return true;
    }

    /** 释放瞬间:击退周围生物 + 造成伤害 + 缓慢 I。 */
    private void releaseBurst(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        double radius = MedalsConfig.T20_KNOCKBACK_RADIUS.get();
        AABB box = player.getBoundingBox().inflate(radius);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (target == player || target.isAlliedTo(player)) {
                continue;
            }
            // 击退:半径内向外推
            Vec3 dir = target.position().subtract(player.position());
            Vec3 horizontal = new Vec3(dir.x, 0, dir.z);
            if (horizontal.lengthSqr() < 1.0E-4D) {
                horizontal = new Vec3(1, 0, 0);
            }
            horizontal = horizontal.normalize().scale(MedalsConfig.T20_KNOCKBACK_DISTANCE.get() * 0.5D);
            target.push(horizontal.x, 0.35D, horizontal.z);

            // 伤害(以玩家为攻击者)
            target.hurt(player.damageSources().playerAttack(player),
                    MedalsConfig.T20_BURST_DAMAGE.get());

            // 缓慢 I 5 秒
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    MedalsConfig.T20_SLOWNESS_SECONDS.get() * 20, 0, false, true, true));
        }

        level.sendParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY() + 0.2D, player.getZ(),
                40, 2.0D, 0.3D, 2.0D, 0.05D);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.9F, 0.7F);
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

        // 护甲 +20%
        AttributeGuard.apply(player, Attributes.ARMOR,
                ModifierIds.TANK_T20_ARMOR, "tank_t20_armor",
                MedalsConfig.pct(MedalsConfig.T20_ARMOR_BONUS), AttributeModifier.Operation.MULTIPLY_BASE);

        // 伤害减免 +20%
        AttributeGuard.apply(player, TankAttributes.DAMAGE_REDUCTION.get(),
                ModifierIds.TANK_T20_DAMAGE_REDUCTION, "tank_t20_damage_reduction",
                MedalsConfig.pct(MedalsConfig.T20_DAMAGE_REDUCTION), AttributeModifier.Operation.ADDITION);

        // 免疫击退:击退抗性拉满
        AttributeGuard.apply(player, Attributes.KNOCKBACK_RESISTANCE,
                ModifierIds.TANK_T20_KNOCKBACK_RESIST, "tank_t20_knockback_resist",
                1.0D, AttributeModifier.Operation.ADDITION);

        // 生命恢复 III
        if (player.tickCount % 20 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 2, false, false, true));
        }
    }

    @Override
    public void clear(net.minecraft.world.entity.player.Player player) {
        AttributeGuard.clearByPrefix(player, Attributes.ARMOR, "tank_t20_");
        AttributeGuard.clearByPrefix(player, TankAttributes.DAMAGE_REDUCTION.get(), "tank_t20_");
        AttributeGuard.clearByPrefix(player, Attributes.KNOCKBACK_RESISTANCE, "tank_t20_");
    }
}
