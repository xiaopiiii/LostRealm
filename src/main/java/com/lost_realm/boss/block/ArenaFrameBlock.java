package com.lost_realm.boss.block;

import net.minecraft.world.level.block.Block;

/**
 * 竞技场骨架方块 —— 带自发光纹理的装饰性框架。
 *
 * <p>与 {@link VoidStoneBlock} 一样,行为上暂无需定制;
 * 单独成类同样是为后续扩展留位(例如"Boss 进入第三阶段时骨架脉冲发光")。
 */
public class ArenaFrameBlock extends Block {

    public ArenaFrameBlock(Properties props) {
        super(props);
    }
}
