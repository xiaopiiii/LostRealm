#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""区块加载器 / 镇魔火把 的 16×16 方块贴图。

两个方块的视觉语言要能一眼分出状态：

区块加载器
  待机：中心能量核是暗青，整块像一块"通电了但没开机"的石头
  运行：核跳到亮青白，四角指示灯点亮，远处也能看出它在工作
  外壳保持深紫石质，和模组里虚空石/竞技场骨架同一个色系

镇魔火把
  火焰用冷色（青白）而不是普通火把的橙黄 —— 它烧的不是木头，是"驱散"
  杆身保持木色，否则玩家不认它是火把
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pngtool

N = 16

# —— 区块加载器 ——
EDGE = (0x16, 0x12, 0x20)
BASE = (0x2c, 0x26, 0x3a)
BASE_D = (0x21, 0x1c, 0x2e)
BASE_L = (0x3b, 0x34, 0x4e)
CORE_RIM = (0x1c, 0x30, 0x42)
CORE_IDLE = (0x33, 0x5d, 0x78)
CORE_IDLE_C = (0x4a, 0x80, 0x9c)
CORE_ON = (0x5f, 0xcf, 0xea)
CORE_ON_C = (0xc8, 0xf6, 0xff)
LAMP_OFF = (0x2a, 0x3f, 0x50)
LAMP_ON = (0x9a, 0xe8, 0xff)

# —— 镇魔火把 ——
STICK = (0x6b, 0x4e, 0x30)
STICK_D = (0x46, 0x32, 0x1e)
FLAME_OUT = (0x2e, 0x6e, 0x98)
FLAME_MID = (0x6f, 0xc8, 0xe8)
FLAME_CORE = (0xd6, 0xf4, 0xff)


def blank():
    return [[(0, 0, 0, 0)] * N for _ in range(N)]


def put(img, x, y, c):
    if 0 <= x < N and 0 <= y < N:
        img[y][x] = tuple(c) + (255,)


def chunk_loader(active):
    img = blank()
    # 底板：左上受光、右下压暗的斜向三段。不用随机噪声 ——
    # 16×16 上撒噪点会读成"花屏"，不是石头。
    for y in range(N):
        for x in range(N):
            if x in (0, N - 1) or y in (0, N - 1):
                put(img, x, y, EDGE)
                continue
            d = (x + y) / (2.0 * (N - 1))
            put(img, x, y, BASE_L if d < 0.22 else (BASE_D if d > 0.80 else BASE))

    # 内凹槽：距离边框 2 格的一圈暗线，让面板看起来是"嵌进壳里"的
    for i in range(2, N - 2):
        for (x, y) in ((i, 2), (i, N - 3), (2, i), (N - 3, i)):
            put(img, x, y, BASE_D)

    # 中心圆形能量核：像素画里圆要用 hypot 算，方块套方块会变成俄罗斯方块
    cx = cy = (N - 1) / 2.0
    for y in range(N):
        for x in range(N):
            r = math.hypot(x - cx, y - cy)
            if r <= 5.4:
                put(img, x, y, CORE_RIM)
            if r <= 4.4:
                put(img, x, y, CORE_IDLE_C if active else CORE_IDLE)
            if r <= 2.6:
                put(img, x, y, CORE_ON if active else CORE_IDLE_C)
    if active:
        for y in range(N):
            for x in range(N):
                if math.hypot(x - cx, y - cy) <= 1.5:
                    put(img, x, y, CORE_ON_C)

    # 四角十字螺丝
    lamp = LAMP_ON if active else LAMP_OFF
    for (sx, sy) in ((2, 2), (N - 3, 2), (2, N - 3), (N - 3, N - 3)):
        put(img, sx, sy, lamp)
        put(img, sx - 1, sy, BASE_D)
        put(img, sx + 1, sy, BASE_D)
        put(img, sx, sy - 1, BASE_D)
        put(img, sx, sy + 1, BASE_D)

    return img


def torch():
    img = blank()
    # 杆：2px 宽，左亮右暗，从 y=8 到底
    for y in range(8, N):
        put(img, 7, y, STICK)
        put(img, 8, y, STICK_D)
    # 火焰：水滴形，上窄下宽。方块状火焰会读成"棒棒糖"
    rows = [
        (3, 7, 7, FLAME_OUT),
        (4, 6, 9, FLAME_OUT),
        (5, 5, 10, FLAME_MID),
        (6, 5, 10, FLAME_MID),
        (7, 6, 9, FLAME_OUT),
    ]
    for (y, x0, x1, c) in rows:
        for x in range(x0, x1 + 1):
            put(img, x, y, c)
    for (y, x0, x1) in ((5, 6, 9), (6, 6, 9)):
        for x in range(x0, x1 + 1):
            put(img, x, y, FLAME_CORE)
    put(img, 7, 4, FLAME_CORE)
    put(img, 8, 4, FLAME_CORE)
    return img


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    RES = os.path.join(root, 'src/main/resources/assets/lost_realm/textures')
    out = []

    a = chunk_loader(False)
    b = chunk_loader(True)
    t = torch()

    for name, im in (('block/chunk_loader', a), ('block/chunk_loader_active', b),
                     ('block/anti_spawn_torch', t), ('item/anti_spawn_torch', t)):
        p = os.path.join(RES, name + '.png')
        pngtool.write_png(p, im)
        out.append(p)
        print('%-32s 色数=%d' % (name, pngtool.palette_size(im)))

    # 预览：待机 / 运行 / 火把，各放大 12 倍
    k, pad = 12, 10
    tw = N * k
    cv = [[(18, 20, 26, 255)] * (pad + 3 * (tw + pad)) for _ in range(tw + pad * 2)]
    for i, im in enumerate((a, b, t)):
        big = pngtool.scale(im, k)
        x0 = pad + i * (tw + pad)
        for y in range(tw):
            for x in range(tw):
                cv[pad + y][x0 + x] = big[y][x]
    pngtool.write_png(os.path.join(root, '..', '..', '03_成品', '新方块-预览.png'), cv)
    print('-> 03_成品/新方块-预览.png')


if __name__ == '__main__':
    main()
