#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""勋章 tooltip 离线预览。

复刻 ItemMedal#appendSkillRows 的排版逻辑（缩进 / 补空格对齐 / 按像素折行），
把四枚勋章的「节点技能」区块渲染成图片，用来在构建前肉眼确认：

  · 技能名是否还在（折行时最容易丢的就是它）
  · 每段描述是否挂在正确的技能名下
  · 法师的 Shift / Ctrl 分段与其余三枚的「Shift 全展」是否正确

它只做「排版」，不含真实字体表 —— 宽度用 Noto Sans CJK 实测（与游戏内
9px 字号的像素宽度同量级），因此折行位置可能与游戏里有 ±1 字的偏差，
但结构问题一眼可见。
"""

import io
import json
import os
import re

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANG = os.path.join(ROOT, 'src/main/resources/assets/lost_realm/lang/zh_cn.json')
OUT = '/workspace/preview_medal_tooltip.png'

FONT_PATH = '/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc'
FONT = ImageFont.truetype(FONT_PATH, 10)

# ---- 与 ItemMedal 保持一致的常量 ----
ROW_INDENT = '  '
MAX_CONTENT_W = 200.0
MIN_DESC_W = 60.0
LINE_H = 12
PAD = 5

# ---- MC 配色 ----
C_GOLD = (255, 170, 0)
C_AQUA = (85, 255, 255)
C_YELLOW = (255, 255, 85)
C_GREEN = (85, 255, 85)
C_GRAY = (170, 170, 170)
C_DGRAY = (85, 85, 85)
C_BLUE = (85, 85, 255)
C_PINK = (255, 85, 255)
C_WHITE = (255, 255, 255)
BG = (16, 0, 16)
BORDER = (80, 0, 200)

# ---- 技能表：与 SkillRegistry 顺序一致 ----
# (等级, 名称键尾段, 描述键尾段, 是否主动)
MEDALS = {
    '射手勋章': [
        (5, 's5_dodge', False),
        (10, 's10_charge_up', False),
        (15, 's15_weakness_insight', False),
        (20, 's30_gale_storm', True),
        (25, 's25_arrow_rain', False),
        (30, 's20_arrow_volley', False),
    ],
    '战士勋章': [
        (5, 'w5_rend', False),
        (10, 'w10_fight_harder', False),
        (15, 'w15_burning_self', True),
        (20, 'w20_blade_of_frost', False),
        (25, 'w25_war_trail', True),
        (30, 'w30_blood_intent', False),
    ],
    '坦克勋章': [
        (5, 't5_heavy_strike', False),
        (10, 't10_offensive_drain', False),
        (15, 't15_star_shield', False),
        (20, 't20_unyielding_bulwark', True),
        (25, 't25_block', False),
        (30, 't30_endless_life', False),
    ],
    '法师勋章': [
        (5, 'm5_elemental_resonance', False),
        (10, 'm10_spell_crit', False),
        (15, 'm15_soul_siphon', False),
        (20, 'm20_witch_overload', True),
        (25, 'm25_soul_burn', True),
        (30, 'm30_barrage_storm', False),
    ],
}

ACTIVE_TAG = '[主动]'
BAD_START = '，。、；：！？）】》」』%,.;:!?)]}'


def text_px(s):
    return FONT.getlength(s)


def pad_right(s, target):
    n = int(round((target - text_px(s)) / 4.0))
    return s if n <= 0 else s + ' ' * n


def wrap(text, first_w, rest_w):
    out = []
    cur = ''
    w = 0.0
    at_first = True
    for ch in text:
        cw = text_px(ch)
        if w + cw > (first_w if at_first else rest_w) and cur and ch not in BAD_START:
            out.append(cur)
            cur = ''
            w = 0.0
            at_first = False
        cur += ch
        w += cw
    if cur:
        out.append(cur)
    return out


def demo(text):
    """把 %s / %% 换成演示值，模拟 Component(format) 的结果。"""
    text = text.replace('%%', '\x00').replace('%s', '15').replace('%s', '15')
    return text.replace('\x00', '%')


def load_lang():
    with io.open(LANG, encoding='utf-8') as f:
        return json.load(f)


def build_rows(lang, skills, lv, detail_levels, split_half, key_prefix=''):
    """返回 [(text, color, is_first_of_skill)] —— 复刻 appendSkillRows。"""
    rows = []
    lv_w = max(text_px('Lv.%d' % s[0]) for s in skills)
    names = []
    for lvl, key, _active in skills:
        names.append('[' + demo(lang['skill.lost_realm.' + key]) + ']')
    name_w = max(text_px(n) for n in names)

    for idx, (lvl, key, active) in enumerate(skills):
        name = names[idx]
        lv_label = pad_right('Lv.%d' % lvl, lv_w) + ' '
        row_head = ROW_INDENT + lv_label + pad_right(name, name_w)
        unlocked = lv >= lvl
        skill_color = C_GOLD if unlocked else C_DGRAY
        desc_color = C_GRAY if unlocked else C_DGRAY
        detail = (idx < split_half) if split_half is not None else True
        detail = detail and (lvl in detail_levels)

        if not detail:
            rows.append((row_head, skill_color, True))
            continue

        desc = demo(lang['skill.lost_realm.' + key + '.desc'])
        head_w = text_px(ROW_INDENT) + text_px(lv_label) + text_px(name) + 4.0
        first_w = max(MIN_DESC_W, MAX_CONTENT_W - head_w)
        rest_w = max(MIN_DESC_W, MAX_CONTENT_W - text_px(ROW_INDENT))
        lines = wrap(desc, first_w, rest_w)
        if not lines:
            rows.append((row_head, skill_color, True))
            continue

        tag = ACTIVE_TAG if active else ''
        last = lines[-1]
        tag_own = False
        if tag:
            limit = first_w if len(lines) == 1 else rest_w
            if text_px(last + ' ' + tag) <= limit:
                last = last + ' ' + tag
            else:
                tag_own = True

        # ★ 首行接着技能名
        rows.append((row_head + ' ' + lines[0], desc_color, True))
        for mid in lines[1:-1]:
            rows.append((ROW_INDENT + mid, desc_color, False))
        if len(lines) > 1:
            rows.append((ROW_INDENT + last, desc_color, False))
        if tag_own:
            rows.append((ROW_INDENT + tag, C_YELLOW, False))
    return rows


def render(medal_name, rows, hint, font):
    w = int(MAX_CONTENT_W + text_px(ROW_INDENT) + 70)
    h = len(rows) * LINE_H + 4 * LINE_H + 2 * PAD
    img = Image.new('RGB', (w, h), BG)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, w - 1, h - 1], outline=BORDER)

    y = PAD
    d.text((PAD, y), medal_name, font=font, fill=C_GOLD)
    y += LINE_H
    d.text((PAD, y), '熟练度 55/100 Lv.12', font=font, fill=C_AQUA)
    y += LINE_H
    d.text((PAD, y), '属性加成:', font=font, fill=C_YELLOW)
    y += LINE_H
    d.text((PAD, y), ROW_INDENT + '远程伤害   +19%     ' + '拉弓速度   +16%', font=font, fill=C_BLUE)
    y += LINE_H
    d.text((PAD, y), '节点技能:', font=font, fill=C_YELLOW)
    y += LINE_H
    for text, color, _first in rows:
        d.text((PAD, y), text, font=font, fill=color)
        y += LINE_H
    d.text((PAD, y), hint, font=font, fill=C_PINK)
    return img


def main():
    lang = load_lang()
    font = FONT
    panels = []
    for medal, skills in MEDALS.items():
        is_mage = medal == '法师勋章'
        # 未按键：只有名字
        rows_closed = build_rows(lang, skills, 12, set(), 3 if is_mage else None)
        if is_mage:
            rows_low = build_rows(lang, skills, 12, {5, 10, 15}, 3)
            rows_high = build_rows(lang, skills, 12, {20, 25, 30}, 3)
            panels.append(render(medal + '（未按键）', rows_closed, '按住 Shift 查看 Lv.5~15 技能', font))
            panels.append(render(medal + '（Shift）', rows_low, '按住 Ctrl 查看 Lv.20~30 技能', font))
            panels.append(render(medal + '（Ctrl）', rows_high, '按住 Shift 查看 Lv.5~15 技能', font))
        else:
            rows_all = build_rows(lang, skills, 12, {5, 10, 15, 20, 25, 30}, None)
            panels.append(render(medal + '（未按键）', rows_closed, '按住 Shift 查看技能详情', font))
            panels.append(render(medal + '（Shift 展开）', rows_all, '(已展开全部技能)', font))

    # 拼图：两列
    cols = 2
    col_w = max(p.width for p in panels) + 12
    row_h = []
    for i in range(0, len(panels), cols):
        row_h.append(max(p.height for p in panels[i:i + cols]) + 12)
    W = cols * col_w
    H = sum(row_h)
    canvas = Image.new('RGB', (W, H), (0, 0, 0))
    y = 0
    for r, rh in enumerate(row_h):
        x = 0
        for p in panels[r * cols:(r + 1) * cols]:
            canvas.paste(p, (x, y))
            x += col_w
        y += rh
    canvas.save(OUT)
    print('saved', OUT, canvas.size)


if __name__ == '__main__':
    main()
