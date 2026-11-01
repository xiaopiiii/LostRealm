package com.lost_realm.client;

import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import com.lost_realm.Medals;
import com.lost_realm.boss.BossEntities;
import com.lost_realm.client.boss.ErebosBoltRenderer;
import com.lost_realm.client.boss.ErebosEffectRenderer;
import com.lost_realm.client.boss.ErebosPartRenderer;
import com.lost_realm.client.boss.ErebosRenderer;
import com.lost_realm.client.boss.WardenBossRenderer;
import com.lost_realm.client.boss.WardenSentinelRenderer;

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
 * 这也是"进维度后崩溃"的真实原因 —— 遗落之主就站在落点正前方。
 *
 * <h2>为什么用 MobRenderer 而不是自定义渲染器</h2>
 * {@link WardenBoss} 与 {@link WardenSentinel} 都继承 {@code Monster}(原版僵尸那一支),
 * 几何与骨骼就是标准的 {@link HumanoidModel}。因此直接复用原版的人形模型层
 * ({@link ModelLayers#ZOMBIE})即可,无需自制模型文件与动画。
 *
 * <h2>体型放大由谁负责</h2>
 * ⚠️ <b>不是渲染器自动完成的。</b>{@code Entity#getScale()} 只影响碰撞箱,
 * 原版渲染管线不消费它(已验证 {@code LivingEntityRenderer#render} 字节码里
 * 无 {@code getScale} 调用)。模型缩放由
 * {@code WardenBossRenderer#preRender} 显式 {@code poseStack.scale(...)} 完成,
 * 数值取自同一个 {@code getScale()},保证视觉与判定一致。
 *
 * <h2>几何与动画由 GeckoLib 提供(dv13 起)</h2>
 * 两个实体都实现 {@code GeoAnimatable},渲染器为 {@code GeoEntityRenderer}。
 * 几何/动画文件由 {@code tools/gen_geo.py} 与 {@code tools/gen_anim.py} 生成。
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
        EntityRenderers.register(BossEntities.WARDEN.get(),
                WardenBossRenderer::new);

        EntityRenderers.register(BossEntities.SENTINEL.get(),
                WardenSentinelRenderer::new);

        // 星辰贯穿之箭：接管原版箭的渲染器，只有带标记的那一支会被放大、常亮
        // （见 StarArrowRenderer 的说明）。普通箭走 super.render，外观不变。
        EntityRenderers.register(net.minecraft.world.entity.EntityType.ARROW,
                StarArrowRenderer::new);

        // ---- 晦星之主·厄瑞玻斯（dv58 新增）----
        // 本体：自绘渲染器，沿轨迹逐节摆放 40 个模型（不走 GeckoLib，原因见 ErebosRenderer）
        EntityRenderers.register(BossEntities.EREBOS.get(),
                ErebosRenderer::new);
        // 碰撞部件：一个"永远不画"的渲染器。看着像多此一举，其实是必需的 ——
        // 没注册渲染器的实体会让客户端在渲染循环里直接 NPE 崩溃（见类注释开头）。
        EntityRenderers.register(BossEntities.EREBOS_PART.get(),
                ErebosPartRenderer::new);
        // 星弹：面向相机的发光小立方（billboard，原因见 ErebosBoltRenderer）
        EntityRenderers.register(BossEntities.EREBOS_BOLT.get(),
                ErebosBoltRenderer::new);
        // 场上效果：空壳渲染器，视觉由实体自己在 tick 里撒粒子（见 ErebosEffectRenderer）
        EntityRenderers.register(BossEntities.EREBOS_SHARD.get(),
                com.lost_realm.client.boss.ErebosShardRenderer::new);
        EntityRenderers.register(BossEntities.EREBOS_EFFECT.get(),
                ErebosEffectRenderer::new);

        Medals.LOGGER.info("[Lost Realm] 已注册 7 个实体渲染器(warden / warden_sentinel / arrow / erebos / erebos_part / erebos_bolt / erebos_effect)。");
    }
}
