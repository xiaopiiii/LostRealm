package com.lost_realm.skill.summoner;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import com.lost_realm.compat.goety.GoetyBridge;
import com.lost_realm.config.MedalsConfig;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;
import com.lost_realm.util.ModifierIds;

/**
 * Lv.60【亡者大军】(被动)
 *
 * <p>两件事：
 * <ul>
 *   <li><b>仆从召唤上限 +{@code S30_SERVANT_LIMIT}</b> —— 由
 *       {@code ServantLimit} 在 Goety 的召唤判定里回答，本类不参与；</li>
 *   <li><b>召唤法术冷却 −{@code S30_COOLDOWN_DISCOUNT}%</b> —— 作用在 Goety
 *       的 {@code cooldown_discount} 属性上，需要每个 tick 幂等刷新，
 *       这正是本类 {@link #applyPassive} 的活。</li>
 * </ul>
 *
 * <p>用 Goety 自己的属性而不是去拦截冷却计时：这样「所有走该属性的召唤法术」
 * 自动一起受益，也不怕 Goety 内部改冷却实现。
 */
public class SkillDeadLegion implements Skill {

    public static final String ID = "su30_dead_legion";

    @Override
    public MedalType medal() {
        return MedalType.SUMMONER;
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
        return Component.translatable("skill.lost_realm." + ID);
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("skill.lost_realm." + ID + ".desc",
                MedalsConfig.S30_SERVANT_LIMIT.get(),
                MedalsConfig.S30_COOLDOWN_DISCOUNT.get(),
                MedalsConfig.S30_SERVANT_HEALTH.get()));
    }

    @Override
    public void applyPassive(ServerPlayer player, int level) {
        GoetyBridge goety = GoetyBridge.get();
        if (goety == null) {
            return;
        }
        goety.applyCooldownDiscount(player, ModifierIds.SUMMONER_S30_COOLDOWN,
                "summoner_s30_cooldown",
                MedalsConfig.pct(MedalsConfig.S30_COOLDOWN_DISCOUNT));
    }

    @Override
    public void clear(net.minecraft.world.entity.player.Player player) {
        GoetyBridge goety = GoetyBridge.get();
        if (goety == null) {
            return;
        }
        goety.applyCooldownDiscount(player, ModifierIds.SUMMONER_S30_COOLDOWN,
                "summoner_s30_cooldown", 0.0D);
    }
}
