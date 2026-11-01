package com.lost_realm.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import com.lost_realm.data.PlayerMedals;
import com.lost_realm.item.TimeCapsuleItem;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;
import com.lost_realm.skill.SkillRegistry;

/**
 * 客户端 -> 服务端:请求激活某个主动技能。
 *
 * <p>服务端会校验:技能存在、属于已解锁等级、且当前不在冷却中。
 *
 * <p>若技能为「开关型」({@link Skill#isToggleable()}),则本包在已开启时
 * 路由到 {@link Skill#deactivate},否则路由到 {@link Skill#activate}。
 */
public class PacketActivateSkill {

    /** {@code PlayerMedals} 中记录开关型技能状态的键前缀。 */
    public static final String TOGGLE_STATE_PREFIX = "toggle_";

    private final String medalId;
    private final String skillId;

    public PacketActivateSkill(String medalId, String skillId) {
        this.medalId = medalId;
        this.skillId = skillId;
    }

    public PacketActivateSkill(FriendlyByteBuf buf) {
        this.medalId = buf.readUtf();
        this.skillId = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(medalId);
        buf.writeUtf(skillId);
    }

    /** 开关型技能在 {@code PlayerMedals} 中存放状态的键。 */
    public static String toggleKey(Skill skill) {
        return TOGGLE_STATE_PREFIX + skill.id();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            MedalType type = MedalType.byId(medalId);
            Skill skill = SkillRegistry.byId(skillId);
            if (type == null || skill == null || !skill.isActive()) {
                return;
            }

            PlayerMedals data = PlayerMedals.of(player);
            int level = com.lost_realm.data.MedalSource.level(player, type);
            if (level < skill.unlockLevel()) {
                player.displayClientMessage(
                        Component.translatable("message.lost_realm.skill_locked", skill.displayName()), true);
                return;
            }

            // ---------- 开关型技能:已开启 -> 关闭 ----------
            if (skill.isToggleable()) {
                String key = toggleKey(skill);
                if (data.getState(key) > 0) {
                    if (skill.deactivate(player, level)) {
                        data.setState(key, 0);
                        // 冷却由技能自身在关闭时结算(可能在 stop 内已设置),这里不覆盖
                        if (skill.cooldownTicks() > 0 && data.getCooldown(skill.id()) <= 0) {
                            data.setCooldown(skill.id(),
                                    TimeCapsuleItem.applyReduction(data, skill.cooldownTicks()));
                        }
                        player.displayClientMessage(
                                Component.translatable("message.lost_realm.skill_deactivated", skill.displayName()), true);
                    }
                    return;
                }
                if (data.isOnCooldown(skill.id())) {
                    player.displayClientMessage(
                            Component.translatable("message.lost_realm.skill_cooldown",
                                    Math.max(1, data.getCooldown(skill.id()) / 20)), true);
                    return;
                }
                if (skill.activate(player, level)) {
                    data.setState(key, 1);
                    player.displayClientMessage(
                            Component.translatable("message.lost_realm.skill_activated", skill.displayName()), true);
                }
                return;
            }

            // ---------- 普通主动技能 ----------
            if (data.isOnCooldown(skill.id())) {
                player.displayClientMessage(
                        Component.translatable("message.lost_realm.skill_cooldown",
                                Math.max(1, data.getCooldown(skill.id()) / 20)), true);
                return;
            }

            if (skill.activate(player, level)) {
                // 时间胶囊的固定减免在此生效
                data.setCooldown(skill.id(), TimeCapsuleItem.applyReduction(data, skill.cooldownTicks()));
                player.displayClientMessage(
                        Component.translatable("message.lost_realm.skill_activated", skill.displayName()), true);
            }
        });
        context.setPacketHandled(true);
    }
}
