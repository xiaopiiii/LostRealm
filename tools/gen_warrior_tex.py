#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成八枚「刃」类战士饰品的贴图与物品模型。

画法:一把竖直的短剑 —— 剑身用各枚的主题色(边缘加深、左上点高光),
护手与剑柄固定用木革色,这样八枚摆在一起像同一套锻造出来的东西。
"""

import io
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_DIR = os.path.join(ROOT, 'src/main/resources/assets/lost_realm/textures/item')
MODEL_DIR = os.path.join(ROOT, 'src/main/resources/assets/lost_realm/models/item')
PREVIEW = '/workspace/preview_warrior_blades.png'

# '#' = 剑身(主题色)   '+' = 护手/剑柄(固定木革色)
BLADE = """
................
.......##.......
......####......
......####......
......####......
......####......
......####......
......####......
....######......
..###++++###....
......++........
......++........
......++........
....######......
....######......
................
"""

WOOD_DARK = (92, 68, 42, 255)
WOOD = (124, 94, 58, 255)

# 每枚:(亮, 中, 暗)
BLADES = {
    'sweeping_blade':    [(226, 238, 248), (186, 202, 220), (128, 146, 168)],
    'dual_blade':        [(255, 234, 150), (238, 200, 92), (176, 138, 44)],
    'crit_blade':        [(255, 130, 120), (222, 72, 66), (150, 36, 34)],
    'backstab_blade':    [(200, 160, 240), (162, 116, 212), (108, 70, 152)],
    'disarm_blade':      [(255, 196, 120), (234, 148, 58), (168, 96, 30)],
    'luck_blade':        [(150, 250, 170), (86, 208, 110), (36, 138, 62)],
    'toughness_blade':   [(198, 132, 98), (152, 88, 60), (94, 50, 34)],
    'charging_blade':    [(150, 196, 255), (88, 148, 242), (42, 92, 170)],
    'dash_blade':        [(240, 250, 255), (198, 224, 240), (132, 164, 186)],
}

HILIGHT = (255, 255, 255, 255)
BAD_CHARS = '+-'


def build(bright, mid, dark):
    rows = [r for r in BLADE.strip('\n').split('\n')]
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()

    def kind(x, y):
        if 0 <= x < 16 and 0 <= y < 16:
            return rows[y][x]
        return '.'

    for y in range(16):
        for x in range(16):
            ch = rows[y][x]
            if ch not in '#+':
                continue
            if ch == '+':
                edge = kind(x - 1, y) not in '+#' or kind(x + 1, y) not in '+#'
                px[x, y] = WOOD_DARK if edge else WOOD
                continue
            # 剑身:4 邻域有非剑身 → 边缘(深),否则中色
            edge = not all(kind(x + dx, y + dy) == '#' for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)))
            px[x, y] = dark if edge else mid

    # 左上一小段高光,让平面像素有一点金属感
    for x, y in ((6, 2), (6, 3), (7, 4)):
        if kind(x, y) == '#':
            px[x, y] = HILIGHT
    for x, y in ((7, 12), (7, 13)):
        if kind(x, y) == '#':
            px[x, y] = bright
    return img


def main():
    tiles = []
    for name, (bright, mid, dark) in BLADES.items():
        img = build(bright, mid, dark)
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
    canvas = Image.new('RGBA', (w, h), (26, 26, 30, 255))
    x = pad
    for t in tiles:
        canvas.paste(t.resize((16 * scale, 16 * scale), Image.NEAREST), (x, pad))
        x += 16 * scale + pad
    canvas.convert('RGB').save(PREVIEW)
    print('preview', PREVIEW)


if __name__ == '__main__':
    main()
