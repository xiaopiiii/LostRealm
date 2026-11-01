package com.lost_realm.compat.goety;

import java.util.List;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import com.Polarice3.Goety.api.entities.IOwned;
import com.Polarice3.Goety.api.items.magic.IWand;
import com.Polarice3.Goety.common.capabilities.soulenergy.FocusCooldown;
import com.Polarice3.Goety.init.ModAttributes;
import com.Polarice3.Goety.utils.SEHelper;
import com.Polarice3.Goety.utils.TotemFinder;

/**
 * {@link GoetyBridge} 的真实实现。
 *
 * <h2>类加载警告</h2>
 * 本类<b>直接引用</b> {@code com.Polarice3.Goety.*}。
 * 绝不可在 Goety 未安装时被加载 —— 实例化入口只在
 * {@link GoetyCompat#bridge()} 内部、确认 {@code ModList.isLoaded("goety")}
 * 之后才触发。
 *
 * <h2>本模组使用的 Goety 公开 API</h2>
 * <ul>
 *   <li>{@link SEHelper} —— 灵魂能量读写(官方公开静态工具类)</li>
 *   <li>{@link TotemFinder} —— 灵魂容器(图腾)查找</li>
 *   <li>{@link ModAttributes} —— 法术强度 / 折扣 / 冷却折扣等属性</li>
 *   <li>{@link IOwned} —— 仆从归属与强化</li>
 *   <li>{@link IWand} —— 魔杖识别</li>
 * </ul>
 *
 * <p>所有属性修饰符仍使用本模组的固定 UUID({@code com.lost_realm.util.ModifierIds}),
 * 并由 {@code AttributeGuard} 做防叠加保护。
 */
public class GoetyBridgeImpl implements GoetyBridge {

    /** 扫描玩家周围寻找仆从的半径(格)。 */
    private static final double SERVANT_SCAN_RADIUS = 48.0D;

    // ================================================================
    // 灵魂能量
    // ================================================================

    @Override
    public int getSouls(Player player) {
        return SEHelper.getSESouls(player);
    }

    @Override
    public int getMaxSouls(Player player) {
        // Goety 的灵魂上限不是直接暴露的常量:
        // 使用方舟时上限为 MainConfig.MaxArcaSouls,使用图腾时为图腾自身上限。
        // 这里通过「图腾物品的最大灵魂」与「当前存量」中较大者估算,
        // 保证 Lv.10「上限 +200」与 Lv.60「灵魂 >50% 上限」有稳定基准。
        try {
            ItemStack totem = TotemFinder.FindTotem(player);
            if (!totem.isEmpty()) {
                int max = maximumSoulsOfTotem(totem);
                if (max > 0) {
                    return max;
                }
            }
        } catch (Throwable ignored) {
            // 落到下面的兜底
        }
        // 兜底:以当前存量为基准(至少 200,避免除零)
        return Math.max(200, SEHelper.getSESouls(player));
    }

    /** 反射取图腾的最大灵魂数(ITotem#maximumSouls 是公开静态方法)。 */
    private static int maximumSoulsOfTotem(ItemStack totem) {
        try {
            Class<?> itotem = Class.forName("com.Polarice3.Goety.api.items.magic.ITotem");
            Object v = itotem.getMethod("maximumSouls", ItemStack.class).invoke(null, totem);
            return v instanceof Integer i ? i : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    @Override
    public boolean hasSoulContainer(Player player) {
        try {
            if (SEHelper.getSEActive(player)) {
                return true;
            }
            return !TotemFinder.FindTotem(player).isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean increaseSouls(Player player, int amount) {
        if (amount <= 0) {
            return false;
        }
        try {
            SEHelper.increaseSouls(player, amount);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean decreaseSouls(Player player, int amount) {
        if (amount <= 0) {
            return true;
        }
        try {
            if (SEHelper.getSESouls(player) < amount) {
                return false;
            }
            SEHelper.decreaseSouls(player, amount);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public int getSoulGiven(LivingEntity victim) {
        try {
            return SEHelper.getSoulGiven(victim);
        } catch (Throwable t) {
            // 兜底:按最大生命值粗估
            return Math.max(1, (int) (victim.getMaxHealth() / 10.0F));
        }
    }

    // ================================================================
    // 法术属性
    // ================================================================

    @Override
    public double soulDiscountMultiplier(LivingEntity entity) {
        AttributeInstance inst = instance(entity, ModAttributes.SOUL_DISCOUNT.get());
        // Goety 语义:属性值 0 表示无折扣;返回值是「未折扣比例」
        return inst == null ? 1.0D : Math.max(0.0D, 1.0D - inst.getValue());
    }

    @Override
    public double cooldownDiscountMultiplier(LivingEntity entity) {
        AttributeInstance inst = instance(entity, ModAttributes.COOLDOWN_DISCOUNT.get());
        return inst == null ? 1.0D : Math.max(0.0D, 1.0D - inst.getValue());
    }

    @Override
    public void applySoulDiscount(LivingEntity entity, java.util.UUID uuid, String name, double amount) {
        apply(entity, ModAttributes.SOUL_DISCOUNT.get(), uuid, name, amount);
    }

    @Override
    public void applyCooldownDiscount(LivingEntity entity, java.util.UUID uuid, String name, double amount) {
        apply(entity, ModAttributes.COOLDOWN_DISCOUNT.get(), uuid, name, amount);
    }

    @Override
    public void applySpellPotency(LivingEntity entity, java.util.UUID uuid, String name, double amount) {
        apply(entity, ModAttributes.SPELL_POTENCY.get(), uuid, name, amount);
    }

    @Override
    public double spellPotencyTotal(LivingEntity entity) {
        AttributeInstance inst = instance(entity, ModAttributes.SPELL_POTENCY.get());
        return inst == null ? 0.0D : inst.getValue();
    }

    @Override
    public double spellPotencyModifierAmount(LivingEntity entity, java.util.UUID uuid) {
        AttributeInstance inst = instance(entity, ModAttributes.SPELL_POTENCY.get());
        if (inst == null) {
            return 0.0D;
        }
        AttributeModifier m = inst.getModifier(uuid);
        return m == null ? 0.0D : m.getAmount();
    }

    @Override
    public void applySpellDuration(LivingEntity entity, java.util.UUID uuid, String name, double amount) {
        apply(entity, ModAttributes.SPELL_DURATION.get(), uuid, name, amount);
    }

    @Override
    public void applySpellRange(LivingEntity entity, java.util.UUID uuid, String name, double amount) {
        apply(entity, ModAttributes.SPELL_RANGE.get(), uuid, name, amount);
    }

    @Override
    public void applyCastingSpeed(LivingEntity entity, java.util.UUID uuid, String name, double amount) {
        apply(entity, ModAttributes.CASTING_SPEED.get(), uuid, name, amount);
    }

    @Override
    public void applySpellRadius(LivingEntity entity, java.util.UUID uuid, String name, double amount) {
        apply(entity, ModAttributes.SPELL_RADIUS.get(), uuid, name, amount);
    }

    @Override
    public void clearSpellModifiers(LivingEntity entity) {
        for (Attribute attr : spellAttributes()) {
            clearByPrefix(entity, attr, "mage_");
        }
    }

    /** 全部法师会用到的 Goety 法术属性。 */
    private static Attribute[] spellAttributes() {
        return new Attribute[] {
                ModAttributes.SPELL_POTENCY.get(),
                ModAttributes.SPELL_DURATION.get(),
                ModAttributes.SPELL_RANGE.get(),
                ModAttributes.SPELL_RADIUS.get(),
                ModAttributes.CASTING_SPEED.get(),
                ModAttributes.SOUL_DISCOUNT.get(),
                ModAttributes.COOLDOWN_DISCOUNT.get()
        };
    }

    private static AttributeInstance instance(LivingEntity entity, Attribute attr) {
        return attr == null ? null : entity.getAttribute(attr);
    }

    /**
     * 施加一条属性修饰符(复用本模组既有的固定 UUID + 防叠加策略)。
     *
     * <p>{@code SOUL_DISCOUNT} / {@code COOLDOWN_DISCOUNT} 的取值区间是 [-1,1],
     * 用 {@code ADDITION} 叠加语义最贴合 Goety 自身的实现。
     */
    private static void apply(LivingEntity entity, Attribute attr,
                              java.util.UUID uuid, String name, double amount) {
        AttributeInstance inst = instance(entity, attr);
        if (inst == null) {
            return;
        }
        // 先移除同 UUID 的旧修饰符,避免重复施加
        if (inst.getModifier(uuid) != null) {
            inst.removeModifier(uuid);
        }
        if (amount == 0.0D) {
            return;
        }
        AttributeModifier.Operation op = AttributeModifier.Operation.ADDITION;
        inst.addTransientModifier(new AttributeModifier(uuid, name, amount, op));
    }

    private static void clearByPrefix(LivingEntity entity, Attribute attr, String prefix) {
        AttributeInstance inst = instance(entity, attr);
        if (inst == null) {
            return;
        }
        List<AttributeModifier> doomed = inst.getModifiers().stream()
                .filter(m -> m.getName() != null && m.getName().startsWith(prefix))
                .toList();
        for (AttributeModifier m : doomed) {
            inst.removeModifier(m);
        }
    }

    // ================================================================
    // 仆从
    // ================================================================

    @Override
    public boolean isServant(Entity entity) {
        return entity instanceof IOwned;
    }

    @Override
    public boolean isOwnedBy(Entity servant, Player owner) {
        if (!(servant instanceof IOwned owned)) {
            return false;
        }
        try {
            LivingEntity master = owned.getMasterOwner();
            return master != null && master.getUUID().equals(owner.getUUID());
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public LivingEntity masterOf(Entity servant) {
        if (!(servant instanceof IOwned owned)) {
            return null;
        }
        try {
            return owned.getMasterOwner();
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public boolean adoptServant(Entity servant, Player owner) {
        if (!(servant instanceof IOwned owned)) {
            return false;
        }
        try {
            owned.setOwnerId(owner.getUUID());
            owned.setTrueOwner(owner);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public int countServants(Player owner) {
        int count = 0;
        for (Entity e : nearby(owner)) {
            if (isOwnedBy(e, owner)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public List<Entity> servantsOf(Player owner) {
        List<Entity> out = new java.util.ArrayList<>();
        for (Entity e : nearby(owner)) {
            if (!e.isRemoved() && isOwnedBy(e, owner)) {
                out.add(e);
            }
        }
        return out;
    }

    @Override
    public boolean hasLifespan(Entity servant) {
        if (!(servant instanceof IOwned owned)) {
            return false;
        }
        try {
            return owned.hasLifespan();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public int servantLifespan(Entity servant) {
        if (!(servant instanceof IOwned owned)) {
            return -1;
        }
        try {
            return owned.hasLifespan() ? owned.getLifespan() : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    @Override
    public void setServantLifespan(Entity servant, int ticks) {
        if (!(servant instanceof IOwned owned)) {
            return;
        }
        try {
            owned.setHasLifespan(true);
            owned.setLifespan(Math.max(0, ticks));
        } catch (Throwable ignored) {
            // 个别仆从无视寿命,忽略
        }
    }

    @Override
    public void makeServantPermanent(Entity servant) {
        if (!(servant instanceof IOwned owned)) {
            return;
        }
        try {
            owned.setHasLifespan(false);
            owned.setLifespan(-1);
        } catch (Throwable ignored) {
            // 忽略
        }
    }

    @Override
    public void buffServant(Entity servant, int durationTicks,
                            double damageBonus, double speedBonus) {
        if (!(servant instanceof LivingEntity living)) {
            return;
        }
        // 用固定前缀 + UUID 施加到原版属性,由 AttributeGuard 保证不叠加
        try {
            // 攻击伤害加成
            applyToLiving(living, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
                    java.util.UUID.nameUUIDFromBytes(
                            ("lost_realm:mage_servant_dmg:" + living.getUUID()).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    "mage_servant_dmg", damageBonus);
            // 移动速度加成
            applyToLiving(living, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
                    java.util.UUID.nameUUIDFromBytes(
                            ("lost_realm:mage_servant_spd:" + living.getUUID()).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    "mage_servant_spd", speedBonus);
        } catch (Throwable ignored) {
            // 个别生物无该属性,忽略
        }
    }

    private static void applyToLiving(LivingEntity living,
                                      net.minecraft.world.entity.ai.attributes.Attribute attr,
                                      java.util.UUID uuid, String name, double amount) {
        AttributeInstance inst = living.getAttribute(attr);
        if (inst == null) {
            return;
        }
        if (inst.getModifier(uuid) != null) {
            inst.removeModifier(uuid);
        }
        inst.addTransientModifier(new AttributeModifier(
                uuid, name, amount, AttributeModifier.Operation.MULTIPLY_BASE));
    }

    @Override
    public void extendServantLife(Entity servant, int ticks) {
        if (!(servant instanceof IOwned owned)) {
            return;
        }
        try {
            if (owned.hasLifespan()) {
                owned.setLifespan(owned.getLifespan() + ticks);
            }
        } catch (Throwable ignored) {
            // 忽略
        }
    }

    /** 玩家周围一定范围内的全部实体。 */
    private static List<Entity> nearby(Player owner) {
        AABB box = owner.getBoundingBox().inflate(SERVANT_SCAN_RADIUS);
        return owner.level().getEntities(owner, box);
    }

    // ================================================================
    // 魔杖 / 聚晶识别
    // ================================================================

    @Override
    public boolean isWand(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof IWand;
    }

    @Override
    public boolean isFocus(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        // 聚晶都有 Items.FOCUS 一类的基础物品标记;用注册名兜底最稳
        var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && GoetyCompat.MODID.equals(key.getNamespace())
                && key.getPath().contains("focus");
    }

    @Override
    public boolean isHoldingWand(Player player) {
        return isWand(player.getMainHandItem()) || isWand(player.getOffhandItem());
    }

    @Override
    public int wandSoulCost(LivingEntity caster, ItemStack wand) {
        if (!(wand.getItem() instanceof IWand w)) {
            return 0;
        }
        try {
            // IWand#SoulUse(LivingEntity, ItemStack) 已含施法者自身灵魂折扣
            int cost = w.SoulUse(caster, wand);
            return Math.max(0, cost);
        } catch (Throwable t) {
            return 0;
        }
    }

    @Override
    public Object currentSpellType(Player player) {
        try {
            ItemStack wand = wandOf(player);
            if (wand.isEmpty() || !(wand.getItem() instanceof IWand w)) {
                return null;
            }
            // IWand#getSpellType() 返回 Goety 的 SpellType 枚举
            return w.getSpellType();
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public int currentSpellTypeOrdinal(Player player) {
        try {
            Object type = currentSpellType(player);
            // SpellType 是枚举,直接取 ordinal
            return type instanceof Enum<?> e ? e.ordinal() : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    @Override
    public boolean refreshWandCooldown(Player player) {
        try {
            ItemStack wand = wandOf(player);
            if (wand.isEmpty()) {
                return false;
            }
            // FocusCooldown#removeCooldown(player, level, item) 直接清空该物品的冷却
            FocusCooldown cooldowns = SEHelper.getFocusCoolDown(player);
            if (cooldowns == null) {
                return false;
            }
            cooldowns.removeCooldown(player, player.level(), wand.getItem());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 取玩家手持的魔杖(优先主手,其次副手)。 */
    private static ItemStack wandOf(Player player) {
        ItemStack main = player.getMainHandItem();
        if (isWandStatic(main)) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (isWandStatic(off)) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    /** {@link #isWand} 的静态版本,供内部工具方法使用。 */
    private static boolean isWandStatic(ItemStack stack) {
        return stack.getItem() instanceof IWand;
    }

    // ================================================================
    // 效果
    // ================================================================

    @Override
    public boolean applyCurse(LivingEntity target, int durationTicks, int amplifier) {
        MobEffect curse = cursedEffect();
        if (curse == null) {
            return false;
        }
        target.addEffect(new MobEffectInstance(curse, durationTicks, amplifier, false, true));
        return true;
    }

    /** Goety 的诅咒效果(禁止回血)。用反射取,避免编译期强依赖具体类名。 */
    private static MobEffect cursedEffect() {
        for (String name : new String[] {
                "com.Polarice3.Goety.common.effects.GoetyEffects.CURSED",
                "com.Polarice3.Goety.init.ModEffects.CURSED"}) {
            try {
                Class<?> cls = Class.forName(name);
                Object v = cls.getMethod("get").invoke(null);
                if (v instanceof MobEffect effect) {
                    return effect;
                }
            } catch (Throwable ignored) {
                // 试下一个
            }
        }
        return null;
    }
}
