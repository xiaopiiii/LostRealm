package com.lost_realm.qol;

import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import com.lost_realm.config.MedalsConfig;

/**
 * <b>A2 无限 buff 共享</b> 服务端处理器。
 *
 * <p>与 {@link PotionInfiniteHandler} 联动:凡是持有「药水无限续杯」所赋予的
 * <b>无限时长</b>效果的玩家,会把该效果共享给半径内(默认 32 格)的其他玩家。
 * 共享以有限时长(默认 10 秒)发放并持续刷新,一旦离开范围便会自然过期。
 *
 * <p>为避免每 tick 全量发包,本处理器以固定的较低频率(见
 * {@link QolConstants#BUFF_SHARE_INTERVAL})运行。
 */
public final class BuffShareHandler {

    /** 每 {@link QolConstants#BUFF_SHARE_INTERVAL} tick 执行一次。 */
    public static void tick(ServerPlayer source) {
        if (!MedalsConfig.QOL_BUFF_SHARE.get()) {
            return;
        }
        if (source.tickCount % QolConstants.BUFF_SHARE_INTERVAL != 0) {
            return;
        }

        // 收集该玩家身上由"无限续杯"维持的无限效果
        List<MobEffectInstance> infinite = source.getActiveEffects().stream()
                .filter(e -> e.getDuration() == MobEffectInstance.INFINITE_DURATION)
                .toList();
        if (infinite.isEmpty()) {
            return;
        }

        int radius = MedalsConfig.QOL_BUFF_SHARE_RADIUS.get();
        Level level = source.level();
        AABB box = source.getBoundingBox().inflate(radius);
        List<ServerPlayer> nearby = level.getEntitiesOfClass(ServerPlayer.class, box,
                p -> p != source && p.isAlive());

        for (ServerPlayer target : nearby) {
            for (MobEffectInstance src : infinite) {
                MobEffect effect = src.getEffect();
                MobEffectInstance cur = target.getEffect(effect);
                // 仅当目标还没有该效果、或即将过期时才补发,减轻网络与效果表压力
                if (cur == null || cur.getDuration() < QolConstants.BUFF_SHARE_DURATION_SECONDS * 20 / 2) {
                    target.addEffect(new MobEffectInstance(effect,
                            QolConstants.BUFF_SHARE_DURATION_SECONDS * 20,
                            src.getAmplifier(), src.isAmbient(), src.isVisible()));
                }
            }
        }
    }

    private BuffShareHandler() {}
}
