package com.lost_realm.skill.archer;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BowItem;

import com.lost_realm.config.MedalsConfig;
import com.lost_realm.data.PlayerMedals;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;

/**
 * Lv.10【闪避】(被动)
 *
 * <p>拉弓时被攻击,有 <b>60%</b> 概率完全免疫此次伤害,
 * 并向同 Y 轴坐标随机 <b>6 格</b>范围内「至少有两格高空间」的位置传送。冷却 <b>20 秒</b>。
 *
 * <p>实际结算位于 {@code CombatHandler#handleDodge}:判定拉弓状态 → 随机概率 →
 * 寻找合法落点 → 取消伤害并传送 → 进入冷却。
 * 本类负责冷却计时与「本次拉弓已触发」标记。
 */
public class SkillDodge implements Skill {

    public static final String ID = "s5_dodge";
    /** 冷却剩余 tick */
    public static final String CD_KEY = ID + "_cooldown";
    /** 本次拉弓是否已触发过(避免同一次拉弓内反复触发) */
    public static final String USED_KEY = ID + "_used";

    @Override
    public MedalType medal() {
        return MedalType.ARCHER;
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
        return Component.translatable("skill.lost_realm.s5_dodge");
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.translatable("skill.lost_realm.s5_dodge.desc",
                        MedalsConfig.S5_DODGE_IMMUNE_CHANCE.get(),
                        MedalsConfig.S5_TELEPORT_RANGE.get(),
                        MedalsConfig.S5_COOLDOWN_SECONDS.get()));
    }

    @Override
    public void applyPassive(ServerPlayer player, int level) {
        PlayerMedals data = PlayerMedals.of(player);
        // 冷却递减
        int cd = data.getState(CD_KEY);
        if (cd > 0) {
            data.setState(CD_KEY, cd - 1);
        }
        // 未在拉弓时重置「本次已触发」标记
        boolean drawing = player.isUsingItem() && player.getUseItem().getItem() instanceof BowItem;
        if (!drawing) {
            data.setState(USED_KEY, 0);
        }
    }

    /** 是否处于冷却中。 */
    public static boolean onCooldown(PlayerMedals data) {
        return data.getState(CD_KEY) > 0;
    }

    /** 进入冷却（时间胶囊的固定减免在此生效）。 */
    public static void startCooldown(PlayerMedals data) {
        data.setState(CD_KEY, com.lost_realm.item.TimeCapsuleItem.applyReduction(
                data, MedalsConfig.S5_COOLDOWN_SECONDS.get() * 20));
    }

    /** 本次拉弓是否已触发过。 */
    public static boolean usedThisDraw(PlayerMedals data) {
        return data.getState(USED_KEY) > 0;
    }

    public static void markUsed(PlayerMedals data) {
        data.setState(USED_KEY, 1);
    }
}
