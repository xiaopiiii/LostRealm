package com.lost_realm.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * 服务端 → 客户端:打开「遗落之主」的对话界面。
 *
 * <h2>为什么用语言键而不是直接传文本</h2>
 * 台词的最终文案写在 {@code lang/*.json} 里,包内只传 <b>key</b>:
 * <ul>
 *   <li>客户端按自己的语言渲染 —— 中/英各看各的</li>
 *   <li>日后改文案只需改 JSON,不必重新编译</li>
 *   <li>包体极小(几条短字符串),无性能负担</li>
 * </ul>
 *
 * <h2>字段</h2>
 * <ul>
 *   <li>{@code lines} —— 台词的语言键列表,按顺序翻页</li>
 *   <li>{@code showChoices} —— 末页是否显示「离开 / 决斗」两个按钮</li>
 * </ul>
 * 注意:是否显示 <b>长台词</b> 由服务端决定 —— 看过一次之后,
 * 服务端只会发一条"摘要台词"并直接带选项(用户要求:
 * 「对话框只会出现一次,被他击败重新对话只会出现离开或决斗的对话」)。
 */
public class PacketOpenDialogue {

    private final List<String> lines;
    private final boolean showChoices;

    public PacketOpenDialogue(List<String> lines, boolean showChoices) {
        this.lines = lines;
        this.showChoices = showChoices;
    }

    public PacketOpenDialogue(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(buf.readUtf(256));
        }
        this.lines = list;
        this.showChoices = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(lines.size());
        for (String s : lines) {
            buf.writeUtf(s, 256);
        }
        buf.writeBoolean(showChoices);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            // 客户端:打开对话界面(纯客户端 Screen,无副作用)
            com.lost_realm.client.dialogue.DialogueScreen.open(lines, showChoices);
        });
        context.setPacketHandled(true);
    }

    public List<String> lines() {
        return lines;
    }

    public boolean showChoices() {
        return showChoices;
    }
}
