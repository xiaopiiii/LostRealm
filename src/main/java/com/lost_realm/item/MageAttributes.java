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
 * 法师勋章所需的「法术暴击」属性。
 *
 * <h2>为什么不复用 RealmAttributes 的 crit_chance</h2>
 * {@code lost_realm:crit_chance} / {@code crit_damage} 由
 * {@code RealmAttributeEffects#onHurtCrit} 消费，那里<b>只对物理伤害掷骰</b>
 * （魔法走法师自己的分支），对 {@code magic} 伤害类型不生效。
 *
 * <p>如果把法术暴击挂在通用属性上,会导致「法师佩戴勋章后近战也一起变强」,
 * 破坏四枚勋章的职责隔离。因此这里单独注册两个属性:
 * <ul>
 *   <li>{@code lost_realm:spell_crit_chance} —— 法术暴击率,取值 0~1(即 0%~100%)</li>
 *   <li>{@code lost_realm:spell_crit_damage} —— 法术暴击伤害倍率,取值 0~10
 *       (基础 1.0 = 100%,即不暴击时无加成;1.35 = 135%)</li>
 * </ul>
 *
 * <p>这两个属性由 {@code CombatHandler} 在<b>魔法伤害结算分支</b>内读取并应用,
 * 因此不会影响近战 / 远程伤害。
 */
@Mod.EventBusSubscriber(modid = Medals.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MageAttributes {

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(ForgeRegistries.ATTRIBUTES, Medals.MODID);

    /** 法术暴击率(0~1)。 */
    public static final RegistryObject<Attribute> SPELL_CRIT_CHANCE =
            ATTRIBUTES.register("spell_crit_chance", () -> new RangedAttribute(
                    Medals.MODID + ".spell_crit_chance", 0.0D, 0.0D, 1.0D).setSyncable(true));

    /**
     * 法术暴击伤害倍率。
     *
     * <p>语义为「暴击时的伤害系数」,基础值 1.0 表示无加成。
     * 每级加成以 <b>ADDITION</b> 方式叠加(例如 +0.35 即 135%)。
     */
    public static final RegistryObject<Attribute> SPELL_CRIT_DAMAGE =
            ATTRIBUTES.register("spell_crit_damage", () -> new RangedAttribute(
                    Medals.MODID + ".spell_crit_damage", 1.0D, 0.0D, 100.0D).setSyncable(true));

    public static void init(net.minecraftforge.eventbus.api.IEventBus bus) {
        ATTRIBUTES.register(bus);
    }

    /** 把这两个属性挂到所有生物上,使其可被读取。 */
    @SubscribeEvent
    public static void modifyAttributes(final EntityAttributeModificationEvent event) {
        event.getTypes().forEach(type -> {
            event.add(type, SPELL_CRIT_CHANCE.get());
            event.add(type, SPELL_CRIT_DAMAGE.get());
        });
    }

    private MageAttributes() {}
}
