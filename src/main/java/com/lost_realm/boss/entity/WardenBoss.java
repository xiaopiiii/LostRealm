package com.lost_realm.boss.entity;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import com.lost_realm.Medals;
import com.lost_realm.boss.BossRegistry;
import com.lost_realm.boss.LordState;
import com.lost_realm.boss.WardenFightManager;
import com.lost_realm.boss.WardenFightSession;
import com.lost_realm.boss.WardenPhase;
import com.lost_realm.boss.ai.WardenArrowStormGoal;
import com.lost_realm.boss.ai.WardenJudgementGoal;
import com.lost_realm.boss.ai.WardenPhaseShiftGoal;
import com.lost_realm.boss.ai.WardenSoulDrainGoal;
import com.lost_realm.boss.anim.WardenAnimations;

/**
 * 遗落之主 —— 整合包的终局 Boss。
 *
 * <h2>为什么继承 {@link Monster}</h2>
 * {@code Monster} 提供了 {@code Enemy} 标记(会被铁傀儡等敌视)、
 * 敌对音效、以及 {@code createMonsterAttributes()} 的默认属性模板。
 * 相比直接继承 {@code PathfinderMob},省去大量样板代码。
 *
 * <h2>状态如何同步到客户端</h2>
 * 用 {@link SynchedEntityData}(原版的数据同步机制)而不是自定义网络包:
 * <ul>
 *   <li>实体的状态变化频率低(整场战斗仅数次),不需要包级优化</li>
 *   <li>原版机制自带"新玩家加入时全量同步",无需额外处理</li>
 *   <li>省下一个 {@code SimpleChannel} 消息注册位</li>
 * </ul>
 * 因此这里只定义一个同步数据:{@code DATA_STATE}({@link LordState} 序号)。
 * 客户端据它决定<b>体型缩放</b>与<b>发光描边色</b>。
 *
 * <h2>体型变化为什么不写渲染器</h2>
 * 本模组零自定义渲染器。体型变化走原版钩子:
 * <ol>
 *   <li>覆写 {@link #getScale()} —— {@code LivingEntity#getDimensions} 内部
 *       已经做了 {@code super.getDimensions(pose).scale(this.getScale())}</li>
 *   <li>切换状态后<b>必须</b>调用 {@link #refreshDimensions()} —— 否则碰撞箱
 *       与眼高不会更新(这是最容易漏的一步)</li>
 * </ol>
 * <b>绝不要覆写 {@code getDimensions}</b> —— 父类已缩放,再缩一次会变成平方。
 *
 * <h2>进入方式</h2>
 * 无需召唤物。玩家搭黑曜石门 + 门钥匙点燃 → 传送进「遗落之都」,
 * 遗落之主已站在城中心高台上({@link LordState#DORMANT}),
 * 右键对话决定「离开」或「决斗」。
 */
public class WardenBoss extends Monster implements GeoAnimatable {

    // ---- 同步数据 ----
    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(WardenBoss.class, EntityDataSerializers.INT);

    /**
     * 换阶段演出的进度(0.0 → 1.0)。
     *
     * <h2>为什么必须走同步(dv10)</h2>
     * 体型插值原本只在服务端算(改 {@code transitionScale} 字段),
     * 客户端根本读不到 —— 结果就是<b>服务端碰撞箱在长大、客户端模型不动</b>,
     * 玩家看到的仍然是一次「瞬变」。做成同步数据后,
     * 客户端每 tick 都能拿到最新进度,模型才会平滑长大。
     */
    private static final EntityDataAccessor<Float> DATA_TRANSITION_PROGRESS =
            SynchedEntityData.defineId(WardenBoss.class, EntityDataSerializers.FLOAT);

    /**
     * 换阶段演出的起始体型(1.0 = 原始尺寸)。
     *
     * <p>同步给客户端 —— 因为客户端要知道"从多大开始长",
     * 单靠进度百分比是算不出绝对体型的。
     */
    private static final EntityDataAccessor<Float> DATA_TRANSITION_FROM_SCALE =
            SynchedEntityData.defineId(WardenBoss.class, EntityDataSerializers.FLOAT);

    /** 换阶段演出的目标体型。 */
    private static final EntityDataAccessor<Float> DATA_TRANSITION_TO_SCALE =
            SynchedEntityData.defineId(WardenBoss.class, EntityDataSerializers.FLOAT);

    /** 由外部(生成时)注入的战斗会话;实体本身不持有会话所有权。 */
    @Nullable
    private WardenFightSession session;

    /** 当前状态机状态(服务端权威;客户端读同步数据)。 */
    private LordState state = LordState.DORMANT;

    /** 锁血演出剩余 tick(0 表示不在演出中)。 */
    private int transitionTicks;

    /** 锁血演出总时长,用于计算回血进度。 */
    private int transitionTotalTicks;

    /**
     * 第二阶段的"本体"offset —— 用于区分分身与本体。
     *
     * <p>设计需求:"本体影子有颜色差异"。实现上不给实体加自定义标记,
     * 而是让分身与本体的 {@code isClone} 标记不同,
     * 客户端据它渲染不同颜色。玩家需要观察哪一只是本体。
     */
    private boolean clone;

    /** 是否已通关(用于避免重复结算掉落)。 */
    private boolean defeated;

    /** 幽灵态的「木剑一击」是否已经打出(防止重复扣血)。 */
    private boolean ghostStruck;

    /**
     * 幽灵态的木剑是否已装备。
     *
     * <p>单独用一个标记而不是复用 {@code ghostStruck} ——
     * 两者语义不同:装备发生在「进入幽灵态时」,出手发生在「贴近玩家时」。
     */
    private boolean ghostSwordEquipped;

    /** 幽灵态起始 tick(用于超时自动转波次的兜底)。 */
    private int ghostTicks;

    public WardenBoss(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 500;
        // 免疫击退 —— Boss 不该被玩家推来推去
        this.setPersistenceRequired();
    }

    // ================================================================
    // GeckoLib 动画(形态几何见 client/boss/WardenBossModel)
    // ================================================================

    private final AnimatableInstanceCache geoCache =
            GeckoLibUtil.createInstanceCache(this);

    /**
     * 一次性动作的剩余 tick(0 表示当前没有一次性动作)。
     *
     * <p>GeckoLib 的 {@code AnimationController} 本身会在动画放完后
     * 回落到 {@code onState} 的默认分支,但它<b>不知道该动画有多长</b> ——
     * 若我们持续返回 {@code setAndContinue(ATTACK)},
     * 它会每 tick 重启动一次,攻击动画永远卡在第一帧。
     * 因此用一个显式的倒计时让实体自己"收招"。
     */
    public int actionTicks;

    /** 当前一次性动作(仅在 {@link #actionTicks} > 0 时有效)。 */
    public software.bernie.geckolib.core.animation.RawAnimation currentAction =
            WardenAnimations.IDLE;

    /** 攻击序号 —— 用于轮换三条手势(见 {@code WardenAnimations#nextAttack})。 */
    private int attackCounter;

    /**
     * 播放一次「一次性动作」(攻击 / 受击)。
     *
     * <p>换阶段演出期间忽略 —— 演出优先级最高,不该被打断。
     *
     * @param anim  要播的动画
     * @param ticks 时长(tick)。GeckoLib 的时长是秒,这里由调用方
     *              按动画文件里的 {@code animation_length} 乘 20 传入
     */
    public void playAction(software.bernie.geckolib.core.animation.RawAnimation anim, int ticks) {
        if (anim == null || ticks <= 0) {
            return;
        }
        // 演出态 / 待机态不播战斗动作
        LordState st = getLordState();
        if (st.isTransition() || !st.isCombatForm()) {
            return;
        }
        this.currentAction = anim;
        this.actionTicks = Math.max(this.actionTicks, ticks);
    }

    /** 攻击序号(每次出手自增,供手势轮换)。 */
    public int getAttackCounter() {
        return attackCounter;
    }

    /**
     * 近战出手 —— 播对应的攻击手势。
     *
     * <h2>为什么覆写这里(dv14)</h2>
     * {@code doHurtTarget} 是 {@code Mob} 近战命中时唯一的汇聚点
     * (AI Goal、{@code MeleeAttackGoal}、技能代码最终都会走到它)。
     * 覆写它就能保证「凡是对玩家造成了近战伤害,就一定播了攻击动画」,
     * 不需要在每个 Goal 里单独补一行 —— 少一处遗漏就少一个"哑火"。
     */
    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && !level().isClientSide) {
            // 按序号轮换三条手势;时长略长于动画长度,留出收招缓冲
            playAction(WardenAnimations.nextAttack(this), 20);
            this.attackCounter++;
        }
        return hit;
    }

    /**
     * 注册动画控制器。
     *
     * <p>ⓘ <b>几何换了、控制器不用换。</b>
     * 三套几何文件(form1/2/3)里的动画 id 是<b>同名</b>的
     * ({@code animation.warden.idle} 等),因此这里只需要一个控制器,
     * 它只管「按语义状态选动画」;具体加载哪套动画文件由
     * {@code WardenBossModel#getAnimationResource} 按形态决定。
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<WardenBoss>(
                this, "main", 4, WardenAnimations::onState));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    @Override
    public double getTick(Object entity) {
        return this.tickCount;
    }

    // ================================================================
    // 属性
    // ================================================================

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, BossRegistry.bossMaxHealth())
                .add(Attributes.ARMOR, 20.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 12.0D)
                .add(Attributes.ATTACK_DAMAGE, 18.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)   // 100% 免疫击退
                .add(Attributes.MOVEMENT_SPEED, WardenPhase.PHASE_1.moveSpeed())
                .add(Attributes.FOLLOW_RANGE, 96.0D);
    }

    // ================================================================
    // 同步数据
    // ================================================================

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_STATE, LordState.DORMANT.ordinal());
        this.entityData.define(DATA_TRANSITION_PROGRESS, 0.0F);
        this.entityData.define(DATA_TRANSITION_FROM_SCALE, 1.0F);
        this.entityData.define(DATA_TRANSITION_TO_SCALE, 1.0F);
    }

    /**
     * 客户端收到状态同步时刷新碰撞箱。
     *
     * <h2>为什么必须覆写(dv10 修)</h2>
     * 原版 {@code EntityDataAccessor} 只在注册时声明了
     * "会影响尺寸"的那些(如史莱姆大小、姿势)才会自动触发
     * {@code refreshDimensions()}。{@code DATA_STATE} 是我们自定义的,
     * 原版不认识它 —— 于是客户端虽然能通过 {@code getScale()}
     * 把<b>模型</b>画大,但<b>碰撞箱</b>一直停留在旧尺寸。
     *
     * <p>后果:选了 Boss 会"看着很大却打不到",或被卡在方块里
     * (碰撞箱与实际模型不匹配)。
     *
     * <p>换阶段进度({@code DATA_TRANSITION_PROGRESS})每 tick 都在变,
     * 同样需要刷新 —— 否则长大过程中碰撞箱是跳变的而不是连续的。
     */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_STATE.equals(key) || DATA_TRANSITION_PROGRESS.equals(key)) {
            refreshDimensions();
        }
    }

    /** 当前状态(客户端读同步数据,服务端读权威字段)。 */
    public LordState getLordState() {
        if (level().isClientSide) {
            return LordState.byIndex(this.entityData.get(DATA_STATE));
        }
        return state;
    }

    /**
     * 切换状态 —— 状态机的<b>唯一入口</b>。
     *
     * <p>职责:
     * <ol>
     *   <li>写同步数据(客户端据此换体型与描边色)</li>
     *   <li><b>调用 {@link #refreshDimensions()}</b> —— 否则 {@link #getScale()}
     *       变了但碰撞箱不变</li>
     *   <li>按状态调整属性(移速)、可见性、无敌标记、血条</li>
     * </ol>
     * 非法转移由调用方(状态推进逻辑)负责把关,本方法不做拒绝 ——
     * 它只保证「切过去之后实体处于自洽状态」。
     */
    public void setLordState(LordState next) {
        if (next == null) {
            return;
        }
        this.state = next;
        this.entityData.set(DATA_STATE, next.ordinal());

        // ★ 关键:体型变了必须刷碰撞箱
        refreshDimensions();

        if (level().isClientSide) {
            return;
        }

        // 无敌状态与可见性
        boolean invul = next.isInvulnerableState();
        this.setInvulnerable(invul);
        // 波次期间本体彻底离场(不可见、无碰撞),由 WaveManager 唱主角
        this.setInvisible(next == LordState.WAVES);
        this.noPhysics = false;
        this.setNoGravity(next == LordState.FORM_3);

        // 移速随状态变化
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(moveSpeedFor(next));
        }
        var dmg = getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) {
            dmg.setBaseValue(attackDamageFor(next));
        }

        // 只在战斗形态里追人;其余状态清目标
        if (!next.isCombatForm()) {
            setTarget(null);
            getNavigation().stop();
        }

        // 血条显隐
        if (session != null) {
            session.setBarVisible(next.barVisible());
            session.applyBarColor(next.barColor());
        }

        // 描边色(无渲染器下的"颜色区分")
        com.lost_realm.boss.LordGlowTeams.applyTo(this, next);

        // ★ dv30：进入战斗/演出形态就把死神镰握到主手。
        //   镰刀现在是独立物品，靠 WardenItemInHandLayer 挂到 weapon 骨骼上。
        //   （幽灵态的木剑不在这里处理 —— 它在 tickGhost 里单独装备，
        //     且必须先于本分支之后的任何覆盖。）
        if (next == LordState.FORM_1 || next == LordState.FORM_2 || next == LordState.FORM_3
                || next == LordState.TRANSITION_1 || next == LordState.TRANSITION_2) {
            equipScythe();
        }

        // 粒子 + 音效演出
        playStateSound(next);
    }

    /**
     * 把死神镰握到主手（dv30）。
     *
     * <p>先比对再设置：{@code setItemInHand} 会重置主手物品与挥砍动画，
     * 每 tick 无脑调用会让攻击动作看起来"卡住"（dv10 踩过同样的坑）。
     */
    private void equipScythe() {
        net.minecraft.world.item.ItemStack cur =
                getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (cur.is(com.lost_realm.boss.BossItems.WARDEN_SCYTHE.get())) {
            return;
        }
        setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(
                        com.lost_realm.boss.BossItems.WARDEN_SCYTHE.get()));
    }

    /**
     * ★ dv32 兜底：确保战斗/演出形态下手里一直握着镰刀。
     *
     * <p>为什么需要兜底：{@link #setLordState} 里虽然会 {@link #equipScythe()}，
     * 但那只在<b>状态切换</b>时触发 —— 从存档加载回来的老实体、或
     * {@code /summon} 出来的 Boss，其 {@code state} 是直接还原/赋初值的，
     * 未必走过 {@code setLordState}，于是手里空空（用户实测：手上没拿镰刀）。
     * 这里每 20 tick 校验一次：只要处于战斗/演出形态且主手不是镰刀就补上。
     * 幽灵态（木剑）与其它状态不受影响。
     */
    private void ensureScytheEquipped() {
        LordState s = getLordState();
        if (s != LordState.FORM_1 && s != LordState.FORM_2 && s != LordState.FORM_3
                && s != LordState.TRANSITION_1 && s != LordState.TRANSITION_2) {
            return;
        }
        equipScythe();
    }

    /** 各状态的移速基准。 */
    private static double moveSpeedFor(LordState s) {
        return switch (s) {
            case DORMANT -> 0.0D;
            case ENRAGED_GHOST -> 0.62D;    // 极速冲脸
            case WAVES -> 0.0D;
            case FORM_1 -> WardenPhase.PHASE_1.moveSpeed();
            case TRANSITION_1 -> 0.0D;
            case FORM_2 -> WardenPhase.PHASE_2.moveSpeed();
            case TRANSITION_2 -> 0.0D;
            case FORM_3 -> WardenPhase.PHASE_3.moveSpeed();
            case DEFEATED -> 0.0D;
        };
    }

    /** 各状态的攻击力基准。 */
    private static double attackDamageFor(LordState s) {
        return switch (s) {
            case FORM_1 -> 18.0D;
            case FORM_2 -> 18.0D * WardenPhase.PHASE_2.damageMultiplier();
            case FORM_3 -> 18.0D * WardenPhase.PHASE_3.damageMultiplier();
            default -> 18.0D;
        };
    }

    /** 状态切换的听觉提示 —— 让玩家不看屏幕也知道"它变了"。 */
    private void playStateSound(LordState next) {
        switch (next) {
            case ENRAGED_GHOST -> playSound(SoundEvents.WARDEN_ROAR, 3.0F, 0.5F);
            case FORM_1 -> playSound(SoundEvents.WARDEN_ROAR, 3.0F, 0.9F);
            case TRANSITION_1, TRANSITION_2 ->
                    playSound(SoundEvents.END_PORTAL_SPAWN, 2.5F, 0.6F);
            case FORM_2 -> playSound(SoundEvents.WARDEN_ROAR, 3.0F, 0.8F);
            case FORM_3 -> playSound(SoundEvents.WARDEN_ROAR, 3.0F, 0.6F);
            case DEFEATED -> playSound(SoundEvents.WARDEN_DEATH, 3.0F, 0.7F);
            default -> { }
        }
    }

    // ================================================================
    // 体型 —— 无渲染器方案的核心
    // ================================================================

    /**
     * 体型缩放钩子。
     *
     * <p>{@code LivingEntity#getDimensions(Pose)} 内部已做
     * {@code super.getDimensions(pose).scale(this.getScale())},
     * 因此只需覆写本方法即可实现「同一实体、多种体型」。
     *
     * <p>切换状态时请务必调用 {@code refreshDimensions()},否则碰撞箱不更新。
     *
     * <p>⚠️ <b>换阶段演出期间返回动态插值</b>——数据来源是<b>同步数据</b>
     * ({@link #DATA_TRANSITION_FROM_SCALE} / {@link #DATA_TRANSITION_TO_SCALE} /
     * {@link #DATA_TRANSITION_PROGRESS}),因此<b>服务端与客户端算出同一个值</b>,
     * 不会出现"服务端碰撞箱在长大、客户端模型不动"的错位。
     */
    @Override
    public float getScale() {
        if (isTransitioning()) {
            float from = this.entityData.get(DATA_TRANSITION_FROM_SCALE);
            float to = this.entityData.get(DATA_TRANSITION_TO_SCALE);
            float p = this.entityData.get(DATA_TRANSITION_PROGRESS);
            // easeOutQuad:前期长得快、后期趋稳,观感比线性更"有劲"
            float eased = 1.0F - (1.0F - p) * (1.0F - p);
            return from + (to - from) * eased;
        }
        return getLordState().scale();
    }

    // ================================================================
    // AI
    // ================================================================

    @Override
    protected void registerGoals() {
        // 优先级数字越小越优先
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // 技能 Goal 放在近战之前,保证技能优先释放
        this.goalSelector.addGoal(1, new WardenJudgementGoal(this));
        this.goalSelector.addGoal(2, new WardenPhaseShiftGoal(this));
        this.goalSelector.addGoal(3, new WardenArrowStormGoal(this));
        this.goalSelector.addGoal(4, new WardenSoulDrainGoal(this));
        // ⚠️ 近战必须用「按状态自锁」的版本,不能直接用 MeleeAttackGoal。
        // 原因见 GatedMeleeAttackGoal 的类注释 —— 幽灵态下它会和
        // tickGhost() 的强制一击叠加,造成玩家一瞬被打两次(「两巴掌」)。
        this.goalSelector.addGoal(5, new GatedMeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 32.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(0, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(
                this, Player.class, false, false));
    }

    /**
     * 只允许在<b>战斗形态</b>出手的近战 Goal。
     *
     * <h2>为什么必须包一层(dv9 修)</h2>
     * 原版 {@link MeleeAttackGoal} 一旦注册就<b>永远有效</b>,
     * 它的 {@code canUse}/{@code canContinueToUse} 只看"目标是否可达"、
     * <b>不看实体自己的状态机</b>。于是幽灵态出现了叠加伤害:
     * <pre>
     *   幽灵态 ──► tickGhost() 每 tick 强制冲脸
     *            └─ 进入 1.0 格近战距离 ──► MeleeAttackGoal 也判定命中,
     *               按 ATTACK_DAMAGE 打一下           ① 原版近战伤害
     *            └─ 同时 dist ≤ GHOST_STRIKE_RANGE,
     *               strikePlayer() 强制扣 50% 上限血   ② 演出伤害
     * </pre>
     * 两下几乎同一刻结算 → 玩家看到的就是"被两巴掌打死"。
     *
     * <p>把这个 Goal 收紧为「仅战斗形态可用」后,幽灵态的伤害来源
     * <b>只剩 {@code strikePlayer()} 一处</b>,且那一处本身已有不致死保底。
     *
     * <p>同时它也顺手修掉了另外两个同源问题:
     * <ul>
     *   <li>{@code DORMANT}(对话态)不会突然出手打人 —— 此前玩家靠近
     *       就会挨打,对话被硬生生打断;</li>
     *   <li>{@code WAVES}(波次态)本体隐身但仍在近战 ——
     *       玩家打小怪时会被"看不见的东西"偷袭。</li>
     * </ul>
     */
    private static final class GatedMeleeAttackGoal extends MeleeAttackGoal {

        private final WardenBoss boss;

        GatedMeleeAttackGoal(WardenBoss boss, double speed, boolean followTarget) {
            super(boss, speed, followTarget);
            this.boss = boss;
        }

        @Override
        public boolean canUse() {
            return boss.canFight() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return boss.canFight() && super.canContinueToUse();
        }
    }

    /** 只有战斗形态允许技能 / 追击目标。 */
    public boolean canFight() {
        return state.isCombatForm() && !isTransitioning() && isActiveFight();
    }

    // ================================================================
    // tick
    // ================================================================

    @Override
    public void aiStep() {
        super.aiStep();

        // ---- 一次性动作收招 ----
        // 必须在客户端也递减 —— GeckoLib 的动画判定跑在客户端,
        // 若只在服务端递减,客户端会永远认为 actionTicks > 0,
        // 攻击动画卡在最后一帧收不回来。
        if (actionTicks > 0) {
            actionTicks--;
        }

        if (level().isClientSide) {
            return;
        }

        // ---- 会话自绑:确保 /summon 出来的 Boss 也能完整工作 ----
        if (tickCount % 20 == 0) {
            ensureSessionBound();
            ensureScytheEquipped();
        }

        // ---- 锁血演出:定格 + 逐渐回满 + 换外观 ----
        if (transitionTicks > 0) {
            tickTransition();
            return;
        }

        // ---- 幽灵态:冲脸一击 ----
        if (state == LordState.ENRAGED_GHOST) {
            tickGhost();
            return;
        }

        // ---- 波次态:本体隐身,由 WaveManager 唱主角 ----
        if (state == LordState.WAVES) {
            if (tickCount % 10 == 0 && session != null) {
                session.waveManager().tick((ServerLevel) level(), this);
            }
            return;
        }

        // ---- 战斗形态:常规维护 ----
        if (tickCount % 10 == 0) {
            if (session != null) {
                // 参战者维护:追踪范围内的玩家自动加入
                trackNearbyPlayers();
                if (state.isCombatForm()) {
                    session.syncBar(getHealth() / getMaxHealth());
                }
            }
        }

        // ---- 常驻粒子光环 ----
        if (tickCount % 10 == 0) {
            spawnAuraParticles();
        }
    }

    /**
     * 确保本实体已绑定战斗会话 —— 供 {@code /summon} 出来的 Boss 兜底。
     *
     * <h2>为什么需要它(dv11 补)</h2>
     * 正常流程里 {@code WardenFightSession} 由
     * {@code BossPortalHandler#spawnLordIfAbsent} 在生成时绑定。
     * 但玩家用命令召唤(或其它模组刷出)时<b>不会走那条路径</b>,
     * 于是 session 为 {@code null},后果是一连串静默失效:
     * <ul>
     *   <li>Boss 血条永远不出现({@code session.syncBar} 被跳过);</li>
     *   <li>五波清场的 {@code WaveManager} 不启动,战斗卡在 WAVES 态;</li>
     *   <li>参战者追踪、播报、结算全部不生效。</li>
     * </ul>
     *
     * <p>策略:每 20 tick 检查一次,若当前维度还没有会话,
     * 就以本实体的身份新建一个并绑定。已有会话(且是别人的)则不抢 ——
     * 一个维度只应有一个遗落之主战斗。
     */
    private void ensureSessionBound() {
        if (session != null) {
            return;
        }
        if (!(level() instanceof ServerLevel sl)) {
            return;
        }
        // 维度里已有会话(属于另一个遗落之主)→ 不抢,但要避免自己永远裸奔
        WardenFightSession existing = WardenFightManager.session(sl);
        if (existing != null && !existing.isFinished()) {
            bindSession(existing);
            return;
        }
        WardenFightSession s = WardenFightManager.start(sl, getUUID(), getName());
        bindSession(s);
        Medals.LOGGER.info("[Lost Realm] 遗落之主(命令召唤)已自动绑定战斗会话。");
    }

    /** 撒常驻粒子 —— 无渲染器下的"形态气质"表达。 */
    private void spawnAuraParticles() {
        if (!(level() instanceof ServerLevel sl)) {
            return;
        }
        float height = getBbHeight();
        sl.sendParticles(state.particle(),
                getX(), getY() + height * 0.5D, getZ(),
                state.isCombatForm() ? 6 : 3,
                getBbWidth() * 0.6D, height * 0.35D, getBbWidth() * 0.6D,
                0.01D);
    }

    /**
     * 锁血演出 —— 血量锁为 1、无敌、逐渐回满,同时体型长大,然后进入下一形态。
     *
     * <h2>为什么做得这么长(默认 30 秒)</h2>
     * 这段时间是给玩家的<b>准备时间</b>:喝药、拉开距离、复读技能 CD。
     * 因此演出本身必须"好看且无害"—— 全程无敌,不主动攻击,
     * 只是站在原地一边回血一边变大,让玩家清楚知道"下一阶段要来了"。
     *
     * <h2>演出要素(每帧叠加)</h2>
     * <ul>
     *   <li><b>体型插值</b> —— 按进度从 {@code transitionFromScale} 线性长到
     *       {@code transitionToScale},并调 {@code refreshDimensions()} 同步碰撞箱;</li>
     *   <li><b>灵魂柱</b> —— 每 2 tick 从脚下向上升起的 SOUL 粒子;</li>
     *   <li><b>回血脉冲</b> —— 每 4 tick 一圈 SOUL_FIRE_FLAME 环;</li>
     *   <li><b>冲击波</b> —— 每 20 tick 一圈横扫的 EXPLOSION 粒子 +
     *       音调随进度升高的爆炸声(听觉上"越来越近");</li>
     *   <li><b>心跳</b> —— 每 15 tick 一声低音 RESPAWN_ANCHOR_DEPLETE,
     *       音调从 0.6 升到 1.4,营造压迫感递增。</li>
     * </ul>
     */
    private void tickTransition() {
        transitionTicks--;
        float max = getMaxHealth();

        // 按剩余比例线性推进(0 → 1)
        float elapsed = transitionTotalTicks - transitionTicks;
        float ratio = Math.max(0.0F, Math.min(1.0F, elapsed / (float) transitionTotalTicks));

        // ---- ① 回血 ----
        setHealth(Math.max(1.0F, max * ratio));
        if (session != null) {
            session.syncBar(getHealth() / max);
        }

        // ---- ② 体型插值:把进度写进同步数据,客户端据此渲染 ----
        // 客户端 getScale() 会用同一套公式算出同样的体型,
        // 因此服务端碰撞箱与客户端模型始终一致。
        this.entityData.set(DATA_TRANSITION_PROGRESS, ratio);
        refreshDimensions();

        if (!(level() instanceof ServerLevel sl)) {
            return;
        }

        // ---- 定格:演出期间钉在原地,不被击退/推挤 ----
        // 演出长达 30 秒,若不锁死位置,玩家用击退类手段就能把它推出竞技场,
        // 演出结束后它会从奇怪的地方"复活"。
        setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
        getNavigation().stop();

        double cx = getX();
        double cy = getY() + getBbHeight() * 0.5D;
        double cz = getZ();
        float w = getBbWidth();
        float h = getBbHeight();

        // ---- ③ 灵魂柱:每 2 tick 从脚底升起 ----
        if (tickCount % 2 == 0) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL,
                    cx, getY() + 0.2D, cz,
                    6, w * 0.35D, 0.1D, w * 0.35D, 0.08D);
        }

        // ---- ④ 回血脉冲:每 4 tick 一圈魂火 ----
        if (tickCount % 4 == 0) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                    cx, cy, cz,
                    12, w * 0.5D, h * 0.4D, w * 0.5D, 0.05D);
        }

        // ---- ⑤ 冲击波:每 20 tick 一圈横扫 + 音调升高的爆炸声 ----
        if (tickCount % 20 == 0) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION,
                    cx, getY() + 0.15D, cz,
                    16, w * 0.8D, 0.05D, w * 0.8D, 0.0D);
            // 音调随进度从 0.7 升到 1.3 —— 听感上"越来越近"
            sl.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE,
                    net.minecraft.sounds.SoundSource.HOSTILE,
                    1.2F, 0.7F + ratio * 0.6F);
        }

        // ---- ⑥ 心跳:每 15 tick 一声,音调递增 ----
        if (tickCount % 15 == 0) {
            sl.playSound(null, blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.get(),
                    net.minecraft.sounds.SoundSource.HOSTILE,
                    1.6F, 0.6F + ratio * 0.8F);
        }

        if (transitionTicks <= 0) {
            finishTransition();
        }
    }

    /**
     * 演出结束:换形态,恢复可战斗状态。
     *
     * <p>切换瞬间来一发「变形冲击」—— 白光闪 + 巨型爆炸 + 龙吼,
     * 让玩家明确感知"新形态登场了"。
     */
    private void finishTransition() {
        LordState next = state.nextFormAfterTransition();
        if (next == null) {
            // 理论上不会发生;防御性回到第一形态
            next = LordState.FORM_1;
        }

        setHealth(getMaxHealth());
        // 演出结束:进度置 1,体型交回状态自带的固定值
        this.entityData.set(DATA_TRANSITION_PROGRESS, 1.0F);
        setLordState(next);

        if (session != null) {
            session.broadcast(net.minecraft.network.chat.Component.translatable(
                    "message.lost_realm.lord.form.enter",
                    net.minecraft.network.chat.Component.translatable(next.langKey())));
        }

        if (level() instanceof ServerLevel sl) {
            double cx = getX();
            double cy = getY() + getBbHeight() * 0.5D;
            double cz = getZ();

            // 变形闪光:END_ROD 向上爆开
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    cx, cy, cz, 80, 1.2D, 1.8D, 1.2D, 0.35D);
            // 巨型爆炸
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                    cx, cy, cz, 8, 2.0D, 2.0D, 2.0D, 0.0D);

            sl.playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_GROWL,
                    net.minecraft.sounds.SoundSource.HOSTILE, 4.0F, 0.6F);
            sl.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE,
                    net.minecraft.sounds.SoundSource.HOSTILE, 4.0F, 0.4F);
        }
    }

    /**
     * 幽灵态 tick —— 「极速木剑冲脸 + 强制一击 + 消失」。
     *
     * <p>实现要点:
     * <ul>
     *   <li><b>不改用 {@code MeleeAttackGoal}</b> —— 走原版近战判定会因
     *       击退、无敌帧、命中盒偏差出现"打不中"的死局。这里改为
     *       距离 ≤ {@value #GHOST_STRIKE_RANGE} 时<b>直接结算伤害</b>,保证必中。</li>
     *   <li>一击扣玩家<b>最大生命值的固定百分比</b>(配置 {@code boss_duel_hit_percent},
     *       默认 50%)—— 不是"造成伤害",而是"扣血",所以无视护甲与减伤。</li>
     *   <li>打完后播放字幕、消失、转入 {@link LordState#WAVES}。</li>
     * </ul>
     */
    private void tickGhost() {
        ghostTicks++;

        Player target = currentTargetPlayer();
        // 找最近的玩家兜底(走位拉扯可能让 getTarget() 为 null)
        if (target == null) {
            target = findNearestPlayer();
            if (target != null) {
                setTarget(target);
            }
        }
        if (target == null) {
            // 场上没人 → 超时直接转波次,避免卡死
            if (ghostTicks > GHOST_TIMEOUT_TICKS) {
                enterWaves();
            }
            return;
        }

        // 持木剑 —— 只在进入幽灵态的第一 tick 装一次。
        // ⚠️ 不要每 tick 调 setItemInHand:它会反复重置主手物品与挥砍动画,
        // 并可能触发原版的攻击冷却重置,是"看起来打了很多下"的诱因之一。
        if (!ghostSwordEquipped) {
            equipWoodenSword();
            ghostSwordEquipped = true;
        }

        // 冲脸:每 tick 直接朝玩家推进(比寻路更直接、更"恐怖")
        facePoint(target.position());
        double dist = distanceTo(target);
        if (dist > GHOST_STRIKE_RANGE) {
            Vec3 dir = target.position().subtract(position()).normalize();
            setDeltaMovement(dir.x * GHOST_SPEED, 0.02D, dir.z * GHOST_SPEED);
            this.hasImpulse = true;
            // 举剑冲刺的挥砍动画 —— 由 ItemInHandLayer 渲染,
            // 每 8 tick 挥一次,让"手持木剑冲脸"在画面上看得见。
            if (tickCount % 8 == 0) {
                swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            }
        } else {
            // 出手前先挥一下,动画与伤害同一刻,观感才对得上
            swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            strikePlayer(target);
            return;
        }

        // 残影粒子
        if (tickCount % 2 == 0 && level() instanceof ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                    getX(), getY() + getBbHeight() * 0.5D, getZ(),
                    20, 0.4D, 0.8D, 0.4D, 0.15D);
        }

        // 超时兜底:玩家跑出城或卡地形
        if (ghostTicks > GHOST_TIMEOUT_TICKS) {
            strikePlayer(target);
        }
    }

    /**
     * 幽灵态的必中一击 —— 扣玩家最大生命值的一个固定百分比。
     *
     * <h2>⚠️ 这一击【绝不致死】(dv9 修)</h2>
     * 旧实现直接 {@code target.setHealth(maxHp * (1 - percent))} ——
     * 这是"把血条强行压到某个值",不看当前血量。后果是:
     * 玩家若已经掉到该线以下(或被第二下打中),血量会被压到
     * {@code maxHp*0.5} 之下的任何值都<b>无法阻止再次下压</b>,
     * 两下就是 100% 必死 —— 玩家实测"和 Boss 对话后被两巴掌打死"。
     *
     * <p>改为<b>常规扣血</b>({@code setHealth(当前 - 伤害)}),并加两条硬保底:
     * <ol>
     *   <li>结算后至少留 {@value #DUEL_MIN_HEALTH} 点血 —— 这一击只用于"震慑",
     *       目的是让玩家体会差距,不是用来杀人的;</li>
     *   <li>伤害同时受配置上限约束({@code boss_duel_hit_percent})。</li>
     * </ol>
     *
     * <p>文案也同步改口:原来直接跳到"你的实力太弱了……"并结束,
     * 现在会明确告知<b>这是试炼、不是击杀</b>,并提示玩家已被免费补满,
     * 避免玩家误以为"打不过就死"而放弃。
     */
    private void strikePlayer(Player target) {
        if (ghostStruck) {
            return;
        }
        ghostStruck = true;

        float percent = BossRegistry.duelHitPercent();
        float damage = target.getMaxHealth() * percent;

        // 常规扣血(不再无视护甲硬压血条),并保证不致死
        target.hurt(damageSources().generic(), damage);
        float floor = Math.min(DUEL_MIN_HEALTH, target.getMaxHealth() * 0.5F);
        if (target.isAlive() && target.getHealth() < floor) {
            target.setHealth(floor);
        }

        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                    target.getX(), target.getY() + 1.0D, target.getZ(),
                    60, 0.5D, 0.8D, 0.5D, 0.4D);
            sl.playSound(null, target.blockPosition(), SoundEvents.WARDEN_ATTACK_IMPACT,
                    net.minecraft.sounds.SoundSource.HOSTILE, 3.0F, 0.6F);
        }

        // 字幕:说明这是试炼、并告知已补满 —— 不是"你太弱了被秒了"
        if (session != null) {
            session.broadcast(net.minecraft.network.chat.Component.translatable(
                    "message.lost_realm.lord.duel.trial_done"));
        }

        // 消失 → 波次
        enterWaves();
    }

    /**
     * 幽灵态结束:退场并开始 5 波怪物。
     *
     * <p>开场前把场上玩家<b>补满血</b> —— 试炼那一击本就是演出,
     * 若让玩家带着 6 点残血直接进五波清场,那就是"必死"而不是"挑战"。
     * 补满同时把这一击的实际代价归零,确保试炼纯粹是叙事环节。
     */
    private void enterWaves() {
        setLordState(LordState.WAVES);

        // 补满参与试炼的玩家(仅补血,不给增益、不清负面)
        List<Player> nearby = level().getEntitiesOfClass(Player.class,
                new AABB(blockPosition()).inflate(96.0D));
        for (Player p : nearby) {
            if (p.isAlive() && !p.isSpectator()) {
                p.setHealth(p.getMaxHealth());
            }
        }

        if (session != null) {
            session.waveManager().begin((ServerLevel) level(), this);
        }
    }

    /** 给幽灵态装上木剑(纯视觉 —— 伤害走强制结算)。 */
    private void equipWoodenSword() {
        setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WOODEN_SWORD));
    }

    @Nullable
    private Player findNearestPlayer() {
        List<Player> list = level().getEntitiesOfClass(Player.class,
                new AABB(blockPosition()).inflate(96.0D));
        Player best = null;
        double bestD = Double.MAX_VALUE;
        for (Player p : list) {
            if (!p.isAlive() || p.isSpectator()) {
                continue;
            }
            double d = distanceToSqr(p);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /** 把附近玩家纳入参战(便于血条显示与消息广播)。 */
    private void trackNearbyPlayers() {
        if (session == null) {
            return;
        }
        List<Player> nearby = level().getEntitiesOfClass(Player.class,
                new AABB(blockPosition()).inflate(96.0D));
        for (Player p : nearby) {
            if (p instanceof net.minecraft.server.level.ServerPlayer sp) {
                session.addParticipant(sp);
            }
        }
    }

    // ================================================================
    // 形态推进(锁血)
    // ================================================================

    /**
     * 进入锁血演出 —— 血量锁为 1、无敌、定格、开始长大。
     *
     * <p>由 {@link #hurt} 在「本次伤害会把血量打到 ≤1」时触发。
     * 演出时长由配置 {@code boss_transition_heal_seconds} 决定(默认 30 秒,
     * 给玩家充足的准备时间)。
     *
     * <h2>这一段的演出要素(dv10 加强)</h2>
     * <ol>
     *   <li><b>体型动态插值</b> —— 从上一形态体型一路长到下一形态体型,
     *       每 tick 调 {@code refreshDimensions()} 让碰撞箱跟上;</li>
     *   <li><b>开场冲击</b> —— 三道爆炸 + 龙息音效 + 大量魂火粒子;</li>
     *   <li><b>期间持续特效</b> —— 见 {@link #tickTransition}:螺旋上升的灵魂柱、
     *       周期性横扫的冲击波、逐渐加快的心跳声(音调随进度升高);</li>
     *   <li><b>形态切换瞬间</b> —— 见 {@link #finishTransition}:闪光 + 巨响 + 震波。</li>
     * </ol>
     */
    private void beginTransition() {
        LordState t = state.transitionFor();
        if (t == null) {
            return;     // 第三形态没有后续锁血
        }

        // ★ 记录体型插值的起止点 —— 必须在 setLordState(t) 之前取,
        //   因为 setLordState 会把 state 换成演出态,scale 就变了。
        //   写进同步数据,客户端才能算出与服务端一致的体型。
        LordState next = t.nextFormAfterTransition();
        float fromScale = state.scale();
        float toScale = next != null ? next.scale() : state.scale();
        this.entityData.set(DATA_TRANSITION_FROM_SCALE, fromScale);
        this.entityData.set(DATA_TRANSITION_TO_SCALE, toScale);
        this.entityData.set(DATA_TRANSITION_PROGRESS, 0.0F);

        setHealth(1.0F);
        transitionTotalTicks = Math.max(20, BossRegistry.transitionHealSeconds() * 20);
        transitionTicks = transitionTotalTicks;
        setLordState(t);

        if (session != null) {
            session.broadcast(net.minecraft.network.chat.Component.translatable(
                    "message.lost_realm.lord.lock"));
        }

        // ---- 开场冲击:爆炸 + 巨响 + 魂火爆发 ----
        if (level() instanceof ServerLevel sl) {
            double cx = getX();
            double cy = getY() + getBbHeight() * 0.5D;
            double cz = getZ();

            // 三道爆炸发射器,错开位置形成"它炸开了"的观感
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                    cx, cy, cz, 6, 1.5D, 1.5D, 1.5D, 0.0D);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                    cx + 2.0D, cy, cz, 2, 0.5D, 0.5D, 0.5D, 0.0D);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                    cx - 2.0D, cy, cz, 2, 0.5D, 0.5D, 0.5D, 0.0D);

            // 魂火柱:从脚底冲到头顶
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                    cx, getY(), cz, 120, 0.8D, 0.2D, 0.8D, 0.25D);

            // 音效三层叠加:巨响 + 龙息 + 凋灵咆哮
            sl.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE,
                    net.minecraft.sounds.SoundSource.HOSTILE, 4.0F, 0.5F);
            sl.playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_GROWL,
                    net.minecraft.sounds.SoundSource.HOSTILE, 3.0F, 0.7F);
            sl.playSound(null, blockPosition(), SoundEvents.WARDEN_ROAR,
                    net.minecraft.sounds.SoundSource.HOSTILE, 3.0F, 0.5F);
        }
    }

    // ================================================================
    // 受伤
    // ================================================================

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) {
            return false;
        }

        // ① 无敌状态:零阶段 / 幽灵态 / 波次态 / 锁血演出 / 已死
        if (state.isInvulnerableState()) {
            // 零阶段被攻击时，给一点反馈，避免玩家以为打不动是 bug
            if (state == LordState.DORMANT
                    && source.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.lost_realm.lord.invulnerable"), true);
            }
            return false;
        }

        // ② 形态 1 / 2:锁血 —— 血量将 ≤1 时锁定并转阶段
        LordState transition = state.transitionFor();
        if (transition != null) {
            float remaining = getHealth() - amount;
            if (remaining <= 1.0F) {
                beginTransition();
                // 记录攻击者,便于结算
                if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp
                        && session != null) {
                    session.addParticipant(sp);
                }
                return true;
            }
        }

        // ③ 常规受伤
        boolean hurt = super.hurt(source, amount);
        if (hurt) {
            // 受击动作:时长 10 tick(0.5 秒 = 动画文件里的 animation_length)
            playAction(WardenAnimations.HURT, 10);
            if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp
                    && session != null) {
                session.addParticipant(sp);
            }
        }
        return hurt;
    }

    /**
     * 死亡结算:结束战斗会话、清掉分身。
     *
     * <p>只有 {@link LordState#FORM_3} 才能被真正击杀 ——
     * 前两形态的"死亡"在 {@link #hurt} 里已被拦成锁血演出。
     */
    @Override
    public void die(DamageSource source) {
        if (defeated) {
            return;
        }
        defeated = true;
        setLordState(LordState.DEFEATED);
        super.die(source);
        if (level() instanceof ServerLevel sl) {
            WardenFightSession s = WardenFightManager.session(sl);
            if (s != null) {
                s.broadcast(net.minecraft.network.chat.Component.translatable(
                        "message.lost_realm.lord.defeated"));
                WardenFightManager.end(sl);
            }
            removeClones(sl);
            com.lost_realm.boss.LordGlowTeams.clear(this);
        }
    }

    /** 清理所有分身(本体死亡时)。 */
    private void removeClones(ServerLevel sl) {
        List<WardenBoss> clones = sl.getEntitiesOfClass(WardenBoss.class,
                new AABB(blockPosition()).inflate(128.0D),
                e -> e != this && e.isClone());
        for (WardenBoss c : clones) {
            c.discard();
        }
    }

    // ================================================================
    // 会话绑定
    // ================================================================

    public void bindSession(WardenFightSession s) {
        this.session = s;
        s.setBarVisible(state.barVisible());
        s.applyBarColor(state.barColor());
    }

    @Nullable
    public WardenFightSession session() {
        return session;
    }

    // ================================================================
    // 杂项覆写
    // ================================================================

    /** Boss 不自然消失。 */
    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    @Override
    public boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    protected boolean shouldDropLoot() {
        return super.shouldDropLoot();
    }

    /** 击退抗性已 100%,但额外禁止被推动导致的位移抖动。 */
    @Override
    public boolean isPushable() {
        return false;
    }

    /** 被攻击时不产生击退位移。 */
    @Override
    public void knockback(double strength, double x, double z) {
        // Boss 免疫击退
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WARDEN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource src) {
        return SoundEvents.WARDEN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WARDEN_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 2.0F;
    }

    /** 自定义名称,中文走语言键;按状态着色,让玩家不看血条也知道打到第几形态。 */
    @Override
    public net.minecraft.network.chat.Component getName() {
        String key = isClone()
                ? "entity.lost_realm.warden.clone"
                : "entity.lost_realm.lord";
        net.minecraft.network.chat.MutableComponent name =
                net.minecraft.network.chat.Component.translatable(key);
        if (!isClone() && state.isCombatForm()) {
            name = name.append(net.minecraft.network.chat.Component.literal(" · ")
                    .append(net.minecraft.network.chat.Component.translatable(state.langKey())));
        }
        return name.withStyle(state.glowColor());
    }

    // ================================================================
    // 交互 —— 右键对话
    // ================================================================

    /**
     * 右键遗落之主 —— 打开对话界面。
     *
     * <p><b>只能覆写 {@code mobInteract}</b>:父类 {@code Mob#interact} 是
     * {@code public final},无法覆写;{@code mobInteract} 是 {@code protected},
     * 正是留给子类的交互钩子。
     *
     * <p>只有 {@link LordState#DORMANT 零阶段}可交互;
     * 一旦进入战斗(幽灵态及之后),右键不再有任何反应。
     */
    @Override
    protected net.minecraft.world.InteractionResult mobInteract(
            Player player, net.minecraft.world.InteractionHand hand) {
        // 客户端:返回 SUCCESS 以触发挥手动画,真正的逻辑在服务端
        if (level().isClientSide) {
            return getLordState() == LordState.DORMANT
                    ? net.minecraft.world.InteractionResult.SUCCESS
                    : net.minecraft.world.InteractionResult.PASS;
        }
        if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (state != LordState.DORMANT) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        // 距离校验(右键的交互距离原版已限 3 格,这里再放宽到对话上限)
        if (sp.distanceTo(this) > com.lost_realm.boss.LordDialogueHandler.MAX_TALK_DISTANCE) {
            return net.minecraft.world.InteractionResult.PASS;
        }

        com.lost_realm.boss.LordDialogueHandler.openFor(sp, this);
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    // ================================================================
    // 存档
    // ================================================================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("LordState", state.ordinal());
        tag.putInt("LordTransitionTicks", transitionTicks);
        tag.putInt("LordTransitionTotal", transitionTotalTicks);
        tag.putBoolean("WardenClone", clone);
        tag.putBoolean("WardenDefeated", defeated);
        tag.putBoolean("LordGhostStruck", ghostStruck);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        LordState loaded = LordState.byIndex(tag.getInt("LordState"));
        // ★ 关键:WAVES 是纯内存态,服务器重启后波次进度已丢失。
        // 若还原到 WAVES,玩家会被卡在"打不完的波次"里 —— 直接跳 FORM_1 开场。
        if (loaded == LordState.WAVES) {
            loaded = LordState.FORM_1;
        }
        // DEFEATED 是终态,不该被"复活"
        if (loaded == LordState.DEFEATED) {
            loaded = LordState.FORM_1;
        }
        // 锁血演出同理:还原后半途的演出没有意义,直接落到下一形态
        if (loaded.isTransition()) {
            LordState next = loaded.nextFormAfterTransition();
            loaded = next == null ? LordState.FORM_1 : next;
        }
        this.state = loaded;
        this.entityData.set(DATA_STATE, loaded.ordinal());
        this.transitionTicks = 0;
        this.transitionTotalTicks = 0;
        this.clone = tag.getBoolean("WardenClone");
        this.defeated = tag.getBoolean("WardenDefeated");
        this.ghostStruck = tag.getBoolean("LordGhostStruck");
        refreshDimensions();
        // ★ dv20：描边已全局关闭 —— 旧存档里的实体带着 Glowing 标记 +
        //   计分板队伍成员身份被原样还原，会继续显示描边。
        //   在这里主动摘一次（applyTo 只在换阶段时被调，救不了载入的场景）。
        com.lost_realm.boss.LordGlowTeams.clear(this);
    }

    /** 该实体是否为玩家所在战斗中的合法目标。 */
    public boolean isActiveFight() {
        return session != null && !session.isFinished();
    }

    /** 用于调试与技能代码:取目标玩家。 */
    @Nullable
    public Player currentTargetPlayer() {
        LivingEntity t = getTarget();
        return t instanceof Player p ? p : null;
    }

    /** 朝向目标的水平单位向量(技能用)。 */
    public Vec3 horizontalDirTo(Entity target) {
        Vec3 delta = target.position().subtract(position());
        Vec3 flat = new Vec3(delta.x, 0.0D, delta.z);
        return flat.lengthSqr() < 1.0E-6D ? Vec3.ZERO : flat.normalize();
    }

    /** 城市中心(技能用来判断是否拉出场地)。 */
    public BlockPos arenaCenter() {
        return com.lost_realm.boss.ArenaBuilder.arenaCenter();
    }

    /** 目标是否在遗落之都内(技能只在场地内生效)。 */
    public boolean targetInArena(Entity target) {
        BlockPos c = arenaCenter();
        double dx = target.getX() - (c.getX() + 0.5D);
        double dz = target.getZ() - (c.getZ() + 0.5D);
        return dx * dx + dz * dz <= (double) (BossRegistry.ARENA_RADIUS * BossRegistry.ARENA_RADIUS);
    }

    /** 让实体朝向某点(技能前摇用)。 */
    public void facePoint(Vec3 point) {
        double dx = point.x - getX();
        double dz = point.z - getZ();
        float yaw = (float) (Math.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        setYRot(yaw);
        setYHeadRot(yaw);
    }

    /**
     * 供技能 Goal / 渲染逻辑检查"是否正在转阶段"。
     *
     * <p>⚠️ 必须走 {@link #getLordState()}(客户端读同步数据),
     * 不能直接读 {@code state} 字段 —— 那个字段只赋值的服务端。
     * 客户端 {@code getScale()} 也会调本方法,走错就会导致
     * 客户端永远认为"不在演出中",体型插值失效。
     */
    public boolean isTransitioning() {
        return getLordState().isTransition();
    }

    /** 向所有参战者广播一条消息(战斗提示用)。 */
    public void displayMessageIfSession(net.minecraft.network.chat.Component msg) {
        if (session != null) {
            session.broadcast(msg);
        }
    }

    // ================================================================
    // 兼容层 —— 既有技能 Goal 仍读 WardenPhase
    // ----------------------------------------------------------------
    // 形态与旧"阶段"是 1:1 对应关系,这里做一层只读映射,
    // 使 WardenJudgementGoal / WardenArrowStormGoal 等无需改动即可工作。
    // 技能本身在 canUse() 里会检查 canFight(),因此非战斗形态不会误放技能。
    // ================================================================

    /** 由当前状态推导的旧"阶段"(仅供技能 Goal 读取技能强度)。 */
    public WardenPhase getPhase() {
        return switch (getLordState()) {
            case FORM_2 -> WardenPhase.PHASE_2;
            case FORM_3 -> WardenPhase.PHASE_3;
            default -> WardenPhase.PHASE_1;
        };
    }

    public boolean isClone() {
        return clone;
    }

    public void setClone(boolean v) {
        this.clone = v;
    }

    /** 供 {@link WaveManager} 使用:推进到第一形态。 */
    public void enterRealFight() {
        ghostStruck = false;
        ghostSwordEquipped = false;
        setHealth(getMaxHealth());
        setLordState(LordState.FORM_1);
        if (session != null) {
            session.broadcast(net.minecraft.network.chat.Component.translatable(
                    "message.lost_realm.lord.reappear"));
        }
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
                    getX(), getY() + getBbHeight() * 0.5D, getZ(),
                    200, 2.0D, 2.0D, 2.0D, 0.2D);
        }
    }

    /** 供 {@link com.lost_realm.boss.LordDialogueHandler} 使用:从零阶段进入决斗。 */
    public void beginDuel() {
        if (state != LordState.DORMANT && state != LordState.FORM_1) {
            return;
        }
        ghostStruck = false;
        ghostSwordEquipped = false;
        ghostTicks = 0;
        setLordState(LordState.ENRAGED_GHOST);
        Player p = findNearestPlayer();
        if (p != null) {
            setTarget(p);
        }
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
                    getX(), getY() + getBbHeight() * 0.5D, getZ(),
                    120, 1.0D, 1.5D, 1.0D, 0.3D);
        }
    }

    /** 零阶段是否处于可对话状态。 */
    public boolean isTalkable() {
        return state == LordState.DORMANT;
    }

    // ---- 常量 ----

    /** 幽灵态强制命中的判定距离(格)。 */
    private static final double GHOST_STRIKE_RANGE = 2.5D;

    /** 幽灵态每 tick 的推进速度(格/tick)。比任何走位都快,但有超时兜底。 */
    private static final double GHOST_SPEED = 0.85D;

    /** 幽灵态超时 tick(8 秒)—— 玩家跑出城或卡地形时的兜底。 */
    private static final int GHOST_TIMEOUT_TICKS = 160;

    /**
     * 幽灵试炼那一击的<b>血量下限</b>(点)。
     *
     * <p>这一击是「震慑演出」而不是击杀手段,因此无论配置把
     * {@code boss_duel_hit_percent} 调得多高,结算后玩家都至少剩这么多血。
     * 取 6 点约等于玩家满血 20 点时剩三分之一 —— 够疼、有压迫感,
     * 但不会因为"两次判定"或"残血进场"直接暴毙。
     */
    private static final float DUEL_MIN_HEALTH = 6.0F;
}
