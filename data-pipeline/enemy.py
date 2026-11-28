"""Merge 深海栖舰 (enemy ships) and 深海装备 (enemy equipment) into Ship.json / Equip.json.

Enemy equipment ids are offset by +1000 to avoid colliding with player equipment ids.
"""
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
ASSETS = os.path.join(BASE, "appbuild", "app", "src", "main", "assets")
SRC = os.path.join(BASE, "gen", "out_assets")

EQUIP_OFFSET = 1000

HEADING_STYPE = {
    "驱逐舰": 2, "轻巡洋舰": 3, "重雷装巡洋舰": 4, "重巡洋舰": 5, "航空巡洋舰": 6,
    "轻空母": 7, "轻型航母": 7, "战列巡洋舰": 8, "高速战舰": 8, "戦艦": 9, "战列舰": 9,
    "航空战舰": 10, "航空母舰": 11, "正规空母": 11, "正规航母": 11, "潜水舰": 13,
    "潜水空母": 14, "补给舰": 15, "运输舰": 15, "水上机母舰": 16, "扬陆舰": 17,
    "装甲空母": 18, "工作舰": 19, "潜水母舰": 20, "练习巡洋舰": 21, "鱼雷艇": 2,
}

RANGE_MAP = {"无": 0, "短": 1, "中": 2, "长": 3, "超长": 4}
ATTR_KEYS = ["range", "speed", "aa", "armor", "asw", "evasion", "fire", "hp", "luck", "los", "torpedo", "bomb", "accuracy"]


def read(p):
    with open(p, encoding="utf-8") as f:
        return f.read()


def num(v):
    m = re.search(r"-?\d+", str(v)) if v is not None else None
    return int(m.group()) if m else 0


def split_blocks(text, template):
    """Yield (position, body) for each {{template...}} block (nesting aware)."""
    needle = "{{" + template
    pos = 0
    while True:
        st = text.find(needle, pos)
        if st < 0:
            return
        depth = 0
        i = st
        end = -1
        while i < len(text) - 1:
            if text[i] == "{" and text[i + 1] == "{":
                depth += 1
                i += 2
                continue
            if text[i] == "}" and text[i + 1] == "}":
                depth -= 1
                i += 2
                if depth == 0:
                    end = i
                    break
                continue
            i += 1
        if end < 0:
            return
        yield st, text[st + 2:end - 2]
        pos = end


def split_top(s, sep):
    out, buf = [], []
    link = tmpl = 0
    i = 0
    while i < len(s):
        c = s[i]
        nxt = s[i + 1] if i + 1 < len(s) else ""
        if c == "[" and nxt == "[":
            link += 1
        elif c == "]" and nxt == "]":
            link = max(0, link - 1)
        elif c == "{" and nxt == "{":
            tmpl += 1
        elif c == "}" and nxt == "}":
            tmpl = max(0, tmpl - 1)
        if c == sep and link == 0 and tmpl == 0:
            out.append("".join(buf))
            buf = []
            i += 1
            continue
        buf.append(c)
        i += 1
    out.append("".join(buf))
    return out


def parse_fields(body):
    f = {}
    for part in split_top(body, "|")[1:]:
        eq = part.find("=")
        if eq > 0:
            f[part[:eq].strip()] = part[eq + 1:].strip()
    return f


def clean(s):
    if not s:
        return ""
    s = re.sub(r"<br\s*/?>", " ", s)
    s = re.sub(r"\[\[([^\]|]*)\|([^\]]*)\]\]", r"\2", s)
    s = re.sub(r"\[\[([^\]]*)\]\]", r"\1", s)
    s = re.sub(r"<[^>]+>", "", s)
    return re.sub(r"\s+", " ", s).strip()


def section_stype(text, pos):
    best, name = -1, ""
    for m in re.finditer(r"^==+\s*([^=\n]+?)\s*==+\s*$", text[:pos], re.M):
        best = m.start()
        name = m.group(1).strip()
    name = re.sub(r"^BOSS型", "", name)
    return HEADING_STYPE.get(name, 0)


def build_enemy_equips(player_equips, page):
    table = find_table(page, "equipDataTable")
    out = []
    by_name = {}
    for key, v in table.items():
        if not isinstance(v, dict):
            continue
        try:
            orig = int(key)
        except ValueError:
            continue
        eid = orig + EQUIP_OFFSET
        name_ja = clean(v.get("日文名") or "")
        name_cn = clean(v.get("中文名") or "") or name_ja
        attr = {k: 0 for k in ATTR_KEYS}
        for cn, en in (("火力", "fire"), ("雷装", "torpedo"), ("对空", "aa"), ("装甲", "armor"),
                       ("爆装", "bomb"), ("命中", "accuracy"), ("索敌", "los"), ("对潜", "asw")):
            attr[en] = num(v.get(cn))
        r = v.get("射程")
        attr["range"] = RANGE_MAP.get(str(r).strip(), num(r)) if not isinstance(r, (int, float)) else int(r)
        types = v.get("类别")
        if not isinstance(types, list) or not types:
            types = v.get("类型") if isinstance(v.get("类型"), list) else []
        entry = {
            "id": eid,
            "name": {"zh_cn": name_cn, "zh_tw": zhconv.to_tc(name_cn, PROPS), "ja": name_ja, "en": ""},
            "introduction": {"zh_cn": "", "zh_tw": "", "ja": "", "en": ""},
            "remark": "",
            "attr": attr,
            "type": [num(x) for x in types],
            "rarity": num(v.get("稀有度")),
            "broken": [0, 0, 0, 0],
            "status": {"research": 0, "improvement": 0, "upgrade": 0, "rank": 0},
        }
        out.append(entry)
        for n in (name_cn, name_ja):
            if n:
                by_name.setdefault(n, eid)
    return out, by_name


def main():
    player_equips = [e for e in json.load(open(os.path.join(SRC, "Equip.json"), encoding="utf-8"))
                     if e["id"] < EQUIP_OFFSET]
    ships = [s for s in json.load(open(os.path.join(SRC, "Ship.json"), encoding="utf-8"))
             if s["id"] < 1500]

    enemy_equip_entries, enemy_equip_by_name = build_enemy_equips(
        player_equips, read(os.path.join(CACHE, "enemy_equip.txt")))

    # 装备名 -> id（玩家装备优先，其次深海装备）
    equip_by_name = {}
    for e in player_equips:
        for n in (e["name"]["zh_cn"], e["name"]["ja"]):
            if n:
                equip_by_name.setdefault(n, e["id"])
    for n, eid in enemy_equip_by_name.items():
        equip_by_name.setdefault(n, eid)

    start2 = json.load(open(os.path.join(CACHE, "api_start2.json"), encoding="utf-8"))
    s2_stype = {}
    for rec in start2.get("api_mst_ship", []):
        sid = rec.get("api_id") or 0
        if sid >= 1500 and rec.get("api_stype"):
            s2_stype[sid] = rec["api_stype"]

    page = read(os.path.join(CACHE, "enemy_ships.txt"))
    enemy_ships = []
    unresolved_names = []
    seen = set()
    for m in re.finditer(r"\{\{深海栖姬单条列表(.*?)\}\}", page, re.S):
        pos, body = m.start(), m.group(1)
        f = parse_fields(body)
        sid = num(f.get("编号"))
        if not sid or sid < 1500 or sid in seen:
            continue
        seen.add(sid)
        name_cn = clean(f.get("名字")) or clean(f.get("中文名"))
        name_ja = name_cn
        attr = {k: 0 for k in ATTR_KEYS}
        attr["hp"] = num(f.get("耐久"))
        attr["fire"] = num(f.get("火力2")) or num(f.get("火力"))
        attr["torpedo"] = num(f.get("雷装2")) or num(f.get("雷装"))
        attr["aa"] = num(f.get("对空2")) or num(f.get("对空"))
        attr["armor"] = num(f.get("装甲"))
        attr["luck"] = num(f.get("运"))
        r = f.get("射程")
        attr["range"] = RANGE_MAP.get(str(r).strip(), num(r)) if not isinstance(r, (int, float)) else int(r)
        eq_names = [clean(f.get("装备%d" % i)) for i in range(1, 6)]
        eq_ids = [equip_by_name.get(n, 0) for n in eq_names if n]
        unresolved_names.extend(n for n in eq_names if n and n not in equip_by_name)
        slots = len(eq_ids)
        while len(eq_ids) < slots:
            eq_ids.append(0)
        enemy_ships.append({
            "id": sid,
            "wiki_id": str(sid),
            "name": {"zh_cn": name_cn, "zh_tw": zhconv.to_tc(name_cn, PROPS), "ja": name_ja, "en": ""},
            "stype": s2_stype.get(sid) or section_stype(page, pos),
            "ctype": 0,
            "cnum": 0,
            "rarity": 0,
            "name_for_search": name_cn,
            "build_time": 0,
            "broken": [0, 0, 0, 0],
            "power_up": [0, 0, 0, 0],
            "consume": [0, 0],
            "painter": "",
            "cv": "",
            "equip": {"slots": slots, "id": eq_ids, "space": [0] * slots},
            "remodel": {"blueprint": False, "catapult": False, "from_id": 0, "to_id": 0, "level": 0, "cost": [0, 0]},
            "attr": attr,
            "attr_max": {k: 0 for k in ATTR_KEYS},
            "attr_99": {k: 0 for k in ATTR_KEYS},
            "get": {"drop": 0, "remodel": 0, "build": 0, "build_time": 0},
        })

    equips = sorted(player_equips + enemy_equip_entries, key=lambda e: e["id"])
    ships = sorted(ships + enemy_ships, key=lambda s: s["id"])
    json.dump(equips, open(os.path.join(ASSETS, "Equip.json"), "w", encoding="utf-8"), ensure_ascii=False)
    json.dump(ships, open(os.path.join(ASSETS, "Ship.json"), "w", encoding="utf-8"), ensure_ascii=False)
    print("enemy equips %d (ids %d..%d), enemy ships %d (ids %d..%d)"
          % (len(enemy_equip_entries),
             min(e["id"] for e in enemy_equip_entries), max(e["id"] for e in enemy_equip_entries),
             len(enemy_ships), min(s["id"] for s in enemy_ships), max(s["id"] for s in enemy_ships)))
    print("total equips %d, total ships %d" % (len(equips), len(ships)))
    print("enemy equip names unresolved:", len(set(unresolved_names)), sorted(set(unresolved_names))[:8])


if __name__ == "__main__":
    main()
