package com.lost_realm.boss.erebos;

/**
 * Erebos 的路径跟随核心 —— <b>有界历史轨迹 + 弧长采样</b>。
 *
 * <h2>为什么不能直接套原版的 {@code DragonFlightHistory}</h2>
 * 原版末影龙用它记录头部轨迹（内部是 {@code Node[64]}），但两个关键差异让它没法直接用：
 * <ol>
 *   <li><b>缓冲太短。</b>它只有 64 个点，取的是「延迟 N 个点」的位置。
 *       我们的虫身是 <b>40 节 × 2.4 格 = 96 格</b>长，巡游时头部每 tick 走约 0.55 格，
 *       光把身体铺满就需要约 175 tick —— 64 个点远远不够，硬套会让整条虫缩成一团。</li>
 *   <li><b>它不是按弧长取的。</b>「延迟 N 个点」隐含「每 tick 位移相同」的假设，
 *       而这条虫会从巡游（0.55 格/tick）一路加速到冲锋（2.6 格/tick），
 *       速度差近 5 倍 —— 用固定延迟取点，冲锋时整条身体会被拉成一条直线，
 *       停下时又会挤成一坨。</li>
 * </ol>
 *
 * <h2>算法</h2>
 * 逐 tick 记录头部位置（环形缓冲），采样时<b>从最新点往回</b>累积路径长度，
 * 每走满一个「节距」就解出一个体节 —— 这正是那份设计里要求的
 * 「体节按头部真实走过路径的弧长逐节跟随」。速度变了也无所谓，
 * 因为算的是<b>走过的弧长</b>而不是<b>经过的 tick 数</b>。
 *
 * <h2>性能</h2>
 * 关键点是<b>一次回溯同时解出全部节</b>（内层 while 推进节索引），
 * 复杂度是 O(回溯的点数 + 节数)，而不是 O(节数 × 点数)。
 * 40 节的虫每 tick 只做几百次浮点运算 —— 这是它能跑在 20tps 上的原因。
 *
 * <h2>零分配</h2>
 * 采样结果写进调用方传进来的 {@code double[]}，本类自身在稳态下不产生任何垃圾。
 * 虫子每秒跑 20 次采样，若每次 new 数组，一分钟就是 1200 个短命数组 ——
 * 对 MC 这种每 tick 都在跟 GC 抢时间的环境，这种细节是要抠的。
 */
public final class ErebosTrail {

    /**
     * 环形缓冲容量（点数）。
     *
     * <p>需要覆盖「最慢有效速度下走完一整条身长」的时长：
     * 巡游 0.55 格/tick 时，铺满 96 格只要约 175 tick。
     * 取 1024 留了 6 倍余量 —— 哪怕头部慢到 0.1 格/tick（96 格需 960 tick）也不会截断。
     */
    private static final int CAPACITY = 1024;

    /** 相邻记录点的最小间距（格）。低于此值不记录，避免原地悬停时把缓冲塞满重复点。 */
    private static final double MIN_STEP = 0.02D;

    private final double[] xs = new double[CAPACITY];
    private final double[] ys = new double[CAPACITY];
    private final double[] zs = new double[CAPACITY];

    /**
     * 轨迹断点标记。
     *
     * <p>跨星门时头部会"从 A 消失、在 B 出现"，中间那段根本不是它走过的路。
     * 如果不做标记，跟随算法会把 A→B 之间的<strong>空间距离</strong>当成路径长度，
     * 于是整条虫被拉成一条横跨战场的长条（原设计特意警告过这一点）。
     *
     * <p>标记之后，采样回溯到断点就停下，剩下的节全部堆在断点处 ——
     * 表现为"身体后半截还留在门里"，正是想要的观感。
     */
    private final boolean[] breaks = new boolean[CAPACITY];

    /** 最新点的槽位。-1 表示缓冲为空。 */
    private int head = -1;

    /** 已记录的点数（上限 {@link #CAPACITY}）。 */
    private int size;

    /**
     * 把 {@link #head} 往回数 {@code back} 个点，返回其槽位。
     *
     * <p>用「先加容量再取模」避免负数取模 —— Java 的 {@code %} 对负数返回负数，
     * 直接写 {@code (head - back) % CAPACITY} 在 head 较小时会算出负数下标并抛异常。
     */
    private int slot(int back) {
        return (head - back + CAPACITY * 2) % CAPACITY;
    }

    /** 记录一个头部位置。 */
    public void push(double x, double y, double z) {
        if (head >= 0) {
            int i = slot(0);
            double dx = x - xs[i];
            double dy = y - ys[i];
            double dz = z - zs[i];
            if (dx * dx + dy * dy + dz * dz < MIN_STEP * MIN_STEP) {
                // 位移太小：不记新点。移动端每 tick 都会调用这里，
                // 悬停时若逐 tick 记点，缓冲会被同一个坐标灌满。
                return;
            }
        }
        head = (head + 1) % CAPACITY;
        xs[head] = x;
        ys[head] = y;
        zs[head] = z;
        // 环形复用：新占用的槽位必须清掉上一圈遗留的断点标记，
        // 否则会突然冒出一条"凭空断掉"的身体
        breaks[head] = false;
        if (size < CAPACITY) {
            size++;
        }
    }

    /**
     * 把当前位置标记为轨迹断点 —— 跨星门前后各调一次。
     *
     * <p>只需要标在"跳转前"的最后一个点上：回溯是从最新点往回走的，
     * 撞到它就停，于是跳转前的史前轨迹自然不可达。
     */
    public void markBreak() {
        if (head >= 0) {
            this.breaks[head] = true;
        }
    }

    /** 清空轨迹。Boss 重置、跨维度、或玩家瞬移导致身体会拉成一条直线时调用。 */
    public void clear() {
        head = -1;
        size = 0;
    }

    /**
     * 把整条轨迹整体平移 —— 用于「整条虫一起瞬移」。
     *
     * <h2>为什么不是 {@link #markBreak()}</h2>
     * 打断点后，回溯到断点就停，剩下的节只能堆在断点处，
     * 然后随着头部在新位置移动，<b>一节一节</b>地从旧位置"抽"出来。
     * 这是"钻过门"的演出，但它同时也是玩家反馈里那句
     * 「头过去了，身体一节一节跟着过去」的来源 —— 看起来像身体在追头。
     *
     * <p>整体平移则完全不同：所有采样点一起挪，<b>身体形状原封不动</b>，
     * 视觉上是"整条虫同时消失、同时出现在另一处"。这才是瞬移该有的样子。
     */
    public void translate(double dx, double dy, double dz) {
        for (int i = 0; i < size; i++) {
            int idx = slot(i);
            xs[idx] += dx;
            ys[idx] += dy;
            zs[idx] += dz;
        }
    }

    /** 轨迹上已记录的点数。 */
    public int size() {
        return size;
    }

    /** 轨迹是否已足够铺满整条虫身。 */
    public boolean isReady(int segments, double spacing) {
        return length() >= segments * spacing;
    }

    /**
     * 轨迹当前<b>有效</b>长度（格）。
     *
     * <p>关键点：遇到断点就停。跨星门后头部在 B、史前轨迹在 A，
     * 两者之间的直线距离不是"走过的路" —— 若把它算进来，
     * 长度会瞬间从 0 跳到 200+，于是 {@link #isReady} 立刻返回 true，
     * "身体逐节跟进"就永远等不到正确的结束时机。
     *
     * <p>也就是说：这个数现在真正代表"离断点有多远"，
     * 也就是"身体从门里出来了多少"。
     */
    public double length() {
        if (size < 2) {
            return 0.0D;
        }
        double total = 0.0D;
        double px = xs[slot(0)];
        double py = ys[slot(0)];
        double pz = zs[slot(0)];
        for (int i = 1; i < size; i++) {
            int idx = slot(i);
            if (breaks[idx]) {
                break;
            }
            double dx = xs[idx] - px;
            double dy = ys[idx] - py;
            double dz = zs[idx] - pz;
            total += Math.sqrt(dx * dx + dy * dy + dz * dz);
            px = xs[idx];
            py = ys[idx];
            pz = zs[idx];
        }
        return total;
    }

    /**
     * 按弧长采样出各节的位置。
     *
     * <p>第 0 节 = 头部当前位置，第 k 节 = 沿路径往回 k × {@code spacing} 格处，
     * 线性插值落在相邻两个记录点之间。
     *
     * <p>轨迹长度不足时（刚生成、或刚跨过星门断层），剩余节全部落在
     * <b>轨迹最末端</b> —— 于是身体表现为"还没钻出来"，而不是被拉长到天上。
     * 开场「逐节钻出」的演出正是靠这个行为自然成立的。
     *
     * @param out     输出数组，长度至少 {@code segments * 3}（x,y,z 依次排列）
     * @param segments 要解出的节数（含头部）
     * @param spacing  相邻两节的弧长间距（格）
     */
    public void sample(double[] out, int segments, double spacing) {
        if (segments <= 0) {
            return;
        }
        if (size < 2) {
            // 还没有轨迹：整条虫缩在当前位置
            double x = size == 1 ? xs[head] : 0.0D;
            double y = size == 1 ? ys[head] : 0.0D;
            double z = size == 1 ? zs[head] : 0.0D;
            for (int k = 0; k < segments; k++) {
                out[k * 3] = x;
                out[k * 3 + 1] = y;
                out[k * 3 + 2] = z;
            }
            return;
        }

        // 第 0 节固定在最新点上
        int newest = slot(0);
        out[0] = xs[newest];
        out[1] = ys[newest];
        out[2] = zs[newest];

        int k = 1;                 // 下一个待解出的节
        double target = spacing;   // 下一个目标弧长
        double travelled = 0.0D;

        double px = xs[newest];
        double py = ys[newest];
        double pz = zs[newest];

        for (int i = 1; i < size && k < segments; i++) {
            int idx = slot(i);
            // 撞到断点：停止回溯，剩下的节堆在<b>断点本身</b>（身体"还在门里"）
            if (breaks[idx]) {
                px = xs[idx];
                py = ys[idx];
                pz = zs[idx];
                break;
            }
            double cx = xs[idx];
            double cy = ys[idx];
            double cz = zs[idx];

            double dx = cx - px;
            double dy = cy - py;
            double dz = cz - pz;
            double seg = Math.sqrt(dx * dx + dy * dy + dz * dz);

            // 一段路径可能同时跨过好几个节距（高速冲锋时一段位移可能大于一个节距）
            while (k < segments && travelled + seg >= target) {
                double t = seg > 1.0E-9D ? (target - travelled) / seg : 0.0D;
                out[k * 3] = px + dx * t;
                out[k * 3 + 1] = py + dy * t;
                out[k * 3 + 2] = pz + dz * t;
                k++;
                target += spacing;
            }

            travelled += seg;
            px = cx;
            py = cy;
            pz = cz;
        }

        // 轨迹不够长：剩余节堆在「最后解到的位置」。
        //   - 正常走完（还没铺满）：就是轨迹最旧的那个点，身体"还没钻出来"；
        //   - 撞到断点：就是断点本身（跨星门时那一头还在门里）。
        //
        // ⚠️ 这里曾经固定用 slot(size - 1)（环形缓冲里最旧的点），
        //    正常情况没问题，但<b>撞断点时全错了</b> ——
        //    最旧的点可能是上千个样本之前、几十格开外的位置，
        //    于是瞬移之后整条身体被甩到那个久远的坐标上，
        //    看起来就像"头过去了，身体还傻在原地"。玩家实测反馈过这个现象。
        for (; k < segments; k++) {
            out[k * 3] = px;
            out[k * 3 + 1] = py;
            out[k * 3 + 2] = pz;
        }
    }

    /**
     * 取第 {@code index} 节当前所在的槽位下标（内部用，仅供调试/校验）。
     *
     * <p>不推荐在热路径上用 —— 它每次都要重新回溯一遍。
     */
    int rawSlot(int back) {
        return slot(Math.min(back, Math.max(0, size - 1)));
    }
}
