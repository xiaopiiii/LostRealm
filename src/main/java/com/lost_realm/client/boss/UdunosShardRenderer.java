package com.lost_realm.client.boss;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import com.lost_realm.Medals;
import com.lost_realm.boss.udunos.UdunosShard;

/**
 * 死亡碎块的渲染器 —— 一个缩小的体节方块。
 *
 * <h2>为什么直接用体节的几何</h2>
 * 设计稿要求「从当前 Boss 贴图切出碎片」。真正切 UV 需要给每块碎片
 * 单独写一套 UV 偏移，而这块方块只有 0.1~0.2 格大 ——
 * 玩家在几十格外看到的是"一小片带装甲花纹的东西在翻滚"，
 * 切不切 UV 肉眼分不出来，但多出来的每实体贴图参数会让渲染翻倍。
 * 所以统一用体节贴图的一小块，靠缩小 + 随机自转制造"碎"的观感。
 *
 * <p>淡出用 {@code entityTranslucent} + alpha，最后 0.4 秒逐渐消失。
 */
public class UdunosShardRenderer extends EntityRenderer<UdunosShard> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Medals.MODID, "textures/entity/udunos_body.png");

    private final ModelPart shard;

    public UdunosShardRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shard = UdunosModel.body();
    }

    @Override
    public void render(UdunosShard entity, float entityYaw, float partialTick,
                       PoseStack pose, MultiBufferSource buffer, int packedLight) {

        float s = entity.shardScale();
        float alpha = entity.fade();
        if (alpha <= 0.02F) {
            return;
        }

        pose.pushPose();
        // 自转：xRot 由实体每 tick 累加，yRot 是它的朝向（同时充当翻滚角）
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(
                Mth.lerp(partialTick, entity.yRotO, entity.getYRot())));
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(
                Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(entity.getYRot() * 0.37F));
        pose.scale(s, s, s);
        // 与主渲染器保持一致：MC 模型空间 +Y 向下
        pose.scale(-1.0F, -1.0F, 1.0F);

        var buf = buffer.getBuffer(RenderType.entityTranslucent(TEXTURE));
        this.shard.render(pose, buf, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                1.0F, 1.0F, 1.0F, alpha);
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(UdunosShard entity) {
        return TEXTURE;
    }
}
