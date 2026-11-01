#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
真·贴图预览渲染器（离线、纯 CPU、正交投影）。

=== 为什么再写一个 ===
`preview_geo.py` 只是把每个面填成**平均色** —— 造型能看，但贴图细节（脸部、
符文、剑格）全丢了，用来验收「贴图对不对」是不够的。本脚本把每个面**真正
贴上贴图**（逐面仿射变换）：

  · 正交投影是线性变换 → 平面的矩形贴图投到屏幕上仍是**平行四边形**，
    所以用 3 组点对应解出仿射矩阵、PIL `Image.AFFINE` 变换即可**精确**还原，
    不需要逐像素光栅化。
  · 画家算法按面心深度排序；同一立方体的面之间按「是否朝向相机」剔除背面。
  · 用 glowmask 提亮自发光像素，模拟 GeckoLib 的 AutoGlowingGeoLayer。

支持两种 UV：
  · uv_layout 区域（form1/form3，共享 warden.png）
  · 装箱图集槽位（form2，bbmodel 管线产出的 512 图集）

用法：
  python3 tools/render_preview.py            # 渲染三形态
  python3 tools/render_preview.py form2      # 只渲染 form2
输出：/workspace/render_{form1,form2,form3}.png（正视 | 等距）
"""

import json
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gecko_math as GM
import uv_layout as UV

GEO_DIR = os.path.join(os.path.dirname(__file__), "..",
                       "src/main/resources/assets/lost_realm/geo")
TEX_DIR = os.path.join(os.path.dirname(__file__), "..",
                       "src/main/resources/assets/lost_realm/textures/entity")
OUT = "/workspace"

# dv21 起三形态共用同一张 128x128 贴图与 uv_layout（bbmodel 管线已弃用）
FORMS = {
    "form1": ("warden_form1", "warden.png", "warden_glowmask.png"),
    # ★ dv34：二三阶段换成用户给的 bbmodel，贴图随之换成各自的重排图集
    "form2": ("warden_form2", "warden_phase2.png", "warden_phase2_glowmask.png"),
    "form3": ("warden_form3", "warden_phase3.png", "warden_phase3_glowmask.png"),
}

REG_BY_UV = {v[:2]: k for k, v in UV.REGIONS.items()}
FACE_KEYS = ["back", "right", "left", "top", "bottom", "front"]

# 与 gecko_math.cube_corners 的角序一致
#   0:(ox,oy,oz) 1:(ox,oy,ez) 2:(ox,ey,oz) 3:(ox,ey,ez)
#   4:(ex,oy,oz) 5:(ex,oy,ez) 6:(ex,ey,oz) 7:(ex,ey,ez)
FACE_IDX = [
    (1, 3, 7, 5),   # +z
    (4, 5, 7, 6),   # +x
    (0, 1, 3, 2),   # -x
    (2, 3, 7, 6),   # +y
    (0, 1, 5, 4),   # -y
    (0, 2, 6, 4),   # -z
]
FACE_SHADE = [0.72, 0.84, 0.84, 1.10, 0.55, 0.96]   # 背面/侧面/顶面/底面/正面
FACE_NORMAL = [(0, 0, 1), (1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, -1)]


def atlas_faces(u, v, w, h, d):
    return {
        "top":    (u + d,         v,     u + d + w,         v + d),
        "bottom": (u + d + w,     v,     u + 2 * d + w,     v + d),
        "right":  (u,             v + d, u + d,             v + d + h),
        "front":  (u + d,         v + d, u + d + w,         v + d + h),
        "left":   (u + d + w,     v + d, u + 2 * d + w,     v + d + h),
        "back":   (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def face_rects(cube):
    u, v = cube.get("uv", (0, 0))
    w, h, d = [int(round(t)) for t in cube["size"]]
    reg = REG_BY_UV.get((u, v))
    if reg is not None:
        return UV.faces(reg, w, h, d)
    return atlas_faces(u, v, w, h, d)


def project(p, mode):
    """正交投影。

    ★ front 的横轴必须是 **-x**：实体朝 -z，正视 = 相机在 -z 侧朝 +z 看，
      此时「屏幕右」= cross(视线, up) = (0,0,1)×(0,1,0) = (-1,0,0) = -x。
      写成 +x 的话画出来的是**背面**（dv20 初版就是这样，跟等距视图对不上）。
    """
    x, y, z = p
    if mode == "front":
        return -x, -y
    return (x - z) * 0.866, (x + z) * 0.5 - y


import math as _math
# 正交投影的「视线方向」（指向屏幕内部）——由投影矩阵的零空间解出：
#   front: x=屏幕右, y=屏幕上  -> v=(0,0,1)
#   iso  : sx=(x-z)*0.866, sy=(x+z)*0.5-y -> 解 P·v=0 得 v∝(1,1,1)
VIEW_DIR = {"front": np.array([0.0, 0.0, 1.0]),
            "iso": np.array([1.0, 1.0, 1.0]) / _math.sqrt(3.0)}


def face_depth(pts, mode):
    """沿视线方向的距离（越大越远）。升序绘制 = 远的先画。"""
    v = VIEW_DIR[mode]
    return float(np.mean([p[0] * v[0] + p[1] * v[1] + p[2] * v[2] for p in pts]))


def face_axes(n):
    """给定面法线（世界坐标），返回 (u_dir, up_dir)：

    约定「从外侧看这张脸」——u 向右、up 向上（所以纹理 v 向下）。
    这样 bbmodel 里逐面画的 uv 矩形才会**正着**贴上去，不会转 90° 或镜像。
    """
    d = (-n[0], -n[1], -n[2])            # 视线方向（由外向内）
    right = np.cross(d, (0, 1, 0))
    if np.linalg.norm(right) < 1e-6:      # 顶/底面：视线与 up 平行，退化了
        right = np.array([1.0, 0.0, 0.0])
    right = right / np.linalg.norm(right)
    up = np.cross(right, d)
    return right, up


def affine_from_quad(src_rect, quad):
    """源矩形(正交) -> 屏幕平行四边形 的仿射矩阵；返回 PIL 用的**逆**映射。

    PIL 的 Image.AFFINE 需要「目标像素 -> 源像素」，所以求逆。
    """
    sx0, sy0, sx1, sy1 = src_rect
    (p0, p1, p2, _p3) = quad          # p0=(sx0,sy0) p1=(sx1,sy0) p2=(sx0,sy1)
    ax = (p1[0] - p0[0]) / max(1e-6, sx1 - sx0)
    ay = (p1[1] - p0[1]) / max(1e-6, sx1 - sx0)
    bx = (p2[0] - p0[0]) / max(1e-6, sy1 - sy0)
    by = (p2[1] - p0[1]) / max(1e-6, sy1 - sy0)
    cx = p0[0] - ax * sx0 - bx * sy0
    cy = p0[1] - ay * sx0 - by * sy0
    det = ax * by - ay * bx
    if abs(det) < 1e-9:
        return None
    ia = by / det
    ib = -bx / det
    ic = (bx * cy - by * cx) / det
    id_ = -ay / det
    ie = ax / det
    if_ = (ay * cx - ax * cy) / det
    return (ia, ib, ic, id_, ie, if_)


def shade_color(img, factor):
    a = np.asarray(img, dtype=np.float32)
    a[..., :3] *= factor
    np.clip(a, 0, 255, out=a)
    return Image.fromarray(a.astype(np.uint8), "RGBA")


def sample_track(track, t):
    """在时间 t 线性插值取一帧（post/vector 两种通道通用）。

    注意：key 是 str(t)，格式不统一（"0" / "1.0"），必须保留原始 key。
    """
    pairs = sorted(((float(k), k) for k in track))
    if not pairs:
        return None
    if t <= pairs[0][0]:
        return track[pairs[0][1]]
    if t >= pairs[-1][0]:
        return track[pairs[-1][1]]
    for i in range(len(pairs) - 1):
        t0, k0 = pairs[i]
        t1, k1 = pairs[i + 1]
        if t0 <= t <= t1:
            f = (t - t0) / (t1 - t0)
            a, b = track[k0], track[k1]
            key = "post" if "post" in a else "vector"
            return {key: [a[key][j] + (b[key][j] - a[key][j]) * f for j in range(3)]}
    return track[pairs[0][1]]


def apply_pose(data, anim_id, t):
    """把动画某一帧的 rotation **覆盖**到骨骼上（GeckoLib 是替换不是叠加）。

    为什么必须做这一步：静止姿态看着正常、一播动画就散架，是这类骨骼模型
    最容易漏掉的验收项（dv20 第一次就是只验了静止姿态）。
    """
    if anim_id is None:
        return
    ap = os.path.join(os.path.dirname(__file__), "..",
                      "src/main/resources/assets/lost_realm/animations")
    geo_name = data["minecraft:geometry"][0]["description"]["identifier"].split(".")[-1]
    path = os.path.join(ap, geo_name + ".animation.json")
    if not os.path.exists(path):
        return
    anim = json.load(open(path, encoding="utf-8"))["animations"].get(anim_id)
    if not anim:
        return
    tracks = anim["bones"]
    for b in data["minecraft:geometry"][0]["bones"]:
        chans = tracks.get(b["name"])
        if not chans:
            continue
        if "rotation" in chans:
            v = sample_track(chans["rotation"], t)
            if v:
                b["rotation"] = v["post"]


def render(geo_name, tex_name, glow_name, mode, scale=28.0, pad=30,
           bg=(38, 40, 50, 255), want_bg=True, anim_id=None, t=0.0,
           yaw=0.0, bounds=None):
    """z-buffer 软件光栅：逐面精确贴图 + 逐像素深度测试。

    为什么不用画家算法：这个模型 97 个立方体互相穿插（剑穿过手、下摆压在腿上），
    按面心排序必然出现「近的薄片被远的大面盖住」的错乱。z-buffer 没有这个问题。

    :param yaw: 绕模型 y 轴旋转的角度（度）。做「转盘预览」用。
    :param bounds: (minx,maxx,miny,maxy) 固定取景框。多角度连拍时必须传，
        否则每帧各算一次包围盒 → 逐帧缩放/位置都变，拼成动画会抖。
    :return: (PIL.Image, bounds)
    """
    with open(os.path.join(GEO_DIR, geo_name + ".geo.json"), encoding="utf-8") as f:
        data = json.load(f)
    apply_pose(data, anim_id, t)
    bones = GM.model_bones(data)
    tex = Image.open(os.path.join(TEX_DIR, tex_name)).convert("RGBA")
    tarr = np.asarray(tex, dtype=np.float32)
    gp = os.path.join(TEX_DIR, glow_name)
    if os.path.exists(gp):
        garr = np.asarray(Image.open(gp).convert("RGBA"), dtype=np.float32)[..., 3]
    else:
        garr = None

    ca, sa = float(np.cos(np.radians(yaw))), float(np.sin(np.radians(yaw)))

    def ry(p):
        return np.array([p[0] * ca + p[2] * sa, p[1], -p[0] * sa + p[2] * ca])

    cache = {}
    quads = []
    for bname, b in bones.items():
        for c in b.get("cubes", []):
            corners = _corners_px(bones, bname, c, cache)
            rects = face_rects(c)
            for fi, idx in enumerate(FACE_IDX):
                quad3 = [ry(corners[i]) for i in idx]
                # ★ 必须用**实际角点**算法线，不能用固定的 FACE_NORMAL ——
                #   dv27 起方块会被骨骼旋转（倾斜的羽毛），固定法线会把该留的面剔掉、
                #   该剔的留下，画面上就是「羽毛断成一截一截」。
                e1 = quad3[1] - quad3[0]
                e2 = quad3[2] - quad3[0]
                n = np.cross(e1, e2)
                ln = float(np.linalg.norm(n))
                if ln < 1e-9:
                    continue
                n = n / ln
                vd = VIEW_DIR[mode]
                if float(np.dot(n, vd)) > -1e-6:
                    continue                     # 背面剔除
                rect = rects.get(FACE_KEYS[fi])
                if rect is None:
                    continue
                x0, y0, x1, y1 = [int(round(t)) for t in rect]
                if x1 <= x0 or y1 <= y0:
                    continue
                quads.append((quad3, (x0, y0, x1, y1), fi))

    allp = [p for q in quads for p in q[0]]
    if bounds is not None:
        minx, maxx, miny, maxy = bounds
    else:
        xs = [project(p, mode)[0] for p in allp]
        ys = [project(p, mode)[1] for p in allp]
        minx, maxx, miny, maxy = min(xs), max(xs), min(ys), max(ys)
    W = int((maxx - minx) * scale) + pad * 2
    H = int((maxy - miny) * scale) + pad * 2
    depth = np.full((H, W), 1e18, dtype=np.float32)
    color = np.zeros((H, W, 3), dtype=np.float32)
    alpha = np.zeros((H, W), dtype=np.float32)

    vd = VIEW_DIR[mode]

    def scr(p):
        sx, sy = project(p, mode)
        return ((sx - minx) * scale + pad, (sy - miny) * scale + pad)

    for quad3, rect, fi in quads:
        # 排成 左上/右上/左下/右下（先左右、再上下）
        pts = sorted(quad3, key=lambda p: -project(p, mode)[1])
        if len(pts) != 4:
            continue
        top = sorted(pts[:2], key=lambda p: project(p, mode)[0])
        bot = sorted(pts[2:], key=lambda p: project(p, mode)[0])
        tl3, tr3, bl3, br3 = top[0], top[1], bot[0], bot[1]
        tl, tr, bl = scr(tl3), scr(tr3), scr(bl3)

        ex = np.array([tr[0] - tl[0], tr[1] - tl[1]])
        ey = np.array([bl[0] - tl[0], bl[1] - tl[1]])
        det = ex[0] * ey[1] - ex[1] * ey[0]
        if abs(det) < 1e-6:
            continue

        x0i = max(0, int(np.floor(min(tl[0], tr[0], bl[0], scr(br3)[0]))))
        x1i = min(W - 1, int(np.ceil(max(tl[0], tr[0], bl[0], scr(br3)[0]))))
        y0i = max(0, int(np.floor(min(tl[1], tr[1], bl[1], scr(br3)[1]))))
        y1i = min(H - 1, int(np.ceil(max(tl[1], tr[1], bl[1], scr(br3)[1]))))
        if x1i <= x0i or y1i <= y0i:
            continue

        gx, gy = np.meshgrid(np.arange(x0i, x1i + 1) + 0.5,
                             np.arange(y0i, y1i + 1) + 0.5)
        px = gx - tl[0]
        py = gy - tl[1]
        u = (px * ey[1] - py * ey[0]) / det
        v = (-px * ex[1] + py * ex[0]) / det
        inside = (u >= -0.002) & (u <= 1.002) & (v >= -0.002) & (v <= 1.002) \
            & (u + v <= 1.002 + 1e-6)
        # 平行四边形：还要补 u+v>1 的另一半（本模型的面必为平行四边形）
        inside = (u >= -0.002) & (u <= 1.002) & (v >= -0.002) & (v <= 1.002)
        if not inside.any():
            continue

        # 深度：正交投影下沿面线性插值
        zt = tl3[0] * vd[0] + tl3[1] * vd[1] + tl3[2] * vd[2]
        zr = tr3[0] * vd[0] + tr3[1] * vd[1] + tr3[2] * vd[2]
        zb = bl3[0] * vd[0] + bl3[1] * vd[1] + bl3[2] * vd[2]
        z = zt + (zr - zt) * u + (zb - zt) * v

        # ★ 纹理 v 方向：v=0 对应四边形的**上边**，而贴图行号向下增大，
        #   所以要从 sy1 往 sy0 走（实测不翻会整体上下颠倒：与图集槽位比对
        #   MAE 38.2 vs 翻转后 3.3）。
        sx0, sy0, sx1, sy1 = rect
        su = sx0 + (sx1 - sx0) * np.clip(u, 0, 1)
        sv = sy1 - (sy1 - sy0) * np.clip(v, 0, 1)
        si = np.clip(su.astype(np.int32), 0, tex.width - 1)
        sj = np.clip(sv.astype(np.int32), 0, tex.height - 1)
        samp = tarr[sj, si]
        a = samp[..., 3] / 255.0
        rgb = samp[..., :3] * FACE_SHADE[fi]
        if garr is not None:
            rgb = np.clip(rgb + (garr[sj, si] / 255.0)[..., None] * 45.0, 0, 255)

        sub_z = depth[y0i:y1i + 1, x0i:x1i + 1]
        sub_a = alpha[y0i:y1i + 1, x0i:x1i + 1]
        win = inside & (z < sub_z) & (a > 0.02)
        if not win.any():
            continue
        sub_z[win] = z[win]
        sub_a[win] = a[win]
        color[y0i:y1i + 1, x0i:x1i + 1][win] = rgb[win]

    out = np.zeros((H, W, 4), dtype=np.float32)
    out[..., :3] = color
    out[..., 3] = alpha * 255.0
    if want_bg:
        base = np.zeros((H, W, 4), dtype=np.float32)
        base[..., 0], base[..., 1], base[..., 2] = bg[0], bg[1], bg[2]
        base[..., 3] = 255
        aa = out[..., 3:4] / 255.0
        comp = base.copy()
        comp[..., :3] = out[..., :3] * aa + base[..., :3] * (1 - aa)
        out = comp
    return Image.fromarray(out.astype(np.uint8), "RGBA"), (minx, maxx, miny, maxy)


def _corners_px(bones, bname, cube, cache):
    M = GM.bone_matrix(bones, bname, cache)
    corners = GM.cube_corners(cube) if hasattr(GM, "cube_corners") else None
    if corners is None:
        raise SystemExit("gecko_math 缺少 cube_corners")
    out = []
    for c in corners:
        # ★ 单位铁律：gecko_math 全程用 **block**（GeckoLib 内部空间）。
        #   `bone_matrix` 的平移项是 pivot/16，cube_corners 也是 block ——
        #   所以这里**绝不能**再乘 16：乘了就等于把 pivot 平移缩小 16 倍，
        #   旋转中心会退化成原点，带 rotation 的骨骼（触手/头冠/镰刀）全错位。
        v = np.array([c[0], c[1], c[2], 1.0])
        w = M @ v
        out.append(np.array([float(w[0]), float(w[1]), float(w[2])]))
    return out


def main():
    args = sys.argv[1:]
    anim_id = None
    t = 0.0
    which = []
    i = 0
    while i < len(args):
        if args[i] == "--anim":
            anim_id = args[i + 1]
            t = float(args[i + 2])
            i += 3
        else:
            which.append(args[i])
            i += 1
    which = which or ["form1", "form2", "form3"]
    try:
        font = ImageFont.truetype(
            "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 22)
    except Exception:
        font = ImageFont.load_default()

    for key in which:
        geo_name, tex, glowm = FORMS[key]
        f, _ = render(geo_name, tex, glowm, "front", anim_id=anim_id, t=t)
        i, _ = render(geo_name, tex, glowm, "iso", anim_id=anim_id, t=t)

        gap = 22
        single = Image.new("RGBA", (f.width + i.width + gap,
                                    max(f.height, i.height)), (38, 40, 50, 255))
        single.paste(f, (0, 0))
        single.paste(i, (f.width + gap, 0))
        suffix = "" if anim_id is None else "_" + anim_id.split(".")[-1]
        path = os.path.join(OUT, f"render_{key}{suffix}.png")
        single.save(path)
        print(f"  {key}: {path}  {single.width}x{single.height}"
              f"（左=正视，右=等距）")


if __name__ == "__main__":
    print("真贴图预览渲染 ->")
    main()
