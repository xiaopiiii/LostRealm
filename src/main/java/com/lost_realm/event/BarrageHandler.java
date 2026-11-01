package com.lost_realm.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.lost_realm.Medals;
import com.lost_realm.config.MedalsConfig;
import com.lost_realm.data.PlayerMedals;
import com.lost_realm.skill.mage.SkillBarrageStorm;

/**
 * Lv.60【弹幕风暴】的「三连发」投射物复制器。
 *
 * <h2>为什么挂在 {@code EntityJoinLevelEvent}</h2>
 * Goety 的法术投射物种类极多(火球、寒霜射线、雷霆投射物、各种聚晶弹等),
 * 逐一 Mixin 每个法术类的成本极高,而且会与其它模组冲突。
 *
 * <p>好在所有投射物最终都要经过 {@link ServerLevel#addFreshEntity(Entity)} 进入世界,
 * 因此只要监听「实体加入世界」这一个事件,就能用<b>统一的通用逻辑</b>
 * 捕获任意 Goety 法术投射物的生成,而无须知道它具体是哪个类。
 *
 * <h2>触发条件</h2>
 * <ol>
 *   <li>该投射物的主人是服务端玩家</li>
 *   <li>该玩家法师熟练度 ≥ 30</li>
 *   <li>该玩家处于「三连发待发」状态(由 {@link SkillBarrageStorm} 在法术命中时置位)</li>
 *   <li>该投射物本身<b>不是</b>本处理器克隆出的副弹(防止无限递归)</li>
 * </ol>
 *
 * <h2>复制策略</h2>
 * 把原投射物的速度方向在「水平面内」向左右各旋转配置角度(默认 5°),
 * 并以同一个反序列化器拷贝出两份副弹。
 * <ul>
 *   <li>投射物 → 真正生成左右各一发副弹</li>
 *   <li>非投射物法术(如瞬发 AOE / 增益)→ 不做任何事,
 *       其补偿由 {@link SkillBarrageStorm} 自身的常驻「法术作用半径 +25%」承担</li>
 * </ul>
 *
 * <p>副弹通过持久数据标记 {@link #NBT_CHILD},避免被再次克隆。
 */
@Mod.EventBusSubscriber(modid = Medals.MODID)
public final class BarrageHandler {

    /** 副弹标记:克隆出的投射物带此标记,不再被克隆。 */
    public static final String NBT_CHILD = "medals_barrage_child";

    /** 单次触发最多克隆的副弹数量(左右各一发)。 */
    private static final int SIDE_SHOTS = 2;

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        // 只处理投射物
        if (!(event.getEntity() instanceof Projectile projectile)) {
            return;
        }
        // 副弹不再递归克隆
        if (projectile.getPersistentData().getBoolean(NBT_CHILD)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(projectile.getOwner() instanceof ServerPlayer owner)) {
            return;
        }
        if (!com.lost_realm.compat.goety.GoetyCompat.isLoaded()) {
            return;
        }

        PlayerMedals data = PlayerMedals.of(owner);
        if (com.lost_realm.data.MedalSource.level(owner, com.lost_realm.skill.MedalType.MAGE) < 60) {
            return;
        }
        if (!SkillBarrageStorm.isBarrageReady(data)) {
            return;
        }

        // 消耗待发标记:一次触发只放大「一発」法术
        SkillBarrageStorm.consumeBarrage(data);

        spawnSideShots(level, owner, projectile);
    }

    /**
     * 围绕原投射物生成左右两侧的副弹。
     *
     * <p>速度方向在水平面内旋转 ±{@code M30_BARRAGE_ANGLE} 度。
     * 若原投射物速度过小(接近静止),则退化为按玩家视线方向旋转。
     */
    private static void spawnSideShots(ServerLevel level, ServerPlayer owner, Projectile primary) {
        Vec3 velocity = primary.getDeltaMovement();
        Vec3 look = owner.getLookAngle();

        // 以水平分量构造旋转基准;速度过小时用视线方向兜底
        Vec3 base = new Vec3(velocity.x, 0.0D, velocity.z);
        if (base.lengthSqr() < 1.0E-8D) {
            base = new Vec3(look.x, 0.0D, look.z);
        }
        if (base.lengthSqr() < 1.0E-8D) {
            return;
        }
        base = base.normalize();

        double angle = Math.toRadians(Math.max(0.0D, MedalsConfig.M30_BARRAGE_ANGLE.get()));
        double speed = velocity.length();
        if (speed < 1.0E-4D) {
            speed = 1.0D;
        }
        // 保留原速度的竖直分量,只旋转水平方向
        double vy = velocity.y;

        for (int i = 0; i < SIDE_SHOTS; i++) {
            double sign = (i == 0) ? 1.0D : -1.0D; // 左 / 右
            Vec3 dir = rotateAroundY(base, sign * angle);

            Projectile copy;
            try {
                copy = cloneProjectile(primary);
            } catch (Throwable t) {
                // 个别投射物不支持反序列化拷贝,静默跳过
                return;
            }
            if (copy == null) {
                return;
            }

            copy.setPos(primary.getX(), primary.getY(), primary.getZ());
            copy.setOwner(owner);
            copy.setDeltaMovement(dir.x * speed, vy, dir.z * speed);
            // 朝向与速度一致,保证渲染正确
            copy.setYRot((float) (Math.toDegrees(Math.atan2(dir.x, dir.z))));
            copy.setXRot((float) Math.toDegrees(Math.atan2(vy, Math.sqrt(dir.x * dir.x + dir.z * dir.z))));
            copy.getPersistentData().putBoolean(NBT_CHILD, true);

            level.addFreshEntity(copy);
        }

        // 视觉反馈:提示三连发已射出
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                primary.getX(), primary.getY(), primary.getZ(),
                16, 0.3D, 0.3D, 0.3D, 0.4D);
    }

    /**
     * 通过 NBT 反序列化拷贝一个投射物。
     *
     * <p>用 {@link Projectile#getType()} 拿到实体类型后重新创建,
     * 再把原投射物的持久数据完整写过去 —— 这样无论是 Goety 的
     * 自定义投射物还是其它模组的,只要实现了标准的
     * {@code readAdditionalSaveData / addAdditionalSaveData} 就能正确复制。
     */
    private static Projectile cloneProjectile(Projectile primary) {
        Entity created = primary.getType().create(primary.level());
        if (!(created instanceof Projectile copy)) {
            return null;
        }
        var tag = new net.minecraft.nbt.CompoundTag();
        primary.saveWithoutId(tag);
        copy.load(tag);
        return copy;
    }

    /**
     * 将水平方向向量绕 Y 轴旋转 {@code angle} 弧度。
     */
    private static Vec3 rotateAroundY(Vec3 v, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        // 绕 Y 轴:[x' = x·cos + z·sin, z' = -x·sin + z·cos]
        double x = v.x * cos + v.z * sin;
        double z = -v.x * sin + v.z * cos;
        Vec3 out = new Vec3(x, 0.0D, z);
        return out.lengthSqr() < 1.0E-8D ? v : out.normalize();
    }

    private BarrageHandler() {}
}
