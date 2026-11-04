package com.lost_realm.client.boss;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import com.lost_realm.Medals;
import com.lost_realm.boss.udunos.UdunosPart;

/**
 * 碰撞部件的渲染器 —— <b>它存在的唯一目的是"什么都不画"</b>。
 *
 * <h2>为什么"不渲染"也必须注册一个渲染器</h2>
 * 这不是可选优化，而是<b>必须做的事</b>。原版在渲染循环里对每个可见实体
 * 查一次渲染器表，查不到就直接抛 NPE：
 * <pre>
 *   NullPointerException: Cannot invoke "EntityRenderer.shouldRender(...)"
 *     because "entityrenderer" is null
 * </pre>
 * 而且它崩在渲染线程、在别人的调用栈里，<b>try-catch 兜不住</b>。
 * 本模组的 {@code EntityRendererRegistry} 就是当年栽在这上面才建的。
 *
 * <p>所以：给 40 个纯判定部件注册一个"永远返回 false"的渲染器，
 * 把 {@code shouldRender} 直接挡在最前面，连后续的遍历都省掉。
 */
public class UdunosPartRenderer extends EntityRenderer<UdunosPart> {

    /** 占位贴图。永远用不到，但接口要求返回一个非 null 值。 */
    private static final ResourceLocation PLACEHOLDER =
            new ResourceLocation(Medals.MODID, "textures/entity/udunos_body.png");

    public UdunosPartRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(UdunosPart entity, float entityYaw, float partialTick,
                       PoseStack pose, MultiBufferSource buffer, int packedLight) {
        // 故意为空：部件的视觉表现完全由 UdunosRenderer 负责
    }

    @Override
    public boolean shouldRender(UdunosPart entity, Frustum frustum,
                                double camX, double camY, double camZ) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(UdunosPart entity) {
        return PLACEHOLDER;
    }
}
