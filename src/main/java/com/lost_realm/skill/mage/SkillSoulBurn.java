package com.lost_realm.skill.mage;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import com.lost_realm.compat.goety.GoetyBridge;
import com.lost_realm.config.MedalsConfig;
import com.lost_realm.data.PlayerMedals;
import com.lost_realm.item.MageAttributes;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;
import com.lost_realm.util.AttributeGuard;
import com.lost_realm.util.ModifierIds;

/**
 * Lv.50【灵魂燃烧】(主动 · 可开关)
 *
 * <p>按快捷键开启。开启后:
 * <ul>
 *   <li>每秒消耗 <b>50 点</b>灵魂能量</li>
 *   <li>每 <b>2 秒</b>叠加 1 层「燃烧」,最多 <b>10 层</b></li>
 *   <li>每层提供:法术强度 <b>+10%</b>、施法速度 <b>+5%</b>、法术暴击率 <b>+2%</b></li>
 * </ul>
 * 满层时:法术强度 +100%、施法速度 +50%、法术暴击率 +20%。
 *
 * <p>开启后<b>不进入冷却</b>,可无限持续,直到灵魂耗尽或玩家手动关闭。
 * 关闭瞬间(无论自然结束还是手动)进入 <b>30 秒</b>冷却。
 *
 * <h2>开关型技能</h2>
 * 本技能是四枚勋章中唯一的「可开关」技能。{@link #isToggleable()} 返回 {@code true}
 * 后,{@code PacketActivateSkill} 会在「已开启」时调用 {@link #deactivate} 而非重新激活。
 */
public class SkillSoulBurn implements Skill {

    public static final String ID = "m25_soul_burn";
    /** 是否处于开启状态(1 = 开) */
    public static final String STATE_ON = ID + "_on";
    /** 当前燃烧层数 */
    public static final String STATE_STACKS = ID + "_stacks";
    /** 距离下一次扣魂的剩余 tick */
    public static final String STATE_DRAIN_TICK = ID + "_drain";
    /** 距离下一次叠层的剩余 tick */
    public static final String STATE_STACK_TICK = ID + "_stack_tick";

    @Override
    public MedalType medal() {
        return MedalType.MAGE;
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
        return Component.translatable("skill.lost_realm.m25_soul_burn");
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("skill.lost_realm.m25_soul_burn.desc",
                MedalsConfig.M25_SOUL_PER_SECOND.get(),
                MedalsConfig.M25_STACK_INTERVAL_SECONDS.get(),
                MedalsConfig.M25_MAX_STACKS.get(),
                MedalsConfig.M25_POTENCY_PER_STACK.get(),
                MedalsConfig.M25_CASTING_SPEED_PER_STACK.get(),
                MedalsConfig.M25_CRIT_CHANCE_PER_STACK.get(),
                MedalsConfig.M25_COOLDOWN_SECONDS.get()));
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public boolean isToggleable() {
        return true;
    }

    /**
     * 开关型技能不进「点一次冷却一次」的常规逻辑:
     * 冷却由「关闭」动作负责设置,因此这里返回 0。
     */
    @Override
    public int cooldownTicks() {
        return 0;
    }

    public static boolean isBurning(PlayerMedals data) {
        return data.getState(STATE_ON) > 0;
    }

    @Override
    public boolean activate(ServerPlayer player, int level) {
        GoetyBridge goety = GoetyBridge.get();
        if (goety == null) {
            return false;
        }
        PlayerMedals data = PlayerMedals.of(player);
        if (isBurning(data)) {
            return false;
        }
        if (!goety.hasSoulContainer(player) || goety.getSouls(player) < MedalsConfig.M25_SOUL_PER_SECOND.get()) {
            player.displayClientMessage(
                    Component.translatable("skill.lost_realm.m25_soul_burn.no_soul"), true);
            return false;
        }
        data.setState(STATE_ON, 1);
        data.setState(STATE_STACKS, 0);
        data.setState(STATE_DRAIN_TICK, 20);
        data.setState(STATE_STACK_TICK, MedalsConfig.M25_STACK_INTERVAL_SECONDS.get() * 20);
        player.displayClientMessage(Component.translatable("skill.lost_realm.m25_soul_burn.active"), true);
        return true;
    }

    /**
     * 关闭技能(手动或灵魂耗尽),并设置冷却。
     *
     * @param player   玩家
     * @param noSoul   是否因灵魂不足而关闭(用于提示文案)
     */
    public static void stop(ServerPlayer player, boolean noSoul) {
        PlayerMedals data = PlayerMedals.of(player);
        if (!isBurning(data)) {
            return;
        }
        data.setState(STATE_ON, 0);
        data.setState(STATE_STACKS, 0);
        data.setState(STATE_DRAIN_TICK, 0);
        data.setState(STATE_STACK_TICK, 0);
        // 进入冷却(由 PacketActivateSkill 检查 isOnCooldown);时间胶囊的固定减免在此生效
        data.setCooldown(ID, com.lost_realm.item.TimeCapsuleItem.applyReduction(
                data, MedalsConfig.M25_COOLDOWN_SECONDS.get() * 20));
        clearModifiers(player);

        player.displayClientMessage(Component.translatable(noSoul
                ? "skill.lost_realm.m25_soul_burn.no_soul"
                : "skill.lost_realm.m25_soul_burn.inactive"), true);
    }

    @Override
    public boolean deactivate(ServerPlayer player, int level) {
        stop(player, false);
        return true;
    }

    @Override
    public void applyPassive(ServerPlayer player, int level) {
        GoetyBridge goety = GoetyBridge.get();
        if (goety == null) {
            return;
        }
        PlayerMedals data = PlayerMedals.of(player);
        if (!isBurning(data)) {
            return;
        }

        // ---- 扣魂(每秒 50)----
        int drain = data.getState(STATE_DRAIN_TICK) - 1;
        if (drain <= 0) {
            int cost = MedalsConfig.M25_SOUL_PER_SECOND.get();
            if (goety.getSouls(player) < cost) {
                // 灵魂不足 → 自动关闭
                stop(player, true);
                return;
            }
            goety.decreaseSouls(player, cost);
            drain = 20;
        }
        data.setState(STATE_DRAIN_TICK, drain);

        // ---- 叠层(每 2 秒)----
        int stackTick = data.getState(STATE_STACK_TICK) - 1;
        if (stackTick <= 0) {
            int stacks = Math.min(data.getState(STATE_STACKS) + 1, MedalsConfig.M25_MAX_STACKS.get());
            data.setState(STATE_STACKS, stacks);
            stackTick = MedalsConfig.M25_STACK_INTERVAL_SECONDS.get() * 20;
        }
        data.setState(STATE_STACK_TICK, stackTick);

        applyModifiers(player, data);
    }

    /** 按当前层数施加全部修饰符。 */
    private static void applyModifiers(ServerPlayer player, PlayerMedals data) {
        GoetyBridge goety = GoetyBridge.get();
        if (goety == null) {
            return;
        }
        int stacks = data.getState(STATE_STACKS);

        double castingSpeed = MedalsConfig.pct(MedalsConfig.M25_CASTING_SPEED_PER_STACK) * stacks;
        goety.applyCastingSpeed(player, ModifierIds.MAGE_M25_CASTING_SPEED,
                "mage_m25_casting_speed", castingSpeed);

        // 法术强度按「基础强度 × 每层比例 × 层数」计算,避免自我叠加
        double potency = basePotency(player) * MedalsConfig.pct(MedalsConfig.M25_POTENCY_PER_STACK) * stacks;
        goety.applySpellPotency(player, ModifierIds.MAGE_M25_SPELL_POTENCY,
                "mage_m25_spell_potency", potency);

        double crit = MedalsConfig.pct(MedalsConfig.M25_CRIT_CHANCE_PER_STACK) * stacks;
        AttributeGuard.apply(player, MageAttributes.SPELL_CRIT_CHANCE.get(),
                ModifierIds.MAGE_M25_SPELL_CRIT_CHANCE, "mage_m25_spell_crit_chance",
                crit, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
    }

    /** 取「除本技能外」的法术强度基准值。 */
    private static double basePotency(ServerPlayer player) {
        GoetyBridge goety = GoetyBridge.get();
        if (goety == null) {
            return 0.0D;
        }
        double total = goety.spellPotencyTotal(player);
        double ownAmount = goety.spellPotencyModifierAmount(player, ModifierIds.MAGE_M25_SPELL_POTENCY);
        return Math.max(0.0D, total - ownAmount);
    }

    /** 归零本技能的全部修饰符。 */
    private static void clearModifiers(ServerPlayer player) {
        GoetyBridge goety = GoetyBridge.get();
        if (goety != null) {
            goety.applyCastingSpeed(player, ModifierIds.MAGE_M25_CASTING_SPEED, "mage_m25_casting_speed", 0.0D);
            goety.applySpellPotency(player, ModifierIds.MAGE_M25_SPELL_POTENCY, "mage_m25_spell_potency", 0.0D);
        }
        AttributeGuard.remove(player, MageAttributes.SPELL_CRIT_CHANCE.get(),
                ModifierIds.MAGE_M25_SPELL_CRIT_CHANCE);
    }

    @Override
    public void clear(net.minecraft.world.entity.player.Player player) {
        if (player instanceof ServerPlayer sp) {
            clearModifiers(sp);
            PlayerMedals data = PlayerMedals.of(sp);
            data.setState(STATE_ON, 0);
            data.setState(STATE_STACKS, 0);
        }
    }

    /** 当前层数(供 HUD 显示)。 */
    public static int stacks(PlayerMedals data) {
        return data.getState(STATE_STACKS);
    }
}
