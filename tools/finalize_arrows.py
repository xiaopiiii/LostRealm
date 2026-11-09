#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把手工挑好的箭矢贴图定稿入库。

跟契约那批同一道工序：居中 → 轻量化到 24 色。

**这里刻意不压到 12 色**：实测这批 37~57 色的图里，多出来的色在承担箭杆的
明暗过渡，压到 12 会把过渡切成硬色块（星辰箭的紫色分层、反重力箭的青蓝渐变都会崩）。
契约那批是 100~195 色、平均每色只占 2 像素，那是真噪声，所以压得狠。
区别是这批是用户在 Pixelorama 里手工挑/微调过的，
所以只做"机械处理"，不动造型。

命名靠中文文件名对到物品 ID —— StarItem 保存时带的是提示词，
用户改名成中文了，所以这里用一张表硬对应。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pngtool
from finalize_contracts import center, quantize

SRC = r'D:\MC 贴图临时存放处'

MAP = {
    'piercing_arrow':      '贯穿之箭_1.png',
    'explosive_arrow':     '爆炸之箭_2.png',
    'antigravity_arrow':   '反重力之箭_2.png',
    'thunder_arrow':       '雷鸣箭_金黄_1.png',
    'armor_pierce_arrow':  '穿甲箭_深铁灰_2.png',
    'hold_arrow':          '定身箭_冰蓝_1.png',
    'summon_arrow':        '召唤箭_骨白_2.png',
    'star_arrow':          '星辰贯穿之箭_紫金_3.png',
}

# 还没做的两枚，用来提示
MISSING = {
    'potion_arrow': '药水箭',
    'twin_arrow':   '双响之箭',
}

ORDER = ['potion_arrow', 'explosive_arrow', 'piercing_arrow', 'antigravity_arrow',
         'thunder_arrow', 'armor_pierce_arrow', 'hold_arrow', 'summon_arrow',
         'twin_arrow', 'star_arrow']


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    TEX = os.path.join(root, 'src/main/resources/assets/lost_realm/textures/item')
    proj = os.path.join(root, '..', '..', '03_成品')

    done = {}
    print('%-20s %-24s %s' % ('物品', '源文件', '色数'))
    for cid, fname in MAP.items():
        src = os.path.join(SRC, fname)
        if not os.path.exists(src):
            print('!! 找不到', fname)
            continue
        img = pngtool.read_png(src)
        before = pngtool.palette_size(img)
        img = center(img)
        img = quantize(img, 24)
        pngtool.write_png(os.path.join(TEX, cid + '.png'), img)
        done[cid] = img
        print('%-20s %-24s %3d -> %d' % (cid, fname, before, pngtool.palette_size(img)))

    # 预览：按 ORDER 排，缺的位留空
    k, pad = 10, 10
    tw = 16 * k
    W = pad + len(ORDER) * (tw + pad)
    H = tw + pad * 2
    cv = [[(18, 20, 26, 255)] * W for _ in range(H)]
    x0 = pad
    for cid in ORDER:
        if cid in done:
            big = pngtool.scale(done[cid], k)
            for y in range(tw):
                for x in range(tw):
                    cv[pad + y][x0 + x] = big[y][x]
        else:
            for y in range(tw):
                for x in range(tw):
                    if x < 2 or y < 2 or x >= tw - 2 or y >= tw - 2:
                        cv[pad + y][x0 + x] = (60, 40, 40, 255)
        x0 += tw + pad
    pngtool.write_png(os.path.join(proj, '箭矢-预览.png'), cv)

    print('\n已入库 %d 枚 ->%s' % (len(done), ' 03_成品/箭矢-预览.png'))
    for cid, name in MISSING.items():
        print('缺：%s（%s）' % (name, cid))


if __name__ == '__main__':
    main()
