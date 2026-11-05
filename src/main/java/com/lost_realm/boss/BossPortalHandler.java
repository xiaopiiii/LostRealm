package com.lost_realm.boss;

import java.util.Optional;
import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;

import org.jetbrains.annotations.Nullable;

import com.lost_realm.Medals;
import com.lost_realm.boss.block.RealmPortalBlock;

/**
 * 遗落境传送门的<b>核心逻辑</b> —— 传送、落点计算、框架校验、门面开凿。
 *
 * <h2>为什么不放在方块类里</h2>
 * 传送逻辑涉及三件事:①发起传送 ②决定落点 ③在目标维度搭出接应门。
 * 这三件事都要访问 ServerLevel 与服务器实例,放在方块类里会让方块承担过多职责,
 * 且方块类在专用服务器上也可能被加载 —— 单独成类更清晰。
 *
 * <h2>落点策略:竞技场固定入口</h2>
 * 由于遗落境内<b>只有一座竞技场</b>,不存在"落点搜索"的必要 ——
 * 直接返回竞技场入口坐标即可,比原版的 {@code PortalForcer} 搜索快得多,
 * 也彻底避免了"生成在虚空里"的风险。
 *
 * <h2>接应门</h2>
 * 从主世界进入遗落境时,如果竞技场入口没有门,会自动搭一座
 * (保证玩家随时能原路返回)。从遗落境回主世界时,则记在原进入点附近 ——
 * 若原进入点已失效(被填埋等),退回玩家重生点。
 */
public final class BossPortalHandler {

    /** 玩家从遗落境返回主世界时,门开在距离原进入点多远。 */
    private static final int RETURN_PORTAL_SPREAD = 16;

    /**
     * 尝试把实体传送过去。
     *
     * <p>自带冷却判定 —— 玩家站在门里时 {@code entityInside} 每 tick 都会调用,
     * 必须靠冷却拦住,否则会每 tick 传送一次。
     */
    public static void tryTeleport(ServerLevel from, Entity entity) {
        if (entity.isOnPortalCooldown()) {
            return;
        }
        Entity changed = teleport(from, entity);
        if (changed != null) {
            // 传送成功后进入冷却,防止在两端门之间反复横跳
            changed.setPortalCooldown(100);
        }
    }

    /**
     * 执行传送。
     *
     * @return 传送后的实体(null 表示未传送)
     */
    @Nullable
    public static Entity teleport(ServerLevel from, Entity entity) {
        MinecraftServer server = from.getServer();
        if (server == null) {
            return null;
        }

        boolean toRealm = !isRealm(from);
        ServerLevel target = server.getLevel(toRealm ? BossRegistry.REALM_LEVEL : Level.OVERWORLD);
        if (target == null) {
            Medals.LOGGER.warn("[Lost Realm] 目标维度未加载,传送取消。");
            return null;
        }

        return entity.changeDimension(target, new RealmTeleporter(toRealm));
    }

    /** 该维度是否就是遗落境。 */
    public static boolean isRealm(ServerLevel level) {
        return level.dimension().equals(BossRegistry.REALM_LEVEL);
    }

    /**
     * 计算传送落点。
     *
     * <p>两个方向共用一套坐标(城中心上方):
     * <ul>
     *   <li><b>进遗落境</b> → 城门内侧的入口平台,面朝城市内部</li>
     *   <li><b>回主世界</b> → 玩家重生点附近的空地</li>
     * </ul>
     */
    @Nullable
    public static PortalInfo destinationFor(ServerLevel target, Entity entity) {
        if (isRealm(target)) {
            ensureRealmArrivalPlatform(target);
            // dv5:落点改到城门内侧 —— 玩家一进来就能看见中央高台上的遗落之主
            BlockPos entry = LostCityBuilder.entrySpot();
            Vec3 pos = new Vec3(entry.getX() + 0.5D, entry.getY() + 1.0D, entry.getZ() + 0.5D);
            // 面向城市中心(北向),让玩家第一眼看到遗落之主
            return new PortalInfo(pos, Vec3.ZERO, 180.0F, 0.0F);
        }
        // 回主世界:找玩家重生点附近的落脚处
        BlockPos spawn = entity instanceof ServerPlayer sp
                ? sp.getRespawnPosition() != null ? sp.getRespawnPosition() : target.getSharedSpawnPos()
                : target.getSharedSpawnPos();
        BlockPos safe = findSafeSpot(target, spawn);
        return new PortalInfo(
                new Vec3(safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D),
                Vec3.ZERO, 0.0F, 0.0F);
    }

    // ================================================================
    // 门面与框架
    // ================================================================

    /**
     * 判断传送门框架是否完整(用于 {@code updateShape} 的自动消解)。
     *
     * <p>本模组的门<b>不依赖框架</b> —— 门面一旦被点燃就永久稳定存在,
     * 挖掉黑曜石也不会消失。这是刻意设计:遗落境的门是"被点燃的裂隙",
     * 而非"由黑曜石撑开的结构"。因此此处恒返回 {@code true}。
     */
    public static boolean isFrameworkIntact(LevelAccessor level, BlockPos pos, Direction.Axis axis) {
        return true;
    }

    /**
     * 在落点自动搭一座接应门,保证玩家能原路返回。
     *
     * <p>只在遗落境侧执行 —— 主世界侧的门由玩家自己搭(点燃)。
     * 这样"第一次去"和"以后往返"的体验是有区别的:初见要自己造门,之后门会一直在。
     *
     * <p>本方法负责两件事:
     * <ol>
     *   <li>确保遗落之都已生成({@link LostCityBuilder#ensureGenerated})</li>
     *   <li>在城门内侧搭一座返回门</li>
     * </ol>
     *
     * <p>不再自动生成 Boss —— 旧的人形 Boss「终末之影」已随 beta45 整体删除,
     * 现在的终局 Boss 乌蠹诺斯由失落祭坛仪式或暗星信标召唤。
     */
    private static void ensureRealmArrivalPlatform(ServerLevel realm) {
        // 确保遗落之都已生成(幂等)—— 首次进入时在此搭建
        LostCityBuilder.ensureGenerated(realm);

        // 接应门开在城门内侧偏南,避免挡住视野
        BlockPos entry = LostCityBuilder.entrySpot();
        BlockPos portalFoot = entry.offset(0, 0, 4);
        if (!realm.getBlockState(portalFoot).isAir()) {
            return;
        }
        // 搭 2x3 的返回门(沿 X 轴)
        for (int dy = 0; dy < 3; dy++) {
            for (int dx = 0; dx < 2; dx++) {
                BlockPos p = portalFoot.offset(dx, dy, 0);
                realm.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
            }
        }
        BlockState portal = BossBlocks.REALM_PORTAL.get()
                .defaultBlockState()
                .setValue(RealmPortalBlock.AXIS, Direction.Axis.X);
        for (int dy = 0; dy < 3; dy++) {
            for (int dx = 0; dx < 2; dx++) {
                realm.setBlockAndUpdate(portalFoot.offset(dx, dy, 0), portal);
            }
        }
        realm.playSound(null, portalFoot, SoundEvents.PORTAL_TRIGGER,
                SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    /** 找一个可站立的落脚点(向下探 8 格,找不到就抬高到地表)。 */
    private static BlockPos findSafeSpot(ServerLevel level, BlockPos hint) {
        BlockPos.MutableBlockPos p = hint.mutable();
        for (int i = 0; i < 8; i++) {
            if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
                    && !level.getBlockState(p.below()).isAir()) {
                return p.immutable();
            }
            p.move(Direction.DOWN);
        }
        return hint.above(2);
    }

    // ================================================================
    // ITeleporter 实现
    // ================================================================

    /**
     * 自定义传送器:负责把实体放到目标维度的正确位置。
     *
     * <p>{@code placeEntity} 是 Forge 提供的扩展点 —— 默认实现会调用
     * 原版的落点搜索,这里覆写为固定的竞技场入口。
     */
    private static final class RealmTeleporter implements ITeleporter {

        private final boolean toRealm;

        RealmTeleporter(boolean toRealm) {
            this.toRealm = toRealm;
        }

        @Override
        public PortalInfo getPortalInfo(Entity entity, ServerLevel target,
                                        Function<ServerLevel, PortalInfo> defaultInfo) {
            PortalInfo info = destinationFor(target, entity);
            return info != null ? info : defaultInfo.apply(target);
        }

        @Override
        public Entity placeEntity(Entity entity, ServerLevel current, ServerLevel target,
                                  float yaw,
                                  Function<Boolean, Entity> repositionEntity) {
            // false = 不重新创建实体,直接搬过去(保留玩家状态与 NBT)
            Entity placed = repositionEntity.apply(false);
            if (placed != null) {
                placed.setPortalCooldown(100);
                if (placed instanceof ServerPlayer sp) {
                    // 首次抵达放个提示音,强化仪式感
                    sp.playNotifySound(SoundEvents.END_PORTAL_SPAWN, SoundSource.AMBIENT, 1.0F, 1.0F);
                }
            }
            return placed;
        }

        @Override
        public boolean playTeleportSound(ServerPlayer player, ServerLevel from, ServerLevel to) {
            return true;
        }
    }

    private BossPortalHandler() {}
}
