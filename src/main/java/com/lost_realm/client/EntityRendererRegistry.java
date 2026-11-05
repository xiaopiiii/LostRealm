package com.lost_realm.client;

import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import com.lost_realm.Medals;
import com.lost_realm.boss.BossEntities;
import com.lost_realm.client.boss.UdunosBoltRenderer;
import com.lost_realm.client.boss.UdunosEffectRenderer;
import com.lost_realm.client.boss.UdunosPartRenderer;
import com.lost_realm.client.boss.UdunosRenderer;

/**
 * 实体的<b>客户端渲染器注册</b>。
 *
 * <h2>为什么必须有这个类(dv9 修)</h2>
 * 本模组此前只注册了实体的 <b>EntityType</b>({#link BossEntities})与属性,
 * <b>从未注册对应的 EntityRenderer</b>。后果是致命的:
 * <pre>
 *   java.lang.NullPointerException: Cannot invoke
 *     "EntityRenderer.shouldRender(...)" because "entityrenderer" is null
 *       at EntityRenderDispatcher.render(...)
 *       at LevelRenderer.renderLevel(...)
 * </pre>
 * 客户端在渲染循环里对每个可见实体查一次渲染器表,
 * 查不到就拿到 {@code null} 并直接抛 NPE —— 崩在渲染线程,
 * 且<b>无法被任何 try-catch 兜住</b>(在别人的调用栈里)。
 *
 * <p>表现特征:实体在服务端一切正常(能生成、能打、能掉血),
 * 但玩家<b>只要把它纳入视野就立刻崩客户端</b>。
 *
 * <p>⚠️ 因此<b>每注册一个 EntityType,就必须在这里补一行渲染器</b> ——
 * 包括 {@code udunos_part} 这种"根本不需要画"的纯判定盒(给它一个什么都不画的
 * 渲染器,见 {@code UdunosPartRenderer})。
 *
 * <h2>为什么挂在 MOD 总线上</h2>
 * {@code FMLClientSetupEvent} 属于 MOD 生命周期事件,
 * 且 {@code EntityRenderers.register} 必须在客户端 setup 阶段调用 ——
 * 过早(注册期)调用时 {@code EntityRenderers} 的静态表尚未就绪。
 */
@Mod.EventBusSubscriber(modid = Medals.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EntityRendererRegistry {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 渲染器注册会写入全局静态表,必须在主线程串行执行
        event.enqueueWork(EntityRendererRegistry::registerAll);
    }

    private static void registerAll() {
        // 星辰贯穿之箭：接管原版箭的渲染器，只有带标记的那一支会被放大、常亮
        // （见 StarArrowRenderer 的说明）。普通箭走 super.render，外观不变。
        EntityRenderers.register(net.minecraft.world.entity.EntityType.ARROW,
                StarArrowRenderer::new);

        // ---- 遗落之主·乌蠹诺斯（dv58 新增）----
        // 本体：自绘渲染器，沿轨迹逐节摆放 40 个模型（不走 GeckoLib，原因见 UdunosRenderer）
        EntityRenderers.register(BossEntities.UDUNOS.get(),
                UdunosRenderer::new);
        // 碰撞部件：一个"永远不画"的渲染器。看着像多此一举，其实是必需的 ——
        // 没注册渲染器的实体会让客户端在渲染循环里直接 NPE 崩溃（见类注释开头）。
        EntityRenderers.register(BossEntities.UDUNOS_PART.get(),
                UdunosPartRenderer::new);
        // 星弹：面向相机的发光小立方（billboard，原因见 UdunosBoltRenderer）
        EntityRenderers.register(BossEntities.UDUNOS_BOLT.get(),
                UdunosBoltRenderer::new);
        // 场上效果：空壳渲染器，视觉由实体自己在 tick 里撒粒子（见 UdunosEffectRenderer）
        EntityRenderers.register(BossEntities.UDUNOS_SHARD.get(),
                com.lost_realm.client.boss.UdunosShardRenderer::new);
        EntityRenderers.register(BossEntities.UDUNOS_EFFECT.get(),
                UdunosEffectRenderer::new);

        Medals.LOGGER.info("[Lost Realm] 已注册 7 个实体渲染器(warden / warden_sentinel / arrow / udunos / udunos_part / udunos_bolt / udunos_effect)。");
    }
}
