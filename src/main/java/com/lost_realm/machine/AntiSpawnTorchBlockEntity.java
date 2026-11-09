package com.lost_realm.machine;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * <b>镇魔火把</b>的方块实体 —— 唯一职责是把自己的坐标登记到一张静态表里。
 *
 * <h2>为什么要登记，不直接扫方块</h2>
 * 刷怪判定是高频事件：一个加载中的区块群每秒可能触发几十次
 * {@code CheckSpawn}。如果每次都去扫半径 32 格的三维方块（65×65×65 ≈ 27 万个格子），
 * 服务器直接卡死。所以火把一放下就把坐标记进 set，判定时只遍历"现存的火把"，
 * 通常只有几个元素，是常数级开销。
 *
 * <h2>卸载时从这里移除是安全的</h2>
 * {@code setRemoved} 在区块卸载时也会触发，这里会把坐标从表里拿掉；
 * 区块重新加载时 {@link #onLoad()} 又会加回来。
 * 中间这段时间区块本身没加载，也就不会有刷怪尝试，漏判不会产生实际后果。
 */
public class AntiSpawnTorchBlockEntity extends BlockEntity {

    /** 保护半径（方块）。64×64 指的是直径。 */
    public static final int RADIUS = 32;

    /** 竖直方向也给同样的范围，形成 64³ 的立方保护区。 */
    public static final int VERTICAL = 32;

    private static final Map<ResourceKey<Level>, Set<BlockPos>> TORCHES = new HashMap<>();

    public AntiSpawnTorchBlockEntity(BlockPos pos, BlockState state) {
        super(MachineBlocks.ANTI_SPAWN_TORCH_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) {
            TORCHES.computeIfAbsent(level.dimension(), k -> new HashSet<>())
                    .add(getBlockPos().immutable());
        }
    }

    @Override
    public void setRemoved() {
        if (level != null) {
            Set<BlockPos> set = TORCHES.get(level.dimension());
            if (set != null) {
                set.remove(getBlockPos());
            }
        }
        super.setRemoved();
    }

    /** 该坐标是否落在任意一支镇魔火把的保护范围内。 */
    public static boolean isProtected(Level level, double x, double y, double z) {
        Set<BlockPos> set = TORCHES.get(level.dimension());
        if (set == null || set.isEmpty()) {
            return false;
        }
        for (BlockPos p : set) {
            if (Math.abs(p.getX() + 0.5D - x) <= RADIUS
                    && Math.abs(p.getZ() + 0.5D - z) <= RADIUS
                    && Math.abs(p.getY() + 0.5D - y) <= VERTICAL) {
                return true;
            }
        }
        return false;
    }

    /** 世界卸载时清表，避免单机切换存档后旧坐标残留。 */
    public static void clearLevel(ResourceKey<Level> dimension) {
        TORCHES.remove(dimension);
    }
}
