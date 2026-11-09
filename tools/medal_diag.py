#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""诊断：把环上像素按角度排序，列出相邻像素的色差，找"跳色"的确切位置。

用法：python medal_diag.py <png>
不修改任何文件，只打印统计。
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pngtool


def luma(c):
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def main(path):
    img = pngtool.read_png(path)
    N = len(img)
    C = (N - 1) / 2.0
    r_out = N / 2.0 - 0.05
    r_in = 11.30 * (N / 28.0) if N == 28 else N / 2.0 - 2.2

    ring = []
    for y in range(N):
        for x in range(N):
            px = img[y][x]
            if px[3] < 8:
                continue
            dx, dy = x - C, y - C
            r = math.hypot(dx, dy)
            if r_in < r <= r_out:
                ring.append((math.atan2(dy, dx), x, y, r, px))

    ring.sort()
    print(f'{os.path.basename(path)}  {N}x{N}  环像素 {len(ring)}  色数 {pngtool.palette_size(img)}')
    laps = []
    for i in range(len(ring)):
        a, x, y, r, p = ring[i]
        b, x2, y2, r2, q = ring[(i + 1) % len(ring)]
        d = abs(a - b)
        if d > math.pi:
            continue                       # 首尾接缝，跳过
        gap = math.hypot(x2 - x, y2 - y)
        if gap > 1.5:
            continue                       # 圆周上不相邻
        de = sum((p[k] - q[k]) ** 2 for k in range(3)) ** 0.5
        dl = abs(luma(p) - luma(q))
        laps.append((dl, de, math.degrees(a), (x, y), (x2, y2), p[:3], q[:3]))

    laps.sort(reverse=True)
    print('\n沿圆周相邻像素，明度差最大的 8 处：')
    for dl, de, deg, p1, p2, c1, c2 in laps[:8]:
        print(f'  ΔL={dl:5.1f}  ΔRGB={de:5.1f}  @{deg:7.1f}°  {p1}{c1} -> {p2}{c2}')
    avg = sum(l[0] for l in laps) / len(laps)
    print(f'\n平均 ΔL={avg:.1f}  最大 ΔL={laps[0][0]:.1f}  中位 ΔL={laps[len(laps)//2][0]:.1f}')

    # 径向：同一角度上内外两层是否硬切
    print('\n径向探针（从左上 225° 沿半径逐像素）：')
    for ang_deg in (225, 315, 45, 135):
        ang = math.radians(ang_deg)
        row = []
        for rr in [i * 0.5 for i in range(int(r_in * 2) - 2, int(r_out * 2) + 2)]:
            x = int(round(C + rr * math.cos(ang)))
            y = int(round(C + rr * math.sin(ang)))
            if 0 <= x < N and 0 <= y < N and img[y][x][3] > 8:
                if not row or row[-1][0] != (x, y):
                    row.append(((x, y), img[y][x][:3]))
        s = '  '.join(f'{c}' for _, c in row)
        print(f'  {ang_deg:3d}°  {s}')


if __name__ == '__main__':
    main(sys.argv[1])
