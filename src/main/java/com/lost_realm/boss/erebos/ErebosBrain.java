package com.lost_realm.boss.erebos;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.lost_realm.boss.erebos.move.ErebosMove;
import com.lost_realm.boss.erebos.move.MoveCharge;
import com.lost_realm.boss.erebos.move.MoveCorePulse;
import com.lost_realm.boss.erebos.move.MoveCorridor;
import com.lost_realm.boss.erebos.move.MoveCrossCore;
import com.lost_realm.boss.erebos.move.MoveGravityStar;
import com.lost_realm.boss.erebos.move.MoveReverseMeteor;
import com.lost_realm.boss.erebos.move.MoveStarGate;
import com.lost_realm.boss.erebos.move.MoveStarHunt;
import com.lost_realm.boss.erebos.move.MoveStarJudgement;
import com.lost_realm.boss.erebos.move.MoveStarRing;
import com.lost_realm.boss.erebos.move.MoveStarScar;
import com.lost_realm.boss.erebos.move.MoveStarScatter;
import com.lost_realm.boss.erebos.move.MoveTailLaser;

/**
 * Erebos 的<b>行为调度</b> —— 什么时候巡游、什么时候出招、出哪一招。
 *
 * <h2>职责边界（beta4 起重划）</h2>
 * 上一版所有行为都挤在这个类里，只能容纳两三个招式。现在它只管三件事：
 * <ol>
 *   <li><b>开场</b>（{@link Action#EMERGE}）—— 从生成点逐节钻出。</li>
 *   <li><b>巡游</b>（{@link Action#CRUISE}）—— 在玩家周围较远处不规则游走，
 *       时长由阶段决定（10 / 8 / 3 / 0 秒）。</li>
 *   <li><b>选招与执行</b>（{@link Action#MOVE}）—— 从招式池里挑一个交给它演，演完回巡游。</li>
 * </ol>
 * 具体招式怎么打，全部在 {@code move/} 包下的 {@link ErebosMove} 子类里 ——
 * 加一招就是加一个文件，不用动这里。
 *
 * <h2>选招的两条规矩</h2>
 * <ol>
 *   <li><b>按阶段过滤。</b>碎星播撒和星弧巡猎是第一阶段专属；噬光冲锋全阶段可用。
 *       招式自己声明 {@code availableIn(phase)}。</li>
 *   <li><b>不连续出同一招。</b>原设计对第三阶段的要求是"一轮内不重复，两轮交界避免
 *       连续同招"—— 这里把它提前到<b>所有阶段</b>统一执行。理由很实际：
 *       连着两次冲锋是一回事，连着两次碎星播撒就是玩家站着发呆看两遍。</li>
 * </ol>
 *
 * <h2>为什么巡游时长逐阶段缩短</h2>
 * 原设计 10 / 8 / 3 / 0 秒。前三阶段是"喘息时间"，第四阶段<b>直接归零</b>——
 * 收尾阶段不允许有停手的时候。这个设计意图原样保留：
 * {@link ErebosPhase#cruiseTicks()} 返回 0 时，巡游会立刻转入选招。
 */
public final class ErebosBrain {

    /** 顶层行为。 */
    public enum Action {
        /** 开场钻出（逐节展开，无敌、不伤害玩家）。 */
        EMERGE,
        /** 巡游。 */
        CRUISE,
        /** 正在执行一个招式。 */
        MOVE
    }

    private final ErebosBoss boss;

    /** 全部招式实例。服务端用它选招；客户端不 tick 它，只是持有。 */
    private final List<ErebosMove> pool = new ArrayList<>();

    private Action action = Action.EMERGE;
    private int ticks;
    private Vec3 velocity = Vec3.ZERO;

    @Nullable
    private Vec3 cruiseTarget;

    /**
     * 到达巡游目标后的"再飞一会儿"计数。
     *
     * <p>没有它的时候，一进到 {@code CRUISE_ARRIVE} 半径内就立刻重选目标，
     * 于是每 2~3 秒一次急转弯 —— 玩家描述的"动来动去毫无规律""抽搐"
     * 有一半是这么来的：不是走得乱，是被频繁打断。
     */
    private int cruiseHold;

    @Nullable
    private ErebosMove current;

    /** 上一招的 id —— 用于"不连续出同一招"。 */
    @Nullable
    private String lastMoveId;

    /**
     * 开场盘绕的圆心 —— 就是出场地（召唤瞬间的头部位置）。
     *
     * <p>头部绕着它转，所以整场登场期间 Boss 都停在玩家附近，
     * 不会像旧版那样直线飞出 96 格外。详见 {@link ErebosTuning#EMERGE_RADIUS}。
     */
    private Vec3 emergeCenter = Vec3.ZERO;

    /**
     * 盘绕角度（弧度）—— <b>自己累加的，不每 tick 从坐标反算</b>。
     *
     * <p>反算会踩坑：{@code atan2} 的值域只有 ±180°，转过半圈就会翻转一次，
     * 累积角度被打回去 —— 结果是"绕了两圈却只记下 155°"，
     * 身体铺开的形状也会跟着错乱。（这一版在离线模拟里抓到过。）
     */
    private double emergeAngle;

    /** 当前盘绕半径（格）—— 同样是状态，随目标半径平滑逼近。 */
    private double emergeRadius;

    /**
     * 建立招式池。
     *
     * <p>这里登记的是<b>全部</b>招式；哪一招在当前阶段能用，由招式自己
     * 通过 {@code availableIn(phase)} 声明。这样"阶段与招式的对应关系"
     * 就写在招式定义里，而不是散在这个调度类里 ——
     * 加新招式时不需要动这里以外的任何文件。
     *
     * <h2>各阶段实际会抽到哪些</h2>
     * <ul>
     *   <li><b>阶段一</b>：噬光冲锋 / 碎星播撒 / 星弧巡猎（3 种）</li>
     *   <li><b>阶段二</b>：噬光冲锋 / 星门贯穿 / 引力残星 / 星弧巡猎（4 种）</li>
     *   <li><b>阶段三</b>：噬光冲锋 / 星门贯穿 + 裂星十字核 / 囚星天环 /
     *       星核脉冲 / 逆轨流星 / 尾刃刻痕 / 崩星审判 / 裂界回廊（9 种）</li>
     *   <li><b>阶段四</b>：噬光冲锋 / 星门贯穿 / 延迟星痕（3 种，且没有巡游间隔）</li>
     * </ul>
     */
    public ErebosBrain(ErebosBoss boss) {
        this.boss = boss;

        // ---- 全阶段通用的移动方式 ----
        this.pool.add(new MoveCharge(boss));          // 噬光冲锋（1/2/3/4）
        this.pool.add(new MoveStarGate(boss));        // 星门贯穿（2/3/4）

        // ---- 第一阶段：教学三招 ----
        this.pool.add(new MoveStarScatter(boss));     // 碎星播撒（1）
        this.pool.add(new MoveStarHunt(boss));        // 星弧巡猎（1）

        // ---- 第二阶段 ----
        this.pool.add(new MoveGravityStar(boss));     // 引力残星（2）

        // ---- 第三阶段：战斗主体，八招 ----
        this.pool.add(new MoveCrossCore(boss));       // 裂星十字核（3）
        this.pool.add(new MoveStarRing(boss));        // 囚星天环（3）
        this.pool.add(new MoveCorePulse(boss));       // 星核脉冲（3）
        this.pool.add(new MoveReverseMeteor(boss));   // 逆轨流星（3）
        this.pool.add(new MoveTailLaser(boss));       // 尾刃刻痕（3）
        this.pool.add(new MoveStarJudgement(boss));   // 崩星审判（3）
        this.pool.add(new MoveCorridor(boss));        // 裂界回廊（3）

        // ---- 第四阶段：高压收尾 ----
        this.pool.add(new MoveStarScar(boss));        // 延迟星痕（4）
    }

    // ================================================================
    // 对外只读
    // ================================================================

    public Action action() {
        return action;
    }

    public int ticks() {
        return ticks;
    }

    /** 头部当前速度向量（格/tick）。 */
    public Vec3 velocity() {
        return velocity;
    }

    /** 招式池只读视图 —— 仅供 {@code /lrsdebug} 查看"本阶段到底能用几招"。 */
    public java.util.List<ErebosMove> poolView() {
        return java.util.Collections.unmodifiableList(this.pool);
    }

    @Nullable
    public ErebosMove current() {
        return current;
    }

    // ================================================================
    // 主循环
    // ================================================================

    public void tick(@Nullable Player target) {
        this.ticks++;

        if (target == null) {
            // 开场钻出也必须能在无目标时结束。
            // 否则一旦玩家在召唤后立刻走开（退游戏、被传送），Boss 会永远停在
            // EMERGE —— 而 EMERGE 是"无敌"的，等于场上多了个打不动的摆设。
            if (this.action == Action.EMERGE) {
                tickEmerge(null);
                return;
            }
            // 没有目标时，正在演的招式仍要推进 —— 否则它会永远卡在 MOVE 状态出不来
            if (this.action == Action.MOVE && this.current != null) {
                this.velocity = this.current.tick(null);
                if (this.current.isFinished()) {
                    this.current = null;
                    to(Action.CRUISE);
                }
                return;
            }
            this.velocity = this.velocity.scale(0.90D);
            return;
        }

        switch (this.action) {
            case EMERGE -> tickEmerge(target);
            case CRUISE -> tickCruise(target);
            case MOVE -> tickMove(target);
        }
    }

    private void to(Action next) {
        this.action = next;
        this.ticks = 0;
    }

    // ================================================================
    // 开场
    // ================================================================

    /**
     * 开场 —— <b>盘绕展开</b>。
     *
     * <h2>为什么是盘绕，不是直线</h2>
     * 身体全长 96 格，头部必须真走满 96 格才能铺开。若走直线，
     * 登场结束时 Boss 必然停在 96 格外 —— 玩家站在出场地根本看不见它
     * （而且一旦玩家是创造/旁观、不在索敌范围内，转巡游后它也不会回来，
     * 就永远停在那边了）。所以让头部<b>绕着出场地转</b>：
     * 走满 96 格弧长正好盘成一整圈，全程都在玩家 15 格上下。
     *
     * <h2>每 tick 干什么</h2>
     * 把"下一 tick 该在哪"算成一个目标点，再取"当前位置 → 目标点"作为位移。
     * 目标点 = 圆心 + 极坐标(半径按进度扩张, 角度按线速度推进)。
     * 用角速度 {@code speed / 半径} 换算，是为了让<b>线速度恒定</b> ——
     * 否则圈越小转得越快，玩家会看到一段莫名其妙的加速。
     *
     * <p>半径很小时不做切向推进 —— 要等超过
     * {@link ErebosTuning#EMERGE_MIN_TURN_RADIUS} 才开始转，
     * 否则身体那几节会折成疙瘩（见该常量的实测对比）。
     */
    private void tickEmerge(@Nullable Player target) {
        if (this.ticks == 1) {
            this.emergeCenter = this.boss.position();
            // 起手背对玩家甩出去 —— 玩家能看着它从脚边绕开，而不是迎面糊一脸。
            // 无目标时随便挑个固定方向：盘绕本身不依赖玩家，照样能转起来。
            Vec3 away = target == null
                    ? new Vec3(1.0D, 0.0D, 0.0D)
                    : this.boss.position().subtract(target.position());
            this.emergeAngle = away.lengthSqr() < 1.0E-4D
                    ? 0.0D
                    : Math.atan2(away.z, away.x);
            this.emergeRadius = 0.0D;
        }

        // ---- 速度：起手略快，随后落到主体速度 ----
        double speed = Math.max(
                ErebosTuning.EMERGE_MIN_SPEED,
                ErebosTuning.EMERGE_SPEED * Math.pow(0.985D, Math.max(0, this.ticks - 1)));

        // ---- 半径与角度：整份速度只花在其中一样上 ----
        // ⚠️ 硬约束：每 tick 的位移必须正好是 speed，否则"12 秒铺满"就是空话。
        //    第一版是让半径按 0.15 的指数逼近"目标半径"，位移由逼近速度决定 ——
        //    服务端实测对不上：开局那几 tick 只有 0.1 格/tick，而设计值是 0.4，
        //    表现为"起步几乎不动、随后突然加速"。
        //    所以这里明确二选一：半径没到位，整份速度用于向外扩张（走直线）；
        //    到位之后，整份速度用于切向绕圈。两段各自的位移都恰好等于 speed。
        // ---- 半径与角度：<b>螺旋</b>外扩，位移恒等于 speed ----
        //
        // 旧写法是「先纯径向走到半径到位，再纯切向绕圈」，两段各占满整份速度。
        // 问题是两段之间方向从 0° 直接跳到 90° —— 轨迹上留下一个直角，
        // 身体经过那里时会折出一道折痕（离线模拟：相邻节最大转角 88°）。
        //
        // 改成按进度把速度在「径向 / 切向」之间平滑分配：
        //   φ 从 0（纯往外甩）连续转到 90°（纯盘旋），
        //   而 cos²φ + sin²φ = 1 保证了每 tick 的位移始终等于 speed。
        // 于是路径是一条螺旋线，没有折点。
        double progress = Mth.clamp(this.emergeRadius / ErebosTuning.EMERGE_RADIUS, 0.0D, 1.0D);
        double phi = progress * (Math.PI * 0.5D);
        double radialStep = speed * Math.cos(phi);
        double angularStep = speed * Math.sin(phi) / Math.max(this.emergeRadius, 1.0D);

        if (this.emergeRadius < ErebosTuning.EMERGE_RADIUS) {
            this.emergeRadius = Math.min(ErebosTuning.EMERGE_RADIUS,
                    this.emergeRadius + radialStep);
        }
        // 半径太小时不转，否则那几节会折成疙瘩（见 EMERGE_MIN_TURN_RADIUS）
        if (this.emergeRadius > ErebosTuning.EMERGE_MIN_TURN_RADIUS) {
            this.emergeAngle += angularStep;
        }

        Vec3 goal = new Vec3(
                this.emergeCenter.x + Math.cos(this.emergeAngle) * this.emergeRadius,
                this.emergeCenter.y,
                this.emergeCenter.z + Math.sin(this.emergeAngle) * this.emergeRadius);

        // 位移 = 目标点 − 当前位置。EMERGE 期间 Boss 不会被外力推动，
        // 所以"位置"始终等于上一 tick 的 goal，这条位移的长度就约等于 speed。
        this.velocity = goal.subtract(this.boss.position());

        // 身体完全铺开才算"出来了" —— 用轨迹长度判断，而不是用计时器。
        // 计时器会在服务器卡顿时骗人：时间到了身体却还没走出去。
        boolean done = this.boss.isFullyEmerged()
                && this.ticks > ErebosTuning.EMERGE_GRACE_TICKS;

        // ⚠️ 但没有超时的等待条件是危险的：只要轨迹因任何原因长不够
        //    （区块没加载导致 tick 被节流、别的模组动了实体 tick、
        //      位置更新被覆盖……），Boss 就会<b>永久</b>停在 EMERGE ——
        //    表现为"无敌（打不出伤害）+ 不出招 + 身体缩成一个头"。
        //    所以补一道硬超时：宁可身体没铺满就开打，也不要变成打不动的雕像。
        if (!done && this.ticks > ErebosTuning.EMERGE_TIMEOUT_TICKS) {
            done = true;
        }

        if (done) {
            to(Action.CRUISE);
            if (target != null) {
                pickCruiseTarget(target);
            }
        }
    }

    // ================================================================
    // 巡游
    // ================================================================

    private void tickCruise(Player target) {
        // ★ 到达目标后不要立刻换向。
        //   原来一到 6 格内就重选，导致每 2~3 秒一次急转弯 ——
        //   玩家说的"动来动去毫无规律""抽搐"有一半来自这里：
        //   它不是走得乱，是被频繁打断。
        //   现在到达后继续沿当前方向飞 10 tick（0.5 秒）再换目标，
        //   路径自然连成弧线，而不是锯齿。
        if (this.cruiseHold > 0) {
            this.cruiseHold--;
        } else if (this.cruiseTarget == null
                || this.boss.position().distanceTo(this.cruiseTarget) < ErebosTuning.CRUISE_ARRIVE) {
            pickCruiseTarget(target);
            this.cruiseHold = 10;
        }

        int cruiseFor = this.boss.phase().cruiseTicks();
        if (cruiseFor <= 0 || this.ticks >= cruiseFor) {
            // 第四阶段的 cruiseTicks 是 0：不给喘息，直接进招
            pickMove(target);
            return;
        }

        Vec3 toTarget = this.cruiseTarget.subtract(this.boss.position());
        if (toTarget.lengthSqr() < 1.0E-4D) {
            return;
        }
        Vec3 desired = toTarget.normalize().scale(ErebosTuning.CRUISE_SPEED);
        this.velocity = turnTowards(this.velocity, desired,
                ErebosTuning.CRUISE_TURN, ErebosTuning.CRUISE_SPEED);
    }

    /**
     * 在玩家周围的较远处随机挑一个目标点。
     *
     * <p>原设计特意说明"巡游不绕玩家固定转圈，沿玩家周围较远区域平滑随机移动"——
     * 所以每到一个点就重新随机角度与半径，而不是做圆周运动。
     * 也用了高度：MC 是 3D，上下起伏比平面横移更有压迫感，
     * 也让玩家不得不抬头看它。
     */
    private void pickCruiseTarget(@Nullable Player target) {
        var random = this.boss.getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double radius = Mth.lerp(random.nextDouble(),
                ErebosTuning.CRUISE_RADIUS_MIN, ErebosTuning.CRUISE_RADIUS_MAX);

        // ★ target 必须允许为 null。
        //   没有玩家时（单人暂停、玩家死亡、创造/旁观不计入索敌），
        //   原来这里直接 target.getX() —— 每 tick 空指针，
        //   而异常发生在 tickCruise 的前半段，后面的"选招"永远执行不到：
        //   表现就是"它一直在飘，但一个技能也不放"。
        //   实测日志里 pickMove 一条都没有，根因就在这。
        double cx = target != null ? target.getX() : this.boss.getX();
        double cy = target != null ? target.getY() : this.boss.getY();
        double cz = target != null ? target.getZ() : this.boss.getZ();

        double x = cx + Math.cos(angle) * radius;
        double z = cz + Math.sin(angle) * radius;
        double y = cy + (random.nextDouble() - 0.5D) * 2.0D * ErebosTuning.CRUISE_HEIGHT;

        double minY = this.boss.level().getMinBuildHeight() + 12.0D;
        double maxY = Math.min(this.boss.level().getMaxBuildHeight() - 6.0D, cy + 40.0D);
        this.cruiseTarget = new Vec3(x, Mth.clamp(y, minY, Math.max(minY, maxY)), z);
    }

    // ================================================================
    // 招式
    // ================================================================

    private void tickMove(Player target) {
        ErebosMove move = this.current;
        if (move == null) {
            to(Action.CRUISE);
            pickCruiseTarget(target);
            return;
        }
        Vec3 moveVelocity = move.tick(target);

        // ★ 招式要求"站住"时，不要一路刹到 0。
        //
        //   原来的做法是按 0.93/tick 衰减，约 30 tick（1.5 秒）收住 ——
        //   也就是说除了开头那一下"滑停"，剩下的十几秒里这条 210 格的巨物
        //   是**完全钉死**的。玩家反馈"放技能还是会停下来"就是这个。
        //
        //   现在改成「保持最低巡航速度继续漂移」：速度不低于巡航的 45%，
        //   并掺一点朝玩家的切向分量，让它绕着玩家慢慢打转，
        //   而不是径直漂到墙边顶着。观感是"悬停着伺机而动"，不是"卡住了"。
        if (moveVelocity.lengthSqr() < 1.0E-8D && this.velocity.lengthSqr() > 1.0E-4D) {
            double speed = Math.max(ErebosTuning.CRUISE_SPEED * 0.45D,
                    this.velocity.length() * 0.97D);
            Vec3 dir = this.velocity.normalize();

            Vec3 flat = target.position().subtract(this.boss.position());
            flat = new Vec3(flat.x, 0.0D, flat.z);
            if (flat.lengthSqr() > 4.0D) {
                Vec3 tangent = new Vec3(-flat.z, 0.0D, flat.x).normalize();
                // 必须与当前朝向同侧，否则会"拧"向反方向，看着又是一次抽搐
                if (tangent.dot(dir) < 0.0D) {
                    tangent = tangent.scale(-1.0D);
                }
                dir = dir.scale(0.75D).add(tangent.scale(0.25D)).normalize();
            }
            moveVelocity = dir.scale(speed);
        }
        this.velocity = moveVelocity;
        if (move.isFinished()) {
            this.current = null;
            to(Action.CRUISE);
            pickCruiseTarget(target);
        }
    }

    /**
     * 挑一个招式开演。
     *
     * <p>候选 = 当前阶段可用的 + 不是上一招的。
     * 万一过滤完一个都不剩（比如某个阶段只配了一招），就退而求其次允许重复 ——
     * 宁可重复出招，也不能因为选不出招而卡死在巡游里。
     */
    private void pickMove(@Nullable Player target) {
        ErebosPhase phase = this.boss.phase();
        com.lost_realm.Medals.LOGGER.info(
                "[LR/erebos] pickMove 进入 phase={} pool={} last={}",
                phase, this.pool.size(), String.valueOf(this.lastMoveId));

        List<ErebosMove> candidates = new ArrayList<>();
        for (ErebosMove move : this.pool) {
            if (move.availableIn(phase) && !move.id().equals(this.lastMoveId)) {
                candidates.add(move);
            }
        }
        if (candidates.isEmpty()) {
            for (ErebosMove move : this.pool) {
                if (move.availableIn(phase)) {
                    candidates.add(move);
                }
            }
        }
        if (candidates.isEmpty()) {
            to(Action.CRUISE);
            pickCruiseTarget(target);
            return;
        }

        ErebosMove chosen = candidates.get(this.boss.getRandom().nextInt(candidates.size()));
        // 诊断：把"当前阶段 / 候选数 / 选中招"打出来 —— 用来确认
        // 「只放几个技能」到底是阶段没推进，还是候选池被过滤空了。
        com.lost_realm.Medals.LOGGER.info(
                "[LR/erebos] pickMove phase={} 候选={} 选中={} 血量={}/{}",
                phase, candidates.size(), chosen.id(),
                this.boss.getHealth(), this.boss.getMaxHealth());
        this.lastMoveId = chosen.id();
        this.current = chosen;
        chosen.enter(target);
        to(Action.MOVE);
    }

    /**
     * 打断当前招式 —— 阶段转场时用。
     *
     * <p>让招式有机会收拾自己生成的东西（星门、限制圈、审判柱）。
     * 不调这一步的话，转场清场只清掉了实体，招式对象内部还握着
     * 已经失效的引用，下一次 {@code onFinished} 会对着死实体操作。
     */
    public void interrupt(net.minecraft.server.level.ServerLevel level) {
        ErebosMove move = this.current;
        if (move != null) {
            move.cancel();
        }
        this.current = null;
        to(Action.CRUISE);
    }

    // ================================================================
    // 工具
    // ================================================================

    /**
     * 把当前速度朝目标方向转，但每 tick 最多转 {@code maxAngle} 弧度。
     *
     * <p>用球面插值（两个单位向量按比例 lerp 再归一化）而不是拆 yaw/pitch ——
     * 后者在接近垂直时会因为万向节问题抖动。
     */
    private static Vec3 turnTowards(Vec3 current, Vec3 desired, double maxAngle, double minSpeed) {
        double desiredLen = desired.length();
        if (desiredLen < 1.0E-6D) {
            return current;
        }
        if (current.lengthSqr() < 1.0E-8D) {
            return desired;
        }
        Vec3 from = current.normalize();
        Vec3 to = desired.scale(1.0D / desiredLen);

        double angle = Math.acos(Mth.clamp(from.dot(to), -1.0D, 1.0D));
        Vec3 dir = (angle <= maxAngle || angle < 1.0E-6D)
                ? to
                : from.lerp(to, maxAngle / angle).normalize();
        return dir.scale(Math.max(desiredLen, minSpeed));
    }
}
