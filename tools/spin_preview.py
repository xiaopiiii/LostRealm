#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把已生成的 GeckoLib 模型转成「可拖动旋转」的单文件 HTML + 转盘 GIF。

做法：用 tools/render_preview.py 这台**离线 z-buffer 光栅器**绕 y 轴连拍 N 帧，
再把这些帧塞进一个 HTML（拖动切换帧）。
选这条路而不是网页里现搭 three.js，是因为这样渲出来的一帧和**游戏内像素级一致**：
同一套几何、同一张贴图、同一套盒展开 UV —— three.js 里重写一遍必然有偏差。
"""
import base64
import io
import os
import sys

sys.path.insert(0, '/workspace/medals-mod/tools')

from PIL import Image

import render_preview as RP

N_FRAMES = 24
SCALE = 30.0
PAD = 14
OUT_GIF = "/workspace/scythe_turntable.gif"
OUT_HTML = "/workspace/scythe_viewer.html"
BOX = (380, 480)          # HTML 帧尺寸


TEX_BY_GEO = {
    "warden_form1": ("warden.png", "warden_glowmask.png"),
    "warden_form2": ("warden_phase2.png", "warden_phase2_glowmask.png"),
    "warden_form3": ("warden_phase3.png", "warden_phase3_glowmask.png"),
}


def shoot(geo, yaw, bounds=None):
    _t, _g = TEX_BY_GEO.get(geo, ("warden.png", "warden_glowmask.png"))
    return RP.render(geo, _t, _g, "iso",
                     scale=SCALE, pad=PAD, yaw=yaw, bounds=bounds)


def main():
    geo = sys.argv[1] if len(sys.argv) > 1 else "warden_form1"
    title = sys.argv[2] if len(sys.argv) > 2 else "遗落之主 · 巨镰"

    # ① 先扫一遍求并集取景框，避免逐帧缩放/位移导致动画抖动
    minx = miny = 1e9
    maxx = maxy = -1e9
    for i in range(N_FRAMES):
        _, b = shoot(geo, i * 360.0 / N_FRAMES)
        minx, maxx = min(minx, b[0]), max(maxx, b[1])
        miny, maxy = min(miny, b[2]), max(maxy, b[3])
    bounds = (minx, maxx, miny, maxy)
    print(f"  {geo} 统一取景框 x[{minx:.1f},{maxx:.1f}] y[{miny:.1f},{maxy:.1f}]")

    frames = []
    for i in range(N_FRAMES):
        im, _ = shoot(geo, i * 360.0 / N_FRAMES, bounds)
        frames.append(im)
    print(f"  渲染 {N_FRAMES} 帧 -> {frames[0].size}")

    # ② 转盘 GIF
    frames[0].save(OUT_GIF, save_all=True, append_images=frames[1:],
                   duration=110, loop=0, disposal=2, optimize=True)
    print(f"  GIF: {OUT_GIF}  {os.path.getsize(OUT_GIF)//1024} KB")

    # ③ 单文件 HTML（拖动旋转）
    data = []
    for f in frames:
        thumb = f.convert("RGB").resize(BOX, Image.LANCZOS)
        buf = io.BytesIO()
        thumb.save(buf, "JPEG", quality=82)
        data.append("data:image/jpeg;base64," + base64.b64encode(buf.getvalue()).decode())

    html = HTML_TMPL.replace("__TITLE__", title).replace(
        "__FRAMES__", ",\n".join('"%s"' % d for d in data))
    with open(OUT_HTML, "w", encoding="utf-8") as fh:
        fh.write(html)
    print(f"  HTML: {OUT_HTML}  {os.path.getsize(OUT_HTML)//1024} KB")


HTML_TMPL = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>__TITLE__</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; }
  body {
    margin: 0; min-height: 100vh; display: flex; flex-direction: column;
    align-items: center; justify-content: center; gap: 14px;
    background: radial-gradient(circle at 50% 30%, #1b1a24 0%, #0c0b10 70%);
    font-family: system-ui, -apple-system, "PingFang SC", "Microsoft YaHei", sans-serif;
    color: #cfc7bd; user-select: none; -webkit-user-select: none;
  }
  h1 { font-size: 17px; font-weight: 600; letter-spacing: .14em; margin: 0; color: #d8b46a; }
  .hint { font-size: 12px; letter-spacing: .08em; color: #7d7568; }
  .stage {
    width: min(88vw, 460px); aspect-ratio: 380 / 480; position: relative;
    cursor: grab; border-radius: 14px;
    background: radial-gradient(circle at 50% 45%, #24222e 0%, #131218 75%);
    box-shadow: 0 24px 60px rgba(0,0,0,.6), inset 0 0 0 1px rgba(190,160,110,.12);
    touch-action: none;
  }
  .stage:active { cursor: grabbing; }
  .stage img { width: 100%; height: 100%; object-fit: contain; display: block;
               border-radius: 14px; pointer-events: none; }
  .bar { display: flex; align-items: center; gap: 10px; }
  button {
    background: #201d28; color: #cfc7bd; border: 1px solid #3a3546;
    border-radius: 8px; padding: 6px 14px; font-size: 12px; cursor: pointer;
    letter-spacing: .08em;
  }
  button:hover { border-color: #6b5a34; color: #e2c179; }
  .badge { font-size: 11px; color: #6b6559; letter-spacing: .06em; }
</style>
</head>
<body>
  <h1>__TITLE__</h1>
  <div class="stage" id="stage"><img id="view" alt="3D 预览"></div>
  <div class="bar">
    <button id="toggle">暂停自转</button>
    <span class="badge" id="badge">拖动画面可自由旋转</span>
  </div>
  <div class="hint">逐帧预渲染 · 与游戏内贴图/几何完全一致</div>
<script>
  const FRAMES = [
__FRAMES__
  ];
  const N = FRAMES.length;
  const view = document.getElementById('view');
  const stage = document.getElementById('stage');
  const badge = document.getElementById('badge');
  const toggle = document.getElementById('toggle');
  let idx = 0, dragging = false, lastX = 0, auto = true;

  const PER_FRAME = 360 / N;
  let acc = 0;

  function show(i) {
    idx = ((i % N) + N) % N;
    view.src = FRAMES[idx];
  }
  function toFrame(dx) {
    return dx / (stage.clientWidth || 380) * N;
  }

  stage.addEventListener('pointerdown', e => {
    dragging = true; lastX = e.clientX; acc = 0;
    stage.setPointerCapture(e.pointerId);
    badge.textContent = '松手后可继续自转';
  });
  stage.addEventListener('pointermove', e => {
    if (!dragging) return;
    acc += toFrame(e.clientX - lastX);
    lastX = e.clientX;
    const step = Math.trunc(acc);
    if (step !== 0) { acc -= step; show(idx - step); }
  });
  const stop = e => {
    if (!dragging) return;
    dragging = false;
    try { stage.releasePointerCapture(e.pointerId); } catch (_) {}
    badge.textContent = '拖动画面可自由旋转';
  };
  stage.addEventListener('pointerup', stop);
  stage.addEventListener('pointercancel', stop);

  toggle.addEventListener('click', () => {
    auto = !auto;
    toggle.textContent = auto ? '暂停自转' : '继续自转';
  });

  let t0 = performance.now();
  function loop(t) {
    if (auto && !dragging) {
      if (t - t0 > 90) {
        t0 = t;
        show(idx + 1);
        stage.style.transform = 'rotate(0deg)';
      }
    }
    requestAnimationFrame(loop);
  }
  show(0);
  requestAnimationFrame(loop);
</script>
</body>
</html>
"""

if __name__ == "__main__":
    main()
