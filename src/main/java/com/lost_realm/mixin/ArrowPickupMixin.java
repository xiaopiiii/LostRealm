package com.lost_realm.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Items;

import com.lost_realm.config.MedalsConfig;

/**
 * {@link AbstractArrow} 注入:<b>G24 无限弓射出的箭 = 不可拾取(落地即消失)</b>。
 *
 * <h2>锚点选择:为什么是 {@code setKnockback}</h2>
 * {@code BowItem.releaseUsing} 对箭矢的操作序列(字节码偏移):
 * <pre>
 *   267  arrow.shootFromRotation(...)          // 设定初速度
 *   280  arrow.setCritArrow(...)
 *   301  arrow.getBaseDamage()
 *   316  arrow.setBaseDamage(...)
 *   337  arrow.setKnockback(...)               // ★ 本类注入点
 *   354  arrow.setSecondsOnFire(...)
 *   378  player.getAbilities().instabuild
 *   414  arrow.pickup = CREATIVE_ONLY          // 原版内联覆写
 *   420  level.addFreshEntity(arrow)           // 加入世界
 * </pre>
 * 需要满足两个约束:
 * <ol>
 *   <li><b>锚点必须是原版方法、且在 {@code AbstractArrow} 上直接声明、有稳定 SRG 名</b>
 *       —— 三者缺一不可,否则 refmap 无法生成、运行时静默失效。
 *       两个踩过的坑:
 *       <ul>
 *         <li>{@code Level#addFreshEntity} —— 在 1.20.1 里其实是
 *             {@code LevelWriter} 上的 Forge 追加接口方法,{@code Level} 类上<b>查不到</b>,
 *             注入它不会产生 refmap 条目。</li>
 *         <li>{@code setSecondsOnFire} —— 虽然 BowItem 会调用,
 *             但它继承自 {@code Entity},<b>并未在 {@code AbstractArrow} 上声明</b>,
 *             作为 Mixin 目标会解析失败。</li>
 *       </ul>
 *       {@code setKnockback} 三条全中:原版方法、{@code AbstractArrow} 自有、
 *       SRG {@code m_36735_}(已从 {@code mcp_mappings.tsrg} 逐行核对)。</li>
 *   <li><b>时机要尽可能靠后</b> —— 越靠后越不容易被后续逻辑覆盖。</li>
 * </ol>
 *
 * <h2>原版 putfield 会不会盖掉我们的设置?</h2>
 * 会 —— 偏移 414 的 {@code putfield} 在 337 之后执行。但它<b>只在特定条件下</b>触发:
 * <pre>
 *   if (instabuild || ammo.is(SPECTRAL_ARROW) || ammo.is(TIPPED_ARROW))
 *       arrow.pickup = CREATIVE_ONLY;
 * </pre>
 * 而本功能生效的前提是:玩家<b>不是</b>创造模式(创造模式本就不缺箭),
 * 且哨兵箭是普通箭({@code Items.ARROW}),不满足后两个 {@code is(...)} 条件。
 * <b>因此这个 if 在本路径下恒不成立,不会覆盖我们的写入。</b>
 * 这是经过完整条件推演得到的结论,而非"大概不会"。
 *
 * <h2>{@code DISALLOWED} 与 {@code CREATIVE_ONLY} 的区别</h2>
 * <ul>
 *   <li>{@code CREATIVE_ONLY} —— 创造模式玩家<b>仍能捡起</b>,只是生存玩家无效。
 *       依然是"能捡",不符合"落地即消失"。</li>
 *   <li>{@code DISALLOWED} —— <b>任何人都不能捡</b>,命中方块后不生成掉落物。
 *       这正是需求要的表现。</li>
 * </ul>
 *
 * <h2>判定条件:为什么用"背包无箭"识别哨兵箭</h2>
 * 只要玩家背包里还有普通箭,{@code Player#getProjectile} 就会命中原版分支、
 * 返回<b>真实</b>箭矢,{@code PlayerProjectileMixin} 连哨兵都不会创建。
 * 因此"发射者背包无箭"恰好等价于"这一枚是哨兵箭" ——
 * 无需往箭矢上打任何自定义标记,也不依赖 NBT。
 *
 * <p>四重前置条件(功能开关 / 是玩家所有 / 非创造模式 / 背包无箭)共同保证:
 * 只有"无限弓 + 无箭"这一条新路径会被影响。
 */
@Mixin(AbstractArrow.class)
public abstract class ArrowPickupMixin {

    @Inject(method = "setKnockback", at = @At("RETURN"))
    private void medals$sentinelArrowNoPickup(int knockback, CallbackInfo ci) {
        if (!MedalsConfig.QOL_INFINITE_BOW_NO_ARROW.get()) {
            return;
        }
        AbstractArrow self = (AbstractArrow) (Object) this;

        // 只处理玩家射出的箭
        if (!(self.getOwner() instanceof Player player)) {
            return;
        }
        // 创造 / 旁观模式玩家射出的箭,原版语义本就是"不可回收",不越权处理
        if (player.getAbilities().instabuild) {
            return;
        }
        // 背包里还有箭 → 这是原版正常射出的真实箭矢,保持原样
        if (medals$hasArrowInInventory(player)) {
            return;
        }
        // 背包无箭 → 这一枚是哨兵箭:设为不可拾取(落地即消失)
        self.pickup = AbstractArrow.Pickup.DISALLOWED;
    }

    /** 遍历背包(含主副手),判断是否还存在普通箭。 */
    private static boolean medals$hasArrowInInventory(Player player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(Items.ARROW)) {
                return true;
            }
        }
        return false;
    }
}
