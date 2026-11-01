package com.lost_realm.client.boss;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

import com.lost_realm.Medals;
import com.lost_realm.boss.entity.WardenSentinel;

/** 遗落哨卫渲染器 —— 与 Boss 共用管线,只换模型与贴图。 */
public class WardenSentinelRenderer extends GeoEntityRenderer<WardenSentinel> {

    public WardenSentinelRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new WardenSentinelModel());
        this.shadowRadius = 0.5F;
        // 自发光(眼睛) —— 贴图旁边放同名的 *_glowmask.png 即可
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public ResourceLocation getTextureLocation(WardenSentinel entity) {
        return new ResourceLocation(Medals.MODID, "textures/entity/warden_sentinel.png");
    }
}
