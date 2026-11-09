#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""28×28 圆形勋章底座 + 16×16 物品内嵌。

v2 修的是「跳色」，不是「色数」。
上一版把 12 档量化改成连续插值，色数从 20 涨到 55，但用户还是说突兀、变化不大。
诊断（medal_diag.py）测出来：中位 ΔL = 0.0，最大 ΔL = 34。
也就是说环上有一半的相邻像素**完全同色**，然后在某处突然跳 34 级亮度。

三个元凶都是同一类东西——**硬切**：
  1. lig = max(0, cos(θ))**0.55   → cos 为负时直接锁 0，半圈死平，出界线
  2. k = 0.86 if r > R_IN+1.05 else 1.0  → 径向一道台阶
  3. FACE if r < R_IN*0.74 else INNER    → 面盘一道台阶

v2 原则：**画布里不存在任何 if 分档**，所有明暗都由连续函数给出。
  光照 = 主光(左上) 0.74 + 补光(右下) 0.26，两个余弦叠加 → 全周导数处处非零，
         不会再出现大段同色的"板"
  颜色 = 7 个等亮度色标插值（深蓝紫 → 中性银白），色相随亮度连续走
  径向 = 抛物线 (1 - 0.15*(2s-1)^2)，内沿外沿微压暗，无台阶
  面盘 = smoothstep 凹面渐变，无台阶

尺寸账（16×16 方形外接圆半径 11.31）：
  28 半径 13.5 → 环 2.19px  ← 环就是 2 像素，物理上不可能有 4 级径向层次
  32 半径 15.5 → 环 4.19px  ← 物品只占 50%
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pngtool

N = 28
C = (N - 1) / 2.0          # 13.5
R_OUT = 13.45
R_IN = 11.30               # 16×16 外接圆 11.31，刚好吃满不裁角

LIGHT_MAIN = math.radians(-135.0)   # 主光：左上
LIGHT_FILL = math.radians(35.0)     # 补光：右下，弱
W_MAIN, W_FILL = 0.74, 0.26


def _mix_light(a):
    u1 = (math.cos(a - LIGHT_MAIN) + 1.0) * 0.5
    u2 = (math.cos(a - LIGHT_FILL) + 1.0) * 0.5
    return W_MAIN * u1 + W_FILL * u2


# 两盏灯相差 170°，物理上不可能同时照到 1，所以 u 的天然范围只有 0.26~0.74。
# 不归一化的话色标只用掉中间一半 —— 高光到不了白、暗部到不了深，整环发灰。
_SCAN = [_mix_light(i * math.pi / 360.0) for i in range(720)]
U_MIN, U_MAX = min(_SCAN), max(_SCAN)

# 面盘（物品四角露出来的那点底）
FACE_CENTER = (0x2c, 0x25, 0x39)
FACE_EDGE = (0x17, 0x13, 0x20)


def _clamp(v, lo=0.0, hi=255.0):
    return lo if v < lo else (hi if v > hi else v)


def _luma(c):
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def smoothstep(t):
    t = _clamp(t, 0.0, 1.0)
    return t * t * (3 - 2 * t)


def make_ramp(lo=92.0, hi=212.0, n=7):
    """造一条等亮度色标：暗端偏蓝紫，亮端中性微暖，亮度严格等差。

    三等分插值会在转折点留一道折痕（斜率突变，看着还是一道线），
    所以用 7 个色标，每段斜率只差 2~3%，肉眼连成一条。
    """
    stops = []
    for i in range(n):
        t = i / (n - 1.0)
        L = lo + (hi - lo) * t
        db = 20.0 * (1 - t) ** 1.2 - 3.0 * t      # 蓝偏移：暗 +17 → 亮 -3
        dr = -5.0 * (1 - t) ** 1.5                # 暗部红少，显得冷
        rgb = [L + dr, L, L + db]
        k = L / _luma(rgb)
        stops.append(tuple(int(_clamp(v * k)) for v in rgb))
    return stops


def ramp_lookup(ramp, u):
    u = _clamp(u, 0.0, 1.0)
    f = u * (len(ramp) - 1)
    i = min(int(f), len(ramp) - 2)
    t = f - i
    a, b = ramp[i], ramp[i + 1]
    return tuple(int(a[j] + (b[j] - a[j]) * t) for j in range(3))


def base_frame(ramp=None):
    ramp = ramp or make_ramp()
    img = [[(0, 0, 0, 0)] * N for _ in range(N)]
    for y in range(N):
        for x in range(N):
            dx, dy = x - C, y - C
            r = math.hypot(dx, dy)
            if r > R_OUT:
                continue
            ang = math.atan2(dy, dx)

            if r > R_IN:
                # —— 金属环 ——
                # 双余弦叠加：全周处处在变，没有"死平段"
                u = (_mix_light(ang) - U_MIN) / (U_MAX - U_MIN)
                # 环面是圆柱侧面：内外沿微压暗，中间受光 —— 抛物线，无台阶。
                # 幅度只能给到 5%：环总宽 2.19px，0.15 的压暗会在 1 个像素内
                # 跨完（约 30 级亮度），那本身就是一道看得见的刀口。
                s = (r - R_IN) / (R_OUT - R_IN)
                radial = 1.0 - 0.05 * (2.0 * s - 1.0) ** 2
                col = ramp_lookup(ramp, u * radial)
            else:
                # —— 面盘 —— 从环内侧的深色向内平滑抬到中心略亮，凹面感
                t = smoothstep((R_IN - r) / 3.2)
                col = tuple(int(FACE_EDGE[j] + (FACE_CENTER[j] - FACE_EDGE[j]) * t)
                            for j in range(3))
            img[y][x] = col + (255,)
    return img


def embed(obj16, ramp=None):
    img = base_frame(ramp)
    ox = oy = (N - 16) // 2
    for y in range(16):
        for x in range(16):
            px = obj16[y][x]
            if px[3] > 8:
                img[oy + y][ox + x] = px
    # 投影行删掉。原本在 y=22 整行 ×0.78，但那行距中心 r≈11.33~12.75，
    # 全部落在金属环上（物品只占 y 6..21），等于拿一把刀把环切成内暗外亮两段。
    # 诊断实测这一刀就是残余的最大跳变：94 -> 128，ΔL=34。
    # 物品本身比面盘亮，暗色面盘已经形成衬托，不需要额外投影。
    return img


def ring_stats(img):
    """沿圆周相邻像素的明度差 —— 越大越"跳"。"""
    n = len(img)
    c = (n - 1) / 2.0
    pts = []
    for y in range(n):
        for x in range(n):
            px = img[y][x]
            if px[3] < 8:
                continue
            dx, dy = x - c, y - c
            r = math.hypot(dx, dy)
            if R_IN < r <= n / 2.0 - 0.05:
                pts.append((math.atan2(dy, dx), x, y, px))
    pts.sort()
    ds = []
    for i in range(len(pts)):
        a, x, y, p = pts[i]
        b, x2, y2, q = pts[(i + 1) % len(pts)]
        if abs(a - b) > math.pi or math.hypot(x2 - x, y2 - y) > 1.5:
            continue
        ds.append(abs(_luma(p) - _luma(q)))
    if not ds:
        return (0, 0, 0)
    ds.sort()
    return (sum(ds) / len(ds), ds[-1], ds[len(ds) // 2])


def grid(rows_of_tiles, k=12, pad=8, bg=(18, 20, 26, 255)):
    tw = N * k
    cols = max(len(r) for r in rows_of_tiles)
    W = pad + cols * (tw + pad)
    H = pad + len(rows_of_tiles) * (tw + pad)
    canvas = [[bg] * W for _ in range(H)]
    for ri, tiles in enumerate(rows_of_tiles):
        x0, y0 = pad, pad + ri * (tw + pad)
        for t in tiles:
            big = pngtool.scale(t, k)
            for y in range(tw):
                for i in range(tw):
                    canvas[y0 + y][x0 + i] = big[y][i]
            x0 += tw + pad
    return canvas


# 亮度区间整体上抬 18 级：goety 那批物品本身偏暗，环再暗就跟图标糊成一片。
# 抬的是整条色标（暗端亮端一起抬），跨度不变，所以柔和度不受影响。
VARIANTS = {
    'A_标准': (73.0, 230.0),
    'B_柔和': (92.0, 212.0),
    'C_雾化': (110.0, 196.0),
}

if __name__ == '__main__':
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    TEX = os.path.join(root, 'src/main/resources/assets/lost_realm/textures/item')
    SRC = os.environ.get('MEDAL_SRC') or os.path.join(root, '..', '..', 'tmp_cc0', 'content16')
    out_root = os.path.join(root, '..', '..', '03_成品')

    jobs = [('archer', '游侠'), ('blade', '刃'), ('tank', '守御'),
            ('mage', '术士'), ('summoner', '唤灵')]
    objs = {c: pngtool.read_png(os.path.join(SRC, f'{c}.png')) for c, _ in jobs}

    # 三档柔和度各出一行，供用户挑
    rows, labels = [], []
    for name, (lo, hi) in VARIANTS.items():
        rp = make_ramp(lo, hi)
        rows.append([embed(objs[c], rp) for c, _ in jobs])
        labels.append(name)

    print('柔和度档位            平均ΔL  最大ΔL  中位ΔL  色数')
    for name, tiles in zip(labels, rows):
        st = ring_stats(tiles[1])
        cs = pngtool.palette_size(tiles[1])
        print(f'  {name:14s}  {st[0]:6.1f}  {st[1]:6.1f}  {st[2]:6.1f}   {cs:3d}')

    pngtool.write_png(os.path.join(out_root, '勋章柔和度三档对比.png'),
                      grid(rows, k=12, pad=10))
    print('对比图 -> 03_成品/勋章柔和度三档对比.png')

    # 落盘：默认用 B（柔和档）
    CHOICE = os.environ.get('MEDAL_VARIANT', 'B_柔和')
    rp = make_ramp(*VARIANTS[CHOICE])
    for c, _ in jobs:
        framed = embed(objs[c], rp)
        pngtool.write_png(os.path.join(TEX, f'{c}_medal.png'), framed)
    big = [embed(objs[c], rp) for c, _ in jobs]
    pngtool.write_png(os.path.join(out_root, '职业勋章-预览(28x28).png'),
                      grid([big], k=16, pad=12))
    print(f'已写入 5 枚贴图（{CHOICE}）')
