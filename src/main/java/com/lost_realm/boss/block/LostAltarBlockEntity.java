package com.lost_realm.boss.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.lost_realm.Medals;
import com.lost_realm.boss.BossBlocks;
import com.lost_realm.boss.BossEntities;
import com.lost_realm.boss.BossRegistry;
import com.lost_realm.boss.ErebosArenaBuilder;
import com.lost_realm.boss.erebos.ErebosBoss;

/**
 * 中央祭坛的方块实体 —— <b>20 秒召唤仪式</b>的状态机。
 *
 * <h2>为什么用方块实体而不是全局静态计时</h2>
 * 静态计时在服务器重启、维度卸载、区块被卸载后就丢了；
 * 而仪式是「玩家站在祭坛前看 20 秒」的内容，中途断掉会非常突兀。
 * 方块实体能随区块一起存档，卸载后再加载可以继续。
 *
 * <h2>粒子的三层结构</h2>
 * <pre>
 *   0 – 8 秒   中央一道细柱，缓慢上升；十二柱顶只有零星光点
 *   8 – 16 秒  中央变粗，柱顶开始持续喷发，并向中央汇聚
 *   16 – 20 秒 中央开始爆闪，地面震动，柱顶喷发加密一倍
 * </pre>
 * 这个「由静到动」的推进比一上来就满屏粒子更像「仪式在积蓄」。
 * 20 秒的总粒子量约 1.1 万，分摊到 400 tick 后每秒不到 600 ——
 * 单人视角完全撑得住，而且这些粒子只在祭坛 100 格内可见时才发。
 */
public class LostAltarBlockEntity extends BlockEntity {

    /** 剩余 tick；0 表示没有仪式。 */
    private int ritualTicks;

    /** 仪式开始时的玩家（用于决定 Boss 朝向）；可能为 null。 */
    private Player summoner;

    public LostAltarBlockEntity(BlockPos pos, BlockState state) {
        super(BossBlocks.LOST_ALTAR_BE.get(), pos, state);
    }

    // ================================================================
    // 对外
    // ================================================================

    public boolean isRitualRunning() {
        return this.ritualTicks > 0;
    }

    public int ritualTicksLeft() {
        return this.ritualTicks;
    }

    /** 附近是否已经有一条乌蠹诺斯（判定半径与召唤物一致，512 格）。 */
    public boolean bossAlreadyPresent(ServerLevel level) {
        AABB area = new AABB(this.worldPosition).inflate(512.0D);
        for (ErebosBoss boss : level.getEntitiesOfClass(ErebosBoss.class, area)) {
            if (boss.isAlive() && !boss.isRemoved()) {
                return true;
            }
        }
        return false;
    }

    /** 开始仪式。 */
    public void startRitual(Player player) {
        this.ritualTicks = BossRegistry.RITUAL_TICKS;
        this.summoner = player;
        this.setChanged();
        Medals.LOGGER.info("[LR/altar] 召唤仪式开始于 {}", this.worldPosition);
    }

    // ================================================================
    // tick
    // ================================================================

    public void serverTick() {
        if (this.ritualTicks <= 0) {
            return;
        }
        if (!(this.level instanceof ServerLevel level)) {
            return;
        }

        this.ritualTicks--;
        int elapsed = BossRegistry.RITUAL_TICKS - this.ritualTicks;

        if (this.ritualTicks <= 0) {
            this.finishRitual(level);
            return;
        }

        this.spawnParticles(level, elapsed);
        this.playSounds(level, elapsed);

        // 每 20 tick 落一次盘，避免崩服时整段仪式白跑
        if ((elapsed & 15) == 0) {
            this.setChanged();
        }
    }

    // ----------------------------------------------------------------
    // 粒子
    // ----------------------------------------------------------------

    private void spawnParticles(ServerLevel level, int elapsed) {
        double cx = this.worldPosition.getX() + 0.5D;
        double cy = this.worldPosition.getY() + 1.0D;
        double cz = this.worldPosition.getZ() + 0.5D;

        boolean dense = elapsed > 160;   // 8 秒后
        boolean climax = elapsed > 320;  // 16 秒后

        // ---- 中央：一道向上抽起的光柱 ----
        int column = dense ? 10 : 5;
        double radius = dense ? 2.2D : 1.2D;
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                cx, cy + 1.0D, cz, column, radius, 0.6D, radius, 0.08D);
        if ((elapsed & 3) == 0) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    cx, cy + 0.4D, cz, dense ? 6 : 3, radius * 0.6D, 0.3D, radius * 0.6D, 0.02D);
        }

        // ---- 十二柱顶：向上喷，然后朝中央汇聚 ----
        // 每 2 tick 一轮：12 次发包/tick 的峰值折半，单人完全够用
        if ((elapsed & 1) == 0) {
            int perPillar = climax ? 4 : (dense ? 2 : 1);
            for (int i = 0; i < BossRegistry.PILLAR_COUNT; i++) {
                BlockPos top = ErebosArenaBuilder.pillarAltarPos(i);
                if (top == null) {
                    continue;
                }
                double px = top.getX() + 0.5D;
                double py = top.getY() + 1.2D;
                double pz = top.getZ() + 0.5D;

                level.sendParticles(ParticleTypes.SOUL,
                        px, py, pz, perPillar, 0.35D, 0.8D, 0.35D, 0.015D);

                if (dense) {
                    // 朝中央的方向取中点，做一条「灵魂流」——视觉上柱子与祭坛连成一片
                    double mx = px + (cx - px) * 0.35D;
                    double mz = pz + (cz - pz) * 0.35D;
                    level.sendParticles(ParticleTypes.END_ROD,
                            mx, py + 0.5D, mz, 1, 0.15D, 0.15D, 0.15D, 0.01D);
                }
            }
        }

        // ---- 高潮：爆闪 ----
        if (climax) {
            level.sendParticles(ParticleTypes.FLASH,
                    cx, cy + 2.0D, cz, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            if ((elapsed & 7) == 0) {
                level.sendParticles(ParticleTypes.EXPLOSION,
                        cx + (level.random.nextDouble() - 0.5D) * 6.0D,
                        cy + level.random.nextDouble() * 3.0D,
                        cz + (level.random.nextDouble() - 0.5D) * 6.0D,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    // ----------------------------------------------------------------
    // 音效
    // ----------------------------------------------------------------

    private void playSounds(ServerLevel level, int elapsed) {
        // 每 2 秒一记钟声，音高随进度爬升 —— 这是「越来越近」的主要来源
        if (elapsed % 40 == 0) {
            float progress = elapsed / (float) BossRegistry.RITUAL_TICKS;
            level.playSound(null, this.worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.BLOCKS, 3.0F, 0.45F + progress * 0.55F);
        }
        // 每 4 秒一层低频轰鸣
        if (elapsed % 80 == 0) {
            level.playSound(null, this.worldPosition, SoundEvents.RESPAWN_ANCHOR_AMBIENT,
                    SoundSource.BLOCKS, 2.5F, 0.4F);
        }
        // 最后 3 秒：心跳般的重击，越来越密
        int left = BossRegistry.RITUAL_TICKS - elapsed;
        if (left < 60 && left % (left < 20 ? 5 : 10) == 0) {
            level.playSound(null, this.worldPosition,
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS,
                    4.0F, 0.7F + (60 - left) / 60.0F * 0.6F);
        }
    }

    // ----------------------------------------------------------------
    // 收尾
    // ----------------------------------------------------------------

    private void finishRitual(ServerLevel level) {
        this.ritualTicks = 0;
        this.setChanged();

        // 祭坛恢复熄灭状态
        BlockState state = this.getBlockState();
        if (state.hasProperty(LostAltarBlock.ACTIVE)) {
            level.setBlock(this.worldPosition, state.setValue(LostAltarBlock.ACTIVE, false), 3);
        }

        Vec3 spot = new Vec3(
                this.worldPosition.getX() + 0.5D,
                this.worldPosition.getY() + 20.0D,
                this.worldPosition.getZ() + 0.5D);

        // ---- 惊天动地：三层音效叠加 ----
        level.playSound(null, this.worldPosition, SoundEvents.END_PORTAL_SPAWN,
                SoundSource.HOSTILE, 10.0F, 0.42F);
        level.playSound(null, this.worldPosition, SoundEvents.WITHER_SPAWN,
                SoundSource.HOSTILE, 8.0F, 0.55F);
        level.playSound(null, this.worldPosition, SoundEvents.GENERIC_EXPLODE,
                SoundSource.HOSTILE, 9.0F, 0.45F);

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                spot.x, spot.y, spot.z, 6, 3.0D, 1.5D, 3.0D, 0.0D);
        level.sendParticles(ParticleTypes.FLASH,
                spot.x, spot.y, spot.z, 8, 2.0D, 1.0D, 2.0D, 0.0D);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                spot.x, spot.y, spot.z, 400, 6.0D, 6.0D, 6.0D, 0.6D);

        // ---- 召唤 ----
        ErebosBoss boss = BossEntities.EREBOS.get().create(level);
        if (boss == null) {
            Medals.LOGGER.warn("[LR/altar] 仪式结束但实体创建失败");
            return;
        }

        float yaw = 0.0F;
        if (this.summoner != null && this.summoner.isAlive()) {
            yaw = (float) Math.toDegrees(Math.atan2(
                    this.summoner.getX() - spot.x, this.summoner.getZ() - spot.z));
        }
        boss.moveTo(spot.x, spot.y, spot.z, yaw, 0.0F);
        level.addFreshEntity(boss);

        Medals.LOGGER.info("[LR/altar] 仪式完成，乌蠹诺斯已召唤于 {}", spot);
        this.summoner = null;
    }

    // ================================================================
    // 存档
    // ================================================================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Ritual", this.ritualTicks);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.ritualTicks = tag.getInt("Ritual");
    }
}
