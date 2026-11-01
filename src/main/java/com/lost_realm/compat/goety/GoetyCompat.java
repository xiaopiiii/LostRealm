package com.lost_realm.compat.goety;

import net.minecraftforge.fml.ModList;

/**
 * Goety 加载检测与桥接实例管理。
 *
 * <h2>类加载隔离</h2>
 * {@link GoetyBridgeImpl} 引用了 {@code com.Polarice3.Goety.*} 的类。
 * 如果在本类中直接 {@code new GoetyBridgeImpl()},即使 Goety 未安装,
 * JVM 也可能在解析常量池时提前加载该类并抛 {@link NoClassDefFoundError}。
 *
 * <p>因此真实实现放在独立的静态内部类 {@link Holder} 里,
 * 只有当 {@code goety} 确实加载时才触发 {@code Holder} 的初始化,
 * 从而保证未装 Goety 的环境永远不会触碰 Goety 的类。
 */
public final class GoetyCompat {

    public static final String MODID = "goety";

    /** 是否已加载 Goety(纯字符串判断,不触碰 Goety 类)。 */
    private static Boolean loaded;

    public static boolean isLoaded() {
        if (loaded == null) {
            try {
                loaded = ModList.get() != null && ModList.get().isLoaded(MODID);
            } catch (Throwable t) {
                loaded = false;
            }
        }
        return loaded;
    }

    /**
     * 取得桥接实例。
     *
     * <p>Goety 未加载、或桥接初始化失败时返回 {@code null}。
     * 调用方必须处理 {@code null}(所有法师技能均以此为「软禁用」开关)。
     */
    public static GoetyBridge bridge() {
        if (!isLoaded()) {
            return null;
        }
        try {
            return Holder.INSTANCE;
        } catch (Throwable t) {
            // Goety 版本不匹配 / 结构变化 —— 安全降级为「无桥接」
            return null;
        }
    }

    /** 持有真实实现的静态内部类(隔离类加载)。 */
    private static final class Holder {
        static final GoetyBridge INSTANCE = new GoetyBridgeImpl();
    }

    private GoetyCompat() {}
}
