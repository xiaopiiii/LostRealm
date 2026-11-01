#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成十面「盾」的贴图与物品模型。

形状是一面经典的三角盾：上宽下尖。每面只换主题色（边缘加深 + 左上高光），
九合一的不朽神盾额外在正中央嵌一颗亮色宝石 —— 一眼就能看出它跟别的不一样。
"""

import io
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_DIR = os.path.join(ROOT, 'src/main/resources/assets/lost_realm/textures/item')
MODEL_DIR = os.path.join(ROOT, 'src/main/resources/assets/lost_realm/models/item')
OUT = '/workspace/preview_tank_shields.png'

SHAPE = """
..############..
.##############.
################
################
################
################
################
################
.##############.
..############..
...##########...
....########....
.....##..##.....
......####......
.......##.......
................
"""

# 每面盾:(亮, 中, 暗)
SHIELDS = {
    'thorns_shield':    [(150, 232, 160), (58, 158, 84),  (26, 92, 50)],    # 荆棘:青绿
    'adaptive_shield':  [(196, 214, 232), (118, 146, 178), (62, 84, 112)],  # 适应:灰蓝
    'shockwave_shield': [(255, 216, 130), (232, 160, 54),  (158, 92, 22)],  # 震荡:橙
    'sacrifice_shield': [(248, 138, 132), (198, 56, 56),   (124, 24, 28)],  # 牺牲:血红
    'earth_shield':     [(214, 186, 138), (152, 118, 70),  (96, 68, 36)],   # 大地:土褐
    'devouring_shield': [(186, 150, 240), (118, 76, 190),  (62, 34, 116)],  # 吞噬:暗紫
    'nourish_shield':   [(255, 236, 186), (226, 190, 106), (158, 118, 48)], # 反哺:麦黄
    'vitality_shield':  [(140, 250, 170), (74, 202, 104),  (28, 128, 62)],  # 生机:翠绿
    'vanguard_shield':  [(196, 234, 255), (108, 170, 226), (46, 96, 152)],  # 先锋:亮蓝
    'eternal_shield':   [(255, 240, 168), (238, 190, 60),  (162, 106, 18)], # 不朽:金
}

GEM = (255, 255, 255, 255)


def build(bright, mid, dark, gem):
    rows = SHAPE.strip('\n').split('\n')
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()

    def solid(x, y):
        return 0 <= x < 16 and 0 <= y < 16 and rows[y][x] == '#'

    for y in range(16):
        for x in range(16):
            if rows[y][x] != '#':
                continue
            edge = not (solid(x - 1, y) and solid(x + 1, y) and solid(x, y - 1) and solid(x, y + 1))
            px[x, y] = dark if edge else mid

    # 左上一点高光,让平面像素有点金属感
    for x, y in ((3, 2), (4, 2), (3, 3), (4, 3)):
        if rows[y][x] == '#':
            px[x, y] = bright

    if gem:
        # 正中间嵌一颗宝石(菱形),只有九合一才有
        for x, y in ((7, 6), (8, 6), (6, 7), (7, 7), (8, 7), (9, 7),
                     (7, 8), (8, 8), (7, 9), (8, 9)):
            if rows[y][x] == '#':
                px[x, y] = GEM
        for x, y in ((7, 5), (8, 5), (6, 6), (9, 6), (6, 8), (9, 8), (7, 10), (8, 10)):
            if rows[y][x] == '#':
                px[x, y] = bright
    return img


def main():
    tiles = []
    for name, (bright, mid, dark) in SHIELDS.items():
        img = build(bright, mid, dark, gem=(name == 'eternal_shield'))
        img.save(os.path.join(TEX_DIR, name + '.png'))
        with io.open(os.path.join(MODEL_DIR, name + '.json'), 'w', encoding='utf-8') as f:
            json.dump({'parent': 'minecraft:item/generated',
                       'textures': {'layer0': 'lost_realm:item/' + name}}, f, indent=2)
            f.write('\n')
        tiles.append(img)
        print('wrote', name)

    scale, pad = 6, 8
    w = len(tiles) * (16 * scale + pad) + pad
    h = 16 * scale + pad * 2
    canvas = Image.new('RGBA', (w, h), (24, 24, 28, 255))
    x = pad
    for t in tiles:
        canvas.paste(t.resize((16 * scale, 16 * scale), Image.NEAREST), (x, pad))
        x += 16 * scale + pad
    canvas.convert('RGB').save(OUT)
    print('preview', OUT)


if __name__ == '__main__':
    main()
