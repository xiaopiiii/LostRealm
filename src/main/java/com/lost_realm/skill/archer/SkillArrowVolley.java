package com.lost_realm.skill.archer;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import com.lost_realm.config.MedalsConfig;
import com.lost_realm.data.MedalSource;
import com.lost_realm.data.PlayerMedals;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;
import com.lost_realm.util.ArrowFinder;

/**
 * Lv.60【三发】(被动)
 *
 * <p>射出箭矢时,在原箭上下各偏 1~3 度额外射出 2 根箭矢(合计 <b>三发</b>),
 * 额外箭矢伤害为原箭的 1/4,并强制突破受击无敌帧独立结算
 * (按序号递减 100% / 50% / 25%)。
 *
 * <p><b>兼容多重射击</b>:数量不变,仍为三发,但三根箭的伤害各 +25%。
 *
 * <h2>实现要点</h2>
 * <ul>
 *   <li>挂接 {@code ArrowLooseEvent}:原箭由原版生成,本技能只负责补射额外箭矢与打标记。</li>
 *   <li><b>必须延后到本 tick 的任务队列再补射。</b>
 *       Forge 把 {@code onArrowLoose} 注入在 {@code BowItem#releaseUsing} 里
 *       {@code createArrow(...)} <b>之前</b> —— 事件触发时原箭还不存在,
 *       同步去「找原箭」永远返回 {@code null},技能就完全不生效。
 *       所以这里只做参数校验,真正的补射丢给 {@code server.execute(...)},
 *       那时原箭已 {@code addFreshEntity} 且 {@code tickCount} 极小,可以稳定取到。</li>
 *   <li>额外箭矢完整复刻原箭的「速度、底伤(含力量附魔)、暴击、击退、火矢、穿透、弹药效果」,
 *       因此原版弓的全部附魔都会自然生效。</li>
 *   <li>额外箭矢一律 {@code Pickup.DISALLOWED}:它们是凭空补射出来的,
 *       允许拾取等于给玩家一台造箭机。</li>
 *   <li>无敌帧突破:额外箭矢命中后,由 {@code CombatHandler} 读取标记,
 *       在 {@code LivingHurtEvent} 中以 {@code LivingEntity#invulnerableTime} 归零的方式绕过。</li>
 * </ul>
 */
public class SkillArrowVolley implements Skill {

    /** 技能 ID 保持历史值(s20_*),便于旧存档的技能状态键平滑沿用 */
    public static final String ID = "s20_arrow_volley";
    /** 箭矢 NBT 标记键:是否为本技能生成的额外箭矢 */
    public static final String NBT_EXTRA = "medals_extra_volley";
    /** 箭矢 NBT 键:额外箭矢的伤害倍率(如 0.25) */
    public static final String NBT_DAMAGE_MULT = "medals_volley_mult";
    /** 箭矢 NBT 键:该额外箭矢的序号,用于 100%/50%/25% 递减 */
    public static final String NBT_INDEX = "medals_volley_index";

    @Override
    public MedalType medal() {
        return MedalType.ARCHER;
    }

    @Override
    public int unlockLevel() {
        return 60;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component displayName() {
        return Component.translatable("skill.lost_realm.s20_arrow_volley");
    }

    @Override
    public List<Component> description() {
        //   ⚠️ 实参个数必须与 lang 里 skill.lost_realm.s20_arrow_volley.desc 的
        //      %s 个数**严格一致** —— Component 走的是 String.format，
        //      参数少了会直接抛 MissingFormatArgumentException 让 tooltip 崩掉。
        return List.of(Component.translatable("skill.lost_realm.s20_arrow_volley.desc",
                MedalsConfig.S20_MULTISHOT_BONUS.get()));
    }

    @Override
    public void applyPassive(ServerPlayer player, int level) {
        // 发射时结算(见 CombatHandler#onArrowLoose)
    }

    // ================================================================
    // 发射结算
    // ================================================================

    /**
     * 玩家松开弓弦时调用,补射 2 根额外箭矢。
     *
     * <p><b>本方法只做校验与排队</b>,真正的补射在 {@link #fireVolley} 里进行,
     * 原因见类注释(事件早于原版生成箭矢)。
     *
     * @param player 射手
     * @param data   勋章数据
     * @param charge 蓄力 tick 数(原版 {@code BowItem} 语义)
     * @param stack  手持的弓
     */
    public static void onArrowLoose(ServerPlayer player, PlayerMedals data, int charge, ItemStack stack) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(stack.getItem() instanceof BowItem)) {
            return;
        }
        if (MedalSource.level(player, MedalType.ARCHER) < 60) {
            return;
        }

        // 原版:蓄力不足(松手过早)时不射出箭矢,本技能同样不补射
        float power = BowItem.getPowerForTime(charge);
        if (power < 0.1F) {
            return;
        }

        // 复制一份弓,避免任务执行时手上物品已变化
        ItemStack bow = stack.copy();
        level.getServer().execute(() -> fireVolley(player, bow, charge, power));
    }

    /**
     * 真正的补射(已延后到本 tick 的任务队列,原箭此时已存在)。
     *
     * @param player 射手
     * @param bow    发射时手持的弓(副本)
     * @param charge 蓄力 tick 数
     * @param power  蓄力比例(0~1)
     */
    private static void fireVolley(ServerPlayer player, ItemStack bow, int charge, float power) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (player.isRemoved()) {
            return;
        }

        // 原箭:由原版刚刚生成,是本技能所有属性复刻的模板
        AbstractArrow found = ArrowFinder.findFresh(player,
                a -> !a.getPersistentData().getBoolean(NBT_EXTRA));
        if (!(found instanceof Arrow primary)) {
            // 原版这次并没有射出箭(被取消 / 没弹药),不应凭空空射
            return;
        }

        ItemStack ammo = player.getProjectile(bow);
        boolean infinite = isInfinite(bow);
        boolean creative = player.getAbilities().instabuild;
        if (ammo.isEmpty() && !infinite && !creative) {
            return;
        }
        if (ammo.isEmpty()) {
            ammo = new ItemStack(Items.ARROW);
        }

        // 消耗弹药:原箭那 1 根由原版扣,这里补扣额外箭矢的部分
        boolean noConsume = (creative || infinite) && ammo.is(Items.ARROW);
        if (!noConsume) {
            int need = getSecondShotCount();
            for (int i = 0; i < need; i++) {
                player.getInventory().removeItem(ammo.copyWithCount(1));
            }
        }

        // 以视线为轴、在竖直平面内偏转,得到「上下张开」的扇面
        Vec3 look = player.getLookAngle();
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 side = look.cross(up);
        if (side.lengthSqr() < 1.0E-6D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        }
        side = side.normalize();

        double speed = primary.getDeltaMovement().length();
        if (speed < 1.0E-4D) {
            speed = power * 3.0D;
        }

        // ★ 这里要的是「额外补射几根」,不是「合计几根」——
        //   早先误用了 getExtraCount()(=3 合计),于是在原版那 1 根之外又补了 3 根,
        //   玩家看到的是四发。弹药的账(上面那行)当初反而写对了,所以是「扣 2 根、射 3 根」。
        int count = getSecondShotCount();
        for (int i = 0; i < count; i++) {
            // 第一根向上偏、第二根向下偏,后续交替
            double sign = (i % 2 == 0) ? 1.0D : -1.0D;
            double deg = spreadForIndex(i);
            Vec3 dir = rotate(look, side, sign * Math.toRadians(deg));

            Arrow extra = buildArrow(level, player, ammo, primary, dir, speed, power);
            extra.getPersistentData().putBoolean(NBT_EXTRA, true);
            extra.getPersistentData().putDouble(NBT_DAMAGE_MULT, multForIndex(i));
            extra.getPersistentData().putInt(NBT_INDEX, i + 1);
            extra.getPersistentData().putString(ExtraArrowMode.NBT_KEY, ExtraArrowMode.VOLLEY.name());
            // 补射出来的箭不回收,否则玩家站着不动就能刷箭
            extra.pickup = AbstractArrow.Pickup.DISALLOWED;

            level.addFreshEntity(extra);
        }

        // 兼容多重射击:原箭加伤(数量不变,仍是三发)
        double bonus = MedalsConfig.pct(MedalsConfig.S20_MULTISHOT_BONUS);
        if (bonus > 0.0D) {
            primary.getPersistentData().putDouble(NBT_DAMAGE_MULT, 1.0D + bonus);
        }
    }

    /**
     * 三发<b>合计</b>箭数：1 根原箭 + 2 根额外箭。
     *
     * <p>⚠️ 这是"总数"，不是"要补射的数量"。要补射几根请用 {@link #getSecondShotCount()} ——
     * 把它直接塞进补射循环里会射出四根（这个坑踩过一次）。
     */
    public static int getExtraCount() {
        return 3;
    }

    /** 额外补射的箭数（= 合计 3 − 原版那 1 根 = 2）。 */
    public static int getSecondShotCount() {
        return getExtraCount() - 1;
    }

    /** 第 {@code i} 根额外箭矢的偏角(度),由配置范围取值。 */
    private static double spreadForIndex(int i) {
        int min = MedalsConfig.S20_SPREAD_MIN_DEG.get();
        int max = Math.max(min, MedalsConfig.S20_SPREAD_MAX_DEG.get());
        // 前两根固定取范围中值,保证视觉上稳定「上下各一发」;后续在范围内随机
        if (i < 2) {
            return min + (max - min) * 0.5D;
        }
        return min + Math.random() * Math.max(0.0D, (max - min));
    }

    /**
     * 额外箭矢的伤害倍率(按索引递减)。
     *
     * @param index 第几根额外箭矢(从 0 开始)
     * @return 相对原箭的伤害倍率
     */
    public static double multForIndex(int index) {
        double base = MedalsConfig.pct(MedalsConfig.S20_EXTRA_ARROW_DAMAGE); // 0.25
        return switch (index) {
            case 0 -> base;           // 第一根:25%(相对原箭),即"递减 100%"
            case 1 -> base * 0.5D;    // 第二根:12.5%(递减 50%)
            default -> base * 0.25D;  // 第三根:6.25%(递减 25%)
        };
    }

    /** 是否由本技能生成。 */
    public static boolean isExtraArrow(net.minecraft.world.entity.projectile.AbstractArrow arrow) {
        return arrow.getPersistentData().getBoolean(NBT_EXTRA);
    }

    // ================================================================
    // 工具
    // ================================================================

    /**
     * 复刻一支与「原箭」同源的箭矢。
     *
     * <p>直接复用原箭实例的属性(暴击、穿透、击退、底伤,底伤里已含力量附魔),
     * 因此弓上的力量 / 冲击 / 火矢 / 无限等附魔全部自然生效。
     */
    private static Arrow buildArrow(ServerLevel level, ServerPlayer player, ItemStack ammo,
                                    Arrow primary, Vec3 dir, double speed, float power) {
        Arrow arrow = new Arrow(level, player);
        // 出生点与原箭完全一致(玩家眼高),只让方向偏开
        arrow.setPos(primary.getX(), primary.getY(), primary.getZ());

        // 一点点速度扰动,让三发在视觉上自然散开而非完全重叠
        double jittered = speed * (0.98D + level.getRandom().nextDouble() * 0.04D);
        arrow.shoot(dir.x, dir.y, dir.z, (float) jittered, 0.0F);

        // 继承原箭的结算属性
        arrow.setBaseDamage(primary.getBaseDamage());
        arrow.setCritArrow(primary.isCritArrow());
        arrow.setKnockback(primary.getKnockback());
        if (primary.getPierceLevel() > 0) {
            arrow.setPierceLevel(primary.getPierceLevel());
        }
        if (primary.isOnFire()) {
            arrow.setSecondsOnFire(100);
        }
        // 弹药外观(药箭 / 光灵箭)
        if (!ammo.isEmpty()) {
            try {
                arrow.setEffectsFromItem(ammo);
            } catch (Throwable ignored) {
                // 普通箭矢无效果,忽略
            }
        }
        // 原箭被某些模组替换成自定义箭实体时,至少保证「拉满 = 暴击」这条语义
        if (!primary.isCritArrow() && power >= 1.0F) {
            arrow.setCritArrow(true);
        }
        return arrow;
    }

    private static boolean isInfinite(ItemStack bow) {
        return bow.getEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.INFINITY_ARROWS) > 0;
    }

    /**
     * 将方向向量绕 {@code axis} 旋转 {@code angle} 弧度。
     */
    private static Vec3 rotate(Vec3 v, Vec3 axis, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double dot = v.dot(axis);
        // 罗德里格旋转公式
        double x = v.x * cos + (axis.y * v.z - axis.z * v.y) * sin + axis.x * dot * (1.0D - cos);
        double y = v.y * cos + (axis.z * v.x - axis.x * v.z) * sin + axis.y * dot * (1.0D - cos);
        double z = v.z * cos + (axis.x * v.y - axis.y * v.x) * sin + axis.z * dot * (1.0D - cos);
        return new Vec3(x, y, z).normalize();
    }

    /** 记录本技能在 {@code CombatHandler} 中的无敌帧突破标记键。 */
    public static final String NBT_BYPASS = "medals_volley_bypass";

    /** 供 {@code CombatHandler} 读取额外箭矢伤害倍率,非额外箭矢时返回 0。 */
    public static double damageMultiplierOf(AbstractArrow arrow) {
        if (arrow == null) {
            return 0.0D;
        }
        double mult = arrow.getPersistentData().getDouble(NBT_DAMAGE_MULT);
        if (mult <= 0.0D) {
            return 0.0D;
        }
        return mult;
    }

    /** 便捷:判断某支箭是否由本技能(非箭雨)生成。 */
    public static boolean isVolleyArrow(AbstractArrow arrow) {
        ExtraArrowMode mode = ExtraArrowMode.parse(
                arrow.getPersistentData().getString(ExtraArrowMode.NBT_KEY));
        return mode == ExtraArrowMode.VOLLEY || (mode == null && isExtraArrow(arrow));
    }

    /** 保留给 {@code CombatHandler} 使用的辅助方法(避免重复计算攻击者)。 */
    public static LivingEntity ownerOf(AbstractArrow arrow) {
        return arrow.getOwner() instanceof LivingEntity le ? le : null;
    }
}
