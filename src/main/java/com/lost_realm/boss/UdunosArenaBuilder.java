package com.lost_realm.boss;

import java.lang.reflect.Method;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import com.lost_realm.Medals;
import com.lost_realm.boss.block.LostAltarBlock;

/**
 * <b>终末竞技场</b> —— 遗落境维度里唯一的建筑，也是乌蠹诺斯的召唤场。
 *
 * <h2>布局（beta25 重做）</h2>
 * <pre>
 *                    半径 123 的外墙（高 30，四道门）
 *
 *          ●  ●  ●  ●  ●  ●  ●  ●  ●  ●  ●  ●      ← 12 根巨柱，半径 100，高 47
 *                        （每根柱顶一座祭坛 + 根之图腾）
 *
 *                          ███████
 *                          █ 祭坛 █                 ← 中央三层圆台 + 核心祭坛
 *                          ███████
 * </pre>
 * <b>中间只有一个祭坛，其余全部空出来。</b>旧版把空间切成了广场、露台、
 * 角塔、柱廊，看起来很"满"，但尺度感反而被切碎了。空旷才显大。
 *
 * <h2>为什么用原版方块 + 两个自定义方块混搭</h2>
 * 全用自定义方块要额外画十几张 16×16 贴图，而竞技场真正需要"自定义质感"的
 * 只有祭坛本身。地面、柱身、外墙用深板岩 / 黑石 / 哭泣的黑曜石，
 * 与原版的紫黑基调天然吻合，也省掉一整批资源文件。
 */
public final class UdunosArenaBuilder {

    /** 中心。 */
    private static BlockPos center() {
        return new BlockPos(BossRegistry.ARENA_CENTER_X,
                BossRegistry.ARENA_CENTER_Y,
                BossRegistry.ARENA_CENTER_Z);
    }

    /** 中央核心祭坛的坐标（右键点）。 */
    public static BlockPos coreAltarPos() {
        return center().offset(0, BossRegistry.ALTAR_CORE_HEIGHT, 0);
    }

    /**
     * 第 {@code i} 根巨柱顶端的祭坛坐标。
     *
     * <p>建造与仪式共用这一个方法 —— 两处各算一遍必然出现"粒子喷在柱子旁边"
     * 的错位，而且这种错位在单人测试里很难发现。
     *
     * <p>角度偏移 {@code 7.5°}：柱子正好错开四道正门，玩家从门进来不会被柱子挡视线。
     */
    public static BlockPos pillarAltarPos(int i) {
        if (i < 0 || i >= BossRegistry.PILLAR_COUNT) {
            return null;
        }
        double angle = Math.PI * 2.0D / BossRegistry.PILLAR_COUNT * i
                + Math.PI / BossRegistry.PILLAR_COUNT;
        int dx = (int) Math.round(Math.cos(angle) * BossRegistry.PILLAR_RING_RADIUS);
        int dz = (int) Math.round(Math.sin(angle) * BossRegistry.PILLAR_RING_RADIUS);
        // 基座 3 层 + 柱身 PILLAR_HEIGHT + 柱头 1 层，祭坛<b>再上一层</b>。
        // 祭坛若与柱头同层，会覆盖柱头中心那一格、并且整块陷进平台里 ——
        // 12/16 高的祭坛模型被 16/16 的平台方块包住，几乎看不出来。
        int dy = 3 + BossRegistry.PILLAR_HEIGHT + 2;
        return center().offset(dx, dy, dz);
    }

    /** 玩家从传送门进来的落点（外墙内侧）。 */
    public static BlockPos entrySpot() {
        return center().offset(0, 1, BossRegistry.ARENA_RADIUS - 10);
    }

    // ================================================================
    // 入口
    // ================================================================

    /**
     * 确保竞技场已生成（幂等）。
     *
     * <p>哨兵换成了「中心地下 4 格的哭泣的黑曜石」—— 旧版的哨兵是地下 3 格的
     * ARENA_FRAME，所有旧存档都已经有它了，继续用它会导致<b>新竞技场永远不生成</b>。
     * 换位置等于给旧存档一次"自动重建"。
     */
    public static boolean ensureGenerated(ServerLevel level) {
        BlockPos sentinel = center().below(4);
        if (level.getBlockState(sentinel).is(Blocks.CRYING_OBSIDIAN)) {
            return false;
        }

        Medals.LOGGER.info("[Lost Realm] 正在建造终末竞技场（半径 {}，直径 {} 格）...",
                BossRegistry.ARENA_RADIUS, BossRegistry.ARENA_RADIUS * 2);
        long t0 = System.currentTimeMillis();

        preloadChunks(level);
        level.setBlock(sentinel, Blocks.CRYING_OBSIDIAN.defaultBlockState(), 2);

        clearAbove(level);
        buildFloor(level);
        buildFloorRings(level);
        buildCentralAltar(level);
        buildPillars(level);
        buildOuterWall(level);

        Medals.LOGGER.info("[Lost Realm] 终末竞技场建造完成，用时 {} ms。",
                System.currentTimeMillis() - t0);
        return true;
    }

    /** 预加载覆盖范围内的全部 chunk。半径 141 → 19×19 ≈ 361 个。 */
    private static void preloadChunks(ServerLevel level) {
        BlockPos c = center();
        int r = BossRegistry.ARENA_RADIUS + 16;
        int minCx = (c.getX() - r) >> 4;
        int maxCx = (c.getX() + r) >> 4;
        int minCz = (c.getZ() - r) >> 4;
        int maxCz = (c.getZ() + r) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                level.getChunk(cx, cz);
            }
        }
    }

    /**
     * 清掉旧建筑。
     *
     * <p>只读不写为主：旧存档里这片空间绝大部分是空气，逐个 {@code setBlock}
     * 会是 200 万次写入，而先判断"不是空气才写"能把写入压到几千次。
     * 250 万次读取在已加载的 chunk 上是毫秒级，代价可以忽略。
     */
    private static void clearAbove(ServerLevel level) {
        BlockPos c = center();
        int r = BossRegistry.ARENA_RADIUS + 3;
        int r2 = r * r;
        BlockState air = Blocks.AIR.defaultBlockState();
        int y0 = c.getY() + 1;
        int y1 = c.getY() + 120;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r2) {
                    continue;
                }
                for (int y = y0; y <= y1; y++) {
                    BlockPos p = new BlockPos(c.getX() + dx, y, c.getZ() + dz);
                    if (!level.getBlockState(p).isAir()) {
                        level.setBlock(p, air, 2);
                    }
                }
            }
        }
    }

    // ================================================================
    // 地面
    // ================================================================

    /**
     * 圆形地面。
     *
     * <p>中心用深板岩砖，外圈用虚空石，边缘三格铺装饰 —— 三层材质把
     * "作战区 / 环道 / 边界"分出来，不需要真的画线。
     */
    private static void buildFloor(ServerLevel level) {
        BlockPos c = center();
        BlockState core = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState mid = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState outer = BossBlocks.VOID_STONE.get().defaultBlockState();
        BlockState edge = BossBlocks.ARENA_FRAME.get().defaultBlockState();

        int r = BossRegistry.ARENA_RADIUS;
        int r2 = r * r;
        int inner = 34;
        int inner2 = inner * inner;
        int midR = 96;
        int midR2 = midR * midR;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > r2) {
                    continue;
                }
                BlockPos p = c.offset(dx, 0, dz);
                BlockState state;
                if (d2 <= inner2) {
                    state = core;
                } else if (d2 <= midR2) {
                    state = mid;
                } else {
                    state = outer;
                }
                if (d2 > (r - 3) * (r - 3)) {
                    state = edge;
                }
                level.setBlock(p, state, 2);
                // 只有边缘垫两层：从外面看不会露出"薄片"，中心区省一半写入
                if (d2 > (r - 6) * (r - 6)) {
                    level.setBlock(p.below(), outer, 2);
                    level.setBlock(p.below(2), outer, 2);
                }
            }
        }
    }

    /**
     * 同心发光环 + 十二条辐射线。
     *
     * <p>这是这个竞技场最重要的"尺度提示"：站在中央往外看，
     * 一圈一圈的环会把 250 格的直径读出来；没有这些环，空旷就只是空旷。
     */
    private static void buildFloorRings(ServerLevel level) {
        BlockPos c = center();
        BlockState glow = BossBlocks.ARENA_FRAME.get().defaultBlockState();
        BlockState rune = Blocks.CRYING_OBSIDIAN.defaultBlockState();

        int r = BossRegistry.ARENA_RADIUS - 6;
        int step = BossRegistry.FLOOR_RING_STEP;

        // 同心环：每隔 20 格一圈，宽 1 格
        for (int ring = step; ring <= r; ring += step) {
            int steps = (int) Math.ceil(Math.PI * 2.0D * ring);
            for (int i = 0; i < steps; i++) {
                double a = Math.PI * 2.0D * i / steps;
                int dx = (int) Math.round(Math.cos(a) * ring);
                int dz = (int) Math.round(Math.sin(a) * ring);
                level.setBlock(c.offset(dx, 0, dz), glow, 2);
            }
        }

        // 辐射线：与十二根柱子同角度，从祭坛边缘拉到外墙
        for (int i = 0; i < BossRegistry.PILLAR_COUNT; i++) {
            double a = Math.PI * 2.0D / BossRegistry.PILLAR_COUNT * i
                    + Math.PI / BossRegistry.PILLAR_COUNT;
            for (int d = 30; d <= BossRegistry.ARENA_RADIUS - 6; d += 2) {
                int dx = (int) Math.round(Math.cos(a) * d);
                int dz = (int) Math.round(Math.sin(a) * d);
                level.setBlock(c.offset(dx, 0, dz), rune, 2);
            }
        }
    }

    // ================================================================
    // 中央祭坛
    // ================================================================

    /**
     * 中央祭坛：三层圆台 + 核心方块 + 符文环。
     *
     * <p>圆台半径 26 / 18 / 12，每层高 1 —— 玩家从地面走上来只有一个台阶，
     * 不会挡住近战；但视觉上明显"抬起来"，强化它是全场的焦点。
     */
    private static void buildCentralAltar(ServerLevel level) {
        BlockPos c = center();
        BlockState base = BossBlocks.VOID_STONE.get().defaultBlockState();
        BlockState step = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        BlockState trim = BossBlocks.ARENA_FRAME.get().defaultBlockState();

        int baseR = BossRegistry.ALTAR_BASE_RADIUS;
        int[] radii = {baseR, baseR - 8, baseR - 14};

        for (int layer = 0; layer < radii.length; layer++) {
            int r = radii[layer];
            int y = 1 + layer;
            int r2 = r * r;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    int d2 = dx * dx + dz * dz;
                    if (d2 > r2) {
                        continue;
                    }
                    BlockPos p = c.offset(dx, y, dz);
                    boolean edge = d2 > (r - 2) * (r - 2);
                    level.setBlock(p, edge ? trim : (layer == 0 ? base : step), 2);
                }
            }
        }

        // 核心方块：3×3 的祭坛基座，正中间那一块可右键
        int top = 1 + radii.length - 1;   // = 3
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(c.offset(dx, top, dz), step, 2);
            }
        }
        BlockPos corePos = coreAltarPos();
        level.setBlock(corePos.below(), Blocks.CRYING_OBSIDIAN.defaultBlockState(), 2);
        level.setBlock(corePos, BossBlocks.LOST_ALTAR.get().defaultBlockState()
                .setValue(LostAltarBlock.CORE, true)
                .setValue(LostAltarBlock.ACTIVE, false), 3);

        // 核心周围四个方位的小符文柱，把"这里可以互动"指出来
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 2.0D * k + Math.PI / 4.0D;
            int dx = (int) Math.round(Math.cos(a) * 4.2D);
            int dz = (int) Math.round(Math.sin(a) * 4.2D);
            level.setBlock(c.offset(dx, top, dz), trim, 2);
            level.setBlock(c.offset(dx, top + 1, dz), trim, 2);
        }

        // 顶面外圈符文：半径 10 的圆，间隔放哭泣的黑曜石（微光）
        int ringR = 10;
        int steps = (int) Math.ceil(Math.PI * 2.0D * ringR);
        for (int i = 0; i < steps; i++) {
            double a = Math.PI * 2.0D * i / steps;
            int dx = (int) Math.round(Math.cos(a) * ringR);
            int dz = (int) Math.round(Math.sin(a) * ringR);
            level.setBlock(c.offset(dx, top, dz), Blocks.CRYING_OBSIDIAN.defaultBlockState(), 2);
        }

        // 核心上方留空（Boss 从这里升起），但放一个隐形光源让祭坛有顶光
        level.setBlock(corePos.above(3), Blocks.LIGHT.defaultBlockState(), 2);
    }

    // ================================================================
    // 十二根巨柱
    // ================================================================

    /**
     * 十二根巨柱 + 柱顶祭坛 + 根之图腾。
     *
     * <p>柱身 3×3 实心，每 7 格一道镶边（原版黑石 + 镶金黑石交替），
     * 柱头 5×5 平台。高 42 格 —— 站在柱脚抬头需要约 60°，这才叫"巨柱"。
     */
    private static void buildPillars(ServerLevel level) {
        BlockPos c = center();
        BlockState shaft = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        BlockState band = Blocks.GILDED_BLACKSTONE.defaultBlockState();
        BlockState base = BossBlocks.VOID_STONE.get().defaultBlockState();
        BlockState trim = BossBlocks.ARENA_FRAME.get().defaultBlockState();

        for (int i = 0; i < BossRegistry.PILLAR_COUNT; i++) {
            BlockPos altar = pillarAltarPos(i);
            if (altar == null) {
                continue;
            }
            int dx = altar.getX() - c.getX();
            int dz = altar.getZ() - c.getZ();

            // ---- 基座：5×5 → 3×3，三层 ----
            for (int layer = 0; layer < 3; layer++) {
                int r = 2 - layer;          // 2,1,0
                int y = 1 + layer;
                for (int ox = -r; ox <= r; ox++) {
                    for (int oz = -r; oz <= r; oz++) {
                        level.setBlock(c.offset(dx + ox, y, dz + oz),
                                layer == 2 ? trim : base, 2);
                    }
                }
            }

            // ---- 柱身：3×3，42 格 ----
            int shaftBase = 4;
            int shaftTop = shaftBase + BossRegistry.PILLAR_HEIGHT - 1;
            for (int y = shaftBase; y <= shaftTop; y++) {
                boolean ring = (y - shaftBase) % 7 == 0;
                for (int ox = -1; ox <= 1; ox++) {
                    for (int oz = -1; oz <= 1; oz++) {
                        level.setBlock(c.offset(dx + ox, y, dz + oz),
                                ring ? band : shaft, 2);
                    }
                }
            }

            // ---- 柱头：5×5 平台 ----
            int capY = shaftTop + 1;
            for (int ox = -2; ox <= 2; ox++) {
                for (int oz = -2; oz <= 2; oz++) {
                    boolean edge = Math.abs(ox) == 2 || Math.abs(oz) == 2;
                    level.setBlock(c.offset(dx + ox, capY, dz + oz),
                            edge ? trim : base, 2);
                }
            }

            // ---- 柱顶祭坛 ----
            level.setBlock(altar, BossBlocks.LOST_ALTAR.get().defaultBlockState()
                    .setValue(LostAltarBlock.CORE, false)
                    .setValue(LostAltarBlock.ACTIVE, false), 3);

            // 祭坛顶上的光，让十二根柱子从远处就是十二个亮点
            level.setBlock(altar.above(2), Blocks.LIGHT.defaultBlockState(), 2);

            // ---- 根之图腾 ----
            placeTotem(level, altar);
        }
    }

    /**
     * 在祭坛顶面放一个「根之图腾」。
     *
     * <h2>为什么是「屏障 + 展示框」而不是把展示框直接挂在祭坛上</h2>
     * {@code new ItemFrame(level, 祭坛坐标, Direction.UP)} 算出来的位置是
     * {@code 祭坛坐标.y + 0.03} —— 落在<b>方块内部</b>，模型被完全遮住，
     * 看起来像"什么都没放"。而构造后 {@code setPos} 抬高也没用：
     * {@code HangingEntity#recalculateBoundingBox} 每 tick 都会按
     * 「支撑方块 + 朝向」重新算一遍，位置会被打回去。
     *
     * <p>所以改成在祭坛上方一格放<b>屏障</b>（不可见、可支撑），
     * 再把展示框挂在屏障顶面。物品离祭坛顶约 1.2 格，像悬浮供奉；
     * 屏障位于柱顶 47 格高处，玩家碰不到，不影响任何操作。
     *
     * <p>Goety 是可选依赖：没装时退回紫水晶碎片，不至于让祭坛空着。
     */
    private static void placeTotem(ServerLevel level, BlockPos altarPos) {
        // 重建时清掉旧的展示实体，否则会叠在一起
        AABB box = new AABB(altarPos).inflate(2.0D);
        for (ItemFrame old : level.getEntitiesOfClass(ItemFrame.class, box)) {
            old.discard();
        }

        Item totem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("goety", "totem_of_roots"));
        if (totem == null) {
            totem = net.minecraft.world.item.Items.AMETHYST_SHARD;
        }
        ItemStack stack = new ItemStack(totem);
        fillSouls(stack);

        BlockPos mount = altarPos.above();
        level.setBlock(mount, Blocks.BARRIER.defaultBlockState(), 2);

        ItemFrame frame = new ItemFrame(level, mount, Direction.UP);
        frame.setItem(stack);
        frame.setInvulnerable(true);
        if (!level.addFreshEntity(frame)) {
            Medals.LOGGER.warn("[Lost Realm] 根之图腾展示框放置失败于 {}", mount);
        }
    }

    /**
     * 把图腾灌满灵魂。
     *
     * <p>Goety 的 {@code ITotem} 是可选依赖里的接口，这里用反射调用 ——
     * 直接 import 会让没装 Goety 的客户端在加载这个类时抛
     * {@code NoClassDefFoundError}，而那和"要不要显示图腾"完全不成比例。
     *
     * <p>注意取上限要用<b>实例方法</b> {@code getMaxSouls()}：
     * 静态的 {@code ITotem.maximumSouls(stack)} 读的是 NBT 里的 MaxSouls 字段，
     * 全新物品没写过这个字段，会返回 0 —— 于是"灌满"变成什么都不做，
     * 图腾永远是空的。这是实测时对着展示框 NBT 才发现的。
     */
    private static void fillSouls(ItemStack stack) {
        try {
            Class<?> it = Class.forName("com.Polarice3.Goety.api.items.magic.ITotem");
            Method set = it.getMethod("setSoulsAmount", ItemStack.class, int.class);

            int max = 0;
            try {
                Method getMax = stack.getItem().getClass().getMethod("getMaxSouls");
                max = (int) getMax.invoke(stack.getItem());
            } catch (NoSuchMethodException ignored) {
                // 不是 TotemOfSouls 家族，保持原样
            }
            if (max <= 0) {
                Method maxStatic = it.getMethod("maximumSouls", ItemStack.class);
                max = (int) maxStatic.invoke(null, stack);
            }
            if (max > 0) {
                set.invoke(null, stack, max);
            }
        } catch (Throwable ignored) {
            // 没装 Goety、或该版本接口不同 —— 保持默认状态即可
        }
    }

    // ================================================================
    // 外墙
    // ================================================================

    /** 外墙：半径 123，高 30，四道 11 格宽的门。 */
    private static void buildOuterWall(ServerLevel level) {
        BlockPos c = center();
        BlockState wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState trim = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState glow = BossBlocks.ARENA_FRAME.get().defaultBlockState();

        int r = BossRegistry.WALL_RADIUS;
        int r2 = r * r;
        int innerR2 = (r - 3) * (r - 3);
        int gate = BossRegistry.GATE_HALF_WIDTH;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > r2 || d2 <= innerR2) {
                    continue;
                }
                // 四道门：正东 / 正西 / 正南 / 正北
                if ((Math.abs(dx) <= gate && Math.abs(dz) >= r - 2)
                        || (Math.abs(dz) <= gate && Math.abs(dx) >= r - 2)) {
                    continue;
                }
                for (int y = 1; y <= BossRegistry.WALL_HEIGHT; y++) {
                    BlockState state;
                    if (y == BossRegistry.WALL_HEIGHT) {
                        state = glow;
                    } else if (y % 6 == 0) {
                        state = trim;
                    } else {
                        state = wall;
                    }
                    level.setBlock(c.offset(dx, y, dz), state, 2);
                }
            }
        }

        // 四道门的门框：比墙高 6 格的两根立柱 + 门楣
        for (int k = 0; k < 4; k++) {
            int sx = k == 0 ? 1 : (k == 1 ? -1 : 0);
            int sz = k == 2 ? 1 : (k == 3 ? -1 : 0);
            int bx = sx * (r - 1);
            int bz = sz * (r - 1);
            int px = sz != 0 ? 1 : 0;   // 门的横向单位向量
            int pz = sx != 0 ? 1 : 0;
            for (int side = -1; side <= 1; side += 2) {
                for (int y = 1; y <= BossRegistry.WALL_HEIGHT + 6; y++) {
                    BlockPos p = c.offset(bx + px * gate * side, y, bz + pz * gate * side);
                    level.setBlock(p, y > BossRegistry.WALL_HEIGHT ? glow : trim, 2);
                }
            }
            for (int t = -gate; t <= gate; t++) {
                for (int y = BossRegistry.WALL_HEIGHT + 5; y <= BossRegistry.WALL_HEIGHT + 6; y++) {
                    level.setBlock(c.offset(bx + px * t, y, bz + pz * t), glow, 2);
                }
            }
        }
    }

    private UdunosArenaBuilder() {}
}
