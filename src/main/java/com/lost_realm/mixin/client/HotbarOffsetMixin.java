package com.lost_realm.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 底部 HUD 整体上移 1 像素。
 *
 * <h2>为什么改 {@code Gui#render} 而不是逐个改子方法</h2>
 * 底部的五个图层各自从 {@code Gui#screenHeight} 现算 Y 坐标:
 *
 * <ul>
 *   <li>{@code renderHotbar} —— 快捷栏格子 + 手持物品</li>
 *   <li>{@code renderPlayerHealth} —— 血量 / 护甲 / 饥饿 / 坐骑血</li>
 *   <li>{@code renderVehicleHealth} —— 坐骑血量</li>
 *   <li>{@code renderExperienceBar} —— 经验条</li>
 *   <li>{@code renderSelectedItemName} —— 手持物品名</li>
 * </ul>
 *
 * 它们内部没有共享的 PoseStack 变换,也没有一个统一的「HUD 根节点」,
 * 所以逐个改会产生五处分散的注入点,后续跟随原版更新时极易失效。
 *
 * <p>反过来看,这五个调用在 {@code render} 里是<b>连续</b>的一段
 * (快捷栏 → 血量 → 坐骑血 → 经验条 → 物品名),因此这里只在
 * <b>这段区间的两端</b>各注入一次,用一次
 * {@code pose.translate(0, -1, 0)} 把整段一起端起来 —— 一处逻辑、
 * 一处偏移量,改起来只动一个常数。
 *
 * <h2>为什么在 {@code renderHotbar} 之前 push、{@code renderSelectedItemName} 之后 pop</h2>
 * 注入点选在「调用这两个方法的前后」而非 {@code render} 的 HEAD/RETURN,
 * 是为了<b>不误伤</b>同处 {@code render} 内、但属于屏幕上半部分的内容:
 * BOSS 血条、聊天栏、记分板、字幕、Tab 列表、十字准星等。
 * 那些内容必须留在原位。
 *
 * <h2>PoseStack 平衡</h2>
 * push 与 pop 严格配对,且两端分别挂在「必定执行」的调用点上。
 * 即便某个子方法内部提前 return(如 {@code renderHotbar} 里玩家为空时),
 * 也不影响本注入 —— 因为平衡点是原版的调用语句,而非子方法的内部流程。
 */
@Mixin(Gui.class)
public abstract class HotbarOffsetMixin {

    /**
     * 底部 HUD 的垂直偏移量(像素)。
     *
     * <p>负值向上。此处为 <b>-1</b>:把整个底部 HUD 抬高 1 像素。
     */
    private static final float MEDALS_HUD_OFFSET_Y = -1.0F;

    /** 记录本帧是否已 push,避免异常路径下重复 push 或 pop 失配。 */
    private boolean medals$hudOffsetPushed;

    /**
     * 在 {@code renderHotbar} 调用<b>之前</b>上移坐标系。
     *
     * <p>锚点选择 {@code INVOKE renderHotbar} 的 {@code BEFORE},
     * 这样偏移恰好覆盖到快捷栏本身。
     */
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Gui;renderHotbar(FLnet/minecraft/client/gui/GuiGraphics;)V",
                    shift = At.Shift.BEFORE))
    private void medals$pushHudOffset(GuiGraphics gfx, float partialTick, CallbackInfo ci) {
        PoseStack pose = gfx.pose();
        pose.pushPose();
        pose.translate(0.0F, MEDALS_HUD_OFFSET_Y, 0.0F);
        this.medals$hudOffsetPushed = true;
    }

    /**
     * 在 {@code renderSelectedItemName} 调用<b>之后</b>恢复坐标系。
     *
     * <p>该方法是底部 HUD 那一段的最后一个图层,因此在此 pop 可保证
     * 「上移」只作用于底部 HUD,而不会波及之后绘制的其它界面元素。
     *
     * <p>采用两个重载中「无参」的那个
     * ({@code renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;)V}),
     * 它正是 {@code render} 中实际调用的版本。
     */
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Gui;renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;)V",
                    shift = At.Shift.AFTER))
    private void medals$popHudOffset(GuiGraphics gfx, float partialTick, CallbackInfo ci) {
        medals$restore(gfx);
    }

    /**
     * 兜底:若因其它模组取消/改动导致 pop 点未命中,在 {@code render} 返回前强制还原,
     * 防止 PoseStack 失衡污染后续所有绘制。
     */
    @Inject(method = "render", at = @At("RETURN"))
    private void medals$ensureRestored(GuiGraphics gfx, float partialTick, CallbackInfo ci) {
        medals$restore(gfx);
    }

    /** 幂等还原:只有确实 push 过才 pop,避免重复 pop 造成下溢。 */
    private void medals$restore(GuiGraphics gfx) {
        if (this.medals$hudOffsetPushed) {
            this.medals$hudOffsetPushed = false;
            gfx.pose().popPose();
        }
    }
}
