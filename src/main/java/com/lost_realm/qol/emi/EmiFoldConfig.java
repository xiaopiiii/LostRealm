package com.lost_realm.qol.emi;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

import dev.emi.emi.screen.ConfigScreen;
import dev.emi.emi.screen.widget.config.BooleanWidget;
import dev.emi.emi.screen.widget.config.ConfigSearch;
import dev.emi.emi.screen.widget.config.GroupNameWidget;
import dev.emi.emi.screen.widget.config.ListWidget;

import com.lost_realm.Medals;

/**
 * 把本模组的几个开关做成 EMI 配置界面里的<b>正经设置行</b>。
 *
 * <h2>为什么不能自己画</h2>
 * 手画的方块不在列表里：不参与搜索、不跟着分组折叠、不受"放弃变更 / 完成"约束，
 * 看着就像外挂上去的。要做成原生的，只能往 EMI 自己的 {@code ConfigScreen.list}
 * 里塞 {@link BooleanWidget}。
 *
 * <h2>塞在哪儿</h2>
 * EMI 的配置项是按"分组标题 {@code GroupNameWidget} + 若干 {@code ConfigEntryWidget}"
 * 拍平的，子项的 {@code parentGroups} 指向所属分组。找到对应 id 的分组
 * （{@code dev} = 开发，{@code general} = 基础），把行插在分组标题正下方第一项。
 *
 * <h2>两个坑（都踩过）</h2>
 * <ol>
 *   <li>{@code BooleanWidget} 的 {@code Supplier<String>} 参数<b>不是</b>本项关键词，
 *       而是<b>搜索框里的查询词</b>：{@code isVisible()} 做的是
 *       {@code 本项文本.contains(查询词)}。传错的话空搜索框下
 *       {@code "".contains("关键词")} 恒为 false，整行永远不渲染。</li>
 *   <li>插入时机必须在列表填完之后。放在 {@code addJumpButtons} 里插会跑到列表最顶上，
 *       所以改成每帧渲染前检查一次、缺了哪行补哪行。</li>
 * </ol>
 *
 * <p>整段包在 try/catch 里：EMI 改内部结构就静默失败，
 * 折叠那边会自动退回"左下角手画开关"的老方案。
 */
public final class EmiFoldConfig {

    /** 一行原生设置。 */
    private record Row(String groupId, String labelKey, String tooltipKey, String key,
                       Supplier<Boolean> getter, java.util.function.Consumer<Boolean> setter) {
    }

    private static final List<Row> ROWS = new ArrayList<>();

    static {
        ROWS.add(new Row("dev", "config.emi.lostrealm_fold_edit",
                "config.emi.tooltip.lostrealm_fold_edit", "fold_edit",
                EmiFoldStore::isEditEnabled,
                v -> {
                    EmiFoldStore.setEditEnabled(Boolean.TRUE.equals(v));
                    EmiFold.editToggled();
                }));
        ROWS.add(new Row("general", "config.emi.lostrealm_recipe_pages",
                "config.emi.tooltip.lostrealm_recipe_pages", "recipe_pages",
                EmiFoldStore::isShowRecipePages,
                v -> EmiFoldStore.setShowRecipePages(Boolean.TRUE.equals(v))));
    }

    private static boolean foldRowPresent;

    private EmiFoldConfig() {}

    /** 折叠编辑那一行在位吗（不在就画左下角兜底开关）。 */
    public static boolean installed() {
        return foldRowPresent;
    }

    /** 每帧调用一次，缺哪行补哪行。 */
    public static void ensure(ConfigScreen screen) {
        if (screen == null) {
            return;
        }
        try {
            ListWidget list = listOf(screen);
            if (list == null) {
                return;
            }
            List<Object> entries = entriesOf(list);
            if (entries == null) {
                return;
            }
            for (Row row : ROWS) {
                if (!containsKey(entries, row.key())) {
                    install(screen, list, entries, row);
                }
            }
            foldRowPresent = containsKey(entries, "fold_edit");
        } catch (Throwable t) {
            Medals.LOGGER.debug("[Lost Realm] 刷新 EMI 配置行失败", t);
        }
    }

    private static boolean containsKey(List<Object> entries, String key) {
        for (Object o : entries) {
            if (o instanceof RowEntry e && key.equals(e.key)) {
                return true;
            }
        }
        return false;
    }

    private static void install(ConfigScreen screen, ListWidget list, List<Object> entries, Row row) {
        try {
            GroupNameWidget group = null;
            for (Object o : entries) {
                if (o instanceof GroupNameWidget g && row.groupId().equals(g.id)) {
                    group = g;
                    break;
                }
            }
            if (group == null) {
                return;
            }
            RowEntry entry = new RowEntry(screen, row);
            entry.parentGroups = List.of(group);
            int at = entries.indexOf(group);
            at = at < 0 ? entries.size() : at + 1;
            try {
                group.children.add(0, entry);
            } catch (Throwable ignored) {
                // 分组列表不可写也不能让整行消失 —— 下面照样插进 list
            }
            try {
                entries.add(at, entry);
                // parentList 必须指回去：ConfigEntryWidget.render 里要读
                // parentList.client（屏幕宽高），漏了这一句就是
                // "Cannot read field client because this.parentList is null"
                entry.parentList = list;
            } catch (Throwable t) {
                Medals.LOGGER.debug("[Lost Realm] 直接插入配置行失败，退回 addEntry", t);
                try {
                    list.addEntry(entry);
                } catch (Throwable ignored) {
                    return;
                }
            }
            Medals.LOGGER.info("[Lost Realm] 配置行「{}」已挂进 EMI 配置界面（{} 分组第 {} 项）",
                    row.key(), row.groupId(), at);
        } catch (Throwable t) {
            Medals.LOGGER.warn("[Lost Realm] 往 EMI 配置界面加设置行失败", t);
        }
    }

    /**
     * 配置界面搜索框的当前内容。
     *
     * <p>见类注释第 1 个坑：这个 Supplier 必须返回<b>查询词</b>，不是本项关键词。
     */
    private static Supplier<String> queryOf(ConfigScreen screen) {
        return new Supplier<>() {
            private ConfigSearch box;
            private boolean resolved;

            @Override
            public String get() {
                if (!resolved) {
                    resolved = true;
                    try {
                        Field f = field(screen.getClass(), "search");
                        Object v = f == null ? null : f.get(screen);
                        box = v instanceof ConfigSearch cs ? cs : null;
                    } catch (Throwable ignored) {
                        box = null;
                    }
                }
                return box == null ? "" : box.getSearch();
            }
        };
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

    /** EMI 原生的"开/关"按钮行，只是读写我们自己的开关。 */
    private static final class RowEntry extends BooleanWidget {

        /** 用来认领：列表重建后判断这一行还在不在。 */
        private final String key;

        RowEntry(ConfigScreen screen, Row row) {
            super(Component.translatable(row.labelKey()),
                    tooltip(row.tooltipKey()),
                    queryOf(screen),
                    screen.new Mutator<Boolean>() {
                        @Override
                        protected Boolean getValue() {
                            return row.getter().get();
                        }

                        @Override
                        protected void setValue(Boolean value) {
                            row.setter().accept(value);
                        }
                    });
            this.key = row.key();
        }

        /**
         * 兜底：这一行的绘制/布局任何异常都吞掉。
         *
         * <p>它是插进 EMI 自己的列表里的，一旦抛异常就是<b>整个配置界面直接崩</b>
         * （已经因为少赋一次 {@code parentList} 崩过一次）。宁可少显示一行，
         * 也不能把玩家踢出游戏。
         */
        @Override
        public void render(net.minecraft.client.gui.GuiGraphics g, int index, int y, int x,
                           int width, int height, int mouseX, int mouseY, boolean hovered,
                           float delta) {
            try {
                super.render(g, index, y, x, width, height, mouseX, mouseY, hovered, delta);
            } catch (Throwable ignored) {
                // 静默
            }
        }

        @Override
        public void update(int x, int y, int width, int height) {
            try {
                super.update(x, y, width, height);
            } catch (Throwable ignored) {
                // 静默
            }
        }

        private static List<ClientTooltipComponent> tooltip(String key) {
            try {
                return List.of(ClientTooltipComponent.create(
                        Component.translatable(key).getVisualOrderText()));
            } catch (Throwable t) {
                return List.of();
            }
        }
    }
}
