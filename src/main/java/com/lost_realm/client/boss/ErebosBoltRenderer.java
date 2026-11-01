package com.lost_realm.client.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import com.lost_realm.Medals;
import com.lost_realm.boss.erebos.ErebosBolt;
import com.lost_realm.boss.erebos.ErebosTuning;

/**
 * 星弹的渲染器 —— 一个面向摄像机的发光小立方。
 *
 * <h2>为什么用 billboard（永远面向相机）</h2>
 * 弹幕在屏幕上只有十几个像素、还飞得飞快。这种尺寸下，
 * "它到底朝向哪边"玩家根本看不出来，但如果它是个有明确朝向的立方体，
 * 转动时会一闪一闪地变化轮廓 —— 反而显得毛糙。
 * 让它永远正对相机，视觉上就是一颗稳定的亮斑，最容易读。
 *
 * <h2>为什么全亮渲染</h2>
 * 用 {@code entityTranslucentEmissive} + {@code FULL_BRIGHT}：
 * 星弹是自发光的东西，不该受环境光照影响。更重要的是 ——
 * Boss 战大概率发生在夜里或暗处，如果弹幕会随光照变暗，
 * 玩家就会"看不清该躲哪"，那是致命的可读性问题。
 */
public class ErebosBoltRenderer extends EntityRenderer<ErebosBolt> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Medals.MODID, "textures/entity/erebos_bolt.png");

    private final ModelPart bolt;

    public ErebosBoltRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.bolt = ErebosModel.bolt();
    }

    @Override
    public void render(ErebosBolt entity, float entityYaw, float partialTick,
                       PoseStack pose, MultiBufferSource buffer, int packedLight) {
        float scale = entity.boltScale();

        // 种子蓄力时膨胀 —— 这是玩家判断"还有多久炸"的第二个线索
        // （第一个是 ErebosBolt 自己画的收拢光环）
        int warn = entity.burstWarning();
        if (warn > 0) {
            double progress = 1.0D - (double) warn / Math.max(1, ErebosTuning.HUNT_BURST_WARN);
            scale *= 1.0F + (float) (progress * 0.9D);
        }

        pose.pushPose();
        pose.scale(scale, scale, scale);
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        this.bolt.render(pose, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();

        super.render(entity, entityYaw, partialTick, pose, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(ErebosBolt entity) {
        return TEXTURE;
    }

    @Override
    protected boolean shouldShowName(ErebosBolt entity) {
        return false;
    }
}
