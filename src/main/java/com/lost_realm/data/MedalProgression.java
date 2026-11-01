package com.lost_realm.data;

import net.minecraft.server.level.ServerPlayer;

import com.lost_realm.skill.MedalType;
import com.lost_realm.util.ExpTable;

/**
 * <b>勋章进度与枷锁</b> —— 三章节的等级门。
 *
 * <h2>为什么要枷锁</h2>
 * 整合包分三章，每章该有一个"打不过就得先变强"的终点。如果熟练度可以一路
 * 刷到 60 级，玩家会在第一章就把整条成长曲线走完，后面两章只剩跑图。
 *
 * <p>所以在 <b>20 级</b>与 <b>40 级</b> 各设一道枷锁：
 * 等级到此为止，必须用一枚<b>破枷印记</b>打开，才能继续。
 * 两道枷锁把 60 级切成 1–20 / 21–40 / 41–60 三段，正好对应三章。
 *
 * <h2>为什么枷锁是玩家级而不是勋章级</h2>
 * 「你走到第几章」属于玩家的流程进度，不是某枚勋章的属性。
 * 勋章会掉落、会交易、会被抢，但章节不会跟着物品走。
 * 所以状态存在 {@link PlayerMedals}，破一次枷五枚勋章一起受益。
 *
 * <h2>老存档怎么办</h2>
 * 早已刷到 30 级的玩家不会被打回 20 级 —— 封顶只作用在<b>写入</b>上
 * （见 {@link MedalNbt#addExp(MedalType, double, int)}），已有等级原样保留，
 * 只是不能再涨。
 */
public final class MedalProgression {

    /** 两道枷锁所在的等级：20 与 40。 */
    public static final int[] SHACKLE_LEVELS = {20, 40};

    /** 破枷结果。 */
    public enum BreakResult {
        /** 成功破枷。 */
        SUCCESS,
        /** 已经全部打开。 */
        ALREADY_OPEN,
        /** 还没有任何一枚勋章达到当前枷锁等级。 */
        NOT_READY,
    }

    /**
     * 某个阶段下允许达到的最高等级。
     *
     * @param stage 已破枷数（0 / 1 / 2）
     */
    public static int capFor(int stage) {
        int s = Math.max(0, stage);
        if (s >= SHACKLE_LEVELS.length) {
            return ExpTable.maxLevel();
        }
        return Math.min(SHACKLE_LEVELS[s], ExpTable.maxLevel());
    }

    /**
     * 当前阶段要破的下一道枷锁等级；已全开返回 {@code -1}。
     */
    public static int nextShackleLevel(int stage) {
        if (stage < 0 || stage >= SHACKLE_LEVELS.length) {
            return -1;
        }
        int lv = SHACKLE_LEVELS[stage];
        return lv < ExpTable.maxLevel() ? lv : -1;
    }

    /**
     * 玩家佩戴中勋章的最高等级。
     *
     * <p>只统计<b>佩戴中</b>的勋章：把勋章放在箱子里不该推动章节进度。
     */
    public static int bestWornLevel(ServerPlayer player) {
        int best = MedalSource.NO_MEDAL_LEVEL;
        for (MedalType type : MedalType.values()) {
            best = Math.max(best, MedalSource.level(player, type));
        }
        return best;
    }

    /**
     * 尝试打破下一道枷锁。
     *
     * <p><b>只判定，不改数据</b>：扣物品、放音效、发消息由调用方（道具）负责，
     * 这样命令与道具可以走同一套判定，不会出现"命令能开、道具不能开"的偏差。
     */
    public static BreakResult tryBreak(ServerPlayer player) {
        PlayerMedals pm = PlayerMedals.of(player);
        int stage = pm.getShackleStage();

        int need = nextShackleLevel(stage);
        if (need < 0) {
            return BreakResult.ALREADY_OPEN;
        }
        if (bestWornLevel(player) < need) {
            return BreakResult.NOT_READY;
        }

        pm.setShackleStage(stage + 1);
        return BreakResult.SUCCESS;
    }

    private MedalProgression() {}
}
