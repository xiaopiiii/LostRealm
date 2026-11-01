package com.lost_realm.boss.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import com.lost_realm.boss.WardenAltar;

/**
 * 四相祭坛方块。
 *
 * <h2>机制定位</h2>
 * 竞技场四角各有一座祭坛,分别对应整合包的四大系统:
 * <ol>
 *   <li>{@link WardenAltar#SOUL 灵魂祭坛} —— 用诡厄巫法的法术命中 Boss</li>
 *   <li>{@link WardenAltar#APOTHEOSIS 神化祭坛} —— 触发一次属性暴击</li>
 *   <li>{@link WardenAltar#MEDAL 勋章祭坛} —— 用勋章主动技能命中</li>
 *   <li>{@link WardenAltar#PURE 纯净祭坛} —— 不依赖任何加成造成伤害</li>
 * </ol>
 * 四座全部点亮,才破除 Boss 的「终末护盾」—— 这是全战斗的核心目标。
 *
 * <h2>为什么四个祭坛共用一个方块</h2>
 * 用 {@link #ALTAR} 属性区分种类,而非注册四个独立方块:
 * <ul>
 *   <li>只需一套注册代码、一套方块实体、一套贴图(靠状态切换颜色)</li>
 *   <li>竞技场生成时按枚举循环放置,代码更短</li>
 *   <li>玩家把祭坛挖走再放回来,类型不会丢失(状态跟着走)</li>
 * </ul>
 *
 * <h2>为什么 {@code LIT} 状态不足以判定</h2>
 * 点亮进度需要记在<b>战斗会话</b>上而非方块上 —— 否则玩家换个祭坛、
 * 或者 Boss 重开后,方块状态会残留。因此 {@code LIT} 只用于<b>视觉</b>,
 * 真正的进度由 {@code WardenFightSession} 维护,方块的 LIT 由它同步刷新。
 */
public class WardenAltarBlock extends Block {

    /** 祭坛种类。 */
    public static final EnumProperty<WardenAltar> ALTAR =
            EnumProperty.create("altar", WardenAltar.class);

    /** 是否已点亮(仅视觉,真相在战斗会话里)。 */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public WardenAltarBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any()
                .setValue(ALTAR, WardenAltar.SOUL)
                .setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ALTAR, LIT);
    }

    /**
     * 右键祭坛可以查看它当前的状态与激活条件。
     *
     * <p>这是给玩家的"说明书"—— 集成包 Boss 的机制若不给提示,
     * 玩家会卡在"打不动"的困惑里。提示走语言键,支持中英。
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            WardenAltar altar = state.getValue(ALTAR);
            boolean lit = state.getValue(LIT);
            player.displayClientMessage(Component.translatable(
                    lit ? "message.lost_realm.altar.lit" : "message.lost_realm.altar.unlit",
                    Component.translatable(altar.langKey())), true);
            if (!lit) {
                player.displayClientMessage(
                        Component.translatable(altar.hintKey()), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * 祭坛不可被玩家徒手破坏 —— 防止玩家在战斗中拆掉机制。
     *
     * <p>返回 {@code -1} 表示"挖掘时间无限",生存模式下等价于不可破坏。
     * 这是必要的保护:否则玩家可能因为不理解机制而把祭坛挖了,
     * 导致 Boss 永久无敌、战斗卡死。
     */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (player.isCreative()) {
            return super.getDestroyProgress(state, player, level, pos);
        }
        return 0.0F;
    }
}
