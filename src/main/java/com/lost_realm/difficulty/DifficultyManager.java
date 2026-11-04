package com.lost_realm.difficulty;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.lost_realm.Medals;
import com.lost_realm.item.ItemMedal;
import com.lost_realm.skill.MedalType;
import com.lost_realm.util.AttributeGuard;
import com.lost_realm.util.ModifierIds;

import top.theillusivec4.curios.api.CuriosApi;

/**
 * 难度系统运行时 —— 把 {@link DifficultyLevel} 的数值真正落到世界里。
 *
 * <h2>三条落点,互不干扰</h2>
 * <ol>
 *   <li><b>敌对生物</b>({@link Monster}):只在<b>实体进入世界</b>时给它的
 *       最大生命/护甲/攻击挂一组固定 UUID 的倍率修饰符。之后无论难度怎么变,
 *       属性实例本身不会漂移 —— 换难度时<b>重算一次</b>即可
 *       ({@link #reapplyAllMobs}),不需要每 tick 扫描。</li>
 *   <li><b>玩家</b>:攻击/护甲同样是固定 UUID 的倍率修饰符,但玩家会死亡重生、
 *       换维度,属性实例会被重建,所以由 {@code PlayerTickHandler} 每 40 tick
 *       幂等刷新一次(值没变时不重复施加,见 {@link #applyOrRemove})。</li>
 *   <li><b>一次性行为</b>:回血衰减走 {@code LivingHealEvent},中立生物主动
 *       敌对走 {@code LivingTickEvent},熟练度倍率走 {@code MedalSource#addExp}。
 *       这三处都没有「属性实例」可挂,只能挂在事件上。</li>
 * </ol>
 *
 * <h2>为什么怪物用 MULTIPLY_TOTAL</h2>
 * {@code MULTIPLY_TOTAL} 是「在最终数值上再乘一次」,玩家在怪物血条/伤害上
 * 看到的正好是描述里的 +60% / +50%。换成 {@code MULTIPLY_BASE} 在叠加了
 * 其它模组的加成后就不再是 1.6 倍了。全档统一用同一套运算,数值才可预期。
 *
 * <h2>排除项</h2>
 * <ul>
 *   <li>{@code ErebosPart}(Boss 的碰撞部件)不是 {@link LivingEntity},天然不受影响;</li>
 *   <li>玩家自己的宠物/仆从({@link OwnableEntity} 且主人是玩家)不会被中立敌对的
 *       规则反咬 —— 否则「深渊」难度下召唤师的仆从会当场倒戈;</li>
 *   <li>创造/旁观模式玩家不会被中立生物追。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Medals.MODID)
public final class DifficultyManager {

    /** 中立生物「发现」玩家的半径。故意做小:只在贴脸时才翻脸,不是全世界追杀。 */
    private static final double NEUTRAL_AGGRO_RANGE = 16.0D;

    /** 单次激怒后保持敌对的时长(tick),200~400 秒之间随机,避免整齐划一。 */
    private static final int ANGER_TICKS_MIN = 4000;
    private static final int ANGER_TICKS_MAX = 8000;

    /** 当前难度缓存(进程内)。权威值在 {@link DifficultyState} 里。 */
    private static volatile DifficultyLevel current = DifficultyLevel.NORMAL;

    // ================================================================
    // 对外取数
    // ================================================================

    /** 当前难度。热路径(每次治疗/每次加经验)都会读它,所以是 volatile 字段而非存档查询。 */
    public static DifficultyLevel level() {
        return current;
    }

    /** 熟练度获取倍率 —— 由 {@code MedalSource#addExp} 统一乘上。 */
    public static double proficiencyMultiplier() {
        return current.proficiency();
    }

    // ================================================================
    // 切换难度
    // ================================================================

    /**
     * 切换难度:写存档 → 重算全部已加载怪物 → 刷新全部在线玩家。
     *
     * @return 切换前的难度(用于提示「从 X 变成 Y」)
     */
    public static DifficultyLevel set(MinecraftServer server, DifficultyLevel next) {
        DifficultyLevel previous = current;
        DifficultyState state = DifficultyState.get(server);
        state.setLevel(next);
        current = next;

        reapplyAllMobs(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            refreshPlayer(player);
        }

        Component msg = Component.literal("[遗落境] 世界难度已切换为 ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(next.label()).withStyle(next.color()))
                .append(Component.literal(describe(next)).withStyle(ChatFormatting.DARK_GRAY));
        server.getPlayerList().broadcastSystemMessage(msg, false);
        return previous;
    }

    /** 把当前难度重新作用到所有已加载的敌对生物上。 */
    private static void reapplyAllMobs(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity living) {
                    applyToMob(living);
                }
            }
        }
    }

    // ================================================================
    // 敌对生物
    // ================================================================

    /**
     * 给一只敌对生物挂上当前难度的倍率修饰符。
     *
     * <p>幂等:数值没变时直接返回({@link #applyOrRemove} 内部判断),
     * 因此「实体进世界」与「切换难度全量重算」可以走同一条路径。
     *
     * <p>血量按<b>比例</b>保留:一只掉到 40% 血的怪,在被重新施加难度后
     * 仍然是 40% 血,而不是被动回满。
     */
    public static void applyToMob(LivingEntity mob) {
        if (mob == null || mob.level().isClientSide) {
            return;
        }
        if (!(mob instanceof Monster)) {
            return;
        }

        DifficultyLevel lv = current;
        float oldMax = mob.getMaxHealth();
        float oldHealth = mob.getHealth();

        applyOrRemove(mob, Attributes.MAX_HEALTH, ModifierIds.DIFFICULTY_MOB_HEALTH,
                "difficulty_mob_health", lv.mobHealth() - 1.0D);
        applyOrRemove(mob, Attributes.ARMOR, ModifierIds.DIFFICULTY_MOB_ARMOR,
                "difficulty_mob_armor", lv.mobArmor() - 1.0D);
        applyOrRemove(mob, Attributes.ATTACK_DAMAGE, ModifierIds.DIFFICULTY_MOB_ATTACK,
                "difficulty_mob_attack", lv.mobAttack() - 1.0D);

        float newMax = mob.getMaxHealth();
        // 血量按比例保留。oldHealth <= 0 时不碰 —— 有些 Boss 在构造期血量还是 0,
        // 一旦按 0/oldMax 的比例去算就会把它当场按死。
        if (oldHealth > 0.01F && newMax > 0.0F && Math.abs(newMax - oldMax) > 0.01F) {
            float ratio = oldMax > 0.01F ? Math.min(1.0F, oldHealth / oldMax) : 1.0F;
            mob.setHealth(Math.max(0.01F, Math.min(newMax, newMax * ratio)));
        }
    }

    // ================================================================
    // 玩家
    // ================================================================

    /** 幂等刷新玩家的难度相关修饰符(攻击/护甲/职业饰品栏)。 */
    public static void refreshPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        DifficultyLevel lv = current;

        applyOrRemove(player, Attributes.ATTACK_DAMAGE, ModifierIds.DIFFICULTY_PLAYER_ATTACK,
                "difficulty_player_attack", lv.playerAttack() - 1.0D);
        applyOrRemove(player, Attributes.ARMOR, ModifierIds.DIFFICULTY_PLAYER_ARMOR,
                "difficulty_player_armor", lv.playerArmor() - 1.0D);

        refreshJobSlot(player, lv);
    }

    /**
     * 「深渊」难度的 +1 职业饰品栏。
     *
     * <p>职业槽(箭/武器/盾牌/聚晶/契约)在数据包里都是 0 格,靠勋章物品上的
     * {@code SlotAttribute} 修饰符撑开(见 {@code ItemMedal#getAttributeModifiers})。
     * 所以这里给<b>当前佩戴那枚勋章对应的槽位</b>再补一个 +1 ——
     * 玩家换成别的职业勋章时,那 +1 也跟着换到新槽位上。
     *
     * <p>没戴勋章就没有职业槽可用,自然也就不给这一格:
     * 空槽位在饰品界面上是白占位置。
     *
     * <h2>为什么走 {@code addTransientSlotModifier} 而不是直接改属性</h2>
     * 槽位的实际格数由 {@code CurioStacksHandler} 自己维护的<b>修饰符表</b>算出
     * ({@code baseSize + ΣADDITION}),不是直接读玩家属性实例。只往
     * {@code AttributeInstance} 上挂修饰符,属性面板会变、界面上却不多出一格。
     * 改完还要手动 {@code update()}:它才负责真正 resize 并通知客户端刷新。
     * 另外加之前必须查一次,同一个 UUID 重复 add 会在这张表里留下两条 ——
     * 那就不是 +1 而是 +2 了。
     */
    private static void refreshJobSlot(ServerPlayer player, DifficultyLevel lv) {
        MedalType worn = lv.extraJobSlots() > 0 ? wornMedal(player) : null;
        String target = worn == null ? null : worn.jobSlot();
        double amount = lv.extraJobSlots();

        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            for (MedalType type : MedalType.values()) {
                String slotId = type.jobSlot();
                handler.getStacksHandler(slotId).ifPresent(stacks -> {
                    AttributeModifier existing =
                            stacks.getModifiers().get(ModifierIds.DIFFICULTY_JOB_SLOT);
                    boolean wanted = slotId.equals(target);

                    if (!wanted) {
                        if (existing != null) {
                            handler.removeSlotModifier(slotId, ModifierIds.DIFFICULTY_JOB_SLOT);
                            stacks.update();
                        }
                        return;
                    }
                    if (existing != null
                            && existing.getOperation() == AttributeModifier.Operation.ADDITION
                            && Math.abs(existing.getAmount() - amount) < 1.0E-4D) {
                        return;
                    }
                    if (existing != null) {
                        handler.removeSlotModifier(slotId, ModifierIds.DIFFICULTY_JOB_SLOT);
                    }
                    handler.addTransientSlotModifier(slotId, ModifierIds.DIFFICULTY_JOB_SLOT,
                            "difficulty_job_slot", amount, AttributeModifier.Operation.ADDITION);
                    stacks.update();
                });
            }
        });
    }

    /** 玩家当前佩戴的勋章类型;没戴返回 {@code null}。 */
    private static MedalType wornMedal(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).map(handler ->
                handler.findFirstCurio(stack -> stack.getItem() instanceof ItemMedal)
                        .map(r -> ((ItemMedal) r.stack().getItem()).medalType())
                        .orElse(null)
        ).orElse(null);
    }

    // ================================================================
    // 修饰符工具
    // ================================================================

    /**
     * 施加「最终倍率」修饰符;{@code multiplier} 为 0(即倍率 1.0)时改为移除。
     *
     * <p>与直接调 {@link AttributeGuard#apply} 的区别:数值与运算都没变时
     * <b>什么都不做</b>。玩家每 40 tick 会走一次刷新,不加这层判断的话
     * 属性每秒被 remove+add 两次,其它模组的属性缓存会被无意义地反复失效。
     */
    private static void applyOrRemove(LivingEntity entity, Attribute attribute, java.util.UUID uuid,
                                      String name, double multiplier) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (Math.abs(multiplier) < 1.0E-4D) {
            if (instance.getModifier(uuid) != null) {
                instance.removeModifier(uuid);
            }
            return;
        }
        AttributeModifier existing = instance.getModifier(uuid);
        if (existing != null
                && existing.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL
                && Math.abs(existing.getAmount() - multiplier) < 1.0E-4D) {
            return;
        }
        AttributeGuard.apply(entity, attribute, uuid, name, multiplier,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    // ================================================================
    // 事件
    // ================================================================

    /** 敌对生物进入世界 → 立刻按当前难度定档。 */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof LivingEntity living) {
            applyToMob(living);
        }
    }

    /** 玩家造成/承受的治疗量衰减(只作用于玩家,怪物治疗不受影响)。 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHeal(LivingHealEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        double multiplier = current.heal();
        if (multiplier >= 0.999D) {
            return;
        }
        if (event.getAmount() <= 0.0F) {
            return;
        }
        event.setAmount((float) (event.getAmount() * multiplier));
    }

    /**
     * 中立生物主动敌对。
     *
     * <p>每只生物每 20 tick 检查一次(而不是每 tick),开销可以忽略:
     * {@code instanceof} 之后才是逻辑,而绝大多数实体第一个判断就被筛掉。
     *
     * <p>只激怒<b>还没有目标</b>的中立生物,不动正在打别人的那些 ——
     * 否则狼群会集体放弃嘴里的羊来追玩家,世界行为会变得很怪。
     */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        DifficultyLevel lv = current;
        if (!lv.neutralAggressive()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        if ((entity.tickCount & 15) != 0) {
            return;
        }
        if (!(entity instanceof NeutralMob neutral) || !(entity instanceof Mob mob)) {
            return;
        }
        // 玩家自己的宠物/仆从:不反咬主人
        if (entity instanceof OwnableEntity ownable && ownable.getOwner() instanceof Player) {
            return;
        }
        if (mob.getTarget() != null && mob.getTarget().isAlive()) {
            return;
        }

        Player nearest = entity.level().getNearestPlayer(entity, NEUTRAL_AGGRO_RANGE);
        if (nearest == null || nearest.isCreative() || nearest.isSpectator()) {
            return;
        }

        mob.setTarget(nearest);
        neutral.setPersistentAngerTarget(nearest.getUUID());
        // 每次重新激怒都把计时器顶满:否则玩家跑远一点,愤怒就自然衰减掉了
        neutral.setRemainingPersistentAngerTime(ANGER_TICKS_MIN
                + entity.getRandom().nextInt(ANGER_TICKS_MAX - ANGER_TICKS_MIN));
    }

    /** 服务器启动:从存档读出难度,并把已加载的生物定档。 */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        current = DifficultyState.get(server).level();
        reapplyAllMobs(server);
        if (current != DifficultyLevel.NORMAL) {
            Medals.LOGGER.info("[Lost Realm] 世界难度:{}", current.label());
        }
    }

    /** 服务器停止:清掉缓存,避免下一个存档继承上一个世界的难度。 */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        current = DifficultyLevel.NORMAL;
    }

    /** 玩家进服:立刻刷新一次,并告知当前难度。 */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        refreshPlayer(player);
        if (current != DifficultyLevel.NORMAL) {
            player.displayClientMessage(
                    Component.literal("[遗落境] 当前世界难度:")
                            .withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(current.label()).withStyle(current.color())),
                    false);
        }
    }

    /** 玩家重生/换维度:玩家实体会重建,修饰符要重新挂上。 */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            refreshPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            refreshPlayer(player);
        }
    }

    // ================================================================
    // 文字
    // ================================================================

    /** 难度的一句话摘要(命令与广播共用)。 */
    public static String describe(DifficultyLevel lv) {
        StringBuilder sb = new StringBuilder("  ");
        sb.append("怪物血 ").append(pct(lv.mobHealth()))
                .append(" / 甲 ").append(pct(lv.mobArmor()))
                .append(" / 攻 ").append(pct(lv.mobAttack()));
        if (lv.touchesPlayer()) {
            sb.append(" · 玩家攻 ").append(pct(lv.playerAttack()))
                    .append(" / 甲 ").append(pct(lv.playerArmor()))
                    .append(" / 回血 ").append(pct(lv.heal()))
                    .append(" / 熟练度 ").append(pct(lv.proficiency()));
        }
        if (lv.neutralAggressive()) {
            sb.append(" · 中立生物主动攻击");
        }
        if (lv.extraJobSlots() > 0) {
            sb.append(" · 职业饰品栏 +").append(lv.extraJobSlots());
        }
        return sb.toString();
    }

    /** 1.2 → "+20%",0.85 → "-15%",1.0 → "0%"。 */
    public static String pct(double multiplier) {
        int value = (int) Math.round((multiplier - 1.0D) * 100.0D);
        return (value >= 0 ? "+" : "") + value + "%";
    }

    private DifficultyManager() {}
}
