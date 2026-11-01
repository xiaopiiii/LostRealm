package com.lost_realm.client.boss;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import com.lost_realm.Medals;
import com.lost_realm.boss.erebos.ErebosEffect;

/**
 * 场上效果的渲染器 —— <b>它什么都不画，但必须存在</b>。
 *
 * <h2>粒子不在这里</h2>
 * 激光、限制圈、审判柱、星痕、星门这些效果的画面，全部由
 * {@link ErebosEffect#tick()} 在<b>客户端 tick 阶段</b>撒粒子完成 ——
 * 不是在这里。
 *
 * <p>为什么放在 tick 而不是 render：`render` 每帧可能被调用 1~3 次
 * （阴影、多视角、重渲染），在里面撒粒子会让数量随帧率浮动 ——
 * 帧率高的时候粒子翻倍、帧率低的时候又不够。固定在 tick 里，
 * 粒子密度就与帧率解耦了。
 *
 * <h2>那为什么还要注册这个渲染器</h2>
 * 因为<b>不注册会崩</b>。原版渲染循环对每个可见实体查一次渲染器表，
 * 查不到就 {@code NullPointerException}，而且崩在渲染线程、
 * 在别人的调用栈里，catch 不住。所以这里给一个
 * {@code shouldRender → false} 的空壳，把查询挡在最前面。
 */
public class ErebosEffectRenderer extends EntityRenderer<ErebosEffect> {

    private static final ResourceLocation PLACEHOLDER =
            new ResourceLocation(Medals.MODID, "textures/entity/erebos_bolt.png");

    public ErebosEffectRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(ErebosEffect entity, float entityYaw, float partialTick,
                       PoseStack pose, MultiBufferSource buffer, int packedLight) {
        // 故意为空：视觉由 ErebosEffect#clientParticles 负责
    }

    @Override
    public boolean shouldRender(ErebosEffect entity, Frustum frustum,
                                double camX, double camY, double camZ) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(ErebosEffect entity) {
        return PLACEHOLDER;
    }
}
