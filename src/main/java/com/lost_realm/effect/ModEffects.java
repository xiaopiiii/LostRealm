package com.lost_realm.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.lost_realm.Medals;

/** 本模组自注册的状态效果。 */
public final class ModEffects {

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Medals.MODID);

    /** 缴械：持有者无法造成伤害。 */
    public static final RegistryObject<MobEffect> DISARMED =
            EFFECTS.register("disarmed", DisarmedEffect::new);

    /** 眩晕：持有者无法移动、也无法造成伤害。 */
    public static final RegistryObject<MobEffect> STUNNED =
            EFFECTS.register("stunned", StunnedEffect::new);

    /** 脆弱：持有者受到的伤害提高（敕令之契的标记）。 */
    public static final RegistryObject<MobEffect> VULNERABLE =
            EFFECTS.register("vulnerable", VulnerableEffect::new);

    /**
     * 流血：每 2 秒结算 {@code 1 + 等级} 点伤害（撕裂）。
     *
     * <p>beta30 之前这是 Apothic Attributes 的效果，现在由本模组自建。
     */
    public static final RegistryObject<MobEffect> BLEEDING =
            EFFECTS.register("bleeding", BleedingEffect::new);

    public static void init(IEventBus bus) {
        EFFECTS.register(bus);
    }

    private ModEffects() {}
}
