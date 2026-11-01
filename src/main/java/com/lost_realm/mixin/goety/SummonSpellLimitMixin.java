package com.lost_realm.mixin.goety;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.Polarice3.Goety.common.magic.SummonSpell;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import com.lost_realm.compat.goety.ServantLimit;

/**
 * 让「召唤法术」认本模组的仆从上限加成。
 *
 * <h2>Goety 的上限是怎么判的</h2>
 * {@code SummonSpell#conditionsMet} 里做了一件事：
 * <pre>
 *   count(附近同类仆从) >= this.summonLimit()  →  拒绝召唤
 * </pre>
 * 但 {@code summonLimit()} 是<b>每个法术各自覆写</b>的（25 个子类，如
 * {@code ZombieSpell} 覆写成 32、{@code WraithSpell} 覆写成 16…）。
 * 想把 25 个覆写点都改掉是不现实的 —— 而且 Goety 一更新就会失效。
 *
 * <p>所以这里改的是<b>唯一的那处比较</b>：既然 25 个子类的上限最终都在
 * 同一个方法里被读一次，那就 Redirect 这次读取，返回值统一加上本模组的加成。
 * 覆写多少个类都不影响。
 *
 * <h2>@Pseudo 与 require = 0</h2>
 * 本类直接引用 Goety 的类，而 Goety 对本模组是<b>可选依赖</b>。
 * {@code @Pseudo} 让 Mixin 在目标类不存在时安静跳过；
 * {@code require = 0} 让 Goety 改了内部实现（找不到那个调用点）时也只报警告，
 * 而不是把游戏打崩。少了这点加成就少了吧，不该连累玩家进不了游戏。
 *
 * <h2>为什么是 remap = false</h2>
 * Goety 的 jar 是<b>未经 ForgeGradle 反混淆</b>直接放进 {@code libs/} 的
 * （见 build.gradle 里那段 "files(...) dependencies are not deobfuscated" 警告）。
 * 也就是说，它在运行时的类名 / 方法名就是现在写的这一套原始名，
 * 没有 SRG 中间层，因此这里<b>不能</b>让注解处理器去做映射查找 ——
 * 去做反而会报 "Unable to locate obfuscation mapping"。
 */
@Pseudo
@Mixin(SummonSpell.class)
public abstract class SummonSpellLimitMixin {

    @Redirect(
            method = "conditionsMet",
            require = 0,
            remap = false,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lcom/Polarice3/Goety/common/magic/SummonSpell;summonLimit()I"))
    private int lostrealm$summonLimit(SummonSpell self, ServerLevel level, LivingEntity caster) {
        return self.summonLimit() + ServantLimit.bonusFor(caster);
    }
}
