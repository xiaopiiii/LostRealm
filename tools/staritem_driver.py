#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 StarItem 的窗口当"可编程界面"来驱动。

它用 Rust 的 winit 自绘界面 —— 没有 DOM、没有 HTTP 口、没有 UI Automation 树，
所以唯一的路是：截图看状态、算屏幕坐标、发真实鼠标键盘事件。

坐标约定：本文件里所有 click() 的入参都是**窗口内坐标**，
由 _origin() 每次调用时重新取窗口位置换算成屏幕坐标。
"""
import ctypes
import ctypes.wintypes as wt
import subprocess
import time

u = ctypes.windll.user32
g = ctypes.windll.gdi32

try:
    ctypes.windll.shcore.SetProcessDpiAwareness(2)
except Exception:
    pass

HWND = 132966


def find_window(pid=None, title='StarItem'):
    res = []

    @ctypes.WINFUNCTYPE(ctypes.c_bool, wt.HWND, wt.LPARAM)
    def cb(h, l):
        t = ctypes.create_unicode_buffer(256)
        u.GetWindowTextW(h, t, 256)
        if t.value == title and u.IsWindowVisible(h):
            res.append(h)
        return True

    u.EnumWindows(cb, 0)
    return res[0] if res else None


def origin():
    r = wt.RECT()
    u.GetWindowRect(HWND, ctypes.byref(r))
    return r.left, r.top, r.right - r.left, r.bottom - r.top


def activate():
    u.ShowWindow(HWND, 9)
    u.SetForegroundWindow(HWND)
    time.sleep(0.25)


def click(x, y, double=False):
    ox, oy, _, _ = origin()
    u.SetCursorPos(ox + x, oy + y)
    time.sleep(0.05)
    u.mouse_event(0x0002, 0, 0, 0, 0)
    u.mouse_event(0x0004, 0, 0, 0, 0)
    if double:
        time.sleep(0.05)
        u.mouse_event(0x0002, 0, 0, 0, 0)
        u.mouse_event(0x0004, 0, 0, 0, 0)
    time.sleep(0.15)


def key(vk, up=False):
    u.keybd_event(vk, 0, 0x0002 if up else 0, 0)


def tap(vk):
    key(vk)
    key(vk, True)
    time.sleep(0.03)


def ctrl(vk):
    key(0x11)
    tap(vk)
    key(0x11, True)
    time.sleep(0.1)


class _KEYBDINPUT(ctypes.Structure):
    _fields_ = [('wVk', wt.WORD), ('wScan', wt.WORD), ('dwFlags', wt.DWORD),
                ('time', wt.DWORD), ('dwExtraInfo', ctypes.c_void_p)]


class _MOUSEINPUT(ctypes.Structure):
    _fields_ = [('dx', wt.LONG), ('dy', wt.LONG), ('mouseData', wt.DWORD),
                ('dwFlags', wt.DWORD), ('time', wt.DWORD), ('dwExtraInfo', ctypes.c_void_p)]


class _HARDWAREINPUT(ctypes.Structure):
    _fields_ = [('uMsg', wt.DWORD), ('wParamL', wt.WORD), ('wParamH', wt.WORD)]


class _U(ctypes.Union):
    _fields_ = [('ki', _KEYBDINPUT), ('mi', _MOUSEINPUT), ('hi', _HARDWAREINPUT)]


class _INPUT(ctypes.Structure):
    _fields_ = [('type', wt.DWORD), ('u', _U)]


def type_unicode(text):
    """用 KEYEVENTF_UNICODE 逐字发键。中文也能进，不依赖剪贴板。"""
    for ch in text:
        for flags in (0x0004, 0x0004 | 0x0002):
            inp = _INPUT(type=1)
            inp.u.ki = _KEYBDINPUT(0, ord(ch), flags, 0, None)
            u.SendInput(1, ctypes.byref(inp), ctypes.sizeof(_INPUT))
            time.sleep(0.012)
        time.sleep(0.03)


def set_clipboard(text):
    subprocess.run(['powershell', '-NoProfile', '-Command', 'Set-Clipboard -Value $args[0]',
                    'x', text], capture_output=True, timeout=15)


def shot(path):
    ox, oy, w, h = origin()
    hdc = u.GetDC(0)
    mdc = g.CreateCompatibleDC(hdc)
    bmp = g.CreateCompatibleBitmap(hdc, w, h)
    g.SelectObject(mdc, bmp)
    g.BitBlt(mdc, 0, 0, w, h, hdc, ox, oy, 0x00CC0020)

    class BIH(ctypes.Structure):
        _fields_ = [('biSize', wt.DWORD), ('biWidth', wt.LONG), ('biHeight', wt.LONG),
                    ('biPlanes', wt.WORD), ('biBitCount', wt.WORD), ('biCompression', wt.DWORD),
                    ('biSizeImage', wt.DWORD), ('biXPelsPerMeter', wt.LONG),
                    ('biYPelsPerMeter', wt.LONG), ('biClrUsed', wt.DWORD),
                    ('biClrImportant', wt.DWORD)]

    bi = BIH()
    bi.biSize = ctypes.sizeof(BIH)
    bi.biWidth = w
    bi.biHeight = -h
    bi.biPlanes = 1
    bi.biBitCount = 32
    buf = ctypes.create_string_buffer(w * h * 4)
    g.GetDIBits(mdc, bmp, 0, h, buf, ctypes.byref(bi), 0)
    raw = buf.raw
    rows = [[(raw[(y * w + x) * 4 + 2], raw[(y * w + x) * 4 + 1], raw[(y * w + x) * 4], 255)
             for x in range(w)] for y in range(h)]
    g.DeleteObject(bmp)
    g.DeleteDC(mdc)
    u.ReleaseDC(0, hdc)
    if path:
        import pngtool
        pngtool.write_png(path, rows)
    return rows


def full_shot(path=None, crop=None):
    """全屏截图。crop=(x0,y0,x1,y1) 只截一块，方便看对话框。"""
    W = u.GetSystemMetrics(0)
    H = u.GetSystemMetrics(1)
    hdc = u.GetDC(0)
    mdc = g.CreateCompatibleDC(hdc)
    bmp = g.CreateCompatibleBitmap(hdc, W, H)
    g.SelectObject(mdc, bmp)
    g.BitBlt(mdc, 0, 0, W, H, hdc, 0, 0, 0x00CC0020)

    class BIH(ctypes.Structure):
        _fields_ = [('biSize', wt.DWORD), ('biWidth', wt.LONG), ('biHeight', wt.LONG),
                    ('biPlanes', wt.WORD), ('biBitCount', wt.WORD), ('biCompression', wt.DWORD),
                    ('biSizeImage', wt.DWORD), ('biXPelsPerMeter', wt.LONG),
                    ('biYPelsPerMeter', wt.LONG), ('biClrUsed', wt.DWORD),
                    ('biClrImportant', wt.DWORD)]

    bi = BIH()
    bi.biSize = ctypes.sizeof(BIH)
    bi.biWidth = W
    bi.biHeight = -H
    bi.biPlanes = 1
    bi.biBitCount = 32
    buf = ctypes.create_string_buffer(W * H * 4)
    g.GetDIBits(mdc, bmp, 0, H, buf, ctypes.byref(bi), 0)
    raw = buf.raw
    rows = [[(raw[(y * W + x) * 4 + 2], raw[(y * W + x) * 4 + 1], raw[(y * W + x) * 4], 255)
             for x in range(W)] for y in range(H)]
    g.DeleteObject(bmp)
    g.DeleteDC(mdc)
    u.ReleaseDC(0, hdc)
    if crop:
        x0, y0, x1, y1 = crop
        rows = [r[x0:x1] for r in rows[y0:y1]]
    if path:
        import pngtool
        pngtool.write_png(path, rows)
    return rows


def click_abs(x, y, wait=0.4):
    u.SetCursorPos(x, y)
    time.sleep(0.12)
    u.mouse_event(0x0002, 0, 0, 0, 0)
    time.sleep(0.06)
    u.mouse_event(0x0004, 0, 0, 0, 0)
    time.sleep(wait)


# 界面锚点（窗口内坐标，来自首张截图实测）
INPUT = (300, 225)
GEN = (834, 225)
SAVE_ALL = (800, 452)

if __name__ == '__main__':
    import sys
    import os
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    activate()
    print('origin', origin())
    set_clipboard('金王冠')
    click(*INPUT)
    ctrl(0x41)          # Ctrl+A
    ctrl(0x56)          # Ctrl+V
    time.sleep(0.3)
    click(*GEN)
    print('生成中...')
    time.sleep(12)
    proj = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), '..', '..')
    shot(os.path.join(proj, 'tmp_cc0', 'star_test.png'))
    print('-> tmp_cc0/star_test.png')
