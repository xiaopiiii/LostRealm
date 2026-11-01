package com.lost_realm.boss.block;

import net.minecraft.world.level.block.Block;

/**
 * 虚空石 —— 竞技场主建材。
 *
 * <p>目前不需要任何特殊行为,单独成类是为了给后续可能的
 * 「Boss 战中会碎裂/发光」等动态效果留出扩展位,
 * 避免将来改行为时要动 {@code BossBlocks} 的注册代码。
 */
public class VoidStoneBlock extends Block {

    public VoidStoneBlock(Properties props) {
        super(props);
    }
}
