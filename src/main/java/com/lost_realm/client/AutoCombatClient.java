package com.lost_realm.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.lost_realm.Medals;
import com.lost_realm.config.MedalsConfig;
import com.lost_realm.item.ArrowCharmType;

/**
 * 客户端「自动攻击 / 自动射击」处理器。
 *
 * <ul>
 *   <li><b>E13 自动攻击</b> —— {@link #onClientTick}:手持武器按住左键、攻击冷却已满
 *       且(可选)准星指向实体时,自动再次挥击。</li>
 *   <li><b>E14 自动射击</b> —— 手持弓箭长按右键,待拉满后自动释放;</li>
 * </ul>
 *
 * <p>本类仅在客户端加载(由 {@code Dist.CLIENT} 限定)。
 */
@Mod.EventBusSubscriber(modid = Medals.MODID, value = Dist.CLIENT)
public final class AutoCombatClient {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || mc.screen != null) {
            return;
        }
        if (player.isSpectator()) {
            return;
        }

        autoAttack(mc, player);
        autoShoot(mc, player);
    }

    // ==================== E13 自动攻击 ====================

    private static void autoAttack(Minecraft mc, LocalPlayer player) {
        if (!MedalsConfig.QOL_AUTO_ATTACK.get()) {
            return;
        }
        if (!mc.options.keyAttack.isDown()) {
            return;
        }
        // 攻击冷却已满
        if (player.getAttackStrengthScale(0.0F) < 1.0F) {
            return;
        }

        Entity target = null;
        if (MedalsConfig.QOL_AUTO_ATTACK_REQUIRE_TARGET.get()) {
            HitResult hit = mc.hitResult;
            if (hit instanceof EntityHitResult ehr
                    && ehr.getType() == HitResult.Type.ENTITY) {
                target = ehr.getEntity();
            }
            if (target == null) {
                return; // 需要目标但准星未指向实体
            }
        }

        // 复用原版攻击逻辑:对目标挥击;无目标时按「空挥」处理
        if (target != null) {
            mc.gameMode.attack(player, target);
            player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        } else {
            // 空挥:仅播放挥击动作(避免误伤方块)
            player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }

    // ==================== E14 自动射击 ====================

    private static void autoShoot(Minecraft mc, LocalPlayer player) {
        if (!MedalsConfig.QOL_AUTO_SHOOT.get()) {
            return;
        }
        if (!mc.options.keyUse.isDown()) {
            return;
        }
        if (!player.isUsingItem()) {
            return;
        }

        ItemStack use = player.getUseItem();
        if (use.getItem() instanceof BowItem bow) {
            int remaining = player.getUseItemRemainingTicks();
            int used = bow.getUseDuration(use) - remaining;
            // ⚠️ 星辰贯穿之箭的蓄力门槛远高于原版的「拉满」1 秒（默认 10 秒）。
            //    若这里仍按 20 tick 松手，服务端会因为「蓄力不足」把发射取消掉，
            //    而玩家还按着右键 → 原版立刻重新拉弓 → 表现为「一直拉弓、永远射不出去」。
            //    所以必须按星辰箭自己的门槛决定何时松手。
            int required = BOW_FULL_DRAW_TICKS;
            if (ArrowCharmType.STAR.isActive(player)) {
                required = Math.max(required,
                        MedalsConfig.ARROW_STAR_CHARGE_SECONDS.get() * 20);
            }
            if (used >= required) {
                mc.gameMode.releaseUsingItem(player);
            }
        } else if (use.getItem() instanceof CrossbowItem) {
            if (CrossbowItem.isCharged(use)) {
                mc.gameMode.releaseUsingItem(player);
            }
        }
    }

    /** 原版弓「拉满」（战斗力 1.0）所需的 tick 数。 */
    private static final int BOW_FULL_DRAW_TICKS = 20;

    private AutoCombatClient() {}
}
