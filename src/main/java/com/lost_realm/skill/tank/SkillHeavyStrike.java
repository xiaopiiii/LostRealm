package com.lost_realm.skill.tank;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

import com.lost_realm.attribute.RealmAttributes;
import com.lost_realm.config.MedalsConfig;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;
import com.lost_realm.util.AttributeGuard;
import com.lost_realm.util.ModifierIds;

import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Lv.10【重击】
 *
 * <p>使用攻速 ≤ 1 的武器攻击时:伤害 +20%、暴击率 +20%、暴击伤害 +20%。
 *
 * <p>判定基于主手武器当前的攻击速度属性(含修饰符)。
 * 满足条件时以固定 UUID 施加,不满足则移除。
 */
public class SkillHeavyStrike implements Skill {

    public static final String ID = "t5_heavy_strike";

    @Override
    public MedalType medal() {
        return MedalType.TANK;
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
        return Component.translatable("skill.lost_realm.t5_heavy_strike");
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("skill.lost_realm.t5_heavy_strike.desc",
                MedalsConfig.T5_SLOW_WEAPON_ATTACK_SPEED.get(),
                MedalsConfig.T5_DAMAGE.get(),
                MedalsConfig.T5_CRIT_CHANCE.get(),
                MedalsConfig.T5_CRIT_DAMAGE.get()));
    }

    @Override
    public void applyPassive(ServerPlayer player, int level) {
        // 每次攻击时动态结算;这里只在满足条件时维持修饰符
        double atkSpeed = getWeaponAttackSpeed(player);
        boolean heavy = atkSpeed > 0 && atkSpeed <= MedalsConfig.T5_SLOW_WEAPON_ATTACK_SPEED.get();

        if (!heavy) {
            clear(player);
            return;
        }
        AttributeGuard.apply(player, Attributes.ATTACK_DAMAGE,
                ModifierIds.TANK_T5_DAMAGE, "tank_t5_damage",
                MedalsConfig.pct(MedalsConfig.T5_DAMAGE), AttributeModifier.Operation.MULTIPLY_BASE);

        AttributeGuard.apply(player, RealmAttributes.critChance(),
                ModifierIds.TANK_T5_CRIT_CHANCE, "tank_t5_crit_chance",
                MedalsConfig.pct(MedalsConfig.T5_CRIT_CHANCE), AttributeModifier.Operation.ADDITION);

        AttributeGuard.apply(player, RealmAttributes.critDamage(),
                ModifierIds.TANK_T5_CRIT_DAMAGE, "tank_t5_crit_damage",
                MedalsConfig.pct(MedalsConfig.T5_CRIT_DAMAGE), AttributeModifier.Operation.MULTIPLY_BASE);

        // 视觉提示:持慢速武器时给予微弱粒子(可选)
    }

    /** 读取主手武器带来的攻击速度修正值。 */
    public static double getWeaponAttackSpeed(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.isEmpty()) {
            return 4.0D; // 空手默认 4.0,不属于慢速武器
        }
        // 玩家基础攻速 4.0,武器通过修饰符削减;这里读取玩家攻击速度属性的最终值
        var inst = player.getAttribute(Attributes.ATTACK_SPEED);
        if (inst == null) {
            return 4.0D;
        }
        // 直接使用当前攻击速度值(含武器修饰符),慢速武器一般 ≤ 1.6(如斧 1.0,剑 1.6)
        return inst.getValue();
    }

    @Override
    public void clear(net.minecraft.world.entity.player.Player player) {
        AttributeGuard.clearByPrefix(player, Attributes.ATTACK_DAMAGE, "tank_t5_");
        AttributeGuard.clearByPrefix(player, RealmAttributes.critChance(), "tank_t5_");
        AttributeGuard.clearByPrefix(player, RealmAttributes.critDamage(), "tank_t5_");
    }
}
