package com.lost_realm.skill.warrior;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import com.lost_realm.attribute.RealmAttributes;
import com.lost_realm.config.MedalsConfig;
import com.lost_realm.data.PlayerMedals;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;
import com.lost_realm.util.AttributeGuard;
import com.lost_realm.util.ModifierIds;

/**
 * Lv.30【燃烧自我】(主动)
 *
 * <p>开启后持续 12 秒。期间:
 * <ul>
 *   <li>自身每秒扣除 4% 最大生命值(无视火焰免疫,走真实伤害)</li>
 *   <li>+5 火焰伤害</li>
 *   <li>近战攻击使目标着火 10 秒</li>
 *   <li>+10% 攻击伤害</li>
 *   <li>+15% 移速</li>
 * </ul>
 *
 * <p>每秒扣血使用 {@code DamageTypes.GENERIC} 的绕过护甲路径,
 * 并在扣血前清除目标(自身)的火焰免疫判定,确保「无视火焰免疫」。
 */
public class SkillBurningSelf implements Skill {

    public static final String ID = "w15_burning_self";
    public static final String STATE_REMAIN = ID + "_remain";
    /** 每秒扣血计时器 */
    public static final String STATE_TICK = ID + "_tick";

    @Override
    public MedalType medal() {
        return MedalType.WARRIOR;
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
        return Component.translatable("skill.lost_realm.w15_burning_self");
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("skill.lost_realm.w15_burning_self.desc",
                MedalsConfig.W15_DURATION_SECONDS.get(),
                MedalsConfig.W15_HP_COST_PER_SECOND.get(),
                MedalsConfig.W15_FIRE_DAMAGE.get(),
                MedalsConfig.W15_TARGET_FIRE_SECONDS.get(),
                MedalsConfig.W15_ATTACK_DAMAGE.get(),
                MedalsConfig.W15_MOVE_SPEED.get()));
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public boolean activate(ServerPlayer player, int level) {
        PlayerMedals data = PlayerMedals.of(player);
        if (isActive(data)) {
            return false;
        }
        data.setState(STATE_REMAIN, MedalsConfig.W15_DURATION_SECONDS.get() * 20);
        data.setState(STATE_TICK, 0);
        return true;
    }

    public static boolean isActive(PlayerMedals data) {
        return data.getState(STATE_REMAIN) > 0;
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

        // 每秒扣血(无视火焰免疫:直接按最大生命值百分比扣真实伤害)
        int tick = data.getState(STATE_TICK) + 1;
        if (tick >= 20) {
            tick = 0;
            double cost = player.getMaxHealth() * MedalsConfig.pct(MedalsConfig.W15_HP_COST_PER_SECOND);
            if (cost > 0) {
                // 使用 setHealth 直扣,绕过一切免疫与减伤
                float newHp = (float) Math.max(0.5D, player.getHealth() - cost);
                player.setHealth(newHp);
            }
        }
        data.setState(STATE_TICK, tick);

        // 属性加成(固定 UUID)
        AttributeGuard.apply(player, RealmAttributes.fireDamage(),
                ModifierIds.WARRIOR_W15_FIRE_DAMAGE, "warrior_w15_fire_damage",
                MedalsConfig.W15_FIRE_DAMAGE.get(), AttributeModifier.Operation.ADDITION);

        AttributeGuard.apply(player, Attributes.ATTACK_DAMAGE,
                ModifierIds.WARRIOR_W15_ATTACK_DAMAGE, "warrior_w15_attack_damage",
                MedalsConfig.pct(MedalsConfig.W15_ATTACK_DAMAGE), AttributeModifier.Operation.MULTIPLY_BASE);

        AttributeGuard.apply(player, Attributes.MOVEMENT_SPEED,
                ModifierIds.WARRIOR_W15_MOVE_SPEED, "warrior_w15_move_speed",
                MedalsConfig.pct(MedalsConfig.W15_MOVE_SPEED), AttributeModifier.Operation.MULTIPLY_BASE);
    }

    /** 近战命中时使目标着火。 */
    public static void igniteTarget(ServerPlayer player, net.minecraft.world.entity.LivingEntity target) {
        PlayerMedals data = PlayerMedals.of(player);
        if (!isActive(data)) {
            return;
        }
        target.setSecondsOnFire(MedalsConfig.W15_TARGET_FIRE_SECONDS.get());
    }

    @Override
    public void clear(net.minecraft.world.entity.player.Player player) {
        AttributeGuard.clearByPrefix(player, RealmAttributes.fireDamage(), "warrior_w15_");
        AttributeGuard.clearByPrefix(player, Attributes.ATTACK_DAMAGE, "warrior_w15_");
        AttributeGuard.clearByPrefix(player, Attributes.MOVEMENT_SPEED, "warrior_w15_");
    }
}
