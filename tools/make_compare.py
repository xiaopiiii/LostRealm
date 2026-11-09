#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""出一张「旧硬切版 vs 新三档」并排对比图，同尺寸放大，方便一眼比差异。"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pngtool
import medal_frame28 as M


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    proj = os.path.join(root, '..', '..')
    old = pngtool.read_png(os.path.join(proj, '03_成品', '勋章贴图(beta52-28x28硬切版).png'))
    src = os.path.join(proj, 'tmp_cc0', 'content16', 'blade.png')
    obj = pngtool.read_png(src)

    tiles = [old]
    names = ['旧(硬切)']
    for name, (lo, hi) in M.VARIANTS.items():
        tiles.append(M.embed(obj, M.make_ramp(lo, hi)))
        names.append(name)

    for t, n in zip(tiles, names):
        st = M.ring_stats(t)
        print(f'{n:10s} 平均ΔL={st[0]:5.1f} 最大ΔL={st[1]:5.1f} 色数={pngtool.palette_size(t):3d}')

    pngtool.write_png(os.path.join(proj, '03_成品', '勋章新旧对比.png'),
                      M.grid([tiles], k=16, pad=12))
    print('-> 03_成品/勋章新旧对比.png  （左起：旧 / A标准 / B柔和 / C雾化）')


if __name__ == '__main__':
    main()
