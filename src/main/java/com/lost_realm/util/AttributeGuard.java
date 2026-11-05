package com.lost_realm.util;

import java.util.Collection;
import java.util.UUID;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import com.lost_realm.Medals;
import com.lost_realm.config.MedalsConfig;

/**
 * 属性修饰符守卫 —— 防叠加保护。
 *
 * <p>职责:
 * <ol>
 *   <li>施加修饰符前,先移除同 UUID 的旧修饰符,保证「覆盖而非累加」。</li>
 *   <li>统计同一 UUID 的现存数量,超过 {@link MedalsConfig#MAX_MODIFIERS_PER_UUID} 时拒绝施加。</li>
 *   <li>提供一键清除本模组全部修饰符的能力(等级回落/勋章卸下时使用)。</li>
 * </ol>
 *
 * <p>固定 UUID 的语义见 {@link ModifierIds}。所有技能都必须经由本类施加修饰符。
 */
public final class AttributeGuard {

    /**
     * 安全施加一个固定 UUID 修饰符。
     *
     * @return 实际施加成功时返回 true
     */
    public static boolean apply(LivingEntity entity, Attribute attr, UUID uuid, String name,
                                double amount, AttributeModifier.Operation op) {
        if (entity == null || attr == null || uuid == null) {
            return false;
        }
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst == null) {
            return false;
        }

        // 防叠加:同 UUID 视为同一修饰符,先移除再加,避免重复触发导致无限叠加
        AttributeModifier existing = inst.getModifier(uuid);

        // ★ 幂等短路(beta44):数值与运算都没变时什么都不做。
        //
        //   本方法在 tick 里被高频调用(每枚勋章每 tick 一次基础成长 + 每个已解锁技能
        //   各若干项),原先每次都 remove+add —— 属性表被标脏、客户端每 tick 收到属性包、
        //   其它模组的属性缓存全部失效。实测这是战斗掉帧的主要来源。
        //   加上这三行后,稳态下每次调用只是一次 getModifier + 两次比较。
        if (existing != null && existing.getOperation() == op
                && Math.abs(existing.getAmount() - amount) < 1.0E-6D) {
            return true;
        }

        int sameCount = countByUuid(inst, uuid);
        if (sameCount > MedalsConfig.MAX_MODIFIERS_PER_UUID.get()) {
            Medals.LOGGER.warn("[Lost Realm] 检测到修饰符 {} 数量异常({}),已拒绝继续叠加以保护稳定性。",
                    name, sameCount);
            return false;
        }
        if (existing != null) {
            inst.removeModifier(uuid);
        }

        AttributeModifier modifier = new AttributeModifier(uuid, Medals.MODID + ":" + name, amount, op);
        // addTransientModifier 返回 void,施加后校验是否生效
        inst.addTransientModifier(modifier);
        return inst.getModifier(uuid) != null;
    }

    /** 移除指定 UUID 的修饰符。 */
    public static void remove(LivingEntity entity, Attribute attr, UUID uuid) {
        if (entity == null || attr == null || uuid == null) {
            return;
        }
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst != null && inst.getModifier(uuid) != null) {
            inst.removeModifier(uuid);
        }
    }

    /** 清理本模组在一项属性上的所有修饰符(名称以 lost_realm: 开头且 UUID 属于本模组)。 */
    public static void clearOwned(LivingEntity entity, Attribute attr) {
        if (entity == null || attr == null) {
            return;
        }
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst == null) {
            return;
        }
        Collection<AttributeModifier> mods = inst.getModifiers();
        UUID[] toRemove = mods.stream()
                .filter(m -> isOurs(m) && !isProtected(m))
                .map(AttributeModifier::getId)
                .toArray(UUID[]::new);
        for (UUID u : toRemove) {
            inst.removeModifier(u);
        }
    }

    /**
     * 世界难度挂的修饰符不归「勋章清理」管。
     *
     * <p>勋章卸下时 {@link #clearOwned} 是按「{@code lost_realm:} 前缀」清整条属性的，
     * 而难度的攻/甲修饰符正好也是同前缀、同属性（{@code ATTACK_DAMAGE} / {@code ARMOR}）。
     * 不加这层排除的话，卸一枚战士勋章会把「深渊」的 -15% 攻击一起删掉 ——
     * 虽然 40 tick 后会被 {@code DifficultyManager#refreshPlayer} 补回来，
     * 但那两秒里玩家是无惩罚状态。
     */
    private static boolean isProtected(AttributeModifier m) {
        UUID id = m.getId();
        return id.equals(ModifierIds.DIFFICULTY_PLAYER_ATTACK)
                || id.equals(ModifierIds.DIFFICULTY_PLAYER_ARMOR)
                || id.equals(ModifierIds.DIFFICULTY_JOB_SLOT);
    }

    /** 判断修饰符是否由本模组创建。 */
    public static boolean isOurs(AttributeModifier m) {
        return m.getName() != null && m.getName().startsWith(Medals.MODID + ":");
    }

    /**
     * 只清除「本模组且名称以给定前缀开头」的修饰符。
     *
     * <p>用于技能停用时精确回收自己的修饰符,避免误删同一属性上其它技能的效果。
     *
     * @param prefix 修饰符名(形如 {@code "tank_t5_"});传 {@code null} 表示清全部本模组修饰符
     */
    public static void clearByPrefix(LivingEntity entity, Attribute attr, String prefix) {
        if (entity == null || attr == null) {
            return;
        }
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst == null) {
            return;
        }
        String full = Medals.MODID + ":";
        UUID[] toRemove = inst.getModifiers().stream()
                .filter(m -> m.getName() != null && m.getName().startsWith(full)
                        && (prefix == null || m.getName().startsWith(full + prefix)))
                .map(AttributeModifier::getId)
                .toArray(UUID[]::new);
        for (UUID u : toRemove) {
            inst.removeModifier(u);
        }
    }

    private static int countByUuid(AttributeInstance inst, UUID uuid) {
        int n = 0;
        for (AttributeModifier m : inst.getModifiers()) {
            if (m.getId().equals(uuid)) {
                n++;
            }
        }
        return n;
    }

    private AttributeGuard() {}
}
