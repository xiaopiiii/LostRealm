package com.lost_realm.client.boss;

import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.model.GeoModel;

import com.lost_realm.Medals;
import com.lost_realm.boss.entity.WardenSentinel;

/**
 * 遗落哨卫的 GeckoLib 模型 —— 只用人形几何(第一形态)。
 *
 * <p>哨卫是波次里刷的小怪,造型上<b>刻意保持"未腐化"</b>:
 * 没有触手、没有骨刺,只有基础人形 —— 与遗落之主的形态递进形成对比,
 * 也让玩家一眼能分清「小怪」与「本体」。
 *
 * <p>复用 {@code warden_form1} 几何与动画,另配一张贴图。
 */
public class WardenSentinelModel extends GeoModel<WardenSentinel> {

    private static final ResourceLocation MODEL =
            new ResourceLocation(Medals.MODID, "geo/warden_form1.geo.json");
    private static final ResourceLocation ANIM =
            new ResourceLocation(Medals.MODID, "animations/warden_form1.animation.json");
    private static final ResourceLocation TEX =
            new ResourceLocation(Medals.MODID, "textures/entity/warden_sentinel.png");

    @Override
    public ResourceLocation getModelResource(WardenSentinel animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(WardenSentinel animatable) {
        return TEX;
    }

    @Override
    public ResourceLocation getAnimationResource(WardenSentinel animatable) {
        return ANIM;
    }

    @Override
    public boolean crashIfBoneMissing() {
        return false;
    }
}
