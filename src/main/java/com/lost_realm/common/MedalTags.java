package com.lost_realm.common;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import com.lost_realm.Medals;

/**
 * 本模组使用的标签。
 *
 * <p>{@link #NEUTRAL_MOBS} 用于标记「中立生物」,命中的生物其熟练度收益减半。
 * 可通过数据包在 {@code data/lost_realm/tags/entity_types/neutral_mobs.json} 中自定义。
 */
public final class MedalTags {

    /** 中立生物标签,收益减半。 */
    public static final TagKey<EntityType<?>> NEUTRAL_MOBS =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(Medals.MODID, "neutral_mobs"));

    private MedalTags() {}
}
