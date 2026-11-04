package com.lost_realm.util;

import java.lang.reflect.Field;

import com.lost_realm.Medals;

/**
 * 抬高原版属性的取值上限。
 *
 * <h2>为什么需要这个</h2>
 * 「最大生命」在原版里是 {@code RangedAttribute}，上限写死 <b>1024</b>。
 * {@code AttributeInstance#calculateValue()} 最后会走一次
 * {@code sanitizeValue()}，也就是把结果硬夹到 [下限, 上限]。
 *
 * <p>后果非常隐蔽：<b>给多了不报错，直接没有</b>。
 * 终局 Boss 设计成 24000 血，实际生成出来只有 1024 ——
 * 玩家两下就把它打死了，而日志里一行警告都没有。
 * 这不是"肉度调得不对"，是"调多少都没用"。
 *
 * <h2>为什么用反射而不是 AccessTransformer</h2>
 * AT 能改成员的修饰符（比如把 {@code private} 变 {@code public}），
 * 但改不了<b>值</b> —— 而这里的 1024 是字段初始化时写进去的。
 * 所以只能在运行期把它读出来再写回去。
 *
 * <h2>为什么按"类型 + 值"找字段，而不是按名字</h2>
 * 生产环境下字段名是 SRG 名（{@code f_22311_} 之类），开发环境是 MCP 名
 * （{@code maxValue}），写死任何一个都会在另一种环境下失效。
 * {@code RangedAttribute} 里 double 字段只有上下限两个，按值匹配又稳又简单。
 *
 * <h2>失败了怎么办</h2>
 * 静默失败，绝不影响游戏启动 —— 但会把 {@link #isMaxHealthRaised()} 置为
 * false，让 {@code UdunosBoss#getMaxHealth()} 走兜底分支。
 * 少一个属性上限，不该连累玩家进不了游戏。
 */
public final class AttributeLimits {

    /** 原版给最大生命写死的上限。 */
    private static final double VANILLA_MAX_HEALTH_CAP = 1024.0D;

    /**
     * 抬高后的上限。
     *
     * <p>取这个数是想让它<b>不构成实际约束</b>：目前最高的设计值是
     * 乌蠹诺斯的 24000 血，留出近 40 倍余量，够以后加更肉的东西。
     */
    private static final double RAISED_MAX_HEALTH_CAP = 1_000_000.0D;

    private static boolean maxHealthRaised;

    /**
     * 把「最大生命」的上限抬高。请在 mod 构造阶段调用 ——
     * 必须早于任何实体被创建（实体的属性实例是在构造时算值的）。
     */
    public static void raiseMaxHealthCap() {
        try {
            Object attribute = net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH;
            Class<?> type = attribute.getClass();
            boolean changed = false;

            for (Field field : type.getDeclaredFields()) {
                if (field.getType() != double.class) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    // 只动那个正好等于原版上限的字段（即 maxValue），
                    // minValue 是 1.0，不会被误伤
                    if (field.getDouble(attribute) == VANILLA_MAX_HEALTH_CAP) {
                        field.setDouble(attribute, RAISED_MAX_HEALTH_CAP);
                        changed = true;
                    }
                } catch (Throwable ignored) {
                    // 单个字段失败不影响其它字段
                }
            }

            maxHealthRaised = changed;
            if (changed) {
                Medals.LOGGER.info("[Lost Realm] 已把最大生命上限从 {} 抬到 {}。",
                        VANILLA_MAX_HEALTH_CAP, RAISED_MAX_HEALTH_CAP);
            } else {
                Medals.LOGGER.warn("[Lost Realm] 未能抬高最大生命上限；"
                        + "超过 1024 的 Boss 血量将由 UdunosBoss 的兜底分支接管。");
            }
        } catch (Throwable t) {
            maxHealthRaised = false;
            Medals.LOGGER.warn("[Lost Realm] 抬高最大生命上限时出错，已忽略。", t);
        }
    }

    /** 上限是否已成功抬高。 */
    public static boolean isMaxHealthRaised() {
        return maxHealthRaised;
    }

    private AttributeLimits() {}
}
