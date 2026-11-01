package com.lost_realm.client.boss;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Erebos 的三套几何（头 / 体节 / 尾）。
 *
 * <h2>为什么不用 GeckoLib</h2>
 * 本模组其他 Boss 走的是 GeckoLib（骨骼动画）。Erebos <b>不能用</b>，原因很实在：
 * GeckoLib 的骨骼位置来自<b>动画文件里的关键帧</b>，而这条虫的 40 节位置是
 * <b>每 tick 由路径跟随算出来的</b>（见 {@code ErebosTrail}）——
 * 两者根本对不上。真要硬套，就得每帧往 40 根骨骼里塞动态坐标，
 * 等于把 GeckoLib 当普通模型用，还白白多一层动画系统的开销。
 *
 * <p>直接用原版的 {@link ModelPart} 反而干净：几何是静态的（永远长一个样），
 * <b>只有摆放位置是动态的</b> —— 而摆放是渲染器的事。
 *
 * <h2>朝向约定</h2>
 * 模型建成 <b>+Z 为前方</b>（头部星核在 +Z 侧、尾尖往 -Z 伸）。
 * 渲染器负责把 +Z 转到该体节的实际行进方向。
 *
 * <h2>尺寸与 UV</h2>
 * 建模按 1 像素 = 1/16 格，即 16 像素的立方体 = 1 格。渲染时整体
 * {@code scale(2.4)} 得到 2.4 格直径 —— 与 {@code ErebosTuning.SEGMENT_SPACING}
 * 一致，所以相邻体节正好首尾相接。
 */
public final class ErebosModel {

    private ErebosModel() {}

    /**
     * 头部。
     *
     * <h2>为什么重做</h2>
     * 旧版头部就是一个 16×16×16 的立方体加几个薄片 ——
     * 玩家实测的原话是「头和身体都是用同一个模型，头那里啥都没有，连个眼睛都没有」。
     * 确实：一个方块配一个方块身体，远的看就是条会动的香肠。
     *
     * <h2>新版的结构</h2>
     * <ul>
     *   <li><b>颅骨</b> 14×14×18 —— 比体节窄、比体节长，光轮廓就能分辨头尾</li>
     *   <li><b>上颚 + 下颚</b> 分成两块，下颚带一点张角，正面看是张开的嘴</li>
     *   <li><b>11 颗牙</b> 上下交错，只为了"咬合"的剪影</li>
     *   <li><b>两只眼</b> 贴在颅骨两侧偏前，用贴图里的亮紫白，全亮光照下自行发光</li>
     *   <li><b>两对后弯的角</b> 每侧两段，给头部一个明确的"朝向"</li>
     *   <li><b>颊甲</b> 两侧斜板，遮住颅骨与颚之间的接缝</li>
     * </ul>
     *
     * <p>全部由立方体拼成 —— 这是 {@link ModelPart} 的物理限制，
     * 但靠<b>尺寸差</b>（14 宽的头 vs 16 宽的体节）和<b>轮廓破口</b>（牙、角、颚）
     * 就能拉开层次，不必非得是"一个方块"。
     */
    public static ModelPart head() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // ---- 颅骨：比体节窄 2px、长 2px ----
        root.addOrReplaceChild("skull", CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-7.0F, -7.0F, -9.0F, 14.0F, 14.0F, 18.0F),
                PartPose.ZERO);

        // ---- 上颚：向前伸出，比颅骨再窄一圈 ----
        root.addOrReplaceChild("snout", CubeListBuilder.create()
                .texOffs(0, 32)
                .addBox(-5.5F, -4.0F, 9.0F, 11.0F, 6.0F, 8.0F),
                PartPose.ZERO);

        // ---- 下颚：独立部件，带 0.12rad 张角（约 7°）----
        root.addOrReplaceChild("jaw", CubeListBuilder.create()
                .texOffs(64, 0)
                .addBox(-4.5F, 0.0F, 9.0F, 9.0F, 4.0F, 7.0F),
                PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.12F, 0.0F, 0.0F));

        // ---- 上排牙：6 颗，从上颚下缘往下扎 ----
        for (int i = 0; i < 6; i++) {
            float x = -5.0F + i * 2.0F;
            root.addOrReplaceChild("tooth_up_" + i, CubeListBuilder.create()
                    .texOffs(64, 28)
                    .addBox(x, 2.0F, 11.5F, 1.4F, 3.0F, 1.4F),
                    PartPose.ZERO);
        }

        // ---- 下排牙：5 颗，与上排交错 ----
        for (int i = 0; i < 5; i++) {
            float x = -4.0F + i * 2.0F;
            root.addOrReplaceChild("tooth_down_" + i, CubeListBuilder.create()
                    .texOffs(64, 28)
                    .addBox(x, 2.4F, 11.5F, 1.4F, 2.6F, 1.4F),
                    PartPose.ZERO);
        }

        // ---- 双眼：贴在颅骨两侧偏前 ----
        root.addOrReplaceChild("eye_l", CubeListBuilder.create()
                .texOffs(64, 16)
                .addBox(-7.6F, -3.0F, 3.0F, 2.0F, 3.0F, 4.0F),
                PartPose.ZERO);
        root.addOrReplaceChild("eye_r", CubeListBuilder.create()
                .texOffs(64, 16)
                .addBox(5.6F, -3.0F, 3.0F, 2.0F, 3.0F, 4.0F),
                PartPose.ZERO);

        // ---- 角：每侧两段，向后上方弯 ----
        root.addOrReplaceChild("horn_l1", CubeListBuilder.create()
                .texOffs(64, 36)
                .addBox(-8.0F, -9.0F, -4.0F, 3.0F, 3.0F, 7.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.25F, 0.0F, 0.0F));
        root.addOrReplaceChild("horn_l2", CubeListBuilder.create()
                .texOffs(64, 36)
                .addBox(-7.5F, -10.6F, -8.0F, 2.0F, 2.0F, 5.5F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.55F, 0.0F, 0.0F));
        root.addOrReplaceChild("horn_r1", CubeListBuilder.create()
                .texOffs(64, 36)
                .addBox(5.0F, -9.0F, -4.0F, 3.0F, 3.0F, 7.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.25F, 0.0F, 0.0F));
        root.addOrReplaceChild("horn_r2", CubeListBuilder.create()
                .texOffs(64, 36)
                .addBox(5.5F, -10.6F, -8.0F, 2.0F, 2.0F, 5.5F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.55F, 0.0F, 0.0F));

        // ---- 背脊：从颅顶往后拉，与体节的装甲环连成一条线 ----
        root.addOrReplaceChild("crest", CubeListBuilder.create()
                .texOffs(0, 64)
                .addBox(-5.0F, -10.0F, -9.0F, 10.0F, 4.0F, 16.0F),
                PartPose.ZERO);

        // ---- 星核：额头正中，全亮 ----
        root.addOrReplaceChild("core", CubeListBuilder.create()
                .texOffs(64, 52)
                .addBox(-3.0F, -2.0F, 8.0F, 6.0F, 5.0F, 3.0F),
                PartPose.ZERO);

        // 128×128：jaw / horn / crest 的 box 展开宽度超过 64，
        // 用 64 会越界采样到贴图边缘（表现为头四周一圈灰蓝色描边）。
        return LayerDefinition.create(mesh, 128, 128).bakeRoot();
    }

    /** 普通体节。 */
    public static ModelPart body() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // 主体：16 × 16 × 16，正好一格
        root.addOrReplaceChild("segment", CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-8.0F, -8.0F, -8.0F, 16.0F, 16.0F, 16.0F),
                PartPose.ZERO);

        // 装甲环：略大一圈的束带，撑出"分节"的观感 ——
        // 40 个一模一样的立方体会显得像香肠，加一道环就有了骨骼感
        root.addOrReplaceChild("ring", CubeListBuilder.create()
                .texOffs(0, 32)
                .addBox(-9.0F, -9.0F, -2.0F, 18.0F, 18.0F, 4.0F),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    /**
     * 星弹 —— 一个发光的小立方体。
     *
     * <p>8 像素 = 0.5 格，渲染时按 {@code boltScale} 与蓄力进度再缩放。
     * 弹幕在屏幕上很小又很快，所以造型上不做任何细节 ——
     * 只求"一眼看出是个紫色的东西"，细节越多反而越糊。
     */
    public static ModelPart bolt() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // 外层：晶棱
        root.addOrReplaceChild("shell", CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                PartPose.ZERO);

        // 内核：更亮更小的那一颗（自发光贴图只画在这一块上）
        root.addOrReplaceChild("core", CubeListBuilder.create()
                .texOffs(0, 16)
                .addBox(-2.5F, -2.5F, -2.5F, 5.0F, 5.0F, 5.0F),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 32, 32).bakeRoot();
    }

    /** 尾部（比体节小一圈，并在末端收尖）。 */
    public static ModelPart tail() {        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("segment", CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-6.0F, -6.0F, -6.0F, 12.0F, 12.0F, 12.0F),
                PartPose.ZERO);

        // 尾尖：沿 -Z（后方）收出去
        root.addOrReplaceChild("tip", CubeListBuilder.create()
                .texOffs(0, 24)
                .addBox(-3.0F, -3.0F, -12.0F, 6.0F, 6.0F, 7.0F),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }
}
