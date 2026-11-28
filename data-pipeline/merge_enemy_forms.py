"""Merge same-name enemy ships into one entry with stacked forms.

The merged entry keeps the lowest id, stores the first form as `attr`, the last
as `attr_max`, and every form's attributes (in id order) in `forms`, so the app
can render "132→162→182".
"""
import json
import os

BASE = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\apkwork"
A = os.path.join(BASE, "appbuild", "app", "src", "main", "assets")


# 指定某些编号作为同名组的“主条目”（其余形态并入它的 forms）
PREFERRED = {2308}


def canonical(forms):
    for ship in forms:
        if ship["id"] in PREFERRED:
            return ship
    return forms[0]


def main():
    path = os.path.join(A, "Ship.json")
    ships = json.load(open(path, encoding="utf-8"))

    groups = {}
    order = []
    for ship in ships:
        if ship["id"] >= 1500:
            name = (ship.get("name") or {}).get("zh_cn") or ""
            if name not in groups:
                groups[name] = []
                order.append(name)
            groups[name].append(ship)

    players = [s for s in ships if s["id"] < 1500]
    merged = []
    total_forms = 0
    for name in order:
        forms = sorted(groups[name], key=lambda s: s["id"])
        base = canonical(forms)
        if len(forms) > 1:
            total_forms += len(forms)
            base = json.loads(json.dumps(base))
            base["forms"] = [f.get("attr") or {} for f in forms]
            base["attr"] = forms[0].get("attr") or {}
            base["attr_max"] = forms[-1].get("attr") or {}
        merged.append(base)

    out = sorted(players + merged, key=lambda s: s["id"])
    json.dump(out, open(path, "w", encoding="utf-8"), ensure_ascii=False)
    print("ships=%d (players=%d enemies=%d, merged forms=%d)" % (
        len(out), len(players), len(merged), total_forms))


if __name__ == "__main__":
    main()
