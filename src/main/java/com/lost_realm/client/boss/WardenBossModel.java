package com.lost_realm.client.boss;

import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

import com.lost_realm.Medals;
import com.lost_realm.boss.LordState;
import com.lost_realm.boss.entity.WardenBoss;

/**
 * 遗落之主的 GeckoLib 模型 —— <b>按当前形态切换三套几何</b>。
 *
 * <h2>为什么必须引入 GeckoLib(dv13 的根因结论)</h2>
 * 在此之前本模组走的是「零自定义渲染器」路线,靠覆写
 * {@code Entity#getScale()} 表达「换阶段变造型」。这条路经实测<b>行不通</b>:
 *
 * <pre>
 * $ javap -p net/minecraft/client/renderer/entity/LivingEntityRenderer.class
 *   ... 方法列表里没有 scale() ...
 * $ javap -p -c ...LivingEntityRenderer.class | grep m_6134_   # m_6134_ = getScale
 *   (无输出)
 * </pre>
 *
 * 即 <b>原版渲染管线根本不调用 {@code getScale()}</b>。
 * 它的唯一作用是撑大碰撞箱({@code getDimensions} 内部会用它),
 * 而模型大小完全由 {@code HumanoidModel} 的固定骨骼几何决定 ——
 * 于是表现为「服务端碰撞箱变大了、客户端模型纹丝不动」,
 * 这正是玩家反馈的「换阶段建模还是没变」。
 *
 * <p>更根本的限制是:{@code HumanoidModel} 只有 7 根骨骼
 * (head / hat / body / 左右臂 / 左右腿),<b>没有任何可挂载触手、翅膀的位置</b>。
 * 因此想要「触手 / 骨刺」这类造型,必须换成带自定义骨骼树的模型系统。
 *
 * <h2>三套几何与形态的对应</h2>
 * <table border="1">
 *   <tr><th>形态</th><th>几何文件</th><th>造型</th></tr>
 *   <tr><td>DORMANT / FORM_1</td><td>warden_form1</td><td>人形 + 背后根芽 + 肩甲</td></tr>
 *   <tr><td>FORM_2</td><td>warden_form2</td><td>6 条触手(各 5 节) + 5 根脊椎骨刺</td></tr>
 *   <tr><td>FORM_3</td><td>warden_form3</td><td>8 条触手(各 6 节) + 7 根骨刺 + 肩部骨翼</td></tr>
 * </table>
 *
 * <p>换阶段演出({@code TRANSITION_*})期间沿用<b>下一形态</b>的几何 ——
 * 这样玩家在回血的 30 秒里就能看到它「一边长触手一边回血」。
 *
 * <h2>为什么区分 {@code crashIfBoneMissing}</h2>
 * 几何文件由 {@code tools/gen_geo.py} 生成,骨骼名可能随调参变动。
 * 若某些动画轨道引用了不存在的骨骼,GeckoLib 默认会直接抛异常崩客户端。
 * 这里<b>覆写为 false</b>:缺失骨骼只跳过该轨道,不影响其它动画 ——
 * 对「几何与动画由脚本分别生成」的场景是必要的容错。
 */
public class WardenBossModel extends GeoModel<WardenBoss> {

    private static ResourceLocation geo(String name) {
        return new ResourceLocation(Medals.MODID, "geo/" + name + ".geo.json");
    }

    private static ResourceLocation anim(String name) {
        return new ResourceLocation(Medals.MODID, "animations/" + name + ".animation.json");
    }

    private static ResourceLocation texture(String name) {
        return new ResourceLocation(Medals.MODID, "textures/entity/" + name + ".png");
    }

    /**
     * 形态 -> 几何名。
     *
     * <p>⚠️ 这里<b>读的是 {@link WardenBoss#getLordState()}</b>
     * (走 {@code entityData} 同步数据),不是服务端私有字段。
     * 客户端读服务端私有字段只会拿到旧值 —— 这是 dv11 踩过的坑
     * (见项目规则:「影响外观的判断必须读同步数据」)。
     */
    private static String formName(WardenBoss entity) {
        LordState st = entity.getLordState();
        // 演出期间直接展示「即将变成」的形态,让换阶段看得见
        LordState shown = st.isTransition() ? st.nextFormAfterTransition() : st;
        if (shown == null) {
            shown = st;
        }
        return switch (shown) {
            case FORM_2 -> "warden_form2";
            case FORM_3 -> "warden_form3";
            // DORMANT / FORM_1 / ENRAGED_GHOST / WAVES / DEFEATED 都用第一形态几何
            default -> "warden_form1";
        };
    }

    @Override
    public ResourceLocation getModelResource(WardenBoss animatable) {
        return geo(formName(animatable));
    }

    @Override
    public ResourceLocation getTextureResource(WardenBoss animatable) {
        // ★ dv34：二三阶段换成用户提供的 bbmodel（tools/bbmodel_to_gecko.py
        //   产出的重排图集），form1 仍用 gen_geo/gen_texture 那套 128x128 图。
        LordState st = animatable.getLordState();
        LordState shown = st.isTransition() ? st.nextFormAfterTransition() : st;
        if (shown == null) {
            shown = st;
        }
        return switch (shown) {
            case FORM_2 -> texture("warden_phase2");
            case FORM_3 -> texture("warden_phase3");
            default -> texture("warden");
        };
    }

    @Override
    public ResourceLocation getAnimationResource(WardenBoss animatable) {
        return anim(formName(animatable));
    }

    /**
     * 骨骼缺失时不崩 —— 只跳过该条轨道。
     * 几何与动画由脚本分别生成,允许短暂不同步。
     */
    @Override
    public boolean crashIfBoneMissing() {
        return false;
    }
}
