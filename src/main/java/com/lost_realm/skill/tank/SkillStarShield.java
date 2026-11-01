package com.lost_realm.skill.tank;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import com.lost_realm.config.MedalsConfig;
import com.lost_realm.data.PlayerMedals;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;

/**
 * Lv.30【星盾】
 *
 * <p>每 45 秒生成一面盾,每面最多抵挡 50 伤害,最多存 3 面。
 * 视觉上绕玩家旋转;第一人称下隐藏。
 *
 * <p>本类只负责「生成层数」与计时;实际抵挡由 {@code CombatHandler} 在受伤时
 * 逐层扣减。视觉由客户端渲染器根据同步的层数绘制(见 {@code client.ShieldRenderer}).
 */
public class SkillStarShield implements Skill {

    public static final String ID = "t15_star_shield";
    /** 当前层数 */
    public static final String STATE_STACKS = ID + "_stacks";
    /** 每层剩余可抵挡伤害(累积打包存储,用分层倍率编码) */
    public static final String STATE_TIMER = ID + "_timer";
    /** 每层剩余耐久,形如 "50|50|50" 不方便放 int,这里仅存总耐久 */
    public static final String STATE_DURABILITY = ID + "_durability";

    @Override
    public MedalType medal() {
        return MedalType.TANK;
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
        return Component.translatable("skill.lost_realm.t15_star_shield");
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("skill.lost_realm.t15_star_shield.desc",
                MedalsConfig.T15_SHIELD_INTERVAL.get(),
                MedalsConfig.T15_SHIELD_MAX_BLOCK.get(),
                MedalsConfig.T15_SHIELD_MAX_STACKS.get()));
    }

    @Override
    public void applyPassive(ServerPlayer player, int level) {
        PlayerMedals data = PlayerMedals.of(player);
        int stacks = data.getState(STATE_STACKS);
        int timer = data.getState(STATE_TIMER);

        timer++;
        int intervalTicks = MedalsConfig.T15_SHIELD_INTERVAL.get() * 20;
        int maxStacks = MedalsConfig.T15_SHIELD_MAX_STACKS.get();

        if (timer >= intervalTicks) {
            timer = 0;
            if (stacks < maxStacks) {
                // 新盾满耐久
                int durability = data.getState(STATE_DURABILITY)
                        + MedalsConfig.T15_SHIELD_MAX_BLOCK.get();
                data.setState(STATE_STACKS, stacks + 1);
                data.setState(STATE_DURABILITY, durability);

                if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.6F);
                }
            }
        }
        data.setState(STATE_TIMER, timer);
    }

    /**
     * 尝试用星盾抵挡伤害。
     *
     * @param incoming 受到的伤害
     * @return 实际被盾挡下的伤害量
     */
    public static double absorb(ServerPlayer player, double incoming) {
        PlayerMedals data = PlayerMedals.of(player);
        int stacks = data.getState(STATE_STACKS);
        if (stacks <= 0 || incoming <= 0) {
            return 0.0D;
        }

        int durability = data.getState(STATE_DURABILITY);
        int maxBlock = MedalsConfig.T15_SHIELD_MAX_BLOCK.get();
        double absorbed = Math.min(incoming, durability);

        durability -= (int) Math.ceil(absorbed);

        // 盾被击碎则减少层数
        while (durability <= 0 && stacks > 0) {
            stacks--;
            durability += stacks > 0 ? maxBlock : 0;
        }
        if (stacks <= 0) {
            durability = 0;
        }

        data.setState(STATE_STACKS, stacks);
        data.setState(STATE_DURABILITY, Math.max(0, durability));

        if (absorbed > 0 && player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.7F, 1.2F);
        }
        return absorbed;
    }

    /** 当前层数(供渲染)。 */
    public static int stacks(PlayerMedals data) {
        return data.getState(STATE_STACKS);
    }

    @Override
    public void clear(Player player) {
        PlayerMedals data = PlayerMedals.of(player);
        data.setState(STATE_STACKS, 0);
        data.setState(STATE_DURABILITY, 0);
        data.setState(STATE_TIMER, 0);
    }
}
