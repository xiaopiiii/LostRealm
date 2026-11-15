package com.lost_realm.qol.emi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.fml.loading.FMLPaths;

import dev.emi.emi.api.stack.EmiStack;

import com.lost_realm.Medals;

/**
 * <b>自定义折叠组的持久化</b>。
 *
 * <p>组完全由玩家自己定义（在 EMI 列表里 Ctrl+拖拽框选），不存在任何自动分类:
 * 前面十几轮试过注册名后缀、物品标签、显示名后缀、以及它们的组合，
 * 每一种都能被举出反例（{@code end_rod} / {@code blaze_rod} 同后缀但无关，
 * 标签又把导线粗细混在一起）。分类是语义问题，交给玩家一句话就解决了。
 *
 * <h2>文件格式</h2>
 * <pre>
 * {
 *   "_readme": ["..."],
 *   "groups": [
 *     { "name": "羊毛", "members": ["minecraft:white_wool", "minecraft:red_wool"] }
 *   ]
 * }
 * </pre>
 *
 * <p>成员标识用物品注册名。带 NBT 的变体（附魔书、药水）写成
 * {@code 注册名#十六进制哈希} —— 哈希由游戏生成，手改时一般只需要写注册名部分。
 *
 * <p>退出游戏后可以直接改这个文件，重新进入即生效；改了文件但没重启时，
 * 系统会按修改时间自动重读。
 */
public final class EmiFoldStore {

    /** 组名 → 成员 id（保持顺序，第一个就是收起时的代表格）。 */
    private static final Map<String, List<String>> GROUPS = new LinkedHashMap<>();
    /** 成员 id → 组名，用于 O(1) 反查。 */
    private static final Map<String, String> OWNER = new LinkedHashMap<>();

    /**
     * <b>折叠编辑开关</b>。只管"改分组"这类编辑动作（框选、成组、拆组、底部按钮、F6/F7/F8）；
     * 关掉之后已经折好的组照旧折叠、照旧能点角标展开收起 ——
     * 它不是"折叠功能开关"，关它不该把东西全摊开。
     */
    private static boolean editEnabled = true;

    /**
     * 框选方式：false = 必须按住 Ctrl+Shift 拖拽（默认，不易误触）；
     * true = 常开，直接左键拖拽就是框选，不用按住修饰键。
     * 按 F6 切换，存在同一个 json 里。
     */
    private static boolean stickySelect;

    /**
     * 配方界面顶上一排"配方类型"标签多了会分页，开关控制要不要在标签栏左端
     * 显示 {@code 当前页/总页数}。
     */
    private static boolean showRecipePages = true;

    private static long lastModified = -1L;
    private static boolean everLoaded;

    private EmiFoldStore() {}

    // ================================================================
    // 读写
    // ================================================================

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("lost_realm").resolve("emi_fold_groups.json");
    }

    /** 文件改过就重读。每帧调用也没问题（只 stat 一次）。 */
    public static void reloadIfChanged() {
        Path f = file();
        try {
            long m = Files.exists(f) ? Files.getLastModifiedTime(f).toMillis() : 0L;
            if (everLoaded && m == lastModified) {
                return;
            }
            lastModified = m;
            everLoaded = true;
            read(f);
        } catch (Throwable t) {
            Medals.LOGGER.warn("[Lost Realm] 读取 EMI 折叠组失败，本次按空处理", t);
        }
    }

    private static void read(Path f) throws IOException {
        GROUPS.clear();
        OWNER.clear();
        if (!Files.exists(f)) {
            Files.createDirectories(f.getParent());
            writeTemplate(f);
            return;
        }
        String text = Files.readString(f, StandardCharsets.UTF_8);
        if (text.isBlank()) {
            return;
        }
        JsonObject root = JsonParser.parseString(text).getAsJsonObject();
        editEnabled = !root.has("editEnabled") || root.get("editEnabled").getAsBoolean();
        stickySelect = root.has("stickySelect") && root.get("stickySelect").getAsBoolean();
        showRecipePages = !root.has("showRecipePages") || root.get("showRecipePages").getAsBoolean();
        JsonElement arr = root.get("groups");
        if (arr == null || !arr.isJsonArray()) {
            return;
        }
        for (JsonElement el : arr.getAsJsonArray()) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            String name = o.has("name") ? o.get("name").getAsString() : null;
            if (name == null || name.isBlank()) {
                continue;
            }
            List<String> members = new ArrayList<>();
            JsonElement ms = o.get("members");
            if (ms != null && ms.isJsonArray()) {
                for (JsonElement m : ms.getAsJsonArray()) {
                    if (!m.isJsonPrimitive()) {
                        continue;
                    }
                    String id = m.getAsString().trim();
                    if (id.isEmpty() || OWNER.containsKey(id)) {
                        continue;              // 同一个物品只归一个组
                    }
                    members.add(id);
                    OWNER.put(id, name);
                }
            }
            if (members.size() >= 2) {
                GROUPS.put(name, members);
            } else {
                for (String id : members) {
                    OWNER.remove(id);
                }
            }
        }
    }

    private static void writeTemplate(Path f) throws IOException {
        JsonObject root = new JsonObject();
        JsonArray readme = new JsonArray();
        readme.add("自定义折叠组：把同一类的物品写进一个组的 members 里，EMI 列表里就只占一格。");
        readme.add("members 写物品注册名，例如 minecraft:white_wool。");
        readme.add("带 NBT 的变体（附魔书、药水）写成 注册名#哈希，哈希由游戏自动生成。");
        readme.add("也可以不改文件：在游戏里按住 Ctrl 左键拖拽框选一批物品，自动成组。");
        readme.add("一个物品只能属于一个组；组不足 2 个成员会被忽略。");
        root.add("_readme", readme);
        root.addProperty("editEnabled", true);
        root.addProperty("stickySelect", false);
        root.addProperty("showRecipePages", true);
        root.add("groups", new JsonArray());
        Files.writeString(f, new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n",
                StandardCharsets.UTF_8);
    }

    /** 落盘。失败只记日志，不抛。 */
    public static void save() {
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            JsonObject root = new JsonObject();
            // 保留原有说明（若玩家删了也不要紧）
            JsonArray readme = new JsonArray();
            readme.add("自定义折叠组：把同一类的物品写进一个组的 members 里，EMI 列表里就只占一格。");
            readme.add("members 写物品注册名，例如 minecraft:white_wool。");
            readme.add("带 NBT 的变体（附魔书、药水）写成 注册名#哈希，哈希由游戏自动生成。");
            readme.add("也可以不改文件：在游戏里按住 Ctrl 左键拖拽框选一批物品，自动成组。");
            readme.add("一个物品只能属于一个组；组不足 2 个成员会被忽略。");
            root.add("_readme", readme);
            root.addProperty("editEnabled", editEnabled);
            root.addProperty("stickySelect", stickySelect);
            root.addProperty("showRecipePages", showRecipePages);

            JsonArray groups = new JsonArray();
            for (Map.Entry<String, List<String>> e : GROUPS.entrySet()) {
                JsonObject o = new JsonObject();
                o.addProperty("name", e.getKey());
                JsonArray ms = new JsonArray();
                for (String id : e.getValue()) {
                    ms.add(id);
                }
                o.add("members", ms);
                groups.add(o);
            }
            root.add("groups", groups);

            Files.writeString(f, new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n",
                    StandardCharsets.UTF_8);
            lastModified = Files.getLastModifiedTime(f).toMillis();
            everLoaded = true;
        } catch (Throwable t) {
            Medals.LOGGER.warn("[Lost Realm] 保存 EMI 折叠组失败", t);
        }
    }

    // ================================================================
    // 查询
    // ================================================================

    /** 物品/堆栈的稳定标识；拿不到返回 null。 */
    public static String idOf(EmiStack stack) {
        if (stack == null) {
            return null;
        }
        try {
            ItemStack is = stack.getItemStack();
            if (is == null || is.isEmpty()) {
                return null;
            }
            String base = BuiltInRegistries.ITEM.getKey(is.getItem()).toString();
            CompoundTag tag = is.getTag();
            if (tag == null || tag.isEmpty()) {
                return base;
            }
            return base + "#" + Integer.toHexString(tag.toString().hashCode());
        } catch (Throwable t) {
            return null;
        }
    }

    /** 折叠编辑开关是否开着。 */
    public static boolean isEditEnabled() {
        return editEnabled;
    }

    /** 切换折叠编辑开关并立刻落盘。返回切换后的状态。 */
    public static boolean toggleEdit() {
        editEnabled = !editEnabled;
        save();
        return editEnabled;
    }

    /** 配方类型分页的页数指示是否显示。 */
    public static boolean isShowRecipePages() {
        return showRecipePages;
    }

    /** 设定"显示配方类型页数"并立刻落盘。 */
    public static void setShowRecipePages(boolean value) {
        if (showRecipePages == value) {
            return;
        }
        showRecipePages = value;
        save();
    }

    /** 直接设定折叠编辑开关（EMI 配置界面的那一行用它）。 */
    public static void setEditEnabled(boolean value) {
        if (editEnabled == value) {
            return;
        }
        editEnabled = value;
        save();
    }

    /** 框选是否常开（不用按住修饰键）。 */
    public static boolean isStickySelect() {
        return stickySelect;
    }

    /** 切换常开框选并落盘。返回切换后的状态。 */
    public static boolean toggleStickySelect() {
        stickySelect = !stickySelect;
        save();
        return stickySelect;
    }

    /** 该成员属于哪个组；没有返回 null。 */
    public static String groupOf(String id) {
        return id == null ? null : OWNER.get(id);
    }

    public static List<String> membersOf(String group) {
        List<String> m = GROUPS.get(group);
        return m == null ? List.of() : m;
    }

    public static Collection<String> groupNames() {
        return GROUPS.keySet();
    }

    public static boolean isEmpty() {
        return GROUPS.isEmpty();
    }

    public static int size() {
        return GROUPS.size();
    }

    // ================================================================
    // 修改
    // ================================================================

    /**
     * 新建/覆盖一个组。传入的成员会先从它们原来所属的组里摘掉，
     * 保证"一个物品只属于一个组"。
     */
    public static String put(String name, Collection<String> ids) {
        List<String> clean = new ArrayList<>(new LinkedHashSet<>(ids));
        if (clean.size() < 2) {
            return null;
        }
        for (String id : clean) {
            String old = OWNER.remove(id);
            if (old != null && !old.equals(name)) {
                List<String> ms = GROUPS.get(old);
                if (ms != null) {
                    ms.remove(id);
                    if (ms.size() < 2) {
                        GROUPS.remove(old);
                    }
                }
            }
        }
        GROUPS.put(name, clean);
        for (String id : clean) {
            OWNER.put(id, name);
        }
        save();
        return name;
    }

    /** 生成一个不重复的组名。 */
    public static String nextName() {
        for (int i = 1; i < 1000; i++) {
            String n = "自定义 " + i;
            if (!GROUPS.containsKey(n)) {
                return n;
            }
        }
        return "自定义 " + System.currentTimeMillis();
    }

    public static boolean remove(String name) {
        List<String> ms = GROUPS.remove(name);
        if (ms == null) {
            return false;
        }
        for (String id : ms) {
            OWNER.remove(id);
        }
        save();
        return true;
    }
}
