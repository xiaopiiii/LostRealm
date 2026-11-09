#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把九枚契约饰品换成 goety 的专业像素美术。

为什么换
--------
前三版都是手绘：几何图形拼出来的王冠/盾/心，色数 4~9，形状生硬。
用户原话「质量也太低了，没有第一次生成职业图标的那种质量」——
第一次那批职业勋章之所以好看，是因为内嵌物品直接取自 goety
（同作者、同画风、原生 16×16 的专业美术），不是画出来的。

所以这里走同一条路：九枚契约各挑一件语义对得上的 goety 物件。

为什么不用 32×32
-----------------
goety 526 张物品贴图里 489 张原生就是 16×16，32×32 的只有 28 张，
而且全是法杖/镰刀/锤子这类大件武器，没有饰品。
把 16×16 拉到 32 只能靠最近邻放大，每个像素变 2×2，分辨率翻倍但信息量不变，
视觉上不会更好，反而丢掉原生像素画的手绘格子感。

映射
----
统御→金王冠   军团→紫号角   共生→金蓝吊坠
殉爆→红眼符   魂链→金锁链   血肉→血瓶
骨甲→银蓝盾   裂变→虚空晶   敕令→紫卷轴
"""
import io
import os
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pngtool

JAR = (r'D:\zhuomian\PCL 正式版 2.10.8\.minecraft\versions\1.20.1-Forge_47.4.28'
       r'\mods\[诡厄巫法] goety-2.5.60.1.jar')

MAP = {
    'dominion_contract':  'storm_crown',            # 统御：金冠蓝宝石，王者
    'legion_contract':    'command_horn',           # 军团：号角，号令
    'symbiosis_contract': 'sea_amulet',             # 共生：金环吊坠
    'martyr_contract':    'ominous_charm_filled',   # 殉爆：暗青符 + 红眼
    'soulchain_contract': 'gold_dungeon_chain',     # 魂链：金锁链
    'flesh_contract':     'unholy_blood',           # 血肉：血瓶
    'bone_contract':      'magic_shield',           # 骨甲：盾
    'fission_contract':   'void_shard',             # 裂变：虚空晶簇
    'decree_contract':    'forbidden_scroll',       # 敕令：卷轴
    # 第九枚之后还有一枚"九合一"终极，不换的话整套里就它一块彩色方块，特别扎眼。
    # 用最深的一顶王冠，跟统御那顶金冠拉开档次。
    'overlord_contract':  'abyss_crown',
}

ORDER = ['dominion', 'legion', 'symbiosis', 'martyr', 'soulchain',
         'flesh', 'bone', 'fission', 'decree', 'overlord']


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    TEX = os.path.join(root, 'src/main/resources/assets/lost_realm/textures/item')
    out_root = os.path.join(root, '..', '..', '03_成品')

    z = zipfile.ZipFile(JAR)
    names = {n.split('/')[-1][:-4]: n for n in z.namelist()
             if '/textures/item/' in n and n.endswith('.png')}

    tiles, labels = [], []
    for k in ORDER:
        src = MAP[f'{k}_contract']
        path = names[src]
        raw = z.read(path)
        dst = os.path.join(TEX, f'{k}_contract.png')
        with open(dst, 'wb') as f:
            f.write(raw)
        im = pngtool.read_png(dst)
        assert len(im) == 16 and len(im[0]) == 16, (src, len(im[0]), len(im))
        tiles.append(im)
        labels.append(src)
        print(f'{k:10s} <- {src:24s} 色数={pngtool.palette_size(im)}')

    # 预览：14 倍放大
    k, pad = 14, 10
    tw = 16 * k
    W = pad + len(tiles) * (tw + pad)
    H = tw + pad * 2
    bg = (18, 20, 26, 255)
    cv = [[bg] * W for _ in range(H)]
    x0 = pad
    for t in tiles:
        big = pngtool.scale(t, k)
        for y in range(tw):
            for x in range(tw):
                cv[pad + y][x0 + x] = big[y][x]
        x0 += tw + pad
    pngtool.write_png(os.path.join(out_root, '契约-预览.png'), cv)
    print('-> 03_成品/契约-预览.png')

    # 新旧对比：上排手绘 v3，下排 goety
    old_dir = os.path.join(out_root, '契约旧贴图(beta52)')
    old = [pngtool.read_png(os.path.join(old_dir, f'{k}_contract.png')) for k in ORDER]
    k2, pad2 = 12, 8
    tw2 = 16 * k2
    W2 = pad2 + len(ORDER) * (tw2 + pad2)
    H2 = pad2 * 3 + tw2 * 2
    cv2 = [[bg] * W2 for _ in range(H2)]
    for ri, group in enumerate((old, tiles)):
        x0 = pad2
        y0 = pad2 + ri * (tw2 + pad2 + 6)
        for t in group:
            big = pngtool.scale(t, k2)
            for y in range(tw2):
                for x in range(tw2):
                    cv2[y0 + y][x0 + x] = big[y][x]
            x0 += tw2 + pad2
    pngtool.write_png(os.path.join(out_root, '契约-新旧对比.png'), cv2)
    print('-> 03_成品/契约-新旧对比.png（上排手绘 v3 / 下排 goety 美术）')


if __name__ == '__main__':
    main()
