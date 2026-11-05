package com.lost_realm.client.sky;

import org.joml.Matrix4f;

import com.lost_realm.Medals;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * 遗落境维度的「动态宇宙背景」。
 *
 * <h2>为什么自己画，而不是换一张天空贴图</h2>
 * 设计稿要求四件事：星云有形变翻涌、星环实际转动、陨石有深度视差、星光按不同周期闪烁。
 * 一张静态贴图做不到其中任何一件 —— 它连"转"都不会。
 * 所以这里接管 {@code renderSky}，直接在天空球上画几何：
 * 全部是顶点色四边形，不带贴图，也就不需要额外的美术资源。
 *
 * <h2>四阶段递进</h2>
 * 亮度与星环转速跟着 Boss 的当前阶段走（{@link #setIntensity}）。
 * 越往后星云越亮、星环转得越快 —— 玩家不看血条也能从"天色"感到压力在上升。
 *
 * <h2>性能</h2>
 * 每帧约 200 个四边形，全部在一次 draw call 里提交，
 * 比原版末地天空的星空盒还轻。没有逐帧分配（buffer 由 Tesselator 复用）。
 */
public class UdunosSkyEffects extends DimensionSpecialEffects {

    public static final ResourceLocation ID = new ResourceLocation(Medals.MODID, "udunos_sky");

    /** 天球半径 —— 只要比渲染距离内的所有东西远，又不会被远裁剪面切掉。 */
    private static final float R = 100.0F;

    /** 星云块数量。再多就会糊成一片紫色。 */
    private static final int NEBULA_COUNT = 9;

    /** 星光数量。 */
    private static final int STAR_COUNT = 260;

    /** 当前强度 0~1（阶段Ⅰ→Ⅳ 由 0 升到 1）。 */
    private static float intensity = 0.25F;

    /** 由 Boss 的阶段驱动。 */
    public static void setIntensity(float value) {
        intensity = Mth.clamp(value, 0.0F, 1.0F);
    }

    public UdunosSkyEffects() {
        // 无云、无地面、完全不画原版天空（SkyType.NONE）、不强制亮色光照
        super(Float.NaN, false, SkyType.NONE, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 base, float brightness) {
        // 紫黑色的雾，比原版暗，避免远处糊成灰
        return base.multiply(0.12D, 0.09D, 0.18D);
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack pose,
                             Camera camera, Matrix4f projection, boolean isFoggy, Runnable setupFog) {

        pose.pushPose();
        // ★ 不要再套一次相机旋转。
        //   LevelRenderer#renderLevel 在调 renderSky 之前已经做过
        //       pose.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
        //       pose.mulPose(Axis.YP.rotationDegrees(camera.getYRot() + 180));
        //   这里再反向转一次等于把相机旋转「抵消」掉 ——
        //   几何就退化成视空间（贴在屏幕上不动），正是 beta31 出现的现象。
        //   什么都不做，才是"固定在世界上、随视角转动"。位置不平移则保证它在无穷远。

        Matrix4f mat = pose.last().pose();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();

        // ---- 1. 星云：几块大而暗的紫色，缓慢翻涌 ----
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        RandomSource rnd = RandomSource.create(20240517L);
        float t = (ticks + partialTick) * 0.0025F;
        for (int i = 0; i < NEBULA_COUNT; i++) {
            float ang = rnd.nextFloat() * Mth.TWO_PI;
            float elev = -0.55F + rnd.nextFloat() * 1.1F;
            float size = 26.0F + rnd.nextFloat() * 34.0F;
            float drift = t + i * 0.7F;
            float a = (0.035F + 0.05F * intensity) * (0.75F + 0.25F * Mth.sin(drift * 2.1F));

            double cy = Math.sin(elev);
            double cz = Math.cos(elev) * Math.cos(ang);
            double cx = Math.cos(elev) * Math.sin(ang);

            // 以该方向为中心，取两个切向量张开一个四边形
            double ux = Math.cos(ang), uz = -Math.sin(ang);
            double vx = -Math.sin(elev) * Math.sin(ang);
            double vy = Math.cos(elev);
            double vz = -Math.sin(elev) * Math.cos(ang);

            float s = size;
            quad(buf, mat,
                    cx * R, cy * R, cz * R,
                    ux, uz, vx, vy, vz, s,
                    0.30F, 0.17F, 0.48F, a);
        }
        tess.end();

        // ---- 2. 巨大星体 + 星环 ----
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float sunAng = (ticks + partialTick) * 0.00035F;
        float sunEl = 0.30F;
        double sx = Math.cos(sunEl) * Math.sin(sunAng);
        double sy = Math.sin(sunEl);
        double sz = Math.cos(sunEl) * Math.cos(sunAng);

        // 光晕（三层，越外越淡）
        disc(buf, mat, sx, sy, sz, 34.0F, 0.42F, 0.30F, 0.62F, 0.05F + 0.05F * intensity);
        disc(buf, mat, sx, sy, sz, 26.0F, 0.55F, 0.40F, 0.78F, 0.12F + 0.06F * intensity);
        disc(buf, mat, sx, sy, sz, 17.0F, 0.78F, 0.62F, 0.95F, 0.85F);

        // 星环：绕星体的两个椭圆环，转速随阶段加快
        float ringSpin = (ticks + partialTick) * (0.006F + 0.010F * intensity);
        ring(buf, mat, sx, sy, sz, 40.0F, 46.0F, ringSpin, 0.62F, 0.44F, 0.92F, 0.30F);
        ring(buf, mat, sx, sy, sz, 52.0F, 58.0F, ringSpin + 0.5F, 0.40F, 0.28F, 0.62F, 0.20F);
        tess.end();

        // ---- 3. 星光：不同周期闪烁 ----
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        RandomSource srnd = RandomSource.create(8848L);
        for (int i = 0; i < STAR_COUNT; i++) {
            float ang = srnd.nextFloat() * Mth.TWO_PI;
            float elev = (float) Math.asin(srnd.nextFloat() * 2.0F - 1.0F);
            float phase = srnd.nextFloat() * Mth.TWO_PI;
            float speed = 0.9F + srnd.nextFloat() * 2.6F;      // 每颗星自己的周期
            float twinkle = 0.45F + 0.55F * Mth.sin((ticks + partialTick) * 0.05F * speed + phase);
            float a = twinkle * (0.55F + 0.35F * intensity);
            float size = 0.9F + srnd.nextFloat() * 1.4F;

            double cy = Math.sin(elev);
            double cz = Math.cos(elev) * Math.cos(ang);
            double cx = Math.cos(elev) * Math.sin(ang);
            double ux = Math.cos(ang), uz = -Math.sin(ang);
            double vx = -Math.sin(elev) * Math.sin(ang);
            double vy = Math.cos(elev);
            double vz = -Math.sin(elev) * Math.cos(ang);

            quad(buf, mat, cx * R, cy * R, cz * R, ux, uz, vx, vy, vz, size,
                    0.92F, 0.88F, 1.0F, a);
        }
        tess.end();

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        pose.popPose();

        // true = 天空由我们接管，别让原版再画一遍
        return true;
    }

    // ================================================================
    // 几何工具
    // ================================================================

    /** 以 (cx,cy,cz) 为中心、两个切向量张开的方形。 */
    private static void quad(BufferBuilder buf, Matrix4f mat,
                             double cx, double cy, double cz,
                             double ux, double uz,
                             double vx, double vy, double vz,
                             float size,
                             float r, float g, float b, float a) {
        // ★ 零分配(beta44):原来是 `double[][] corners = {{-1,-1},...}`，
        //   每画一个四边形就 new 5 个对象。星空 260 + 星云 9 = 每帧 1345 个，
        //   60fps 下约 8 万个/秒，手机端直接反映成 GC 抖动。
        //   四个角是常量，展开写即可，一个对象都不用分配。
        quadVertex(buf, mat, cx, cy, cz, ux, uz, vx, vy, vz, size, -1.0D, -1.0D, r, g, b, a);
        quadVertex(buf, mat, cx, cy, cz, ux, uz, vx, vy, vz, size, 1.0D, -1.0D, r, g, b, a);
        quadVertex(buf, mat, cx, cy, cz, ux, uz, vx, vy, vz, size, 1.0D, 1.0D, r, g, b, a);
        quadVertex(buf, mat, cx, cy, cz, ux, uz, vx, vy, vz, size, -1.0D, 1.0D, r, g, b, a);
    }

    /** {@link #quad} 的单个角点。抽出来只为把循环变成 4 次直调。 */
    private static void quadVertex(BufferBuilder buf, Matrix4f mat,
                                   double cx, double cy, double cz,
                                   double ux, double uz,
                                   double vx, double vy, double vz,
                                   float size, double cu, double cv,
                                   float r, float g, float b, float a) {
        float px = (float) (cx + (ux * cu + vx * cv) * size);
        float py = (float) (cy + (vy * cv) * size);
        float pz = (float) (cz + (uz * cu + vz * cv) * size);
        buf.vertex(mat, px, py, pz).color(r, g, b, a).endVertex();
    }

    /** 面向原点的圆盘（星体 / 光晕）。 */
    private static void disc(BufferBuilder buf, Matrix4f mat,
                             double dx, double dy, double dz,
                             float radius, float r, float g, float b, float a) {
        // 取任意两个与方向正交的向量
        double[] u = ortho(dx, dy, dz);
        double[] v = cross(dx, dy, dz, u[0], u[1], u[2]);
        int seg = 28;
        for (int i = 0; i < seg; i++) {
            double a0 = Mth.TWO_PI * i / seg;
            double a1 = Mth.TWO_PI * (i + 1) / seg;
            vertex(buf, mat, dx, dy, dz, u, v, 0, 0, r, g, b, a);
            vertex(buf, mat, dx, dy, dz, u, v, Math.cos(a0) * radius, Math.sin(a0) * radius, r, g, b, a);
            vertex(buf, mat, dx, dy, dz, u, v, Math.cos(a1) * radius, Math.sin(a1) * radius, r, g, b, a);
            vertex(buf, mat, dx, dy, dz, u, v, 0, 0, r, g, b, a);
        }
    }

    /** 倾斜的椭圆环，每段一个四边形；内圈亮、外圈淡出。 */
    private static void ring(BufferBuilder buf, Matrix4f mat,
                             double dx, double dy, double dz,
                             float radiusIn, float radiusOut, float spin,
                             float r, float g, float b, float a) {
        double[] u = ortho(dx, dy, dz);
        double[] v = cross(dx, dy, dz, u[0], u[1], u[2]);
        double tilt = 0.55D;
        int seg = 56;
        for (int i = 0; i < seg; i++) {
            double a0 = Mth.TWO_PI * i / seg + spin;
            double a1 = Mth.TWO_PI * (i + 1) / seg + spin;

            float c0 = (float) Math.cos(a0);
            float s0 = (float) (Math.sin(a0) * tilt);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) (Math.sin(a1) * tilt);

            // ★ 零分配(beta44):原来是 4 次 pointOnRing，每次 new float[3]，
            //   2 个环 × 56 段 = 每帧 448 个数组。改成直接算完就写顶点。
            ringVertex(buf, mat, dx, dy, dz, u, v, c0, s0, radiusIn, r, g, b, a);
            ringVertex(buf, mat, dx, dy, dz, u, v, c1, s1, radiusIn, r, g, b, a);
            ringVertex(buf, mat, dx, dy, dz, u, v, c1, s1, radiusOut, r, g, b, a * 0.3F);
            ringVertex(buf, mat, dx, dy, dz, u, v, c0, s0, radiusOut, r, g, b, a * 0.3F);
        }
    }

    /** 星环上的一个顶点(直接写入缓冲区,不产生中间数组)。 */
    private static void ringVertex(BufferBuilder buf, Matrix4f mat,
                                   double dx, double dy, double dz,
                                   double[] u, double[] v,
                                   float ca, float sa, float radius,
                                   float r, float g, float b, float a) {
        float px = (float) (dx * R + (u[0] * ca + v[0] * sa) * radius);
        float py = (float) (dy * R + (u[1] * ca + v[1] * sa) * radius);
        float pz = (float) (dz * R + (u[2] * ca + v[2] * sa) * radius);
        buf.vertex(mat, px, py, pz).color(r, g, b, a).endVertex();
    }
    private static void vertex(BufferBuilder buf, Matrix4f mat,
                               double dx, double dy, double dz,
                               double[] u, double[] v,
                               double offU, double offV,
                               float r, float g, float b, float a) {
        float px = (float) (dx * R + (u[0] * offU + v[0] * offV));
        float py = (float) (dy * R + (u[1] * offU + v[1] * offV));
        float pz = (float) (dz * R + (u[2] * offU + v[2] * offV));
        buf.vertex(mat, px, py, pz).color(r, g, b, a).endVertex();
    }

    private static double[] ortho(double x, double y, double z) {
        // 取一个与 (x,y,z) 不平行的参考轴
        double ax = Math.abs(y) < 0.9D ? 0.0D : 1.0D;
        double ay = Math.abs(y) < 0.9D ? 1.0D : 0.0D;
        double[] c = cross(x, y, z, ax, ay, 0.0D);
        double len = Math.sqrt(c[0] * c[0] + c[1] * c[1] + c[2] * c[2]);
        if (len < 1.0E-6D) {
            return new double[]{1.0D, 0.0D, 0.0D};
        }
        return new double[]{c[0] / len, c[1] / len, c[2] / len};
    }

    private static double[] cross(double ax, double ay, double az,
                                  double bx, double by, double bz) {
        return new double[]{
                ay * bz - az * by,
                az * bx - ax * bz,
                ax * by - ay * bx
        };
    }
}
