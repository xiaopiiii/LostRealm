package com.lost_realm.boss.erebos;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * 厄瑞玻斯的音效 —— 全部复用原版资源，不额外打包音频。
 *
 * <h2>为什么要节流</h2>
 * 这条件东西一次齐射最多 72 发，如果每发都 {@code playSound}，
 * 服务端会在一 tick 内向所有附近玩家推 72 个音效包 ——
 * 客户端叠成一片白噪音，听起来不是"齐射"而是"爆音"。
 * 所以统一按「每 tick 最多一次」限制：第一发生效，同 tick 的其余全部丢弃。
 *
 * <p>节流用 {@code gameTime} 而不是墙钟 —— 服务端停顿后恢复时不会误判。
 */
public final class ErebosSfx {

    private static long lastShotTick = Long.MIN_VALUE;
    private static long lastHitTick = Long.MIN_VALUE;
    private static long lastBoomTick = Long.MIN_VALUE;

    private ErebosSfx() {}

    /** 弹幕发射：短促的凋灵射击，音调抬高一点，听起来更"晶体"。 */
    public static void shot(ServerLevel level, Vec3 pos) {
        long now = level.getGameTime();
        if (now == lastShotTick) {
            return;
        }
        lastShotTick = now;
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 1.4F, 1.55F);
    }

    /**
     * Boss 受击：铁砧落地的那一下"铛"叠在凋灵受击的闷响上。
     *
     * <p>设计稿要求"原版机械 Boss 风格" —— 铁砧正好是金属块撞金属块，
     * 而凋灵的低频给体积感。两个一起放才不像敲钟。
     */
    public static void hit(ServerLevel level, Vec3 pos) {
        long now = level.getGameTime();
        if (now == lastHitTick) {
            return;
        }
        lastHitTick = now;
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.55F, 1.35F);
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.WITHER_HURT, SoundSource.HOSTILE, 0.45F, 0.85F);
    }

    /** 爆炸 / 爆散。 */
    public static void boom(ServerLevel level, Vec3 pos, float volume) {
        long now = level.getGameTime();
        if (now == lastBoomTick) {
            return;
        }
        lastBoomTick = now;
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, volume, 1.15F);
    }

    /** 星门开启 / 穿越。 */
    public static void gate(ServerLevel level, Vec3 pos) {
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.PORTAL_TRAVEL, SoundSource.HOSTILE, 0.7F, 0.75F);
    }
}
