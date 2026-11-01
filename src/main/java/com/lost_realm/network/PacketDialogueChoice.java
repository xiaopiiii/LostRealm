package com.lost_realm.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkEvent;

import com.lost_realm.boss.LordDialogueHandler;
import com.lost_realm.boss.LordState;
import com.lost_realm.boss.entity.WardenBoss;

/**
 * 客户端 → 服务端:提交「遗落之主」对话界面的选择。
 *
 * <h2>选择语义</h2>
 * <ul>
 *   <li>{@code 0} = 离开 —— 关闭对话,遗落之主留在零阶段,玩家可以再来</li>
 *   <li>{@code 1} = 决斗 —— 触发幽灵态演出(极速冲脸 + 一击 + 波次)</li>
 * </ul>
 *
 * <h2>服务端必须校验(防作弊)</h2>
 * 这是客户端发来的选择,绝不可盲信:
 * <ol>
 *   <li>玩家与遗落之主的距离 ≤ {@link LordDialogueHandler#MAX_TALK_DISTANCE}——
 *       防止隔着半张地图"远程决斗"</li>
 *   <li>遗落之主存在且处于 {@link LordState#DORMANT} ——
 *       防止战斗中途重复触发决斗</li>
 * </ol>
 * 任一不满足则静默忽略(不提示 —— 正常玩家不会触发)。
 */
public class PacketDialogueChoice {

    /** 选择:离开。 */
    public static final int CHOICE_LEAVE = 0;

    /** 选择:决斗。 */
    public static final int CHOICE_DUEL = 1;

    private final int choice;

    public PacketDialogueChoice(int choice) {
        this.choice = choice;
    }

    public PacketDialogueChoice(FriendlyByteBuf buf) {
        this.choice = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(choice);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (!(player.level() instanceof ServerLevel sl)) {
                return;
            }

            // ---- ① 找到附近的遗落之主 ----
            WardenBoss boss = sl.getEntitiesOfClass(WardenBoss.class,
                    new AABB(player.blockPosition())
                            .inflate(LordDialogueHandler.MAX_TALK_DISTANCE),
                    e -> !e.isClone()).stream().findFirst().orElse(null);
            if (boss == null) {
                return;
            }

            // ---- ② 必须是零阶段(防重复触发) ----
            if (boss.getLordState() != LordState.DORMANT) {
                return;
            }

            // ---- ③ 距离二次校验(玩家可能在包发出后瞬移) ----
            if (player.distanceTo(boss) > LordDialogueHandler.MAX_TALK_DISTANCE) {
                return;
            }

            LordDialogueHandler.onChoice(player, boss, choice);
        });
        context.setPacketHandled(true);
    }

    /** 便捷:给玩家的提示(供界面侧复用文案)。 */
    public static Component leaveMessage() {
        return Component.translatable("message.lost_realm.lord.duel.leave");
    }

    public int choice() {
        return choice;
    }
}
