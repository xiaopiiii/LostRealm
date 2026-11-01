package com.lost_realm.client.particle;

import com.lost_realm.Medals;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组的自定义粒子类型。
 *
 * <h2>为什么要有自己的粒子</h2>
 * Boss 战与召唤仪式原先全用原版粒子（{@code REVERSE_PORTAL} 紫色方块、
 * {@code SOUL}、{@code END_ROD}、{@code FLASH}、{@code EXPLOSION_EMITTER}）——
 * 玩家反馈"太素、像游戏自带的、不好看"。所以这里注册三个自绘粒子，
 * 用暗黑星渊风格的贴图（{@code textures/particle/erebos_*.png}）。
 *
 * <h2>为什么用 {@link SimpleParticleType}</h2>
 * 这些粒子不需要额外参数（颜色写死在贴图里、大小由 provider 决定），
 * 用最轻的 SimpleParticleType 即可；自定义贴图靠 {@code particles.json}
 * 把粒子名映射到贴图文件。
 */
public final class ModParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Medals.MODID);

    /** 能量光点：召唤 / 星门 / Boss 通用的小发光粒子。 */
    public static final RegistryObject<SimpleParticleType> EREBOS_ORB =
            PARTICLES.register("erebos_orb", () -> new SimpleParticleType(false));

    /** 能量爆发：大的星芒，用于召唤完成、Boss 出场、爆闪。 */
    public static final RegistryObject<SimpleParticleType> EREBOS_BURST =
            PARTICLES.register("erebos_burst", () -> new SimpleParticleType(false));

    public static void init(IEventBus bus) {
        PARTICLES.register(bus);
    }

    private ModParticles() {}
}
