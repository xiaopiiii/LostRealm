package com.lost_realm.difficulty;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 难度存档数据 —— 把「当前难度」写进主世界的 {@code level.dat}。
 *
 * <h2>为什么必须是存档数据而不是静态字段</h2>
 * 难度是<b>世界规则</b>,不是玩家属性:关掉游戏再进来,服务器应当记得
 * 上一次设的是哪一档;而同一个进程里换存档时,又绝不能把上一个世界的
 * 难度带过去。静态字段两头都不满足,{@link SavedData} 正好都满足。
 *
 * <p>数据存在主世界(overworld)的数据存储里 —— 维度存档各存各的,
 * 只有主世界那一份是「跟着整个存档走」的。
 */
public class DifficultyState extends SavedData {

    /** 存档文件名:{@code data/lost_realm_difficulty.dat}。 */
    private static final String DATA_NAME = "lost_realm_difficulty";

    /** 难度编号的 NBT 键。 */
    private static final String KEY_LEVEL = "Difficulty";

    /** 当前难度编号(1~5)。默认普通。 */
    private int level = DifficultyLevel.NORMAL.id();

    /** 供 {@code computeIfAbsent} 使用的空构造(全新存档走这里)。 */
    public DifficultyState() {}

    /** 从 NBT 恢复。越界/缺字段一律回落到「普通」。 */
    private static DifficultyState load(CompoundTag tag) {
        DifficultyState state = new DifficultyState();
        state.level = DifficultyLevel.clamp(tag.getInt(KEY_LEVEL)).id();
        return state;
    }

    /** 取到当前存档的难度数据(首次访问时自动创建)。 */
    public static DifficultyState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                DifficultyState::load, DifficultyState::new, DATA_NAME);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt(KEY_LEVEL, level);
        return tag;
    }

    /** 当前难度。 */
    public DifficultyLevel level() {
        return DifficultyLevel.byId(level);
    }

    /** 设置难度并标脏(值没变时不写盘)。 */
    public void setLevel(DifficultyLevel next) {
        if (next == null || level == next.id()) {
            return;
        }
        level = next.id();
        setDirty();
    }
}
