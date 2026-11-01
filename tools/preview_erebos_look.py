#!/usr/bin/env python3
"""按 ErebosModel 的真实立方体几何 + 真实贴图，软渲染出厄瑞玻斯的样子。

不依赖任何 3D 库：正交投影 + 背面剔除 + 画家算法，
每个面细分成小四边形逐格采样贴图（最近邻），还原 MC 的观感。
"""
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont

TEX_DIR = 'src/main/resources/assets/lost_realm/textures/entity'
OUT = '/workspace/preview_erebos_beta28.png'

# ================================================================
# 1. 几何：直接从 ErebosModel.java 抄过来
#    (name, uvX, uvY, minX, minY, minZ, w, h, d)
#    注意 addBox(x,y,z,w,h,d) 的 (x,y,z) 是最小角
# ================================================================
HEAD = [
    # (name, uvX, uvY, minX, minY, minZ, w, h, d[, (rotX, (pivotX, pivotY, pivotZ))])
    ('skull',    0,  0,   -7.0,  -7.0,  -9.0, 14.0, 14.0, 18.0),
    ('snout',    0, 32,   -5.5,  -4.0,   9.0, 11.0,  6.0,  8.0),
    ('jaw',     64,  0,   -4.5,   0.0,   9.0,  9.0,  4.0,  7.0, (0.12, (0.0, 5.0, 0.0))),
    ('eye_l',   64, 16,   -7.6,  -3.0,   3.0,  2.0,  3.0,  4.0),
    ('eye_r',   64, 16,    5.6,  -3.0,   3.0,  2.0,  3.0,  4.0),
    ('horn_l1', 64, 36,   -8.0,  -9.0,  -4.0,  3.0,  3.0,  7.0, (-0.25, (0.0, 0.0, 0.0))),
    ('horn_l2', 64, 36,   -7.5, -10.6,  -8.0,  2.0,  2.0,  5.5, (-0.55, (0.0, 0.0, 0.0))),
    ('horn_r1', 64, 36,    5.0,  -9.0,  -4.0,  3.0,  3.0,  7.0, (-0.25, (0.0, 0.0, 0.0))),
    ('horn_r2', 64, 36,    5.5, -10.6,  -8.0,  2.0,  2.0,  5.5, (-0.55, (0.0, 0.0, 0.0))),
    ('crest',    0, 64,   -5.0, -10.0,  -9.0, 10.0,  4.0, 16.0),
    ('core',    64, 52,   -3.0,  -2.0,   8.0,  6.0,  5.0,  3.0),
]
# 上排 6 颗牙、下排 5 颗，交错咬合
HEAD += [('tooth_up_%d' % i, 64, 28, -5.0 + i * 2.0, 2.0, 11.5, 1.4, 3.0, 1.4)
         for i in range(6)]
HEAD += [('tooth_down_%d' % i, 64, 28, -4.0 + i * 2.0, 2.4, 11.5, 1.4, 2.6, 1.4)
         for i in range(5)]
BODY = [
    ('segment', 0,  0, -8, -8, -8, 16, 16, 16),
    ('ring',    0, 32, -9, -9, -2, 18, 18,  4),
]
TAIL = [
    ('segment', 0,  0,  -6, -6,  -6, 12, 12, 12),
    ('tip',     0, 24,  -3, -3, -12,  6,  6,  7),
]

TEX = {
    'head': np.asarray(Image.open(os.path.join(TEX_DIR, 'erebos_head.png')).convert('RGBA'), dtype=np.float32),
    'body': np.asarray(Image.open(os.path.join(TEX_DIR, 'erebos_body.png')).convert('RGBA'), dtype=np.float32),
    'tail': np.asarray(Image.open(os.path.join(TEX_DIR, 'erebos_tail.png')).convert('RGBA'), dtype=np.float32),
}

# 每节的缩放（渲染器：基础 2.4，最后几节额外缩小）
SEGMENT_SPACING = 4.2   # = ErebosTuning.SEGMENT_SPACING（50 节 × 4.2 = 210 格）
SCALE = 4.8             # = ErebosTuning.BODY_DIAMETER（16px 模型 → 4.8 格直径）


def section_scale(index, count):
    if index >= count - 2:
        return SCALE * (0.78 if index == count - 1 else 0.92)
    return SCALE


# ================================================================
# 2. MC 的 box UV 展开：把 (u,v,w,h,d) 变成六个面的 UV 矩形
#    +---+---+---+---+
#    |   | U | D |   |
#    +---+---+---+---+
#    | W | N | E | S |
#    +---+---+---+---+
# ================================================================
def face_uvs(u, v, w, h, d):
    return {
        'up':    (u + d,             v,     w, d),
        'down':  (u + d + w,         v,     w, d),
        'west':  (u,                 v + d, d, h),
        'north': (u + d,             v + d, w, h),
        'east':  (u + d + w,         v + d, d, h),
        'south': (u + d + w + d,     v + d, w, h),
    }


def face_geometry(name, x, y, z, w, h, d):
    """返回六个面：{face: (origin, right_vec, down_vec, normal)}，单位 = 像素。"""
    x1, y1, z1 = x + w, y + h, z + d
    return {
        # 外侧观察者的「右」与「下」
        'up':    ((x,  y1, z),  (1, 0, 0), (0, 0, 1), (0, 1, 0)),
        'down':  ((x,  y,  z1), (1, 0, 0), (0, 0, -1), (0, -1, 0)),
        'north': ((x1, y1, z),  (-1, 0, 0), (0, -1, 0), (0, 0, -1)),
        'south': ((x,  y1, z1), (1, 0, 0), (0, -1, 0), (0, 0, 1)),
        'west':  ((x,  y1, z),  (0, 0, 1), (0, -1, 0), (-1, 0, 0)),
        'east':  ((x1, y1, z1), (0, 0, -1), (0, -1, 0), (1, 0, 0)),
    }


def face_size(face, w, h, d):
    return {
        'up': (w, d), 'down': (w, d),
        'north': (w, h), 'south': (w, h),
        'west': (d, h), 'east': (d, h),
    }[face]


# ================================================================
# 3. 相机（正交）
# ================================================================
class Camera:
    """正交或透视相机。

    ortho：靠 scale 控制大小（特写用）。
    perspective：靠 focal 控制——巨物的压迫感来自近大远小，正交给不了。
    """

    def __init__(self, yaw_deg, pitch_deg, width, height,
                 scale=1.0, center=(0, 0, 0), eye=(0, 0, 0), focal=None):
        self.yaw = math.radians(yaw_deg)
        self.pitch = math.radians(pitch_deg)
        self.scale = scale
        self.w, self.h = width, height
        self.center = np.asarray(center, dtype=np.float64)
        self.eye = np.asarray(eye, dtype=np.float64)
        self.focal = focal
        self.pixel_offset = np.zeros(2)

    def to_cam(self, pts):
        """世界坐标 -> 相机坐标。z 越大越远。"""
        pts = np.asarray(pts, dtype=np.float64) - self.center - self.eye
        cy, sy = math.cos(self.yaw), math.sin(self.yaw)
        cp, sp = math.cos(self.pitch), math.sin(self.pitch)
        x = cy * pts[..., 0] + sy * pts[..., 2]
        z1 = -sy * pts[..., 0] + cy * pts[..., 2]
        y = cp * pts[..., 1] - sp * z1
        z = sp * pts[..., 1] + cp * z1
        return np.stack([x, y, z], axis=-1)

    def project(self, pts):
        cam = self.to_cam(pts)
        off = self.pixel_offset
        if self.focal:
            z = np.maximum(cam[..., 2], 0.05)
            sx = self.w * 0.5 + cam[..., 0] / z * self.focal + off[0]
            sy = self.h * 0.5 - cam[..., 1] / z * self.focal + off[1]
        else:
            sx = self.w * 0.5 + cam[..., 0] * self.scale + off[0]
            sy = self.h * 0.5 - cam[..., 1] * self.scale + off[1]
        return np.stack([sx, sy], axis=-1), cam[..., 2]

    def auto_fit(self, points, margin=0.90):
        """按包围盒自动定缩放与居中（仅正交）。"""
        q = self.to_cam(points)
        lo, hi = q.min(axis=0), q.max(axis=0)
        span = np.maximum(hi - lo, 1e-6)
        self.scale = min(self.w / span[0], self.h / span[1]) * margin
        cx = (lo[0] + hi[0]) * 0.5
        cy = (lo[1] + hi[1]) * 0.5
        self.pixel_offset = np.array([-cx * self.scale, cy * self.scale])
        return self

    def rotate_only(self, v):
        """只旋转、不平移 —— 法线是方向向量，跟着相机平移就废了。"""
        v = np.asarray(v, dtype=np.float64)
        cy, sy = math.cos(self.yaw), math.sin(self.yaw)
        cp, sp = math.cos(self.pitch), math.sin(self.pitch)
        x = cy * v[..., 0] + sy * v[..., 2]
        z1 = -sy * v[..., 0] + cy * v[..., 2]
        y = cp * v[..., 1] - sp * z1
        z = sp * v[..., 1] + cp * z1
        return np.stack([x, y, z], axis=-1)

    def normal_visible(self, n_world):
        # ⚠️ 必须走 rotate_only，不能用 to_cam —— 后者会减掉眼位，
        # 而透视视角下眼位离原点很远，一减就把所有法线都翻成"背对相机"，
        # 整幅画面会莫名其妙地全空。
        return self.rotate_only(n_world)[2] < -1e-6


# ================================================================
# 4. 摆放：把某一节模型变换到世界
# ================================================================
def basis_from_forward(fwd):
    f = np.asarray(fwd, dtype=np.float64)
    n = np.linalg.norm(f)
    f = f / n if n > 1e-9 else np.array([0.0, 0.0, 1.0])
    up = np.array([0.0, 1.0, 0.0])
    if abs(np.dot(f, up)) > 0.98:
        up = np.array([0.0, 0.0, 1.0])
    right = np.cross(up, f)
    right /= np.linalg.norm(right)
    up2 = np.cross(f, right)
    return right, up2, f


def rotate_x(p, rot, pivot=True):
    """绕 X 轴旋转，方向与 MC 的 Axis.XP 一致。

    点要绕 pivot 转（对应 PartPose.offsetAndRotation 的偏移），
    法线是方向向量，只转不平移。
    """
    ang, pv = rot
    c, s = math.cos(ang), math.sin(ang)
    if not pivot:
        return np.array([p[0], p[1] * c - p[2] * s, p[1] * s + p[2] * c])
    px, py, pz = pv
    y = p[1] - py
    z = p[2] - pz
    return np.array([p[0], py + y * c - z * s, pz + y * s + z * c])


def collect_faces(parts, tex_key, pos, fwd, scale):
    """把一个模型（parts 列表）摆到 pos、朝向 fwd，返回世界空间的面列表。"""
    right, up, fwd_u = basis_from_forward(fwd)
    # 模型空间 -> 世界：先 /16 转格，再乘 scale，再换基
    k = scale / 16.0
    tex = TEX[tex_key]
    faces = []

    for entry in parts:
        name, u, v, x, y, z, w, h, d = entry[:9]
        rot = entry[9] if len(entry) > 9 else None
        uvs = face_uvs(u, v, w, h, d)
        geo = face_geometry(name, x, y, z, w, h, d)
        for face, (org, rv, dv, nv) in geo.items():
            uu, vv, uw, uh = uvs[face]
            sw, sh = face_size(face, w, h, d)

            def to_world(s, t):
                p = np.array(org) + np.asarray(rv) * (s * sw) + np.asarray(dv) * (t * sh)
                if rot is not None:
                    p = rotate_x(p, rot, pivot=True)
                p = p * k
                # ⚠️ MC 的模型空间 **+Y 朝下**（屏幕坐标系），世界空间 +Y 朝上，
                #    这里必须取负。体节是对称的所以以前没暴露，一换成不对称的头
                #    （角在 y=-9、颚在 y=+4）就会整颗头上下颠倒。
                return (right * p[0] - up * p[1] + fwd_u * p[2]) + pos

            # 法线转世界（方向向量：只旋转、不平移，Y 同样取负）
            nv_r = rotate_x(np.asarray(nv, dtype=np.float64), rot, pivot=False) \
                if rot is not None else np.asarray(nv, dtype=np.float64)
            n_world = right * nv_r[0] - up * nv_r[1] + fwd_u * nv_r[2]
            faces.append({
                'corner': np.array([to_world(0, 0), to_world(1, 0), to_world(1, 1), to_world(0, 1)]),
                'uv': (uu, vv, uw, uh),
                'normal': n_world,
                'tex': tex,
                'part': name,
            })
    return faces


# ================================================================
# 5. 绘制
# ================================================================
LIGHT = np.array([0.35, 1.0, -0.30])
LIGHT = LIGHT / np.linalg.norm(LIGHT)

# 贴图本身很暗（装甲主色只有 58,36,92），按 0.4~1.0 的系数乘下来几乎全黑。
# 游戏里实体会吃到环境亮度加成，这里用一个整体增益模拟那一步。
GAIN = 2.05


def render(draw, cam, faces, ambient=0.55):
    """画家算法：远的先画。每个面细分成小四边形逐格采样。"""
    # 逐面计算深度（用四角平均）
    for f in faces:
        pts2, depth = cam.project(f['corner'])
        f['_pts2'] = pts2
        f['_depth'] = float(depth.mean())
        f['_visible'] = cam.normal_visible(f['normal'])

    faces = [f for f in faces if f['_visible']]
    faces.sort(key=lambda f: -f['_depth'])

    for f in faces:
        n = f['normal']
        lam = max(0.0, float(np.dot(n, LIGHT)))
        bright = ambient + (1.0 - ambient) * lam
        tex = f['tex']
        uu, vv, uw, uh = f['uv']
        c = f['corner']

        # 自适应细分密度
        p = f['_pts2']
        span = max(np.linalg.norm(p[1] - p[0]), np.linalg.norm(p[3] - p[0]))
        div = int(max(1, min(14, round(span / 2.2))))

        for i in range(div):
            s0, s1 = i / div, (i + 1) / div
            for j in range(div):
                t0, t1 = j / div, (j + 1) / div
                # 世界空间四边形（双线性）
                def lerp(s, t):
                    return ((1 - s) * (1 - t) * c[0] + s * (1 - t) * c[1]
                            + s * t * c[2] + (1 - s) * t * c[3])
                quad_w = np.array([lerp(s0, t0), lerp(s1, t0), lerp(s1, t1), lerp(s0, t1)])
                quad_p, _ = cam.project(quad_w)

                # 采样贴图（格中心，最近邻）
                px = int(uu + (s0 + s1) * 0.5 * uw)
                py = int(vv + (t0 + t1) * 0.5 * uh)
                px = max(0, min(tex.shape[1] - 1, px))
                py = max(0, min(tex.shape[0] - 1, py))
                col = tex[py, px]
                if col[3] < 8:
                    continue
                # 自发光像素（眼睛 / 牙 / 星核）直接吃原色 ——
                # 否则乘上 GAIN 会全部过曝成纯白，头部就只剩几块白斑。
                if float(col[:3].mean()) > 150.0:
                    rgb = tuple(int(min(255, ch)) for ch in col[:3])
                else:
                    rgb = tuple(int(max(0, min(255, ch * bright * GAIN))) for ch in col[:3])
                xy = [(float(q[0]), float(q[1])) for q in quad_p]
                draw.polygon(xy, fill=rgb, outline=rgb)


# ================================================================
# 6. 场景
# ================================================================
def worm_curve(n=4200):
    """一条足够长的游动轨迹 —— 总弧长必须 > 50 节 × 4.2 = 210 格，
    否则 sample_segments 会把多出来的节全部 clamp 到起点，堆成一坨。"""
    t = np.linspace(0.0, 4.0 * math.pi, n)
    x = t * 19.0
    y = np.sin(t * 0.55) * 9.0 + np.sin(t * 0.21 + 1.1) * 3.5
    z = np.cos(t * 0.62) * 16.0 + np.cos(t * 0.28) * 6.0
    return np.stack([x, y, z], axis=-1)


def sample_segments(count=40):
    """按弧长每 2.4 格取一节 —— 与 ErebosTrail 同一套算法。"""
    pts = worm_curve()
    d = np.linalg.norm(np.diff(pts, axis=0), axis=1)
    s = np.concatenate([[0.0], np.cumsum(d)])
    total = s[-1]
    targets = total - np.arange(count) * SEGMENT_SPACING
    targets = np.clip(targets, 0.0, total)
    xs = np.interp(targets, s, pts[:, 0])
    ys = np.interp(targets, s, pts[:, 1])
    zs = np.interp(targets, s, pts[:, 2])
    return np.stack([xs, ys, zs], axis=-1)


def build_worm(count=40):
    seg = sample_segments(count)
    faces = []
    for i in range(count):
        pos = seg[i]
        if i < count - 1:
            fwd = seg[i] - seg[i + 1]        # +Z 朝前 = 指向更小的 i（头在 i=0）
            fwd = -fwd if np.linalg.norm(fwd) < 1e-6 else fwd
        else:
            fwd = seg[i - 1] - seg[i]
        parts, key = (HEAD, 'head') if i == 0 else ((TAIL, 'tail') if i >= count - 2 else (BODY, 'body'))
        faces += collect_faces(parts, key, pos, fwd, section_scale(i, count))
    return faces, seg


def render_view(faces, cam, w, h, bg=(19, 17, 25), ambient=0.55):
    """渲染到一个独立图像 —— 独立渲染再拼接，比给 project 打偏移补丁可靠得多。"""
    img = Image.new('RGB', (w, h), bg)
    draw = ImageDraw.Draw(img)
    render(draw, cam, [dict(f) for f in faces], ambient=ambient)
    return img


def view_of(faces, yaw, pitch, w, h, margin=0.90, ambient=0.55, center=(0, 0, 0)):
    """正交 + 自动取景（特写用）。返回 (图像, 相机)，相机用于事后标注。"""
    cam = Camera(yaw, pitch, w, h, center=center)
    pts = np.concatenate([f['corner'] for f in faces], axis=0)
    cam.auto_fit(pts, margin)
    return render_view(faces, cam, w, h, ambient=ambient), cam


def part_anchor(faces, part):
    """某个部件所有顶点的投影中心（标注引线用）。"""
    pts = np.concatenate([f['corner'] for f in faces if f.get('part') == part], axis=0)
    return pts.mean(axis=0)


def view_from_eye(faces, eye, yaw, pitch, w, h, focal, ambient=0.52):
    """透视（游戏内视角用）—— 近大远小，这才有巨物的分量。"""
    cam = Camera(yaw, pitch, w, h, eye=eye, focal=focal)
    return render_view(faces, cam, w, h, ambient=ambient)


def box_solid(draw, cam, cx, cy, cz, sx, sy, sz, color):
    """一个实心方块（给比例参考用）。"""
    pts = []
    for dx in (-0.5, 0.5):
        for dy in (-0.5, 0.5):
            for dz in (-0.5, 0.5):
                pts.append([cx + dx * sx, cy + dy * sy, cz + dz * sz])
    p2, dep = cam.project(np.array(pts))
    idx = [(0, 1, 3, 2), (4, 5, 7, 6), (0, 1, 5, 4), (2, 3, 7, 6), (0, 2, 6, 4), (1, 3, 7, 5)]
    for k in sorted(range(6), key=lambda k: -dep[list(idx[k])].mean()):
        f = idx[k]
        shade = 0.60 + 0.40 * (1.0 - k / 6.0)
        c = tuple(int(min(255, v * shade)) for v in color)
        draw.polygon([tuple(p2[q]) for q in f], fill=c, outline=c)


def in_game_view(faces, w, h):
    """游戏内视角：玩家站在地面上，抬头看它横跨天空。

    用透视而不是正交 —— 巨物的分量来自近大远小，正交给不了这个。
    再补一条地平线和地面色块，让"这是站在 MC 里看到的"一眼成立。
    """
    eye = np.array([120.0, 2.0, -170.0])
    cam = Camera(yaw_deg=0, pitch_deg=4, width=w, height=h, eye=eye, focal=1300.0)

    img = Image.new('RGB', (w, h), (16, 14, 26))
    d = ImageDraw.Draw(img)

    # 天空渐变（紫黑宇宙感）
    for y in range(0, h // 2 + 60):
        t = y / max(1, h // 2 + 60)
        c = (int(16 + 26 * t), int(14 + 14 * t), int(26 + 26 * t))
        d.line([(0, y), (w, y)], fill=c)

    # 地平线：远在相机「前方」的一条 y=0 的线
    # ⚠️ 第一次把 z 写成了 -400（相机后方），投影出来落在画面顶上，
    #    整个画面都被填成地面色 —— 一定要用相机前方的点。
    horizon_pts = np.array([[x, 0.0, 520.0] for x in np.linspace(-900, 900, 90)])
    hp, _ = cam.project(horizon_pts)
    hy = float(np.median(hp[:, 1]))
    hy = min(max(hy, h * 0.55), h - 40)
    d.rectangle([0, hy, w, h], fill=(21, 25, 19))
    for i in range(0, w, 4):
        d.point((i, hy + 2), fill=(42, 55, 36))
    d.line([(0, hy), (w, hy)], fill=(56, 72, 46))

    render(d, cam, [dict(f) for f in faces], ambient=0.50)

    # 前景：玩家的手臂/视线参考，宣示"这是第一人称"
    d.rectangle([w - 300, h - 90, w - 60, h - 20], fill=(96, 78, 54), outline=(64, 52, 36))
    d.text((24, h - 34), 'first-person, standing on the ground', fill=(150, 142, 172))
    return img


def main():
    count = 50   # = ErebosTuning.SEGMENTS
    faces, seg = build_worm(count)
    print('段数 %d，首尾直线距离 %.0f 格，总弧长约 %.0f 格'
          % (count, float(np.linalg.norm(seg[0] - seg[-1])), count * SEGMENT_SPACING))

    W, H = 2000, 1240
    img = Image.new('RGB', (W, H), (13, 11, 18))
    draw = ImageDraw.Draw(img)

    def cap(x, y, text, color=(244, 238, 255)):
        draw.text((x, y), text, fill=color)

    def sub(x, y, text, color=(130, 122, 152)):
        draw.text((x, y), text, fill=color)

    def frame(x0, y0, x1, y1):
        draw.rectangle([x0, y0, x1, y1], outline=(46, 42, 60))

    # ================= 上排：游戏内视角 =================
    cap(24, 18, 'WHAT YOU ACTUALLY SEE IN GAME')
    sub(24, 42, '210 blocks of body (50 segments x 4.2) across the sky; each segment is 4.8 blocks wide '
                '(a player is 1.8 tall)')

    v_game = in_game_view(faces, 1950, 540)
    img.paste(v_game, (25, 70))
    frame(25, 70, 1974, 609)

    # ================= 下排 =================
    y0 = 632
    cap(24, y0, 'WHOLE BODY')
    sub(24, y0 + 24, '50 segments placed by arc length, so the gap never stretches when it charges')

    v_body, _ = view_of(faces, yaw=35, pitch=18, w=880, h=530, margin=0.94, ambient=0.62)
    img.paste(v_body, (25, y0 + 48))
    frame(25, y0 + 48, 904, y0 + 577)

    cap(930, y0, 'HEAD')
    sub(930, y0 + 24, 'narrow skull + upper snout + hinged jaw + 11 teeth + 2 eyes + 2 pairs of horns')

    head_faces = []
    for i in range(1):
        pos = seg[i]
        fwd = seg[i] - seg[i + 1] if i < count - 1 else seg[i - 1] - seg[i]
        parts, key = (HEAD, 'head') if i == 0 else (BODY, 'body')
        head_faces += collect_faces(parts, key, pos, fwd, section_scale(i, count))
    # 从头的正前方偏侧 34°、略俯视 —— 能同时看到眼、张开的颚、牙和两根角。
    # 之前写死 yaw=232（等于从后上方看），只能看到一个光秃的颅骨背面。
    hf = seg[0] - seg[1]
    head_yaw = math.degrees(math.atan2(float(hf[0]), float(hf[2]))) - 68.0
    v_head, head_cam = view_of(head_faces, yaw=head_yaw, pitch=-14, w=540, h=530,
                               margin=0.82, ambient=0.70)
    hx, hy0 = 930, y0 + 48
    img.paste(v_head, (hx, hy0))
    frame(hx, hy0, hx + 539, hy0 + 529)

    # 把头部零件标出来 —— 这些凸起只有 2~3 像素（0.3~0.45 格），
    # 不标的话根本认不出哪个是哪个
    marks = [('core', 'star core (forehead, self-lit)'),
             ('eye_l', 'eye (both sides)'),
             ('tooth_up_0', 'teeth (11, interlocked)'),
             ('horn_l2', 'horns (2 pairs, swept back)')]
    _ = marks   # 标注线会横穿头部，细节反而更难看清；文字说明已在上方
    d = ImageDraw.Draw(img)
    d.text((hx + 14, hy0 + 496),
           'eyes / teeth / star-core are self-lit in game (FULL_BRIGHT)',
           fill=(150, 142, 178))

    # 比例
    props = [(-3.6, 1.2, 0.0, 4.8, 4.8, 4.8), (4.6, 0.9, 0.8, 0.6, 1.8, 0.6)]
    cam3 = Camera(206, 12, 380, 530, center=np.zeros(3))
    corners = []
    for (bx, by, bz, sx, sy, sz) in props:
        for dx in (-0.5, 0.5):
            for dy in (-0.5, 0.5):
                for dz in (-0.5, 0.5):
                    corners.append([bx + dx * sx, by + dy * sy, bz + dz * sz])
    cam3.auto_fit(np.array(corners), 0.78)
    v_scale = Image.new('RGB', (380, 530), (19, 17, 25))
    d3 = ImageDraw.Draw(v_scale)
    box_solid(d3, cam3, *props[0], (128, 92, 200))
    box_solid(d3, cam3, *props[1], (86, 186, 120))
    d3.text((14, 500), 'purple = 1 segment   green = player', fill=(150, 142, 172))
    img.paste(v_scale, (1486, y0 + 48))
    frame(1486, y0 + 48, 1865, y0 + 577)

    # ================= 右侧零件清单 =================
    lines = [
        ('skull',  '14 x 14 x 18', 'narrower + longer than a segment'),
        ('snout',  '11 x 6 x 8',   'upper jaw, reaches forward'),
        ('jaw',    '9 x 4 x 7',    'hinged, 7 deg open'),
        ('teeth',  '1.4 x 3 x 1.4', '11 of them, interlocked'),
        ('eyes',   '2 x 3 x 4',    'bright, self-lit'),
        ('horns',  '3 x 3 x 7 / 2 x 2 x 5', 'two pairs, swept back'),
        ('core',   '6 x 5 x 3',    'star core, forehead'),
        ('ring',   '18 x 18 x 4',  'band on each body segment'),
    ]
    yy = y0 + 56
    for name, size, desc in lines:
        draw.text((1890, yy), name, fill=(226, 208, 255))
        draw.text((1890, yy + 17), size, fill=(150, 142, 178))
        draw.text((1890, yy + 32), desc, fill=(120, 112, 140))
        yy += 58

    draw.text((25, H - 26),
              'rendered offline from the actual cube geometry and the actual textures — '
              'this is literally what the model is, not concept art',
              fill=(112, 104, 132))

    img.save(OUT)
    print('已输出', OUT, img.size)

    # 头部单独再来一张大的 —— 540px 的面板看不清牙和眼
    v_big, _ = view_of(head_faces, yaw=head_yaw, pitch=-10, w=900, h=900,
                       margin=0.78, ambient=0.72)
    big = '/workspace/erebos_head_closeup_beta28.png'
    v_big.save(big)
    print('已输出', big, v_big.size)


if __name__ == '__main__':
    main()
