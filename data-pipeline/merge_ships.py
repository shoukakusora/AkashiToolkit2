"""Merge ships with EXACTLY the same display name — player ships only.

深海栖姬按 wiki「深海栖舰列表」逐条保留（名字已带 elite / flagship / 改flagship / 后期型
等后缀，属于不同舰船，不再合并）。
"""
import json
import os

BASE = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\apkwork"
ASSETS = os.path.join(BASE, "appbuild", "app", "src", "main", "assets")


def main():
    path = os.path.join(ASSETS, "Ship.json")
    ships = json.load(open(path, encoding="utf-8"))

    seen = set()
    out = []
    for ship in sorted(ships, key=lambda s: s["id"]):
        if ship["id"] >= 1500:
            out.append(ship)
            continue
        name = (ship.get("name") or {}).get("zh_cn") or ""
        if name in seen:
            continue
        seen.add(name)
        out.append(ship)

    json.dump(out, open(path, "w", encoding="utf-8"), ensure_ascii=False)
    players = sum(1 for s in out if s["id"] < 1500)
    enemies = len(out) - players
    print("before=%d after=%d (players=%d enemies=%d)" % (
        len(ships), len(out), players, enemies))


if __name__ == "__main__":
    main()
