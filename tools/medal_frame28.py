#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""28×28 圆形勋章底座 + 16×16 物品内嵌。

尺寸为什么是 28（而不是 24 或 32）：
  圆形底座的环宽 = 画布半径 - 物品外接圆半径（16×16 是 11.31）。
  24 → 环只剩 0.69px，画不出边；32 → 环 4.7px，但物品只占 50%，显得被挤。
  28 → 环 2.8px，物品占 57%，两头都能接受。
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pngtool

N = 28
C = (N - 1) / 2.0          # 13.5
R_OUT = 13.6
R_IN = 11.4                # 略大于 16×16 的外接圆 11.31，四角绝不裁

OUTLINE = (0x13, 0x0d, 0x16)
RIM = [(0x5c, 0x58, 0x6a), (0x92, 0x8d, 0xa2), (0xc4, 0xbf, 0xd2), (0xef, 0xea, 0xf8)]
FACE = (0x26, 0x20, 0x30)
INNER = (0x18, 0x13, 0x1f)


def base_frame():
    img = [[(0, 0, 0, 0)] * N for _ in range(N)]
    for y in range(N):
        for x in range(N):
            dx, dy = x - C, y - C
            r = math.hypot(dx, dy)
            if r > R_OUT:
                continue
            ang = math.atan2(dy, dx)
            lig = max(0.0, math.cos(ang + 0.75 * math.pi)) ** 0.75

            if r > R_OUT - 0.95:                      # 外描边
                col = OUTLINE
            elif r > R_IN + 1.15:                     # 金属环主体（受光/背光）
                if lig > 0.42:
                    col = RIM[3]
                elif lig > 0.16:
                    col = RIM[2]
                elif lig > 0.02:
                    col = RIM[1]
                else:
                    col = RIM[0]
            elif r > R_IN + 0.45:                     # 环内缘的暗线
                col = RIM[0]
            elif r > R_IN:                            # 内阴影
                col = INNER
            else:                                     # 底面
                col = FACE if r < R_IN * 0.72 else INNER
            img[y][x] = col + (255,)
    return img


def embed(obj16):
    img = base_frame()
    ox = oy = (N - 16) // 2            # 28-16=12 -> 6
    for y in range(16):
        for x in range(16):
            px = obj16[y][x]
            if px[3] > 8:
                img[oy + y][ox + x] = px
    for x in range(16):                # 物品下缘投影
        if obj16[15][x][3] > 8:
            img[oy + 16][ox + x] = INNER + (255,)
    return img


def sheet(tiles, k=10, pad=6, bg=(22, 24, 30, 255)):
    tw = N * k
    W = pad + len(tiles) * (tw + pad)
    H = tw + pad * 2
    canvas = [[bg] * W for _ in range(H)]
    x0 = pad
    for t in tiles:
        big = pngtool.scale(t, k)
        for y in range(tw):
            for i in range(tw):
                canvas[pad + y][x0 + i] = big[y][i]
        x0 += tw + pad
    return canvas


if __name__ == '__main__':
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    TEX = os.path.join(root, 'src/main/resources/assets/lost_realm/textures/item')
    SRC = os.environ.get('MEDAL_SRC') or os.path.join(root, '..', '..', 'tmp_cc0', 'content16')
    out = []
    for c in ('archer', 'blade', 'tank', 'mage', 'summoner'):
        obj = pngtool.read_png(os.path.join(SRC, f'{c}.png'))
        assert len(obj) == 16 and len(obj[0]) == 16, (c, len(obj[0]), len(obj))
        framed = embed(obj)
        out.append(framed)
        pngtool.write_png(os.path.join(TEX, f'{c}_medal.png'), framed)
        print(f'{c:9s} 28×28 色数={pngtool.palette_size(framed)}')
    pngtool.write_png(os.path.join(root, '..', '..', 'tmp_cc0', 'framed28.png'), sheet(out))
    print('预览 -> tmp_cc0/framed28.png')
