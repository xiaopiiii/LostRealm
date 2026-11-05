package com.lost_realm.item;

import java.util.List;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import com.lost_realm.attribute.RealmAttributes;
import com.lost_realm.config.MedalsConfig;
import com.lost_realm.event.SlashHandler;
import com.lost_realm.util.ModifierIds;

/**
 * 刃终极：<b>「我有一剑！」</b>
 *
 * <h2>三部分</h2>
 * <ol>
 *   <li><b>常驻属性</b>（四条，见 {@link #getDefaultAttributeModifiers}）——
 *       挂在物品的 MAINHAND 默认修饰符上，因此拿起即生效、收起即失效，
 *       不需要任何 tick 刷新逻辑；</li>
 *   <li><b>蓄力</b>（{@link #use} / {@link #onUseTick} / {@link #releaseUsing}）——
 *       Shift + 右键开始，满 10 秒自动斩出，中途松手即取消；</li>
 *   <li><b>剑气</b>（{@link SlashHandler}）—— 结算与动画都在那边，
 *       本类只负责「什么时候挥出去」。</li>
 * </ol>
 *
 * <h2>蓄力为什么用原版的「使用物品」机制</h2>
 * 原版 {@code LivingEntity} 每 tick 会给正在使用的物品发 {@code onUseTick}，
 * 剩余时长归零时自动走 {@code completeUsingItem} → {@link #releaseUsing}。
 * 也就是说：<b>「满时自动斩出」不需要自己写计时器</b> ——
 * {@code releaseUsing} 收到 {@code timeLeft <= 0} 就是"蓄满了"，
 * 收到 {@code timeLeft > 0} 就是"松手了"。两种情形天然分开。
 */
public class IHaveASwordItem extends SwordItem {

    public IHaveASwordItem() {
        super(Tiers.NETHERITE, 3, -2.4F,
                new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    // ================================================================
    // 一、常驻属性
    // ================================================================

    /**
     * 武器自带的攻击伤害 / 攻速 + 四条终极属性。
     *
     * <h2>为什么暴击率用 ADDITION，其余用 MULTIPLY_TOTAL</h2>
     * <ul>
     *   <li><b>暴击率</b>：{@code lost_realm:crit_chance} 是「0~10 的绝对值，
     *       基础 0.05」。+100% 直接加 1.0，即把暴击率顶到 105% —— 刀刀暴击。</li>
     *   <li><b>暴击伤害 / 攻击伤害 / 经验获取</b>：这三条要的是「翻倍」，
     *       而 {@code MULTIPLY_BASE} 只在**属性基础值**上乘（玩家攻击力基础才 1.0，
     *       加 100% 等于加 1 点，塞牙缝都不够）。{@code MULTIPLY_TOTAL} 才是
     *       在算完之后整体乘，语义与"翻倍"一致。</li>
     * </ul>
     *
     * <p>这里刻意不缓存 Multimap：Forge 只在装备/属性刷新时调用本方法
     * （每个玩家每 tick 一次），重建一个 6 条目的 map 的开销可以忽略，
     * 而缓存会让「改了配置要重启才生效」——不划算。
     */
    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        Multimap<Attribute, AttributeModifier> base = super.getDefaultAttributeModifiers(slot);
        if (slot != EquipmentSlot.MAINHAND) {
            return base;
        }
        Multimap<Attribute, AttributeModifier> map = HashMultimap.create();
        map.putAll(base);

        put(map, RealmAttributes.critChance(), ModifierIds.SWORD_CRIT_CHANCE,
                "i_have_a_sword_crit_chance",
                MedalsConfig.pct(MedalsConfig.SWORD_CRIT_CHANCE),
                AttributeModifier.Operation.ADDITION);

        put(map, RealmAttributes.critDamage(), ModifierIds.SWORD_CRIT_DAMAGE,
                "i_have_a_sword_crit_damage",
                MedalsConfig.pct(MedalsConfig.SWORD_CRIT_DAMAGE),
                AttributeModifier.Operation.MULTIPLY_TOTAL);

        put(map, Attributes.ATTACK_DAMAGE, ModifierIds.SWORD_ATTACK_DAMAGE,
                "i_have_a_sword_attack_damage",
                MedalsConfig.pct(MedalsConfig.SWORD_ATTACK_DAMAGE),
                AttributeModifier.Operation.MULTIPLY_TOTAL);

        put(map, RealmAttributes.experienceGained(), ModifierIds.SWORD_EXP_GAINED,
                "i_have_a_sword_exp_gained",
                MedalsConfig.pct(MedalsConfig.SWORD_EXP_GAINED),
                AttributeModifier.Operation.MULTIPLY_TOTAL);

        return map;
    }

    /**
     * 往 map 里放一条本模组命名的修饰符。
     *
     * <p>{@code attribute} 为 null 时整条跳过：Attributeslib 在极早期的加载阶段
     * 可能还没把属性对象交出来，塞一个 null 键进去会让原版遍历时直接 NPE。
     */
    private static void put(Multimap<Attribute, AttributeModifier> map, Attribute attribute,
                            java.util.UUID uuid, String name,
                            double amount, AttributeModifier.Operation op) {
        if (attribute == null) {
            return;
        }
        map.put(attribute, new AttributeModifier(uuid, "lost_realm:" + name, amount, op));
    }

    // ================================================================
    // 二、蓄力
    // ================================================================

    /** 蓄力总时长（tick）——满时原版会自动走 releaseUsing(…, 0)。 */
    @Override
    public int getUseDuration(ItemStack stack) {
        return Math.max(1, MedalsConfig.SWORD_CHARGE_SECONDS.get() * 20);
    }

    /** 举剑蓄力的姿势：用三叉戟那一套（抬起手臂），比拉弓更贴切。 */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    /**
     * 开始蓄力。只有按住 Shift 时才进入 —— 否则右键点一下就会开始读条，
     * 而剑的右键本来该是「什么都不做」。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remaining) {
        if (!(living instanceof Player player)) {
            return;
        }
        int total = getUseDuration(stack);
        int charged = total - remaining;
        if (charged <= 0) {
            return;
        }
        double progress = Math.min(1.0D, charged / (double) total);

        chargeParticles(level, player, progress);

        // 充能音效:每半秒一次,音调随蓄力升高
        if (!level.isClientSide && charged % 10 == 0) {
            level.playSound(null, player.blockPosition(),
                    net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,
                    net.minecraft.sounds.SoundSource.PLAYERS,
                    0.35F, 0.6F + (float) progress);
        }
    }

    /**
     * 蓄力期间绕剑聚集的光点。
     *
     * <p>用 {@code ServerLevel#addParticle}（服务端广播）而不是客户端粒子：
     * 这样旁边的人也能看见你在蓄力 —— 这一剑的威慑力有一半来自"看得见它在攒"。
     */
    private static void chargeParticles(Level level, Player player, double progress) {
        net.minecraft.util.RandomSource random = player.getRandom();
        Vec3 center = player.getEyePosition().add(player.getLookAngle().scale(1.15D));
        // 越接近满,圈收得越紧、粒子越密
        double radius = 1.0D - progress * 0.55D;
        int count = 1 + (int) (progress * 5.0D);

        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            double y = center.y + (random.nextDouble() - 0.5D) * 1.4D;
            level.addParticle(ParticleTypes.END_ROD, x, y, z, 0.0D, 0.0D, 0.0D);
            if (progress > 0.6D) {
                level.addParticle(ParticleTypes.SWEEP_ATTACK, x, y, z, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    /**
     * 松手 / 蓄满。
     *
     * <p>原版把两种情况压成同一个回调，靠 {@code timeLeft} 区分：
     * 归零 = 蓄满了 → 斩；大于零 = 中途松手 → 收剑。
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int timeLeft) {
        if (level.isClientSide || !(living instanceof ServerPlayer player)) {
            return;
        }
        if (timeLeft > 0) {
            return;
        }
        SlashHandler.perform(player);

        int cooldown = MedalsConfig.SWORD_COOLDOWN_SECONDS.get();
        if (cooldown > 0) {
            player.getCooldowns().addCooldown(this, cooldown * 20);
        }
    }

    // ================================================================
    // 三、tooltip
    // ================================================================

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        // ---- 常驻属性 ----
        tooltip.add(Component.translatable("item.lost_realm.i_have_a_sword.tip.attr.1",
                        MedalsConfig.SWORD_CRIT_CHANCE.get(),
                        MedalsConfig.SWORD_CRIT_DAMAGE.get())
                .withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("item.lost_realm.i_have_a_sword.tip.attr.2",
                        MedalsConfig.SWORD_ATTACK_DAMAGE.get(),
                        MedalsConfig.SWORD_EXP_GAINED.get())
                .withStyle(ChatFormatting.BLUE));

        // ---- 技能 ----
        tooltip.add(Component.translatable("item.lost_realm.i_have_a_sword.tip.skill.1",
                        MedalsConfig.SWORD_CHARGE_SECONDS.get())
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.lost_realm.i_have_a_sword.tip.skill.2",
                        fmt(MedalsConfig.SWORD_REACH.get()),
                        MedalsConfig.SWORD_DAMAGE.get())
                .withStyle(ChatFormatting.GRAY));
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v)
                : String.valueOf(Math.round(v * 10.0D) / 10.0D);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
