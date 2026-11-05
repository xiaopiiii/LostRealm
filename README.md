# Lost Realm（遗落境）—— 五枚勋章熟练度系统 + 遗落之都终局战

> **当前版本:`1.0.0-beta46`**（`gradle.properties` 的 `mod_build`）。
> 编译用 Minecraft **1.20.1** / Forge **47.4.10**；玩家实例实测跑在 **47.4.28**。
> 版本号规则：每次改动内容都要把 `mod_build` 末尾数字 +1 —— 它同时决定
> 模组列表里显示的版本与 jar 文件名。完整约定见 [`.codebuddy/项目规则.md`](.codebuddy/项目规则.md)。
>
> `mod_id` 自 dv1 起为 `lost_realm`，显示名 `Lost Realm` / 中文名 `遗落境`。
> `dv1` 之前叫 `medals` 的旧存档需要重新配置（物品/配方/配置文件名都变了）。

## 一句话

五枚勋章（射手 / 刃 / 坦克 / 法师 / 召唤师）各自 1~60 级熟练度与 6 个节点技能，
外加一整套体验改进（QoL）、一个可视化配置界面、五档世界难度，
以及一条 50 节、约 206 格长的巨虫终局 Boss —— **乌蠹诺斯**。

## 现状速览

| 项 | 现状 |
|---|---|
| 版本 | `1.0.0-beta46` |
| 注册名 / 包名 | `lost_realm` / `com.lost_realm` |
| 勋章 | 5 枚，各 6 个节点技能（Lv.5 / 10 / 15 / 20 / 25 / 30 解锁） |
| 熟练度 | Lv.1~60；六段成长率 7/6/5/4/3/2%；枷锁在 Lv.20 与 Lv.40 |
| 阶位 | 1~20 / 21~40 / 41~60（射手：游射 · 游侠 · 箭神） |
| 战斗属性 | 12 条自注册（beta30 起不依赖 Apothic Attributes）+ 坦克减伤 |
| 配置界面 | 18 个分类 / **384 项**，含独立的「世界难度」页与「乌蠹诺斯」分类 |
| 世界难度 | **5 档**，`/lr difficulty` 或配置界面切换，写进存档 |
| 终局 Boss | **乌蠹诺斯**（`lost_realm:udunos`）：50 节 / ≈206 格 / 4 阶段 / 星门 / 暗星天幕 / 双 BGM |
| 语言键 | 中英各 **1418** 条，逐键对齐 |
| 源码规模 | **227** 个 Java 文件 |

## 本文读法

- **改代码前** → §二（核心机制的实现位置）+ §四（属性映射）+ 项目规则 §4「静默失效速查」。
- **查功能在哪** → §六（体验改进）、§六·补充二（世界难度）、§六·补充三（乌蠹诺斯）。
- **要构建** → §八。
- **看历史** → `gradle.properties` 顶部的版本历史注释块（dv1 → beta46 逐条）。
  旧人形 Boss 的 dv 期档案已随代码一起删除（`beta45`），本文不再保留那 1700 行。

---

## 零、两条版本线（dv 线与 beta 线）

| 线 | 范围 | 说明 |
|---|---|---|
| `dv1` ~ `dv30` | 早期 | 勋章系统成型、遗落之都、终末之影（人形 Boss）三形态迭代 |
| `beta1` ~ `beta46` | 当前 | 架构重构与实测修复期：属性自注册、Boss 换代为巨虫、配置界面重做 |

近期关键节点：

- **beta26** 等级上限 30 → 60，成长曲线改六段（7/6/5/4/3/2）。
- **beta30** 彻底移除 Apothic Attributes / Placebo，12 条战斗属性自注册。
- **beta32** 乌蠹诺斯血量临时降到 500 便于实测（`UdunosTuning.MAX_HEALTH`，发布前恢复 12000）。
- **beta33 ~ beta35** 连修「整条消失 / 出场抽搐 / 体节闪烁 / 星门后消失」，
  最终把客户端自算物理整段删除，改为**服务端唯一脊线**（`PacketUdunosSpine` 带瞬移标志）。
- **beta37** 新增五档世界难度（存档数据 + 指令）。
- **beta38** 配置界面加「世界难度」页；新增 G 键作为配置界面第二入口。
- **beta39** 配置项全量对账：补齐 97 项界面缺失、删除 6 项死配置。
- **beta40** README 全量重写（本版只改文档，jar 内容与 beta39 一致）。
- **beta41~43** 配置界面删旧 Boss / 加乌蠹诺斯配置 / Boss 全量改名 `udunos`。
- **beta44** 代码审查修复：属性刷新幂等短路、星空渲染零分配、怪物难度倍率改持久修饰符、
  删除失效的「遗落本源」与 `RealmCapConfig`。
- **beta46** 「战士」全量改名为「刃」（内部标识 `blade` / 槽位 `blade`），
  九枚刃换新贴图；顺带修好一直缺失的契约槽。
- **beta45** 删除旧人形 Boss「终末之影」整套死代码（28 个文件 / 约 6700 行），
  材料掉落改由乌蠹诺斯供应，两条进度改判据。


> **法师勋章**以【诡厄巫法 / Goety】为底座,通过其官方公开 API 实现
> 灵魂能量、聚晶法术与仆从三大系统的深度咬合。

---

## 一、硬性依赖(mods.toml 已声明)

| 模组 | modId | 必需 | 作用 |
|---|---|---|---|
| **Curios API** | `curios` | ✅ 是 | 勋章佩戴的饰品槽位 |
| ~~**Apothic Attributes**~~ | ~~`attributeslib`~~ | ❌ **已移除** | beta30 起属性全部自注册,见「四、属性映射」 |
| ~~**Placebo**~~ | ~~`placebo`~~ | ❌ **已移除** | 随 Apothic Attributes 一起移除 |
| **GeckoLib** | `geckolib` | ✅ 是 | 遗落之主的骨骼模型与关键帧动画（`dv13` 起） |
| **诡厄巫法 Goety** | `goety` | ⭕ 可选 | 法师勋章的底座(灵魂/法术/仆从) |
| **Configured** | `configured` | ⭕ 可选 | 提供游戏内配置屏幕 |

> 声明位于 `src/main/resources/META-INF/mods.toml`。`mandatory=true` 的只剩**两项**：
> **Curios** 与 **GeckoLib**（后者只为遗落之都/旧 Boss 的骨骼模型服务，但仍是硬前置）。
>
> **`beta30` 起连 Apothic Attributes 也不需要了** —— 12 条战斗属性改由本模组注册
> (`attribute/RealmAttributes`),效果在 `attribute/RealmAttributeEffects` 与
> `mixin/LivingEntityArmorPierceMixin` 里实现,流血效果也自建为 `lost_realm:bleeding`。
> 整个项目对 `dev.shadowsoffire` 家族的 import 已降为 **0**。
> 硬前置只剩 Curios(与 GeckoLib),数值量纲与原 attributeslib 1.3.7 逐条对齐,老存档不受影响。
>
> **`dv13` 起新增 GeckoLib 前置** —— 版本选 **4.4.9**
> (4.4.x 是 1.20.1 **Forge** 的稳定线;4.7+ / 4.8+ 主要面向 NeoForge,
> 装错会因 API 不兼容崩溃)。jar 也可放在 `libs/` 走本地依赖,
> 与 Goety 同一套路,便于离线构建。
>
> **未安装 Goety 时**:法师勋章自动软禁用(不出现在创造标签页、技能不触发、
> 属性不施加),其余三枚勋章完全不受影响 —— 由 `compat/goety/GoetyCompat` 保证。

---

## 二、三项核心机制的实现位置

### 1. 配置单位:百分比一律写整数

**约定**:配置文件中所有百分比都写成整数,`20` 即表示 `20%`。

```toml
[archer]
    # Lv.1 箭伤加成 [整数百分比: 8 表示 8%]
    lv1_arrow_damage = 8
```

代码侧统一通过 `MedalsConfig.pct(...)` 除以 100:

```java
// config/MedalsConfig.java
public static double pct(IntValue value) {
    return value.get() / 100.0D;
}

// 使用处(MedalRuntime.java)
double arrow = MedalsConfig.pct(MedalsConfig.ARCHER_L1_ARROW_DMG)
             + MedalsConfig.pct(MedalsConfig.ARCHER_PER_ARROW_DMG) * steps;
```

这样玩家在配置屏幕上看到的是干净的整数,不会出现 `0.2000000001` 这类浮点噪音。

### 2. 防叠加保护:所有修饰符固定 UUID

所有 `AttributeModifier` 的 UUID 都来自 `util/ModifierIds.java`,由确定性命名 UUID 生成:

```java
public static UUID id(String path) {
    return UUID.nameUUIDFromBytes((Medals.MODID + ":" + path).getBytes(StandardCharsets.UTF_8));
}
```

**为什么必须固定 UUID**:`AttributeInstance` 用 UUID 区分修饰符。若每次施加都随机生成 UUID,
同一效果反复触发(如每 tick 刷新、技能重复激活)会不断叠加,属性数值指数膨胀,
最终导致数值溢出与崩溃。固定 UUID 让重复施加变成**覆盖**,从根本上杜绝叠加。

`util/AttributeGuard.java` 进一步做三重保护:

```java
// ① 同 UUID 先移除再加,保证覆盖而非累加
// ② 同 UUID 数量超限时拒绝施加
if (sameCount > MedalsConfig.MAX_MODIFIERS_PER_UUID.get()) { return false; }
// ③ 按前缀精确回收(技能停用时只清自己的)
AttributeGuard.clearByPrefix(player, Attributes.ATTACK_DAMAGE, "tank_t5_");
```

### 3. 熟练度溢出修正

`event/CombatHandler.java` 中的 `effectiveDamage(...)`:

```java
/** 有效伤害 = min(本次伤害, 目标当前生命值 + 伤害吸收) */
public static double effectiveDamage(double rawDamage, LivingEntity target) {
    double pool = target.getHealth() + target.getAbsorptionAmount();
    return Math.min(rawDamage, Math.max(0.0D, pool));
}
```

- **溢出不计**:目标剩 3 血却被打 20 伤害 → 只有 3 计入熟练度。
- **击杀奖励按最大生命值**:`victim.getMaxHealth() * 系数`。
- **中立生物收益减半**:`isNeutral()` 判定 `NeutralMob` 接口 + `lost_realm:neutral_mobs` 标签,
  命中后所有收益 `* 0.5`。

---

## 二·补充、箭矢类技能的事件链

射手的两项被动都挂在战斗事件的固定位置,链路清晰、互不干扰:

```
ArrowLooseEvent ──► CombatHandler#onArrowLoose ──► SkillArrowVolley#onArrowLoose
                     (等级 ≥30 才补射)              补射 2 根额外箭 + 原箭 +25%

ProjectileImpactEvent ──► CombatHandler#onProjectileHit ──► SkillArrowRain#trigger
                     (仅击中活体)                   20% 概率在头顶 6 格 3×3 内落 4~6 箭

LivingHurtEvent(HIGH) ──► CombatHandler#applyArrowModifiers
                     (所有带 medals_volley_mult 的箭矢)   伤害倍率 + 无敌帧归零

LivingDamageEvent(LOWEST) ──► 熟练度结算(取实际扣血量)
```

**无敌帧突破的用意**:原版 `LivingEntity.invulnerableTime` 会让同一瞬间的多支箭只生效一根。
【三发】与【箭雨】都会把目标的无敌帧清零,使每根箭各自走完 `LivingHurtEvent` →
`LivingDamageEvent`,从而独立结算伤害与熟练度。该逻辑**仅对勋章机制生成的箭矢**生效
(靠持久数据 `medals_extra_volley` / `medals_volley_mult` 判定),原版箭矢不受影响。

**额外箭矢的来源区分**:`skill/archer/ExtraArrowMode.java` 定义了 `VOLLEY` / `MULTISHOT_BONUS` / `RAIN`
三种模式,写入箭矢持久数据 `medals_extra_volley_mode`。箭雨箭矢命中时会被识别为 `RAIN` 并**跳过**箭雨判定,
避免无限递归。

---

## 三、熟练度曲线

| 区间 | 规则 |
|---|---|
| 最高等级 | **60**（配置 `max_level`；beta26 起，旧存档自动迁移 30 → 60） |
| Lv.1 → Lv.2 | **100** 经验（`base_exp`） |
| 1~10 级 | 每级所需经验比上一级 **+7%** |
| 11~20 级 | **+6%** |
| 21~30 级 | **+5%** |
| 31~40 级 | **+4%** |
| 41~50 级 | **+3%** |
| 51~60 级 | **+2%** |

实现见 `util/ExpTable.java`：六段成长率编译进 `table[]`，配置改动时惰性重建。
按 7/6/5/4/3/2 收窄后，1 → 60 级总需求约 **3.6 万**经验（≈1200 次击杀），
最后一级 1,338 —— 固定 8% 的话到 60 级要 13.9 万，纯刷。

阶位按 **20 级一档**，与三道枷锁对齐（`ExpTable#tierOf`）：

| 等级 | 射手 | 其它四枚 |
|---|---|---|
| 1~20 | 游射 | 见习 |
| 21~40 | 游侠 | 精锐 |
| 41~60 | 箭神 | 宗师 |

枷锁落在 **Lv.20 / Lv.40**（`MedalProgression`），破枷要消耗**破枷印记**（`MedalShackleItem`）。
> `beta44` 删掉了早期设计的「遗落本源」—— 它把上限写进一个静态字段，
> 而真正的封顶读的是枷锁阶段，等于用了没效果；功能已被枷锁系统完全取代。

---

## 四、属性映射(自注册,beta30 起)

| 需求中的属性 | 本模组属性 ID | 基础值 |
|---|---|---|
| 箭伤 | `lost_realm:arrow_damage` | 1.0 |
| 拉弓速度 | `lost_realm:draw_speed` | 1.0 |
| 闪避 | `lost_realm:dodge_chance` | 0.0 |
| 暴击率 | `lost_realm:crit_chance` | 0.05 |
| 暴击伤害 | `lost_realm:crit_damage` | 1.5 |
| 护甲穿透 | `lost_realm:armor_pierce` | 0.0 |
| 生命偷取 | `lost_realm:life_steal` | 0.0 |
| 过量治疗 | `lost_realm:overheal` | 0.0 |
| 火焰伤害 | `lost_realm:fire_damage` | 0.0 |
| 冰霜伤害 | `lost_realm:cold_damage` | 0.0 |
| 受治疗量 | `lost_realm:healing_received` | 1.0 |
| 经验获取 | `lost_realm:experience_gained` | 1.0 |

> 基础值与上下限对齐 **Apothic Attributes 1.3.7** 的注册代码,以保证历史存档里
> 已经写进修饰符的数值不会漂移。统一在 `attribute/RealmAttributes` 中引用。
>
> 「已损生命值伤害」(`current_hp_damage`) 本模组从未使用,故未注册。
>
> 近战伤害/攻速/移速/护甲/最大生命用原版属性;攻击距离用 `forge:entity_reach`;
> 坦克的「伤害减免」因前置未提供,单独注册 `lost_realm:damage_reduction`(见 `item/TankAttributes.java`)。

---

## 五、节点技能一览

### 射手勋章

**阶位命名**:1~20 级 = **游射** / 21~40 级 = **游侠** / 41~60 级 = **箭神**
(其它四枚勋章沿用通用阶位:见习 / 精锐 / 宗师。见 `util/ExpTable#tierOf`)

| 等级 | 技能 | 类型 | 实现类 |
|---|---|---|---|
| 5 | **闪避** | 被动触发 | `SkillDodge` |
| 10 | 蓄势 | 命中结算 | `SkillChargeUp` |
| 15 | **破绽** | 命中结算 | `SkillWeaknessInsight` |
| 20 | 疾风骤雨 | **主动** | `SkillGaleStorm` |
| 25 | **箭雨** | 命中结算(被动) | `SkillArrowRain` |
| 30 | **三发** | 发射结算(被动) | `SkillArrowVolley` |

#### Lv.5【闪避】被动
拉弓时被攻击,有 **60%** 概率完全免疫该次伤害,并向**同 Y 轴**随机 **6 格**范围内
「至少有两格高空间」的位置传送。冷却 **20 秒**。
判定与传送位于 `CombatHandler#handleDodge`,冷却/时间窗状态存放于 `SkillDodge` 的
`PlayerMedals` state(`s5_dodge_cooldown` / `s5_dodge_used`)—— 注意:等级与熟练度已改存于勋章物品 NBT,`PlayerMedals` 只保留冷却/技能状态。

#### Lv.25【箭雨】被动
箭矢**击中活体目标**时,有 **20%** 概率在目标头顶上方 **6 格**、
**3×3** 方块范围内随机掉落 **4~6** 根箭矢,垂直下落砸向目标区域。
- 钩子:`CombatHandler#onProjectileHit` → `SkillArrowRain#trigger`
- 箭雨箭矢标记 `ExtraArrowMode.RAIN`,**不再递归触发**箭雨
- 每根箭矢倍率 `RAIN_DAMAGE_MULT = 0.35`,并复用无敌帧突破,保证多根各自结算

#### Lv.30【三发】被动
射箭时在原箭上下各偏 **1~3 度** 额外射出 **2 根**箭矢(合计三发),
额外箭矢伤害为原箭的 **1/4**,强制突破受击无敌帧并**独立结算**(按序号递减 100% / 50% / 25%)。
- 钩子:`CombatHandler#onArrowLoose`(`ArrowLooseEvent`)→ `SkillArrowVolley#onArrowLoose`
- 额外箭矢由 `buildArrow` **复刻原箭**的暴击 / 穿透 / 击退 / 底伤,因此力量、冲击、火矢、无限等附魔全部自然生效
- **兼容多重射击**:数量不变(仍为三发),三根箭伤害各 **+25%**
- 无敌帧突破与倍率应用位于 `CombatHandler#applyArrowModifiers`,读取箭矢持久数据 `medals_volley_mult`

### 法师勋章(以【诡厄巫法 / Goety】为底座)

**阶位命名**:1~20 **学徒** / 21~40 **术士** / 41~60 **大巫**

**Lv.1 基础属性 + 每级成长**(全部作用于 Goety 自己注册的属性,因此与聚晶法术天生联动)

| 属性 | Lv.1 | 每级 |
|---|---|---|
| `goety:spell_potency` 法术强度 | +2(绝对值) | +0.5 |
| `goety:casting_speed` 施法速度 | +5% | +0.5% |
| `goety:spell_radius` 法术作用半径 | +5% | +1.0% |
| `goety:soul_discount` 灵魂消耗折扣 | +2% | +0.15% |

**熟练度来源**

| 来源 | 配置项 | 默认 |
|---|---|---|
| 有效魔法伤害(溢出不计) | `mage.exp_spell_damage` | 20 /点 |
| 击杀(按目标最大生命) | `mage.exp_kill_hp` | 20 /点 |
| 灵魂消耗(5 魂 = 1 熟练度) | `mage.exp_soul` | 20 /点 |

| 等级 | 技能 | 类型 | 实现类 |
|---|---|---|---|
| 5 | 元素共鸣 | 被动 | `SkillElementalResonance` |
| 10 | 法爆 | 被动(自定义属性) | `SkillSpellCrit` |
| 15 | 灵魂虹吸 | 命中结算 | `SkillSoulSiphon` |
| 20 | 巫术过载 | **主动** | `SkillWitchOverload` |
| 25 | 灵魂燃烧 | **主动 · 可开关** | `SkillSoulBurn` |
| 30 | 弹幕风暴 | 命中结算 + 投射物复制 | `SkillBarrageStorm` |

#### Goety 集成架构(`compat/goety/`)

```
GoetyBridge.java       纯接口(只依赖原版类型),技能一律面向它编程
GoetyCompat.java       加载检测 + 「静态内部类 Holder」隔离类加载
GoetyBridgeImpl.java   真实实现,直接引用 com.Polarice3.Goety.*
```

**关键设计 —— 类加载隔离**:`GoetyBridgeImpl` 引用了 Goety 的类,如果直接在
`GoetyCompat` 里 `new`,JVM 可能提前解析其常量池并抛 `NoClassDefFoundError`。
因此实例化放在静态内部类 `Holder` 中,**只有确认 `ModList.isLoaded("goety")` 后才触发初始化**。
技能类自身**不 import 任何 Goety 类**(包括属性的读取也走桥接的
`spellPotencyTotal` / `spellPotencyModifierAmount`),保证未装 Goety 时零风险。

**用到的 Goety 官方 API**:

| API | 用途 | 对应技能 |
|---|---|---|
| `utils.SEHelper` | 灵魂读写 + `getFocusCoolDown` 取法术冷却表 | Lv.15 / 25 / 30 |
| `utils.TotemFinder` | 灵魂容器(图腾)查找 | Lv.25 |
| `init.ModAttributes` | 法术强度 / 施法速度 / 作用半径 / 各类折扣属性 | Lv.1 / 15 / 20 / 25 / 30 |
| `common.capabilities.soulenergy.FocusCooldown` | `removeCooldown(player, level, item)` 直接清空法术冷却 | Lv.30 |
| `api.items.magic.IWand` | 魔杖识别、灵魂消耗(`SoulUse`)、当前法术流派(`getSpellType`) | Lv.5 / 15 / 30 |

#### Lv.5【元素共鸣】—— 用枚举序号做流派状态
`SpellType` 是枚举,而 `PlayerMedals` 的状态系统只支持 int。因此把流派编码为
`SpellType.ordinal() + 1` 存入状态键(0 = 无),施放不同流派即叠层、
相同流派仅刷新计时窗口,窗口过期清零。无需任何字符串状态 API。

#### Lv.10【法爆】—— 为什么必须自注册属性
Apothic Attributes 的 `crit_chance` / `crit_damage`
**只作用于近战伤害**,对 `magic` 伤害类型完全无效,且被 Apothic 自己的 Mixin
在近战结算处读取。若复用它们,法师的近战也会一并变强,破坏勋章职责隔离。

因此本模组自注册了两个属性:

```java
lost_realm:spell_crit_chance   // 0~1,ADDITION
lost_realm:spell_crit_damage   // 1.0 = 100%,ADDITION 存"额外倍率"
```

由 `CombatHandler#handleMageOnDamage` 在**魔法伤害分支内**掷骰并乘算,
与神话前置、刃的暴击完全解耦。成长联动:每解锁一个后续技能(Lv.15/20/25/30)
额外 +3% 暴击率、+5% 暴击伤害。

#### Lv.15【灵魂虹吸】—— 「负面效果」的判定
遍历目标全部激活效果,只要存在任意一个 `MobEffectCategory != BENEFICIAL`
(HARMFUL 或 NEUTRAL)即视为带负面效果。这覆盖原版与各模组的减益,
无需维护枚举清单。返还沿用**先扣后补**策略(Goety 未开放取消消耗的钩子)。

#### Lv.20【巫术过载】—— 法术强度的正确算法
Goety 的 `SpellAttribute.potency(type, 0, 0, 2048)` 是**绝对值**(基础 0、上限 2048),
不是百分比。因此「+30%」实现为:

```java
base = max(0, 属性总值 - 本技能自身修饰符的量)
增量 = base × 30%
```

减去自身贡献再乘比例,保证每 tick 幂等刷新时**不会指数自叠**。
`SkillSoulBurn` 的每层强度加成使用同一套 `basePotency` 模式。

#### Lv.25【灵魂燃烧】—— 唯一可开关的技能
`Skill#isToggleable()` 返回 `true` 后,`PacketActivateSkill` 会在已开启时
路由到 `Skill#deactivate` 而非重新激活。开启不进冷却,关闭瞬间(或灵魂耗尽自动关闭)
才结算 30 秒冷却——因此 `cooldownTicks()` 返回 0,冷却由 `stop()` 自行 `setCooldown`。

#### Lv.30【弹幕风暴】—— 一个事件搞定所有法术投射物
Goety 的法术投射物种类极多,逐一 Mixin 成本高且易冲突。但所有投射物最终都要经过
`ServerLevel#addFreshEntity(Entity)`,因此只需监听 **`EntityJoinLevelEvent`**:

- 原投射物主人是玩家 && 法师 ≥ 30 && 处于「三连发待发」
- 用 `type.create()` + `saveWithoutId`/`load` **反序列化拷贝**两份副弹
- 速度方向在**水平面内**左右各旋转 5°,保留原竖直分量
- 副弹打 `medals_barrage_child` 标记,避免递归克隆

非投射物法术(瞬发 AOE / 增益)不做克隆,其补偿由常驻的「作用半径 +25%」承担。
冷却刷新直接调用公开 API `FocusCooldown#removeCooldown`。
概率带**保底递增**:20% → 35% → 50% …,触发后归零。

### 刃勋章
| 等级 | 技能 | 类型 | 实现类 |
|---|---|---|---|
| 5 | 撕裂 | 命中施加 | `SkillRend` |
| 10 | 愈战愈勇 | 动态属性 | `SkillFightHarder` |
| 15 | 燃烧自我 | **主动** | `SkillBurningSelf` |
| 20 | 寒霜之刃 | 命中施加 | `SkillBladeOfFrost` |
| 25 | 战迹血淋 | **主动** | `SkillWarTrail` |
| 30 | 血意 | 暴击结算 | `SkillBloodIntent` |

### 坦克勋章
| 等级 | 技能 | 类型 | 实现类 |
|---|---|---|---|
| 5 | 重击 | 动态属性 | `SkillHeavyStrike` |
| 10 | 攻势汲取 | 命中触发 | `SkillOffensiveDrain` |
| 15 | 星盾 | 计时/抵挡 | `SkillStarShield` |
| 20 | 不屈壁垒 | **主动** | `SkillUnyieldingBulwark` |
| 25 | 格挡 | 受伤结算 | `SkillBlock` |
| 30 | 生生不息 | 自动触发 | `SkillEndlessLife` |

主动技能默认 **R 键**触发(可在按键设置中修改)。

---

## 六、体验改进

> 本板块收集**不影响勋章系统、纯改善游玩体验**的改动，相互独立，各自可单独开关。

### 第 1 项 · 原版经验等级大改

> ⚠️ **注意区分**：本项修改的是 **Minecraft 原版玩家经验等级**
> （屏幕下方的绿条、附魔台消耗的那个），
> **与勋章熟练度（`ExpTable`，Lv.1~60）完全是两套独立系统**，互不影响。

原版经验需求是 3 段线性公式（`7+2×lv` / `37+5×(lv-15)` / `112+9×(lv-30)`），
本项把它替换为指数增长曲线：

| 区间 | 规则 |
|---|---|
| Lv.1 → 40 | 每级 = 上一级 × **1.10** |
| Lv.40 → 70 | 每级 = 上一级 × **1.08** |
| Lv.70 → 100 | 每级 = 上一级 × **1.04** |
| Lv.101 及以上 | 基础增长率 **2%**；每跨越 25 级（125 / 150 / 175…）增长率 **×0.95**；低于 **0.005%** 后固定为 0.005% |

**关键等级所需经验**（默认起点 7）：

| 等级 | 需求 | 等级 | 需求 |
|---|---|---|---|
| 0 → 1 | 7 | 70 → 71 | 3,188 |
| 40 → 41 | 317 | 100 → 101 | 10,340 |
| 41 → 42 | 342 | 125 → 126 | 16,964 |

**实现方式**：Mixin 注入 `Player#getXpNeededForNextLevel()`（`@At("HEAD")` + `cancellable`）。

之所以只注入这一个方法就够 —— 原版 `giveExperiencePoints` 内部是
`experienceProgress += amount / getXpNeededForNextLevel()`，
即**进度条本身就读取本方法**。因此替换返回值后，进度条、升级判定、
附魔台/铁砧消耗会**自动全部保持一致**，无需触碰任何其它逻辑。

**配置项**（`config/lost_realm-common.toml` 的 `[experience]` 段，配置屏幕中为独立标签页）：

| 配置键 | 默认 | 说明 |
|---|---|---|
| `enabled` | `true` | 总开关；`false` 时完全回退原版公式 |
| `base_exp` | `7` | 曲线起点（0 → 1 级所需） |
| `growth_1_40` | `10` | 1~40 级增长率（整数 %） |
| `growth_40_70` | `8` | 40~70 级增长率 |
| `growth_70_100` | `4` | 70~100 级增长率 |
| `growth_after_100` | `2` | 101+ 基础增长率 |
| `decay_percent` | `95` | 每跨一个台阶时增长率乘以的百分比 |
| `decay_step` | `25` | 台阶跨度（级） |
| `min_growth_milli` | `5` | 增长率下限，放大 1000 倍存储（5 = 0.005%） |

实现见 `util/VanillaXpCurve.java` 与 `mixin/PlayerXpMixin.java`。
曲线采用**逐级累乘**（而非闭式解），保证与规则描述逐位一致；
因增长率递减极快，即便到 Lv.4000 也不会触及 `int` 上限。

### 第 2 ~ 7 项 · 药水与生存

| # | 功能 | 说明 | 实现 | 配置键（`[qol]` 下） |
|---|---|---|---|---|
| 2 | **药水无限续杯** | 身怀**普通药水**时获得其效果的无限时长；数量耗尽立即失去（喷溅/滞留型不生效） | 事件 `TickEvent.PlayerTickEvent` + `PotionInfiniteHandler` | `potion_infinite.enabled` / `scan_backpack` |
| 3 | **无限 buff 共享** | 持有第 2 项无限效果的玩家，把该效果共享给 32 格内其他玩家（可调） | 事件 + `BuffShareHandler` | `buff_share.enabled` / `radius`（默认 32） |
| 4 | **更好的不死图腾** | 图腾放在背包也能触发死亡保护（含精妙背包） | Mixin `LivingEntity#checkTotemDeathProtection` | `totem_backpack.enabled` |
| 5 | **修改原版鞘翅** | 鞘翅额外 +10 护甲、+4 盔甲韧性（固定 UUID，防叠加） | 事件 `ItemAttributeModifierEvent` | `elytra_armor.enabled` / `armor` / `toughness` |
| 6 | **死亡保留 Buff** | 死亡后不清空身上效果 | Mixin `LivingEntity#die`（HEAD 快照 / TAIL 恢复） | `keep_buffs_on_death.enabled` |
| 7 | **死亡掉落物集中** | 掉落物全部落在死亡点，不再四散 | Mixin `Player#drop` | `death_drop_centered.enabled` |

### 第 8 ~ 10 项 · 睡觉

| # | 功能 | 说明 | 实现 | 配置键 |
|---|---|---|---|---|
| 8 | **任意时间睡觉** | 取消"仅夜晚/雷雨"限制 | 事件 `SleepingTimeCheckEvent` | `sleep_anytime.enabled` |
| 9 | **睡觉跳过 6 小时** | 每次睡眠成功额外推进 6 小时（1h = 1000 tick） | Mixin `ServerLevel#tickTime` | `sleep_skip.hours`（默认 6） |
| 10 | **一人睡全场跳过** | 原版需全体就寝，现只需一名玩家 | Mixin `SleepStatus#areEnoughSleeping` | `one_player_sleep.enabled` |

### 第 11 ~ 20 项 · 交互、战斗与附魔

| # | 功能 | 说明 | 实现 | 配置键 |
|---|---|---|---|---|
| 11 | **耕地保护** | 在耕地上跳跃不再踩坏 | 事件 `BlockEvent.FarmlandTrampleEvent` | `farmland_protect.enabled` |
| 12 | **满腹进食** | 饱食度满仍可继续吃食物 | Mixin `Player#canEat` | `eat_when_full.enabled` |
| 13 | **凋零骷髅头 15%** | 掉落概率提升至 15%（固定，不可调） | Mixin `WitherSkeleton#dropCustomDeathLoot` | `wither_skull.enabled` |
| 14 | **自动攻击** | 手持武器按住左键，冷却满且指向目标时自动挥出 | 客户端 tick | `auto_attack.enabled` / `require_target` |
| 15 | **自动射击** | 长按右键，弓拉满 / 弩装填后自动发射 | 客户端 tick | `auto_shoot.enabled` |
| 16 | **村民交易 64 次** | 每项交易上限 12 → 64（固定，不可调） | Mixin `MerchantOffer#getMaxUses` | `villager_trades_64.enabled` |
| 17 | **交易刷新按钮** | 交易界面追加"刷新交易"按钮，重置交易次数 | 事件 + `PacketRefreshTrades` | `refresh_trades.enabled` |
| 18 | **附魔刷新按钮** | 附魔台追加"刷新附魔"按钮，消耗经验+青金石重掷 | 事件 + `PacketRefreshEnchant` | `refresh_enchant.enabled` / `cost_levels` / `cost_lapis` |
| 19 | **附魔台保留青金石** | 关闭界面后青金石留在台内 | Mixin `EnchantmentMenu#removed` | `keep_lapis.enabled` |
| 20 | **无限 + 经验修补不冲突** | 两者可共存于同一件物品 | Mixin `ArrowInfiniteEnchantment#checkCompatibility` | `infinity_mending.enabled` |
| 20b | **附魔全部预览** | 附魔台一次显示全部候选附魔 | Mixin（客户端）`EnchantmentScreen#render` | `enchant_preview_all.enabled` |
| 21 | **底部 HUD 上移 1px** | 快捷栏 / 血量 / 护甲 / 饥饿 / 经验条 / 手持物品名整体抬高 1 像素 | Mixin（客户端）`Gui#render` | 无（写死 `-1.0F`） |
| 22 | **更好的状态效果 HUD** | 状态效果图标可缩放（100/75/50/25%）+ 四角停靠 + 横/纵向排列 + 三种倒计时 + 减益红框 | Mixin（客户端）`Gui#renderEffects` + `EffectHudRenderer` | `effect_hud.*`（6 项） |
| 23 | **药水可堆叠** | 药水（含喷溅 / 滞留）堆叠上限 1 → **16** | Mixin `Item#getMaxStackSize` | `stack_potion.enabled` |
| 24 | **不死图腾可堆叠** | 堆叠上限 1 → **16** | Mixin `Item#getMaxStackSize` | `stack_totem.enabled` |
| 25 | **末影珍珠堆叠 64** | 堆叠上限 16 → **64** | Mixin `Item#getMaxStackSize` | `stack_ender_pearl.enabled` |
| 26 | **无限弓无需箭矢** | 带「无限」附魔的弓在背包无箭时也能射击，射出的箭落地即消失 | Mixin `Player#getProjectile` + `Level#addFreshEntity` | `infinite_bow_no_arrow.enabled` |

> 第 2、3 项可对接 **精妙背包（Sophisticated Backpacks）**：
> 通过反射软兼容（`compat/backpack/BackpackCompat`），未安装该模组时零开销跳过。

### 第 21 项 · 底部 HUD 上移 1 像素

把屏幕底部那一整条 HUD —— **快捷栏格子、血量、护甲、饥饿、坐骑血、经验条、
手持物品名** —— 整体向上移动 **1 像素**。偏移量写死在代码里，不提供配置项。

**实现要点（`HotbarOffsetMixin`）**：这五个图层各自从 `Gui#screenHeight`
现算 Y 坐标，内部没有共享的 PoseStack 变换，逐个改会产生五处分散注入点。
但它们在 `Gui#render` 里是**连续**的一段，因此只在**这段区间的两端**各注入一次：

| 注入点 | 锚点 | 动作 |
|---|---|---|
| push | `INVOKE renderHotbar` 之前 | `pose.pushPose()` + `translate(0, -1, 0)` |
| pop | `INVOKE renderSelectedItemName` 之后 | `pose.popPose()` |

锚点刻意选在「这两个子方法的调用语句前后」而**不是** `render` 的
`HEAD`/`RETURN`，这样 BOSS 血条、聊天栏、记分板、字幕、Tab 列表、
十字准星等屏幕上半部分的内容**不会**被误伤，全部留在原位。

另有一处 `@Inject(at = @At("RETURN"))` 兜底：若 pop 锚点因其它模组改动而
未命中，则在 `render` 返回前强制还原，防止 `PoseStack` 失衡污染后续绘制
（`render` 方法只有一个 return 点，该兜底必定执行）。

> 只影响**游戏内 HUD**。打开箱子 / 背包等容器界面时，格子位置保持原版不变。

### 第 22 项 · 更好的状态效果 HUD

替换原版的状态效果图标显示，提供 **缩放 / 方位 / 方向 / 倒计时** 四组可调项，
并附带一个**游戏内可视化编辑界面**，全部改动所见即所得。

**① 缩放（`effect_hud.scale`）**

可选 **100 / 75 / 50 / 25** 四档，以原版图标大小（24px 槽位）为 100%。
缩放时背景框和图标一起等比缩小，图标位置同步重算，不会出现错位。

**② 方位（`effect_hud.corner`）**

四个角落任选：`0` 左上、`1` 右上、`2` 左下、`3` 右下。默认右上（与原版一致）。

**③ 方向（`effect_hud.flow`）**

`0` 横向（图标排成一行）、`1` 纵向（图标排成一列）。**与方位相互独立**，
共 4 × 2 = 8 种组合。

**④ 倒计时（`effect_hud.timer`）—— 三选一**

| 值 | 模式 | 说明 |
|---|---|---|
| `0` | 不显示 | 与原版一致，仅在剩余 10 秒内闪烁 |
| `1` | 分钟 | `mm:ss` 形式，如 `2:35` |
| `2` | 秒 | 纯秒数，如 `155`（默认） |
| `3` | 进度条 | 图标下方一条 2px 细线，按剩余比例收缩；增益绿色、减益红色 |

**实现要点**：原版 `Gui#renderEffects` 的位置全部由 `screenWidth` 现算的**绝对值**
决定（每个效果 25px、槽位 24×24），并且**从不操作 PoseStack** ——
因此缩放 / 挪位没法靠一次 `pose.translate/scale` 搞定，只能整体接管重画。

| 步骤 | 位置 | 动作 |
|---|---|---|
| 1 | `EffectHudMixin` → `@Inject(method="renderEffects", at=HEAD, cancellable=true)` | 命中即 `cancel()`，原版逻辑完全不执行 |
| 2 | 同上 | 容器界面（`EffectRenderingInventoryScreen#canSeeEffects`）保持原版，避免破坏背包内布局 |
| 3 | `EffectHudRenderer.render(gfx, screenWidth, screenHeight)` | 自行分组（增益 / 减益）、排序、算锚点、逐个绘制 |

**编辑界面**：默认按 **右 Alt** 打开（可在「按键设置 → 遗落境」中改键）。
界面内实时预览，底部面板可切换四组参数，**保存**写入配置、
**取消**回滚、**重置**恢复默认。

> 由于 Forge 的 `ConfigValue` 在运行期只读，编辑界面通过中间的
> `EffectHudOverrides` 覆盖层实现"预览 → 保存/回滚"语义：
> 预览只改内存中的覆盖值，点保存才真正 `set()` + `CLIENT_SPEC.save()` 落盘。

**⑤ 减益红框（`effect_hud.harmful_border`）**

给**减益（负面）效果**加一圈醒目红框 —— 原版对增益与减益使用同一套灰白底框，
只能靠图标本身区分，危急时刻容易看漏。红框画在**底框之上、图标之下**，
既盖住原版灰边又不遮挡图标本体。

实现上做两级描边：外圈 1px **亮红**（`0xFFFF3030`）＋ 内圈 1px **暗红**（`0xFF7A0E0E`），
纯色描边在浅色背景上偏平，叠一圈暗红内衬后轮廓更实、也不会抢图标重心。
缩放较小时（`slot <= 10`，即 25% 档）自动降为**单线红框**，
避免边框吃掉过多像素。

> 这里用 4 次 `fill` 画四条边，而**不用** `renderOutline` ——
> 后者在 1.20.1 里颜色固定为半透明黑、无法指定，也不支持"外扩"边框。

### 第 23–25 项 · 物品堆叠上限放宽

| 项目 | 物品 | 原上限 | 新上限 |
|---|---|---|---|
| G21 | 药水（含喷溅 / 滞留） | 1 | **16** |
| G22 | 不死图腾 | 1 | **16** |
| G23 | 末影珍珠 | 16 | **64** |

三项都由 `ItemMaxStackSizeMixin` 统一处理，注入点是
`Item#getMaxStackSize()`（**无参**，SRG `m_41753_`）。

**为什么必须选无参版本** —— `Item` 上有两个同名方法：

| 方法 | 来源 | 能否注入 |
|---|---|---|
| `getMaxStackSize()` | **原版**，SRG `m_41753_` | ✅ 采用。虽是 `final`，但 `@Inject` 改写方法体字节码，不受 final 限制（受限的只有 `@Overwrite`） |
| `getMaxStackSize(ItemStack)` | **Forge 追加**，SRG 表中查无此名 | ❌ 不采用。写进 Mixin 会导致 refmap 无法解析、正式环境**静默注入失败** |

注入后 `ItemStack#getMaxStackSize()`（内部再调同签名方法）与
`ItemStack#isStackable()`（内部即 `getMaxStackSize() > 1`）都会自动跟随，
无需额外处理 —— 这也是只改一处就能生效的原因。

> **附魔书堆叠已按需求取消。** 原版判断"两本能否合并"用的是**静态方法**
> `ItemStack.isSameItemSameTags`，它表达的是整块 NBT「完全相等」的语义
> （`Objects.equals(a.tag, b.tag)`），被"丢弃重复物品""整理背包"等多个功能共用。
> 改写它会造成广泛连带影响，且静态方法无法用实例 `@Inject` 拦截，故不做。

### 第 26 项 · 无限弓无需箭矢

带**无限附魔**的弓，在背包里一支箭都没有时，依然可以拉弓射击。
射出的箭为**创造模式箭**：落地即消失、任何人都无法捡回。

**为什么要钩 `getProjectile`** —— 看 `BowItem.releaseUsing` 的字节码顺序：

```
ItemStack ammo = player.getProjectile(stack);   // ① 取箭
if (ammo.isEmpty()) { ...射不出去,直接返回... }  // ② 无箭的判定在这里
ArrowItem.isInfinite(ammo, bow, player)         // ③ 有箭时才据此决定是否消耗
```

原版的「无限附魔」只作用在 **③** —— 它阻止的是**箭矢被消耗**，
前提是**本来就得有箭**。所以"没箭也能射"必须在 **①** 上解决：
让 `getProjectile` 在「无限弓＋无箭」时返回一枚非空**哨兵箭**。

**哨兵必须是普通箭（`Items.ARROW`）**，依据是 `ArrowItem.isInfinite` 的字节码：

```
int lvl = EnchantmentHelper.getItemEnchantmentLevel(INFINITY_ARROWS, bow);
if (lvl > 0 && this.getClass() == ArrowItem.class) return true;   // 注意是 ==
return false;
```

它要求 `this.getClass()` **严格等于** `ArrowItem`，因此在 `SpectralArrowItem`
等子类上恒为 `false`。这一层很关键：它意味着本次射击**不会被原版判为"无限箭"**，
`BowItem` 会走消耗分支把哨兵 `shrink` 掉 —— 而哨兵是**刚 new 出来的游离对象**，
不属于背包任何格子，所以这次消耗不产生任何实际损耗，既不会凭空多出箭，
也不会误伤玩家的真实箭矢。

**再把箭设为不可拾取**：因为哨兵是普通箭、玩家又非创造模式，
`BowItem` 里偏移 411 的那句 `if (instabuild || 光灵箭 || 药箭) pickup = CREATIVE_ONLY`
**不成立**，箭会落到默认的 `ALLOWED`（可捡回），与"落地即消失"不符。

该 `putfield` 是**内联**在 `BowItem` 里的，不经过 `AbstractArrow#setOwner`，
所以钩 `setOwner` 不会触发。最终锚定 **`AbstractArrow#setKnockback`**
（SRG `m_36735_`，位于 BowItem 偏移 337）—— 三个选锚准则缺一不可：
**原版方法 ＋ 在 `AbstractArrow` 上直接声明 ＋ 有稳定 SRG 名**。两个踩过的坑：

| 候选锚点 | 否决原因 |
|---|---|
| `Level#addFreshEntity` | 1.20.1 里实为 `LevelWriter` 上的 **Forge 追加接口方法**，`Level` 类上查不到，注入后 **refmap 不生成条目、运行时静默失效** |
| `AbstractArrow#setSecondsOnFire` | 继承自 `Entity`，**未在 `AbstractArrow` 上声明**，作为 Mixin 目标解析失败 |
| `AbstractArrow#getPickupItem()` | SRG 表中只映射为单字符 `p`（Forge/Parchment 追加成员），refmap 解析不可靠 |

> **原版 `putfield`（偏移 414）会不会盖掉我们的写入？不会。**
> 它只在 `instabuild || 光灵箭 || 药箭` 时触发，而本功能生效前提是
> 玩家**非**创造模式（创造模式本就不缺箭）且哨兵是普通箭 —— 条件恒不成立。
> 这是条件推演得出的结论，不是"大概不会"。

> 判定条件用 **"发射者背包无箭"** 来识别哨兵箭：只要背包里还有普通箭，
> `getProjectile` 就会命中原版分支返回**真实**箭矢，哨兵根本不会创建。
> 因此二者恰好等价，无需往箭上打任何自定义标记，也不依赖 NBT。
> 对**非玩家**发射者（骷髅等）直接跳过，不干扰原版行为。

### 第 27 项 · 可视化配置界面（`dv3` 起，beta38/39 大改）

把两份 Forge 配置（`SPEC` / `CLIENT_SPEC`）里的 **384 项**全收进一个双栏窗口，
不必再手改 `.toml`。侧边栏 18 个分类，其中 **「世界难度」不是配置项**，
而是独立的一页（见 [§六·补充二](#六补充二世界难度系统beta37)）。

**打开方式**

| 入口 | 操作 |
|---|---|
| 快捷键 | **G** 或 **右 Ctrl**（两个独立键位，可在「选项 → 控制 → Lost Realm」分别改绑/解绑） |
| 模组列表 | 主菜单 → 模组 → Lost Realm → **配置**（接管 Forge 原生配置按钮） |

**布局**

```
┌──────────┬──────────────────────────────────────────┐
│ 全部配置  │  选项名                     [ 控件 ]      │
│ 世界难度  │ ──────────────────────────────────────── │
│ 体验改进  │  药水无限续杯               [ 开 ]        │
│ 状态效果HUD│ 共享半径                 [ 32 ] %        │
│ 熟练度经验│  停靠位置   [左上 | ▶右上 | 左下 | 右下 ] │
│ 射手数值  │                                          │
│ …（17类）│  ← 滚轮翻页，右侧有滚动条                 │
├──────────┴──────────────────────────────────────────┤
│ [保存] [取消] [重置本页] [全部重置]       N 项待保存  │
└─────────────────────────────────────────────────────┘
```

**四类控件**

| 控件 | 用于 | 交互 |
|---|---|---|
| **开 / 关** | `BooleanValue` | 点击翻转 |
| **数字框** | `IntValue` / `DoubleValue` | **直接敲数字**（dv36 起不再用 −/+ 按钮），单位画在框右侧 |
| **多按钮高亮** | 枚举型（缩放 / 方位 / 方向 / 倒计时） | 点击切换，当前项描边 |
| **应用 / 当前** | 世界难度页 | 点「应用」立即生效，当前档置灰 |

> 枚举按钮上限 8 个（`switchOpt` 里硬断言）。连续数值误用成枚举会横排铺满屏幕 ——
> dv36 就踩过：缩放 25~100 当成枚举，生成 76 个按钮并溢出到侧边栏外。

**编辑模型：先缓冲、再提交**

改动只写进 `ConfigOption` 的内存缓冲，**不碰真实配置**；改过的行名称变琥珀色，
页脚实时显示「N 项改动未保存」。点保存才 `apply()` 到 `ConfigValue` 并写盘，取消则全部丢弃。

> 保存时只 `save()` 真正被改过的那份 spec（`LinkedHashSet<ForgeConfigSpec>`），
> 避免两份配置各写一次盘。

> **难度页是例外**：它没有缓冲，点「应用」直接发 `PacketDifficultySet`，
> 服务端校验 OP 后执行并广播；底部只留「完成」，不摆保存/取消/重置。

**分类清单（18）**

世界难度 · 体验改进 · 状态效果 HUD · 熟练度与经验 · 射手数值 · 刃数值 ·
坦克数值 · 法师数值 · 召唤师 · 箭类饰品 · 刃饰品 · 法师晶石 · 召唤契约 ·
终极武器 · 盾类饰品 · 职业槽位 · 乌蠹诺斯 ·（外加综合视图「全部配置」）

| 文件 | 职责 |
|---|---|
| `client/config/ConfigOption.java` | 单项抽象：读写后端 + 编辑缓冲 + 脏检查 + 重置 |
| `client/config/ConfigCategory.java` | 18 个分类注册表（侧边栏条目） |
| `client/config/ConfigCatalog.java` | **384 项登记表** —— 界面唯一数据源 |
| `client/config/ConfigScreen.java` | 双栏界面；难度页在这里单独渲染 |

> **登记表为什么手写**：反射拿不到 `defineInRange` 的 min/max、中文标签、分类归属。
> 代价是新增配置要同步加一行 —— 所以 beta39 做了全量对账，把漏掉的 97 项补齐、
> 6 项死配置删掉。判据：**未登记 = 0、死配置 = 0、默认值/上下限与配置一致 = 0 差异**。

**未装 Configured 也能用** —— 走 Forge 原生 `ConfigScreenHandler.ConfigScreenFactory`；
装了 Configured 时额外登记一份，方便从它的入口进入。

---

## 六·补充、旧人形 Boss「终末之影」—— **已删除（`beta45`）**

> 本章原本是 `dv4` ~ `dv30` 关于人形 Boss「终末之影」（`lost_realm:warden`）的完整档案：
> GeckoLib 三形态建模、五波清场、四相祭坛、审判读条、对话链……共约 1700 行。
> **`beta45` 把这套内容从代码里整体删除了**，因此本章已无对应实现，整章移除。

### 为什么删

它在 `beta1x` 之后就已经**没有任何生成点**了 —— 失落祭坛的仪式召唤的是乌蠹诺斯，
`/summon` 只是调试口子。但那套代码仍然占着：

| 层 | 内容 |
|---|---|
| 实体 | `WardenBoss` / `WardenSentinel` + 属性注册 + 渲染器 + 模型 + 8 张贴图 |
| AI | 5 个 `Warden*Goal`、`WardenAnimations`（3 套骨骼动画）、`WardenPhase` |
| 战斗 | `WardenFightManager` / `WardenFightSession` / `WaveManager` / `WardenSpawnHandler` / `LordState` |
| 对话 | `LordDialogueHandler` + 两个网络包 + `DialogueScreen` + 11 条文本 |
| 方块 | `warden_altar`（四相祭坛，本就「仅作装饰」） |
| 物品 | `warden_scythe`（旧 Boss 手里的兵刃，玩家拿不到） |

合计 **28 个文件、约 6700 行**，全部删除，无兼容层。

### 删掉之后怎么补

旧 Boss 是**材料来源**，所以掉落必须有人接手：

| 材料 | 原来 | 现在 |
|---|---|---|
| `warden_core` 遗落之主·核心 | `warden` 100% | **乌蠹诺斯** 死亡时 2~5 个 |
| `altar_core` 祭坛激活核心 | `warden_sentinel` 100% | **乌蠹诺斯** 死亡时 1~3 个 |
| `warden_echo` 遗落之主的残响 | `warden` 30% | **乌蠹诺斯** 死亡时 1 个 |

配方（门钥匙 / 冠冕 / 残响→碎片）保持原样，因此仍然成立。两条进度也一并改了口：
`sentinel` 的判据从「击杀守望者仆从」改成「取得祭坛激活核心」，
`finale` 从「击杀 warden」改成「击杀 `lost_realm:udunos`」。

> 顺带清掉了每只 Boss 前 5 tick 打的 `diag` INFO 快照和渲染器前 3 帧的诊断日志。

---

## 六·补充二、世界难度系统（`beta37`）

五档难度，**由指令控制、写在存档里**，不是 `.toml` 配置项。

```
/lr difficulty              查看当前难度 + 五档一览（任何玩家）
/lr difficulty <1-5>        切换（OP）
/lr difficulty set <1-5>    同上，写全一点（OP）
/lostrealm difficulty ...   同一套命令挂在 /lostrealm 下
```

### 三个入口（beta38 起）

1. **配置界面 → 世界难度**（侧边栏第一位）：五档各一行，上面一行写怪物侧、
   下面一行写玩家侧，右侧「应用」直接切档；当前档用琥珀色竖条 + 置灰按钮标出。
   这一页**不读 `ConfigOption`** —— 难度是存档数据，点「应用」走网络包，
   服务端校验 OP（单机房主直接放行）后生效并广播；被拒会回一条聊天提示。
   界面一打开就发 `PacketDifficultySet(0)` 查询一次当前值。
2. **指令**：见上面的表。
3. **快捷键开界面**：右 Ctrl（原有）或 **G**（beta38 新增）。
   两个键位独立注册，可在「选项 → 控制 → Lost Realm」里分别改绑或解绑。

| # | 名称 | 敌对生物 | 玩家侧 |
|---|------|----------|--------|
| 1 | 简单 | 血/甲/攻 −20% | — |
| 2 | 普通 | 原版 | — |
| 3 | 困难 | 血/甲/攻 +20% | — |
| 4 | 噩梦 | 血/攻 +40%、甲 +30% | 攻/甲 −10%、回血 −15%、熟练度 +7% |
| 5 | 深渊 | 血 +60%、攻 +50%、甲 +40% | 攻/甲 −15%、回血 −25%、熟练度 +15%、中立生物主动攻击、职业饰品栏 +1 |

### 三条落点

1. **敌对生物**：只认 `Enemy` 标记（`Monster` 只是它的子集，史莱姆/恶魂/幻翼也带这个标记）。实体进入世界（`EntityJoinLevelEvent`）时给
   `MAX_HEALTH` / `ARMOR` / `ATTACK_DAMAGE` 挂固定 UUID 的 `MULTIPLY_TOTAL` 修饰符。
   换难度改的是**同一个 UUID 的数值**，不是再叠一层 —— 所以「简单打一半切深渊」
   不会变成 0.8 × 1.6 的残留。血量按比例保留（掉到 40% 的怪换难度后仍是 40%）。
2. **玩家**：攻击/护甲同样是固定 UUID 的倍率修饰符，由 `PlayerTickHandler`
   每 40 tick 幂等刷新一次（值没变直接 return）。死亡重生/换维度会重建属性实例，
   40 tick 内一定补回。
3. **一次性行为**：回血衰减走 `LivingHealEvent`（只对玩家）、中立生物敌对走
   `LivingTickEvent`（每 20 tick 检查，半径 16 格，只激怒**当前没有目标**的那些，
   并排除玩家自己的宠物/仆从）、熟练度倍率统一乘在 `MedalSource#addExp` 里
   （调试指令走新增的 `addExpRaw`，给多少是多少）。

### 坑位

- **+1 职业饰品栏不能直接改属性**：Curios 的槽位格数是 `CurioStacksHandler`
  自己维护的修饰符表算出来的（`baseSize + ΣADDITION`），不是读玩家属性实例。
  必须走 `ICuriosItemHandler#addTransientSlotModifier` + `stacks.update()`
  才会真正 resize；而且同一个 UUID 重复 `add` 会在表里留两条 —— 那就成 +2 了，
  所以加之前必须先查 `stacks.getModifiers()`。
- **血量比例要在施加修饰符前取**：`getMaxHealth()` 一旦被修饰符改掉，
  就再也算不出「原来的满血是多少」。
- **`oldHealth <= 0` 时不要动血量**：有些 Boss 在构造期血量还是 0，
  按 0/oldMax 的比例去算会把它当场按死。
- **难度是存档数据**（主世界 `level.dat` 里的 `lost_realm_difficulty`）：
  进程内只做缓存，`ServerStoppingEvent` 时清回「普通」，避免换存档串味。

---

## 六·补充三、遗落之主·乌蠹诺斯 —— 当前终局 Boss（`beta1x` 起）

> **命名**：显示名「**遗落之主·乌蠹诺斯**」（英文 `Udunos, Lord of the Lost`）。
> **内部标识与显示名同源**（beta43 起全量重命名）：实体 ID `lost_realm:udunos`、
> 类名 `Udunos*`、包 `boss/udunos/`、配置段 `[udunos]`、语言键 `*.udunos.*`、
> 贴图与音效文件名 `udunos_*`。旧名（`erebos` / 晦星）已从源码中彻底移除，
> 全库 grep 结果为 0 —— 本项目是个人开发版，不承担旧存档兼容。

> 一条 **50 节、约 206 格长**的巨虫，在本模组里是
> 「泰拉瑞亚尺度换算到 MC 尺度」的产物 —— 长度压到 6 个区块、速度压到
> 每 tick 几个方块，否则服务端会把它当传送处理。换算过程见 `UdunosTuning` 类注释。

### 实体清单

| 实体 | 作用 |
|---|---|
| `lost_realm:udunos` | 本体（头部碰撞箱 4.8×4.8，`updateInterval(1)` 每 tick 同步位置） |
| `lost_realm:udunos_part` | 身体各节的碰撞部件（头部以外的判定都靠它，转发伤害给本体） |
| `lost_realm:udunos_bolt` / `udunos_shard` | 弹幕：噬光弹与碎星 |
| `lost_realm:udunos_effect` | 招式用的纯视觉实体 |

### 体型与阶段

| 项 | 值 |
|---|---|
| 节数 | 50（节距 4.2 格 → 全长约 **206 格**） |
| 体径 | 4.8 格（渲染时沿身体方向压 0.875，避免相邻节共面闪烁） |
| 阶段 | 4 个：血量 **80% / 60% / 15%** 分界 |
| 招式 | 一阶段：碎星播撒 · 噬光冲锋 · 星弧巡猎；二阶段加星门贯穿 · 引力残星；四阶段无巡游 |

### 移动与战斗

- **巡游** 1.25 格/tick，**冲锋** 4.5 格/tick（每轮 3 次，预警 18/16/14/14 tick）。
- **动态减伤**：短时间内连续挨打会叠减伤（阈值 200 点 / 窗口 20 tick / 上限 75%），
  逼玩家打「躲—打—躲」的节奏，而不是站桩对轰。
- **接触伤害**：头 14、身体 8，同目标 10 tick 冷却。
- **拴绳** 256 格；出场是「盘旋钻出地面」（EMERGE），400 tick 未完成则兜底结束。
- **星门**：二三阶段会开传送门把自己从 A 门穿到 B 门（`move/MoveStarGate`）。
  穿门是**瞬移**，客户端必须硬切，否则会把身体拉成一条线（见下方「已知坑」）。

### 客户端同步（beta35 起定型）

```
服务端（唯一物理） --PacketUdunosSpine(50 节坐标 + teleport 标志)--> 客户端
客户端：只做 spinePrev/spineNow 插值 + 渲染，不跑任何自算物理
```

- 每 tick 下发一次，`broadcastSpine` 距离门限 320 格（覆盖半径 250 的竞技场）。
- **不设「位移超过阈值就判瞬移」**：30 格漏判（星门间距 20~40）、8 格误判（冲锋抽搐）。
  改为服务端 `markTeleport()` 显式置位，客户端只认标志硬切。
- 客户端 `clientStep()` 只剩「抄脊线 + 播粒子 + 播环境音」三件事。

### 视觉与声音

| 模块 | 内容 |
|---|---|
| `client/sky/UdunosSkyEffects` | 暗星天幕：分层星空 + 星球 + 星云（`renderSky` 里直接画，**不要再套相机旋转** —— 拿到的 pose 已含） |
| `client/UdunosBattleMusic` | 两首战斗 BGM 交替；`Attenuation.NONE`（默认 LINEAR + 位置 0,0,0 会让音量为 0） |
| `client/boss/UdunosRenderer` | 逐节盒渲染 + 逐节视锥剔除；本体 `noCulling = true` 绕过原版单 AABB |
| 战利品 | 五件遗物（`starfall_greatsword` / `hushlight_railgun` / `collapsar_eye` / `terminal_dragonpact` / `bulwark_of_the_last_light`）+ `event_horizon` |
| 材料 | `warden_core` 2~5 / `altar_core` 1~3 / `warden_echo` 1（`beta45` 起由它供应，见 §六·补充） |

### 怎么召出来

- **失落祭坛**（`LostAltarBlockEntity`）：投入材料 → 仪式读条 → 召唤。
- **暗星信标**（`DarkstarBeaconItem`）：直接召唤，调试用。
- 也可 `/summon lost_realm:udunos`（实体没加 `noSummon()`，就是为测试方便）。

### 数值现状

> ⚠️ `UdunosTuning.MAX_HEALTH` 自 **beta32** 起被临时改成 **500**（原设计 12000），
> 纯粹是为了实测方便。**正式发布前必须改回去**，源码里有 ★ 标记。

### 已知坑（都踩过）

| 症状 | 真因 | 约束 |
|---|---|---|
| 特定机位整条消失 | 单 AABB 视锥剔除 | 本体 `noCulling = true`，别改回去 |
| 身体某个位置一直闪 | 体径 4.8 > 节距 4.2，相邻两节共面 | 渲染沿身体方向压 0.875 |
| 移动时闪烁 / 抽搐 | 客户端自算 + 抄脊线两套时间轴打架 | 客户端**只抄不算** |
| 星门后整条消失 | 阈值判瞬移漏判 | 用 `teleport` 标志硬切 |
| 打身体不暴击 | `UdunosPart` 不是 `LivingEntity`，原版暴击判定跳过 | 部件 `hurt()` 里补 `ForgeHooks.getCriticalHit` |

### 与世界难度的关系

难度系统按 `Enemy` 判定（beta37），所以**乌蠹诺斯也会随难度缩放**：
深渊档下血量 ×1.6、攻击 ×1.5、护甲 ×1.4。阶段判定用的是**血量比例**而非绝对值，
因此缩放不会打乱阶段转换。

---

## 七、目录结构

> 只列「改代码时真的会去找」的层。完整清单：`find src/main/java -name '*.java'`（227 个文件）。

```
.codebuddy/项目规则.md                开发约定(版本号 / 命名 / 技术 / 交付)

src/main/java/com/lost_realm/
├── Medals.java                       主类:全部 DeferredRegister + 事件总线挂接
├── attribute/                        beta30 起的自注册属性
│   ├── RealmAttributes.java            12 条战斗属性定义与挂载
│   └── RealmAttributeEffects.java      暴击 / 破甲 / 吸血 / 闪避的结算
├── config/                           Forge 配置
│   ├── MedalsConfig.java               390 个字段(SPEC / CLIENT_SPEC 两份)
│   └── MedalsConfigLoader.java         供配置界面读取的分类映射
├── difficulty/                       ★ beta37 世界难度
│   ├── DifficultyLevel.java            五档枚举(数值集中,不可拆)
│   ├── DifficultyState.java            存档 SavedData(主世界 level.dat)
│   └── DifficultyManager.java          落点与事件(生物 / 玩家 / 治疗 / 中立 / 栏位)
├── command/
│   ├── MedalCommands.java              /lostrealm medal xp|level|info|unlock
│   ├── DifficultyCommand.java          /lr difficulty [1-5]
│   ├── ArenaCommand.java               /lr arena rebuild|tp|ritual|info
│   └── UdunosServerDebugCommand.java   /lrsdebug udunos
├── data/                             玩家数据与熟练度
│   ├── PlayerMedals.java               仅冷却 / 技能运行时状态(capability)
│   ├── MedalNbt.java                   熟练度存在勋章物品自己的 NBT 里
│   ├── MedalSource.java                取数门面(难度倍率在这里乘)
│   └── MedalProgression.java           枷锁(20 / 40)与破枷
├── item/                             勋章 / 箭 / 刃 / 盾 / 晶石 / 契约 / 终极剑
├── skill/                            30 个节点技能(5 枚 × 6)
├── event/                            战斗、tick、技能事件(CombatHandler 等 13 个)
├── network/                          11 个包(含 PacketUdunosSpine / PacketDifficulty*)
├── util/                             UUID 表 / 防叠加 / 经验表 / 属性上限
├── mixin/                            23 个注入(体验改进 + 破甲)
├── qol/                              体验改进处理器
├── compat/                           Goety / 精妙背包 / JEI 软兼容
├── effect/                           自定义状态效果(流血等)
├── sound/                            Boss 战 BGM 注册
├── common/                           Curios 兼容与实体标签
├── client/
│   ├── ClientBootstrap / MedalKeybinds / ClientDifficulty / ClientMedalCache
│   ├── config/                       配置界面(18 分类 / 384 项 / 难度页 / 乌蠹诺斯页)
│   ├── effect/                       状态效果 HUD + 可视化编辑界面
│   ├── sky/                          ★ 暗星天幕(UdunosSkyEffects)
│   └── boss/                         ★ 乌蠹诺斯渲染器
└── boss/
    ├── 遗落之都 / 传送门(LostCityBuilder / BossPortalHandler / ArenaBuilder)
    ├── udunos/                       ★ 乌蠹诺斯:本体 / 部件 / 弹幕 / 招式 / 脊线
    │   └── move/                      巡游 / 冲锋 / 星门
    ├── relic/                        ★ 五件遗物 + 事件视界
    └── block/ item/
```

资源与数据包：

```
src/main/resources/
├── META-INF/mods.toml
├── assets/lost_realm/
│   ├── lang/{zh_cn,en_us}.json        1361 ↔ 1361
│   ├── sounds/music/                  udunos_battle_1/2.ogg
│   ├── textures/{block,entity,gui,item,mob_effect,slot}/
│   └── models/ blockstates/
├── assets/minecraft/                  覆盖原版贴图(无限弓等)
└── data/
    ├── lost_realm/{advancements,dimension,dimension_type,loot_tables,recipes,damage_type}
    ├── lost_realm/curios/slots/        6 个饰品槽(medal / arrow / blade / shield / crystal / contract)
    └── lost_realm/tags/entity_types/   中立生物名单
```

> `data/curios/curios/slots/` 下那份是更早期版本的写法，当前以
> `data/lost_realm/curios/slots/` 为准（Curios 会把 `data/<命名空间>/curios/slots/`
> 的命名空间当作「谁提供的槽位」）；但 `data/curios/tags/items/` 的物品标签仍然必需。

`tools/` 是美术资产生成脚本（Python + Pillow），只在本机跑，不进 jar。

---

## 八、构建

```bash
export JAVA_HOME=/path/to/jdk-17      # 必须 17(21/25 会在 ForgeGradle 处失败)
./gradlew build --offline             # 首次构建要联网拉 Forge / MC 依赖(约 500MB)
```

产物：`build/libs/Lost Realm-<mod_version>-<mod_build>.jar`（当前：`Lost Realm-1.0.0-beta46.jar`）
（文件名 = `mod_version` + `mod_build`；`build.gradle` 用 `archiveFileName` 显式拼，
不写会得到 `...-1.0.0-beta46-1.0.0-beta46.jar` 这种双版本名。）

**`libs/` 里必须放两个前置**（不入库，需自行下载）：

| 文件 | 用途 |
|---|---|
| `goety-*.jar` | 诡厄巫法 —— 法师 / 召唤师勋章的底座 |
| `geckolib-forge-1.20.1-4.4.x.jar` | 骨骼动画（4.7+ 是 NeoForge 线，装错直接崩） |

**投放**：直接放进玩家实例的 `mods/`。同一个 `modId` 只能有一份，
换版本先移除旧的 —— 两份共存会 `DuplicateModsFoundException` 起不来。

```
D:/zhuomian/PCL 正式版 2.10.8/.minecraft/versions/1.20.1-Forge_47.4.28/mods/
```

> **版本号规则**：每次改动把 `mod_build` 末尾数字 +1，并在 `gradle.properties`
> 顶部的版本历史块补一段（改了什么、为什么）。只改注释可例外。
> 该规则与命名 / 技术约定一起维护在 `.codebuddy/项目规则.md`。

> **命名**：jar 文件名用显示名（`Lost Realm`，含空格），注册名永远是 `lost_realm`。
> 改文件名不影响存档兼容 —— 数据包路径与所有注册 ID 都走注册名。

---

## 九、数据包扩展

中立生物收益减半的名单可通过标签自定义:

`data/lost_realm/tags/entity_types/neutral_mobs.json`

```json
{ "replace": false, "values": ["minecraft:enderman", "minecraft:bee"] }
```

实现上同时兼容原版 `NeutralMob` 接口(末影人、蜜蜂、羊驼、猪灵等自动识别)。
