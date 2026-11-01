package com.lost_realm.skill.archer;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import com.lost_realm.attribute.RealmAttributes;
import com.lost_realm.config.MedalsConfig;
import com.lost_realm.data.PlayerMedals;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;
import com.lost_realm.util.AttributeGuard;
import com.lost_realm.util.ModifierIds;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Lv.40【疾风骤雨】(主动)
 *
 * <p>开启后 10 秒内:拉弓不减速、移速 +20%(骑马 +30%)、拉弓速度 +80%、箭矢伤害 +10%。
 * 期间每次击杀延长持续时间,最多延长 5 秒。冷却 70 秒。
 *
 * <p>「拉弓不减速」通过给玩家施加一个巨大的移动速度补偿实现 —— 原版拉弓会降低移速,
 * 这里在激活期间持续施加固定 UUID 的移速修饰符抵消减速,并叠加额外加成。
 * 由于所有修饰符使用固定 UUID,重复施加只会覆盖,不会叠加导致崩溃。
 */
public class SkillGaleStorm implements Skill {

    public static final String ID = "s30_gale_storm";
    /** 激活剩余 tick */
    public static final String STATE_REMAIN = ID + "_remain";
    /** 已延长的累计 tick(用于限制最多 5 秒) */
    public static final String STATE_EXTENDED = ID + "_extended";

    @Override
    public MedalType medal() {
        return MedalType.ARCHER;
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
        return Component.translatable("skill.lost_realm.s30_gale_storm");
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("skill.lost_realm.s30_gale_storm.desc",
                MedalsConfig.S30_DURATION_SECONDS.get(),
                MedalsConfig.S30_MOVE_SPEED.get(),
                MedalsConfig.S30_MOVE_SPEED_MOUNTED.get(),
                MedalsConfig.S30_DRAW_SPEED.get(),
                MedalsConfig.S30_ARROW_DAMAGE.get(),
                MedalsConfig.S30_MAX_EXTEND.get(),
                MedalsConfig.S30_COOLDOWN_SECONDS.get()));
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public int cooldownTicks() {
        return MedalsConfig.S30_COOLDOWN_SECONDS.get() * 20;
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
        data.setState(STATE_REMAIN, MedalsConfig.S30_DURATION_SECONDS.get() * 20);
        data.setState(STATE_EXTENDED, 0);
        return true;
    }

    @Override
    public void applyPassive(ServerPlayer player, int level) {
        PlayerMedals data = PlayerMedals.of(player);
        int remain = data.getState(STATE_REMAIN);

        if (remain <= 0) {
            // 未激活:清理修饰符
            clear(player);
            return;
        }

        data.setState(STATE_REMAIN, remain - 1);

        boolean mounted = player.getVehicle() != null;
        double movePct = mounted
                ? MedalsConfig.pct(MedalsConfig.S30_MOVE_SPEED_MOUNTED)
                : MedalsConfig.pct(MedalsConfig.S30_MOVE_SPEED);

        // 移速加成(固定 UUID)
        AttributeGuard.apply(player, Attributes.MOVEMENT_SPEED,
                ModifierIds.ARCHER_S30_MOVE_SPEED, "archer_s30_move_speed",
                movePct, AttributeModifier.Operation.MULTIPLY_BASE);

        // 拉弓速度加成
        AttributeGuard.apply(player, RealmAttributes.drawSpeed(),
                ModifierIds.ARCHER_S30_DRAW_SPEED, "archer_s30_draw_speed",
                MedalsConfig.pct(MedalsConfig.S30_DRAW_SPEED), AttributeModifier.Operation.MULTIPLY_BASE);

        // 箭矢伤害加成
        AttributeGuard.apply(player, RealmAttributes.arrowDamage(),
                ModifierIds.ARCHER_S30_ARROW_DAMAGE, "archer_s30_arrow_damage",
                MedalsConfig.pct(MedalsConfig.S30_ARROW_DAMAGE), AttributeModifier.Operation.MULTIPLY_BASE);
    }

    /** 击杀时调用,延长持续时间。 */
    public static void onKill(ServerPlayer player, PlayerMedals data) {
        if (!isActive(data)) {
            return;
        }
        int extended = data.getState(STATE_EXTENDED);
        int maxExtend = MedalsConfig.S30_MAX_EXTEND.get() * 20;
        if (extended >= maxExtend) {
            return;
        }
        // 每次击杀延长 1 秒,但总延长不超过配置上限
        int add = Math.min(20, maxExtend - extended);
        data.setState(STATE_EXTENDED, extended + add);
        data.setState(STATE_REMAIN, data.getState(STATE_REMAIN) + add);
    }

    @Override
    public void clear(net.minecraft.world.entity.player.Player player) {
        AttributeGuard.clearByPrefix(player, Attributes.MOVEMENT_SPEED, "archer_s30_");
        AttributeGuard.clearByPrefix(player, RealmAttributes.drawSpeed(), "archer_s30_");
        AttributeGuard.clearByPrefix(player, RealmAttributes.arrowDamage(), "archer_s30_");
    }
}
