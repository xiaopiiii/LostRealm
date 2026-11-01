#!/usr/bin/env python3
"""
dv16 几何预览渲染器（离线、纯 CPU）。

把 .geo.json 按 GeckoLib 的变换语义（见 gecko_math.py）算成世界像素坐标，
再用**正视**与**等距**两种投影 + 画家算法画成 2D 预览。
配色采样 warden.png / warden_glowmask.png，所以"长什么样"基本可信；
发光像素用 glowmask 的颜色提亮，模拟自发光。

输出（/workspace/）：
    preview_form{1,2,3}.png        每形态：正视 | 等距
    preview_all.png                三形态并排（每格上=正视、下=等距）
"""
import json
import os
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gecko_math as GM

GEO_DIR = os.path.join(os.path.dirname(__file__), "..",
                        "src/main/resources/assets/lost_realm/geo")
TEX_DIR = os.path.join(os.path.dirname(__file__), "..",
                       "src/main/resources/assets/lost_realm/textures/entity")
OUT = "/workspace"

# ★ 形态 -> 贴图：form2 走 bbmodel 管线产出的 512 图集，其它走 warden.png
TEXTURE_BY_FORM = {
    "warden_form2": ("warden_phase2.png", "warden_phase2_glowmask.png"),
}
TEX = Image.open(os.path.join(TEX_DIR, "warden.png")).convert("RGBA")
try:
    GLOW = Image.open(os.path.join(TEX_DIR, "warden_glowmask.png")).convert("RGBA")
except Exception:
    GLOW = None

# ★ 角序陷阱 ★ gecko_math.cube_corners 的迭代是
#     [for x in (o,e) for y in (o,e) for z in (o,e)]
# 即 **x 最外、y 居中、z 最内**，所以
#     0:(ox,oy,oz) 1:(ox,oy,ez) 2:(ox,ey,oz) 3:(ox,ey,ez)
#     4:(ex,oy,oz) 5:(ex,oy,ez) 6:(ex,ey,oz) 7:(ex,ey,ez)
# 按此重排六个面（dv16 初版把索引 1 当成 (ex,oy,oz)，导致「正面」被当作
# 侧面 → 正脸套用了发光色，整块发亮）。
FACE_IDX = [
    (1, 3, 7, 5),   # +z 背面
    (4, 5, 7, 6),   # +x 侧面
    (0, 1, 3, 2),   # -x 侧面
    (2, 3, 7, 6),   # +y 顶面
    (0, 1, 5, 4),   # -y 底面
    (0, 2, 6, 4),   # -z 正面（实体朝向）
]
FACE_SHADE = [0.84, 0.72, 0.96, 1.30, 0.60, 1.02]
GLOW_FACES = {1, 2, 3}   # 侧面 / 顶面用 glowmask 提亮，模拟自发光


def load_geo(name):
    with open(os.path.join(GEO_DIR, f"{name}.geo.json"), encoding="utf-8") as f:
        d = json.load(f)
    return {b["name"]: b for b in d["minecraft:geometry"][0]["bones"]}


def cube_world_corners_px(bones, bname, cube, cache):
    M = GM.bone_matrix(bones, bname, cache)
    block = np.array(GM.cube_corners(cube), dtype=float)
    world = (M @ np.hstack([block, np.ones((8, 1))]).T).T[:, :3]
    return np.array([GM._to_px(tuple(v)) for v in world])


def _sample(img, u, v):
    if img is None:
        return None
    x = max(0, min(img.width - 1, int(round(u))))
    y = max(0, min(img.height - 1, int(round(v))))
    r, g, b, a = img.getpixel((x, y))
    return None if a < 8 else (r, g, b)


def _region_avg(img, x0, y0, x1, y1, want_glow=False):
    if img is None:
        return None
    x1, y1 = max(x0 + 1, x1), max(y0 + 1, y1)
    crop = img.crop((x0, y0, min(x1, img.width), min(y1, img.height)))
    px = [p for p in crop.getdata() if p[3] > 8]
    if not px:
        return None
    if want_glow:
        # 发光：取最亮的那批像素（否则平均会被少量发光点稀释）
        px.sort(key=lambda p: -(p[0] + p[1] + p[2]))
        px = px[:max(1, len(px) // 3)]
    return tuple(int(sum(c[i] for c in px) / len(px)) for i in range(3))


import uv_layout as UV
REG_BY_UV = {v[:2]: k for k, v in UV.REGIONS.items()}
# 与 FACE_IDX 一一对应
FACE_KEYS = ["back", "right", "left", "top", "bottom", "front"]


def _atlas_faces(u, v, w, h, d):
    """form2 图集模式：cube.uv 就是该立方体盒展开块的左上角，
    六面按标准十字展开推导（与 uv_layout.faces 同一套公式，只是原点来自 uv）。"""
    return {
        "top":    (u + d,       v,     u + d + w,     v + d),
        "bottom": (u + d + w,   v,     u + 2 * d + w, v + d),
        "right":  (u,           v + d, u + d,         v + d + h),
        "front":  (u + d,       v + d, u + d + w,     v + d + h),
        "left":   (u + d + w,   v + d, u + 2 * d + w, v + d + h),
        "back":   (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def set_texture(form):
    """按形态切换采样用的贴图与发光遮罩。"""
    global TEX, GLOW
    tex, gl = TEXTURE_BY_FORM.get(form, (None, None))
    if tex is None:
        TEX = Image.open(os.path.join(TEX_DIR, "warden.png")).convert("RGBA")
        gp = os.path.join(TEX_DIR, "warden_glowmask.png")
    else:
        TEX = Image.open(os.path.join(TEX_DIR, tex)).convert("RGBA")
        gp = os.path.join(TEX_DIR, gl)
    GLOW = Image.open(gp).convert("RGBA") if os.path.exists(gp) else None


def sample(cube):
    """逐面采样：返回 6 组 (base, glow)，顺序与 FACE_IDX 一致。

    逐面比「整块一个平均色」精确得多 —— 胸口符纹、兜帽里的眼睛、
    兵刃锋口这些「只占一小块」的细节才能显示出来。
    """
    u, v = cube.get("uv", (0, 0))
    w, h, d = cube["size"]
    reg = REG_BY_UV.get((u, v))
    if reg is None:
        # form2：uv 是装箱槽位 -> 直接用 u,v 推展开（见 _atlas_faces）
        f = _atlas_faces(u, v, w, h, d)
    else:
        f = UV.faces(reg, w, h, d)
    out = []
    for key in FACE_KEYS:
        x0, y0, x1, y1 = f[key]
        out.append((_region_avg(TEX, x0, y0, x1, y1) or (110, 88, 150),
                    _region_avg(GLOW, x0, y0, x1, y1, want_glow=True)))
    return out


def project(p, mode):
    x, y, z = p
    if mode == "front":          # 相机在 -z 侧看正面（实体朝 -z）
        return x, -y
    sx = (x - z) * 0.866
    sy = (x + z) * 0.5 - y
    return sx, sy


def depth(pts, mode):
    if mode == "front":
        return -pts[:, 2].mean()          # z 越小越近 → 深度大者后画
    return (pts[:, 0] + pts[:, 2]).mean()


def render_form(name, scale=3.0, pad=64, mode="iso", bg=(46, 48, 58, 255)):
    bones = load_geo(name)
    cache = {}
    cubes = []
    for bname, b in bones.items():
        for cube in b.get("cubes", []):
            if not cube.get("size") or max(cube["size"]) < 0.01:
                continue
            pts = cube_world_corners_px(bones, bname, cube, cache)
            cubes.append((depth(pts, mode), pts, sample(cube)))
    cubes.sort(key=lambda c: c[0])

    sx_all, sy_all = [], []
    for _, pts, _ in cubes:
        for p in pts:
            sx, sy = project(p, mode)
            sx_all.append(sx)
            sy_all.append(sy)
    minx, maxx = min(sx_all), max(sx_all)
    miny, maxy = min(sy_all), max(sy_all)
    W = int((maxx - minx) * scale) + 2 * pad
    H = int((maxy - miny) * scale) + 2 * pad
    img = Image.new("RGBA", (W, H), bg)
    dr = ImageDraw.Draw(img)

    def to_screen(p):
        sx, sy = project(p, mode)
        return ((sx - minx) * scale + pad, (sy - miny) * scale + pad)

    gy = to_screen((0, 0, 0))[1]
    dr.line([(pad - 30, gy), (W - pad + 30, gy)], fill=(118, 120, 132, 170), width=2)
    if mode == "front":
        cx = to_screen((0, 0, 0))[0]
        dr.line([(cx, pad - 30), (cx, H - pad + 30)], fill=(96, 98, 110, 110), width=1)

    for _, pts, cols in cubes:
        for fi, idx in enumerate(FACE_IDX):
            quad = [to_screen(pts[i]) for i in idx]
            base, glow = cols[fi]
            if glow is not None and fi in GLOW_FACES:
                r, g, b = glow           # 自发光面用 glowmask 的颜色
            else:
                sh = FACE_SHADE[fi]
                r = max(0, min(255, int(base[0] * sh)))
                g = max(0, min(255, int(base[1] * sh)))
                b = max(0, min(255, int(base[2] * sh)))
            dr.polygon(quad, fill=(r, g, b, 255), outline=(24, 24, 34, 170))
    return img


def main():
    forms = [("warden_form1", "FORM1"), ("warden_form2", "FORM2"),
             ("warden_form3", "FORM3")]
    try:
        font = ImageFont.truetype(
            "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 22)
    except Exception:
        font = ImageFont.load_default()

    front, iso = [], []
    for geo, tag in forms:
        set_texture(geo)
        f = render_form(geo, scale=3.0, mode="front")
        i = render_form(geo, scale=3.0, mode="iso")
        front.append(f)
        iso.append(i)
        gap = 18
        single = Image.new("RGBA", (f.width + i.width + gap, max(f.height, i.height)),
                           (46, 48, 58, 255))
        single.paste(f, (0, 0))
        single.paste(i, (f.width + gap, 0))
        single.save(os.path.join(OUT, f"preview_{tag.lower()}.png"))
        print(f"{tag}: front {f.width}x{f.height} + iso {i.width}x{i.height}")

    gap = 26
    cols = []
    for f, i in zip(front, iso):
        w = max(f.width, i.width)
        c = Image.new("RGBA", (w, f.height + i.height + 8), (46, 48, 58, 255))
        c.paste(f, ((w - f.width) // 2, 0))
        c.paste(i, ((w - i.width) // 2, f.height + 8))
        cols.append(c)

    lh = 34
    total_w = sum(c.width for c in cols) + gap * (len(cols) - 1)
    total_h = max(c.height for c in cols) + lh
    combo = Image.new("RGBA", (total_w, total_h), (46, 48, 58, 255))
    dr = ImageDraw.Draw(combo)
    x = 0
    for c, (_, tag) in zip(cols, forms):
        combo.paste(c, (x, 0))
        bbox = dr.textbbox((0, 0), tag, font=font)
        dr.text((x + (c.width - (bbox[2] - bbox[0])) // 2, c.height + 6),
                tag, fill=(224, 224, 234, 255), font=font)
        x += c.width + gap
    combo.save(os.path.join(OUT, "preview_all.png"))
    print(f"all -> {combo.width}x{combo.height}")


if __name__ == "__main__":
    main()
