"""Canonical daily-improvement generator.

Runs the wiki-module based generator (per-day, per-ship, with upgrade targets)
and writes `appbuild/app/src/main/assets/EquipImprovement.json`.
The akashi-list.me HTML parser is kept as `equip_improvement_akashi.py` for
cross-checking only (its per-ship weekdays are unreliable).
"""
import json
import os
import runpy

BASE = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\apkwork"
OUT = os.path.join(BASE, "gen", "imp", "EquipImprovement_wiki.json")
ASSETS = os.path.join(BASE, "appbuild", "app", "src", "main", "assets")


def main():
    if not os.path.exists(OUT):
        runpy.run_path(os.path.join(os.path.dirname(__file__), "equip_improvement_wiki.py"),
                       run_name="__main__")
    data = json.load(open(OUT, encoding="utf-8"))
    target = os.path.join(ASSETS, "EquipImprovement.json")
    json.dump(data, open(target, "w", encoding="utf-8"), ensure_ascii=False)
    print("wrote %s (%d entries)" % (target, len(data)))


if __name__ == "__main__":
    main()
