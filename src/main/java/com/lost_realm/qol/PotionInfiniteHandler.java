package com.lost_realm.qol;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionUtils;

import com.lost_realm.compat.backpack.BackpackCompat;
import com.lost_realm.config.MedalsConfig;

/**
 * <b>A1 药水无限续杯</b> 服务端处理器。
 *
 * <p>逻辑:
 * <ol>
 *   <li>扫描玩家背包(可选含精妙背包)中所有<b>普通药水</b>
 *       ({@code PotionItem} 实例;喷溅/滞留因不继承该类型而天然排除);</li>
 *   <li>按<b>效果</b>汇总瓶数 —— 同一种效果可以由不同的药水提供，
 *       它们会累加在一起;</li>
 *   <li><b>某效果的同效药水总数 ≥ 16 瓶</b> → 给予该效果的<b>无限时长</b>
 *       效果(时长 -1);</li>
 *   <li>一旦跌破 16 瓶(喝掉、丢出、被抢),立即移除该无限效果。</li>
 * </ol>
 *
 * <p>之所以要求「凑满一叠」而不是「有 1 瓶就给」：无限 buff 是永久收益，
 * 门槛太低会让药水系统失去意义。16 同时也是「药水堆叠」的上限，
 * 因此实际操作就是「背包里囤满一叠该效果药水」。
 *
 * <p>为避免频繁改动效果表,仅当「本次该给的集合」与「上次已给的集合」不同时才写操作。
 */
public final class PotionInfiniteHandler {

    /** 记录每个玩家"当前由本功能维持的无限效果"的集合。 */
    private static final Map<UUID, Set<MobEffect>> ACTIVE = new HashMap<>();

    /** 每 tick 调用(仅服务端玩家)。 */
    public static void tick(ServerPlayer player) {
        if (!MedalsConfig.QOL_POTION_INFINITE.get()) {
            // 功能被关闭:清理本功能此前施加的无限效果,避免残留
            clear(player);
            return;
        }

        Set<MobEffect> desired = collectAvailableEffects(player);
        Set<MobEffect> current = ACTIVE.computeIfAbsent(player.getUUID(), k -> new HashSet<>());

        // 新增:本次存在、上次没有
        for (MobEffect effect : desired) {
            MobEffectInstance existing = player.getEffect(effect);
            boolean infinite = existing != null && existing.getDuration() == MobEffectInstance.INFINITE_DURATION;
            if (!infinite) {
                int amp = bestAmplifier(player, effect);
                player.addEffect(new MobEffectInstance(effect,
                        MobEffectInstance.INFINITE_DURATION, amp, true, false));
            }
        }
        // 移除:上次有、本次没有
        for (MobEffect effect : current) {
            if (!desired.contains(effect)) {
                MobEffectInstance existing = player.getEffect(effect);
                if (existing != null && existing.getDuration() == MobEffectInstance.INFINITE_DURATION) {
                    player.removeEffect(effect);
                }
            }
        }

        current.clear();
        current.addAll(desired);
    }

    /** 玩家下线时清理记录(不影响其身上的效果)。 */
    public static void forget(UUID uuid) {
        ACTIVE.remove(uuid);
    }

    /** 统计玩家背包中**每种效果**当前拥有的药水总瓶数。 */
    private static Set<MobEffect> collectAvailableEffects(ServerPlayer player) {
        Map<MobEffect, Integer> bottles = new HashMap<>();
        countInto(player.getInventory(), bottles);

        if (MedalsConfig.QOL_POTION_SCAN_BACKPACK.get()) {
            for (ItemStack s : BackpackCompat.collectItems(player)) {
                addBottles(s, bottles);
            }
        }

        // ★ 只有「同效果药水总瓶数 ≥ 16」的效果才会被续杯。
        //   这是刻意的门槛：一瓶两瓶就能白嫖永久 buff 太廉价，
        //   凑满一叠（= 一个满堆叠）才算真的「囤够了」。
        Set<MobEffect> effects = new HashSet<>();
        for (Map.Entry<MobEffect, Integer> e : bottles.entrySet()) {
            if (e.getValue() >= requiredBottles()) {
                effects.add(e.getKey());
            }
        }
        return effects;
    }

    /**
     * 触发无限续杯所需的药水瓶数。
     *
     * <p>取 {@link MedalsConfig#STACK_POTION_MAX}（16）—— 也就是「一满叠」。
     * <b>注意</b>：这个门槛依赖「药水堆叠 16」那个开关是开着的；
     * 若把它关掉，原版药水堆叠上限是 1，玩家永远凑不满，本功能就等于失效。
     */
    private static int requiredBottles() {
        return MedalsConfig.STACK_POTION_MAX;
    }

    private static void countInto(Inventory inv, Map<MobEffect, Integer> bottles) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            addBottles(inv.getItem(i), bottles);
        }
    }

    /**
     * 把这一叠药水提供的每个效果各累加 {@code stack.getCount()} 瓶。
     *
     * <p>关键在于按<b>瓶数</b>而非「格数」计数：一叠 16 瓶算 16，
     * 3 叠各 6 瓶也是 18 —— 玩家的直觉是「我身上有几瓶」，
     * 而不是「我占了几格」。
     */
    private static void addBottles(ItemStack stack, Map<MobEffect, Integer> bottles) {
        if (stack.isEmpty() || !(stack.getItem() instanceof PotionItem)) {
            return; // 喷溅型/滞留型不继承 PotionItem,天然被排除
        }
        int count = stack.getCount();
        if (count <= 0) {
            return;
        }
        for (MobEffectInstance inst : PotionUtils.getMobEffects(stack)) {
            bottles.merge(inst.getEffect(), count, Integer::sum);
        }
    }

    /** 在背包中查找提供该效果的最高等级药水,取其 amplifier。 */
    private static int bestAmplifier(ServerPlayer player, MobEffect effect) {
        int best = 0;
        best = Math.max(best, amplifierIn(player.getInventory(), effect));
        if (MedalsConfig.QOL_POTION_SCAN_BACKPACK.get()) {
            for (ItemStack s : BackpackCompat.collectItems(player)) {
                best = Math.max(best, amplifierOf(s, effect));
            }
        }
        return best;
    }

    private static int amplifierIn(Inventory inv, MobEffect effect) {
        int best = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            best = Math.max(best, amplifierOf(inv.getItem(i), effect));
        }
        return best;
    }

    private static int amplifierOf(ItemStack stack, MobEffect effect) {
        if (stack.isEmpty() || !(stack.getItem() instanceof PotionItem)) {
            return 0;
        }
        int best = 0;
        for (MobEffectInstance inst : PotionUtils.getMobEffects(stack)) {
            if (inst.getEffect() == effect) {
                best = Math.max(best, inst.getAmplifier());
            }
        }
        return best;
    }

    /** 移除本功能此前维持的所有无限效果。 */
    private static void clear(ServerPlayer player) {
        Set<MobEffect> current = ACTIVE.remove(player.getUUID());
        if (current == null) {
            return;
        }
        for (MobEffect effect : current) {
            MobEffectInstance existing = player.getEffect(effect);
            if (existing != null && existing.getDuration() == MobEffectInstance.INFINITE_DURATION) {
                player.removeEffect(effect);
            }
        }
    }

    private PotionInfiniteHandler() {}
}
