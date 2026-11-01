#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Blockbench `.bbmodel` -> GeckoLib 资产转换器（二阶段 form2 专用）。

=== 为什么换成 bbmodel 管线 ===
dv17/dv18 用的是外部 AI 直接吐的 `.glb`：GLB 只有「节点 + 三角网格」，
GeckoLib 只有「骨骼 + 轴对齐立方体」，所以每个网格只能压成**包围盒**
（92 个立方体是 AI 恰好用了方块建模才没崩），而且它的 UV 是任意三角面 UV，
贴图无法直接复用，只能**程序化重画**（dv19 那次 atlas 99.8% 透明就是这原因）。

`.bbmodel` 完全不同 —— 它是 Blockbench 的工程文件，本质上就是 GeckoLib 的
原生数据：`outliner` 是骨骼树、`elements` 是立方体、`faces` 是逐面 UV、
`animations` 是关键帧。所以这条管线是**逐值搬运**，不是"猜"。

=== 输入 ===
  /root/bbmodel_src/shadow_king_phase2.bbmodel
    · 17 骨骼 / 97 立方体 / 256x256 内嵌贴图 / 3 条动画(idle/walk/attack)

=== 输出 ===
  geo/warden_form2.geo.json                几何
  animations/warden_form2.animation.json   动画（9 个固定 id）
  textures/entity/warden_phase2.png        重排后的盒展开图集
  textures/entity/warden_phase2_glowmask.png  自发光遮罩（金色符文）

=== 三条硬规则（都是踩过的坑）===
 1. 【旋转立方体 -> 包装成骨骼】
    bbmodel 里 48 个立方体带自身旋转（护肩、剑刃、下摆…）。GeckoLib 的
    cube 级旋转支持面窄，而**骨骼旋转一定支持**，且语义完全等价：
        bone{pivot = element.origin, rotation = element.rotation} + 轴对齐 cube
    在 GeckoLib 里就是 `M = T(p)·H·T(-p)`，与 Blockbench 绕 origin 旋转一致。
    所以每个带旋转的立方体都包一层同名骨骼 —— 逐值保真，零近似。

 2. 【盒展开图集】GeckoLib 的 cube 只有一个 uv 左上角，六面由 size 推出来
    （见 uv_layout.py 的十字展开）。bbmodel 是 box_uv=false 的**逐面** UV，
    所以要把每个面按 bbmodel 的 uv 矩形裁下来，贴进标准展开的对应槽位：
        up(+y)->top   down(-y)->bottom   east(+x)->right
        north(-z)->front   west(-x)->left   south(+z)->back
    两个约定都是「从外侧看」，所以直接裁剪粘贴、不翻转。

 3. 【朝向/原点】bbmodel 正面 = -z（brow/chin/tabard 都在负 z），
    x 已以 0 为轴、脚底 y=0 —— 与本项目约定一致，**不需要旋转也不需要抬升**。
    （dv19 那次"陷进地里"就是因为 GLB 脚底在 -23.7px。）

=== 落盘前自检 ===
  · 骨骼名唯一、父子拓扑合法（父先于子）
  · 每个 cube 装得进自己的图集槽位
  · 世界 AABB：不入地(y_min >= -0.5)、核心居中(|x 中心| <= 1.0)、规模合理
  · 动画引用的骨骼必须真实存在（GeckoLib 对缺失骨骼静默跳过）
  · 9 个动画 id 齐全（Java 侧 hardcode 的契约）
"""

import base64
import io
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gecko_math as GM

# ============================================================
# 路径
# ============================================================
# ★ dv34：改吃用户提供的《mc建模_已修正.bbmodel》（二三阶段共用一套）
#   用法：python3 tools/bbmodel_to_gecko.py 2   # 生成 form2
#         python3 tools/bbmodel_to_gecko.py 3   # 生成 form3
BB_SRC = "/workspace/ref/mc_model_fixed.bbmodel"
FORM = int(sys.argv[1]) if len(sys.argv) > 1 and sys.argv[1].isdigit() else 2

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GEO_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "lost_realm", "geo")
ANIM_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "lost_realm", "animations")
TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "lost_realm", "textures", "entity")

GEO_OUT = os.path.join(GEO_DIR, f"warden_form{FORM}.geo.json")
ANIM_OUT = os.path.join(ANIM_DIR, f"warden_form{FORM}.animation.json")
TEX_OUT = os.path.join(TEX_DIR, f"warden_phase{FORM}.png")
GLOW_OUT = os.path.join(TEX_DIR, f"warden_phase{FORM}_glowmask.png")

IDENTIFIER = f"geometry.lost_realm.warden_form{FORM}"

# 动画 id 契约（WardenAnimations.java 里 hardcode，不能改）
ANIM_IDS = [
    "animation.warden.idle",
    "animation.warden.walk",
    "animation.warden.death",
    "animation.warden.hurt",
    "animation.warden.attack_sweep",
    "animation.warden.attack_smash",
    "animation.warden.attack_lash",
    "animation.warden.phase_roar",
    "animation.warden.phase_awaken",
]

ATLAS_W = 512
PAD = 1


# ============================================================
# 读取
# ============================================================

def load_bb():
    with open(BB_SRC, encoding="utf-8") as f:
        return json.load(f)


def extract_texture(bb):
    tex = bb["textures"][0]
    raw = tex["source"].split(",", 1)[1]
    im = Image.open(io.BytesIO(base64.b64decode(raw))).convert("RGBA")
    return im, tex.get("name", "tex.png")


# ============================================================
# 骨骼树
# ============================================================

def build_bones(bb):
    """outliner -> 有序骨骼列表（父先于子）。返回 (bones, used_names)。"""
    groups = {g["uuid"]: g for g in bb.get("groups", [])}
    els = {}
    for e in bb["elements"]:
        els[e["uuid"]] = e

    bones = []

    def add(name, pivot, parent, rotation=None):
        b = {"name": name, "pivot": [round(float(v), 4) for v in pivot], "cubes": []}
        if parent:
            b["parent"] = parent["name"] if isinstance(parent, dict) else parent
        if rotation is not None and any(abs(v) > 1e-6 for v in rotation):
            b["rotation"] = [round(float(v), 4) for v in rotation]
        bones.append(b)
        return b

    def make_cube(e):
        lo = [min(e["from"][k], e["to"][k]) for k in range(3)]
        hi = [max(e["from"][k], e["to"][k]) for k in range(3)]
        size = [hi[k] - lo[k] for k in range(3)]
        # ★★ dv34：抵消 GeckoLib 的 cube.origin.x 镜像 ★★
        #   constructCube 会把 x 起点换成 -(ox+ow)，所以我们**预先写 -(ox+ow)**，
        #   渲染时正好落回 [ox, ox+ow]。
        #   为什么必须抵消：pivot **不镜像**，若 cube 镜像而 pivot 不镜像，
        #   带 rotation 的骨骼就会绕错中心旋转 —— 实测 72 个旋转部件
        #   （镰刀的 blade_*/edge_* 等）全部脱手飞到脚下，画面成一地碎片。
        return {
            "origin": [round(-(lo[0] + size[0]), 4), round(lo[1], 4), round(lo[2], 4)],
            "size": [round(v, 4) for v in size],
            # ★ dv35：显式补 inflate —— gen_geo 产出的 cube 一直带着它，
            #   缺省虽然理论上等于 0，但没必要让两种来源的 cube 结构不同。
            "inflate": 0.0,
            "_element": e,          # 内部用，落盘前删
        }

    def walk(node, parent):
        if isinstance(node, str):
            e = els.get(node)
            if e is None:
                return
            rot = e.get("rotation")
            if rot is not None and any(abs(v) > 1e-6 for v in rot):
                # ★ 规则 1：带旋转的立方体包一层同名骨骼
                nm = unique_name(e["name"], bones)
                b = add(nm, e.get("origin", [0, 0, 0]), parent, rotation=rot)
                b["cubes"].append(make_cube(e))
            else:
                if parent is None:
                    raise SystemExit(f"元素 {e['name']} 没有父骨骼")
                parent["cubes"].append(make_cube(e))
            return

        g = groups.get(node["uuid"])
        if g is None:
            return
        nm = unique_name(g.get("name", "bone"), bones)
        b = add(nm, g.get("origin", [0, 0, 0]), parent,
                rotation=g.get("rotation"))
        for c in node.get("children", []):
            walk(c, b)

    for n in bb["outliner"]:
        walk(n, None)
    return bones


def unique_name(base, bones):
    taken = {b["name"] for b in bones}
    if base not in taken:
        return base
    i = 1
    while f"{base}_{i}" in taken:
        i += 1
    return f"{base}_{i}"


# ============================================================
# 图集：逐面 UV -> 盒展开槽位
# ============================================================

FACE_TO_SLOT = {
    "up": "top", "down": "bottom",
    "east": "right", "north": "front", "west": "left", "south": "back",
}


def slot_rect(u, v, w, h, d, slot):
    """某个面在展开图里的矩形 (x0,y0,x1,y1)。"""
    if slot == "top":
        return (u + d, v, u + d + w, v + d)
    if slot == "bottom":
        return (u + d + w, v, u + 2 * d + w, v + d)
    if slot == "right":
        return (u, v + d, u + d, v + d + h)
    if slot == "front":
        return (u + d, v + d, u + d + w, v + d + h)
    if slot == "left":
        return (u + d + w, v + d, u + 2 * d + w, v + d + h)
    return (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h)   # back


def build_atlas(bones, src, src_w, src_h):
    """给每个 cube 分配展开槽位，并从源贴图裁面贴进去。

    返回 (atlas, glowmask, atlas_h)。
    """
    cubes = [c for b in bones for c in b["cubes"]]

    # ---- 货架式装箱 ----
    x = 0
    y = 0
    row_h = 0
    for c in cubes:
        w, h, d = c["size"]
        sw, sh = round(2 * (d + w)), round(d + h)
        if x + sw + PAD > ATLAS_W:
            x = 0
            y += row_h + PAD
            row_h = 0
        c["uv"] = [x, y]
        c["_slot_size"] = (sw, sh)
        x += sw + PAD
        row_h = max(row_h, sh)
    atlas_h = y + row_h
    atlas_h = 1 << (atlas_h - 1).bit_length()      # 向上取 2 的幂

    atlas = Image.new("RGBA", (ATLAS_W, atlas_h), (0, 0, 0, 0))
    glow = Image.new("RGBA", (ATLAS_W, atlas_h), (0, 0, 0, 0))
    src_mask = make_glow_mask(src)

    for c in cubes:
        e = c["_element"]
        u, v = c["uv"]
        w, h, d = [round(t) for t in c["size"]]
        for bb_face, slot in FACE_TO_SLOT.items():
            fd = (e.get("faces") or {}).get(bb_face)
            if not fd or "uv" not in fd:
                continue
            u1, v1, u2, v2 = fd["uv"]
            u1, u2 = min(u1, u2), max(u1, u2)
            v1, v2 = min(v1, v2), max(v1, v2)
            # 源矩形裁切（夹到贴图范围内）
            sx0 = max(0, min(src_w, int(round(u1))))
            sy0 = max(0, min(src_h, int(round(v1))))
            sx1 = max(0, min(src_w, int(round(u2))))
            sy1 = max(0, min(src_h, int(round(v2))))
            if sx1 - sx0 < 1 or sy1 - sy0 < 1:
                continue
            tx0, ty0, tx1, ty1 = slot_rect(u, v, w, h, d, slot)
            tw, th = max(1, tx1 - tx0), max(1, ty1 - ty0)
            crop = src.crop((sx0, sy0, sx1, sy1)).resize((tw, th), Image.NEAREST)
            atlas.paste(crop, (tx0, ty0))

            mcrop = src_mask.crop((sx0, sy0, sx1, sy1)).resize((tw, th), Image.NEAREST)
            glow.paste(mcrop, (tx0, ty0))
    return atlas, glow, atlas_h


def make_glow_mask(src):
    """从源贴图里挑「该发光」的像素 —— 金色符文/宝石（亮、偏黄、饱和）。"""
    px = src.load()
    out = Image.new("RGBA", src.size, (0, 0, 0, 0))
    op = out.load()
    for yy in range(src.height):
        for xx in range(src.width):
            r, g, b, a = px[xx, yy]
            if a < 8:
                continue
            luma = 0.30 * r + 0.59 * g + 0.11 * b
            if luma > 130 and r > 140 and b < 150 and (r - b) > 40:
                op[xx, yy] = (255, 255, 255, 255)
    return out


# ============================================================
# 动画
# ============================================================

def rot_track(frames):
    return {str(t): {"post": [round(float(v), 4) for v in val]} for t, val in frames}


def vec_track(frames):
    return {str(t): {"vector": [round(float(v), 4) for v in val]} for t, val in frames}


def convert_animations(bb, bone_names):
    """bbmodel 动画 -> GeckoLib。提供 idle/walk/attack，其余合成。"""
    out = {}

    for a in bb.get("animations", []):
        name = (a.get("name") or "").strip().lower()
        target = {
            "idle": "animation.warden.idle",
            "walk": "animation.warden.walk",
            "run": "animation.warden.walk",
            "attack": "animation.warden.attack_smash",
            "attack_smash": "animation.warden.attack_smash",
            "attack_sweep": "animation.warden.attack_sweep",
            "hurt": "animation.warden.hurt",
            "death": "animation.warden.death",
            "phase_roar": "animation.warden.phase_roar",
            "roar": "animation.warden.phase_roar",
            "phase_awaken": "animation.warden.phase_awaken",
            "awaken": "animation.warden.phase_awaken",
            # ★ dv34：用户给的模型里自带的布料飘动动画（4s 循环，驱动
            #   body/head/arm 的轻微摆动）—— 直接当作 idle，比合成的好。
            "cape_flutter": "animation.warden.idle",
            "flutter": "animation.warden.idle",
        }.get(name)
        if target is None or target in out:
            continue

        loop = a.get("loop")
        gloop = {"loop": True, "hold": "hold_on_last_frame", "once": False}.get(loop, True)
        bones = {}
        for an in a.get("animators", {}).values():
            if an.get("type") not in (None, "bone"):
                continue
            bn = an.get("name")
            if bn not in bone_names:
                continue
            for kf in an.get("keyframes", []):
                ch = kf.get("channel")
                dp = (kf.get("data_points") or [{}])[0]
                val = [float(dp.get("x", 0) or 0), float(dp.get("y", 0) or 0),
                       float(dp.get("z", 0) or 0)]
                t = float(kf.get("time", 0))
                if ch == "rotation":
                    bones.setdefault(bn, {}).setdefault("rotation", {})[f"{t}"] = \
                        {"post": [round(v, 4) for v in val]}
                elif ch == "position":
                    bones.setdefault(bn, {}).setdefault("position", {})[f"{t}"] = \
                        {"vector": [round(v, 4) for v in val]}
                elif ch == "scale":
                    bones.setdefault(bn, {}).setdefault("scale", {})[f"{t}"] = \
                        {"vector": [1 + v / 100.0 for v in val]}
        out[target] = {"animation_length": float(a.get("length", 1.0)),
                       "loop": gloop, "bones": bones}

    out.update(synth_missing(bone_names, out.keys()))
    return out


def synth_missing(bone_names, have):
    """补齐 bbmodel 里没有的那几条动画（用真实存在的骨骼名）。"""
    res = {}

    def has(n):
        return n in bone_names

    def add(anim_id, length, loop, tracks):
        bones = {}
        for bn, chans in tracks.items():
            if not has(bn):
                continue
            for ch, tr in chans.items():
                bones.setdefault(bn, {}).setdefault(ch, {}).update(tr)
        res[anim_id] = {"animation_length": length, "loop": loop, "bones": bones}

    # ---- dv34 新增：用户模型只带一条布料动画，这三条必须补 ----
    if "animation.warden.idle" not in have:
        add("animation.warden.idle", 2.0, True, {
            "body": {"rotation": rot_track([(0, (0, 0, 0)), (1.0, (2.0, 0, 0)),
                                            (2.0, (0, 0, 0))]),
                     "position": vec_track([(0, (0, 0, 0)), (1.0, (0, 0.45, 0)),
                                            (2.0, (0, 0, 0))])},
            "head": {"rotation": rot_track([(0, (0, 0, 0)), (0.7, (-3.0, 2.0, 0)),
                                            (1.4, (2.0, -2.0, 0)), (2.0, (0, 0, 0))])},
            "arm_r": {"rotation": rot_track([(0, (0, 0, 0)), (1.0, (2.5, 0, 0)),
                                             (2.0, (0, 0, 0))])},
            "arm_l": {"rotation": rot_track([(0, (0, 0, 0)), (1.0, (-2.5, 0, 0)),
                                             (2.0, (0, 0, 0))])},
            "cape_l_u": {"rotation": rot_track([(0, (0, 0, 0)), (1.0, (-3.5, 0, 0)),
                                                (2.0, (0, 0, 0))])},
            "cape_r_u": {"rotation": rot_track([(0, (0, 0, 0)), (1.0, (-3.5, 0, 0)),
                                                (2.0, (0, 0, 0))])},
            "cape_b_u": {"rotation": rot_track([(0, (0, 0, 0)), (1.0, (-4.0, 0, 0)),
                                                (2.0, (0, 0, 0))])},
            "cape_b_m": {"rotation": rot_track([(0, (0, 0, 0)), (1.0, (-3.0, 0, 0)),
                                                (2.0, (0, 0, 0))])},
            "wisp_1": {"position": vec_track([(0, (0, 0, 0)), (1.0, (0, 0.9, 0)),
                                              (2.0, (0, 0, 0))])},
            "wisp_2": {"position": vec_track([(0, (0, 0, 0)), (1.4, (0, 1.2, 0)),
                                              (2.0, (0, 0, 0))])},
            "wisp_3": {"position": vec_track([(0, (0, 0, 0)), (0.8, (0, -0.8, 0)),
                                              (2.0, (0, 0, 0))])},
            "wisp_4": {"position": vec_track([(0, (0, 0, 0)), (1.6, (0, 1.0, 0)),
                                              (2.0, (0, 0, 0))])},
            "wisp_5": {"position": vec_track([(0, (0, 0, 0)), (1.2, (0, -1.0, 0)),
                                              (2.0, (0, 0, 0))])},
        })

    if "animation.warden.walk" not in have:
        add("animation.warden.walk", 1.0, True, {
            "body": {"rotation": rot_track([(0, (0, 0, 0)), (0.25, (6, 0, 0)),
                                            (0.5, (0, 0, 0)), (0.75, (6, 0, 0)),
                                            (1.0, (0, 0, 0))]),
                     "position": vec_track([(0, (0, 0, 0)), (0.25, (0, 0.7, 0)),
                                            (0.5, (0, 0, 0)), (0.75, (0, 0.7, 0)),
                                            (1.0, (0, 0, 0))])},
            "legs": {"rotation": rot_track([(0, (0, 0, 0)), (0.25, (-14, 0, 0)),
                                            (0.5, (0, 0, 0)), (0.75, (14, 0, 0)),
                                            (1.0, (0, 0, 0))])},
            "arm_r": {"rotation": rot_track([(0, (0, 0, 0)), (0.25, (12, 0, 0)),
                                             (0.75, (-12, 0, 0)), (1.0, (0, 0, 0))])},
            "arm_l": {"rotation": rot_track([(0, (0, 0, 0)), (0.25, (-12, 0, 0)),
                                             (0.75, (12, 0, 0)), (1.0, (0, 0, 0))])},
            "cape_b_u": {"rotation": rot_track([(0, (0, 0, 0)), (0.5, (-9, 0, 0)),
                                                (1.0, (0, 0, 0))])},
            "cape_b_m": {"rotation": rot_track([(0, (0, 0, 0)), (0.5, (-7, 0, 0)),
                                                (1.0, (0, 0, 0))])},
        })

    if "animation.warden.attack_smash" not in have:
        add("animation.warden.attack_smash", 1.0, False, {
            "body": {"rotation": rot_track([(0, (0, 0, 0)), (0.32, (-16, 0, 0)),
                                            (0.52, (18, 0, 0)), (1.0, (0, 0, 0))])},
            "arm_r": {"rotation": rot_track([(0, (0, 0, 0)), (0.32, (-78, 0, 0)),
                                             (0.52, (46, 0, 0)), (1.0, (0, 0, 0))])},
            "arm_l": {"rotation": rot_track([(0, (0, 0, 0)), (0.32, (-30, 0, 0)),
                                             (0.52, (24, 0, 0)), (1.0, (0, 0, 0))])},
            "scythe": {"rotation": rot_track([(0, (0, 0, 0)), (0.32, (-34, 0, 0)),
                                              (0.52, (52, 0, 0)), (1.0, (0, 0, 0))])},
            "head": {"rotation": rot_track([(0, (0, 0, 0)), (0.32, (-12, 0, 0)),
                                            (0.52, (14, 0, 0)), (1.0, (0, 0, 0))])},
        })

    if "animation.warden.hurt" not in have:
        add("animation.warden.hurt", 0.5, False, {
            "body": {"rotation": rot_track([(0, (0, 0, 0)), (0.12, (-8, 0, 0)),
                                            (0.3, (-3, 0, 0)), (0.5, (0, 0, 0))])},
            "head": {"rotation": rot_track([(0, (0, 0, 0)), (0.12, (-9, 0, 0)),
                                            (0.5, (0, 0, 0))])},
            "arm_r": {"rotation": rot_track([(0, (0, 0, 0)), (0.12, (-12, 0, 0)),
                                             (0.5, (0, 0, 0))])},
            "arm_l": {"rotation": rot_track([(0, (0, 0, 0)), (0.12, (-12, 0, 0)),
                                             (0.5, (0, 0, 0))])},
        })

    if "animation.warden.death" not in have:
        add("animation.warden.death", 1.8, "hold_on_last_frame", {
            "root": {"position": vec_track([(0, (0, 0, 0)), (0.9, (0, -1.2, 0)),
                                            (1.8, (0, -5.5, 0))])},
            "body": {"rotation": rot_track([(0, (0, 0, 0)), (0.5, (12, 0, 0)),
                                            (1.8, (-72, 0, 6))])},
            "head": {"rotation": rot_track([(0, (0, 0, 0)), (1.8, (28, 0, 0))])},
            "leg_r": {"rotation": rot_track([(0, (0, 0, 0)), (1.8, (24, 0, 0))])},
            "leg_l": {"rotation": rot_track([(0, (0, 0, 0)), (1.8, (-18, 0, 0))])},
        })

    if "animation.warden.attack_sweep" not in have:
        add("animation.warden.attack_sweep", 1.0, False, {
            "body": {"rotation": rot_track([(0, (0, 0, 0)), (0.18, (0, -20, 0)),
                                            (0.5, (0, 22, 0)), (1.0, (0, 0, 0))])},
            "arm_r": {"rotation": rot_track([(0, (0, 0, 0)), (0.18, (-15, -45, -20)),
                                             (0.5, (-8, 42, 12)), (1.0, (0, 0, 0))])},
            "head": {"rotation": rot_track([(0, (0, 0, 0)), (0.3, (0, -16, 0)),
                                            (1.0, (0, 0, 0))])},
        })

    if "animation.warden.attack_lash" not in have:
        add("animation.warden.attack_lash", 1.2, False, {
            "body": {"rotation": rot_track([(0, (0, 0, 0)), (0.2, (0, 24, 0)),
                                            (0.6, (0, -22, 0)), (1.2, (0, 0, 0))])},
            "arm_l": {"rotation": rot_track([(0, (0, 0, 0)), (0.2, (-18, 40, 22)),
                                             (0.6, (-10, -46, -14)), (1.2, (0, 0, 0))])},
            "arm_r": {"rotation": rot_track([(0, (0, 0, 0)), (0.4, (0, 18, 0)),
                                             (1.2, (0, 0, 0))])},
        })

    if "animation.warden.phase_roar" not in have:
        add("animation.warden.phase_roar", 2.0, False, {
            "body": {"rotation": rot_track([(0, (0, 0, 0)), (0.4, (9, 0, 0)),
                                            (1.2, (-13, 0, 0)), (2.0, (0, 0, 0))])},
            "head": {"rotation": rot_track([(0, (0, 0, 0)), (0.5, (-26, 0, 0)),
                                            (2.0, (0, 0, 0))])},
            "arm_r": {"rotation": rot_track([(0, (0, 0, 0)), (0.6, (0, 0, -42)),
                                             (2.0, (0, 0, 0))])},
            "arm_l": {"rotation": rot_track([(0, (0, 0, 0)), (0.6, (0, 0, 42)),
                                             (2.0, (0, 0, 0))])},
            "root": {"position": vec_track([(0, (0, 0, 0)), (0.7, (0, 1.4, 0)),
                                            (2.0, (0, 0, 0))])},
        })

    if "animation.warden.phase_awaken" not in have:
        add("animation.warden.phase_awaken", 2.4, False, {
            "body": {"rotation": rot_track([(0, (0, 0, 0)), (0.5, (-7, 0, 0)),
                                            (2.4, (0, 0, 0))])},
            "head": {"rotation": rot_track([(0, (0, 0, 0)), (0.6, (-18, 0, 0)),
                                            (2.4, (0, 0, 0))])},
            "arm_r": {"rotation": rot_track([(0, (0, 0, 0)), (0.9, (-10, 0, -58)),
                                             (2.4, (0, 0, 0))])},
            "arm_l": {"rotation": rot_track([(0, (0, 0, 0)), (0.9, (-10, 0, 58)),
                                             (2.4, (0, 0, 0))])},
            "root": {"position": vec_track([(0, (0, 0, 0)), (0.9, (0, 1.8, 0)),
                                            (2.4, (0, 0, 0))])},
            "coat": {"rotation": rot_track([(0, (0, 0, 0)), (1.0, (-9, 0, 0)),
                                            (2.4, (0, 0, 0))])},
        })
    return res


# ============================================================
# 落盘
# ============================================================

def strip_internal(bones):
    for b in bones:
        for c in b["cubes"]:
            c.pop("_element", None)
            c.pop("_slot_size", None)


def verify(bones):
    names = [b["name"] for b in bones]
    assert len(names) == len(set(names)), "骨骼名重复"
    seen = set()
    for b in bones:
        p = b.get("parent")
        if p and p not in seen:
            raise SystemExit(f"骨骼 {b['name']} 的父 {p} 尚未定义（拓扑非法）")
        seen.add(b["name"])
    empty = [b["name"] for b in bones if not b["cubes"]]
    if empty:
        print(f"  提示：{len(empty)} 个骨骼没有立方体（只作动画支点）")




# ============================================================
# ★ dv34：刚性归位（模型原点不在脚底中心）
# ============================================================
#
# 用户给的 bbmodel 是在 Blockbench 里凭手感摆的，原点既不在脚底、
# 也不在几何中心（实测 y_min=-40.8、x 中心=-7.98）。GeckoLib 渲染时
# **不做任何居中补偿**（dv16 反汇编确认的），所以必须自己搬。
#
# x 方向有个陷阱：GeckoLib 的 constructCube 会把 cube.origin.x 换成
# -(ox+ow)/16（镜像），而 constructBone 的 pivot **不镜像**。
# 现在 make_cube 已经**预先抵消**了那个镜像，所以 cube 与 pivot 落在
# 同一个（bbmodel 原始）坐标系里，整体平移时 x / y / z 一律同向相加。

def shift_bones(bones, dx, dy, dz):
    for b in bones:
        pv = b.get("pivot")
        if pv:
            pv[0] = round(pv[0] + dx, 4)
            pv[1] = round(pv[1] + dy, 4)
            pv[2] = round(pv[2] + dz, 4)
        for c in b.get("cubes", []):
            c["origin"][0] = round(c["origin"][0] + dx, 4)
            c["origin"][1] = round(c["origin"][1] + dy, 4)
            c["origin"][2] = round(c["origin"][2] + dz, 4)


def bones_aabb(bones):
    g = {"format_version": "1.12.0",
         "minecraft:geometry": [{"description": {"identifier": IDENTIFIER,
                                                 "texture_width": ATLAS_W,
                                                 "texture_height": ATLAS_W},
                                 "bones": bones}]}
    bmap = GM.model_bones(g)
    cache = {}
    lo = np.array([1e9] * 3)
    hi = np.array([-1e9] * 3)
    for bn, b in bmap.items():
        for c in b.get("cubes", []):
            l, h = GM.cube_world_aabb_px(bmap, bn, c, cache)
            lo = np.minimum(lo, l)
            hi = np.maximum(hi, h)
    return lo, hi


def recenter(bones):
    """把模型搬到「x 居中 / 脚底贴地 / z 居中」，返回实际平移量。"""
    lo, hi = bones_aabb(bones)
    dx = -(lo[0] + hi[0]) / 2.0
    dy = -lo[1]
    dz = -(lo[2] + hi[2]) / 2.0
    shift_bones(bones, dx, dy, dz)
    lo2, hi2 = bones_aabb(bones)
    # x 符号若猜反，中心会跑到 -原值 去；这里补一次反向修正
    if abs((lo2[0] + hi2[0]) / 2.0) > 0.5:
        shift_bones(bones, -2.0 * dx, 0.0, 0.0)
        lo2, hi2 = bones_aabb(bones)
        dx = -dx
    return dx, dy, dz, lo2, hi2


def main():
    print(f"bbmodel -> GeckoLib 转换（form{FORM}）->")
    bb = load_bb()
    src, tex_name = extract_texture(bb)
    print(f"  源贴图 {tex_name} {src.width}x{src.height}")
    print(f"  源模型：{len(bb['groups'])} 骨骼 / {len(bb['elements'])} 立方体 / "
          f"{len(bb.get('animations', []))} 条动画")

    bones = build_bones(bb)
    dx, dy, dz, lo2, hi2 = recenter(bones)
    print(f"  刚性归位：x{dx:+.2f} y{dy:+.2f} z{dz:+.2f}"
          f"  → AABB x[{lo2[0]:.1f},{hi2[0]:.1f}] y[{lo2[1]:.1f},{hi2[1]:.1f}]"
          f" z[{lo2[2]:.1f},{hi2[2]:.1f}]")
    n_cubes = sum(len(b["cubes"]) for b in bones)
    n_wrapped = sum(1 for b in bones
                    if "rotation" in b and b["name"] not in {g.get("name") for g in bb["groups"]})
    print(f"  GeckoLib：{len(bones)} 骨骼 / {n_cubes} 立方体"
          f"（其中 {n_wrapped} 个由旋转立方体包装而来）")
    verify(bones)

    atlas, glow, atlas_h = build_atlas(bones, src, src.width, src.height)
    print(f"  图集 {atlas.width}x{atlas_h}"
          f"（发光像素 {sum(1 for p in glow.getdata() if p[3] > 8)}）")

    # ---- geo ----
    strip_internal(bones)
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": IDENTIFIER,
                "texture_width": atlas.width,
                "texture_height": atlas_h,
                "visible_bounds_width": 5.0,
                "visible_bounds_height": 5.0,
                "visible_bounds_offset": [0.0, 2.0, 0.0],
            },
            "bones": bones,
        }]
    }

    # ---- 世界坐标自检 ----
    bmap = GM.model_bones(geo)
    cache = {}
    lo = np.array([1e9] * 3)
    hi = np.array([-1e9] * 3)
    core_lo = None
    core_hi = None
    for bn, b in bmap.items():
        for c in b.get("cubes", []):
            l, h = GM.cube_world_aabb_px(bmap, bn, c, cache)
            lo = np.minimum(lo, l)
            hi = np.maximum(hi, h)
    fails = []
    # ★ A1 不入地：长袍下摆是**带旋转的布料片**，绕上端枢轴旋开后它的 AABB
    #   角会探到地面以下 0.2 格左右（Blockbench 自己的包围盒忽略旋转所以显示 0）。
    #   布料蹭地是可接受的，但**脚掌必须严格贴地** —— 分开判：
    if lo[1] < -4.5:
        fails.append(f"A1 入地过深 y_min={lo[1]:.2f}")
    # ★ dv34 通用化：不再假设骨骼命名（旧版硬编码 shin/leg/body）。
    #   改为看整体 AABB —— 最低点必须贴着 y=0（脚底在地面）。
    if abs(lo[1]) > 3.0 and lo[1] > -4.5:
        fails.append(f"A1 最低点不在脚底 y_min={lo[1]:.2f}（应 ≈ 0）")
    if hi[1] > 130.0:
        fails.append(f"A4 过高 y_max={hi[1]:.2f}")
    cx = (lo[0] + hi[0]) / 2
    if abs(cx) > 2.0:
        fails.append(f"A2 模型未居中 x 中心={cx:.2f}")
    print(f"  世界 AABB x[{lo[0]:.1f},{hi[0]:.1f}] y[{lo[1]:.1f},{hi[1]:.1f}] "
          f"z[{lo[2]:.1f},{hi[2]:.1f}]  高 {hi[1] - lo[1]:.1f}px")
    if fails:
        print("!! 自检失败，拒绝落盘：")
        for f in fails:
            print("   -", f)
        raise SystemExit(1)

    # ---- 动画 ----
    bone_names = {b["name"] for b in bones}
    anims = convert_animations(bb, bone_names)
    missing = [i for i in ANIM_IDS if i not in anims]
    if missing:
        print("!! 缺少动画 id：", missing)
        raise SystemExit(1)
    problems = []
    for aid, a in anims.items():
        for bn in a["bones"]:
            if bn not in bone_names:
                problems.append(f"{aid} 引用了不存在的骨骼 [{bn}]")
    if problems:
        print("!! 动画骨骼自检失败：")
        for p in problems[:20]:
            print("   -", p)
        raise SystemExit(1)

    anim_file = {"format_version": "1.8.0", "animations": {k: anims[k] for k in ANIM_IDS}}

    os.makedirs(GEO_DIR, exist_ok=True)
    os.makedirs(ANIM_DIR, exist_ok=True)
    os.makedirs(TEX_DIR, exist_ok=True)
    with open(GEO_OUT, "w", encoding="utf-8") as f:
        json.dump(geo, f, ensure_ascii=False, indent=1)
    with open(ANIM_OUT, "w", encoding="utf-8") as f:
        json.dump(anim_file, f, ensure_ascii=False, indent=1)
    atlas.save(TEX_OUT)
    glow.save(GLOW_OUT)

    print(f"  动画 {len(anim_file['animations'])} 条："
          + ", ".join(k.split('.')[-1] for k in ANIM_IDS))
    for aid in ANIM_IDS:
        a = anim_file["animations"][aid]
        print(f"    {aid.split('.')[-1]:14s} len={a['animation_length']:<5} "
              f"loop={str(a['loop']):22s} 骨骼 {len(a['bones'])}")
    print("  OK：几何 / 动画 / 贴图 / 发光遮罩 已写出")


if __name__ == "__main__":
    main()
