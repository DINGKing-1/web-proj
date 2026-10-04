# -*- coding: utf-8 -*-
"""修复被 PowerShell 编码损坏的 Java 字符串字面量。"""
import pathlib
import re

files = [
    "src/main/java/com/secondhand/mall/service/AdminService.java",
    "src/main/java/com/secondhand/mall/controller/AdminApiController.java",
    "src/main/java/com/secondhand/mall/controller/ExportController.java",
    "src/main/java/com/secondhand/mall/controller/AnnouncementController.java",
    "src/main/java/com/secondhand/mall/controller/CouponController.java",
    "src/main/java/com/secondhand/mall/controller/CategoryController.java",
    "src/main/java/com/secondhand/mall/controller/WithdrawController.java",
]

# 形态：字符串被截断成  "…中文�?  （缺收尾引号）或  "…中文?）
# 先按已知残缺片段整词替换，再做通用补全。
known = [
    ("非法的处置动�?", "非法的处置动作"),
    ("举报不存�?", "举报不存在"),
    ("假闭�?", "假闭环"),
    ("该商品已下�?", "该商品已下架"),
    ("举报已处�?", "举报已处理"),
    ("举报未采�?", "举报未采纳"),
    ("您举报的商品�?", "您举报的商品「"),
    ("感谢反馈�?", "感谢反馈"),
    ("再次反馈�?", "再次反馈"),
    ("用户不存在�?", "用户不存在"),
    ("无管理员权限", "无管理员权限"),
    ("已有一笔待处理", "已有一笔待处理"),
]

for f in files:
    p = pathlib.Path(f)
    t = p.read_text(encoding="utf-8")
    orig = t
    for a, b in known:
        t = t.replace(a, b)
    # 通用：  "....<FFFD>?);  →  "....");
    t = re.sub(r'"([^"\n]*?)\�\?\s*\)', r'"\1")', t)
    t = re.sub(r'"([^"\n]*?)\�\?\s*\+', r'"\1" +', t)
    t = re.sub(r'"([^"\n]*?)\�\?\s*:', r'"\1" :', t)
    t = re.sub(r'"([^"\n]*?)\�\?\s*;', r'"\1";', t)
    # 残余替换符去掉
    t = t.replace("�", "")
    if t != orig:
        p.write_text(t, encoding="utf-8")
        print("fixed", f)
    else:
        print("nochange", f)
