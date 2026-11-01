package com.lost_realm.boss.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.lost_realm.boss.ArenaBuilder;
import com.lost_realm.boss.BossBlocks;
import com.lost_realm.boss.block.RealmPortalBlock;

/**
 * 遗落之境门钥匙 —— <b>点燃传送门</b>的激活物品。
 *
 * <h2>使用方式</h2>
 * 玩家在末地或主世界搭好一个黑曜石框架(内空 2×3),
 * 手持本物品右键框架底部,即点燃遗落境传送门。
 *
 * <h2>为什么用"右键点火"而不是"框架自动成型"</h2>
 * 与下界门的打火石行为一致 —— 玩家有既有的操作直觉,
 * 不需要学习新交互。不同的是:本门<b>不消耗</b>钥匙(下界门打火石会损耗),
 * 因为钥匙本身是任务链产物,消耗掉会让玩家陷入"回不去"的困境。
 *
 * <h2>判定策略</h2>
 * 不要求玩家精确搭出特定尺寸的框架 —— 只要在可点燃的位置右键,
 * 就在该处开一道 2×3 的门。这降低了操作门槛,
 * 也避免了 {@code PortalShape} 那套复杂的框架搜索逻辑。
 */
public class RealmKeyItem extends Item {

    public RealmKeyItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos clicked = ctx.getClickedPos();
        Direction face = ctx.getClickedFace();

        // 在点击面的外侧寻找可放门的位置
        BlockPos base = clicked.relative(face);

        // 门沿"与玩家视线垂直"的轴展开,手感更自然
        Direction.Axis axis = ctx.getHorizontalDirection().getAxis() == Direction.Axis.X
                ? Direction.Axis.Z
                : Direction.Axis.X;

        if (!isRoomForPortal(level, base, axis)) {
            if (ctx.getPlayer() != null) {
                ctx.getPlayer().displayClientMessage(
                        Component.translatable("message.lost_realm.key.no_room"), true);
            }
            return InteractionResult.FAIL;
        }

        if (level instanceof ServerLevel server) {
            ArenaBuilder.placePortal(server, base, axis);
            server.playSound(null, base, SoundEvents.PORTAL_TRIGGER,
                    SoundSource.BLOCKS, 1.0F, 0.8F);
            if (ctx.getPlayer() != null) {
                ctx.getPlayer().displayClientMessage(
                        Component.translatable("message.lost_realm.key.ignited"), true);
            }
        }
        // 不消耗物品 —— 钥匙是唯一任务产物,消耗掉会让玩家困在遗落境
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 检查 2×3 的空间是否都是可替换的(空气/草等)。 */
    private static boolean isRoomForPortal(Level level, BlockPos base, Direction.Axis axis) {
        for (int h = 0; h < 3; h++) {
            for (int w = 0; w < 2; w++) {
                BlockPos p = axis == Direction.Axis.X
                        ? base.offset(w, h, 0)
                        : base.offset(0, h, w);
                BlockState st = level.getBlockState(p);
                if (!st.isAir() && !st.canBeReplaced()) {
                    return false;
                }
            }
        }
        // 门下需要有一层支撑(否则浮空门很怪) —— 但允许悬空以降低门槛,
        // 这里只要下方不是虚空即可
        return !level.getBlockState(base.below()).is(Blocks.VOID_AIR);
    }
}
