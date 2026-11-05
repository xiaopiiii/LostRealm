#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把外部生成的图标（下载的 PNG）收编成模组贴图。

它负责三件事，避免"图看着好、进游戏却不对劲"：

1. **栅格化**：尺寸不是 32 就按块众数重采样（不插值、不产生中间色）。
2. **居中留边**：MC 物品图标习惯四周留 1px 空边，否则在物品栏里会贴边。
3. **体检**：报告实际尺寸、不透明像素占比、色数 —— 色数超过 ~24
   基本说明这张图是"高分辨率图缩下来的"，不是原生像素画。

用法：
    # 先看预览，不写游戏目录
    python tools/import_icons.py --src ~/Downloads --names a.png=archer,b.png=blade

    # 确认后落盘
    python tools/import_icons.py --src ~/Downloads --names ... --apply

    # 只出拼图看效果
    python tools/import_icons.py --src tmp_rd/out --preview tmp_rd/preview.png
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pngtool

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_DIR = os.path.join(ROOT, 'src/main/resources/assets/lost_realm/textures/item')
SIZE = 32
CLASSES = ('archer', 'blade', 'tank', 'mage', 'summoner')


def load(path):
    rows = pngtool.read_png(path)
    w, h = len(rows[0]), len(rows)
    if w != SIZE or h != SIZE:
        # 大小不一：先按块众数降到 SIZE，再裁边居中
        rows = pngtool.downscale_majority(rows, SIZE)
    return pngtool.crop_pad(rows, SIZE, pad=1)


def health(name, rows):
    filled = sum(1 for row in rows for c in row if c[3] > 8)
    total = SIZE * SIZE
    return (f'{name:9s} {SIZE}x{SIZE}  不透明 {filled / total:.0%}  '
            f'色数 {pngtool.palette_size(rows):2d}')


def sheet(tiles, k=5, pad=6, bg=(20, 22, 26, 255)):
    tw = SIZE * k
    w = pad + len(tiles) * (tw + pad)
    h = tw + pad * 2
    canvas = [[bg] * w for _ in range(h)]
    x = pad
    for _name, rows in tiles:
        big = pngtool.scale(rows, k)
        for y in range(tw):
            for i in range(tw):
                canvas[pad + y][x + i] = big[y][i]
        x += tw + pad
    return canvas


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--src', required=True, help='目录或单个文件')
    ap.add_argument('--names', default='', help='a.png=archer,b.png=blade（不给则按文件名猜）')
    ap.add_argument('--apply', action='store_true', help='写入 textures/item/')
    ap.add_argument('--preview', default='', help='输出预览拼图路径')
    a = ap.parse_args()

    if os.path.isdir(a.src):
        files = sorted(os.path.join(a.src, f) for f in os.listdir(a.src)
                       if f.lower().endswith('.png'))
    else:
        files = [a.src]

    pairs = []
    if a.names:
        for item in a.names.split(','):
            fn, cls = item.split('=')
            pairs.append((cls.strip(), os.path.join(a.src, fn.strip())))
    else:
        for f in files:
            low = os.path.basename(f).lower()
            for cls in CLASSES:
                if cls in low:
                    pairs.append((cls, f))
                    break
    if not pairs:
        sys.exit('没匹配到任何职业，请用 --names 指定：a.png=archer,b.png=blade')

    tiles = []
    for cls, path in pairs:
        if not os.path.exists(path):
            print(f'!! 缺文件 {path}')
            continue
        rows = load(path)
        print(health(cls, rows))
        tiles.append((cls, rows))

    if a.preview and tiles:
        pngtool.write_png(a.preview, sheet(tiles))
        print('预览 ->', a.preview)

    if a.apply:
        os.makedirs(TEX_DIR, exist_ok=True)
        for cls, rows in tiles:
            dst = os.path.join(TEX_DIR, f'{cls}_medal.png')
            pngtool.write_png(dst, rows)
            print('写入', dst)


if __name__ == '__main__':
    main()
