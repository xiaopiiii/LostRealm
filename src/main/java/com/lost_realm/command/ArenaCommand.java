package com.lost_realm.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import com.lost_realm.boss.BossRegistry;
import com.lost_realm.boss.ErebosArenaBuilder;
import com.lost_realm.boss.block.LostAltarBlock;
import com.lost_realm.boss.block.LostAltarBlockEntity;

/**
 * 竞技场运维命令 —— {@code /lr arena rebuild} 与 {@code /lr arena tp}。
 *
 * <h2>为什么需要 rebuild</h2>
 * 竞技场只在「哨兵不存在」时自动建造。开发期反复改布局时，
 * 玩家不可能每次都重开存档，所以给一条强制重建：
 * 删掉哨兵 → 调用同一个建造入口，走的是<b>生产环境同一条代码路径</b>，
 * 不存在"命令修好了、自动生成还是坏的"这种偏差。
 *
 * <p>重建会清空半径 128 内、地面以上 120 格的全部方块 ——
 * 这是玩家自己放的建筑也会被清掉的操作，所以命令要 OP，且输出耗时。
 */
public final class ArenaCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lr")
                .then(Commands.literal("arena")
                        .then(Commands.literal("rebuild")
                                .requires(src -> src.hasPermission(2))
                                .executes(ArenaCommand::rebuild))
                        .then(Commands.literal("tp")
                                .requires(src -> src.hasPermission(2))
                                .executes(ArenaCommand::teleport))
                        .then(Commands.literal("ritual")
                                .requires(src -> src.hasPermission(2))
                                .executes(ArenaCommand::ritual))
                        .then(Commands.literal("info")
                                .requires(src -> src.hasPermission(2))
                                .executes(ArenaCommand::info))));
    }

    private static int rebuild(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getServer().getLevel(BossRegistry.REALM_LEVEL);
        if (level == null) {
            src.sendFailure(Component.literal("遗落境维度未加载 —— 先进一次维度再执行。"));
            return 0;
        }

        BlockPos sentinel = new BlockPos(BossRegistry.ARENA_CENTER_X,
                BossRegistry.ARENA_CENTER_Y, BossRegistry.ARENA_CENTER_Z).below(4);
        level.setBlock(sentinel, Blocks.AIR.defaultBlockState(), 2);

        src.sendSuccess(() -> Component.literal("正在重建竞技场，首次会卡 20~60 秒…")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);

        long t0 = System.currentTimeMillis();
        boolean built = ErebosArenaBuilder.ensureGenerated(level);
        long ms = System.currentTimeMillis() - t0;

        if (built) {
            src.sendSuccess(() -> Component.literal("竞技场重建完成，用时 " + ms + " ms。")
                    .withStyle(ChatFormatting.GREEN), true);
        } else {
            src.sendFailure(Component.literal("重建失败：哨兵已存在（未触发建造）。"));
        }
        return built ? 1 : 0;
    }

    private static int teleport(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("只有玩家能执行这条命令。"));
            return 0;
        }
        ServerLevel level = src.getServer().getLevel(BossRegistry.REALM_LEVEL);
        if (level == null) {
            src.sendFailure(Component.literal("遗落境维度未加载。"));
            return 0;
        }
        BlockPos spot = ErebosArenaBuilder.entrySpot();
        player.teleportTo(level, spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, 180.0F, 0.0F);
        src.sendSuccess(() -> Component.literal("已传送至竞技场入口。")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    /**
     * 直接点燃中央祭坛的仪式 —— 给测试用，不跑过去右键。
     *
     * <p>走的是和右键完全相同的 {@code startRitual}，不会出现
     * 「命令能召、右键不能召」这种偏差；玩家测试时仍然可以走过去右键。
     */
    private static int ritual(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getServer().getLevel(BossRegistry.REALM_LEVEL);
        if (level == null) {
            src.sendFailure(Component.literal("遗落境维度未加载。"));
            return 0;
        }

        BlockPos core = ErebosArenaBuilder.coreAltarPos();
        if (!(level.getBlockEntity(core) instanceof LostAltarBlockEntity altar)) {
            src.sendFailure(Component.literal("中央祭坛不存在 —— 先执行 /lr arena rebuild。"));
            return 0;
        }
        if (altar.isRitualRunning()) {
            src.sendFailure(Component.literal("仪式已经在进行中。"));
            return 0;
        }
        if (altar.bossAlreadyPresent(level)) {
            src.sendFailure(Component.literal("遗落之主已经在此地了。"));
            return 0;
        }

        altar.startRitual(src.getPlayer());
        level.setBlock(core, level.getBlockState(core).setValue(LostAltarBlock.ACTIVE, true), 3);
        src.sendSuccess(() -> Component.literal("仪式已点燃，20 秒后降临。")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return 1;
    }

    /**
     * 竞技场自检：直接读方块，不依赖任何命令语法猜测。
     *
     * <p>开发期用 {@code /execute if block} 验证结构时踩过坑 ——
     * 方块 id 与后续参数在某些命令上下文中解析不干净，结果全是假阴性。
     * 这条命令把关键位置的真实方块 id 打出来，一次看全。
     */
    private static int info(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getServer().getLevel(BossRegistry.REALM_LEVEL);
        if (level == null) {
            src.sendFailure(Component.literal("遗落境维度未加载。"));
            return 0;
        }

        BlockPos center = new BlockPos(BossRegistry.ARENA_CENTER_X,
                BossRegistry.ARENA_CENTER_Y, BossRegistry.ARENA_CENTER_Z);
        BlockPos core = ErebosArenaBuilder.coreAltarPos();

        src.sendSuccess(() -> Component.literal("===== 终末竞技场 =====")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        src.sendSuccess(() -> Component.literal(String.format(
                "中心 %s   地表 %s   中心柱 %s",
                center, id(level, center), id(level, center.above()))), false);
        src.sendSuccess(() -> Component.literal(String.format(
                "核心祭坛 %s = %s   方块实体=%s",
                core, id(level, core),
                level.getBlockEntity(core) != null ? "有" : "无")), false);

        int altars = 0;
        for (int i = 0; i < BossRegistry.PILLAR_COUNT; i++) {
            BlockPos p = ErebosArenaBuilder.pillarAltarPos(i);
            if (p != null && level.getBlockState(p).is(com.lost_realm.boss.BossBlocks.LOST_ALTAR.get())) {
                altars++;
            }
        }
        final int a = altars;
        src.sendSuccess(() -> Component.literal("柱顶祭坛 " + a + "/" + BossRegistry.PILLAR_COUNT)
                .withStyle(a == BossRegistry.PILLAR_COUNT ? ChatFormatting.GREEN : ChatFormatting.RED), false);

        int frames = level.getEntitiesOfClass(
                net.minecraft.world.entity.decoration.ItemFrame.class,
                new net.minecraft.world.phys.AABB(center).inflate(160.0D)).size();
        src.sendSuccess(() -> Component.literal("柱顶图腾展示框 " + frames + " 个"), false);

        // 半径采样：确认地面真的铺到了 125
        StringBuilder edge = new StringBuilder("地板采样: ");
        for (int d : new int[]{0, 40, 80, 120, 124, 125, 126}) {
            edge.append(d).append('=').append(id(level, center.offset(d, 0, 0))).append("  ");
        }
        src.sendSuccess(() -> Component.literal(edge.toString()).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static String id(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        var key = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return key == null ? "?" : key.toString();
    }

    private ArenaCommand() {}
}
