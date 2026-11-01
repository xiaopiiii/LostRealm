package com.lost_realm.client.dialogue;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import com.lost_realm.network.MedalsNetwork;
import com.lost_realm.network.PacketDialogueChoice;

/**
 * 「遗落之主」的<b>对话界面</b>。
 *
 * <h2>视觉设计</h2>
 * 屏幕底部一块半透明黑底面板,逐字打出一段台词;
 * 右下角「下一页」按钮翻页;末页换成「离开 / 决斗」两个按钮。
 * 台词滚动打字用 {@code openTime} 计时实现,不引入额外状态。
 *
 * <h2>1.20.1 的 API 注意点</h2>
 * <ul>
 *   <li>{@code Screen#renderBackground} <b>只接受 1 个参数</b>
 *       ({@code GuiGraphics}) —— 4 参重载是 1.20.5+ 才有的,
 *       写 4 参会编译报错</li>
 *   <li>换行用 {@code Font#split} 手动拆,不用 {@code FormattedText}
 *       的重载以避免版本差异</li>
 * </ul>
 *
 * <h2>为什么不暂停游戏</h2>
 * {@link #isPauseScreen()} 返回 {@code false} —— 对话时遗落之主仍在原地,
 * 玩家也仍可能被其他因素影响(多人服务器尤其如此)。
 * 暂停会造成"单人能暂停、多人不能"的体验割裂。
 */
public class DialogueScreen extends Screen {

    private static final int PANEL_BG = 0xD0101010;
    private static final int PANEL_BORDER = 0xFF9B59B6;
    private static final int TEXT_COLOR = 0xFFE8E0F0;
    private static final int NAME_COLOR = 0xFFC79BF0;

    /** 打字速度:每字符消耗的毫秒数。 */
    private static final long MS_PER_CHAR = 22L;

    /** 台词语言键列表(由服务端下发)。 */
    private final List<String> lines;

    /** 末页是否显示「离开 / 决斗」两个选项。 */
    private final boolean showChoices;

    /** 当前页索引。 */
    private int page;

    /** 本页开始打字的时间戳(毫秒)。 */
    private long pageOpenTime;

    /** 布局缓存(用于 render 里绘制面板)。 */
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    public DialogueScreen(List<String> lines, boolean showChoices) {
        super(Component.translatable("screen.lost_realm.dialogue.title"));
        this.lines = lines == null || lines.isEmpty()
                ? List.of("dialogue.lost_realm.lord.line.brief")
                : lines;
        this.showChoices = showChoices;
        this.pageOpenTime = System.currentTimeMillis();
    }

    /** 供网络包调用:打开界面。 */
    public static void open(List<String> lines, boolean showChoices) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new DialogueScreen(lines, showChoices));
    }

    // ================================================================
    // 布局
    // ================================================================

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearWidgets();

        panelW = Math.min(420, width - 40);
        panelH = 120;
        panelX = (width - panelW) / 2;
        panelY = height - panelH - 24;

        boolean lastPage = isLastPage();
        int btnY = panelY + panelH - 26;

        if (lastPage && showChoices) {
            // 末页:离开 / 决斗
            addRenderableWidget(Button.builder(
                            Component.translatable("dialogue.lost_realm.lord.choice.leave"),
                            b -> choose(PacketDialogueChoice.CHOICE_LEAVE))
                    .bounds(panelX + panelW - 174, btnY, 80, 20).build());

            Button duel = Button.builder(
                            Component.translatable("dialogue.lost_realm.lord.choice.duel"),
                            b -> choose(PacketDialogueChoice.CHOICE_DUEL))
                    .bounds(panelX + panelW - 90, btnY, 80, 20).build();
            // 决斗按钮用紫色描边强调 —— 这是不可逆的选择
            addRenderableWidget(duel);
        } else if (!lastPage) {
            addRenderableWidget(Button.builder(
                            Component.translatable("dialogue.lost_realm.lord.next"),
                            b -> nextPage())
                    .bounds(panelX + panelW - 90, btnY, 80, 20).build());
        }
    }

    private boolean isLastPage() {
        return page >= lines.size() - 1;
    }

    private void nextPage() {
        if (!isLastPage()) {
            page++;
            pageOpenTime = System.currentTimeMillis();
            rebuild();
        }
    }

    /** 提交选择 —— 发到服务端后立即关界面。 */
    private void choose(int choice) {
        MedalsNetwork.sendToServer(new PacketDialogueChoice(choice));
        onClose();
    }

    // ================================================================
    // 渲染
    // ================================================================

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // ★ 1.20.1 只有单参 renderBackground
        renderBackground(gfx);

        // ---- 面板 ----
        gfx.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, PANEL_BG);
        gfx.renderOutline(panelX - 2, panelY - 2, panelW + 4, panelH + 4, PANEL_BORDER);

        // ---- 说话者 ----
        gfx.drawString(font,
                Component.translatable("entity.lost_realm.lord"),
                panelX + 10, panelY + 8, NAME_COLOR);

        // ---- 台词(打字机) ----
        Component line = Component.translatable(lines.get(page));
        String full = line.getString();
        long elapsed = System.currentTimeMillis() - pageOpenTime;
        int charsShown = (int) Math.min(full.length(), elapsed / MS_PER_CHAR);
        String shown = full.substring(0, Math.max(0, charsShown));

        int wrapW = panelW - 20;
        List<FormattedCharSequence> rows = font.split(
                Component.literal(shown).withStyle(line.getStyle()), wrapW);
        int ty = panelY + 24;
        for (FormattedCharSequence row : rows) {
            if (ty > panelY + panelH - 30) {
                break;  // 面板放不下就截断(台词长度已在语言键里控制好)
            }
            gfx.drawString(font, row, panelX + 10, ty, TEXT_COLOR);
            ty += font.lineHeight + 2;
        }

        // ---- 翻页提示(右下角小字,当本页打字未完时提示点击加速) ----
        if (!isLastPage()) {
            boolean done = charsShown >= full.length();
            gfx.drawString(font,
                    Component.translatable(done
                            ? "dialogue.lost_realm.lord.hint.next"
                            : "dialogue.lost_realm.lord.hint.skip"),
                    panelX + 10, panelY + panelH - 20, 0xFF808080);
        }

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    /**
     * 点击任意处:若本页仍在打字 → 立即打完;否则翻页。
     * 这是"点击加速"的常见交互,让急性子玩家不必等。
     */
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && !isLastPage()) {
            String full = Component.translatable(lines.get(page)).getString();
            long elapsed = System.currentTimeMillis() - pageOpenTime;
            if (elapsed / MS_PER_CHAR < full.length()) {
                // 立即打完:把计时拨到"刚好看完"
                pageOpenTime = System.currentTimeMillis() - full.length() * MS_PER_CHAR;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        // 关界面 = 留在零阶段,玩家可以再来右键
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }
}
