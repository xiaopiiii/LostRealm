package com.lost_realm.boss.erebos;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;

import org.joml.Vector3f;

/**
 * Erebos 的<b>特效与音效</b>集中处。
 *
 * <h2>为什么要单独一个类</h2>
 * 原因不是"代码整洁"，而是原设计里有两条硬性约束必须统一兑现：
 *
 * <ol>
 *   <li><b>光影必须有上限。</b>"全局设每帧和存活数量上限，减少特效模式进一步降低"——
 *       如果每个招式自己随手 spawn 粒子，这条约束就没人能保证。
 *       集中在这里，任何新增招式都走同一套节流。</li>
 *   <li><b>音效必须有并发上限。</b>"进行同时播放数量限制，避免叠加爆音"——
 *       一次爆散 14 发弹幕、每发都播一次音效，会叠成一坨噪音。
 *       这里用"同 tick 同类型只播一次"的方式压住。</li>
 * </ol>
 *
 * <h2>粒子选用</h2>
 * 全部用原版粒子，不额外打包贴图 —— 弹幕本体已经有自己的模型与贴图，
 * 碎光只需要"紫色、会淡出、无伤害"这三条，原版足够。
 * （{@code DustParticleOptions} 可以指定颜色，是唯一能出紫色的一种。）
 */
public final class ErebosFx {

    /** 每 tick 允许生成的粒子总数上限（服务端）。 */
    private static final int MAX_PARTICLES_PER_TICK = 220;

    /** 本 tick 已生成数（服务端计数器，由 {@link #beginTick()} 归零）。 */
    private static int particlesThisTick;

    /** 音效节流：记录每个音效最近一次播放的 tick。 */
    private static long lastPopSoundTick = Long.MIN_VALUE;

    private static final Vector3f VIOLET = new Vector3f(0.72F, 0.52F, 1.0F);
    private static final Vector3f PALE = new Vector3f(0.92F, 0.84F, 1.0F);

    private ErebosFx() {}

    /**
     * 每个服务端 tick 开头调用一次，重置粒子配额。
     *
     * <p>由 {@code ErebosBoss} 在其 tick 里调用 —— 只要场上有 Boss，
     * 配额就会被重置；没有 Boss 时不会有人生成特效，也无需重置。
     */
    public static void beginTick() {
        particlesThisTick = 0;
    }

    private static boolean take(int n) {
        if (particlesThisTick + n > MAX_PARTICLES_PER_TICK) {
            return false;
        }
        particlesThisTick += n;
        return true;
    }

    // ================================================================
    // 弹幕特效
    // ================================================================

    /**
     * 弹幕消散时的碎光。
     *
     * <p>原设计："弹幕消散时添加少量紫色碎光，普通弹约五颗，0.4—0.67 秒内
     * 向外飘散、减速、闪烁并淡出。"用 {@code DustParticleOptions} 配速度参数
     * 就能得到这个效果 —— 原版粒子本身就带速度与衰减。
     */
    public static void playPop(ServerLevel level, Vec3 pos) {
        if (!take(5)) {
            return;
        }
        level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(VIOLET, 1.15F),
                pos.x, pos.y, pos.z, 5, 0.22D, 0.22D, 0.22D, 0.06D);
    }

    /** 种子爆散。粒子多一点，并且配一个真正的爆炸音。 */
    public static void playBurst(ServerLevel level, Vec3 pos) {
        if (take(24)) {
            level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(PALE, 1.5F),
                    pos.x, pos.y, pos.z, 18, 0.6D, 0.6D, 0.6D, 0.18D);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    pos.x, pos.y, pos.z, 6, 0.5D, 0.5D, 0.5D, 0.05D);
        }
        playOnce(level, pos, SoundEvents.SHULKER_BULLET_HURT, 1.6F, 1.35F, 6);
    }

    /** 弹幕发射。 */
    public static void playShot(ServerLevel level, Vec3 pos) {
        playOnce(level, pos, SoundEvents.SHULKER_SHOOT, 1.1F, 1.5F, 4);
    }

    /**
     * 冲锋预警的起手音。
     *
     * <p>原设计："冲锋有长红线和原版音效预警。"音效在这里给的是一声闷响 ——
     * 它必须<b>短促且能穿过背景音</b>，因为玩家此刻要做的是"立刻找掩体/横向拉开"。
     */
    public static void playChargeCue(ServerLevel level, Vec3 pos) {
        playOnce(level, pos, SoundEvents.RAVAGER_ROAR, 2.2F, 0.85F, 8);
    }

    /**
     * 场上效果（激光 / 审判柱 / 星痕）进入"生效"的那一刻。
     *
     * <p>原设计："攻击位置使用世界坐标……发射方向必须和最后一帧预警一致。"
     * 这一声就是那个"预警结束、真的来了"的听觉锚点 ——
     * 玩家闭着眼也该知道什么时候该动。
     */
    public static void playEffectFire(ServerLevel level, Vec3 pos) {
        playOnce(level, pos, SoundEvents.BEACON_ACTIVATE, 1.3F, 1.8F, 4);
    }

    /** 阶段推进 / 转场。 */
    public static void playPhaseShift(ServerLevel level, Vec3 pos) {        playOnce(level, pos, SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 0.7F, 20);
        if (take(40)) {
            level.sendParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    pos.x, pos.y, pos.z, 30, 3.0D, 3.0D, 3.0D, 0.4D);
        }
    }

    /**
     * 节流播放音效 —— 同一个音效在 {@code cooldown} tick 内只响一次。
     *
     * <p>不加这个，一次 14 发爆散会在同一 tick 里叠 14 层同频音效，
     * 音量直接爆表。这是原设计特意叮嘱的"避免叠加爆音"。
     */
    private static void playOnce(ServerLevel level, Vec3 pos,
                                 net.minecraft.sounds.SoundEvent sound,
                                 float volume, float pitch, int cooldown) {
        long now = level.getGameTime();
        if (now - lastPopSoundTick < cooldown) {
            return;
        }
        lastPopSoundTick = now;
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.HOSTILE,
                volume, pitch);
    }

    // ================================================================
    // 客户端本地特效（不走网络）
    // ================================================================

    /**
     * 预警线粒子（客户端本地生成）。
     *
     * <p>一条几十格长的预警线如果走服务端广播，带宽会被白白烧掉；
     * 而它纯粹是"给玩家看的提示"，客户端自己画即可。
     * 这也是它每次只画一半点位的原因 —— 隔帧交错，开销再压一半。
     */
    public static void warningLine(net.minecraft.world.level.Level level, Vec3 from, Vec3 dir,
                                   double length, float red, float green, float blue,
                                   int tickCount, boolean alternate) {
        if (dir.lengthSqr() < 1.0E-8D) {
            return;
        }
        Vec3 d = dir.normalize();
        double step = 2.0D;
        int index = 0;
        RandomSource random = level.getRandom();
        for (double travel = 1.0D; travel <= length; travel += step) {
            if (alternate && index++ % 2 != (tickCount % 2)) {
                continue;
            }
            Vec3 p = from.add(d.scale(travel));
            level.addParticle(new net.minecraft.core.particles.DustParticleOptions(
                            new Vector3f(red, green, blue), 1.05F),
                    p.x, p.y, p.z, 0.0D, 0.0D, 0.0D);
        }
    }

    /**
     * 把单位向量 {@code base} 绕世界<b>Y 轴</b>旋转 {@code angle} 弧度。
     *
     * <p>客户端画扇形预警线时用它算出"左右边界"的方向 ——
     * 与服务端 {@code ErebosMove#fanAround} 是同一套数学，
     * 所以画出来的线一定和真正射出的弹幕对齐。两处算法必须是同一个，
     * 否则会出现"预警线在这，弹幕却打那"这种最伤信任的 bug。
     */
    public static Vec3 fan(Vec3 base, double angle) {
        Vec3 horizontal = new Vec3(base.x, 0.0D, base.z);
        horizontal = horizontal.lengthSqr() < 1.0E-8D
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : horizontal.normalize();
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double x = horizontal.x * cos - horizontal.z * sin;
        double z = horizontal.x * sin + horizontal.z * cos;
        Vec3 dir = new Vec3(x, base.y * 0.6D, z);
        return dir.lengthSqr() < 1.0E-8D ? base : dir.normalize();
    }

    /** 客户端本地的紫色尾迹（弹幕拖尾用）。 */
    public static void clientTrail(net.minecraft.world.level.Level level, Vec3 pos, float scale) {
        level.addParticle(new net.minecraft.core.particles.DustParticleOptions(VIOLET, scale),
                pos.x, pos.y, pos.z, 0.0D, 0.0D, 0.0D);
    }
}
