package com.lost_realm.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import com.lost_realm.machine.ChunkLoaderBlockEntity;
import com.lost_realm.network.MedalsNetwork;
import com.lost_realm.network.PacketChunkLoaderSet;

/**
 * <b>区块加载器</b>的调整界面。
 *
 * <p>三样东西：把范围在 1×1 / 3×3 / 5×5 / 7×7 / 9×9 之间调，一个总开关，一个关闭。
 *
 * <h2>为什么每次点按钮就立刻发包</h2>
 * 不做"改完再点确定"：加载器只有三个可调项，没有需要用户一次性提交的复合状态。
 * 立刻生效的好处是玩家可以边调边看——站在远处把范围往上加，眼看着区块加载出来的
 * 范围变大，比对着一个数字猜要直观得多。关掉界面时也不需要再发一次"保存"。
 *
 * <h2>界面状态从哪来</h2>
 * 打开时由 {@link com.lost_realm.network.PacketChunkLoaderOpen} 带过来，
 * 不是客户端自己猜的。之后每次改动只改本地副本用于立即重绘，
 * 真实状态以服务端 {@code BlockEntity} 为准。
 */
public class ChunkLoaderScreen extends Screen {

    private static final int PANEL_W = 232;
    private static final int PANEL_H = 150;
    private static final int BG = 0xC8120E1A;
    private static final int BORDER = 0xFF6B5A9E;
    private static final int TEXT = 0xFFE6E1F0;
    private static final int DIM = 0xFF9C93B8;

    private final BlockPos pos;
    private int size;
    private boolean active;
    private Button toggleButton;

    private ChunkLoaderScreen(BlockPos pos, int size, boolean active) {
        super(Component.translatable("gui.lost_realm.chunk_loader.title"));
        this.pos = pos;
        this.size = size;
        this.active = active;
    }

    public static void open(BlockPos pos, int size, boolean active) {
        Minecraft.getInstance().setScreen(new ChunkLoaderScreen(pos, size, active));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = this.height / 2 - PANEL_H / 2;

        this.addRenderableWidget(Button.builder(Component.literal("−"), b -> step(-1))
                .bounds(cx - 74, top + 52, 26, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("+"), b -> step(1))
                .bounds(cx + 48, top + 52, 26, 20).build());

        this.toggleButton = Button.builder(toggleLabel(), b -> {
            this.active = !this.active;
            b.setMessage(toggleLabel());
            push();
        }).bounds(cx - 64, top + 84, 128, 20).build();
        this.addRenderableWidget(this.toggleButton);

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(cx - 44, top + 116, 88, 20).build());
    }

    private Component toggleLabel() {
        return Component.translatable(active
                ? "gui.lost_realm.chunk_loader.state_on"
                : "gui.lost_realm.chunk_loader.state_off");
    }

    /** 在允许的档位里前后移动一格。 */
    private void step(int dir) {
        int idx = 0;
        for (int i = 0; i < ChunkLoaderBlockEntity.SIZES.length; i++) {
            if (ChunkLoaderBlockEntity.SIZES[i] == this.size) {
                idx = i;
            }
        }
        idx = Mth.clamp(idx + dir, 0, ChunkLoaderBlockEntity.SIZES.length - 1);
        this.size = ChunkLoaderBlockEntity.SIZES[idx];
        push();
    }

    private void push() {
        MedalsNetwork.sendToServer(new PacketChunkLoaderSet(this.pos, this.size, this.active));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        int cx = this.width / 2;
        int top = this.height / 2 - PANEL_H / 2;
        int left = cx - PANEL_W / 2;

        g.fill(left, top, left + PANEL_W, top + PANEL_H, BG);
        // 1px 边框：上/下/左/右四条线，比 fill 一个空心矩形更可控
        g.fill(left, top, left + PANEL_W, top + 1, BORDER);
        g.fill(left, top + PANEL_H - 1, left + PANEL_W, top + PANEL_H, BORDER);
        g.fill(left, top, left + 1, top + PANEL_H, BORDER);
        g.fill(left + PANEL_W - 1, top, left + PANEL_W, top + PANEL_H, BORDER);

        g.drawCenteredString(this.font, this.title, cx, top + 14, TEXT);
        g.drawCenteredString(this.font,
                Component.translatable("gui.lost_realm.chunk_loader.pos",
                        this.pos.getX(), this.pos.getY(), this.pos.getZ()),
                cx, top + 30, DIM);

        Component sizeText = Component.translatable("gui.lost_realm.chunk_loader.size",
                this.size, this.size);
        g.drawCenteredString(this.font, sizeText, cx, top + 58, TEXT);

        if (!this.active) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.lost_realm.chunk_loader.idle_hint"),
                    cx, top + 72, 0xFF8A6A3A);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        // 单人游戏里不暂停：调范围时能看到远处区块加载的进度，边调边看
        return false;
    }
}
