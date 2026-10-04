# -*- coding: utf-8 -*-
"""扫描 Java 源码中疑似被截断的字符串字面量。"""
import pathlib
import re

roots = [
    pathlib.Path("src/main/java"),
    pathlib.Path("src/test/java"),
]
# 启发式：行内双引号数量为奇数，或字符串里出现  ?紧邻 )/;/+/:
pat_odd = re.compile(r'^.*$')
for root in roots:
    for p in root.rglob("*.java"):
        text = p.read_text(encoding="utf-8", errors="replace")
        for i, line in enumerate(text.splitlines(), 1):
            # 去掉转义引号后统计
            stripped = line.replace('\\"', '')
            n = stripped.count('"')
            suspicious = (n % 2 == 1) or re.search(r'["\'][^"\']*[\w一-鿿]\?(?=[\)\;\+\,\:])', line)
            if suspicious and 'http' not in line and '://' not in line:
                print(f"{p}:{i}: {line.strip()[:120]}")
