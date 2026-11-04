package com.lost_realm.sound;

import com.lost_realm.Medals;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组的自定义声音。
 *
 * <h2>为什么现在才建这个类</h2>
 * 在此之前所有音效都直接复用原版 {@code SoundEvents}（剑、盾、爆炸），
 * 但 Boss 战音乐必须是自己的资源 —— 原版没有能循环三分钟的曲子。
 *
 * <h2>注册名里的点</h2>
 * {@code music.erebos_battle} 这个带点的写法是<b>照抄原版</b>
 * （{@code music.dragon}、{@code music.end} 都长这样）。
 * 它同时是 {@code sounds.json} 里的键，两者必须一字不差，
 * 否则运行时只会得到一句 "Unable to play unknown soundEvent" 的警告，
 * 而不会报错 —— 属于典型的静默失败。
 */
public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Medals.MODID);

    /**
     * 乌蠹诺斯战斗曲 —— 第一段，阶段Ⅰ / Ⅱ 使用。
     *
     * <p>整首 179.7 秒，按能量低谷切在 84.0 秒：
     * 前半段偏铺陈，后半段进鼓、压迫感更强，
     * 正好对上「阶段三开始才是真正的战斗」这个节奏。
     */
    public static final RegistryObject<SoundEvent> EREBOS_BATTLE_1 =
            register("music.erebos_battle_1");

    /** 第二段，阶段Ⅲ / Ⅳ 使用。 */
    public static final RegistryObject<SoundEvent> EREBOS_BATTLE_2 =
            register("music.erebos_battle_2");

    private static RegistryObject<SoundEvent> register(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(
                new ResourceLocation(Medals.MODID, path)));
    }

    public static void init(IEventBus modBus) {
        SOUNDS.register(modBus);
    }

    private ModSounds() {}
}
