package com.lost_realm.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import com.lost_realm.Medals;
import com.lost_realm.data.PlayerMedals;

/**
 * 网络通道注册。
 *
 * <p>用于把服务端的熟练度 / 技能状态同步到客户端(HUD、星盾视觉等)。
 */
public final class MedalsNetwork {

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Medals.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private static int id = 0;

    public static void register() {
        CHANNEL.registerMessage(id++, PacketSyncMedals.class,
                PacketSyncMedals::encode,
                PacketSyncMedals::new,
                PacketSyncMedals::handle);

        CHANNEL.registerMessage(id++, PacketActivateSkill.class,
                PacketActivateSkill::encode,
                PacketActivateSkill::new,
                PacketActivateSkill::handle);

        // 体验改进(QoL):交易刷新 / 附魔刷新
        CHANNEL.registerMessage(id++, PacketRefreshTrades.class,
                PacketRefreshTrades::encode,
                PacketRefreshTrades::new,
                PacketRefreshTrades::handle);

        CHANNEL.registerMessage(id++, PacketRefreshEnchant.class,
                PacketRefreshEnchant::encode,
                PacketRefreshEnchant::new,
                PacketRefreshEnchant::handle);

        // ---- 遗落之主:对话系统(dv5) ----
        CHANNEL.registerMessage(id++, PacketOpenDialogue.class,
                PacketOpenDialogue::encode,
                PacketOpenDialogue::new,
                PacketOpenDialogue::handle);

        // ---- 突进之刃(dv49) ----
        CHANNEL.registerMessage(id++, PacketDash.class,
                PacketDash::encode,
                PacketDash::new,
                PacketDash::handle);

        CHANNEL.registerMessage(id++, PacketDialogueChoice.class,
                PacketDialogueChoice::encode,
                PacketDialogueChoice::new,
                PacketDialogueChoice::handle);

        // ---- 乌蠹诺斯脊线同步(beta18) ----
        // 客户端位置同步在这套环境里不可靠(实测轨迹永远只有 1 个点),
        // 改为服务端直接下发 40 节坐标。见 PacketUdunosSpine 的类注释。
        CHANNEL.registerMessage(id++, PacketUdunosSpine.class,
                PacketUdunosSpine::encode,
                PacketUdunosSpine::new,
                PacketUdunosSpine::handle);

        // ---- 世界难度(beta38):配置界面里的难度页 ----
        // S2C 只用于显示;C2S 的 level=0 表示「仅查询」。
        CHANNEL.registerMessage(id++, PacketDifficultySync.class,
                PacketDifficultySync::encode,
                PacketDifficultySync::new,
                PacketDifficultySync::handle);

        CHANNEL.registerMessage(id++, PacketDifficultySet.class,
                PacketDifficultySet::encode,
                PacketDifficultySet::new,
                PacketDifficultySet::handle);
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    /** 便捷:同步某玩家的勋章数据(熟练度已随物品 NBT 自动同步,这里只是发信号)。 */
    public static void sync(ServerPlayer player, PlayerMedals data) {
        sendToPlayer(player, new PacketSyncMedals());
    }

    private MedalsNetwork() {}
}
