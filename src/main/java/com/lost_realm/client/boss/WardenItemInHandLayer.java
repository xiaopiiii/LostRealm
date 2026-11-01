package com.lost_realm.client.boss;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

import com.lost_realm.boss.entity.WardenBoss;

/**
 * 把 Boss 主手里的物品渲染到模型的 {@code weapon} 骨骼上（dv30）。
 *
 * <h2>为什么必须自己写一层</h2>
 * GeckoLib <b>4.4.9 根本没有 {@code ItemInHandGeoLayer}</b>（那是 4.5+ 才有的类，
 * 而我们锁死在 1.20.1 Forge 的稳定线 4.4.9，用户明确拒绝降级/升级）。
 * 反汇编 {@code geckolib-forge-1.20.1-4.4.9.jar} 确认渲染层目录里只有
 * AutoGlowing / BlockAndItem / BoneFilter / FastBoneFilter / ItemArmor 五层。
 *
 * <p>好在 {@link BlockAndItemGeoLayer} 就是为此设计的通用层，直接复用：
 * <pre>
 *   renderForBone:
 *     poseStack.pushPose();
 *     RenderUtils.translateAndRotateMatrixForBone(poseStack, bone);  // 移到骨骼 pivot
 *     ... renderStackForBone(...)  // Minecraft.getItemRenderer().renderStatic(...)
 *     poseStack.popPose();
 * </pre>
 * 关键是 {@code translateAndRotateMatrixForBone} = {@code translateToPivotPoint}
 * + {@code rotateMatrixAroundBone}，用的是骨骼的<b>原始 pivot</b>
 * （反汇编 {@code RenderUtils} 确认读的是 {@code CoreGeoBone#getPivotX}），
 * 而 {@code GeoEntityRenderer#renderRecursively} 在调用图层之前已经做过一次
 * {@code translateAwayFromPivotPoint}，因此不会产生"平移两次"的叠加错误。
 *
 * <h2>它解决了什么</h2>
 * dv29 把巨镰做成模型几何，反复重写十几版都不像新月弧（GeckoLib 只有长方体）。
 * 现在镰刀是一个<b>真正的物品</b>，贴图直接用参考图，造型 100% 忠于原图；
 * 本层只负责把它挂到右手上，并让它跟着摆臂动画一起动。
 */
public class WardenItemInHandLayer extends BlockAndItemGeoLayer<WardenBoss> {

    /**
     * 挂载骨骼名 —— 由 {@code tools/gen_geo.py} 的 {@code part_weapon_anchor()} 生成，
     * 是一个无 cube 的空锚点，pivot 正好落在右手掌心。
     */
    public static final String HAND_BONE = "weapon";

    public WardenItemInHandLayer(GeoRenderer<WardenBoss> renderer) {
        super(renderer);
    }

    /**
     * 只在 {@code weapon} 骨骼上放物品，其余骨骼一律返回 null（基类会直接跳过）。
     *
     * <p>拿的是<b>主手</b>物品，因此幽灵态的木剑也会正常出现在手里 ——
     * 那是 dv11 就定好的演出，不该被这层破坏。
     */
    @Override
    protected ItemStack getStackForBone(GeoBone bone, WardenBoss animatable) {
        if (!HAND_BONE.equals(bone.getName())) {
            return null;
        }
        ItemStack stack = animatable.getItemInHand(InteractionHand.MAIN_HAND);
        return stack.isEmpty() ? null : stack;
    }

    /**
     * 用第三人称右手的变换 —— 与 {@code warden_scythe.json} 里
     * {@code display.thirdperson_righthand} 对应（那里把缩放拉到 4.2，
     * 让镰刀在 Boss 手里显得格外巨大）。
     *
     * <p>基类默认返回 {@code ItemDisplayContext.NONE}（反汇编确认），
     * 那样物品会以"未变换"的原始姿态出现在骨骼上，姿态是错的。
     */
    @Override
    protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack,
                                                          WardenBoss animatable) {
        return ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }
}
