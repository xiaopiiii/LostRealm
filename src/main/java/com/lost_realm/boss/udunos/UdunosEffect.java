package com.lost_realm.boss.udunos;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

import com.lost_realm.boss.BossEntities;

/**
 * <b>场上的一个效果</b> —— 激光、限制圈、审判柱、星痕、星门，全都用它承载。
 *
 * <h2>为什么做成实体，而不是自定义网络包</h2>
 * 这些东西本质上都是"服务端判定 + 客户端画出来"。最直觉的做法是写一套
 * 专用的同步包，但那意味着要自己处理<b>丢包、重连、玩家中途入场、
 * 维度切换</b>这一整套麻烦事 —— 而这些正是 MC 的实体同步层已经解决了几十年的。
 *
 * <p>所以这里反过来：<b>把效果做成实体</b>，位置用实体位置、朝向用实体朝向、
 * 参数用 {@code EntityDataAccessor}，于是同步、追踪范围、玩家入场补发、
 * 存档清理全都免费。代价是每 tick 有一点实体开销 —— 而场上的效果
 * 通常只有个位数到十几个，完全可以接受。
 *
 * <h2>渲染全靠粒子</h2>
 * 每种效果都不建模型，只在客户端每 tick 撒粒子。理由：
 * <ul>
 *   <li>激光是"一道光"、限制圈是"一个圆"、星痕是"地上的圈" ——
 *       它们本来就没有实体感，用粒子反而更像能量而不是几何体。</li>
 *   <li>省掉了五套几何 + 五张贴图 + 五个渲染器。</li>
 * </ul>
 *
 * <h2>判定</h2>
 * 服务端在 {@link #tick} 里自己算 —— 每种效果的形状不同（线段距离 / 圆内 / 圆柱内），
 * 但都遵守同一条纪律：<b>预警期间零伤害，生效期间与可见范围严格一致</b>。
 * 原设计要求"审判、脉冲和十字激光的实际碰撞与视觉一致"，这一条就在这里落实。
 */
public class UdunosEffect extends Entity {

    /** 效果种类。 */
    public enum Kind {
        /** 限制圈：固定世界坐标的圆形边界，<b>本身不造成伤害</b>，只拦着玩家不让出去。 */
        RING,
        /** 激光：一条长条，可绕 Y 轴旋转。<b>负宽度防不住</b> —— 见 {@link #tickLaser}。 */
        LASER,
        /** 审判柱：一根竖直光柱，覆盖地面一个圆。 */
        COLUMN,
        /** 星痕：地上的延迟爆发点。 */
        SCAR,
        /** 星门：一个紫色的门，只做视觉与传送锚点。 */
        GATE;

        public static Kind byIndex(int i) {
            Kind[] v = values();
            return v[Mth.clamp(i, 0, v.length - 1)];
        }
    }

    private static final EntityDataAccessor<Integer> DATA_KIND =
            SynchedEntityData.defineId(UdunosEffect.class, EntityDataSerializers.INT);
    /** 是否已进入"生效"阶段（预警期是 false）。客户端据此换配色。 */
    private static final EntityDataAccessor<Boolean> DATA_ACTIVE =
            SynchedEntityData.defineId(UdunosEffect.class, EntityDataSerializers.BOOLEAN);
    /** 主尺寸：圈的半径 / 激光长度 / 柱的半径。 */
    private static final EntityDataAccessor<Float> DATA_MAJOR =
            SynchedEntityData.defineId(UdunosEffect.class, EntityDataSerializers.FLOAT);
    /** 次尺寸：激光半宽 / 其他。 */
    private static final EntityDataAccessor<Float> DATA_MINOR =
            SynchedEntityData.defineId(UdunosEffect.class, EntityDataSerializers.FLOAT);
    /** 本效果所属的 Boss（用于取伤害来源）。 */
    private static final EntityDataAccessor<Integer> DATA_OWNER =
            SynchedEntityData.defineId(UdunosEffect.class, EntityDataSerializers.INT);

    // ---- 服务端参数 ----
    private int warnTicks;
    private int activeTicks;
    private int age;
    private float damage;
    private double spinDegPerTick;
    private float hitRadius;
    /** 每个玩家可被本效果命中的间隔（tick）。 */
    private int hitCooldown = 8;
    private final java.util.Map<java.util.UUID, Integer> lastHit = new java.util.HashMap<>();

    @Nullable
    private UdunosBoss owner;

    public UdunosEffect(EntityType<? extends UdunosEffect> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    // ================================================================
    // 工厂
    // ================================================================

    private static UdunosEffect base(ServerLevel level, UdunosBoss owner, Kind kind,
                                     Vec3 pos, int warnTicks, int activeTicks, float damage) {
        UdunosEffect effect = new UdunosEffect(BossEntities.UDUNOS_EFFECT.get(), level);
        effect.entityData.set(DATA_KIND, kind.ordinal());
        effect.entityData.set(DATA_OWNER, owner == null ? -1 : owner.getId());
        effect.owner = owner;
        effect.warnTicks = Math.max(0, warnTicks);
        effect.activeTicks = Math.max(1, activeTicks);
        effect.damage = damage;
        effect.setPos(pos.x, pos.y, pos.z);
        return effect;
    }

    /** 限制圈。半径单位是格。 */
    public static UdunosEffect ring(ServerLevel level, UdunosBoss owner, Vec3 center, double radius) {
        UdunosEffect effect = base(level, owner, Kind.RING, center, 20, 12000, 0.0F);
        effect.entityData.set(DATA_MAJOR, (float) radius);
        effect.entityData.set(DATA_MINOR, 0.0F);
        return effect;
    }

    /**
     * 激光。
     *
     * @param origin      起点
     * @param yaw         初始偏航（度）
     * @param length      长度（格）
     * @param halfWidth   半宽（格）
     * @param warnTicks   预警时长
     * @param activeTicks 生效时长
     * @param spin        旋转角速度（度/tick，可负）
     */
    public static UdunosEffect laser(ServerLevel level, UdunosBoss owner, Vec3 origin,
                                     float yaw, float pitch, double length, float halfWidth,
                                     int warnTicks, int activeTicks, float damage,
                                     double spinDegPerTick) {
        UdunosEffect effect = base(level, owner, Kind.LASER, origin, warnTicks, activeTicks, damage);
        effect.setYRot(yaw);
        effect.setXRot(pitch);
        effect.entityData.set(DATA_MAJOR, (float) length);
        effect.entityData.set(DATA_MINOR, halfWidth);
        effect.spinDegPerTick = spinDegPerTick;
        effect.hitCooldown = 6;
        return effect;
    }

    /** 审判柱：竖直光柱，覆盖地面一个圆。 */
    public static UdunosEffect column(ServerLevel level, UdunosBoss owner, Vec3 ground,
                                      double radius, int warnTicks, int activeTicks, float damage) {
        UdunosEffect effect = base(level, owner, Kind.COLUMN, ground, warnTicks, activeTicks, damage);
        effect.entityData.set(DATA_MAJOR, (float) radius);
        effect.hitCooldown = 8;
        return effect;
    }

    /** 星痕：地上一个延迟爆发的点。 */
    public static UdunosEffect scar(ServerLevel level, UdunosBoss owner, Vec3 ground,
                                    double radius, int warnTicks, int activeTicks, float damage) {
        UdunosEffect effect = base(level, owner, Kind.SCAR, ground, warnTicks, activeTicks, damage);
        effect.entityData.set(DATA_MAJOR, (float) radius);
        effect.hitCooldown = 10;
        return effect;
    }

    /** 星门（纯视觉锚点）。 */
    public static UdunosEffect gate(ServerLevel level, UdunosBoss owner, Vec3 pos, double radius) {
        UdunosEffect effect = base(level, owner, Kind.GATE, pos, 0, 200, 0.0F);
        effect.entityData.set(DATA_MAJOR, (float) radius);
        return effect;
    }

    // ================================================================
    // 同步
    // ================================================================

    @Override
    protected void defineSynchedData() {
        // ⚠️ 不能调 super（Entity 里是抽象方法）；Entity 的基础槽位由它自己的构造函数注册
        this.entityData.define(DATA_KIND, Kind.SCAR.ordinal());
        this.entityData.define(DATA_ACTIVE, false);
        this.entityData.define(DATA_MAJOR, 1.0F);
        this.entityData.define(DATA_MINOR, 0.2F);
        this.entityData.define(DATA_OWNER, -1);
    }

    public Kind kind() {
        return Kind.byIndex(this.entityData.get(DATA_KIND));
    }

    public boolean active() {
        return this.entityData.get(DATA_ACTIVE);
    }

    public float major() {
        return this.entityData.get(DATA_MAJOR);
    }

    public float minor() {
        return this.entityData.get(DATA_MINOR);
    }

    /** 距离本效果"进入生效"还有多久（客户端用于让预警越闪越急）。 */
    public int ticksIntoPhase() {
        return Math.max(0, this.age - this.warnTicks);
    }

    // ================================================================
    // 服务端 tick
    // ================================================================

    @Override
    public void tick() {
        if (this.level().isClientSide) {
            // 客户端也推进 age —— 渲染的"越接近生效越急"靠它。
            // 两端的 age 各自递增，速率一致，所以不需要同步。
            this.age++;
            this.clientParticles();
            return;
        }
        this.age++;

        // 寿命：预警 + 生效
        if (this.age > this.warnTicks + this.activeTicks) {
            this.discard();
            return;
        }
        // 主人没了就一起收 —— 否则 Boss 被打死后场地上会残留一片激光
        if (this.owner != null && (this.owner.isRemoved() || !this.owner.isAlive())) {
            this.discard();
            return;
        }

        boolean nowActive = this.age > this.warnTicks;
        if (this.entityData.get(DATA_ACTIVE) != nowActive) {
            this.entityData.set(DATA_ACTIVE, nowActive);
            if (nowActive) {
                UdunosFx.playEffectFire((ServerLevel) this.level(), this.position());
            }
        }

        // 旋转（激光）
        if (this.spinDegPerTick != 0.0D) {
            this.setYRot((float) (this.getYRot() + this.spinDegPerTick));
        }

        switch (this.kind()) {
            case RING -> this.tickRing();
            case LASER -> this.tickLaser();
            case COLUMN -> this.tickColumn();
            case SCAR -> this.tickScar();
            case GATE -> {
                // 纯视觉
            }
        }

        // 命中表会随玩家进出累积，顺手清一下
        if (this.lastHit.size() > 24) {
            this.lastHit.entrySet().removeIf(e -> this.age - e.getValue() > 100);
        }
    }

    /**
     * 限制圈：把越界的玩家推回来。
     *
     * <p>原设计："圈本身立即阻止越界，玩家可沿圈边滑动；限制圈没有伤害、
     * 持续扣血或激怒惩罚。"所以这里只做位置纠正，不产生任何伤害 ——
     * 沿边滑动的效果靠"只纠正径向分量"自然得到。
     */
    private void tickRing() {
        double radius = this.major();
        Vec3 center = this.position();
        for (Player player : this.nearbyPlayers(radius + 24.0D)) {
            Vec3 delta = player.position().subtract(center);
            double distance = delta.length();
            if (distance <= radius) {
                continue;
            }
            Vec3 inward = delta.scale(1.0D / Math.max(1.0E-4D, distance));
            Vec3 corrected = center.add(inward.scale(radius * 0.999D));
            player.setPos(corrected.x, player.getY(), corrected.z);
            // 把向外的速度分量也削掉，否则玩家会被"顶"在墙上抖
            Vec3 motion = player.getDeltaMovement();
            double outward = motion.x * inward.x + motion.z * inward.z;
            if (outward > 0.0D) {
                player.setDeltaMovement(motion.x - inward.x * outward, motion.y,
                        motion.z - inward.z * outward);
                player.hurtMarked = true;
            }
        }
    }

    /**
     * 激光：点到线段距离判定。
     *
     * <p>用"点到线段距离"而不是"AABB 相交"：激光会绕圈旋转，
     * 轴对齐盒子转起来会时大时小，出现过"明明没碰到却被烧伤"。
     */
    private void tickLaser() {
        if (!this.active() || this.damage <= 0.0F) {
            return;
        }
        double length = this.major();
        double halfWidth = this.minor();
        Vec3 origin = this.position();
        Vec3 dir = forward();

        for (Player player : this.nearbyPlayers(length + 8.0D)) {
            Vec3 toPlayer = player.position().add(0.0D, player.getBbHeight() * 0.5D, 0.0D)
                    .subtract(origin);
            double along = toPlayer.dot(dir);
            if (along < 0.0D || along > length) {
                continue;
            }
            Vec3 closest = origin.add(dir.scale(along));
            double distance = closest.distanceTo(player.position()
                    .add(0.0D, player.getBbHeight() * 0.5D, 0.0D));
            if (distance <= halfWidth + 0.45D) {
                this.hit(player);
            }
        }
    }

    /** 审判柱：圆柱内判定。 */
    private void tickColumn() {
        if (!this.active() || this.damage <= 0.0F) {
            return;
        }
        double radius = this.major();
        Vec3 base = this.position();
        for (Player player : this.nearbyPlayers(radius + 4.0D)) {
            double dx = player.getX() - base.x;
            double dz = player.getZ() - base.z;
            if (dx * dx + dz * dz <= (radius + 0.4D) * (radius + 0.4D)
                    && player.getY() >= base.y - 2.0D && player.getY() <= base.y + 32.0D) {
                this.hit(player);
            }
        }
    }

    /** 星痕：地面圆判定（比审判柱矮，跳不过去但也不算太苛刻）。 */
    private void tickScar() {
        if (!this.active() || this.damage <= 0.0F) {
            return;
        }
        double radius = this.major();
        Vec3 base = this.position();
        for (Player player : this.nearbyPlayers(radius + 4.0D)) {
            double dx = player.getX() - base.x;
            double dz = player.getZ() - base.z;
            if (dx * dx + dz * dz <= (radius + 0.4D) * (radius + 0.4D)
                    && player.getY() >= base.y - 3.0D && player.getY() <= base.y + 6.0D) {
                this.hit(player);
            }
        }
    }

    /** 本效果朝向的水平单位向量。 */
    public Vec3 forward() {
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        float pitch = this.getXRot() * Mth.DEG_TO_RAD;
        double cosPitch = Math.cos(pitch);
        return new Vec3(-Math.sin(yaw) * cosPitch, -Math.sin(pitch), Math.cos(yaw) * cosPitch);
    }

    private List<Player> nearbyPlayers(double radius) {
        AABB area = new AABB(this.getX() - radius, this.getY() - radius, this.getZ() - radius,
                this.getX() + radius, this.getY() + radius, this.getZ() + radius);
        return this.level().getEntitiesOfClass(Player.class, area);
    }

    /** 结算一次命中（带个体冷却，避免每 tick 都判、把玩家瞬秒）。 */
    private void hit(Player player) {
        if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
            return;
        }
        Integer last = this.lastHit.get(player.getUUID());
        if (last != null && this.age - last < this.hitCooldown) {
            return;
        }
        var source = this.owner != null
                ? this.damageSources().mobAttack(this.owner)
                : this.damageSources().magic();
        if (player.hurt(source, this.damage)) {
            this.lastHit.put(player.getUUID(), this.age);
        }
    }

    // ================================================================
    // 客户端粒子（在渲染器里调用）
    // ================================================================

    /**
     * 客户端每 tick 调用一次，画出这个效果。
     *
     * <h2>为什么每个分支的粒子数都压得这么低</h2>
     * MC 的粒子总量上限是 4000，超了就静默丢弃。崩星审判一轮能同时立起
     * 十几根柱子，如果每根都撒几十粒，一秒钟就能把配额吃干 ——
     * 结果是玩家自己的火把、附魔台、药水粒子全被挤掉，看着像"世界出故障了"。
     *
     * <p>所以这里给每种效果定死一个很小的"每 tick 粒数"（4~8），
     * 靠粒子自身的存续时间（约 1 秒）去累积出连续的观感：
     * 6 粒/tick × 20 tick = 每秒 120 粒堆成一道光，足够了。
     * 光效的"亮"靠颜色和密度印象，不靠堆数量。
     */
    private void clientParticles() {
        Level level = this.level();
        var random = level.getRandom();
        boolean active = this.active();

        switch (this.kind()) {
            case RING -> {
                // 限制圈：一小段一小段地轮转，转一圈要若干 tick，
                // 但因为粒子活得久，看起来始终是完整的一圈
                double radius = this.major();
                int points = 6;
                double offset = (this.age % 40) * (Math.PI * 2.0D / 40.0D);
                for (int i = 0; i < points; i++) {
                    double angle = offset + Math.PI * 2.0D * i / points;
                    level.addParticle(dust(0.55F, 0.38F, 1.0F, 1.0F),
                            this.getX() + Math.cos(angle) * radius,
                            this.getY() + 0.15D,
                            this.getZ() + Math.sin(angle) * radius,
                            0.0D, 0.0D, 0.0D);
                }
            }
            case LASER -> {
                int points = 8;
                Vec3 origin = this.position();
                Vec3 dir = this.forward();
                double length = this.major();
                for (int i = 0; i < points; i++) {
                    // 用 age 让采样点在线上滚动，形成"能量流动"的错觉
                    double t = ((this.age * 0.06D) + (double) i / points) % 1.0D;
                    Vec3 p = origin.add(dir.scale(length * t));
                    float bright = active ? (float) (1.0D - t * 0.4D) : 0.30F;
                    level.addParticle(dust(0.55F * bright + 0.30F, 0.30F * bright, 1.0F, 1.1F),
                            p.x, p.y, p.z, 0.0D, 0.0D, 0.0D);
                }
            }
            case COLUMN -> {
                int points = 4;
                double radius = this.major();
                for (int i = 0; i < points; i++) {
                    double angle = random.nextDouble() * Math.PI * 2.0D;
                    double r = radius * Math.sqrt(random.nextDouble());
                    double x = this.getX() + Math.cos(angle) * r;
                    double z = this.getZ() + Math.sin(angle) * r;
                    level.addParticle(dust(active ? 1.0F : 0.45F, active ? 0.90F : 0.26F, 1.0F, 1.2F),
                            x, this.getY() + 0.12D, z, 0.0D, 0.0D, 0.0D);
                    if (active) {
                        level.addParticle(ParticleTypes.END_ROD, x,
                                this.getY() + random.nextDouble() * 18.0D, z, 0.0D, 0.5D, 0.0D);
                    }
                }
            }
            case SCAR -> {
                int points = 4;
                double radius = this.major();
                // 越接近爆发，圈收得越紧 —— 这就是"要炸了"的读数
                double r = active ? radius : radius * (1.0D - 0.4D * this.progress());
                for (int i = 0; i < points; i++) {
                    double angle = random.nextDouble() * Math.PI * 2.0D;
                    level.addParticle(dust(active ? 1.0F : 0.52F, active ? 0.84F : 0.26F, 1.0F, 1.0F),
                            this.getX() + Math.cos(angle) * r,
                            this.getY() + 0.1D,
                            this.getZ() + Math.sin(angle) * r,
                            0.0D, 0.0D, 0.0D);
                }
            }
            case GATE -> {
                int points = 6;
                double radius = this.major();
                for (int i = 0; i < points; i++) {
                    double angle = random.nextDouble() * Math.PI * 2.0D;
                    level.addParticle(dust(0.75F, 0.48F, 1.0F, 1.0F),
                            this.getX() + Math.cos(angle) * radius,
                            this.getY() + 1.2D + Math.sin(angle) * radius,
                            this.getZ(), 0.0D, 0.0D, 0.0D);
                }
                if (random.nextInt(3) == 0) {
                    level.addParticle(ParticleTypes.REVERSE_PORTAL,
                            this.getX() + (random.nextDouble() - 0.5D) * radius,
                            this.getY() + 1.2D + (random.nextDouble() - 0.5D) * radius,
                            this.getZ(), 0.0D, 0.0D, 0.0D);
                }
            }
        }
    }

    private float progress() {
        return this.warnTicks <= 0 ? 1.0F
                : Mth.clamp((float) this.age / this.warnTicks, 0.0F, 1.0F);
    }

    private static ParticleOptions dust(float r, float g, float b, float scale) {
        return new net.minecraft.core.particles.DustParticleOptions(new Vector3f(r, g, b), scale);
    }

    /** 预警总时长（客户端渲染用）。 */
    public int warnTicks() {
        return this.warnTicks;
    }

    /** 当前年龄（客户端渲染用）。 */
    public int age() {
        return this.age;
    }

    // ================================================================
    // Entity 杂项
    // ================================================================

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.warnTicks = tag.getInt("lr_warn");
        this.activeTicks = tag.getInt("lr_active");
        this.age = tag.getInt("lr_age");
        this.damage = tag.getFloat("lr_dmg");
        this.spinDegPerTick = tag.getDouble("lr_spin");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("lr_warn", this.warnTicks);
        tag.putInt("lr_active", this.activeTicks);
        tag.putInt("lr_age", this.age);
        tag.putFloat("lr_dmg", this.damage);
        tag.putDouble("lr_spin", this.spinDegPerTick);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(Math.max(8.0D, this.major()));
    }

    @Override
    public boolean isInWall() {
        return false;
    }

    /** 供 {@code UdunosFx} 判断是不是自己人。 */
    public boolean belongsTo(@Nullable LivingEntity entity) {
        return entity != null && this.owner == entity;
    }
}
