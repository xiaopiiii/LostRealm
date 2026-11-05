#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 16×16 的物品图标嵌进 32×32 的金属勋章底座。

为什么不缩放物品：16×16 的徽标放大到 22 以上，最近邻会出现粗细不均的
台阶；缩小又会丢细节。所以底座做到刚好能装下 16×16 ——
方形外接圆半径 11.31，内圈取 11.8 就正好一点不裁。

32×32 的好处：底座有 3px 的宽度画金属环 + 金内圈 + 铆钉，
而 16×16 只能画 1px 边框，撑不起"勋章"的形。
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pngtool

N = 32
C = 15.5
R_OUT = 15.2      # 外缘
R_RIM = 11.9      # 金圈外缘
R_IN = 11.3       # 金圈内缘（= 16×16 外接圆 11.31，物品刚好不被裁角）

OUTLINE = (0x12, 0x0d, 0x14)
RIM = [(0x55, 0x51, 0x62), (0x95, 0x90, 0xa4), (0xc6, 0xc1, 0xd3), (0xf0, 0xec, 0xf8)]
GOLD = [(0x8a, 0x63, 0x22), (0xcb, 0x9f, 0x41), (0xf7, 0xd6, 0x74)]
FACE = [(0x20, 0x1b, 0x2a), (0x2c, 0x25, 0x37), (0x3a, 0x31, 0x48)]
RIVET = (0xf0, 0xec, 0xf8)


def snap(c, ramp):
    return min(ramp, key=lambda r: (r[0] - c[0]) ** 2 + (r[1] - c[1]) ** 2 + (r[2] - c[2]) ** 2)


def base_frame():
    img = [[(0, 0, 0, 0)] * N for _ in range(N)]
    for y in range(N):
        for x in range(N):
            dx, dy = x - C, y - C
            r = math.hypot(dx, dy)
            if r > R_OUT:
                continue
            ang = math.atan2(dy, dx)
            lig = max(0.0, math.cos(ang + 0.75 * math.pi)) ** 0.8

            if r > R_RIM:                                # 金属外环（3.3px，四阶）
                if lig > 0.62:
                    col = RIM[3]
                elif lig > 0.30:
                    col = RIM[2]
                elif lig > 0.06:
                    col = RIM[1]
                else:
                    col = RIM[0]
            elif r > R_IN:                               # 金圈（很窄，但靠亮度抢眼）
                col = GOLD[2] if lig > 0.20 else (GOLD[1] if lig < 0.02 else GOLD[1])
                if lig < 0.02:
                    col = GOLD[0]
            else:                                        # 底面：外沿暗、往里略亮
                col = FACE[2] if r < R_IN * 0.5 else (FACE[1] if r < R_IN * 0.82 else FACE[0])

            # 外缘描边
            if r > R_OUT - 0.95:
                col = OUTLINE
            img[y][x] = col + (255,)

    # 顶部一颗装饰铆钉（只留一颗，多了会脏）
    for (x, y) in ((15, 2), (16, 2), (15, 3), (16, 3)):
        img[y][x] = RIVET + (255,)

    return img


def embed(obj16):
    """16×16 物品居中嵌进底座，物品的透明像素露出底面。"""
    img = base_frame()
    ox = oy = (N - 16) // 2
    for y in range(16):
        for x in range(16):
            px = obj16[y][x]
            if px[3] > 8:
                img[oy + y][ox + x] = px
    # 物品下缘投影：紧贴物品下方那一行压暗，做出"嵌进凹槽"的层次
    for x in range(16):
        col = ox + x
        row = oy + 16
        if 0 <= row < N and obj16[15][x][3] > 8:
            img[row][col] = FACE[0] + (255,)
    return img


def sheet(tiles, k=8, pad=5, bg=(22, 24, 30, 255)):
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
    # 内容层（16×16 的纯图标）与成品分开：成品是 32×32 带框，
    # 若直接从成品读会拿不到干净的内容层，重跑一次就会套两层框。
    SRC = os.environ.get('MEDAL_SRC') or os.path.join(root, '..', '..', 'tmp_cc0', 'content16')
    out = []
    for c in ('archer', 'blade', 'tank', 'mage', 'summoner'):
        obj = pngtool.read_png(os.path.join(SRC, f'{c}.png'))
        assert len(obj) == 16 and len(obj[0]) == 16, (c, len(obj[0]), len(obj))
        framed = embed(obj)
        out.append(framed)
        pngtool.write_png(os.path.join(TEX, f'{c}_medal.png'), framed)
        print(f'{c:9s} 嵌入完成 色数={pngtool.palette_size(framed)}')
    pngtool.write_png(os.path.join(root, '..', '..', 'tmp_cc0', 'framed_preview.png'), sheet(out))
    print('预览 -> tmp_cc0/framed_preview.png')
