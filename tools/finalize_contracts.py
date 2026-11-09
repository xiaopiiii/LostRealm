#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 StarItem 生成的候选定稿成游戏贴图。

为什么还要过一道工序
--------------------
StarItem 是在 16×16 网格上扩散的，但它**没有锁调色板**——
实测每张 100~170 色，而手绘 16×16 一般是 8~24 色。
色数失控的表现是边缘碎色、同色区域里混着几个近似色，
放大看就是"脏"。所以这里做三件事：

  ① 居中   按 alpha bbox 平移，保证九枚在物品栏里视觉大小一致
  ② 量化   k-means 压到 12 色（手绘像素画的常见色数）
  ③ 二值化 alpha  扩散输出的 alpha 虽然已经阈值过，仍逐张复核
"""
import glob
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pngtool

OUT = r'D:\WorkBuddy\2026-11-01-01-18-09\LostRealm_dev_beta29\tmp_cc0\star_out'

# 契约 ID -> (候选目录, 文件名)
PICK = {
    'dominion':  ('crown',        'crown_2.png'),
    'legion':    ('flag',         'red_flag_2.png'),
    'symbiosis': ('ring',         'ring_2.png'),
    'martyr':    ('skull',        'skull_2.png'),
    'soulchain': ('chain',        'chain_2.png'),
    'flesh':     ('heart',        'heart_2.png'),
    'bone':      ('shield',       'shield_3.png'),
    'fission':   ('crystal',      'crystal_4.png'),
    'decree':    ('parchment',    'old_parchment_4.png'),
}

ORDER = ['dominion', 'legion', 'symbiosis', 'martyr', 'soulchain',
         'flesh', 'bone', 'fission', 'decree']


def center(img):
    h, w = len(img), len(img[0])
    xs = [x for y in range(h) for x in range(w) if img[y][x][3] > 128]
    ys = [y for y in range(h) for x in range(w) if img[y][x][3] > 128]
    if not xs:
        return img
    dx = (w - 1 - (max(xs) + min(xs))) // 2
    dy = (h - 1 - (max(ys) + min(ys))) // 2
    out = [[(0, 0, 0, 0)] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            if img[y][x][3] > 128:
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h:
                    out[ny][nx] = img[y][x]
    return out


def quantize(img, k=12, iters=16):
    h, w = len(img), len(img[0])
    pts = [(y, x, img[y][x][:3]) for y in range(h) for x in range(w) if img[y][x][3] > 128]
    if not pts:
        return img
    cols = [p[2] for p in pts]
    random.seed(11)
    cent = random.sample(cols, min(k, len(cols)))

    def nearest(c):
        best, bi = 1 << 30, 0
        for i, m in enumerate(cent):
            d = (c[0] - m[0]) ** 2 + (c[1] - m[1]) ** 2 + (c[2] - m[2]) ** 2
            if d < best:
                best, bi = d, i
        return bi

    for _ in range(iters):
        groups = [[] for _ in cent]
        for _, _, c in pts:
            groups[nearest(c)].append(c)
        new = []
        for i, g in enumerate(groups):
            new.append(tuple(sum(v[j] for v in g) // len(g) for j in range(3)) if g else cent[i])
        if new == cent:
            break
        cent = new

    out = [[(0, 0, 0, 0)] * w for _ in range(h)]
    for y, x, c in pts:
        out[y][x] = cent[nearest(c)] + (255,)
    return out


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    TEX = os.path.join(root, 'src/main/resources/assets/lost_realm/textures/item')
    proj = os.path.join(root, '..', '..', '03_成品')

    tiles = []
    print('%-10s %-22s %s' % ('契约', '源文件', '色数 原始->量化'))
    for cid in ORDER:
        folder, fname = PICK[cid]
        src = os.path.join(OUT, folder, fname)
        img = pngtool.read_png(src)
        before = pngtool.palette_size(img)
        img = center(img)
        img = quantize(img, 12)
        after = pngtool.palette_size(img)
        pngtool.write_png(os.path.join(TEX, cid + '_contract.png'), img)
        tiles.append(img)
        print('%-10s %-22s %3d -> %d' % (cid, fname, before, after))

    # 预览
    k, pad = 10, 10
    tw = 16 * k
    W = pad + len(tiles) * (tw + pad)
    H = tw + pad * 2
    cv = [[(18, 20, 26, 255)] * W for _ in range(H)]
    x0 = pad
    for t in tiles:
        big = pngtool.scale(t, k)
        for y in range(tw):
            for x in range(tw):
                cv[pad + y][x0 + x] = big[y][x]
        x0 += tw + pad
    pngtool.write_png(os.path.join(proj, '契约-AI版预览.png'), cv)
    print('-> 03_成品/契约-AI版预览.png')


if __name__ == '__main__':
    main()
