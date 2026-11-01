package com.lost_realm.attribute;

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
 * 本模组<b>自注册</b>的战斗属性（beta30 起）。
 *
 * <h2>为什么自己注册，而不是继续借用前置模组的属性</h2>
 * beta29 及以前，这 12 条属性来自 <b>Apothic Attributes</b>（{@code attributeslib}），
 * 等于把「勋章的全部成长核心」压在一个不是我们必须依赖的模组上：
 * 玩家不装它，暴击 / 吸血 / 破甲 / 闪避 / 拉弓速度全线失效。
 * beta30 把它们收进本模组，前置只剩 Curios 一个硬依赖。
 *
 * <h2>数值契约（与 attributeslib 1.3.7 逐条对齐，不要随手改）</h2>
 * 基础值/上下限全部照抄对方 1.3.7 的注册代码，
 * 因为历史存档里的修饰符数值（{@code MedalRuntime} / 各技能写的 {@code 0.05}、{@code 0.08} …）
 * 都是按这套量纲算的。改基础值 = 老存档的强度集体漂移。
 *
 * <pre>
 *   属性                        基础值   下限   上限    语义
 *   lost_realm:crit_chance       0.05    0.0   10.0   暴击概率，1.0 = 100%
 *   lost_realm:crit_damage        1.5    1.0  100.0   暴击倍率，1.5 = 150%
 *   lost_realm:life_steal         0.0    0.0   10.0   吸血比例，0.1 = 10%
 *   lost_realm:armor_pierce       0.0    0.0 1000.0   固定护甲穿透（点）
 *   lost_realm:dodge_chance       0.0    0.0    1.0   闪避概率，1.0 = 100%
 *   lost_realm:overheal           0.0    0.0   10.0   治疗溢出转伤害吸收的比例
 *   lost_realm:healing_received   1.0    0.0 1000.0   受疗倍率，1.0 = 100%
 *   lost_realm:experience_gained  1.0    0.0 1000.0   经验倍率，11.0 = +1000%
 *   lost_realm:fire_damage        0.0    0.0 1000.0   近战附带的火焰伤害（点）
 *   lost_realm:cold_damage        0.0    0.0 1000.0   近战附带的冰霜伤害（点）
 *   lost_realm:arrow_damage       1.0    0.0   10.0   箭矢基础伤害倍率
 *   lost_realm:draw_speed         1.0    0.0    4.0   拉弓速度倍率
 * </pre>
 *
 * <h2>谁来消费这些属性</h2>
 * <b>不是本类</b>。本类只负责「注册 + 挂到实体上」，
 * 真正让它们生效的是 {@link RealmAttributeEffects}（事件）与
 * {@code mixin/LivingEntityArmorPierceMixin}（护甲穿透必须进原版结算流程）。
 *
 * <p>⚠️ 语言键是 {@code attribute.name.lost_realm.<id>}，
 * 与 {@code RangedAttribute} 的 descriptionId 保持一致；
 * 中英两份语言文件必须同时加，否则属性界面显示裸键。
 */
@Mod.EventBusSubscriber(modid = Medals.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RealmAttributes {

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(ForgeRegistries.ATTRIBUTES, Medals.MODID);

    /** 暴击概率，基础 5%（原版跳劈暴击以外的属性暴击）。1.0 = 100%。 */
    public static final RegistryObject<Attribute> CRIT_CHANCE =
            ranged("crit_chance", 0.05D, 0.0D, 10.0D);

    /** 暴击倍率，基础 1.5。叠加方式由调用方决定（本模组多为 MULTIPLY_BASE / ADDITION）。 */
    public static final RegistryObject<Attribute> CRIT_DAMAGE =
            ranged("crit_damage", 1.5D, 1.0D, 100.0D);

    /** 吸血比例：造成物理伤害时按此比例回血，0.1 = 10%。 */
    public static final RegistryObject<Attribute> LIFE_STEAL =
            ranged("life_steal", 0.0D, 0.0D, 10.0D);

    /** 固定护甲穿透（点）：结算时先把目标护甲减掉这么多，再走原版护甲公式。 */
    public static final RegistryObject<Attribute> ARMOR_PIERCE =
            ranged("armor_pierce", 0.0D, 0.0D, 1000.0D);

    /** 闪避概率：命中瞬间掷骰，命中则整次伤害直接取消。1.0 = 100%。 */
    public static final RegistryObject<Attribute> DODGE_CHANCE =
            ranged("dodge_chance", 0.0D, 0.0D, 1.0D);

    /** 过量治疗转伤害吸收的比例；吸收上限固定为最大生命的 50%。 */
    public static final RegistryObject<Attribute> OVERHEAL =
            ranged("overheal", 0.0D, 0.0D, 10.0D);

    /** 受到治疗的倍率，1.0 = 原样。 */
    public static final RegistryObject<Attribute> HEALING_RECEIVED =
            ranged("healing_received", 1.0D, 0.0D, 1000.0D);

    /**
     * 经验获取倍率，1.0 = 原样。
     *
     * <p>战士终极「我有一剑！」的 +1000% 用 {@code MULTIPLY_TOTAL +10.0} 表达，
     * 即最终 11.0 倍。
     */
    public static final RegistryObject<Attribute> EXPERIENCE_GAINED =
            ranged("experience_gained", 1.0D, 0.0D, 1000.0D);

    /** 近战附带的火焰伤害（点）。命中时点燃目标。 */
    public static final RegistryObject<Attribute> FIRE_DAMAGE =
            ranged("fire_damage", 0.0D, 0.0D, 1000.0D);

    /** 近战附带的冰霜伤害（点）。命中时施加缓慢。 */
    public static final RegistryObject<Attribute> COLD_DAMAGE =
            ranged("cold_damage", 0.0D, 0.0D, 1000.0D);

    /** 箭矢基础伤害倍率，1.0 = 原样。臂力/弓术成长挂在这里。 */
    public static final RegistryObject<Attribute> ARROW_DAMAGE =
            ranged("arrow_damage", 1.0D, 0.0D, 10.0D);

    /** 拉弓速度倍率，1.0 = 原样；2.0 ≈ 两倍速开弓。 */
    public static final RegistryObject<Attribute> DRAW_SPEED =
            ranged("draw_speed", 1.0D, 0.0D, 4.0D);

    /**
     * 统一的注册助手。
     *
     * <p>{@code setSyncable(true)} 不可省：拉弓速度与闪避是<b>双端</b>都要读的值，
     * 不同步的话客户端算出来的蓄力进度和服务端对不上，表现为「拉弓条满了但射不出去」。
     */
    private static RegistryObject<Attribute> ranged(String id, double base, double min, double max) {
        return ATTRIBUTES.register(id, () -> new RangedAttribute(
                "attribute.name." + Medals.MODID + "." + id, base, min, max).setSyncable(true));
    }

    public static void init(net.minecraftforge.eventbus.api.IEventBus bus) {
        ATTRIBUTES.register(bus);
    }

    /**
     * 把这 12 条属性挂到<b>所有</b>生物类型上。
     *
     * <p>必须挂全，不能只挂玩家：{@code LivingEntity#getAttributeValue} 在属性缺失时
     * 返回 0，而 {@code healing_received / arrow_damage / draw_speed / experience_gained}
     * 的「无加成」基准值是 <b>1.0</b> —— 一旦某类实体缺属性，
     * 读数会变成 0，治疗直接归零、箭矢伤害清零。
     */
    @SubscribeEvent
    public static void modifyAttributes(final EntityAttributeModificationEvent event) {
        for (var type : event.getTypes()) {
            event.add(type, CRIT_CHANCE.get());
            event.add(type, CRIT_DAMAGE.get());
            event.add(type, LIFE_STEAL.get());
            event.add(type, ARMOR_PIERCE.get());
            event.add(type, DODGE_CHANCE.get());
            event.add(type, OVERHEAL.get());
            event.add(type, HEALING_RECEIVED.get());
            event.add(type, EXPERIENCE_GAINED.get());
            event.add(type, FIRE_DAMAGE.get());
            event.add(type, COLD_DAMAGE.get());
            event.add(type, ARROW_DAMAGE.get());
            event.add(type, DRAW_SPEED.get());
        }
    }

    /* -------------------------- 访问器 -------------------------- */

    /*
     * 这组方法刻意做成「无参、直接返回 Attribute」的形态：
     * 它们出现之前，全项目的调用点是 ApothAttributes#xxx()，
     * 保持同名同形可以让 beta30 的这次替换只改类名、不动逻辑。
     *
     * ⚠️ 这里<b>不</b>返回 null —— 属性由本模组自己注册，不存在「前置没装」的降级状态。
     * 调用方原来的 null 判断可以保留（无害），但不再是必需的。
     */

    public static Attribute critChance()      { return CRIT_CHANCE.get(); }

    public static Attribute critDamage()      { return CRIT_DAMAGE.get(); }

    public static Attribute lifeSteal()       { return LIFE_STEAL.get(); }

    public static Attribute armorPierce()     { return ARMOR_PIERCE.get(); }

    public static Attribute dodgeChance()     { return DODGE_CHANCE.get(); }

    public static Attribute overheal()        { return OVERHEAL.get(); }

    public static Attribute healingReceived() { return HEALING_RECEIVED.get(); }

    public static Attribute experienceGained(){ return EXPERIENCE_GAINED.get(); }

    public static Attribute fireDamage()      { return FIRE_DAMAGE.get(); }

    public static Attribute coldDamage()      { return COLD_DAMAGE.get(); }

    public static Attribute arrowDamage()     { return ARROW_DAMAGE.get(); }

    public static Attribute drawSpeed()       { return DRAW_SPEED.get(); }

    private RealmAttributes() {}
}
