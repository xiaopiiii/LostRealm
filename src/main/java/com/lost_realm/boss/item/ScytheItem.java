package com.lost_realm.boss.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

/**
 * 遗落之主的死神镰 —— 一把真正的<b>武器</b>（dv31）。
 *
 * <h2>dv30 → dv31 改了什么</h2>
 * dv30 只是个装饰物品，且 display 缩放拉到 4.0 —— GUI 里直接超出物品栏、
 * 拿在手上都看不全。dv31 按用户要求改成正经武器并全面回调：
 * <ul>
 *   <li><b>攻击伤害 64</b>、<b>攻击速度 1.0</b>（见 {@link #TIER} 与注册处）</li>
 *   <li>贴图原样使用参考图（256×256 透明底 PNG，一个像素都没动）</li>
 *   <li>模型回到标准 {@code item/generated}，GUI 用默认缩放 —— 图标正好铺满格子</li>
 *   <li>第三人称缩放 4.0 → <b>2.0</b>（Goety 镰刀模板 2.36 / 原版剑 0.85），
 *       拿在手上能看全整把刀</li>
 * </ul>
 *
 * <h2>攻击速度 1.0 的换算</h2>
 * {@link SwordItem} 的 attackSpeed 参数是<b>加算修饰符</b>，玩家基础攻速是 4.0：
 * 原版钻石剑传 {@code -2.4} → 最终 1.6。所以要最终 1.0 就得传 {@code -3.0}
 * （在注册处写，这里传的是修饰符而不是显示值）。
 */
public class ScytheItem extends SwordItem {

    /**
     * 死神镰的材质档：本体伤害由注册处直接给（64），
     * 材质档不再追加（{@link #getAttackDamageBonus()} 返回 0），
     * 保证「攻击伤害 64」是准确的显示值。
     */
    public static final Tier TIER = new Tier() {
        @Override public int getUses() {
            return 2765;                       // 与下界合金同级
        }

        @Override public float getSpeed() {
            return 12.0F;
        }

        @Override public float getAttackDamageBonus() {
            return 0.0F;                       // ★ 不追加，伤害完全由注册处决定
        }

        @Override public int getEnchantmentValue() {
            return 22;
        }

        @Override public int getLevel() {
            return 3;                          // 下界合金级
        }

        @Override public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }
    };

    public ScytheItem(Tier tier, int attackDamage, float attackSpeed, Properties props) {
        super(tier, attackDamage, attackSpeed, props);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lost_realm.warden_scythe.tip.1")
                .withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("item.lost_realm.warden_scythe.tip.2")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
