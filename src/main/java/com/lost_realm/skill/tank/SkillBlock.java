package com.lost_realm.skill.tank;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import com.lost_realm.config.MedalsConfig;
import com.lost_realm.data.PlayerMedals;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;

/**
 * Lv.50【格挡】
 *
 * <p>受到攻击时有 30% 概率获得 20 点格挡值,每 2 秒最多一次。
 * 每 1 点格挡值抵消 1.5 点伤害,但单次抵消不超过该次攻击伤害的 20%。
 * 若 10 秒内未再获得格挡值,则格挡值清零。
 */
public class SkillBlock implements Skill {

    public static final String ID = "t25_block";
    public static final String STATE_VALUE = ID + "_value";
    public static final String LAST_GAIN = ID + "_last_gain";

    @Override
    public MedalType medal() {
        return MedalType.TANK;
    }

    @Override
    public int unlockLevel() {
        return 50;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component displayName() {
        return Component.translatable("skill.lost_realm.t25_block");
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("skill.lost_realm.t25_block.desc",
                MedalsConfig.T25_BLOCK_CHANCE.get(),
                MedalsConfig.T25_BLOCK_VALUE.get(),
                MedalsConfig.T25_BLOCK_PER_VALUE.get(),
                MedalsConfig.T25_BLOCK_CAP.get(),
                MedalsConfig.T25_RESET_SECONDS.get()));
    }

    @Override
    public void applyPassive(ServerPlayer player, int level) {
        PlayerMedals data = PlayerMedals.of(player);
        long now = player.level().getGameTime();
        long last = data.getTimestamp(LAST_GAIN);
        long resetTicks = MedalsConfig.T25_RESET_SECONDS.get() * 20L;

        // 10 秒未获得则清零
        if (data.getState(STATE_VALUE) > 0 && now - last > resetTicks) {
            data.setState(STATE_VALUE, 0);
        }
    }

    /**
     * 受伤时尝试获得格挡值。
     *
     * @return 是否成功获得
     */
    public static boolean tryGainBlock(ServerPlayer player) {
        PlayerMedals data = PlayerMedals.of(player);
        long now = player.level().getGameTime();
        long last = data.getTimestamp(LAST_GAIN);
        long interval = MedalsConfig.T25_INTERVAL_SECONDS.get() * 20L;

        if (now - last < interval) {
            return false;
        }
        if (player.getRandom().nextDouble() >= MedalsConfig.pct(MedalsConfig.T25_BLOCK_CHANCE)) {
            return false;
        }

        data.setTimestamp(LAST_GAIN, now);
        data.addState(STATE_VALUE, MedalsConfig.T25_BLOCK_VALUE.get());
        return true;
    }

    /**
     * 用当前格挡值抵消一次伤害。
     *
     * @param incoming 本次攻击的原始伤害
     * @return 实际被抵消的伤害
     */
    public static double absorb(ServerPlayer player, double incoming) {
        PlayerMedals data = PlayerMedals.of(player);
        int value = data.getState(STATE_VALUE);
        if (value <= 0 || incoming <= 0) {
            return 0.0D;
        }

        // 每点格挡值可抵消 1.5 伤害
        double capacity = value * MedalsConfig.T25_BLOCK_PER_VALUE.get();
        // 单次抵消不超过本次攻击伤害的 20%
        double cap = incoming * MedalsConfig.pct(MedalsConfig.T25_BLOCK_CAP);
        double absorbed = Math.min(Math.min(incoming, capacity), cap);

        // 按消耗的点数扣减
        int consumed = (int) Math.ceil(absorbed / MedalsConfig.T25_BLOCK_PER_VALUE.get());
        data.setState(STATE_VALUE, Math.max(0, value - consumed));
        return absorbed;
    }

    public static int value(PlayerMedals data) {
        return data.getState(STATE_VALUE);
    }

    @Override
    public void clear(Player player) {
        PlayerMedals data = PlayerMedals.of(player);
        data.setState(STATE_VALUE, 0);
    }
}
