package com.lost_realm.boss;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.lost_realm.Medals;
import com.lost_realm.boss.entity.WardenBoss;
import com.lost_realm.boss.entity.WardenSentinel;
import com.lost_realm.boss.erebos.ErebosBolt;
import com.lost_realm.boss.erebos.ErebosEffect;
import com.lost_realm.boss.erebos.ErebosBoss;
import com.lost_realm.boss.erebos.ErebosPart;
import com.lost_realm.boss.erebos.ErebosShard;
import com.lost_realm.boss.erebos.ErebosTuning;

/**
 * Boss 系统实体注册。
 *
 * <h2>实体清单</h2>
 * | 实体 | 用途 |
 * |---|---|
 * | {@code lost_realm:warden} | 终末之影本体(含三阶段) |
 * | {@code lost_realm:warden_sentinel} | 任务链第四阶段的守望者仆从(3 只) |
 *
 * <h2>属性注册为何必须在 MOD 事件总线上</h2>
 * {@code EntityAttributeCreationEvent} 是 MOD 总线事件 ——
 * 属性必须在该事件中注册,否则实体会以"无属性"状态生成并立即崩溃。
 * 这是 Forge 1.20.1 的硬性约定,不能改为普通事件总线。
 */
public final class BossEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Medals.MODID);

    /**
     * 终末之影。
     *
     * <p>尺寸设为 1.2 × 4.0 —— 比普通怪物高大很多,营造压迫感;
     * 第三阶段视觉上会通过缩放(渲染层)进一步放大。
     *
     * <p>{@code noSummon()} <b>不能</b>加:本 Boss 需要被召唤物生成。
     * {@code fireImmune()} 加上:终末之影免疫火焰,符合"非血肉之躯"的设定。
     */
    public static final RegistryObject<EntityType<WardenBoss>> WARDEN =
            ENTITIES.register("warden", () -> EntityType.Builder
                    .of(WardenBoss::new, MobCategory.MONSTER)
                    .sized(1.2F, 4.0F)
                    .clientTrackingRange(16)
                    .updateInterval(3)
                    .fireImmune()
                    .build(Medals.MODID + ":warden"));

    /**
     * 守望者仆从 —— 任务链的守门怪。
     *
     * <p>比本体弱得多(1/8 血量),单独注册实体是为了有独立的名字与掉落表,
     * 而非复用本体缩小版。
     */
    public static final RegistryObject<EntityType<WardenSentinel>> SENTINEL =
            ENTITIES.register("warden_sentinel", () -> EntityType.Builder
                    .of(WardenSentinel::new, MobCategory.MONSTER)
                    .sized(0.9F, 2.6F)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build(Medals.MODID + ":warden_sentinel"));

    // ================================================================
    // 晦星之主·厄瑞玻斯(Erebos) —— 终局宇宙蠕虫
    // ================================================================

    /**
     * 厄瑞玻斯本体。
     *
     * <h2>{@code sized(2.4, 2.4)} 的含义</h2>
     * 这不是"整条虫有多大"—— 96 格长的身体没法、也不该塞进一个碰撞箱。
     * 这个 2.4 格见方的箱子<b>只是头部</b>：它同时决定了
     * "玩家撞到虫头算头部伤害"和"实体位置点在哪"。
     * 其余 39 节的判定由 {@link #EREBOS_PART} 负责。
     *
     * <h2>为什么 {@code updateInterval(1)}</h2>
     * 默认每 3 tick 才同步一次位置。这条虫巡游 0.55、冲锋 2.6 格/tick，
     * 3 tick 的间隔意味着客户端会看到它"每隔几格闪一下"。
     * 拉到 1（每 tick 同步）—— 位置包本身很小，换来的是运动连续。
     *
     * <p>{@code noSummon()} <b>不加</b>：方便用指令直接生成来测试。
     */
    public static final RegistryObject<EntityType<ErebosBoss>> EREBOS =
            ENTITIES.register("erebos", () -> EntityType.Builder
                    .of(ErebosBoss::new, MobCategory.MONSTER)
                    // 与 BODY_DIAMETER 同步（beta20 起 4.8）—— 准星选中、交互都靠它
                    .sized((float) ErebosTuning.BODY_DIAMETER, (float) ErebosTuning.BODY_DIAMETER)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .fireImmune()
                    .build(Medals.MODID + ":erebos"));

    /**
     * 厄瑞玻斯的碰撞部件 —— 沿身体每节一个。
     *
     * <h2>为什么是独立实体</h2>
     * 攻击判定的入口是"射线检测命中哪个实体"。单个实体的碰撞箱只能覆盖头部，
     * 玩家对着中段身体砍刀会直接落空。部件实体让每一节都能被准星选中，
     * 受击后把伤害<b>转发回本体</b>（见 {@code ErebosPart#hurt}）——
     * 于是"共享血量"天然成立，也不会出现打两处掉两份血。
     *
     * <h2>几个刻意的设置</h2>
     * <ul>
     *   <li>{@code MobCategory.MISC}：它不是生物，不进刷怪上限、不参与生物 AI。</li>
     *   <li>{@code noSave()}：不写进存档。世界重载后由本体重新创建 ——
     *       否则会留下一堆"没有主人的幽灵判定盒"。</li>
     *   <li>{@code updateInterval(1)}：位置必须每 tick 跟上，否则判定会滞后于视觉。</li>
     * </ul>
     */
    public static final RegistryObject<EntityType<ErebosPart>> EREBOS_PART =
            ENTITIES.register("erebos_part", () -> EntityType.Builder
                    // ⚠️ 必须显式写 <ErebosPart>。ErebosPart 有两个构造函数
                    // （注册用的 (EntityType, Level) 与服务端创建用的 (ErebosBoss)），
                    // 不指定的话编译器会把 T 推断成 Entity，
                    // 然后抱怨"EntityType<Entity> 无法转成 EntityType<? extends ErebosPart>"。
                    .<ErebosPart>of(ErebosPart::new, MobCategory.MISC)
                    .sized(ErebosTuning.PART_SIZE, ErebosTuning.PART_SIZE)
                    .clientTrackingRange(16)
                    // ---- 从 1 降到 2 ----
                    // 部件有 40 个（每节一个），updateInterval(2) 意味着
                    // 每个部件每 tick 都要发一次位置包 —— 单个 Boss 就是 800 包/秒。
                    // 实测中出现过 4 个 Boss 并存（召唤去重半径太小，见 DarkstarBeaconItem），
                    // 那就是 160 个实体 / 3200 包/秒，手机端直接把同步挤爆：
                    // 客户端位置严重滞后 → 轨迹长不起来 → 身体塌缩成一个头。
                    // 降到 2 之后：包量减半，判定滞后 1 tick（约 0.5 格），玩家感知不到。
                    .updateInterval(2)
                    .noSave()
                    .fireImmune()
                    .build(Medals.MODID + ":erebos_part"));

    /**
     * 厄瑞玻斯的星弹 —— 全部招式的公共弹药。
     *
     * <h2>为什么弹幕要有独立的实体类型</h2>
     * 原版没有"既发光、又被方块挡、又要按距离推导速度"的通用弹幕，
     * 而新造一个比复用（比如硬套雪球再改）干净得多：
     * 雪球的伤害、音效、粒子、存档语义全都要覆写，覆写完就只剩个类名是雪球了。
     *
     * <h2>{@code updateInterval(1)}</h2>
     * 弹幕速度 1.2 格/tick，默认的每 3 tick 同步一次会让客户端看到它"跳着走"。
     */
    public static final RegistryObject<EntityType<ErebosBolt>> EREBOS_BOLT =
            ENTITIES.register("erebos_bolt", () -> EntityType.Builder
                    .<ErebosBolt>of(ErebosBolt::new, MobCategory.MISC)
                    .sized(ErebosTuning.BOLT_HITBOX * 2.0F, ErebosTuning.BOLT_HITBOX * 2.0F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .noSave()
                    .build(Medals.MODID + ":erebos_bolt"));

    /**
     * 死亡演出的装甲碎块。
     *
     * <p>{@code noSave()}：Boss 都死了，没必要让几百块碎片写进存档。
     * {@code noPhysics}：不参与碰撞，只在自己的 tick 里做一次 AABB 检测，
     * 所以不会和玩家、方块实体互相卡住。
     */
    public static final RegistryObject<EntityType<ErebosShard>> EREBOS_SHARD =
            ENTITIES.register("erebos_shard", () -> EntityType.Builder
                    .<ErebosShard>of(ErebosShard::new, MobCategory.MISC)
                    .sized(0.45F, 0.45F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .noSave()
                    .fireImmune()
                    .build(Medals.MODID + ":erebos_shard"));

    /**
     * 场上的效果载体 —— 激光 / 限制圈 / 审判柱 / 星痕 / 星门。
     *
     * <h2>为什么做成实体（而不是自定义网络包）</h2>
     * 这些效果都是"服务端判定 + 客户端画出来"。若自建同步包，
     * 就得自己处理丢包、重连、玩家中途入场、维度切换这一整套 ——
     * 而这些正是原版实体同步层已经解决了几十年的。做成实体，
     * 位置同步、追踪范围、入场补发、世界卸载清理全都免费。
     *
     * <h2>{@code updateInterval(1)}</h2>
     * 激光会旋转、限制圈要跟着玩家移动判定，位置与朝向必须每 tick 跟上。
     *
     * <p>{@code noSave()}：效果都是短命的，存档里留着没有意义，
     * 重载后由招式重新生成。
     */
    public static final RegistryObject<EntityType<ErebosEffect>> EREBOS_EFFECT =
            ENTITIES.register("erebos_effect", () -> EntityType.Builder
                    .<ErebosEffect>of(ErebosEffect::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(16)
                    .updateInterval(5)
                    .noSave()
                    .fireImmune()
                    .build(Medals.MODID + ":erebos_effect"));

    public static void init(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener(BossEntities::onAttributeCreation);
    }

    /** 属性注册 —— 必须挂在 MOD 总线。 */
    private static void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(WARDEN.get(), WardenBoss.createAttributes().build());
        event.put(SENTINEL.get(), WardenSentinel.createAttributes().build());
        event.put(EREBOS.get(), ErebosBoss.createAttributes().build());
        // EREBOS_PART 没有属性：它是 Entity 而非 LivingEntity，没有生命值也不结算伤害
    }

    private BossEntities() {}
}
