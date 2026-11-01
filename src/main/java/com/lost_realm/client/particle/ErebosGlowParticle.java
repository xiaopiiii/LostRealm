package com.lost_realm.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * 自绘发光粒子 —— 暗黑星渊风格的星芒光点。
 *
 * <p>从贴图图集取 sprite（{@code particles.json} 里映射到
 * {@code textures/particle/erebos_*.png}），用加法混合发光，
 * 随寿命淡出。这是替换原版丑粒子（紫色方块 / 烟雾）的核心。
 */
public class ErebosGlowParticle extends TextureSheetParticle {

    /** 是否用「星芒爆发」图（大的多尖角），否则用「光点」图。 */
    private final boolean burst;

    protected ErebosGlowParticle(ClientLevel level, double x, double y, double z,
                                 double vx, double vy, double vz,
                                 SpriteSet sprites, boolean burst) {
        super(level, x, y, z, vx, vy, vz);
        this.burst = burst;
        this.pickSprite(sprites);
        // 寿命：0.6~1.4 秒，随机
        this.lifetime = 12 + this.random.nextInt(16);
        this.gravity = 0.0F;
        this.friction = 0.96F;
        this.quadSize = burst
                ? 0.5F + this.random.nextFloat() * 0.5F
                : 0.12F + this.random.nextFloat() * 0.18F;
        this.alpha = 1.0F;
        // 自带一点自转，让星芒"旋"起来
        this.roll = this.random.nextFloat() * 360.0F;
        this.oRoll = this.roll;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        // 阻尼减速
        this.xd *= this.friction;
        this.yd *= this.friction;
        this.zd *= this.friction;
        this.move(this.xd, this.yd, this.zd);
        // 淡出 + 缓慢缩小
        float lifeFrac = this.age / (float) this.lifetime;
        this.alpha = 1.0F - lifeFrac;
        if (this.burst) {
            this.quadSize *= 0.97F;
        }
        this.oRoll = this.roll;
        this.roll += this.burst ? 2.0F : 4.0F;
    }

    @Override
    public ParticleRenderType getRenderType() {
        // 贴图自带 alpha（黑底已转透明），用普通半透明混合即可发光。
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** 光点粒子。 */
    public static class OrbProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public OrbProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new ErebosGlowParticle(level, x, y, z, vx, vy, vz, sprites, false);
        }
    }

    /** 星芒爆发粒子。 */
    public static class BurstProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public BurstProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new ErebosGlowParticle(level, x, y, z, vx, vy, vz, sprites, true);
        }
    }
}
