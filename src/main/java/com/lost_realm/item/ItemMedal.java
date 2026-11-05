package com.lost_realm.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.Multimap;

import com.lost_realm.config.MedalsConfig;
import com.lost_realm.data.MedalNbt;
import com.lost_realm.data.PlayerMedals;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;
import com.lost_realm.skill.SkillRegistry;
import com.lost_realm.util.ExpTable;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import top.theillusivec4.curios.api.SlotAttribute;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * 勋章物品 —— 佩戴于 Curios「勋章」饰品槽的饰品。
 *
 * <p>佩戴后:
 * <ul>
 *   <li>持续累积对应勋章的熟练度(由事件层计算经验来源)</li>
 *   <li>按当前等级施加基础属性成长</li>
 *   <li>按已解锁节点施加技能效果</li>
 * </ul>
 *
 * <h2>熟练度存放在物品自身</h2>
 * 累计经验与等级**不存在玩家身上**,而是写在勋章的 NBT 里(见 {@link MedalNbt})。
 * 因此勋章被取下、装箱、交易、丢给别人时,熟练度都跟着走。
 * 玩家侧只保留「主动技能冷却」与「节点技能运行时状态」这类
 * 属于玩家此刻状态的短生命期数据。
 *
 * <h2>一次只能带一枚</h2>
 * 通过 {@link #canEquip} 检查 Curios 背包中是否已有其它勋章来实现。
 * 由于「勋章」槽位本身也只开 1 格,这是双重保险。
 */
public class ItemMedal extends Item implements ICurioItem {

    private final MedalType type;

    public ItemMedal(MedalType type) {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
        this.type = type;
    }

    public MedalType medalType() {
        return type;
    }

    /**
     * 该勋章在当前环境下是否可用。
     *
     * <p>法师勋章依赖 Goety 的官方 API(灵魂能量 / 聚晶法术 / 仆从);
     * 召唤师勋章同样以 Goety 的仆从体系为底座。未安装 Goety 时两者都返回
     * {@code false},从而软禁用:不出现在创造标签页、技能不触发、属性不施加。
     *
     * <p>其余勋章恒为可用。
     */
    public boolean isEnabled() {
        if (type == MedalType.MAGE || type == MedalType.SUMMONER) {
            return com.lost_realm.compat.goety.GoetyCompat.isLoaded();
        }
        return true;
    }

    // ================================================================
    // 物品生命周期 —— 保证 NBT 绑定
    // ================================================================

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        super.onCraftedBy(stack, level, player);
        MedalNbt.of(stack).bind(type);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean selected) {
        super.inventoryTick(stack, level, entity, slotId, selected);
        // 指令 /give 出来的勋章可能还没绑定,补一次(开销极小:仅查一个字符串)
        if (!level.isClientSide && entity instanceof Player && !stack.isEmpty()) {
            CompoundTag tag = stack.getTag();
            if (tag == null || !tag.contains("MedalOwner")) {
                MedalNbt.of(stack).bind(type);
            }
        }
    }

    // ================================================================
    // Curios 饰品行为
    // ================================================================

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        // 同一槽位只允许佩戴一枚勋章:检查 Curios 背包中是否已有其他勋章
        var living = slotContext.entity();
        if (living == null) {
            return true;
        }
        return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(living).map(handler -> {
            var found = handler.findFirstCurio(s -> s.getItem() instanceof ItemMedal);
            if (found.isEmpty()) {
                return true;
            }
            ItemStack equipped = found.get().stack();
            // 允许「当前这一枚」自身
            return equipped == stack || equipped.isEmpty()
                    || equipped.getItem() == stack.getItem();
        }).orElse(true);
    }

    /**
     * 佩戴这枚勋章时，解锁对应的<b>职业专属饰品槽</b>。
     *
     * <h2>为什么用 SlotAttribute</h2>
     * 四个职业槽（箭 / 武器 / 盾牌 / 聚晶）在数据包里都定义为 <b>0 格</b>，
     * 于是没戴勋章的人在饰品界面上根本看不到它们 —— 这正是我们要的效果：
     * 玩射手的玩家不该被刃/坦克/法师的槽位占掉三行位置。
     *
     * <p>Curios 的槽位数量是「基础值 + SlotAttribute 修饰符」算出来的，
     * 而物品的 {@code getAttributeModifiers} 恰好只在<b>物品位于饰品槽中</b>时生效，
     * 于是「戴上 → +N 格」「摘下 → 收回」全自动，不需要自己维护任何状态。
     *
     * <p>这也是 5.x 唯一还活着的方式：{@code CuriosApi.addSlotModifier}
     * 在这个版本已经是空壳（调用即报错），SlotAttribute 才是现行 API。
     */
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(
            SlotContext slotContext, java.util.UUID uuid, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> map =
                ICurioItem.super.getAttributeModifiers(slotContext, uuid, stack);
        map.put(SlotAttribute.getOrCreate(type.jobSlot()),
                new AttributeModifier(uuid, "lost_realm_job_slot",
                        MedalsConfig.JOB_SLOT_SIZE.get(),
                        AttributeModifier.Operation.ADDITION));
        return map;
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        // 绑定类型(某些来源的勋章可能还没有 NBT)
        MedalNbt.of(stack).bind(type);
        if (slotContext.entity() instanceof ServerPlayer player) {
            // 首次获得或换装时,确保属性修饰符刷新到当前等级
            MedalRuntime.refresh(player, type, PlayerMedals.of(player), stack);
        }
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        if (slotContext.entity() instanceof ServerPlayer player) {
            // 卸下勋章:清理其带来的所有属性修饰符
            MedalRuntime.clear(player, type);
        }
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        // ★ 这里原本每 tick 也调一次 MedalRuntime.refresh —— 与
        //   PlayerTickHandler#onPlayerTick 里的那份完全重复(同一 tick 刷两遍)。
        //   beta44 起只保留 PlayerTickHandler 那一处(它遍历全部 5 个槽位,
        //   本方法只对「当前这件物品」触发,覆盖范围更小)。
        //   这个空实现保留是为了说明"为什么不在这里刷",免得以后又被加回来。
    }

    // ================================================================
    // 提示信息 —— 全部从物品自身的 NBT 读取
    // ================================================================

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        // 关键:数据来源于物品本身,不依赖玩家 —— 在箱子/背包里也能看到熟练度
        MedalNbt nbt = MedalNbt.of(stack);
        int lv = nbt.getLevel(type);

        // ---- 标题 + 阶位(同一行) ----
        // ★ 阶位跟着勋章名走:`法师勋章[学徒]`。
        //   此前阶位单独占一行(「等级: Lv.6 [精锐]」),既多占一行高度,
        //   又要玩家自己把「勋章 → 阶位」两块信息在脑子里拼起来。
        tooltip.add(Component.literal(type.displayName())
                .withStyle(ChatFormatting.GOLD)
                .append(tierTag(type, lv)));

        // ---- 熟练度 + 等级(同一行) ----
        // ★ 数字等级跟在熟练度后面。分子是本级已积累,分母是升到下一级所需。
        //   旧版是「进度条 + 百分比」外加单独一行的「距下一级: N XP」,三处说同一件事。
        tooltip.add(Component.translatable("tooltip.lost_realm.medal.progress")
                .append(" " + progressText(nbt, lv))
                .append(" ")
                .append(Component.literal("Lv." + lv).withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.AQUA));

        // ---- 枷锁提示 ----
        // 只在「恰好停在枷锁等级」时显示：已经破枷的玩家会继续升到 21/41，
        // 自然不会看到这一行；而卡住的人正是最需要知道原因的人。
        if ((lv == 20 || lv == 40) && lv < com.lost_realm.util.ExpTable.maxLevel()) {
            tooltip.add(Component.translatable("tooltip.lost_realm.medal.shackled", lv)
                    .withStyle(ChatFormatting.DARK_RED));
        }

        // ---- 属性加成 ----
        // ★ 标题单独一行,属性**一行两项**（名称 / 数值各自成列）。
        //   只写当前等级实际生效的数值,不写「每级 +X」的成长公式 ——
        //   玩家打开 tooltip 想知道的是"这枚勋章现在给了我什么"。
        tooltip.add(Component.translatable("tooltip.lost_realm.medal.attributes")
                .withStyle(ChatFormatting.YELLOW));
        tooltip.addAll(attributeRows(type, lv));

        // ---- 节点技能 ----
        // ★ 标题单独一行;效果**直接接在技能名后面**,一行放不下才折行。
        //
        //   按键规则(dv46 起):
        //     · 法师 —— 文案最长,分两段:Shift 展开 Lv.10/20/30,Ctrl 展开 Lv.40/50/60;
        //     · 其余三枚 —— 只用一个 Shift 展开全部 6 条。
        tooltip.add(Component.translatable("tooltip.lost_realm.medal.skills")
                .withStyle(ChatFormatting.YELLOW));

        boolean split = type == MedalType.MAGE;
        boolean lowOpen = shiftDown();
        boolean highOpen = split && ctrlDown();
        appendSkillRows(tooltip, type, lv, lowOpen, highOpen, split);

        int[] ranges = levelRanges(type);
        if (split) {
            if (!lowOpen) {
                tooltip.add(Component.translatable("tooltip.lost_realm.medal.hold_shift",
                        ranges[0], ranges[1]));
            }
            if (!highOpen) {
                tooltip.add(Component.translatable("tooltip.lost_realm.medal.hold_ctrl",
                        ranges[2], ranges[3]));
            }
        } else if (!lowOpen) {
            tooltip.add(Component.translatable("tooltip.lost_realm.medal.hold_shift_all"));
        }
    }

    /**
     * 低阶 / 高阶两段的技能等级区间:{@code [低起, 低止, 高起, 高止]}。
     *
     * <p>分段位置与 {@link #appendSkillRows} 保持一致（都是「列表对半分」），
     * 否则提示里的数字会和实际展开的那批对不上。
     */
    private static int[] levelRanges(MedalType type) {
        List<Skill> skills = SkillRegistry.of(type);
        int n = skills.size();
        int half = Math.max(1, n / 2);
        return new int[] {
                skills.get(0).unlockLevel(),
                skills.get(Math.min(half, n) - 1).unlockLevel(),
                skills.get(Math.min(half, n - 1)).unlockLevel(),
                skills.get(n - 1).unlockLevel(),
        };
    }

    /**
     * 属性加成区块:标题单独一行,内容是**一行两项**。
     *
     * <p>tooltip 就是若干行纯文本,没有任何「列」能力,想让两列对齐只有补空格一条路。
     * 补多少个由 {@link #textPx} 量出的<b>真实</b>宽度决定（客户端字体,精确到像素）。
     * 列宽取该勋章四项里的最大值,于是两组四项都落在同一套列上:
     * <pre>
     *   法术强度     +7.5      施法速度     +10.5%
     *   灵魂消耗     +3.6%     法术作用半径 +16%
     * </pre>
     * 第一组的数值补齐到列宽后,第二组的名称起点才对得齐（行尾的补白看不见,无所谓）。
     */
    private static List<Component> attributeRows(MedalType type, int lv) {
        List<MedalAttributeText.Attr> attrs = MedalAttributeText.of(type, lv);
        int n = attrs.size();

        String[] names = new String[n];
        String[] values = new String[n];
        double nameW = 0.0D;
        double valueW = 0.0D;
        for (int i = 0; i < n; i++) {
            // translatable 在这一刻解出当前语言的实际文本，中英文各自按自己的宽度对齐。
            // ⚠️ MedalAttributeText 给的是**裸键**（arrow_damage），必须补上 attr. 前缀 ——
            //    漏掉前缀时 tooltip 会直接把键名印出来（显示成「arrow_damage +8%」）。
            names[i] = Component.translatable(ATTR_KEY_PREFIX + attrs.get(i).key()).getString();
            values[i] = attrs.get(i).value();
            nameW = Math.max(nameW, textPx(names[i]));
            valueW = Math.max(valueW, textPx(values[i]));
        }

        List<Component> out = new ArrayList<>((n + 1) / 2);
        for (int i = 0; i < n; i += 2) {
            boolean hasSecond = i + 1 < n;
            MutableComponent row = Component.literal(ROW_INDENT)
                    .append(cell(names[i], nameW, ChatFormatting.BLUE))
                    .append(" ")
                    .append(cell(values[i], hasSecond ? valueW : 0.0D, ChatFormatting.AQUA));
            if (hasSecond) {
                row = row.append(spaces(GROUP_GAP_PX))
                        .append(cell(names[i + 1], nameW, ChatFormatting.BLUE))
                        .append(" ")
                        .append(cell(values[i + 1], 0.0D, ChatFormatting.AQUA));
            }
            out.add(row);
        }
        return out;
    }

    /**
     * 节点技能区块:每条一行,效果**直接接在技能名之后**。
     *
     * <p>默认（不按键）只有名字;按住 Shift 展开详情。
     * {@code split} 为 {@code true}（法师）时把 6 条按等级对半切开:
     * Shift 展开前半段（Lv.10/20/30）、Ctrl 展开后半段（Lv.40/50/60）——
     * 法师的文案加起来四十多行,一次全展开必然顶出屏幕。
     * 其余三枚勋章的文案短,只用一个 Shift 全展。
     *
     * <h2>折行规则</h2>
     * 首行接着技能名走（可用宽度窄），<b>从第二行起回到行首</b>（接近满宽）。
     * 一开始是让续行缩进到「描述起点」的，看着整齐，但代价是每一行都被压在
     * 同一列里 —— 一条 40 字的描述能折出 7~8 行，比不折还占地方。
     *
     * <h2>类型标记的位置</h2>
     * {@code [主动]} 挂在<b>描述末尾</b>而不是技能名后面。挂在名字后面时它会
     * 占掉名称列的宽度，把该条（以及为了对齐而带来的整列）的起点推向右边，
     * 而它其实只是描述的附属信息。
     */
    private static void appendSkillRows(List<Component> out, MedalType type, int lv,
                                        boolean lowOpen, boolean highOpen, boolean split) {
        List<Skill> skills = SkillRegistry.of(type);
        int half = Math.max(1, skills.size() / 2);

        double lvW = 0.0D;
        double nameW = 0.0D;
        for (Skill s : skills) {
            lvW = Math.max(lvW, textPx("Lv." + s.unlockLevel()));
            nameW = Math.max(nameW, textPx(skillLabel(s)));
        }

        for (int i = 0; i < skills.size(); i++) {
            Skill s = skills.get(i);
            boolean detail = split ? (i < half ? lowOpen : highOpen) : lowOpen;
            boolean unlocked = lv >= s.unlockLevel();
            // ★ 技能名用橙色(§6):它要在整片灰色描述里当路标。
            //   未解锁仍然走暗灰 —— 否则玩家分不出哪些还没点亮。
            ChatFormatting skillColor = unlocked ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY;
            ChatFormatting descColor = unlocked ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY;

            String lvLabel = padRight("Lv." + s.unlockLevel(), lvW) + " ";
            String name = padRight(skillLabel(s), nameW);

            MutableComponent row = Component.literal(ROW_INDENT)
                    .append(Component.literal(lvLabel))
                    .append(Component.literal(name))
                    .withStyle(skillColor);

            if (!detail) {
                out.add(row);
                continue;
            }

            double headW = textPx(ROW_INDENT) + textPx(lvLabel) + textPx(name) + 4.0D;
            double firstW = Math.max(MIN_DESC_W, MAX_CONTENT_W - headW);
            double restW = Math.max(MIN_DESC_W, MAX_CONTENT_W - textPx(ROW_INDENT));
            List<String> lines = wrapLines(s.description(), firstW, restW);

            String tag = tagText(s);
            if (lines.isEmpty()) {
                out.add(tag.isEmpty() ? row : row.append(" ").append(tagPart(tag)));
                continue;
            }

            int n = lines.size();
            // ★ 末行顺带把类型标记缀上；实在放不下才让它单独占一行
            MutableComponent tail = Component.literal(lines.get(n - 1));
            boolean tagOnOwnLine = false;
            if (!tag.isEmpty()) {
                double limit = (n == 1) ? firstW : restW;
                if (textPx(lines.get(n - 1) + " " + tag) <= limit) {
                    tail = tail.append(" ").append(tagPart(tag));
                } else {
                    tagOnOwnLine = true;
                }
            }
            MutableComponent lastLine = tail.withStyle(descColor);

            // ★ 首行必须接着技能名 —— 早先折行时把整行（含名字）丢掉了，
            //   结果是「描述还在、技能名消失」，几条描述看着连成一片，
            //   根本分不清哪段属于哪个技能。
            if (n == 1) {
                out.add(row.append(" ").append(lastLine));
            } else {
                out.add(row.append(" ").append(
                        Component.literal(lines.get(0)).withStyle(descColor)));
                for (int k = 1; k < n - 1; k++) {
                    out.add(Component.literal(ROW_INDENT + lines.get(k)).withStyle(descColor));
                }
                out.add(Component.literal(ROW_INDENT).append(lastLine));
            }
            if (tagOnOwnLine) {
                out.add(Component.literal(ROW_INDENT).append(tagPart(tag)));
            }
        }
    }

    private static MutableComponent tagPart(String tag) {
        return Component.literal(tag).withStyle(TAG_COLOR);
    }

    /**
     * 按像素宽度把描述折成多行。
     *
     * <p>原版 tooltip <b>不会自动折行</b> —— 一行有多长就画多长,长了直接顶出屏幕。
     * 所以必须自己断。中文没有词间空格,只能按字断;唯一的讲究是
     * <b>不让标点落在行首</b>（断在「，」「。」前面读起来很别扭),
     * 遇到这类字符时宁可让本行多挤一个字。
     *
     * @param firstW 首行可用宽度（首行前面还挂着技能名，比后续行窄）
     * @param restW  其余行的可用宽度（从行首开始，接近满宽）
     */
    private static List<String> wrapLines(List<Component> segs, double firstW, double restW) {
        List<String> out = new ArrayList<>();
        boolean atFirst = true;
        StringBuilder cur = new StringBuilder();
        double w = 0.0D;

        for (Component seg : segs) {
            String text = seg.getString();
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                int len = Character.charCount(cp);
                String ch = text.substring(i, i + len);
                i += len;

                double cw = charPx(cp);
                if (w + cw > (atFirst ? firstW : restW)
                        && cur.length() > 0 && !isBadLineStart(ch)) {
                    out.add(cur.toString());
                    cur.setLength(0);
                    w = 0.0D;
                    atFirst = false;
                }
                cur.append(ch);
                w += cw;
            }
            // 每段描述（description() 里的一段）自成一行起头
            if (cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
                w = 0.0D;
                atFirst = false;
            }
        }
        return out;
    }

    /** 不该出现在行首的字符（中文标点与收尾括号）。 */
    private static boolean isBadLineStart(String ch) {
        return "，。、；：！？）】》」』%,.;:!?)]}".contains(ch);
    }

    /**
     * 技能类型标记：主动技能返回 {@code [主动]}，被动技能返回空串。
     *
     * <p>只在这里加一次 —— 技能名里已不带后缀（旧版 lang 值形如「疾风骤雨(主动)」，
     * 与这里的前缀撞成了两个「主动」）。
     */
    /**
     * 技能名的显示形态：{@code [闪避]}。
     *
     * <p>加中括号是为了把名字和紧跟其后的描述**在视觉上切开** ——
     * 名字与描述同色系、又直接相连时，一眼扫过去会读成一句长文案。
     */
    private static String skillLabel(Skill skill) {
        return "[" + skill.displayName().getString() + "]";
    }

    private static String tagText(Skill skill) {
        return skill.isActive()
                ? Component.translatable("tooltip.lost_realm.medal.active").getString()
                : "";
    }

    private static Component cell(String text, double targetW, ChatFormatting color) {
        return Component.literal(targetW > 0.0D ? padRight(text, targetW) : text).withStyle(color);
    }

    /** 折算出 {@code px} 像素宽的空格串。 */
    private static String spaces(double px) {
        return " ".repeat(Math.max(1, (int) Math.round(px / 4.0D)));
    }

    /**
     * 一段文本在 tooltip 里的宽度（px）。
     *
     * <p>优先问客户端字体，精确到像素；拿不到时退化为估算
     * （汉字/全角标点 9px、ASCII 空格 4px、其余约 5px）。
     * 兜底是必要的：{@code Minecraft} 是客户端类，而 {@code ItemMedal} 属于
     * common 代码，被打包进服务端时那个类根本不存在 —— 直接引用会抛
     * {@link NoClassDefFoundError}（属于 {@code Error}，必须 catch {@code Throwable}）。
     * {@code appendHoverText} 实际上只在客户端渲染 tooltip 时被调用，
     * 所以正常情况下走的都是字体那条路。
     */
    private static double textPx(String s) {
        try {
            net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
            if (font != null) {
                return font.width(s);
            }
        } catch (Throwable ignored) {
            // 落到下面的估算
        }
        double w = 0.0D;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == ' ') {
                w += 4.0D;
            } else if (cp >= 0x2E80) {
                w += 9.0D;
            } else {
                w += 5.0D;
            }
        }
        return w;
    }

    /**
     * 单字符宽度，带缓存。
     *
     * <p>折行是逐字测量的：一屏 tooltip 累计上千次，每次都去问字体（哪怕只是
     * 一次字符串分配）就没必要了。字符集是有限且高度重复的，缓存命中率接近 100%。
     */
    private static double charPx(int cp) {
        Double cached = CHAR_W.get(cp);
        if (cached != null) {
            return cached;
        }
        double w = textPx(new String(Character.toChars(cp)));
        CHAR_W.put(cp, w);
        return w;
    }

    /**
     * 在右侧补半角空格,使整串宽度接近 {@code target}。
     *
     * <p>空格宽 4px,所以残差最多 2px —— 这是「补空白」这种手段的物理极限,
     * 肉眼已经看不出（真要 0 误差只能自己写 tooltip 渲染,代价远大于收益）。
     */
    private static String padRight(String s, double target) {
        int n = (int) Math.round((target - textPx(s)) / 4.0D);
        return n <= 0 ? s : s + " ".repeat(n);
    }

    /**
     * 区块内容行（属性 / 技能）的行首缩进。
     *
     * <p>标题单独占一行,所以缩进不需要"对齐到标题之后",只要比标题低一层即可。
     * 两个空格 = 8px。
     */
    private static final String ROW_INDENT = "  ";

    /** 属性名前缀（{@link MedalAttributeText} 只给裸键）。 */
    private static final String ATTR_KEY_PREFIX = "attr.lost_realm.";

    /** 属性一行两项时,两组之间的横向间隔（px）。 */
    private static final double GROUP_GAP_PX = 16.0D;

    /**
     * tooltip 内容区的目标最大宽度（px，不含边框内边距）。
     *
     * <p>原版 tooltip <b>不会自动折行</b>,一行有多长就画多长 —— 长描述直接顶出
     * 屏幕（这正是「按住 Shift 后 tooltip 比屏幕还大」的原因）。所以定个上限,
     * 超出的描述交给 {@link #wrapLines} 手动折行。200px 的内容区加上内边距后
     * 约 216px,能塞进最常见的窗口尺寸。
     */
    private static final double MAX_CONTENT_W = 200.0D;

    /** 描述折行时至少保留的宽度（px），避免缩进太深时挤成「一行一个字」。 */
    private static final double MIN_DESC_W = 60.0D;

    /**
     * 技能类型标记（{@code [主动]}）的颜色。
     *
     * <p>它缀在灰色的描述末尾，得能跳出来，又不能跟技能名的绿、数值的青撞。
     * 亮黄是最显眼又没被占用的那一个。
     */
    private static final ChatFormatting TAG_COLOR = ChatFormatting.YELLOW;

    /**
     * 单字符宽度缓存。
     *
     * <p>折行是逐字测量的，一屏 tooltip 累计上千次；字符集有限且高度重复，
     * 缓存下来避免每次都去问字体。
     */
    private static final Map<Integer, Double> CHAR_W = new HashMap<>();

    /**
     * 阶位标签:{@code [精锐]}。
     *
     * <p>走语言键（{@code tier.lost_realm.*}）而不是 {@link ExpTable#tierOf} 的
     * 硬编码中文 —— 后者的返回值直接拼进 tooltip 会让英文客户端看到中文阶位。
     */
    private static MutableComponent tierTag(MedalType type, int level) {
        return Component.literal("[")
                .append(Component.translatable(ExpTable.tierLangKey(type, level)))
                .append("]")
                .withStyle(ChatFormatting.DARK_GRAY);
    }

    /**
     * 熟练度数值文本:{@code 50/100}。
     *
     * <p>分子 = 当前等级内已积累的熟练度,分母 = 从本级升到下一级所需。
     * 满级时没有"下一级",显示 {@code MAX}。
     */
    private String progressText(MedalNbt nbt, int lv) {
        long cur = Math.max(0L, nbt.getExp(type) - ExpTable.totalExpForLevel(lv));
        long need = ExpTable.expToNext(lv);
        if (lv >= ExpTable.maxLevel() || need == Long.MAX_VALUE) {
            return cur + " §6MAX";
        }
        return cur + "/" + need;
    }

    /** 是否按住了 Shift（展开低阶技能的描述）。 */
    private static boolean shiftDown() {
        return keyDown(true);
    }

    /** 是否按住了 Ctrl（展开高阶技能的描述）。 */
    private static boolean ctrlDown() {
        return keyDown(false);
    }

    /**
     * 是否按住了某个「展开详情」的按键。
     *
     * <p>{@code Screen} 是<b>客户端类</b>,理论上服务端不会走到这里
     * （{@code appendHoverText} 只在客户端 GUI 渲染 tooltip 时调用）。
     * 这里仍然兜一层:若真被服务端触碰,抛的是 {@code NoClassDefFoundError}，
     * 属于 {@link Error} 而非 {@link Exception}，必须 catch {@link Throwable} 才拦得住。
     */
    private static boolean keyDown(boolean shift) {
        try {
            return shift
                    ? net.minecraft.client.gui.screens.Screen.hasShiftDown()
                    : net.minecraft.client.gui.screens.Screen.hasControlDown();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
