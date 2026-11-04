package com.lost_realm.client;

import com.lost_realm.boss.erebos.ErebosBoss;
import com.lost_realm.boss.erebos.ErebosPhase;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;

/**
 * 乌蠹诺斯战斗曲的开关与淡入淡出。
 *
 * <h2>判定方式：客户端每 tick 找一次 Boss</h2>
 * 没有走服务端发包、也没有做事件回调 —— BGM 完全由客户端自己判断
 * "视野范围内有没有活着的乌蠹诺斯"。
 *
 * <p>这么选的理由是<b>它不可能不同步</b>：音乐是本地的，
 * 触发条件也是本地的（实体是否被客户端加载）。用服务端事件反而会出现
 * "服务端说开战了，但玩家这边实体还没同步"的错位。
 *
 * <h2>为什么要淡入淡出，而不是直接 play / stop</h2>
 * 直接播放会在 0 秒处"啪"地砸进来；直接 stop 会在半小节上硬切。
 * 实测这两种都比音乐本身难听。淡入约 2.3 秒、淡出约 1.4 秒，
 * 刚好藏在战斗的混乱里。
 */
public final class ErebosMusicController {

    /** 触发半径（格）。Boss 身体长 210 格，但玩家总在头部附近交战，取 128 足够。 */
    private static final double TRIGGER_RANGE = 128.0D;

    /** 每 tick 的淡入步长 —— 0.55 / 0.012 ≈ 46 tick ≈ 2.3 秒。 */
    private static final float FADE_IN_STEP = 0.012F;

    /** 每 tick 的淡出步长 —— 0.55 / 0.02 ≈ 28 tick ≈ 1.4 秒。 */
    private static final float FADE_OUT_STEP = 0.02F;

    private static ErebosBattleMusic music;

    /**
     * 距离上次"找 Boss"过了多少 tick。
     *
     * <p>{@code getEntitiesOfClass} 要遍历 AABB 覆盖的所有 section，
     * 每 tick 都做在 200 模组的整合包里属于白扔性能 ——
     * 而音乐是渐变的，0.5 秒的判定延迟完全听不出来。
     * 音量推进仍然每 tick 一次，所以淡入淡出依旧平滑。
     */
    private static int checkCooldown;

    /** 每隔多少 tick 重新判定一次"附近有没有 Boss"。 */
    private static final int CHECK_INTERVAL = 10;

    /** 最近一次判定的结果。 */
    private static boolean bossHere;

    /** 场上是否有阶段Ⅲ/Ⅳ的 Boss —— 决定放前半段还是后半段。 */
    private static boolean bossLate;

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            bossHere = false;
            checkCooldown = 0;
            stopNow();
            return;
        }

        if (--checkCooldown <= 0) {
            checkCooldown = CHECK_INTERVAL;
            scan(mc);
        }

        if (bossHere) {
            if (music == null) {
                // 先建实例再 play：音量从 0 起，等于"无声音地进入队列"
                music = new ErebosBattleMusic(bossLate);
                mc.getSoundManager().play(music);
            } else if (music.isLate() != bossLate) {
                // 换阶段（Ⅱ→Ⅲ）要换曲：先淡出旧的，完全静音后再起新的。
                // 不能直接 stop + play —— 后半段会硬切进来，和转场演出撞在一起。
                music.lower(FADE_OUT_STEP);
                if (music.isSilent()) {
                    stopNow();
                    music = new ErebosBattleMusic(bossLate);
                    mc.getSoundManager().play(music);
                }
                return;
            }
            music.raise(FADE_IN_STEP);
        } else if (music != null) {
            music.lower(FADE_OUT_STEP);
            // 完全淡出后才真正停止，避免把尾声切断
            if (music.isSilent()) {
                stopNow();
            }
        }
    }

    /**
     * 视野/加载范围内是否有乌蠹诺斯。
     *
     * <p>用 {@code !isRemoved()} 而不是 {@code isAlive()}：
     * Boss 被打死后的 60 tick 崩解演出期间实体仍然存在，
     * 那段时间音乐应该继续 —— 突然静音会让死亡演出变得很空。
     */
    private static void scan(Minecraft mc) {
        bossHere = false;
        bossLate = false;
        var area = mc.player.getBoundingBox().inflate(TRIGGER_RANGE);
        for (ErebosBoss boss : mc.level.getEntitiesOfClass(ErebosBoss.class, area)) {
            if (!boss.isRemoved()) {
                bossHere = true;
                // 用 syncedPhase：这是客户端，phase() 是服务端字段
                int idx = boss.syncedPhase().index();
                bossLate = idx >= ErebosPhase.THREE.index();
                // 天色也跟着阶段走：Ⅰ 0.25 → Ⅳ 1.0
                com.lost_realm.client.sky.ErebosSkyEffects.setIntensity(0.25F + idx * 0.25F);
                return;
            }
        }
    }

    private static void stopNow() {
        if (music != null) {
            Minecraft.getInstance().getSoundManager().stop(music);
            music = null;
        }
    }

    private ErebosMusicController() {}
}
