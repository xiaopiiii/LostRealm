package com.lost_realm.boss;

/**
 * 勋章等级上限的运行期状态 —— 由「遗落本源」提升。
 *
 * <h2>为什么不用配置项直接存</h2>
 * Forge 的 {@code ConfigValue} 运行期只读,虽然支持 {@code set()},
 * 但每次改动都要落盘,且语义上是"玩家设置"而非"游戏进度"。
 * 等级上限属于<b>存档进度</b>(每个存档各自独立),因此单独维护。
 *
 * <h2>持久化</h2>
 * 当前实现保存在静态字段中,随服务器生命周期存在。
 * 正式发布时可挂到 {@code ServerLevel} 的 {@code DimensionDataStorage} 上做持久化
 * (见 {@link #bindPersistent})。这里先保证功能可用,
 * 并在字段上标注 TODO 以免遗忘。
 */
public final class RealmCapConfig {

    /** 勋章等级上限的默认值(与 {@code ExpTable} 保持一致)。 */
    public static final int DEFAULT_CAP = 30;

    private static int cap = DEFAULT_CAP;

    /** 当前上限。 */
    public static int currentCap() {
        return cap;
    }

    /**
     * 设置上限。
     *
     * <p>限制在 [{@value #DEFAULT_CAP}, 50] 区间内 —— 下限不允许低于默认值
     * (否则会让已有玩家数据溢出),上限防止无限叠加。
     */
    public static void setCap(int value) {
        cap = Math.max(DEFAULT_CAP, Math.min(50, value));
    }

    /** 重置为默认(服务器停止时调用)。 */
    public static void reset() {
        cap = DEFAULT_CAP;
    }

    /**
     * 持久化绑定点(预留)。
     *
     * <p>当前未实现:等级上限在服务器重启后会回到默认值。
     * 若需要跨重启保存,可在此接入 {@code DimensionDataStorage} 的
     * {@code SavedData} 机制 —— 把 {@link #cap} 存进主世界的存档数据。
     */
    public static void bindPersistent() {
        // TODO: 接入 SavedData 持久化(需要时实现)
    }

    private RealmCapConfig() {}
}
