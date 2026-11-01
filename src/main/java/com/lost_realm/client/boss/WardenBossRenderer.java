package com.lost_realm.client.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

import com.lost_realm.Medals;
import com.lost_realm.boss.entity.WardenBoss;

/**
 * 遗落之主的 GeckoLib 渲染器。
 *
 * <h2>它替代了什么</h2>
 * dv12 之前用的是 {@code MobRenderer<WardenBoss, HumanoidModel<WardenBoss>>} ——
 * 只能给原版人形骨骼贴图,靠 {@code ItemInHandLayer} 加了一层手持物品渲染,
 * 但<b>无法表达触手 / 骨刺一类自定义几何</b>(原因见
 * {@link WardenBossModel} 的类注释)。
 *
 * <p>换成 {@link GeoEntityRenderer} 后:
 * <ul>
 *   <li>几何来自 {@code geo/*.geo.json},骨骼树可任意嵌套(触手链、骨翼)</li>
 *   <li>动画来自 {@code animations/*.animation.json},由实体侧的
 *       {@code AnimationController} 驱动</li>
 *   <li>手持物品由 GeckoLib 的 {@code ItemInHandGeoLayer} 自动处理,
 *       不再需要手写 {@code ItemInHandLayer}</li>
 * </ul>
 *
 * <h2>⚠️ 模型缩放必须走 {@code preRender},不能指望 {@code getScale()}</h2>
 * 这是 dv13 最重要的一条结论。{@code Entity#getScale()} <b>只影响碰撞箱</b>
 * (通过 {@code getDimensions}),原版渲染管线根本不消费它 ——
 * 用 {@code javap -c} 查 {@code LivingEntityRenderer#render} 的字节码,
 * 搜不到任何 {@code getScale} 调用。
 *
 * <p>因此在 GeckoLib 路径下,想让<b>模型</b>随形态放大,必须自己缩放
 * {@link PoseStack}。这里在 {@code preRender} 里按实体的形态系数
 * {@code pushPose + scale} —— 与 {@code getScale()} 用的是<b>同一个数值来源</b>
 * ({@code WardenBoss#getScale()}),因此模型与碰撞箱始终一致:
 * 视觉上多大,判定上就多大。
 *
 * <p>另外注意模型原点在<b>脚底</b>,而 {@code PoseStack} 在此时已位于脚底位置,
 * 所以直接 {@code scale} 即可(不需要先抬升),否则会出现"模型浮空"。
 */
public class WardenBossRenderer extends GeoEntityRenderer<WardenBoss> {

    public WardenBossRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new WardenBossModel());
        this.shadowRadius = 0.7F;
        // ---- 自发光图层（dv16 新增）----
        // GeckoLib 的 AutoGlowingTexture 会去找 **同名 + `_glowmask`** 的贴图
        // （反汇编 AutoGlowingTexture 确认字符串常量 `_glowmask`），
        // 用自发光 RenderType 把模型再渲染一遍 —— 于是兜帽里的眼睛、
        // 胸口符纹、脑后光环、翼上的眼、兵刃锋口 在暗处会自己发亮。
        // 遮罩由 tools/gen_texture.py 一并产出（warden_glowmask.png）。
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
        // ---- 手持物品层（dv30 新增）----
        // GeckoLib 4.4.9 没有 ItemInHandGeoLayer，这里用通用的
        // BlockAndItemGeoLayer 自己实现一份：把主手物品挂到 weapon 骨骼上。
        // 巨镰（lost_realm:warden_scythe）正是靠这一层出现在 Boss 手里。
        this.addRenderLayer(new WardenItemInHandLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(WardenBoss entity) {
        return new ResourceLocation(Medals.MODID, "textures/entity/warden.png");
    }

    /**
     * 几何的「原始高度」（方块）。由 {@code gen_geo.py} 的骨架决定。
     *
     * <p>★ dv28：模型从 48px 放大到 <b>96px</b>（腿 36 + 身 36 + 头 24），
     * 即 <b>6.0 方块</b>。放大的是**建模空间**（细节更多），
     * 游戏内尺寸仍由这里换算回去 —— 所以显示大小、碰撞箱都没变，
     * 只是模型能做细一倍。
     */
    private static final float BASE_GEO_HEIGHT = 6.0F;

    /**
     * ★ dv34：二三阶段换成用户给的 bbmodel（高 47.8px ≈ 2.99 格），
     * 与 form1 的 96px（6 格）差一倍，所以基准高度要**按形态取** ——
     * 否则那两形态会被算成一半大小（模型只有碰撞箱的一半高、看着陷进地里）。
     */
    private static final float BASE_GEO_HEIGHT_REAPER = 2.99F;

    /** 当前形态对应的几何基准高度（格）。 */
    private static float baseGeoHeight(WardenBoss entity) {
        var st = entity.getLordState();
        var shown = st.isTransition() ? st.nextFormAfterTransition() : st;
        if (shown == com.lost_realm.boss.LordState.FORM_2
                || shown == com.lost_realm.boss.LordState.FORM_3) {
            return BASE_GEO_HEIGHT_REAPER;
        }
        return BASE_GEO_HEIGHT;
    }

    /**
     * 把模型对齐到碰撞箱,并按形态缩放。
     *
     * <h2>为什么要除以基准高度</h2>
     * GeckoLib 按「1 像素 = 1/16 方块」放样几何,我们的骨架是 32 像素高,
     * 即几何自然高度只有 <b>2.0 方块</b>;而 Boss 注册尺寸是
     * <b>1.2 × 4.0</b>(见 {@code BossEntities})。若直接渲染,
     * 模型会比碰撞箱矮一半 —— 表现为「看着打得到、实际判定不到」。
     *
     * <p>因此先乘 {@code 注册高 / 几何高 = 4.0 / 2.0 = 2.0} 做归一,
     * 再乘形态系数 {@link WardenBoss#getScale()}。
     * 两者相乘的结果与碰撞箱严格一致。
     *
     * <p>⚠️ <b>不能用 {@code GeoEntityRenderer#withScale}</b> ——
     * 那是构造期定死的常量,无法随形态变化。形态是运行期动态的,
     * 必须走 {@code preRender} 每帧计算。
     */
    @Override
    public void preRender(PoseStack poseStack, WardenBoss entity, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick,
                          int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        // 归一化到碰撞箱高度 × 形态系数
        float s = (entity.getBbHeight() / baseGeoHeight(entity)) * entity.getScale();
        if (Math.abs(s - 1.0F) > 1.0E-4F) {
            poseStack.scale(s, s, s);
        }
        super.preRender(poseStack, entity, model, bufferSource, buffer,
                isReRender, partialTick, packedLight, packedOverlay,
                red, green, blue, alpha);
    }
}
