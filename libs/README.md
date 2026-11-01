# libs/ —— 本地前置依赖（jar 不入库）

**这个目录默认是空的。** 前置模组的 jar 属于第三方作品，不随本项目分发，
请按下面的清单自行下载后放进来。

## 一、必须手动放进来（否则编译直接失败）

`build.gradle` 会在配置阶段检查这两个文件，缺失时抛出带下载地址的中文错误。

| 文件名（前缀匹配即可） | 版本 | Minecraft | 下载 |
|---|---|---|---|
| `goety-2.5.58.4.jar` | 2.5.58.4 | 1.20.1 Forge | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/goety/files) / [Modrinth](https://modrinth.com/mod/goety) |
| `geckolib-forge-1.20.1-4.4.9.jar` | 4.4.9 | 1.20.1 Forge | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/geckolib/files) / [Modrinth](https://modrinth.com/mod/geckolib) |

注意事项：

- **GeckoLib 必须 4.4.x**。4.7+ / 4.8+ 面向 NeoForge，装错会因 API 不兼容崩溃。
- 文件名前缀对得上就行（`goety-*.jar` / `geckolib-*.jar`），
  但**不要同时放多个版本**，否则两个都会被加进 classpath。
- Goety 是**可选**运行时依赖：玩家不装，法师勋章自动软禁用，其余勋章正常。
  但**编译期必须有**，因为法师勋章直接 import 了它的 API。

## 二、不需要手动放（构建时自动下载）

| 模组 | 版本 | 仓库 |
|---|---|---|
| Curios API | 5.14.1+1.20.1 | `https://maven.theillusivec4.top/` |
| Apothic Attributes | 1.3.7 | `https://maven.shadowsoffire.dev/releases` |
| Placebo（前者的前置） | 1.20.1-8.6.0 | 同上 |
| JEI（`compileOnly`） | 15.20.0.106 | `https://maven.blamejared.com/` |

首次构建会自动拉取到 `~/.gradle/caches`，之后可 `--offline`。

> **JEI 只用于编译**：`compat/jei/LostRealmJeiPlugin` 的彩蛋隐藏逻辑。
> 发布出去的 jar 不带 JEI，玩家不装也不会崩——没有 JEI 时这个类永远不会被加载。
> 但**换新环境后第一次构建必须联网**，否则解析不到 `mezz.jei:jei-1.20.1-forge`。

**如果要完全离线构建**：把这两个 jar 也下载到本目录，然后把 `build.gradle`
里 Curios / Apothic 那四行改成 fileTree 形式：

```groovy
fileTree(dir: 'libs', include: ['curios-*.jar']).each { jar ->
    compileOnly fg.deobf(files(jar))
    runtimeOnly fg.deobf(files(jar))
}
fileTree(dir: 'libs', include: ['ApothicAttributes-*.jar']).each { jar ->
    compileOnly fg.deobf(files(jar))
    runtimeOnly fg.deobf(files(jar))
}
```

## 三、为什么不用 flatDir

`flatDir { dirs 'libs' }` 已在 `repositories` 里声明，但它**不会触发 deobf**
（SRG → MCP 重映射）。前置 jar 是在混淆名（SRG）下编译的，不走 `fg.deobf()`
会出现「方法存在但名字对不上」的 `NoSuchMethodError`，而且是运行时才炸。

正确写法只有一种：

```groovy
compileOnly fg.deobf(files('libs/xxx.jar'))
runtimeOnly fg.deobf(files('libs/xxx.jar'))
```

## 四、玩家侧（打包发布时）

发布出去的 jar **不包含**任何前置，`mods.toml` 里已经声明了依赖关系，
玩家用启动器/整合包安装时由他们自己装好即可。
