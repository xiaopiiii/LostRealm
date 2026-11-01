package com.lost_realm.client;

import com.lost_realm.event.ArrowCharmHandler;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.projectile.Arrow;

/**
 * 星辰贯穿之箭的箭矢渲染 —— 放大 + 常亮。
 *
 * <h2>为什么替换原版箭的渲染器</h2>
 * 项目没有自定义箭矢实体（复用原版 {@code Arrow}），所以想让"这一支"看起来
 * 与众不同，只能接管 {@code EntityType.ARROW} 的渲染器，再按 NBT 标记分流：
 * 带 {@link ArrowCharmHandler#TAG_STAR} 的放大 + 打满亮度，其余原样交给
 * {@code super.render}。注册一处、影响全局，但代价只是每支箭一次
 * {@code getBoolean} 判断。
 *
 * <h2>为什么亮度要写死</h2>
 * 普通箭在暗处就是一团黑。星辰箭是"凝聚了能量的箭"，无论飞到哪里都该是亮的 ——
 * 直接给 {@link LightTexture#FULL_BRIGHT}，跳过世界光照采样。
 */
public class StarArrowRenderer extends ArrowRenderer<Arrow> {

    /** 放大倍数：原版箭有点小，2.5 倍才有"贯穿星辰"的体量感。 */
    private static final float STAR_SCALE = 2.5F;

    /** 星辰箭用「普通箭」那张贴图，跟周围任何箭都不一样。 */
    private static final ResourceLocation STAR_TEXTURE =
            new ResourceLocation("textures/entity/projectiles/arrow.png");
    /** 其余箭沿用原版 tipped_arrow 贴图，外观与装模组前完全一致。 */
    private static final ResourceLocation VANILLA_TEXTURE =
            new ResourceLocation("textures/entity/projectiles/tipped_arrow.png");

    public StarArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(Arrow entity) {
        return entity.getPersistentData().getBoolean(ArrowCharmHandler.TAG_STAR)
                ? STAR_TEXTURE
                : VANILLA_TEXTURE;
    }

    @Override
    public void render(Arrow entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (!entity.getPersistentData().getBoolean(ArrowCharmHandler.TAG_STAR)) {
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
            return;
        }
        poseStack.pushPose();
        poseStack.scale(STAR_SCALE, STAR_SCALE, STAR_SCALE);
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }
}
