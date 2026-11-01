package com.lost_realm.boss.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 守望者仆从 —— 任务链第四阶段的守门怪。
 *
 * <h2>定位</h2>
 * 三只仆从分守竞技场的三座祭坛区域(灵魂 / 神化 / 勋章),
 * 玩家必须击败它们才能获得"祭坛激活核心",
 * 从而进入任务链的最后一个阶段(召唤终末之影)。
 *
 * <h2>为什么用独立实体而不是缩小版的终末之影</h2>
 * <ul>
 *   <li>仆从需要<b>独立</b>的名字、掉落表与成就判定 —— 混用会让成就逻辑变复杂</li>
 *   <li>仆从的 AI 应当简单(纯近战追击),复刻本体会带来无谓的技能负担</li>
 *   <li>玩家在成就界面看到的是"击败守望者仆从"而非"击败终末之影"</li>
 * </ul>
 *
 * <h2>强度设定</h2>
 * 血量 120(本体的 15%),攻击力 12 —— 对精锐阶段的玩家构成威胁但不至于劝退。
 * 真正的考验是"三只同时在场"的压力。
 */
public class WardenSentinel extends Monster implements GeoAnimatable {

    public WardenSentinel(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 60;
        this.setPersistenceRequired();
    }

    // ================================================================
    // GeckoLib 动画(几何复用 warden_form1,见 client/boss/WardenSentinelModel)
    // ================================================================

    private final AnimatableInstanceCache geoCache =
            GeckoLibUtil.createInstanceCache(this);

    private static final RawAnimation IDLE =
            RawAnimation.begin().thenLoop("animation.warden.idle");
    private static final RawAnimation WALK =
            RawAnimation.begin().thenLoop("animation.warden.walk");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<WardenSentinel>(
                this, "main", 4, this::onState));
    }

    private PlayState onState(AnimationState<WardenSentinel> state) {
        double dx = getX() - xo;
        double dz = getZ() - zo;
        boolean moving = dx * dx + dz * dz > 1.0E-5D;
        return state.setAndContinue(moving ? WALK : IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    @Override
    public double getTick(Object entity) {
        return this.tickCount;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 120.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0D)
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.FloatGoal(this));
        this.goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.LookAtPlayerGoal(
                this, net.minecraft.world.entity.player.Player.class, 24.0F));
        this.goalSelector.addGoal(4, new net.minecraft.world.entity.ai.goal.RandomLookAroundGoal(this));

        this.targetSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(this));
        this.targetSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(
                this, net.minecraft.world.entity.player.Player.class, false, false));
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    @Override
    public boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public net.minecraft.network.chat.Component getName() {
        return net.minecraft.network.chat.Component.translatable(
                "entity.lost_realm.warden_sentinel");
    }
}
