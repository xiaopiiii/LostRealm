package com.lost_realm.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.lost_realm.Medals;
import com.lost_realm.data.PlayerMedals;
import com.lost_realm.item.ItemMedal;
import com.lost_realm.item.MedalRuntime;
import com.lost_realm.skill.MedalType;

import top.theillusivec4.curios.api.CuriosApi;

/**
 * 服务端 tick 驱动 —— 每 tick 刷新已佩戴勋章的属性与节点技能状态。
 *
 * <p>由于所有属性修饰符都使用固定 UUID 且经过防叠加保护,
 * 这里每 tick 幂等刷新是安全且推荐的:它能让技能状态、等级变化、临时 Buff
 * 自动保持一致,无需手动追踪每一个修饰符的增删时机。
 */
@Mod.EventBusSubscriber(modid = Medals.MODID)
public class PlayerTickHandler {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }

        PlayerMedals data = PlayerMedals.of(player);
        data.tickCooldowns();

        // 找出玩家已佩戴的勋章 —— 等级取自勋章物品自身(物品 NBT)
        for (MedalType type : MedalType.values()) {
            net.minecraft.world.item.ItemStack stack = wornStack(player, type);
            if (stack != null && !stack.isEmpty()) {
                MedalRuntime.refresh(player, type, data, stack);
            } else {
                // 未佩戴则确保其修饰符已被清除(幂等,开销可接受)
                MedalRuntime.clearIfNeeded(player, type);
            }
        }
    }

    /** 玩家佩戴的指定类型勋章物品栈;未佩戴返回 {@code null}。 */
    public static net.minecraft.world.item.ItemStack wornStack(ServerPlayer player, MedalType type) {
        return CuriosApi.getCuriosInventory(player).map(handler ->
                handler.findFirstCurio(stack -> stack.getItem() instanceof ItemMedal medal
                                && medal.medalType() == type)
                        .map(r -> r.stack()).orElse(net.minecraft.world.item.ItemStack.EMPTY)
        ).orElse(net.minecraft.world.item.ItemStack.EMPTY);
    }

    /** 玩家是否佩戴了指定类型的勋章。 */
    public static boolean isWearing(ServerPlayer player, MedalType type) {
        return CuriosApi.getCuriosInventory(player).map(handler -> {
            var result = handler.findFirstCurio(stack -> stack.getItem() instanceof ItemMedal medal
                    && medal.medalType() == type);
            return result.isPresent();
        }).orElse(false);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            LevelSync.forget(sp.getUUID());
        }
    }

    private PlayerTickHandler() {}
}
