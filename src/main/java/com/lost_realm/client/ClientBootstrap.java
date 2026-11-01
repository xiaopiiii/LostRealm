package com.lost_realm.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * 客户端启动桥接。
 *
 * <p>通过运行时环境判断,避免在服务端加载任何客户端专属类
 * (HUD、按键绑定等),从而杜绝 {@code NoClassDefFoundError}。
 */
public final class ClientBootstrap {

    /** 由主类调用;仅客户端环境下才会真正加载 MedalsClient。 */
    public static void bootstrap() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientInit.run();
        }
    }

    /**
     * 独立的内部类:只有当 {@link #bootstrap()} 确认为客户端时才会被类加载器解析,
     * 保证服务端不会触碰客户端类。
     */
    private static final class ClientInit {
        static void run() {
            MedalsClient.init();
        }
    }

    private ClientBootstrap() {}
}
