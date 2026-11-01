package com.lost_realm.client;

import com.lost_realm.data.MedalNbt;
import com.lost_realm.item.ItemMedal;
import com.lost_realm.skill.MedalType;
import com.lost_realm.util.ExpTable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * 客户端「玩家当前佩戴勋章」的查询入口。
 *
 * <h2>为什么不再需要缓存</h2>
 * 熟练度现在存放在勋章物品自身的 NBT 里(见 {@link MedalNbt}),
 * 而物品栈本身已经由原版同步到客户端 —— 因此**不需要**再自己发包维护一份副本。
 * 客户端直接扫一遍 Curios 背包即可读出等级,数据永远与物品一致。
 *
 * <p>调用频率很低(按键触发、tooltip 渲染),扫描 1 格饰品的开销可以忽略。
 */
public final class ClientMedalCache {

    /** 玩家当前佩戴的勋章物品栈,没有则返回 {@code null}。 */
    public static ItemStack worn() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) {
            return null;
        }
        return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(mc.player)
                .map(h -> h.findFirstCurio(s -> s.getItem() instanceof ItemMedal)
                        .map(r -> r.stack()).orElse(ItemStack.EMPTY))
                .orElse(ItemStack.EMPTY);
    }

    /** 玩家是否佩戴了指定类型的勋章。 */
    public static boolean has(MedalType type) {
        ItemStack stack = worn();
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.getItem() instanceof ItemMedal m && m.medalType() == type;
    }

    /** 指定勋章当前的等级(未佩戴时返回 1)。 */
    public static int level(MedalType type) {
        ItemStack stack = worn();
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ItemMedal m)
                || m.medalType() != type) {
            return 1;
        }
        return MedalNbt.of(stack).getLevel(type);
    }

    /** 指定勋章当前累计经验(未佩戴时返回 0)。 */
    public static long exp(MedalType type) {
        ItemStack stack = worn();
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ItemMedal m)
                || m.medalType() != type) {
            return 0L;
        }
        return MedalNbt.of(stack).getExp(type);
    }

    /** 兼容旧调用:不做缓存,因此无实际动作。 */
    public static void clear() {
        // no-op:数据随物品栈走,无需清理
    }

    /** 兼容旧调用:等级表的上限,便于界面显示「x / MAX」。 */
    public static int maxLevel() {
        return ExpTable.maxLevel();
    }

    private ClientMedalCache() {}
}
