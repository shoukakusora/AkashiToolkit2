"""Merge ships with EXACTLY the same display name (only).

Latest rule from the user:
  * 仅合并同名条目（名字完全一致）；
  * 「-坏」中破形态不再合并，保留为独立条目；
  * 「量产型」「初期型」「后期型」「flagship」「夏季上陆模式」「假日模式」「夏日模式」
    「（哨戒机配备）」「登陆中」「Ⅱ」「Ⅳ」等限定后缀都视为不同舰船，不合并
    （它们名字本来就不同，自然不会被合并）。
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
