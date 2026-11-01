#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
遗落之主贴图 UV 布局 —— **gen_geo.py 与 gen_texture.py 的唯一事实来源**。

=== 为什么要有这个模块 ===
dv13 翻车过：贴图把触手区画到了左腿/左臂的位置上，渲染出来手脚都是紫棒子。
根因是「几何的 UV 偏移」与「贴图画在哪里」是两份**各自手工维护**的常量，
改一边忘一边就串色。

dv16 把布局收敛到这里：`REGIONS` 是唯一的坐标表，
两个生成器都 import 它，物理上不可能再对不上。

=== 画布 ===
512×512（dv28 模型放大到 96px 后，人形部件的展开尺寸翻倍）。

=== 盒展开（GeckoLib 约定）===
一个 w×h×d 的 cube 会占用贴图上一块 `2*(d+w) × (d+h)` 的矩形，
内部按「十字展开图」排布六个面：

    [u+d,     v]     上 (w × d)
    [u+d+w,   v]     下 (w × d)
    [u,       v+d]   右 (d × h)
    [u+d,     v+d]   前 (w × h)     ← 实体朝 -z，这是正脸
    [u+d+w,   v+d]   左 (d × h)
    [u+2d+w,  v+d]   后 (w × h)

`faces()` 把它程序化，贴图生成器据此往正确的面上画眼睛 / 符纹。
"""

TEX_W = 512
TEX_H = 512

# ============================================================
# 区域表：name -> (x, y, w, h)
#
# 排布成两行，互不重叠（见 verify()）：
#   货架 1  y 0..96      人形 + 头冠 + 镰刀
#   货架 2  y 104..136   羽翼（羽毛 / 翼根 / 爪）
#
# 每块区域的 (w,h) 必须「≥ 该族 cube 的最大展开尺寸」，
# 由 gen_geo.py 的 verify_uv_fit() 在落盘前强制校验。
# ============================================================

REGIONS = {
    # ---- 人形（dv28：模型放大到 96px = 6 格；「空间翻倍、细节尺寸不变」）----
    "HEAD":     (0,   0,  96, 48),   # 头 24x24x24  -> 展开 96x48
    "TORSO":    (96,  0,  72, 48),   # 躯干 24x36x12 -> 展开 72x48
    "ARM":      (168, 0,  48, 48),   # 臂 12x36x12   -> 展开 48x48
    "LEG":      (216, 0,  48, 48),   # 腿 12x36x12   -> 展开 48x48
    # ---- 三阶段专属 ----
    "HORN":     (264, 0,  32, 32),   # 角 7.2x19.2x7.2 -> 展开 28.8x26.4
    "SCYTHE":   (296, 0,  44, 176),  # ★ dv30 镰杆 5.5x156x5.5 -> 展开 22x161.5
    # ---- 巨镰刀刃（dv30 按参考图量化重建）----
    # 长条最大 33x11.4x6 -> 展开 2*(6+33)=78 x (6+11.4)=17.4
    "BLADE":    (344, 0,  96, 48),
    # 血红刃口（独立几何层）长条 33x2.6x5 -> 展开 2*(5+33)=76 x (5+2.6)=7.6
    "EDGE":     (344, 48, 80, 16),
    # ---- 羽翼（二/三阶段）----
    # ★ 羽毛方块**尺寸不变**（13.6x6.4x2.6 -> 展开 32.4x9），所以区域也不变：
    #   模型空间翻倍后方块相对更小 = 细节更细，这才是放大的意义。
    "FEATHER":  (0, 104,  64, 28),
    "WINGROOT": (64, 104, 64, 32),   # 翼根 14.4x15.6x9.6 -> 展开 48x25
    "RIB":      (128, 104, 48, 32),
    # ---- dv30 新增：刃根装饰件 / 柄上血滴垂饰 ----
    # 装饰翼骨最大 21x5.2x4.2 -> 展开 2*(4.2+21)=50.4 x (4.2+5.2)=9.4
    "GUARD":    (128, 144, 64, 48),
    # 血滴 2x30x1.8 -> 展开 2*(1.8+2)=7.6 x (1.8+30)=31.8
    "BLOOD":    (0,   144, 32, 48),
    # 头骨 5.2x5.2x1.6 -> 展开 2*(1.6+5.2)=13.6 x (1.6+5.2)=6.8
    # ★ 必须独立成区：guard 那几块都从区域原点采样，共用就会被整片染成金色
    "SKULL":    (192, 144, 32, 20),
    # ---- 以下为已弃用造型预留（不再生成，保留以免旧引用报错）----
    "WING":     (0, 200,  64, 40),
    "TENT":     (64, 200, 32, 40),
    "SPARE":    (104, 200, 64, 40),
}

MAX_UNFOLD = {k: (v[2], v[3]) for k, v in REGIONS.items()}


def uv(name):
    """区域左上角，直接喂给 cube 的 uv 字段。"""
    x, y, _, _ = REGIONS[name]
    return (x, y)


def rect(name):
    return REGIONS[name]


def faces(name, w, h, d):
    """
    返回该区域在给定 cube 尺寸下的六面矩形（贴图像素坐标，左上原点）。

    :return: {"top":(x0,y0,x1,y1), "bottom":..., "right":..., "front":..., "left":..., "back":...}
    """
    u, v, _, _ = REGIONS[name]
    return {
        "top":    (u + d,       v,     u + d + w,     v + d),
        "bottom": (u + d + w,   v,     u + 2 * d + w, v + d),
        "right":  (u,           v + d, u + d,         v + d + h),
        "front":  (u + d,       v + d, u + d + w,     v + d + h),
        "left":   (u + d + w,   v + d, u + 2 * d + w, v + d + h),
        "back":   (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def verify():
    """所有区域两两不重叠 —— 落盘前由 gen_geo.py 调用。"""
    bad = []
    names = list(REGIONS)
    for i in range(len(names)):
        for j in range(i + 1, len(names)):
            ax, ay, aw, ah = REGIONS[names[i]]
            bx, by, bw, bh = REGIONS[names[j]]
            if ax < bx + bw and bx < ax + aw and ay < by + bh and by < ay + ah:
                bad.append(f"{names[i]} <-> {names[j]}")
    # 越界检查
    for n, (x, y, w, h) in REGIONS.items():
        if x + w > TEX_W or y + h > TEX_H:
            bad.append(f"{n} 超出画布 {TEX_W}x{TEX_H}")
    return bad


if __name__ == "__main__":
    bad = verify()
    if bad:
        print("!! UV 布局有问题：")
        for b in bad:
            print("   -", b)
        raise SystemExit(1)
    print(f"UV 布局 OK：{len(REGIONS)} 个区域两两不重叠，画布 {TEX_W}x{TEX_H}")
    for n, (x, y, w, h) in REGIONS.items():
        print(f"  {n:9s} ({x:3d},{y:3d})  {w:2d}x{h:2d}")
