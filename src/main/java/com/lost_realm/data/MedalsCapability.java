package com.lost_realm.data;

import javax.annotation.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.jetbrains.annotations.NotNull;

import com.lost_realm.Medals;

/**
 * 玩家勋章数据的 Capability 注册与生命周期。
 *
 * <p>附着在玩家实体上,负责:
 * <ul>
 *   <li>进入世界时附加数据容器</li>
 *   <li>死亡重生 / 切换维度时复制数据</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Medals.MODID)
public final class MedalsCapability {

    public static final ResourceLocation ID = new ResourceLocation(Medals.MODID, "player_medals");

    public static final Capability<PlayerMedals> PLAYER_MEDALS =
            CapabilityManager.get(new CapabilityToken<>() {});

    @SubscribeEvent
    public static void attach(final AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(ID, new Provider());
        }
    }

    @SubscribeEvent
    public static void clone(final PlayerEvent.Clone event) {
        // 无论是否死亡均复制,保证换维度/回床不掉数据
        event.getOriginal().reviveCaps();
        event.getOriginal().getCapability(PLAYER_MEDALS).ifPresent(oldData -> {
            event.getEntity().getCapability(PLAYER_MEDALS).ifPresent(newData -> {
                newData.load(oldData.save());
            });
        });
        event.getOriginal().invalidateCaps();
    }

    /** 数据容器提供者。 */
    public static class Provider implements ICapabilitySerializable<CompoundTag> {

        private final PlayerMedals data = new PlayerMedals();
        private final LazyOptional<PlayerMedals> optional = LazyOptional.of(() -> data);

        @NotNull
        @Override
        public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return PLAYER_MEDALS.orEmpty(cap, optional);
        }

        @Override
        public CompoundTag serializeNBT() {
            return data.save();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            data.load(nbt);
        }
    }

    private MedalsCapability() {}
}
