package com.lost_realm.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.lost_realm.Medals;
import com.lost_realm.machine.AntiSpawnTorchBlockEntity;

/**
 * <b>镇魔火把</b>的拦截逻辑。
 *
 * <h2>为什么是 MobSpawnEvent.PositionCheck</h2>
 * 1.20.1 的 Forge 把老的 {@code LivingSpawnEvent.CheckSpawn} 重构成了
 * {@code MobSpawnEvent} 一族，其中：
 * <ul>
 *   <li>{@code SpawnPlacementCheck} —— 最早，但<b>刷怪笼不触发</b>
 *       （走 CustomSpawnRules 的生成器不检查 spawn placement）；</li>
 *   <li>{@code PositionCheck} —— 实体已建好、位置待校验，{@code @HasResult}
 *       可以直接 {@code DENY}，且自然生成与刷怪笼都会经过这里。</li>
 *   <li>{@code FinalizeSpawn} —— 只能事后 {@code setSpawnCancelled}。</li>
 * </ul>
 * <p>注：1.20.1 的 {@link MobSpawnType} 里没有 {@code RAID}，袭击怪走的是
 * {@code EVENT}/{@code PATROL}，所以这里不需要单独列袭击。
 *
 * <p>选 {@code PositionCheck}：覆盖面最全，又是"位置不合法就不生成"的语义，
 * 正对应"这片地不许刷怪"。
 *
 * <h2>只拦"世界自己刷出来"的</h2>
 * <ul>
 *   <li><b>拦</b>：自然生成、区块生成、刷怪笼、巡逻队、结构生成；</li>
 *   <li><b>放行</b>：刷怪蛋、命令召唤、繁殖、发射器、铁傀儡/雪傀儡建造、转化。</li>
 * </ul>
 * 不这么分的话，玩家在保护区里放刷怪蛋会毫无反应、{@code /summon} 也叫不出东西，
 * 会以为是模组坏了。火把的语义是"这地方不会自己长怪"，不是"禁止一切生物存在"。
 */
@Mod.EventBusSubscriber(modid = Medals.MODID)
public final class AntiSpawnHandler {

    @SubscribeEvent
    public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (event.getResult() != Event.Result.DEFAULT) {
            return;
        }
        if (!isWorldSpawn(event.getSpawnType())) {
            return;
        }
        if (AntiSpawnTorchBlockEntity.isProtected(
                event.getLevel().getLevel(), event.getX(), event.getY(), event.getZ())) {
            event.setResult(Event.Result.DENY);
        }
    }

    private static boolean isWorldSpawn(MobSpawnType reason) {
        return reason == MobSpawnType.NATURAL
                || reason == MobSpawnType.CHUNK_GENERATION
                || reason == MobSpawnType.SPAWNER
                || reason == MobSpawnType.PATROL
                || reason == MobSpawnType.STRUCTURE;
    }

    /** 世界卸载时清掉火把登记表，避免单机切换存档后旧坐标残留。 */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel server) {
            AntiSpawnTorchBlockEntity.clearLevel(server.dimension());
        }
    }

    private AntiSpawnHandler() {}
}
