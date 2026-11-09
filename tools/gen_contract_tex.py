#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""九枚「契约」召唤师饰品 16×16 生成器（v3，纯 Python，无 Pillow）。

前两版为什么不行
----------------
v1「封魂石板」：每枚一个独立底色 + 白符号 → 九个彩色方块，彼此毫无关系。
v2「契约书」：九本一模一样的暗紫书 + 5×5 符文 → 统一了，但 5×5 塞不下东西，
  王冠像汉堡、军团像 U、共生像冒号；书脊/封面/纸页/包角又吃掉大半像素。

v3 的原则：**一个物品 = 一个清晰的剪影**（MC 原版物品都是这么做的）。
九枚做成九个不同的物件，靠形状区分；统一性不靠形状，靠三样：
  ① 同一套近黑外描边（物体不变小）
  ② 同一套打光方向（左上受光，右下压暗）
  ③ 同一套暗色系调色板（金/血/骨/铁/紫，全压在中低亮度）

九个物件
--------
统御→王冠  军团→军旗  共生→双环  殉爆→骷髅  魂链→锁链
血肉→心脏  骨甲→骨盾  裂变→裂晶  敕令→卷轴
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pngtool

N = 16
OUTLINE = (0x13, 0x0f, 0x1a)

# 调色板：hi 高光 / mid 主体 / dk 暗部
PAL = {
    'gold':  ((0xf8, 0xdc, 0x8c), (0xd2, 0xa6, 0x46), (0x86, 0x5e, 0x1c)),
    'blood': ((0xe0, 0x74, 0x74), (0xb0, 0x3a, 0x42), (0x6c, 0x1c, 0x26)),
    'bronze':((0xd8, 0xc0, 0x94), (0x9c, 0x82, 0x5c), (0x54, 0x40, 0x2a)),
    'bone':  ((0xf0, 0xea, 0xd4), (0xc4, 0xbc, 0xa0), (0x82, 0x7a, 0x62)),
    'iron':  ((0xc0, 0xce, 0xdc), (0x82, 0x92, 0xa4), (0x46, 0x52, 0x62)),
    'red':   ((0xe6, 0x62, 0x6a), (0xb8, 0x32, 0x3e), (0x66, 0x14, 0x20)),
    'violet':((0xd0, 0xa8, 0xf8), (0x96, 0x68, 0xd0), (0x4e, 0x30, 0x76)),
    'paper': ((0xf4, 0xea, 0xcc), (0xc8, 0xb8, 0x94), (0x84, 0x74, 0x54)),
    'wood':  ((0xa8, 0x80, 0x54), (0x7e, 0x5c, 0x36), (0x4c, 0x34, 0x1c)),
}
HOLE = (0x16, 0x12, 0x1d)


def new_img():
    return [[(0, 0, 0, 0)] * N for _ in range(N)]


def put(img, x, y, c):
    x, y = int(round(x)), int(round(y))
    if 0 <= x < N and 0 <= y < N:
        img[y][x] = tuple(c) + (255,)


def get(img, x, y):
    if 0 <= x < N and 0 <= y < N and img[y][x][3] > 0:
        return img[y][x][:3]
    return None


def hline(img, x0, x1, y, c):
    for x in range(int(math.floor(x0)), int(math.ceil(x1)) + 1):
        put(img, x, y, c)


def vline(img, x, y0, y1, c):
    for y in range(int(round(y0)), int(round(y1)) + 1):
        put(img, x, y, c)


def rect(img, x0, y0, x1, y1, c):
    for y in range(int(round(y0)), int(round(y1)) + 1):
        hline(img, x0, x1, y, c)


def disc(img, cx, cy, r, c):
    for y in range(N):
        for x in range(N):
            if math.hypot(x - cx, y - cy) <= r:
                put(img, x, y, c)


def ring(img, cx, cy, ro, ri, c):
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - cx, y - cy)
            if ri <= d <= ro:
                put(img, x, y, c)


def ering(img, cx, cy, rx, ry, w, c):
    """椭圆环。w 是环带占半径的比例，w=0.5 约等于 1px 宽。

    圆环在 16 网格上做不出孔：ro=3.7 / ri=2.3 的孔只剩 4 格宽，
    外描边一补就堵死，两个并排的环会糊成一颗胶囊。
    所以这里用更薄的环（相对宽 1px）＋更大的孔，并改成椭圆好排布。
    """
    for y in range(N):
        for x in range(N):
            t = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2
            if 1.0 - w <= t <= 1.0:
                put(img, x, y, c)


def litshadow(img, lo, hi):
    """按 x+y 三档打光：左上 hi，中间不动，右下 dk。"""
    for y in range(N):
        for x in range(N):
            c = get(img, x, y)
            if c is None:
                continue
            s = x + y
            if s < lo:
                put(img, x, y, hi)
            elif s > hi:
                put(img, x, y, img[y][x][:3] if False else c)
    return img


def outline(img):
    """外描边：把紧贴不透明像素的透明格染成近黑，物体本身不变小。"""
    src = [row[:] for row in img]
    for y in range(N):
        for x in range(N):
            if src[y][x][3] > 0:
                continue
            near = any(0 <= x + dx < N and 0 <= y + dy < N and src[y + dy][x + dx][3] > 0
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if near:
                img[y][x] = OUTLINE + (255,)


def center(img):
    xs = [x for y in range(N) for x in range(N) if img[y][x][3] > 0]
    ys = [y for y in range(N) for x in range(N) if img[y][x][3] > 0]
    if not xs:
        return img
    dx = (N - 1 - (max(xs) + min(xs))) // 2
    dy = (N - 1 - (max(ys) + min(ys))) // 2
    out = new_img()
    for y in range(N):
        for x in range(N):
            if img[y][x][3] > 0:
                nx, ny = x + dx, y + dy
                if 0 <= nx < N and 0 <= ny < N:
                    out[ny][nx] = img[y][x]
    return out


# ---------------------------------------------------------------- 九个物件

def crown():
    img = new_img()
    hi, mid, dk = PAL['gold']
    rect(img, 3, 9, 12, 11, mid)
    hline(img, 3, 12, 9, hi)
    hline(img, 3, 12, 11, dk)
    for cx in (4, 11):
        vline(img, cx, 5, 9, mid)
        put(img, cx, 4, hi)
        put(img, cx, 9, hi)
    for cx in (7, 8):
        vline(img, cx, 5, 9, mid)
        put(img, cx, 4, hi)
    put(img, 7, 10, PAL['red'][1])
    put(img, 8, 10, PAL['red'][0])
    return img


def banner():
    img = new_img()
    hi, mid, dk = PAL['blood']
    wh, wm, wd = PAL['wood']
    vline(img, 4, 2, 13, wm)
    put(img, 4, 1, wh)
    put(img, 4, 13, wd)
    for y in range(3, 8):
        end = 12 if y < 6 else (11 if y == 6 else 9)
        hline(img, 5, end, y, mid)
    hline(img, 5, 12, 3, hi)
    hline(img, 5, 9, 7, dk)
    for y in (4, 5):
        put(img, 8, y, PAL['gold'][1])
        put(img, 9, y, PAL['gold'][1])
    return img


def twin_rings():
    img = new_img()
    hi, mid, dk = PAL['bronze']
    # 两环中心距 5.6、外径 6.6 → 只交叠 1px，中间留得出孔
    ering(img, 5.2, 8.0, 3.1, 3.3, 0.58, mid)
    ering(img, 10.8, 8.0, 3.1, 3.3, 0.58, mid)
    for y in range(N):
        for x in range(N):
            c = get(img, x, y)
            if c is None:
                continue
            if x + y < 12:
                put(img, x, y, hi)
            elif x + y > 20:
                put(img, x, y, dk)
    return img


def skull():
    img = new_img()
    hi, mid, dk = PAL['bone']
    disc(img, 7.5, 6.4, 4.2, mid)
    rect(img, 5, 9, 10, 11, mid)
    hline(img, 5, 10, 11, dk)
    disc(img, 6, 6, 1.35, HOLE)
    disc(img, 9, 6, 1.35, HOLE)
    put(img, 7, 8, dk)
    put(img, 8, 8, dk)
    for x in (5, 6, 8, 9, 10):
        put(img, x, 12, mid)
        put(img, x, 11, dk)
    hline(img, 4, 5, 4, hi)
    hline(img, 4, 6, 3, hi)
    return img


def chains():
    img = new_img()
    hi, mid, dk = PAL['iron']
    # 两个环斜向排、互相咬住。上下叠成一竖会读成"8"，要错开才是链。
    ering(img, 5.4, 5.2, 2.7, 3.1, 0.55, mid)
    ering(img, 10.6, 10.8, 2.7, 3.1, 0.55, mid)
    for y in range(N):
        for x in range(N):
            c = get(img, x, y)
            if c is None:
                continue
            if x + y < 9:
                put(img, x, y, hi)
            elif x + y > 15:
                put(img, x, y, dk)
    return img


def heart():
    img = new_img()
    hi, mid, dk = PAL['red']
    disc(img, 6, 6, 3.0, mid)
    disc(img, 10, 6, 3.0, mid)
    for y in range(7, 14):
        half = (13 - y) * 0.78 + 0.4
        hline(img, 8 - half, 8 + half, y, mid)
    for y in range(N):
        for x in range(N):
            c = get(img, x, y)
            if c is None:
                continue
            if x + y < 11:
                put(img, x, y, hi)
            elif x + y > 17:
                put(img, x, y, dk)
    return img


def bone_shield():
    img = new_img()
    hi, mid, dk = PAL['bone']
    for y in range(2, 14):
        w = 4.6 if y < 7 else 4.6 * (13.6 - y) / 6.6
        hline(img, 7.5 - w, 7.5 + w, y, mid)
    vline(img, 7, 3, 11, hi)
    vline(img, 8, 3, 11, hi)
    for y in (5, 7, 9):
        hline(img, 4, 11, y, dk)
    return img


def shard():
    img = new_img()
    hi, mid, dk = PAL['violet']
    for y in range(2, 14):
        half = (y - 1.5) * 0.72 if y < 8 else (13.5 - y) * 0.72
        hline(img, 7.5 - half, 7.5 + half, y, mid)
    for y in range(2, 14):
        put(img, 7, y, dk)
        put(img, 8, y, dk)
    for y in range(3, 6):
        for x in range(4, 7):
            if get(img, x, y):
                put(img, x, y, hi)
    return img


def scroll():
    img = new_img()
    hi, mid, dk = PAL['paper']
    wh, wm, wd = PAL['wood']
    rect(img, 4, 4, 11, 11, mid)
    for x in (3, 12):
        vline(img, x, 3, 12, wm)
        put(img, x, 3, wh)
        put(img, x, 12, wd)
    for y in (6, 8, 10):
        hline(img, 5, 9, y, dk)
    disc(img, 7.5, 7.6, 1.6, PAL['red'][1])
    put(img, 7, 7, PAL['red'][0])
    hline(img, 5, 10, 4, hi)
    return img


BUILDERS = {
    'dominion': crown,
    'legion': banner,
    'symbiosis': twin_rings,
    'martyr': skull,
    'soulchain': chains,
    'flesh': heart,
    'bone': bone_shield,
    'fission': shard,
    'decree': scroll,
}

ORDER = ['dominion', 'legion', 'symbiosis', 'martyr', 'soulchain',
         'flesh', 'bone', 'fission', 'decree']


def build(key):
    img = BUILDERS[key]()
    img = center(img)
    outline(img)
    return img


def grid(tiles, k=12, pad=8, bg=(18, 20, 26, 255)):
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


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    TEX = os.path.join(root, 'src/main/resources/assets/lost_realm/textures/item')
    out_root = os.path.join(root, '..', '..', '03_成品')

    tiles = []
    for k in ORDER:
        im = build(k)
        tiles.append(im)
        pngtool.write_png(os.path.join(TEX, f'{k}_contract.png'), im)
        print(f'{k:10s} 色数={pngtool.palette_size(im)}')

    pngtool.write_png(os.path.join(out_root, '契约-预览.png'), grid(tiles, k=14))
    print('-> 03_成品/契约-预览.png')


if __name__ == '__main__':
    main()
