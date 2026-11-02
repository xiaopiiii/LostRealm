package com.lost_realm.client;

import com.lost_realm.Medals;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * 客户端初始化。
 *
 * <p>接入「配置屏幕模组」Configured:
 * Configured 在 1.20.1 通过 {@code IConfigScreenFactory} 扩展点提供配置屏幕,
 * 本模组对它的调用完全以字符串 + 反射完成(见 {@link #tryRegisterConfigured()}),
 * 因此<b>不需要编译期依赖</b>;未安装 Configured 时会静默跳过,不影响模组运行。
 */
@Mod.EventBusSubscriber(modid = Medals.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class MedalsClient {

    public static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(MedalsClient::clientSetup);
        // 遗落境维度的动态宇宙天空（星云 / 星体 / 星环 / 星光）
        FMLJavaModLoadingContext.get().getModEventBus().addListener(MedalsClient::registerSky);
        // 客户端自检命令 /lrdebug erebos —— RegisterClientCommandsEvent 在 FORGE 总线
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                com.lost_realm.client.ErebosDebugCommand::register);
        // Boss 战 BGM：每 tick 检查附近有没有厄瑞玻斯，有就淡入、没有就淡出。
        // 纯客户端逻辑，服务端不需要知道音乐这件事。
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                ErebosMusicController::onClientTick);
    }

    private static void registerSky(
            net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent event) {
        event.register(com.lost_realm.client.sky.ErebosSkyEffects.ID,
                new com.lost_realm.client.sky.ErebosSkyEffects());
    }

    private static void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // ① 注册本项目自研的可视化配置界面(Forge 原生扩展点)
            registerNativeConfigScreen();

            // ② 已安装 Configured 时也登记一份,便于从 Configured 的入口进入
            if (ModList.get().isLoaded("configured")) {
                tryRegisterConfigured();
            }
        });
    }

    /**
     * 用 Forge 原生扩展点注册配置界面。
     *
     * <p>效果:在「模组列表 → Lost Realm → 配置」点进去时,
     * 打开的是本项目自研的 {@link com.lost_realm.client.config.ConfigScreen},
     * 而不是 Forge 自带的纯文字列表。
     *
     * <p>用的是 1.20.1 Forge 的标准做法:
     * <pre>
     *   ModLoadingContext.get().registerExtensionPoint(
     *       ConfigScreenHandler.ConfigScreenFactory.class,
     *       () -&gt; new ConfigScreenHandler.ConfigScreenFactory(
     *               (mc, parent) -&gt; new ConfigScreen(parent)));
     * </pre>
     * 该扩展点<b>只能注册一次</b>,重复注册会抛异常 ——
     * 因此整个模组只在此处调用。
     */
    private static void registerNativeConfigScreen() {
        try {
            net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(
                    net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                            (mc, parent) ->
                                    new com.lost_realm.client.config.ConfigScreen(parent)));
            Medals.LOGGER.info("[Lost Realm] 已注册可视化配置界面(模组列表入口)。");
        } catch (Throwable t) {
            Medals.LOGGER.warn("[Lost Realm] 可视化配置界面注册失败(不影响运行): {}", t.toString());
        }
    }

    /**
     * 通过反射注册 Configured 的配置屏幕工厂。
     *
     * <p>Configured 的扩展点签名(1.20.1):
     * <pre>
     *   com.mrcrayfish.configured.api.ConfigScreenFactory.register(
     *       net.minecraftforge.fml.config.ModConfig config,
     *       com.mrcrayfish.configured.api.IConfigScreenFactory factory)
     * </pre>
     * 由于 Configured 已在运行期提供默认屏幕,这里只需把本模组注册进它的索引,
     * 让其自动为本模组的 COMMON / CLIENT 配置生成界面。
     */
    private static void tryRegisterConfigured() {
        try {
            Class<?> apiClass = Class.forName("com.mrcrayfish.configured.api.ConfigScreenFactory");
            Class<?> factoryClass = Class.forName("com.mrcrayfish.configured.api.IConfigScreenFactory");

            Object factory = java.lang.reflect.Proxy.newProxyInstance(
                    MedalsClient.class.getClassLoader(),
                    new Class<?>[] { factoryClass },
                    (proxy, method, args) -> {
                        if ("createScreen".equals(method.getName())) {
                            // 返回 null:交由 Configured 使用内置的编辑屏幕
                            return null;
                        }
                        if ("getMod".equals(method.getName())) {
                            return ModList.get().getModContainerById(Medals.MODID).orElse(null);
                        }
                        return null;
                    });

            // 遍历本模组已注册的 ModConfig
            for (net.minecraftforge.fml.config.ModConfig cfg : com.lost_realm.config.MedalsConfigLoader.configs()) {
                apiClass.getMethod("register",
                                net.minecraftforge.fml.config.ModConfig.class, factoryClass)
                        .invoke(null, cfg, factory);
            }
            Medals.LOGGER.info("[Lost Realm] 已向 Configured 注册配置屏幕。");
        } catch (Throwable t) {
            Medals.LOGGER.warn("[Lost Realm] Configured 配置屏幕注册失败(不影响运行): {}", t.toString());
        }
    }

    private MedalsClient() {}
}
