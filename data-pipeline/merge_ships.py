"""Merge ships: exact same names + explicit remodel-to-base merges; drop deleted ids."""
import json
import os

BASE = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\apkwork"
ASSETS = os.path.join(BASE, "appbuild", "app", "src", "main", "assets")

# 改造形态 -> 基础舰娘（按名字）
MERGE_INTO = {
    "吞武里改": "吞武里",
    "吹雪改三护（六式）": "吹雪",
    "吹雪改三护(六式)": "吹雪",
    "凉波改二补": "凉波",
}

# 需要删除的编号（wiki 已移除 / 无效条目）
DELETE_IDS = {2307}


def main():
    path = os.path.join(ASSETS, "Ship.json")
    ships = json.load(open(path, encoding="utf-8"))

    existing = {(s.get("name") or {}).get("zh_cn") or "" for s in ships}
    merged_names = {k for k in MERGE_INTO if k in existing}

    seen = set()
    out = []
    for ship in sorted(ships, key=lambda s: s["id"]):
        if ship["id"] in DELETE_IDS:
            continue
        name = (ship.get("name") or {}).get("zh_cn") or ""
        if name in MERGE_INTO and name in merged_names:
            continue  # 合并进基础形态
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
