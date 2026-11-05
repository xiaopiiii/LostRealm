# -*- coding: utf-8 -*-
"""32×32 职业勋章 —— 逐像素绘制（不是模板套色）。

和之前两版的区别：
  旧版 16×16 只有 2~3 个色阶，靠"规则"上色，结果是没有设计感的色块。
  这一版 32×32，分四层画：
    ① 金属外环（3px 宽，按角度算光：左上最亮、右下最暗，带 2 个高光弧）
    ② 珐琅面（中心亮到边缘暗 + 顶部内投影 + 底部反光，做出"凹进去"的立体感）
    ③ 中央纹章（每个职业单独画，1px 深色描边保证在深色背景上也立得住）
    ④ 顶部镶嵌宝石（带一个 1px 高光点）
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pngtool

N = 32
C = 15.5
R_OUT = 15.0      # 外缘
R_RIM = 12.6      # 金属环内缘（外环 2.4px，比原来细，把面积让给纹章）
R_IN = 11.6       # 珐琅面半径


def hexc(s):
    s = s.lstrip('#')
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16))


def mix(a, b, t):
    return (int(a[0] + (b[0] - a[0]) * t),
            int(a[1] + (b[1] - a[1]) * t),
            int(a[2] + (b[2] - a[2]) * t))


def shade(c, k):
    """k>1 提亮，k<1 压暗；往 255/0 靠，保留色相。"""
    if k >= 1:
        t = min(1.0, k - 1.0)
        return mix(c, (255, 255, 255), t * 0.75)
    return mix(c, (0, 0, 0), 1.0 - k)


# ---------------------------------------------------------------- 调色
PAL = {
    'outline': hexc('#150f12'),
    'rim_d':   hexc('#3d3a47'),
    'rim_m':   hexc('#6f6b7e'),
    'rim_l':   hexc('#a5a0b4'),
    'rim_h':   hexc('#d6d1e2'),
    'gold_d':  hexc('#7a5a22'),
    'gold_m':  hexc('#b8913c'),
    'gold_l':  hexc('#e6c164'),
    'en_d':    hexc('#2a0d14'),
    'en_m':    hexc('#7d1c2c'),
    'en_l':    hexc('#b83048'),
    'en_h':    hexc('#dd5a72'),
    'bl_d':    hexc('#3f4653'),
    'bl_m':    hexc('#7e8b9e'),
    'bl_l':    hexc('#bcc9d8'),
    'bl_h':    hexc('#eef4fb'),
    'gem_d':   hexc('#5c0b1a'),
    'gem_m':   hexc('#b41e38'),
    'gem_l':   hexc('#f2586e'),
}


def snap(c, ramp):
    """把计算出来的连续色吸附到最近的固定色阶 —— 这是"像像素画"的关键。

    不吸附的话，径向渐变会产生 60+ 种颜色，视觉上就是 3D 渲染；
    吸附到 4 阶后只剩 16~20 色，才读得出"手绘像素"的台阶感。
    """
    return min(ramp, key=lambda r: (r[0] - c[0]) ** 2 + (r[1] - c[1]) ** 2 + (r[2] - c[2]) ** 2)


RIM_RAMP = [PAL['rim_d'], PAL['rim_m'], PAL['rim_l'], PAL['rim_h']]


def build(theme):
    """theme: {'enamel': (暗,中,亮,高光)}，返回 32×32 像素。"""
    ed, em, el, eh = theme
    img = [[(0, 0, 0, 0)] * N for _ in range(N)]
    for y in range(N):
        for x in range(N):
            dx, dy = x - C, y - C
            r = math.hypot(dx, dy)
            if r > R_OUT + 0.45:
                continue
            ang = math.atan2(dy, dx)
            # 光源在左上；-3π/4 对应左上方向
            lig = math.cos(ang + 0.75 * math.pi)          # 1 = 正对光
            lig = max(0.0, lig) ** 0.85
            lg = 1.0 - min(1.0, r / R_IN)

            if r > R_RIM + 1.0:                            # ① 金属外环（2 阶）
                base = PAL['rim_l'] if lig > 0.42 else PAL['rim_m']
                if lig > 0.9:
                    base = PAL['rim_h']
                if lig < 0.08:
                    base = PAL['rim_d']
                img[y][x] = base + (255,)
            elif r > R_RIM:                                # ② 金色内圈（1px，提升"造价感"）
                base = PAL['gold_l'] if lig > 0.35 else PAL['gold_m']
                if lig < 0.05:
                    base = PAL['gold_d']
                img[y][x] = base + (255,)
            elif r > R_IN:                                 # ③ 珐琅面：4 阶
                t = (r - R_IN * 0.30) / (R_RIM - R_IN * 0.30)
                t = max(0.0, min(1.0, t))
                base = mix(el, em, t)
                if dy < -3 and r > R_IN - 2.2:              # 顶部内投影
                    base = shade(base, 0.55)
                if dy > 4 and r > R_IN - 1.8:               # 底部反光
                    base = mix(base, eh, 0.55)
                if R_IN - 1.4 < r < R_IN - 0.2:             # 内凹槽：一圈暗线，做出"面嵌在环里"
                    base = shade(base, 0.55)
                img[y][x] = snap(base, [ed, em, el, eh]) + (255,)
            else:                                          # ④ 中央底色
                base = em if lg > 0.6 else mix(em, el, 0.4)
                img[y][x] = snap(base, [ed, em, el, eh]) + (255,)
    return img


def put(img, x, y, c):
    if 0 <= x < N and 0 <= y < N:
        img[y][x] = c + (255,)


def blade_emblem(img):
    """交叉双刃：两条带刃口高光的斜线。"""
    def seg(x0, y0, x1, y1, w=1.15):
        steps = 80
        for i in range(steps + 1):
            t = i / steps
            x = x0 + (x1 - x0) * t
            y = y0 + (y1 - y0) * t
            for oy in range(-2, 3):
                for ox in range(-2, 3):
                    px, py = int(round(x + ox)), int(round(y + oy))
                    if not (0 <= px < N and 0 <= py < N):
                        continue
                    d = math.hypot(px - x, py - y)
                    if d > w:
                        continue
                    # 刃口朝向：法线方向偏光侧为亮
                    nx, ny = (y1 - y0), -(x1 - x0)
                    ln = math.hypot(nx, ny) or 1
                    nx, ny = nx / ln, ny / ln
                    lit = (px - x) * nx + (py - y) * ny < 0
                    if d > w - 0.5:
                        put(img, px, py, PAL['bl_d'])
                    elif lit:
                        put(img, px, py, PAL['bl_h'] if d < w - 0.9 else PAL['bl_l'])
                    else:
                        put(img, px, py, PAL['bl_m'])
    seg(9, 25, 22, 9)
    seg(23, 25, 10, 9)
    # 交叉点的护手小圆
    for oy in range(-2, 3):
        for ox in range(-2, 3):
            if ox * ox + oy * oy <= 4:
                put(img, 16 + ox, 17 + oy, PAL['gold_m'])
    put(img, 15, 16, PAL['gold_l'])
    put(img, 16, 16, PAL['gold_h'] if 'gold_h' in PAL else PAL['gold_l'])


def arc_emblem(img):
    """弓 + 搭箭。"""
    cx, cy, r = 15.0, 18.0, 10.0
    for deg in range(-115, 116, 1):
        a = math.radians(deg - 90)
        for w in (0.0, 0.9):
            x = cx + math.cos(a) * (r - w)
            y = cy + math.sin(a) * (r - w)
            px, py = int(round(x)), int(round(y))
            put(img, px, py, PAL['gold_m'] if w == 0 else PAL['gold_d'])
    for y in range(8, 27):                          # 弓弦
        put(img, 15, y, PAL['bl_d'])
    for x in range(11, 22):                         # 箭
        put(img, x, 17, PAL['bl_h'])
        put(img, x, 18, PAL['bl_m'])


def shield_emblem(img):
    """塔盾：上宽下尖。"""
    for y in range(9, 26):
        t = (y - 9) / 16.0
        half = 7.5 * (1 - t ** 1.9)
        for x in range(int(16 - half), int(16 + half) + 1):
            edge = abs(x - 16) >= int(half) - 1 or y <= 10 or y >= 24
            put(img, x, y, PAL['bl_d'] if edge else (PAL['bl_l'] if x < 15 else PAL['bl_m']))
    for y in range(12, 22):                          # 中脊
        put(img, 16, y, PAL['gold_m'])
    put(img, 16, 13, PAL['gold_l'])


def skull_emblem(img):
    """带角骷髅 + 魂火。"""
    for y in range(10, 20):
        for x in range(11, 21):
            d = math.hypot((x - 15.5) / 5.0, (y - 14.5) / 5.2)
            if d <= 1.0:
                put(img, x, y, PAL['bl_h'])
    for x in (13, 14, 17, 18):                       # 眼窝（2x2 深洞）
        put(img, x, 13, PAL['outline'])
        put(img, x, 14, PAL['outline'])
    for x in range(13, 19, 2):                       # 牙
        put(img, x, 19, PAL['bl_m'])
    for dx, dy in ((9, 11), (10, 10), (21, 11), (20, 10)):   # 角
        put(img, dx, dy, PAL['bl_l'])
    for (x, y) in ((16, 21), (15, 22), (17, 22), (16, 23)):   # 魂火（小火苗）
        put(img, x, y, PAL['gem_l'])
    put(img, 16, 24, PAL['gem_m'])


def mage_emblem(img):
    """法杖：竖直偏左，顶端是本职业色的宝珠。"""
    for i in range(0, 15):
        x = 13 + (i // 5)
        y = 24 - i
        put(img, x, y, PAL['gold_d'])
        put(img, x + 1, y, PAL['gold_m'])
    put(img, 12, 25, PAL['gold_d'])
    cx, cy = 14, 9
    for oy in range(-3, 4):
        for ox in range(-3, 4):
            d = math.hypot(ox, oy)
            if d <= 2.7:
                put(img, cx + ox, cy + oy,
                    PAL['bl_h'] if d < 1.1 else (PAL['en_l'] if d < 2.0 else PAL['en_m']))
    put(img, cx - 1, cy - 1, PAL['bl_h'])


THEMES = {
    'blade':    (PAL['en_d'], PAL['en_m'], PAL['en_l'], PAL['en_h']),
    'archer':   (hexc('#0c2417'), hexc('#1e6b3a'), hexc('#39a95c'), hexc('#6fe08c')),
    'tank':     (hexc('#121a2a'), hexc('#2b4470'), hexc('#4a72ab'), hexc('#7fa8d8')),
    'mage':     (hexc('#1c0f2e'), hexc('#4b2a7d'), hexc('#7a4cc0'), hexc('#b184ea')),
    'summoner': (hexc('#0b2320'), hexc('#14554d'), hexc('#22897c'), hexc('#57c9b6')),
}
EMBLEMS = {
    'blade': blade_emblem,
    'archer': arc_emblem,
    'tank': shield_emblem,
    'mage': mage_emblem,
    'summoner': skull_emblem,
}


def render(name):
    img = build(THEMES[name])
    EMBLEMS[name](img)
    # 宝石（顶部镶嵌）—— 用本职业的高光色，五枚才分得开
    _ed, _em, _el, _eh = THEMES[name]
    for oy in range(-2, 3):
        for ox in range(-2, 3):
            d = math.hypot(ox, oy * 1.15)
            if d <= 1.9:
                x, y = 16 + ox, 5 + oy
                put(img, x, y, _eh if d < 1.0 else _el)
    put(img, 15, 4, PAL['bl_h'])
    # 统一描边：所有不透明像素的外缘压深
    out = [row[:] for row in img]
    for y in range(N):
        for x in range(N):
            if img[y][x][3] == 0:
                continue
            ring = False
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if not (0 <= nx < N and 0 <= ny < N) or img[ny][nx][3] == 0:
                    ring = True
            if ring:
                out[y][x] = PAL['outline'] + (255,)
    return out


def main():
    tiles = []
    for n in ('blade', 'archer', 'tank', 'mage', 'summoner'):
        img = render(n)
        pngtool.write_png(f'../tmp_rd/medal_{n}.png', img)
        tiles.append(img)
        print(n, '色数', pngtool.palette_size(img))
    k = 8
    tw = N * k
    pad = 5
    W = pad + len(tiles) * (tw + pad)
    H = tw + pad * 2
    canvas = [[(18, 18, 22, 255)] * W for _ in range(H)]
    x0 = pad
    for img in tiles:
        big = pngtool.scale(img, k)
        for y in range(tw):
            for i in range(tw):
                canvas[pad + y][x0 + i] = big[y][i]
        x0 += tw + pad
    pngtool.write_png('../tmp_rd/medals_hand.png', canvas)
    print('预览 -> tmp_rd/medals_hand.png')


if __name__ == '__main__':
    os.makedirs('../tmp_rd', exist_ok=True)
    main()
