"""Generate MapDetail.json (node fleets / drops / names) from zh.kcwiki.cn map pages."""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from mapdata import AREA_ORDER, raw, map_fields, discover_maps
import routes as routes_mod

BASE = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\apkwork"
CACHE = os.path.join(BASE, "gen", "cache")
OUT = os.path.join(BASE, "gen", "out_assets")
ASSETS = os.path.join(BASE, "appbuild", "app", "src", "main", "assets")

FORMATION = {
    "単縦陣": 1, "单縦陣": 1, "单纵阵": 1,
    "複縦陣": 2, "复纵阵": 2,
    "輪形陣": 3, "轮形阵": 3,
    "梯形陣": 4, "梯形阵": 4,
    "単横陣": 5, "单横阵": 5,
    "警戒陣": 6, "警戒阵": 6,
}


def split_blocks(text, template):
    """Yield (pos, body) for nesting-aware {{template ...}} blocks."""
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


def fields_of(body):
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
    s = re.sub(r"\{\{[^{}|]*\|([^{}]*)\}\}", r"\1", s)
    s = re.sub(r"<[^>]+>", "", s)
    return re.sub(r"\s+", " ", s).strip()


def ship_ids_from_links(text):
    """[[睦月]] / [[a|b]] / {{稀有舰|薄云}} -> ship ids"""
    names = []
    for m in re.finditer(r"\{\{[^{}|]*\|([^{}]+)\}\}", text):
        names.append(clean(m.group(1)))
    stripped = re.sub(r"\{\{[^{}]*\}\}", "", text)
    for m in re.finditer(r"\[\[([^\]|]+)(?:\|([^\]]+))?\]\]", stripped):
        names.append(clean(m.group(2) or m.group(1)))
    return names


def enemy_ids(text):
    ids = []
    for m in re.finditer(r"\{\{深海横幅\|(\d+)", text):
        ids.append(int(m.group(1)))
    return ids


def main():
    ships = json.load(open(os.path.join(ASSETS, "Ship.json"), encoding="utf-8"))
    import zhconv
    PROPS = os.path.join(CACHE, "properties")

    def norm(n):
        if not n:
            return ""
        n = zhconv.to_sc(n, PROPS)
        for a, b in (("曽", "曾"), ("叡", "睿"), ("號", "号"), ("輸", "输"), ("戰", "战"), ("艦", "舰")):
            n = n.replace(a, b)
        return re.sub(r"\s+", "", n)

    by_name = {}
    for s in ships:
        for n in (s["name"]["zh_cn"], s["name"]["ja"], s.get("wiki_id")):
            if n:
                by_name.setdefault(n.strip(), s["id"])
                by_name.setdefault(norm(n), s["id"])

    maps = discover_maps()

    out = []
    stat = {"points": 0, "drops": 0, "missing_ship": set(), "maps": 0}
    for sea, no, name_ja in maps:
        area_cn = AREA_ORDER[sea - 1]
        title = "%s/%d-%d" % (area_cn, sea, no)
        try:
            page = raw(title)
        except Exception:  # noqa: BLE001
            continue
        head = map_fields(page, "海域资料改")
        name_cn = (head.get("海域译名") or "").strip() or name_ja
        entry = {
            "id": sea * 10 + no,
            "name": {"zh_cn": clean(name_cn), "ja": clean(name_ja)},
            "points": [],
            "drop": [],
            "route": [],
        }

        for pos, body in split_blocks(page, "敌方配置表"):
            f = fields_of(body)
            point = clean(f.get("海域点"))
            if not point:
                continue
            node_name_ja = clean(f.get("海域点原名"))
            node_name_cn = clean(f.get("海域点译名")) or node_name_ja
            fleets = []
            for suffix in ("", "2", "3"):
                enemy = f.get("敌方" + suffix) or ""
                if not enemy:
                    continue
                ids = enemy_ids(enemy)
                if not ids:
                    continue
                formation = FORMATION.get(clean(f.get("阵型" + suffix)), 0)
                ships_padded = ids[:6] + [-1] * (6 - len(ids[:6]))
                fleets.append({"formation": formation, "ships": ships_padded})
            if not fleets:
                continue
            entry["points"].append({
                "point": point,
                "name": {"zh_cn": node_name_cn, "ja": node_name_ja},
                "type": 0 if clean(f.get("nodetype")).lower() != "boss" else 1,
                "fleets": fleets,
            })
            stat["points"] += 1

        for pos, body in split_blocks(page, "掉落表"):
            f = fields_of(body)
            point = clean(f.get("海域点"))
            if not point:
                continue
            ids = []
            for name in ship_ids_from_links(f.get("掉落") or ""):
                sid = by_name.get(name) or by_name.get(norm(name))
                if sid is None:
                    stat["missing_ship"].add(name)
                    continue
                if sid not in ids:
                    ids.append(sid)
            if ids:
                entry["drop"].append({"name": point, "ship": ids})
                stat["drops"] += 1

        # 路线分歧（详情页「路线」区块）：分歧点 + 条件（表格按行拼接为文本）
        try:
            route_html = raw(title + "/带路条件")
            for r in routes_mod.parse_route_html(route_html):
                parts = list(r.get("notes") or [])
                for tb in r.get("tables") or []:
                    rows = tb.get("rows") or []
                    for row in rows:
                        parts.append(" | ".join(row))
                entry["route"].append({
                    "start": r.get("point") or "",
                    "condition": "\n".join(parts),
                    "target": [],
                    "tables": r.get("tables") or [],
                })
        except Exception:  # noqa: BLE001
            pass

        out.append(entry)
        stat["maps"] += 1

    json.dump(out, open(os.path.join(OUT, "MapDetail.json"), "w", encoding="utf-8"), ensure_ascii=False)
    print("MapDetail.json: %d maps, %d nodes, %d drop tables" % (stat["maps"], stat["points"], stat["drops"]))
    print("unresolved drop names: %d %s" % (len(stat["missing_ship"]), sorted(stat["missing_ship"])[:8]))


if __name__ == "__main__":
    main()
