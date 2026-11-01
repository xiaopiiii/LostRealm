#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""为新增的箭饰品生成贴图与物品模型。

做法：以 potion_arrow 为**形状模板**（它同时含箭杆、箭羽、箭头与高光），
只把「箭头主题色」那一组像素按每枚箭的配色重绘，杆/羽/高光原样保留 ——
这样五张新贴图与既有的几枚看起来是同一套东西，只是箭头换了颜色。
"""

import io
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_DIR = os.path.join(ROOT, 'src/main/resources/assets/lost_realm/textures/item')
MODEL_DIR = os.path.join(ROOT, 'src/main/resources/assets/lost_realm/models/item')
TEMPLATE = os.path.join(TEX_DIR, 'potion_arrow.png')
OUT_PREVIEW = '/workspace/preview_new_arrows.png'

# 模板里「主题色」的两个档位（亮 / 暗），以及要原样保留的其它颜色
THEME_BRIGHT = (74, 176, 78)
THEME_DARK = (58, 142, 62)
KEEP = {(206, 206, 206), (96, 72, 44), (122, 92, 58)}

# 每枚新箭：(亮色, 暗色)
ARROWS = {
    'thunder_arrow': [(255, 240, 120), (200, 165, 40)],
    'twin_arrow': [(255, 178, 122), (214, 96, 40)],
    'armor_pierce_arrow': [(214, 224, 236), (120, 138, 160)],
    'hold_arrow': [(160, 240, 255), (72, 158, 200)],
    'summon_arrow': [(238, 205, 152), (150, 116, 74)],
}

MODEL = {
    'parent': 'minecraft:item/generated',
    'textures': {'layer0': None},   # 运行时填充
}


def recolor(template, bright, dark):
    out = template.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            rgb = (r, g, b)
            if rgb in KEEP:
                continue
            if rgb == THEME_BRIGHT:
                px[x, y] = (*bright, a)
            elif rgb == THEME_DARK:
                px[x, y] = (*dark, a)
    return out


def write_model(name):
    path = os.path.join(MODEL_DIR, name + '.json')
    data = {'parent': 'minecraft:item/generated',
            'textures': {'layer0': 'lost_realm:item/' + name}}
    with io.open(path, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write('\n')


def main():
    template = Image.open(TEMPLATE).convert('RGBA')
    tiles = []
    for name, (bright, dark) in ARROWS.items():
        img = recolor(template, bright, dark)
        img.save(os.path.join(TEX_DIR, name + '.png'))
        write_model(name)
        tiles.append((name, img))
        print('wrote', name)

    # 拼一张放大预览（每张 16x16 -> 96x96）
    scale = 6
    pad = 10
    w = len(tiles) * (16 * scale + pad) + pad
    h = 16 * scale + pad * 2 + 14
    canvas = Image.new('RGBA', (w, h), (24, 24, 28, 255))
    x = pad
    for _name, img in tiles:
        canvas.paste(img.resize((16 * scale, 16 * scale), Image.NEAREST), (x, pad))
        x += 16 * scale + pad
    canvas.convert('RGB').save(OUT_PREVIEW)
    print('preview', OUT_PREVIEW)


if __name__ == '__main__':
    main()
