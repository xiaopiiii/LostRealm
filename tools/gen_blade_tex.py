#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成九枚「刃」类饰品的 16x16 贴图（v2 画法）。

旧版为什么丑（`beta45` 之前）：
  1. 构图是一根**竖直的棍子**，读不出"刀"的形状；
  2. 只有 2 阶色 + 3 个白点，没有明暗过渡；
  3. 整圈描深色会把 3 像素宽的刀身吃掉，所以干脆不描边 —— 放在深色 GUI 上就糊了。

这一版做了四件事：
  1. **斜向构图**：刃身 45° 上扬、护手横向、握柄沿同一根轴往左下 —— 和原版剑的阅读习惯一致；
  2. **逐行分带**：沿刀身宽度「左亮 / 中调 / 右暗」三档，斜线不会出现棋盘噪点；
  3. **色阶带色相偏移**：暗部加蓝、亮部加暖，避免"只是变灰"；
  4. **主题色做亮度上限**：太浅的颜色（冰蓝、纯白）先压到 210 以下再上色阶，
     否则整把刀会和 GUI 背景糊在一起。

不依赖 Pillow：自带一个最小 PNG 编码器，任何 Python 3 都能跑。
"""
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_DIR = os.path.join(ROOT, 'src/main/resources/assets/lost_realm/textures/item')

# ---- 每件饰品的主题色（刃身中调） ----
BLADES = {
    'sweeping_blade':  (178, 194, 206),
    'dual_blade':      (255, 234, 150),
    'crit_blade':      (255, 130, 120),
    'backstab_blade':  (200, 160, 240),
    'disarm_blade':    (255, 196, 120),
    'luck_blade':      (150, 250, 170),
    'toughness_blade': (198, 132,  98),
    'charging_blade':  (150, 196, 255),
    'dash_blade':      (238, 244, 252),
}

WOOD = (122, 88, 54)
LEATHER = (74, 56, 42)
GOLD = (206, 168, 92)


def write_png(path, rows):
    h, w = len(rows), len(rows[0])
    raw = bytearray()
    for row in rows:
        raw.append(0)
        for px in row:
            raw.extend(px)

    def chunk(t, d):
        return (struct.pack('>I', len(d)) + t + d
                + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff))

    png = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(bytes(raw), 9))
    png += chunk(b'IEND', b'')
    with open(path, 'wb') as f:
        f.write(png)


def clamp_lum(base, cap=210):
    """主题色太亮时整体压暗，保证色阶拉得开。"""
    lum = (base[0] * 3 + base[1] * 6 + base[2]) / 10.0
    if lum <= cap:
        return base
    k = cap / lum
    return tuple(max(0, min(255, int(c * k))) for c in base)


def tones(base, hue=True):
    """0 描边 / 1 暗 / 2 中 / 3 亮 / 4 高光。"""
    r, g, b = clamp_lum(base) if hue else base
    if hue:
        return [(int(r * 0.22 + 6), int(g * 0.22 + 8), int(b * 0.26 + 20)),
                (int(r * 0.62), int(g * 0.62), int(b * 0.72)),
                (r, g, b),
                (min(255, int(r * 1.10 + 26)), min(255, int(g * 1.10 + 26)), min(255, int(b * 1.06 + 26))),
                (min(255, int(r * 0.30 + 180)), min(255, int(g * 0.30 + 184)), min(255, int(b * 0.28 + 186)))]
    return [tuple(int(c * 0.22 + 8) for c in base),
            tuple(int(c * 0.62) for c in base),
            base,
            tuple(min(255, int(c * 1.12 + 24)) for c in base),
            tuple(min(255, int(c * 0.30 + 184)) for c in base)]


def build_mask(blade_len=8, base_w=3):
    m = [['.'] * 16 for _ in range(16)]
    for y in range(1, blade_len + 1):
        x0 = (blade_len - y) + 3
        w = base_w if y >= 3 else (2 if y == 2 else 1)
        for k in range(w):
            if 0 <= x0 + k < 16:
                m[y][x0 + k] = '1'
    gy = blade_len + 1
    for k in range(0, 4):
        m[gy][2 + k] = 'G'
    m[gy + 1][3] = 'G'
    m[gy + 1][4] = 'G'
    for (x, y) in [(2, gy + 2), (1, gy + 3), (0, gy + 4)]:
        m[y][x] = 'H'
        if x + 1 < 16:
            m[y][x + 1] = 'H'
    m[gy + 5][0] = 'P'
    m[gy + 5][1] = 'P'
    return [''.join(r) for r in m]


def render(mask, base):
    pal = {'1': tones(base), 'G': tones(GOLD, False),
           'H': tones(LEATHER, False), 'P': tones(WOOD, False)}
    px = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for y in range(16):
        x = 0
        while x < 16:
            c = mask[y][x]
            if c == '.':
                x += 1
                continue
            run = []
            while x < 16 and mask[y][x] == c:
                run.append(x)
                x += 1
            n = len(run)
            for i, xx in enumerate(run):
                if n == 1:
                    tone = 2
                elif i == 0:
                    tone = 3 if c == '1' else 2
                elif i == n - 1:
                    tone = 1
                else:
                    tone = 2
                px[y][xx] = pal[c][tone] + (255,)
    return px


def main():
    mask = build_mask()
    for name, base in BLADES.items():
        write_png(os.path.join(TEX_DIR, name + '.png'), render(mask, base))
        print('wrote', name)


if __name__ == '__main__':
    main()
