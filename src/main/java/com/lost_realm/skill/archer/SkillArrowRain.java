package com.lost_realm.skill.archer;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import com.lost_realm.config.MedalsConfig;
import com.lost_realm.data.PlayerMedals;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;

/**
 * Lv.50【箭雨】(被动)
 *
 * <p>箭矢击中目标时,有 <b>20%</b> 概率在目标头顶上方 <b>6 格</b>、
 * <b>3×3</b> 方块范围内随机掉落 <b>4~6</b> 根箭矢,垂直下落砸向目标区域。
 *
 * <p>实现位于 {@link #trigger} ——由 {@code CombatHandler#onProjectileHit} 调用。
 * 每根箭雨箭矢都带有 {@link ExtraArrowMode#RAIN} 标记与固定伤害倍率,
 * 由 {@code CombatHandler} 统一结算,并复用「突破无敌帧」机制,避免多根箭只生效一次。
 */
public class SkillArrowRain implements Skill {

    public static final String ID = "s25_arrow_rain";
    /** 箭雨箭矢的下落初速度(向下,格/tick) */
    private static final double FALL_SPEED = 1.6D;
    /** 每根箭雨箭矢相对原箭的伤害倍率 */
    public static final double RAIN_DAMAGE_MULT = 0.35D;

    @Override
    public MedalType medal() {
        return MedalType.ARCHER;
    }

    @Override
    public int unlockLevel() {
        return 50;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component displayName() {
        return Component.translatable("skill.lost_realm.s25_arrow_rain");
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("skill.lost_realm.s25_arrow_rain.desc",
                MedalsConfig.S25_RAIN_CHANCE.get(),
                MedalsConfig.S25_RAIN_HEIGHT.get(),
                MedalsConfig.S25_RAIN_AREA.get(),
                MedalsConfig.S25_RAIN_MIN_ARROWS.get(),
                MedalsConfig.S25_RAIN_MAX_ARROWS.get()));
    }

    @Override
    public void applyPassive(ServerPlayer player, int level) {
        // 命中时结算(见 CombatHandler#onProjectileHit)
    }

    // ================================================================
    // 命中结算
    // ================================================================

    /**
     * 箭矢击中目标时尝试触发【箭雨】。
     *
     * @param shooter 射手(箭矢主人)
     * @param data    勋章数据
     * @param victim  被击中的目标
     * @param arrow   命中的箭矢
     * @return 是否成功生成箭雨
     */
    public static boolean trigger(ServerPlayer shooter, PlayerMedals data,
                                  LivingEntity victim, AbstractArrow arrow) {
        if (!(victim.level() instanceof ServerLevel level)) {
            return false;
        }
        if (com.lost_realm.data.MedalSource.level(shooter, MedalType.ARCHER) < 25) {
            return false;
        }
        // 箭雨箭矢自身命中不再触发箭雨,避免无限递归
        ExtraArrowMode mode = ExtraArrowMode.parse(
                arrow.getPersistentData().getString(ExtraArrowMode.NBT_KEY));
        if (mode == ExtraArrowMode.RAIN) {
            return false;
        }
        if (shooter.getRandom().nextDouble() >= MedalsConfig.pct(MedalsConfig.S25_RAIN_CHANCE)) {
            return false;
        }

        int min = MedalsConfig.S25_RAIN_MIN_ARROWS.get();
        int max = Math.max(min, MedalsConfig.S25_RAIN_MAX_ARROWS.get());
        int count = min + shooter.getRandom().nextInt(max - min + 1);
        int area = Math.max(1, MedalsConfig.S25_RAIN_AREA.get());
        int height = Math.max(1, MedalsConfig.S25_RAIN_HEIGHT.get());

        // 落点中心:目标脚下方块(箭雨砸向目标所在位置)
        BlockPos center = victim.blockPosition();
        double baseDamage = Math.max(2.0D, arrow.getBaseDamage());
        int half = area / 2;

        for (int i = 0; i < count; i++) {
            // 3×3 范围:在以目标为中心、边长 area 的方块网格内随机取格
            int dx = shooter.getRandom().nextInt(area) - half;
            int dz = shooter.getRandom().nextInt(area) - half;
            double x = center.getX() + dx + 0.15D + shooter.getRandom().nextDouble() * 0.7D;
            double z = center.getZ() + dz + 0.15D + shooter.getRandom().nextDouble() * 0.7D;
            double y = center.getY() + height;

            Arrow rainArrow = new Arrow(level, shooter);
            rainArrow.setPos(x, y, z);
            // 垂直下落,带极轻微水平抖动
            Vec3 dir = new Vec3(
                    (shooter.getRandom().nextDouble() - 0.5D) * 0.08D,
                    -FALL_SPEED,
                    (shooter.getRandom().nextDouble() - 0.5D) * 0.08D).normalize();
            rainArrow.shoot(dir.x, dir.y, dir.z, (float) FALL_SPEED, 0.0F);
            rainArrow.setBaseDamage(baseDamage * RAIN_DAMAGE_MULT);
            // 不继承原箭暴击,落点箭不再触发火矢等过度效果
            rainArrow.setCritArrow(false);

            rainArrow.getPersistentData().putBoolean(SkillArrowVolley.NBT_EXTRA, true);
            rainArrow.getPersistentData().putDouble(SkillArrowVolley.NBT_DAMAGE_MULT, 1.0D);
            rainArrow.getPersistentData().putString(ExtraArrowMode.NBT_KEY, ExtraArrowMode.RAIN.name());
            // 箭雨是凭空降下的,不允许拾取 —— 否则玩家挨一顿砸就能白捡一背包箭
            rainArrow.pickup = AbstractArrow.Pickup.DISALLOWED;

            level.addFreshEntity(rainArrow);
        }

        // 视觉与音效反馈
        level.playSound(null, center, net.minecraft.sounds.SoundEvents.ARROW_HIT_PLAYER,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.9F, 0.6F);
        return true;
    }

    /** 便捷:判断某支箭是否为本技能生成的箭雨箭矢。 */
    public static boolean isRainArrow(AbstractArrow arrow) {
        return ExtraArrowMode.parse(arrow.getPersistentData().getString(ExtraArrowMode.NBT_KEY))
                == ExtraArrowMode.RAIN;
    }

    /** 生成一支用于测试的普通箭(保留备用)。 */
    static ItemStack defaultAmmo() {
        return new ItemStack(Items.ARROW);
    }
}
