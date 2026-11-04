package com.lost_realm.client;

import com.lost_realm.sound.ModSounds;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/**
 * 乌蠹诺斯战斗曲的播放实例。
 *
 * <h2>为什么继承 {@link AbstractSoundInstance} 而不是直接 playSound</h2>
 * 一次性音效用 {@code level.playLocalSound} 就够了，但 BGM 需要三件事：
 * <ul>
 *   <li><b>循环</b> —— {@code looping = true}，战斗打多久都不会停；</li>
 *   <li><b>淡入淡出</b> —— 直接开播会"啪"地砸进来，靠 {@link #volume} 每 tick 平滑逼近；</li>
 *   <li><b>可随时停止</b> —— 持有实例引用才能调 {@code SoundManager#stop}。</li>
 * </ul>
 *
 * <p>{@code relative = false}：这是背景音乐，不该跟着玩家位置移动 ——
 * 位置类音效才需要 relative。
 */
public class UdunosBattleMusic extends AbstractSoundInstance {

    /** 满音量。MUSIC 音源本身还受玩家「音乐」滑块控制，这里只给相对值。 */
    public static final float MAX_VOLUME = 0.55F;

    /** 低于这个值就当"已经没声音了"，可以安全 stop。 */
    private static final float SILENT = 0.001F;

    /** 是否后半段（阶段Ⅲ / Ⅳ）。 */
    private final boolean late;

    public UdunosBattleMusic(boolean late) {
        super((late ? ModSounds.UDUNOS_BATTLE_2 : ModSounds.UDUNOS_BATTLE_1).get(),
                SoundSource.MUSIC, SoundInstance.createUnseededRandom());
        this.late = late;
        // 循环：曲子结尾是渐弱到静音，接回开头（也是静音起步）天然无缝
        this.looping = true;
        // 不受距离衰减
        this.relative = false;
        // ★ attenuation 必须显式设成 NONE。
        //    AbstractSoundInstance 的默认值是 LINEAR，而我们的 x/y/z 是 (0,0,0)：
        //    SoundEngine 会把"玩家到世界原点"当成距离去衰减 ——
        //    竞技场离原点几百格，音量直接被算成 0，
        //    表现就是"BGM 完全不响"，而且日志里一句报错都没有。
        //    之前只把 volume 从 0.01 起抬，解决的是"source 不被创建"，
        //    跟这个衰减是两回事，两个坑都得填。
        this.attenuation = SoundInstance.Attenuation.NONE;
        // ★ 不能从 0.0 起。
        //   SoundEngine#play 里会算一遍 calculateVolume，
        //   结果为 0 时压根不会创建 OpenAL source —— 之后再把 volume 推上去，
        //   也只是在改一个不存在的声道的字段，永远没有声音。
        //   玩家实测"没有音乐、音量全开"就是这个。
        //   取 0.01（约 -40dB，听不见）既能通过判断，淡入也不会"啪"地一下。
        this.volume = 0.01F;
    }

    /**
     * 音量上推一步。
     *
     * <p>音量字段是 {@code protected}（{@link AbstractSoundInstance} 的设计），
     * 外部只能通过这类方法间接改 —— 所以淡入淡出的状态机留在这个类里，
     * 控制器只负责"叫它涨"或"叫它跌"。
     */
    public void raise(float step) {
        this.volume = Math.min(MAX_VOLUME, this.volume + step);
    }

    /** 音量下调一步。 */
    public void lower(float step) {
        this.volume = Math.max(0.0F, this.volume - step);
    }

    /** 这一份是后半段吗 —— 控制器据此判断要不要在换阶段时切曲。 */
    public boolean isLate() {
        return this.late;
    }

    /** 是否已经完全静音（可以 stop 了）。 */
    public boolean isSilent() {
        return this.volume <= SILENT;
    }
}
