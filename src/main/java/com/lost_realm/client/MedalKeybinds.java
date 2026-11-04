package com.lost_realm.client;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.lost_realm.Medals;
import com.lost_realm.network.MedalsNetwork;
import com.lost_realm.network.PacketActivateSkill;
import com.lost_realm.skill.MedalType;
import com.lost_realm.skill.Skill;
import com.lost_realm.skill.SkillRegistry;

/**
 * 主动技能按键绑定(默认 R 键)。
 *
 * <p>按下后,依次尝试激活玩家当前已佩戴勋章中「已解锁且冷却完毕」的主动技能。
 */
@Mod.EventBusSubscriber(modid = Medals.MODID, value = Dist.CLIENT)
public class MedalKeybinds {

    public static final KeyMapping ACTIVATE = new KeyMapping(
            "key.lost_realm.activate",
            GLFW.GLFW_KEY_R,
            "key.categories.lost_realm");

    /**
     * 打开「状态效果 HUD 编辑界面」。
     *
     * <p>默认绑定 <b>右 Alt</b> —— 该键在原版无默认功能,且不与常用模组冲突。
     */
    public static final KeyMapping EFFECT_HUD_EDITOR = new KeyMapping(
            "key.lost_realm.effect_hud_editor",
            GLFW.GLFW_KEY_RIGHT_ALT,
            "key.categories.lost_realm");

    /**
     * 打开「可视化配置界面」(全部配置项的图形化编辑)。
     *
     * <p>默认绑定 <b>右 Ctrl</b>。也可从「模组列表 → Lost Realm → 配置」进入。
     */
    public static final KeyMapping CONFIG_SCREEN = new KeyMapping(
            "key.lost_realm.config_screen",
            GLFW.GLFW_KEY_RIGHT_CONTROL,
            "key.categories.lost_realm");

    /**
     * 打开「可视化配置界面」的<b>第二个</b>快捷键(默认 <b>G</b>)。
     *
     * <h2>为什么还要一个</h2>
     * 右 Ctrl 在不少整合包里会被输入法、ModernUI 或其它模组抢掉,
     * 而它本身又是个"修饰键",按下去没有任何反馈,玩家很容易以为
     * 「这模组根本没有配置界面」。普通字母键没有这些问题。
     *
     * <p>两个键注册成两个独立的 {@link KeyMapping} 而不是"一个键位两个默认值":
     * 这样玩家在「选项 → 控制」里可以分别改、分别解绑,互不影响。
     */
    public static final KeyMapping CONFIG_SCREEN_ALT = new KeyMapping(
            "key.lost_realm.config_screen_alt",
            GLFW.GLFW_KEY_G,
            "key.categories.lost_realm");

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(ACTIVATE);
        event.register(EFFECT_HUD_EDITOR);
        event.register(CONFIG_SCREEN);
        event.register(CONFIG_SCREEN_ALT);
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (MedalKeybinds.ACTIVATE.consumeClick()) {
            activateAvailableSkill();
        }
        // 编辑界面:仅在未打开任何界面时进入,避免与游戏内操作打架
        if (MedalKeybinds.EFFECT_HUD_EDITOR.consumeClick()) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.screen == null) {
                com.lost_realm.client.effect.EffectHudEditorScreen.open();
            }
        }
        // 可视化配置界面(右 Ctrl 或 G,两个键位等价)
        if (MedalKeybinds.CONFIG_SCREEN.consumeClick()
                || MedalKeybinds.CONFIG_SCREEN_ALT.consumeClick()) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.screen == null) {
                com.lost_realm.client.config.ConfigScreen.open(null);
            }
        }
    }

    /** 找到第一个可用主动技能并请求服务端激活。 */
    private static void activateAvailableSkill() {
        for (MedalType type : MedalType.values()) {
            if (!ClientMedalCache.has(type)) {
                continue;
            }
            int lv = ClientMedalCache.level(type);
            for (Skill skill : SkillRegistry.of(type)) {
                if (skill.isActive() && lv >= skill.unlockLevel()) {
                    MedalsNetwork.sendToServer(new PacketActivateSkill(type.id(), skill.id()));
                    return;
                }
            }
        }
    }
}
