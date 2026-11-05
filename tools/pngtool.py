# -*- coding: utf-8 -*-
"""最小 PNG 读写 + 栅格工具（纯 Python，不需要 Pillow）。

只做三件事：读（8bit 非隔行）、写（RGBA）、按块众数降采样。
降采样为什么不用平均：像素画的"像素块"边界必须是硬的，
平均会产生中间色，边缘就糊了，还会把 9 色调色板污染成几十色。
"""
import struct
import zlib


def read_png(path):
    data = open(path, 'rb').read()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', 'not a png: %s' % path
    pos = 8
    idat = bytearray()
    w = h = bitdepth = ctype = None
    while pos < len(data):
        ln = struct.unpack('>I', data[pos:pos + 4])[0]
        typ = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + ln]
        pos += 12 + ln
        if typ == b'IHDR':
            w, h, bitdepth, ctype, _c, _f, inter = struct.unpack('>IIBBBBB', chunk)
            assert bitdepth == 8, 'only 8bit png supported (got %d)' % bitdepth
            assert inter == 0, 'interlaced png not supported'
        elif typ == b'IDAT':
            idat += chunk
        elif typ == b'IEND':
            break
    raw = zlib.decompress(bytes(idat))
    ch = {0: 1, 2: 3, 4: 2, 6: 4}[ctype]
    stride = w * ch
    out = bytearray(h * stride)
    prev = bytearray(stride)
    p = 0
    for y in range(h):
        f = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        if f == 1:
            for i in range(ch, stride):
                line[i] = (line[i] + line[i - ch]) & 0xFF
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif f == 3:
            for i in range(stride):
                a = line[i - ch] if i >= ch else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif f == 4:
            for i in range(stride):
                a = line[i - ch] if i >= ch else 0
                b = prev[i]
                c = prev[i - ch] if i >= ch else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        out[y * stride:(y + 1) * stride] = line
        prev = line
    rows = []
    for y in range(h):
        row = []
        base = y * stride
        for x in range(w):
            o = base + x * ch
            if ch == 4:
                row.append((out[o], out[o + 1], out[o + 2], out[o + 3]))
            elif ch == 3:
                row.append((out[o], out[o + 1], out[o + 2], 255))
            elif ch == 2:
                row.append((out[o], out[o], out[o], out[o + 1]))
            else:
                row.append((out[o], out[o], out[o], 255))
        rows.append(row)
    return rows


def write_png(path, rows):
    h, w = len(rows), len(rows[0])
    raw = bytearray()
    for row in rows:
        raw.append(0)
        for px in row:
            raw.extend((px[0], px[1], px[2], px[3] if len(px) > 3 else 255))

    def chunk(t, d):
        return (struct.pack('>I', len(d)) + t + d
                + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff))

    png = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(bytes(raw), 9))
    png += chunk(b'IEND', b'')
    open(path, 'wb').write(png)


def scale(rows, k):
    """整数倍最近邻放大（预览用，边缘保持硬）。"""
    out = []
    for row in rows:
        big = []
        for px in row:
            big.extend([px] * k)
        for _ in range(k):
            out.append(list(big))
    return out


def downscale_majority(rows, n):
    """任意尺寸 → n×n，每格取覆盖区域内出现最多的颜色（含透明）。"""
    h, w = len(rows), len(rows[0])
    bw, bh = w / n, h / n
    out = []
    for j in range(n):
        y0, y1 = int(j * bh), max(int(j * bh) + 1, int((j + 1) * bh))
        line = []
        for i in range(n):
            x0, x1 = int(i * bw), max(int(i * bw) + 1, int((i + 1) * bw))
            cnt = {}
            for y in range(y0, min(y1, h)):
                for x in range(x0, min(x1, w)):
                    c = rows[y][x]
                    key = (c[0], c[1], c[2], 0 if c[3] < 128 else 255)
                    cnt[key] = cnt.get(key, 0) + 1
            line.append(max(cnt.items(), key=lambda kv: kv[1])[0])
        out.append(line)
    return out


def alpha_bbox(rows):
    """不透明像素的包围盒；全透明返回 None。"""
    h, w = len(rows), len(rows[0])
    x0, y0, x1, y1 = w, h, -1, -1
    for y in range(h):
        for x in range(w):
            if rows[y][x][3] > 8:
                if x < x0: x0 = x
                if y < y0: y0 = y
                if x > x1: x1 = x
                if y > y1: y1 = y
    return None if x1 < 0 else (x0, y0, x1, y1)


def crop_pad(rows, n, pad=1):
    """把图形居中放进 n×n 画布，四周留 pad 像素空边（MC 物品图的惯例）。"""
    h, w = len(rows), len(rows[0])
    bb = alpha_bbox(rows)
    canvas = [[(0, 0, 0, 0)] * n for _ in range(n)]
    if bb is None:
        return canvas
    x0, y0, x1, y1 = bb
    cw, chh = x1 - x0 + 1, y1 - y0 + 1
    avail = n - pad * 2
    k = min(avail / cw, avail / chh)
    tw, th = max(1, int(cw * k)), max(1, int(chh * k))
    ox, oy = (n - tw) // 2, (n - th) // 2
    for j in range(th):
        for i in range(tw):
            sx = x0 + min(cw - 1, int(i / k))
            sy = y0 + min(chh - 1, int(j / k))
            canvas[oy + j][ox + i] = rows[sy][sx]
    return canvas


def palette_size(rows):
    return len({(c[0], c[1], c[2], c[3]) for row in rows for c in row if c[3] > 8})
