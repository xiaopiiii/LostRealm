package com.lost_realm.data;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import com.lost_realm.item.ItemMedal;
import com.lost_realm.skill.MedalType;
import com.lost_realm.util.ExpTable;

import top.theillusivec4.curios.api.CuriosApi;

/**
 * 熟练度取数门面 —— 服务端统一的「玩家 -&gt; 佩戴中勋章 -&gt; 等级/经验」入口。
 *
 * <h2>为什么要这一层</h2>
 * 熟练度改为存放于勋章物品 NBT 之后,任何想读等级的地方都要走三步:
 * <pre>
 *   1. 查 Curios 背包找到佩戴中的那枚勋章
 *   2. 判断它的 {@link MedalType} 是否与想要的类型一致
 *   3. 读它的 {@link MedalNbt#getLevel(MedalType)}
 * </pre>
 * 22 个消费方各写一遍既啰嗦又容易漏判类型。本类把这些收敛成一个方法,
 * 并保证「没戴勋章 = 等级 1」的一致语义。
 *
 * <h2>语义约定</h2>
 * <ul>
 *   <li><b>未佩戴 → 等级 1、经验 0</b>。这样 {@code level >= unlockLevel} 之类的
 *       判断天然为假(所有技能解锁等级 ≥ 5),不需要调用方额外判空。</li>
 *   <li>等级<b>永远来自物品</b>,不再有玩家侧副本,因此不存在两处数据打架。</li>
 * </ul>
 */
public final class MedalSource {

    /** 未佩戴勋章时的等级。 */
    public static final int NO_MEDAL_LEVEL = 1;

    /** 取玩家佩戴的指定类型勋章物品栈(未佩戴返回 {@link ItemStack#EMPTY})。 */
    public static ItemStack stackOf(LivingEntity entity, MedalType type) {
        if (entity == null) {
            return ItemStack.EMPTY;
        }
        return CuriosApi.getCuriosInventory(entity).map(handler ->
                handler.findFirstCurio(s -> s.getItem() instanceof ItemMedal m && m.medalType() == type)
                        .map(r -> r.stack()).orElse(ItemStack.EMPTY)
        ).orElse(ItemStack.EMPTY);
    }

    /** 取玩家佩戴的勋章物品栈(不区分类型,最先找到的那枚)。 */
    public static ItemStack anyWorn(LivingEntity entity) {
        if (entity == null) {
            return ItemStack.EMPTY;
        }
        return CuriosApi.getCuriosInventory(entity).map(handler ->
                handler.findFirstCurio(s -> s.getItem() instanceof ItemMedal)
                        .map(r -> r.stack()).orElse(ItemStack.EMPTY)
        ).orElse(ItemStack.EMPTY);
    }

    /**
     * 玩家佩戴的指定类型勋章的当前等级。
     *
     * <p>未佩戴该类型勋章时返回 {@link #NO_MEDAL_LEVEL}(= 1)。
     */
    public static int level(LivingEntity entity, MedalType type) {
        ItemStack stack = stackOf(entity, type);
        if (stack.isEmpty()) {
            return NO_MEDAL_LEVEL;
        }
        return MedalNbt.of(stack).getLevel(type);
    }

    /** 玩家佩戴的指定类型勋章的累计经验(未佩戴返回 0)。 */
    public static long exp(LivingEntity entity, MedalType type) {
        ItemStack stack = stackOf(entity, type);
        if (stack.isEmpty()) {
            return 0L;
        }
        return MedalNbt.of(stack).getExp(type);
    }

    /**
     * 为佩戴中的勋章增加熟练度经验。
     *
     * <p><b>未佩戴时静默丢弃</b> —— 这是刻意的设计:不戴勋章就不刷熟练度。
     * 返回实际增加的等级数(未佩戴时为 0)。
     *
     * <p>经验在玩家的「枷锁等级」处封顶（见 {@link MedalProgression}）。
     * 顶到枷锁时会给一次提示，但<b>每 60 秒最多一次</b> ——
     * 战斗里每秒都在打怪，不节流的话聊天栏会被这一句刷屏。
     */
    public static int addExp(ServerPlayer player, MedalType type, double amount) {
        ItemStack stack = stackOf(player, type);
        if (stack.isEmpty()) {
            return 0;
        }

        PlayerMedals pm = PlayerMedals.of(player);
        int cap = pm.levelCap();

        MedalNbt nbt = MedalNbt.of(stack);
        int oldLevel = nbt.getLevel(type);
        int gained = nbt.addExp(type, amount, cap);

        if (gained > 0) {
            // 升级后立即刷新属性;只需刷新这一枚,无需全表扫描
            com.lost_realm.item.MedalRuntime.refresh(player, type, pm, stack);

            // 刚顶到枷锁的那一次就要提示 —— 否则玩家看到等级停在 20
            // 却不知道为什么，要等下一次打怪才会收到那句话。
            if (cap < ExpTable.maxLevel() && nbt.getLevel(type) >= cap) {
                hintLocked(player, pm, cap);
            }
        } else if (oldLevel >= cap && amount > 0) {
            hintLocked(player, pm, cap);
        }
        return gained;
    }

    /** 顶到枷锁时的提示（60 秒节流）。 */
    private static void hintLocked(ServerPlayer player, PlayerMedals pm, int cap) {
        long now = player.level().getGameTime();
        if (now - pm.getTimestamp("shackle_hint") < 1200L) {
            return;
        }
        pm.setTimestamp("shackle_hint", now);
        player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable(
                        "message.lost_realm.shackle.locked", cap)
                        .withStyle(net.minecraft.ChatFormatting.GRAY),
                true);
    }

    /** 玩家是否佩戴了指定类型的勋章。 */
    public static boolean isWearing(LivingEntity entity, MedalType type) {
        return !stackOf(entity, type).isEmpty();
    }

    private MedalSource() {}
}
