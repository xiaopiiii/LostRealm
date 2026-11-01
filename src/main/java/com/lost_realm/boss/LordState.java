package com.lost_realm.boss;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.BossEvent;

/**
 * 遗落之主的<b>状态机</b> —— 取代旧版 {@link WardenPhase} 的「血量阈值驱动」。
 *
 * <h2>为什么改用独立状态机而不是枚举阶段</h2>
 * 新流程里存在大量「血量无关」的阶段:
 * <ul>
 *   <li>{@link #DORMANT 零阶段} —— 完全无敌的站桩对话态</li>
 *   <li>{@link #ENRAGED_GHOST 幽灵态} —— 无敌、极速、打一击就跑的演出</li>
 *   <li>{@link #WAVES 波次态} —— Boss 本体隐身,玩家打小怪</li>
 * </ul>
 * 这些状态无法用「血量百分比」表达,而且形态推进改为<b>锁血驱动</b>
 * (血量将 ≤1 时锁定并转阶段),不再看血量阈值。
 * 因此把「阶段」升级为「状态机」,由实体同步、由事件显式驱动。
 *
 * <h2>合法转移</h2>
 * <pre>
 * DORMANT ──选决斗──► ENRAGED_GHOST ──命中后──► WAVES ──5波清空──► FORM_1
 * FORM_1 ──血量将≤1──► TRANSITION_1 ──回满+演出结束──► FORM_2
 * FORM_2 ──血量将≤1──► TRANSITION_2 ──回满+演出结束──► FORM_3
 * FORM_3 ──血量≤0──► DEFEATED
 * （DORMANT 选「离开」:留在 DORMANT）
 * </pre>
 * 其余转移一律由防御性代码忽略 —— 防止网络抖动或读档异常把状态机打乱。
 *
 * <h2>外观元数据</h2>
 * 「模型变化」由几何 + 动画 + 三件轻量手段组合表达:
 * <ol>
 *   <li>{@code scale() 体型缩放} —— ⚠️<b>只影响碰撞箱</b>,
 *       原版渲染管线不消费 {@code Entity#getScale()}。
 *       模型要跟着放大,必须由渲染器缩放 {@code PoseStack}
 *       (见 {@code WardenBossRenderer#preRender})</li>
 *   <li>{@link #glowColor() 发光描边色} —— {@code setGlowingTag(true)}
 *       + {@code PlayerTeam#setColor}</li>
 *   <li>{@link #particle() 粒子光环} —— {@code aiStep} 里定期撒粒子</li>
 * </ol>
 *
 * <p><b>真正的造型差异来自 GeckoLib 几何</b>({@code geo/warden_form1|2|3.geo.json}):
 * 第二形态长出 6 条触手与脊椎骨刺,第三形态是 8 条触手 + 骨翼。
 * 形态到几何的映射见 {@code WardenBossModel#getModelResource}。
 * */
public enum LordState {

    // ================================================================
    // 零阶段 —— 开场白
    // ================================================================

    /**
     * 零阶段:站在城中心,无敌、无血条、可右键对话。
     *
     * <p>「零阶段就相当于开场白,上不上都无所谓」—— 用户原话。
     * 因此它不计入三形态,外观沿用第一形态。
     */
    DORMANT("dormant",
            "boss.lost_realm.lord.state.dormant",
            BossEvent.BossBarColor.PURPLE,
            /* scale */ 1.00F,
            ChatFormatting.LIGHT_PURPLE,
            ParticleTypes.SCULK_SOUL,
            /* barVisible */ false),

    // ================================================================
    // 幽灵态 —— 决斗演出的「一击必杀」
    // ================================================================

    /**
     * 幽灵态:决斗姿态。无敌、无血条、速度极快、手持木剑,
     * 冲向玩家后<b>强制命中一次</b>扣除其 50% 最大生命值,随后消失。
     *
     * <p>体型略小、配高频 {@code PORTAL} 粒子,模拟「残影」的视觉。
     */
    ENRAGED_GHOST("enraged_ghost",
            "boss.lost_realm.lord.state.ghost",
            BossEvent.BossBarColor.PURPLE,
            /* scale */ 0.95F,
            ChatFormatting.DARK_PURPLE,
            ParticleTypes.PORTAL,
            /* barVisible */ false),

    // ================================================================
    // 波次态 —— Boss 隐身,玩家打 5 波小怪
    // ================================================================

    /**
     * 波次态:Boss 本体暂时退场,由 {@link WaveManager} 刷出多波怪物攻击玩家。
     *
     * <p>此状态下 Boss 实体仍存在(承载状态机),但被设为不可见、
     * 无敌、无 AI 目标,且不会推进任何战斗逻辑。
     *
     * <p><b>注意</b>:本状态是纯内存态。服务器重启后读档时若处于
     * {@code WAVES},{@code WardenBoss#readAdditionalSaveData} 会直接跳到
     * {@link #FORM_1},避免玩家被卡在「打不完的波次」里。
     */
    WAVES("waves",
            "boss.lost_realm.lord.state.waves",
            BossEvent.BossBarColor.PURPLE,
            /* scale */ 1.00F,
            ChatFormatting.DARK_GRAY,
            ParticleTypes.SMOKE,
            /* barVisible */ false),

    // ================================================================
    // 三形态 —— 真正可打的战斗
    // ================================================================

    /** 第一形态:正常伤害、正常速度、有血条。 */
    FORM_1("form_1",
            "boss.lost_realm.lord.form.1",
            BossEvent.BossBarColor.PURPLE,
            /* scale */ 1.00F,
            ChatFormatting.LIGHT_PURPLE,
            ParticleTypes.SOUL_FIRE_FLAME,
            /* barVisible */ true),

    /**
     * 第一形态的锁血演出:血量锁为 1 → 无敌 → 逐渐回满 → 换外观。
     *
     * <p>期间 Boss 定格、免疫一切伤害,玩家看到的是一段「它不肯倒下」的演出。
     *
     * <p>⚠️ 这里的 {@code scale} 只是<b>起始值</b> —— 演出期间体型会
     * 由 {@code WardenBoss#tickTransition} 从「上一形态」动态插值到
     * 「下一形态」,让玩家看到它<b>一边回血一边长大</b>。
     * 这正是「换阶段要换建模」的可视化落点(本模组零自定义渲染器,
     * 体型缩放 + 描边色 + 粒子是仅有的三种造型手段)。
     */
    TRANSITION_1("transition_1",
            "boss.lost_realm.lord.state.transition",
            BossEvent.BossBarColor.RED,          // 提前显出新形态(第二形态)的颜色
            /* scale */ 1.00F,
            ChatFormatting.RED,                  // 描边同步提前变红,见 LordGlowTeams
            ParticleTypes.END_ROD,
            /* barVisible */ true),

    /** 第二形态:体型更大、伤害更高。 */
    FORM_2("form_2",
            "boss.lost_realm.lord.form.2",
            BossEvent.BossBarColor.RED,
            // ★ dv21：三形态体型完全一致（用户要求「换阶段不会变大」），
            //   阶段差异只体现在「长出来的触手数量」，不再靠体型表意。
            /* scale */ 1.00F,
            ChatFormatting.RED,
            ParticleTypes.SCULK_SOUL,
            /* barVisible */ true),

    /** 第二形态的锁血演出。 */
    TRANSITION_2("transition_2",
            "boss.lost_realm.lord.state.transition",
            BossEvent.BossBarColor.WHITE,        // 提前显出新形态(第三形态)的颜色
            // ★ dv21：演出期也不再放大（体型三形态恒定）。
            /* scale */ 1.00F,
            ChatFormatting.WHITE,
            ParticleTypes.END_ROD,
            /* barVisible */ true),

    /** 第三形态:最大体型、最强伤害 —— 只有在这里才能被真正击杀。 */
    FORM_3("form_3",
            "boss.lost_realm.lord.form.3",
            BossEvent.BossBarColor.WHITE,
            // ★ dv21：同 FORM_2，体型与一阶段一致。
            /* scale */ 1.00F,
            ChatFormatting.WHITE,
            ParticleTypes.SOUL,
            /* barVisible */ true),

    // ================================================================
    // 终态
    // ================================================================

    /** 已死,等待清理。 */
    DEFEATED("defeated",
            "boss.lost_realm.lord.state.defeated",
            BossEvent.BossBarColor.WHITE,
            /* scale */ 1.00F,
            ChatFormatting.DARK_GRAY,
            ParticleTypes.ASH,
            /* barVisible */ false);

    private final String id;
    private final String langKey;
    private final BossEvent.BossBarColor barColor;
    private final float scale;
    private final ChatFormatting glowColor;
    private final ParticleOptions particle;
    private final boolean barVisible;

    LordState(String id, String langKey, BossEvent.BossBarColor barColor,
              float scale, ChatFormatting glowColor, ParticleOptions particle,
              boolean barVisible) {
        this.id = id;
        this.langKey = langKey;
        this.barColor = barColor;
        this.scale = scale;
        this.glowColor = glowColor;
        this.particle = particle;
        this.barVisible = barVisible;
    }

    public String id() {
        return id;
    }

    /** 状态 / 形态的显示名语言键。 */
    public String langKey() {
        return langKey;
    }

    public BossEvent.BossBarColor barColor() {
        return barColor;
    }

    /**
     * 体型缩放系数(1.0 = 实体注册时的原始尺寸)。
     *
     * <p>⚠️ <b>它只影响碰撞箱,不影响模型。</b>
     * {@code LivingEntity#getDimensions} 内部会 {@code .scale(getScale())},
     * 因此本模组<b>不覆写</b> {@code getDimensions}(否则会二次缩放成平方)。
     *
     * <p>但原版渲染管线<b>不读这个方法</b> —— 已用 {@code javap} 验证
     * {@code LivingEntityRenderer#render} 的字节码里没有 {@code getScale} 调用。
     * 模型要跟着变大,由 {@code WardenBossRenderer#preRender} 缩放 {@code PoseStack},
     * 用的也是同一个数值来源,从而保证「视觉尺寸 == 判定尺寸」。
     */
    public float scale() {
        return scale;
    }

    /** 发光描边的颜色(通过计分板队伍实现,无需渲染器)。 */
    public ChatFormatting glowColor() {
        return glowColor;
    }

    /** 该状态下的常驻粒子(在 {@code aiStep} 里按节流频率撒)。 */
    public ParticleOptions particle() {
        return particle;
    }

    /** 该状态下是否应当显示 Boss 血条。 */
    public boolean barVisible() {
        return barVisible;
    }

    /** 是否为战斗形态(血条可见 + 可受伤)。 */
    public boolean isCombatForm() {
        return this == FORM_1 || this == FORM_2 || this == FORM_3;
    }

    /** 是否为锁血演出态。 */
    public boolean isTransition() {
        return this == TRANSITION_1 || this == TRANSITION_2;
    }

    /** 该状态下 Boss 是否应免疫一切伤害。 */
    public boolean isInvulnerableState() {
        return this == DORMANT || this == ENRAGED_GHOST || this == WAVES
                || isTransition() || this == DEFEATED;
    }

    /** 锁血演出结束后应进入的下一形态(null 表示不适用)。 */
    public LordState nextFormAfterTransition() {
        return switch (this) {
            case TRANSITION_1 -> FORM_2;
            case TRANSITION_2 -> FORM_3;
            default -> null;
        };
    }

    /** 当前形态对应的锁血演出态(null 表示该形态不会被锁血)。 */
    public LordState transitionFor() {
        return switch (this) {
            case FORM_1 -> TRANSITION_1;
            case FORM_2 -> TRANSITION_2;
            default -> null;
        };
    }

    /** 按序号取状态(用于状态存档与网络同步)。 */
    public static LordState byIndex(int i) {
        LordState[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }
}
