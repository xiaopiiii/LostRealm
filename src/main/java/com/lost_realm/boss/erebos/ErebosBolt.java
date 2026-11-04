package com.lost_realm.boss.erebos;

import javax.annotation.Nullable;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

import com.lost_realm.boss.BossEntities;

/**
 * <b>星弹</b> —— 乌蠹诺斯的通用弹幕。
 *
 * <h2>为什么弹幕数量要砍</h2>
 * 这是整份设计里<b>最需要重新取舍</b>的一块。泰拉瑞亚里弹幕是轻量对象
 * （一个精灵 + 几行逻辑），一招上百发毫无压力；<b>MC 里每一发都是一个实体</b>，
 * 要 tick、要同步、要进 AABB 查询。原样照搬的后果不是"难"，是服务器当场去世。
 *
 * <p>所以本模组的做法是把原设计的弹幕数按 <b>≈ 2/3 压缩</b>
 * （碎星播撒 17→11、星弧巡猎 22→14），并且用 {@link ErebosTuning#MAX_LIVE_BOLTS}
 * 封一个全场上限。真正的压迫感靠<b>预警</b>和<b>粒子</b>补 ——
 * 粒子免费，实体收费。
 *
 * <h2>两种形态</h2>
 * <ul>
 *   <li>{@link Kind#PLAIN 直线星弹} —— 沿固定方向飞，撞到东西就散。</li>
 *   <li>{@link Kind#SEED 种子} —— 先飞一段，然后<b>停在原地蓄力</b>一秒，
 *       再向四面八方爆散成一片弹幕。停住那一下是刻意的：
 *       玩家能清楚看到"那颗东西停在半空了，要炸"，于是有明确的躲避时机。</li>
 * </ul>
 *
 * <h2>伤害类型为什么不用 magic</h2>
 * MC 的 {@code magic} 伤害类型<b>无视护甲</b>，而原设计通篇都在说
 * "防御前伤害"—— 说明泰拉瑞亚那边是减防御的。所以这里用
 * {@code mobProjectile}（受护甲减免），与设计意图一致：
 * 玩家堆防御是有意义的。
 */
public class ErebosBolt extends Projectile {

    private static final EntityDataAccessor<Integer> DATA_KIND =
            SynchedEntityData.defineId(ErebosBolt.class, EntityDataSerializers.INT);

    /** 种子弹的爆散预警剩余 tick（0 = 不是预警中）。客户端靠它画蓄力光环。 */
    private static final EntityDataAccessor<Integer> DATA_WARN =
            SynchedEntityData.defineId(ErebosBolt.class, EntityDataSerializers.INT);

    /** 弹幕大小（供后续大招用不同尺寸的弹幕；现均为 1.0）。 */
    private static final EntityDataAccessor<Float> DATA_SCALE =
            SynchedEntityData.defineId(ErebosBolt.class, EntityDataSerializers.FLOAT);

    /** 星弹形态。 */
    public enum Kind {
        /** 直线星弹。 */
        PLAIN,
        /** 种子：飞一段 → 停住蓄力 → 爆散。 */
        SEED;

        public static Kind byIndex(int i) {
            Kind[] v = values();
            return v[Mth.clamp(i, 0, v.length - 1)];
        }
    }

    // ---- 服务端参数（客户端不需要，用默认值即可）----
    private float damage = ErebosTuning.BOLT_DAMAGE;
    private int remainingLife = ErebosTuning.BOLT_LIFE;
    private int burstCount = ErebosTuning.HUNT_BURST_COUNT;
    private double burstSpeed = ErebosTuning.HUNT_BURST_SPEED;
    private int burstLife = ErebosTuning.HUNT_BURST_LIFE;

    /** 种子已经飞了多少 tick。 */
    private int seedAge;

    public ErebosBolt(EntityType<? extends ErebosBolt> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    // ================================================================
    // 工厂
    // ================================================================

    /** 造一发直线星弹。 */
    public static ErebosBolt plain(ServerLevel level, @Nullable Entity owner,
                                   Vec3 pos, Vec3 dir, double speed) {
        ErebosBolt bolt = new ErebosBolt(BossEntities.EREBOS_BOLT.get(), level);
        bolt.setKind(Kind.PLAIN);
        bolt.launch(owner, pos, dir, speed);
        return bolt;
    }

    /** 造一颗种子（飞到地方停下、蓄力、爆散）。 */
    public static ErebosBolt seed(ServerLevel level, @Nullable Entity owner,
                                  Vec3 pos, Vec3 dir, double speed,
                                  int burstCount, double burstSpeed, int burstLife) {
        ErebosBolt bolt = new ErebosBolt(BossEntities.EREBOS_BOLT.get(), level);
        bolt.setKind(Kind.SEED);
        bolt.burstCount = burstCount;
        bolt.burstSpeed = burstSpeed;
        bolt.burstLife = burstLife;
        bolt.launch(owner, pos, dir, speed);
        return bolt;
    }

    /** 设定存活时长（链式）—— 不同招式需要不同的射程。 */
    public ErebosBolt withLife(int life) {
        this.remainingLife = Math.max(1, life);
        return this;
    }

    /** 设定伤害（链式）。 */
    public ErebosBolt withDamage(float damage) {
        this.damage = damage;
        return this;
    }

    private void launch(@Nullable Entity owner, Vec3 pos, Vec3 dir, double speed) {
        this.setOwner(owner);
        this.setPos(pos.x, pos.y, pos.z);
        Vec3 d = dir.lengthSqr() < 1.0E-8D ? new Vec3(0, 0, 1) : dir.normalize();
        this.shoot(d.x, d.y, d.z, (float) speed, 0.0F);
        // 发射音统一在 launch 里放，由 ErebosSfx 做每 tick 一次的节流 ——
        // 齐射 72 发时不能 72 个音效包一起推给客户端。
        if (this.level() instanceof ServerLevel sl) {
            ErebosSfx.shot(sl, pos);
        }
    }

    /**
     * 统计某点附近当前存活的星弹数量。
     *
     * <p>招式在齐射前用它做一次自限 —— 原设计要求"弹幕数量有上限"。
     * 只在<b>发射前</b>查（不是每 tick），所以开销可以忽略：
     * 碎星播撒每 55 tick 才查一次。
     */
    public static int countNear(ServerLevel level, Vec3 center, double radius) {
        AABB area = new AABB(center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
        return level.getEntitiesOfClass(ErebosBolt.class, area).size();
    }

    // ================================================================
    // 同步
    // ================================================================

    @Override
    protected void defineSynchedData() {
        // ⚠️ 这里不能调 super：Projectile 没有实现 defineSynchedData，
        // 指向的是 Entity 的抽象方法。Entity 的基础同步槽（共享标志等）
        // 是在它自己的构造函数里注册的，所以这里只管加自己的即可。
        this.entityData.define(DATA_KIND, Kind.PLAIN.ordinal());
        this.entityData.define(DATA_WARN, 0);
        this.entityData.define(DATA_SCALE, 1.0F);
    }

    public Kind kind() {
        return Kind.byIndex(this.entityData.get(DATA_KIND));
    }

    private void setKind(Kind kind) {
        this.entityData.set(DATA_KIND, kind.ordinal());
    }

    /** 爆散预警剩余 tick（客户端用）。 */
    public int burstWarning() {
        return this.entityData.get(DATA_WARN);
    }

    public float boltScale() {
        return this.entityData.get(DATA_SCALE);
    }

    // ================================================================
    // 每 tick
    // ================================================================

    @Override
    public void tick() {
        super.tick();

        if (this.kind() == Kind.SEED) {
            this.tickSeed();
        } else {
            this.tickPlain();
        }
    }

    /**
     * 直线星弹：走、撞、散。
     *
     * <p>⚠️ 移动与命中<b>只在服务端做</b>。客户端如果照着
     * {@code getDeltaMovement()} 自己推位置，就会与位置同步包打架 ——
     * 表现为弹幕一抖一抖地走。客户端只负责按服务端同步来的位置画粒子。
     */
    private void tickPlain() {
        if (this.level().isClientSide) {
            this.spawnTrail();
            return;
        }

        if (--this.remainingLife <= 0) {
            this.discard();
            return;
        }

        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) {
            this.onHit(hit);
        }
        if (this.isRemoved()) {
            return;
        }

        Vec3 velocity = this.getDeltaMovement();
        this.setPos(this.getX() + velocity.x, this.getY() + velocity.y, this.getZ() + velocity.z);

        if (this.isOutsideWorld()) {
            this.discard();
        }
    }

    /**
     * 种子：飞 → 停 → 蓄力 → 爆散。
     *
     * <p>三个阶段都在服务端推进，客户端只读 {@code DATA_WARN} 决定画不画光环。
     */
    private void tickSeed() {
        if (this.level().isClientSide) {
            this.spawnSeedAura();
            return;
        }

        this.seedAge++;

        if (this.seedAge < ErebosTuning.HUNT_SEED_FLIGHT) {
            // 飞行段：等速直线，不做命中检测（它是"投掷物"，撞墙也只是停下）
            Vec3 velocity = this.getDeltaMovement();
            this.setPos(this.getX() + velocity.x, this.getY() + velocity.y, this.getZ() + velocity.z);
            if (this.isOutsideWorld()) {
                this.discard();
            }
            return;
        }

        // 到点了：钉死位置
        this.setDeltaMovement(Vec3.ZERO);

        int warned = this.seedAge - ErebosTuning.HUNT_SEED_FLIGHT;
        this.entityData.set(DATA_WARN, Math.max(0, ErebosTuning.HUNT_BURST_WARN - warned));

        if (warned >= ErebosTuning.HUNT_BURST_WARN) {
            this.burst();
        }
    }

    /** 向四周炸开一圈弹幕，然后自己消失。 */
    private void burst() {
        if (!(this.level() instanceof ServerLevel server)) {
            this.discard();
            return;
        }
        int count = Mth.clamp(this.burstCount, 1, ErebosTuning.MAX_BOLTS_PER_VOLLEY);
        var random = this.random;
        Vec3 origin = this.position();

        // 一圈均分，再叠一点随机抖动 ——
        // 完全均分的话每一发之间的缝隙都一模一样，玩家会背出"安全角"；
        // 抖动让每次爆散的缺口位置都不同，逼玩家临场判断。
        double base = random.nextDouble() * Math.PI * 2.0D;
        for (int i = 0; i < count; i++) {
            double angle = base + (Math.PI * 2.0D / count) * i
                    + (random.nextDouble() - 0.5D) * 0.28D;
            // 上下也撒开一点，MC 是 3D —— 只在水平面炸开的话玩家往上一跳就全躲了
            double pitch = (random.nextDouble() - 0.5D) * 0.7D;
            Vec3 dir = new Vec3(Math.cos(angle), pitch, Math.sin(angle));
            ErebosBolt child = ErebosBolt.plain(server, this.getOwner(), origin, dir,
                    this.burstSpeed);
            child.remainingLife = this.burstLife;
            child.damage = this.damage * 0.85F;
            child.entityData.set(DATA_SCALE, 0.8F);
            server.addFreshEntity(child);
        }

        ErebosFx.playBurst(server, origin);
        this.discard();
    }

    // ================================================================
    // 命中
    // ================================================================

    @Override
    protected boolean canHitEntity(Entity target) {
        // 不打自己和自己的身体部件
        if (target instanceof ErebosBoss || target instanceof ErebosPart) {
            return false;
        }
        if (target instanceof Player player
                && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        return super.canHitEntity(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        Entity owner = this.getOwner();
        // mobProjectile：受护甲减免 —— 与"防御前伤害"的设计语义一致
        DamageSource source = this.damageSources().mobProjectile(this,
                owner instanceof LivingEntity living ? living : null);
        target.hurt(source, this.damage);
        if (!this.level().isClientSide) {
            ErebosFx.playPop((ServerLevel) this.level(), this.position());
        }
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (!this.level().isClientSide) {
            ErebosFx.playPop((ServerLevel) this.level(), this.position());
        }
        this.discard();
    }

    /** 掉出世界或跑得太远 —— 直接清理，别留着占实体名额。 */
    private boolean isOutsideWorld() {
        return this.getY() < this.level().getMinBuildHeight() - 8.0D
                || this.getY() > this.level().getMaxBuildHeight() + 24.0D
                || this.tickCount > 400;
    }

    // ================================================================
    // 客户端表现
    // ================================================================

    /** 拖尾：每隔几 tick 撒一点紫色碎光。 */
    private void spawnTrail() {
        if (this.tickCount % 2 != 0) {
            return;
        }
        this.level().addParticle(particleForTrail(),
                this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
    }

    private ParticleOptions particleForTrail() {
        return new net.minecraft.core.particles.DustParticleOptions(
                new Vector3f(0.72F, 0.52F, 1.0F), 0.85F);
    }

    /**
     * 种子蓄力光环。
     *
     * <p>半径随时间收拢、粒子随剩余时间变密 —— 玩家不用看数字就知道
     * "它要炸了，还有多久"。这是给"停在半空的那颗东西"一个明确的时间读数。
     */
    private void spawnSeedAura() {
        int warn = this.burstWarning();
        if (warn <= 0) {
            // 还在飞：只有一点尾焰
            this.spawnTrail();
            return;
        }
        int total = Math.max(1, ErebosTuning.HUNT_BURST_WARN);
        double progress = 1.0D - (double) warn / total;
        double radius = 3.2D - progress * 2.2D;      // 从 3.2 收到 1.0
        int arms = 3 + (int) (progress * 5.0D);      // 越接近越密

        var random = this.random;
        for (int i = 0; i < arms; i++) {
            double angle = (Math.PI * 2.0D / arms) * i + this.tickCount * 0.12D;
            double x = this.getX() + Math.cos(angle) * radius;
            double z = this.getZ() + Math.sin(angle) * radius;
            this.level().addParticle(ParticleTypes.END_ROD, x, this.getY(), z, 0.0D, 0.0D, 0.0D);
        }
        if (random.nextInt(3) == 0) {
            this.level().addParticle(ParticleTypes.REVERSE_PORTAL,
                    this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
        }
    }

    // ================================================================
    // 存档
    // ================================================================

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.remainingLife = tag.getInt("lr_life");
        this.seedAge = tag.getInt("lr_seed_age");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("lr_life", this.remainingLife);
        tag.putInt("lr_seed_age", this.seedAge);
    }
}
