package com.lost_realm.client.hud;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import com.lost_realm.Medals;

/**
 * HUD 的<b>自定义贴图</b>：把 PNG 丢进 {@code config/lost_realm/hud/} 就生效。
 *
 * <h2>为什么走配置文件而不是资源包</h2>
 * 资源包要打包、要给文件起 12 个字的路径、改一次还得重进游戏。
 * 这里直接读本地文件：改完存盘，界面上下一帧就变（按修改时间自动重载），
 * 想调像素就调像素。
 *
 * <h2>文件命名</h2>
 * <pre>
 *   config/lost_realm/hud/
 *     health_bg.png    生命条底
 *     health_fill.png  生命条填充（按血量比例从左往右裁）
 *     health_icon.png  数值模式左边的小图标（16×16 以内比较合适）
 *     hunger_*.png     同上
 *     armor_*.png      同上
 * </pre>
 * 任何一个文件不存在就回落到内置的纯色矩形，所以可以只画其中一两个。
 *
 * <h2>尺寸</h2>
 * 条子的底和填充都按<b>拉伸</b>绘制：随便画多大都行，但 80×5 是"一比一"的尺寸，
 * 上下左右都不会被拉伸。填充图只会用到左边 {@code 当前比例} 那一段，
 * 所以画的时候要按"满血"来画。
 *
 * <p>整段读取都包在 try/catch 里，图片坏了就当没有，绝不连累 HUD。
 */
public final class HudTextures {

    /** 一张已经加载好的图。 */
    public record Img(ResourceLocation id, int w, int h, long mtime) {
    }

    /** 配置目录里的覆盖图（按 mtime 热重载）。 */
    private static final Map<String, Img> CACHE = new HashMap<>();
    /** 模组 jar 里内置的默认图（尺寸缓存，资源重载时清空）。 */
    private static final Map<String, Img> JAR_CACHE = new HashMap<>();
    private static boolean readmeWritten;

    private HudTextures() {}

    /** 资源包重新加载（F3+T）时清掉尺寸缓存。 */
    public static void clearCache() {
        JAR_CACHE.clear();
        CACHE.clear();
    }

    /**
     * 取一张图；没有就返回 {@code null}。
     *
     * <p>顺序：<b>配置目录里的同名文件优先</b>（方便随时改），没有就用 jar 里内置的
     * {@code assets/lost_realm/textures/gui/hud/<名字>.png}，再没有才回落到纯色矩形。
     */
    public static Img get(String name) {
        Img custom = fromConfig(name);
        return custom != null ? custom : fromJar(name);
    }

    /** jar 里的内置图：尺寸只能读一次，之后按名字缓存。 */
    private static Img fromJar(String name) {
        Img cached = JAR_CACHE.get(name);
        if (cached != null) {
            return cached;
        }
        ResourceLocation id = new ResourceLocation(Medals.MODID, "textures/gui/hud/" + name + ".png");
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(id);
            if (res.isEmpty()) {
                return null;
            }
            try (var in = res.get().open()) {
                NativeImage image = NativeImage.read(in);
                Img img = new Img(id, image.getWidth(), image.getHeight(), -1L);
                image.close();
                JAR_CACHE.put(name, img);
                return img;
            }
        } catch (Throwable t) {
            Medals.LOGGER.warn("[Lost Realm] 内置 HUD 贴图 {} 读取失败", name, t);
            return null;
        }
    }

    /** 配置目录里的覆盖图。 */
    private static Img fromConfig(String name) {
        try {
            Path dir = dir();
            Path file = dir.resolve(name + ".png");
            if (!Files.isRegularFile(file)) {
                CACHE.remove(name);
                return null;
            }
            long mtime = Files.getLastModifiedTime(file).toMillis();
            Img cached = CACHE.get(name);
            if (cached != null && cached.mtime() == mtime) {
                return cached;
            }
            NativeImage image = NativeImage.read(Files.newInputStream(file));
            DynamicTexture texture = new DynamicTexture(image);
            ResourceLocation id = new ResourceLocation(Medals.MODID, "hud/" + name);
            var manager = Minecraft.getInstance().getTextureManager();
            manager.release(id);                       // 改图后重新注册，先把旧的 GL 贴图放掉
            manager.register(id, texture);
            texture.setFilter(false, false);            // 像素风：不要线性过滤
            Img img = new Img(id, image.getWidth(), image.getHeight(), mtime);
            CACHE.put(name, img);
            Medals.LOGGER.info("[Lost Realm] HUD 贴图已加载：{} ({}×{})", name, img.w(), img.h());
            return img;
        } catch (Throwable t) {
            Medals.LOGGER.warn("[Lost Realm] HUD 贴图 {} 读取失败，用内置画法", name, t);
            CACHE.remove(name);
            return null;
        }
    }

    /** 目录不存在就建出来，顺手放一份说明。 */
    private static Path dir() {
        Path dir = FMLPaths.CONFIGDIR.get().resolve("lost_realm").resolve("hud");
        if (!readmeWritten) {
            readmeWritten = true;
            try {
                Files.createDirectories(dir);
                Path readme = dir.resolve("说明.txt");
                if (!Files.exists(readme)) {
                    Files.writeString(readme, README);
                }
            } catch (Throwable ignored) {
                // 建不出来就只是没有贴图，不影响功能
            }
        }
        return dir;
    }

    private static final String README = """
            把 PNG 放在这个目录里，HUD 就会用你的图替换内置的纯色方块。
            改完存盘即生效，不用重启游戏。

            文件名（任何一个不存在就用内置画法）：
              health_bg.png    生命条底
              health_fill.png  生命条填充
              health_icon.png  数值模式左边的小图标
              hunger_bg.png / hunger_fill.png / hunger_icon.png
              armor_bg.png  / armor_fill.png  / armor_icon.png

            尺寸：
              条的底/填充按拉伸绘制，80×5 是一比一（不拉伸）的尺寸；
              填充图请按"满血"画，游戏会按比例只取左边一段。
              图标建议 9×9 或 16×16。

            透明度照常支持（PNG 的 alpha 会保留）。
            """;
}
