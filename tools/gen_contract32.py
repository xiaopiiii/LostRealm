#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""契约饰品 32×32 —— 程序化像素渲染器。

为什么不用拼方块
----------------
之前几版是"矩形 + 圆形 + 三角"直接填色，每个面一个纯色，
所以看起来像贴纸：没有面朝向、没有倒角、没有高光形状。

这里换成正经的像素画渲染流程：
  ① 形体      用掩码描述每个部件（冠圈/尖/宝石各自一层）
  ② 倒角      对掩码做到边缘的距离变换，h = min(距离/bevel, 1)
              —— 边缘自动降高，于是所有棱都自然"圆"下去，不会是一刀切的平片
  ③ 法线      对高度场做中心差分求梯度 → n = normalize(-dh, -dh, 1)
  ④ 打光      Lambert 漫反射（左上主光）+ 环境光 + Blinn-Phong 高光
  ⑤ 量化      u 落到 7 阶色带上 —— 像素画必须是阶跃，不能是连续渐变
  ⑥ 描边      掩码外扩 1px，用主色压到最暗再乘 0.55

这样出来的明暗是"算"出来的，高光会跟着形体走，不是随手点几个白点。
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pngtool

W = H = 32
AMBIENT = 0.38
LIGHT = (-0.52, -0.70, 0.49)          # 左上偏前
SPEC_POW = 22.0
BEVEL = 3.2


# ------------------------------------------------------------------ 颜色

def _rgb2hsv(r, g, b):
    r, g, b = r / 255.0, g / 255.0, b / 255.0
    mx, mn = max(r, g, b), min(r, g, b)
    d = mx - mn
    if d == 0:
        h = 0.0
    elif mx == r:
        h = (60 * ((g - b) / d)) % 360
    elif mx == g:
        h = 60 * ((b - r) / d) + 120
    else:
        h = 60 * ((r - g) / d) + 240
    s = 0 if mx == 0 else d / mx
    return h, s, mx


def _hsv2rgb(h, s, v):
    h = h % 360
    c = v * s
    x = c * (1 - abs((h / 60) % 2 - 1))
    m = v - c
    if h < 60:   r, g, b = c, x, 0
    elif h < 120: r, g, b = x, c, 0
    elif h < 180: r, g, b = 0, c, x
    elif h < 240: r, g, b = 0, x, c
    elif h < 300: r, g, b = x, 0, c
    else:        r, g, b = c, 0, x
    return tuple(int(round((ch + m) * 255)) for ch in (r, g, b))


COOL = (0x3a, 0x34, 0x52)     # 暗部反射的环境冷色


def ramp(base, n=7):
    """生成 n 阶色带。

    暗端不只压暗，还要往冷紫偏一点 —— 真实金属的暗部反射的是环境天光，
    全用同色相压暗会得到一条"土黄到黑"的带子，看着像塑料。
    """
    h, s, v = _rgb2hsv(*base)
    out = []
    for i in range(n):
        t = i / (n - 1.0)                     # 0 最暗 → 1 最亮
        vv = v * (0.44 + 1.14 * t)
        ss = s * (1.30 - 0.58 * t)
        c = _hsv2rgb(h, max(0.0, min(1.0, ss)), max(0.0, min(1.0, vv)))
        if t < 0.45:
            k = (0.45 - t) * 0.55
            c = tuple(int(c[j] + (COOL[j] - c[j]) * k) for j in range(3))
        out.append(c)
    return out


# ------------------------------------------------------------------ 形体

def disc_mask(cx, cy, rx, ry=None):
    ry = rx if ry is None else ry
    return [[((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0
             for x in range(W)] for y in range(H)]


def rect_mask(x0, y0, x1, y1, round_px=0):
    m = [[False] * W for _ in range(H)]
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if round_px:
                dx = min(x - x0, x1 - x)
                dy = min(y - y0, y1 - y)
                if dx + dy < round_px:
                    continue
            if 0 <= x < W and 0 <= y < H:
                m[y][x] = True
    return m


def taper_mask(cx, y0, y1, w_bottom, w_top):
    """从下往上收窄的尖（王冠的齿）。"""
    m = [[False] * W for _ in range(H)]
    for y in range(y0, y1 + 1):
        t = (y - y0) / max(1.0, (y1 - y0))
        hw = w_top + (w_bottom - w_top) * t
        for x in range(int(round(cx - hw)), int(round(cx + hw)) + 1):
            if 0 <= x < W and 0 <= y < H:
                m[y][x] = True
    return m


def _chamfer(mask):
    """到最近边缘的距离（chamfer 近似欧氏），用于倒角。"""
    INF = 1e9
    d = [[0.0 if not mask[y][x] else INF for x in range(W)] for y in range(H)]
    for y in range(H):
        for x in range(W):
            if d[y][x] == 0:
                continue
            best = d[y][x]
            if y > 0:
                best = min(best, d[y - 1][x] + 1)
                if x > 0:     best = min(best, d[y - 1][x - 1] + 1.414)
                if x < W - 1: best = min(best, d[y - 1][x + 1] + 1.414)
            if x > 0:
                best = min(best, d[y][x - 1] + 1)
            d[y][x] = best
    for y in range(H - 1, -1, -1):
        for x in range(W - 1, -1, -1):
            if d[y][x] == 0:
                continue
            best = d[y][x]
            if y < H - 1:
                best = min(best, d[y + 1][x] + 1)
                if x > 0:     best = min(best, d[y + 1][x - 1] + 1.414)
                if x < W - 1: best = min(best, d[y + 1][x + 1] + 1.414)
            if x < W - 1:
                best = min(best, d[y][x + 1] + 1)
            d[y][x] = best
    return d


class Part:
    def __init__(self, mask, base_color, height=1.0, bevel=BEVEL, spec=1.0, base=0.0, z=0):
        self.z = z            # 层序：凹下去的结构（眼窝、刻槽）要靠 z 盖在主体上，
        self.mask = mask      # 不能靠高度——它们本来就比主体低
        self.ramp = ramp(base_color)
        self.hmap = [[0.0] * W for _ in range(H)]
        dist = _chamfer(mask)
        for y in range(H):
            for x in range(W):
                if mask[y][x]:
                    t = min(1.0, dist[y][x] / bevel)
                    # base 是"最小抬升"：宝石这类嵌件必须整体高于底下的冠圈，
                    # 否则它边缘的高度低于冠圈，会被冠圈盖掉，只剩中间一小块露出
                    self.hmap[y][x] = (base + (1.0 - base) * t) * height
        self.spec = spec


def render(parts, outline_of=None):
    """合成所有部件：取高度最高者决定该像素属于谁。"""
    img = [[(0, 0, 0, 0)] * W for _ in range(H)]
    owner = [[None] * W for _ in range(H)]
    height = [[0.0] * W for _ in range(H)]
    zmap = [[-99] * W for _ in range(H)]
    for p in parts:
        for y in range(H):
            for x in range(W):
                if not p.mask[y][x]:
                    continue
                if (p.z, p.hmap[y][x]) >= (zmap[y][x], height[y][x]):
                    zmap[y][x] = p.z
                    height[y][x] = p.hmap[y][x]
                    owner[y][x] = p

    lx, ly, lz = LIGHT
    ln = math.sqrt(lx * lx + ly * ly + lz * lz)
    lx, ly, lz = lx / ln, ly / ln, lz / ln
    # 半程向量（观察方向 (0,0,1)）
    hx, hy, hz = lx, ly, lz + 1.0
    hn = math.sqrt(hx * hx + hy * hy + hz * hz)
    hx, hy, hz = hx / hn, hy / hn, hz / hn

    for y in range(H):
        for x in range(W):
            p = owner[y][x]
            if p is None:
                continue
            hc = height[y][x]
            hx0 = height[y][max(0, x - 1)]
            hx1 = height[y][min(W - 1, x + 1)]
            hy0 = height[max(0, y - 1)][x]
            hy1 = height[min(H - 1, y + 1)][x]
            if owner[y][max(0, x - 1)] is not p: hx0 = 0.0
            if owner[y][min(W - 1, x + 1)] is not p: hx1 = 0.0
            if owner[max(0, y - 1)][x] is not p: hy0 = 0.0
            if owner[min(H - 1, y + 1)][x] is not p: hy1 = 0.0
            dx = (hx1 - hx0) * 0.5
            dy = (hy1 - hy0) * 0.5
            K = 3.1
            nx, ny, nz = -dx * K, -dy * K, 1.0
            nl = math.sqrt(nx * nx + ny * ny + nz * nz)
            nx, ny, nz = nx / nl, ny / nl, nz / nl

            diff = max(0.0, nx * lx + ny * ly + nz * lz)
            u = AMBIENT + (1.0 - AMBIENT) * diff
            spec = max(0.0, nx * hx + ny * hy + nz * hz) ** SPEC_POW
            # 只有朝光的凸面才吃高光，凹处不给（否则全是白点）
            spec *= p.spec * (1.0 if hc > 0.45 else 0.0)
            u = min(1.0, u + spec * 0.55)

            # 部件接缝：相邻像素属于不同部件时压暗一档。
            # 没有这道线，齿和冠圈都是同一个金色，会糊成一整块看不出结构。
            for dx2, dy2 in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx2, ny2 = x + dx2, y + dy2
                if 0 <= nx2 < W and 0 <= ny2 < H:
                    o2 = owner[ny2][nx2]
                    if o2 is not None and o2 is not p:
                        u -= 0.22
                        break

            n = len(p.ramp)
            idx = int(round(u * (n - 1)))
            idx = max(0, min(n - 1, idx))
            img[y][x] = p.ramp[idx] + (255,)

    # 描边
    edge = [[False] * W for _ in range(H)]
    for y in range(H):
        for x in range(W):
            if owner[y][x] is not None:
                continue
            for dx2, dy2 in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx2, ny2 = x + dx2, y + dy2
                if 0 <= nx2 < W and 0 <= ny2 < H and owner[ny2][nx2] is not None:
                    edge[y][x] = True
                    break
    for y in range(H):
        for x in range(W):
            if edge[y][x] and owner[y][x] is None:
                # 取相邻部件的暗端再压暗
                col = (0x18, 0x14, 0x1e)
                for dx2, dy2 in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx2, ny2 = x + dx2, y + dy2
                    if 0 <= nx2 < W and 0 <= ny2 < H and owner[ny2][nx2] is not None:
                        c = owner[ny2][nx2].ramp[0]
                        col = tuple(int(v * 0.82) for v in c)
                        break
                img[y][x] = col + (255,)
    return img


# ------------------------------------------------------------------ 物件

GOLD = (0xe8, 0xb2, 0x3c)
GEM = (0x3f, 0x8f, 0xd8)


def crown():
    parts = []
    # 冠圈：加高到 9 行，宽 24，这样齿才有地方"坐"住
    band = rect_mask(4, 18, 27, 26, round_px=2)
    parts.append(Part(band, GOLD, height=0.72))
    # 冠圈中段一道凸起的束带：把 9 行高的冠圈切成上沿/束带/下沿三段，
    # 不切的话它就是一块平板，再好的打光也救不回来
    belt = rect_mask(5, 21, 26, 22)
    parts.append(Part(belt, (0xc8, 0x96, 0x2e), height=0.80, base=0.64))
    # 三个齿：矮而宽。做成细高条会读成"三根手指"，王冠的齿必须是三角楔
    for cx, top in ((7.0, 11), (15.5, 9), (24.0, 11)):
        parts.append(Part(taper_mask(cx, top, 19, 2.6, 1.3), GOLD, height=0.92))
    # 齿顶小珠
    for cx, top in ((7.0, 10), (15.5, 8), (24.0, 10)):
        parts.append(Part(disc_mask(cx, top, 1.2), GOLD, height=1.0))
    # 冠圈宝石
    parts.append(Part(disc_mask(15.5, 22.5, 3.4), GEM, height=1.06, base=0.74))
    return render(parts)

GOLD   = (0xe8, 0xb2, 0x3c)
GEM    = (0x3f, 0x8f, 0xd8)
RED    = (0xc0, 0x3a, 0x40)
WOOD   = (0x8a, 0x64, 0x3c)
BONE   = (0xe4, 0xdc, 0xc0)
IRON   = (0x8c, 0x9a, 0xac)
VIOLET = (0x9c, 0x6e, 0xda)
PAPER  = (0xdc, 0xcc, 0xa6)
BRONZE = (0xb6, 0x98, 0x62)
BLOOD  = (0xae, 0x2e, 0x38)
DARK   = (0x26, 0x20, 0x30)


def ring_mask(cx, cy, rx, ry, w):
    """椭圆环。w 是环带占比，0.55 大约 2.5px 宽。"""
    m = [[False] * W for _ in range(H)]
    inner = (1.0 - w) ** 2
    for y in range(H):
        for x in range(W):
            t = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2
            if inner <= t <= 1.0:
                m[y][x] = True
    return m


def banner():
    parts = []
    parts.append(Part(rect_mask(6, 4, 7, 29, round_px=1), WOOD, height=0.78))
    flag = [[False] * W for _ in range(H)]
    for y in range(6, 24):
        for x in range(8, 26):
            if y > 16 and abs(x - 16.5) < (y - 16) * 1.25:
                continue                      # 底部燕尾缺口
            flag[y][x] = True
    parts.append(Part(flag, RED, height=0.86))
    parts.append(Part(disc_mask(16.5, 13, 3.8), GOLD, height=0.96, base=0.66))
    parts.append(Part(rect_mask(14, 15, 19, 16), GOLD, height=0.3, base=0.3, z=4))
    return render(parts)


def twin_rings():
    parts = []
    parts.append(Part(ring_mask(10.5, 16, 6.6, 7.6, 0.55), BRONZE, height=0.92))
    parts.append(Part(ring_mask(21.5, 16, 6.6, 7.6, 0.55), BRONZE, height=0.92))
    return render(parts)


def skull():
    parts = []
    parts.append(Part(disc_mask(16, 13, 9.4, 9.8), BONE, height=1.0))
    parts.append(Part(rect_mask(10, 19, 22, 25, round_px=2), BONE, height=0.86))
    parts.append(Part(disc_mask(11.0, 12.5, 3.1, 3.4), DARK, height=0.18, base=0.1, z=6))
    parts.append(Part(disc_mask(21.0, 12.5, 3.1, 3.4), DARK, height=0.18, base=0.1, z=6))
    parts.append(Part(taper_mask(16, 16, 19, 0.5, 1.7), DARK, height=0.18, base=0.1, z=6))
    for cx in (12, 15, 19, 22):
        parts.append(Part(rect_mask(cx, 23, cx, 26), DARK, height=0.18, base=0.1, z=6))
    return render(parts)


def chains():
    parts = []
    parts.append(Part(ring_mask(12.0, 11.0, 5.9, 6.9, 0.52), IRON, height=0.90))
    parts.append(Part(ring_mask(20.0, 21.0, 5.9, 6.9, 0.52), IRON, height=0.86))
    return render(parts)


def heart():
    parts = []
    parts.append(Part(disc_mask(11.0, 12, 6.4, 6.0), BLOOD, height=1.0))
    parts.append(Part(disc_mask(21.0, 12, 6.4, 6.0), BLOOD, height=1.0))
    tri = [[False] * W for _ in range(H)]
    for y in range(14, 29):
        half = (28.5 - y) * 0.78
        for x in range(int(16 - half), int(16 + half) + 1):
            if 0 <= x < W:
                tri[y][x] = True
    parts.append(Part(tri, BLOOD, height=0.94))
    # 高光：心尖上方一道斜亮斑，靠 z 压在最上层
    parts.append(Part(disc_mask(9.5, 9.0, 2.4, 2.0), (0xe0, 0x76, 0x7c), height=0.2, base=0.2, z=4))
    return render(parts)


def shield():
    m = [[False] * W for _ in range(H)]
    for y in range(4, 29):
        hw = 11.2 if y < 14 else 11.2 * (28.6 - y) / 14.6
        for x in range(int(16 - hw), int(16 + hw) + 1):
            if 0 <= x < W:
                m[y][x] = True
    parts = [Part(m, BONE, height=0.96)]
    parts.append(Part(rect_mask(14, 6, 17, 26, round_px=1), (0xd2, 0xc6, 0xa6), height=1.06, base=0.62))
    for y in (10, 16, 21):
        parts.append(Part(rect_mask(6, y, 25, y), (0x9e, 0x92, 0x74), height=0.3, base=0.28, z=4))
    return render(parts)


def crystal():
    m = [[False] * W for _ in range(H)]
    for y in range(4, 29):
        half = (y - 3) * 1.30 if y < 16 else (29 - y) * 1.30
        for x in range(int(16 - half), int(16 + half) + 1):
            if 0 <= x < W:
                m[y][x] = True
    parts = [Part(m, VIOLET, height=1.0)]
    crack = [[False] * W for _ in range(H)]
    for y in range(5, 28):
        cx = 16 + int(2.6 * math.sin(y * 0.55))
        crack[y][cx] = True
        if 6 < y < 26:
            crack[y][cx - 1] = True
    parts.append(Part(crack, (0x2e, 0x1e, 0x48), height=0.26, base=0.2, z=6))
    return render(parts)


def scroll():
    parts = []
    parts.append(Part(rect_mask(7, 8, 24, 24), PAPER, height=0.62))
    parts.append(Part(rect_mask(4, 5, 27, 9, round_px=2), WOOD, height=0.96))
    parts.append(Part(rect_mask(4, 23, 27, 27, round_px=2), WOOD, height=0.96))
    for y in (12, 15, 18):
        parts.append(Part(rect_mask(9, y, 22, y), (0x8e, 0x7e, 0x5e), height=0.2, base=0.2, z=4))
    parts.append(Part(disc_mask(16, 20, 3.6), RED, height=1.08, base=0.72, z=6))
    return render(parts)


BUILDERS = {
    'dominion': crown,
    'legion': banner,
    'symbiosis': twin_rings,
    'martyr': skull,
    'soulchain': chains,
    'flesh': heart,
    'bone': shield,
    'fission': crystal,
    'decree': scroll,
}

ORDER = ['dominion', 'legion', 'symbiosis', 'martyr', 'soulchain',
         'flesh', 'bone', 'fission', 'decree']


def grid(tiles, k=6, pad=8, bg=(18, 20, 26, 255)):
    tw = W * k
    width = pad + len(tiles) * (tw + pad)
    height = tw + pad * 2
    cv = [[bg] * width for _ in range(height)]
    x0 = pad
    for t in tiles:
        big = pngtool.scale(t, k)
        for y in range(tw):
            for x in range(tw):
                cv[pad + y][x0 + x] = big[y][x]
        x0 += tw + pad
    return cv


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    proj = os.path.join(root, '..', '..')
    tiles = []
    for k in ORDER:
        im = BUILDERS[k]()
        tiles.append(im)
        pngtool.write_png(os.path.join(proj, 'tmp_cc0', 'c32_' + k + '.png'), im)
        print('%-10s 色数=%d' % (k, pngtool.palette_size(im)))
    pngtool.write_png(os.path.join(proj, '03_成品', '契约32-预览.png'), grid(tiles, k=6))
    print('-> 03_成品/契约32-预览.png')


if __name__ == '__main__':
    main()
