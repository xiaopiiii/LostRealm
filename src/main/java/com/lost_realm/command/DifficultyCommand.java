package com.lost_realm.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import com.lost_realm.difficulty.DifficultyLevel;
import com.lost_realm.difficulty.DifficultyManager;

/**
 * 世界难度指令。
 *
 * <pre>
 *   /lr difficulty            查看当前难度与五档一览(任何玩家可用)
 *   /lr difficulty &lt;1-5&gt;      直接切档(OP)
 *   /lr difficulty set &lt;1-5&gt;  同上,写全一点(OP)
 * </pre>
 *
 * <h2>为什么难度不是配置项</h2>
 * 它是<b>这个世界正在发生的事</b>,不是玩家客户端偏好:同一个存档里
 * 所有玩家必须处在同一档,切换要立刻作用到已经在场的怪物上,
 * 而且换存档不能串味。这三点都是「存档数据 + 指令」的语义,
 * 放进 {@code .toml} 只会得到「改了配置但没重载」这种问题。
 *
 * <p>本类只负责解析与回显:真正的写盘与重算见
 * {@link DifficultyManager#set(net.minecraft.server.MinecraftServer, DifficultyLevel)}。
 */
public final class DifficultyCommand {

    /** 权限等级 2 = 普通 OP(与 /give、/effect 同级)。 */
    private static final int PERMISSION = 2;

    /**
     * 构建 {@code difficulty} 子命令树。
     *
     * <p>由 {@code MedalCommands}({@code /lostrealm})与 {@code ArenaCommand}
     * ({@code /lr})各自挂到自己的根命令下 —— 同一个根命令<b>只注册一次</b>,
     * 免得依赖 Brigadier 的重复字面量合并行为。
     */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("difficulty")
                // 查看:不设权限,普通玩家也该知道自己被什么难度打
                .executes(ctx -> show(ctx, 0))
                .then(Commands.literal("set")
                        .requires(src -> src.hasPermission(PERMISSION))
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
                                .executes(ctx -> apply(ctx,
                                        IntegerArgumentType.getInteger(ctx, "level")))))
                .then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
                        .requires(src -> src.hasPermission(PERMISSION))
                        .executes(ctx -> apply(ctx,
                                IntegerArgumentType.getInteger(ctx, "level"))));
    }

    /** 切换难度并回显前后变化。 */
    private static int apply(CommandContext<CommandSourceStack> ctx, int id) {
        DifficultyLevel next = DifficultyLevel.clamp(id);
        DifficultyLevel previous = DifficultyManager.set(
                ctx.getSource().getServer(), next);

        ctx.getSource().sendSuccess(() -> Component.literal("[遗落境] 难度 ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(previous.label()).withStyle(previous.color()))
                .append(Component.literal(" → ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(next.label()).withStyle(next.color())), true);
        // 详细数值单独一行,避免与「切换成功」这句挤在一起看不清
        ctx.getSource().sendSuccess(() -> Component.literal(DifficultyManager.describe(next))
                .withStyle(ChatFormatting.DARK_GRAY), false);
        return next.id();
    }

    /** 打印当前难度 + 五档一览。 */
    private static int show(CommandContext<CommandSourceStack> ctx, int unused) {
        DifficultyLevel current = DifficultyManager.level();
        CommandSourceStack src = ctx.getSource();

        src.sendSuccess(() -> Component.literal("[遗落境] 当前世界难度:")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(current.label()).withStyle(current.color())), false);
        src.sendSuccess(() -> Component.literal(DifficultyManager.describe(current))
                .withStyle(ChatFormatting.DARK_GRAY), false);

        for (DifficultyLevel lv : DifficultyLevel.values()) {
            String arrow = lv == current ? " ▶ " : "   ";
            src.sendSuccess(() -> Component.literal(arrow + lv.id() + ". " + lv.displayName())
                    .withStyle(lv == current ? lv.color() : ChatFormatting.GRAY)
                    .append(Component.literal(DifficultyManager.describe(lv))
                            .withStyle(ChatFormatting.DARK_GRAY)), false);
        }
        src.sendSuccess(() -> Component.literal("切换:/lr difficulty <1-5>(需要 OP)")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        return current.id();
    }

    private DifficultyCommand() {}
}
