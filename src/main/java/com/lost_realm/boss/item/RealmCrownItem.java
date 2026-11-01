package com.lost_realm.boss.item;

import java.util.List;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import com.lost_realm.attribute.RealmAttributes;

/**
 * 遗落境冠冕 —— 8% 掉落的<b>毕业饰品</b>。
 *
 * <h2>设计定位:"全面但小幅"</h2>
 * 它不像勋章那样专精某一职业,而是给<b>所有</b>方向都加一点点:
 * 生命、护甲、韧性、暴击率、暴击伤害、生命偷取。
 *
 * <p>这样设计的理由:作为整合包最终 Boss 的稀有掉落,
 * 它应该对所有玩家都有吸引力(不管你主玩什么职业),
 * 但又不能强到"替代勋章" —— 否则会破坏四勋章系统的核心地位。
 * 因此每一项的数值都控制在"有感但不决定性"的量级。
 *
 * <h2>为什么必须用固定 UUID</h2>
 * 这是本模组的技术红线(见 {@code .codebuddy/项目规则.md`}):
 * 属性修饰符若用随机 UUID,每次装备/卸下都会叠加一条新修饰符,
 * 最终导致数值无限膨胀。固定 UUID 保证同一件装备永远只施加一条。
 *
 * <h2>属性的引用</h2>
 * 暴击率 / 暴击伤害 / 生命偷取走 {@link RealmAttributes} ——
 * 与勋章系统共用同一套属性 ID 引用,避免硬编码字符串。
 */
public class RealmCrownItem extends Item implements ICurioItem {

    // ---- 固定 UUID(每个属性一条,永不重复) ----
    private static final UUID UUID_HEALTH =
            UUID.fromString("a7c3e8f1-4b2d-4e9a-8c11-0d5f6a2b3c41");
    private static final UUID UUID_ARMOR =
            UUID.fromString("b8d4f9a2-5c3e-4f0b-9d22-1e6a7b3c4d52");
    private static final UUID UUID_TOUGHNESS =
            UUID.fromString("c9e5a0b3-6d4f-4a1c-ae33-2f7b8c4d5e63");
    private static final UUID UUID_CRIT_CHANCE =
            UUID.fromString("da06b1c4-7e50-4b2d-bf44-3a8c9d5e6f74");
    private static final UUID UUID_CRIT_DAMAGE =
            UUID.fromString("eb17c2d5-8f61-4c3e-a055-4b9dae6f7085");
    private static final UUID UUID_LIFE_STEAL =
            UUID.fromString("fc28d3e6-9a72-4d4f-b166-5caebf708196");

    // ---- 数值 ----
    private static final double BONUS_HEALTH = 8.0D;
    private static final double BONUS_ARMOR = 4.0D;
    private static final double BONUS_TOUGHNESS = 3.0D;
    private static final double BONUS_CRIT_CHANCE = 0.05D;   // 5%
    private static final double BONUS_CRIT_DAMAGE = 0.10D;   // 10%
    private static final double BONUS_LIFE_STEAL = 0.03D;    // 3%

    public RealmCrownItem(Properties props) {
        super(props);
    }

    // ================================================================
    // Curios:装备时施加、卸下时移除
    // ================================================================

    @Override
    public void onEquip(SlotContext ctx, ItemStack prevStack, ItemStack stack) {
        if (ctx.entity() == null) {
            return;
        }
        apply(ctx, true);
    }

    @Override
    public void onUnequip(SlotContext ctx, ItemStack newStack, ItemStack stack) {
        if (ctx.entity() == null) {
            return;
        }
        apply(ctx, false);
    }

    /**
     * 施加或移除全部修饰符。
     *
     * <p>统一走 {@code removeModifier} + {@code addModifier} 的组合,
     * 而不是"先检查是否存在":后者在装备被复制、跨维度时可能出现
     * 状态不一致,统一先删后加更稳。
     */
    private void apply(SlotContext ctx, boolean add) {
        var entity = ctx.entity();
        addMod(entity, Attributes.MAX_HEALTH, UUID_HEALTH,
                "lost_realm_crown_health", BONUS_HEALTH, add);
        addMod(entity, Attributes.ARMOR, UUID_ARMOR,
                "lost_realm_crown_armor", BONUS_ARMOR, add);
        addMod(entity, Attributes.ARMOR_TOUGHNESS, UUID_TOUGHNESS,
                "lost_realm_crown_toughness", BONUS_TOUGHNESS, add);

        // 战斗属性由本模组自注册(beta30),不再有"前置未装就不存在"的分支
        addMod(entity, RealmAttributes.critChance(), UUID_CRIT_CHANCE,
                "lost_realm_crown_crit_chance", BONUS_CRIT_CHANCE, add);
        addMod(entity, RealmAttributes.critDamage(), UUID_CRIT_DAMAGE,
                "lost_realm_crown_crit_damage", BONUS_CRIT_DAMAGE, add);
        addMod(entity, RealmAttributes.lifeSteal(), UUID_LIFE_STEAL,
                "lost_realm_crown_life_steal", BONUS_LIFE_STEAL, add);
    }

    private void addMod(net.minecraft.world.entity.LivingEntity entity, Attribute attr,
                        UUID uuid, String name, double amount, boolean add) {
        var inst = entity.getAttribute(attr);
        if (inst == null) {
            return;
        }
        // 先删后加 —— 保证幂等
        inst.removeModifier(uuid);
        if (add) {
            inst.addTransientModifier(new AttributeModifier(uuid, name, amount,
                    AttributeModifier.Operation.ADDITION));
        }
    }

    // ================================================================
    // 表现
    // ================================================================

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lost_realm.realm_crown.tip.1")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.empty());
        tip(tooltip, "health", BONUS_HEALTH);
        tip(tooltip, "armor", BONUS_ARMOR);
        tip(tooltip, "toughness", BONUS_TOUGHNESS);
        tipPct(tooltip, "crit_chance", BONUS_CRIT_CHANCE);
        tipPct(tooltip, "crit_damage", BONUS_CRIT_DAMAGE);
        tipPct(tooltip, "life_steal", BONUS_LIFE_STEAL);
    }

    private static void tip(List<Component> tooltip, String key, double value) {
        tooltip.add(Component.translatable("item.lost_realm.realm_crown.stat." + key,
                        String.format("%.0f", value))
                .withStyle(ChatFormatting.BLUE));
    }

    private static void tipPct(List<Component> tooltip, String key, double value) {
        tooltip.add(Component.translatable("item.lost_realm.realm_crown.stat." + key,
                        String.format("%.0f", value * 100.0D))
                .withStyle(ChatFormatting.BLUE));
    }

    /** 该物品是否需要 Goety 才可用(否 —— 它是纯属性饰品)。 */
    public Rarity rarity() {
        return Rarity.EPIC;
    }
}
