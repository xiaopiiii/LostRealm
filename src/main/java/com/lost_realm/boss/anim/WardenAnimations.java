package com.lost_realm.boss.anim;

import net.minecraft.world.entity.LivingEntity;

import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import com.lost_realm.boss.LordState;
import com.lost_realm.boss.entity.WardenBoss;

/**
 * 遗落之主的动画决策 —— <b>动画控制器与几何文件是两套独立的资产</b>,
 * 这里只负责「什么状态下播哪条动画」。
 *
 * <h2>与形态几何的配合</h2>
 * 几何由 {@code WardenBossModel#getModelResource} 按形态切换
 * (form1 / form2 / form3),动画文件名也随形态切换。
 * <b>但动画名(动画 id)是固定的</b> —— 三套动画文件里都叫
 * {@code animation.warden.idle} 等,因此实体侧的控制器<b>不需要知道</b>
 * 当前是哪套几何,只按状态选动画即可。
 *
 * <p>这是刻意设计:若让实体侧也感知形态,换几何时会需要重建控制器,
 * 容易出现「动画卡在旧形态」的问题。把形态差异全部收敛到
 * 模型侧({@code getModelResource} / {@code getAnimationResource}),
 * 实体侧只表达「语义状态」(待机 / 走 / 打 / 咆哮)。
 *
 * <h2>dv14 的两处修正</h2>
 *
 * <h3>① 「出手动作太统一」</h3>
 * dv13 只有一条 {@code animation.warden.attack},所有形态共用
 * 同一套抬臂下劈 —— 玩家连着看三次就腻。dv14 拆成三条手势:
 * <table border="1">
 *   <tr><th>动画</th><th>特征</th></tr>
 *   <tr><td>{@code attack_sweep}</td><td><b>横扫</b> —— 右臂从外侧向内扫,身体反向扭转(横向)</td></tr>
 *   <tr><td>{@code attack_smash}</td><td><b>下砸</b> —— 双臂高举垂直砸落(纵向)</td></tr>
 *   <tr><td>{@code attack_lash}</td><td><b>触手抽击</b> —— 左右两侧触手交替甩出(form2/3 专属)</td></tr>
 * </table>
 * 由本类的 {@link #nextAttackIndex()} 按「攻击序号 % 3」轮换,
 * 同一场战斗里几乎不会连续看到同一个手势。
 *
 * <h3>② 攻击 / 受击动画「形同虚设」</h3>
 * dv13 的 {@code onState} 只看「形态 + 是否移动」,
 * 于是 {@code ATTACK} / {@code HURT} 这两个常量<b>永远不会被返回</b> ——
 * 写了动画却没接线。dv14 引入一次性动作队列:
 * 实体在出手/受击时调用 {@code WardenBoss#playAction(RawAnimation, int)},
 * 控制器优先播它,播完自动回落到待机/走。
 *
 * <h2>状态到动画的映射(常驻部分)</h2>
 * <table border="1">
 *   <tr><th>实体状态</th><th>动画</th></tr>
 *   <tr><td>TRANSITION_2</td><td>animation.warden.phase_awaken(骨翼展开)</td></tr>
 *   <tr><td>TRANSITION_1</td><td>animation.warden.phase_roar(触手波浪爆发)</td></tr>
 *   <tr><td>DORMANT / WAVES / DEFEATED</td><td>animation.warden.idle</td></tr>
 *   <tr><td>ENRAGED_GHOST</td><td>animation.warden.walk(高速冲脸)</td></tr>
 *   <tr><td>移动中</td><td>animation.warden.walk</td></tr>
 *   <tr><td>其它</td><td>animation.warden.idle</td></tr>
 * </table>
 */
public final class WardenAnimations {

    // ================================================================
    // 常驻动画
    // ================================================================

    public static final RawAnimation IDLE =
            RawAnimation.begin().thenLoop("animation.warden.idle");
    public static final RawAnimation WALK =
            RawAnimation.begin().thenLoop("animation.warden.walk");
    public static final RawAnimation DEATH =
            RawAnimation.begin().thenPlay("animation.warden.death");
    /** 换阶段一:触手波浪爆发(形态 1 → 2)。 */
    public static final RawAnimation PHASE_ROAR =
            RawAnimation.begin().thenPlay("animation.warden.phase_roar");
    /** 换阶段二:骨翼展开(形态 2 → 3)。 */
    public static final RawAnimation PHASE_AWAKEN =
            RawAnimation.begin().thenPlay("animation.warden.phase_awaken");

    // ================================================================
    // 一次性动作队列(dv14)
    // ================================================================

    /**
     * 三条攻击手势 —— 由 {@link #nextAttack(WardenBoss)} 轮流返回。
     *
     * <p>注意 {@code attack_lash} 在 form1 的动画文件里是
     * 「退化版」(等同于 sweep),因为第一形态没有触手。
     * 这样实体侧不需要知道形态,永远可以从这三个里挑。
     */
    private static final RawAnimation[] ATTACKS = {
            RawAnimation.begin().thenPlay("animation.warden.attack_sweep"),
            RawAnimation.begin().thenPlay("animation.warden.attack_smash"),
            RawAnimation.begin().thenPlay("animation.warden.attack_lash"),
    };

    public static final RawAnimation HURT =
            RawAnimation.begin().thenPlay("animation.warden.hurt");

    // ================================================================
    // 控制器
    // ================================================================

    /**
     * 主控制器:按 {@link LordState} + 一次性动作队列选动画。
     *
     * <p>注意这里读的是 {@code getLordState()}(同步数据),
     * 因此客户端与服务端看到的是同一个状态 —— 动画不会提前或滞后一个形态。
     */
    public static <T extends WardenBoss> PlayState onState(AnimationState<T> state) {
        T entity = state.getAnimatable();
        LordState st = entity.getLordState();

        // ---- ① 换阶段演出:最高优先级,绝不被覆盖 ----
        switch (st) {
            case TRANSITION_2 -> {
                return state.setAndContinue(PHASE_AWAKEN);
            }
            case TRANSITION_1 -> {
                return state.setAndContinue(PHASE_ROAR);
            }
            default -> { /* 继续往下 */ }
        }

        // ---- ② 死亡 ----
        if (entity.isDeadOrDying()) {
            return state.setAndContinue(DEATH);
        }

        // ---- ③ 待机态:在城里站着没打架 ----
        switch (st) {
            case DORMANT, WAVES, DEFEATED -> {
                return state.setAndContinue(IDLE);
            }
            default -> { /* 继续往下 */ }
        }

        // ---- ④ 一次性动作(攻击 / 受击)----
        // WardenBoss 每 tick 递减 actionTicks,归零后自然回落到 ⑤/⑥
        if (entity.actionTicks > 0) {
            return state.setAndContinue(entity.currentAction);
        }

        // ---- ⑤ 幽灵态是"高速冲脸"的演出,固定播走路动画 ----
        if (st == LordState.ENRAGED_GHOST) {
            return state.setAndContinue(WALK);
        }

        // ---- ⑥ 常规:移动播走、静止播待机 ----
        if (isMoving(entity)) {
            return state.setAndContinue(WALK);
        }
        return state.setAndContinue(IDLE);
    }

    /**
     * 取「下一条」攻击手势(轮换)。
     *
     * <p>用实体的攻击计数器取模,保证同一场战斗里三条手势依次出现,
     * 而不是随机 —— 随机会出现「连着两次下砸」的重复观感。
     */
    public static RawAnimation nextAttack(WardenBoss boss) {
        int i = boss.getAttackCounter();
        return ATTACKS[Math.floorMod(i, ATTACKS.length)];
    }

    /** 攻击手势的条数(供调试 / 自检). */
    public static int attackVariantCount() {
        return ATTACKS.length;
    }

    /** 是否在水平移动(用速度判定,比比较位置更稳)。 */
    private static boolean isMoving(LivingEntity e) {
        double dx = e.getX() - e.xo;
        double dz = e.getZ() - e.zo;
        return dx * dx + dz * dz > 1.0E-5D;
    }

    private WardenAnimations() {}
}
