# Lost Realm（遗落境）—— 五枚勋章熟练度系统 + 遗落之都终局战

> **当前版本:`1.0.0-beta40`**（`gradle.properties` 的 `mod_build`）。
> 编译用 Minecraft **1.20.1** / Forge **47.4.10**；玩家实例实测跑在 **47.4.28**。
> 版本号规则：每次改动内容都要把 `mod_build` 末尾数字 +1 —— 它同时决定
> 模组列表里显示的版本与 jar 文件名。完整约定见 [`.codebuddy/项目规则.md`](.codebuddy/项目规则.md)。
>
> `mod_id` 自 dv1 起为 `lost_realm`，显示名 `Lost Realm` / 中文名 `遗落境`。
> `dv1` 之前叫 `medals` 的旧存档需要重新配置（物品/配方/配置文件名都变了）。

## 一句话

五枚勋章（射手 / 战士 / 坦克 / 法师 / 召唤师）各自 1~60 级熟练度与 6 个节点技能，
外加一整套体验改进（QoL）、一个可视化配置界面、五档世界难度，
以及一条 50 节、约 206 格长的巨虫终局 Boss —— **厄瑞玻斯**。

## 现状速览

| 项 | 现状 |
|---|---|
| 版本 | `1.0.0-beta40` |
| 注册名 / 包名 | `lost_realm` / `com.lost_realm` |
| 勋章 | 5 枚，各 6 个节点技能（Lv.5 / 10 / 15 / 20 / 25 / 30 解锁） |
| 熟练度 | Lv.1~60；六段成长率 7/6/5/4/3/2%；枷锁在 Lv.20 与 Lv.40 |
| 阶位 | 1~20 / 21~40 / 41~60（射手：游射 · 游侠 · 箭神） |
| 战斗属性 | 12 条自注册（beta30 起不依赖 Apothic Attributes）+ 坦克减伤 |
| 配置界面 | 18 个分类 / **384 项**，含独立的「世界难度」页与「厄瑞玻斯」分类 |
| 世界难度 | **5 档**，`/lr difficulty` 或配置界面切换，写进存档 |
| 终局 Boss | **厄瑞玻斯**（`lost_realm:erebos`）：50 节 / ≈206 格 / 4 阶段 / 星门 / 暗星天幕 / 双 BGM |
| 语言键 | 中英各 **1418** 条，逐键对齐 |
| 源码规模 | **257** 个 Java 文件 |

## 本文读法

- **改代码前** → §二（核心机制的实现位置）+ §四（属性映射）+ 项目规则 §4「静默失效速查」。
- **查功能在哪** → §六（体验改进）、§六·补充二（世界难度）、§六·补充三（厄瑞玻斯）。
- **要构建** → §八。
- **看历史** → `gradle.properties` 顶部的版本历史注释块（dv1 → beta40 逐条）。
  本文 §35.x 是 **dv 期档案**，其中的造型/机制描述**已作废**，只作检索用。

---

## 零、两条版本线（dv 线与 beta 线）

| 线 | 范围 | 说明 |
|---|---|---|
| `dv1` ~ `dv30` | 早期 | 勋章系统成型、遗落之都、终末之影（人形 Boss）三形态迭代 |
| `beta1` ~ `beta40` | 当前 | 架构重构与实测修复期：属性自注册、Boss 换代为巨虫、配置界面重做 |

近期关键节点：

- **beta26** 等级上限 30 → 60，成长曲线改六段（7/6/5/4/3/2）。
- **beta30** 彻底移除 Apothic Attributes / Placebo，12 条战斗属性自注册。
- **beta32** 厄瑞玻斯血量临时降到 500 便于实测（`ErebosTuning.MAX_HEALTH`，发布前恢复 12000）。
- **beta33 ~ beta35** 连修「整条消失 / 出场抽搐 / 体节闪烁 / 星门后消失」，
  最终把客户端自算物理整段删除，改为**服务端唯一脊线**（`PacketErebosSpine` 带瞬移标志）。
- **beta37** 新增五档世界难度（存档数据 + 指令）。
- **beta38** 配置界面加「世界难度」页；新增 G 键作为配置界面第二入口。
- **beta39** 配置项全量对账：补齐 97 项界面缺失、删除 6 项死配置。
- **beta40** README 全量重写（本版只改文档，jar 内容与 beta39 一致）。


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

枷锁落在 **Lv.20 / Lv.40**（`MedalProgression`），破枷要消耗「遗落本源」。

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
与神话前置、战士暴击完全解耦。成长联动:每解锁一个后续技能(Lv.15/20/25/30)
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

### 战士勋章
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

世界难度 · 体验改进 · 状态效果 HUD · 熟练度与经验 · 射手数值 · 战士数值 ·
坦克数值 · 法师数值 · 召唤师 · 箭类饰品 · 战士饰品 · 法师晶石 · 召唤契约 ·
终极武器 · 盾类饰品 · 职业槽位 · 厄瑞玻斯 ·（外加综合视图「全部配置」）

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

## 六·补充、终末之影（`lost_realm:warden`）—— dv 期 Boss 档案

> ### ⚠️ 本章是 dv 期档案（`dv4` ~ `dv30`），**不是当前 Boss**
>
> 它记录的是**人形 Boss「终末之影」**（`lost_realm:warden`）的建模、阶段与演出迭代。
> 那个实体**仍注册着、仍能用 `/summon` 叫出来**，但**当前流程不再召唤它** ——
> 失落祭坛的仪式召唤的是**厄瑞玻斯**（`lost_realm:erebos`，50 节巨虫）。
>
> 因此本章的「触手 / 骨翼 / 三形态 / 缩放描边」等描述**与现在的战斗无关**，
> 只作检索与回溯用。**当前终局战请看 §六·补充三。**

> **定位声明（dv 期）**：它当时被设计为整合包的最终 Boss，不是「四勋章系统的考试」。
> 现在这个位置由**厄瑞玻斯**接手（见 §六·补充三），本章仅存档。
> 它读取整合包里**各个体系**的力量（Goety 法术、Apothic 属性、勋章技能、纯原版），
> 但四勋章系统**永远不引用它** —— 依赖方向严格单向，删掉 Boss 内容四勋章仍能独立工作。

### 28. 整体流程

```
① 击杀 3 个整合包 Boss  ──► 遗落碎片 ×3
                              │
② 四勋章各达 20 级      ──► [成就·四相共鸣]（代码授予）
                              │
③ 合成门钥匙           ──► [成就·缝隙中的一瞥]
   （碎片 + 祭坛核心 + 终末之核 + 末影之眼 + 黑曜石 ×2）
                              │
④ 右键点燃黑曜石框架   ──► 遗落境裂隙 → 踏入【遗落之都】 [成就·无归之地]
                              │
⑤ 击败守望者仆从 ×3    ──► 祭坛激活核心 ×3 [成就·先行者]
                              │
⑥ 走到城市中央高台     ──► 遗落之主已在那里等你 —— 右键对话 [成就·遗落之主]
                              │
⑦ 对话选择「决斗」     ──► 幽灵试炼 → 五波清场 → 三形态真身 → 击败它
```

> **`dv5` 的关键变化**：**召唤符已删除**，Boss 也不再需要召唤 ——
> 他**从一开始就站在城市中央**。旧召唤符的 4 项材料已并入门钥匙配方，
> 所以「碎片 + 祭坛核心 + 终末之核 + 末影之眼 + 黑曜石×2」一次合成即可。
> 也就是说：**造一把钥匙 = 拿到入场券 + 全部前置**，流程从 7 步压到 6 步。

### 29. 遗落之都（`dv5` 竞技场升级）

旧的「半径 32 圆盘 + 四石柱」被整体替换为一座**程序化建造的超大建筑**：
半径 **100** 格，约 **25,000~30,000** 次方块写入，全部由 `LostCityBuilder` 在玩家首次抵达时生成。

| 结构 | 规格 | 作用 |
|---|---|---|
| **基座广场** | 半径 40，石板拼花 | 主战场，五波清场在这里打 |
| **中央高台** | 半径 10，高 5，带阶梯 | 遗落之主初始站位于此；零阶段对话发生地 |
| **外围城墙** | 半径 100，高 12，带城垛 | 防止被打进虚空；同时是视觉边界 |
| **城门 / 门廊** | 宽 6，高 12 | 唯一的入口，传送门就立在门内侧 |
| **环廊列柱** | 24 根，高 10 | 石柱掩体，第三形态躲技能用 |
| **三层台地** | 3 级，每级内缩 8、抬高 3 | 立体纵深，远程职业有射击位 |
| **八座高塔** | 8 座，高 24 | 天际线；塔身嵌发光带 |
| **火盆** | 16 座，环绕全城 | 光源 + 氛围（生成时用 flag `3`） |

> **幂等判定改用「地下哨兵块」**：`dv4` 用「中心方块是否 `isAir`」判断城市是否已生成，
> 但 `dv5` 的中心被高台占住，这个条件**永远为真** → 每次进维度都会重建整座城。
> 现改为在城市中心正下方 **3 格**埋一块 `arena_frame` 作哨兵：
> 存在即跳过，不存在即重建。稳定、零额外状态存储、天然兼容"玩家手贱挖了中心"。

> **性能预算**：批量写入一律用 `setBlock(pos, state, 2)`（只发客户端、**不触发邻居更新**）；
> 只有光源方块用 flag `3`。**绝不能用 flag `3` 铺满全城** —— 3 万次邻居更新会直接卡死服务器。
> 另外 `preloadChunks()` 会先强制加载 `x ± 116` 范围内的所有 chunk，
> 避免一次写入跨 chunk 时出现"半个城市不见了"。

### 30. 零阶段：对话与幽灵试炼（`dv5` 新增）

遗落之主刚被玩家看到时处于 **`DORMANT`（零阶段）**：**完全无敌、不还手、无血条**。

**右键对话** —— 靠近 8 格内右键即可开启对话界面（`DialogueScreen`）：

- 打字机式逐字显示（22 ms/字），点击可跳过本页；
- **台词只出现一次**。首次对话有 4 段独白，其中最后一句是
  *"你要走，我不拦你。你要留下来决斗……那我把话说完：你打不过我。"*
- 之后再用右键，**直接弹出选项**（"又是你。话我不重复第二遍 —— 走，还是打？"）。
- 分支按钮：**离开** / **决斗**。

> **「首次」的判定存在佩戴勋章的 NBT 里**（`MedalNbt.hasSeenLordIntro()`），
> 且在**打开对话的瞬间**就落库 —— 这样即使玩家在读完台词前拔线，
> 也不会出现"每次重登都能刷一遍独白"。

点「决斗」→ 进入 **`ENRAGED_GHOST`（幽灵态）**：

| 特征 | 表现 |
|---|---|
| 血条 | **完全隐藏**（`ServerBossEvent#setVisible(false)`） |
| 无敌 | 是 —— 此时任何伤害都不掉血 |
| 移动 | **极速冲脸**（0.85 格/tick，远超正常移速） |
| 手持 | 一把**木剑**（每次进入幽灵态都重新装备） |
| 一击 | 靠近 2.5 格内**强制结算**：扣除 **50% 最大生命**（可在配置里改） |
| 台词 | *"你的实力太弱了，先练一练，再来和我决斗吧！"* |
| 收尾 | 原地消失，转入五波清场 |

> **为什么这一击不走近战判定**：`MeleeAttackGoal` 会受击退、无敌帧、命中盒偏差影响，
> 一旦打空就变成"打也打不到、走也走不掉"的死局。因此改成
> 「距离 ≤ 2.5 时直接 `hurt()`」+ 手动补血（防被吸收盾吃掉）+ **160 tick 超时兜底**。
> 结果是**必中**，且必然会把人打到半血。

### 31. 五波清场（`dv5` 新增，全可配置）

被那一剑打飞之后，Boss 隐入高台（`WAVES` 状态，**隐身 + 无敌**），场上开始刷怪：

| 项 | 默认 | 范围 | 说明 |
|---|---|---|---|
| 波次数量 | 5 | 1~10 | 一共几波 |
| 每波怪物数量 | 5 | 1~20 | 每波刷几只 |
| 波次间隔 | 8s | 1~60 | 上一波清空后到下一波开始的等待 |
| 波次怪物种类 | 僵尸 | 僵尸/骷髅/蜘蛛/掠夺者/凋灵骷髅 | 替换整个波次的怪物类型 |

每波内每 20 tick 出 1 只（均匀铺开，不会一瞬间把玩家淹掉）。
**当且仅当该波全部清空**才进入下一波，最后一波清空 → Boss 走下高台、"勉强算个开始。"

> **存活计数必须靠标签过滤**（重要）：刷怪时给每只打上
> `lost_realm_wave_<序号>` 与 `lost_realm_wave_mob` 两个标签，
> 清点时就按标签统计。**绝不能用"竞技场范围内的所有 `Monster`"** ——
> 那会把自然刷怪、玩家自己放出来的怪都算进去，导致波次永远清不完（这是最容易踩的坑）。
> 会话结束时按 `lost_realm_wave_mob` 标签统一 `discard()`，不留残余。

### 32. 三战斗形态 · 锁血两次（`dv5` 重做）

清场结束进入真身战。血条重新显示，Boss 恢复正常伤害与正常移速。
**三个阶段不再由血量阈值驱动，而是由「锁血」驱动**：

| 形态 | 名称 | 描边色 | 体型 | 移速 | 伤害倍率 |
|---|---|---|---|---|---|
| Ⅰ | **形态·一** | 紫 | ×1.30 | 0.30 | ×1.0 |
| Ⅱ | **形态·二** | **红** | ×1.75 | 0.38 | ×1.35 |
| Ⅲ | **形态·三** | **白** | ×2.30 | 0.26（浮空） | ×1.8 |

**推进机制**：当形态 Ⅰ / Ⅱ 的血量被打到 **≤ 1** 时，**不死亡**，而是：

1. 血量**立刻锁定为 1**；
2. 进入 `TRANSITION` 状态，**锁血 1 的这段时间里继续免疫伤害**；
3. 用 **4 秒**（可配置）把血**逐渐回满**，期间喷涌 `SOUL_FIRE_FLAME` 粒子；
4. 回满后切换描边色与体型，进入下一形态。

形态 Ⅲ 才会真正被打死。

> **锁血的实现关键**：`hurt()` 里当 `血量 − 本次伤害 ≤ 1` 时，
> **直接触发转换并 `return true`，绝不调用 `super.hurt()`** ——
> 否则这一次伤害会照常结算并触发死亡。这是"锁血"与"死亡"之间唯一的灰色地带。

> **"换模型"没有用渲染器**：本项目**零自定义渲染器**。形态差异由五件事共同表达 ——
> ① 覆写 `LivingEntity#getScale()` 改体型（改完必须显式调 `refreshDimensions()`）；
> ② `Scoreboard` 的 `PlayerTeam#setColor(ChatFormatting)` + `setGlowingTag(true)` 换**描边颜色**；
> ③ 粒子光环（按形态切换 `ParticleTypes`）；
> ④ `getName()` 追加形态后缀并整体着色；
> ⑤ `ServerBossEvent` 血条换色。
> 比注册三个 `EntityType` 简单得多，而且不会让战斗会话 / 血条 / 目标 / 无敌 tick 全部失联。

**四相祭坛已移除机制**（`dv5`）：新的"零阶段本就无敌 + 锁血推进"流程与
"点四祭坛破终末护盾"是两套并行机制 —— 无敌时叠加减伤没有意义，锁血推进也不看血量阈值。
因此**祭坛的判定逻辑整体删除**，四座祭坛**保留为城市装饰**。

### 33. 新增内容清单

**方块（4）**：`realm_portal`（无碰撞/无遮挡/发光 11）、`void_stone`、`arena_frame`、`warden_altar`（附 `ALTAR` 枚举 + `LIT` 布尔两个状态，**`dv5` 起仅作装饰**）

**实体（2）**：`warden`（**遗落之主**，HP 800 / 护甲 20 / 韧性 12 / 击退抗性 1.0）、
`warden_sentinel`（守望者仆从，HP 120 / 纯近战，场上上限 6）

**物品（12）**：`lost_realm_fragment`、`altar_core`、`realm_key`、
`warden_core`、`realm_source`、`warden_echo`、`realm_crown` + 4 个 BlockItem

> **`warden_summon`（召唤符）已在 `dv5` 删除** —— 它的 4 项材料并入了 `realm_key` 配方。

> **`realm_source`（遗落本源）的设计意图**：它把四勋章等级上限 **30 → 35 → … → 50** 永久提高。
> 这给了满级玩家一条**继续成长的路**，也让打 Boss 有实质收益 ——
> 否则熟练度练满之后，Boss 就只是一次性的观赏内容。

**掉落**：`warden` → 终末之核 100% / 遗落本源 2~5 个 100% / 残响 30% / 冠冕 **8%**；
`warden_sentinel` → 祭坛激活核心 100%。全部带 `killed_by_player` 条件。

**合成（6）**：门钥匙（含原召唤符材料）、冠冕、虚空石、竞技场骨架、四相祭坛、残响→碎片回收

**任务链（6 环）**：`root` → `resonance` → `glimpse` → `trial` → `sentinel` → `finale`

### 34. 为什么两条成就要用「`impossible` 触发器 + 代码授予」

`resonance`（四勋章各达 20 级）这类条件**原版没有任何对应触发器** ——
原版不知道"勋章等级"是什么。因此 JSON 里写 `minecraft:impossible`
（数据包里永不自动满足），由 `WardenProgressionHandler` 每 20 tick 轮询一次在线玩家、
满足即用 `ServerPlayer#getAdvancements().award(...)` 授予。

> **为什么用轮询而不是事件驱动**：这个条件是「持续状态满足即授予」的语义，
> 天然是轮询而非"某事件发生"。事件驱动要在**升级**处插钩子，
> 且会漏掉「重登后条件已满足但从未发生变化」这种情况。轮询每 20 tick 才跑一次，
> 绝大多数玩家在第一个 `if` 就返回，开销可忽略。
>
> **`dv5` 变化**：旧版还有第二条 `summon`（站在召唤台上持符）的代码授予，
> 随召唤符删除一并移除；`finale` 的前置改挂 `sentinel`。

### 35. 配置项（「遗落之主」分类，15 项）

| 选项 | 默认 | 范围 | 说明 |
|---|---|---|---|
| 启用遗落之主 | 开 | — | 关闭后不再生成/唤醒；进行中的战斗仍可正常结束 |
| 最大生命值 | 800 | 100~10000 | Boss 血量（三形态共用同一上限） |
| 阶段Ⅱ血量阈值 | 60% | 1~99 | *（`dv4` 遗留项，现由锁血驱动形态，不再影响推进）* |
| 阶段Ⅲ血量阈值 | 25% | 1~99 | *（同上）* |
| 祭坛单次充能 | 10% | 1~100 | *（`dv5` 起祭坛已移除机制，此项失效）* |
| 终末护盾保留伤害 | 20% | 0~100 | *（同上）* |
| 终末审判读条 | 12s | 6~60 | 基础时长，练度越高越短 |
| 仆从数量上限 | 6 | 0~32 | 竞技场内同时存活数 |
| 任务链勋章门槛 | 20 | 1~100 | 「四相共鸣」成就要求 |
| **决斗一击伤害** | **50%** | 1~100 | `dv5` 新增 · 幽灵试炼那记木剑扣掉的最大生命比例 |
| **波次数量** | **5** | 1~10 | `dv5` 新增 · 清场共几波 |
| **每波怪物数量** | **5** | 1~20 | `dv5` 新增 · 每波刷几只 |
| **波次间隔** | **8s** | 1~60 | `dv5` 新增 · 清空后到下一波的等待 |
| **波次怪物种类** | **僵尸** | 僵尸/骷髅/蜘蛛/掠夺者/凋灵骷髅 | `dv5` 新增 · 整波替换 |
| **形态转换回血时长** | **4s** | 1~30 | `dv5` 新增 · 锁血为 1 后回满所花秒数 |

> **实现要点**：`BossRegistry` 里这些数值**不是 `static final` 常量，而是访问器方法**
> （`bossMaxHealth()` / `waveCount()` / `duelHitPercent()` …），
> 每个都做 `null` 兜底返回硬编码默认值，并 `clamp` 到合法区间。
> 原因有两个：
> ① `EntityAttributeCreationEvent` 在游戏**启动早期**读取血量，此时 `ConfigValue`
> 可能尚未加载 —— 兜底保证"配置没好也不会崩"；
> ② 旧 `.toml` 里可能残留越界的手改值，`clamp` 保证不会因此产生负数波数之类的怪状态。

### 35.5 `dv8` —— 勋章架构重构与配置界面重新分类

#### ① 熟练度改存于勋章物品自身

**问题**：原先熟练度（经验 + 等级）存在玩家 capability 里。这带来两个后果 ——
勋章被取下装箱、交易给别人时熟练度留在原主人身上；玩家永远无法"练出多枚勋章"。

**改法**：新增 `MedalNbt`（勋章物品 NBT 读写，与 `PlayerMedals` **同形 API**）
与 `MedalSource`（服务端统一取数门面）。等级完全由经验推导、不落盘，
NBT 只存 `MedalOwner` / `MedalExp` / `LordIntro` / `LordDuel` 四项。

```
旧：PlayerMedals.of(player).getLevel(type)
新：MedalSource.level(player, type)          // 内部走 Curios 查佩戴中的那枚
```

> **旧存档不迁移**：模组仍在极早期开发阶段、尚不可正常游玩，
> 因此不做任何「旧熟练度搬进新勋章」的兼容代码 ——
> 升级后所有勋章一律从 1 级从头开始。

**什么留在玩家侧**：主动技能冷却、节点技能运行时状态仍留在 `PlayerMedals`。
理由是冷却属于"玩家此刻的状态"而非"勋章的属性"，且战斗热路径每 tick 都要查，
搬进物品会让每 tick 都走一次 Curios 背包扫描。

#### ② 新增 Curios 专属「勋章」饰品槽

`data/curios/curios/slots/medal.json` 定义槽位，`size: 1` 保证**全局只开 1 格**；
`data/curios/curios/entities/player.json` 把 `minecraft:player` 绑到该槽。
`ItemMedal#canEquip` 另外检查 Curios 背包中是否已有其它勋章 —— 双重保险。

#### ③ 删除屏幕左上角熟练度 HUD

`MedalHud` 整个删除，连带 `show_hud` / `hud_x` / `hud_y` 三项配置一并移除。
等级、阶位、进度条改为直接画在**勋章 tooltip** 上 —— 数据取自物品 NBT，
因此勋章在背包/箱子里也能直接看到熟练度，不再依赖玩家侧的客户端缓存。
`ClientMedalCache` 从一个「收包缓存」改写成「实时查佩戴勋章」的薄封装。

#### ④ 「遗落境」标签页补齐全部 Boss 物品

此前 `MedalRegistry.TAB` 只 `accept` 了 4 枚勋章，Boss 的 11 件物品
（门钥匙 / 碎片 / 祭坛核心 / 遗落之主核心 / 遗落本源 / 残响 / 冠冕 /
虚空石 / 竞技场骨架 / 四相祭坛）**全部漏加**，玩家在创造模式翻遍标签页也找不到。
现已按「勋章 → 门钥匙 → 材料 → 战利品 → 建材」的顺序补齐。

> `realm_portal`（传送门门面）刻意不入标签页 —— 它必须由门钥匙点燃生成，
> 直接放置会绕过流程。

#### ⑤ 配置界面分类重构

原先 19 个分类里，体验改进被拆成 10 个碎片分类（药水/图腾/鞘翅/睡眠/种植/
自动战斗/村民/附魔/堆叠/死亡），侧边栏过长且难找。现合并为 1 个「体验改进」，
组内仍按功能分节排列。

最终 **9 个分类**：
`全部配置` / **体验改进** / `状态效果 HUD` / `熟练度与经验` /
`射手数值` / `战士数值` / `坦克数值` / `法师数值` / `遗落之主`。

四个职业各自一类、Boss 一类 —— 这是需求明确要求的粒度；
「勋章 HUD」分类随 HUD 删除而消失。配置项由 128 项收敛为 **125 项**
（删掉的正是 `show_hud`/`hud_x`/`hud_y`）。

---

### 35.6 `dv9` —— 修复「进维度即崩」的两类资源注册缺失

玩家实测：**只要通过传送门进入「遗落境」维度，客户端立刻崩溃**。
日志里能看到 `[Lost Realm] 遗落之都生成完毕,用时 10247 ms` → `遗落之主已降临`，
两秒后 `[Render thread/ERROR]: Unreported exception thrown!` 崩在渲染线程。

崩溃调用栈只有原版类，`Suspected Mods: NONE`：

```
java.lang.NullPointerException: Cannot invoke
  "EntityRenderer.shouldRender(...)" because "entityrenderer" is null
    at EntityRenderDispatcher.render(EntityRenderDispatcher.java:127)
    at LevelRenderer.renderLevel(LevelRenderer.java:1199)
```

**`Suspected Mods: NONE` 是「崩在原版类里」的典型特征** ——
真凶不是代码逻辑写错，而是**注册缺失**。这里同时缺了两类资源。

#### ① 只注册了 `EntityType`，从未注册 `EntityRenderer`

`BossEntities` 里 `WARDEN` / `SENTINEL` 只做了两件事：
在 `DeferredRegister<EntityType<?>>` 里登记实体类型，
在 `EntityAttributeCreationEvent` 里登记属性。**渲染器一行都没有。**

后果是致命的：客户端每帧都要对视野内每个实体查一次渲染器表，
查不到就拿到 `null`，紧接着 `EntityRenderDispatcher` 直接调
`entityrenderer.shouldRender(...)` → **NPE**。

三个关键特征，使它极易被误判：

| 特征 | 说明 |
|---|---|
| 服务端完全正常 | 实体能生成、能 tick、能受伤、能掉血 —— 只有客户端渲染才崩 |
| **无法 try-catch** | NPE 抛在 `EntityRenderDispatcher` 的调用栈里，不是模组的栈 |
| 只在实体进入视野时触发 | 因此表现为「进维度就崩」—— 遗落之主就站在落点正前方 |

**修法**：新建 `EntityRendererRegistry`，挂在 **MOD 总线**的 `FMLClientSetupEvent`，
用 `event.enqueueWork` 串行注册两个 `MobRenderer<T, HumanoidModel<T>>`：

```java
EntityRenderers.register(BossEntities.WARDEN.get(),
        ctx -> new WardenRenderer<>(ctx, "warden"));
EntityRenderers.register(BossEntities.SENTINEL.get(),
        ctx -> new WardenRenderer<>(ctx, "warden_sentinel"));
```

`WardenBoss` / `WardenSentinel` 都继承 `Monster`（原版僵尸那一支），
几何与骨骼就是标准人形，因此直接复用 `ModelLayers.ZOMBIE` 烘焙的 `HumanoidModel`，
无需自制模型文件与动画。体型放大不需要渲染器操心 ——
`LivingEntity#getDimensions(Pose)` 内部会应用 `getScale()`，
渲染管线按碰撞箱尺寸自动等比放大。

新增贴图 `textures/entity/warden.png` 与 `warden_sentinel.png`（各 64×64 RGBA）。
两贴图按原版人形 UV 布局手工生成（头 / 身 / 双臂 / 双腿六部件，带深色描边、
眼睛与胸前紫色符文），配色区分：遗落之主深紫近黑 + 幽紫眼，守望者仆从灰紫 + 暗红眼。

> **为什么渲染器注册必须放客户端 setup**：`EntityRenderers` 的静态表在注册期尚未就绪，
> 过早调用会写进一张还没被使用的表。且该注册会写全局静态表，
> 必须在主线程串行执行，故套一层 `enqueueWork`。

> **贴图缺失不会崩**：若贴图没跟上，原版会渲染成紫黑格纹而不是崩溃 ——
> 这是刻意依赖的默认行为，避免美术资源滞后时又变成「进维度即崩」。

#### ② 方块只有 `Block` + `BlockItem`，完全没有 blockstate 与模型

日志里另有 12 条资源加载失败：

```
Exception loading blockstate definition:
  'lost_realm:blockstates/realm_portal.json' missing model for variant: '...#axis=x'
```

涉及 `realm_portal`（×2 变体）/ `void_stone`（×1）/ `arena_frame`（×1）/
`warden_altar`（×8 变体）。因为 `blockstates/` 与 `models/block/` 目录**此前完全不存在**，
方块物品在手上、在背包里显示正常（走 `models/item/`），
但**一旦放置到世界里就彻底不可见**，整个遗落之都变成一片隐形方块。

**修法**：补齐 4 个 blockstate + 8 个方块模型。

| blockstate | 变体 |
|---|---|
| `void_stone` | 单变体 |
| `arena_frame` | 单变体 |
| `realm_portal` | `axis=x` / `axis=z` 两个变体（对应 NS / EW 两套朝向模型） |
| `warden_altar` | `altar`(soul/apotheosis/medal/pure) × `lit`(false/true) = **8 个变体** |

模型文件使用 `minecraft:block/cube_all` 父模型；`realm_portal` 拆出
`realm_portal_ns` / `realm_portal_ew` 两个（贴图共用 `realm_portal`）。

#### ③ 顺带修正的标签页遗漏

`MedalRegistry.TAB` 此前只 `accept` 了 4 枚勋章，Boss 的 11 件物品全部漏加 ——
玩家的原话是"刷怪蛋翻遍了，没有他的刷怪蛋，物品这里也只有 4 个职业勋章"。
现已补齐（见 §35.5 ④）。

> **交付自检新增两项**（详见项目规则）：
> ① 「新注册 `EntityType` 必须同时注册 `EntityRenderer`」；
> ② 「新方块必须有 `blockstates/` + `models/block/`，否则世界内不可见」。

---

### 35.7 `dv10` —— 修复「对话决斗后挨打不消失」与配置界面滚动错位

玩家实测反馈两件事：

> 「不是 boss 不平衡，而是对话决斗后打我下下没消失」
> 「？？？？」（配置界面截图，界面顶部糊了一排错位的文字）

#### ① 幽灵试炼「一瞬两下」——原版近战 Goal 与演出伤害叠加

**症状**：点「决斗」后，遗落之主冲脸的瞬间玩家被打掉两下，
而且打完之后 Boss **没有按设计消失**，看起来像"打不完"。

**根因**是两条伤害在同一刻结算：

```
幽灵态 ──► tickGhost() 每 tick 强制冲脸
         └─ 进入 1.0 格近战距离 ─► MeleeAttackGoal 判定命中,
│                                  按 ATTACK_DAMAGE 打一下      ① 原版近战
         └─ 同时 dist ≤ GHOST_STRIKE_RANGE(2.5 格)
            strikePlayer() 强制扣 50% 上限血                    ② 演出伤害
```

`MeleeAttackGoal` 一旦 `addGoal` 就**永远有效** —— 它的
`canUse` / `canContinueToUse` 只看「目标是否可达」，**完全不看实体自己的状态机**。
所以幽灵态下它照打不误。

**修法**：把近战换成自锁版本的 `GatedMeleeAttackGoal`（内部类），
把 `canUse` / `canContinueToUse` 收紧为 `boss.canFight()`：

```java
@Override public boolean canUse() {
    return boss.canFight() && super.canUse();
}
```

`canFight()` 只在 `FORM_1/2/3` 且非锁血演出时为真，于是幽灵态的伤害来源
**只剩 `strikePlayer()` 一处**。

> 这个改动顺手修掉了两个同源问题：
> **① `DORMANT`（对话态）不再突然出手** —— 此前玩家一靠近就挨打，对话被硬生生打断；
> **② `WAVES`（波次态）本体不再偷袭** —— 它虽然 `setInvisible(true)`，
> 但隐身只是不渲染模型，近战 Goal 仍在运行，玩家打小怪时会被"看不见的东西"攻击。

#### ② `strikePlayer` 不再硬压血条，改为绝不致死

旧写法是 `target.setHealth(maxHp * (1 - percent))` —— 这是**「把血条强行按到某个值」**，
不看当前血量。玩家残血进场时，这一下就会把血按到 50% 线以下，
而任何低于该线的血量都**无法阻止下压**，两下即 100% 必死。

**修法**：改为常规扣血 `target.hurt(generic, damage)`（因此护甲与减伤重新生效），
并加两条保底：

| 保底 | 说明 |
|---|---|
| `DUEL_MIN_HEALTH = 6.0` | 结算后至少留 6 点血 —— 这一击是**震慑演出**，不是杀人手段 |
| `Math.min(6.0, maxHp * 0.5)` | 低血量上限的玩家（如 10 点血）按比例再降，避免"保底反而加血" |

#### ③ 试炼结束补满生命

`enterWaves()` 现在会把 96 格内的存活玩家 `setHealth(getMaxHealth())`。
理由：这一击本就是叙事环节，若让玩家带着 6 点残血直接进五波清场，
那是"必死"而不是"挑战"。

配套把字幕从「**你的实力太弱了，先练一练再来**」改为
「**还算……站得住。他收剑退开，身影没入阴影——试炼到此为止。**」
—— 原文案会让玩家误以为"打不过就是失败"，而实际上这是**必然通过**的剧情演出。

#### ④ 木剑改为只装一次

`equipWoodenSword()` 原来写在 `if (!ghostStruck)` 里，等于**每 tick 都调一次**
`setItemInHand` —— 会反复重置主手物品与挥砍动画，也是"看起来打了很多下"的诱因之一。
现改用独立的 `ghostSwordEquipped` 标记，进入幽灵态时只装一次
（与 `ghostStruck` 语义分开：一个是「装备」，一个是「出手」）。

#### ⑤ 配置界面滚动错位

**症状**（截图）：滚动到中间位置时，界面顶部会横着糊出一排
`config.los...` 的原始语言键文字，与标题栏、侧边栏叠在一起。

**根因**：行是按 `y = contentTop() - scroll` 逐个排的，
但 `rebuildRows()` 只判断「**部分可见**」(`y + ROW_H >= contentTop() && y <= contentBottom()`)
就建控件，而 `renderContent()` 里 `gfx.drawString(...)` 画名称时**完全没有裁剪**。
于是首尾两行"半露"在内容区外的文字就画到了标题栏和页脚上。

**修法**（两处一起，缺一不可）：

| 位置 | 改动 |
|---|---|
| `rebuildRows()` | 判定改为「**完整可见**」：`y >= contentTop() && y + ROW_H <= contentBottom()`。因为原版控件一旦 `addRenderableWidget` 就按自己的坐标无条件绘制，**无法被裁剪**，所以只能不给它建 |
| `render()` | 选项区包一层 `gfx.enableScissor(contentX() - PAD, contentTop(), width, contentBottom())` … `disableScissor()`，让文字即便算错位置也画不出内容区 |

顺带修正了步进型数值的定位公式 —— 原来写死 `cx + cw - 142 - font.width(v) + 30`
（`-142 + 30` 是没化简的魔数），收敛为 `drawValue()` 辅助方法，
按两个按钮的中点 `cx + cw - 68` 居中，并让「已改动」时数值也用琥珀色。

---

### 35.8 `dv11` —— 取消神化前置 + 换阶段造型演出 + Boss 持剑修复

玩家的四条反馈：

> 「取消 Apotheosis 前置，Apothic Attributes 这个为前置就够了」
> 「boss 换阶段没换建模」「换阶段什么特效都没有，不帅」
> 「换阶段 30 秒内逐渐回满血，给准备时间」「boss 手里没木剑」

#### ① 取消 Apotheosis 硬前置

原先声明了 `apotheosis` + `placebo` + `attributeslib` 三个必需依赖。
但实际上**代码里只 import 了一个类**：

```bash
$ grep -rh "^import dev.shadowsoffire" src/main/java/ | sort -u
  dev.shadowsoffire.attributeslib.api.ALObjects;      ← 只有一个
```

`attributeslib`（Apothic Attributes）才是提供 `armor_pierce` / `crit_chance` /
`life_steal` 等属性的模组，而神化（Apotheosis）的附魔/词缀系统与本模组**毫无关系**。

**改法**：从 `mods.toml` 删掉 `apotheosis` 依赖声明，保留
`attributeslib` + `placebo`。玩家少装两个模组。

| 依赖 | 改动前 | 改动后 |
|---|---|---|
| `apotheosis` | `mandatory=true` | **已移除** |
| `attributeslib` | `mandatory=true` | `mandatory=true`（保留） |
| `placebo` | `mandatory=true` | `mandatory=true`（保留，attributeslib 的自身前置） |

> **↑ 上表是 `dv11` 当时的结论，已被 `beta30` 覆盖**：属性改由本模组自注册后，
> `attributeslib` 与 `placebo` 也一并移除。现在 `grep -rh "^import dev.shadowsoffire"`
> 的输出是**空的**，`mods.toml` 里不再有这两条依赖。详见「一、硬性依赖」。

#### ② 修复「Boss 手里没木剑」

**根因不在服务端** —— `setItemInHand` 执行了、物品确实在实体手上。
问题在渲染侧：

> `MobRenderer` **本身不渲染手持物品**。它只是个 `LivingEntityRenderer`，
> 构造器没有任何渲染层。原版僵尸能举剑，是因为
> `HumanoidMobRenderer` 在自己的构造器里显式
> `addLayer(new ItemInHandLayer<>(...))`。
> 我们直接继承 `MobRenderer` 就**绕过了那一层**。

**改法**：在 `WardenRenderer` 构造器里补上：

```java
this.addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
```

并在冲脸时每 8 tick 调一次 `swing(MAIN_HAND, true)`，出手前也挥一下 ——
让「手持木剑冲脸」在画面上真正看得见。

> 这也解释了为什么 `DORMANT` 形态看起来"空手"：它本来就没装备物品。
> 修完这条后，幽灵态才有剑。

#### ③ 换阶段「没换建模」

两道问题叠加：

**问题 A：各形态体型差得太小。** 原来是 `1.30 → 1.75 → 2.30`，
相对变化只有 35% / 31%，人形怪在远处几乎看不出。现在拉开为
**`1.00 → 1.45 → 2.00`**（首尾 **2 倍**）。

**问题 B：换阶段期间体型完全不动。** `TRANSITION_1/2` 沿用了上一形态的体型，
所以玩家在那段时间看到的是"一个静止的东西在回血"。

**改法**：演出期间做**动态体型插值**。关键是它必须**走同步数据**：

```java
private static final EntityDataAccessor<Float> DATA_TRANSITION_PROGRESS =
        SynchedEntityData.defineId(WardenBoss.class, EntityDataSerializers.FLOAT);
private static final EntityDataAccessor<Float> DATA_TRANSITION_FROM_SCALE = ...;
private static final EntityDataAccessor<Float> DATA_TRANSITION_TO_SCALE = ...;

@Override public float getScale() {
    if (isTransitioning()) {
        float from = entityData.get(DATA_TRANSITION_FROM_SCALE);
        float to   = entityData.get(DATA_TRANSITION_TO_SCALE);
        float p    = entityData.get(DATA_TRANSITION_PROGRESS);
        float eased = 1.0F - (1.0F - p) * (1.0F - p);   // easeOutQuad
        return from + (to - from) * eased;
    }
    return getLordState().scale();
}
```

> **为什么不放普通字段**：体型插值原本只算在服务端（改一个 `float` 字段），
> 客户端根本读不到 —— 结果是**服务端碰撞箱在长大、客户端模型不动**。
> 做成同步数据后两端用同一公式算出同一个值。

顺带修了两个同源的坑：

| 坑 | 症状 | 修法 |
|---|---|---|
| 缺 `onSyncedDataUpdated` | 自定义 `DATA_STATE` 不触发 `refreshDimensions()` → 客户端**模型变大但碰撞箱不动**，看着大却打不到、或被卡在方块里 | 覆写 `onSyncedDataUpdated`，在 `DATA_STATE` / `DATA_TRANSITION_PROGRESS` 变化时刷新尺寸 |
| `isTransitioning()` 读服务端字段 | 客户端永远认为"不在演出中" → 体型插值整个失效 | 改走 `getLordState()`（客户端读同步数据） |

#### ④ 换阶段「什么特效都没有」

补了三层演出：

| 阶段 | 演出内容 |
|---|---|
| **开场** | 三道 `EXPLOSION_EMITTER`（主体 + 左右两翼）、120 颗魂火从脚底冲顶、三重音效（`GENERIC_EXPLODE` + `ENDER_DRAGON_GROWL` + `WARDEN_ROAR`） |
| **期间**（30 秒） | 每 2 tick 灵魂柱、每 4 tick 魂火环、每 20 tick 冲击波（爆炸声**音调随进度 0.7→1.3 升高**）、每 15 tick 心跳（`RESPAWN_ANCHOR_DEPLETE`，**音调 0.6→1.4 递增**） |
| **收尾** | 80 颗 `END_ROD` 向上爆开 + 8 道巨型爆炸 + 龙吼，宣告新形态登场 |

另外做了两件让"变身"读得出来的事：

- **描边与血条提前换成新形态的颜色** —— `TRANSITION_1` 显**红**（即将变成第二形态）、
  `TRANSITION_2` 显**白**（即将变成第三形态）。玩家在 30 秒里就能看着它朝那个颜色变，
  而不是看完演出才突然跳色。
- **演出期间锁定位置** —— 时长拉到 30 秒后，若不锁死位置，
  玩家用击退手段就能把它推出竞技场，演出结束后它会从奇怪的地方"复活"。

#### ⑤ 换阶段改为 30 秒，明确作为准备时间

`DEFAULT_TRANSITION_HEAL_SECONDS` 由 `4` 改为 **`30`**，
配置范围由 `[1, 30]` 放宽到 **`[5, 120]`**（给整合包作者留余地：
硬核包拉到 2 分钟慢慢演出，速通包压到 5 秒）。

字幕也同步改口 —— 原文案只描述现象，现在明说这是**玩家的窗口期**：

> ~~「它的躯壳裂开了 —— 生命被钉死在最后一格。」~~
> 现：「它的躯壳裂开了 —— 生命被钉死在最后一格。**它开始重新凝聚，趁现在！**
> （药水 / 走位 / 技能冷却）」

---

### 35.9 `dv12` —— 修复「用 `/summon` 召唤的 Boss 半残」

玩家问「召唤 boss 的指令」，随后决定 **不加自定义指令，就用原版 `/summon`**。
顺着这条线查下去，发现了一个真实缺陷。

#### 原版召唤指令（无需任何自定义命令）

```mcfunction
/summon lost_realm:warden ~ ~ ~            # 遗落之主（终局 Boss）
/summon lost_realm:warden_sentinel ~ ~ ~   # 遗落哨卫（小怪）
```

`BossEntities` 里两个实体**都没有调 `noSummon()`**，所以开命令方块或作弊模式即可直接召唤。

#### 缺陷：命令召唤出来的 Boss 是「半残」的

`WardenFightSession`（战斗会话）掌握着 Boss 的**血条、`WaveManager` 五波刷怪、
参战者追踪** —— 几乎是这场仗的全部内容。但它**只在一条路径上被创建**：

```java
// BossPortalHandler#spawnLordIfAbsent —— 唯一的 session 绑定点
```

也就是说，走传送门正常进维度的 Boss 有会话，而 **`/summon` 出来的 Boss
`session == null`** —— 后果是：

| 表现 | 原因 |
|---|---|
| 顶部血条永不出现 | `session` 为空，没有 boss event |
| 打完形态不进入五波清场 | `WaveManager` 从未启动 |
| 参战者列表 / 掉落判定失效 | 追踪器未初始化 |

实体本身能动、能挨打，但整个战斗流程是断的 —— 玩家看到的「半残」正是如此。

#### 修法：实体侧自绑兜底（`WardenBoss#ensureSessionBound`）

原则是 **「按维度唯一的会话类，不能把创建权绑在某一条入口路径上」**。
让 Boss **自己保证有会话**：

```java
// aiStep() 内，每 20 tick 检查一次
if (tickCount % 20 == 0) {
    ensureSessionBound();
}

private void ensureSessionBound() {
    if (session != null) return;                       // 已绑定，直接返回
    if (!(level() instanceof ServerLevel sl)) return;  // 只在服务端做
    WardenFightSession existing = WardenFightManager.session(sl);
    if (existing != null && !existing.isFinished()) {  // 复用本维度已有会话
        bindSession(existing);
        return;
    }
    WardenFightSession s = WardenFightManager.start(sl, getUUID(), getName());
    bindSession(s);
    Medals.LOGGER.info("[Lost Realm] 遗落之主(命令召唤)已自动绑定战斗会话。");
}
```

关键在于**「先查后建」**：如果本维度已经有活跃会话（例如 Boss 是被传送门正常生成的、
或有第二个 Boss 被召唤），就**复用**而不是覆盖 —— 避免两个 Boss 各持一份会话、
血条打架。20 tick 的间隔足够让召唤后第一秒内血条就出现，
又不至于每 tick 都做一次 map 查询。

> **日志线索**：`latest.log` 里出现
> `[Lost Realm] 遗落之主(命令召唤)已自动绑定战斗会话。`
> 即表示这条兜底路径被触发了。

#### 同时补齐的英文语言键

交付自检时发现 `en_us.json` **缺 12 个技能描述键**
（`t5_heavy_strike` / `w30_blood_intent` 等，zh_cn 有、en_us 没有），
英文玩家会看到技能描述空白。已按中文原文逐条补译，
并校验**占位符 `%s` 数量逐键一致（0 处错位）**。

| 指标 | 修复前 | 修复后 |
|---|---|---|
| `zh_cn` 键数 | 494 | 494 |
| `en_us` 键数 | 482 | **494** |
| 技能键缺口 | 12 | **0** |
| `%s` 占位符错位 | — | **0** |

---


`dv1` ~ `dv5` 的 jar **在正式环境从未成功加载过**。原因是缺陷叠加，
`dv6`/`dv7` 逐一修正 —— 这也是本模组**首个真正可运行的版本**。

#### ① `mods.toml` 的 `version` 不是合法 Maven 版本号

```
InvalidModFileException: Illegal version number specified dv5 (Lost Realm-dv5.jar)
    at net.minecraftforge.fml.loading.moddiscovery.ModInfo.<init>(ModInfo.java:77)
```

Forge 会按 **Maven 版本号规范**校验 `mods.toml` 的 `version`，
**要求必须以数字开头**。而 `dv1`/`dv2`/…/`dv5` 全部以字母开头 → 直接拒绝加载整个模组，
表现为启动时弹「**文件 X 不是有效的Mod文件**」。

**修法**：把两个用途拆开（详见项目规则 §1.1）。

| 字段 | 值 | 用途 |
|---|---|---|
| `mod_version` | `1.0.0`（**基底，通常不动**） | 主版本基底，保证拼出来以数字开头 |
| `mod_build` | `beta1`（每次 +1） | β 版序号，同时驱动 mods.toml 的 `version` 与 jar 文件名 |

> **`beta1` 起的变化**：`mods.toml` 的 `version` 不再是恒定的 `1.0.0`，
> 而是 `1.0.0-beta1`（模组列表里显示成「Lost Realm 1.0.0-beta1」），
> 且 **jar 文件名与之完全一致**：`Lost Realm-1.0.0-beta1.jar`。
> 已核实 Forge 的校验正则是 `^\d+.*`，**只要数字开头即可** ——
> 当年 `dv1`~`dv4` 崩掉的原因是**字母开头**，不是"含字母"。
> 拼接写在 `build.gradle` 里：`internalVersion = "${mod_version}-${mod_build}"`。

> **排查提示**：这个报错**看起来像文件损坏或 Java 版本问题**，
> 极易误判。真正的线索是 `latest.log` 里那行 `InvalidModFileException`。

#### ② `EffectHudMixin` 的 `@Shadow` 字段没有生成 refmap 映射

```
[main/FATAL]: Mixin apply failed lost_realm.mixins.json:client.EffectHudMixin
  -> net.minecraft.client.gui.Gui: InvalidMixinException: @Shadow field minecraft
  was not located in the target class net.minecraft.client.gui.Gui
```

`@Shadow` 了 `Gui` 的 `minecraft` / `screenWidth` / `screenHeight` 三个字段。
它们**确实存在于 SRG 映射**（`minecraft -> t` / `screenWidth -> S` / `screenHeight -> T`），
但注**解处理器在 `reobfJar` 阶段没有为它们生成 refmap 条目** ——
产出的 `lost_realm.refmap.json` 里该 Mixin **只有 `renderEffects` 一条方法映射，字段三条全缺**，
运行时 Mixin 拿着字面量名字找不到目标 → APPLY 失败 → 启动崩溃。

**修法**：彻底删除 `@Shadow`，改为公开 API：

| 原来 | 现在 |
|---|---|
| `@Shadow Minecraft minecraft` | `Minecraft.getInstance()` |
| `@Shadow int screenWidth` | `getWindow().getGuiScaledWidth()` |
| `@Shadow int screenHeight` | `getWindow().getGuiScaledHeight()` |

> **为什么开发环境不报错**：Parchment 映射下编译期能找到这些名字，**0 error**；
> 问题只在 `reobfJar` 重命名后才暴露。因此新增交付自检项：
> **refmap 每条映射的值必须含 `m_`（方法）或 `f_`（字段）前缀**。


#### ③ `dv7`：`EnchantmentMenuMixin` 的 `@Shadow` 复发同一问题

`dv6` 修好了 `EffectHudMixin`，但 `EnchantmentMenuMixin` 因为同样用了 `@Shadow`
而在启动时再次崩溃：

```
[FATAL]: Mixin apply failed ...:EnchantmentMenuMixin
  -> net.minecraft.world.inventory.EnchantmentMenu:
  InvalidMixinException: @Shadow field enchantSlots was not located in
  the target class net.minecraft.world.inventory.EnchantmentMenu
```

**这次找到了确定规律**（不再靠推测）。把全项目 4 处字段访问拉出来对比：

| Mixin | 机制 | refmap 有字段映射 |
|---|---|---|
| `EnchantmentMenuAccessor` | `@Accessor` | ✅ |
| `MerchantMenuAccessor` | `@Accessor` | ✅ |
| `client.AbstractContainerScreenAccessor` | `@Accessor` | ✅ |
| `EffectHudMixin`（`dv6` 前） | `@Shadow` | ❌ → 崩 |
| `EnchantmentMenuMixin`（`dv7` 前） | `@Shadow` | ❌ → 崩 |

> **结论**：本项目的 Mixin 注解处理器
> **只为 `@Accessor` / `@Invoker` 生成字段映射，对 `@Shadow` 字段一律不生成。**
>
> ⚠️ **不是「继承字段」的锅**：`enchantSlots` **确实是 `EnchantmentMenu` 自己声明的**
> （SRG 名 `n`），一样没有映射。所以「自己声明就能用 `@Shadow`」是错误判断。

**修法**：新增 `@Accessor("enchantSlots")` 到 `EnchantmentMenuAccessor`，
`EnchantmentMenuMixin` 经由 `((EnchantmentMenuAccessor)(Object) this).medals$getEnchantSlots()` 取值。

**现在全项目 `@Shadow` 数量为 0**，字段访问一律走 `@Accessor` / `@Invoker`。
refmap 相应从 22 条增至 **23 条**（新增 `enchantSlots` 字段映射）。

---

### 35.10 `dv13` —— 引入 GeckoLib，让「换阶段」真正换造型

玩家的反馈：

> 「发现一个问题，换阶段，建模还是没变，而且只有体型变的话，我感觉不够帅，
> 可以加其他的东西，比如触手啊、翅膀啊什么的，不仅仅是变换大小」

#### ① 先查根因：为什么 dv11 的「体型方案」在画面上完全无效

`dv11` 用覆写 `Entity#getScale()` + `refreshDimensions()` 表达「换阶段变大」。
查证方式是直接读字节码 —— 反查 SRG 映射表可知 `m_6134_` 就是 `getScale`：

```bash
$ javap -p net/minecraft/client/renderer/entity/LivingEntityRenderer.class
  ... 方法列表里【没有】scale() ...      # 原版根本没这个钩子
$ javap -p -c ...LivingEntityRenderer.class | grep m_6134_
  (无输出)                              # render 里一次都没调过 getScale
```

**结论：原版渲染管线根本不消费 `getScale()`。** 它唯一的作用是撑大碰撞箱
（`LivingEntity#getDimensions` 内部会 `.scale(getScale())`），
而模型几何完全由 `HumanoidModel` 的固定骨骼决定 —— 于是表现为
**「服务端碰撞箱变大了、客户端模型纹丝不动」**，这正是「建模还是没变」。

更根本的限制：`HumanoidModel` 只有 **7 根骨骼**
（`head` / `hat` / `body` / `左右臂` / `左右腿`），
**没有任何可挂载触手、翅膀的位置** —— 光靠原版模型不可能做出这类造型。

#### ② 接入 GeckoLib

| 项 | 值 |
|---|---|
| 版本 | **4.4.9**（1.20.1 **Forge** 稳定线） |
| 坐标 | `software.bernie.geckolib:geckolib-forge-1.20.1:4.4.9` |
| 引入方式 | jar 放 `libs/`，`compileOnly` + `runtimeOnly` 走 `fg.deobf(files(...))` |
| `mods.toml` | 新增 `geckolib` 依赖（`mandatory=true`） |
| 初始化 | `Medals` 构造器**最前面**调 `GeckoLib.initialize()` |

> **为何不写 maven 坐标**：与 Goety 同一考虑 —— 保证离线构建不依赖外部仓库。
>
> **为何是 4.4.x**：4.7+ / 4.8+ 主要面向 NeoForge，装到 Forge 上会因
> API 不兼容而崩。4.4.9 是 1.20.1 Forge 最后一版稳定发布。

两个实体（`WardenBoss` / `WardenSentinel`）实现 `GeoAnimatable`，
渲染器从 `MobRenderer<..., HumanoidModel<...>>` 换成 `GeoEntityRenderer`。
顺带不再需要手写 `ItemInHandLayer` —— GeckoLib 自带
`ItemInHandGeoLayer` 处理手持物品。

#### ③ 三形态各自的几何

几何文件**由脚本生成**（`tools/gen_geo.py`），不依赖 Blockbench：

| 形态 | 文件 | 骨骼 | 立方体 | 造型 |
|---|---|---|---|---|
| 一 | `warden_form1.geo.json` | 12 | 11 | 人形 + 背后根芽 + 肩甲 |
| 二 | `warden_form2.geo.json` | 44 | 43 | **6 条触手**（各 5 节）+ 5 根脊椎骨刺 |
| 三 | `warden_form3.geo.json` | 74 | 73 | **8 条触手**（各 6 节）+ 7 根骨刺 + **肩部骨翼** |

触手是**骨骼链**（`tent_r_0_0 → tent_r_0_1 → …`），
每节是上一节的子骨骼 —— 这样动画里旋转每一节，就能做出「蠕动 / 卷曲」。
生成器按 `taper` 逐节变细、逐节加长，越靠根部越短粗、越靠尖端越细长。

#### ④ 动画（每形态 6~7 条）

| 动画 | 用途 |
|---|---|
| `idle` | 待机浮动 + 触手慢速蠕动（相位错开，避免整排同频摆动） |
| `walk` | 行走摆臂摆腿 + 触手随步伐甩动 |
| `attack` | 抬臂下劈 |
| `hurt` | 受击后仰 |
| `death` | 倒地 |
| **`phase_roar`** | **换阶段一**：身体后仰 → 触手由收拢猛然爆发张开 → 缓慢归位 |
| **`phase_awaken`** | **换阶段二**：骨翼展开 + 全部触手高举 + 全身挺起 |

> **换阶段动画与 30 秒回血窗口对齐**：`phase_roar` 长 3.5 秒、`phase_awaken` 长 5 秒，
> 在 30 秒的窗口里只占开头一段，剩下时间形态已变但玩家仍在准备 ——
> 既有「演出感」又不至于让玩家干等。

**关键设计**：三套动画文件里的**动画 id 同名**（都叫 `animation.warden.idle` 等），
因此实体侧的控制器**不需要感知当前形态**，只按语义状态（待机/走/打/咆哮）选动画；
「加载哪套动画文件」由模型侧 `getAnimationResource` 按形态决定。
把形态差异全部收敛到模型侧，避免换几何时控制器状态错乱。

#### ⑤ 模型缩放：这次真的生效了

`GeoEntityRenderer` 有 `withScale(float)`，但那是**构造期常量**，无法随形态变化。
形态是运行期动态的，必须每帧算 —— 所以在 `preRender` 里缩放 `PoseStack`：

```java
float s = (entity.getBbHeight() / BASE_GEO_HEIGHT) * entity.getScale();
poseStack.scale(s, s, s);
```

**为什么要除以 `BASE_GEO_HEIGHT`（2.0）**：
GeckoLib 按「1 像素 = 1/16 方块」放样几何，我们的骨架是 32 像素高
（腿 12 + 身 12 + 头 8），即几何自然高度只有 **2.0 方块**；
而 Boss 注册尺寸是 **1.2 × 4.0**。若直接渲染，模型会比碰撞箱矮一半，
表现为「看着打得到、实际判定不到」。因此先做归一化，再乘形态系数 ——
**视觉尺寸与碰撞箱严格一致**。

> 数值仍取自 `getScale()`，而它在换阶段演出期间返回**动态插值**
> （数据来自同步的 `TRANSITION_PROGRESS / FROM / TO`），
> 于是「一边回血一边长大」的观感成立，且两端数值一致、不会错位。

#### ⑥ 贴图重绘（`tools/gen_texture.py`）

原 64×64 贴图只够原版人形区域，新增部件需要自己的 UV 采样区：

| 区域 | 用途 |
|---|---|
| 左侧人形区（头部/身体/四肢） | 保留原有幽匿深紫配色，胸口核心改为**发光色** |
| `0,32–32,64` | **触手**：紫渐变（根部深 → 尖端亮）+ 随机发光斑 |
| `32,32–48,48` | **骨刺**：骨白高光，深灰底 |
| `48,32–64,48` | **骨翼**：骨刺同系，带纵向纹路 |

哨卫贴图**整体去饱和偏冷**（灰蓝调、青白眼睛），保持「未腐化」的观感对比 ——
让玩家一眼能分清「小怪」与「本体」。

#### ⑦ 四个生成脚本

| 脚本 | 产出 |
|---|---|
| `tools/uv_layout.py` | **UV 布局的唯一事实来源**（`128×128`；`gen_geo` 与 `gen_texture` 共用，`dv16` 新增） |
| `tools/gecko_math.py` | **变换模拟器 + 链式骨骼内核**（不产出文件；被 `gen_geo.py` 调用，`dv15` 新增） |
| `tools/gen_geo.py` | 三套 `.geo.json` 几何（含 UV 与矩阵世界坐标双重自检） |
| `tools/gen_anim.py` | 三套 `.animation.json` 动画 |
| `tools/gen_texture.py` | 两张 `128×128` 实体贴图 **+ 两张 `_glowmask` 自发光遮罩** |
| `tools/preview_geo.py` | **离线预览**（正视 + 等距双视图，逐面采样贴图与遮罩；`dv16` 新增） |

> 全部参数化（触手节数、粗细衰减、摆幅、相位、倾角剖面…），后续调参只需改脚本重跑，
> 不必手工在 Blockbench 里重画。固定随机种子保证贴图每次生成结果一致。
>
> **`gecko_math.py` 的意义**：它是唯一能"看到"GeckoLib 内部变换的地方 —— 用 numpy
> 复现 `M = M_parent @ T(+p) @ H @ T(-p)` 后，任何「会不会断裂 / 会不会错位」的问题
> 都能在**落盘前**用世界 AABB 断言回答，而不是等进游戏截屏才发现。详见 §35.12。

---

### 35.11 `dv14` —— 修四个画面问题 + 攻击/触手多样化

`dv13` 把 GeckoLib 接进来之后，画面上暴露了四个问题。本节按「根因 → 修法」逐条说明。

#### ① 触手长在正面，还穿过身体

**根因**：搞错了朝向约定。

> Minecraft 实体**默认朝 `-z` 看**（与玩家一致）→ **`-z` 是正面、`+z` 是背面**。
> 身体 cube 占 `z ∈ [-bd/2, +bd/2]`，所以**背部平面 = `z = +bd/2`**。

`dv13` 的 `humanoid_base` 把挂载点取成了 `-bd/2`（正面），触手从胸口生根、
再向后穿过整个身体 —— 画面上就是「触手长在正面 + 严重错位」。

**修法**：挂载点改为 `bd/2`，延伸方向改为 `z` 递增、`y` 递减（向后下方垂坠）。
验证：form2 触手 pivot `z` 从 `2.40 → 7.78` 递增，`y` 从 `22.6 → 2.02` 递减。

#### ② 触手渲染成纯白/亮红的棒子

**根因**：GeckoLib 的 cube UV 是**「盒子展开图」**，不是单点采样。

```
展开尺寸 = 2*(d + w) × (d + h)
```

若所有 cube 共用同一个 `uv` 起点，就会全部采样到 1×1 像素、拉伸成纯色块。

**为什么不能「逐 cube 独立 UV」**：面积核算表明 form3 的 40 个触手节
每个展开约 95px，合计 **3817px**；而 64×64 贴图扣掉人形区后只剩约 **1500px**
—— **根本装不下**（实测 UV 重叠 111 处）。

**修法**：改用**「同类部件共享同一块纹理」** —— 所有触手节共用一块触手图案，
所有骨刺共用一块，骨翼同理。这也是手工建模（Blockbench）的标准做法：
同一根触手的每一节本来就长得一样，没必要各占一块独立图案。

#### ③ 手脚被染成紫色

**根因**：UV 分区重叠。`dv13` 把触手共享区画成 `0,32-48,64`，
**正好压住**了原版布局里的左腿 `16,48-32,64` 与左臂 `32,48-48,64`。
触手是后画的，把这两块覆盖成紫色渐变 → 渲染出来手脚都是紫棒子。

**修法**：改成**左右分栏**，彻底消除重叠：

| 区域 | 坐标 | 用途 |
|---|---|---|
| 上半 | `0,0` – `56,32` | 头 / 右腿 / 身体 / 右臂（原版布局，一字不动） |
| 下半左栏 | `0,32` – `16,48` | 左腿 ★从 `16,48` 挪来 |
| 下半左栏 | `16,32` – `32,48` | 左臂 ★从 `32,48` 挪来 |
| 下半右栏 | `32,32` – `44,38` | 触手（紫渐变 + 发光斑） |
| 下半右栏 | `32,44` – `45,48` | 骨翼（冷灰纹路） |
| 下半右栏 | `48,32` – `55,41` | 骨刺（骨白） |
| 下半右栏 | `32,50` – `64,62` | 肩甲 |

并在 `gen_geo.py` 里加了**双自检**，任一不满足就直接报错退出：

1. **区域两两不重叠** —— 10 个矩形做 O(n²) 相交检测
2. **展开尺寸装得下** —— 每个 cube 的 `2*(d+w) × (d+h)` 必须落在它所属区域内

#### ④ 三形态太大

**根因**：双重膨胀。form3 的几何本体原先把 body/四肢整体放大到
`9×13×4.5` / `8.5` 立方，再叠上 `getScale() = 2.00`，
两者相乘后模型占满半个屏幕。

**修法**：

- form3 几何**改回与 form1/form2 一致的人形尺度**，体型差异全部交给 `scale` 统一表达
- `scale` 下调：`1.00 → 1.25 → 1.55`（原先 `1.00 → 1.45 → 2.00`）
- 触手/骨翼同步缩短（`seg_len` 4.8→3.9、`seg_count` 5→4），不再插进地面
- form3 的 `max z` 从 `14.9` 降到 **5.8**，`y最低` 从 `-2.37` 回到 **0.0**

#### ⑤ 出手动作 / 方向太统一

两个独立问题：

**(a) 攻击手势只有一套。** `dv13` 只有一条 `attack`，三个形态共用同一套
抬臂下劈 —— 玩家连着看三次就腻。更严重的是：`ATTACK` / `HURT` 这两个
`RawAnimation` 常量在 `onState` 里**永远不会被返回**（那个方法只看形态和移动），
**写了动画却没接线**。

**(b) 触手像梳子。** `idle` 里所有触手共用 `phase = 0.5 * k` 与同一速度，
6~8 条触手整齐同步摆动，毫无生物感。

**修法**：

*攻击侧*

- 拆出**三条手势**，由 `doHurtTarget` 按「攻击序号 % 3」轮换：

  | 动画 | 特征 |
  |---|---|
  | `attack_sweep` | **横扫** —— 右臂从外侧向内扫，身体反向扭转（横向） |
  | `attack_smash` | **下砸** —— 双臂高举垂直砸落（纵向） |
  | `attack_lash` | **触手抽击** —— 左右两侧触手交替甩出（form2/3） |

  用取模而不是随机，避免「连着两次下砸」的重复观感。
- 新增**一次性动作队列**（`playAction(RawAnimation, int)` + `actionTicks`），
  攻击与受击动画真正跑起来，播完自动回落待机/走。
  `actionTicks` 在 `aiStep` 里递减，且**必须在客户端也递减** ——
  GeckoLib 的动画判定跑在客户端，只在服务端递减会让动画卡在最后一帧。
- `attack_lash` 在 form1 的动画文件里退化为 `sweep`，这样实体侧
  永远不需知道当前形态，三个手势都能安心用。

*触手侧*

- `TENT_SEED` 种子表为每条触手固化一组 `(相位, 速度倍率, 横向偏置)`：

  ```python
  TENT_SEED = [(0.0, 1.00, 1.0), (1.9, 1.37, -0.7),
               (3.4, 0.78, 1.4), (5.1, 1.19, -1.1)]
  ```

- 左右两侧再加一层整体错相（`SIDE_PHASE` / `SIDE_SPEED`），避免镜像对称。
- **纵向 / 横向 / 自转用三个互不相关的谐波频率**。若三个轴共用同一个
  `sin`，触手会沿一条平面「画圈」，看起来像刚体摆动：

  ```python
  pitch = a * sin(w + phase + seg_lag)                        # 主频
  yaw   = a*0.72 * sin(w*0.63 + phase + seg_lag + 1.4) + bias # 0.63 倍频
  roll  = a*0.45 * sin(w*1.29 + phase + seg_lag + 2.6)        # 1.29 倍频
  ```

- `phase_roar` / `phase_awaken` 改**波浪式递延**：每条触手按 `k` 延后 `0.18s`，
  视觉上是**从背部向外扩散的涟漪**，压迫感远强于整齐同步。
- 左右骨翼用**不同的 `rise` / `sweep` 与展开节奏**，不做镜像对称。

#### ⑥ 去掉竞技场中央高台上方的圆盘

`LostCityBuilder#buildCentralDais` 原本在顶面上方多放了 5 个发光方块
（中心 1 格 + 四方向各 1 格，悬在 `y+4`）。从玩家视角看是一组漂浮的圆点，
与 Boss 抢视觉焦点；而且 Boss 换阶段大幅长高后，那几个方块正好卡在
模型躯干中间，**穿模明显**。

现全部移除，顶面保持干净石台，让 Boss 本身成为唯一焦点。

#### ⑦ 新增两道自检（防静默失效）

1. **骨骼名自检**（`gen_anim.py`）：校验动画引用的每个骨骼名都真实存在于
   对应的 `.geo.json`。GeckoLib 对不存在的骨骼是**静默跳过**的
   （我们覆写了 `crashIfBoneMissing()` 返回 `false`），写错一个名字不报错、
   只表现为「那段动画没生效」，极难排查。
2. **动画 id 对齐**：Java 侧 `WardenAnimations` 引用的 9 条 id
   与三套 JSON 里定义的 id 完全一一对应（脚本 + 人工双向核对）。

**dv14 自检结果**

| 项 | 结果 |
|---|---|
| `@Shadow` 实际注解 | **0**（红线未破） |
| `@Accessor` / `@Invoker` | **17 类 / 23 条**（与基线一致） |
| refmap | **17 类 / 23 条**（18 方法 + 5 字段），0 无法识别 |
| UV 区域重叠 | 无（10 个矩形两两不相交） |
| UV 展开尺寸 | 全部装得进所属区域 |
| 动画骨骼名 | 全部存在于对应 geo |
| 动画 id | Java 9 条 ↔ JSON 9 条，完全对应 |
| 语言键 | `en_us` 494 ↔ `zh_cn` 494 |
| 编译 | `BUILD SUCCESSFUL`，0 错误 |

---

### 35.12 `dv15` —— 推倒重写几何生成器，根治「散落 / 断裂 / 悬空 / 模型错位」

`dv14` 的触手在游戏里表现为「散落、断裂、悬空的紫色方块」，模型整体还偏离
碰撞箱中心（用户原话：**「碰撞箱在中间，但建模是在旁边」**）。
用户要求「这个建模还是重置一下吧，太丑了」。本节记录**字节码级**的根因定位
与修复方法论。

#### ① 根因：坐标语义冲突（不是"没调好参数"）

对 `geckolib-forge-1.20.1-4.4.9.jar` 做 `javap -p -c` 反汇编，逐方法追踪
`GeoRenderer#renderRecursively` → `prepMatrixForBone` →
`RenderUtils.translateToPivotPoint`（**★注意有两个重载**）→
`rotateMatrixAroundBone` → `BakedModelFactory$Builtin.constructCube`，
得到精确的变换公式：

```
M_bone  = M_parent @ T(+pivot) @ H(rotation) @ T(-pivot)
H       = Rz @ Ry @ Rx                    （右乘；mulPose 依次 Z→Y→X）
cube.origin = (-(ox+ow)/16, oy/16, oz/16) （block 单位，x 取负）
```

**关键推论 1**：`rotation == 0` 时 `T(+p)·I·T(-p) = I` → **pivot 被完全抵消**。
这解释了为什么 `dv14` 的 form1 人形「侥幸看起来正常」，而带 `rotation` 的
触手必然错位 —— 两者用的是同一套写法，但只有后者会触发这个 bug。

**关键推论 2**：**骨骼的 pivot 不移动骨骼本身，只定义旋转中心**。
骨骼位置完全由 `cube.origin` 决定。

`dv14` 的 `gen_tentacle_back()` 用「绝对三角累加」预计算每节 pivot，
**同时**又给每节加 `rotation` → 预计算的绝对终点被父级旋转再变换一次，
误差逐节放大。用矩阵模拟器复现实测：

```
form3 模型 AABB  x[-53.6, 18.5]  y[-35.3, 49.9]  z[-13.1, 18.0]
                 ↑横向偏 53px    ↑陷地 35px       ↑比头还高 50px
```

#### ② 新资产 `tools/gecko_math.py`：先把"裁判"建对，再改几何

用 numpy 复现上述变换链，并**分三步**建立可信度：

1. **正回归**：先用 `dv14` 已确认正确的人形逐值复现（6/6 通过），
   确认模拟器的坐标系与符号约定无误；
2. **负回归**：再复现 `dv14` 的**坏几何**（form3 AABB 达 53px 偏移），
   证明模拟器抓得住故障，而不是"什么都报 OK"；
3. **驱动新算法**：最后才用它验证新几何。

#### ③ 核心算法 `build_chain()`：用"不动点"保证不断裂

链式骨骼（触手 / 骨翼）的每一节 pivot **只取父节末端面中心**：

```
pivot[n]    = [pivot[n-1].x, pivot[n-1].y, pivot[n-1].z - seg_len[n-1]]
rotation[n] = rx[n] - rx[n-1]      （存增量角，见铁律 3）
cube.origin = [pivot.x - s/2, pivot.y - s/2, pivot.z - L]
cube.size   = [s, s, L]
```

之所以成立：`T(+P)R T(-P)` 作用到 `P` 自身恒等于 `P`（`R` 绕 `P` 转，
`P` 原地不动）→ **无论子节怎么旋转，入口永远严丝合缝贴在父节末端**。

#### ④ 调试过程中发现的四条新铁律

> 这些都是在自检反复报错后**实测反推**出来的，已固化进
> `tools/gen_geo.py` 文件头与 `.codebuddy/项目规则.md` 第 13 条。

| # | 铁律 | 违反后果 |
|---|---|---|
| 4 | **yaw 只能加在链首，且两侧用同一符号**——给已带 rx 的骨骼再叠 ry，末端 -z 面中心会绕 pivot 划圆 | 接头裂开（实测 2.1px） |
| 4b | **绝不用 `-side` 镜像 yaw**——`H = Rz@Ry@Rx` **不是** yaw 符号镜像 | 一侧翻到身体正面（这才是 dv14「一侧长正面」的真正成因） |
| 5 | 链的前进方向由 **rx 是否跨过 180°** 决定（≈180 → 世界 +z 向后；≈100 → 向前） | 触手/骨翼长到身前 |
| 6 | 配件 cube 的 z **不得以 pivot 居中跨越深度**（写成 `[o.z, o.z+d]` 而非 `[o.z-d/2, o.z+d/2]`） | 5~7px 深的肩甲捅进胸腔 2.5~3.5px |

#### ⑤ 落盘强制自检 A1~A7

`gen_geo.py` 落盘前必须全部通过，任一失败 `raise SystemExit(1)`：

| 断言 | 内容 |
|---|---|
| A1 | 所有 cube 不入地（`y_min >= -0.5px`） |
| A2 | 触手/骨翼末端在身体后方（`z_center > 2px`） |
| A3 | 末端不穿到身体前方（`z_min >= -0.5px`） |
| A4 | **链式接头误差 < 0.05px**（dv14 根因的直接回归） |
| A5 | 触手不横向飞散（`|x_center - 8| <= 13px`） |
| A6 | 人形六件套逐值复现基准表 |
| A7 | 配件不穿到身体正前方（`z_min >= -2.2px`） |

#### ⑥ 几何参数调整（本轮用户明确诉求）

- **体型回调**：三形态 `scale` `1.00 / 1.10 / 1.20`（`LordState.java`，
  `FORM_2` 自 `1.25F`、`FORM_3` 自 `1.55F`；`TRANSITION_2` 同步为 `1.10F`）
  —— 用户反馈「还是有点大」。
- **触手改蜘蛛腿式细长触须**：4 节剖面 `[-38, -22, 48, 78]`
  （先向上拱起 → 过"膝关节" → 猛地折下 → 末端向后下方扎），
  分节收细 `2.2 → 1.3`；挂载点横向外移，根部不再挤成一团。
- **骨翼改向后外侧掠出**：`droop ≈ 6`（`rx ≈ 176 ≈ 180`，让链沿世界 +z 走）
  ＋ `sweep ≈ -40`；长度 `2.2 → 1.6`、粗细 `1.5 → 1.0`，装得进 `WING_UV` 的 `13x4`。
  `sweep` 幅度上限 50°，超出后末端会越过身体中轴触发 A5。

#### ⑦ 修复前后对比（矩阵模拟实测）

| 形态 | dv14 模型 AABB | dv15 模型 AABB |
|---|---|---|
| form2 | x[-1.2, 17.2] y[0, 34.0] z[-6.8, 16.3] | x[-9.2, 16.0] y[0, 32.0] z[-4.0, 16.2] |
| form3 | **x[-53.6, 18.5] y[-35.3, 49.9] z[-13.1, 18.0]** | **x[-10.5, 16.0] y[0, 32.0] z[-4.0, 22.6]** |

横向偏移由 **53px 收拢到 10.5px**、陷地 **35px 归零**。

**dv15 自检结果**

| 项 | 结果 |
|---|---|
| UV 区域重叠 | 无（10 个矩形两两不相交） |
| UV 展开尺寸 | 全部装得进所属区域 |
| **链式接头** | **form2 18/18、form3 30/30 全绿（误差 < 0.05px）** |
| **人形基准 A6** | **6/6 逐值一致** |
| 骨骼数 | form1 12 / form2 38 / form3 56 |
| 动画骨骼名 | 全部存在于对应 geo |
| 动画 id | Java 9 条 ↔ JSON 9 条，完全对应 |
| `@Shadow` 实际注解 | **0**（红线未破；`@Accessor`/`@Invoker` 4 类 / 13 条） |
| refmap | **23 条**（18 方法 + 5 字段），0 无法识别 |
| 语言键 | `en_us` 494 ↔ `zh_cn` 494 |
| 编译 | `BUILD SUCCESSFUL`，0 错误 |

---

### 35.13 `dv16` —— 三形态造型重做 + 修掉「模型偏半格」

**用户原话**：
> 「不帅，我想要这种效果，当然，不是完全一致」（附参考图：兜帽幽影剑士 → 浮空幽魂镰刀使 → 角冠／光环／翼眼的终末之主）

dv15 虽然把「触手断裂」的**数学**修好了，但骨架是「人形 + 触手」——
**表达不了**参考图里那种「长袍幽影 + 巨镰 + 羽翼 + 光环」的造型。
dv16 把骨架整体换成**长袍幽影**，并顺手修掉一个从 dv13 起就存在的老 bug。

#### ① ★ 老 bug：模型恒定偏移半格 ★

反汇编 `GeoEntityRenderer`：

```
actuallyRender 里唯一的 PoseStack.translate 是 (0, 0.01, 0)
applyRotations 里唯一的 translate 是 Dinnerbone 彩蛋（只动 y）
→ 没有任何水平居中补偿 ⇒ 模型空间 (0,0,0) 就是实体脚底中心
```

再用 GeckoLib **自带的官方实体模型**交叉验证（直接读 JSON，不靠猜）：

| 官方模型 | body cube | x 范围 | 中心 |
|---|---|---|---|
| `mutant_zombie` | `o=[-4,12,-2] s=[8,12,4]` | `[-4, 4]` | **0** |
| `cool_kid` | `o=[-6,4,-3] s=[12,10,6]` | `[-6, 6]` | **0** |
| `creeper` / `gremlin` / `bat` | — | 对称 | **0** |

而本项目 dv13~dv15 一直把身体中心放在 **x = +8**（方块坐标 `0..16` 的习惯），
于是整个模型**恒定偏移 0.5 格** —— 这正是用户说的
「碰撞箱在中间，但建模是在旁边」。

**修复**：整棵树按 `x = 0` 重排，并新增 **A2「核心居中」断言**
（躯干／头／袍／双臂的合并 x AABB 中心 `|c| ≤ 0.6px`），防止复发。

#### ② 三形态新造型

| 形态 | 造型 | 关键部件 |
|---|---|---|
| `form1` | **兜帽幽影剑士** | 兜帽（后片／侧片／顶片／尖顶／前檐）+ 及地长袍 + 拄地巨剑 |
| `form2` | **浮空幽魂镰刀使** | 袍摆收在膝下 + 6 条长破布条 + 胸口符纹 + 巨镰（圆弧刃） |
| `form3` | **终末之主** | 尖角王冠 ×5 + 脑后光环（12 块拼环）+ 带眼羽翼 + 环绕邪眼 ×6 + 巨镰 |

全部以 `x=0` 为轴；武器／羽翼按设计偏心（这是造型需要，不算「偏移」）。

#### ③ 自发光（派上大用场的一次反汇编）

```
AutoGlowingTexture.class 里的字符串常量： "_glowmask"
```

即 GeckoLib 会去找 **同名 + `_glowmask`** 的贴图，用自发光 RenderType 再渲染一遍。
所以：

1. `gen_texture.py` 额外产出 `warden_glowmask.png` / `warden_sentinel_glowmask.png`
2. 渲染器加一层 `new AutoGlowingGeoLayer<>(this)`

兜帽里的眼睛、胸口符纹、脑后光环、翼上的眼、兵刃锋口在暗处会**自己发亮**。

#### ④ 贴图扩到 128×128，并消灭「两边各写一份 UV」

dv13 串色的根因是「几何的 UV 偏移」与「贴图画在哪」是两份各自手工维护的常量。
dv16 新增 **`tools/uv_layout.py`** 作为唯一事实来源，`gen_geo.py` 与
`gen_texture.py` 都 import 它 —— 物理上不可能再对不上。

| 区域 | 位置 | 容纳 |
|---|---|---|
| HEAD / TORSO / ARM / HOOD / HORN / HALO | 上排 | 头、躯干、臂袖、兜帽、角冠、光环 |
| ROBE / SCYTHE / WING / EYE | 中排 | 长袍、巨镰、羽翼、邪眼 |
| SWORD / PAULDRON / STRIP | 下排 | 巨剑、肩甲、破布条 |

#### ⑤ 动画全部重做

旧动画引用 `right_arm` / `left_arm` / `right_leg` / `tent_*` / `spine_*`，
而 GeckoLib 对**缺失骨骼是静默跳过**的 —— 实机表现会是「模型完全不动」。
按新骨骼（`arm_r` / `arm_l` / `robe` / `hood` / `weapon` / `tatter_*` /
`wing_*` / `halo` / `orb_*`）重写 9 条动画，**id 契约不变**。

#### ⑥ 离线预览工具

`tools/preview_geo.py` 升级为「正视 + 等距」双视图，
**逐面**采样 `warden.png` + `warden_glowmask.png`（自发光面直接用 glowmask 颜色），
可以在不进游戏的情况下看造型。

#### 交付自检

| 项 | 结果 |
|---|---|
| **核心居中 A2** | **通过**（躯干／头／袍／双臂 x 中心 ≤ 0.6px） |
| 核心基准 A3 | 通过（body x`[-4,4]` / head x`[-4,4]` / arm x`±[4,8]`） |
| 不入地 A1 / 规模 A4 | 通过 |
| UV 区域重叠 | 无（13 个矩形两两不相交） |
| UV 展开尺寸 | 全部装得进所属区域 |
| 骨骼数 | form1 16 / form2 18 / form3 40 |
| 画布 | `128×128` |
| 动画骨骼名 | 全部存在于对应 geo |
| 动画 id | Java 9 条 ↔ JSON 9 条 ×3 套，完全对应 |
| 自发光遮罩 | `warden_glowmask.png` / `warden_sentinel_glowmask.png` 均在 jar 内 |
| `@Shadow` 实际注解 | **0**（红线未破） |
| refmap | **23 条**，0 无法识别 |
| 语言键 | `en_us` 494 ↔ `zh_cn` 494 |
| 编译 | `BUILD SUCCESSFUL`，0 错误 |

---

### 35.14 `dv17` —— 二阶段模型接入外部 AI 产出的 glb

**用户原话**：
> 「我当然知道不可能一模一样，就是想让你尽量一样，这边用其他 AI 弄好了模型，你来试试看，加进去。」

#### ① 把 Blockbench 导出的 glb 转成 GeckoLib 资产

外部 AI 产出的是 **glTF/GLB（三角面）**，而 GeckoLib 1.20.1 只能读 **立方体骨骼模型（`geo.json`）**。因此写了一个转换器 `tools/glb_to_gecko.py`：

- 解析 112 个节点、92 个 mesh，发现每个 mesh 恰好 24 顶点 = 一个**轴对齐立方体**；
- 从节点 `TRS` 反解出 113 根骨骼的 `pivot`、`rotation`、`cube.origin/size`；
- **贴图**：AI 的 glb 自带 256×256 贴图，但其 UV 大量指向透明区（92 个立方里 74 个六面全采样到空白、18 个披风又都挤到同一小块），直接采样会得到 99.8% 透明的空图集（模型全黑）。因此放弃采样，改为**按部件程序化上色**：长袍/躯干深紫、披风品红、镰刃/眼睛/邪眼青色自发光，再据此生成 `warden_phase2.png` + `warden_phase2_glowmask.png`（512×512，约 1 万有色像素 + 480 自发光像素）；
- 几何与动画仍完全来自 AI 的 glb（形状保真）。

转换后自检：

| 项 | 结果 |
|---|---|
| 骨骼数 | 113 |
| cube 数 | 92 |
| x 核心中心 A2 | **0.00px** |
| 动画骨骼名 | 9 条 id 全部存在于 geo |
| cube UV | 92 / 92 非空 |

#### ② 重名骨骼分配

GLB 的左右披风每个片段都有**两个同名节点**：一个 `holder`（无 mesh，有子骨骼）和一个 `mesh`（有立方）。GeckoLib 要求骨骼名唯一，因此把 **holder 保留原动画名**（如 `cape_l_u`），**mesh 节点加 `_1` 后缀**（如 `cape_l_u_1`）。这样动画仍然能旋转 `cape_l_u`，带动它下面的 mesh 子骨骼一起飘动。

#### ③ 形态感知贴图路由

`WardenBossModel#getTextureResource` 由原本的统一 `warden.png` 改为按形态切换：

| 形态 | 几何 | 动画 | 贴图 |
|---|---|---|---|
| FORM_2 | `warden_form2.geo.json` | `warden_form2.animation.json` | `warden_phase2.png` + glowmask |
| 其它 | `warden_form1/3.geo.json` | `warden_form1/3.animation.json` | `warden.png` + glowmask |

#### ④ 对照预览工具

新增 `tools/preview_compare.py`，用同一组相机位姿同时渲染原始 GLB 与转换后的 geo，确认比例、朝向、z 轴正反面一致。

#### ⑤ 修复「加载期崩溃」（首版 dv17 的 bug，实为 `dv18`）

首版 dv17 一启动就崩：

```
GeckoLibException: lost_realm:animations/warden_form2.animation.json: Error loading animation file
Caused by: JsonSyntaxException: Missing animations, expected to find a JsonObject
```

根因：转换器把动画文件写成了 `{"warden_form2": {"format_version":..,"animations":..}}`
—— **多套了一层**。GeckoLib 要求动画文件的**顶层直接是 `{format_version, animations}`**
（与 `warden_form1/3.animation.json` 一致），不像 geo 那样带 `minecraft:geometry` 容器。
已修正，并新增自检：三套 `*.animation.json` 顶层键集必须一致。

> 该修复本身也是一次内容改动，故版本号递增为 **`dv18`**。

#### 交付自检

| 项 | 结果 |
|---|---|
| GLB cube 数 ↔ geo cube 数 | 92 ↔ 92 |
| 预览对照 | 正视 + 等距双视图轮廓一致 |
| FORM_2 贴图路由 | 引用 `warden_phase2` |
| Glowmask | `warden_phase2_glowmask.png` 已生成 |
| **动画文件顶层结构** | **`{format_version, animations}`，三套一致** |
| 编译 | `BUILD SUCCESSFUL`，0 错误 |

---

### 35.15 `dv18` —— 修复 dv17 的加载期崩溃

`dv17` 首次交付后一启动就崩，日志只有一条原因：

```
[Render thread/ERROR]: Reported exception thrown!
net.minecraft.ReportedException: Rendering overlay
Caused by: software.bernie.geckolib.GeckoLibException:
  lost_realm:animations/warden_form2.animation.json: Error loading animation file
Caused by: com.google.gson.JsonSyntaxException:
  Missing animations, expected to find a JsonObject
    at ...FileLoader.loadAnimationsFile(FileLoader.java:28)
```

#### 根因：动画文件多套了一层命名

`tools/glb_to_gecko.py` 的 `build_animation` 当时返回：

```python
return {"warden_form2": {"format_version": "1.8.0", "animations": anims}}   # ❌
```

而 GeckoLib **要求动画文件的顶层直接是 `{format_version, animations}`**：

```json
{ "format_version": "1.8.0", "animations": { "animation.warden.idle": { ... } } }   // ✅
```

GeckoLib 在顶层找不到 `animations` 字段 → 抛 `JsonSyntaxException` →
在**加载期**即崩（`Rendering overlay` 阶段），表现为「一启动就崩、还没进世界」。

> ⚠️ **geo 与动画的顶层结构不同**：geo 是
> `{format_version, "minecraft:geometry": [...]}`，动画是 `{format_version, animations}`。
> 给 geo 套 `minecraft:geometry` 容器是对的，但不能顺手给动画也套一层 —— 这就是本次事故。

#### 修复

1. 改回 `return {"format_version": "1.8.0", "animations": anims}`；
2. 生成器自检同步改为遍历 `anim["animations"]`；
3. **新增交付自检**：解包 jar，确认每个 `animations/*.animation.json`
   都有非空 `animations` 字段，且三套 form 的顶层键集完全一致；
4. 该结论已写入 `.codebuddy/项目规则.md` §3 第 19 条（防止今后脚本生成时复发）。

#### 交付自检

| 项 | 结果 |
|---|---|
| form2 动画顶层 | `['format_version', 'animations']` ✓ |
| form1 / form2 / form3 顶层键集 | 三者完全一致，各 9 条 id ✓ |
| `animations` 字段非空 | 是 |
| JSON 合法性 | 58 个，0 非法 |
| 语言键 | `zh_cn 494 ↔ en_us 494`，0 缺失 |
| mods.toml | `version="1.0.0"` |
| 编译 | `BUILD SUCCESSFUL`，0 错误 |

---

### 35.16 `dv19` —— 三阶段画面整改（背后之物 / 巨长触手 / 陷地 / 描边）

用户给出两张实机截图，一次提出四个画面问题，全部修掉：

#### ① form3「背后的东西太大、悬在天上，不在 boss 背后」

量了 dv18 form3 各部件 AABB：光环 y[22.4, 43.0]（头顶才 ~35，环顶却到 43
—— 这就是"悬在天上"）、羽翼横铺 x ±20.6px（41px 宽）、邪眼最远 ±12px。

| 部件 | dv18 | dv19 |
|---|---|---|
| 光环半径 | 8.6 | 5.0 |
| 光环环心 | y=31（头顶之上） | y=28（头中线，与头平齐） |
| 光环 z | 4.2 | 4.8（紧贴后脑，头背面 z=4） |
| 光环 y 跨度 | 22.4 ~ 43.0 | 22.8 ~ 35.5 |
| 羽翼总宽 | ±20.6px | ±14px（整体缩到 70%，贴拢肩背） |
| 邪眼最远 | ±12px | ±9.2px（cube 3.4→3.0） |

> ⚠️ 修的过程中踩了个坑：初版把光环的 **z** 误写成 `HEAD_TOP-4.0`（这是
> 一个 y 值 = 28），结果光环飞到身后 1.75 格 —— 恰好是用户抱怨的另一个
> 极端「不在 boss 背后」。z 是深度不是高度，头背面在 z=+4，光环贴 z=4.8。

#### ② 触手太小没压迫感 →「巨长巨长的那种」

form3 的下摆破布条整体废弃，换成 **6 条巨长触手链**（`part_tentacles`）：

- 每条：从袍摆（y=6.5）垂地 1 节，再**沿地面向后拖尾 5 节 × 6px**，
  单条水平总长 ≈ 36px（身后铺开近 2.5 格）；
- 锚点**均匀绕一圈**分布（初版 6 条全挤在 x±4.3 的小圈里，视觉上是一捆棒子）；
- 拖尾带 ±30° 扇形展开（每节横偏 0.55px，远小于节宽，接缝被盖住）；
  不能用骨骼 rotation 做扇形 —— 动画的 rotation 会**替换**静态 rotation，
  一动扇形就弹直了；
- 贴图新增 `TENT` 区（128×128 画布 y88~128 货架）：深紫底 + 角质环纹 +
  中央发光血纹 + 吸盘亮点（暗处一条红线拖在地上，压迫感来源）；
- 动画同步新增 `sway_tentacles`：幅度沿链 ×1.35~1.70 递增（根稳梢甩）、
  相位沿链延迟 0.35 周期（行波蠕动而非整条同蹦），
  攻击/咆哮类动画给 14~22° 的大摆幅。

#### ③ form2 身体陷进地里（模型位置问题）

GLB 模型（二阶段）脚底不在 y=0：脚在 **-23.66px**（约 1.5 格在地下）。
`tools/glb_to_gecko.py` 新增 `lift_model_y`：把整棵树**刚性抬升 +25.94px**
使最低点贴地（抬升只加 pivot.y 与 origin.y，不碰旋转 → 动画/造型全不变）。
`gen_geo.py` 同步新增 `ground_lift`（form3 触手垂到脚下也需要抬回贴地，
dv19 form3 实测抬 1.15px）。

> ⚠️ 副产物：A3「核心部件基准」断言写的是未抬升的绝对 y，抬升后全线误报。
> 已改为**抬升感知**——比较前把期望 y 值补偿 `_ground_lift_dy`，x/z 不变。

#### ④ 三阶段发光描边全部移除

用户：「为什么要有高亮啊？那个边框？好烦啊，给我去掉，三个阶段都有。」
描边来源是 `LordGlowTeams` 给实体 `setGlowingTag(true)` + 队伍配色
（纯原版发光机制）。dv19 起 `applyTo` **不再点亮描边**，直接走清理路径：
`setGlowingTag(false)` + 摘除队伍 —— 连旧存档残留的描边也会被主动摘掉。
旧逻辑保留为 `applyToLegacy` 备查，想恢复只需换回一行。
（贴图级自发光 `AutoGlowingGeoLayer` 与描边是两回事，眼睛/符纹/光环
在暗处发亮的效果**保留**。）

#### ⑤ 工程防复发：form2 资产所有权

dv19 调整中险些用程序化模型覆盖 GLB 二阶段资产（geo 被覆盖而动画还是 GLB 的
→ 骨骼全失配 → 二阶段变静态木头人）。已把所有权钉死：

- `gen_geo.py` / `gen_anim.py` 的生成列表**剔除 warden_form2**，
  物理上杜绝覆盖；
- `warden_form2.geo.json` / `warden_form2.animation.json` /
  `warden_phase2*.png` 一律由 `tools/glb_to_gecko.py` 独占生成。

#### 交付自检

| 项 | 结果 |
|---|---|
| form1 / form3 贴地 | y_min = 0.00（A1 通过） |
| form2 贴地 | GLB 抬升 +25.94px 后 y_min = 0.00（原 -25.94） |
| form3 触手 | 6 条 × 6 节 = 36 节，最低 y=0.00，最远 z=33.7（身后 ~2 格） |
| 光环位置 | z[4.8, 6.4]（贴后脑）、y 顶 35.5（不再高过兜帽） |
| A2 核心居中 / A3 基准 / A4 规模 | 全部通过 |
| 动画骨骼自检 | form1 91 轨道 / form3 434 轨道，引用骨骼全部存在 |
| form2 动画（GLB） | 9 条 id，顶层 `{format_version, animations}`，未被覆盖 |
| 贴图 | `warden.png` 128×128 + glowmask（新增 TENT 区） |
| 编译 | `BUILD SUCCESSFUL`，0 错误 |

---

### 35.17 `dv20` —— 二阶段换 `.bbmodel` 管线 + 三阶段触手重做 + 描边残留根治

#### ① 为什么从 `.glb` 换成 `.bbmodel`

dv17~dv19 的二阶段吃的是外部 AI 直接导出的 `.glb`：

- GLB 只有「节点 + 三角网格」，GeckoLib 只有「骨骼 + 轴对齐立方体」
  → 每个网格只能压成**包围盒**（上次恰好 92 个方块才没崩）
- GLB 的 UV 是任意三角面 UV，和标准盒展开对不上
  → **只能程序化重画贴图**（dv19 那次 atlas 99.8% 透明就是这个原因）

`.bbmodel` 是 Blockbench 的工程文件，本质上就是 GeckoLib 的原生数据，
所以 `tools/bbmodel_to_gecko.py` 是**逐值搬运**而不是「猜」：

| | 源 `.bbmodel` | 转换结果 |
|---|---|---|
| 骨骼 | 17 | **65**（48 个由旋转立方体包装而来） |
| 立方体 | 97 | **97** |
| 贴图 | 256×256 内嵌 | **512×128** 盒展开图集 + 发光遮罩 |
| 动画 | 3 条（idle/walk/attack） | **9 条**（补齐契约） |
| 高度 / 原点 | 64px、脚底 y=0、x 居中 | 一致，**无需旋转、无需抬升** |

#### ② 旋转立方体 → 包一层骨骼（48 个）

护肩、剑刃、下摆、腰带这些立方体**自身带旋转**。GeckoLib 的 cube 级旋转支持面窄，
而骨骼旋转一定支持、语义又完全等价：

```
bone{ pivot = element.origin, rotation = element.rotation }  +  轴对齐 cube
```

在 GeckoLib 里就是 `M = T(p)·H·T(-p)`，与 Blockbench「绕 origin 旋转」一致 ——
**逐值保真、零近似**。48 个全部如此处理（实测它们都有真实枢轴，没有默认 `[8,8,8]`）。

#### ③ 逐面 UV → 盒展开图集

bbmodel 是 `box_uv=false` 的逐面 UV，GeckoLib 的 cube 只有单个 uv 左上角。
两个约定都是「从外侧看」，所以**直接裁面粘贴、不翻转**：

```
up→top   down→bottom   east→right   north→front   west→left   south→back
```

图集 512×128（货架式装箱），并按「亮 + 偏黄」挑出金色符文做自发光遮罩。

#### ④ 三阶段触手重做（用户：要长在背上、像翅膀、至少比人长两倍、会蠕动）

- 锚点从**袍摆**移到**背部肩胛**（挂 `body` 下），每侧 2 条、共 4 条
- 每条 30 节 × 3.2px = **96px ≈ 人形高的 2.1 倍**；静止姿态是
  「上扬 → 展开 → 微垂」的翼弧（全程轴对齐 cube，不写骨骼 rotation）
- 节距 3.2px < 节宽 4.4px → 珠子两两重叠，修掉 dv19「一串断开的点」
- 蠕动改为**温和行波**（每节 2~9°、相位沿链延迟）——
  dv19 用 `amp × 1.35^深度` 导致末梢累计上百度乱甩，实机就是「一地乱飞的红色方块」

#### ⑤ 描边残留根治

dv19 只在**换阶段**时清描边，但**旧存档载入的实体**带着 `Glowing` 标记 +
计分板队伍身份被原样还原，没人去摘 → 边框照旧显示。
现在 `readAdditionalSaveData` 里也主动清一次。

#### ⑥ 新工具：真贴图预览渲染器

`tools/render_preview.py` —— z-buffer 软件光栅（正交投影下每个面是平行四边形，
精确反解 UV 逐像素采样）。旧预览把每个面填平均色，看不出贴图对不对。
过程中量出并修掉两处错误：

- 贴图整体**上下颠倒**（与图集槽位逐像素比对：MAE 38.2 → 3.3）
- 正视投影画成了**背面**（横轴该用 `-x`：相机在 -z 侧时屏幕右 = cross(视线, up) = -x）

#### 交付自检

| 项 | 结果 |
|---|---|
| 动画顶层结构 | 三套均 `['format_version','animations']`，各 9 条 id ✓ |
| 几何 | form1 16 骨骼/27 立方（128²）、form2 65/97（512×128）、form3 154/183（128²），重名 0、内部键泄漏 0 |
| 贴图 | 6 张，尺寸与 geo 声明的 `texture_width/height` 一致 |
| `mods.toml` | `version="1.0.0"` ✓ |
| 语言键 | `zh_cn 494 ↔ en_us 494`，差异 0 |
| JSON 合法性 | 35 个，0 非法 |
| refmap | 已生成 ✓ |
| 编译 | `BUILD SUCCESSFUL`，0 错误（19 条既有 deprecated 警告） |

---

### 35.18 `dv21` —— 建模从零重做：人形基础 + 触手

用户对 dv16~dv20 的「长袍幽影 / 巨镰使 / 终末之主」不满意，要求：
> 「重新从零开始做，用人的模型来做，换阶段不会变大，
>   一阶段到二阶段会长出很多随机蠕动的触手，要长，两倍身体长度」

#### ① 三形态共用一套人形

经典 Minecraft 人形比例，32px = 2 格：头 8 / 躯干 12 / 腿 12，手臂 4 宽。
阶段差异**只体现在长出来的东西**：

| 形态 | 内容 |
|---|---|
| form1 | 人形 + 拄地巨镰 |
| form2 | 人形 + 巨镰 + **10 条触手**（背部 8 + 双臂各 1） |
| form3 | 人形 + 巨镰 + **16 条触手**（背部 12 + 双臂各 2）+ 头冠 |

触手长度 16 节 × 4px = **64px = 2 倍体长**。

#### ② 换阶段不再变大

`LordState` 里 FORM_1/2/3 的 `scale()` 全部改为 **1.00**（原来 1.00/1.10/1.20），
锁血演出（`TRANSITION_*`）也不再放大 —— 8 处 scale 全部 1.00。
生成器里另加断言：三形态人形高度必须相同（实测均 40.2px，含镰刀刃尖）。

#### ③ ★ 触手散架的真正根因与正确写法 ★

dv19/dv20 的触手是「每节骨骼的 pivot = 珠子自己的中心」，
**静止姿态完全正常，一播动画就炸开**（用户截图里天上那些悬浮方块）。
用户上次给的反馈「触手直接长在脚上 / 一堆乱飞的红色方块」就是它。

正确写法（本次采用，并加了数值断言）：

- 第 k 节的 **pivot = 第 k-1 节的末端（关节）**，cube 只是摆在该节的位置上；
- 于是「绕 pivot 旋转」= 「绕关节弯折」，子节连同其后代一起被带过去，
  关节永远严丝合缝，链可以任意弯曲而不断裂；
- 动画幅度必须小：每节 2.0°→4.0°（线性缓增）、相位沿链只延迟 1 个周期
  ⇒ 末节累计约 45°，形成柔和的 S 形行波，而不是被搅成一团；
- 节距 4.0px < 节宽 4.8px ⇒ 相邻节永远重叠，不会看成「一串断开的点」。

`gen_anim.py` 的 `self_check_joints()` 会在落盘前按动画逐帧复算，断言
①相邻节中心距恒定、②链条「跨度/弧长」不得小于 0.55（卷成一团就报错）、
③根部不得被甩离躯干 —— 这三种表现把「散架」堵死在生成阶段。

#### ④ 预览器：真贴图 + 动画姿态

`tools/render_preview.py` 升级为 **z-buffer 软件光栅**，并新增
`--anim <id> <t>` 可以直接渲染**动画某一帧**。
这次修复了两个把预览画错、进而误导判断的 bug：

- **单位铁律**：`gecko_math` 全程用 **block**（GeckoLib 内部空间，且 x 取负）。
  预览器早期又乘了一次 16，等于把 pivot 平移缩小 16 倍 ——
  带 rotation 的骨骼（触手/头冠）旋转中心退化成原点，全错位。
- form2 的贴图映射残留了 bbmodel 时代的 `warden_phase2.png`，
  导致预览配色完全不对。

#### ⑤ 工程收尾

- bbmodel/GLB 管线**弃用**：三形态统一由 `gen_geo.py` + `gen_anim.py` +
  `gen_texture.py` 生成，共用同一张 128×128 贴图（`warden_phase2*.png` 已删）。
- `uv_layout` 精简为 7 个区域（HEAD/TORSO/ARM/LEG/HORN/SCYTHE/TENT）。

#### 交付自检

| 项 | 结果 |
|---|---|
| 三形态体型 | 人形高度均 40.2px；LordState 8 处 scale 全 1.00 |
| 贴地 | 脚掌 y≈0（无抬升、无陷地） |
| 触手 | form2 160 节 / form3 256 节；关节无断裂、无卷曲、根部未甩飞 |
| 动画 | 三套各 9 条 id，顶层 `{format_version, animations}`；引用骨骼全部存在 |
| 贴图 | 4 张 128×128（含 glowmask） |
| mods.toml | `version="1.0.0"` |
| 语言键 | 494 ↔ 494，差异 0 |
| JSON | 35 个全部合法；refmap 已生成 |
| 编译 | `BUILD SUCCESSFUL`，0 错误 |

---

### 35.19 `dv22` —— 触手观感修正（太粗 / 一节节方块）

用户反馈：「触手太大了，而且全都是方块组成起来的，不像触手」。

| 项 | dv21 | dv22 |
|---|---|---|
| 剖面粗细 | 4.8px | **2.9px**（约人体宽的 1/5） |
| 节距 / 节数 | 4.0px / 16 | **2.7px / 24**（总长仍 64px = 2 倍体长） |
| 每节形状 | 立方体「珠子」 | **沿走向拉长的方块** |
| 每条长度 | 全部相同 | 错落（21~26 节） |

**关键改动：不再摆"珠子"，改成沿走向拉长的方块。**
在走向占优的那根轴上，把方块拉长到覆盖整节并留 20% 重叠，另外两轴只留细剖面 ——
相邻节于是变成"错半个身位的台阶"，拼起来是一根连续的管子，
而不是一串能被一眼数出来的珠子。这一步比单纯缩小尺寸有效得多。

配套：
- 动画每节幅度同步调小（1.2~4.6°）——节数翻倍会让累计角度也翻倍，不调就会卷起来；
- `gen_anim.py` 改为**从 geo 实测**每条链的节索引，不再写死（节数错落后写死会引用到
  不存在的骨骼，GeckoLib 静默跳过 → 那条触手整条不动）；
- 触手贴图改成整块区域铺「深紫渐变 + 一条 1px 血纹」，任何子矩形落上去都合理。

自检：三套动画 9 条 id 齐全、动画引用的骨骼 0 缺失、三形态体型一致、脚掌贴地。

---

### 35.20 `dv30` —— 巨镰改为「手持物品」，Boss 手里握真正的镰刀

**背景**：dv29 ~ dv29.11 用 GeckoLib 长方体反复重写了 12 版镰刀几何，
用户始终不满意（"依然是用一个一个方块拼起来的"）。根因是 GeckoLib 只有长方体
元素，参考图那种流畅的新月弧堆不出来；手搓几百个方块也不现实。

**改法**：把镰刀注册成独立物品 `lost_realm:warden_scythe`，
贴图直接取自参考图（128×128，程序化去棋盘格背景 + 去水印 + 旋转成竖直构图），
造型 100% 忠于原图，由新写的渲染层挂到 `weapon` 骨骼上。

| 改动 | 内容 |
| --- | --- |
| ① 物品 | `ScytheItem` + `BossItems.WARDEN_SCYTHE`；EPIC / 防火 / 不可堆叠；入创造标签页 |
| ② 模型几何 | `part_scythe()`（9 根骨骼 / 数十个立方体）整块删除，换成 `part_weapon_anchor()` —— 一个无 cube 的空锚点，pivot 落在右手 `(-18, 36, 0)` |
| ③ 渲染层 | `WardenItemInHandLayer`：GeckoLib 4.4.9 **没有** `ItemInHandGeoLayer`（那是 4.5+ 才有，而我们锁 1.20.1 Forge 稳定线），改用通用的 `BlockAndItemGeoLayer`，只在 `weapon` 骨骼上放物品、上下文指定 `THIRD_PERSON_RIGHT_HAND` |
| ④ 物品模型 | 显式 `elements`（不是 `item/generated`）：quad 原点按贴图中的握把像素算出，保证握把正好落在模型 `(8,8)` = 手中；`display.thirdperson_righthand` 缩放到 **4.0**（对照：Goety 镰刀模板 2.36 / 原版剑 0.85） |
| ⑤ 装备逻辑 | `WardenBoss#setLordState`：进入 `FORM_1/2/3` 与两次演出态自动握镰；幽灵态木剑不受影响 |

**三个关键结论（踩坑记录）**

1. **`ItemTransform` 的 UV 与旋转**：Goety 的 `abyss_staff` 证明模型 UV 恒在
   `0..16` 空间、与贴图分辨率无关，所以 128×128 贴图 + `uv [0,0,16,16]` 是安全的。
   而 Goety/Vanilla 剑的 `rotation [0,-90,55]` 会把物品平面转到 y-z 平面 ——
   对玩家手里的剑无所谓（相机在侧后方），但 Boss 是被围观的，**正面看只剩一条线**，
   所以本版 `ry=0`，让纸片正对镜头。
2. **握把必须在贴图中心附近，否则要用显式 elements**：握把离物体一端只有 12%，
   若强行居中构图，贴图长度利用率只有 57%，刀尖根本举不高。
   改成"物体铺满贴图 + elements 原点补偿"后利用率 = 100%，
   同样缩放下刀尖高度从 `y≈70` 提到 `y≈87`（人形头顶 96）。
3. **`translateAndRotateMatrixForBone` 不会平移两次**：反汇编确认它读的是
   骨骼**原始 pivot**（`CoreGeoBone#getPivotX`），而 `renderRecursively`
   在调用图层前已做过 `translateAwayFromPivotPoint`，两者正好抵消。

自检：几何自检全过（form1 从 ~90 立方体降到 6 个）、jar 内含
`models/item/warden_scythe.json` + `textures/item/warden_scythe.png` +
`ScytheItem.class` + `WardenItemInHandLayer.class`、中英文语言键各 3 条。

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

## 六·补充三、厄瑞玻斯 —— 当前终局 Boss（`beta1x` 起）

> 真名「遗落之主 · 晦星」。一条 **50 节、约 206 格长**的巨虫，在本模组里是
> 「泰拉瑞亚尺度换算到 MC 尺度」的产物 —— 长度压到 6 个区块、速度压到
> 每 tick 几个方块，否则服务端会把它当传送处理。换算过程见 `ErebosTuning` 类注释。

### 实体清单

| 实体 | 作用 |
|---|---|
| `lost_realm:erebos` | 本体（头部碰撞箱 4.8×4.8，`updateInterval(1)` 每 tick 同步位置） |
| `lost_realm:erebos_part` | 身体各节的碰撞部件（头部以外的判定都靠它，转发伤害给本体） |
| `lost_realm:erebos_bolt` / `erebos_shard` | 弹幕：噬光弹与碎星 |
| `lost_realm:erebos_effect` | 招式用的纯视觉实体 |

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
服务端（唯一物理） --PacketErebosSpine(50 节坐标 + teleport 标志)--> 客户端
客户端：只做 spinePrev/spineNow 插值 + 渲染，不跑任何自算物理
```

- 每 tick 下发一次，`broadcastSpine` 距离门限 320 格（覆盖半径 250 的竞技场）。
- **不设「位移超过阈值就判瞬移」**：30 格漏判（星门间距 20~40）、8 格误判（冲锋抽搐）。
  改为服务端 `markTeleport()` 显式置位，客户端只认标志硬切。
- 客户端 `clientStep()` 只剩「抄脊线 + 播粒子 + 播环境音」三件事。

### 视觉与声音

| 模块 | 内容 |
|---|---|
| `client/sky/ErebosSkyEffects` | 暗星天幕：分层星空 + 星球 + 星云（`renderSky` 里直接画，**不要再套相机旋转** —— 拿到的 pose 已含） |
| `client/ErebosBattleMusic` | 两首战斗 BGM 交替；`Attenuation.NONE`（默认 LINEAR + 位置 0,0,0 会让音量为 0） |
| `client/boss/ErebosRenderer` | 逐节盒渲染 + 逐节视锥剔除；本体 `noCulling = true` 绕过原版单 AABB |
| 战利品 | 五件遗物（`starfall_greatsword` / `hushlight_railgun` / `collapsar_eye` / `terminal_dragonpact` / `bulwark_of_the_last_light`）+ `event_horizon` |

### 怎么召出来

- **失落祭坛**（`LostAltarBlockEntity`）：投入材料 → 仪式读条 → 召唤。
- **暗星信标**（`DarkstarBeaconItem`）：直接召唤，调试用。
- 也可 `/summon lost_realm:erebos`（实体没加 `noSummon()`，就是为测试方便）。

### 数值现状

> ⚠️ `ErebosTuning.MAX_HEALTH` 自 **beta32** 起被临时改成 **500**（原设计 12000），
> 纯粹是为了实测方便。**正式发布前必须改回去**，源码里有 ★ 标记。

### 已知坑（都踩过）

| 症状 | 真因 | 约束 |
|---|---|---|
| 特定机位整条消失 | 单 AABB 视锥剔除 | 本体 `noCulling = true`，别改回去 |
| 身体某个位置一直闪 | 体径 4.8 > 节距 4.2，相邻两节共面 | 渲染沿身体方向压 0.875 |
| 移动时闪烁 / 抽搐 | 客户端自算 + 抄脊线两套时间轴打架 | 客户端**只抄不算** |
| 星门后整条消失 | 阈值判瞬移漏判 | 用 `teleport` 标志硬切 |
| 打身体不暴击 | `ErebosPart` 不是 `LivingEntity`，原版暴击判定跳过 | 部件 `hurt()` 里补 `ForgeHooks.getCriticalHit` |

### 与世界难度的关系

难度系统按 `Enemy` 判定（beta37），所以**厄瑞玻斯也会随难度缩放**：
深渊档下血量 ×1.6、攻击 ×1.5、护甲 ×1.4。阶段判定用的是**血量比例**而非绝对值，
因此缩放不会打乱阶段转换。

---

## 七、目录结构

> 只列「改代码时真的会去找」的层。完整清单：`find src/main/java -name '*.java'`（257 个文件）。

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
│   └── ErebosServerDebugCommand.java   /lrsdebug erebos
├── data/                             玩家数据与熟练度
│   ├── PlayerMedals.java               仅冷却 / 技能运行时状态(capability)
│   ├── MedalNbt.java                   熟练度存在勋章物品自己的 NBT 里
│   ├── MedalSource.java                取数门面(难度倍率在这里乘)
│   └── MedalProgression.java           枷锁(20 / 40)与破枷
├── item/                             勋章 / 箭 / 刃 / 盾 / 晶石 / 契约 / 终极剑
├── skill/                            30 个节点技能(5 枚 × 6)
├── event/                            战斗、tick、技能事件(CombatHandler 等 13 个)
├── network/                          11 个包(含 PacketErebosSpine / PacketDifficulty*)
├── util/                             UUID 表 / 防叠加 / 经验表 / 属性上限
├── mixin/                            23 个注入(体验改进 + 破甲)
├── qol/                              体验改进处理器
├── compat/                           Goety / 精妙背包 / JEI 软兼容
├── effect/                           自定义状态效果(流血等)
├── sound/                            Boss 战 BGM 注册
├── common/                           Curios 兼容与实体标签
├── client/
│   ├── ClientBootstrap / MedalKeybinds / ClientDifficulty / ClientMedalCache
│   ├── config/                       配置界面(18 分类 / 384 项 / 难度页 / 厄瑞玻斯页)
│   ├── effect/                       状态效果 HUD + 可视化编辑界面
│   ├── dialogue/                     零阶段对话 UI
│   ├── sky/                          ★ 暗星天幕(ErebosSkyEffects)
│   └── boss/                         ★ 厄瑞玻斯渲染器
└── boss/
    ├── 遗落之都 / 传送门 / 任务链(LostCityBuilder / WaveManager / WardenFight*)
    ├── erebos/                       ★ 厄瑞玻斯:本体 / 部件 / 弹幕 / 招式 / 脊线
    │   └── move/                      巡游 / 冲锋 / 星门
    ├── relic/                        ★ 五件遗物 + 事件视界
    └── block/ entity/ item/ ai/ anim/
```

资源与数据包：

```
src/main/resources/
├── META-INF/mods.toml
├── assets/lost_realm/
│   ├── lang/{zh_cn,en_us}.json        1418 ↔ 1418
│   ├── geo/ animations/               warden_form1~3(旧 Boss 模型,历史遗留)
│   ├── sounds/music/                  erebos_battle_1/2.ogg
│   ├── textures/{block,entity,gui,item,mob_effect,slot}/
│   └── models/ blockstates/
├── assets/minecraft/                  覆盖原版贴图(无限弓等)
└── data/
    ├── lost_realm/{advancements,dimension,dimension_type,loot_tables,recipes,damage_type}
    ├── lost_realm/curios/slots/        6 个饰品槽(medal / arrow / weapon / shield / crystal / contract)
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

产物：`build/libs/Lost Realm-<mod_version>-<mod_build>.jar`（当前：`Lost Realm-1.0.0-beta40.jar`）
（文件名 = `mod_version` + `mod_build`；`build.gradle` 用 `archiveFileName` 显式拼，
不写会得到 `...-1.0.0-beta40-1.0.0-beta40.jar` 这种双版本名。）

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
