#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""24×24 圆角方形勋章底座 + 16×16 物品内嵌。

为什么不是圆形：24×24 的圆半径 12，而 16×16 方形的外接圆半径是 11.31，
圆环只剩 0.69px —— 画不出边框。圆角方形才能在 24 的边长里塞下 16×16
并且留出 4px 的边（1px 描边 + 2px 金属 + 1px 金线）。

为什么物品不缩放：16×16 是这个模组所有物品图标的原生尺寸，
放大/缩小都会破坏像素台阶。24×24 里物品占 2/3，比 32×32 时的 1/2 更醒目。
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pngtool

N = 24
INSET = 4                  # 物品左上角，也是边框宽度
CORNER = 7                 # 圆角半径（加大，才有徽章感）
RING = 2                   # 金属环宽度

OUTLINE = (0x14, 0x0f, 0x18)
RIM = [(0x42, 0x3e, 0x4f), (0x74, 0x70, 0x84), (0x9e, 0x99, 0xae), (0xc8, 0xc3, 0xd6)]
GOLD = [(0x6f, 0x50, 0x1c), (0xa8, 0x82, 0x38)]
FACE = (0x28, 0x22, 0x33)
RIVET = (0xf4, 0xf0, 0xfa)


def round_rect_dist(x, y, r):
    """到圆角矩形边缘的距离（内部为负）。"""
    lo, hi = r, N - 1 - r
    dx = max(lo - x, 0, x - hi)
    dy = max(lo - y, 0, y - hi)
    return math.hypot(dx, dy) - r


def base_frame():
    img = [[(0, 0, 0, 0)] * N for _ in range(N)]
    for y in range(N):
        for x in range(N):
            d = round_rect_dist(x, y, CORNER)
            if d > 0:
                continue
            # 光源左上：用 (x+y) 同时表达左上亮度（方形不适合按角度算）
            lig = 1.0 - (x * 0.55 + y * 0.45) / (N * 1.0)
            lig = max(0.0, min(1.0, lig * 1.45))

            if -1.0 < d <= 0.0:                       # 最外一圈描边
                col = OUTLINE
            elif -2.2 < d <= -1.0:                    # 金属外沿（受光面）
                col = RIM[2] if lig > 0.55 else (RIM[1] if lig > 0.2 else RIM[0])
            elif -3.4 < d <= -2.2:                    # 金属内侧
                col = RIM[1] if lig > 0.45 else RIM[0]
            elif -4.0 < d <= -3.4:                    # 内阴影（凹槽边）
                col = (0x1c, 0x17, 0x24)
            else:                                     # 底面
                col = FACE
            img[y][x] = col + (255,)

    return img


def embed(obj16):
    img = base_frame()
    ox = oy = INSET
    for y in range(16):
        for x in range(16):
            px = obj16[y][x]
            if px[3] > 8:
                img[oy + y][ox + x] = px
    # 物品下缘投影：嵌进凹槽的层次
    for x in range(16):
        if obj16[15][x][3] > 8:
            img[oy + 16][ox + x] = (0x18, 0x13, 0x20, 255)
    return img


def sheet(tiles, k=12, pad=6, bg=(22, 24, 30, 255)):
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
        print(f'{c:9s} 24×24 色数={pngtool.palette_size(framed)}')
    pngtool.write_png(os.path.join(root, '..', '..', 'tmp_cc0', 'framed24.png'), sheet(out))
    print('预览 -> tmp_cc0/framed24.png')
