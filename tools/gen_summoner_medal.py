#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成召唤师勋章的贴图(summoner_medal.png)。

沿用四枚既有勋章的**同一套形状**:顶部窄、中部鼓、底部收尖的徽章轮廓,
外圈描边、左上打高光。只有配色换成亡灵系的幽绿 —— 四枚勋章本就靠颜色区分,
形状一致反而让它们在饰品栏里成为一眼可辨的一组。
"""

import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'src/main/resources/assets/lost_realm/textures/item/summoner_medal.png')
PREVIEW = '/workspace/preview_summoner_medal.png'

SHAPE = """\
................
.......###......
...##########...
..############..
..############..
..############..
..############..
..############..
..############..
..############..
...##########...
...##########...
....########....
......####......
.......##.......
................"""

# 与 mage_medal 同构:中色 / 描边 / 高光 / 点缀
MID = (62, 148, 116, 255)     # 幽绿主体
DARK = (26, 74, 60, 255)      # 更深的描边
LIGHT = (168, 255, 214, 255)  # 高光(魂火绿)
ACCENT = (120, 240, 190, 255) # 中央点缀


def main():
    rows = SHAPE.split('\n')
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()

    def on(x, y):
        return 0 <= x < 16 and 0 <= y < 16 and rows[y][x] == '#'

    for y in range(16):
        for x in range(16):
            if not on(x, y):
                continue
            edge = not (on(x - 1, y) and on(x + 1, y) and on(x, y - 1) and on(x, y + 1))
            px[x, y] = DARK if edge else MID

    # 左上高光
    for x, y in ((4, 3), (5, 3), (4, 4), (6, 2), (7, 2)):
        if on(x, y):
            px[x, y] = LIGHT

    # 中央一点魂火点缀(与其余勋章的做法一致:不是完整图案,只是一处亮点)
    for x, y in ((7, 6), (8, 6), (7, 7), (8, 7)):
        if on(x, y):
            px[x, y] = ACCENT

    img.save(OUT)
    print('wrote', OUT)

    img.resize((16 * 10, 16 * 10), Image.NEAREST).convert('RGB').save(PREVIEW)
    print('preview', PREVIEW)


if __name__ == '__main__':
    main()
