#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
GeckoLib 变换模拟器（dv15 新增）—— 用于生成阶段的世界坐标自检。

=== 为什么需要这个文件 ===
dv14 的触手「散落、断裂、悬空、整体偏移」，肉眼排查无从下手。
根因是生成脚本用**绝对三角累加**算每一节的 pivot，
而 GeckoLib 实际是**逐骨骼递归累积 PoseStack**（子骨骼继承父骨骼的全部变换）——
两套坐标语义冲突，误差逐节放大，4 节后整条触手彻底飞散。

本模块用矩阵精确复现 GeckoLib 的变换链，把「很丑」变成可量化的数值断言：
  子节 pivot 世界位置 必须正好落在 父节 cube 末端面中心（误差 < 0.05px）。

=== 全部结论均来自 javap 反汇编 geckolib-forge-1.20.1-4.4.9.jar ===

1) 单位换算（BakedModelFactory$Builtin.constructCube / constructBone）
   bone.pivot      = ( -px,  +py,  +pz )            [像素 -> 已是 block，除 16 由 translate 做]
   bone.rotation   = ( -rx,  -ry,  +rz )  弧度       [注意 z 不取负]
   cube.origin'    = ( -(ox+ow)/16,  oy/16,  oz/16 )
   cube.size'      = (  +w/16,      +h/16,  +d/16 )

2) 渲染递归（GeoRenderer#renderRecursively）
   push
   translateMatrixToBone(poseStack, bone)      // 动画位置轨道
   prepMatrixForBone(poseStack, bone):
        translateToPivotPoint(poseStack, bone)
        rotateMatrixAroundBone(poseStack, bone)
        scaleMatrixForBone(poseStack, bone)
        translateAwayFromPivotPoint(poseStack, bone)
   renderCubesOfBone(poseStack, bone, ...)
   renderChildBones(poseStack, entity, bone, ...)   // 子骨骼继承全部变换
   pop

3) prepMatrixForBone 的实际平移量（★反直觉，字节码原文）
   T(-pivotX, +pivotY, +pivotZ) ... R ... T(+pivotX, -pivotY, -pivotZ)
   净效果等价于标准写法 T(-p) R T(+p)。
   ===> 因此本模块直接按净效果建模：

       M_bone = M_parent @ T(-pivot) @ H(rotation) @ T(+pivot)

4) rotateMatrixAroundBone 的轴序（★关键）
   字节码按 Z -> Y -> X 依次 mulPose（右乘），故

       H = Rz @ Ry @ Rx        （Z 最先作用在向量上，X 最后）

5) cube 顶点（VertexSet）在 cube.origin' 的 8 个角上取，本模块忽略 inflate（本项目恒为 0）。

=== 由此得到的「坐标系铁律」===
  · bone.pivot 与 bone.cubes[].origin 处在**同一个空间** = 父骨骼的局部空间
  · 根骨骼的「父」就是世界原点（实体脚下中心，+y 向上，-z 为正面）
  · rotation 绕**本骨骼自己的 pivot** 旋转，子骨骼继承父的全部变换
  · 当 rotation == 0 时 M = M_parent @ I，pivot 自动抵消 ——
    这正是 dv14 人形（无 rotation）看起来正常、而触手（有 rotation）必然错位的数学原因
"""

import numpy as np


# ============================================================
# 基础矩阵
# ============================================================

def T(x, y, z):
    m = np.eye(4)
    m[0, 3], m[1, 3], m[2, 3] = float(x), float(y), float(z)
    return m


def Rx(d):
    c, s = np.cos(np.radians(d)), np.sin(np.radians(d))
    m = np.eye(4)
    m[1, 1], m[1, 2] = c, -s
    m[2, 1], m[2, 2] = s, c
    return m


def Ry(d):
    c, s = np.cos(np.radians(d)), np.sin(np.radians(d))
    m = np.eye(4)
    m[0, 0], m[0, 2] = c, s
    m[2, 0], m[2, 2] = -s, c
    return m


def Rz(d):
    c, s = np.cos(np.radians(d)), np.sin(np.radians(d))
    m = np.eye(4)
    m[0, 0], m[0, 1] = c, -s
    m[1, 0], m[1, 1] = s, c
    return m


def H(rot):
    """
    骨骼旋转矩阵。rot 是 **JSON 里写的角度**（度，[rx, ry, rz]）。

    GeckoLib 内部把 JSON 的 x/y 取负、z 保持，
    因此这里先做同样的换算，再按 Z->Y->X 的顺序相乘：
        H = Rz @ Ry @ Rx
    """
    rx, ry, rz = rot
    return Rz(rz) @ Ry(-ry) @ Rx(-rx)


# ============================================================
# 从几何 JSON 取数据
# ============================================================

def bone_pivot(b):
    """
    JSON 的 bone.pivot（像素）-> GeckoLib 内部 pivot。

    constructBone 里 updatePivot(-px, +py, +pz)，
    随后 translateToPivotPoint 又做了一次 (x 取负, y/z 不取负) 的平移，
    两次叠加后**净效果等于 pivot 原样**，所以这里直接用原值（像素）。

    另：bone.rotation 由 updateRotation(-rx, -ry, +rz) 换算成弧度，
    本模拟器的 H() 负责同样的换算。
    """
    px, py, pz = b.get("pivot", [0.0, 0.0, 0.0])
    return np.array([px, py, pz], dtype=float)


def bone_rotation(b):
    rot = b.get("rotation")
    return list(rot) if rot else [0.0, 0.0, 0.0]


def cube_origin(c):
    """
    JSON 的 cube.origin（像素）-> GeckoLib 顶点起始（**block** 单位）。

    constructCube: origin' = ( -(ox+ow)/16, oy/16, oz/16 )
    """
    ox, oy, oz = c["origin"]
    w = c["size"][0]
    return np.array([-(ox + w) / 16.0, oy / 16.0, oz / 16.0], dtype=float)


def cube_size(c):
    """constructCube: size' = (w/16, h/16, d/16)，同为 block 单位。"""
    w, h, d = c["size"]
    return np.array([w / 16.0, h / 16.0, d / 16.0], dtype=float)


def cube_corners(c):
    """
    cube 的 8 个角（block 单位，GeckoLib 内部坐标）。

    VertexSet 在 origin' 与 origin'+size' 之间取 8 角，
    origin' 的 x 已换成 -(ox+ow)/16，加上 w/16 后即 -ox/16。
    """
    o = cube_origin(c)
    s = cube_size(c)
    e = o + s
    return [np.array([x, y, z], dtype=float)
            for x in (o[0], e[0])
            for y in (o[1], e[1])
            for z in (o[2], e[2])]


# ============================================================
# 骨骼世界矩阵
# ============================================================

def model_bones(data):
    """把 geo JSON 转成 {name: bone} 字典。"""
    return {b["name"]: b for b in data["minecraft:geometry"][0]["bones"]}


def bone_matrix(bones, name, _cache=None, _depth=0):
    """
    累积世界矩阵（返回 block 单位，GeckoLib 内部坐标）。

    M = M_parent @ T(+pivot) @ H(rotation) @ T(-pivot)      p = JSON pivot / 16

    <p>注意：调用方每次传入自己的 dict 即可；传 None 时内部自建。
    每次调用必须使用**独立的**缓存（不同形态的骨骼表不同，
    跨形态复用旧缓存会拿到错误的矩阵）。
    """
    if _cache is None:
        _cache = {}
    if name in _cache:
        return _cache[name]
    if _depth > 64:
        raise RuntimeError(f"骨骼层级过深或有环：{name}")

    b = bones[name]
    parent = b.get("parent")
    if parent and parent in bones:
        PM = bone_matrix(bones, parent, _cache, _depth + 1)
    else:
        PM = np.eye(4)

    # ------------------------------------------------------------------
    # pivot 在**父骨骼局部空间**
    #
    # PoseStack 的 push/translate/mulPose/pop 是"后乘"语义，
    # 依次执行下面的调用后，栈顶矩阵（相对入栈时）为：
    #
    #   translateToPivotPoint(poseStack, bone)
    #       -> poseStack.translate(-pivotX/16, +pivotY/16, +pivotZ/16)
    #   rotateMatrixAroundBone(poseStack, bone)       -> H
    #   scaleMatrixForBone(poseStack, bone)           -> S（本项目恒为 1）
    #   translateAwayFromPivotPoint(poseStack, bone)
    #       -> poseStack.translate(+pivotX/16, -pivotY/16, -pivotZ/16)
    #
    # bone.pivot 已被 constructBone 存成 (-px, +py, +pz)（像素），
    # 因此两段平移换算到 block 后分别是 T(+px,+py,+pz)/16 与 T(-px,-py,-pz)/16。
    # 于是每骨骼的净变换正好是教科书式的"绕 pivot 旋转"：
    #
    #     M_bone = M_parent @ T(+p) @ H @ T(-p)       p = JSON pivot / 16
    #
    # 当 rotation == 0 时 H = I，上式退化为恒等 ——
    # 这正是 form1 人形（无 rotation）看起来正常的数学原因。
    # ------------------------------------------------------------------
    p = bone_pivot(b) / 16.0
    M = PM @ T(p[0], p[1], p[2]) @ H(bone_rotation(b)) @ T(-p[0], -p[1], -p[2])
    _cache[name] = M
    return M


def bone_world_origin(bones, name, _cache=None):
    """骨骼 pivot 在世界空间的位置（block，实体脚下中心为原点）。"""
    M = bone_matrix(bones, name, _cache)
    v = M @ np.array([0.0, 0.0, 0.0, 1.0])
    return np.array([v[0], v[1], v[2]])


def cube_world_aabb(bones, bname, c, _cache=None):
    """单个 cube 的世界 AABB（block 单位，GeckoLib 内部坐标）。"""
    M = bone_matrix(bones, bname, _cache)
    pts = []
    for corner in cube_corners(c):
        v = M @ np.array([corner[0], corner[1], corner[2], 1.0])
        pts.append([v[0], v[1], v[2]])
    pts = np.array(pts)
    return pts.min(axis=0), pts.max(axis=0)


def _to_px(v):
    """block 坐标（GeckoLib 内部）-> JSON 像素坐标。x 轴取反，见 cube_world_aabb_px。"""
    return np.array([-v[0] * 16.0, v[1] * 16.0, v[2] * 16.0])


def _aabb_from(pts_px):
    """由若干像素坐标点求 min/max（x 取反后需重新求 min/max）。"""
    a = np.array(pts_px)
    return a.min(axis=0), a.max(axis=0)


def cube_world_aabb_px(bones, bname, c, _cache=None):
    """
    单个 cube 的世界 AABB，换算回 **Blockbench/JSON 像素系**，便于和基准表逐值比对。

    <h3>为什么要换算</h3>
    GeckoLib 的模型空间相对 Blockbench 的 JSON 是 **x 镜像**的：
    constructCube 把 origin 的 x 换成 -(ox+ow)，即整条 x 轴取反。
    所以 GeckoLib 世界里 cube 占 `x[-(ox+ow), -ox]`，
    换回 JSON 口径就是 `x[ox, ox+ow]`，与本项目基准表一致。

    <h3>换算方式</h3>
      · x：先取负再乘 16
      · y、z：直接乘 16
    取反后 x 的 min/max 会互换，因此必须**重新求** min/max，
    否则得到的区间是 [max, min]。
    """
    M = bone_matrix(bones, bname, _cache)
    pts = []
    for corner in cube_corners(c):
        v = M @ np.array([corner[0], corner[1], corner[2], 1.0])
        pts.append(_to_px(v[:3]))
    return _aabb_from(pts)


def bone_world_aabb_px(bones, bname, _cache=None):
    """骨骼所有 cube 合并后的世界 AABB（JSON 像素系）。"""
    b = bones[bname]
    lo = np.array([1e9, 1e9, 1e9])
    hi = np.array([-1e9, -1e9, -1e9])
    for c in b.get("cubes", []):
        l, h = cube_world_aabb_px(bones, bname, c, _cache)
        lo = np.minimum(lo, l)
        hi = np.maximum(hi, h)
    return lo, hi


def model_world_aabb_px(bones, _cache=None):
    """整个模型的世界 AABB（JSON 像素系）。"""
    lo = np.array([1e9, 1e9, 1e9])
    hi = np.array([-1e9, -1e9, -1e9])
    for name in bones:
        if not bones[name].get("cubes"):
            continue
        l, h = bone_world_aabb_px(bones, name, _cache)
        lo = np.minimum(lo, l)
        hi = np.maximum(hi, h)
    return lo, hi


def bone_world_aabb(bones, bname, _cache=None):
    """骨骼所有 cube 合并后的世界 AABB。"""
    b = bones[bname]
    lo = np.array([1e9, 1e9, 1e9])
    hi = np.array([-1e9, -1e9, -1e9])
    for c in b.get("cubes", []):
        l, h = cube_world_aabb(bones, bname, c, _cache)
        lo = np.minimum(lo, l)
        hi = np.maximum(hi, h)
    return lo, hi


def model_world_aabb(bones, _cache=None):
    lo = np.array([1e9, 1e9, 1e9])
    hi = np.array([-1e9, -1e9, -1e9])
    for name in bones:
        if not bones[name].get("cubes"):
            continue
        l, h = bone_world_aabb(bones, name, _cache)
        lo = np.minimum(lo, l)
        hi = np.maximum(hi, h)
    return lo, hi


def cube_far_face_center(bones, bname, c, sign=1.0, _cache=None):
    """
    某个 cube 沿**本骨骼局部 z 轴**某一面的几何中心（世界坐标）。

    :param sign: +1 -> 取局部 z 较大的一面（origin_z + d）；-1 -> 取较小的一面（origin_z）
    """
    M = bone_matrix(bones, bname, _cache)
    o = cube_origin(c)
    s = cube_size(c)
    z = (o[2] + s[2]) if sign >= 0 else o[2]
    cx = o[0] + s[0] * 0.5
    cy = o[1] + s[1] * 0.5
    v = M @ np.array([cx, cy, z, 1.0])
    return np.array([v[0], v[1], v[2]])


# ============================================================
# 链式骨骼（触手 / 骨翼）—— dv15 新增，用于根治「断裂、散落」
# ============================================================

def build_chain(bones, seg_names, mount_local_px, profile_deg,
                seg_len, seg_size, axis="x"):
    """
    生成一条**精确首尾相接**的链式骨骼（触手、骨翼）。

    <h3>为什么这样写才对（dv14 翻车的根因）</h3>
    dv14 用「绝对三角累加」预计算每一节的 pivot，同时又给骨骼加 rotation。
    但 GeckoLib 里子骨骼会**继承父骨骼的全部变换**，预计算的绝对坐标被父级
    旋转再变换一次，误差逐节放大 —— 4 节后整条触手飞到 x=-53、y=-35、
    z=+18（实测），画面上就是「散落、断裂、悬空的紫色方块」。

    <h3>正确做法</h3>
    每一节的 pivot 只用**父骨骼局部空间里的一个点**，且该点取「父节的末端面中心」：

        P_{n+1} = 父节 cube 的末端面中心（在父节 local 空间里的坐标）

    因为骨骼变换是 `T(+P) R T(-P)`，把它作用到 `P` 自己身上恒等于 `P`
    （`R` 绕 `P` 转，`P` 原地不动），所以**无论子节怎么旋转，入口永远严丝
    合缝地贴在父节末端**——这正是我们要的「不断裂」性质。

    <h3>角度约定</h3>
    `profile_deg[n]` 是第 n 节的**绝对**倾角，单位为度，含义是
    「从 +z（实体背面）向 +y（上）偏转多少」。
    映射到 JSON 的 `rotation.rx` 为 `rx = 180 - a`；
    写入骨骼时取其**相对父节的增量**（`a[n] - a[n-1]`），
    这样累积后每节的绝对角恰好等于设计值。

    :param bones: 目标 bones 字典（原地写入）
    :param seg_names: 各节骨骼名（顺序即链序）
    :param mount_local_px: 第 0 节 pivot，写在**父骨骼局部空间**（像素）
    :param profile_deg: 各节绝对倾角（度）
    :param seg_len: 各节长度（像素）
    :param seg_size: 各节横截面边长（像素）
    :return: 新增骨骼名列表
    """
    added = []
    pivot = np.array(mount_local_px, dtype=float)
    for i, name in enumerate(seg_names):
        L = float(seg_len[i])
        s = float(seg_size[i])
        a = float(profile_deg[i])
        abs_rx = 180.0 - a
        rel_rx = abs_rx - (180.0 - float(profile_deg[i - 1])) if i > 0 else abs_rx

        # cube 沿本骨骼局部 -z 延伸一个节长，横截面以 pivot 为中心：
        #   占 z ∈ [pivot_z - L, pivot_z]
        cube_org = [pivot[0] - s / 2.0, pivot[1] - s / 2.0, pivot[2] - L]
        bones[name] = {
            "name": name,
            "pivot": [round(float(v), 3) for v in pivot],
            "rotation": [round(rel_rx, 3), 0.0, 0.0],
            "cubes": [{
                "origin": [round(float(v), 3) for v in cube_org],
                "size": [round(s, 3), round(s, 3), round(L, 3)],
                "uv": None,          # 由调用方填充
                "inflate": 0.0,
            }],
            "_parent": None,         # 由调用方填写
        }
        # 下一节的入口 = 本节的末端面中心（同一局部空间里就是 pivot_z - L）
        pivot = np.array([pivot[0], pivot[1], pivot[2] - L])
        added.append(name)
    return added


def verify_chain_joints(bones, chain, tol=0.05, _cache=None):
    """
    校验一条链的每一处接头：**子节入口 == 父节末端面中心**（误差 < tol 像素）。

    这是针对 dv14 根因的直接回归测试 —— 当时该误差高达 10~12 像素。
    返回 [(父, 子, 误差), ...]，只包含失败项。
    """
    bad = []
    for i in range(1, len(chain)):
        p, c = chain[i - 1], chain[i]
        pcube = bones[p]["cubes"][0]
        ccube = bones[c]["cubes"][0]
        # 父节向外末端（局部 z 小的一端）
        tip = cube_far_face_center(bones, p, pcube, -1, _cache)
        # 子节向内起始（局部 z 大的一端）
        base = cube_far_face_center(bones, c, ccube, +1, _cache)
        d = float(np.linalg.norm(tip - base) * 16.0)
        if d > tol:
            bad.append((p, c, round(d, 4)))
    return bad
