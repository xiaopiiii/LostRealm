package com.lost_realm.boss.erebos;

/**
 * Erebos（遗落之主·乌蠹诺斯）的全部可调数值 —— <b>从泰拉瑞亚尺度重新平衡到 MC 尺度</b>。
 *
 * <h2>为什么必须整体重算，不能照搬</h2>
 * 那份设计是给泰拉瑞亚写的，两边的"物理尺度"差着量级。照抄会得到两种结果：
 * 要么玩家根本躲不掉，要么服务器当场去世。三处关键的换算：
 *
 * <h3>① 长度：310 格 → 96 格</h3>
 * 原设计虫身 4956 px = <b>310 格</b>（泰拉瑞亚 1 格 = 16 px），
 * 那是 <b>20 个 MC 区块</b>宽 —— 加载、渲染、路径记录全都撑不住。
 * 压到 <b>6 个区块（96 格）</b>：仍然跨 6 个区块，视觉上依然是"巨物"，
 * 但引擎扛得住。
 *
 * <h3>② 速度：px/帧 → 格/tick</h3>
 * 泰拉瑞亚按 60 帧/秒计，MC 按 20 tick/秒计，而两边的"格"都是 16 像素：
 * <pre>
 *   MC 速度(格/tick) = 泰拉瑞亚速度(px/帧) × 60 ÷ 16 ÷ 20 = × 0.1875
 * </pre>
 * 代进去会发现原设计的数值在 MC 里是荒谬的：
 * <table border="1">
 *   <tr><th>项</th><th>原设计</th><th>换算到 MC</th><th>实际采用</th></tr>
 *   <tr><td>远距巡游</td><td>14 px/帧</td><td>2.63 格/tick</td>
 *       <td><b>0.55</b>（约玩家跑速 2 倍）</td></tr>
 *   <tr><td>冲锋最高速</td><td>105.6 px/帧</td><td><b>19.8 格/tick</b></td>
 *       <td><b>2.6</b>（约玩家跑速 9 倍）</td></tr>
 * </table>
 * 19.8 格/tick 是"每 tick 跨 20 个方块"—— 比箭还快，服务端会把它当传送处理，
 * 渲染上更是每帧跳一大截，玩家只会看到一段闪烁。MC 里实体的合理上限是
 * 每 tick 几个方块，所以冲锋取 2.6 格/tick：仍然是"一闪而过"的观感，
 * 但玩家看得见它来、也看得见它走。
 *
 * <h3>③ 伤害：按"挨几下死"反推，而不是按数值比例</h3>
 * 泰拉瑞亚月后玩家约 <b>500 血</b>，Boss 头部接触伤害 560（防御前）——
 * 打掉防御后约剩 224，即"<b>挨三下死</b>"。
 * MC 玩家是 <b>20 血</b>，所以对应的不是"560 ÷ 500 × 20 = 22"，
 * 而是"挨三下死"这个<b>体验</b>：20 ÷ 3 ≈ 6.7 血（护甲后）。
 * 反推护甲前（铁套减伤约 44%）→ <b>14</b>，验算：14 × 0.56 = 7.8 血，三下正好打死。
 *
 * <h4>关于难度倍率</h4>
 * 原设计特意叮嘱"正确处理普通/专家/大师的伤害换算，不要被难度倍率重复放大"。
 * 这条在 MC 里<b>天然满足</b> —— MC 的难度不缩放伤害（只影响刷怪与 AI），
 * 所以本模组<b>不写任何难度乘数</b>，就不会有"重复放大"。
 *
 * <h2>这份数值放在哪</h2>
 * 与 {@code BossRegistry} 同一套路：<b>配置优先、常量兜底</b>。
 * 配置没加载好（启动早期事件、或用户删了配置文件）时自动回退到这里的常量，
 * 绝不返回 null、也绝不崩。
 */
public final class ErebosTuning {

    // ================================================================
    // 一、体型
    // ================================================================

    /**
     * 部件总数（含头部与尾部）。
     *
     * <p>原设计是 60 部件 / 310 格。压到 96 格后若仍保留 60 节，
     * 每节只剩 1.6 格直径 —— 像条细绳，"巨物感"全无。
     * 取 <b>40 节 × 2.4 格</b>：直径是玩家的 4 倍，且节数够多，
     * 转弯时曲线依然平滑（节数太少会看出折线）。
     */
    public static final int SEGMENTS = 50;

    /**
     * 相邻两节的弧长间距（格）。40 × 3.6 = <b>144 格</b>（原 96 格，beta20 起 ×1.5）。
     *
     * <p>长度是靠节距拉的、不是靠加节数 —— 节数一涨，碰撞部件实体就跟着涨，
     * 而这部手机端最吃紧的恰恰是「实体数量 × 同步频率」。
     * 保持 40 节只调节距，长度白赚 50%，开销一分不涨。
     *
     * <p>副作用只有一个：转弯时折线感会略明显。但体型同时也放大了一倍，
     * 节距 3.6 &lt; 直径 4.8，相邻两节仍有 25% 重叠，看不出来。
     */
    public static final double SEGMENT_SPACING = 4.2D;

    /** 体节渲染直径（格）。判定上不起作用，判定走部件实体。beta20 起由 2.4 放大到 4.8。 */
    public static final double BODY_DIAMETER = 4.8D;

    /**
     * 碰撞部件的边长（格）。
     *
     * <h2>为什么是「每节一个」，而不是「几个大盒子」</h2>
     * MC 的 {@code EntityDimensions} 只有<b>宽和高</b>、没有「深」——
     * 所以部件实体永远只能是个<b>立方体</b>（或竖直长方体）。
     * 这就封死了"用少量大盒子覆盖身体"这条路：
     * 要让一个盒子覆盖 6.4 格长的一段，它的边长就得是 6.4，
     * 而身体只有 2.4 格粗 —— 于是横向白多出 4 格"空气判定"，
     * 玩家站在身体拐弯的内侧会被莫名撞到。
     *
     * <p>所以改成<b>沿身体每节放一个立方体</b>：节距 3.6，边长取 5.2
     * （略大于体径 4.8，让相邻盒子轻微重叠、不留缝隙）。
     * 这样覆盖是精确的 —— 有身体的地方就有判定，没有身体的地方就没有。
     *
     * <p>代价是部件数量 = 节数（40 个）。它们不 tick（{@code tick()} 为空）、
     * 不做物理、不存档，实际开销只是位置同步与 AABB 遍历。
     */
    public static final float PART_SIZE = 5.2F;

    // ================================================================
    // 二、血量与阶段
    // ================================================================

    /**
     * 最大生命值。
     *
     * <h2>为什么从 24000 砍到 12000</h2>
     * 原值是"假设玩家 DPS 70~90"算出来的。实测反馈：<b>打不到 80%</b>，
     * 于是<b>永远停在第一阶段，永远只会那三招</b> ——
     * 后面 10 个招式、四次转场，玩家一次都没见过。
     *
     * <p>这不是平衡问题，是<b>内容可达性</b>问题：血量定得太高，
     * 等于把后面 3/4 的设计全部藏起来了。宁可整体偏短，也不能让玩家
     * 打十分钟还没进第二阶段。
     *
     * <p>按 DPS 30 估算：12000 / 30 = 400 秒 ≈ 6.7 分钟走完全程，
     * 打到 80%（进阶段Ⅱ）只要 80 秒，60%（阶段Ⅲ）约 160 秒 ——
     * 这个节奏才对得上"4~6 分钟"的设计目标。
     *
     * <p>配置里的 {@code boss_max_health} 是旧 WardenBoss 的，Erebos 不读它，
     * 要调血量只能改这里。</p>
     */
    public static double MAX_HEALTH = 500.0D;   // ★ beta32 测试期临时调低，正式发布前恢复 12000

    /** 阶段Ⅰ结束（100% → 80%）。 */
    public static float PHASE_1_END = 0.80F;

    /** 阶段Ⅱ结束（80% → 60%）。 */
    public static float PHASE_2_END = 0.60F;

    /** 阶段Ⅲ结束（60% → 15%）。 */
    public static float PHASE_3_END = 0.15F;

    // ================================================================
    // 三、运动
    // ================================================================

    /** 常规巡游速度（格/tick）。约 11 格/秒 —— 玩家跑速的 2 倍，追得上但追不上太久。 */
    public static double CRUISE_SPEED = 1.25D;

    /** 冲锋最高速度（格/tick）。约 52 格/秒，"一闪而过"，但不会快到看不见。 */
    public static double CHARGE_SPEED = 4.5D;

    /** 冲锋加速度（格/tick²）。原设计要求"加减速温和" —— 别一帧就到顶速。 */
    public static final double CHARGE_ACCEL = 0.085D;

    /** 巡游时的每 tick 最大转向（弧度）。约 3.2°/tick，转弯有惯性、不会急转。 */
    public static final double CRUISE_TURN = 0.07D;

    /** 冲锋后的回转速（弧度/tick），比巡游更慢 —— 体现"保留惯性后再掉头"。 */
    public static final double RECOVER_TURN = 0.035D;

    /** 头部速度低于此值时视为"停住"。 */
    public static final double MIN_SPEED = 0.02D;

    // ================================================================
    // 四、噬光冲锋
    // ================================================================

    /** 每轮冲锋次数。原设计："普通冲锋每轮三次"。 */
    public static final int CHARGES_PER_ROUND = 3;

    /**
     * 冲锋距离超出"起跑时与玩家的距离"的量（格）。
     *
     * <p>原设计是 1920 px = 120 格 —— 那是为了让 310 格长的身体也能整体穿过。
     * 身体压到 96 格后，40 格足矣：头部多跑 40 格，尾部自然跟着掠过玩家所在区域。
     * <b>关键是"撞到玩家不停住"</b>，这一条比距离本身重要。
     */
    public static final double CHARGE_OVERSHOOT = 40.0D;

    /** 分阶段预警时长（tick）。原设计 0.9 / 0.8 / 0.7 / 0.7 秒。 */
    public static final int[] CHARGE_WARNING_TICKS = {18, 16, 14, 14};

    /** 单次冲锋结束后、进入下一次预警前的回转时间（tick）。 */
    public static final int CHARGE_RECOVER_TICKS = 26;

    /** 冲锋预警线的绘制长度（格）。 */
    public static final double WARNING_LINE_LENGTH = 64.0D;

    // ================================================================
    // 五、巡游
    // ================================================================

    /**
     * 分阶段巡游时长（tick）。
     *
     * <p>原设计 10 / 8 / 3 / 0 秒 —— 阶段越靠后越不给喘息，
     * 第四阶段完全没有巡游。这个设计意图完整保留。
     */
    // ★ 原设计是 10s / 8s / 3s / 0（泰拉瑞亚节奏），实测在 MC 里太稀：
    //   一场战斗约 2 分钟，光巡游就吃掉 30 秒，阶段Ⅲ 的 8 个招式根本轮不完，
    //   玩家的体感就是「只会放那几个技能」。
    //   现在压到 2.5s / 2s / 1.25s / 0 —— 出招密度提高约 4 倍，
    //   13 个招式在一场里基本都能见到，压迫感也随之上来。
    public static final int[] CRUISE_TICKS = {50, 40, 25, 0};

    /** 巡游目标点与玩家的距离范围（格）。 */
    public static final double CRUISE_RADIUS_MIN = 32.0D;
    public static final double CRUISE_RADIUS_MAX = 58.0D;

    /** 巡游目标点垂直偏移范围（格）—— MC 是 3D，巡游要利用高度差。 */
    public static final double CRUISE_HEIGHT = 10.0D;

    /** 到达目标点多近算"到达"（格），到达后换一个新目标点。 */
    public static final double CRUISE_ARRIVE = 12.0D;

    // ================================================================
    // 六、接触伤害
    // ================================================================

    /** 头部接触伤害（护甲前）。原设计 560，按"挨三下死"换算得 14。 */
    public static float CONTACT_HEAD = 14.0F;

    /** 身体/尾部接触伤害（护甲前）。原设计 330，约为头部的 0.59 倍 → 8。 */
    public static float CONTACT_BODY = 8.0F;

    /** 同一玩家被接触伤害的最小间隔（tick）。避免每 tick 都判定、把玩家瞬秒。 */
    public static final int CONTACT_COOLDOWN = 10;

    // ================================================================
    // 七、动态减伤
    // ================================================================

    /**
     * 动态减伤触发的"每秒失血"阈值。
     *
     * <p>原设计是 10000 伤害/秒（对应 240 万血，即 0.42% 血量/秒）。
     * 按同样比例换算到 24000 血 → 100/秒。但那个值在 MC 里<b>形同虚设</b>：
     * 玩家正常 DPS 才 70~90，永远够不到阈值，减伤机制等于不存在。
     *
     * <p>所以这里按<b>设计意图</b>而不是比例来定：这条机制的用途是
     * "防止超模爆发把 Boss 秒了"，阈值应当设在<b>略高于玩家正常 DPS</b>。
     * 取 <b>200 伤害/秒</b>（约正常 DPS 的 2.5 倍）——
     * 平时打不触发，一旦有人掏出「我有一剑」（单发 5000）就会立刻被压下去。
     */
    public static final double REDUCTION_THRESHOLD = 200.0D;

    /** 动态减伤上限。原设计 0.75，保留 —— 是"压制"不是"无敌"。 */
    public static double REDUCTION_CAP = 0.75D;

    /**
     * 失血统计窗口（tick）。
     *
     * <p>原设计是"最近 60 帧"，即 1 秒 —— MC 的 1 秒就是 <b>20 tick</b>。
     */
    public static final int REDUCTION_WINDOW = 20;

    /** 减伤平滑系数：每 tick 向目标值靠拢的比例。防止数值突跳。 */
    public static final double REDUCTION_SMOOTH = 0.08D;

    // ================================================================
    // 八、开场
    // ----------------------------------------------------------------
    // 这一组参数共同决定"登场演出"。先把关键约束摆出来：
    // 身体全长 = SEGMENTS × SEGMENT_SPACING，头部必须**真的走满这个长度**才能铺开。
    // 于是出现一个绕不开的矛盾 —— 若走直线，Boss 必然停在 96 格外，
    // 玩家站在出场地根本不可能看见它。
    // 解法是让头部**绕着出场地走**，见 EMERGE_RADIUS。
    // ================================================================

    /**
     * 开场钻出期间的<b>起手</b>速度（格/tick）。约 10 格/秒。
     *
     * <p>比主体速度（{@link #EMERGE_MIN_SPEED}）略快一点，保留"冲出来"的劲头，
     * 之后按 0.985/tick 衰减，很快落到主体速度。
     */
    public static final double EMERGE_SPEED = 1.1D;

    /**
     * 开场钻出的<b>主体</b>速度（格/tick）。约 8 格/秒。
     *
     * <h2>整场演出多长</h2>
     * {@code 96 ÷ 0.4 = 240 tick = 12 秒}。
     * 换算关系是 {@code 时长(秒) = EMERGE_ARC ÷ (EMERGE_MIN_SPEED × 20)}——
     * 反过来说，想要 8 秒就得取 0.6 格/tick。
     *
     * <h2>为什么敢放这么慢</h2>
     * 因为路径已经改成盘绕（见 {@link #EMERGE_RADIUS}），头部全程绕在玩家周围，
     * <b>一直看得见</b>，慢下来才谈得上"看清身体逐节铺开"。
     * 如果还是直线，慢只会变成"慢慢飘远、然后看不见"，比快更糟。
     */
    public static final double EMERGE_MIN_SPEED = 0.85D;

    /**
     * 开场铺满身体所需的弧长（格）—— 就是身体全长。
     *
     * <p>头部只有真走过这么长的路径，40 节才能全部展开。
     * 它同时是盘绕半径的分母：走完这个长度，正好盘完一整圈。
     */
    public static final double EMERGE_ARC = SEGMENTS * SEGMENT_SPACING;

    /**
     * 盘绕半径（格）。
     *
     * <h2>为什么是"弧长 ÷ 2π"</h2>
     * 要让"走满 96 格弧长"恰好等于"盘完一整圈"，圆周长就得等于 96 ——
     * 于是半径 = 96 / 2π ≈ <b>15.3 格</b>。
     * 登场结束时 Boss 正好盘成一个环把玩家围在中间，头尾之间留一道缺口
     * （缺口来自向外扩张那段也消耗了弧长，大约盘不满 360°）。
     *
     * <h2>为什么这个几何是对的</h2>
     * 它一次解决三个问题：
     * <ol>
     *   <li><b>看得见。</b>Boss 始终在玩家 15 格上下，再也不会跑到 96 格外去。</li>
     *   <li><b>贴合设定。</b>"乌蠹诺斯"是条宇宙巨虫，盘绕登场远比直线飞离有压迫感。</li>
     *   <li><b>衔接自然。</b>登场结束时它就在身边，转巡游不必再飞回来。</li>
     * </ol>
     * 顺带还消掉了"创造模式看不见"这个测试痛点 —— 盘绕只依赖出场地，
     * 不依赖玩家是否算作战目标，所以创造/旁观模式下也能看完整场登场。
     */
    public static final double EMERGE_RADIUS = EMERGE_ARC / (Math.PI * 2.0D);

    /**
     * 开始做切向盘绕的最小半径（格）。
     *
     * <h2>为什么不能一出门就转</h2>
     * 身体是每 2.4 格一节。如果半径还很小就让它转圈，
     * 同样的 2.4 格弧长对应的转角会大得离谱 —— 半径 1 格时每节要拐 137°，
     * 那几节会直接折叠成疙瘩（离线模拟里实测相邻节最大转角 90°）。
     *
     * <p>取 3.0 之后，相邻节最大转角压到 <b>56°</b>，是一眼能看出"在转弯"
     * 但不会折起来的程度。
     *
     * <p>效果对比（离线模拟，均为走满 96 格弧长）：
     * <pre>
     *   1.0 → 盘 1.44 圈，相邻节最大转角 90°
     *   2.0 → 盘 1.27 圈，最大转角 82°
     *   3.0 → 盘 1.14 圈，最大转角 56°   ← 采用
     * </pre>
     *
     * <p>注：现在的速度驱动方式（见 {@link ErebosBrain#tickEmerge}）是
     * "半径先匀速扩到 {@link #EMERGE_RADIUS}，到位后才开始转"，
     * 所以实际上开始转弯时半径已经是 15.3 —— 这个门限退化成了一道保险，
     * 防止将来改动动的把"扩张期"缩短到半径还很小就开始转。
     */
    public static final double EMERGE_MIN_TURN_RADIUS = 3.0D;

    /** 开场保护玩家的时长（tick）—— 身体还没铺开时不给接触伤害。 */
    public static final int EMERGE_GRACE_TICKS = 40;

    /**
     * 开场的<b>硬超时</b>（tick）—— 到点无论如何都结束 EMERGE。
     *
     * <h2>为什么必须有这一条</h2>
     * EMERGE 的结束条件原本只有"身体铺满"（{@code isFullyEmerged()}，
     * 即轨迹长度 ≥ 96 格）。这在正常情况下 240 tick 左右就能满足，但它是
     * <b>一个没有退路的等待条件</b>：只要轨迹因为任何原因长不够，
     * 状态机就永远停在 EMERGE ——
     * <ul>
     *   <li><b>无敌</b>：{@code hurt()} 里 EMERGE 直接 return false，打不出伤害</li>
     *   <li><b>不出招</b>：EMERGE 不参与选招</li>
     *   <li><b>身体缩成一团</b>：轨迹不够，40 节都堆在头部</li>
     * </ul>
     * 三者叠在一起，玩家看到的正是"只有一个头、不会动、打不动"。
     *
     * <p>而"轨迹长不够"的原因可以很离谱：区块没加载导致实体被 tick 节流、
     * 某个优化模组改了实体 tick、甚至只是位置更新被别的东西覆盖。
     * 这些都不该让 Boss 变成一尊打不动的雕像。
     *
     * <p>取 400 tick（20 秒）—— 正常 12 秒铺满，留足余量；真卡住了也能在
     * 20 秒后自己走出来。宁可身体没铺满就开打，也不要永久无敌。
     */
    public static final int EMERGE_TIMEOUT_TICKS = 400;

    /**
     * 实体活跃半径（格）：超出此距离的玩家不参与索敌，Boss 也会主动中止战斗。
     *
     * <h2>为什么从 128 提到 256</h2>
     * 旧值 128 有个致命的"失联"问题：Boss 一旦离玩家超过 128 格，
     * {@code getNearestPlayer(this, 128)} 就返回 null → {@code brain.tick(null)}
     * → 巡游速度每 tick 乘 0.9 → <b>停在原地，永远不再回来</b>。
     * 而玩家那边同时因为超出渲染距离，chunk 没加载、连实体都不存在 ——
     * 两边一起消失，玩家只会认为"Boss 没了"。
     *
     * <p>实测就撞上过：玩家在 (-55.2, -60.0, 15.6)，Boss 停在 (-59.6, -35.4, 160.3)，
     * 直线距离约 147 格 —— 刚好卡在 128 与 256 之间，于是彻底失联。
     *
     * <p>取 256 与 {@code clientTrackingRange(16) = 16 区块} 对齐，
     * 含义很直接：<b>只要客户端还看得见它，它就该看得见玩家</b>。
     * 另外这条虫身体就有 96 格长，玩家完全可能站在尾巴附近 ——
     * 128 连"身长 + 一点余量"都不够。
     */
    public static double LEASH_RANGE = 256.0D;

    // ================================================================
    // 九、星弹（通用弹幕）
    // ================================================================

    /**
     * 星弹速度（格/tick）。
     *
     * <p>原设计 32 px/帧，纯换算得 <b>6 格/tick</b> —— 那是箭的两倍，
     * 玩家根本来不及侧身。MC 里能躲的弹幕上限大约在 1~1.5 格/tick
     * （烈焰人火球 1.0、恶魂火球 1.0），所以取 <b>1.2</b>：
     * 够快、有压迫感，但从"看到"到"被击中"留得出反应时间。
     */
    public static final double BOLT_SPEED = 1.2D;

    /**
     * 星弹伤害（护甲前）。
     *
     * <p>原设计"普通星弹 250~285（防御前）"。跟接触伤害一样，
     * 这里搬的是<b>体验</b>不是数字：20 血挨 4~5 下 = 护甲前约 12。
     * 比头部接触伤害（14）略低 —— 被弹幕蹭一下不该和迎面撞上一样痛。
     */
    public static final float BOLT_DAMAGE = 12.0F;

    /** 星弹存活时长（tick）。3 秒 × 1.2 格/tick ≈ 72 格射程。 */
    public static final int BOLT_LIFE = 60;

    /** 星弹碰撞半径（格）。比视觉略大一点，避免"明明擦到了却没判定"。 */
    public static final float BOLT_HITBOX = 0.55F;

    // ================================================================
    // 十、招式 —— 碎星播撒（第一阶段）
    // ================================================================

    /** 候选出口数量：从头到尾沿身体均匀取 8 个体节。 */
    public static final int SCATTER_EXITS = 8;

    /** 每波同时发射的出口数。原设计："始终只有两个点位同时发射"。 */
    public static final int SCATTER_POINTS = 2;

    /** 每个出口每波的弹幕数。原设计 17 发，MC 里砍到 11 —— 见类注释的实体预算说明。 */
    public static final int SCATTER_PER_POINT = 33;

    /** 扇形总张角（度）。11 发达成约 108°，与 17 发达成 112° 的观感接近。 */
    public static final double SCATTER_SPREAD_DEG = 108.0D;

    /** 长红线预警时长（tick）。原设计 2 秒。 */
    public static final int SCATTER_WARN = 40;

    /** 波数范围。 */
    public static final int SCATTER_WAVE_MIN = 3;
    public static final int SCATTER_WAVE_MAX = 5;

    /** 波次间隔（tick）。原设计 165 帧 = 2.75 秒 = 55 tick。 */
    public static final int SCATTER_WAVE_INTERVAL = 55;

    /** 预警线绘制长度（格）。原设计 3200 px = 200 格，MC 视距只有 12 区块，压到 48。 */
    public static final double SCATTER_WARN_LINE = 48.0D;

    /** 第 {@code index} 个候选出口对应的体节序号（0 = 头）。 */
    public static int scatterExitSegment(int index) {
        int n = Math.max(2, SCATTER_EXITS);
        int i = Math.max(0, Math.min(n - 1, index));
        return (int) Math.round(i * (double) (SEGMENTS - 1) / (n - 1));
    }

    // ================================================================
    // 十一、招式 —— 星弧巡猎（第一阶段）
    // ================================================================

    /** 每轮射出的种子弹数量范围。 */
    public static final int HUNT_SEED_MIN = 6;
    public static final int HUNT_SEED_MAX = 9;

    /** 轮数范围。 */
    public static final int HUNT_ROUNDS_MIN = 3;
    public static final int HUNT_ROUNDS_MAX = 4;

    /** 种子弹飞行速度（格/tick）—— 慢，玩家看得清它飘到哪。 */
    public static final double HUNT_SEED_SPEED = 0.55D;

    /** 种子弹飞行时长（tick）。原设计 1.5 秒。 */
    public static final int HUNT_SEED_FLIGHT = 30;

    /** 种子停住后的爆散预警（tick）。原设计 1 秒。 */
    public static final int HUNT_BURST_WARN = 20;

    /** 每个种子爆散的弹幕数。原设计 22 发，MC 里砍到 14。 */
    public static final int HUNT_BURST_COUNT = 42;

    /** 爆散弹速度（格/tick）。原设计 20~24 px/帧，取偏慢值方便躲。 */
    public static final double HUNT_BURST_SPEED = 1.0D;

    /** 爆散弹存活时长（tick）。原设计 4 秒。 */
    public static final int HUNT_BURST_LIFE = 80;

    // ================================================================
    // 十二、实体预算
    // ----------------------------------------------------------------
    // 泰拉瑞亚的弹幕是轻量对象，一发几百个没事；MC 里每发都是一个实体，
    // 要 tick、要同步、要进 AABB 查询。所以凡是"整屏铺满"的招式，
    // 数量都要按这里的上限压 —— 剩下的压迫感用粒子补（粒子免费，实体收费）。
    // ================================================================

    /**
     * 全场星弹数量上限。超过后不再生成新弹幕（而不是把旧的挤掉）——
     * 挤掉会导致"预警线还在、弹幕却没了"，那比少发几颗更糟。
     */
    public static final int MAX_LIVE_BOLTS = 360;

    /**
     * 限制圈半径（格）。
     *
     * <p>原设计 802 px = <b>50 格</b>。MC 里必须压到 32：视距只有 12 区块，
     * 50 格外的圈边已经在雾里，玩家根本看不出"那是个圈"，
     * 只会觉得"莫名其妙被什么东西挡了一下"。32 格大约是一屏半，
     * 既拦得住人，又能一眼看清边界。
     */
    public static final double ARENA_RADIUS = 32.0D;

    /**
     * 限制圈的<b>内侧</b>盘旋半径 —— 星核脉冲 / 裂界回廊 / 延迟星痕在用。
     *
     * <p>刻意比圈小一圈（0.72）：这几招是「圈内作业」，虫身留在圈内侧当压力源，
     * 玩家沿圈边跑时不至于一直在身体里穿进穿出。
     *
     * <p>⚠️ 但<b>围圈招式（囚星天环）不能用这个值</b> —— 原设计明确要求
     * 「虫身和限制圈重叠」，那一招用 {@link #RING_ORBIT_RADIUS}（= 圈半径）。
     * 两个常量别合并。
     */
    public static final double ARENA_ORBIT_RADIUS = ARENA_RADIUS * 0.72D;

    // ================================================================
    // 十三、招式 —— 星门贯穿（阶段 2/3/4）
    // ================================================================

    /** 穿门冲刺次数。原设计："连续四次穿门冲撞"。 */
    public static final int GATE_CHARGES = 4;

    /** 每次穿门的预警（tick）。原设计 0.7 秒。 */
    public static final int GATE_WARN = 14;

    /** 穿门冲刺速度（格/tick）—— 比普通冲锋更快，因为它是"瞬移式"的。 */
    public static final double GATE_SPEED = 5.0D;

    /** 每次从出口发射的星弹数。原设计 7 颗。 */
    public static final int GATE_BOLTS = 21;

    /** 星门半径（格）。 */
    public static final double GATE_RADIUS = 3.2D;

    /** 星门距玩家的距离（格）。 */
    public static final double GATE_DISTANCE = 26.0D;

    // ================================================================
    // 十四、招式 —— 引力残星（阶段 2）
    // ================================================================

    /** 一轮里的投放次数。原设计"一轮约 10 秒，共六次投放"。 */
    public static final int GRAVITY_DROPS = 6;

    /** 投放间隔（tick）。10 秒 ÷ 6 次 ≈ 33。 */
    public static final int GRAVITY_INTERVAL = 33;

    /** 炸弹落地预警（tick）。原设计 1 秒。 */
    public static final int GRAVITY_WARN = 20;

    /** 每次爆散的弹幕数。原设计"较慢的放射弹幕"。 */
    public static final int GRAVITY_BOLTS = 30;

    /** 放射弹速度（格/tick）。原设计 5 px/帧 → 0.94，这里更慢，好躲。 */
    public static final double GRAVITY_BOLT_SPEED = 0.42D;

    /** 放射弹存活（tick）。原设计 8 秒 —— 慢速弹要活得久才够飞出去。 */
    public static final int GRAVITY_BOLT_LIFE = 150;

    // ================================================================
    // 十五、招式 —— 裂星十字核（阶段 3）
    // ================================================================

    /** 炸弹飞行速度（格/tick）。原设计 26 px/帧，砍到能追上人的程度。 */
    public static final double CROSS_SPEED = 1.55D;

    /** 最短预警（tick）。原设计 1.25 秒。 */
    public static final int CROSS_MIN_WARN = 25;

    /** 最迟引爆（tick）。原设计 3 秒。 */
    public static final int CROSS_MAX_WARN = 60;

    /** 进入这个距离后停止追踪、开始滑行减速（格）。原设计"半屏"。 */
    public static final double CROSS_LOCK_RANGE = 24.0D;

    /** 爆炸后四道激光的长度（格）。 */
    public static final double CROSS_LASER_LENGTH = 52.0D;

    /** 激光半宽（格）。原设计"最大核心宽度约 102 px" ≈ 3.2 格 → 半宽 1.6。 */
    public static final float CROSS_LASER_HALF = 1.6F;

    /** 四向激光的存活（tick）。原设计"4 秒内旋转 360 度"。 */
    public static final int CROSS_LASER_LIFE = 80;

    // ================================================================
    // 十六、招式 —— 囚星天环（阶段 3）
    // ================================================================

    /** 星弹生成半径（格）。原设计 420 px ≈ 26 格。 */
    public static final double RING_SPAWN_RADIUS = 26.0D;

    // ---- beta31：围圈过程 ----
    //
    // 原设计："Boss 在约 3 秒内沿轨迹逐节围到限制圈上，形成过程可见，
    // 并持续沿圈旋转。虫身和限制圈重叠。不能瞬间传送所有体节形成圆圈。"
    //
    // beta30 及以前的实现有三个问题：
    //   ① 轨道半径是 ARENA_RADIUS × 0.72 = 23 格，而限制圈在 32 格 ——
    //      身体整个缩在圈**里面** 9 格，根本不与圈重叠；
    //   ② 周长 2π×23 = 145 格，而身体 50×4.2 = 206 格 → 要缠 1.4 圈，
    //      必然自我压叠；
    //   ③ 用的是巡航速度（1.25×1.15 ≈ 1.44 格/tick），
    //      头部走完 206 格要 143 tick ≈ 7.2 秒，"3 秒"从来没兑现过。
    //
    // 修法：轨道半径提到与限制圈一致（周长 201 格 ≈ 身体 1.02 圈，正好贴成一圈），
    // 并单独加一个「环布」阶段，用 3.43 格/tick 在 60 tick 内把身体铺上去。

    /** 围圈时的轨道半径 —— 与限制圈同半径，身体才会「贴着圈」。 */
    public static final double RING_ORBIT_RADIUS = ARENA_RADIUS;

    /** 「环布」阶段时长（tick）。原设计约 3 秒 = 60 tick。 */
    public static final int RING_WRAP_TICKS = 60;

    /**
     * 「环布」阶段头部切向速度（格/tick）。
     *
     * <p>= 身体全长 / 环布时长 = 49×4.2 / 60 ≈ 3.43。
     * 这是让<b>整条身体</b>在 3 秒内全部落到圆上的唯一速度 ——
     * 慢了围不完，快了会甩尾拉直。
     */
    public static final double RING_WRAP_SPEED =
            (SEGMENTS - 1) * SEGMENT_SPACING / RING_WRAP_TICKS;

    /**
     * 「环布」阶段每 tick 最大转向角（弧度）。
     *
     * <p>环布的角速度 = 3.43 / 32 ≈ 0.107 rad/tick，而巡航限幅只有 0.07 ——
     * 沿用巡航限幅会转向不足，头部一边外扩一边绕，画出一圈螺旋。
     * 这里放到 0.15，既能跟上圆，又不至于一眼看出"硬拐"。
     */
    public static final double RING_WRAP_TURN = 0.15D;


    /** 波数。原设计四波。 */
    public static final int RING_WAVES = 4;

    /** 波间隔（tick）。原设计 200 帧 ≈ 67。 */
    public static final int RING_WAVE_INTERVAL = 67;

    /** 每波弹幕数。原设计 36 颗，MC 里砍半。留缺口靠不铺满一圈实现。 */
    public static final int RING_BOLTS = 54;

    /** 弹速（格/tick）。原设计初始 4 px/帧 —— 极慢，是"逼近型"威胁。 */
    public static final double RING_BOLT_SPEED = 0.65D;

    // ================================================================
    // 十七、招式 —— 尾刃刻痕（阶段 3）
    // ================================================================

    /** 每道激光之间的间隔（tick）。原设计约 0.6 秒。 */
    public static final int TAIL_LASER_INTERVAL = 12;

    /** 一轮放几道。 */
    public static final int TAIL_LASER_COUNT = 10;

    /** 激光长度（格）。原设计 3840 px = 240 格，MC 视距不够，压到 72。 */
    public static final double TAIL_LASER_LENGTH = 72.0D;

    /** 激光半宽（格）。原设计"最大核心宽度约 102 px" → 半宽 1.6。 */
    public static final float TAIL_LASER_HALF = 1.6F;

    /** 预警（tick）。 */
    public static final int TAIL_LASER_WARN = 16;

    /** 生效（tick）—— 原设计"持续伤害一直保留到可见核心消失"。 */
    public static final int TAIL_LASER_ACTIVE = 18;

    // ================================================================
    // 十八、招式 —— 星核脉冲（阶段 3）
    // ================================================================

    /** 波数。原设计五波。 */
    public static final int PULSE_WAVES = 5;

    /** 波间隔（tick）。原设计 231 帧 ≈ 77。 */
    public static final int PULSE_WAVE_INTERVAL = 77;

    /** 每波环上的弹幕数。12 发 → 缺口占 30 度。 */
    public static final int PULSE_BOLTS = 36;

    /** 扩散速度（格/tick）。原设计 2.5 px/帧 ≈ 0.47。 */
    public static final double PULSE_SPEED = 0.47D;

    /** 弹幕存活（tick）—— 要够飞到限制圈边缘。 */
    public static final int PULSE_LIFE = 100;

    /** 每波角速度范围（度/tick）。原设计 20~60 度/秒 → 1~3 度/tick。 */
    public static final double PULSE_SPIN_MIN = 1.0D;
    public static final double PULSE_SPIN_MAX = 3.0D;

    // ================================================================
    // 十九、招式 —— 逆轨流星（阶段 3）
    // ================================================================

    public static final int METEOR_WAVES_MIN = 5;
    public static final int METEOR_WAVES_MAX = 6;

    /** 预警时长（tick）。原设计 2 秒。 */
    public static final int METEOR_WARN = 40;

    /** 每侧弹幕数。原设计每方向 34 条，MC 里砍到 10 —— 通道仍然清晰可读。 */
    public static final int METEOR_PER_SIDE = 30;

    /** 弹速（格/tick）。原设计 27.86 px/帧 —— 太快，砍到能躲。 */
    public static final double METEOR_SPEED = 1.15D;

    public static final int METEOR_LIFE = 80;

    /** 波间隔（tick）。原设计 119 帧 ≈ 40。 */
    public static final int METEOR_WAVE_INTERVAL = 40;

    // ================================================================
    // 二十、招式 —— 崩星审判（阶段 3）
    // ================================================================

    /** 轮数。原设计连续三轮。 */
    public static final int JUDGE_ROUNDS = 3;

    /** 首组预警（tick）。原设计 1.3 秒。 */
    public static final int JUDGE_WARN = 26;

    /** 光束存续（tick）。原设计约 0.6 秒。 */
    public static final int JUDGE_ACTIVE = 12;

    /** 每轮列数（隔一列一根，共摆两排）。 */
    public static final int JUDGE_COLUMNS = 14;

    /** 两组爆发之间的转移窗口（tick）。原设计约 1 秒。 */
    public static final int JUDGE_TRANSFER = 20;

    /** 星柱半径（格）。 */
    public static final double JUDGE_RADIUS = 2.4D;

    /** 列间距（格）。原设计"相邻安全列约一个星柱宽"，即可转移。 */
    public static final double JUDGE_SPACING = 5.6D;

    // ================================================================
    // 二十一、招式 —— 裂界回廊（阶段 3）
    // ================================================================

    public static final int CORRIDOR_WARN = 25;

    /** 激光生效时长（tick）。原设计 706 帧 ≈ 235，压到 160 免得站桩太久。 */
    public static final int CORRIDOR_ACTIVE = 160;

    /** 旋转角速度（度/tick）。原设计约 28 度/秒 → 1.4。 */
    public static final double CORRIDOR_SPIN = 1.4D;

    /** 散射轮数。 */
    public static final int CORRIDOR_WAVES = 6;

    /** 每轮散射弹数。原设计 20~24，砍到 16。 */
    public static final int CORRIDOR_BOLTS = 48;

    /** 散射弹速度（格/tick）。原设计 10~12 px/帧 → 约 0.55。 */
    public static final double CORRIDOR_BOLT_SPEED = 0.55D;

    // ================================================================
    // 二十二、招式 —— 延迟星痕（阶段 4）
    // ================================================================

    /** 盘绕时留下的星痕数量。 */
    public static final int SCAR_COUNT = 66;

    /** 星痕预警（tick）。原设计"先预警后爆发"。 */
    public static final int SCAR_WARN = 34;

    /** 星痕爆发时长（tick）。 */
    public static final int SCAR_ACTIVE = 8;

    /** 星痕半径（格）。 */
    public static final double SCAR_RADIUS = 2.6D;

    // ================================================================
    // 二十三、星核暴露窗口（阶段 3）
    // ================================================================

    /**
     * 暴露窗口期间，动态减伤被削到原来的多少倍。
     *
     * <p>原设计："第三阶段……并有短暂星核暴露窗口。"
     * 这是给玩家的<b>输出窗口</b> —— 围圈那几个动作会让星核裸露在外，
     * 此时 Boss 的减伤近乎失效。没有这个窗口，第三阶段就只是"更密的弹幕"，
     * 而没有"该反击了"的节奏变化。
     */
    public static final double EXPOSED_REDUCTION_SCALE = 0.15D;

    /** 暴露窗口结束后，减伤需要多久恢复（tick）。 */
    public static final int EXPOSED_RECOVER_TICKS = 40;

    /** 单个招式一次生成弹幕的硬上限，防止某个参数配错直接把服务器打爆。 */
    public static final int MAX_BOLTS_PER_VOLLEY = 72;

    // ================================================================
    // 配置覆盖(beta41 起)
    // ----------------------------------------------------------------
    // 为什么是「可写静态字段 + 启动时灌值」而不是把 238 个调用点改成 getter:
    // 调用点全部写成 ErebosTuning.XXX,把 final 去掉后它们一行都不用改,
    // 而配置只需在加载后灌一次。代价是运行中改配置不会立刻生效 ——
    // 对 Boss 数值来说这反而是想要的(打到一半的 Boss 不该突然换血)。
    // ================================================================

    /** 从配置覆盖默认值(配置未加载时静默跳过)。 */
    public static void applyConfig() {
        try {
            MAX_HEALTH   = readD(com.lost_realm.config.MedalsConfig.EREBOS_HEALTH, MAX_HEALTH);
            PHASE_1_END  = (float) (readI(com.lost_realm.config.MedalsConfig.EREBOS_PHASE_1, Math.round(PHASE_1_END * 100)) / 100.0D);
            PHASE_2_END  = (float) (readI(com.lost_realm.config.MedalsConfig.EREBOS_PHASE_2, Math.round(PHASE_2_END * 100)) / 100.0D);
            PHASE_3_END  = (float) (readI(com.lost_realm.config.MedalsConfig.EREBOS_PHASE_3, Math.round(PHASE_3_END * 100)) / 100.0D);
            CRUISE_SPEED = readD(com.lost_realm.config.MedalsConfig.EREBOS_CRUISE_SPEED, CRUISE_SPEED);
            CHARGE_SPEED = readD(com.lost_realm.config.MedalsConfig.EREBOS_CHARGE_SPEED, CHARGE_SPEED);
            CONTACT_HEAD = (float) readD(com.lost_realm.config.MedalsConfig.EREBOS_CONTACT_HEAD, CONTACT_HEAD);
            CONTACT_BODY = (float) readD(com.lost_realm.config.MedalsConfig.EREBOS_CONTACT_BODY, CONTACT_BODY);
            REDUCTION_CAP = readI(com.lost_realm.config.MedalsConfig.EREBOS_REDUCTION_CAP, (int) Math.round(REDUCTION_CAP * 100)) / 100.0D;
            LEASH_RANGE  = readI(com.lost_realm.config.MedalsConfig.EREBOS_LEASH, (int) LEASH_RANGE);
        } catch (Throwable ignored) {
            // 配置没加载好(启动早期)时保持默认值,绝不因此崩溃
        }
    }

    private static double readD(net.minecraftforge.common.ForgeConfigSpec.DoubleValue v, double fallback) {
        try {
            Double got = v == null ? null : v.get();
            return got == null ? fallback : got;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static int readI(net.minecraftforge.common.ForgeConfigSpec.IntValue v, int fallback) {
        try {
            Integer got = v == null ? null : v.get();
            return got == null ? fallback : got;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private ErebosTuning() {}
}
