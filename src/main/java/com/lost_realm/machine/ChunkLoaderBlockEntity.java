package com.lost_realm.machine;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * <b>区块加载器</b>的方块实体 —— 真正负责强加载的那一层。
 *
 * <h2>为什么用 setChunkForced 而不是 ForgeChunkManager</h2>
 * Forge 的 {@code ForgeChunkManager} 每个 ticket 有区块数量上限
 * （{@code forge.maxChunksPerTicket}，默认 25）。9×9 是 81 个区块，
 * 一次装不下，得切成四个 ticket 分别管理，代码复杂度陡增。
 *
 * <p>而 {@link ServerLevel#setChunkForced} 就是原版 {@code /forceload} 命令用的东西，
 * 没有数量上限，而且会被原版自己写进存档的 forced-chunks 数据里 ——
 * 重启后即使本模组的方块实体还没加载，区块也是强加载状态。
 *
 * <h2>为什么要自己维护引用计数</h2>
 * {@code setChunkForced} 是布尔开关，没有"谁请求的"这个概念。
 * 两个加载器范围重叠时，拆掉其中一个就会把公共区块关掉，另一个跟着失效。
 * 所以这里按「维度 → 区块 → 请求数」记一份计数，归零才真正关。
 *
 * <p>计数只存在内存里：世界重启后每个加载器的 {@link #onLoad()} 会重新登记一遍，
 * 计数自然重建。不需要持久化，持久化反而会在"方块被外力删除但数据残留"时出错。
 */
public class ChunkLoaderBlockEntity extends BlockEntity {

    /** 允许的边长，全是奇数，保证以加载器为中心对称。 */
    public static final int[] SIZES = {1, 3, 5, 7, 9};

    private static final Map<ResourceKey<Level>, Map<Long, Integer>> FORCE_COUNT = new HashMap<>();

    /** 本实体当前占着的区块（ChunkPos.asLong）。释放时按这份清单逐个减计数。 */
    private final Set<Long> mine = new HashSet<>();

    /** 边长（区块数）。 */
    private int size = 3;

    /** 开关。关闭时保留设置但不强加载。 */
    private boolean active = false;

    public ChunkLoaderBlockEntity(BlockPos pos, BlockState state) {
        super(MachineBlocks.CHUNK_LOADER_BE.get(), pos, state);
    }

    // ================================================================
    // 强加载的施加与释放
    // ================================================================

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            // 不能在 onLoad 里直接 setChunkForced：这时候区块自己还在加载，
            // 立刻去强制加载邻居会踩进同一轮加载流程里，服务端主线程会抛
            // "Cannot add chunk while loading"。丢到下一 tick 再施加就没这问题。
            server.getServer().execute(() -> {
                if (!isRemoved() && level == server) {
                    apply(server);
                }
            });
        }
    }

    /**
     * 按当前 size / active 重新施加一遍。
     *
     * <p>先释放再施加，是因为改尺寸时旧范围可能比新范围大，
     * 不先清掉会留下"幽灵加载"——方块已经缩成 1×1，但 9×9 的区块还开着。
     */
    public void apply(ServerLevel server) {
        release(server);
        if (!active) {
            return;
        }
        int r = size / 2;
        int cx = getBlockPos().getX() >> 4;
        int cz = getBlockPos().getZ() >> 4;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                force(server, cx + dx, cz + dz);
            }
        }
    }

    private void force(ServerLevel server, int cx, int cz) {
        long key = ChunkPos.asLong(cx, cz);
        Map<Long, Integer> table =
                FORCE_COUNT.computeIfAbsent(server.dimension(), k -> new HashMap<>());
        int n = table.merge(key, 1, Integer::sum);
        mine.add(key);
        if (n == 1) {
            server.setChunkForced(cx, cz, true);
        }
    }

    /** 释放本实体占着的全部区块。计数归零才真正取消强加载。 */
    public void release(ServerLevel server) {
        if (mine.isEmpty()) {
            return;
        }
        Map<Long, Integer> table = FORCE_COUNT.get(server.dimension());
        for (long key : mine) {
            int cx = ChunkPos.getX(key);
            int cz = ChunkPos.getZ(key);
            if (table == null) {
                server.setChunkForced(cx, cz, false);
                continue;
            }
            int n = table.merge(key, -1, Integer::sum);
            if (n <= 0) {
                table.remove(key);
                server.setChunkForced(cx, cz, false);
            }
        }
        mine.clear();
    }

    /** 方块被破坏时调用。区块卸载不算 —— 那种情况下实体还会再加载回来。 */
    public void onBlockedRemoved() {
        if (level instanceof ServerLevel server) {
            release(server);
        }
    }

    // ================================================================
    // 设置
    // ================================================================

    public int size() {
        return size;
    }

    public boolean isActive() {
        return active;
    }

    /** 边长必须落在 {@link #SIZES} 里，越界的值夹到最近的合法档。 */
    public void setSize(int next) {
        int clamped = SIZES[0];
        for (int s : SIZES) {
            if (Math.abs(s - next) < Math.abs(clamped - next)) {
                clamped = s;
            }
        }
        if (clamped == size) {
            return;
        }
        this.size = clamped;
        setChanged();
        if (level instanceof ServerLevel server) {
            apply(server);
        }
    }

    public void setActive(boolean next) {
        if (next == active) {
            return;
        }
        this.active = next;
        setChanged();
        if (level instanceof ServerLevel server) {
            apply(server);
        }
    }

    // ================================================================
    // 存档
    // ================================================================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Size", size);
        tag.putBoolean("Active", active);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // 存档里的值可能是旧版本写的、也可能是手改 NBT，统一夹到合法档位，
        // 否则 size=64 会让一放下去就强加载 4096 个区块。
        int saved = tag.contains("Size") ? tag.getInt("Size") : 3;
        size = SIZES[0];
        for (int s : SIZES) {
            if (Math.abs(s - saved) < Math.abs(size - saved)) {
                size = s;
            }
        }
        active = tag.getBoolean("Active");
    }
}
