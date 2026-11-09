#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成十枚「契约」召唤师饰品的贴图与物品模型。

形状是一块竖长的**封魂石板**：圆角矩形、四周描边、中央刻一个符号。
九枚各自一个符号（王冠 / 横纹 / 双环 / 星爆 / 链环 / 心 / 叉骨 / 裂纹 / 箭头），
终极「王者之主」是金底王冠并多一道亮芯。
"""

import io
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_DIR = os.path.join(ROOT, 'src/main/resources/assets/lost_realm/textures/item')
MODEL_DIR = os.path.join(ROOT, 'src/main/resources/assets/lost_realm/models/item')
OUT = '/workspace/preview_contracts.png'

SLAB = """
................
..############..
.##############.
################
################
################
################
################
################
################
################
################
################
################
.##############.
..############..
"""

# 中央 8x8 符号区（左上角落在 x=4, y=4）
SYMBOLS = {
    'dominion_contract': """
........
........
.#.##.#.
.######.
.######.
........
........
........
""",
    'legion_contract': """
........
.######.
........
.######.
........
.######.
........
........
""",
    'symbiosis_contract': """
........
.##..##.
#..#..#.
#..#..#.
.##..##.
........
........
........
""",
    'martyr_contract': """
...#....
.#.#.#..
..###...
#######.
..###...
.#.#.#..
...#....
........
""",
    'soulchain_contract': """
..##....
.#..#...
.#..#...
..##....
....##..
...#..#.
...#..#.
....##..
""",
    'flesh_contract': """
........
.##..##.
########
########
.######.
..####..
...##...
........
""",
    'bone_contract': """
........
##....##
.##..##.
..####..
..####..
.##..##.
##....##
........
""",
    'fission_contract': """
....#...
...#....
..##....
.#.#....
...#....
..#.....
..#.....
........
""",
    'decree_contract': """
........
..####..
.######.
.######.
.#.##.#.
...#....
...#....
........
""",
    'overlord_contract': """
#..##..#
.#.##.#.
.######.
.######.
.######.
........
..####..
........
""",
}

# 每枚契约:(亮, 中, 暗) + 符号色
CONTRACTS = {
    'dominion_contract':  [(255, 214, 160), (176, 54, 54),  (96, 24, 28),   (255, 236, 170)],
    'legion_contract':    [(190, 214, 255), (74, 110, 180), (32, 56, 110),  (222, 236, 255)],
    'symbiosis_contract': [(190, 255, 200), (62, 170, 96),  (22, 92, 50),   (226, 255, 214)],
    'martyr_contract':    [(255, 196, 130), (216, 96, 36),  (128, 44, 12),  (255, 232, 178)],
    'soulchain_contract': [(178, 252, 236), (58, 196, 180), (20, 116, 108), (226, 255, 246)],
    'flesh_contract':     [(255, 178, 196), (190, 58, 96),  (110, 20, 48),  (255, 216, 226)],
    'bone_contract':      [(255, 252, 236), (196, 186, 158), (118, 108, 84), (255, 255, 246)],
    'fission_contract':   [(216, 178, 255), (140, 88, 214), (70, 34, 120),  (238, 216, 255)],
    'decree_contract':    [(178, 200, 255), (74, 92, 196),  (28, 36, 110),  (216, 228, 255)],
    'overlord_contract':  [(255, 248, 190), (240, 196, 62), (150, 100, 14), (255, 255, 224)],
}


def build(bright, mid, dark, glyph):
    rows = SLAB.strip('\n').split('\n')
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()

    def solid(x, y):
        return 0 <= x < 16 and 0 <= y < 16 and rows[y][x] == '#'

    for y in range(16):
        for x in range(16):
            if not solid(x, y):
                continue
            edge = not (solid(x - 1, y) and solid(x + 1, y) and solid(x, y - 1) and solid(x, y + 1))
            px[x, y] = dark if edge else mid

    # 左上高光
    for x, y in ((3, 3), (4, 3), (3, 4)):
        if solid(x, y):
            px[x, y] = bright

    # 中央符号
    for gy, line in enumerate(glyph.strip('\n').split('\n')):
        for gx, ch in enumerate(line):
            if ch == '#':
                x, y = 4 + gx, 4 + gy
                if solid(x, y):
                    px[x, y] = glyph_color

    # 终极:额外一条竖直亮芯(在符号位置再压一列亮光)
    if is_overlord:
        for y in range(5, 12):
            for x in (7, 8):
                if solid(x, y) and px[x, y] != (0, 0, 0, 0):
                    px[x, y] = (255, 255, 240, 255)
    return img


def main():
    global glyph_color, is_overlord
    tiles = []
    for name, (bright, mid, dark, gly) in CONTRACTS.items():
        glyph_color = gly
        is_overlord = (name == 'overlord_contract')
        img = build(bright, mid, dark, SYMBOLS[name])
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


glyph_color = (255, 255, 255)
is_overlord = False

if __name__ == '__main__':
    main()
