package com.lost_realm.skill.mage;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import com.lost_realm.config.MedalsConfig;
import com.lost_realm.item.MageAttributes;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;
import com.lost_realm.util.AttributeGuard;
import com.lost_realm.util.ModifierIds;

/**
 * Lv.20【法爆】(被动)
 *
 * <p>让你的<b>法术可以暴击</b>。
 *
 * <p>法术暴击率 <b>+10%</b>,法术暴击伤害 <b>135%</b>,均为固定值。
 * 早先还有一层「每解锁一个后续法师技能额外加成」的成长联动,
 * 因文案过长且让数值不可预期,已整体移除。
 *
 * <h2>为什么用独立属性</h2>
 * Apothic Attributes 的 {@code crit_chance} / {@code crit_damage}
 * <b>只作用于近战伤害</b>,对 {@code magic} 伤害类型无效。
 * 若复用它们,法师的近战也会一并变强,破坏勋章职责隔离。
 * 因此这里使用本模组自注册的
 * {@link MageAttributes#SPELL_CRIT_CHANCE} 与 {@link MageAttributes#SPELL_CRIT_DAMAGE},
 * 由 {@code CombatHandler} 在魔法伤害分支内读取。
 *
 * <p>本类只负责<b>施加属性</b>;真正的掷骰与伤害倍率结算在
 * {@code CombatHandler#handleMageOnDamage} 中完成。
 */
public class SkillSpellCrit implements Skill {

    public static final String ID = "m10_spell_crit";

    @Override
    public MedalType medal() {
        return MedalType.MAGE;
    }

    @Override
    public int unlockLevel() {
        return 20;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component displayName() {
        return Component.translatable("skill.lost_realm.m10_spell_crit");
    }

    @Override
    public List<Component> description() {
        //   ⚠️ 实参个数必须与 lang 里 skill.lost_realm.m10_spell_crit.desc 的
        //      %s 个数**严格一致**（Component 走 String.format，缺参会抛异常）。
        return List.of(Component.translatable("skill.lost_realm.m10_spell_crit.desc",
                MedalsConfig.M10_BASE_CRIT_CHANCE.get(),
                MedalsConfig.M10_BASE_CRIT_DAMAGE.get()));
    }

    @Override
    public void applyPassive(ServerPlayer player, int level) {
        if (level < 20) {
            return;
        }
        double chance = MedalsConfig.pct(MedalsConfig.M10_BASE_CRIT_CHANCE);
        // 暴击伤害:属性语义为倍率,基础 1.0 = 100%。
        // 配置里填 135 表示 135%,换算成「额外加成」= 1.35 - 1.0 = 0.35
        double damageBonus = MedalsConfig.pct(MedalsConfig.M10_BASE_CRIT_DAMAGE) - 1.0D;

        AttributeGuard.apply(player, MageAttributes.SPELL_CRIT_CHANCE.get(),
                ModifierIds.MAGE_M10_SPELL_CRIT_CHANCE, "mage_m10_spell_crit_chance",
                Math.min(1.0D, chance), net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
        AttributeGuard.apply(player, MageAttributes.SPELL_CRIT_DAMAGE.get(),
                ModifierIds.MAGE_M10_SPELL_CRIT_DAMAGE, "mage_m10_spell_crit_damage",
                Math.max(0.0D, damageBonus), net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
    }

    /**
     * 读取玩家当前的法术暴击率(供 {@code CombatHandler} 结算使用)。
     */
    public static double spellCritChance(ServerPlayer player) {
        var attr = MageAttributes.SPELL_CRIT_CHANCE.get();
        var inst = player.getAttribute(attr);
        return inst == null ? 0.0D : Math.max(0.0D, Math.min(1.0D, inst.getValue()));
    }

    /**
     * 读取玩家当前的法术暴击伤害倍率(基础 1.0 = 100%)。
     */
    public static double spellCritDamage(ServerPlayer player) {
        var attr = MageAttributes.SPELL_CRIT_DAMAGE.get();
        var inst = player.getAttribute(attr);
        return inst == null ? 1.0D : Math.max(1.0D, inst.getValue());
    }

    @Override
    public void clear(net.minecraft.world.entity.player.Player player) {
        AttributeGuard.clearByPrefix(player, MageAttributes.SPELL_CRIT_CHANCE.get(), "mage_m10_");
        AttributeGuard.clearByPrefix(player, MageAttributes.SPELL_CRIT_DAMAGE.get(), "mage_m10_");
    }
}
