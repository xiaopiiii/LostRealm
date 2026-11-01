package com.lost_realm.boss;

import net.minecraft.world.entity.player.Player;

import com.lost_realm.compat.goety.GoetyCompat;

/**
 * Boss 与 <b>Goety(诡厄巫法)</b>之间的桥接点。
 *
 * <h2>为什么复用既有 GoetyBridge 而不是新写一层</h2>
 * 本模组已有 {@code compat/goety/GoetyBridge}(纯接口)+ {@code GoetyBridgeImpl}(真实实现)
 * 这一套类加载隔离体系。Boss 只需要"读灵魂 / 扣灵魂"两个能力,
 * 而 {@link com.lost_realm.compat.goety.GoetyBridge} 已经具备 ——
 * 再写一份实现只会造成两处维护。因此本类只做<b>语义封装与降级</b>,
 * 真正的 Goety 调用仍走既有桥接。
 *
 * <h2>降级策略</h2>
 * 未安装 Goety(或桥接为 null)时:
 * <ul>
 *   <li>{@link #readSouls} 返回 0 → 技能改用"按玩家最大生命折算"的替代算法</li>
 *   <li>{@link #drainSouls} 返回 0 → 不产生实际扣除,但仍给 Boss 加护盾</li>
 * </ul>
 * 这样"灵魂汲取"在任何环境下都是可用机制,只是数值来源不同。
 */
public final class BossGoetyHooks {

    /**
     * 读取玩家的灵魂量。
     *
     * @return 灵魂数;未安装 Goety 或读取失败时返回 0
     */
    public static int readSouls(Player player) {
        if (!GoetyCompat.isLoaded()) {
            return 0;
        }
        var bridge = GoetyCompat.bridge();
        if (bridge == null) {
            return 0;
        }
        try {
            return Math.max(0, bridge.getSouls(player));
        } catch (Throwable t) {
            // 任何异常都不该让 Boss 技能崩掉整场战斗
            return 0;
        }
    }

    /**
     * 扣除玩家灵魂。
     *
     * @param amount 期望扣除量
     * @return 实际扣除量(未装 Goety 时恒为 0)
     */
    public static int drainSouls(Player player, int amount) {
        if (amount <= 0 || !GoetyCompat.isLoaded()) {
            return 0;
        }
        var bridge = GoetyCompat.bridge();
        if (bridge == null) {
            return 0;
        }
        try {
            int before = Math.max(0, bridge.getSouls(player));
            int actual = Math.min(before, amount);
            if (actual > 0) {
                bridge.decreaseSouls(player, actual);
            }
            return actual;
        } catch (Throwable t) {
            return 0;
        }
    }

    private BossGoetyHooks() {}
}
