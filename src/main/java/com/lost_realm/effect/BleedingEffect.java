package com.lost_realm.effect;

import javax.annotation.Nullable;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import com.lost_realm.Medals;

/**
 * <b>流血</b> —— 撕裂（刃 Lv.10）施加的持续伤害。
 *
 * <h2>为什么自建</h2>
 * beta29 及以前直接挂的是 Apothic Attributes 的 {@code attributeslib:bleeding}。
 * beta30 拆掉那个前置，效果也必须自己有一份，否则撕裂会「命中没反应」。
 *
 * <h2>数值（与原效果一致）</h2>
 * <ul>
 *   <li>每 <b>40 tick（2 秒）</b>结算一次，伤害 = {@code 1 + 等级}</li>
 *   <li>伤害类型 {@code lost_realm:bleeding}：{@code scaling = never}，
 *       不吃难度缩放，也不会因为攻击力变化而变大</li>
 *   <li>伤害归属 {@code getLastAttacker()}，所以流血致死算玩家的击杀</li>
 * </ul>
 *
 * <p>⚠️ 伤害类型定义在 {@code data/lost_realm/damage_type/bleeding.json}。
 * 只放 Java 不放 json 会在施加时抛「未知伤害类型」，且报错指向注册表、很难联想。
 */
public class BleedingEffect extends MobEffect {

    /**
     * 流血伤害类型。
     *
     * <p>本模组<b>不</b>覆盖原版的 {@code minecraft:bypasses_armor} 标签 ——
     * 那个标签是全局的，改它会影响别的模组的伤害类型。
     * 所以流血伤害照常吃护甲，语义上更像「伤口在护甲缝隙里」，也可接受。
     */
    public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(
            Registries.DAMAGE_TYPE, new ResourceLocation(Medals.MODID, "bleeding"));

    public BleedingEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B0000);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) {
            return;
        }
        entity.hurt(source(entity.level(), entity.getLastAttacker()), 1.0F + amplifier);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 40 == 0;
    }

    /**
     * 构造一次流血伤害。
     *
     * <p><b>直接实体故意留空</b>，只把「造成者」设为攻击者：
     * <ul>
     *   <li>击杀仍然算玩家的（{@code getEntity()} 有值）；</li>
     *   <li>但 {@code getDirectEntity()} 为 null，本模组的吸血 / 元素附加 / 暴击
     *       在判定时都会跳过它 —— 否则每 2 秒一次的流血会反复触发吸血，
     *       变成「挂上流血就永久回血」。</li>
     * </ul>
     *
     * <p>注：原版 {@code DamageSources#source(...)} 是 private，构造 {@link DamageSource}
     * 是唯一可用的路径。
     */
    public static DamageSource source(Level level, @Nullable Entity attacker) {
        var holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DAMAGE_TYPE);
        return new DamageSource(holder, null, attacker);
    }
}
