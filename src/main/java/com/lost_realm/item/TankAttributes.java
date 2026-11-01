package com.lost_realm.item;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.lost_realm.Medals;

/**
 * 坦克勋章所需的「伤害减免」属性。
 *
 * <p>Apothic Attributes 未提供全局伤害减免属性(它只提供按伤害类型的减免词缀),
 * 因此这里单独注册一个 {@code lost_realm:damage_reduction},
 * 取值 0~0.9(即 0%~90%),由 {@code CombatHandler} 在受伤结算时读取并削减伤害。
 */
@Mod.EventBusSubscriber(modid = Medals.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TankAttributes {

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(ForgeRegistries.ATTRIBUTES, Medals.MODID);

    public static final RegistryObject<Attribute> DAMAGE_REDUCTION =
            ATTRIBUTES.register("damage_reduction", () -> new RangedAttribute(
                    Medals.MODID + ".damage_reduction", 0.0D, 0.0D, 0.9D).setSyncable(true));

    public static void init(net.minecraftforge.eventbus.api.IEventBus bus) {
        ATTRIBUTES.register(bus);
    }

    /** 把该属性挂到所有生物上,使其可被读取。 */
    @SubscribeEvent
    public static void modifyAttributes(final EntityAttributeModificationEvent event) {
        event.getTypes().forEach(type ->
                event.add(type, DAMAGE_REDUCTION.get()));
    }

    private TankAttributes() {}
}
