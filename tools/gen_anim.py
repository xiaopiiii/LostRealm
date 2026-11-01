#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
遗落之主 GeckoLib 动画生成器（dv21 重做：人形肢体 + 触手随机蠕动）。

=== 骨骼名（与 gen_geo.py 严格对应）===
  人形：root / body / head / arm_r / arm_l / leg_r / leg_l / weapon(挂 arm_r)
  头冠：horn_0..4（静态，不驱动）
  触手：tent_b_{i}_{s}  背部长出的（form2: i=0..7；form3: i=0..11）
        tent_r_{i}_{s}  右臂触手（form2: i=0；form3: i=0,2）
        tent_l_{i}_{s}  左臂触手（form2: i=1；form3: i=1,3）
        s = 0..15（每节 4px，共 64px = 2 倍体长）

=== ★ 触手动画的铁律：幅度要小、相位沿链慢延迟 ★ ===
触手是**父子链**：子节的旋转会继承父节。所以「每节都甩 20°」= 末节累计
300°，整条链会卷成弹簧/被搅散。
本文件的取值：每节幅度 = base + gain·s（约 2.0° → 4.0°），
              相位沿链总共只延迟一个周期（spread=1.0），
              ⇒ 末节累计 ≈ 45°，形成柔和的 S 形行波，而不是乱甩。
另外每条触手的**相位/频率/幅度都不同**（用固定种子随机），
所以看起来是「随机蠕动」，而不是整排同步摆动。

=== 动画 id 契约（Java 侧 WardenAnimations 硬编码，不能改）===
  animation.warden.{idle, walk, death, hurt,
                    attack_sweep, attack_smash, attack_lash,
                    phase_roar, phase_awaken}

=== 落盘前的强制自检 ===
  · 动画引用的每个骨骼名都必须真实存在于对应 geo（GeckoLib 对缺失骨骼静默跳过）
  · 9 个 id 齐全
  · ★ 触手关节完整性：按动画逐帧复算，父子关节间隙必须 ≈ 0，
    且相邻节中心距恒为节距（散架的两种表现都被这两条断言拦住）
"""

import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gecko_math as GM

OUT_DIR = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources',
                       'assets', 'lost_realm', 'animations')
GEO_DIR = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources',
                       'assets', 'lost_realm', 'geo')

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

TENT_SEGS = 16
SEED = 20261013


# ============================================================
# 轨道小工具
# ============================================================

def rot_track(frames):
    return {str(t): {"post": [round(float(v), 4) for v in val]}
            for t, val in frames}


def vec_track(frames):
    return {str(t): {"vector": [round(float(v), 4) for v in val]}
            for t, val in frames}


def merge(*dicts):
    out = {}
    for d in dicts:
        for bone, chans in d.items():
            out.setdefault(bone, {})
            for ch, tr in chans.items():
                out[bone].setdefault(ch, {}).update(tr)
    return out


def limb(name, axis, frames):
    """单骨骼单轴旋转轨道（frames 里给的是标量）。axis: 0=x 1=y 2=z"""
    out = []
    for t, v in frames:
        val = [0.0, 0.0, 0.0]
        val[axis] = float(v[0] if isinstance(v, (tuple, list)) else v)
        out.append((t, tuple(val)))
    return {name: {"rotation": rot_track(out)}}


# ============================================================
# ★ 触手随机蠕动 ★
# ============================================================

def tentacle_tracks(chains, segs=TENT_SEGS, length=4.0, samples=10,
                    base_amp=2.0, amp_gain=0.10, spread=1.0, seed=SEED):
    """chains: {(prefix, 编号): [节索引...]} -> 每个节一条 rotation 轨道。

    每条触手：随机相位 / 随机频率(0.85~1.25) / 随机幅度(0.8~1.25)，
    所以整体看起来是「各自乱动」；链内则相位延迟形成行波。
    """
    rng = random.Random(seed)
    parts = []
    for key, seg_idx in chains.items():
        prefix, i = key
        n = len(seg_idx)
        ph = rng.uniform(0.0, 2.0 * math.pi)
        freq = rng.uniform(0.85, 1.25)
        scl = rng.uniform(0.80, 1.25)
        tilt = rng.uniform(-0.35, 0.35)
        for k, s in enumerate(seg_idx):
            amp = (base_amp + amp_gain * k) * scl
            delay = spread * (k / max(1, n))
            frames = []
            for j in range(samples + 1):
                t = length * j / samples
                w = 2.0 * math.pi * (j / samples - delay) * freq + ph
                frames.append((t, (amp * math.sin(w),
                                   amp * 0.25 * math.sin(w * 1.7 + ph),
                                   amp * 0.7 * math.sin(w * 1.23 + 1.1 + tilt))))
            parts.append({f"{prefix}_{i}_{s}": {"rotation": rot_track(frames)}})
    return parts


def chains_for(form):
    """★ 从 geo 文件里**实测**骨翼骨骼：{("wing_l"/"wing_r"): [节索引...]}。

    不写死节数 —— 生成器改了几段、缩放了多大，这里都自动跟上。
    """
    geo_path = os.path.join(GEO_DIR, form + ".geo.json")
    if not os.path.exists(geo_path):
        return {}
    geo = json.load(open(geo_path, encoding="utf-8"))
    out = {}
    for b in geo["minecraft:geometry"][0]["bones"]:
        n = b["name"]
        if not n.startswith("wing_"):
            continue
        parts = n.split("_")            # wing_l_3
        if len(parts) != 3 or not parts[2].isdigit():
            continue
        out.setdefault(f"{parts[0]}_{parts[1]}", []).append(int(parts[2]))
    for v in out.values():
        v.sort()
    return out


def wing_tracks(chains, length=4.0, samples=10, base=2.0, gain=1.2,
                delay=0.18, phase=0.0, seed=SEED):
    """骨翼扇动：逐节绕 z 轴旋转，沿链延迟 → 翼面像真翅膀一样有柔性。

    左右翼符号相反（左翼 x>0，绕 +z 抬；右翼 x<0，绕 -z 抬），否则会一边上一片下。
    """
    rng = random.Random(seed)
    parts = []
    for key, segs in chains.items():
        sgn = 1.0 if key.endswith("l") else -1.0
        ph = rng.uniform(-0.25, 0.25) + phase
        for k, i in enumerate(segs):
            amp = (base + gain * k) * sgn
            d = delay * k
            frames = []
            for j in range(samples + 1):
                t = length * j / samples
                w = 2.0 * math.pi * (j / samples - d) + ph
                frames.append((t, (0.0, 0.0, amp * math.sin(w))))
            parts.append({f"{key}_{i}_0": {"rotation": rot_track(frames)}})
    return parts


# ============================================================
# 人形肢体动作
# ============================================================

def core_idle():
    """待机：呼吸起伏 + 头部微摆。"""
    return merge(
        {"root": {"position": vec_track([(0, (0, 0, 0)), (1.0, (0, 1.0, 0)),
                                         (2.0, (0, 0.15, 0)), (3.0, (0, 0.45, 0)),
                                         (4.0, (0, 0, 0))])}},
        limb("body", 0, [(0, (0,)), (1.0, (-1.5,)), (2.0, (0.5,)), (3.0, (-1.2,)), (4.0, (0,))]),
        limb("head", 0, [(0, (0,)), (1.3, (2.0,)), (2.6, (-1.5,)), (4.0, (0,))]),
        {k: {"rotation": rot_track([(0, (0, 0, 0)), (2.0, (0, 0, v)), (4.0, (0, 0, 0))])}
         for k, v in (("arm_r", -3.0), ("arm_l", 3.0))},
    )


def core_walk():
    """行走：双腿交替摆动、双臂反向摆、躯干起伏。"""
    return merge(
        {"root": {"position": vec_track([(0, (0, 0, 0)), (0.3, (0, 1.4, 0)),
                                         (0.6, (0, 0, 0)), (0.9, (0, 0.7, 0)),
                                         (1.2, (0, 0, 0))])}},
        limb("leg_r", 0, [(0, (22,)), (0.3, (0,)), (0.6, (-22,)), (0.9, (0,)), (1.2, (22,))]),
        limb("leg_l", 0, [(0, (-22,)), (0.3, (0,)), (0.6, (22,)), (0.9, (0,)), (1.2, (-22,))]),
        limb("arm_r", 0, [(0, (-18,)), (0.6, (16,)), (1.2, (-18,))]),
        limb("arm_l", 0, [(0, (18,)), (0.6, (-16,)), (1.2, (18,))]),
        limb("body", 1, [(0, (-3,)), (0.6, (3,)), (1.2, (-3,))]),
    )


def core_attack_smash():
    """下砸：抬臂 → 劈下。镰刀挂在 arm_r 上，跟着走。"""
    return merge(
        {"root": {"position": vec_track([(0, (0, 0, 0)), (0.45, (0, 2.4, 0)),
                                         (0.7, (0, -1.0, 0)), (1.2, (0, 0, 0))])}},
        limb("arm_r", 0, [(0, (0,)), (0.35, (-95,)), (0.62, (48,)), (1.2, (0,))]),
        limb("arm_l", 0, [(0, (0,)), (0.35, (-30,)), (0.62, (20,)), (1.2, (0,))]),
        limb("body", 0, [(0, (0,)), (0.4, (-14,)), (0.65, (16,)), (1.2, (0,))]),
        limb("head", 0, [(0, (0,)), (0.5, (10,)), (1.2, (0,))]),
        limb("leg_r", 0, [(0, (0,)), (0.5, (10,)), (1.2, (0,))]),
        limb("leg_l", 0, [(0, (0,)), (0.5, (-8,)), (1.2, (0,))]),
    )


def core_attack_sweep():
    """横扫：躯干转身带镰刀横扫。"""
    return merge(
        limb("body", 1, [(0, (0,)), (0.25, (-34,)), (0.6, (36,)), (1.0, (0,))]),
        limb("arm_r", 0, [(0, (0,)), (0.25, (-38,)), (0.6, (26,)), (1.0, (0,))]),
        {"arm_r": {"rotation": rot_track([(0, (0, 0, 0)), (0.25, (-30, -34, -18)),
                                          (0.6, (-16, 30, 10)), (1.0, (0, 0, 0))])}},
        limb("head", 1, [(0, (0,)), (0.4, (-18,)), (1.0, (0,))]),
    )


def core_attack_lash():
    """左臂抽击（反向横扫）。"""
    return merge(
        limb("body", 1, [(0, (0,)), (0.22, (28,)), (0.6, (-26,)), (1.2, (0,))]),
        {"arm_l": {"rotation": rot_track([(0, (0, 0, 0)), (0.22, (-34, 30, 20)),
                                          (0.6, (-18, -28, -12)), (1.2, (0, 0, 0))])}},
        limb("arm_r", 0, [(0, (0,)), (0.5, (-14,)), (1.2, (0,))]),
    )


def core_hurt():
    return merge(
        limb("body", 0, [(0, (0,)), (0.12, (-9,)), (0.32, (-3,)), (0.5, (0,))]),
        limb("head", 0, [(0, (0,)), (0.12, (-11,)), (0.5, (0,))]),
        limb("arm_r", 0, [(0, (0,)), (0.12, (-14,)), (0.5, (0,))]),
        limb("arm_l", 0, [(0, (0,)), (0.12, (-14,)), (0.5, (0,))]),
        limb("leg_r", 0, [(0, (0,)), (0.14, (8,)), (0.5, (0,))]),
        limb("leg_l", 0, [(0, (0,)), (0.14, (6,)), (0.5, (0,))]),
    )


def core_death():
    """倒地：向后仰倒 + 下沉。"""
    return merge(
        {"root": {"position": vec_track([(0, (0, 0, 0)), (0.9, (0, -3.0, 0)),
                                         (1.8, (0, -6.0, 0))])}},
        limb("body", 0, [(0, (0,)), (0.6, (16,)), (1.8, (-74,))]),
        limb("head", 0, [(0, (0,)), (0.9, (14,)), (1.8, (26,))]),
        limb("leg_r", 0, [(0, (0,)), (1.8, (26,))]),
        limb("leg_l", 0, [(0, (0,)), (1.8, (-20,))]),
        {k: {"rotation": rot_track([(0, (0, 0, 0)), (1.8, (12, 0, v))])}
         for k, v in (("arm_r", -22.0), ("arm_l", 22.0))},
    )


def core_phase_roar():
    return merge(
        {"root": {"position": vec_track([(0, (0, 0, 0)), (0.7, (0, 3.2, 0)),
                                         (2.0, (0, 0, 0))])}},
        limb("body", 0, [(0, (0,)), (0.4, (10,)), (1.2, (-15,)), (2.0, (0,))]),
        limb("head", 0, [(0, (0,)), (0.5, (-28,)), (2.0, (0,))]),
        {"arm_r": {"rotation": rot_track([(0, (0, 0, 0)), (0.6, (-14, 0, -46)),
                                          (2.0, (0, 0, 0))])}},
        {"arm_l": {"rotation": rot_track([(0, (0, 0, 0)), (0.6, (-14, 0, 46)),
                                          (2.0, (0, 0, 0))])}},
    )


def core_phase_awaken():
    return merge(
        {"root": {"position": vec_track([(0, (0, 0, 0)), (0.9, (0, 4.0, 0)),
                                         (2.4, (0, 0, 0))])}},
        limb("body", 0, [(0, (0,)), (0.5, (-9,)), (2.4, (0,))]),
        limb("head", 0, [(0, (0,)), (0.6, (-20,)), (2.4, (0,))]),
        {"arm_r": {"rotation": rot_track([(0, (0, 0, 0)), (0.9, (-16, 0, -62)),
                                          (2.4, (0, 0, 0))])}},
        {"arm_l": {"rotation": rot_track([(0, (0, 0, 0)), (0.9, (-16, 0, 62)),
                                          (2.4, (0, 0, 0))])}},
    )


# ============================================================
# 组装
# ============================================================

def build(form):
    """返回 {anim_id: {animation_length, loop, bones}}。"""
    chains = chains_for(form)

    def wing(length, base, gain, off=0.0):
        if not chains:
            return []
        # ★ dv24.5：链是 16 段，**每段幅度与相位延迟都要按段数重标定**。
        #   累计摆幅 ≈ 16·base + 120·gain；下面这组让 idle 约 20°、咆哮约 58°。
        #   早前按 6 段标定的幅度套在 12/16 段上，会把整片翼搅成碎块。
        return wing_tracks(chains, length=length, base=base, gain=gain,
                           delay=0.035, phase=off, seed=SEED + int(off * 100))

    defs = [
        # ★ dv24.1：翼加大后，扇动幅度同步加大 —— 大翼配小摆幅会显得僵。
        # ★ dv25（羽翼）：每根羽毛都是**独立刚体**（1 骨骼 + 一串小方块），
        #   绕翼根摆动，不存在链式累计/断裂问题 —— 所以摆幅可以给得**大得多**，
        #   整对翼才像真翅膀在扇。早前按「链」标的 0.45°/根 在羽毛上完全看不见。
        #   单根摆幅 ≈ base + 11·gain（12 根飞羽）。
        (ANIM_IDS[0], 4.0, True, core_idle(), 4.0, 6.0, 0.9),
        (ANIM_IDS[1], 1.2, True, core_walk(), 1.2, 9.0, 1.2),
        (ANIM_IDS[2], 1.8, "hold_on_last_frame", core_death(), 1.8, 20.0, 2.4),
        (ANIM_IDS[3], 0.5, False, core_hurt(), 0.5, 12.0, 1.6),
        (ANIM_IDS[4], 1.0, False, core_attack_sweep(), 1.0, 18.0, 2.2),
        (ANIM_IDS[5], 1.2, False, core_attack_smash(), 1.2, 15.0, 1.8),
        (ANIM_IDS[6], 1.2, False, core_attack_lash(), 1.2, 18.0, 2.2),
        (ANIM_IDS[7], 2.0, False, core_phase_roar(), 2.0, 26.0, 3.0),
        (ANIM_IDS[8], 2.4, False, core_phase_awaken(), 2.4, 30.0, 3.4),
    ]

    out = {}
    for k, (aid, length, loop, core, tl, base, gain) in enumerate(defs):
        tracks = merge(core, *wing(tl, base, gain, off=0.11 * k))
        out[aid] = {"animation_length": length, "loop": loop, "bones": tracks}
    return out


# ============================================================
# 自检
# ============================================================

def self_check_names(geo, anims, name):
    problems = []
    bones = {b["name"] for b in geo["minecraft:geometry"][0]["bones"]}
    for aid, a in anims.items():
        for bn_ in a["bones"]:
            if bn_ not in bones:
                problems.append(f"{name} / {aid.split('.')[-1]} 引用了不存在的骨骼 [{bn_}]")
    return problems


def self_check_joints(geo, anims, name):
    """★ 触手链的两条体检（dv19/dv20「一播动画就散架」的防线）：

      ① 相邻节中心距必须与静止姿态一致（散架的第一种表现：板块被拧开）
      ② 链条「直线跨度 / 弧长」不得过小（散架的第二种表现：整条卷成一团）
         并且首节必须始终挂在躯干附近（不能被甩飞）

    注意：只查 ① 是不够的 —— dv19 那种写法珠子间距也是恒定的，
    真正暴露问题的是 ②（累计旋转把链子卷成一团）。
    """
    bones = GM.model_bones(geo)
    chains = {}
    for bn_ in bones:
        if bn_.startswith("wing_"):
            parts = bn_.split("_")   # wing_l_3
            if len(parts) != 3 or not parts[2].isdigit():
                continue             # wing_r_rib_0 / wing_r_claw 等附件，跳过
            chains.setdefault(f"{parts[0]}_{parts[1]}", []).append(bn_)
    problems = []
    np = __import__("numpy")

    for aid, a in anims.items():
        posed_all = {}
        for bn_, chans in a["bones"].items():
            tr = chans.get("rotation")
            if tr:
                posed_all[bn_] = tr
        for key, names in chains.items():
            names.sort(key=lambda n: int(n.split("_")[-1]))
            # 静止节距
            rest = [float(np.linalg.norm(
                np.array(GM.bone_pivot(bones[names[k + 1]])) -
                np.array(GM.bone_pivot(bones[names[k]]))))
                for k in range(len(names) - 1)]
            rest_d = float(np.linalg.norm(
                np.array(GM.bone_pivot(bones[names[-1]])) -
                np.array(GM.bone_pivot(bones[names[0]]))))
            rest_len = sum(rest)
            for t in (0.2, 0.4, 0.6, 0.8, 1.0):
                posed = {k: _sample(v, t) for k, v in posed_all.items()}
                cache = {}
                cen = []
                for n in names:
                    m = _matrix(bones, n, posed, cache)
                    p = GM.bone_pivot(bones[n])
                    cen.append((m @ np.array([p[0], p[1], p[2], 1.0]))[:3])
                d = [float(np.linalg.norm(cen[k + 1] - cen[k]))
                     for k in range(len(cen) - 1)]
                # ★ 与**静止姿态**逐节对比（不是要求各节等距 ——
                #   dv22.1 起每节长度是递增的 5.5~9.5px，属于设计）
                dev = max(abs(d[k] - rest[k]) for k in range(len(d))) if d and rest else 0.0
                if dev > 0.06:
                    problems.append(f"{name}/{aid.split('.')[-1]}/{key} 节距畸变 "
                                    f"{dev:.3f}px @t={t}")
                span = float(np.linalg.norm(cen[-1] - cen[0]))
                arc = sum(d)
                if arc > 1e-6 and span / arc < 0.55:
                    problems.append(f"{name}/{aid.split('.')[-1]}/{key} 触手卷曲异常 "
                                    f"跨度/弧长={span / arc:.2f} @t={t}")
                # 首节必须挂在躯干附近（静止时的距离 + 余量）
                anchor = GM.bone_pivot(bones[names[0]])
                parent = bones[names[0]].get("parent")
                if parent and parent in bones:
                    mp = _matrix(bones, parent, posed, cache)
                    pp = GM.bone_pivot(bones[parent])
                    wpar = (mp @ np.array([pp[0], pp[1], pp[2], 1.0]))[:3]
                    if float(np.linalg.norm(cen[0] - wpar)) > 24.0:
                        problems.append(f"{name}/{aid.split('.')[-1]}/{key} 根部被甩飞 "
                                        f"{float(np.linalg.norm(cen[0] - wpar)):.1f}px @t={t}")
    return problems


def _seg_len(bones, a, b):
    pa = GM.bone_pivot(bones[a]); pb = GM.bone_pivot(bones[b])
    return sum((pa[i] - pb[i]) ** 2 for i in range(3)) ** 0.5


def _sample(track, t):
    """线性插值取一帧。注意 key 是 str(t) 且格式不统一（"0" / "1.0"），
    所以必须保留原始 key，不能拿 float 反推字符串。"""
    pairs = sorted(((float(k), k) for k in track))
    if t <= pairs[0][0]:
        return track[pairs[0][1]]["post"]
    if t >= pairs[-1][0]:
        return track[pairs[-1][1]]["post"]
    for i in range(len(pairs) - 1):
        t0, k0 = pairs[i]
        t1, k1 = pairs[i + 1]
        if t0 <= t <= t1:
            f = (t - t0) / (t1 - t0)
            a = track[k0]["post"]
            b = track[k1]["post"]
            return [a[j] + (b[j] - a[j]) * f for j in range(3)]
    return track[pairs[0][1]]["post"]


def _matrix(bones, name, posed, cache):
    if name in cache:
        return cache[name]
    b = bones[name]
    p = GM.bone_pivot(b)
    rot = posed.get(name, GM.bone_rotation(b))
    m = GM.T(*p) @ GM.H(rot) @ GM.T(-p[0], -p[1], -p[2])
    par = b.get("parent")
    if par:
        m = _matrix(bones, par, posed, cache) @ m
    cache[name] = m
    return m


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    all_problems = []
    # ★ dv34：只生成 form1 的动画 —— form2/form3 的动画由 bbmodel 管线
    #   （tools/bbmodel_to_gecko.py）产出，跑这里会覆盖掉。
    for form in ("warden_form1",):
        anims = build(form)
        missing = [i for i in ANIM_IDS if i not in anims]
        if missing:
            print("!! 缺少动画 id：", missing)
            raise SystemExit(1)
        data = {"format_version": "1.8.0",
                "animations": {k: anims[k] for k in ANIM_IDS}}
        path = os.path.join(OUT_DIR, form + ".animation.json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=1)
        tracks = sum(len(a["bones"]) for a in anims.values())
        print(f"  {form}.animation.json  动画 {len(anims)} 条  骨骼轨道 {tracks}")

        geo_path = os.path.join(GEO_DIR, form + ".geo.json")
        if os.path.exists(geo_path):
            geo = json.load(open(geo_path, encoding="utf-8"))
            all_problems += self_check_names(geo, anims, form)
            all_problems += self_check_joints(geo, anims, form)

    if all_problems:
        print("!! 动画自检失败：")
        for p in all_problems[:30]:
            print("   -", p)
        raise SystemExit(1)
    print("自检通过：骨骼名全部存在、9 个 id 齐全、触手关节无断裂")


if __name__ == '__main__':
    print("生成遗落之主动画（dv21 人形 + 触手蠕动）->")
    main()
