package com.lost_realm.boss.udunos.move;

import javax.annotation.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.lost_realm.boss.udunos.UdunosBolt;
import com.lost_realm.boss.udunos.UdunosBoss;
import com.lost_realm.boss.udunos.UdunosPhase;
import com.lost_realm.boss.udunos.UdunosTuning;

/**
 * 一个<b>招式</b> —— 乌蠹诺斯战斗的最小可组合单元。
 *
 * <h2>为什么要拆出来</h2>
 * 这份设计的终局形态是<b>三十多个招式</b>。如果全塞在 {@code UdunosBrain}
 * 的一个 {@code switch} 里，那个类会长到两千行、每加一招都要动同一处地方，
 * 而且"哪些招属于哪个阶段"这种信息会散落得到处都是。
 *
 * <p>拆成独立类之后，加一招就是<b>新增一个文件</b>：
 * <pre>
 *   public final class MoveXxx extends UdunosMove {
 *       public MoveKind kind()      { return MoveKind.XXX; }
 *       public boolean availableIn(UdunosPhase p) { return p == UdunosPhase.ONE; }
 *       public void begin(Player t) { ... }
 *       public Vec3 tick(Player t)  { ...; return 本tick头部速度; }
 *   }
 * </pre>
 *
 * <h2>契约</h2>
 * <ul>
 *   <li>{@link #tick} 返回<b>本 tick 头部应当具有的速度</b>（格/tick）。
 *       返回 {@link Vec3#ZERO} 表示"这一招让头部停住"——
 *       比如碎星播撒要求 Boss 悬停着撒弹幕。</li>
 *   <li>把 {@link #finished} 置为 true 表示招式演完，控制权交回巡游。
 *       <b>必须确保它一定会被置位</b>：原设计明确说"每一招都必须完成规定波数
 *       和主要危险弹幕生命周期，禁止因为固定计时太短而截断招式"，
 *       反过来说也成立 —— 招式不能卡住不结束，那样 Boss 会僵在原地。</li>
 *   <li>招式<b>不负责</b>选自己什么时候上；那是 {@code UdunosBrain} 的事。</li>
 * </ul>
 *
 * <h2>关于"每招都要有前摇"</h2>
 * 原设计的核心承诺是"高难度但有可理解的预警与可执行的躲避路径"。
 * 落到实现上就是：<b>任何会造成伤害的东西，都必须先被画出来一段时间</b>。
 * 招式的 {@code begin} 到实际开火之间，至少要有一段可读的预警。
 */
public abstract class UdunosMove {

    /** 招式种类标号 —— 客户端靠它决定画什么（走同步数据，不走类名）。 */
    public enum MoveKind {
        /** 无招式（巡游/开场）。 */
        NONE,
        /** 噬光冲锋。 */
        CHARGE,
        /** 碎星播撒。 */
        SCATTER,
        /** 星弧巡猎。 */
        HUNT,
        /** 星门贯穿。 */
        GATE,
        /** 引力残星。 */
        GRAVITY,
        /** 裂星十字核。 */
        CROSS,
        /** 囚星天环。 */
        RING,
        /** 尾刃刻痕。 */
        TAIL_LASER,
        /** 星核脉冲。 */
        PULSE,
        /** 逆轨流星。 */
        METEOR,
        /** 崩星审判。 */
        JUDGEMENT,
        /** 裂界回廊。 */
        CORRIDOR,
        /** 延迟星痕（阶段四）。 */
        SCAR
    }

    protected final UdunosBoss boss;

    /** 本招已持续的 tick 数。 */
    protected int ticks;

    /** 招式是否已演完。 */
    protected boolean finished;

    /** 本 tick 头部应当具有的速度。 */
    protected Vec3 velocity = Vec3.ZERO;

    protected UdunosMove(UdunosBoss boss) {
        this.boss = boss;
    }

    // ================================================================
    // 子类必须实现
    // ================================================================

    /** 招式种类（用于客户端渲染与日志）。 */
    public abstract MoveKind kind();

    /** 唯一 id —— 用于"一轮之内不重复选招"的判重。 */
    public abstract String id();

    /** 该招式是否在指定阶段可用。 */
    public abstract boolean availableIn(UdunosPhase phase);

    /** 开始。此时应完成所有初始状态的建立。 */
    public abstract void begin(@Nullable Player target);

    /**
     * 推进一个 tick。
     *
     * @return 本 tick 头部速度；{@link Vec3#ZERO} 表示让头部停住
     */
    public abstract Vec3 tick(@Nullable Player target);

    /** 招式的显示名语言键（用于调试与转场提示）。 */
    public abstract String langKey();

    /**
     * 本招是否让星核暴露（玩家的输出窗口）。
     *
     * <p>原设计第三阶段要求"并有短暂星核暴露窗口"。围圈类招式会让星核
     * 裸在体外，此时 Boss 的动态减伤近乎失效 —— 玩家该做的是<b>放弃躲避、
     * 转为输出</b>。没有这个窗口，第三阶段就只是"更密的弹幕"，
     * 缺少"该反击了"的节奏反转。
     */
    public boolean exposesCore() {
        return false;
    }

    /**
     * 当前是否<b>不该造成接触伤害</b>。
     *
     * <p>原设计对围圈的要求："准备期间保护玩家，围好后身体和尾部恢复接触伤害。"
     * 环布阶段头部以 3.4 格/tick 掠过整圈，玩家站在圈内也可能被扫到 ——
     * 那一段属于"演出"而不是"考验"，所以关掉接触伤害。
     *
     * <p>默认 false；只有需要演出保护的招式才覆写。
     */
    public boolean suppressesContact() {
        return false;
    }

    // ================================================================
    // 通用
    // ================================================================

    public boolean isFinished() {
        return finished;
    }

    public int ticks() {
        return ticks;
    }

    public Vec3 velocity() {
        return velocity;
    }

    /**
     * 进入本招 —— 由 Brain 在 begin 前调用，把计时归零。
     *
     * <p><b>刻意保留 {@link #velocity} 不清零。</b>
     * 招式刚接上的那几 tick 往往还没有位移（准备动作），
     * 若这里把速度清掉，210 格的身体会"啪"地钉住一瞬 ——
     * 那正是玩家说的"突然停顿一下，像是技能搞的鬼"。
     * 留着上一招的惯性，由 {@code UdunosBrain} 决定怎么衰减，
     * 视觉上就变成"滑一小段再站稳"。
     */
    public final void enter(@Nullable Player target) {
        this.ticks = 0;
        this.finished = false;
        this.begin(target);
    }

    /** 被子类在 {@link #tick} 里调用，把本 tick 的速度记下来。 */
    protected final void move(Vec3 v) {
        this.velocity = v;
    }

    /**
     * 结束本招。
     *
     * <p><b>刻意不把 {@link #velocity} 清零</b>：冲锋演完时头部还带着惯性，
     * 归零会让这条 96 格的虫在最后一刻"啪"地刹住 —— 那正是原设计
     * 第 3.6 条要避免的"无预警的急停"。让速度自然交还给巡游的转向逻辑去消化。
     */
    protected final void finish() {
        this.finished = true;
        this.onFinished();
    }

    /**
     * 被强行打断（阶段转场、Boss 死亡）。等同 finish，但语义上是"没演完"。
     */
    public final void cancel() {
        this.finished = true;
        this.onFinished();
    }

    /**
     * 招式结束时的清理钩子。
     *
     * <p>专门给"招式期间生成了临时实体"的招用（星门、限制圈、审判柱……）——
     * 不清理的话，转阶段或 Boss 死亡时这些东西会留在场上。
     * 默认什么都不做。
     */
    protected void onFinished() {
        // 默认空实现
    }

    // ================================================================
    // 给子类用的小工具
    // ================================================================

    /** 把速度朝目标方向转，每 tick 最多转 {@code maxAngle} 弧度。 */
    protected static Vec3 turnTowards(Vec3 current, Vec3 desired, double maxAngle, double minSpeed) {
        double len = desired.length();
        if (len < 1.0E-6D) {
            return current;
        }
        if (current.lengthSqr() < 1.0E-8D) {
            return desired;
        }
        Vec3 from = current.normalize();
        Vec3 to = desired.scale(1.0D / len);
        double angle = Math.acos(net.minecraft.util.Mth.clamp(from.dot(to), -1.0D, 1.0D));
        Vec3 dir = (angle <= maxAngle || angle < 1.0E-6D)
                ? to
                : from.lerp(to, maxAngle / angle).normalize();
        return dir.scale(Math.max(len, minSpeed));
    }

    /**
     * 沿一个水平圆做切向运动 —— 围圈类招式（囚星天环 / 星核脉冲 / 裂界回廊）共用。
     *
     * <p>做法是"切向速度 + 径向修正"：主体沿切线走，同时用一个正比于
     * "离圆周还有多远"的分量把头部拉回圆上。这比每帧直接 {@code setPos} 到
     * 圆周上要好 —— 后者会让身体瞬间被拽成数学圆，而原设计特意要求
     * "不要让整条身体随头部刚性旋转，不要把整条虫瞬间摆成数学圆"。
     *
     * @param center    圆心（水平面）
     * @param radius    目标半径
     * @param speed     切向速度
     * @param direction +1 / -1，决定绕行方向
     */
    protected Vec3 orbitTowards(Vec3 center, double radius, double speed, double direction) {
        return orbitTowards(center, radius, speed, direction, UdunosTuning.CRUISE_TURN);
    }

    /**
     * 同上，但可以指定每 tick 的最大转向角。
     *
     * <h2>为什么需要这个重载</h2>
     * 绕圈的角速度 = 切向速度 / 半径。巡航时 1.44 / 32 ≈ 0.045 rad/tick，
     * 远小于 {@code CRUISE_TURN}（0.07），所以永远转得过来。
     * 但「环布」阶段要用 3.43 格/tick —— 角速度 0.107 rad/tick，
     * <b>超过 0.07 的限幅</b>：头部根本跟不上圆，会一边加速一边外扩，
     * 绕成一圈螺旋，身体永远闭不上环。
     *
     * <p>所以环布阶段必须放开转向限幅（不超过 0.15，否则看起来像瞬移）。
     */
    protected Vec3 orbitTowards(Vec3 center, double radius, double speed, double direction,
                                double maxTurn) {
        Vec3 from = this.boss.position().subtract(center);
        Vec3 radial = new Vec3(from.x, 0.0D, from.z);
        if (radial.lengthSqr() < 1.0E-6D) {
            radial = new Vec3(radius, 0.0D, 0.0D);
        } else {
            radial = radial.normalize().scale(radius);
        }
        Vec3 targetPos = center.add(radial.x, 0.0D, radial.z);

        // 切向：水平面内垂直于半径
        Vec3 tangent = new Vec3(-radial.z, 0.0D, radial.x).normalize().scale(direction);
        // 径向修正（限幅），离得远拉得快，贴近了就基本不修正
        Vec3 correction = targetPos.subtract(this.boss.position());
        double pull = Math.min(1.0D, correction.length() / Math.max(2.0D, radius * 0.35D));
        Vec3 desired = tangent.add(correction.normalize().scale(pull * 0.8D));
        if (desired.lengthSqr() < 1.0E-6D) {
            desired = tangent;
        }
        return turnTowards(this.velocity, desired.normalize().scale(speed),
                maxTurn, speed * 0.65D);
    }

    /**
     * 把单位向量 {@code base} 绕世界 Y 轴旋转 {@code angle} 弧度，得到扇形里的一个方向。
     *
     * <p>用来把"瞄准玩家"的那一发摊成一个扇形 —— 原设计要求
     * "每点至少一发瞄准玩家"，所以扇形要<b>以瞄准方向为中心</b>展开，
     * 而不是随便挑个角度开始铺。
     */
    protected static Vec3 fanAround(Vec3 base, double angle) {
        Vec3 horizontal = new Vec3(base.x, 0.0D, base.z);
        if (horizontal.lengthSqr() < 1.0E-8D) {
            horizontal = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            horizontal = horizontal.normalize();
        }
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double x = horizontal.x * cos - horizontal.z * sin;
        double z = horizontal.x * sin + horizontal.z * cos;
        // 保留一点原始俯仰，让扇形不是严格水平的
        double y = base.y * 0.6D;
        Vec3 dir = new Vec3(x, y, z);
        return dir.lengthSqr() < 1.0E-8D ? base : dir.normalize();
    }

    /** 从头部指向目标玩家胸口位置的单位向量。 */
    @Nullable
    protected Vec3 aimAt(@Nullable Player target) {
        if (target == null) {
            return null;
        }
        Vec3 center = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        Vec3 dir = center.subtract(boss.position());
        return dir.lengthSqr() < 1.0E-6D ? null : dir.normalize();
    }

    /** 距离玩家多远。没有目标时返回 {@link Double#MAX_VALUE}。 */
    protected double distanceTo(@Nullable Player target) {
        return target == null ? Double.MAX_VALUE : boss.position().distanceTo(target.position());
    }

    /**
     * 场上还能再容纳多少发星弹。
     *
     * <p>用"还够不够打一轮"而不是"精确数字"，是因为招式要的是
     * 一个干脆的 yes/no：够就照常齐射，不够就这轮跳过。
     * 半途减量反而更糟 —— 玩家看到预警线铺开、弹幕却稀稀拉拉，
     * 会以为是自己卡了。
     */
    protected int boltBudgetLeft() {
        if (!(this.boss.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return 0;
        }
        int live = UdunosBolt.countNear(level, this.boss.position(), 220.0D);
        return Math.max(0, UdunosTuning.MAX_LIVE_BOLTS - live);
    }
}
