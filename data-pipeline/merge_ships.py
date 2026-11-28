"""Merge ships with EXACTLY the same display name (players and enemies).

Also used to decide which bundled thumbnails are still needed: entries merged
away no longer need an image, and placeholder copies must not be shipped.
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
