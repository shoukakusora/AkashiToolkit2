"""Merge externally supplied development formulas into Development.json.

Put lines in `gen/dev_extra.txt`, one formula per block:

    20/240/250/251 | 目标：46cm三连装炮 | 备注：低消耗通用公式

or simply:

    20/240/250/251
    目标：46cm三连装炮
    备注：低消耗通用公式

The script dedupes by (fuel, ammo, steel, bauxite, target) and rewrites both
`gen/out_assets/Development.json` and `appbuild/.../assets/Development.json`.
"""
import json
import os
import re

BASE = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\apkwork"
SRC = os.path.join(BASE, "gen", "dev_extra.txt")
OUT = os.path.join(BASE, "gen", "out_assets", "Development.json")
ASSETS = os.path.join(BASE, "appbuild", "app", "src", "main", "assets", "Development.json")

FORMULA = re.compile(r"(\d{1,4})\s*/\s*(\d{1,4})\s*/\s*(\d{1,4})\s*/\s*(\d{1,4})")


def parse_extra(text):
    entries = []
    current = None
    for raw in text.split("\n"):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue

        m = FORMULA.search(line)
        if m and "|" not in line:
            if current and current.get("_has_formula"):
                entries.append(current)
            current = {
                "fuel": int(m.group(1)),
                "ammo": int(m.group(2)),
                "steel": int(m.group(3)),
                "bauxite": int(m.group(4)),
                "target": "",
                "result": "",
                "extra": "",
                "extraResult": "",
                "ship": "",
                "_has_formula": True,
            }
            continue

        if "|" in line and FORMULA.search(line.split("|")[0]):
            parts = [p.strip() for p in line.split("|")]
            fm = FORMULA.search(parts[0])
            if not fm:
                continue
            entries.append({
                "fuel": int(fm.group(1)),
                "ammo": int(fm.group(2)),
                "steel": int(fm.group(3)),
                "bauxite": int(fm.group(4)),
                "target": parts[1] if len(parts) > 1 else "",
                "result": parts[2] if len(parts) > 2 else "",
                "extra": parts[3] if len(parts) > 3 else "",
                "extraResult": parts[4] if len(parts) > 4 else "",
                "ship": "",
                "_has_formula": True,
            })
            continue

        if current is not None:
            key = line.split("：")[0]
            value = line.split("：", 1)[1] if "：" in line else line
            if key.startswith("目标"):
                current["target"] = value
            elif key.startswith("产物") or key.startswith("结果"):
                current["result"] = value
            elif key.startswith("备注") or key.startswith("说明"):
                current["extra"] = value

    if current and current.get("_has_formula"):
        entries.append(current)
    return entries


def main():
    if not os.path.exists(SRC):
        print("missing", SRC)
        return
    extra = parse_extra(open(SRC, encoding="utf-8").read())

    base = json.load(open(OUT, encoding="utf-8")) if os.path.exists(OUT) else []
    seen = {(e["fuel"], e["ammo"], e["steel"], e["bauxite"], e.get("target", "")) for e in base}

    added = 0
    for item in extra:
        item.pop("_has_formula", None)
        key = (item["fuel"], item["ammo"], item["steel"], item["bauxite"], item.get("target", ""))
        if key in seen:
            continue
        seen.add(key)
        base.append(item)
        added += 1

    for path in (OUT, ASSETS):
        json.dump(base, open(path, "w", encoding="utf-8"), ensure_ascii=False)

    print("extra parsed: %d, added: %d, total: %d" % (len(extra), added, len(base)))


if __name__ == "__main__":
    main()
