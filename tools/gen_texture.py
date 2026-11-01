#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
遗落之主贴图生成器（dv21 重做：人形 + 触手 + 头冠 + 驱镰，128x128 + 发光遮罩）。

=== 与 gen_geo.py 的一致性 ===
分区坐标**不再各写一份** —— 两个脚本都 import `uv_layout.py`，
并且本脚本用 `UV.faces(区域, w,h,d)` 直接取六个面的矩形，
所以「几何的 UV 偏移」与「贴图画在哪」物理上不可能对不上。

=== 发光遮罩 ===
GeckoLib 的 `AutoGlowingTexture` 会去找**同名 + `_glowmask`** 的贴图并自发光渲染
（反汇编确认字符串常量 `_glowmask`）。所以只要：
  1. 生成 `warden_glowmask.png`（只让要发光的像素不透明）
  2. 渲染器带 `AutoGlowingGeoLayer`
眼睛 / 胸口符纹 / 镰刃锋口 / 触手血纹 就会在暗处自己发亮。

=== 配色 ===
幽影深紫 + 血红发光。甲片近黑，布料偏紫，头冠骨白，镰刀冷灰。
"""

import os
import random

from PIL import Image, ImageDraw

import uv_layout as UV

random.seed(20261013)

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources',
                   'assets', 'lost_realm', 'textures', 'entity')

# ---- 调色板 ----
CLOTH_DARK = (26, 21, 40)
CLOTH_MID = (46, 37, 70)
CLOTH_LIT = (68, 54, 98)
ARMOR_DARK = (20, 18, 28)
ARMOR_MID = (44, 39, 56)
ARMOR_LIT = (58, 51, 70)
BONE = (168, 160, 148)
BONE_LIT = (206, 198, 184)
METAL = (86, 84, 98)
METAL_LIT = (146, 144, 160)

GLOW_RED = (255, 66, 54)
GLOW_ROSE = (244, 96, 148)
GLOW_MAGENTA = (222, 106, 232)


def cl(v):
    return max(0, min(255, int(v)))


def noise(d, box, base, vary=7):
    x0, y0, x1, y1 = [int(round(t)) for t in box]
    for y in range(y0, y1):
        for x in range(x0, x1):
            n = random.randint(-vary, vary)
            d.point((x, y), (cl(base[0] + n), cl(base[1] + n), cl(base[2] + n), 255))


def vgrad(d, box, top, bottom, vary=6):
    x0, y0, x1, y1 = [int(round(t)) for t in box]
    h = max(1, y1 - y0)
    for y in range(y0, y1):
        t = (y - y0) / h
        c = [top[i] + (bottom[i] - top[i]) * t for i in range(3)]
        for x in range(x0, x1):
            n = random.randint(-vary, vary)
            d.point((x, y), (cl(c[0] + n), cl(c[1] + n), cl(c[2] + n), 255))


def rbox(d, box, color):
    """画一个矩形（右/下边界为开区间）。退化成零宽/零高时自动补 1px，
    否则 PIL 会抛 "y1 must be greater than or equal to y0"。"""
    x0, y0, x1, y1 = [int(round(t)) for t in box]
    if x1 <= x0:
        x1 = x0 + 1
    if y1 <= y0:
        y1 = y0 + 1
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=color)


def f(name, w, h, d_):
    return {k: tuple(int(round(v)) for v in r)
            for k, r in UV.faces(name, w, h, d_).items()}


# ============================================================
# 人形三件套
# ============================================================

def build_head(dr, gl):
    fx = f("HEAD", 24, 24, 24)
    # 头盔主体：顶部亮、底部暗
    vgrad(dr, (fx['right'][0], fx['right'][1], fx['back'][2], fx['back'][3]),
          CLOTH_LIT, CLOTH_DARK, vary=7)
    for r in (fx['top'], fx['bottom'], fx['left'], fx['front'], fx['back']):
        noise(dr, r, CLOTH_MID, 7)
    rbox(dr, fx['top'], CLOTH_LIT + (255,))
    # 正脸：黑色面甲 + 一条横缝
    fr = fx['front']
    noise(dr, fr, ARMOR_DARK, 5)
    y_mid = (fr[1] + fr[3]) // 2
    rbox(dr, (fr[0], y_mid - 1, fr[2], y_mid + 1), (58, 20, 26, 255))
    # ★ 两只发光眼（脸宽 12px，眼占 3px）
    for ex in (fr[0] + 4, fr[2] - 10):
        rbox(dr, (ex, y_mid - 4, ex + 6, y_mid + 2), GLOW_ROSE + (255,))
        rbox(gl, (ex, y_mid - 4, ex + 6, y_mid + 2), GLOW_ROSE + (255,))
    # 头顶一圈符文
    tp = fx['top']
    rbox(dr, (tp[0] + 1, tp[1] + 1, tp[2] - 1, tp[1] + 2), (96, 40, 48, 255))
    rbox(gl, (tp[0] + 1, tp[1] + 1, tp[2] - 1, tp[1] + 2), GLOW_RED + (255,))


def build_torso(dr, gl):
    fx = f("TORSO", 24, 36, 12)
    for k in fx:
        noise(dr, fx[k], CLOTH_MID, 7)
    # 胸甲：正面中段一块近黑甲片
    fr = fx['front']
    rbox(dr, (fr[0], fr[1] + 6, fr[2], fr[3] - 6), ARMOR_MID + (255,))
    rbox(dr, (fr[0], fr[1] + 6, fr[2], fr[1] + 8), ARMOR_LIT + (255,))
    # ★ 胸口符纹
    cx = (fr[0] + fr[2]) // 2
    cy = (fr[1] + fr[3]) // 2
    rbox(dr, (cx - 4, cy - 10, cx + 4, cy + 10), (120, 34, 40, 255))
    rbox(dr, (cx - 10, cy - 4, cx + 10, cy + 4), (120, 34, 40, 255))
    rbox(dr, (cx - 2, cy - 6, cx + 2, cy + 6), GLOW_RED + (255,))
    rbox(dr, (cx - 6, cy, cx + 6, cy), GLOW_RED + (255,))
    rbox(gl, (cx - 6, cy - 6, cx + 6, cy + 6), GLOW_RED + (255,))
    # 腰带
    rbox(dr, (fr[0], fr[3] - 8, fr[2], fr[3] - 4), (92, 74, 40, 255))
    # 背面披风色
    bk = fx['back']
    vgrad(dr, bk, CLOTH_MID, CLOTH_DARK, vary=6)


def build_limb(dr, gl, region, w, h, d_, glow_edge=False):
    fx = f(region, w, h, d_)
    for k in fx:
        noise(dr, fx[k], CLOTH_MID, 6)
    fr = fx['front']
    # 下段（手/靴）+ 一道亮边
    rbox(dr, (fr[0], fr[3] - 12, fr[2], fr[3]), ARMOR_MID + (255,))
    rbox(dr, (fr[0], fr[3] - 12, fr[2], fr[3] - 10), ARMOR_LIT + (255,))
    if glow_edge:
        rbox(dr, (fr[0], fr[3] - 5, fr[2], fr[3] - 4), (110, 32, 38, 255))
        rbox(gl, (fr[0], fr[3] - 5, fr[2], fr[3] - 4), GLOW_RED + (255,))


def build_crown(dr, gl):
    fx = f("HORN", 7.2, 19.2, 7.2)
    for k in fx:
        vgrad(dr, fx[k], BONE_LIT, BONE, vary=6)
    tp = fx['top']
    rbox(dr, tp, (232, 214, 150, 255))
    rbox(gl, tp, (255, 196, 96, 255))


def build_scythe(dr, gl):
    """镰柄（dv30）：深冷铁色杆 + 纵向血纹 + 金属箍。

    柄被切成主杆 / 握把 / 柄箍 / 4 段矛尖共 8 个 cube，全都采样这一块区域，
    所以画法必须「对任意一块都成立」：整块铺同样的金属纵向渐变 +
    几条纵向血纹，任何子矩形落上去都合理。
    """
    rx, ry, rw, rh = UV.REGIONS["SCYTHE"]
    x0, y0 = int(rx), int(ry)
    vgrad(dr, (x0, y0, x0 + rw, y0 + rh), (38, 34, 42), (11, 10, 15), vary=6)
    # 纵向血纹（沿柄长方向）
    for k in range(7):
        xx = x0 + 3 + k * 7
        if xx >= x0 + rw:
            break
        rbox(dr, (xx, y0, xx + 1, y0 + rh), (112, 26, 34, 255))
        if k % 2 == 0:
            rbox(gl, (xx, y0, xx + 1, y0 + rh), (152, 38, 48, 255))
    # 金属箍（沿柄周期性出现；刻意压暗，别抢血纹的视觉）
    for yy in range(y0 + 10, y0 + rh - 3, 52):
        rbox(dr, (x0, yy, x0 + rw, yy + 3), (54, 50, 60, 255))
        rbox(dr, (x0, yy, x0 + rw, yy + 1), (76, 72, 84, 255))


def build_blade(dr, gl):
    """漆黑刃身（dv30）：**纯黑刃面** + 暗红中脊血痕。

    ★ dv30 起红边不再画在贴图上，改成独立的一层 EDGE 几何：
      6 根长条首尾相接，红边粗细不受 UV 拉伸影响 —— dv29 的共享 UV 方案下，
      2.2px 厚的薄段会被整根拉成红色。所以这块区域只画「黑底 + 暗红纹理」。
    """
    rx, ry, rw, rh = UV.REGIONS["BLADE"]
    x0, y0 = int(rx), int(ry)
    vgrad(dr, (x0, y0, x0 + rw, y0 + rh), (34, 28, 34), (6, 5, 9), vary=5)
    # 暗红中脊：沿刃长方向的不规则血痕
    for k in range(6):
        yy = y0 + 2 + k * 3
        xa = x0 + 6 + (k % 3) * 9
        xb = min(x0 + 40 + (k % 4) * 11, x0 + rw - 2)
        rbox(dr, (xa, yy, xb, yy + 1), (74, 16, 24, 255))
        if k % 2 == 0:
            rbox(gl, (xa, yy, xb, yy + 1), (54, 12, 18, 255))


def build_edge(dr, gl):
    """血红刃口（dv30）：独立几何层，整条亮红 + 内侧压暗过渡。

    长条尺寸 33 x EDGE_H(2.6) x 5，整块画成血红，暗处会整条亮起来 ——
    参考图里「死神镰」最抢眼的特征。
    """
    rx, ry, rw, rh = UV.REGIONS["EDGE"]
    x0, y0 = int(rx), int(ry)
    vgrad(dr, (x0, y0, x0 + rw, y0 + rh), (184, 36, 40), (74, 13, 17), vary=5)
    # 内侧（贴刃身那一侧）压暗，做出「刃口从刃身里长出来」的过渡
    rbox(dr, (x0, y0 + rh - 2, x0 + rw, y0 + rh), (44, 8, 12, 255))
    rbox(gl, (x0, y0, x0 + rw, y0 + rh), (168, 34, 40, 255))


def build_guard(dr, gl):
    """刃根装饰（dv30）：黑红甲板 + 金色头骨。

    中轴护板、两片各 3 根翅骨、以及一枚头骨都采样这块区域，
    所以画成「黑底 + 不规则暗红裂纹」的甲片质感，左上角点一块金色给头骨用。
    """
    rx, ry, rw, rh = UV.REGIONS["GUARD"]
    x0, y0 = int(rx), int(ry)
    vgrad(dr, (x0, y0, x0 + rw, y0 + rh), (40, 34, 42), (11, 9, 14), vary=6)
    for k in range(7):
        yy = y0 + 3 + k * 6
        xa = x0 + (k * 11) % 40
        rbox(dr, (xa, yy, min(xa + 18, x0 + rw), yy + 1), (108, 22, 30, 255))


def build_skull(dr, gl):
    """刃根装饰正中的金色头骨（dv30）。

    ★ 必须独立成区：guard_core / gwing_* 全都从 GUARD 区域原点采样，
      头骨画在 GUARD 里会把整片翼一起染成金色（实测截图确认）。
    """
    rx, ry, rw, rh = UV.REGIONS["SKULL"]
    x0, y0 = int(rx), int(ry)
    vgrad(dr, (x0, y0, x0 + rw, y0 + rh), (156, 122, 52), (94, 70, 28), vary=6)
    # 两只眼窝（front 面从区域原点 +1.6 起）
    rbox(dr, (x0 + 2, y0 + 2, x0 + 5, y0 + 6), (28, 22, 14, 255))
    rbox(dr, (x0 + 8, y0 + 2, x0 + 11, y0 + 6), (28, 22, 14, 255))
    rbox(gl, (x0 + 3, y0 + 3, x0 + 4, y0 + 5), (236, 182, 84, 255))
    rbox(gl, (x0 + 9, y0 + 3, x0 + 10, y0 + 5), (236, 182, 84, 255))


def build_blood(dr, gl):
    """血滴垂饰（dv30）：暗红渐变，末端更亮（像刚滴下来的血）。"""
    rx, ry, rw, rh = UV.REGIONS["BLOOD"]
    x0, y0 = int(rx), int(ry)
    vgrad(dr, (x0, y0, x0 + rw, y0 + rh), (62, 10, 16), (168, 26, 30), vary=6)
    rbox(gl, (x0 + 1, y0 + rh - 8, x0 + rw - 1, y0 + rh), (118, 22, 28, 255))


def build_feather(dr, gl):
    """羽毛纹理（dv25）。

    所有羽毛块共用这一块区域（块最大展开 21.6x10.8，32x32 里放不下第二份），
    所以画法要「对任意一块都成立」：
      · 整块铺**近黑的羽色**；
      · 羽毛块的 `top` 面画亮一档的灰 —— 密排后每根羽毛的上缘就有一条高光，
        看起来像羽轴；
      · 最上沿再压 1px 暗青（参考图里第二款的青色发光边）。
    真正让「一根根羽毛」成立的是**几何**（密排小方块 + 羽毛间的缝），
    贴图只负责色调与高光。
    """
    rx, ry, rw, rh = UV.REGIONS["FEATHER"]
    x0, y0 = int(rx), int(ry)
    # 基底：近黑羽色，略带纵向渐变（越往下越暗 → 密排后有层次）
    vgrad(dr, (x0, y0, x0 + rw, y0 + rh), (30, 30, 40), (10, 10, 14), vary=4)
    fx = UV.faces("FEATHER", 7.6, 7.6, 4.3)
    # top 面 = 羽轴上缘高光
    tp = [int(round(v)) for v in fx['top']]
    rbox(dr, (tp[0], tp[1], tp[2], tp[1] + 2), (72, 76, 92, 255))
    # front/back 的最上一行也提亮一点（正面看也有高光）
    for key in ('front', 'back'):
        r = [int(round(v)) for v in fx[key]]
        rbox(dr, (r[0], r[1], r[2], r[1] + 1), (52, 56, 70, 255))
    # 青色发光边（只在最上沿一像素）
    rbox(gl, (tp[0], tp[1], tp[2], tp[1] + 1), (54, 132, 156, 255))


def build_wingroot(dr, gl):
    """翼根骨板：深色甲板 + 骨色边缘，让羽毛明确「长在背上」。"""
    rx, ry, rw, rh = UV.REGIONS["WINGROOT"]
    x0, y0 = int(rx), int(ry)
    vgrad(dr, (x0, y0, x0 + rw, y0 + rh), (40, 36, 48), (18, 16, 24), vary=6)
    fx = UV.faces("WINGROOT", 14.4, 15.6, 9.6)
    tp = [int(round(v)) for v in fx['top']]
    rbox(dr, (tp[0], tp[1], tp[2], tp[1] + 2), (150, 140, 120, 255))
    for key in ('front', 'back'):
        r = [int(round(v)) for v in fx[key]]
        rbox(dr, (r[0], r[1], r[2], r[1] + 1), (104, 98, 88, 255))


def build_rib(dr, gl):
    """翼前缘骨肋：整块骨白 + 一点点高光。"""
    rx, ry, rw, rh = UV.REGIONS["RIB"]
    x0, y0 = int(rx), int(ry)
    vgrad(dr, (x0, y0, x0 + rw, y0 + rh), (206, 198, 182), (140, 132, 118), vary=6)
    rbox(dr, (x0, y0 + 1, x0 + rw, y0 + 2), (232, 226, 212, 255))


def build_wing(dr, gl):
    """骨翼（dv24.4）。

    画法要点：**骨缘靠贴图而不是几何**。
    前一版每段加一根几何骨肋，那些水平小条逐段错开，从正面看是一排
    「阶梯横杠」——整片翼因此被看成瓦片堆。改成在图集里画：

      · front / back 面的**上缘**画一条骨白带 → 从正面看就是翼的上缘骨，
        而且相邻段共用同一张贴图，骨带天然连续、没有台阶；
      · 上缘往下一段做暗红过渡，再往下是翼膜（深紫渐变 + 脉纹）。
    """
    rx, ry, rw, rh = UV.REGIONS["WING"]
    x0, y0 = int(rx), int(ry)
    w, h, d = 12.0, 20.0, 1.6         # 代表尺寸（dv32 翼膜：宽12 长20 厚1.6）
    fx = UV.faces("WING", w, h, d)

    # 先整块铺膜色
    vgrad(dr, (x0, y0, x0 + rw, y0 + rh), (52, 36, 72), (15, 11, 24), vary=5)

    for key in ("front", "back"):
        r = fx[key]
        # 上缘骨带（2px，别太宽太亮 —— 太抢眼会把整片翼压成「一道白边」）
        rbox(dr, (r[0], r[1], r[2], r[1] + 2), (176, 168, 152, 255))
        rbox(dr, (r[0], r[1] + 2, r[2], r[1] + 3), (116, 108, 98, 255))
        # 骨带下方一道暗红过渡（翼膜的"根"）
        rbox(dr, (r[0], r[1] + 3, r[2], r[1] + 4), (92, 30, 40, 255))
        # 横向脉纹（比底色亮一点点，别太硬）
        for k in range(1, 5):
            yy = r[1] + 6 + k * 3
            if yy >= r[3]:
                break
            rbox(dr, (r[0], yy, r[2], yy + 1), (60, 42, 84, 255))
    # 中段一条发光血纹（暗处发亮的翼脉）
    r = fx["front"]
    yy = r[1] + 7
    rbox(dr, (r[0], yy, r[2], yy + 1), (116, 34, 44, 255))
    rbox(gl, (r[0], yy, r[2], yy + 1), (172, 50, 62, 255))
    # 上缘骨带也微弱自发光，暗处能看出骨架轮廓
    rbox(gl, (r[0], r[1], r[2], r[1] + 1), (70, 66, 58, 255))


def build_tentacle(dr, gl):
    """触手节（dv21.1：节变小到 3.4px，贴图也要跟着收）。

    每个小方块独立采样这块区域，所以画成「均匀的深紫 + 一道极细血纹」，
    不要画环纹 —— 环纹会在每个节上重复，看起来像一串断开的齿轮。
    面只有 3.4px 宽，血纹最多占 1px，否则整条触手会看起来是红的。
    """
    # ★ dv21.1：节被拉长后尺寸不固定（沿走向 3~5px），逐面矩形会有多种，
    #   所以整块区域都铺成「深紫渐变 + 一条竖向血纹」，任何子矩形落上去都合理。
    rx, ry, rw, rh = UV.REGIONS["TENT"]
    x0, y0 = int(rx), int(ry)
    full = (x0, y0, x0 + rw, y0 + rh)
    vgrad(dr, full, (60, 44, 86), (20, 16, 32), vary=4)
    vein = x0 + rw // 3
    rbox(dr, (vein, y0, vein + 1, y0 + rh), (96, 30, 40, 255))
    rbox(gl, (vein, y0, vein + 1, y0 + rh), (150, 44, 56, 255))


def build_warden():
    im = Image.new("RGBA", (UV.TEX_W, UV.TEX_H), (0, 0, 0, 0))
    gl = Image.new("RGBA", (UV.TEX_W, UV.TEX_H), (0, 0, 0, 0))
    dr = ImageDraw.Draw(im)
    g = ImageDraw.Draw(gl)

    build_head(dr, g)
    build_torso(dr, g)
    build_limb(dr, g, "ARM", 12, 36, 12, glow_edge=False)
    build_limb(dr, g, "LEG", 12, 36, 12, glow_edge=False)
    build_crown(dr, g)
    build_scythe(dr, g)
    build_blade(dr, g)
    build_edge(dr, g)
    build_guard(dr, g)
    build_blood(dr, g)
    build_skull(dr, g)
    build_feather(dr, g)
    build_wingroot(dr, g)
    build_wing(dr, g)
    return im, gl


def build_sentinel(im, gl):
    """哨卫：去饱和偏冷，保持「未腐化」的观感对比。"""
    im = im.copy()
    px = im.load()
    for y in range(UV.TEX_H):
        for x in range(UV.TEX_W):
            r, gg, b, a = px[x, y]
            if a == 0:
                continue
            gray = 0.38 * r + 0.40 * gg + 0.22 * b
            px[x, y] = (cl(gray * 0.92), cl(gray * 0.98), cl(gray * 1.18), a)
    d = ImageDraw.Draw(im)
    d.rectangle([9, 10, 10, 11], fill=(150, 224, 236, 255))
    d.rectangle([13, 10, 14, 11], fill=(150, 224, 236, 255))
    gl = gl.copy()
    g = ImageDraw.Draw(gl)
    g.rectangle([0, 0, UV.TEX_W - 1, UV.TEX_H - 1], fill=(0, 0, 0, 0))
    g.rectangle([9, 10, 10, 11], fill=(150, 224, 236, 255))
    g.rectangle([13, 10, 14, 11], fill=(150, 224, 236, 255))
    return im, gl


def self_check(im, gl):
    print("  区域抽样（左上角像素）：")
    for label, reg in UV.REGIONS.items():
        x, y = reg[0], reg[1]
        r, gg, b, a = im.load()[x, y]
        ga = gl.load()[x, y][3]
        print(f"    {label:8s} ({x:3d},{y:3d}) rgba({r:3d},{gg:3d},{b:3d},{a}) "
              f"{'发光' if ga else '    '}")


def main():
    os.makedirs(OUT, exist_ok=True)
    im, gl = build_warden()
    im.save(os.path.join(OUT, "warden.png"))
    gl.save(os.path.join(OUT, "warden_glowmask.png"))
    print(f"  warden.png {im.width}x{im.height} + warden_glowmask.png")
    self_check(im, gl)

    si, sg = build_sentinel(im, gl)
    si.save(os.path.join(OUT, "warden_sentinel.png"))
    sg.save(os.path.join(OUT, "warden_sentinel_glowmask.png"))
    print("  warden_sentinel.png + warden_sentinel_glowmask.png")


if __name__ == '__main__':
    print("生成实体贴图（dv21 人形 + 触手）->")
    main()
