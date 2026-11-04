package com.lost_realm.boss;

import net.minecraft.server.level.ServerPlayer;

import com.lost_realm.data.PlayerMedals;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;
import com.lost_realm.skill.SkillRegistry;

/**
 * Boss 与<b>勋章系统</b>之间的只读桥接 —— 用于任务链校验与终末审判的实力评估。
 *
 * <h2>单向依赖原则</h2>
 * 本类是 Boss 侧对勋章系统的<b>唯一</b>访问点,且<b>只读</b>。
 * 勋章系统({@code skill/}、{@code item/}、{@code data/})绝不引用 Boss 包 ——
 * 这样即使把 Boss 内容整体删掉,四勋章系统也能独立工作。
 *
 * <h2>用途一:任务链校验</h2>
 * 任务链第二阶段要求"四枚勋章各达 20 级",需要读取玩家勋章等级。
 *
 * <h2>用途二:终末审判的读条时长</h2>
 * 需求要求"读条时长由玩家综合实力决定",这里用
 * 「四勋章等级总和」作为"实力"的量化指标 —— 它天然反映了玩家
 * 在本整合包中的投入深度,且不需要发明新的评分体系。
 */
public final class BossProgressionHooks {

    /** 任务链要求的勋章等级门槛(默认值)。 */
    public static final int REQUIRED_MEDAL_LEVEL = 20;

    /** 任务链要求的勋章等级(旧版固定值,beta41 起不再读配置)。 */
    public static int requiredMedalLevel() {
        return REQUIRED_MEDAL_LEVEL;
    }

    /**
     * 玩家四枚勋章等级之和(0 ~ 120)。
     *
     * @return 总等级;读取失败返回 0(不会因此崩溃)
     */
    public static int totalMedalLevels(ServerPlayer player) {
        try {
            PlayerMedals data = PlayerMedals.of(player);
            if (data == null) {
                return 0;
            }
            int sum = 0;
            for (MedalType t : MedalType.values()) {
                sum += com.lost_realm.data.MedalSource.level(player, t);
            }
            return sum;
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * 是否满足"四勋章各达门槛等级"。
     *
     * <p>门槛由配置决定({@link #requiredMedalLevel()},默认 20)。
     * 用于任务链第二阶段的完成判定。
     */
    public static boolean meetsMedalRequirement(ServerPlayer player) {
        try {
            PlayerMedals data = PlayerMedals.of(player);
            if (data == null) {
                return false;
            }
            int need = requiredMedalLevel();
            for (MedalType t : MedalType.values()) {
                if (com.lost_realm.data.MedalSource.level(player, t) < need) {
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 玩家在某勋章上的等级。 */
    public static int levelOf(ServerPlayer player, MedalType type) {
        try {
            return com.lost_realm.data.MedalSource.level(player, type);
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * 玩家是否正在使用某勋章的主动技能(用于「勋章祭坛」的判定)。
     *
     * <p>判定依据:该勋章的<b>主动</b>技能中有任意一个正处于冷却中。
     * 冷却只会在技能被使用后设置,因此"冷却中"等价于"近期用过"。
     * 这是最简且最稳的判据 —— 不必去枚举技能内部状态,
     * 也不会因为新增技能而需要同步修改。
     */
    public static boolean isUsingMedalAbility(ServerPlayer player, MedalType type) {
        try {
            PlayerMedals data = PlayerMedals.of(player);
            if (data == null) {
                return false;
            }
            // 遍历该勋章的全部技能,只要有主动技能在冷却中即视为"用过"
            for (Skill skill : SkillRegistry.of(type)) {
                if (!skill.isActive()) {
                    continue;
                }
                if (data.getCooldown(skill.id()) > 0) {
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 任意勋章正在使用主动技能(勋章祭坛的宽松判定)。 */
    public static boolean isUsingAnyMedalAbility(ServerPlayer player) {
        for (MedalType t : MedalType.values()) {
            if (isUsingMedalAbility(player, t)) {
                return true;
            }
        }
        return false;
    }

    private BossProgressionHooks() {}
}
