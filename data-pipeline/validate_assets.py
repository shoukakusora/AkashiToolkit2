"""Validate packaged assets before building; exits non-zero on structural errors."""
import json
import os
import sys

BASE = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\apkwork"
ASSETS = os.path.join(BASE, "appbuild", "app", "src", "main", "assets")

errors = []


def load(name):
    p = os.path.join(ASSETS, name)
    if not os.path.exists(p):
        errors.append("missing " + name)
        return []
    return json.load(open(p, encoding="utf-8"))


def check_equip():
    equips = load("Equip.json")
    ids = set()
    for e in equips:
        if e["id"] in ids:
            errors.append("duplicate equip id %s" % e["id"])
        ids.add(e["id"])
        t = e.get("type")
        if not isinstance(t, list) or len(t) < 5:
            errors.append("equip %s type len=%s" % (e["id"], 0 if not t else len(t)))
        for k in ("broken",):
            if not isinstance(e.get(k), list) or len(e[k]) != 4:
                errors.append("equip %s %s invalid" % (e["id"], k))
        for imp in e.get("improvements") or []:
            if len(imp.get("cost") or []) != 4:
                errors.append("equip %s cost len" % e["id"])
            if len(imp.get("ship") or []) != 7:
                errors.append("equip %s ship len" % e["id"])
            if len(imp.get("upgrade") or []) != 2:
                errors.append("equip %s upgrade len" % e["id"])
    print("equips:", len(equips))


def check_ship():
    ships = load("Ship.json")
    ids = set()
    for s in ships:
        if s["id"] in ids:
            errors.append("duplicate ship id %s" % s["id"])
        ids.add(s["id"])
        for k, n in (("broken", 4), ("power_up", 4), ("consume", 2)):
            if not isinstance(s.get(k), list) or len(s[k]) != n:
                errors.append("ship %s %s len" % (s["id"], k))
        eq = s.get("equip") or {}
        if not isinstance(eq.get("id"), list) or not isinstance(eq.get("space"), list):
            errors.append("ship %s equip arrays" % s["id"])
        if s.get("stype") is None:
            errors.append("ship %s stype null" % s["id"])
    print("ships:", len(ships))


def check_quest():
    quests = load("Quest.json")
    for q in quests:
        r = q.get("reward") or {}
        res = r.get("resource")
        if not isinstance(res, list) or len(res) != 4:
            errors.append("quest %s resource len" % q.get("code"))
        for e in r.get("entries") or []:
            if e.get("type") not in ("text", "equip", "item", "ship"):
                errors.append("quest %s bad entry type %s" % (q.get("code"), e.get("type")))
    print("quests:", len(quests))


def check_map():
    maps = load("Map.json")
    for m in maps:
        n = len(m.get("airforce") or [])
        if n == 0:
            errors.append("map %s no airforce" % m.get("map"))
        for k in ("recommend", "condition", "other"):
            if len(m.get(k) or []) != n:
                errors.append("map %s %s parallel" % (m.get("map"), k))
    details = load("MapDetail.json")
    ids = set()
    for d in details:
        if d["id"] in ids:
            errors.append("duplicate map detail id %s" % d["id"])
        ids.add(d["id"])
        for p in d.get("points") or []:
            for f in p.get("fleets") or []:
                if len(f.get("ships") or []) != 6:
                    errors.append("mapdetail %s fleet len" % d["id"])
    print("maps:", len(maps), "mapDetails:", len(details))


def check_others():
    for name in ("Expedition.json", "Item.json", "ShipClass.json", "ShipType.json",
                 "EquipType.json", "EquipTypeParent.json", "ExtraIllustration.json",
                 "Home.json", "Event.json"):
        data = load(name)
        print("%-26s %d" % (name, len(data)))


if __name__ == "__main__":
    check_equip()
    check_ship()
    check_quest()
    check_map()
    check_others()
    if errors:
        print("\nVALIDATION FAILED (%d)" % len(errors))
        for e in errors[:25]:
            print("  -", e)
        sys.exit(1)
    print("\nVALIDATION OK")
