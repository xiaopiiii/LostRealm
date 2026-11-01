#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
把外部 AI 做的 Blockbench .glb（遗落之主二阶段）转换进本模组的 GeckoLib 资产。

=== 输入 ===
  /root/inspect_zip/contents/遗落之主二阶段建模+动画.glb
  - 112 个节点（骨骼树），92 个 mesh（每个 mesh 恰好 24 顶点 = 一个立方体）
  - 1 张 256x256 贴图，1 条动画 clip `cape_flutter`

=== 输出 ===
  geo/warden_form2.geo.json          几何（替代 dv16 form2）
  animations/warden_form2.animation.json  动画（9 个固定 id）
  textures/entity/warden_phase2.png          重建的贴图集
  textures/entity/warden_phase2_glowmask.png 自发光遮罩（提取高亮像素）

=== 转换铁律（与 gecko_math.py 逐字节对齐）===
  每个 GLB 节点 N（平移 T_N、旋转 R_N、缩放=1、挂一个立方 mesh）-> 一个 GeckoLib 骨骼：
    bone.pivot = T_N * 16 * SCALE          （父局部，像素）
    bone.rotation = R_N 的 intrinsic-XYZ 欧拉（度）  -> 见 quat_to_euler
    cube.origin/size 由 (AABB_local + T_N) * SCALE 反解（见 cube_json）
  这样骨骼世界矩阵 = SCALE * GLB 世界矩阵，模型被整体等比放大 SCALE 倍。

  贴图：GeckoLib 的 cube 只有单个 uv(左上角)，六面按标准「十字展开」由 size 推导。
  GLB 的贴图未必用标准展开，所以**重建一张新图集**：
    把每个 cube 的 6 个面（按局部坐标 +/-x/+y/-z 归类 24 顶点）各自 UV 矩形，
    采样原图（V 翻转）后填进标准展开对应的面槽。

=== 自检 ===
  落盘后用 gecko_math 复现世界 AABB，确认：
    A2 核心居中（x 中心 |c| <= 1.0px）、A4 规模合理、x=0 轴。
"""

import json
import math
import os
import sys

import numpy as np
from PIL import Image
from pygltflib import GLTF2

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gecko_math as GM

# ============================================================
# 配置
# ============================================================
SCALE = 1.4          # 等比放大（GLB 原模型约 2.4 格高，放大到约 3.4 格当 boss）
ATLAS = 512          # 图集边长
TEX_SRC = "/root/inspect_zip/contents/遗落之主二阶段建模+动画.glb"
TEX_PNG = "/root/inspect_zip/glb_tex.png"   # 已提取的 256x256

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GEO_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "lost_realm", "geo")
ANIM_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "lost_realm", "animations")
TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "lost_realm", "textures", "entity")

GEO_OUT = os.path.join(GEO_DIR, "warden_form2.geo.json")
ANIM_OUT = os.path.join(ANIM_DIR, "warden_form2.animation.json")
TEX_OUT = os.path.join(TEX_DIR, "warden_phase2.png")
GLOW_OUT = os.path.join(TEX_DIR, "warden_phase2_glowmask.png")

# ============================================================
# GLB 读取
# ============================================================

def load_glb():
    g = GLTF2().load(TEX_SRC)
    BIN = g.binary_blob()
    return g, BIN


def acc_np(g, BIN, acc):
    bv = g.bufferViews[acc.bufferView]
    off = bv.byteOffset or 0
    arr = np.frombuffer(BIN, dtype=np.uint8, count=bv.byteLength, offset=off)
    comp = {5120: np.int8, 5121: np.uint8, 5122: np.int16, 5123: np.uint16,
            5125: np.uint32, 5126: np.float32}[acc.componentType]
    ncomp = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}[acc.type]
    return arr.view(comp).reshape(acc.count, ncomp).astype(np.float64)


def quat_to_R(qx, qy, qz, qw):
    x, y, z, w = qx, qy, qz, qw
    R = np.array([
        [1 - 2*(y*y + z*z), 2*(x*y - z*w),     2*(x*z + y*w)],
        [2*(x*y + z*w),     1 - 2*(x*x + z*z), 2*(y*z - x*w)],
        [2*(x*z - y*w),     2*(y*z + x*w),     1 - 2*(x*x + y*y)],
    ])
    return R


def quat_to_euler_deg(q):
    """GeckoLib JSON 旋转 [rx,ry,rz]。

    gecko_math: H = Rz(rz) @ Ry(-ry) @ Rx(-rx)。
    令 alpha=-rx, beta=-ry, gamma=rz，则 H = Rz(gamma)Ry(beta)Rx(alpha)
    即 intrinsic XYZ 欧拉。从矩阵反解：
        alpha = atan2(R21, R22)
        beta  = asin(-R20)（取 [-90,90]）
        gamma = atan2(R10, R00)
    于是 JSON = [-alpha, -beta, gamma]。
    """
    R = quat_to_R(*q)
    alpha = math.atan2(R[2, 1], R[2, 2])
    beta = math.atan2(-R[2, 0], math.hypot(R[2, 1], R[2, 2]))
    gamma = math.atan2(R[1, 0], R[0, 0])
    rx = -alpha
    ry = -beta
    rz = gamma
    return [math.degrees(rx), math.degrees(ry), math.degrees(rz)]


# ============================================================
# 立方体 JSON（从 AABB+T_N 反解，匹配 cube_origin' 公式）
# ============================================================

def cube_json(mn, mx, T):
    """mn,mx = 立方在节点局部空间的 AABB（block）；T = 节点平移（block）。
    返回 (origin_px, size_px)。"""
    Qmn = (mn + T) * SCALE
    Qmx = (mx + T) * SCALE
    ox = -Qmx[0] * 16.0
    oy = Qmn[1] * 16.0
    oz = Qmn[2] * 16.0
    w = (Qmx[0] - Qmn[0]) * 16.0
    h = (Qmx[1] - Qmn[1]) * 16.0
    d = (Qmx[2] - Qmn[2]) * 16.0
    return ([round(float(v), 3) for v in (ox, oy, oz)],
            [round(float(v), 3) for v in (w, h, d)])


def gather_mesh_faces(g, BIN, mesh_idx):
    """返回 (mn, mx, faces) —— mn/mx 为节点局部 AABB；
    faces = {'mx','nx','py','ny','nz','pz' -> (umin,umax,vmin,vmax)} 在 GLB UV[0,1]、V 向上。"""
    m = g.meshes[mesh_idx]
    pr = m.primitives[0]
    pos = acc_np(g, BIN, g.accessors[pr.attributes.POSITION])
    uv = acc_np(g, BIN, g.accessors[pr.attributes.TEXCOORD_0])
    mn = pos.min(axis=0)
    mx = pos.max(axis=0)
    tol = 1e-4
    faces = {}
    # 每个面由该坐标等于极值的一组 4 顶点构成
    keys = [("mx", mn[0], mx[0], 0, +1), ("nx", mn[0], mx[0], 0, -1),
            ("py", mn[1], mx[1], 1, +1), ("ny", mn[1], mx[1], 1, -1),
            ("pz", mn[2], mx[2], 2, +1), ("nz", mn[2], mx[2], 2, -1)]
    for key, lo, hi, axis, sign in keys:
        target = mx[axis] if sign > 0 else mn[axis]
        mask = np.abs(pos[:, axis] - target) < tol
        sel = uv[mask]
        if sel.shape[0] == 0:
            # 退化：取最接近极值的
            sel = uv[np.argmin(np.abs(pos[:, axis] - target))]
            sel = sel.reshape(1, -1)
        faces[key] = (float(sel[:, 0].min()), float(sel[:, 0].max()),
                      float(sel[:, 1].min()), float(sel[:, 1].max()))
    return mn, mx, faces


# ============================================================
# 几何转换
# ============================================================

def build_geometry(g, BIN):
    nodes = g.nodes
    # 计算每个 mesh 的局部 AABB + 面 UV
    mesh_info = {}
    for i, n in enumerate(nodes):
        if n.mesh is not None:
            mn, mx, faces = gather_mesh_faces(g, BIN, n.mesh)
            mesh_info[i] = (mn, mx, faces)

    # 唯一骨骼名（GeckoLib 要求）。
    # 重名时优先把「无 mesh 的组节点（holder）」保留原名（动画旋转 holder 能带动整条子链），
    # 带 mesh 的节点加后缀（如 cape_l_u -> cape_l_u_1）。
    # 关键：keeper 选中的原名要先「预留」，否则在索引顺序上先处理的 mesh 节点会抢走原名。
    keeper = {}
    for i, n in enumerate(nodes):
        base = n.name or f"node_{i}"
        if base not in keeper:
            keeper[base] = i
        else:
            cur = keeper[base]
            # 优先让「无 mesh 的 holder」当 keeper
            if nodes[cur].mesh is not None and n.mesh is None:
                keeper[base] = i
    reserved = {nodes[keeper[b]].name or f"node_{keeper[b]}" for b in keeper}
    used = set()
    assigned = {}
    for i, n in enumerate(nodes):
        base = n.name or f"node_{i}"
        if keeper.get(base) == i:
            nm = base                      # keeper 保留原名（已预留，不会冲突）
        else:
            nm = base
            k = 1
            while nm in used or nm in reserved:
                nm = f"{base}_{k}"
                k += 1
        used.add(nm)
        assigned[i] = nm

    bones = []
    cube_records = []   # (bone_name, size_px, faces_uv)
    for i, n in enumerate(nodes):
        name = assigned[i]
        T = np.array(n.translation or [0, 0, 0], dtype=float)
        rot = quat_to_euler_deg(n.rotation or [0, 0, 0, 1])
        pivot = [round(float(v * 16.0 * SCALE), 3) for v in T]
        b = {"name": name, "pivot": pivot}
        if any(abs(v) > 1e-4 for v in rot):
            b["rotation"] = [round(float(v), 3) for v in rot]
        b["cubes"] = []
        if n.mesh is not None:
            mn, mx, faces = mesh_info[i]
            origin, size = cube_json(mn, mx, T)
            b["cubes"].append({"origin": origin, "size": size,
                                "uv": None, "inflate": 0.0})
            cube_records.append((name, size, faces))
        bones.append(b)

    # 挂父子关系（用分配后的唯一名）
    for i, n in enumerate(nodes):
        for c in (n.children or []):
            bones[c]["parent"] = assigned[i]

    return bones, cube_records


# ============================================================
# 贴图图集重建
# ============================================================

def part_color(name):
    """按部件名给一个底色；(颜色, 是否自发光)。"""
    n = name.lower()
    if 'eye' in n:
        return (96, 232, 255), True        # 眼睛：青色自发光
    if 'wisp' in n or 'orb' in n:
        return (140, 190, 255), True       # 邪眼/光球：自发光
    if 'blade' in n or 'edge' in n:
        return (226, 232, 248), True        # 镰刃：自发光
    if any(k in n for k in ('hood', 'head', 'mask', 'beak', 'frill', 'fin',
                            'robe', 'torso', 'body', 'chest', 'belt', 'collar', 'pelvis')):
        return (62, 42, 94), False          # 长袍/躯干：深紫
    if 'cape' in n:
        return (152, 60, 168), False        # 披风：品红
    if any(k in n for k in ('scythe', 'shaft', 'wraps', 'spear', 'drops', 'tassel')):
        return (58, 58, 74), False          # 镰柄/缠绕：冷灰
    if any(k in n for k in ('arm', 'pauldron', 'shoulder', 'spike')):
        return (82, 54, 124), False         # 手臂/肩甲：紫
    if any(k in n for k in ('leg', 'boot', 'foot', 'knee')):
        return (50, 36, 80), False          # 腿：暗紫
    return (88, 60, 124), False


FACE_SHADE = {"py": 1.18, "ny": 0.74, "mx": 1.06, "nx": 0.92, "nz": 1.10, "pz": 0.84}


def build_atlas(cube_records, src_img):
    """返回 (atlas_rgba, slots) —— slots: bone_name -> (u,v) 左上角像素。"""
    W = 512
    H = 512
    atlas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    slots = {}
    glow_rects = []
    cx, cy, row_h = 0, 0, 0
    for name, size, faces in cube_records:
        w, h, d = size
        netW = 2 * (d + w)
        netH = d + h
        if cx + netW > W:
            cx = 0
            cy += row_h + 1
            row_h = 0
        u, v = cx, cy
        # 面槽（uv_layout 约定：top +y, bottom -y, front -z, back +z, right +x, left -x）
        slot = {
            "py": (u + d,     v,         w, d),
            "ny": (u + d + w, v,         w, d),
            "mx": (u,         v + d,     d, h),
            "nz": (u + d,     v + d,     w, h),
            "nx": (u + d + w, v + d,     d, h),
            "pz": (u + 2*d+w, v + d,     w, h),
        }
        # 采样填图
        base, is_glow = part_color(name)
        for fk, rect in slot.items():
            if fk not in faces:
                continue
            umin, umax, vmin, vmax = faces[fk]
            # 源：GLB UV，V 向上 -> 翻转
            sx0 = int(round(umin * src_img.width))
            sx1 = int(round(umax * src_img.width))
            sy0 = int(round((1.0 - vmax) * src_img.height))
            sy1 = int(round((1.0 - vmin) * src_img.height))
            sx0, sx1 = min(sx0, sx1), max(sx0, sx1)
            sy0, sy1 = min(sy0, sy1), max(sy0, sy1)
            if sx1 - sx0 < 1:
                sx1 = sx0 + 1
            if sy1 - sy0 < 1:
                sy1 = sy0 + 1
            sx, sy, sw, sh = int(round(rect[0])), int(round(rect[1])), \
                             max(1, int(round(rect[2]))), max(1, int(round(rect[3])))
            f = FACE_SHADE.get(fk, 1.0)
            r = int(min(255, base[0] * f)); g = int(min(255, base[1] * f)); b = int(min(255, base[2] * f))
            fill = Image.new("RGBA", (sw, sh), (r, g, b, 255))
            atlas.paste(fill, (sx, sy))
            if is_glow:
                glow_rects.append((sx, sy, sw, sh))
                # 必须显式传 L 模式 alpha 作 mask,否则 RGBA 当 mask 被忽略,
                # 后续透明裁剪会把先前的非透明像素擦掉。
                pass  # 旧采样逻辑已由程序化上色取代
        slots[name] = (u, v)
        cx += netW + 1
        row_h = max(row_h, netH)
    if cy + row_h > H:
        print(f"  !! 图集溢出：需要高度 {cy+row_h} > {H}")
    return np.array(atlas), slots, glow_rects


def build_glowmask(atlas_rgba, glow_rects):
    """高亮（自发光）矩形 -> 白色遮罩。"""
    glow = np.zeros_like(atlas_rgba)
    if glow_rects:
        gimg = Image.fromarray(glow, "RGBA")
        for (x, y, w, h) in glow_rects:
            gimg.paste((255, 255, 255, 255), (x, y, x + w, y + h))
        glow = np.array(gimg)
    return glow


# ============================================================
# 动画转换
# ============================================================

def quat_track_to_euler(g, BIN, sampler):
    inp = g.accessors[sampler.input]
    out = g.accessors[sampler.output]
    times = acc_np(g, BIN, inp)[:, 0]
    quats = acc_np(g, BIN, out)   # (k,4)
    track = {}
    for t, q in zip(times, quats):
        e = quat_to_euler_deg(q)
        track[str(round(float(t), 3))] = {"post": [round(float(v), 3) for v in e]}
    return track


def vec_track(g, BIN, sampler, scale=16.0*SCALE):
    inp = g.accessors[sampler.input]
    out = g.accessors[sampler.output]
    times = acc_np(g, BIN, inp)[:, 0]
    vecs = acc_np(g, BIN, out)
    track = {}
    for t, v in zip(times, vecs):
        track[str(round(float(t), 3))] = {"vector": [round(float(x*scale), 3) for x in v]}
    return track


def convert_cape_flutter(g, BIN):
    a = g.animations[0]
    bones = {}
    for ch in a.channels:
        node = ch.target.node
        name = g.nodes[node].name or f"node_{node}"
        path = ch.target.path
        sampler = a.samplers[ch.sampler]
        tr = (quat_track_to_euler(g, BIN, sampler) if path == "rotation"
              else vec_track(g, BIN, sampler))
        bones.setdefault(name, {})[path] = tr
    return {"loop": True, "animation_length": 4.0, "bones": bones}


# ---- 程序化攻击/演出（针对 GLB 骨骼名）----

def _t(frames):
    return {str(round(t, 3)): {"post": [round(float(v), 3) for v in val]} for t, val in frames}


def anim_attack_sweep():
    parts = {
        "arm_r": _t([(0.0, [0, 0, 0]), (0.20, [-40, 0, -100]), (0.46, [-6, 0, 72]), (0.85, [0, 0, 0])]),
        "arm_l": _t([(0.0, [0, 0, 0]), (0.20, [-14, 0, 24]), (0.46, [-4, 0, -18]), (0.85, [0, 0, 0])]),
        "body":  _t([(0.0, [0, 0, 0]), (0.20, [0, -30, 0]), (0.46, [0, 36, 0]), (0.85, [0, 0, 0])]),
        "head":  _t([(0.0, [0, 0, 0]), (0.20, [0, -24, 0]), (0.46, [0, 28, 0]), (0.85, [0, 0, 0])]),
        "scythe": _t([(0.0, [0, 0, 0]), (0.20, [0, 0, -28]), (0.46, [0, 0, 22]), (0.85, [0, 0, 0])]),
    }
    return {"loop": False, "animation_length": 0.85, "bones": parts}


def anim_attack_smash():
    parts = {
        "arm_r": _t([(0.0, [0, 0, 0]), (0.24, [-130, 0, 4]), (0.48, [55, 0, -4]), (0.90, [0, 0, 0])]),
        "arm_l": _t([(0.0, [0, 0, 0]), (0.24, [-124, 0, -6]), (0.48, [52, 0, 4]), (0.90, [0, 0, 0])]),
        "body":  _t([(0.0, [0, 0, 0]), (0.24, [-20, 0, 0]), (0.48, [18, 0, 0]), (0.90, [0, 0, 0])]),
        "head":  _t([(0.0, [0, 0, 0]), (0.24, [-16, 0, 0]), (0.48, [14, 0, 0]), (0.90, [0, 0, 0])]),
        "scythe": _t([(0.0, [0, 0, 0]), (0.24, [-30, 0, 0]), (0.48, [46, 0, 0]), (0.90, [0, 0, 0])]),
    }
    return {"loop": False, "animation_length": 0.90, "bones": parts}


def anim_attack_lash():
    parts = {
        "arm_r": _t([(0.0, [0, 0, 0]), (0.30, [-20, 0, -10]), (1.0, [0, 0, 0])]),
        "arm_l": _t([(0.0, [0, 0, 0]), (0.30, [-20, 0, 10]), (1.0, [0, 0, 0])]),
        "body":  _t([(0.0, [0, 0, 0]), (0.26, [-13, 0, 0]), (0.58, [11, 0, 0]), (1.1, [0, 0, 0])]),
        "head":  _t([(0.0, [0, 0, 0]), (0.58, [11, 0, 0]), (1.1, [0, 0, 0])]),
        "scythe": _t([(0.0, [0, 0, 0]), (0.30, [-14, 0, 0]), (1.1, [0, 0, 0])]),
        "cape_b_u": _t([(0.0, [0, 0, 0]), (0.30, [-26, 0, 0]), (1.1, [0, 0, 0])]),
        "cape_l_u": _t([(0.0, [0, 0, 0]), (0.30, [-22, 6, 0]), (1.1, [0, 0, 0])]),
        "cape_r_u": _t([(0.0, [0, 0, 0]), (0.30, [-22, -6, 0]), (1.1, [0, 0, 0])]),
    }
    for i in range(1, 6):
        parts[f"wisp_{i}"] = _t([(0.0, [0, 0, 0]), (0.30, [0, 30, 0]), (1.1, [0, 0, 0])])
    return {"loop": False, "animation_length": 1.1, "bones": parts}


def anim_hurt():
    parts = {
        "body":  _t([(0.0, [0, 0, 0]), (0.15, [-17, 0, 6]), (0.55, [0, 0, 0])]),
        "head":  _t([(0.0, [0, 0, 0]), (0.15, [-22, 5, 0]), (0.55, [0, 0, 0])]),
        "arm_r": _t([(0.0, [0, 0, 0]), (0.15, [-10, 0, 12]), (0.55, [0, 0, 0])]),
        "arm_l": _t([(0.0, [0, 0, 0]), (0.15, [-10, 0, -12]), (0.55, [0, 0, 0])]),
    }
    return {"loop": False, "animation_length": 0.55, "bones": parts}


def anim_death():
    parts = {
        "root": {"rotation": _t([(0.0, [0, 0, 0]), (1.7, [-86, 0, 0])]),
                 "position": {str(t): {"vector": [0, round(y*16*SCALE, 3), 0]}
                               for t, y in [(0.0, 0.0), (1.7, -1.5)]}},
        "body":  _t([(0.0, [0, 0, 0]), (0.45, [-24, 0, 0]), (1.7, [-12, 0, 0])]),
        "head":  _t([(0.0, [0, 0, 0]), (1.7, [34, 0, 0])]),
        "arm_r": _t([(0.0, [0, 0, 0]), (1.7, [-72, 0, 22])]),
        "arm_l": _t([(0.0, [0, 0, 0]), (1.7, [-72, 0, -22])]),
        "scythe": _t([(0.0, [0, 0, 0]), (1.7, [10, 0, 30])]),
    }
    return {"loop": False, "animation_length": 1.7, "bones": parts}


def anim_phase_roar():
    parts = {
        "body":  _t([(0.0, [0, 0, 0]), (0.6, [-24, 0, 0]), (1.4, [11, 0, 0]), (3.6, [0, 0, 0])]),
        "head":  _t([(0.0, [0, 0, 0]), (0.6, [-36, 0, 0]), (1.4, [20, 0, 0]), (3.6, [0, 0, 0])]),
        "arm_r": _t([(0.0, [0, 0, 0]), (0.6, [-30, 0, -44]), (1.4, [-14, 0, -76]), (3.6, [0, 0, 0])]),
        "arm_l": _t([(0.0, [0, 0, 0]), (0.6, [-30, 0, 44]), (1.4, [-14, 0, 76]), (3.6, [0, 0, 0])]),
        "scythe": _t([(0.0, [0, 0, 0]), (1.0, [-20, 0, 0]), (3.6, [0, 0, 0])]),
        "cape_b_u": _t([(0.0, [0, 0, 0]), (0.6, [12, 0, 0]), (3.6, [0, 0, 0])]),
        "cape_l_u": _t([(0.0, [0, 0, 0]), (0.6, [10, 8, 0]), (3.6, [0, 0, 0])]),
        "cape_r_u": _t([(0.0, [0, 0, 0]), (0.6, [10, -8, 0]), (3.6, [0, 0, 0])]),
    }
    return {"loop": False, "animation_length": 3.6, "bones": parts}


def anim_phase_awaken():
    parts = {
        "body":  _t([(0.0, [0, 0, 0]), (1.0, [-32, 0, 0]), (2.6, [9, 0, 0]), (5.0, [0, 0, 0])]),
        "head":  _t([(0.0, [0, 0, 0]), (1.0, [-44, 0, 0]), (2.6, [24, 0, 0]), (5.0, [0, 0, 0])]),
        "arm_r": _t([(0.0, [0, 0, 0]), (1.0, [-20, 0, -32]), (2.6, [-8, 0, -96]), (5.0, [0, 0, 0])]),
        "arm_l": _t([(0.0, [0, 0, 0]), (1.0, [-20, 0, 32]), (2.6, [-8, 0, 96]), (5.0, [0, 0, 0])]),
        "scythe": _t([(0.0, [0, 0, 0]), (1.6, [-14, 0, 0]), (5.0, [0, 0, 0])]),
        "cape_b_u": _t([(0.0, [0, 0, 0]), (1.0, [-18, 0, 0]), (5.0, [0, 0, 0])]),
        "cape_l_u": _t([(0.0, [0, 0, 0]), (1.0, [-16, 10, 0]), (5.0, [0, 0, 0])]),
        "cape_r_u": _t([(0.0, [0, 0, 0]), (1.0, [-16, -10, 0]), (5.0, [0, 0, 0])]),
    }
    for i in range(1, 6):
        parts[f"wisp_{i}"] = _t([(0.0, [0, 0, 0]), (2.6, [0, 180, 0]), (5.0, [0, 360, 0])])
    return {"loop": False, "animation_length": 5.0, "bones": parts}


def build_animation(g, BIN):
    idle = convert_cape_flutter(g, BIN)
    walk = {"loop": True, "animation_length": 1.0, "bones": idle["bones"]}
    anims = {
        "animation.warden.idle": idle,
        "animation.warden.walk": walk,
        "animation.warden.attack_sweep": anim_attack_sweep(),
        "animation.warden.attack_smash": anim_attack_smash(),
        "animation.warden.attack_lash": anim_attack_lash(),
        "animation.warden.hurt": anim_hurt(),
        "animation.warden.death": anim_death(),
        "animation.warden.phase_roar": anim_phase_roar(),
        "animation.warden.phase_awaken": anim_phase_awaken(),
    }
    # ⚠️ GeckoLib 要求动画文件的顶层就是 {format_version, animations}，
    # 不能像 geo 那样再套一层命名（套了会报 "Missing animations, expected
    # to find a JsonObject" 并在加载期直接崩客户端）。
    return {"format_version": "1.8.0", "animations": anims}


# ============================================================
# 主流程
# ============================================================

def center_model_x(bones):
    """把整棵树在 x 方向刚性平移，使世界 AABB 的 x 中心 = 0。
    pivot.x -= cx，cube.origin.x += cx（两者同量反向，旋转关系不变）。"""
    bones_dict = GM.model_bones({"minecraft:geometry": [{"bones": bones}]})
    cache = {}
    lo = np.array([1e9, 1e9, 1e9]); hi = np.array([-1e9, -1e9, -1e9])
    for bname, b in bones_dict.items():
        for c in b.get("cubes", []):
            l, h = GM.cube_world_aabb_px(bones_dict, bname, c, cache)
            lo = np.minimum(lo, l); hi = np.maximum(hi, h)
    cx = (lo[0] + hi[0]) * 0.5
    if abs(cx) < 1e-3:
        return 0.0
    # 注意：simulator 上报的 x 已做 -v*16 镜像，内部空间中心 = -cx/16。
    # 要把内部中心移到 0，需把内部 x 平移 +cx/16：
    #   pivot 内部 x = pivot[0]/16  -> pivot[0] += cx
    #   cube 内部 max x = -origin[0]/16 -> origin[0] -= cx
    for b in bones:
        if b.get("pivot"):
            b["pivot"][0] = round(b["pivot"][0] + cx, 3)
        for c in b.get("cubes", []):
            c["origin"][0] = round(c["origin"][0] - cx, 3)
    return cx


def lift_model_y(bones, min_y_px=0.0):
    """把整棵树在 y 方向刚性抬起，使世界 AABB 的最低点 = min_y_px。

    为什么要做：AI 的 glb 是「reaper 静置姿势」—— 披风/镰刀垂到脚下
    (y 最低约 -25.9px)，直接放进游戏里会让<b>身体陷进地面</b>。
    抬起整棵树后脚底落在 y=0（实体脚底平面），与碰撞箱对齐。

    simulator 上报的 y 不做镜像（只有 x 镜像，见 gecko_math._to_px），
    所以内部 y 与上报 y 同号同量：
        pivot[1] += dy   (dy = min_y_px - lo[1])
        cube.origin[1] += dy
    """
    bones_dict = GM.model_bones({"minecraft:geometry": [{"bones": bones}]})
    cache = {}
    lo_y = 1e9
    for bname, b in bones_dict.items():
        for c in b.get("cubes", []):
            l, h = GM.cube_world_aabb_px(bones_dict, bname, c, cache)
            lo_y = min(lo_y, l[1])
    dy = min_y_px - lo_y
    if abs(dy) < 1e-3:
        return 0.0
    for b in bones:
        if b.get("pivot"):
            b["pivot"][1] = round(b["pivot"][1] + dy, 3)
        for c in b.get("cubes", []):
            c["origin"][1] = round(c["origin"][1] + dy, 3)
    return dy


def assign_uvs(bones, cube_records, slots):
    by_name = {b["name"]: b for b in bones}
    for name, size, faces in cube_records:
        b = by_name[name]
        if not b["cubes"]:
            continue
        u, v = slots[name]
        b["cubes"][0]["uv"] = [int(round(u)), int(round(v))]


def main():
    os.makedirs(GEO_DIR, exist_ok=True)
    os.makedirs(ANIM_DIR, exist_ok=True)
    os.makedirs(TEX_DIR, exist_ok=True)

    g, BIN = load_glb()
    src_img = Image.open(TEX_PNG).convert("RGBA")

    bones, cube_records = build_geometry(g, BIN)
    atlas_rgba, slots, glow_rects = build_atlas(cube_records, src_img)
    assign_uvs(bones, cube_records, slots)

    # 可见边界
    xs = []; ys = []; zs = []
    for b in bones:
        for c in b.get("cubes", []):
            o = c["origin"]; s = c["size"]
            xs += [o[0], o[0]+s[0]]; ys += [o[1], o[1]+s[1]]; zs += [o[2], o[2]+s[2]]
    vbw = max(6.0, (max(xs)-min(xs))/16.0 + 1)
    vbh = max(8.0, (max(ys)-min(ys))/16.0 + 1)
    vbo = [0, (max(ys)+min(ys))/2.0/16.0, 0]

    data = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.lost_realm.warden_form2",
                "texture_width": ATLAS,
                "texture_height": ATLAS,
                "visible_bounds_width": round(vbw, 2),
                "visible_bounds_height": round(vbh, 2),
                "visible_bounds_offset": [round(v, 2) for v in vbo],
            },
            "bones": bones,
        }]
    }

    # 自检
    bones_dict = GM.model_bones(data)
    cache = {}
    lo_all = np.array([1e9, 1e9, 1e9]); hi_all = np.array([-1e9, -1e9, -1e9])
    for bname, b in bones_dict.items():
        for c in b.get("cubes", []):
            l, h = GM.cube_world_aabb_px(bones_dict, bname, c, cache)
            lo_all = np.minimum(lo_all, l); hi_all = np.maximum(hi_all, h)
    cx0 = (lo_all[0] + hi_all[0]) * 0.5
    if abs(cx0) > 0.6:
        shifted = center_model_x(bones)
        # 重新计算 AABB
        bones_dict = GM.model_bones(data)
        cache = {}
        lo_all = np.array([1e9, 1e9, 1e9]); hi_all = np.array([-1e9, -1e9, -1e9])
        for bname, b in bones_dict.items():
            for c in b.get("cubes", []):
                l, h = GM.cube_world_aabb_px(bones_dict, bname, c, cache)
                lo_all = np.minimum(lo_all, l); hi_all = np.maximum(hi_all, h)
    cx = (lo_all[0] + hi_all[0]) * 0.5
    print(f"  AABB x[{lo_all[0]:.1f},{hi_all[0]:.1f}] y[{lo_all[1]:.1f},{hi_all[1]:.1f}] "
          f"z[{lo_all[2]:.1f},{hi_all[2]:.1f}] | x中心={cx:.2f}px | 骨骼 {len(bones)}")
    if abs(cx) > 1.0:
        raise SystemExit(f"!! A2 核心未居中 x中心={cx:.2f}")

    # 贴地：把整棵树抬起，使最低点落在脚底平面 y=0，
    # 修掉「二阶段身体陷进地里」的问题。
    dy = lift_model_y(bones, min_y_px=0.0)
    if abs(dy) > 1e-3:
        print(f"  贴地抬升 {dy:.2f}px（最低点 {lo_all[1]:.2f} -> 0.00）")
        bones_dict = GM.model_bones(data)
        cache = {}
        lo_all = np.array([1e9, 1e9, 1e9]); hi_all = np.array([-1e9, -1e9, -1e9])
        for bname, b in bones_dict.items():
            for c in b.get("cubes", []):
                l, h = GM.cube_world_aabb_px(bones_dict, bname, c, cache)
                lo_all = np.minimum(lo_all, l); hi_all = np.maximum(hi_all, h)
        print(f"  抬升后 AABB y[{lo_all[1]:.1f},{hi_all[1]:.1f}]")
    if lo_all[1] < -0.5:
        raise SystemExit(f"!! A1 入地：y_min={lo_all[1]:.2f}（抬升后仍低于地面）")
    if hi_all[1] > 80.0:
        print(f"  !! A4 高度偏高 y_max={hi_all[1]:.1f}")

    # 写几何
    with open(GEO_OUT, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    # 写贴图
    Image.fromarray(atlas_rgba, "RGBA").save(TEX_OUT)
    glow = build_glowmask(atlas_rgba, glow_rects)
    Image.fromarray(glow, "RGBA").save(GLOW_OUT)
    # 写动画
    anim = build_animation(g, BIN)
    with open(ANIM_OUT, 'w', encoding='utf-8') as f:
        json.dump(anim, f, ensure_ascii=False, indent=2)

    # 动画骨骼自检
    gbones = {b["name"] for b in bones}
    problems = []
    for aname, a in anim["animations"].items():
        for bn in a["bones"]:
            if bn not in gbones:
                problems.append(f"{aname} -> {bn}")
    if problems:
        print("  !! 动画引用了不存在的骨骼：")
        for p in problems[:20]:
            print("   -", p)
        raise SystemExit(1)

    print(f"  OK: 几何 {len(bones)} 骨骼 / {len(cube_records)} 立方 | 动画 9 条 | "
          f"贴图 {os.path.basename(TEX_OUT)} + glowmask")


if __name__ == '__main__':
    main()
