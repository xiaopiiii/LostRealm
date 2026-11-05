package com.lost_realm.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.lost_realm.Medals;
import com.lost_realm.config.MedalsConfig;
import com.lost_realm.network.MedalsNetwork;
import com.lost_realm.network.PacketDash;

/**
 * 突进之刃的客户端半边：检测「攻击键连点两次」。
 *
 * <h2>为什么用 isDown 的上升沿，而不是 consumeClick</h2>
 * {@code KeyMapping#consumeClick()} 会<b>吃掉</b>这次点击 ——
 * 原版 {@code Minecraft#handleKeybinds} 正是靠它来触发攻击的，
 * 我们在别处消耗掉，玩家就会发现自己砍不出刀。
 * 所以这里只观察 {@code isDown()} 的 false → true 跳变，一个字节都不碰原版状态。
 *
 * <p>只有真正发生「按下」的那一帧才去查 Curios（两层廉价判断在前），
 * 所以常态下这个处理器几乎不产生开销。
 */
@Mod.EventBusSubscriber(modid = Medals.MODID, value = Dist.CLIENT)
public final class DashClientHandler {

    private static boolean wasAttackDown = false;
    /** 上一次「按下攻击键」的 tick（玩家自己的 tickCount）。 */
    private static int lastClickTick = Integer.MIN_VALUE;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || mc.screen != null) {
            wasAttackDown = false;
            return;
        }

        boolean down = mc.options.keyAttack.isDown();
        boolean justPressed = down && !wasAttackDown;
        wasAttackDown = down;
        if (!justPressed) {
            return;
        }

        // 刻意<b>不</b>在客户端判断「有没有戴突进之刃」:
        // Curios 的客户端数据在某些时序下可能还没同步好,一旦这里判 false,
        // 玩家就会遇到「明明戴着却完全没反应」——而且看不出原因。
        // 交给服务端校验,那边是真值;不满足时服务端还会回一条说明。
        if (player.getMainHandItem().isEmpty()) {
            return;   // 空手就别发包了
        }

        int now = player.tickCount;
        int window = Math.max(2, MedalsConfig.BLADE_DASH_DOUBLE_TAP_TICKS.get());
        if (now - lastClickTick <= window) {
            MedalsNetwork.sendToServer(new PacketDash());
            // 清掉时间戳,免得三连点被判成两次突进
            lastClickTick = Integer.MIN_VALUE;
        } else {
            lastClickTick = now;
        }
    }

    private DashClientHandler() {}
}
