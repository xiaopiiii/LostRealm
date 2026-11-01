package com.lost_realm.skill.tank;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import com.lost_realm.config.MedalsConfig;
import com.lost_realm.data.PlayerMedals;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;

/**
 * Lv.20【攻势汲取】
 *
 * <p>攻击时有 35% 概率获得 4 点伤害吸收,每 3 秒最多触发一次。
 *
 * <p>获取逻辑位于 {@code CombatHandler} 的近战命中处:
 * 检查时间戳间隔 → 判定概率 → {@code player.setAbsorptionAmount(...)}。
 */
public class SkillOffensiveDrain implements Skill {

    public static final String ID = "t10_offensive_drain";
    /** 上次触发时间戳(tick) */
    public static final String LAST_TRIGGER = ID + "_last";

    @Override
    public MedalType medal() {
        return MedalType.TANK;
    }

    @Override
    public int unlockLevel() {
        return 20;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component displayName() {
        return Component.translatable("skill.lost_realm.t10_offensive_drain");
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("skill.lost_realm.t10_offensive_drain.desc",
                MedalsConfig.T10_ABSORB_CHANCE.get(),
                MedalsConfig.T10_ABSORB_AMOUNT.get(),
                MedalsConfig.T10_INTERVAL_SECONDS.get()));
    }

    @Override
    public void applyPassive(ServerPlayer player, int level) {
        // 命中时结算
    }

    /**
     * 尝试在攻击时触发吸取。
     *
     * @return 是否成功触发
     */
    public static boolean tryTrigger(ServerPlayer player) {
        PlayerMedals data = PlayerMedals.of(player);
        long now = player.level().getGameTime();
        long last = data.getTimestamp(LAST_TRIGGER);
        long interval = MedalsConfig.T10_INTERVAL_SECONDS.get() * 20L;

        if (now - last < interval) {
            return false;
        }
        if (player.getRandom().nextDouble() >= MedalsConfig.pct(MedalsConfig.T10_ABSORB_CHANCE)) {
            return false;
        }

        data.setTimestamp(LAST_TRIGGER, now);
        float current = player.getAbsorptionAmount();
        player.setAbsorptionAmount(current + MedalsConfig.T10_ABSORB_AMOUNT.get());
        return true;
    }
}
