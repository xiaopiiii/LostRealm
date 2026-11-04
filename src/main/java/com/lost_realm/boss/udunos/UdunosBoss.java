package com.lost_realm.boss.udunos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.lost_realm.Medals;
import com.lost_realm.boss.udunos.UdunosBrain.Action;
import com.lost_realm.boss.udunos.move.UdunosMove;
import com.lost_realm.boss.udunos.move.UdunosMove.MoveKind;

/**
 * <b>遗落之主·乌蠹诺斯（Udunos）</b> —— 宇宙蠕虫形终局 Boss。
 *
 * <h2>它是什么</h2>
 * 一条 <b>40 节 / 96 格（6 个区块）</b>长的宇宙巨虫，四阶段、共享血量、
 * 以"预警 → 躲避"为核心的高难度战斗。与既有的「终末之影」并存，
 * 是两个独立的 Boss（见 {@code BossEntities}）。
 *
 * <h2>整体架构：一个实体，一条轨迹，两层判定</h2>
 * <pre>
 *   UdunosBoss（唯一实体，位置 = 头部）
 *      │  每 tick：brain 算速度 → 挪头部 → 往 trail 记一个点
 *      │
 *      ├── UdunosTrail   40 节的位置由「头部走过的弧长」反推（服务端+客户端各算一份）
 *      ├── UdunosPart    15 个碰撞部件，沿身体铺开，受击转发回本体  ← 攻击判定
 *      └── UdunosBrain   招式状态机（巡游 / 冲锋 / 后续三十招）      ← 行为
 * </pre>
 *
 * <h3>① 为什么不是 40 个实体</h3>
 * 40 个实体要 tick、要同步、要各自寻路，服务器 TPS 会被吃光。
 * 身体的位置<b>完全由数学决定</b>（头部走过路径的弧长），
 * 所以只需要 1 个实体在算，外加少量纯判定用的部件。
 *
 * <h3>② 为什么不需要把体节位置同步给客户端</h3>
 * 头部位置本来就会同步（实体自带），客户端拿到后<b>跑同一套路径跟随算法</b>，
 * 就能推出和服务端几乎一致的身体 —— 这正是原版末影龙的做法。
 * 96 格 × 40 节的位置如果逐 tick 同步，带宽会爆炸；这样一分钱不花。
 *
 * <h3>③ 为什么移动不走 {@code move()}</h3>
 * 一条 96 格的虫子在方块间做碰撞检测毫无意义（它根本不落地、也不穿墙，
 * 是"飞行的巨物"）。这里直接 {@code setPos} 驱动头部，
 * 碰撞检测完全交给 15 个部件实体。省掉的是每 tick 上百次方块相交测试。
 *
 * <h2>共享血量是天然的</h2>
 * 原设计强调"攻击身体会伤害整条虫，头、身体和尾部不能分别结算"——
 * 在 MC 里这一条<b>不需要任何代码</b>：只有一个实体、只有一份血，
 * 部件只是把伤害转发回来而已。也不存在"打两处掉两份血"。
 *
 * <h2>动态减伤</h2>
 * 统计最近 {@link UdunosTuning#REDUCTION_WINDOW} tick 的实际失血；
 * 超过阈值后按 {@code clamp(1 - 阈值/失血, 0, 0.75)} 平滑地提供减伤，
 * 玩家输出降下来再平滑回落。用途是<b>压制超模爆发</b>
 * （比如本模组自己的「我有一剑」单发 5000），而不是把 Boss 变成无敌墙。
 */
public class UdunosBoss extends Monster {

    // ================================================================
    // 同步数据（客户端要用的那几样）
    // ================================================================

    /** 当前阶段序号。客户端靠它选血条颜色与音效。 */
    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(UdunosBoss.class, EntityDataSerializers.INT);

    /** 当前行为序号（{@link Action#ordinal()}）。客户端靠它决定画不画预警线。 */
    private static final EntityDataAccessor<Integer> DATA_ACTION =
            SynchedEntityData.defineId(UdunosBoss.class, EntityDataSerializers.INT);

    /** 冲锋预警方向（单位向量）。客户端沿它画长红线。 */
    private static final EntityDataAccessor<Vector3f> DATA_AIM =
            SynchedEntityData.defineId(UdunosBoss.class, EntityDataSerializers.VECTOR3);

    /** 冲锋预警剩余 tick。 */
    private static final EntityDataAccessor<Integer> DATA_WARNING =
            SynchedEntityData.defineId(UdunosBoss.class, EntityDataSerializers.INT);

    /** 身体是否已完全展开（开场逐节钻出的进度标记）。 */
    private static final EntityDataAccessor<Boolean> DATA_EMERGED =
            SynchedEntityData.defineId(UdunosBoss.class, EntityDataSerializers.BOOLEAN);

    /**
     * 当前招式的种类标号（{@code MoveKind.ordinal()}）。
     *
     * <p>客户端靠它决定"该画哪种预警" —— 冲锋是一根长线、碎星播撒是三根扇形线、
     * 星弧巡猎不画线（种子自己带光环）。用序号而不是字符串，
     * 是因为同步槽只支持基本类型，而字符串每 tick 同步的代价不值得。
     */
    private static final EntityDataAccessor<Integer> DATA_MOVE =
            SynchedEntityData.defineId(UdunosBoss.class, EntityDataSerializers.INT);

    /** 招式的自定义参数 A（例：碎星播撒的第 1 个发射出口序号）。 */
    private static final EntityDataAccessor<Integer> DATA_PARAM_A =
            SynchedEntityData.defineId(UdunosBoss.class, EntityDataSerializers.INT);

    /** 招式的自定义参数 B。 */
    private static final EntityDataAccessor<Integer> DATA_PARAM_B =
            SynchedEntityData.defineId(UdunosBoss.class, EntityDataSerializers.INT);

    // ================================================================
    // 核心组件
    // ================================================================

    /** 头部走过的路径。服务端与客户端各维护一份，内容几乎一致。 */
    private final UdunosTrail trail = new UdunosTrail();

    /** 招式状态机。 */
    private final UdunosBrain brain = new UdunosBrain(this);

    /** 15 个碰撞部件。仅服务端持有并驱动；客户端由 EntityType 反序列化出另一批实例。 */
    @Nullable
    private UdunosPart[] parts;

    // ---- 体节位置缓存 ----
    /** 当前 tick 采样出的体节位置（长度 {@code SEGMENTS * 3}）。 */
    private final double[] segments = new double[UdunosTuning.SEGMENTS * 3];
    /** 上一 tick 的采样结果，供渲染插值用。 */
    private final double[] segmentsPrev = new double[UdunosTuning.SEGMENTS * 3];

    /**
     * 每节的<b>渲染用平滑朝向</b>（度）。
     *
     * <p>由渲染器每帧推进，用来抹掉 yaw 的逐帧抖动 ——
     * 身体接近垂直时 {@code atan2(tx, tz)} 会剧烈跳变，
     * 结果就是整条虫绕自身轴高频打转，看起来像抽搐。
     * 放这里而不是渲染器里，是因为渲染器实例是共享的、没有"每节"状态。
     */
    private final float[] yawSmooth = new float[UdunosTuning.SEGMENTS];

    /** 读取某节当前的平滑朝向（不推进）。 */
    public float smoothedYaw(int i) {
        return this.yawSmooth[Mth.clamp(i, 0, this.yawSmooth.length - 1)];
    }

    /** 推进并返回某节的平滑朝向（按最短角插值）。 */
    public float smoothedYaw(int i, float target) {
        int k = Mth.clamp(i, 0, this.yawSmooth.length - 1);
        float prev = this.yawSmooth[k];
        float delta = Mth.wrapDegrees(target - prev);
        // 0.65 ≈ 2 帧收敛：比 0.45 更跟手（0.45 时转弯会明显甩尾，
        // 高速移动下看着像身体在"扭"）
        this.yawSmooth[k] = prev + delta * 0.65F;
        return this.yawSmooth[k];
    }

    // ---- 包围盒缓存（渲染剔除用，覆盖整条虫） ----
    private double cullMinX, cullMinY, cullMinZ, cullMaxX, cullMaxY, cullMaxZ;

    // ================================================================
    // 客户端：服务端下发的脊线
    // ----------------------------------------------------------------
    // 原设计与原版末影龙相同：只同步头部，客户端自己跑一遍路径跟随。
    // 但实测发现客户端的位置根本没更新（轨迹永远只有 1 个点、40 节完全重合），
    // 而服务端那边身体是正常铺开的 —— "客户端自算"这条链路在当前环境里是断的。
    // 于是改成服务端直接下发整条脊线，客户端照着画：塌缩不可能再发生。
    // 详见 PacketUdunosSpine 的类注释。
    // ================================================================

    /** 最近一帧的脊线（扁平 x,y,z，已转成 double 以便与自算的 segments 同构）。仅客户端有值。 */
    @Nullable
    private double[] spineNow;

    /** 上一帧的脊线，用于前后插值（否则 10Hz 的更新在 60fps 下会一跳一跳）。 */
    @Nullable
    private double[] spinePrev;

    /** 最后一次收到脊线的时刻（nanoTime），用于判断数据是否还新鲜。 */
    private long spineNanos;

    /**
     * 接收服务端下发的脊线。只应在客户端调用。
     *
     * <p>网络上传的是 {@code float[]}（省带宽），这里立刻转成 {@code double[]} ——
     * 这样渲染器只有一种数据类型，不必为"用哪一份"写两套取坐标的分支。
     *
     * <p>把上一帧挪到 {@link #spinePrev}，让渲染器能做 {@code lerp}：
     * 10Hz 的数据直接画会有明显的"一格一格"感。
     */
    public void acceptSpine(float[] points) {
        acceptSpine(points, false);
    }

    public void acceptSpine(float[] points, boolean teleport) {
        if (!this.level().isClientSide) {
            return;
        }
        double[] arr = new double[points.length];
        for (int i = 0; i < points.length; i++) {
            arr[i] = points[i];
        }

        // ★ 瞬移「硬切」由服务端显式告知，不再用固定阈值猜。
        //
        //   曾经用「头部单帧位移 > 阈值」判断瞬移：阈值 30 格时，星门两门间距
        //   若 < 30 格就不硬切 → 脊线在 A/B 间插值拉成一条线 → 整条虫看似消失；
        //   阈值降到 8 格后，穿门后头部以 5 格/tick 高速前进，连续帧位移逼近 8 格，
        //   又触发连续误判硬切 → prev 恒等于 now → 插值失效 → 抽搐更严重。
        //   两种阈值都错。服务端最清楚「这一帧是不是瞬移」，让它带标志下来最干净。
        boolean jumped = teleport;

        this.spinePrev = jumped ? arr : this.spineNow;
        this.spineNow = arr;
        this.spineNanos = System.nanoTime();
    }

    /** 最近一帧脊线；没有则 null。 */
    @Nullable
    public double[] spineNow() {
        return this.spineNow;
    }

    /** 上一帧脊线；没有则 null。 */
    @Nullable
    public double[] spinePrev() {
        return this.spinePrev;
    }

    /** 脊线数据是否还在有效期内（超过 1 秒没更新就当作失效，回退到客户端自算）。 */
    public boolean hasFreshSpine() {
        return this.spineNow != null
                && System.nanoTime() - this.spineNanos < 1_000_000_000L;
    }

    // ---- 动态减伤 ----
    private final double[] bleedWindow = new double[UdunosTuning.REDUCTION_WINDOW];
    private int bleedCursor;
    private double reduction;

    // ---- 接触伤害冷却（按玩家记，防止每 tick 都判定把玩家瞬秒） ----
    private final Map<UUID, Integer> contactCooldowns = new HashMap<>();

    /**
     * 星核是否处于"暴露"状态（第三阶段的输出窗口）。
     *
     * <p>由当前招式决定（{@code UdunosMove#exposesCore()}），每 tick 刷新。
     */
    private boolean exposed;

    /**
     * 服务端瞬移标记：这一 tick 是否发生了星门穿门瞬移。
     *
     * <p>星门招式在 {@link com.lost_realm.boss.udunos.move.MoveStarGate#enterGate()}
     * 里调 {@link #markTeleport()} 置位；{@link #broadcastSpine} 发完这一帧的脊线后
     * 立即清位。客户端据此决定「硬切」（不做插值），而不是靠固定位移阈值去猜。
     */
    private boolean teleportThisTick;

    /** 星门瞬移时调用，通知下一帧脊线包带上硬切标志。 */
    public void markTeleport() {
        this.teleportThisTick = true;
    }

    /** 上一 tick 所处的阶段 —— 用来检测"刚刚换阶段了"。 */
    private UdunosPhase lastPhase = UdunosPhase.ONE;

    /** 上一次播报过的招式 id —— 用来"只在换招时播报一次"。 */
    @Nullable
    private String announcedMove;

    /** 血条。 */
    private final ServerBossEvent bossBar = new ServerBossEvent(
            this.getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

    // ================================================================
    // 招式 → 客户端 的待同步状态
    // ----------------------------------------------------------------
    // 为什么用"招式主动写入 + Boss 每 tick 统一提交"这种两段式：
    // 同步槽是有限的（这里只有 5 个），而招式会越加越多。
    // 让每个招式在 tick 里把自己那一帧要画的东西写进来，
    // 由 Boss 在 tick 之后一次提交 —— 这样新增招式不需要碰同步槽定义。
    // ================================================================

    private MoveKind pendingMove = MoveKind.NONE;
    private Vec3 pendingAim = Vec3.ZERO;
    private int pendingWarning;
    private int pendingParamA;
    private int pendingParamB;

    /** 本 tick 正在演哪个招式（供客户端渲染决定画什么）。 */
    public void syncMove(MoveKind kind) {
        this.pendingMove = kind;
    }

    /** 本 tick 的参考方向（冲锋的锁定方向 / 扇形的中心方向）。 */
    public void syncAim(Vec3 aim) {
        this.pendingAim = aim;
    }

    /** 预警剩余 tick。 */
    public void syncWarning(int ticks) {
        this.pendingWarning = ticks;
    }

    /** 招式的两个自定义参数。 */
    public void syncParams(int a, int b) {
        this.pendingParamA = a;
        this.pendingParamB = b;
    }

    // ================================================================
    // 构造与注册
    // ================================================================

    /**
     * 诊断计数器 —— 只在前几 tick 打印，避免刷屏。
     *
     * <p>加它的原因：玩家反馈"召唤出来后头停在空中，过一会儿整个消失，血条却还在"。
     * 这个现象指向"服务端还活着、客户端不画了"，但光看现象分不清是
     * 服务端没 tick、还是血量同步丢了、还是渲染被剔除。
     * 前几 tick 打一版完整快照，一眼就能分辨。
     */
    private int diagTicks;

    public UdunosBoss(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        // 位置完全由路径驱动，不走原版移动/碰撞管线（见类注释③）
        this.noPhysics = true;
        // 不注册任何 Goal —— 行为由 UdunosBrain 直接驱动
        this.setNoAi(true);
        // ★ 关掉原版剔除。
        //
        //   这条虫 210 格长，而原版只肯拿<b>一个 AABB</b> 去和视锥做一次相交测试。
        //   无论那个盒怎么算，都必然有一批"人在虫身上/贴着某一节"的机位会判成不可见 ——
        //   玩家实测的「靠近头部整条消失、靠近身体没事、飞高也没用」就是这一类。
        //   之前用 spineNow 拼盒缓解过，但只要盒的来源和摄像机位置错开一帧仍会误杀。
        //
        //   关掉之后渲染器每帧都会被调用，而 UdunosRenderer 内部本来就按
        //   MAX_RENDER_DIST_SQ 逐节做了距离剔除 —— 性能代价是"一条虫多一次调用"，
        //   换的是绝不会再出现"莫名其妙整个不见"。
        this.noCulling = true;
        this.xpReward = 500;
    }

    /**
     * 属性表。
     *
     * <p>几点刻意的选择：
     * <ul>
     *   <li>{@code ARMOR = 0}：减伤<b>只走动态减伤</b>，不叠护甲。
     *       两者叠在一起会让数值难以推理（护甲是隐式减免，动态减伤是显式的）。</li>
     *   <li>{@code KNOCKBACK_RESISTANCE = 1}：96 格的虫子被箭推得晃是荒谬的。</li>
     *   <li>{@code MOVEMENT_SPEED = 0}：它不用原版移动，填 0 免得寻路系统乱动。</li>
     * </ul>
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, UdunosTuning.MAX_HEALTH)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.FOLLOW_RANGE, UdunosTuning.LEASH_RANGE)
                .add(Attributes.ATTACK_DAMAGE, UdunosTuning.CONTACT_BODY);
    }

    @Override
    protected void defineSynchedData() {
        // ★ 必须先调 super —— 原版 Entity / LivingEntity / Mob 在这里注册
        // 生命值、共享标志等一系列同步槽。漏掉它的话，LivingEntity 构造函数里的
        // setHealth() 会因为找不到 DATA_HEALTH_ID 直接 NPE，实体根本造不出来，
        // 表现就是 /summon 报「无法召唤实体」（异常被 EntityType.loadEntity 吞掉）。
        super.defineSynchedData();
        this.entityData.define(DATA_PHASE, 0);
        this.entityData.define(DATA_ACTION, Action.EMERGE.ordinal());
        this.entityData.define(DATA_AIM, new Vector3f(1.0F, 0.0F, 0.0F));
        this.entityData.define(DATA_WARNING, 0);
        this.entityData.define(DATA_EMERGED, false);
        this.entityData.define(DATA_MOVE, MoveKind.NONE.ordinal());
        this.entityData.define(DATA_PARAM_A, 0);
        this.entityData.define(DATA_PARAM_B, 0);
    }

    // ================================================================
    // 每 tick
    // ================================================================

    /**
     * 主循环。
     *
     * <p>服务端与客户端做的事情<b>几乎一样</b>（都要推进轨迹、都要采样体节），
     * 差别只在：服务端额外跑状态机、驱动部件、结算接触伤害。
     * 客户端不跑状态机是因为它不该做权威判定 —— 它只负责"照着显示"。
     */
    @Override
    public void aiStep() {
        // ★ 客户端优先：推进 trail 这件事<b>不能</b>被"逻辑上是否存活"拦住。
        //
        // 客户端只负责显示 —— 实体正在死亡、血量同步成了 0、乃至已被标记移除，
        // 玩家也仍然应该看到它，否则画面就是"凭空消失"。
        // 更要命的是原版 LivingEntity.tick() 里写的是
        //     if (!isRemoved()) { aiStep(); }
        // 一旦 isRemoved 为真，本方法再也不会被调用，身体数据就<b>永久冻在最后一帧</b>——
        // 表现正是"实体还在（右键召唤物提示已存在），屏幕上却什么都没有"。
        // 所以"推进 trail"必须放在所有存活判断之前。
        if (this.level().isClientSide) {
            this.clientStep();
            return;
        }

        // 死亡/移除后不再推进任何东西 —— 否则 60 tick 的崩解演出期间
        // brain 还在跑，尸体会继续往前游，看着像"它没死"。
        if (!this.isAlive() || this.isRemoved()) {
            // ★ 诊断：原版 LivingEntity.tick() 里是
            //     if (!isRemoved()) { aiStep(); }
            //   也就是实体一旦被标记移除，这里就再也不会被调用 ——
            //   客户端实体的体节数据随之冻结、整条虫不再渲染。
            //   而"服务端还活着（右键召唤物提示已存在）+ 屏幕上看不见"
            //   正好就是这个症状。所以这里必须留下痕迹，否则永远查不出来。
            if (this.diagTicks++ < 5) {
                Medals.LOGGER.warn(
                        "[LR/udunos] {} aiStep SKIPPED: alive={} removed={} reason={} hp={}",
                        this.level().isClientSide ? "CLIENT" : "SERVER",
                        this.isAlive(), this.isRemoved(), this.getRemovalReason(),
                        this.getHealth());
            }
            return;
        }

        ServerLevel serverLevel = (ServerLevel) this.level();

        if (this.diagTicks++ < 5) {
            Medals.LOGGER.info(
                    "[LR/udunos] SERVER tick#{} action={} target={} hp={}/{} pos={} trail={} seg0={}",
                    this.diagTicks, this.brain.action(),
                    this.getTarget() == null ? "-" : this.getTarget().getName().getString(),
                    this.getHealth(), this.getMaxHealth(),
                    fmt(this.position()),
                    this.trail.size(),
                    fmtSeg(0));
        }

        // 重置本 tick 的特效配额 —— 原设计"全局设每帧和存活数量上限"的落点。
        // 放在这里而不是放在每个招式里，是为了让"上限"这件事只有一个出口。
        UdunosFx.beginTick();

        // ---- 1. 索敌 ----
        Player target = serverLevel.getNearestPlayer(this, UdunosTuning.LEASH_RANGE);
        if (target != null && (target.isCreative() || target.isSpectator())) {
            target = null;
        }
        if (target != null) {
            this.setTarget(target);
        }

        // ---- 2. 推进状态机，得到本 tick 的速度 ----
        this.brain.tick(target);

        // ---- 3. 把头部挪到新位置（不走 move，直接 setPos） ----
        Vec3 velocity = this.brain.velocity();
        if (velocity.lengthSqr() > 1.0E-9D) {
            Vec3 next = this.position().add(velocity);
            this.setPos(next.x, next.y, next.z);
        }

        // ---- 4. 推进轨迹 + 采样体节 ----
        this.advanceTrail();

        // ---- 5. 阶段转场 ----
        this.checkPhaseTransition(serverLevel);

        // ---- 6. 星核暴露窗口 + 换招播报 ----
        var activeMove = this.brain.current();
        this.exposed = activeMove != null && activeMove.exposesCore();
        this.announceMoveIfChanged(serverLevel);

        // ---- 6. 同步给客户端的几样东西 ----
        this.syncStateToClient(target);

        // ---- 6. 碰撞部件跟着身体走 ----
        this.updateParts(serverLevel);

        // ---- 7. 接触伤害 ----
        this.tickContactDamage(serverLevel, target);

        // ---- 8. 动态减伤窗口滚动 ----
        this.rollBleedWindow();

        // ---- 9. 血条 ----
        this.updateBossBar();

        // ---- 10. 把整条脊线下发给客户端 ----
        // ★ 必须每 tick 发。曾经是每 2 tick（10Hz），结果玩家反馈"移动一卡一卡、会抽搐"。
        //   原因是相位对不上：客户端插值用的是 partialTick（0→1 每 tick 循环一次），
        //   而数据每 2 tick 才换一帧 —— 于是每个包周期里有一半时间在原地"等"，
        //   然后一次性跳过两个 tick 的位移。20Hz 的发包 + 20Hz 的插值相位一致，
        //   这才是平滑的前提。
        //   带宽：40 节 × 3 float × 4B = 480B/tick ≈ 9.6 KB/s，单人/三通毫无压力。
        this.broadcastSpine(serverLevel);
    }

    /**
     * 把当前 40 节坐标打包发给附近玩家。
     *
     * <p>约 480 字节/包、10Hz —— 比原来 40 个部件实体各自发位置包还省，
     * 而且一个包就是完整的一帧，不会出现"发了一半"的中间态。
     */
    private void broadcastSpine(ServerLevel level) {
        float[] pts = new float[UdunosTuning.SEGMENTS * 3];
        for (int i = 0; i < pts.length; i++) {
            pts[i] = (float) this.segments[i];
        }
        // 带上瞬移标志，发完即清位 —— 保证只有瞬移那一帧是"硬切"
        boolean teleported = this.teleportThisTick;
        this.teleportThisTick = false;
        var packet = new com.lost_realm.network.PacketUdunosSpine(this.getId(), pts, teleported);
        for (ServerPlayer player : level.players()) {
            // ★ 门限从 256 提到 320，覆盖整个 250 格竞技场 + 余量。
            //
            //   旧值 256 是「整条虫消失」和「出场抽搐」的共同诱因：
            //   玩家一旦被打飞到 256 格外（开放场地里很常见），脊线就停发，
            //   客户端退回到那条断掉的自算链路 → 抽搐或整条消失。
            //   320 格保证只要玩家还在这场战斗里，就永远收得到脊线。
            if (player.distanceToSqr(this) < 320.0D * 320.0D) {
                com.lost_realm.network.MedalsNetwork.sendToPlayer(player, packet);
            }
        }
    }

    /**
     * 客户端每 tick 做的事。
     *
     * <p>客户端<b>同样</b>要跑一遍路径跟随 —— 否则身体没法画。
     * 它读的是同步过来的头部位置（带插值），因此算出来的体节位置
     * 与服务端有极细微的差别，但玩家看不出来，这正是原版末影龙的做法。
     */
    private void clientStep() {
        // ★ 客户端<b>不再自己算身体</b>。
        //
        //   整条虫的位置由服务端每 tick 下发的脊线包（broadcastSpine）决定，
        //   渲染器直接用 spineNow/spinePrev 插值。客户端自算（advanceTrail）
        //   这条链路在整合包环境里是断的（头部位置收不到更新），一旦触发
        //   就会让 segments 塌缩、剔除盒变小、身体抽搐 —— 历次"抽搐/消失"
        //   几乎都跟它有关。所以这里彻底移除自算，只做「把脊线抄进 segments」
        //   供剔除盒使用这一件事。
        this.syncSegmentsFromSpine();
        this.spawnMoveWarningFx();
        this.spawnAmbience();

        if (this.diagTicks++ < 5) {
            Medals.LOGGER.info(
                    "[LR/udunos] CLIENT tick#{} alive={} removed={} hp={} seg0={} trail={}",
                    this.diagTicks, this.isAlive(), this.isRemoved(),
                    this.getHealth(), fmtSeg(0), this.trail.size());
        }
    }

    private static String fmt(Vec3 v) {
        return String.format("(%.1f, %.1f, %.1f)", v.x, v.y, v.z);
    }

    /**
     * 立刻按当前轨迹重算体节 —— 供"整条身体瞬移"这类操作在 <b>同一 tick 内</b>刷新。
     *
     * <p>正常流程里 {@link #advanceTrail()} 每 tick 会做这件事，但它同时会
     * 把当前位置 push 进轨迹。瞬移时位置是在招式里改的，若等下一 tick 才采样，
     * 这一 tick 下发的还是旧脊线 —— 客户端会看到身体在新位置停留一帧再跳。
     */
    public void refreshSegments() {
        this.trail.sample(this.segments, UdunosTuning.SEGMENTS, UdunosTuning.SEGMENT_SPACING);
        this.updateCullBox();
    }

    /**
     * 用服务端下发的脊线覆盖客户端自算的体节。
     *
     * <h2>为什么必须覆盖，而不是只在渲染器里另走一条路</h2>
     * 渲染器直接用脊线画，是对的；但<b>剔除盒是由 {@link #segments} 算的</b>
     * （{@link #updateCullBox()}）。客户端的 {@code segments} 是自算的 ——
     * 而客户端位置收不到更新，于是 40 节全塌缩成一个点，剔除盒也就只有那么几格大。
     *
     * <p>后果是：视锥测试对的是"一个点"，这个点一出画面，
     * <b>整条 96 格的身体被一起剔除</b> ——
     * 玩家看到的就是"要站到某个特定角度才看得见，一转头全没了"。
     *
     * <p>所以这里把脊线抄进 {@code segments}：剔除盒、自算兜底、{@code /lrdebug}
     * 三处同时变正确，只花 40 次数组拷贝。
     */
    private void syncSegmentsFromSpine() {
        double[] src = this.spineNow;
        if (src == null || src.length != this.segments.length) {
            return;
        }
        // 只抄「当前」这一份给剔除盒用；渲染器不读 segments（它直接用 spineNow/spinePrev），
        // 所以这里不写 segmentsPrev，避免再引入一套时间轴。
        System.arraycopy(src, 0, this.segments, 0, this.segments.length);
        this.updateCullBox();
    }

    private String fmtSeg(int i) {
        int k = i * 3;
        if (k + 2 >= this.segments.length) {
            return "?";
        }
        return String.format("(%.1f, %.1f, %.1f)",
                this.segments[k], this.segments[k + 1], this.segments[k + 2]);
    }

    /**
     * 宇宙氛围 —— 战斗期间在玩家周围漂浮的星光。
     *
     * <h2>为什么不去改天空盒</h2>
     * 原设计要求"动态背景为紫黑宇宙主题，四阶段渐变升级"。
     * 真正做一个自定义 {@code DimensionSpecialEffects} 要写自定义
     * 天空渲染管线，而且会和整合包里其它改天空的模组打架
     * （遗落境维度里可能还叠着别的模组的行星、星云渲染）。
     *
     * <p>这里走另一条路：<b>用粒子在玩家周围铺一层缓慢漂浮的星光</b>。
     * 成本低、不和任何模组冲突，而且亮度随阶段递增 ——
     * 玩家会明显感到"越往后越亮、越像深处"。
     *
     * <h2>四阶段的变化</h2>
     * 阶段越高，星光越密、越偏紫白（原本是暗紫）。
     * 这是很便宜的"气氛升级"，但它不需要玩家做任何事就能感受到
     * "这一阶段和上一阶段不一样"。
     */
    private void spawnAmbience() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        // 只在玩家与 Boss 距离够近时才铺氛围 —— 战场之外不给
        if (player.distanceToSqr(this) > 128.0D * 128.0D) {
            return;
        }
        int phase = this.syncedPhase().index();
        // 每 4 tick 一次，数量随阶段递增
        if (this.tickCount % 4 != 0) {
            return;
        }
        int count = 2 + phase;
        var random = this.level().getRandom();
        float r = 0.45F + phase * 0.10F;
        float g = 0.28F + phase * 0.07F;
        float b = 0.95F;

        for (int i = 0; i < count; i++) {
            // 铺在玩家周围一个大球壳上：远处、稀疏、缓慢漂浮
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = 16.0D + random.nextDouble() * 34.0D;
            double height = (random.nextDouble() - 0.35D) * 26.0D;
            double x = player.getX() + Math.cos(angle) * radius;
            double z = player.getZ() + Math.sin(angle) * radius;
            double y = player.getY() + height;
            this.level().addParticle(
                    new net.minecraft.core.particles.DustParticleOptions(
                            new org.joml.Vector3f(r, g, b), 1.25F),
                    x, y, z, 0.0D, 0.008D, 0.0D);
        }
    }

    /** 把头部当前位置记进轨迹，并采样出各体节位置。 */
    private void advanceTrail() {
        // 保存上一 tick 的结果供渲染插值，否则高速移动时会有明显"步进感"
        System.arraycopy(this.segments, 0, this.segmentsPrev, 0, this.segments.length);

        this.trail.push(this.getX(), this.getY(), this.getZ());
        this.trail.sample(this.segments, UdunosTuning.SEGMENTS, UdunosTuning.SEGMENT_SPACING);
        this.updateCullBox();
    }

    /** 重算覆盖整条虫的包围盒 —— 渲染剔除靠它，否则摄像机稍一偏移整条虫就消失。 */
    private void updateCullBox() {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < UdunosTuning.SEGMENTS; i++) {
            double x = this.segments[i * 3];
            double y = this.segments[i * 3 + 1];
            double z = this.segments[i * 3 + 2];
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
        double r = UdunosTuning.BODY_DIAMETER;
        this.cullMinX = minX - r;
        this.cullMinY = minY - r;
        this.cullMinZ = minZ - r;
        this.cullMaxX = maxX + r;
        this.cullMaxY = maxY + r;
        this.cullMaxZ = maxZ + r;
    }

    // ================================================================
    // 阶段转场
    // ================================================================

    /**
     * 检测换阶段，并做清场与演出。
     *
     * <h2>为什么清场是硬性要求</h2>
     * 原设计写得很明确："达到血量阈值立即切阶段，<b>取消并清理上一阶段
     * 危险弹幕</b>，播放转场。这一条优先于'技能放完再换招'。"
     *
     * <p>不清的后果是：玩家在血量跌破 60% 的那一刻，同时面对
     * 第三阶段的新招和第二阶段残留的一片弹幕 —— 那不是难，是脏。
     * 转场必须是一个明确的"现在安全"的呼吸点。
     *
     * <h2>清哪些</h2>
     * 场上所有星弹 + 所有场效果（激光 / 审判柱 / 星痕 / 星门）。
     * 限制圈也一起清 —— 新阶段会自己重新立圈。
     */
    private void checkPhaseTransition(ServerLevel level) {
        UdunosPhase now = this.phase();
        if (now == this.lastPhase) {
            return;
        }
        UdunosPhase from = this.lastPhase;
        this.lastPhase = now;
        Medals.LOGGER.info("[LR/udunos] 阶段切换 {} -> {}  (hp {}/{} = {})",
                from, now, this.getHealth(), this.getMaxHealth(),
                String.format("%.2f", this.getHealth() / Math.max(1.0F, this.getMaxHealth())));

        // ---- 清场 ----
        double radius = 200.0D;
        AABB area = new AABB(this.getX() - radius, this.getY() - radius, this.getZ() - radius,
                this.getX() + radius, this.getY() + radius, this.getZ() + radius);
        for (UdunosBolt bolt : level.getEntitiesOfClass(UdunosBolt.class, area)) {
            bolt.discard();
        }
        for (UdunosEffect effect : level.getEntitiesOfClass(UdunosEffect.class, area)) {
            effect.discard();
        }
        // 招式自己也可能握着临时引用 —— 通知它收手
        var currentMove = this.brain.current();
        if (currentMove != null) {
            this.brain.interrupt(level);
        }

        // ---- 演出 ----
        UdunosFx.playPhaseShift(level, this.position());

        // ★ 转场必须是"大字 + 音效"，不能只往聊天栏丢一行字。
        //   实测反馈："打他的时候没有任何转阶段的提示，也没有阶段这一说"——
        //   聊天栏会被战斗信息刷掉，玩家根本注意不到。
        //   这里用 Title（屏幕中央大字，60 tick 停留）+ 低音调凋灵生成音。
        Component title = Component.literal("第 " + romanOf(now.index() + 1) + " 阶段")
                .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE)
                .withStyle(net.minecraft.ChatFormatting.BOLD);
        Component subtitle = Component.translatable(now.langKey())
                .withStyle(net.minecraft.ChatFormatting.WHITE);

        AABB announce = new AABB(this.cullMinX, this.cullMinY, this.cullMinZ,
                this.cullMaxX, this.cullMaxY, this.cullMaxZ).inflate(80.0D);
        for (Player player : level.getEntitiesOfClass(Player.class, announce)) {
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.connection.send(new net.minecraft.network.protocol.game
                        .ClientboundSetTitlesAnimationPacket(8, 60, 22));
                sp.connection.send(new net.minecraft.network.protocol.game
                        .ClientboundSetTitleTextPacket(title));
                sp.connection.send(new net.minecraft.network.protocol.game
                        .ClientboundSetSubtitleTextPacket(subtitle));
            }
            // 聊天栏也留一条，方便回看
            player.displayClientMessage(
                    Component.translatable(now.langKey())
                            .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), false);
        }

        // 低音调、大音量 —— 这一声是"它变强了"的听觉信号
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                net.minecraft.sounds.SoundEvents.WITHER_SPAWN,
                net.minecraft.sounds.SoundSource.HOSTILE, 4.0F, 0.55F);

        // 转场期间短暂无敌，避免玩家在弹幕清理的空隙里被贴脸打死
        this.invulnerableTime = Math.max(this.invulnerableTime, 30);
    }

    /** 1 → I、2 → II …… 用于转场大字。 */
    private static String romanOf(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> "IV";
        };
    }

    // ================================================================
    // 招式播报
    // ================================================================

    /**
     * 每换一招，给附近玩家在 actionbar 上打一次招式名。
     *
     * <h2>为什么值得为"显示一行字"写一段代码</h2>
     * 原设计的目标是"高难度但<b>有可理解</b>的预警与可执行的躲避路径"。
     * 那三条红线的形状确实能告诉玩家"往哪躲"，但告诉不了
     * "现在面对的是哪一招、后面还会不会有第二段"——
     * 而后者恰恰是初见的玩家最需要的信息：他要能<b>给招式起个名字</b>，
     * 才会去记它、去复盘、去下次做得更好。
     *
     * <p>只在换招那一 tick 播报一次，不刷屏。
     */
    private void announceMoveIfChanged(ServerLevel level) {
        var move = this.brain.current();
        String id = move == null ? null : move.id();
        if (id == null || id.equals(this.announcedMove)) {
            return;
        }
        this.announcedMove = id;

        Component message = Component.translatable(move.langKey())
                .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE);
        AABB area = new AABB(this.cullMinX, this.cullMinY, this.cullMinZ,
                this.cullMaxX, this.cullMaxY, this.cullMaxZ).inflate(64.0D);
        for (Player player : level.getEntitiesOfClass(Player.class, area)) {
            player.displayClientMessage(message, true);
        }
    }

    // ================================================================
    // 状态同步
    // ================================================================

    private void syncStateToClient(@Nullable Player target) {
        this.entityData.set(DATA_PHASE, this.phase().index());
        this.entityData.set(DATA_ACTION, this.brain.action().ordinal());
        this.entityData.set(DATA_EMERGED, this.isFullyEmerged());

        // 招式写入的渲染参数只在"确实在出招"时提交。
        // 不这么挡一下的话，招式演完的那一 tick 会因为 pending 还是旧值，
        // 在屏幕上留下一根挥之不去的红线。
        boolean inMove = this.brain.action() == UdunosBrain.Action.MOVE;
        this.entityData.set(DATA_MOVE, inMove ? this.pendingMove.ordinal() : MoveKind.NONE.ordinal());
        this.entityData.set(DATA_PARAM_A, inMove ? this.pendingParamA : 0);
        this.entityData.set(DATA_PARAM_B, inMove ? this.pendingParamB : 0);
        this.entityData.set(DATA_WARNING, inMove ? this.pendingWarning : 0);

        Vec3 aim = inMove ? this.pendingAim : Vec3.ZERO;
        if (aim.lengthSqr() > 1.0E-6D) {
            this.entityData.set(DATA_AIM, new Vector3f((float) aim.x, (float) aim.y, (float) aim.z));
        }

        // 重置待提交值：招式必须每 tick 主动写入，而不是"设一次就一直有效"
        this.pendingMove = MoveKind.NONE;
        this.pendingAim = Vec3.ZERO;
        this.pendingWarning = 0;
        this.pendingParamA = 0;
        this.pendingParamB = 0;
    }

    // ================================================================
    // 碰撞部件
    // ================================================================

    /**
     * 建立碰撞部件 —— <b>每一节一个</b>。
     *
     * <p>为什么不合并成几个大盒子：MC 的 {@code EntityDimensions} 只有宽和高，
     * 部件盒子永远只能是立方体。想让一个盒子覆盖 6.4 格长的一段，它就得是
     * 6.4 格立方 —— 而身体只有 2.4 格粗，横向会白多出 4 格空气判定。
     * 每节一个 2.6 格立方体反而<b>更精确</b>：节距 2.4，相邻盒子轻微重叠，无缝覆盖。
     */
    private void ensureParts(ServerLevel level) {
        if (this.parts != null) {
            return;
        }
        int count = UdunosTuning.SEGMENTS;
        UdunosPart[] created = new UdunosPart[count];
        for (int i = 0; i < count; i++) {
            UdunosPart part = new UdunosPart(this);
            level.addFreshEntity(part);
            created[i] = part;
        }
        this.parts = created;
    }

    /** 每 tick 把第 i 个部件挪到第 i 节的位置。 */
    private void updateParts(ServerLevel level) {
        this.ensureParts(level);
        UdunosPart[] arr = this.parts;
        if (arr == null) {
            return;
        }
        int count = Math.min(arr.length, UdunosTuning.SEGMENTS);
        for (int i = 0; i < count; i++) {
            UdunosPart part = arr[i];
            // ★ 部件可能被外部移除（玩家打掉、指令清场、其它模组的世界事件）。
            // 一旦失效就必须补一个新的 —— 否则那一段身体从此再也砍不中，
            // 而 Boss 看上去依旧完整，玩家只会觉得"我的刀从它身上穿过去了"。
            // 这类"判定悄悄少了一块"的问题比直接崩溃更难查，所以宁可每 tick 多问一句。
            if (part == null || part.isRemoved()) {
                part = new UdunosPart(this);
                level.addFreshEntity(part);
                arr[i] = part;
            }
            part.snapTo(this.segments[i * 3], this.segments[i * 3 + 1], this.segments[i * 3 + 2]);
        }
    }

    /** 移除全部部件（Boss 死亡、被移除、卸载时）。 */
    private void discardParts() {
        UdunosPart[] arr = this.parts;
        this.parts = null;
        if (arr == null) {
            return;
        }
        for (UdunosPart part : arr) {
            if (part != null && !part.isRemoved()) {
                part.discard();
            }
        }
    }

    // ================================================================
    // 接触伤害
    // ================================================================

    /**
     * 身体碰到玩家的伤害。
     *
     * <p>头部（本体碰撞箱）与身体（部件碰撞箱）给不同的伤害 ——
     * 原设计头部 560 / 身体 330，换算后是 14 / 8。
     * 头部更高是为了惩罚"被迎面撞上"，而不是"被身体蹭了一下"。
     */
    private void tickContactDamage(ServerLevel level, @Nullable Player target) {
        // 开场保护：身体还没铺开时不伤害玩家（原设计第 5.3 条）
        if (this.brain.action() == Action.EMERGE) {
            return;
        }
        // 招式级的保护窗口（原设计第 5.3 条同样适用于围圈）：
        // 囚星天环在环布阶段以 3.4 格/tick 掠过整圈，那一段是演出不是考验。
        var activeMove = this.brain.current();
        if (activeMove != null && activeMove.suppressesContact()) {
            return;
        }

        AABB range = new AABB(this.cullMinX, this.cullMinY, this.cullMinZ,
                this.cullMaxX, this.cullMaxY, this.cullMaxZ).inflate(1.0D);
        List<Player> players = level.getEntitiesOfClass(Player.class, range);
        if (players.isEmpty()) {
            return;
        }

        int now = this.tickCount;
        for (Player player : players) {
            if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
                continue;
            }
            Integer last = this.contactCooldowns.get(player.getUUID());
            if (last != null && now - last < UdunosTuning.CONTACT_COOLDOWN) {
                continue;
            }

            AABB hitbox = player.getBoundingBox();
            boolean head = this.getBoundingBox().inflate(0.3D).intersects(hitbox);
            boolean body = false;
            if (!head && this.parts != null) {
                for (UdunosPart part : this.parts) {
                    if (part.getBoundingBox().inflate(0.3D).intersects(hitbox)) {
                        body = true;
                        break;
                    }
                }
            }
            if (!head && !body) {
                continue;
            }

            float damage = head ? UdunosTuning.CONTACT_HEAD : UdunosTuning.CONTACT_BODY;
            if (player.hurt(this.damageSources().mobAttack(this), damage)) {
                this.contactCooldowns.put(player.getUUID(), now);
                // 撞上时给一点向上的位移，让"被巨物撞到"有实感
                Vec3 push = player.position().subtract(this.position());
                if (push.lengthSqr() > 1.0E-4D) {
                    Vec3 dir = push.normalize().scale(0.55D);
                    player.push(dir.x, 0.32D, dir.z);
                }
            }

            // 冷却表会随玩家加入/退出无限增长，顺手清掉过期条目
            if (this.contactCooldowns.size() > 32) {
                this.contactCooldowns.entrySet().removeIf(e -> now - e.getValue() > 200);
            }
        }
    }

    // ================================================================
    // 动态减伤
    // ================================================================

    /** 滚动失血窗口。放在每 tick 末尾：先把"最旧那一格"清掉，它才代表"刚刚过去的一秒之外"。 */
    private void rollBleedWindow() {
        this.bleedCursor = (this.bleedCursor + 1) % UdunosTuning.REDUCTION_WINDOW;
        this.bleedWindow[this.bleedCursor] = 0.0D;

        double sum = 0.0D;
        for (double v : this.bleedWindow) {
            sum += v;
        }
        double target = sum <= UdunosTuning.REDUCTION_THRESHOLD
                ? 0.0D
                : Mth.clamp(1.0D - UdunosTuning.REDUCTION_THRESHOLD / sum,
                        0.0D, UdunosTuning.REDUCTION_CAP);
        // 平滑：避免减伤值一帧跳到底，那样玩家会觉得"打着打着突然打不动了"
        this.reduction += (target - this.reduction) * UdunosTuning.REDUCTION_SMOOTH;
    }

    /** 本 tick 记录一笔实际失血。 */
    private void recordBleed(double amount) {
        this.bleedWindow[this.bleedCursor] += amount;
    }

    /** 当前动态减伤比例（0~0.75）。 */
    public double damageReduction() {
        return this.reduction;
    }

    // ================================================================
    // 受伤与死亡
    // ================================================================

    /**
     * 受伤结算。
     *
     * <h2>为什么要清掉 {@code invulnerableTime}</h2>
     * 原版给所有生物 20 tick 的受击无敌帧（1 秒内只有"更大的一刀"才算数）。
     * 对一个 24000 血的 Boss 来说这是灾难：玩家攻速 1.6/秒、每两次只有一次生效，
     * DPS 直接腰斩，战斗会被无意义地拉长一倍。所以这里清零，
     * 让每一次挥砍都落到实处 —— 真正需要压制的是"爆发"，
     * 而那交给下面的动态减伤处理，不该靠无敌帧一刀切。
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source) || this.level().isClientSide) {
            return false;
        }

        // ★ 放行「无视无敌」的伤害（/kill、虚空、掉出世界）。
        //
        //   否则会出现一个很难受的死锁：Boss 卡在 EMERGE（比如区块没加载、
        //   或被别的模组节流），而 EMERGE 期间 hurt() 无条件返回 false ——
        //   于是它既打不动、也删不掉，/kill 都杀不死，永远挡着下一次召唤。
        //   实测里就撞上过：一条卡在 (160,70,0.5) 的残留实体，
        //   kill 返回 "Killed 2 entities" 但它还在。
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }

        // 开场钻出期间无敌（原设计第 5.3 条）
        if (this.brain.action() == Action.EMERGE) {
            return false;
        }

        // 动态减伤：把超模爆发压下去，但最多压 75%。
        // 星核暴露期间减伤几乎失效 —— 那就是原设计要求的"输出窗口"。
        double effectiveReduction = this.exposed
                ? this.reduction * UdunosTuning.EXPOSED_REDUCTION_SCALE
                : this.reduction;
        float applied = (float) (amount * (1.0D - effectiveReduction));

        this.invulnerableTime = 0;
        boolean hit = super.hurt(source, applied);
        if (hit) {
            this.recordBleed(applied);
            if (this.level() instanceof ServerLevel sl) {
                UdunosSfx.hit(sl, this.position());
            }
        }
        return hit;
    }

    /**
     * 死亡演出 —— 约 4 秒的身体崩解。
     *
     * <h2>演出怎么排</h2>
     * 原设计要求"裂纹与能量沿尾部向头部传播，身体逐节崩解，
     * 最后头部星核内缩、闪光、冲击环爆发"。
     * 这里用一个从尾到头推进的"崩解波"来实现：
     * 波锋扫过的节直接炸成粒子，还没扫到的节保持完整 ——
     * 于是玩家看到的是"它从尾巴开始一寸寸碎掉"，而不是"整体炸没"。
     *
     * <h2>为什么物品在最后才掉</h2>
     * 在整个崩解过程中间掉东西，玩家的注意力会被地上的物品拉走，
     * 而演出最好看的部分（头部内缩 + 闪光）就在最后那一秒。
     * 先让玩家看完，再给奖励 —— 那个顺序是有意义的。
     */
    /** 每一节是否已经碎过 —— 防止崩解波停留时重复生成碎片。 */
    private final boolean[] shattered = new boolean[UdunosTuning.SEGMENTS];

    /**
     * 在某个体节处甩出一把装甲碎块。
     *
     * <p>朝外方向取「该节 − 虫身中段」，这样整条虫是"从中间向两侧炸开"，
     * 而不是所有碎片朝同一个方向飞。朝向继承体节切线，看起来像是
     * 从装甲片上剥下来的。
     */
    private void spawnShards(ServerLevel level, int index, int count) {
        double x = this.segments[index * 3];
        double y = this.segments[index * 3 + 1];
        double z = this.segments[index * 3 + 2];
        Vec3 pos = new Vec3(x, y, z);

        int mid = UdunosTuning.SEGMENTS / 2;
        Vec3 center = new Vec3(this.segments[mid * 3],
                this.segments[mid * 3 + 1], this.segments[mid * 3 + 2]);
        Vec3 outward = pos.subtract(center);
        outward = outward.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 1.0D, 0.0D) : outward.normalize();

        int prev = Math.max(0, index - 1);
        int next = Math.min(UdunosTuning.SEGMENTS - 1, index + 1);
        double dx = this.segments[next * 3] - this.segments[prev * 3];
        double dz = this.segments[next * 3 + 2] - this.segments[prev * 3 + 2];
        float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));

        for (int k = 0; k < count; k++) {
            UdunosShard.spawn(level, pos, outward, yaw, this.getRandom());
        }
    }

    @Override
    protected void tickDeath() {
        this.deathTime++;

        if (this.level().isClientSide) {
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        int total = 60;

        // ---- 崩解波：从尾到头推进 ----
        double sweep = (double) this.deathTime / total;      // 0 → 1
        int front = (int) (UdunosTuning.SEGMENTS * (1.0D - sweep));
        for (int i = 0; i < UdunosTuning.SEGMENTS; i++) {
            // 每一节只在波锋扫过它的那一 tick 碎一次 ——
            // 用 shattered[] 记住状态，否则波锋停留的这几 tick 会重复生成，
            // 碎块数量直接翻四倍（原设计上限 420）。
            if (this.shattered[i] || i < front - 1 || i > front + 1) {
                continue;
            }
            this.shattered[i] = true;
            // 50 节 × 7 + 头部 10 = 360 块，正好是设计稿的"正常约 360"。
            this.spawnShards(level, i, i == 0 ? 10 : 7);

            double x = this.segments[i * 3];
            double y = this.segments[i * 3 + 1];
            double z = this.segments[i * 3 + 2];
            level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(
                            new org.joml.Vector3f(0.85F, 0.6F, 1.0F), 1.5F),
                    x, y, z, 6, 0.9D, 0.9D, 0.9D, 0.12D);
        }

        // ---- 头部星核内缩 → 闪光 → 冲击环 ----
        if (this.deathTime == total - 12) {
            UdunosFx.playPhaseShift(level, this.position());
        }
        if (this.deathTime == total - 2) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLASH,
                    this.getX(), this.getY(), this.getZ(), 1, 0, 0, 0, 0);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                    this.getX(), this.getY(), this.getZ(), 3, 2.0D, 2.0D, 2.0D, 0);
        }

        if (this.deathTime >= total) {
            dropRelics(level);
            this.remove(Entity.RemovalReason.KILLED);
        }
    }

    /**
     * 掉落遗物 —— <b>五件职业武器 + 一件纪念品</b>。
     *
     * <p>全部散落在头部位置周围，而不是堆成一摞 —— 让玩家看见"掉了好几样"，
     * 而不是"掉了一个包裹"。这种视觉上的丰盛感，是结算一个
     * 打了几十分钟的 Boss 时最廉价也最有效的手段。
     *
     * <h2>为什么一次全掉，而不是随机掉一件</h2>
     * 原设计要求"宝藏袋必出四种武器各一把"。这是对的：
     * 最终 Boss 不该有"没抽到"这种事 —— 玩家已经付出了全部，
     * 抽卡式的收尾只会把成就感冒犯一遍。
     */
    private void dropRelics(ServerLevel level) {
        var random = this.getRandom();
        net.minecraft.world.item.Item[] drops =
                com.lost_realm.boss.relic.UdunosRelics.allDrops();
        Vec3 base = this.position();
        for (net.minecraft.world.item.Item item : drops) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = 2.0D + random.nextDouble() * 2.5D;
            net.minecraft.world.entity.item.ItemEntity entity =
                    new net.minecraft.world.entity.item.ItemEntity(level,
                            base.x + Math.cos(angle) * radius,
                            base.y + 0.6D,
                            base.z + Math.sin(angle) * radius,
                            new net.minecraft.world.item.ItemStack(item));
            entity.setDefaultPickUpDelay();
            entity.setExtendedLifetime();
            level.addFreshEntity(entity);
        }
        // 收尾的声音 —— 一声长的、往上扬的调子，把"结束了"这件事说清楚
        level.playSound(null, base.x, base.y, base.z,
                net.minecraft.sounds.SoundEvents.END_PORTAL_SPAWN,
                net.minecraft.sounds.SoundSource.HOSTILE, 4.0F, 0.5F);
    }

    @Override
    public void die(DamageSource source) {
        // 死亡时收回全部部件 —— 否则 15 个判定盒会作为"隐形障碍"留在世界上
        this.discardParts();
        super.die(source);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        Medals.LOGGER.info("[LR/udunos] REMOVE reason={} after {} ticks, hp={}, pos={}",
                reason, this.tickCount, this.getHealth(), fmt(this.position()));
        this.discardParts();
        this.bossBar.removeAllPlayers();
        super.remove(reason);
    }

    // ================================================================
    // 原版行为的关闭开关
    // ----------------------------------------------------------------
    // 这条虫不该被推动、不该被挤压、不该踩进岩浆、也不该被"寻路"逻辑
    // 拽着走。把它们一条条关掉，比想办法让它们"工作正常"便宜得多。
    // ================================================================

    /** 位置完全由路径驱动，不参与原版移动管线。 */
    @Override
    public void travel(Vec3 input) {
        // 故意为空
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    /** 不推别的实体 —— 96 格的虫身经过时会把周围一切挤飞。 */
    @Override
    protected void pushEntities() {
        // 故意为空
    }

    @Override
    public boolean isInWall() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    /** 不因距离过远而消失 —— 这是 Boss。 */
    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean shouldDespawnInPeaceful() {
        return false;
    }

    /** 掉落由死亡演出另外处理，这里关掉原版战利品表。 */
    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    /** 不做"卡在方块里就往上顶"的修正。 */
    @Override
    public void checkInsideBlocks() {
        // 故意为空
    }

    // ================================================================
    // 血条
    // ================================================================

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossBar.removePlayer(player);
    }

    private void updateBossBar() {
        this.bossBar.setProgress(Mth.clamp(
                this.getHealth() / Math.max(1.0F, this.getMaxHealth()), 0.0F, 1.0F));
        this.bossBar.setColor(this.phase().barColor());
    }

    // ================================================================
    // 访问器（供渲染器、部件、后续招式使用）
    // ================================================================

    /** 当前阶段。以血量比例即时判定 —— 原设计要求"达到阈值立即切阶段"。 */
    public UdunosPhase phase() {
        return UdunosPhase.forHealth(
                this.getHealth() / Math.max(1.0F, this.getMaxHealth()));
    }

    /** 客户端侧读到的阶段（取自同步数据）。 */
    public UdunosPhase syncedPhase() {
        return UdunosPhase.byIndex(this.entityData.get(DATA_PHASE));
    }

    /** 客户端侧读到的行为。 */
    public Action syncedAction() {
        return Action.values()[Mth.clamp(
                this.entityData.get(DATA_ACTION), 0, Action.values().length - 1)];
    }

    /** 客户端侧读到的招式种类。 */
    public MoveKind syncedMove() {
        int i = this.entityData.get(DATA_MOVE);
        MoveKind[] values = MoveKind.values();
        return values[Mth.clamp(i, 0, values.length - 1)];
    }

    /** 客户端侧读到的招式参数 A。 */
    public int syncedParamA() {
        return this.entityData.get(DATA_PARAM_A);
    }

    /** 客户端侧读到的招式参数 B。 */
    public int syncedParamB() {
        return this.entityData.get(DATA_PARAM_B);
    }

    /** 客户端侧读到的预警方向。 */
    public Vec3 syncedAim() {
        Vector3f v = this.entityData.get(DATA_AIM);
        return new Vec3(v.x(), v.y(), v.z());
    }

    /** 客户端侧读到的预警剩余 tick。 */
    public int syncedWarning() {
        return this.entityData.get(DATA_WARNING);
    }

    public UdunosBrain brain() {
        return this.brain;
    }

    /** 体节位置数组（当前 tick）。渲染器直接读它，避免每帧重新采样。 */
    public double[] segments() {
        return this.segments;
    }

    /** 上一 tick 的体节位置，供渲染插值。 */
    public double[] segmentsPrev() {
        return this.segmentsPrev;
    }

    public int segmentCount() {
        return UdunosTuning.SEGMENTS;
    }

    /** 身体是否已完全展开（开场演出结束的判据）。 */
    public boolean isFullyEmerged() {
        return this.trail.isReady(UdunosTuning.SEGMENTS, UdunosTuning.SEGMENT_SPACING);
    }

    /** 星核是否处于暴露状态（客户端可用来加一层视觉提示）。 */
    public boolean isCoreExposed() {
        return this.exposed;
    }

    /** 轨迹 —— 招式跨星门时需要用 {@code markBreak()} 打断它。 */
    public UdunosTrail trail() {
        return this.trail;
    }

    /** 头部的世界坐标（= 实体位置）。 */
    public Vec3 headPosition() {
        return this.position();
    }

    /**
     * 取第 {@code index} 节的当前位置。
     *
     * <p>招式用它把弹幕从身体的<b>特定部位</b>射出来 ——
     * 原设计要求的"从头、身体、尾部的候选出口发射"就靠这个实现：
     * 从尾巴射出的弹幕和从头射出的是同一套逻辑，只是起点不同。
     */
    public Vec3 segmentPosition(int index) {
        int i = Mth.clamp(index, 0, UdunosTuning.SEGMENTS - 1);
        return new Vec3(this.segments[i * 3], this.segments[i * 3 + 1], this.segments[i * 3 + 2]);
    }

    /** 事件视界之类的战利品用（部件被拾取时显示）。 */
    @Override
    public ItemStack getPickResult() {
        return ItemStack.EMPTY;
    }

    /**
     * 渲染剔除盒 —— 必须覆写。
     *
     * <p>默认实现返回头部 2.4 格的碰撞箱，于是摄像机稍一移动，
     * 96 格的虫身就被整体剔掉、在屏幕上凭空消失。
     */
    @Override
    public AABB getBoundingBoxForCulling() {
        // ★ 客户端优先用「服务端下发的脊线」算。
        //   这条链路不依赖任何本地状态，因此不会因为"客户端位置没同步"
        //   而退化成几格大的小盒 —— 那正是"要特定角度才看得见"的根因：
        //   视锥测试对的是一个点，点一出画面，96 格的身体被整体剔除。
        double[] pts = this.spineNow;
        if (pts != null && pts.length >= 6) {
            return boxOfPoints(pts, UdunosTuning.BODY_DIAMETER);
        }

        // 还没有脊线（刚生成的那一瞬）：用体节盒 ∪ 本体盒。
        // ★ 必须把「本体」并进来 —— 体节盒要等第一次 advanceTrail 之后才有值，
        //   在此之前这些字段还是 0，单独返回它就是一个「原点处的零体积盒」。
        AABB body = new AABB(this.cullMinX, this.cullMinY, this.cullMinZ,
                this.cullMaxX, this.cullMaxY, this.cullMaxZ);
        AABB self = super.getBoundingBoxForCulling();
        AABB union = new AABB(
                Math.min(body.minX, self.minX), Math.min(body.minY, self.minY),
                Math.min(body.minZ, self.minZ),
                Math.max(body.maxX, self.maxX), Math.max(body.maxY, self.maxY),
                Math.max(body.maxZ, self.maxZ));
        // 客户端这份自算数据可能是塌缩的，多撑出半个身长。
        // 代价只是"视野外时仍会进一次渲染器"（渲染器里逐节还有 160 格距离判断，不会真画顶点），
        // 换的是绝不会再出现"转个角度整条虫消失"。
        return this.level().isClientSide ? union.inflate(UdunosTuning.EMERGE_ARC * 0.5D) : union;
    }

    /** 用一串扁平坐标（x,y,z,x,y,z…）算包围盒，四周留 r 的余量。 */
    private static AABB boxOfPoints(double[] pts, double r) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int i = 0; i + 2 < pts.length; i += 3) {
            double x = pts[i];
            double y = pts[i + 1];
            double z = pts[i + 2];
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }
        if (minX > maxX) {
            return new AABB(0, 0, 0, 0, 0, 0);
        }
        return new AABB(minX - r, minY - r, minZ - r, maxX + r, maxY + r, maxZ + r);
    }

    /** 不要因为距离远而降低 tick 频率 —— 高速移动的虫身需要每 tick 更新。 */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256.0D * 256.0D;
    }

    // ================================================================
    // 存档
    // ================================================================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("lr_reduction", this.reduction);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.reduction = tag.getDouble("lr_reduction");
    }

    // ================================================================
    // 预警线的客户端粒子
    // ================================================================

    /**
     * 画当前招式的预警。
     *
     * <p>全部用<b>客户端本地粒子</b>而不是服务端广播 —— 一条几十格长、
     * 每 tick 几十颗的线如果走网络，带宽会白白烧掉；
     * 而它纯粹是"给玩家看的提示"，客户端自己生成即可。
     *
     * <p>三种招式的预警形态各不相同，这也是它们能被玩家区分开的原因：
     * <ul>
     *   <li><b>噬光冲锋</b> —— 一根长红线，随玩家移动而缓慢转向，最后锁定。</li>
     *   <li><b>碎星播撒</b> —— 两个出口各三根线，张成一个扇形，告诉玩家"这片区域会被覆盖"。</li>
     *   <li><b>星弧巡猎</b> —— 不画线。它的预警在种子弹自己身上（那圈收拢的光环）。</li>
     * </ul>
     */
    private void spawnMoveWarningFx() {
        MoveKind kind = this.syncedMove();
        if (kind == MoveKind.NONE) {
            return;
        }
        switch (kind) {
            case CHARGE -> this.fxCharge();
            case SCATTER -> this.fxScatter();
            default -> {
                // HUNT 的预警由种子弹负责；其余招式尚未接入
            }
        }
    }

    /** 噬光冲锋：一根长红线。 */
    private void fxCharge() {
        int left = this.syncedWarning();
        if (left <= 0) {
            return;
        }
        Vec3 aim = this.syncedAim();
        if (aim.lengthSqr() < 1.0E-6D) {
            return;
        }
        // 剩余时间越少画得越密 —— 玩家不看数字也能感到"要来了"
        boolean alternate = left > 6;
        UdunosFx.warningLine(this.level(), this.position(), aim,
                UdunosTuning.WARNING_LINE_LENGTH, 1.0F, 0.12F, 0.18F,
                this.tickCount, alternate);
    }

    /**
     * 碎星播撒：两个出口各画一个扇形的边界与中线。
     *
     * <p>只画三根（左边界、中线、右边界）而不是把 11 发都画出来 ——
     * 6 根线已经足够让玩家看出"这片扇形是危险区"，
     * 而 66 根线除了把屏幕糊满没有任何信息增量。
     */
    private void fxScatter() {
        int left = this.syncedWarning();
        if (left <= 0) {
            return;
        }
        Vec3 aim = this.syncedAim();
        if (aim.lengthSqr() < 1.0E-6D) {
            return;
        }
        // 预警前段隔帧画，最后 8 tick 全画 —— 把粒子用在最需要的时候
        if (left > 8 && this.tickCount % 2 != 0) {
            return;
        }

        double half = Math.toRadians(UdunosTuning.SCATTER_SPREAD_DEG) * 0.5D;
        for (int exit : new int[]{this.syncedParamA(), this.syncedParamB()}) {
            int segment = UdunosTuning.scatterExitSegment(exit);
            Vec3 origin = this.segmentPosition(segment);
            for (int k = -1; k <= 1; k++) {
                Vec3 dir = UdunosFx.fan(aim, half * k);
                UdunosFx.warningLine(this.level(), origin, dir,
                        UdunosTuning.SCATTER_WARN_LINE, 1.0F, 0.30F, 0.42F,
                        this.tickCount, true);
            }
        }
    }
}
