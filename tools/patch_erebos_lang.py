#!/usr/bin/env python3
"""把晦星之主·厄瑞玻斯（Erebos）的语言键写进中英语言文件。

独立成脚本而不是手改 JSON，是为了保证：
  1. 中英两份的键集合完全一致（少一个英文客户端就会漏显示）；
  2. 键按分组插入，不会打乱文件里原有的顺序（语言文件是人工维护的，
     顺序一乱 diff 就没法看了）。
"""
import collections
import io
import json

ZH = 'src/main/resources/assets/lost_realm/lang/zh_cn.json'
EN = 'src/main/resources/assets/lost_realm/lang/en_us.json'

ZH_ENTRIES = {
    'entity.lost_realm.erebos': '晦星之主·厄瑞玻斯',
    'entity.lost_realm.erebos_part': '厄瑞玻斯·躯节',
    'item.lost_realm.darkstar_beacon': '黯星信标',
    'item.lost_realm.darkstar_beacon.tip': '右键使用，呼唤晦星之主降临。',
    'item.lost_realm.darkstar_beacon.tip.2': '它拥有 %s 点生命，四度蜕变，一次比一次难缠。',
    'message.lost_realm.erebos.already_present': '晦星之主已经在此地了。',
    'boss.lost_realm.erebos.phase.1': '噬光',
    'boss.lost_realm.erebos.phase.2': '引星',
    'boss.lost_realm.erebos.phase.3': '囚星',
    'boss.lost_realm.erebos.phase.4': '崩星',
    'move.lost_realm.erebos.charge': '噬光冲锋',
    'move.lost_realm.erebos.scatter': '碎星播撒',
    'move.lost_realm.erebos.hunt': '星弧巡猎',
    'move.lost_realm.erebos.gate': '星门贯穿',
    'move.lost_realm.erebos.gravity': '引力残星',
    'move.lost_realm.erebos.cross': '裂星十字核',
    'move.lost_realm.erebos.ring': '囚星天环',
    'move.lost_realm.erebos.tail_laser': '尾刃刻痕',
    'move.lost_realm.erebos.pulse': '星核脉冲',
    'move.lost_realm.erebos.meteor': '逆轨流星',
    'move.lost_realm.erebos.judgement': '崩星审判',
    'move.lost_realm.erebos.corridor': '裂界回廊',
    'move.lost_realm.erebos.scar': '延迟星痕',
    'item.lost_realm.starfall_greatsword': '断轨·裂星巨剑',
    'item.lost_realm.hushlight_railgun': '寂光·终末轨炮',
    'item.lost_realm.collapsar_eye': '坍星·湮灭之眼',
    'item.lost_realm.terminal_dragonpact': '终星·龙王之契',
    'item.lost_realm.bulwark_of_the_last_light': '末光壁垒',
    'item.lost_realm.event_horizon': '事件视界',
    'item.lost_realm.event_horizon.tip': '见证过晦星之主陨落的人，才配戴它。',
    'item.lost_realm.darkstar_beacon.tip.3': '打倒它的人会得到五件东西 —— 每个职业各一件。',
    'item.lost_realm.relic.legend': '晦星遗物',
    'item.lost_realm.starfall_greatsword.tip': '挥击时向四周迸发星陨，对 %s 格内的其它敌人造成 %s 点伤害。',
    'item.lost_realm.hushlight_railgun.tip': '无需蓄力，箭矢穿透多名敌人，且有 %s%% 概率不消耗弹药。',
    'item.lost_realm.collapsar_eye.tip': '右键放置引力核心（最多 %s 个），持续把 %s 格内的敌人拽向中心并周期性粉碎。对巨型生物只造成伤害，无法牵引。',
    'item.lost_realm.terminal_dragonpact.tip': '召唤一条只忠于你的星龙。',
    'item.lost_realm.bulwark_of_the_last_light.tip': '举盾时获得极高的全方位减伤与行动速度 —— 挡得住的不只是正面。',
}

EN_ENTRIES = {
    'entity.lost_realm.erebos': 'Erebos, Lord of the Dim Stars',
    'entity.lost_realm.erebos_part': 'Erebos Segment',
    'item.lost_realm.darkstar_beacon': 'Darkstar Beacon',
    'item.lost_realm.darkstar_beacon.tip': 'Use to call down the Lord of the Dim Stars.',
    'item.lost_realm.darkstar_beacon.tip.2': 'It has %s health and four phases, each worse than the last.',
    'message.lost_realm.erebos.already_present': 'Erebos already walks here.',
    'boss.lost_realm.erebos.phase.1': 'Lightdevour',
    'boss.lost_realm.erebos.phase.2': 'Starward',
    'boss.lost_realm.erebos.phase.3': 'Starjail',
    'boss.lost_realm.erebos.phase.4': 'Starfall',
    'move.lost_realm.erebos.charge': 'Lightdevouring Charge',
    'move.lost_realm.erebos.scatter': 'Starseed Scatter',
    'move.lost_realm.erebos.hunt': 'Star Arc Hunt',
    'move.lost_realm.erebos.gate': 'Stargate Piercer',
    'move.lost_realm.erebos.gravity': 'Gravity Remnant',
    'move.lost_realm.erebos.cross': 'Star-Splitting Cross',
    'move.lost_realm.erebos.ring': 'Starjail Halo',
    'move.lost_realm.erebos.tail_laser': 'Tail-Edge Score',
    'move.lost_realm.erebos.pulse': 'Core Pulse',
    'move.lost_realm.erebos.meteor': 'Reverse-Orbit Meteors',
    'move.lost_realm.erebos.judgement': 'Starfall Judgement',
    'move.lost_realm.erebos.corridor': 'Rift Corridor',
    'move.lost_realm.erebos.scar': 'Delayed Starscars',
    'item.lost_realm.starfall_greatsword': 'Orbitbreak, Star-Rending Greatsword',
    'item.lost_realm.hushlight_railgun': 'Hushlight, Terminal Railgun',
    'item.lost_realm.collapsar_eye': 'Collapsar, Eye of Annihilation',
    'item.lost_realm.terminal_dragonpact': 'Terminal Star, Dragon Pact',
    'item.lost_realm.bulwark_of_the_last_light': 'Bulwark of the Last Light',
    'item.lost_realm.event_horizon': 'Event Horizon',
    'item.lost_realm.event_horizon.tip': 'Only those who watched the Lord of the Dim Stars fall may wear this.',
    'item.lost_realm.darkstar_beacon.tip.3': 'Slaying it yields five weapons - one for every calling.',
    'item.lost_realm.relic.legend': 'Relic of the Dim Stars',
    'item.lost_realm.starfall_greatsword.tip': 'Each swing scatters starfall, dealing %s damage to other enemies within %s blocks.',
    'item.lost_realm.hushlight_railgun.tip': 'No charge-up. Arrows pierce multiple foes, and %s%% of shots cost no ammo.',
    'item.lost_realm.collapsar_eye.tip': 'Right-click to place a gravity core (up to %s). Pulls enemies within %s blocks toward the centre and periodically crushes them. Colossal creatures take damage but cannot be dragged.',
    'item.lost_realm.terminal_dragonpact.tip': 'Summons a star dragon bound to you alone.',
    'item.lost_realm.bulwark_of_the_last_light.tip': 'While raised, grants overwhelming all-round damage reduction and speed - it guards far more than the front.',
}


def load(path):
    with io.open(path, encoding='utf-8') as f:
        return json.load(f, object_pairs_hook=collections.OrderedDict)


def save(path, data):
    with io.open(path, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write('\n')


def patch(path, entries):
    data = load(path)
    added = 0
    changed = 0
    for key, value in entries.items():
        if key not in data:
            added += 1
        elif data[key] != value:
            changed += 1
        data[key] = value
    save(path, data)
    return added, changed, len(data)


for path, entries in ((ZH, ZH_ENTRIES), (EN, EN_ENTRIES)):
    a, c, total = patch(path, entries)
    print('%-52s 新增 %d / 更新 %d / 共 %d 键' % (path, a, c, total))

zh = load(ZH)
en = load(EN)
only_zh = sorted(set(zh) - set(en))
only_en = sorted(set(en) - set(zh))
print('中英键集合一致:', not only_zh and not only_en)
if only_zh:
    print('  仅中文:', only_zh[:10])
if only_en:
    print('  仅英文:', only_en[:10])
