#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成十枚「晶石」法师饰品的贴图与物品模型。

形状是一颗上下出尖的菱形宝石：边缘加深、内部填中色、左上打一点高光。
九合一的主宰晶石额外在正中嵌一条亮芯 —— 像一块正在发光的核心。
"""

import io
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_DIR = os.path.join(ROOT, 'src/main/resources/assets/lost_realm/textures/item')
MODEL_DIR = os.path.join(ROOT, 'src/main/resources/assets/lost_realm/models/item')
OUT = '/workspace/preview_mage_crystals.png'

SHAPE = """
.......##.......
......####......
......####......
.....######.....
....########....
....########....
...##########...
..############..
..############..
.##############.
.##############.
..############..
...##########...
....########....
.....######.....
......####......
"""

# 每枚晶石:(亮, 中, 暗)
CRYSTALS = {
    'chain_crystal':   [(180, 226, 255), (86, 156, 238),  (34, 84, 162)],   # 连锁:电蓝
    'sniper_crystal':  [(226, 234, 244), (150, 166, 190), (84, 96, 118)],   # 狙击:银灰
    'circle_crystal':  [(160, 245, 232), (56, 186, 172),  (20, 110, 104)],  # 法阵:青玉
    'soulfire_crystal':[(150, 240, 226), (58, 200, 214),  (22, 116, 142)],  # 魂燃:魂青
    'overdraft_crystal':[(255, 150, 150), (206, 62, 70),  (128, 22, 34)],   # 透支:血红
    'ember_crystal':   [(255, 196, 130), (232, 128, 48),  (158, 66, 18)],   # 余烬:橙
    'hexnail_crystal': [(198, 160, 250), (126, 82, 206),  (64, 34, 122)],   # 咒钉:暗紫
    'echo_crystal':    [(186, 252, 240), (74, 214, 196),  (26, 138, 128)],  # 回响:声波青
    'swift_crystal':   [(220, 244, 255), (128, 190, 240), (52, 108, 174)],  # 疾行:亮蓝
    'sovereign_crystal':[(255, 244, 180), (240, 196, 62), (166, 112, 16)],  # 主宰:金
}

CORE = (255, 255, 255, 255)


def build(bright, mid, dark, core):
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

    for x, y in ((6, 4), (7, 4), (6, 5), (7, 5)):
        if rows[y][x] == '#':
            px[x, y] = bright

    if core:
        # 中央一条竖直亮芯 —— 九合一才有
        for y in range(6, 12):
            for x in (7, 8):
                if rows[y][x] == '#':
                    px[x, y] = CORE
        for y in (5, 12):
            for x in (7, 8):
                if rows[y][x] == '#':
                    px[x, y] = bright
    return img


def main():
    tiles = []
    for name, (bright, mid, dark) in CRYSTALS.items():
        img = build(bright, mid, dark, core=(name == 'sovereign_crystal'))
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
