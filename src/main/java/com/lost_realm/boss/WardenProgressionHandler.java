package com.lost_realm.boss;

import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.lost_realm.Medals;
import com.lost_realm.skill.MedalType;

/**
 * 任务链的<b>代码授予器</b> —— 补足无法用数据包触发器表达的成就条件。
 *
 * <h2>为什么有些成就要用 impossible 触发器 + 代码授予</h2>
 * Minecraft 的 advancement 触发器(data pack trigger)覆盖的是"原版事件":
 * 拿到物品、击杀实体、切换维度、附魔等等。但本模组的任务链里有条件
 * 原版<b>根本没有对应触发器</b>:
 * <ul>
 *   <li><b>共鸣</b>:四枚勋章各达 20 级 —— 原版不知道"勋章等级"是什么</li>
 * </ul>
 * 因此这个成就写成 {@code minecraft:impossible} 触发器(数据包里永不自动满足),
 * 由本类在合适的时机用 {@link ServerPlayer#getAdvancements()} 手动授予。
 *
 * <p>dv5 起「召唤」成就已随召唤符一并删除 ——
 * 遗落之主不再需要召唤物,玩家进维度即可见到他。
 *
 * <h2>为什么用 TickEvent 而不是自建事件</h2>
 * 「共鸣」属于"持续状态满足即授予"的类型(等级达标),
 * 天然是<b>轮询</b>语义,而非"某个事件发生"。
 * 用每 {@value #CHECK_INTERVAL} tick 一次的节流轮询,既简单又不会漏判;
 * 若用事件驱动,反而要在升级处插钩子,且容易漏掉
 * "重登后条件已满足但从未发生变化"这种情况。
 *
 * <h2>性能</h2>
 * 每 {@value #CHECK_INTERVAL} tick 才跑一次,且只遍历在线玩家;
 * 绝大多数玩家一次判定就在第一个 if 里返回了,开销可忽略。
 */
@Mod.EventBusSubscriber(modid = Medals.MODID)
public final class WardenProgressionHandler {

    /** 轮询间隔(tick)。1 秒一次足以让成就"感觉上是即时的"。 */
    private static final int CHECK_INTERVAL = 20;

    // 成就 id(与 data/lost_realm/advancements/*.json 一一对应)
    private static final ResourceLocation ADV_RESONANCE =
            new ResourceLocation(Medals.MODID, "resonance");

    private static int tickCounter;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // 只在服务端每 20 tick 跑一次(PlayerTickEvent 每个玩家每 tick 都会触发)
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (++tickCounter % CHECK_INTERVAL != 0) {
            return;
        }
        tickCounter = 0;

        try {
            checkResonance(player);
        } catch (Throwable t) {
            // 成就授予失败绝不能影响玩家正常游戏
            Medals.LOGGER.debug("[Lost Realm] 任务链判定异常", t);
        }
    }

    // ================================================================
    // 共鸣 —— 四勋章各达 20 级
    // ================================================================

    /**
     * 检查并授予「共鸣」成就。
     *
     * <p>条件:四枚勋章<b>全部</b>达到 {@link BossProgressionHooks#REQUIRED_MEDAL_LEVEL} 级。
     * 这是任务链从"打怪攒材料"切换到"打磨自身练度"的转折点 ——
     * 它逼迫玩家真正去练四条线的熟练度,而不是只玩一个职业。
     */
    private static void checkResonance(ServerPlayer player) {
        if (!BossProgressionHooks.meetsMedalRequirement(player)) {
            return;
        }
        award(player, ADV_RESONANCE,
                "advancement.lost_realm.resonance.title",
                "message.lost_realm.resonance.granted");
    }

    // ================================================================
    // 通用授予
    // ================================================================

    /**
     * 授予成就;若此前未获得,则额外发送一条提示。
     *
     * <p>{@code Advancement#getCriteria()} 的键在 JSON 里统一为 {@code "granted"},
     * 但为了让 {@code impossible} 之外的成就也能复用本方法,这里做一次
     * "取第一个条件键"的兜底。
     */
    private static void award(ServerPlayer player, ResourceLocation id,
                              String titleKey, String grantMessageKey) {
        Advancement adv = player.server.getAdvancements().getAdvancement(id);
        if (adv == null) {
            return;     // 数据包未加载(理论上不会发生)
        }
        var progress = player.getAdvancements().getOrStartProgress(adv);
        if (progress.isDone()) {
            return;     // 已获得,不重复提示
        }
        // getCriteria() 返回 Map<String, Criterion> —— 取第一个键即可
        // (两个 JSON 里都只有一个条件,键名分别是 granted / has_*)
        String criterion = adv.getCriteria().keySet().stream().findFirst().orElse("granted");
        player.getAdvancements().award(adv, criterion);

        // 成就弹窗由原版负责;这里补一条聊天提示,让玩家知道"进度推进了"
        player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable(grantMessageKey), false);
    }

    private WardenProgressionHandler() {}
}
