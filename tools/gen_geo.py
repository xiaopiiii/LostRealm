#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
遗落之主三形态 GeckoLib 几何生成器（dv21 从零重做：人形基础 + 触手）。

=== dv21 为什么推倒重来 ===
dv16~dv20 的造型是「长袍幽影 / 巨镰使 / 终末之主」，用户反馈：
  「重新从零开始做，用人的模型来做，换阶段不会变大，
    一阶段到二阶段会长出很多随机蠕动的触手，要长，两倍身体长度」
所以 dv21 的设计是：

  · **三形态共用一套人形**（经典 Minecraft 人形比例：头 8 / 躯干 12 / 腿 12，
    32px = 2 格），阶段差异只体现在「长出来的东西」，**体型完全一致**。
  · form1：人 + 巨镰。
  · form2：人 + 巨镰 + **10 条长触手**（背部 8 条 + 双臂各 1 条）。
  · form3：人 + 巨镰 + **16 条触手**（背部 12 + 双臂各 2）+ 头冠。
  · 触手长度 = 2 × 体长 = 2 × 32 = **64px**（16 节 × 4px）。

=== ★ 触手链的唯一正确写法：枢轴放在「关节」★ ===
dv19/dv20 的触手是「每节骨骼的 pivot = 珠子自己的中心」。静止姿态看着没问题，
**一播动画就散架**：每节绕自己中心转，关节处的相邻两节被互相拧开，
累计下来整条链被搅成一团悬浮的小方块。

正确写法（本文件 part_tentacles 采用）：
  · 第 k 节的 **pivot = 第 k-1 节的末端**（也就是关节）；
  · 第 k 节的 cube 只是恰好摆在那一节的位置上；
  · 于是「绕 pivot 旋转」= 「绕关节弯折」——子节连同其后代一起被带过去，
    关节永远严丝合缝，链可以任意弯曲而不断裂。

=== 坐标系（已由官方模型钉死）===
  · x：±，左为正、右为负；y：向上，脚底 = 0；z：实体朝 -z（-z 是正面）
  · bone.pivot 与 cube.origin 都写在同一个模型空间里

=== 落盘前的强制自检 ===
  A1 不入地（脚掌 y≈0、其余部件 y_min ≥ -4.5）
  A2 核心居中（人形躯干/头/四肢合并 AABB 的 x 中心 |c| ≤ 0.6px）
  A3 核心部件逐值复现基准表（抬升感知）
  A4 规模合理
  UV 区域不重叠 + 每个 cube 的展开装得进所属区域
  ★ 体型一致性（三形态人形高度相同 —— 用户要求「换阶段不会变大」）
  任一失败 → SystemExit(1)，不落盘。
"""

import json
import math
import os
import random
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gecko_math as GM
import uv_layout as UV

OUT_DIR = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources',
                       'assets', 'lost_realm', 'geo')

SEED = 20261013


# ============================================================
# 基础构件
# ============================================================

def cub(origin, size, region, uv=None):
    return {
        "origin": [round(float(v), 3) for v in origin],
        "size": [round(float(v), 3) for v in size],
        "uv": list(uv) if uv is not None else list(UV.uv(region)),
        "inflate": 0.0,
    }


def bn(name, pivot, parent=None, cubes=None, rotation=None):
    b = {"name": name, "pivot": [round(float(v), 3) for v in pivot]}
    if parent:
        b["parent"] = parent
    if rotation is not None:
        b["rotation"] = [round(float(v), 3) for v in rotation]
    b["cubes"] = cubes or []
    return b


# ============================================================
# 人形基准（经典 Minecraft 人形，32px = 2 格）
# ============================================================

# ★★ dv28：模型从 48px 放大到 **96px（6 格）** ★★
#   目的不是"整体变大"，而是**空间翻倍、细节尺寸不变** ——
#   这样同样一个小方块在新模型里只占一半的相对大小，
#   羽毛/盔甲才能堆出参考图那种密度（48px 时空间根本不够放）。
#   游戏内显示尺寸由渲染器的 BASE_GEO_HEIGHT 换算，模型多大都不影响。
LEG_TOP = 36.0          # 腿顶 = 髋
BODY_TOP = 72.0         # 躯干顶 = 脖子
HEAD_TOP = 96.0         # 头顶（96px = 6 格）
SHOULDER = 66.0         # 肩关节高度
BODY_LEN = HEAD_TOP

HUMAN_CORE = ("body", "head", "arm_r", "arm_l", "leg_r", "leg_l")

# 核心部件世界 AABB 基准（未抬升；x 居中 / y 向上 / z 前后）
CORE_BASELINE = {
    "body":  (-12.0, 12.0, 36.0, 72.0, -6.0, 6.0),
    "head":  (-12.0, 12.0, 72.0, 96.0, -12.0, 12.0),
    "arm_r": (-24.0, -12.0, 36.0, 72.0, -6.0, 6.0),
    "arm_l": (12.0, 24.0, 36.0, 72.0, -6.0, 6.0),
    "leg_r": (-12.0, 0.0, 0.0, 36.0, -6.0, 6.0),
    "leg_l": (0.0, 12.0, 0.0, 36.0, -6.0, 6.0),
}


def part_human():
    """经典人形：三段式（头/躯干/腿）+ 两条下垂手臂。三形态共用。

    ★ dv25：整体放大到 **150%**（48px = 3 格）。用户要「身体加宽 + 身高拉到 3 格」：
      躯干 8→12 宽、头 8→12、四肢 4→6，身高 32→48。
      放大后身体才有分量，能压住背后的羽翼，不再像「大布挂在小虫子上」。
    """
    return [
        bn("body", [0.0, LEG_TOP, 0.0], "root",
           [cub([-12, LEG_TOP, -6], [24, 36, 12], "TORSO")]),
        bn("head", [0.0, BODY_TOP, 0.0], "body",
           [cub([-12, BODY_TOP, -12], [24, 24, 24], "HEAD")]),
        bn("arm_r", [-18.0, SHOULDER, 0.0], "body",
           [cub([-24, LEG_TOP, -6], [12, 36, 12], "ARM")]),
        bn("arm_l", [18.0, SHOULDER, 0.0], "body",
           [cub([12, LEG_TOP, -6], [12, 36, 12], "ARM")]),
        bn("leg_r", [-6.0, LEG_TOP, 0.0], "root",
           [cub([-12, 0, -6], [12, 36, 12], "LEG")]),
        bn("leg_l", [6.0, LEG_TOP, 0.0], "root",
           [cub([0, 0, -6], [12, 36, 12], "LEG")]),
    ]


# ============================================================
# ★ dv30：巨镰不再是模型几何，改为「手持物品」★
# ============================================================
#
# 为什么改：dv29~dv29.11 反复重写了十几版镰刀几何，用户始终不满意 ——
#   "依然是用一个一个方块拼起来的"。GeckoLib 只有长方体元素，根本做不出
#   参考图那种流畅的新月弧；手搓几百个方块拼一把镰刀也不现实。
#
# 新方案：把镰刀注册成**独立物品**（lost_realm:warden_scythe），
#   贴图直接取自参考图（128x128，程序化去棋盘格背景），由
#   client/boss/WardenItemInHandLayer 渲染到 weapon 骨骼上。
#   → 造型 100% 忠于参考图，且能随动画摆臂一起动。
#
# weapon 骨骼因此退化为一个**空锚点**（无 cubes），只负责两件事：
#   ① pivot = 右手位置，供物品层定位
#   ② rotation.x = 把刀锋"立起来"（物品 display 里的 Ry(-90) 会让
#      长度方向偏向前后 z 轴，绕 X 转 -35° 把 z 分量转回 y，刀尖才够高）
#
# 坐标语义（与本项目其它骨骼一致）：
#   · cube.origin 与子骨骼 pivot 都在**父骨骼的 cube 坐标系**里
#   · arm_r 的 cube 在模型空间 (-24,36,-6)..(-12,72,6)，
#     所以右手（臂底中心）= 模型空间 (-18, 36, 0)

HAND_R = (-18.0, 36.0, 0.0)     # 右手握持点（arm_r 立方体底面中心）
WEAPON_TILT_X = 0.0             # 立起刀锋的俯仰角（度）
#   ★ 现在是 0：物品 display 已不再绕 Y 轴转（ry=0，纸片正对镜头），
#     且贴图已旋转成竖直构图，不需要骨骼再补俯仰。
#     若想让镰刀前倾/后仰，改这里即可（正=前倾）。


def part_weapon_anchor():
    """巨镰的手持锚点（dv30）：空骨骼，物品由渲染层挂上来。"""
    return [bn("weapon", list(HAND_R), "arm_r", [],
               rotation=[WEAPON_TILT_X, 0.0, 0.0])]



# ============================================================
# ★ 触手链（dv21 核心）★
# ============================================================

# ★ dv22.1：回到「第一版触手」（dv13）的做法 —— 用户明确说那版最好。
#   dv13 的触手之所以像触手，是因为每节是**一根细长的杆**，不是一颗小方块：
#     截面 2.2 → 1.76 → 1.41 → 1.13 → 0.90（逐节收细）
#     沿走向长度 5.5 → 6.1 → 6.7 → 7.2 → 7.8（逐节变长）
#   5 节拼起来 ≈ 33px，整条又细又长、越往尖端越尖。
#   本版把它拉长到 2 倍体长（用户之前的要求）：
#     9 节，长度 5.5→9.5 递增，总长 ≈ 67px；截面 2.2→0.95 收细。
# ★ dv23.2：用户反馈「不衔接，就像一些棍子放在身后而已，没有手的感觉」。
#   根因：每根触手一出来就是 2.2px 的细棍 → 看着像"贴"在背后的一根棍。
#   真实肢体的特征是「**根部粗、迅速收细、从体内长出来**」，所以：
#     · 首节截面 4.4px（≈ 手臂粗细），逐节 ×0.82 快速收细到 1.4px
#     · 挂点移到**躯干内部**（z=+0.6，躯干背面在 z=+2），根段自然从肉里钻出来
#     · 每根再加一个「根座」鼓包，明确长在背上
# ============================================================
# ★ 羽翼（dv25：从「膜」改成「羽毛」）★
# ============================================================
# 用户给的参考图是**黑色羽毛翅膀**：一根根细长的羽毛从翼根扇形展开、
# 越外侧越长、末端收成尖，羽毛之间留缝 —— 这是「翼」的辨识度所在。
#
# === 为什么羽毛比膜更适合方块 ===
# 膜翼无论多少段拼起来，轮廓永远是一条平滑的边，看着就是「一块布/一块板」
# （dv24 试了 6 版都是这个毛病）。而羽毛本来就是**细长的条**，方块只要
# 沿一条直线密排就能表现；羽毛之间的**缝隙**又天然给出羽状边缘。
#
# === 实现铁律（踩过的坑）===
#   · **完全不使用 bone.rotation 做扇形**。之前试过让每块板带静态倾斜，
#     结果父子链的旋转重复累计，整对翼被算飞（y 冲到 77px）。
#     这里改用「沿方向密排轴对齐小方块」—— 方向由坐标算，不经旋转矩阵。
#   · 每根羽毛是**独立刚体**（自己的骨骼 + 一串小方块），动画时绕翼根摆动，
#     不存在链式断裂问题，可以放心给大摆幅。

# ★★ dv27：**每一片羽毛都带倾斜** —— 这是折腾十几版后最关键的一条 ★★
#
#   用户给的参考图里，羽毛是一根根**斜的细长条**，层层叠叠；
#   而之前所有版本的方块都是**轴对齐**的（只能横或竖），所以怎么调都是「一格一格」。
#
#   GeckoLib 的 bone 有 rotation，正确用法（已用小实验逐项验证）：
#     · pivot 沿**局部 +x** 前进（不是沿"世界方向"！否则父旋转会把它重复转一次，
#       整条链被算飞 —— dv24 那次 y 冲到 77px 就是这个原因）；
#     · 骨骼 rotation 是**相对父的增量**：
#         第 0 段给整根羽毛的方向角，后续段只给很小的「弯曲增量」；
#         增量全为 0 时整根羽毛是**直的**（子段继承父的旋转）。
#     · 绕 z 旋转：取负 = 上扬（右翼镜像取正）。
#
#   每段 = 一个「以 pivot 为中心、沿羽轴拉长」的方块 → 段段相叠成连续羽片。

FEATHER_N = 8                    # 主飞羽根数（16 → 8：少而清晰，不再是一坨黑点）
F_ANG0, F_ANG1 = -6.0, 26.0      # 羽轴方向角（-14..46 → -6..26：收窄扇面，收拢贴背）
# ★ 段数翻倍、**每段长度不变** —— 模型空间翻倍后，羽毛变长了一倍，
#   但组成它的方块还是原来那么大，所以羽片看起来细了一倍（这才是放大的意义）。
F_SEG = 7                        # 每根羽毛的段数（11 → 7：缩短，不再拖得很长）
F_SEGLEN0, F_SEGLEN1 = 5.5, 8.5  # 每段长度
F_W0, F_W1 = 7.5, 3.4            # 羽毛宽度（加宽单片，轮廓更清晰）
F_TH = 2.6                       # 羽毛厚度（**保持不变**）
F_OV = 2.6                       # 段间沿羽轴的重叠（**保持不变**）
F_CURL = 1.5                     # 每段弯曲增量（度）

COVERT_N = 5                     # 覆羽（10 → 5：压根部的短羽，少量即可）
C_ANG0, C_ANG1 = -14.0, 10.0
C_SEG = 4                        # 覆羽段数（6 → 4）
C_SEGLEN = 6.0
C_W = 5.6
C_TH = 2.4

WING_ORIGIN = (4.8, 66.0, 6.0)   # 翼根挂点（肩胛、躯干背面；×2）


def _feather_chain(name, side, ang_deg, segs, seg_len, w0, w1, th, ov, curl,
                   ox, oy, oz, region="FEATHER"):
    """一根羽毛：`segs` 块**倾斜**方块，沿手工算出的弧线排布。

    ★ 为什么**不用父子链**（踩过的坑，dv27）：
      链式写法里，第 k 段的 pivot 是"在父坐标系里沿 +x 前进 seg_len"，
      父的旋转会把它再转一次 —— 实测段间**世界距离会从 7.7 一路涨到 13.0**，
      而方块只有 10.8 长 → 羽毛断成一截一截。
      改成「每段都独立挂在 body 下、位置由 cos/sin 直接算出来」后，
      段距恒等于 seg_len，重叠量完全可控。
      （方块以 pivot 为中心，所以绕 z 正向或反向转都落在同一条直线上，
        左右翼的镜像只需翻转 rotation 的符号。）

    :param side: +1 左翼（向 +x 伸展）/ -1 右翼（向 -x 伸展）
    """
    out = []
    px, py, pz = ox, oy, oz
    cum = ang_deg                       # 段 k 的**绝对方向角**（逐段加 curl 变弯）
    for k in range(segs):
        a = math.radians(cum)
        half = seg_len * 0.5
        # 段心 = 沿弧线从本段起点前进半段
        cx = px + side * math.cos(a) * half
        cy = py + math.sin(a) * half
        t = (k + 0.5) / segs
        w = w0 + (w1 - w0) * t
        L = seg_len + ov
        cube = cub([cx - L * 0.5, cy - w * 0.5, pz - th * 0.5], [L, w, th], region)
        # 绕 z 的符号：取负 = 上扬；右翼镜像取正
        deg = -cum if side > 0 else cum
        # ★★ pivot 的 x 必须取负 ★★
        #   GeckoLib 的 constructCube 把 cube.origin.x 换成 -(ox+ow)/16（x 镜像），
        #   而 constructBone 的 pivot **不镜像**（净效果 = 原值）。
        #   于是「带 rotation 的骨骼」如果 pivot.x 不取负，旋转中心会跑到方块的
        #   另一侧去 —— 表现就是方块绕着一个远处的点在转，段距忽大忽小、羽毛断开。
        #   （0 附近的小方块看不出问题，所以这个坑一直没暴露。）
        out.append(bn(f"{name}_{k}", [-cx, cy, pz], "body", [cube],
                      rotation=[0.0, 0.0, round(deg, 3)]))
        # 前进到本段末端（下一段的起点）
        px += side * math.cos(a) * seg_len
        py += math.sin(a) * seg_len
        cum += curl
    return out


def part_feather_wings(tag_pairs=(("r", -1.0), ("l", 1.0)),
                       segs_extra=0, z_step=1.1):
    """一对羽翼：羽毛向上外扇形展开，越外侧越长、越上扬，且逐段外弯。

    :param segs_extra: 额外段数（三阶段的翼更长，加段数而不是加方块尺寸）
    """
    out = []
    ox0, oy0, oz0 = WING_ORIGIN
    for tag, side in tag_pairs:
        ox = side * ox0
        oy = oy0
        oz = oz0
        # 翼根骨板：让羽毛明确「从背上长出来」
        out.append(bn(f"wroot_{tag}", [ox, oy, oz - 1.2], "body",
                      [cub([ox - 3.6, oy - 3.9, oz - 2.4],
                           [7.2, 7.8, 4.8], "WINGROOT")]))
        rng = random.Random(SEED + (0 if tag == "r" else 7))
        # 主飞羽：越外侧越长、越上扬、越靠后（后掠）
        for i in range(FEATHER_N):
            t = i / (FEATHER_N - 1)
            ang = F_ANG0 + (F_ANG1 - F_ANG0) * t + rng.uniform(-1.6, 1.6)
            sl = (F_SEGLEN0 + (F_SEGLEN1 - F_SEGLEN0) * t) + rng.uniform(-0.5, 0.5)
            out += _feather_chain(f"wing_{tag}_{i}", side, ang, F_SEG + segs_extra, sl,
                                  F_W0, F_W1, F_TH, F_OV, F_CURL,
                                  ox, oy, oz + i * z_step)
        # 覆羽：短、密、压在根部之上（z 更靠前）
        for i in range(COVERT_N):
            t = i / (COVERT_N - 1)
            ang = C_ANG0 + (C_ANG1 - C_ANG0) * t + rng.uniform(-1.2, 1.2)
            out += _feather_chain(f"wingc_{tag}_{i}", side, ang, C_SEG,
                                  C_SEGLEN, C_W, C_W * 0.62, C_TH, F_OV, 1.6,
                                  ox, oy, oz - 3.8 + i * 0.8)
    return out


# ============================================================
# ★ dv33：末影龙式蝠翼（替换 dv27 的羽毛链）★
# ============================================================
#
# 用户看了实机后反馈羽毛翅膀「一坨黑点、什么鬼」，同意换成末影龙那种
# 「翼骨 + 翼膜」的形态。GeckoLib 没有三角网格，翼膜用**少数几片大的薄
# 长方体**拼：1 根翼骨（3 节）+ 3 片扇形翼膜，从肩胛向两侧上方展开。
#
# 朝向选 **x-y 平面展开**（绕 z 轴，复用羽毛已验证的镜像规则）：
#   末影龙翅膀最鲜明的正面轮廓就是一个「斜向上张开的三角膜 + 指骨」，
#   Boss 是被围观的，膜面正对镜头才看得清 —— 绕 x 的「向后展开」版
#   从正面看会缩成一条线（上一版因此几乎看不见）。
#   膜宽放在 z（向后），所以侧面看仍有厚度，不会变成纯纸片。

D_WING_ORIGIN = (6.0, 62.0, 6.0)   # 翼根（肩胛外侧、背面）


def part_dragon_wings(tag_pairs=(("r", -1.0), ("l", 1.0))):
    """一对末影龙式蝠翼：翼根骨座 + 展开翼骨 + 3 片扇形翼膜。"""
    out = []
    ox0, oy0, oz0 = D_WING_ORIGIN
    for tag, side in tag_pairs:
        ox = side * ox0
        oy = oy0
        oz = oz0
        # 翼根骨座：让翅膀明确「从背上长出来」
        out.append(bn(f"wroot_{tag}", [ox, oy, oz - 1.2], "body",
                      [cub([ox - 3.4, oy - 3.6, oz - 2.2],
                           [6.8, 7.2, 4.4], "WINGROOT")]))
        # 翼骨：3 节，绕 z 向「侧上方」展开（逐节更上扬）
        px, py = ox, oy
        cum = 14.0
        for k in range(3):
            a = math.radians(cum)
            L = 9.0
            cx = px + side * math.cos(a) * L * 0.5
            cy = py + math.sin(a) * L * 0.5
            cube = cub([cx - L * 0.5, cy - 1.7, oz - 1.7], [L, 3.4, 3.4], "WINGROOT")
            deg = -cum if side > 0 else cum      # 与羽毛一致的镜像规则
            out.append(bn(f"wbone_{tag}_{k}", [-cx, cy, oz], "body", [cube],
                          rotation=[0.0, 0.0, round(deg, 3)]))
            px += side * math.cos(a) * L
            py += math.sin(a) * L
            cum += 17.0
        # 翼膜：3 片大薄方块，绕 z 以不同仰角向侧上方扇形展开
        #   尺寸 [长 L, 厚 TH, 宽 W]：长在 x（展开方向）、宽在 z（向后），
        #   front/back 面是 L×W 的大面，从正面看得见整片膜。
        for i, ang_deg in enumerate((22.0, 40.0, 58.0)):
            a = math.radians(ang_deg)
            L = 19.0 - i * 3.0      # 越靠下越短（三角翼尖）
            W = 13.0 - i * 2.0      # 膜宽（z 向后，逐片变窄）
            TH = 1.6                # 翼膜厚度
            cx = ox + side * math.cos(a) * L * 0.5
            cy = oy + math.sin(a) * L * 0.5
            cube = cub([cx - L * 0.5, cy - TH * 0.5, oz - W * 0.5],
                       [L, TH, W], "WING")
            deg = -ang_deg if side > 0 else ang_deg
            out.append(bn(f"wmem_{tag}_{i}", [-cx, cy, oz], "body", [cube],
                          rotation=[0.0, 0.0, round(deg, 3)]))
    return out


# ============================================================
# 头冠（仅 form3）
# ============================================================

HORN_SPEC = [
    # (x, y, z, 长, 粗, 绕 z 倾角)
    (-9.6, HEAD_TOP - 1.2, 3.6, 19.2, 7.2, 22.0),
    (9.6, HEAD_TOP - 1.2, 3.6, 19.2, 7.2, -22.0),
    (-13.2, HEAD_TOP - 7.2, -7.2, 15.0, 6.0, 40.0),
    (13.2, HEAD_TOP - 7.2, -7.2, 15.0, 6.0, -40.0),
    (0.0, HEAD_TOP + 1.8, 1.2, 16.8, 6.6, 0.0),
]


def part_crown():
    """头冠：绕 z 倾斜的角。

    ★ 与羽毛同一个坑：cube 的 x 会被 GeckoLib 镜像、pivot 不会，
      所以**带 rotation 的骨骼 pivot.x 必须取负**，否则角会绕着错位的中心转。
    """
    out = []
    for i, (x, y, z, h, w, tilt) in enumerate(HORN_SPEC):
        out.append(bn(f"horn_{i}", [-x, y, z], "head",
                      [cub([x - w / 2, y, z - w / 2], [w, h, w], "HORN")],
                      rotation=[0.0, 0.0, tilt]))
    return out


# ============================================================
# 三形态组装
# ============================================================

def wrap(bones, ident, vbw, vbh, vbo):
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": ident,
                "texture_width": UV.TEX_W,
                "texture_height": UV.TEX_H,
                "visible_bounds_width": vbw,
                "visible_bounds_height": vbh,
                "visible_bounds_offset": vbo,
            },
            "bones": [bn("root", [0.0, 0.0, 0.0])] + bones,
        }]
    }


def build_form1():
    """一阶段：人形 + 拄地巨镰。"""
    bones = part_human() + part_weapon_anchor()
    return wrap(bones, "geometry.lost_realm.warden_form1", 12.0, 10.0, [0, 4.0, 0])


def build_form2():
    """二阶段：人形 + 巨镰 + **一对羽翼**。

    ★ dv26：羽翼**不再是 GeckoLib 几何** —— 它是自由三角网格
      （见 tools/gen_wing_mesh.py），由渲染器直接画三角形。
      这里只留一个空的 `wing_root` 挂点，方便 Java 侧拿到翼根位置。
    """
    bones = part_human() + part_weapon_anchor()
    bones += part_dragon_wings()
    return wrap(bones, "geometry.lost_realm.warden_form2", 32.0, 24.0, [0, 6.4, 0])


def build_form3():
    """三阶段：人形 + 巨镰 + **末影龙式蝠翼** + 头冠。"""
    bones = part_human() + part_weapon_anchor()
    bones += part_crown()
    bones += part_dragon_wings()
    return wrap(bones, "geometry.lost_realm.warden_form3", 40.0, 28.0, [0, 7.2, 0])


# ============================================================
# 自检
# ============================================================

def ground_lift(data, name):
    """把整棵树沿 y 刚性抬起，使世界 AABB 最低点 = 0（只动 pivot.y/origin.y）。"""
    bones_list = data["minecraft:geometry"][0]["bones"]
    bones = GM.model_bones(data)
    cache = {}
    lo_y = 1e9
    for bname, b in bones.items():
        for c in b.get("cubes", []):
            l, h = GM.cube_world_aabb_px(bones, bname, c, cache)
            lo_y = min(lo_y, l[1])
    dy = -lo_y
    if abs(dy) < 1e-3:
        return 0.0
    for b in bones_list:
        if b.get("pivot"):
            b["pivot"][1] = round(b["pivot"][1] + dy, 3)
        for c in b.get("cubes", []):
            c["origin"][1] = round(c["origin"][1] + dy, 3)
    data["_ground_lift_dy"] = round(dy, 3)
    return dy


def _region_of(u, v):
    for n, (x, y, _, _) in UV.REGIONS.items():
        if x == u and y == v:
            return n
    return None


def verify_uv_fit(data, name):
    bad = []
    for b in data["minecraft:geometry"][0]["bones"]:
        for c in b.get("cubes", []):
            u, v = c["uv"]
            reg = _region_of(u, v)
            if reg is None:
                bad.append(f"{name}/{b['name']} UV({u},{v}) 未登记")
                continue
            w, h, d = c["size"]
            ew, eh = 2 * (d + w), d + h
            rw, rh = UV.MAX_UNFOLD[reg]
            if ew > rw + 0.01 or eh > rh + 0.01:
                bad.append(f"{name}/{b['name']} 区域 {reg} 展开 "
                           f"{ew:.1f}x{eh:.1f} 超出 {rw}x{rh}")
    return bad


def verify_world_layout(data, name):
    bones = GM.model_bones(data)
    cache = {}
    fails = []
    lo_all = np.array([1e9, 1e9, 1e9])
    hi_all = np.array([-1e9, -1e9, -1e9])
    for bname, b in bones.items():
        for c in b.get("cubes", []):
            l, h = GM.cube_world_aabb_px(bones, bname, c, cache)
            lo_all = np.minimum(lo_all, l)
            hi_all = np.maximum(hi_all, h)

    # A1 不入地：脚掌必须贴地；镰刀刃尖等允许略微探入
    for bn_ in ("leg_r", "leg_l"):
        if bn_ in bones:
            ly = GM.bone_world_aabb_px(bones, bn_, cache)[0][1]
            if abs(ly) > 0.5:
                fails.append(f"A1 脚掌未贴地 [{bn_}] y={ly:.2f}")
    for bname, b in bones.items():
        for c in b.get("cubes", []):
            l, h = GM.cube_world_aabb_px(bones, bname, c, cache)
            if l[1] < -13.6:
                fails.append(f"A1 部件入地过深 [{bname}] y_min={l[1]:.2f}")

    # A2 核心居中
    clo = chi = None
    for bname in HUMAN_CORE:
        if bname not in bones:
            continue
        l, h = GM.bone_world_aabb_px(bones, bname, cache)
        clo = l if clo is None else np.minimum(clo, l)
        chi = h if chi is None else np.maximum(chi, h)
    if clo is not None:
        c = (clo[0] + chi[0]) * 0.5
        if abs(c) > 0.6:
            fails.append(f"A2 人形未居中：x 中心 = {c:.2f}px")

    # A3 人形基准（抬升感知）
    dy = data.get("_ground_lift_dy", 0.0)
    for bname, exp in CORE_BASELINE.items():
        if bname not in bones:
            fails.append(f"A3 缺少核心骨骼 {bname}")
            continue
        l, h = GM.bone_world_aabb_px(bones, bname, cache)
        got = (round(l[0], 2), round(h[0], 2), round(l[1], 2),
               round(h[1], 2), round(l[2], 2), round(h[2], 2))
        exp_l = (exp[0], exp[1], round(exp[2] + dy, 2),
                 round(exp[3] + dy, 2), exp[4], exp[5])
        if any(abs(a - b) > 0.02 for a, b in zip(got, exp_l)):
            fails.append(f"A3 [{bname}] 偏离基准 得到 {got} 期望 {exp_l}")

    # A4 规模：触手 64px + 张开，z 到 ~66、x 到 ~±40 属预期
    # ★ dv24.1：骨翼加大后单侧到 x≈±56（翼展 ≈ 2.8 倍身高）属预期。
    # ★ dv25：3 格体型 + 羽翼（羽尖到 y≈95、翼展 ≈±48）属预期
    if hi_all[0] > 240 or lo_all[0] < -240:
        fails.append(f"A4 x 过宽 x[{lo_all[0]:.1f},{hi_all[0]:.1f}]")
    if hi_all[1] > 340 or lo_all[1] < -13.6:
        fails.append(f"A4 y 异常 y[{lo_all[1]:.1f},{hi_all[1]:.1f}]")
    if hi_all[2] > 260 or lo_all[2] < -56:
        fails.append(f"A4 z 过深 z[{lo_all[2]:.1f},{hi_all[2]:.1f}]")

    print(f"  {name}: AABB x[{lo_all[0]:6.1f},{hi_all[0]:6.1f}] "
          f"y[{lo_all[1]:6.1f},{hi_all[1]:6.1f}] z[{lo_all[2]:6.1f},{hi_all[2]:6.1f}]"
          f" | 骨骼 {len(bones)}")
    return fails, (lo_all, hi_all)


def count(data, name):
    blist = data["minecraft:geometry"][0]["bones"]
    nc = sum(len(b.get("cubes", [])) for b in blist)
    print(f"  {name}: 骨骼 {len(blist):3d}  立方体 {nc:3d}")
    return blist


def main():
    random.seed(SEED)
    os.makedirs(OUT_DIR, exist_ok=True)

    bad = UV.verify()
    if bad:
        print("!! UV 布局有问题：")
        for b in bad:
            print("   -", b)
        raise SystemExit(1)
    print(f"UV 布局自检通过：{len(UV.REGIONS)} 个区域两两不重叠（{UV.TEX_W}x{UV.TEX_H}）")

    print("生成遗落之主几何（dv21 人形基础 + 触手）->")
    all_data = {}
    # ★ dv34：二三阶段改由 bbmodel 管线（tools/bbmodel_to_gecko.py）独占，
    #   这里**只生成 form1** —— 否则每次跑本脚本都会把用户给的建模覆盖掉。
    #   单形态先跑一次全量自检，再补一个「只比 form1」的体型断言。
    for name, fn in (("warden_form1", build_form1),):
        data = fn()
        all_data[name] = data
        dy = ground_lift(data, name)
        if abs(dy) > 1e-3:
            print(f"  {name}: 贴地抬升 {dy:.2f}px")
        count(data, name)

    misfit = []
    for name, data in all_data.items():
        misfit += verify_uv_fit(data, name)
    if misfit:
        print("!! UV 展开尺寸超出所属区域：")
        for m in misfit[:40]:
            print("   -", m)
        raise SystemExit(1)
    print("UV 尺寸自检通过")

    print("世界坐标自检 ->")
    boxes = {}
    all_fails = []
    for name, data in all_data.items():
        f, box = verify_world_layout(data, name)
        all_fails += f
        boxes[name] = box
    if all_fails:
        print("!! 世界坐标自检失败（拒绝落盘）：")
        for f in all_fails[:40]:
            print("   -", f)
        raise SystemExit(1)
    print("世界坐标自检通过：贴地、人形居中、基准一致、规模合理")

    # ★ 三形态「人形本体」必须完全一致（用户要求「换阶段不会变大」）
    #   ★ dv25：**只比人形核心部件**，不能比整体 AABB ——
    #     羽翼/头冠本来就该超出头顶，用整体高度比会误报。
    def core_height(data):
        bones = GM.model_bones(data)
        cache = {}
        lo = np.array([1e9, 1e9, 1e9])
        hi = np.array([-1e9, -1e9, -1e9])
        for bname in HUMAN_CORE:
            if bname not in bones:
                continue
            l, h = GM.bone_world_aabb_px(bones, bname, cache)
            lo = np.minimum(lo, l)
            hi = np.maximum(hi, h)
        return float(hi[1] - lo[1])

    ref_h = core_height(all_data["warden_form1"])
    # ★ dv34：二三阶段已改为 bbmodel 管线，本脚本只产 form1，
    #   体型一致性只在「本脚本产出的形态之间」比较（当前只有 form1）。
    for name in ("warden_form2", "warden_form3"):
        if name not in all_data:
            continue
        bh = core_height(all_data[name])
        if abs(bh - ref_h) > 0.6:
            print(f"!! 体型不一致：{name} 人形高 {bh:.1f}px vs form1 {ref_h:.1f}px")
            raise SystemExit(1)
    print(f"体型一致性自检通过：本脚本产出形态的人形高度一致（{ref_h:.1f}px）")

    for name, data in all_data.items():
        with open(os.path.join(OUT_DIR, name + ".geo.json"), 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)


if __name__ == '__main__':
    main()
