#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
遗落之主的**羽毛翅膀**：自由三角网格生成器（dv26）。

=== 为什么整个换掉 GeckoLib 的翅膀 ===
用户连续否掉了 7 版方块翼：
  「不要拿这种方块来拼成建模」「就是一格一格的」「建模就是建模，连起来的那种」
根因是**结构性的**：GeckoLib（以及原版实体模型管线）的几何元素**只有长方体**，
无论怎么排都是「一格一格」。羽毛这种连续的流线形状，用方块永远做不出来。

所以本版**不走 GeckoLib 的几何**，改为：
  · 用参数曲面生成一片片连续的**羽片**（三角网格，可任意弯曲收尖）；
  · 网格数据落到 `models/entity/lord_wing.json`，由 Java 侧读取；
  · 渲染时在 `WardenBossRenderer#render` 里用 VertexConsumer 直接画三角形
    （不经过任何方块模型管线）。

=== 羽毛的数学形状 ===
每根羽毛是一条**参数化带面**：
    P(u, v) = 根点 + dir·(L·u) + perp·(w(u)·v) + 法向·(cup(u))
  · u ∈ [0,1] 沿羽长，v ∈ [-1,1] 沿羽宽；
  · 半宽 w(u) = W·(0.5+0.5·sin(πu))·√(1-u³) —— 根略窄、中段最宽、尖端收成尖；
  · cup(u)：羽片沿长度方向的**杯状弯曲**，让羽片不是一张平板，
    暗处才有羽片之间相互遮挡的层次；
  · perp 取「垂直于羽轴、且落在竖直平面内」的方向，所以羽毛是**立着**的，
    与参考图（竖起的黑羽翼）一致。

运行：python3 tools/gen_wing_mesh.py
产物：
  src/main/resources/assets/lost_realm/models/entity/lord_wing.json
  src/main/resources/assets/lost_realm/textures/entity/lord_wing.png
"""

import json
import math
import os
import random

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'lost_realm')

SEED = 20261013

# ------------------------------------------------------------
# 羽毛参数（单位 = 模型像素，1 格 = 16px；与人体骨架同一坐标系）
# ------------------------------------------------------------
ROOT_Y = 33.0            # 翼根高度（肩胛）
ROOT_X = 2.4             # 翼根距中轴
ROOT_Z = 3.2             # 翼根前后（躯干背面 z=+3）

FLIGHT_N = 13            # 主飞羽根数
F_ANG0, F_ANG1 = -16.0, 44.0      # 羽轴方向角（0=水平向外，正=上扬）
F_LEN0, F_LEN1 = 26.0, 66.0       # 羽长（内短外长）
F_W0, F_W1 = 4.6, 6.2             # 半宽（外羽更宽）

COVERT_N = 9             # 覆羽（压在根部的短羽，做层次）
C_ANG0, C_ANG1 = -26.0, 20.0
C_LEN0, C_LEN1 = 16.0, 30.0
C_W = 4.4

U_SEG = 12               # 羽长方向细分数
V_SEG = 2                # 羽宽方向细分数

CUP = 0.9                # 杯状弯曲幅度（px）
SWEEP = 0.22             # 每根羽毛向后（+z）的偏移系数（外侧越靠后 → 后掠）


def _feather(side, ang_deg, length, wmax, z0, zdrift, seed):
    """生成一根羽毛的顶点与索引。

    :param side:  -1 右翼 / +1 左翼
    :param ang_deg: 羽轴方向角（度），正 = 上扬
    :param length: 羽长
    :param wmax: 半宽峰值
    :param z0: 根部 z
    :param zdrift: 从根到尖的 z 漂移（后掠）
    :return: (verts, index)，verts 为 (x, y, z, u, v)
    """
    rng = random.Random(seed)
    a = math.radians(ang_deg)
    dx, dy = math.cos(a) * side, math.sin(a)
    # 羽宽方向：垂直于羽轴、且位于「竖直平面」内
    # （羽轴在 x-y 平面内，所以垂直方向也在 x-y 平面内，羽毛因此是竖着的）
    px, py = -dy, dx

    verts = []
    for i in range(U_SEG + 1):
        u = i / U_SEG
        # 半宽：根略窄 → 中段最宽 → 尖端收尖
        w = wmax * (0.5 + 0.5 * math.sin(math.pi * u)) * math.sqrt(max(0.0, 1.0 - u ** 3))
        w = max(w, 0.18)
        cx = ROOT_X * side + dx * length * u
        cy = ROOT_Y + dy * length * u
        cz = z0 + zdrift * u
        # 杯状弯曲 + 一点随机抖动（避免每根都一模一样）
        cup = CUP * math.sin(math.pi * u) + rng.uniform(-0.12, 0.12)
        for j in range(V_SEG + 1):
            v = -1.0 + 2.0 * j / V_SEG
            x = cx + px * w * v
            y = cy + py * w * v
            z = cz + cup * (1.0 - abs(v))          # 中间鼓、两边薄
            verts.append((round(x, 3), round(y, 3), round(z, 3),
                          round(0.5 + 0.5 * v, 4), round(u, 4)))

    index = []
    for i in range(U_SEG):
        for j in range(V_SEG):
            a0 = i * (V_SEG + 1) + j
            b0 = a0 + 1
            c0 = a0 + (V_SEG + 1)
            d0 = c0 + 1
            index += [a0, c0, b0, b0, c0, d0]
    return verts, index


def build():
    groups = []
    rng = random.Random(SEED)
    for tag, side in (("r", -1.0), ("l", 1.0)):
        # --- 主飞羽 ---
        for i in range(FLIGHT_N):
            t = i / (FLIGHT_N - 1)
            ang = F_ANG0 + (F_ANG1 - F_ANG0) * t + rng.uniform(-1.5, 1.5)
            ln = F_LEN0 + (F_LEN1 - F_LEN0) * t + rng.uniform(-2.5, 2.5)
            wm = F_W0 + (F_W1 - F_W0) * t
            z0 = ROOT_Z + i * 0.55 + rng.uniform(-0.1, 0.1)
            v, ix = _feather(side, ang, ln, wm, z0, SWEEP * ln, SEED + i * 31)
            groups.append({"name": f"flight_{tag}_{i}",
                           "pivot": [ROOT_X * side, ROOT_Y, z0],
                           "verts": v, "index": ix})
        # --- 覆羽（短，压在羽毛根部之上，z 更靠前） ---
        for i in range(COVERT_N):
            t = i / (COVERT_N - 1)
            ang = C_ANG0 + (C_ANG1 - C_ANG0) * t + rng.uniform(-1.2, 1.2)
            ln = C_LEN0 + (C_LEN1 - C_LEN0) * t + rng.uniform(-1.2, 1.2)
            z0 = ROOT_Z - 1.6 + i * 0.35
            v, ix = _feather(side, ang, ln, C_W, z0, 0.12 * ln, SEED + 900 + i * 17)
            groups.append({"name": f"covert_{tag}_{i}",
                           "pivot": [ROOT_X * side, ROOT_Y, z0],
                           "verts": v, "index": ix})

    nv = sum(len(g["verts"]) for g in groups)
    nt = sum(len(g["index"]) // 3 for g in groups)
    return groups, nv, nt


# ============================================================
# 贴图：一根羽毛的纹理（黑色羽片 + 中轴 + 尖端）
# ============================================================

def build_texture(path):
    from PIL import Image, ImageDraw
    im = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # u（羽长）映射到贴图的 y，v（羽宽）映射到 x。
    # 羽片主体：近黑，越靠羽轴越深、边缘略亮（受光边）
    # ★ 亮度经过一轮实拍校正：初版主体只有 (26,26,33)，加上 0.34 的暗部系数
    #   后整对翼几乎全黑、看不出羽片层次。这里整体提亮约一倍。
    # ★ 羽片剖面：**中间亮、两侧羽缘压暗**。
    #   上一版把羽缘画亮，结果相邻羽片糊成一整块黑布（"一束刀片"）；
    #   压暗羽缘之后，每片羽毛之间有暗缝，才数得出一片片羽毛。
    for y in range(256):
        t = y / 255.0                       # 0 = 根, 1 = 尖
        base = (74 - int(18 * t), 72 - int(18 * t), 88 - int(20 * t))
        for x in range(256):
            vv = abs(x / 255.0 - 0.5) * 2.0   # 0 = 羽轴, 1 = 羽缘
            k = 1.16 - 0.62 * vv
            c = (max(6, min(255, int(base[0] * k))),
                 max(6, min(255, int(base[1] * k))),
                 max(8, min(255, int(base[2] * k))))
            d.point((x, y), c + (255,))
    # 羽轴：一条细高光
    d.rectangle([124, 0, 131, 255], fill=(112, 116, 138, 255))
    # 羽尖的青色发光边（只留最末一小段，避免整片尖端变亮蓝三角）
    for y in range(240, 256):
        f = (y - 240) / 16.0
        d.rectangle([0, y, 255, y],
                    fill=(int(52 + 66 * f), int(86 + 76 * f), int(104 + 84 * f), 255))
    im.save(path)
    return im


def main():
    groups, nv, nt = build()
    os.makedirs(os.path.join(RES, 'models', 'entity'), exist_ok=True)
    out = os.path.join(RES, 'models', 'entity', 'lord_wing.json')
    with open(out, 'w', encoding='utf-8') as f:
        json.dump({"texture": "lost_realm:textures/entity/lord_wing.png",
                   "groups": groups}, f, separators=(',', ':'))
    tex = os.path.join(RES, 'textures', 'entity', 'lord_wing.png')
    os.makedirs(os.path.dirname(tex), exist_ok=True)
    build_texture(tex)
    print(f"羽翼网格 -> {out}")
    print(f"  羽片 {len(groups)} 片  顶点 {nv}  三角形 {nt}")
    print(f"  贴图 -> {tex}")
    size = os.path.getsize(out)
    print(f"  JSON {size / 1024:.1f} KB")


if __name__ == '__main__':
    main()
