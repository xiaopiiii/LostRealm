package com.lost_realm.command;

import com.lost_realm.Medals;
import com.lost_realm.data.MedalNbt;
import com.lost_realm.data.MedalSource;
import com.lost_realm.data.PlayerMedals;
import com.lost_realm.item.ItemMedal;
import com.lost_realm.item.MedalRuntime;
import com.lost_realm.skill.MedalType;
import com.lost_realm.util.ExpTable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 勋章调试指令。
 *
 * <pre>
 *   /lostrealm medal xp &lt;点数&gt;     给<b>佩戴中</b>的勋章加熟练度
 *   /lostrealm medal level &lt;等级&gt;  把佩戴中的勋章直接设成 N 级
 * </pre>
 *
 * <h2>为什么只作用于「佩戴中」的勋章</h2>
 * 熟练度存在勋章物品自身的 NBT 里（见 {@link MedalNbt}），一枚躺在箱子里的勋章
 * 和一枚戴在身上的勋章在数据上没有区别 —— 指令必须明确「改哪一枚」。
 * 按玩家佩戴的那枚来改，符合直觉，也避免误改到背包里囤着的其它勋章。
 *
 * <h2>为什么改完要立刻刷新属性</h2>
 * 勋章的基础属性是按等级计算后施加到玩家身上的。等级变了却只改 NBT，
 * 玩家要等到下一次 {@code curioTick} 才会看到数值变化 —— 中间这段时间
 * tooltip 写的和实际生效的对不上。所以两条指令都在改完后立即调
 * {@link MedalRuntime#refresh}。
 */
@Mod.EventBusSubscriber(modid = Medals.MODID)
public final class MedalCommands {

    /** 权限等级 2 = 普通 OP（与 /give、/effect 同级）。 */
    private static final int PERMISSION = 2;

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("lostrealm")
                .then(Commands.literal("medal")
                        .requires(src -> src.hasPermission(PERMISSION))
                        .then(Commands.literal("xp")
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(1.0D))
                                        .executes(ctx -> addExp(ctx,
                                                DoubleArgumentType.getDouble(ctx, "amount")))))
                        .then(Commands.literal("level")
                                .then(Commands.argument("level",
                                                IntegerArgumentType.integer(1, ExpTable.maxLevel()))
                                        .executes(ctx -> setLevel(ctx,
                                                IntegerArgumentType.getInteger(ctx, "level")))))
                        // 无玩家也能跑的自检：等级上限 / 枷锁 / 技能节点
                        .then(Commands.literal("info")
                                .executes(MedalCommands::info))
                        // OP 直接设置枷锁阶段（0/1/2），方便测试三章流程
                        .then(Commands.literal("unlock")
                                .then(Commands.argument("stage",
                                                IntegerArgumentType.integer(0, 2))
                                        .executes(ctx -> unlock(ctx,
                                                IntegerArgumentType.getInteger(ctx, "stage"))))))
                // 世界难度:/lostrealm difficulty [1-5]
                .then(DifficultyCommand.node()));
    }

    /** {@code /lostrealm medal info} —— 打印等级上限、枷锁与技能节点。 */
    private static int info(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        int max = ExpTable.maxLevel();

        StringBuilder sb = new StringBuilder("技能节点: ");
        for (com.lost_realm.skill.MedalType type : com.lost_realm.skill.MedalType.values()) {
            sb.append(type.id()).append("[");
            var skills = com.lost_realm.skill.SkillRegistry.of(type);
            for (int i = 0; i < skills.size(); i++) {
                if (i > 0) {
                    sb.append(' ');
                }
                sb.append(skills.get(i).unlockLevel());
            }
            sb.append("] ");
        }

        src.sendSuccess(() -> Component.literal("===== 勋章进度 =====")
                .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), false);
        src.sendSuccess(() -> Component.literal("等级上限 " + max), false);
        src.sendSuccess(() -> Component.literal(
                "枷锁 " + com.lost_realm.data.MedalProgression.SHACKLE_LEVELS[0]
                        + " / " + com.lost_realm.data.MedalProgression.SHACKLE_LEVELS[1]
                        + "（三章：1–20 / 21–40 / 41–" + max + "）"), false);
        src.sendSuccess(() -> Component.literal(String.format(
                "累计经验：Lv.20 = %,d   Lv.40 = %,d   Lv.60 = %,d",
                ExpTable.totalExpForLevel(20),
                ExpTable.totalExpForLevel(40),
                ExpTable.totalExpForLevel(60))), false);
        src.sendSuccess(() -> Component.literal(sb.toString())
                .withStyle(net.minecraft.ChatFormatting.GRAY), false);
        return 1;
    }

    /** {@code /lostrealm medal unlock <0|1|2>} —— 直接设置枷锁阶段。 */
    private static int unlock(CommandContext<CommandSourceStack> ctx, int stage)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        com.lost_realm.data.PlayerMedals.of(player).setShackleStage(stage);
        int cap = com.lost_realm.data.PlayerMedals.of(player).levelCap();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "枷锁阶段已设为 " + stage + "，上限 " + cap + " 级。"), true);
        return 1;
    }

    /** {@code /lostrealm medal xp <点数>} —— 增加熟练度（可能连带升级）。 */
    private static int addExp(CommandContext<CommandSourceStack> ctx, double amount)
            throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();

        ItemStack medal = worn(src, player);
        if (medal.isEmpty()) {
            return 0;
        }
        MedalType type = ((ItemMedal) medal.getItem()).medalType();
        MedalNbt nbt = MedalNbt.of(medal);

        int before = nbt.getLevel(type);
        // addExpRaw:调试指令给多少就是多少,不吃世界难度倍率(见 MedalSource#addExpRaw)。
        // 内部在升级时会顺手刷新属性(见 MedalSource#addExp)
        MedalSource.addExpRaw(player, type, amount);
        int after = nbt.getLevel(type);

        Component name = medal.getHoverName();
        src.sendSuccess(() -> Component.translatable("commands.lost_realm.medal.xp",
                name, number(amount), progressText(nbt, type, after), after), false);
        if (after > before) {
            src.sendSuccess(() -> Component.translatable("commands.lost_realm.medal.level_up",
                    name, after), false);
        }
        return 1;
    }

    /** {@code /lostrealm medal level <等级>} —— 直接设置等级（经验对齐到该级起点）。 */
    private static int setLevel(CommandContext<CommandSourceStack> ctx, int level)
            throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();

        ItemStack medal = worn(src, player);
        if (medal.isEmpty()) {
            return 0;
        }
        MedalType type = ((ItemMedal) medal.getItem()).medalType();
        MedalNbt nbt = MedalNbt.of(medal);

        nbt.setLevel(type, level);
        int actual = nbt.getLevel(type);
        // 等级换了就得立刻重算属性，否则要等到下一次 curioTick 才生效
        MedalRuntime.refresh(player, type, PlayerMedals.of(player), medal);

        Component name = medal.getHoverName();
        src.sendSuccess(() -> Component.translatable("commands.lost_realm.medal.level_set",
                name, actual, progressText(nbt, type, actual)), false);
        return 1;
    }

    /** 取玩家佩戴的勋章；没有则发一条失败消息并返回 {@link ItemStack#EMPTY}。 */
    private static ItemStack worn(CommandSourceStack src, ServerPlayer player) {
        ItemStack medal = MedalSource.anyWorn(player);
        if (medal.isEmpty() || !(medal.getItem() instanceof ItemMedal)) {
            src.sendFailure(Component.translatable("commands.lost_realm.medal.no_medal"));
            return ItemStack.EMPTY;
        }
        return medal;
    }

    /** 本级进度文本：{@code 50/100}，满级为 {@code MAX}。 */
    private static String progressText(MedalNbt nbt, MedalType type, int level) {
        long need = ExpTable.expToNext(level);
        if (level >= ExpTable.maxLevel() || need == Long.MAX_VALUE) {
            return "MAX";
        }
        return (nbt.getExp(type) - ExpTable.totalExpForLevel(level)) + "/" + need;
    }

    /** 整数不带小数点（100 而不是 100.0）。 */
    private static String number(double v) {
        return Math.abs(v - Math.rint(v)) < 1.0E-6D
                ? String.valueOf((long) Math.rint(v))
                : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    private MedalCommands() {}
}
