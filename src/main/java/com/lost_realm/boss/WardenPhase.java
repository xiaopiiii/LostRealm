package com.lost_realm.boss;

import net.minecraft.world.BossEvent;

/**
 * 终末之影的三个阶段(形态)。
 *
 * <h2>形态设定</h2>
 * <ol>
 *   <li>{@link #PHASE_1 残影} —— 人形,持镰。教玩家认技能,压力最低。</li>
 *   <li>{@link #PHASE_2 崩解} —— 半透明,分裂出 {@code PHASE_2_CLONES} 个分身,
 *       本体影子颜色略有差异(需玩家辨别)。</li>
 *   <li>{@link #PHASE_3 终末} —— 巨大化并浮空,释放全屏技能,玩家需躲石柱。</li>
 * </ol>
 *
 * <h2>血条颜色</h2>
 * 每个阶段一个颜色,玩家不看数字也能从血条颜色判断进度。
 * 这里把颜色作为枚举元数据而不是散落在渲染代码里,便于集中调整。
 */
public enum WardenPhase {

    PHASE_1("phase_1",
            "boss.lost_realm.phase.1",
            BossEvent.BossBarColor.PURPLE,
            /* 移动速度 */ 0.30D, /* 伤害倍率 */ 1.0F, /* 是否浮空 */ false),

    PHASE_2("phase_2",
            "boss.lost_realm.phase.2",
            BossEvent.BossBarColor.RED,
            /* 移动速度 */ 0.38D, /* 伤害倍率 */ 1.35F, /* 是否浮空 */ false),

    PHASE_3("phase_3",
            "boss.lost_realm.phase.3",
            BossEvent.BossBarColor.WHITE,
            /* 移动速度 */ 0.26D, /* 伤害倍率 */ 1.8F, /* 是否浮空 */ true);

    private final String id;
    private final String langKey;
    private final BossEvent.BossBarColor barColor;
    private final double moveSpeed;
    private final float damageMultiplier;
    private final boolean flying;

    WardenPhase(String id, String langKey, BossEvent.BossBarColor barColor,
                double moveSpeed, float damageMultiplier, boolean flying) {
        this.id = id;
        this.langKey = langKey;
        this.barColor = barColor;
        this.moveSpeed = moveSpeed;
        this.damageMultiplier = damageMultiplier;
        this.flying = flying;
    }

    public String id() {
        return id;
    }

    public String langKey() {
        return langKey;
    }

    public BossEvent.BossBarColor barColor() {
        return barColor;
    }

    public double moveSpeed() {
        return moveSpeed;
    }

    public float damageMultiplier() {
        return damageMultiplier;
    }

    /** 第三阶段浮空 —— 玩家无法近战,必须用远程/法术,构成阶段设计的张力。 */
    public boolean isFlying() {
        return flying;
    }

    /** 按序号取阶段(用于状态存档与网络同步)。 */
    public static WardenPhase byIndex(int i) {
        WardenPhase[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }
}
