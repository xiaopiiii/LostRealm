package com.lost_realm.compat.goety;

import java.util.Set;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import com.lost_realm.config.MedalsConfig;
import com.lost_realm.data.MedalSource;
import com.lost_realm.event.SummonerState;
import com.lost_realm.item.ContractCharmType;
import com.lost_realm.skill.MedalType;

/**
 * 召唤师对「仆从召唤上限」的加成。
 *
 * <h2>为什么需要它、又为什么放在兼容层</h2>
 * Goety 的上限判定散在<b>它自己的两个地方</b>（法术召唤 {@code SummonSpell#conditionsMet}
 * 与仪式召唤 {@code RitualRequirements#canSummon}），本模组只能靠 Mixin
 * 在那两处回调进来问一句「这个玩家能多召几个」。
 *
 * <p>Mixin 是<b>运行在 Goety 类内部</b>的代码，一旦本模组这边抛异常，
 * 崩的就是 Goety 的召唤流程。所以这个方法做了三重保守：
 * <ol>
 *   <li>只接受玩家，其余实体一律 0；</li>
 *   <li>整段 try/catch，任何异常都退化成「没有任何加成」；</li>
 *   <li>不触碰 Goety 的类 —— 答这个问题只需要读本模组的配置与 Curios 饰品。</li>
 * </ol>
 *
 * <h2>加成的四个来源</h2>
 * <table border="1">
 *   <tr><th>来源</th><th>条件</th></tr>
 *   <tr><td>Lv.40【军团号令】</td><td>技能处于激活期间（主动技能，有时限）</td></tr>
 *   <tr><td>Lv.60【亡者大军】</td><td>解锁即常驻</td></tr>
 *   <tr><td>军团之契</td><td>佩戴该契约</td></tr>
 *   <tr><td>王者之主</td><td>佩戴终极契约</td></tr>
 * </table>
 */
public final class ServantLimit {

    /**
     * 该实体作为召唤者时，仆从上限的额外增量。
     *
     * @return 增量（≥0）；任何异常或非玩家都返回 0
     */
    public static int bonusFor(LivingEntity caster) {
        try {
            if (!(caster instanceof Player player)) {
                return 0;
            }
            int bonus = 0;
            int level = MedalSource.level(player, MedalType.SUMMONER);

            // 军团号令:主动技能,只在其持续期间给上限
            if (level >= 40 && SummonerState.commandActive(player)) {
                bonus += MedalsConfig.S20_SERVANT_LIMIT.get();
            }
            // 亡者大军:被动,解锁即常驻
            if (level >= 60) {
                bonus += MedalsConfig.S30_SERVANT_LIMIT.get();
            }

            Set<ContractCharmType> charms = ContractCharmType.activeSet(player);
            if (charms.contains(ContractCharmType.LEGION)) {
                bonus += MedalsConfig.CONTRACT_LEGION_LIMIT.get();
            }
            if (charms.contains(ContractCharmType.OVERLORD)) {
                bonus += MedalsConfig.CONTRACT_OVERLORD_LIMIT.get();
            }
            return Math.max(0, bonus);
        } catch (Throwable t) {
            return 0;
        }
    }

    private ServantLimit() {}
}
