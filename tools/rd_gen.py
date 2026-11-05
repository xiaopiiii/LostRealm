#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""用 Retro Diffusion 生成「职业勋章」物品图标（原生像素，非缩放）。

用法：
    export RD_KEY=rdpk-xxxx
    python tools/rd_gen.py --check              # 只算成本，不出图
    python tools/rd_gen.py --only blade          # 出某一个
    python tools/rd_gen.py --all                 # 出全部五个
    python tools/rd_gen.py --all --num 2         # 每个出两版

为什么走 `rd_fast__mc_item`：这是 Retro Diffusion 官方为 Minecraft 物品图
准备的风格，原生尺寸范围 16×16 ~ 128×128，32×32 是它的舒适区。
它不是"高分辨率图加像素滤镜"——输出本身就落在 32×32 栅格上、调色板受限。

落盘策略：拿到图后不直接写进材质目录，先落到 `tmp_rd/out/`，
再由 `import_icons.py` 统一裁边/居中/校验，确认没问题才覆盖。
"""
import argparse
import base64
import json
import os
import sys
import time
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pngtool

API = 'https://api.retrodiffusion.ai/v2/inferences'
STYLE = 'rd_fast__mc_item'
SIZE = 32

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, 'tmp_rd', 'out')

# ---------------------------------------------------------------- 五个职业
# 刻意共用一套「圆形金属勋章 + 中央徽记 + 特定主色」的骨架：
# 同一个模组里的五枚勋章必须一眼看出是一套，只靠颜色和徽记区分。
COMMON = ('game inventory icon, single object, perfectly centered, '
          'flat front view, thick dark outline, limited palette, '
          'clean readable silhouette, transparent background')

MEDALS = {
    'archer': (
        'a round silver medallion with a jade green enamel face, '
        'an engraved bow with a nocked arrow as the central emblem, '
        'small emerald gem at the top rim, ' + COMMON),
    'blade': (
        'a round silver medallion with a crimson red enamel face, '
        'two crossed curved blades as the central emblem, '
        'small ruby gem at the top rim, ' + COMMON),
    'tank': (
        'a round steel medallion with a slate blue enamel face, '
        'a tower shield with a riveted iron boss as the central emblem, '
        'small sapphire gem at the top rim, ' + COMMON),
    'mage': (
        'a round silver medallion with a deep violet enamel face, '
        'a wizard staff topped with a floating purple crystal as the central emblem, '
        'small amethyst gem at the top rim, ' + COMMON),
    'summoner': (
        'a round dark iron medallion with a teal green enamel face, '
        'a horned skull with a wisp of soul fire as the central emblem, '
        'small emerald gem at the top rim, ' + COMMON),
}


def key():
    k = os.environ.get('RD_KEY', '').strip()
    if not k:
        sys.exit('缺少 RD_KEY 环境变量')
    return k


def _retry(fn, tries=4, pause=3.0):
    """代理偶尔会直接掐连接（RemoteDisconnected），这里统一重试。"""
    last = None
    for i in range(tries):
        try:
            return fn()
        except Exception as e:                      # noqa: BLE001
            last = e
            if i == tries - 1:
                break
            print(f'    (网络抖动 {type(e).__name__}，{pause:.0f}s 后重试 {i + 1}/{tries - 1})')
            time.sleep(pause)
    raise last


def post_json(url, payload, timeout=90):
    body = json.dumps(payload).encode()

    def call():
        req = urllib.request.Request(url, data=body, method='POST', headers={
            'X-RD-Token': key(),
            'Content-Type': 'application/json',
        })
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return json.loads(r.read().decode())

    return _retry(call)


def get_json(url, timeout=60):
    def call():
        req = urllib.request.Request(url, headers={'X-RD-Token': key()})
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return json.loads(r.read().decode())

    return _retry(call)


def base_payload(prompt, num):
    return {
        'prompt': prompt,
        'prompt_style': STYLE,
        'width': SIZE,
        'height': SIZE,
        'num_images': num,
        'remove_bg': True,
        'async': True,
    }


def check_cost(names, num):
    total = 0.0
    for n in names:
        d = post_json(API, dict(base_payload(MEDALS[n], num), check_cost=True))
        cost = d.get('balance_cost', 0.0)
        total += cost
        print(f"  {n:9s} 单价 ${d.get('balance_cost_if_balance', 0):.3f} × {num} = ${cost:.3f}"
              f"   余额可用={d.get('would_use')}  credit=剩余{d.get('remaining_credits')}"
              f"  余额={d.get('remaining_balance')}")
    print(f"合计 ${total:.3f}")


def wait(task_id, timeout=240):
    url = API.replace('/inferences', f'/inferences/tasks/{task_id}')
    deadline = time.time() + timeout
    while time.time() < deadline:
        d = get_json(url)
        st = d.get('status')
        if st and st not in ('accepted', 'processing', 'queued', 'running'):
            return d
        time.sleep(3)
    return {'status': 'timeout'}


def collect_images(d):
    """任务完成后图片可能在 base64_images / output_images / output_urls 里。"""
    out = []
    for item in d.get('base64_images') or []:
        if isinstance(item, str) and item.startswith('data:'):
            item = item.split(',', 1)[-1]
        if isinstance(item, str) and len(item) > 200:
            out.append(('b64', item))
    for item in d.get('output_images') or []:
        if isinstance(item, str) and len(item) > 200:
            out.append(('b64', item))
        elif isinstance(item, dict):
            for k in ('base64', 'b64', 'data', 'url'):
                if item.get(k):
                    out.append(('b64' if k != 'url' else 'url', item[k]))
                    break
    for u in d.get('output_urls') or []:
        out.append(('url', u))
    return out


def gen(name, num):
    payload = base_payload(MEDALS[name], num)
    r = post_json(API, payload)
    if r.get('error'):
        print(f"  {name}: 提交失败 {r}")
        return
    tid = r.get('task_id') or r.get('request_id')
    print(f"  {name}: 已提交 {tid}")
    d = wait(tid)
    if d.get('status') != 'completed':
        print(f"  {name}: 失败 status={d.get('status')} {str(d.get('error'))[:160]}")
        return
    imgs = collect_images(d)
    os.makedirs(OUT_DIR, exist_ok=True)
    for i, (kind, data) in enumerate(imgs):
        path = os.path.join(OUT_DIR, f'{name}_{i}.png')
        if kind == 'b64':
            open(path, 'wb').write(base64.b64decode(data))
        else:
            with urllib.request.urlopen(data, timeout=60) as resp:
                open(path, 'wb').write(resp.read())
        rows = pngtool.read_png(path)
        print(f"  {name}[{i}]: {len(rows[0])}x{len(rows)} 色数={pngtool.palette_size(rows)} -> {path}")
    print(f"  {name}: 成本 ${d.get('balance_cost', 0):.3f}  余额 ${d.get('remaining_balance', '?')}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--only', default='')
    ap.add_argument('--all', action='store_true')
    ap.add_argument('--check', action='store_true')
    ap.add_argument('--num', type=int, default=1)
    a = ap.parse_args()

    names = list(MEDALS) if a.all or not a.only else [s.strip() for s in a.only.split(',')]
    bad = [n for n in names if n not in MEDALS]
    if bad:
        sys.exit(f'未知职业: {bad}  可选: {list(MEDALS)}')

    print(f"模型风格 {STYLE} / {SIZE}x{SIZE} / 每个 {a.num} 张 / 目标 {names}")
    if a.check:
        check_cost(names, a.num)
        return
    for n in names:
        gen(n, a.num)


if __name__ == '__main__':
    main()
