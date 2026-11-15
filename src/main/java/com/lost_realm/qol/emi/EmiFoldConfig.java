package com.lost_realm.qol.emi;

import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

import dev.emi.emi.screen.ConfigScreen;
import dev.emi.emi.screen.widget.config.BooleanWidget;
import dev.emi.emi.screen.widget.config.ConfigEntryWidget;
import dev.emi.emi.screen.widget.config.GroupNameWidget;
import dev.emi.emi.screen.widget.config.ListWidget;

import com.lost_realm.Medals;

/**
 * 把「折叠编辑」做成 EMI 配置界面里的<b>一行正经设置</b>，而不是自己画在左下角。
 *
 * <h2>为什么不能只画一个</h2>
 * 左下角那个手画的小方块长得就不像配置项：位置不在列表里、不参与搜索、
 * 不跟着分组折叠、也不受"放弃变更 / 完成"那一套约束，看着就像外挂上去的。
 * 要做成原生的，就得往 EMI 自己的 {@code ConfigScreen.list} 里塞一个
 * {@link BooleanWidget}。
 *
 * <h2>塞在哪儿</h2>
 * EMI 的配置项是按"分组名 + 子项"拍平的：{@code GroupNameWidget} 是分组标题，
 * 后面若干 {@code ConfigEntryWidget} 是它的子项，子项的 {@code parentGroups}
 * 指向所属分组（决定缩进、折叠时是否隐藏）。所以只要找到 id 为 {@code dev}
 * 的那个分组，把我们的行插在它最后一个子项后面，渲染出来的位置、样式、
 * 鼠标悬停就和原版那几行一模一样。
 *
 * <h2>为什么在渲染时插，而不是在 init 里</h2>
 * 一开始是混进 {@code addJumpButtons} 里插的，结果那一行跑到了列表最顶上
 * （那时候列表还没填完，等于插在开头），翻到「开发」当然看不见。
 * 改成每帧渲染前检查一次：这时 {@code init()} 早就跑完、列表也齐了，
 * 插入位置一定落在分组末尾。用列表对象本身去重，窗口缩放重建列表也能自动补上。
 *
 * <h2>为什么要反射</h2>
 * {@code ListWidget.children} 是私有字段，而且 {@code m_6702_()} 在不同映射下
 * 名字不一样。直接读字段名 {@code children}（EMI 自己的字段名，不参与 MC 映射）最稳。
 *
 * <p>整段包在 try/catch 里：万一 EMI 改了内部结构，这里静默失败，
 * 外面会自动退回"左下角手画那个开关"的老方案，功能不会跟着一起没。
 */
public final class EmiFoldConfig {

    /** 已经成功插进去的那个列表实例 —— 顺便当"插过了"的标记。 */
    private static ListWidget lastList;
    private static boolean installed;

    private EmiFoldConfig() {}

    /** 当前列表里已经有我们那一行了吗（没有就画左下角兜底开关）。 */
    public static boolean installed() {
        return installed;
    }

    /**
     * 每帧调用一次，保证配置列表里有我们那一行。
     *
     * <p>不是靠一个"插过了"的布尔值就收工：EMI 在搜索框有内容时会重建条目列表
     * （同一个 ListWidget，children 换一批），那种情况下标记会骗人。
     * 每帧扫一眼列表里还有没有那一行最实在，代价是几十次 instanceof。
     */
    public static void ensure(ConfigScreen screen) {
        if (screen == null) {
            return;
        }
        ListWidget list = listOf(screen);
        if (list == null) {
            return;
        }
        List<Object> entries = entriesOf(list);
        if (entries == null) {
            return;
        }
        for (Object o : entries) {
            if (o instanceof FoldEditEntry) {
                lastList = list;
                installed = true;
                return;
            }
        }
        lastList = list;
        installed = false;
        install(screen, list, entries);
    }

    private static void install(ConfigScreen screen, ListWidget list, List<Object> entries) {
        try {
            GroupNameWidget dev = null;
            for (Object o : entries) {
                if (o instanceof GroupNameWidget g && "dev".equals(g.id)) {
                    dev = g;
                    break;
                }
            }
            if (dev == null) {
                return;
            }

            FoldEditEntry entry = new FoldEditEntry(screen);
            entry.parentGroups = List.of(dev);
            entry.endGroup = false;

            // 放在「开发」分组的第一项：跟在分组标题正下方，一眼就能看到。
            // 挂到组尾的话，这一行会落在列表最底部（开发是最后一组），
            // 不进到最底下一行是看不见的 —— 上一次就是这么"消失"的。
            int at = entries.indexOf(dev);
            at = at < 0 ? entries.size() : at + 1;
            try {
                dev.children.add(0, entry);
            } catch (Throwable ignored) {
                // 分组列表不可写也不能让整行消失 —— 下面照样插进 list
            }
            try {
                entries.add(at, entry);
                entry.parentList = list;
            } catch (Throwable t) {
                list.addEntry(entry);
            }
            installed = true;
            Medals.LOGGER.info("[Lost Realm] 折叠编辑已挂进 EMI 配置界面（开发分组第 {} 项，共 {} 项）",
                    at, entries.size());
        } catch (Throwable t) {
            Medals.LOGGER.warn("[Lost Realm] 往 EMI 配置界面加设置行失败，退回左下角开关", t);
        }
    }

    /** {@code ConfigScreen.list}（EMI 自己的字段名，不参与 MC 映射）。 */
    private static ListWidget listOf(ConfigScreen screen) {
        try {
            Field f = field(screen.getClass(), "list");
            return f == null ? null : (ListWidget) f.get(screen);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 读 {@code ListWidget.children}（私有字段）。 */
    @SuppressWarnings("unchecked")
    private static List<Object> entriesOf(ListWidget list) {
        try {
            Field f = field(list.getClass(), "children");
            if (f == null) {
                return null;
            }
            Object v = f.get(list);
            return v instanceof List<?> l ? (List<Object>) l : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Field field(Class<?> type, String name) {
        Class<?> c = type;
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    /** 就是 EMI 原生的那个"开/关"按钮行，只是读写我们自己的开关。 */
    private static final class FoldEditEntry extends BooleanWidget {

        FoldEditEntry(ConfigScreen screen) {
            super(Component.translatable("config.emi.lostrealm_fold_edit"),
                    tooltip(),
                    () -> "遗落境 lost realm 折叠编辑 fold edit",
                    screen.new Mutator<Boolean>() {
                        @Override
                        protected Boolean getValue() {
                            return EmiFoldStore.isEditEnabled();
                        }

                        @Override
                        protected void setValue(Boolean value) {
                            EmiFoldStore.setEditEnabled(Boolean.TRUE.equals(value));
                            EmiFold.editToggled();
                        }
                    });
        }

        private static List<ClientTooltipComponent> tooltip() {
            try {
                return List.of(ClientTooltipComponent.create(
                        Component.translatable("config.emi.tooltip.lostrealm_fold_edit")
                                .getVisualOrderText()));
            } catch (Throwable t) {
                return List.of();
            }
        }
    }
}
