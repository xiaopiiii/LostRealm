#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把召唤师职业的全部语言键写进 zh_cn.json / en_us.json。

只做一件事:读 → 合并 → 覆盖写。已有的键保持不变(不会被改写),
新增的键追加到文件末尾。重复执行是安全的。
"""

import io
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANG = os.path.join(ROOT, 'src/main/resources/assets/lost_realm/lang')

ZH = {
    # ---- 基础 ----
    "item.lost_realm.summoner_medal": "召唤师勋章",
    "tier.lost_realm.summoner.1": "唤灵者",
    "tier.lost_realm.summoner.2": "结契者",
    "tier.lost_realm.summoner.3": "深渊召唤者",
    "curios.identifier.contract": "契约",
    "curios.slot.contract": "契约",
    "curios.modifiers.contract": "佩戴契约时:",
    "config.lost_realm.cat.summoner": "召唤师数值",
    "config.lost_realm.cat.contracts": "召唤师饰品",
    "effect.lost_realm.vulnerable": "脆弱",
    "tooltip.lost_realm.charm.require_summoner": "需要佩戴「召唤师勋章」才能放入契约槽",

    # ---- 属性名 ----
    "attr.lost_realm.servant_health": "仆从生命",
    "attr.lost_realm.servant_damage": "仆从伤害",
    "attr.lost_realm.servant_speed": "仆从速度",
    "attr.lost_realm.servant_lifespan": "仆从寿命",

    # ---- 六个节点技能 ----
    "skill.lost_realm.su5_soul_bond": "灵魂纽带",
    "skill.lost_realm.su5_soul_bond.desc":
        "仆从每次造成伤害为你补充 %s 点灵魂,同一仆从每 %s 秒最多一次。",
    "skill.lost_realm.su10_bone_hardening": "骸骨硬化",
    "skill.lost_realm.su10_bone_hardening.desc":
        "你的全部仆从获得 %s%% 额外减伤。",
    "skill.lost_realm.su15_dead_revival": "亡者复苏",
    "skill.lost_realm.su15_dead_revival.desc":
        "仆从死亡时有 %s%% 概率原地复活,并以 %s%% 的血量站起来。",
    "skill.lost_realm.su20_legion_command": "军团号令",
    "skill.lost_realm.su20_legion_command.desc":
        "号令军团 %s 秒:仆从伤害 +%s%%、召唤上限 +%s,期间仆从寿命不再流逝。冷却 %s 秒。",
    "skill.lost_realm.su25_soul_fire": "魂火",
    "skill.lost_realm.su25_soul_fire.desc":
        "仆从的每次攻击额外附带 %s 点伤害,并在命中处溅起魂火。",
    "skill.lost_realm.su30_dead_legion": "亡者大军",
    "skill.lost_realm.su30_dead_legion.desc":
        "仆从召唤上限 +%s、召唤法术冷却 -%s%%,仆从最大生命 +%s%%。",

    # ---- 九枚契约 + 终极 ----
    "item.lost_realm.dominion_contract": "统御之契",
    "item.lost_realm.dominion_contract.tip":
        "仆从最大生命 +%s%%、伤害 +%s%%;仆从击杀目标时为你补充 %s 点灵魂。",
    "item.lost_realm.legion_contract": "军团之契",
    "item.lost_realm.legion_contract.tip":
        "仆从召唤上限 +%s,寿命延长 %s%%。",
    "item.lost_realm.symbiosis_contract": "共生之契",
    "item.lost_realm.symbiosis_contract.tip":
        "你受到伤害时,%s 格内最近的仆从替你承受 %s%% 的伤害。",
    "item.lost_realm.martyr_contract": "殉爆之契",
    "item.lost_realm.martyr_contract.tip":
        "仆从死亡时爆炸,%s 格内的敌人受到相当于该仆从最大生命 %s%% 的伤害。",
    "item.lost_realm.soulchain_contract": "魂链之契",
    "item.lost_realm.soulchain_contract.tip":
        "每个存活仆从为你提供 %s%% 法术强度,最多计 %s 个。",
    "item.lost_realm.flesh_contract": "血肉之契",
    "item.lost_realm.flesh_contract.tip":
        "仆从死亡时,为你回复相当于其最大生命 %s%% 的生命。",
    "item.lost_realm.bone_contract": "骨甲之契",
    "item.lost_realm.bone_contract.tip":
        "每个存活仆从为你提供 %s%% 伤害减免,最多计 %s 个。",
    "item.lost_realm.fission_contract": "裂变之契",
    "item.lost_realm.fission_contract.tip":
        "召唤仆从时 %s%% 概率额外多召一个;多出来的那个血量与寿命只有 %s%%。",
    "item.lost_realm.decree_contract": "敕令之契",
    "item.lost_realm.decree_contract.tip":
        "仆从的攻击会给目标叠上「脆弱」:受到伤害 +%s%%,持续 %s 秒。",
    "item.lost_realm.overlord_contract": "王者之主",
    "item.lost_realm.overlord_contract.tip.1":
        "仆从最大生命 +%s%%、伤害 +%s%%、移动速度 +%s%%。",
    "item.lost_realm.overlord_contract.tip.2":
        "王者不死:你的仆从不再受寿命限制,召唤上限 +%s。",
}

# 配置项: id -> (标签, 说明, 单位或 None)
ZH_OPTS = {
    "summoner": [
        ("exp_servant_damage", "仆从伤害熟练度", "仆从每点有效伤害获得的熟练度(溢出伤害不计)", None),
        ("exp_kill_hp", "仆从击杀熟练度", "按目标最大生命值转化(中立生物减半)", None),
        ("l1_servant_health", "Lv.1 仆从生命", "Lv.1 基础:仆从最大生命加成", "%"),
        ("l1_servant_damage", "Lv.1 仆从伤害", "Lv.1 基础:仆从伤害加成", "%"),
        ("l1_servant_speed", "Lv.1 仆从速度", "Lv.1 基础:仆从移动速度加成", "%"),
        ("l1_servant_lifespan", "Lv.1 仆从寿命", "Lv.1 基础:仆从寿命延长", "%"),
        ("per_servant_health", "每级仆从生命", "每提升一级增加的仆从最大生命(可小数)", "%"),
        ("per_servant_damage", "每级仆从伤害", "每提升一级增加的仆从伤害(可小数)", "%"),
        ("per_servant_speed", "每级仆从速度", "每提升一级增加的仆从移动速度(可小数)", "%"),
        ("per_servant_lifespan", "每级仆从寿命", "每提升一级增加的仆从寿命延长(可小数)", "%"),
        ("su5_soul_per_hit", "灵魂纽带回魂", "仆从每次造成伤害为你补充的灵魂", "点"),
        ("su5_soul_cooldown", "灵魂纽带间隔", "同一仆从触发回魂的最小间隔", "tick"),
        ("su10_servant_reduction", "骸骨硬化减伤", "仆从获得的额外减伤", "%"),
        ("su15_revive_chance", "亡者复苏概率", "仆从死亡时原地复活的概率", "%"),
        ("su15_revive_health", "亡者复苏血量", "复活时恢复到的血量比例", "%"),
        ("su20_duration", "军团号令持续", "号令的持续时间", "秒"),
        ("su20_servant_damage", "军团号令增伤", "号令期间仆从的伤害加成", "%"),
        ("su20_servant_limit", "军团号令上限", "号令期间仆从召唤上限的增加量", None),
        ("su20_life_extend", "军团号令寿命", "号令期间为仆从追加的寿命", "秒"),
        ("su20_cooldown", "军团号令冷却", "号令结束后的冷却", "秒"),
        ("su25_extra_damage", "魂火附加伤害", "仆从每次攻击额外附带的伤害(可小数)", "点"),
        ("su30_servant_limit", "亡者大军上限", "仆从召唤上限的增加量", None),
        ("su30_cooldown_discount", "亡者大军冷却折扣", "召唤法术的冷却缩减", "%"),
        ("su30_servant_health", "亡者大军生命", "仆从最大生命加成", "%"),
    ],
    "contracts": [
        ("refresh_interval", "仆从刷新间隔", "把仆从属性重写一遍的间隔,越小越实时、越大越省性能", "tick"),
        ("dominion_health", "统御之契生命", "仆从最大生命加成", "%"),
        ("dominion_damage", "统御之契伤害", "仆从伤害加成", "%"),
        ("dominion_soul", "统御之契回魂", "仆从击杀目标时补充的灵魂", "点"),
        ("legion_limit", "军团之契上限", "仆从召唤上限的增加量", None),
        ("legion_life", "军团之契寿命", "仆从寿命延长", "%"),
        ("symbiosis_ratio", "共生之契转移比例", "由最近的仆从替你承受的伤害比例", "%"),
        ("symbiosis_radius", "共生之契搜寻半径", "寻找替身仆从的半径(可小数)", "格"),
        ("martyr_radius", "殉爆之契爆炸半径", "仆从死亡爆炸的作用范围(可小数)", "格"),
        ("martyr_ratio", "殉爆之契爆炸伤害", "按仆从最大生命值折算的爆炸伤害", "%"),
        ("soulchain_potency", "魂链之契单仆从强度", "每个存活仆从提供的法术强度", "%"),
        ("soulchain_max", "魂链之契计入上限", "最多计入的仆从数量", "个"),
        ("flesh_ratio", "血肉之契治疗量", "按仆从最大生命值折算的治疗量", "%"),
        ("bone_reduction", "骨甲之契单仆从减伤", "每个存活仆从提供的伤害减免", "%"),
        ("bone_max", "骨甲之契计入上限", "最多计入的仆从数量", "个"),
        ("fission_chance", "裂变之契触发概率", "召唤时额外多召一个的概率", "%"),
        ("fission_ratio", "裂变之契继承比例", "额外仆从的血量与寿命比例", "%"),
        ("decree_vulnerable", "敕令之契脆弱增伤", "目标「脆弱」期间受到的额外伤害", "%"),
        ("decree_seconds", "敕令之契脆弱持续", "「脆弱」的持续时间", "秒"),
        ("overlord_health", "王者之主生命", "仆从最大生命加成", "%"),
        ("overlord_damage", "王者之主伤害", "仆从伤害加成", "%"),
        ("overlord_speed", "王者之主速度", "仆从移动速度加成", "%"),
        ("overlord_limit", "王者之主上限", "仆从召唤上限的增加量", None),
    ],
}

EN = {
    "item.lost_realm.summoner_medal": "Summoner's Medal",
    "tier.lost_realm.summoner.1": "Spirit Caller",
    "tier.lost_realm.summoner.2": "Pact Binder",
    "tier.lost_realm.summoner.3": "Abyssal Summoner",
    "curios.identifier.contract": "Contract",
    "curios.slot.contract": "Contract",
    "curios.modifiers.contract": "When wearing a contract:",
    "config.lost_realm.cat.summoner": "Summoner Values",
    "config.lost_realm.cat.contracts": "Summoner Charms",
    "effect.lost_realm.vulnerable": "Vulnerable",
    "tooltip.lost_realm.charm.require_summoner":
        "Requires the \"Summoner's Medal\" to be equipped in a Contract slot",

    "attr.lost_realm.servant_health": "Servant Health",
    "attr.lost_realm.servant_damage": "Servant Damage",
    "attr.lost_realm.servant_speed": "Servant Speed",
    "attr.lost_realm.servant_lifespan": "Servant Lifespan",

    "skill.lost_realm.su5_soul_bond": "Soul Bond",
    "skill.lost_realm.su5_soul_bond.desc":
        "Whenever a servant deals damage, restore %s souls. At most once every %s seconds per servant.",
    "skill.lost_realm.su10_bone_hardening": "Bone Hardening",
    "skill.lost_realm.su10_bone_hardening.desc":
        "All your servants gain %s%% additional damage reduction.",
    "skill.lost_realm.su15_dead_revival": "Dead Revival",
    "skill.lost_realm.su15_dead_revival.desc":
        "When a servant dies, it has a %s%% chance to rise again at %s%% health.",
    "skill.lost_realm.su20_legion_command": "Legion Command",
    "skill.lost_realm.su20_legion_command.desc":
        "Command the legion for %s seconds: servant damage +%s%%, summon limit +%s, "
        "and servant lifespan stops draining. Cooldown %s seconds.",
    "skill.lost_realm.su25_soul_fire": "Soul Fire",
    "skill.lost_realm.su25_soul_fire.desc":
        "Each servant attack deals %s extra damage and scatters soul fire on impact.",
    "skill.lost_realm.su30_dead_legion": "Legion of the Dead",
    "skill.lost_realm.su30_dead_legion.desc":
        "Servant summon limit +%s, summon spell cooldown -%s%%, servant max health +%s%%.",

    "item.lost_realm.dominion_contract": "Contract of Dominion",
    "item.lost_realm.dominion_contract.tip":
        "Servant max health +%s%%, damage +%s%%. Servant kills restore %s souls.",
    "item.lost_realm.legion_contract": "Contract of the Legion",
    "item.lost_realm.legion_contract.tip":
        "Servant summon limit +%s, lifespan +%s%%.",
    "item.lost_realm.symbiosis_contract": "Contract of Symbiosis",
    "item.lost_realm.symbiosis_contract.tip":
        "When you take damage, the nearest servant within %s blocks absorbs %s%% of it.",
    "item.lost_realm.martyr_contract": "Contract of Martyrdom",
    "item.lost_realm.martyr_contract.tip":
        "Servants explode on death, dealing damage to enemies within %s blocks "
        "equal to %s%% of the servant's max health.",
    "item.lost_realm.soulchain_contract": "Contract of Soul Chains",
    "item.lost_realm.soulchain_contract.tip":
        "Each living servant grants %s%% spell potency (up to %s servants).",
    "item.lost_realm.flesh_contract": "Contract of Flesh",
    "item.lost_realm.flesh_contract.tip":
        "When a servant dies, heal yourself for %s%% of its max health.",
    "item.lost_realm.bone_contract": "Contract of Bone",
    "item.lost_realm.bone_contract.tip":
        "Each living servant grants %s%% damage reduction (up to %s servants).",
    "item.lost_realm.fission_contract": "Contract of Fission",
    "item.lost_realm.fission_contract.tip":
        "%s%% chance to summon one extra servant, with only %s%% health and lifespan.",
    "item.lost_realm.decree_contract": "Contract of the Decree",
    "item.lost_realm.decree_contract.tip":
        "Servant attacks inflict Vulnerable: +%s%% damage taken for %s seconds.",
    "item.lost_realm.overlord_contract": "Overlord",
    "item.lost_realm.overlord_contract.tip.1":
        "Servant max health +%s%%, damage +%s%%, movement speed +%s%%.",
    "item.lost_realm.overlord_contract.tip.2":
        "The Overlord never dies: your servants ignore lifespan entirely, summon limit +%s.",
}

EN_OPTS = {
    "summoner": [
        ("exp_servant_damage", "Servant Damage EXP", "Proficiency per point of effective servant damage", None),
        ("exp_kill_hp", "Servant Kill EXP", "Based on the victim's max health (halved for neutral mobs)", None),
        ("l1_servant_health", "Lv.1 Servant Health", "Base servant max health bonus", "%"),
        ("l1_servant_damage", "Lv.1 Servant Damage", "Base servant damage bonus", "%"),
        ("l1_servant_speed", "Lv.1 Servant Speed", "Base servant movement speed bonus", "%"),
        ("l1_servant_lifespan", "Lv.1 Servant Lifespan", "Base servant lifespan extension", "%"),
        ("per_servant_health", "Servant Health / Level", "Servant max health gained per level", "%"),
        ("per_servant_damage", "Servant Damage / Level", "Servant damage gained per level", "%"),
        ("per_servant_speed", "Servant Speed / Level", "Servant movement speed gained per level", "%"),
        ("per_servant_lifespan", "Servant Lifespan / Level", "Servant lifespan gained per level", "%"),
        ("su5_soul_per_hit", "Soul Bond Souls", "Souls restored per servant hit", "souls"),
        ("su5_soul_cooldown", "Soul Bond Interval", "Minimum interval per servant", "ticks"),
        ("su10_servant_reduction", "Bone Hardening", "Extra damage reduction for servants", "%"),
        ("su15_revive_chance", "Dead Revival Chance", "Chance to revive a dying servant", "%"),
        ("su15_revive_health", "Dead Revival Health", "Health ratio after revival", "%"),
        ("su20_duration", "Legion Command Duration", "How long the command lasts", "s"),
        ("su20_servant_damage", "Legion Command Damage", "Servant damage bonus during the command", "%"),
        ("su20_servant_limit", "Legion Command Limit", "Extra servant summon limit", None),
        ("su20_life_extend", "Legion Command Lifespan", "Lifespan added to servants", "s"),
        ("su20_cooldown", "Legion Command Cooldown", "Cooldown after the command ends", "s"),
        ("su25_extra_damage", "Soul Fire Damage", "Extra damage per servant attack", "dmg"),
        ("su30_servant_limit", "Legion of the Dead Limit", "Extra servant summon limit", None),
        ("su30_cooldown_discount", "Legion of the Dead Cooldown", "Summon spell cooldown reduction", "%"),
        ("su30_servant_health", "Legion of the Dead Health", "Servant max health bonus", "%"),
    ],
    "contracts": [
        ("refresh_interval", "Servant Refresh Interval", "How often servant stats are rewritten", "ticks"),
        ("dominion_health", "Dominion Health", "Servant max health bonus", "%"),
        ("dominion_damage", "Dominion Damage", "Servant damage bonus", "%"),
        ("dominion_soul", "Dominion Souls", "Souls restored on servant kills", "souls"),
        ("legion_limit", "Legion Limit", "Extra servant summon limit", None),
        ("legion_life", "Legion Lifespan", "Servant lifespan extension", "%"),
        ("symbiosis_ratio", "Symbiosis Ratio", "Portion of damage absorbed by the nearest servant", "%"),
        ("symbiosis_radius", "Symbiosis Radius", "Search radius for the decoy servant", "blocks"),
        ("martyr_radius", "Martyr Explosion Radius", "Blast radius on servant death", "blocks"),
        ("martyr_ratio", "Martyr Explosion Damage", "Damage as a ratio of servant max health", "%"),
        ("soulchain_potency", "Soul Chains Potency", "Spell potency per living servant", "%"),
        ("soulchain_max", "Soul Chains Cap", "Maximum servants counted", "servants"),
        ("flesh_ratio", "Flesh Healing", "Healing as a ratio of servant max health", "%"),
        ("bone_reduction", "Bone Reduction", "Damage reduction per living servant", "%"),
        ("bone_max", "Bone Cap", "Maximum servants counted", "servants"),
        ("fission_chance", "Fission Chance", "Chance to summon an extra servant", "%"),
        ("fission_ratio", "Fission Inheritance", "Health and lifespan ratio of the extra servant", "%"),
        ("decree_vulnerable", "Decree Vulnerable", "Extra damage taken while Vulnerable", "%"),
        ("decree_seconds", "Decree Duration", "Duration of Vulnerable", "s"),
        ("overlord_health", "Overlord Health", "Servant max health bonus", "%"),
        ("overlord_damage", "Overlord Damage", "Servant damage bonus", "%"),
        ("overlord_speed", "Overlord Speed", "Servant movement speed bonus", "%"),
        ("overlord_limit", "Overlord Limit", "Extra servant summon limit", None),
    ],
}


def opt_entries(table):
    out = {}
    for group, items in table.items():
        for key, label, desc, unit in items:
            out["config.lost_realm.opt." + key] = label
            out["config.lost_realm.opt." + key + ".desc"] = desc
            if unit:
                out["config.lost_realm.opt." + key + ".unit"] = unit
    return out


def merge(name, extra):
    path = os.path.join(LANG, name)
    with io.open(path, encoding='utf-8') as f:
        data = json.load(f)
    added = 0
    for k, v in extra.items():
        if k not in data:
            added += 1
        data[k] = v
    with io.open(path, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write('\n')
    print(name, 'total', len(data), 'added', added)


def main():
    merge('zh_cn.json', {**ZH, **opt_entries(ZH_OPTS)})
    merge('en_us.json', {**EN, **opt_entries(EN_OPTS)})


if __name__ == '__main__':
    main()
