package com.lost_realm.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.lost_realm.boss.entity.WardenBoss;

/**
 * 「遗落之主」第零阶段的<b>5 波怪物调度器</b>。
 *
 * <h2>它负责什么</h2>
 * 玩家选「决斗」→ 被遗落之主一击打飞 → 遗落之主消失 → 本调度器接管:
 * 按配置的波数 / 每波怪数 / 波间隔,依次刷出小怪攻击玩家。
 * 全部波次清空后,回调 {@link WardenBoss#enterRealFight()} 让 Boss 重新登场。
 *
 * <h2>为什么挂在 {@link WardenFightSession} 上而不是全局事件</h2>
 * 波次进度是<b>会话级</b>的:换维度、换存档、重进游戏都应当重置。
 * 用全局 {@code PlayerTickEvent} 会把逻辑写散,且难以清理。
 * 挂在 Session 上则天然随战斗生命周期创建与销毁。
 *
 * <h2>为什么用实体标签而不是"范围内所有怪物"</h2>
 * 遗落境虽然 {@code disable_mob_generation:true},但玩家可能自带刷怪蛋、
 * 或有其他模组在维度内刷怪。若用「范围内所有 Monster」计数,
 * 会把无关怪物算进来,导致波次永远清不掉。
 * 因此每只波次怪都打上 {@code lost_realm_wave_<n>} 标签,
 * 计数与清空判定<b>只认标签</b>。
 */
public final class WaveManager {

    /** 实体标签前缀。 */
    private static final String TAG_PREFIX = "lost_realm_wave_";

    /** 每只波次怪的额外标签(用于整体清理)。 */
    private static final String TAG_ALL = "lost_realm_wave_mob";

    /** 当前波次(0 基;-1 表示未开始)。 */
    private int currentWave = -1;

    /** 本波已生成的怪数量(用于分批生成,避免一帧刷出一堆)。 */
    private int spawnedInWave;

    /** 距离下一次生成的 tick。 */
    private int spawnCooldown;

    /** 距离下一波开始的 tick。 */
    private int interWaveDelay;

    /** 是否正在运行。 */
    private boolean active;

    /** 已记录的波次怪 UUID(兜底清理用,标签为主、UUID 为辅)。 */
    private final List<UUID> tracked = new ArrayList<>();

    // ================================================================
    // 开始 / 结束
    // ================================================================

    /** 开始第 1 波。 */
    public void begin(ServerLevel level, WardenBoss boss) {
        if (active) {
            return;
        }
        active = true;
        currentWave = 0;
        spawnedInWave = 0;
        spawnCooldown = 0;
        interWaveDelay = 0;
        tracked.clear();

        int total = BossRegistry.waveCount();
        broadcast(level, boss, Component.translatable(
                "message.lost_realm.lord.waves.begin", 1, total,
                BossRegistry.waveMobCount()));
    }

    /** 全部波次清空后的收尾。 */
    public void finish(ServerLevel level, WardenBoss boss) {
        active = false;
        currentWave = -1;
        tracked.clear();
        boss.enterRealFight();
    }

    /** 中止并清除所有波次怪(会话结束时调用)。 */
    public void clear(ServerLevel level) {
        active = false;
        currentWave = -1;
        if (level == null) {
            tracked.clear();
            return;
        }
        for (Monster m : level.getEntitiesOfClass(Monster.class,
                arenaBox(), e -> e.getTags().contains(TAG_ALL))) {
            m.discard();
        }
        tracked.clear();
    }

    public boolean isActive() {
        return active;
    }

    public int currentWave() {
        return currentWave;
    }

    // ================================================================
    // 主循环 —— 由 WardenBoss#aiStep 在 WAVES 状态下调用于每 10 tick
    // ================================================================

    /**
     * 推进一波的调度。
     *
     * <p>调用频率为每 10 tick 一次(与 Boss 的 {@code tickCount % 10} 对齐),
     * 因此下面所有 tick 计数都按 10 的倍数递减。
     */
    public void tick(ServerLevel level, WardenBoss boss) {
        if (!active) {
            return;
        }
        int waves = BossRegistry.waveCount();

        // ---- 波间隔倒计时 ----
        if (interWaveDelay > 0) {
            interWaveDelay -= 10;
            if (interWaveDelay <= 0) {
                currentWave++;
                spawnedInWave = 0;
                if (currentWave >= waves) {
                    finish(level, boss);
                    return;
                }
                broadcast(level, boss, Component.translatable(
                        "message.lost_realm.lord.waves.wave",
                        currentWave + 1, waves));
            }
            return;
        }

        // ---- 分批生成本波怪 ----
        int perWave = BossRegistry.waveMobCount();
        if (spawnedInWave < perWave) {
            if (spawnCooldown > 0) {
                spawnCooldown -= 10;
            } else {
                spawnWaveMob(level, boss, currentWave);
                spawnedInWave++;
                // 每只之间隔 20 tick,避免一帧刷一堆
                spawnCooldown = 20;
            }
            return;
        }

        // ---- 本波是否清空 ----
        if (countAlive(level, currentWave) == 0) {
            if (currentWave >= waves - 1) {
                // 最后一波清空 → Boss 登场
                broadcast(level, boss, Component.translatable(
                        "message.lost_realm.lord.waves.clear"));
                finish(level, boss);
            } else {
                // 进入波间隔
                interWaveDelay = Math.max(20, BossRegistry.waveIntervalSeconds() * 20);
                broadcast(level, boss, Component.translatable(
                        "message.lost_realm.lord.waves.cleared", currentWave + 1));
            }
        }
    }

    // ================================================================
    // 生成与计数
    // ================================================================

    /** 生成一只本波怪物。 */
    private void spawnWaveMob(ServerLevel level, WardenBoss boss, int waveIndex) {
        EntityType<? extends Monster> type = BossRegistry.waveMobType();
        if (type == null) {
            return;
        }
        Monster mob = type.create(level);
        if (mob == null) {
            return;
        }
        BlockPos around = boss != null ? boss.blockPosition() : ArenaBuilder.arenaCenter();
        BlockPos spot = WardenSpawnHandler.findSpawnSpot(level, around);
        if (spot == null) {
            // 找不到落点就退回"玩家附近"
            spot = boss != null ? boss.blockPosition().offset(3, 0, 3) : ArenaBuilder.arenaCenter().above();
        }
        mob.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spot),
                MobSpawnType.MOB_SUMMONED, null, null);

        // ★ 标记:计数只认标签,不认"范围内所有怪物"
        mob.addTag(TAG_PREFIX + waveIndex);
        mob.addTag(TAG_ALL);
        mob.setPersistenceRequired();

        level.addFreshEntity(mob);
        tracked.add(mob.getUUID());

        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                spot.getX() + 0.5D, spot.getY() + 1.0D, spot.getZ() + 0.5D,
                20, 0.5D, 0.6D, 0.5D, 0.05D);
        level.playSound(null, spot, net.minecraft.sounds.SoundEvents.SCULK_SHRIEKER_SHRIEK,
                net.minecraft.sounds.SoundSource.HOSTILE, 0.8F, 1.6F);
    }

    /** 统计某波仍存活的怪物数量(按标签过滤)。 */
    public int countAlive(ServerLevel level, int waveIndex) {
        String tag = TAG_PREFIX + waveIndex;
        return level.getEntitiesOfClass(Monster.class, arenaBox(),
                e -> e.isAlive() && e.getTags().contains(tag)).size();
    }

    /** 全场波次怪总数(调试 / 播报用)。 */
    public int countAllAlive(ServerLevel level) {
        return level.getEntitiesOfClass(Monster.class, arenaBox(),
                e -> e.isAlive() && e.getTags().contains(TAG_ALL)).size();
    }

    /** 城市范围的 AABB —— 波次怪只可能在城里。 */
    private static AABB arenaBox() {
        double r = BossRegistry.ARENA_RADIUS + 32.0D;
        return AABB.ofSize(Vec3.atCenterOf(ArenaBuilder.arenaCenter()), r * 2.0D, 256.0D, r * 2.0D);
    }

    private void broadcast(ServerLevel level, WardenBoss boss, Component msg) {
        WardenFightSession s = WardenFightManager.session(level);
        if (s != null) {
            s.broadcast(msg);
            return;
        }
        // 会话缺失时退回"广播给附近玩家"
        if (boss != null) {
            level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,
                    new AABB(boss.blockPosition()).inflate(128.0D))
                    .forEach(p -> p.displayClientMessage(msg, false));
        }
    }

    /** 供外部读取标签前缀(测试 / 调试)。 */
    public static String tagPrefix() {
        return TAG_PREFIX;
    }

    /** 供外部读取"所有波次怪"的标签。 */
    public static String tagAll() {
        return TAG_ALL;
    }

    /** 便捷:某实体是否属于波次怪。 */
    public static boolean isWaveMob(net.minecraft.world.entity.Entity e) {
        return e != null && e.getTags().contains(TAG_ALL);
    }

    /** 兼容:让 {@link Mob} 类型的调用方也能判定。 */
    public static boolean isWaveMob(Mob mob) {
        return mob != null && mob.getTags().contains(TAG_ALL);
    }
}
