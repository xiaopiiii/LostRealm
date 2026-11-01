package com.lost_realm.skill.summoner;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import com.lost_realm.config.MedalsConfig;
import com.lost_realm.data.PlayerMedals;
import com.lost_realm.event.SummonerState;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;

/**
 * Lv.40【军团号令】(主动)
 *
 * <p>开启后 {@code S20_DURATION_SECONDS} 秒内：
 * <ul>
 *   <li>仆从伤害 +{@code S20_SERVANT_DAMAGE}%；</li>
 *   <li>仆从召唤上限 +{@code S20_SERVANT_LIMIT}；</li>
 *   <li>仆从寿命停止流逝。</li>
 * </ul>
 *
 * <h2>状态放在哪</h2>
 * 到期刻记在 {@link SummonerState}（玩家存档里），因为读取它的有三处：
 * 本技能自己、{@code SummonerCharmHandler} 的仆从刷新、以及被 Mixin
 * 从 Goety 内部回调的 {@code ServantLimit}。三处互不持有对方引用，
 * 需要一个公共落点。
 *
 * <p>加成不在本类里施加 —— 「把加成写到每个仆从身上」这件事本来就归
 * {@code SummonerCharmHandler#refreshServants} 管，本类只在
 * {@link #activate} 时把开关打开。
 */
public class SkillLegionCommand implements Skill {

    public static final String ID = "su20_legion_command";

    @Override
    public MedalType medal() {
        return MedalType.SUMMONER;
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
        return Component.translatable("skill.lost_realm." + ID);
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("skill.lost_realm." + ID + ".desc",
                MedalsConfig.S20_DURATION_SECONDS.get(),
                MedalsConfig.S20_SERVANT_DAMAGE.get(),
                MedalsConfig.S20_SERVANT_LIMIT.get(),
                MedalsConfig.S20_COOLDOWN_SECONDS.get()));
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public int cooldownTicks() {
        return MedalsConfig.S20_COOLDOWN_SECONDS.get() * 20;
    }

    /** 号令是否仍在生效（供 HUD / 其它逻辑查询）。 */
    public static boolean isActive(ServerPlayer player) {
        return SummonerState.commandActive(player);
    }

    @Override
    public boolean activate(ServerPlayer player, int level) {
        // 已在生效中就不必再开一次（也不浪费冷却）
        if (SummonerState.commandActive(player)) {
            return false;
        }
        SummonerState.startCommand(player, MedalsConfig.S20_DURATION_SECONDS.get());
        return true;
    }

    @Override
    public void clear(net.minecraft.world.entity.player.Player player) {
        SummonerState.stopCommand(player);
    }

    @Override
    public void onLevelChanged(ServerPlayer player, int oldLevel, int newLevel) {
        if (newLevel < 40) {
            SummonerState.stopCommand(player);
        }
    }

    /** 便于 HUD 显示剩余秒数。 */
    public static int remainingSeconds(ServerPlayer player) {
        long until = PlayerMedals.of(player).getTimestamp(SummonerState.KEY_COMMAND_UNTIL);
        if (until <= 0L) {
            return 0;
        }
        long remain = until - player.level().getGameTime();
        return remain <= 0L ? 0 : (int) (remain / 20L);
    }
}
