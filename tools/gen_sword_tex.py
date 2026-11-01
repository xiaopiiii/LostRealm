#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成战士终极「我有一剑！」的贴图与物品模型。

画的是一把 45° 斜放的**宽刃金剑**：刃身比原版钻石剑厚一档（三像素宽，
上缘亮、中间中金、下缘暗），根部压一道深金护手，往下是短柄。
比普通剑「大一号」是刻意的 —— 它是九把「刃」合出来的终极武器，
在快捷栏里该一眼认出来。
"""

import io
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_DIR = os.path.join(ROOT, 'src/main/resources/assets/lost_realm/textures/item')
MODEL_DIR = os.path.join(ROOT, 'src/main/resources/assets/lost_realm/models/item')
NAME = 'i_have_a_sword'
OUT = '/workspace/preview_i_have_a_sword.png'

# 刃身：上缘亮、中间中金、下缘暗 —— 三档拉出金属的立体感
BLADE_LIGHT = (255, 248, 202, 255)
BLADE_MID = (240, 196, 64, 255)
BLADE_EDGE = (158, 106, 20, 255)
# 护手：比刃更深，压住刃根
GUARD = (206, 156, 36, 255)
GUARD_DARK = (118, 82, 16, 255)
# 剑柄：暗褐木
GRIP = (86, 62, 36, 255)
GRIP_DARK = (48, 34, 20, 255)
# 柄尾配重：一点亮金收尾
POMMEL = (255, 226, 130, 255)

SIZE = 16


def build():
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()

    def put(x, y, color):
        if 0 <= x < SIZE and 0 <= y < SIZE:
            px[x, y] = color

    # ---- 剑刃：从剑尖 (13, 1) 沿 ↙ 方向到根部 (4, 10) ----
    for t in range(10):
        x = 13 - t
        y = 1 + t
        put(x, y, BLADE_LIGHT)        # 刃背上缘：高光
        put(x, y + 1, BLADE_MID)      # 刃身
        put(x - 1, y + 1, BLADE_EDGE)  # 刃口下缘：暗
        # 剑尖再补一格，避免尖头显得被削平
        if t == 0:
            put(x + 1, y, BLADE_LIGHT)

    # ---- 护手：垂直于刃身，过根部 (4, 10) 沿 ↘ 展开 ----
    for k in range(-3, 4):
        put(4 + k, 10 + k, GUARD if k % 2 == 0 else GUARD_DARK)

    # ---- 剑柄：从 (3, 11) 沿 ↙ 收下去，两像素宽 ----
    for t in range(3):
        x = 3 - t
        y = 11 + t
        put(x, y, GRIP)
        put(x + 1, y, GRIP_DARK)

    # ---- 柄尾配重 ----
    put(0, 14, POMMEL)
    put(1, 13, GRIP_DARK)
    return img


def main():
    img = build()
    img.save(os.path.join(TEX_DIR, NAME + '.png'))
    with io.open(os.path.join(MODEL_DIR, NAME + '.json'), 'w', encoding='utf-8') as f:
        json.dump({'parent': 'minecraft:item/handheld',
                   'textures': {'layer0': 'lost_realm:item/' + NAME}}, f, indent=2)
        f.write('\n')
    print('wrote', NAME)

    img.resize((SIZE * 12, SIZE * 12), Image.NEAREST).convert('RGB').save(OUT)
    print('preview', OUT)


if __name__ == '__main__':
    main()
