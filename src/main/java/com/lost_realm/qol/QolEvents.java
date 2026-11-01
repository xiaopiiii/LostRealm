package com.lost_realm.qol;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.SleepingTimeCheckEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.SleepFinishedTimeEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.lost_realm.Medals;
import com.lost_realm.config.MedalsConfig;
import com.lost_realm.util.ModifierIds;

/**
 * 「体验改进(QoL)」板块的统一事件入口。
 *
 * <p>承载所有<b>可用 Forge 事件实现、无需 Mixin</b> 的功能,以及
 * 药水无限续杯 / buff 共享两个每 tick 驱动器的挂载点:
 * <ul>
 *   <li>B4 鞘翅加属性 —— {@link ItemAttributeModifierEvent}</li>
 *   <li>C6 任意时间睡觉 —— {@link SleepingTimeCheckEvent}</li>
 *   <li>D9 耕地保护 —— {@link BlockEvent.FarmlandTrampleEvent}</li>
 *   <li>A1 药水无限续杯 / A2 buff 共享 —— {@link TickEvent.PlayerTickEvent}</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Medals.MODID)
public final class QolEvents {

    // ==================== B4 鞘翅加属性 ====================

    @SubscribeEvent
    public static void onItemAttributeModifier(ItemAttributeModifierEvent event) {
        if (!MedalsConfig.QOL_ELYTRA_ARMOR.get()) {
            return;
        }
        if (!event.getItemStack().is(Items.ELYTRA)) {
            return;
        }
        if (event.getSlotType() != EquipmentSlot.CHEST) {
            return;
        }
        int armor = MedalsConfig.QOL_ELYTRA_ARMOR_VALUE.get();
        int toughness = MedalsConfig.QOL_ELYTRA_TOUGHNESS_VALUE.get();
        if (armor != 0) {
            event.addModifier(Attributes.ARMOR,
                    ModifierIds.mod(ModifierIds.QOL_ELYTRA_ARMOR, "qol_elytra_armor",
                            armor, AttributeModifier.Operation.ADDITION));
        }
        if (toughness != 0) {
            event.addModifier(Attributes.ARMOR_TOUGHNESS,
                    ModifierIds.mod(ModifierIds.QOL_ELYTRA_TOUGHNESS, "qol_elytra_toughness",
                            toughness, AttributeModifier.Operation.ADDITION));
        }
    }

    // ==================== C6 任意时间睡觉 ====================

    @SubscribeEvent
    public static void onSleepingTimeCheck(SleepingTimeCheckEvent event) {
        if (MedalsConfig.QOL_SLEEP_ANYTIME.get()) {
            event.setResult(Event.Result.ALLOW);
        }
    }

    // ==================== C7 睡觉跳过指定小时数 ====================

    /**
     * 原版睡眠成功后会「跳到次日清晨」。此处利用 Forge 的
     * {@link SleepFinishedTimeEvent#setTimeAddition(long)} 在清晨基础上
     * 再追加 {@code hours * 1000} tick(默认 6 小时)。
     *
     * <p>相比注入 {@code ServerLevel#setDayTime}(每 tick 都会被调用)，
     * 该事件只在「睡觉成功」时触发一次，语义精确且零风险。
     */
    @SubscribeEvent
    public static void onSleepFinished(SleepFinishedTimeEvent event) {
        int hours = MedalsConfig.QOL_SLEEP_SKIP_HOURS.get();
        if (hours <= 0) {
            return;
        }
        event.setTimeAddition(hours * QolConstants.TICKS_PER_HOUR);
    }

    // ==================== D9 耕地保护 ====================

    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (MedalsConfig.QOL_FARMLAND_PROTECT.get()) {
            event.setCanceled(true);
        }
    }

    // ==================== A1 / A2 每 tick 驱动 ====================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        PotionInfiniteHandler.tick(player);
        BuffShareHandler.tick(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            PotionInfiniteHandler.forget(sp.getUUID());
        }
    }

    private QolEvents() {}
}
