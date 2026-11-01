package com.lost_realm.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.minecraftforge.network.NetworkEvent;

import com.lost_realm.config.MedalsConfig;

/**
 * 客户端 -> 服务端:请求刷新当前附魔台的候选附魔(F17 附魔刷新按钮)。
 *
 * <p>服务端收到后:
 * <ol>
 *   <li>校验经验等级与青金石数量是否足够;</li>
 *   <li>扣除经验等级与青金石;</li>
 *   <li>改写附魔种子(随机),使下一次 {@code slotsChanged} 产生全新候选。</li>
 * </ol>
 */
public class PacketRefreshEnchant {

    private final int containerId;

    public PacketRefreshEnchant(int containerId) {
        this.containerId = containerId;
    }

    public PacketRefreshEnchant(FriendlyByteBuf buf) {
        this.containerId = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.containerId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        if (player == null) {
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> {
            if (!(player.containerMenu instanceof EnchantmentMenu menu)) {
                return;
            }
            if (menu.containerId != this.containerId) {
                return;
            }

            int needLevels = MedalsConfig.QOL_REFRESH_ENCHANT_COST_LEVELS.get();
            int needLapis = MedalsConfig.QOL_REFRESH_ENCHANT_COST_LAPIS.get();

            // 创造模式无视消耗
            boolean creative = player.getAbilities().instabuild;
            if (!creative) {
                if (player.experienceLevel < needLevels) {
                    return;
                }
                if (countLapis(player) < needLapis) {
                    return;
                }
                player.giveExperienceLevels(-needLevels);
                consumeLapis(player, needLapis);
            }

            // 改写附魔种子 -> 下一次容器变更即重新生成候选
            DataSlot seed = ((com.lost_realm.mixin.EnchantmentMenuAccessor) menu).medals$getEnchantmentSeedSlot();
            int newSeed = player.getRandom().nextInt();
            seed.set(newSeed);
            menu.slotsChanged(menu.getSlot(0).container);
            menu.broadcastChanges();
        });
        context.setPacketHandled(true);
    }

    private static int countLapis(ServerPlayer player) {
        int c = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.LAPIS_LAZULI)) {
                c += s.getCount();
            }
        }
        return c;
    }

    private static void consumeLapis(ServerPlayer player, int amount) {
        int left = amount;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.LAPIS_LAZULI)) {
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
    }
}
