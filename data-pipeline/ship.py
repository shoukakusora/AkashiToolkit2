"""Generate Ship.json / ShipClass.json / ShipType.json from zh.kcwiki.cn + kcwiki API."""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from lua import find_table
import zhconv

BASE = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\apkwork"
CACHE = os.path.join(BASE, "gen", "cache")
PROPS = os.path.join(CACHE, "properties")
OUT = os.path.join(BASE, "gen", "out_assets")
OLD = os.path.join(BASE, "assets")

STAT_MAP = {
    "耐久": "hp",
    "火力": "fire",
    "雷装": "torpedo",
    "对空": "aa",
    "装甲": "armor",
    "对潜": "asw",
    "回避": "evasion",
    "索敌": "los",
    "运": "luck",
}
ATTR_KEYS = ["range", "speed", "aa", "armor", "asw", "evasion", "fire", "hp", "luck", "los", "torpedo", "bomb", "accuracy"]

try:
    from pykakasi import kakasi
    _kak = kakasi()
except Exception:  # noqa: BLE001
    _kak = None

try:
    from pypinyin import lazy_pinyin
except Exception:  # noqa: BLE001
    lazy_pinyin = None


def to_int(v, default=0):
    if isinstance(v, bool):
        return int(v)
    if isinstance(v, (int, float)):
        return int(v)
    if v is None:
        return default
    m = re.search(r"-?\d+", str(v))
    return int(m.group(0)) if m else default


def read(p):
    with open(p, encoding="utf-8") as f:
        return f.read()


def romaji(kana):
    if not kana:
        return ""
    import kana as kanalib
    return kanalib.title_case(kanalib.to_romaji(kana))


def pinyin_of(text):
    if not text or lazy_pinyin is None:
        return ""
    try:
        return "".join(lazy_pinyin(text)).lower()
    except Exception:  # noqa: BLE001
        return ""


def pair(v):
    if isinstance(v, list) and len(v) >= 2:
        return to_int(v[0]), to_int(v[1])
    if isinstance(v, list) and len(v) == 1:
        return to_int(v[0]), to_int(v[0])
    if isinstance(v, (int, float)):
        return int(v), int(v)
    return 0, 0


def main():
    os.makedirs(OUT, exist_ok=True)
    lua = read(os.path.join(CACHE, "ship_module.lua"))
    tb = find_table(lua, "shipDataTb")
    start2 = json.load(open(os.path.join(CACHE, "api_start2.json"), encoding="utf-8"))
    detail = json.load(open(os.path.join(CACHE, "ships_detail.json"), encoding="utf-8"))

    s2_by_id = {s["api_id"]: s for s in start2.get("api_mst_ship", [])}
    det_by_id = {}
    for d in detail:
        det_by_id[d["id"]] = d

    by_game_id = {}
    for key, v in tb.items():
        if not isinstance(v, dict):
            continue
        gid = to_int(v.get("ID"))
        if gid:
            by_game_id[gid] = v

    wiki_to_id = {}
    for d in detail:
        if d.get("wiki_id"):
            wiki_to_id[str(d["wiki_id"]).lower()] = d["id"]

    old = json.load(open(os.path.join(OLD, "Ship.json"), encoding="utf-8"))
    old_by_id = {s["id"]: s for s in old}
    old_class = json.load(open(os.path.join(OLD, "ShipClass.json"), encoding="utf-8"))

    ships = []
    classes = {}
    missing_s2 = 0
    for gid, v in sorted(by_game_id.items()):
        name_cn = (v.get("中文名") or "").strip()
        name_ja = (v.get("日文名") or "").strip()
        kana = (v.get("假名") or "").strip()
        if not name_cn and not name_ja:
            continue
        s2 = s2_by_id.get(gid)
        d = det_by_id.get(gid)
        if s2 is None:
            missing_s2 += 1
        ctype = to_int(s2.get("api_ctype")) if s2 else 0
        # cnum 由后面统一按舰级重算（接口里的 cnum 对部分舰娘是全局序号，不能用）
        if not ctype and d:
            ctype = to_int(d.get("ctype"))
        stype = to_int(v.get("舰种")) or (to_int(s2.get("api_stype")) if s2 else 0)

        data = v.get("数据") if isinstance(v.get("数据"), dict) else {}
        attr = {k: 0 for k in ATTR_KEYS}
        attr_max = {k: 0 for k in ATTR_KEYS}
        for cn, en in STAT_MAP.items():
            lo, hi = pair(data.get(cn))
            attr[en] = lo
            attr_max[en] = max(0, hi - lo)
        attr["range"] = to_int(data.get("射程"))
        attr["speed"] = to_int(data.get("速力"))
        rarity = to_int(data.get("稀有"))
        if not rarity:
            rarity = to_int((old_by_id.get(gid) or {}).get("rarity"))

        eq = v.get("装备") if isinstance(v.get("装备"), dict) else {}
        slots = to_int(eq.get("格数"))
        space = eq.get("搭载") if isinstance(eq.get("搭载"), list) else []
        space = [to_int(x) for x in space][:max(slots, len(space))]
        eq_ids = eq.get("初期装备") if isinstance(eq.get("初期装备"), list) else []
        eq_ids = [to_int(x) for x in eq_ids][:max(slots, len(eq_ids))]
        eq_ids = [0 if x < 0 else x for x in eq_ids]
        if len(space) < slots:
            space += [0] * (slots - len(space))
        if len(eq_ids) < slots:
            eq_ids += [0] * (slots - len(eq_ids))

        rm = v.get("改造") if isinstance(v.get("改造"), dict) else {}
        blue_raw = rm.get("图纸")
        blueprint = bool(isinstance(blue_raw, str) and "改装设计图" in blue_raw)
        catapult = bool(isinstance(blue_raw, str) and "弹射器" in blue_raw)
        from_wiki = str(rm.get("改造前", "")).lower()
        to_wiki = str(rm.get("改造后", "")).lower()
        from_id = 0 if from_wiki in ("-1", "", "none") else wiki_to_id.get(from_wiki, 0)
        to_id = 0 if to_wiki in ("-1", "", "none") else wiki_to_id.get(to_wiki, 0)
        if not to_id and d:
            to_id = to_int(d.get("after_ship_id"))
        if not from_id and d:
            from_id = 0
        level = to_int(rm.get("等级"))
        if not level and d:
            level = to_int(d.get("after_lv"))
        cost = [to_int(rm.get("弹药")), to_int(rm.get("钢材"))]
        consume = v.get("消耗") if isinstance(v.get("消耗"), dict) else {}
        broken = v.get("解体") if isinstance(v.get("解体"), dict) else {}
        pu = v.get("改修") if isinstance(v.get("改修"), dict) else {}
        get = v.get("获得") if isinstance(v.get("获得"), dict) else {}

        en = romaji(kana)
        name_for_search = ",".join([x for x in [name_cn, kana, en, name_cn, pinyin_of(name_cn)] if x])
        entry = {
            "id": gid,
            "wiki_id": (str(d.get("wiki_id")).strip() if d and d.get("wiki_id") else "%03d" % to_int(v.get("图鉴号"))),
            "name": {
                "zh_cn": name_cn,
                "zh_tw": zhconv.to_tc(name_cn, PROPS),
                "ja": name_ja,
                "en": en,
            },
            "stype": stype,
            "ctype": ctype,
            "cnum": 1,  # 统一在写文件前按舰级重算
            "rarity": rarity,
            "name_for_search": name_for_search,
            "build_time": to_int(get.get("时间")),
            "broken": [to_int(broken.get("燃料")), to_int(broken.get("弹药")), to_int(broken.get("钢材")), to_int(broken.get("铝"))],
            "power_up": [to_int(pu.get("火力")), to_int(pu.get("雷装")), to_int(pu.get("对空")), to_int(pu.get("装甲"))],
            "consume": [to_int(consume.get("燃料")), to_int(consume.get("弹药"))],
            "painter": (v.get("画师") or "").strip(),
            "cv": (v.get("声优") or "").strip(),
            "equip": {"slots": slots, "id": eq_ids, "space": space},
            "remodel": {"blueprint": blueprint, "catapult": catapult, "from_id": from_id,
                        "to_id": to_id, "level": level, "cost": cost},
            "attr": attr,
            "attr_max": attr_max,
            "attr_99": {k: 0 for k in ATTR_KEYS},
            "get": {
                "drop": to_int(get.get("掉落")),
                "remodel": to_int(get.get("改造")),
                "build": to_int(get.get("建造")),
                "build_time": to_int(get.get("时间")),
            },
        }
        ships.append(entry)

        cls_name = None
        lvl = v.get("级别")
        if isinstance(lvl, list) and lvl:
            cls_name = str(lvl[0]).strip()
        if ctype and cls_name:
            classes.setdefault(ctype, cls_name.replace("型", "级"))

    # 统一重算舰级内序号：同 ctype 的基础舰按图鉴号排序编号，改造舰继承其基础舰
    by_id = {s["id"]: s for s in ships}
    base_cnum = {}
    for ct in sorted({s["ctype"] for s in ships if s["ctype"]}):
        group = [s for s in ships if s["ctype"] == ct and s["remodel"]["from_id"] == 0]
        group.sort(key=lambda x: (len(str(x.get("wiki_id", ""))), str(x.get("wiki_id", "")), x["id"]))
        for i, s in enumerate(group, 1):
            base_cnum[s["id"]] = i
    for s in ships:
        cur = s
        seen = set()
        while cur and cur["remodel"]["from_id"] and cur["id"] not in seen:
            seen.add(cur["id"])
            nxt = by_id.get(cur["remodel"]["from_id"])
            if nxt is None:
                break
            cur = nxt
        s["cnum"] = base_cnum.get(cur["id"], 1) if cur else 1

    ships.sort(key=lambda s: s["id"])
    json.dump(ships, open(os.path.join(OUT, "Ship.json"), "w", encoding="utf-8"), ensure_ascii=False)

    # ShipClass.json : {name, id}
    class_list = []
    for cid, name in classes.items():
        class_list.append({"name": name, "id": cid})
    for c in old_class:
        if c["id"] not in classes:
            class_list.append(c)
    class_list.sort(key=lambda c: c["id"])
    json.dump(class_list, open(os.path.join(OUT, "ShipClass.json"), "w", encoding="utf-8"), ensure_ascii=False)

    # ShipType.json : keep old entries, add unknown types
    old_type = json.load(open(os.path.join(OLD, "ShipType.json"), encoding="utf-8"))
    have = {t["id"] for t in old_type}
    new_types = sorted({s["stype"] for s in ships} - have)
    report = ["ships %d, classes %d, missing start2 %d" % (len(ships), len(class_list), missing_s2),
              "unknown stype in old ShipType.json: %s" % new_types]
    if new_types:
        for t in new_types:
            nm = next((s for s in ships if s["stype"] == t), None)
            old_type.append({"id": t, "name": {"zh_cn": "", "zh_tw": "", "ja": "", "en": ""}, "short": "", "equip_type": "0" * 76})
    json.dump(old_type, open(os.path.join(OUT, "ShipType.json"), "w", encoding="utf-8"), ensure_ascii=False)

    # validation
    diff = {"attr": 0, "attr_max": 0, "ctype": 0, "cnum": 0, "stype": 0, "remodel": 0, "rarity": 0}
    samples = []
    for gid, o in old_by_id.items():
        n = next((x for x in ships if x["id"] == gid), None)
        if not n:
            continue
        for f in ("attr", "attr_max", "ctype", "cnum", "stype", "rarity"):
            if o.get(f) != n.get(f):
                diff[f] += 1
                if len(samples) < 10:
                    samples.append((gid, f, o.get(f), n.get(f)))
        if (o.get("remodel") or {}).get("to_id") != n["remodel"]["to_id"]:
            diff["remodel"] += 1
    report.append("mismatch %s" % diff)
    report += ["  %s" % (s,) for s in samples]
    open(os.path.join(BASE, "ship_validation.txt"), "w", encoding="utf-8").write("\n".join(report))
    print("\n".join(report), flush=True)


if __name__ == "__main__":
    main()
