#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""批量驱动 StarItem：输入提示词 → 生成 → 全部保存 → 归档。

保存对话框的行为（实测）：
  · 点"全部保存到文件夹"会弹系统文件夹选择框，默认停在**上次用过的目录**
    （用户已手动选过 D:\\MC 贴图临时存放处，所以这里不用再导航）
  · 按钮用合成点击不响应，但 **Enter 有效**
  · 保存后文件名是 `{提示词}_1.png .. _4.png`，同名会直接覆盖
  · 状态栏会写"已保存到 <路径>"，据此判断成功

所以每跑一枚之前必须把目录清空，否则四张会互相覆盖、分不清批次。
"""
import os
import shutil
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import staritem_driver as D

SRC = r'D:\MC 贴图临时存放处'
OUT = r'D:\WorkBuddy\2026-11-01-01-18-09\LostRealm_dev_beta29\tmp_cc0\star_out'

# 契约 ID -> 给模型的英文词
WORDS = [
    ('banner', 'banner'),
    ('skull', 'skull'),
    ('heart', 'heart'),
]


def clear_src():
    for f in os.listdir(SRC):
        p = os.path.join(SRC, f)
        if os.path.isfile(p):
            os.remove(p)


def run_one(word, tag, wait=18.0):
    clear_src()
    D.activate()
    D.click(*D.INPUT)
    time.sleep(0.25)
    D.ctrl(0x41)
    time.sleep(0.15)
    D.type_unicode(word)
    time.sleep(0.35)
    D.click(*D.GEN)
    time.sleep(wait)
    D.click(*D.SAVE_ALL)
    time.sleep(1.6)
    D.tap(0x0D)              # 确认文件夹对话框
    time.sleep(2.2)

    got = [f for f in os.listdir(SRC) if f.lower().endswith('.png')]
    dst = os.path.join(OUT, tag)
    os.makedirs(dst, exist_ok=True)
    for f in got:
        shutil.move(os.path.join(SRC, f), os.path.join(dst, f))
    print('%-8s -> %d 张 (%s)' % (word, len(got), ', '.join(sorted(got))))
    return len(got)


if __name__ == '__main__':
    for tag, word in WORDS:
        run_one(word, tag)
