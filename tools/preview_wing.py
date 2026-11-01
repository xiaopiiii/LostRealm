#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
羽翼网格预览渲染器（dv26）。

与 `render_preview.py`（画 GeckoLib 长方体）不同，这里画的是
**自由三角网格**：正交投影 + z-buffer + 逐像素 UV 采样 + Lambert 光照。
用来在写 Java 之前先把「形状对不对」确认掉。

运行：python3 tools/preview_wing.py
输出：/workspace/wing_preview.png
"""

import json
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'lost_realm')
MESH = os.path.join(RES, 'models', 'entity', 'lord_wing.json')
TEX = os.path.join(RES, 'textures', 'entity', 'lord_wing.png')

SCALE = 5.0          # 模型像素 -> 屏幕像素
BODY_QUADS = []
PAD = 30

# 人形参考（模型像素，与 gen_geo.py 的 3 格骨架一致）
HUMAN = [
    # (x0, y0, z0, x1, y1, z1)
    (-6, 36, -6, 6, 48, 6),       # 头
    (-6, 18, -3, 6, 36, 3),       # 躯干
    (-12, 18, -3, -6, 36, 3),     # 右臂
    (6, 18, -3, 12, 36, 3),       # 左臂
    (-6, 0, -3, 0, 18, 3),        # 右腿
    (0, 0, -3, 6, 18, 3),         # 左腿
]

VIEWS = {
    "front": lambda p: (-p[0], -p[1], p[2]),
    "iso": lambda p: ((p[0] - p[2]) * 0.866, (p[0] + p[2]) * 0.5 - p[1],
                      (p[0] + p[2]) * 0.577),
}


sys.path.insert(0, HERE)
import gecko_math as GM          # noqa: E402
import render_preview as RP      # noqa: E402


def body_quads(geo_name):
    """把 geo.json 的人形方块拆成「四边形 + UV 矩形」，与羽翼共用同一套投影。"""
    with open(os.path.join(RES, 'geo', geo_name + '.geo.json'), encoding='utf-8') as f:
        data = json.load(f)
    bones = GM.model_bones(data)
    cache = {}
    out = []
    for bname, b in bones.items():
        for c in b.get('cubes', []):
            # gecko_math 给的是 block 单位 -> ×16 回到模型像素
            corners = [np.array(p) * 16.0 for p in RP._corners_px(bones, bname, c, cache)]
            rects = RP.face_rects(c)
            for fi, idx in enumerate(RP.FACE_IDX):
                rect = rects.get(RP.FACE_KEYS[fi])
                if rect is None:
                    continue
                out.append(([corners[i] for i in idx], rect, fi))
    return out


def quad_to_tris(quad, rect, fi, to_screen, depth_of):
    """把四边形的 4 个角按「从外侧看」排成 左上/右上/左下/右下，再切成两个三角形。

    ★ 必须传**屏幕变换**（含平移缩放），不能传裸投影 ——
      只传投影的话 s3 会是 (-6, 6) 这种以模型原点为心的小坐标，
      光栅窗口落进贴图左上角的空白区，采样 alpha 全 0，
      画面上就是「人形完全不见了」（这个 bug 刚踩过）。

    返回 [(s3, uv3, z3), ...]。
    """
    n = RP.FACE_NORMAL[fi]
    right, up = RP.face_axes(np.array(n, dtype=float))
    scored = []
    for p in quad:
        su = float(np.dot(p, right))
        sv = float(np.dot(p, up))
        scored.append((su, sv, p))
    umin = min(t[0] for t in scored)
    umax = max(t[0] for t in scored)
    vmin = min(t[1] for t in scored)
    vmax = max(t[1] for t in scored)

    def pick(uu, vv):
        for su, sv, p in scored:
            if abs(su - uu) < 1e-6 and abs(sv - vv) < 1e-6:
                return p
        return scored[0][2]

    tl = pick(umin, vmax)
    tr = pick(umax, vmax)
    bl = pick(umin, vmin)
    br = pick(umax, vmin)
    x0, y0, x1, y1 = rect
    uv_tl = (x0, y0)
    uv_tr = (x1, y0)
    uv_bl = (x0, y1)
    uv_br = (x1, y1)
    out = []
    for tri in ((0, 1, 2), (0, 2, 3)):
        pts = [(tl, uv_tl), (tr, uv_tr), (bl, uv_bl), (br, uv_br)]
        s3 = []
        uv3 = []
        z3 = []
        for k in tri:
            p, uv = pts[k]
            s3.append(to_screen(p))
            uv3.append(uv)
            z3.append(depth_of(p))
        out.append((s3, uv3, z3))
    return out


def load():
    with open(MESH, encoding='utf-8') as f:
        return json.load(f)


def collect(mesh):
    """把所有羽片展开成 (tri_verts, uv, group) 列表。"""
    tris = []
    for g in mesh["groups"]:
        v = g["verts"]
        ix = g["index"]
        for k in range(0, len(ix), 3):
            tri = []
            for m in range(3):
                x, y, z, u, vv = v[ix[k + m]]
                tri.append(((x, y, z), (u, vv)))
            tris.append((tri, g["name"]))
    return tris


def render(mode, tex, tris, want_bg=(30, 32, 40)):
    proj = VIEWS[mode]
    pts = []
    for tri, _ in tris:
        for p, _uv in tri:
            pts.append(proj(p))
    xs = [q[0] for q in pts]
    ys = [q[1] for q in pts]
    # 加上人形范围
    for (x0, y0, z0, x1, y1, z1) in HUMAN:
        for xx in (x0, x1):
            for yy in (y0, y1):
                for zz in (z0, z1):
                    q = proj((xx, yy, zz))
                    xs.append(q[0])
                    ys.append(q[1])
    minx, maxx, miny, maxy = min(xs), max(xs), min(ys), max(ys)
    W = int((maxx - minx) * SCALE) + PAD * 2
    H = int((maxy - miny) * SCALE) + PAD * 2
    depth = np.full((H, W), 1e18, dtype=np.float64)
    rgb = np.zeros((H, W, 3), dtype=np.float64)
    alpha = np.zeros((H, W), dtype=np.float64)

    tarr = np.asarray(tex, dtype=np.float64)

    def scr(p):
        sx, sy, _ = proj(p)
        return ((sx - minx) * SCALE + PAD, (sy - miny) * SCALE + PAD)

    # 光照：从观察者左上前方
    L = np.array([-0.4, 0.7, -1.0])
    L = L / np.linalg.norm(L)

    for tri, _g in tris:
        p3 = [t[0] for t in tri]
        uv = [t[1] for t in tri]
        s = [scr(p) for p in p3]
        d3 = [proj(p)[2] for p in p3]

        # 面法线（世界坐标）
        e1 = np.array(p3[1]) - np.array(p3[0])
        e2 = np.array(p3[2]) - np.array(p3[0])
        n = np.cross(e1, e2)
        ln = np.linalg.norm(n)
        if ln < 1e-9:
            continue
        n = n / ln
        # 双面：法线朝观察者
        vd = {"front": np.array([0, 0, -1.0]), "iso": np.array([-0.577, -0.577, -0.577])}[mode]
        if np.dot(n, vd) > 0:
            n = -n
        lam = max(0.0, float(np.dot(n, L)))
        shade = 0.46 + 0.62 * lam

        x0 = max(0, int(math.floor(min(q[0] for q in s))))
        x1 = min(W - 1, int(math.ceil(max(q[0] for q in s))))
        y0 = max(0, int(math.floor(min(q[1] for q in s))))
        y1 = min(H - 1, int(math.ceil(max(q[1] for q in s))))
        if x1 <= x0 or y1 <= y0:
            continue

        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        # 重心坐标
        ax, ay = s[0]
        bx, by = s[1]
        cx, cy = s[2]
        det = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if abs(det) < 1e-9:
            continue
        w0 = ((by - cy) * (gx - cx) + (cx - bx) * (gy - cy)) / det
        w1 = ((cy - ay) * (gx - cx) + (ax - cx) * (gy - cy)) / det
        w2 = 1.0 - w0 - w1
        inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        if not inside.any():
            continue
        z = w0 * d3[0] + w1 * d3[1] + w2 * d3[2]
        u = w0 * uv[0][0] + w1 * uv[1][0] + w2 * uv[2][0]
        vv = w0 * uv[0][1] + w1 * uv[1][1] + w2 * uv[2][1]
        ti = np.clip((su := np.clip(u, 0, 1)) * (tex.width - 1), 0, tex.width - 1).astype(np.int32)
        tj = np.clip(np.clip(vv, 0, 1) * (tex.height - 1), 0, tex.height - 1).astype(np.int32)
        samp = tarr[tj, ti]
        a = samp[..., 3] / 255.0
        col = samp[..., :3] * shade

        sub_z = depth[y0:y1 + 1, x0:x1 + 1]
        sub_a = alpha[y0:y1 + 1, x0:x1 + 1]
        win = inside & (z < sub_z) & (a > 0.03)
        if not win.any():
            continue
        sub_z[win] = z[win]
        sub_a[win] = a[win]
        rgb[y0:y1 + 1, x0:x1 + 1][win] = col[win]

    # ---- 画 GeckoLib 人形（方块，与羽翼共用同一 z-buffer）----
    body_tex = Image.open(os.path.join(RES, 'textures', 'entity', 'warden.png')).convert('RGBA')
    bt = np.asarray(body_tex, dtype=np.float64)
    for (quad, rect, fi) in BODY_QUADS:
        for (s3, uv3, z3) in quad_to_tris(quad, rect, fi, scr,
                                         lambda pp: proj(pp)[2]):
            x0i = max(0, int(math.floor(min(q[0] for q in s3))))
            x1i = min(W - 1, int(math.ceil(max(q[0] for q in s3))))
            y0i = max(0, int(math.floor(min(q[1] for q in s3))))
            y1i = min(H - 1, int(math.ceil(max(q[1] for q in s3))))
            if x1i <= x0i or y1i <= y0i:
                continue
            gx, gy = np.meshgrid(np.arange(x0i, x1i + 1) + 0.5,
                                 np.arange(y0i, y1i + 1) + 0.5)
            (ax, ay), (bx, by), (cx, cy) = s3
            det = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
            if abs(det) < 1e-9:
                continue
            w0 = ((by - cy) * (gx - cx) + (cx - bx) * (gy - cy)) / det
            w1 = ((cy - ay) * (gx - cx) + (ax - cx) * (gy - cy)) / det
            w2 = 1.0 - w0 - w1
            ins = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            if not ins.any():
                continue
            z = w0 * z3[0] + w1 * z3[1] + w2 * z3[2]
            uu = w0 * uv3[0][0] + w1 * uv3[1][0] + w2 * uv3[2][0]
            vv = w0 * uv3[0][1] + w1 * uv3[1][1] + w2 * uv3[2][1]
            ti = np.clip(uu, 0, body_tex.width - 1).astype(np.int32)
            tj = np.clip(vv, 0, body_tex.height - 1).astype(np.int32)
            samp = bt[tj, ti]
            a = samp[..., 3] / 255.0
            col = samp[..., :3] * RP.FACE_SHADE[fi]
            sub_z = depth[y0i:y1i + 1, x0i:x1i + 1]
            sub_a = alpha[y0i:y1i + 1, x0i:x1i + 1]
            win = ins & (z < sub_z) & (a > 0.03)
            if not win.any():
                continue
            sub_z[win] = z[win]
            sub_a[win] = a[win]
            rgb[y0i:y1i + 1, x0i:x1i + 1][win] = col[win]

    out = np.zeros((H, W, 4), dtype=np.float64)
    out[..., :3] = rgb
    out[..., 3] = alpha * 255
    base = np.zeros((H, W, 4), dtype=np.float64)
    base[..., 0], base[..., 1], base[..., 2] = want_bg
    base[..., 3] = 255
    aa = out[..., 3:4] / 255.0
    comp = base.copy()
    comp[..., :3] = out[..., :3] * aa + base[..., :3] * (1 - aa)
    return Image.fromarray(comp.astype(np.uint8), "RGBA")


def main():
    global BODY_QUADS
    which = sys.argv[1] if len(sys.argv) > 1 else "warden_form3"
    BODY_QUADS = body_quads(which)
    mesh = load()
    tris = collect(mesh)
    tex = Image.open(TEX).convert("RGBA")
    f = render("front", tex, tris)
    i = render("iso", tex, tris)
    gap = 16
    canvas = Image.new("RGB", (f.width + i.width + gap, max(f.height, i.height)),
                       (30, 32, 40))
    canvas.paste(f, (0, 0))
    canvas.paste(i, (f.width + gap, 0))
    out = "/workspace/wing_preview.png"
    canvas.save(out)
    print(f"羽翼预览（左=正视，右=等距） -> {out}  {canvas.size}")


if __name__ == '__main__':
    main()
