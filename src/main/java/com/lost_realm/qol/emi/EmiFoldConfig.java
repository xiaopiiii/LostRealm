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
 * 的那个分组，把我们的行加进它的 {@code children}，再往 {@code list} 里追加一条，
 * 渲染出来的位置、样式、鼠标悬停就和原版那几行一模一样。
 *
 * <h2>为什么要反射</h2>
 * {@code ListWidget.children} 是私有字段，而且 EMI 的 {@code m_6702_()} 在不同
 * 映射下名字不一样。直接读字段名 {@code children}（EMI 自己的字段名，不参与
 * MC 映射）最稳，也不会因为映射版本不同而炸。
 *
 * <p>整段包在 try/catch 里：万一 EMI 改了内部结构，这里静默失败，
 * 外面会自动退回"左下角手画那个开关"的老方案，功能不会跟着一起没。
 */
public final class EmiFoldConfig {

    /** 已经成功把原生行塞进去了 —— 外面就不用再画左下角那个了。 */
    private static boolean installed;

    private EmiFoldConfig() {}

    public static boolean installed() {
        return installed;
    }

    /** 由 {@code EmiConfigScreenMixin} 在配置界面建好之后调用。 */
    public static void install(ConfigScreen screen, ListWidget list) {
        if (screen == null || list == null) {
            return;
        }
        try {
            List<Object> entries = entriesOf(list);
            if (entries == null) {
                return;
            }
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
            for (ConfigEntryWidget c : dev.children) {
                if (c instanceof FoldEditEntry) {
                    installed = true;          // 已经加过了（比如窗口缩放重进 init）
                    return;
                }
            }

            FoldEditEntry entry = new FoldEditEntry(screen);
            entry.parentGroups = List.of(dev);
            entry.endGroup = true;
            // 原来的最后一项不再是"组尾"，分隔线挪到我们这一行下面
            if (!dev.children.isEmpty()) {
                try {
                    dev.children.get(dev.children.size() - 1).endGroup = false;
                } catch (Throwable ignored) {
                    // 列表不可写就只是少一条分隔线
                }
            }
            try {
                dev.children.add(entry);
            } catch (Throwable ignored) {
                // 分组列表不可写也不能让它整行消失 —— 下面照样塞进 list
            }
            list.addEntry(entry);
            installed = true;
            Medals.LOGGER.info("[Lost Realm] 折叠编辑已挂进 EMI 配置界面（开发分组）");
        } catch (Throwable t) {
            Medals.LOGGER.warn("[Lost Realm] 往 EMI 配置界面加设置行失败，退回左下角开关", t);
        }
    }

    /** 读 {@code ListWidget.children}（私有字段，名字是 EMI 自己的，不参与 MC 映射）。 */
    @SuppressWarnings("unchecked")
    private static List<Object> entriesOf(ListWidget list) {
        Field f = null;
        Class<?> c = list.getClass();
        while (c != null && f == null) {
            try {
                f = c.getDeclaredField("children");
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        if (f == null) {
            return null;
        }
        f.setAccessible(true);
        try {
            Object v = f.get(list);
            return v instanceof List<?> l ? (List<Object>) l : null;
        } catch (IllegalAccessException e) {
            return null;
        }
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
