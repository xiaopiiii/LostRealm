package com.lost_realm.client;

import com.lost_realm.boss.udunos.UdunosBoss;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * 接收服务端下发的脊线，转交给对应的 {@link UdunosBoss}。
 *
 * <p>单独一个类是为了让 {@link com.lost_realm.network.PacketUdunosSpine} 不必
 * 直接 import 客户端类型 —— 那个包类在服务端也会被类加载，
 * 一旦它引用了 {@code Minecraft}，服务端就会在装包时炸掉。
 */
public final class UdunosSpineHandler {

    public static void accept(int entityId, float[] points, boolean teleport) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Entity entity = level.getEntity(entityId);
        if (entity instanceof UdunosBoss boss) {
            boss.acceptSpine(points, teleport);
        }
    }

    private UdunosSpineHandler() {}
}
