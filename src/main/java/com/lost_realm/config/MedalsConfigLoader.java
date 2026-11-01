package com.lost_realm.config;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.fml.config.ModConfig;

/**
 * 已注册的 ModConfig 收集器。
 *
 * <p>供 Configured 配置屏幕接入时枚举本模组的配置文件使用。
 */
public final class MedalsConfigLoader {

    private static final List<ModConfig> CONFIGS = new ArrayList<>();

    public static void register(ModConfig config) {
        CONFIGS.add(config);
    }

    public static List<ModConfig> configs() {
        return CONFIGS;
    }

    private MedalsConfigLoader() {}
}
