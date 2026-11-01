#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
渲染对照：把 GLB 原模型 与 转换后的 geo+图集 从同一相机（正面/等距）画出来，
肉眼比对轴习惯（x 居中、z 朝向）与造型是否一致。
仅用于离线自检，不进 jar。
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw
from pygltflib import GLTF2

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gecko_math as GM

GLB = "/root/inspect_zip/contents/遗落之主二阶段建模+动画.glb"
TEX_PNG = "/root/inspect_zip/glb_tex.png"
GEO = "/workspace/medals-mod/src/main/resources/assets/lost_realm/geo/warden_form2.geo.json"
ATLAS = "/workspace/medals-mod/src/main/resources/assets/lost_realm/textures/entity/warden_phase2.png"

FACE_IDX = [(1, 3, 7, 5), (4, 5, 7, 6), (0, 1, 3, 2), (2, 3, 7, 6), (0, 1, 5, 4), (0, 2, 6, 4)]
FACE_NORM = [(0, 0, 1), (1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, -1)]


def load_glb():
    g = GLTF2().load(GLB)
    return g, g.binary_blob()


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
    return np.array([
        [1 - 2*(y*y + z*z), 2*(x*y - z*w), 2*(x*z + y*w)],
        [2*(x*y + z*w), 1 - 2*(x*x + z*z), 2*(y*z - x*w)],
        [2*(x*z - y*w), 2*(y*z + x*w), 1 - 2*(x*x + y*y)]])


def glb_world_matrices(g):
    """返回每个节点的世界矩阵（block 单位，含父链）。"""
    parent_of = {}
    for i, n in enumerate(g.nodes):
        for c in (n.children or []):
            parent_of[c] = i
    mats = {}
    def rec(i):
        n = g.nodes[i]
        T = np.eye(4); T[:3, 3] = n.translation or [0, 0, 0]
        R = quat_to_R(*(n.rotation or [0, 0, 0, 1]))
        M = np.eye(4); M[:3, :3] = R
        L = T @ M
        if i in parent_of:
            W = mats[parent_of[i]] @ L
        else:
            W = L
        mats[i] = W
        for c in (n.children or []):
            rec(c)
    for r in (g.scenes[g.scene].nodes if g.scene is not None else g.scenes[0].nodes):
        rec(r)
    return mats


def glb_cubes(g, BIN, mats, src_img):
    cubes = []
    for i, n in enumerate(g.nodes):
        if n.mesh is None:
            continue
        m = g.meshes[n.mesh]
        pr = m.primitives[0]
        pos = acc_np(g, BIN, g.accessors[pr.attributes.POSITION])
        uv = acc_np(g, BIN, g.accessors[pr.attributes.TEXCOORD_0])
        W = mats[i]
        corners = []
        for xi in (0, 1):
            for yi in (0, 1):
                for zi in (0, 1):
                    p = np.array([pos[:, 0].min() if xi == 0 else pos[:, 0].max(),
                                  pos[:, 1].min() if yi == 0 else pos[:, 1].max(),
                                  pos[:, 2].min() if zi == 0 else pos[:, 2].max()])
                    wc = W @ np.array([p[0], p[1], p[2], 1.0])
                    corners.append(wc[:3])
        corners = np.array(corners)  # 8x3, index = xi*4+yi*2+zi
        # 每面颜色：取该面 4 顶点 UV 平均 -> 采样原图
        faces = []
        for fk in range(6):
            idx = FACE_IDX[fk]
            cidx = [xi*4+yi*2+zi for (xi, yi, zi) in
                    [((k >> 2) & 1, (k >> 1) & 1, k & 1) for k in idx]]
            uvs = uv[[4 * (c // 4) + (c % 4) for c in cidx]] if False else None
            # 直接用 24 顶点里的 4 个：重算
            sel = []
            for k in idx:
                xi, yi, zi = (k >> 2) & 1, (k >> 1) & 1, k & 1
                # 找到满足该角标的顶点
                cand = np.where((np.isclose(pos[:, 0], pos[:, 0].min() if xi == 0 else pos[:, 0].max())) &
                                (np.isclose(pos[:, 1], pos[:, 1].min() if yi == 0 else pos[:, 1].max())) &
                                (np.isclose(pos[:, 2], pos[:, 2].min() if zi == 0 else pos[:, 2].max())))[0]
                sel.append(uv[cand[0]])
            sel = np.array(sel)
            u = float(sel[:, 0].mean()); v = float(sel[:, 1].mean())
            sx = int(round(u * src_img.width)); sv = int(round((1 - v) * src_img.height))
            col = src_img.getpixel((min(max(sx, 0), src_img.width - 1),
                                    min(max(sv, 0), src_img.height - 1)))
            faces.append(([corners[k] for k in idx], col[:3]))
        cubes.append(faces)
    return cubes


def geo_cubes(geo_path, atlas_img):
    d = json.load(open(geo_path))
    bd = GM.model_bones(d)
    cache = {}
    cubes = []
    for b in d["minecraft:geometry"][0]["bones"]:
        for c in b.get("cubes", []):
            if c.get("uv") is None:
                continue
            corners = GM.cube_corners(c)  # block 单位（gecko 内部）
            M = GM.bone_matrix(bd, b["name"], cache)
            wc = []
            for cc in corners:
                v = M @ np.array([cc[0], cc[1], cc[2], 1.0])
                wc.append(np.array([v[0], v[1], v[2]]))
            wc = np.array(wc)
            # 转成 JSON 像素系（和 GLB 对齐：_to_px 镜像 x）
            wc = np.array([[-p[0]*16, p[1]*16, p[2]*16] for p in wc])
            u, v0 = c["uv"]
            w, h, dpt = c["size"]
            # 六面槽（与 build_atlas 同约定）
            slot = {
                0: (u + dpt, v0, w, dpt),          # +z back
                1: (u, v0 + dpt, dpt, h),          # +x right
                2: (u + dpt + w, v0 + dpt, dpt, h),  # -x left
                3: (u + dpt, v0 + dpt, w, h),      # +y top
                4: (u + dpt + w, v0, w, dpt),      # -y bottom
                5: (u + 2*dpt + w, v0 + dpt, w, h),  # -z front
            }
            faces = []
            for fk in range(6):
                sx, sy, sw, sh = slot[fk]
                sw = max(1, int(round(sw))); sh = max(1, int(round(sh)))
                crop = atlas_img.crop((int(sx), int(sy), int(sx)+sw, int(sy)+sh)).convert("RGB")
                cols = np.array(crop, dtype=float).reshape(-1, 3)
                col = cols.mean(axis=0)
                if not np.all(np.isfinite(col)):
                    col = np.array([120.0, 120.0, 120.0])
                faces.append(([wc[k] for k in FACE_IDX[fk]], tuple(col.astype(int))))
            cubes.append(faces)
    return cubes


def render(cubes, view, W=420, H=460, bg=(20, 20, 28)):
    # 收集所有点定位
    allp = np.array([p for cu in cubes for f in cu for p in f[0]])
    # 视图变换
    def proj(p):
        x, y, z = p
        if view == "front":
            return np.array([x, y, z])
        else:  # iso
            a = math.radians(32); b = math.radians(22)
            # Ry
            x1 = x*math.cos(a) + z*math.sin(a)
            z1 = -x*math.sin(a) + z*math.cos(a)
            y1 = y
            # Rx tilt
            y2 = y1*math.cos(b) - z1*math.sin(b)
            z2 = y1*math.sin(b) + z1*math.cos(b)
            return np.array([x1, y2, z2])
    pts = [proj(p) for cu in cubes for f in cu for p in f[0]]
    pts = np.array(pts)
    cx = (pts[:, 0].min() + pts[:, 0].max())/2
    cy = (pts[:, 1].min() + pts[:, 1].max())/2
    span = max(pts[:, 0].ptp(), pts[:, 1].ptp())
    sc = (min(W, H) - 30) / max(span, 1e-3)
    img = Image.new("RGB", (W, H), bg)
    dr = ImageDraw.Draw(img, "RGBA")
    # 收集面（世界点 + 颜色 + 深度）
    faces = []
    for cu in cubes:
        for f in cu:
            wp = f[0]
            vp = [proj(p) for p in wp]
            depth = sum(p[2] for p in vp)/4
            sp = [( (p[0]-cx)*sc + W/2, H/2 - (p[1]-cy)*sc) for p in vp]
            col = f[1]
            faces.append((depth, sp, col, wp))
    faces.sort(key=lambda x: x[0])  # 远（z 大）先画
    light = np.array([0.35, 0.7, 0.55]); light /= np.linalg.norm(light)
    for depth, sp, col, wpts in faces:
        # 世界空间法线用于明暗
        a = np.array(wpts[1]) - np.array(wpts[0]); bb = np.array(wpts[2]) - np.array(wpts[0])
        n3 = np.cross(a, bb)
        nl = np.linalg.norm(n3)
        if nl < 1e-9:
            shade = 0.7
        else:
            shade = 0.5 + 0.5*abs(np.dot(n3/nl, light))
        r = min(255, int(col[0]*shade)); g = min(255, int(col[1]*shade)); b = min(255, int(col[2]*shade))
        dr.polygon(sp, fill=(r, g, b))
    return img


def main():
    g, BIN = load_glb()
    src = Image.open(TEX_PNG).convert("RGB")
    atlas = Image.open(ATLAS).convert("RGB")
    glb = glb_cubes(g, BIN, glb_world_matrices(g), src)
    geo = geo_cubes(GEO, atlas)
    gf = render(glb, "front"); gi = render(glb, "iso")
    xf = render(geo, "front"); xi = render(geo, "iso")
    # 拼接对照
    gw, gh = gf.size
    canvas = Image.new("RGB", (gw*2+20, gh*2+20), (10, 10, 14))
    canvas.paste(gf, (0, 0)); canvas.paste(gi, (0, gh+10))
    canvas.paste(xf, (gw+20, 0)); canvas.paste(xi, (gw+20, gh+10))
    out = "/root/inspect_zip/compare.png"
    canvas.save(out)
    gf.save("/root/inspect_zip/ref_front.png"); xf.save("/root/inspect_zip/conv_front.png")
    gi.save("/root/inspect_zip/ref_iso.png"); xi.save("/root/inspect_zip/conv_iso.png")
    print("saved", out, "glb cubes", len(glb), "geo cubes", len(geo))


if __name__ == '__main__':
    main()
