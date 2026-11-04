package com.lost_realm.command;

import java.util.List;

import com.lost_realm.boss.erebos.ErebosBoss;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/**
 * 服务端自检命令 —— {@code /lrsdebug erebos}。
 *
 * <h2>它回答客户端命令回答不了的问题</h2>
 * 客户端那条 {@code /lrdebug} 只能告诉你"实体在不在客户端"，但看不出
 * <b>状态机走到哪一步了</b>。而乌蠹诺斯最要命的一类故障恰恰在这里：
 * 它卡在 {@code EMERGE}（登场）出不来，于是
 * <ul>
 *   <li>{@code hurt()} 直接返回 false → <b>打不出伤害</b></li>
 *   <li>不参与选招 → <b>不放招式</b></li>
 *   <li>轨迹不够长 → 40 节全堆在头部 → <b>只有一个头</b></li>
 * </ul>
 * 这三件事在屏幕上看起来毫不相干，其实是同一个状态。
 *
 * <h2>看什么</h2>
 * <pre>
 *   ticks 很小但时间过了很久  → 实体根本没被 tick（区块没加载 / 被节流）
 *   ticks 很大 + trail &lt; 96   → 在 tick，但位置没变化（位移没生效）
 *   ticks 很大 + trail ≥ 96   → 数据都对，问题在渲染
 *   action 停在 EMERGE        → 就是上面说的"卡在登场"
 * </pre>
 *
 * <p>只读，不改任何状态；需要 OP（权限等级 2）。
 */
public final class ErebosServerDebugCommand {

    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lrsdebug")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("erebos")
                        .executes(ctx -> report(ctx.getSource()))));
    }

    private static int report(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        var origin = src.getPosition();
        List<ErebosBoss> found = level.getEntitiesOfClass(ErebosBoss.class,
                new AABB(origin, origin).inflate(512.0D));

        reply(src, ChatFormatting.DARK_PURPLE, "===== 乌蠹诺斯 · 服务端状态 =====");
        if (found.isEmpty()) {
            reply(src, ChatFormatting.RED, "这个维度里没有本体。");
            reply(src, ChatFormatting.GRAY, "→ 若客户端也没有，说明它从未生成或被移除了。");
            return 0;
        }

        for (ErebosBoss boss : found) {
            int ticks = boss.brain().ticks();
            String action = boss.brain().action().name();
            int trailPoints = boss.trail().size();
            double trailLen = boss.trail().length();
            double[] seg = boss.segments();
            int n = boss.segmentCount();

            reply(src, ChatFormatting.GRAY, String.format(
                    "位置 (%.1f, %.1f, %.1f)", boss.getX(), boss.getY(), boss.getZ()));
            reply(src, ChatFormatting.GRAY, String.format(
                    "状态 %s  已持续 %d tick  血量 %.0f", action, ticks, boss.getHealth()));
            reply(src, ChatFormatting.GRAY, String.format(
                    "轨迹 %d 点 / %.1f 格  (铺满需要 %.0f 格)", trailPoints, trailLen,
                    com.lost_realm.boss.erebos.ErebosTuning.EMERGE_ARC));
            reply(src, ChatFormatting.GRAY, String.format(
                    "第0节 (%.1f, %.1f, %.1f)", seg[0], seg[1], seg[2]));
            reply(src, ChatFormatting.GRAY, String.format(
                    "第%d节 (%.1f, %.1f, %.1f)", n / 2,
                    seg[(n / 2) * 3], seg[(n / 2) * 3 + 1], seg[(n / 2) * 3 + 2]));
            reply(src, ChatFormatting.GRAY, String.format(
                    "第%d节 (%.1f, %.1f, %.1f)", n - 1,
                    seg[(n - 1) * 3], seg[(n - 1) * 3 + 1], seg[(n - 1) * 3 + 2]));

            // ---- 招式池自检（不依赖玩家）----
            reply(src, ChatFormatting.DARK_PURPLE, "----- 招式池 -----");
            var phase = boss.phase();
            var pool = boss.brain().poolView();
            reply(src, ChatFormatting.GRAY, String.format(
                    "当前阶段 %s  池内 %d 招", phase, pool.size()));
            int usable = 0;
            StringBuilder sb = new StringBuilder();
            for (var mv : pool) {
                boolean ok = mv.availableIn(phase);
                if (ok) usable++;
                sb.append(ok ? "§a" : "§8").append(mv.id()).append(ok ? " " : " ");
            }
            reply(src, ChatFormatting.GRAY, String.format(
                    "本阶段可用 %d 招：", usable));
            reply(src, ChatFormatting.GRAY, sb.toString());

            // ---- 自动判读 ----
            reply(src, ChatFormatting.DARK_PURPLE, "----- 判读 -----");
            if (ticks < 20) {
                reply(src, ChatFormatting.RED,
                        "状态机几乎没推进 → 实体没有被正常 tick（区块未加载，或被别的模组节流）。");
            } else if (trailLen < com.lost_realm.boss.erebos.ErebosTuning.EMERGE_ARC * 0.9D) {
                reply(src, ChatFormatting.RED, String.format(
                        "在 tick 但轨迹只有 %.1f 格 → 位置位移没有生效。", trailLen));
                reply(src, ChatFormatting.GRAY,
                        "→ 这正是卡在 EMERGE 的原因：身体铺不满，状态机永远出不来。");
            } else {
                reply(src, ChatFormatting.GREEN, "轨迹已铺满，路径数据正常。");
            }
            if ("EMERGE".equals(action) && ticks > 400) {
                reply(src, ChatFormatting.RED,
                        "已超过 400 tick 仍停在 EMERGE —— 打不出伤害就是它（登场期间无敌）。");
            }
        }
        return 1;
    }

    private static void reply(CommandSourceStack src, ChatFormatting color, String text) {
        src.sendSuccess(() -> Component.literal(text).withStyle(color), false);
    }

    private ErebosServerDebugCommand() {}
}
