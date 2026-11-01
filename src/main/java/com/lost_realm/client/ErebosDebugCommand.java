package com.lost_realm.client;

import java.util.List;

import com.lost_realm.boss.erebos.ErebosBoss;
import com.lost_realm.client.boss.ErebosRenderer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;

/**
 * 客户端自检命令 —— {@code /lrdebug erebos}。
 *
 * <h2>为什么需要它</h2>
 * 厄瑞玻斯有一条"身体 40 节由头部路径反推"的渲染链，出问题时屏幕上的表现
 * 全都一样（"只有一个头"／"整个不见"），但根因可能完全不同：
 * <ul>
 *   <li>客户端实体根本没创建</li>
 *   <li>创建了，但位置从没同步过（轨迹只有 1 个点）</li>
 *   <li>轨迹正常，但渲染器被剔除／没被调用</li>
 * </ul>
 * 这三种情况的修法毫无交集。<b>日志能分辨，但翻日志很麻烦</b> ——
 * 尤其手机端，找到 {@code latest.log} 再翻到那几行本身就是一道门槛。
 *
 * <p>所以这里做一条只在客户端存在的命令：把上述关键状态直接打到聊天栏，
 * 截图就能看清。它<b>不改变任何游戏状态</b>，纯读取。
 *
 * <h2>怎么看结果</h2>
 * <pre>
 *   轨迹点数 ≤ 2        → 客户端位置从没更新过（同步问题）
 *   轨迹长度 ≈ 96 格    → 路径数据是好的
 *   渲染器"从未"        → 渲染被剔除，或压根没进渲染队列
 *   渲染器"0.0x 秒前"   → 渲染在跑，问题在摆放/坐标
 * </pre>
 *
 * <p>命令只在客户端注册（{@link RegisterClientCommandsEvent}），
 * 服务端不会看到它，也不会因为缺少客户端类而崩。
 */
public final class ErebosDebugCommand {

    /** 玩家周围多少格内找本体（客户端只持有已加载实体，给大一点无妨）。 */
    private static final double SEARCH_RADIUS = 256.0D;

    /** 由 {@link MedalsClient#init()} 挂到 FORGE 总线。 */
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("lrdebug")
                        .then(Commands.literal("erebos")
                                .executes(ctx -> report())));
    }

    private static int report() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return 0;
        }

        List<ErebosBoss> found = mc.level.getEntitiesOfClass(
                ErebosBoss.class, mc.player.getBoundingBox().inflate(SEARCH_RADIUS));

        send("§5===== 厄瑞玻斯 · 客户端自检 =====");
        send(String.format("§7维度 §f%s §7@ §f(%.1f, %.1f, %.1f)",
                mc.level.dimension().location(),
                mc.player.getX(), mc.player.getY(), mc.player.getZ()));

        if (found.isEmpty()) {
            send("§c客户端世界里没有这个实体。");

            // ---- 列出附近的实体：用来区分"整个实体组都没同步"和"只有本体丢了" ----
            // 若连 erebos_part 都没有，说明服务端压根没把它纳入追踪；
            // 若部件有一堆、本体却不在，那就是本体那条包丢了。
            List<net.minecraft.world.entity.Entity> near = mc.level.getEntities(
                    mc.player, mc.player.getBoundingBox().inflate(64.0D));
            int parts = 0;
            int shown = 0;
            StringBuilder list = new StringBuilder();
            for (net.minecraft.world.entity.Entity e : near) {
                if (e instanceof com.lost_realm.boss.erebos.ErebosPart) {
                    parts++;
                    continue;
                }
                if (shown++ < 8) {
                    list.append("\n§8  - §f")
                            .append(net.minecraft.world.entity.EntityType.getKey(e.getType()))
                            .append(String.format(" §7%.1f 格", Math.sqrt(e.distanceToSqr(mc.player))));
                }
            }
            send("§7附近 64 格内的部件实体：§f" + parts + " §7个");
            if (shown == 0 && parts == 0) {
                send("§7附近一个实体也没有。");
            } else if (shown > 0) {
                send("§7其它实体：" + list);
            }
            send("§e→ 请对照服务端跑一次 §f/data get entity @e[type=lost_realm:erebos,limit=1]");
            send("§e  两边都空 = 服务端已经把它移除了；只有客户端空 = 生成包没送到。");
            return 0;
        }

        for (ErebosBoss boss : found) {
            int trailPoints = boss.trail().size();
            double trailLen = boss.trail().length();
            double[] seg = boss.segments();
            int n = boss.segmentCount();

            send(String.format("§7位置 §f(%.1f, %.1f, %.1f)  §7距你 §f%.1f 格",
                    boss.getX(), boss.getY(), boss.getZ(),
                    Math.sqrt(boss.distanceToSqr(mc.player))));
            send(String.format("§7血量 §f%.1f§7/§f%.1f   §7移除 §f%s",
                    boss.getHealth(), boss.getMaxHealth(),
                    boss.isRemoved() ? ("是(" + boss.getRemovalReason() + ")") : "否"));
            send(String.format("§7轨迹 §f%d 点 §7/ §f%.1f 格", trailPoints, trailLen));

            // 剔除盒尺寸 —— 视锥测试对的就是它。
            // 正常应覆盖整条虫（约 144 × 10 × 144）；若只有几点大，
            // 就会出现"要站到特定角度才看得见、一转头全没了"。
            net.minecraft.world.phys.AABB cull = boss.getBoundingBoxForCulling();
            send(String.format("§7剔除盒 §f%.1f × %.1f × %.1f §7格   §7脊线 §f%s",
                    cull.getXsize(), cull.getYsize(), cull.getZsize(),
                    boss.hasFreshSpine() ? "有效" : "无/过期"));

            // 头、中、尾三节 —— 正常铺开时三者应相距数十格
            send(String.format("§7第0节 §f(%.1f, %.1f, %.1f)", seg[0], seg[1], seg[2]));
            send(String.format("§7第%d节 §f(%.1f, %.1f, %.1f)", n / 2,
                    seg[(n / 2) * 3], seg[(n / 2) * 3 + 1], seg[(n / 2) * 3 + 2]));
            send(String.format("§7第%d节 §f(%.1f, %.1f, %.1f)", n - 1,
                    seg[(n - 1) * 3], seg[(n - 1) * 3 + 1], seg[(n - 1) * 3 + 2]));

            long last = ErebosRenderer.LAST_RENDER_NANOS;
            if (last == 0L) {
                send("§c渲染器：从未被调用");
            } else {
                double secs = (System.nanoTime() - last) / 1.0E9D;
                send(String.format("§7渲染器：§f%.2f 秒前 §7刚画过", secs));
            }

            // ---- 自动结论 ----
            send("§5----- 判读 -----");
            // ① 先看剔除盒 —— 这是"要特定角度才看得见"的唯一嫌疑。
            double cullSpan = Math.max(cull.getXsize(), cull.getZsize());
            if (cullSpan < 20.0D) {
                send(String.format("§c剔除盒只有 %.1f 格 → 视锥测试对的是一个小点，", cullSpan));
                send("§c 头那个点一出画面，整条虫就被整体剔除。");
                send(String.format("§7  正常应 ≈ %.0f 格（覆盖整条身体）。",
                        com.lost_realm.boss.erebos.ErebosTuning.EMERGE_ARC));
            } else {
                send(String.format("§a剔除盒 %.1f 格，覆盖整条虫，视锥剔除正常。", cullSpan));
            }
            // ② 再看身体是否铺开：用头尾两节的直线距离，比 trail 点数更直接
            double span = Math.sqrt(
                    sq(seg[0] - seg[(n - 1) * 3])
                            + sq(seg[1] - seg[(n - 1) * 3 + 1])
                            + sq(seg[2] - seg[(n - 1) * 3 + 2]));
            if (span < 20.0D) {
                send(String.format("§c头尾只相距 %.1f 格 → 40 节塌缩在一起。", span));
                send("§7  这就是「只有一个头」的来源；看上面的脊线是否「有效」。");
            } else {
                send(String.format("§a头尾相距 %.1f 格，身体已铺开。", span));
            }
            // ③ 渲染器是否还在被调用
            if (last != 0L && (System.nanoTime() - last) / 1.0E9D < 1.0D) {
                send("§a渲染器在正常被调用。");
            } else if (last != 0L) {
                send("§c渲染器很久没被调用 → 被视锥剔除了（或不在渲染队列里）。");
            }
        }
        return 1;
    }

    private static double sq(double v) {
        return v * v;
    }

    private static void send(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(text), false);
        }
    }

    private ErebosDebugCommand() {}
}
