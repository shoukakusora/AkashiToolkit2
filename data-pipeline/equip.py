"""Generate Equip.json / EquipImprovement.json from zh.kcwiki.cn data."""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from lua import find_table
from wiki import fetch_wikitext
import zhconv

BASE = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\apkwork"
CACHE = os.path.join(BASE, "gen", "cache")
PAGES = os.path.join(BASE, "gen", "cache", "pages")
PROPS = os.path.join(BASE, "gen", "cache", "properties")
OUT = os.path.join(BASE, "gen", "out_assets")
OLD = os.path.join(BASE, "assets")

ATTR_MAP = {
    "火力": "fire",
    "对空": "aa",
    "装甲": "armor",
    "对潜": "asw",
    "回避": "evasion",
    "索敌": "los",
    "雷装": "torpedo",
    "爆装": "bomb",
    "命中": "accuracy",
}
ATTR_KEYS = ["range", "aa", "armor", "asw", "evasion", "fire", "los", "torpedo", "bomb", "accuracy"]
RANGE_MAP = {"无": 0, "短": 1, "中": 2, "长": 3, "超长": 4, "超長": 4, "长/3": 3}
STATUS_MAP = {"开发": "research", "改修": "improvement", "更新": "upgrade", "熟练": "rank"}


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def to_int(v, default=0):
    if isinstance(v, bool):
        return int(v)
    if isinstance(v, (int, float)):
        return int(v)
    if v is None:
        return default
    s = str(v).strip()
    m = re.search(r"-?\d+", s)
    return int(m.group(0)) if m else default


def range_value(v):
    if isinstance(v, (int, float)):
        return int(v)
    s = str(v).strip()
    if s in RANGE_MAP:
        return RANGE_MAP[s]
    m = re.search(r"(\d+)", s)
    if m:
        return int(m.group(1))
    return 0


def resources(d):
    """{'燃料':x,'弹药':y,'钢材':z,'铝':w} -> [x,y,z,w]"""
    if not isinstance(d, dict):
        return [0, 0, 0, 0]
    return [to_int(d.get("燃料")), to_int(d.get("弹药")), to_int(d.get("钢材")), to_int(d.get("铝"))]


def consume(d):
    """{开发:[a,b], 改修:[c,d], 装备数:n, 装备:'id'} -> [a,b,c,d,n,(id)]"""
    if not isinstance(d, dict):
        return None
    kai = d.get("开发") or [0, 0]
    shu = d.get("改修") or [0, 0]
    if not isinstance(kai, list):
        kai = [to_int(kai)]
    if not isinstance(shu, list):
        shu = [to_int(shu)]
    out = [to_int(kai[0]), to_int(kai[-1]), to_int(shu[0]), to_int(shu[-1]), to_int(d.get("装备数"))]
    if "装备" in d and d.get("装备") not in ("", None):
        out.append(to_int(d.get("装备")))
    return out


def plain_text(s):
    if not s or not isinstance(s, str):
        return ""
    s = re.sub(r"<ref[^>]*>.*?</ref>", "", s, flags=re.S)
    s = re.sub(r"<ref[^>]*/>", "", s)
    s = re.sub(r"<br\s*/?>", "\n", s, flags=re.I)
    s = re.sub(r"<[^>]+>", "", s)
    s = re.sub(r"\[\[(?:[^\]|]*\|)?([^\]]*)\]\]", r"\1", s)
    s = s.replace("'''", "").replace("''", "")
    lines = [ln.strip() for ln in s.split("\n")]
    return "\n".join([ln for ln in lines if ln]).strip()


def extract_equip_page(text):
    """Pull 图鉴说明原文 / 译文 out of the auto-generated equipment page."""
    if not text:
        return None, None
    ja = re.search(r"\|\s*图鉴说明原文\s*=([^\n]*(?:\n(?!\s*\||\s*\}\})[^\n]*)*)", text)
    zh = re.search(r"\|\s*图鉴说明译文\s*=([^\n]*(?:\n(?!\s*\||\s*\}\})[^\n]*)*)", text)
    return (plain_text(ja.group(1)) if ja else None, plain_text(zh.group(1)) if zh else None)


def main():
    os.makedirs(OUT, exist_ok=True)
    equip_lua = read(os.path.join(CACHE, "equip_module.txt"))
    ship_lua = read(os.path.join(CACHE, "ship_module.lua"))
    tb = find_table(equip_lua, "equipDataTb")
    ships = find_table(ship_lua, "shipDataTb")

    ship_by_name = {}
    for key, s in ships.items():
        if not isinstance(s, dict):
            continue
        sid = to_int(s.get("ID"))
        for field in ("中文名", "日文名"):
            nm = s.get(field)
            if isinstance(nm, str) and nm.strip():
                ship_by_name.setdefault(nm.strip(), sid)
    print("ships: %d, name index: %d" % (len(ships), len(ship_by_name)), flush=True)

    old = json.load(open(os.path.join(OLD, "Equip.json"), encoding="utf-8"))
    old_by_id = {e["id"]: e for e in old}

    # collect pages
    titles = []
    for key, e in tb.items():
        if not isinstance(e, dict):
            continue
        nm = e.get("中文名")
        if isinstance(nm, str) and nm.strip():
            titles.append(nm.strip())
    pages = fetch_wikitext(sorted(set(titles)), PAGES)

    equip_improvement = []
    out = []
    stats = {"new": 0, "updated": 0, "no_intro": 0}
    for key, e in tb.items():
        if not isinstance(e, dict):
            continue
        eid = to_int(e.get("ID"), to_int(key))
        name_cn = (e.get("中文名") or "").strip()
        name_ja = (e.get("日文名") or "").strip()
        if not name_cn and not name_ja:
            continue
        old_e = old_by_id.get(eid)

        attr = {k: 0 for k in ATTR_KEYS}
        src = e.get("属性") if isinstance(e.get("属性"), dict) else {}
        for cn, en in ATTR_MAP.items():
            attr[en] = to_int(src.get(cn))
        attr["range"] = range_value(src.get("射程"))

        status = {v: 0 for v in STATUS_MAP.values()}
        st = e.get("状态") if isinstance(e.get("状态"), dict) else {}
        for cn, en in STATUS_MAP.items():
            status[en] = to_int(st.get(cn))

        types = e.get("类别") if isinstance(e.get("类别"), list) else []
        types = [to_int(x) for x in types]

        rarity_str = e.get("稀有度") or ""
        rarity = len(rarity_str) if isinstance(rarity_str, str) else to_int(rarity_str)

        intro_zh = intro_ja = None
        page = pages.get(name_cn)
        if page:
            intro_ja, intro_zh = extract_equip_page(page)

        intro_old = old_e.get("introduction", {}) if old_e else {}
        zh_cn = intro_zh or intro_old.get("zh_cn", "")
        ja = intro_ja or intro_old.get("ja", "")
        if not zh_cn and not ja:
            stats["no_intro"] += 1
        name_zh_tw = zhconv.to_tc(name_cn, PROPS) if name_cn else intro_old.get("zh_tw", "")
        intro_zh_tw = zhconv.to_tc(zh_cn, PROPS) if zh_cn else intro_old.get("zh_tw", "")

        improvements = []
        for field in ("装备改修", "装备改修2", "装备改修3"):
            imp = e.get(field)
            if not isinstance(imp, dict) or not imp:
                continue
            date = imp.get("日期") if isinstance(imp.get("日期"), dict) else {}
            dates = []
            ship_ids = []
            for day in ("日", "一", "二", "三", "四", "五", "六"):
                names = date.get(day) if isinstance(date.get(day), list) else []
                names = [str(n).strip() for n in names if str(n).strip()]
                dates.append(names)
                ids = []
                for nm in names:
                    if nm == "〇":
                        ids = [0]
                        break
                    sid = ship_by_name.get(nm)
                    if sid is not None:
                        ids.append(sid)
                ship_ids.append(ids)
            up = imp.get("更新装备") if isinstance(imp.get("更新装备"), dict) else {}
            entry_imp = {
                "remark": plain_text(imp.get("改修备注") or ""),
                "cost": resources(imp.get("资源消费")),
                "date": dates,
                "ship": ship_ids,
                "upgrade": [to_int(up.get("装备")), to_int(up.get("等级"))],
            }
            # 只有真正存在的消费表才写入，避免生成空数组（App 端会按下标读取）
            for field, key in (("item", "初期消费"), ("item2", "中段消费"), ("item3", "更新消费")):
                consumed = consume(imp.get(key))
                if consumed:
                    entry_imp[field] = consumed
            improvements.append(entry_imp)
            # 改修提醒用: 舰娘 -> 可改修天
            days_index = {"日": 0, "一": 1, "二": 2, "三": 3, "四": 4, "五": 5, "六": 6}
            data = {}
            for day, i in days_index.items():
                for nm in dates[i]:
                    if nm == "〇":
                        continue
                    sid = ship_by_name.get(nm)
                    if sid is None:
                        continue
                    data.setdefault(str(sid), [])
                    if i not in data[str(sid)]:
                        data[str(sid)].append(i)
            if data:
                equip_improvement.append({
                    "id": eid,
                    "upgrade_to": [to_int(up.get("装备")), to_int(up.get("等级"))],
                    "data": data,
                })

        entry = {
            "id": eid,
            "name": {
                "zh_cn": name_cn,
                "zh_tw": name_zh_tw,
                "ja": name_ja,
                "en": (old_e or {}).get("name", {}).get("en", ""),
            },
            "introduction": {"zh_cn": zh_cn, "zh_tw": intro_zh_tw, "ja": ja, "en": ""},
            "remark": plain_text(e.get("备注") or (old_e or {}).get("remark", "")),
            "attr": attr,
            "type": types,
            "rarity": rarity,
            "broken": resources(e.get("废弃")),
            "status": status,
        }
        if improvements:
            entry["improvements"] = improvements
        out.append(entry)
        if old_e:
            stats["updated"] += 1
        else:
            stats["new"] += 1

    out.sort(key=lambda x: x["id"])
    json.dump(out, open(os.path.join(OUT, "Equip.json"), "w", encoding="utf-8"), ensure_ascii=False)
    json.dump(equip_improvement, open(os.path.join(OUT, "EquipImprovement.json"), "w", encoding="utf-8"), ensure_ascii=False)
    print("Equip.json %d entries (new %d, updated %d, no intro %d), improvements %d" % (
        len(out), stats["new"], stats["updated"], stats["no_intro"], len(equip_improvement)), flush=True)

    # validation against old data
    diff = {"attr": 0, "type": 0, "rarity": 0, "broken": 0, "status": 0, "name": 0}
    samples = []
    for eid, o in old_by_id.items():
        n = next((x for x in out if x["id"] == eid), None)
        if not n:
            continue
        for f in ("attr", "type", "rarity", "broken", "status"):
            if o.get(f) != n.get(f):
                diff[f] += 1
                if len(samples) < 12:
                    samples.append((eid, f, o.get(f), n.get(f)))
        if o.get("name", {}).get("zh_cn") != n.get("name", {}).get("zh_cn"):
            diff["name"] += 1
    report = ["old entries %d, new entries %d" % (len(old_by_id), len(out)),
              "mismatches: %s" % diff] + ["  id=%s %s old=%s new=%s" % s for s in samples]
    open(os.path.join(BASE, "equip_validation.txt"), "w", encoding="utf-8").write("\n".join(report))
    print("validation written", flush=True)


if __name__ == "__main__":
    main()
