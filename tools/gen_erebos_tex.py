#!/usr/bin/env python3
"""生成晦星之主·厄瑞玻斯的实体贴图与黯星信标图标。

风格取自设计稿：紫黑装甲、银色刃缘、紫白发光星核。

⚠️ 这里生成的是**程序化占位**，目的只是"在游戏里能看出形状与配色"，
你后面若自己重画，直接覆盖同名 PNG 即可（尺寸与 UV 布局不用变）：
    textures/entity/erebos_head.png  64x64
    textures/entity/erebos_body.png  64x64
    textures/entity/erebos_tail.png  64x64
    textures/item/darkstar_beacon.png 16x16
"""
import math
import os
import random

from PIL import Image

ENTITY = 'src/main/resources/assets/lost_realm/textures/entity'
ITEM = 'src/main/resources/assets/lost_realm/textures/item'

# ---- 配色 ----
DARK = (24, 14, 40, 255)        # 装甲暗部
MID = (58, 36, 92, 255)         # 装甲主色
LIGHT = (108, 76, 158, 255)     # 装甲亮部
SILVER = (172, 180, 200, 255)   # 银色刃缘
GLOW = (146, 92, 255, 255)      # 紫色辉光
CORE = (232, 206, 255, 255)     # 紫白星核


def armor(size, seed, band=4, sheen=0.25, core_spots=()):
    """一张"紫黑装甲板"的 UV 贴图。

    因为 MC 的 box UV 各面在贴图上的位置是固定的（顶/底/四侧各占一块），
    这里不逐面去画，而是让**整张图**都具备均匀的装甲质感 ——
    这样无论哪个面采到哪一块，看上去都是同一套材质。
    """
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    px = img.load()
    rnd = random.Random(seed)

    for y in range(size):
        for x in range(size):
            # 横向装甲带
            b = (y // band) % 2
            base = (MID if b == 0 else DARK)[:3]
            # 板缝压暗
            if y % band == 0:
                base = tuple(max(0, c - 16) for c in base)
            # 细噪点，避免大色块显得像塑料
            n = rnd.randint(-13, 13)
            col = tuple(max(0, min(255, c + n)) for c in base[:3]) + (255,)
            px[x, y] = col

    # ---- 银色刃缘：沿几条斜线铺高光，在面上会表现为锐利的棱 ----
    for i in range(size):
        for k, off in enumerate((size // 5, size // 2, (size * 4) // 5)):
            yy = (i + off) % size
            if k % 2 == 0 and i % 2 == 0:
                px[i, yy] = SILVER

    # ---- 零散的亮部，让装甲有"被光照到"的层次 ----
    for _ in range(max(1, int(size * size * sheen * 0.12))):
        x = rnd.randrange(size)
        y = rnd.randrange(size)
        px[x, y] = LIGHT

    # ---- 发光星核区域 ----
    for (cx, cy, r) in core_spots:
        for y in range(size):
            for x in range(size):
                d = math.hypot(x - cx, y - cy)
                if d > r:
                    continue
                t = 1.0 - d / r
                px[x, y] = CORE if t > 0.45 else GLOW
    return img


def beacon():
    """黯星信标图标 16x16：紫黑底 + 环形结构 + 中心星核。"""
    S = 16
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    px = img.load()
    rnd = random.Random(7)
    cx = cy = (S - 1) / 2.0

    for y in range(S):
        for x in range(S):
            d = math.hypot(x - cx, y - cy)

            # 外圈：银色环
            if 5.6 <= d <= 7.1:
                px[x, y] = SILVER
                continue
            # 内圈：紫色底盘
            if d <= 5.6:
                base = MID if (x + y) % 3 else DARK
                n = rnd.randint(-10, 10)
                px[x, y] = tuple(max(0, min(255, c + n)) for c in base[:3]) + (255,)
                continue

    # 四道放射光刺
    for i in range(1, 6):
        px[8, max(0, 8 - i - 2)] = GLOW
        px[8, min(S - 1, 8 + i + 2)] = GLOW
        px[max(0, 8 - i - 2), 8] = GLOW
        px[min(S - 1, 8 + i + 2), 8] = GLOW

    # 中心星核
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - cx, y - cy)
            if d <= 2.2:
                px[x, y] = CORE
            elif d <= 3.1:
                px[x, y] = GLOW
    return img


def bolt_texture():
    """星弹贴图 32x32。

    UV 布局（由 ErebosModel.bolt 决定）：
        外层晶棱 8x8x8 → 占 (0,0)-(32,16)
        内核     5x5x5 → 占 (0,16)-(20,26)
    分两块填色，是为了让内核能"更亮"—— 那个亮核心正是碰撞中心，
    玩家据此判断它到底在哪，比让整颗球一样亮清楚得多。
    """
    S = 32
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    px = img.load()
    rnd = random.Random(5)

    for y in range(S):
        for x in range(S):
            in_core = (16 <= y <= 26 and x <= 20)
            base = CORE if in_core else MID
            n = rnd.randint(-10, 10)
            px[x, y] = tuple(max(0, min(255, c + n)) for c in base[:3]) + (255,)

    # 两处亮斑，让面看起来是"有核的晶棱"而不是一块纯色
    for (cx, cy, r) in ((8.0, 8.0, 4.5), (8.0, 21.0, 3.2)):
        for y in range(S):
            for x in range(S):
                d = math.hypot(x - cx, y - cy)
                if d <= r:
                    px[x, y] = CORE if d <= r * 0.55 else GLOW
    return img


def main():
    os.makedirs(ENTITY, exist_ok=True)
    os.makedirs(ITEM, exist_ok=True)

    # 头部：额外在 UV 的右下角埋一块星核（那颗发光核心的 UV 起点是 (0,52)）
    head = armor(64, seed=11, band=4, sheen=0.5,
                 core_spots=[(12, 58, 7.0), (8, 14, 4.0)])
    head.save(os.path.join(ENTITY, 'erebos_head.png'))

    # 体节：装甲环在 UV (0,32) 起，用更密的带纹表现"分节的钢环"
    body = armor(64, seed=23, band=3, sheen=0.35)
    body.save(os.path.join(ENTITY, 'erebos_body.png'))

    # 尾部：更暗，收束感
    tail = armor(64, seed=37, band=5, sheen=0.22,
                 core_spots=[(10, 30, 4.0)])
    tail.save(os.path.join(ENTITY, 'erebos_tail.png'))

    beacon().save(os.path.join(ITEM, 'darkstar_beacon.png'))

    # 星弹（所有招式的公共弹药）
    bolt_texture().save(os.path.join(ENTITY, 'erebos_bolt.png'))

    for p in ('erebos_head', 'erebos_body', 'erebos_tail'):
        im = Image.open(os.path.join(ENTITY, p + '.png'))
        print('%-28s %s %s' % (p, im.size, im.mode))
    im = Image.open(os.path.join(ITEM, 'darkstar_beacon.png'))
    print('%-28s %s %s' % ('darkstar_beacon', im.size, im.mode))


if __name__ == '__main__':
    main()
