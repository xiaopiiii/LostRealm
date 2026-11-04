package com.lost_realm.boss.udunos;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.lost_realm.boss.BossEntities;

/**
 * 死亡演出里飞出去的装甲碎块。
 *
 * <h2>为什么不做成粒子</h2>
 * 设计稿写得很死：「必须是真实装甲碎块 —— 从贴图切出、继承体节位置和朝向、
 * 向外甩出、自转、受重力下落、触地轻微反弹，再淡出。不是只放几个光点。」
 * 粒子做不到自转和反弹，也没有体积感 —— 远看就是一团紫色雾气。
 *
 * <h2>性能</h2>
 * 一次死亡约 360 块，每块寿命 6 秒。它们是独立实体，
 * <b>不占弹幕名额、没有碰撞伤害</b>（{@code noPhysics}），
 * 也不写存档（{@code noSave}）—— Boss 都死了，没人需要它们活过重载。
 * 渲染只用一个共享的 16×16×16 方块模型，缩到 0.1~0.2 格，
 * 三百多个也就三百多次 draw，比一整条虫的 50 节还轻。
 */
public class UdunosShard extends Entity {

    /** 6 秒 —— 足够看到落地、弹一下、淡出。 */
    private static final int MAX_LIFE = 120;

    private int life = MAX_LIFE;
    private double vx;
    private double vy;
    private double vz;
    private float spinX;
    private float spinY;
    private float shardScale = 0.14F;

    public UdunosShard(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    // ================================================================
    // 工厂
    // ================================================================

    /**
     * 在某个体节的位置甩出一块碎片。
     *
     * @param pos     体节世界坐标
     * @param outward 由虫身指向外的方向（通常是「体节 − 圆心」归一化）
     * @param yaw     体节朝向，碎片继承它，看起来像是从装甲上剥下来的
     */
    public static void spawn(ServerLevel level, Vec3 pos, Vec3 outward, float yaw, RandomSource rnd) {
        UdunosShard shard = new UdunosShard(BossEntities.UDUNOS_SHARD.get(), level);
        shard.setPos(pos.x, pos.y, pos.z);
        shard.setYRot(yaw);
        shard.yRotO = yaw;

        // 向外 + 随机偏 + 向上抛
        shard.vx = outward.x * 0.16D + (rnd.nextDouble() - 0.5D) * 0.26D;
        shard.vy = 0.10D + rnd.nextDouble() * 0.16D;
        shard.vz = outward.z * 0.16D + (rnd.nextDouble() - 0.5D) * 0.26D;

        shard.spinX = (rnd.nextFloat() - 0.5F) * 24.0F;
        shard.spinY = (rnd.nextFloat() - 0.5F) * 24.0F;
        shard.shardScale = 0.10F + rnd.nextFloat() * 0.10F;

        level.addFreshEntity(shard);
    }

    // ================================================================
    // tick
    // ================================================================

    @Override
    public void tick() {
        super.tick();

        if (--this.life <= 0) {
            this.discard();
            return;
        }

        this.vy -= 0.035D;                       // 比原版重力轻一点，碎块飘得久

        double nx = this.getX() + this.vx;
        double ny = this.getY() + this.vy;
        double nz = this.getZ() + this.vz;

        var box = this.getBoundingBox().move(nx - this.getX(), ny - this.getY(), nz - this.getZ());
        if (this.level().noCollision(this, box)) {
            this.setPos(nx, ny, nz);
        } else {
            // 触地：竖直速度反向并衰减，水平摩擦拖慢（设计稿的"轻微反弹"）
            this.vy = -this.vy * 0.28D;
            this.vx *= 0.62D;
            this.vz *= 0.62D;
            if (Math.abs(this.vy) < 0.03D) {
                this.vy = 0.0D;
            }
            this.setPos(this.getX() + this.vx, this.getY(), this.getZ() + this.vz);
        }

        this.setXRot(this.getXRot() + this.spinX);
        this.setYRot(this.getYRot() + this.spinY);

        // 落地后转得慢下来，避免在地上抽风
        this.spinX *= 0.985F;
        this.spinY *= 0.985F;
    }

    /** 渲染用：剩余生命比例（0~1），最后 0.4 秒开始淡出。 */
    public float fade() {
        return Math.min(1.0F, this.life / 24.0F);
    }

    public float shardScale() {
        return this.shardScale;
    }

    // ================================================================
    // 存档：不存
    // ================================================================

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean shouldRenderAtSqrDistance(double distSqr) {
        return distSqr < 128.0D * 128.0D;
    }
}
