package com.lost_realm.skill.mage;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import com.lost_realm.compat.goety.GoetyBridge;
import com.lost_realm.config.MedalsConfig;
import com.lost_realm.data.PlayerMedals;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;
import com.lost_realm.util.ModifierIds;

/**
 * Lv.60【弹幕风暴】(被动)
 *
 * <ul>
 *   <li>法术命中敌人时,<b>20%</b> 概率立刻刷新该法术的冷却</li>
 *   <li><b>保底递增</b>:未成功触发时,下一次触发概率 <b>+15%</b>
 *       (20% → 35% → 50% → …);一旦成功触发立刻重置回 20%</li>
 *   <li>触发时,你<b>下一次施法</b>将变为「三连发」:
 *       副法术强度为主法术的 <b>35%</b>,向左右各偏移 <b>5°</b> 发射</li>
 *   <li>常驻属性:法术作用半径 <b>+25%</b></li>
 * </ul>
 *
 * <h2>三连发的实现方式</h2>
 * 「刷新冷却」直接调用 Goety 的 {@code FocusCooldown#removeCooldown},是公开 API。
 *
 * <p>「三连发」通过监听 {@code EntityJoinLevelEvent} 实现:当检测到
 * Goety 的法术投射物生成、且玩家处于「三连发待发」状态时,
 * 克隆两发副本并向左右各旋转配置角度。详见
 * {@code event/BarrageHandler}。本类只负责状态维护与概率结算。
 */
public class SkillBarrageStorm implements Skill {

    public static final String ID = "m30_barrage_storm";
    /** 保底计数(连续未触发次数) */
    public static final String STATE_PITY = ID + "_pity";
    /** 三连发待发标记(1 = 待发) */
    public static final String STATE_READY = ID + "_ready";

    @Override
    public MedalType medal() {
        return MedalType.MAGE;
    }

    @Override
    public int unlockLevel() {
        return 60;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component displayName() {
        return Component.translatable("skill.lost_realm.m30_barrage_storm");
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("skill.lost_realm.m30_barrage_storm.desc",
                MedalsConfig.M30_REFRESH_CHANCE.get(),
                MedalsConfig.M30_PITY_INCREMENT.get(),
                MedalsConfig.M30_BARRAGE_POTENCY.get(),
                MedalsConfig.M30_BARRAGE_ANGLE.get(),
                MedalsConfig.M30_SPELL_RADIUS.get()));
    }

    @Override
    public void applyPassive(ServerPlayer player, int level) {
        if (level < 60) {
            return;
        }
        GoetyBridge goety = GoetyBridge.get();
        if (goety == null) {
            return;
        }
        // 常驻:法术作用半径 +25%
        goety.applySpellRadius(player, ModifierIds.MAGE_M30_SPELL_RADIUS,
                "mage_m30_spell_radius", MedalsConfig.pct(MedalsConfig.M30_SPELL_RADIUS));
    }

    /**
     * 结算一次「法术命中」的冷却刷新判定。
     *
     * <p>概率 = 基础概率 + 保底次数 × 递增幅度。
     * 成功则刷新冷却、置「三连发待发」、保底归零;失败则保底 +1。
     *
     * @return 是否触发
     */
    public static boolean onSpellHit(ServerPlayer player) {
        GoetyBridge goety = GoetyBridge.get();
        if (goety == null) {
            return false;
        }
        if (com.lost_realm.data.MedalSource.level(player, MedalType.MAGE) < 60) {
            return false;
        }

        PlayerMedals data = PlayerMedals.of(player);
        int pity = data.getState(STATE_PITY);
        double chance = MedalsConfig.pct(MedalsConfig.M30_REFRESH_CHANCE)
                + MedalsConfig.pct(MedalsConfig.M30_PITY_INCREMENT) * pity;
        chance = Math.min(1.0D, chance);

        if (player.getRandom().nextDouble() >= chance) {
            // 未触发:保底 +1
            data.setState(STATE_PITY, pity + 1);
            return false;
        }

        // 触发:刷新冷却 + 置三连发待发 + 保底归零
        goety.refreshWandCooldown(player);
        data.setState(STATE_PITY, 0);
        data.setState(STATE_READY, 1);

        if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                    player.getX(), player.getY() + 1.2D, player.getZ(),
                    20, 0.5D, 0.5D, 0.5D, 0.05D);
        }
        return true;
    }

    /** 当前保底计数(供 HUD 显示)。 */
    public static int pity(PlayerMedals data) {
        return data.getState(STATE_PITY);
    }

    /** 是否处于「三连发待发」状态。 */
    public static boolean isBarrageReady(PlayerMedals data) {
        return data.getState(STATE_READY) > 0;
    }

    /** 消耗掉「三连发待发」标记(投射物已复制完成后调用)。 */
    public static void consumeBarrage(PlayerMedals data) {
        data.setState(STATE_READY, 0);
    }

    @Override
    public void clear(net.minecraft.world.entity.player.Player player) {
        GoetyBridge goety = GoetyBridge.get();
        if (goety != null) {
            goety.applySpellRadius(player, ModifierIds.MAGE_M30_SPELL_RADIUS,
                    "mage_m30_spell_radius", 0.0D);
        }
        if (player instanceof ServerPlayer sp) {
            PlayerMedals data = PlayerMedals.of(sp);
            data.setState(STATE_READY, 0);
        }
    }
}
