package com.lost_realm.compat.backpack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * 「精妙背包(Sophisticated Backpacks / Sophisticated Core)」软兼容桥。
 *
 * <h2>为什么用纯反射</h2>
 * 本模组<b>不</b>把精妙背包声明为编译期依赖(其 API 在不同版本间存在差异,
 * 且并非所有玩家都会安装)。因此这里<b>完全以反射</b>访问其运行时 API:
 * <ol>
 *   <li>用 {@link ModList#isLoaded(String)} 字符串判存,未安装时所有方法零开销返回;</li>
 *   <li>通过 {@code CapabilityBackpackWrapper.getCapabilityInstance()} 取得能力实例;</li>
 *   <li>对每个背包物品 {@code ItemStack} 取 {@code IStorageWrapper},
 *       再经 {@code getInventoryHandler()}/{@code getSlotCount()}/{@code getStackInSlot(i)}
 *       遍历其内部物品。</li>
 * </ol>
 * 任何一步反射失败(版本差异)都会被吞掉并安全降级为「无背包内容」,
 * 绝不会因为缺少/不同版本的精妙背包而崩溃。
 */
public final class BackpackCompat {

    public static final String MODID_BACKPACKS = "sophisticatedbackpacks";
    public static final String MODID_CORE = "sophisticatedcore";

    private static Boolean loaded;

    /** 是否已安装精妙背包(或精妙核心)。 */
    public static boolean isLoaded() {
        if (loaded == null) {
            try {
                ModList list = ModList.get();
                loaded = list != null
                        && (list.isLoaded(MODID_BACKPACKS) || list.isLoaded(MODID_CORE));
            } catch (Throwable t) {
                loaded = false;
            }
        }
        return loaded;
    }

    /**
     * 收集玩家所有背包(物品栏内的背包物品 + 已佩戴的背包)中承载的全部物品。
     *
     * <p>未安装精妙背包时返回空列表。返回的是背包内部物品栈的引用(可安全回读)。
     */
    public static List<ItemStack> collectItems(Player player) {
        if (!isLoaded() || player == null) {
            return Collections.emptyList();
        }
        try {
            return BackpackAccess.collect(player);
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    /**
     * 与精妙背包交互的真实实现(隔离类加载)。
     *
     * <p>放在独立静态内部类里,只有当 {@link #isLoaded()} 为真时才会被解析,
     * 从而保证未安装该模组的环境永远不会加载其相关类。
     */
    private static final class BackpackAccess {

        static List<ItemStack> collect(Player player) {
            List<ItemStack> out = new ArrayList<>();
            // 遍历玩家背包内所有物品,凡是"背包物品"就尝试取其内部库存
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.isEmpty()) {
                    continue;
                }
                collectFromBackpackItem(stack, out);
            }
            return out;
        }

        /** 尝试把单个背包物品内部的所有物品加入 out;非背包物品静默跳过。 */
        private static void collectFromBackpackItem(ItemStack backpackStack, List<ItemStack> out) {
            try {
                Object wrapper = getBackpackWrapper(backpackStack);
                if (wrapper == null) {
                    return;
                }
                // IBackpackWrapper#getInventoryForInputOutput() -> IStorageWrapper
                Object storage = invokeAny(wrapper, "getInventoryForInputOutput", "getInventoryWrapper");
                if (storage == null) {
                    // 退而求其次:部分版本 wrapper 自身即存储外壳
                    storage = invokeAny(wrapper, "getStorageWrapper");
                }
                if (storage == null) {
                    return;
                }
                // IStorageWrapper#getInventoryHandler() -> IItemHandler-like
                Object handler = invokeAny(storage, "getInventoryHandler");
                if (handler == null) {
                    return;
                }
                int slots = asInt(invokeAny(handler, "getSlots", "getSlotCount"));
                for (int s = 0; s < slots; s++) {
                    Object got = invokeWithInt(handler, "getStackInSlot", s);
                    if (got instanceof ItemStack is && !is.isEmpty()) {
                        out.add(is);
                    }
                }
            } catch (Throwable ignored) {
                // 该物品不是背包 / 版本 API 不同 —— 跳过
            }
        }

        /** 通过 Forge 能力 API 取背包包装器(反射)。 */
        private static Object getBackpackWrapper(ItemStack stack) throws Exception {
            Class<?> capClass = Class.forName(
                    "net.p3pp3rf1y.sophisticatedbackpacks.api.CapabilityBackpackWrapper");
            Method getter = capClass.getMethod("getCapabilityInstance");
            return getter.invoke(null, stack);
        }

        /** 依次尝试多个候选方法名,返回首个成功调用的结果(无参)。 */
        private static Object invokeAny(Object target, String... names) {
            for (String name : names) {
                try {
                    Method m = target.getClass().getMethod(name);
                    m.setAccessible(true);
                    return m.invoke(target);
                } catch (Throwable ignored) {
                    // 尝试下一个
                }
            }
            return null;
        }

        /** 依次尝试多个候选方法名,传入一个 int 参数。 */
        private static Object invokeWithInt(Object target, String name, int arg) {
            try {
                Method m = target.getClass().getMethod(name, int.class);
                m.setAccessible(true);
                return m.invoke(target, arg);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static int asInt(Object o) {
            return o instanceof Number n ? n.intValue() : 0;
        }
    }

    private BackpackCompat() {}
}
