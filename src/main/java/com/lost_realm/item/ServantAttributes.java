package com.lost_realm.item;

import net.minecraft.world.entity.EntityType;
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
 * 召唤师勋章所需的<b>四条仆从属性</b>。
 *
 * <h2>为什么要自己注册</h2>
 * Goety 把仆从当成普通生物来用，它只提供「法术强度 / 冷却折扣」这类
 * <b>施法者</b>属性，<b>完全没有</b>「仆从有多能打、多能扛」这层概念 ——
 * 仆从的强弱在 Goety 里是硬编码在各个仆从实体身上的。
 *
 * <p>所以这里补上这一层：把「我想让仆从变强多少」抽象成四条属性，
 * 挂在<b>主人</b>（玩家）身上；{@code SummonerCharmHandler} 再周期性
 * 把主人的这四条数值<b>翻译成仆从自身的原版属性修饰符</b>。
 *
 * <h2>为什么挂在玩家身上而不是仆从身上</h2>
 * <ul>
 *   <li>加成来源是玩家（勋章等级 + 契约饰品），玩家才是「持有者」；</li>
 *   <li>仆从是短命的，把数值挂在它们身上会随召唤/消散反复丢；</li>
 *   <li>挂在玩家身上时，{@code /attribute} 能直接把整套加成看个明白。</li>
 * </ul>
 *
 * <h2>数值语义</h2>
 * 四条一律是<b>系数</b>（0.30 = +30%），基础值 0 表示无加成。
 * 由 {@code AttributeGuard} 以 {@code MULTIPLY_BASE} 方式叠加，
 * 因此不同的勋章等级 / 饰品之间是相加关系，不会互相放大。
 */
@Mod.EventBusSubscriber(modid = Medals.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ServantAttributes {

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(ForgeRegistries.ATTRIBUTES, Medals.MODID);

    /** 仆从最大生命加成（系数，0.30 = +30%）。 */
    public static final RegistryObject<Attribute> SERVANT_HEALTH =
            ATTRIBUTES.register("servant_health", () -> new RangedAttribute(
                    Medals.MODID + ".servant_health", 0.0D, 0.0D, 100.0D).setSyncable(true));

    /** 仆从攻击伤害加成（系数）。 */
    public static final RegistryObject<Attribute> SERVANT_DAMAGE =
            ATTRIBUTES.register("servant_damage", () -> new RangedAttribute(
                    Medals.MODID + ".servant_damage", 0.0D, 0.0D, 100.0D).setSyncable(true));

    /** 仆从移动速度加成（系数）。 */
    public static final RegistryObject<Attribute> SERVANT_SPEED =
            ATTRIBUTES.register("servant_speed", () -> new RangedAttribute(
                    Medals.MODID + ".servant_speed", 0.0D, 0.0D, 100.0D).setSyncable(true));

    /**
     * 仆从寿命延长（系数，0.25 = 寿命 +25%）。
     *
     * <p>取 1.0 表示寿命翻倍。这一条不是原版属性，而是「寿命」这种
     * Goety 特有概念在属性层的投影（见 {@code IOwned#getLifespan}）。
     */
    public static final RegistryObject<Attribute> SERVANT_LIFESPAN =
            ATTRIBUTES.register("servant_lifespan", () -> new RangedAttribute(
                    Medals.MODID + ".servant_lifespan", 0.0D, 0.0D, 1000.0D).setSyncable(true));

    public static void init(net.minecraftforge.eventbus.api.IEventBus bus) {
        ATTRIBUTES.register(bus);
    }

    /**
     * 把四条属性挂到<b>玩家</b>身上。
     *
     * <p>只挂玩家、不挂全部生物：这是「主人的加成」，仆从自己不持有它。
     * 全生物挂载会让每个僵尸都平白多出四个属性实例（服务端内存白白翻几倍），
     * 而它们一次也用不上。
     */
    @SubscribeEvent
    public static void modifyAttributes(final EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, SERVANT_HEALTH.get());
        event.add(EntityType.PLAYER, SERVANT_DAMAGE.get());
        event.add(EntityType.PLAYER, SERVANT_SPEED.get());
        event.add(EntityType.PLAYER, SERVANT_LIFESPAN.get());
    }

    private ServantAttributes() {}
}
