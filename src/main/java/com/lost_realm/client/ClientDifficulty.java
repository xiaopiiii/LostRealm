package com.lost_realm.client;

import com.lost_realm.difficulty.DifficultyLevel;

/**
 * 客户端侧的世界难度缓存。
 *
 * <p>难度是<b>服务端权威数据</b>(存在主世界 {@code level.dat} 里),客户端只持有
 * 一份用于显示的副本。同步时机只有两个:
 * <ul>
 *   <li>进服 / 打开配置界面时主动查询一次;</li>
 *   <li>服务端切档后主动广播({@code PacketDifficultySync})。</li>
 * </ul>
 * 因此这里<b>没有</b>「客户端自己改难度」的入口 —— 客户端点按钮只是发一个请求,
 * 真正写入与生效都在服务端,失败时客户端会收到一条聊天提示。
 */
public final class ClientDifficulty {

    /** 默认按「普通」显示,收到同步包后覆盖。 */
    private static DifficultyLevel current = DifficultyLevel.NORMAL;

    public static DifficultyLevel level() {
        return current;
    }

    /** 收到服务端同步。 */
    public static void set(int id) {
        current = DifficultyLevel.clamp(id);
    }

    private ClientDifficulty() {}
}
