package com.lost_realm.client.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import com.lost_realm.Medals;
import com.lost_realm.boss.udunos.UdunosBoss;
import com.lost_realm.boss.udunos.UdunosTuning;

/**
 * Udunos 的渲染器 —— <b>沿轨迹逐节摆放 40 个模型</b>。
 *
 * <h2>它做的事其实只有三件</h2>
 * <ol>
 *   <li><b>取位置</b>：从 {@code UdunosBoss#segments()} 拿到本 tick 的 40 节坐标
 *       （客户端自己算的，不靠网络同步），再用 {@code segmentsPrev()} 做插值 ——
 *       否则 20tps 的位置更新在 60fps 下会有明显"一卡一卡"的步进感。</li>
 *   <li><b>算朝向</b>：每节的朝向取<b>前后两节连线的方向</b>（而不是"从头指向它"，
 *       那样弯曲时每节都会歪）。这就是"体节采用自己的路径切线朝向"。</li>
 *   <li><b>摆上去渲染</b>：{@code pushPose → translate → rotate → scale → render → popPose}。</li>
 * </ol>
 *
 * <h2>为什么不用 {@code LivingEntityRenderer}</h2>
 * 那条管线假定"一个实体 = 一个模型"，还带着人形骨骼、动画插值、坐姿/骑姿状态机
 * 一大堆用不上的东西。这里直接用最朴素的 {@link EntityRenderer}，
 * 渲染逻辑完全自己掌控 —— 因为体节位置本来就不来自骨骼。
 *
 * <h2>性能</h2>
 * 40 个立方 × 6 面，用同一个 {@code RenderType} 的 {@code VertexConsumer}，
 * 会被合并进同一批绘制。真正的开销来自"要不要画"的判断 ——
 * 所以这里有距离剔除：超过 160 格的体节直接跳过，
 * 玩家不可能同时看清整条虫的每一节。
 */
public class UdunosRenderer extends EntityRenderer<UdunosBoss> {

    private static final ResourceLocation TEX_HEAD =
            new ResourceLocation(Medals.MODID, "textures/entity/udunos_head.png");
    private static final ResourceLocation TEX_BODY =
            new ResourceLocation(Medals.MODID, "textures/entity/udunos_body.png");
    private static final ResourceLocation TEX_TAIL =
            new ResourceLocation(Medals.MODID, "textures/entity/udunos_tail.png");

    /** 体节渲染缩放：模型是 1 格立方，乘它得到 {@code BODY_DIAMETER} 格直径（beta20 起 4.8）。 */
    private static final float SCALE = (float) UdunosTuning.BODY_DIAMETER;

    /** 超过这个距离的体节不渲染（平方值）。 */
    /**
     * 单个体节的最大绘制距离（平方）。
     *
     * <p>与 {@code UdunosBoss#shouldRenderAtSqrDistance} 的 256 格对齐 ——
     * 两者不一致时会出现"实体被判定为可见、但每一节都因太远被跳过"的空跑：
     * 玩家开到 24 区块视距（384 格）站在两百格外欣赏它时，
     * 会看到一条<b>完全没有身体</b>的虫（只剩粒子）。
     */
    private static final double MAX_RENDER_DIST_SQ = 256.0D * 256.0D;

    /**
     * 相邻体节的最小间距（平方）：小于它的节视为「堆在同一点」，直接跳过不画。
     *
     * <p>正常节距 4.2 格，弦长恒在 4 格上下；只有"轨迹不够长、剩余节全堆在一点"
     * 时才会接近 0。取 1.5 格作阈值，两边都留足余量。
     */
    private static final double MIN_SEGMENT_GAP_SQ = 1.5D * 1.5D;

    /**
     * 体节沿身体方向的压缩比 = 节距 / 体径 = 4.2 / 4.8。
     *
     * <p>小于 1 时相邻体节不再互相插进对方，转弯时也不会共面闪烁。
     */
    private static final float SEGMENT_Z_SQUASH =
            (float) (UdunosTuning.SEGMENT_SPACING / UdunosTuning.BODY_DIAMETER);

    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart tail;

    /** 诊断帧计数（只打前几帧）。 */
    private int diagFrames;

    /**
     * 最后一次真正进入 {@link #render} 的时刻（{@link System#nanoTime()}）。
     *
     * <p>给游戏内的 {@code /lrdebug udunos} 用：命令会拿"现在 − 这个值"判断渲染器
     * 到底有没有被调用。这是区分「渲染被剔除 / 实体不存在」和「渲染正常但位置错了」
     * 最快的一刀 —— 不用翻日志，截图就能说清。
     */
    public static volatile long LAST_RENDER_NANOS;

    private static String fmt(double v) {
        return String.format("%.1f", v);
    }

    public UdunosRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = (float) UdunosTuning.BODY_DIAMETER;
        this.head = UdunosModel.head();
        this.body = UdunosModel.body();
        this.tail = UdunosModel.tail();
    }

    @Override
    public void render(UdunosBoss entity, float entityYaw, float partialTick,
                       PoseStack pose, MultiBufferSource buffer, int packedLight) {
        LAST_RENDER_NANOS = System.nanoTime();
        int count = entity.segmentCount();
        if (count <= 0) {
            return;
        }

        // ------------------------------------------------------------
        // 位置数据源：永远只用服务端下发的脊线。
        //
        //   原设计是「客户端自己跑一遍路径跟随」（和末影龙一样），但那条链路
        //   在整合包环境里是断的（/lrdebug 显示轨迹永远 1 个点、40 节完全重合）。
        //   所以后来改成「优先服务端脊线、超 1 秒退回自算」——
        //   可这正是「打一会儿整条虫消失」的元凶：脊线包每 tick 只在玩家 256 格内才发，
        //   玩家被逼退/被打飞到 256 格外（或网络卡顿积压）→ 超过 1 秒判「不新鲜」
        //   → 退回那条已知会塌的自算链路 → 整条虫消失，而 BGM（128 格）还在。
        //
        //   正确做法：只要拿到过一份脊线就<b>永远画它</b>。没更新就停在原地
        //   （虫在远处不动也合理），绝不切回自算。只有从来没收到过（实体刚生成
        //   的那一瞬）才退回自算兜底。
        // ------------------------------------------------------------
        double[] curr;
        double[] prev;
        double[] syncNow = entity.spineNow();
        if (syncNow != null && syncNow.length == count * 3) {
            curr = syncNow;
            double[] syncPrev = entity.spinePrev();
            prev = (syncPrev != null && syncPrev.length == syncNow.length) ? syncPrev : syncNow;
        } else {
            curr = entity.segments();
            prev = entity.segmentsPrev();
            if (curr == null || prev == null) {
                return;
            }
        }

        // 前 3 帧打一版快照：用来区分「渲染器根本没被调用」
        // 和「被调用了但拿到的坐标是错的」——这两种情况在屏幕上都是"看不见"，
        // 但从日志一眼就能分辨。
        if (this.diagFrames++ < 3) {
            com.lost_realm.Medals.LOGGER.info(
                    "[LR/udunos] RENDER frame#{} count={} seg0=({}, {}, {}) segMid=({}, {}, {})",
                    this.diagFrames, count,
                    fmt(curr[0]), fmt(curr[1]), fmt(curr[2]),
                    fmt(curr[(count / 2) * 3]), fmt(curr[(count / 2) * 3 + 1]),
                    fmt(curr[(count / 2) * 3 + 2]));
        }

        Vec3 camera = this.entityRenderDispatcher.camera.getPosition();

        // ★★★ 关键基准点：本体的「插值位置」。
        //
        // 调用链是：LevelRenderer → EntityRenderDispatcher#render(x,y,z,poseStack,...)
        //   → poseStack.pushPose() → poseStack.translate(x, y, z) → 才轮到这里的 render()。
        // 其中 x/y/z = (本体位置 − 摄像机位置)。也就是说：**本渲染器被调用时，
        // PoseStack 的原点已经是「本体」了**（这是原版所有渲染器的共同前提，
        // 比如 UdunosBoltRenderer 就只做 scale/mulPose、从不 translate 世界坐标）。
        //
        // 所以体节必须按「相对本体的偏移」摆放，即 (体节 − 本体)；
        // 一旦写成 (体节 − 摄像机)，本体的位移就被重复计入一次，
        // 实际画在 (体节 − 摄像机) + (本体 − 摄像机) = 两倍距离处 ——
        // 现象正是「召唤瞬间本体≈摄像机、看得见；Boss 一飞走就整个消失」。
        //
        // 插值基准也必须与 LevelRenderer 保持一致（xOld → getX() 按 partialTick 插值），
        // 否则高速移动时虫身会整体滞后/前跳一整个 tick 的位移。
        double ex = Mth.lerp(partialTick, entity.xOld, entity.getX());
        double ey = Mth.lerp(partialTick, entity.yOld, entity.getY());
        double ez = Mth.lerp(partialTick, entity.zOld, entity.getZ());

        // 全亮光照。这条虫活动在紫黑宇宙背景里，若按环境光照渲染，
        // 稍微背光的那几节就会糊成一团黑 —— 而设计要求"在紫黑背景下仍可一眼辨认"。
        // 全亮同时也是它的设定：星核自发光，整条身体都透着光。
        int light = LightTexture.FULL_BRIGHT;
        int overlay = OverlayTexture.NO_OVERLAY;

        // 三种几何共用各自的贴图；同一 RenderType 的顶点会被合并成一批
        VertexConsumer headBuf = buffer.getBuffer(RenderType.entityCutoutNoCull(TEX_HEAD));
        VertexConsumer bodyBuf = buffer.getBuffer(RenderType.entityCutoutNoCull(TEX_BODY));
        VertexConsumer tailBuf = buffer.getBuffer(RenderType.entityCutoutNoCull(TEX_TAIL));

        // 上一节实际画出去的位置 —— 用来丢弃"堆在同一点"的重复节（见下）。
        double lastX = Double.NaN;
        double lastY = 0.0D;
        double lastZ = 0.0D;

        for (int i = 0; i < count; i++) {
            // ---- 插值位置 ----
            double x = Mth.lerp(partialTick, prev[i * 3], curr[i * 3]);
            double y = Mth.lerp(partialTick, prev[i * 3 + 1], curr[i * 3 + 1]);
            double z = Mth.lerp(partialTick, prev[i * 3 + 2], curr[i * 3 + 2]);

            // ---- 丢掉与上一节重合的体节（beta32）----
            //
            //   UdunosTrail#sample 在"轨迹不够长"时会把剩下的节<b>全部堆在同一个点</b>
            //   （开场没铺开、跨星门撞断点、以及贴身缠斗被挤短的时候）。
            //   几十个一模一样的立方体重叠在一起，共面就是 z-fighting ——
            //   玩家看到的是"身体某一处一直在闪"。
            //   合法的相邻节弦长恒在 4 格左右（节距 4.2），留 1.5 格的判定余量足够安全。
            if (!Double.isNaN(lastX)) {
                double ddx = x - lastX;
                double ddy = y - lastY;
                double ddz = z - lastZ;
                if (ddx * ddx + ddy * ddy + ddz * ddz < MIN_SEGMENT_GAP_SQ) {
                    continue;
                }
            }
            lastX = x;
            lastY = y;
            lastZ = z;

            // 到摄像机的世界距离 —— 只用来"太远就不画"（性能剔除），不参与摆放
            double cdx = x - camera.x;
            double cdy = y - camera.y;
            double cdz = z - camera.z;
            if (cdx * cdx + cdy * cdy + cdz * cdz > MAX_RENDER_DIST_SQ) {
                continue;
            }

            // ---- 切线朝向：取前后两节的连线 ----
            // ★ 必须用「插值后的坐标」，不能直接用 curr。
            //   curr 每 tick 才换一帧，而这里每帧（60fps）都在算 ——
            //   用 curr 会让朝向在一帧之内保持不变、下一帧突变，
            //   转弯时身体各节会"一格一格地摆头"，看起来就是在抽搐。
            int a = Math.max(0, i - 1);
            int b = Math.min(count - 1, i + 1);
            double ax = Mth.lerp(partialTick, prev[a * 3], curr[a * 3]);
            double ay = Mth.lerp(partialTick, prev[a * 3 + 1], curr[a * 3 + 1]);
            double az = Mth.lerp(partialTick, prev[a * 3 + 2], curr[a * 3 + 2]);
            double bx = Mth.lerp(partialTick, prev[b * 3], curr[b * 3]);
            double by = Mth.lerp(partialTick, prev[b * 3 + 1], curr[b * 3 + 1]);
            double bz = Mth.lerp(partialTick, prev[b * 3 + 2], curr[b * 3 + 2]);
            double tx = bx - ax;
            double ty = by - ay;
            double tz = bz - az;
            double len = Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (len < 1.0E-6D) {
                // 身体还没铺开（开场瞬间）：默认朝 +Z，避免归一化出 NaN
                tx = 0.0D;
                ty = 0.0D;
                tz = 1.0D;
                len = 1.0D;
            }
            tx /= len;
            ty /= len;
            tz /= len;

            // ---- 选几何与缩放 ----
            ModelPart part;
            VertexConsumer buf;
            float scale = SCALE;
            if (i == 0) {
                part = this.head;
                buf = headBuf;
            } else if (i >= count - 2) {
                part = this.tail;
                buf = tailBuf;
                // 尾巴再收一点，避免和体节一样粗
                scale *= (i == count - 1) ? 0.78F : 0.92F;
            } else {
                part = this.body;
                buf = bodyBuf;
            }

            // ---- 摆上去 ----
            pose.pushPose();
            // ★ 相对「本体」而不是相对「摄像机」—— 见方法开头的说明。
            //   写成 cdx/cdy/cdz 会让整条虫被画到两倍距离外。
            pose.translate(x - ex, y - ey, z - ez);
            // 把模型的 +Z 转到 (tx, ty, tz)：
            //   先绕 Y 转出水平方位，再绕 X 补俯仰。
            //   pitch 取 -asin(ty) 是推导结果，符号写反会变成"身体朝天倒着游"。
            //
            // ⚠️ 水平分量接近 0 时（身体接近垂直）atan2(tx, tz) 会剧烈抖动，
            //    yaw 每帧乱跳，模型就绕着自身轴打转 —— 这也是"抽搐"的一个来源。
            //    门限以下直接沿用平滑值，不让它跟。
            double horiz = Math.sqrt(tx * tx + tz * tz);
            float yawTarget = horiz < 0.08D
                    ? entity.smoothedYaw(i)
                    : (float) Math.toDegrees(Math.atan2(tx, tz));
            float yaw = entity.smoothedYaw(i, yawTarget);
            float pitch = (float) Math.toDegrees(-Math.asin(Mth.clamp(ty, -1.0D, 1.0D)));
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.scale(scale, scale, scale);
            // ★ 沿身体方向把体节压短，消除相邻节的重叠（beta32）。
            //
            //   体节模型 16px 宽，scale 后直径 4.8 格；但节距只有 4.2 格 ——
            //   相邻两节沿身体方向必然重叠 0.6 格，转弯时弦长更短、重叠更狠。
            //   装甲环还比主体外扩一圈（18px），重叠时两个环的侧面共面，
            //   就 z-fighting：玩家看到的"两节叠一起、两个颜色闪来闪去"。
            //
            //   把 +Z（行进方向）压到 4.2/4.8 = 0.875，相邻节就首尾相接、不再插进彼此。
            //   X/Y 不动，截面直径仍是 4.8 格，观感不受影响。
            pose.scale(1.0F, 1.0F, SEGMENT_Z_SQUASH);
            // ★ MC 的模型空间是「+Y 向下、+X 向左」，与原版 LivingEntityRenderer
            //   里那句 scale(-1,-1,1) 对应。本渲染器继承的是 EntityRenderer，
            //   那条链上没有这一步 —— 不补的话整条虫上下颠倒：
            //   角朝下、牙朝上、下颚翻到头顶。
            //   体节是对称的看不出来，头一改成不对称的造型就露馅。
            pose.scale(-1.0F, -1.0F, 1.0F);
            part.render(pose, buf, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
            pose.popPose();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(UdunosBoss entity) {
        // 实际渲染会按节区分贴图，这里只是接口要求的一个默认值
        return TEX_BODY;
    }

    /**
     * 不做名字牌 —— 血条已经写在屏幕顶端了，头顶再挂一行纯属遮挡。
     */
    @Override
    protected boolean shouldShowName(UdunosBoss entity) {
        return false;
    }
}
