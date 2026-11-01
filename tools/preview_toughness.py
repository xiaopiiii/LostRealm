#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把【坚韧之刃】的减伤曲线画出来，方便直接看数值合不合理。

四档生命对应四条阶梯线 —— 同样的「每次 -5%」，血量越低天花板越高，
但要爬到顶需要挨更多下（这是设计上刻意留的：血越少，你得越能熬）。
"""

from PIL import Image, ImageDraw, ImageFont

FONT = '/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc'
OUT = '/workspace/preview_toughness_curve.png'

STEP = 5          # 每次递减
TIERS = [         # (标签, 上限, 颜色)
    ('剩余 >75%',  15, (150, 156, 166)),
    ('剩余 50~75%', 20, (108, 178, 214)),
    ('剩余 25~50%', 25, (226, 176, 72)),
    ('剩余 <25%',  30, (222, 86, 74)),
]
HITS = 8

W, H = 940, 470
L, R, T, B = 130, 880, 92, 372
YMAX = 35

img = Image.new('RGB', (W, H), (22, 22, 26))
d = ImageDraw.Draw(img)
f = lambda s: ImageFont.truetype(FONT, s)
f_title, f_sub, f_lab, f_small = f(23), f(14), f(14), f(13)

d.text((40, 26), '坚韧之刃 — 减伤曲线', font=f_title, fill=(240, 214, 120))
d.text((40, 60), '同一个敌人连续命中时逐次递减；5 秒内没再挨它的打则重新计数',
       font=f_sub, fill=(150, 154, 162))

def px(i):   # 第 i 次受击（1 起）
    return L + (i - 1) * (R - L) / (HITS - 1)

def py(v):
    return B - v / YMAX * (B - T)

# 网格与刻度
for v in range(0, YMAX + 1, 5):
    y = py(v)
    d.line([(L, y), (R, y)], fill=(48, 48, 56), width=1)
    d.text((L - 46, y - 8), '%d%%' % v, font=f_small, fill=(150, 154, 162))
for i in range(1, HITS + 1):
    x = px(i)
    d.text((x - 6, B + 10), str(i), font=f_small, fill=(150, 154, 162))
d.line([(L, B), (R, B)], fill=(96, 96, 106), width=2)
d.line([(L, T), (L, B)], fill=(96, 96, 106), width=2)
d.text(((L + R) / 2 - 42, B + 36), '连续受击次数', font=f_lab, fill=(196, 200, 208))

# 四条曲线
for label, cap, color in TIERS:
    pts = []
    for i in range(1, HITS + 1):
        v = min(cap, (i - 1) * STEP)
        pts.append((px(i), py(v)))
    d.line(pts, fill=color, width=3, joint='curve')
    for i, (x, y) in enumerate(pts, start=1):
        v = min(cap, (i - 1) * STEP)
        d.ellipse([x - 4, y - 4, x + 4, y + 4], fill=color)
        if v == cap and (i == 1 or min(cap, (i - 2) * STEP) != cap):
            d.text((x + 8, y - 20), '%d%%' % v, font=f_small, fill=color)

# 图例
lx, ly = L + 10, T + 6
for label, cap, color in TIERS:
    d.rectangle([lx, ly + 4, lx + 22, ly + 10], fill=color)
    d.text((lx + 30, ly - 2), '%s（上限 %d%%）' % (label, cap), font=f_lab, fill=(206, 210, 216))
    ly += 24

# 脚注
d.text((40, H - 34), '第 1 下永远全额，第 2 下起每次 -5%；上限随血量损失每 25% 抬高 5%',
       font=f_small, fill=(140, 144, 152))

img.save(OUT)
print(OUT)
