"""Merge duplicate ship entries (same display name) and damaged enemy variants ("-坏").

Rules
----
* 深海栖姬：名字去掉结尾的「-坏 / 坏 / -壊 / 壊」后同名者合并，只保留 id 最小的基础形态
* 舰娘：完全相同名字的条目只保留 id 最小的一条
"""
import json
import os
import re

BASE = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\apkwork"
ASSETS = os.path.join(BASE, "appbuild", "app", "src", "main", "assets")

DAMAGED = re.compile(r"[-—–\s]*(?:坏|壊|壞)$")


def base_name(name):
    return DAMAGED.sub("", name or "").strip()


def merge(ships):
    seen = {}
    out = []
    for ship in sorted(ships, key=lambda s: s["id"]):
        name = (ship.get("name") or {}).get("zh_cn") or ""
        key = base_name(name) if ship["id"] >= 1500 else name
        if key in seen:
            continue
        seen[key] = ship["id"]
        out.append(ship)
    return out


def main():
    path = os.path.join(ASSETS, "Ship.json")
    ships = json.load(open(path, encoding="utf-8"))
    merged = merge(ships)
    json.dump(merged, open(path, "w", encoding="utf-8"), ensure_ascii=False)
    players = sum(1 for s in merged if s["id"] < 1500)
    enemies = len(merged) - players
    print("before=%d after=%d (players=%d enemies=%d)" % (
        len(ships), len(merged), players, enemies))


if __name__ == "__main__":
    main()
