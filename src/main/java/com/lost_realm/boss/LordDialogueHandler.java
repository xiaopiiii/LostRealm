package com.lost_realm.boss;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.lost_realm.boss.entity.WardenBoss;
import com.lost_realm.data.MedalNbt;
import com.lost_realm.data.MedalSource;
import com.lost_realm.network.MedalsNetwork;
import com.lost_realm.network.PacketDialogueChoice;
import com.lost_realm.network.PacketOpenDialogue;

/**
 * 遗落之主的<b>对话与决斗流程</b>(服务端权威)。
 *
 * <h2>流程</h2>
 * <pre>
 * 玩家右键遗落之主(仅 DORMANT 状态可交互)
 *   ├─ 首次  → 发送 4 段长台词 + 末页选项「离开 / 决斗」
 *   └─ 非首次 → 直接发送 1 段简短台词 + 选项「离开 / 决斗」
 * 玩家点「离开」 → 关闭界面,留在 DORMANT,可以再来
 * 玩家点「决斗」 → {@link WardenBoss#beginDuel()} 进入幽灵态演出
 * </pre>
 *
 * <h2>「是否看过开场白」的持久化</h2>
 * 用现有 {@link PlayerMedals} 的 {@code getState / setState} 存一个
 * {@code lord_intro_seen} 标记 —— 零新增基础设施。
 *
 * <p><b>打开即标记</b>(而非关闭时):玩家若中途按 Esc 退出,
 * 下次进来就只看到简短台词 + 选项 —— 符合「台词只会出现一次」的语义。
 *
 * <h2>为什么「离开」不算失败</h2>
 * 用户要求:「对话只会出现一次,若被他击败重新对话只会出现离开或决斗的对话」。
 * 因此「离开」只是一种无损的推迟,玩家可以反复来、反复看简版台词。
 */
public final class LordDialogueHandler {

    /** 允许对话的最大距离(格)。 */
    public static final double MAX_TALK_DISTANCE = 8.0D;

    /** 开场白台词的语言键(按顺序翻页)。 */
    private static final List<String> INTRO_LINES = List.of(
            "dialogue.lost_realm.lord.line.1",
            "dialogue.lost_realm.lord.line.2",
            "dialogue.lost_realm.lord.line.3",
            "dialogue.lost_realm.lord.line.4");

    /** 重复对话时的简短台词(只一页)。 */
    private static final List<String> BRIEF_LINES = List.of(
            "dialogue.lost_realm.lord.line.brief");

    /**
     * 玩家右键遗落之主 —— 打开对话。
     *
     * <p>由 {@code WardenBoss#mobInteract} 调用。
     */
    public static void openFor(ServerPlayer player, WardenBoss boss) {
        if (!(player.level() instanceof ServerLevel)) {
            return;
        }
        if (boss.getLordState() != LordState.DORMANT) {
            return;
        }
        if (player.distanceTo(boss) > MAX_TALK_DISTANCE) {
            return;
        }

        // 通关标记随勋章走:读玩家当前佩戴的任意一枚勋章的 NBT
        MedalNbt medal = MedalNbt.of(MedalSource.anyWorn(player));
        boolean firstTime = !medal.hasSeenLordIntro();

        List<String> lines = firstTime ? INTRO_LINES : BRIEF_LINES;
        if (firstTime) {
            // ★ 打开即标记 —— 中途退出也不会导致下次重复播放长台词
            medal.markLordIntroSeen();
            // 物品 NBT 变更后立即回写,避免未保存就退游戏
            com.lost_realm.item.MedalSourceSync.flush(player);
        }

        // 末页始终带选项 —— 用户要求「重新对话只会出现离开或决斗」
        MedalsNetwork.sendToPlayer(player, new PacketOpenDialogue(lines, true));

        // 首次对话时给一点仪式感
        if (firstTime) {
            boss.playSound(net.minecraft.sounds.SoundEvents.WARDEN_AMBIENT, 2.5F, 0.6F);
        }
    }

    /**
     * 处理玩家的选择。
     *
     * <p>由 {@link PacketDialogueChoice} 在校验通过后调用。
     *
     * @param choice {@link PacketDialogueChoice#CHOICE_LEAVE} 或
     *               {@link PacketDialogueChoice#CHOICE_DUEL}
     */
    public static void onChoice(ServerPlayer player, WardenBoss boss, int choice) {
        if (choice == PacketDialogueChoice.CHOICE_DUEL) {
            startDuel(player, boss);
            return;
        }
        // 离开:只在客户端提示一句,什么都不改变
        player.displayClientMessage(
                Component.translatable("message.lost_realm.lord.duel.leave"), true);
    }

    /** 开始决斗:标记 + 进入幽灵态。 */
    private static void startDuel(ServerPlayer player, WardenBoss boss) {
        MedalNbt.of(MedalSource.anyWorn(player)).markLordDueled();
        com.lost_realm.item.MedalSourceSync.flush(player);

        // 确保玩家已加入参战者(血条 / 播报都基于它)
        WardenFightSession session = boss.session();
        if (session == null && player.level() instanceof ServerLevel sl) {
            session = WardenFightManager.start(sl, boss.getUUID(), boss.getName());
            boss.bindSession(session);
        }
        if (session != null) {
            session.addParticipant(player);
        }

        // 进入幽灵态演出
        boss.beginDuel();
    }

    /** 供客户端界面读取台词数(测试 / 调试用)。 */
    public static int introLineCount() {
        return INTRO_LINES.size();
    }

    private LordDialogueHandler() {}
}
