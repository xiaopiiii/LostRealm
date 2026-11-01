#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把战士终极「我有一剑！」的语言键写进 zh_cn.json / en_us.json。

与 patch_summoner_lang.py 同一套做法:读 → 合并 → 覆盖写,重复执行安全。
"""

import io
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANG = os.path.join(ROOT, 'src/main/resources/assets/lost_realm/lang')

ZH = {
    'config.lost_realm.cat.sword': '我有一剑！',
    'item.lost_realm.i_have_a_sword': '我有一剑！',
    'item.lost_realm.i_have_a_sword.tip.attr.1': '暴击率 +%s%%、暴击伤害 +%s%%',
    'item.lost_realm.i_have_a_sword.tip.attr.2': '攻击伤害 +%s%%、经验获取 +%s%%',
    'item.lost_realm.i_have_a_sword.tip.skill.1':
        '【剑气横扫】手持此剑,按住 Shift + 右键蓄力 %s 秒,满蓄自动斩出;中途松手即收剑。',
    'item.lost_realm.i_have_a_sword.tip.skill.2':
        '一道巨大的剑气横扫而出:前方 %s 格内的敌人各受 %s 点伤害,沿途方块尽数崩碎(不产生掉落物)。',
}

EN = {
    'config.lost_realm.cat.sword': 'I Have a Sword!',
    'item.lost_realm.i_have_a_sword': 'I Have a Sword!',
    'item.lost_realm.i_have_a_sword.tip.attr.1': 'Crit Chance +%s%%, Crit Damage +%s%%',
    'item.lost_realm.i_have_a_sword.tip.attr.2': 'Attack Damage +%s%%, Experience Gained +%s%%',
    'item.lost_realm.i_have_a_sword.tip.skill.1':
        '[Sword Sweep] Hold this sword and press Shift + Right-click to charge for %s seconds. '
        'It is released automatically when fully charged, or sheathed if you let go early.',
    'item.lost_realm.i_have_a_sword.tip.skill.2':
        'A colossal blade of sword-qi sweeps out: every enemy within %s blocks takes %s damage, '
        'and every block along its path is shattered (no drops).',
}

# id -> (中文标签, 中文说明, 单位|None)
ZH_OPTS = [
    ('sword_crit_chance', '暴击率加成', '手持这把剑时获得的暴击率(100 = 刀刀暴击)', '%'),
    ('sword_crit_damage', '暴击伤害加成', '手持时获得的暴击伤害(100 = 翻倍)', '%'),
    ('sword_attack_damage', '攻击伤害加成', '手持时获得的攻击伤害(100 = 翻倍)', '%'),
    ('sword_exp_gained', '经验获取加成', '手持时获得经验的比例(1000 = ×11)', '%'),
    ('sword_slash_damage', '剑气伤害', '剑气对每个敌人各造成的伤害,不随距离衰减', '点'),
    ('sword_charge_seconds', '蓄力时长', '满蓄后自动斩出', '秒'),
    ('sword_cooldown_seconds', '斩出冷却', '0 表示只靠蓄力限制', '秒'),
    ('sword_reach', '剑气射程', '剑气从身前扫出的距离', '格'),
    ('sword_width', '剑气宽度', '剑气的横向宽度', '格'),
    ('sword_height', '剑气高度', '剑气覆盖的高度', '格'),
    ('sword_anim_ticks', '剑气飞行时长', '纯视觉表现,不影响伤害与破坏的结算时点', 'tick'),
    ('sword_break_blocks', '破坏方块', '是否破坏剑气路径上的方块。硬度为负的(基岩等)始终保留', None),
    ('sword_hit_players', '斩击玩家', '是否连同玩家一起斩。默认关闭,免得误伤队友', None),
]

EN_OPTS = [
    ('sword_crit_chance', 'Crit Chance', 'Crit chance granted while holding this sword (100 = always crit)', '%'),
    ('sword_crit_damage', 'Crit Damage', 'Crit damage granted while holding (100 = doubled)', '%'),
    ('sword_attack_damage', 'Attack Damage', 'Attack damage granted while holding (100 = doubled)', '%'),
    ('sword_exp_gained', 'Experience Gained', 'Experience multiplier while holding (1000 = x11)', '%'),
    ('sword_slash_damage', 'Sword-Qi Damage', 'Damage dealt to each enemy, not reduced by distance', 'dmg'),
    ('sword_charge_seconds', 'Charge Time', 'Releases automatically when fully charged', 's'),
    ('sword_cooldown_seconds', 'Cooldown', '0 means the charge time is the only limit', 's'),
    ('sword_reach', 'Sweep Reach', 'How far the sword-qi sweeps forward', 'blocks'),
    ('sword_width', 'Sweep Width', 'Lateral width of the sword-qi', 'blocks'),
    ('sword_height', 'Sweep Height', 'How tall the sword-qi is', 'blocks'),
    ('sword_anim_ticks', 'Sweep Flight Time', 'Purely visual; damage and block breaking resolve instantly', 'ticks'),
    ('sword_break_blocks', 'Break Blocks', 'Whether blocks along the path are destroyed. Negative-hardness blocks always stay', None),
    ('sword_hit_players', 'Hit Players', 'Whether the sweep also hits players. Off by default to avoid friendly fire', None),
]


def entries(table):
    out = {}
    for key, label, desc, unit in table:
        out['config.lost_realm.opt.' + key] = label
        out['config.lost_realm.opt.' + key + '.desc'] = desc
        if unit:
            out['config.lost_realm.opt.' + key + '.unit'] = unit
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
    merge('zh_cn.json', {**ZH, **entries(ZH_OPTS)})
    merge('en_us.json', {**EN, **entries(EN_OPTS)})


if __name__ == '__main__':
    main()
