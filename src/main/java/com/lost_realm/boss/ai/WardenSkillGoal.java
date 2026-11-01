package com.lost_realm.boss.ai;

import net.minecraft.world.entity.ai.goal.Goal;

import com.lost_realm.boss.entity.WardenBoss;

/**
 * Boss 技能 Goal 的<b>公共基类</b> —— 统一冷却、前摇、目标校验。
 *
 * <h2>为什么要抽基类</h2>
 * 四个技能 Goal 有大量共同逻辑:
 * <ul>
 *   <li>都需要"每 N 秒才能放一次"的冷却</li>
 *   <li>都需要"前摇 → 释放"的两段式演出(否则技能瞬发,玩家无从躲避)</li>
 *   <li>都需要"目标在竞技场内 && 不在转阶段"的前置检查</li>
 * </ul>
 * 把这些抽到基类,子类只需实现 {@link #canUseNow()} 与 {@link #cast()}。
 *
 * <h2>前摇的意义</h2>
 * 本 Boss 的所有技能都有明确前摇(castTimeTicks),这是<b>可玩性的核心</b>:
 * 没有前摇的技能等于"随机挨打",玩家无法通过操作规避,
 * 战斗会退化成拼血量。前摇期间 Boss 会停止移动并做出视觉提示。
 */
public abstract class WardenSkillGoal extends Goal {

    protected final WardenBoss boss;

    /** 冷却 tick 数。 */
    private final int cooldownTicks;

    /** 前摇 tick 数。 */
    private final int castTimeTicks;

    /** 剩余冷却。 */
    private int cooldown;

    /** 前摇剩余 tick(-1 表示未在前摇)。 */
    private int casting = -1;

    protected WardenSkillGoal(WardenBoss boss, int cooldownTicks, int castTimeTicks) {
        this.boss = boss;
        this.cooldownTicks = cooldownTicks;
        this.castTimeTicks = castTimeTicks;
    }

    // ================================================================
    // Goal 生命周期
    // ================================================================

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        // dv5:只有「战斗形态」且不在锁血演出中才放技能 ——
        // 零阶段 / 幽灵态 / 波次态一律不放(那时 Boss 要么无敌、要么根本不在场)
        if (!boss.canFight()) {
            return false;
        }
        if (boss.getTarget() == null || !boss.getTarget().isAlive()) {
            return false;
        }
        if (!boss.targetInArena(boss.getTarget())) {
            return false;
        }
        return canUseNow();
    }

    /** 子类实现:当前是否满足释放条件(阶段、血量等)。 */
    protected abstract boolean canUseNow();

    @Override
    public void start() {
        casting = castTimeTicks;
        boss.getNavigation().stop();
        onCastStart();
    }

    @Override
    public boolean canContinueToUse() {
        return casting > 0 && boss.getTarget() != null && boss.getTarget().isAlive();
    }

    @Override
    public void tick() {
        if (casting < 0) {
            return;
        }
        // 前摇期间:面朝目标、原地不动
        if (boss.getTarget() != null) {
            boss.getLookControl().setLookAt(boss.getTarget(), 30.0F, 30.0F);
        }
        onCastTick(castTimeTicks - casting);
        casting--;
        if (casting == 0) {
            cast();
            onCastFinish();
        }
    }

    @Override
    public void stop() {
        casting = -1;
        cooldown = cooldownTicks;
        onStop();
    }

    /** 前摇结束后真正释放技能。 */
    protected abstract void cast();

    // ================================================================
    // 可覆写的演出钩子
    // ================================================================

    /** 开始前摇时的表现(音效、粒子)。 */
    protected void onCastStart() {}

    /** 前摇进行中(可用于画预警圈)。 */
    protected void onCastTick(int elapsed) {}

    /** 释放后的表现。 */
    protected void onCastFinish() {}

    /** 结束时的清理。 */
    protected void onStop() {}

    // ================================================================
    // 工具
    // ================================================================

    protected int castTime() {
        return castTimeTicks;
    }

    /** 已前摇的 tick 数。 */
    protected int elapsedCast() {
        return castTimeTicks - Math.max(0, casting);
    }
}
