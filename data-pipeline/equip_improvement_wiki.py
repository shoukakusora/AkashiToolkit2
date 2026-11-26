"""WIKI-BASED generator for EquipImprovement.json (改修 → 每日改修).

NOTE (2026-11-26): the project switched the shipped EquipImprovement.json to the
akashi-list.me source (pygen/equip_improvement.py).  This script is kept as the
zh.kcwiki.cn based alternative / cross-check.  It does NOT write into
appbuild/.../assets by default - pass --out <path> if you really want to.

Source
------
* Page/module: 模块:舰娘装备数据改  (https://zh.kcwiki.cn/wiki/模块:舰娘装备数据改)
  It is the data module consumed by 模块:舰娘装备函数改, which renders the
  wiki's 每日改修/全部改修 table (page 装备改修列表,
  https://zh.kcwiki.cn/wiki/装备改修列表).

Output schema (must stay compatible with the Android app)
---------------------------------------------------------
    [{"id": <equip id>, "upgrade_to": [<equip id>, <level>] | null,
      "data": {"<day flag>": [<ship id>, ...], ...}}, ...]

* day flag is a bitmask: bit0 = Sunday, bit1 = Monday, ... bit6 = Saturday.
  The app (EquipImprovementAdapter) shows 7 tabs, tab index 0 = Sunday and
  filters with `(flag & (1 << tabIndex)) > 0`.
* ship ids are Ship.json ids of player ships (< 1500); 0 means "any ship"
  (the app renders a special "any" label for 0).

Semantics decoded from the wiki module/rendering function:
* 日期[day][1] == "×"  -> equipment cannot be improved that day
* 日期[day][1] == "〇"  -> improvable that day, no helper ship restriction (ship 0)
* 日期[day][1] == "?"  -> improvable but helper unknown -> day is skipped
* otherwise            -> list of helper ship names for that day
* 更新装备              -> upgrade target (equip id + required star level)

Four vanilla equipments (12.7cm連装砲, 14cm単装砲, 61cm四連装魚雷,
九四式爆雷投射機) are marked "×" on every weekday by the zh module even though
they are improvable with *any* helper ship on specific weekdays.  For those the
weekday availability is taken from the en.kancollewiki.net data module
(`_ships = { [true] = {...} }`) and falls back to FALLBACK_ANY_DAYS, which also
matches the original 2016 app data (flags 0x7f / 0x7f / 0x67 / 0x18).

Usage
-----
    python pygen/equip_improvement_wiki.py                  # cache, write gen/imp/
    python pygen/equip_improvement_wiki.py --refresh        # re-download module
    python pygen/equip_improvement_wiki.py --out <path>     # write elsewhere
"""
import argparse
import io
import json
import os
import re
import ssl
import sys
import time
import urllib.parse
import urllib.request

BASE = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\apkwork"
CACHE = os.path.join(BASE, "gen", "cache")
RAW = os.path.join(BASE, "gen", "imp")
ASSETS = os.path.join(BASE, "appbuild", "app", "src", "main", "assets")
PROPS = os.path.join(BASE, "gen", "cache", "properties")
REPORT = os.path.join(RAW, "EquipImprovement_wiki_report.txt")
DEFAULT_OUT = os.path.join(RAW, "EquipImprovement_wiki.json")

MODULE_TITLE = "模块:舰娘装备数据改"
MODULE_URL = "https://zh.kcwiki.cn/wiki/" + urllib.parse.quote(MODULE_TITLE)
LIST_URL = "https://zh.kcwiki.cn/wiki/" + urllib.parse.quote("装备改修列表")
API = "https://zh.kcwiki.cn/api.php"
INDEX = "https://zh.kcwiki.cn/index.php"
EN_API = "https://en.kancollewiki.net/w/index.php?title="
EN_MODULE_URL = "https://en.kancollewiki.net/wiki/Module:Data/Equipment/"
HDR = {"User-Agent": "AkashiToolkitDataSync/1.0 (https://zh.kcwiki.cn/)"}
CTX = ssl._create_unverified_context()

DAYS = ["日", "一", "二", "三", "四", "五", "六"]          # 日 = Sunday
DAY_NAMES = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
FIELDS = ["装备改修", "装备改修2", "装备改修3", "装备改修4"]

# en.kancollewiki.net day names -> our day index (0 = Sunday)
EN_DAYS = {"Sunday": 0, "Monday": 1, "Tuesday": 2, "Wednesday": 3,
           "Thursday": 4, "Friday": 5, "Saturday": 6}

# Fallback if en.kancollewiki.net cannot be reached.
FALLBACK_ANY_DAYS = {2: [0, 1, 2, 3, 4, 5, 6], 4: [0, 1, 2, 3, 4, 5, 6],
                     14: [0, 1, 2, 5, 6], 44: [3, 4]}

# en.kancollewiki.net data modules used for the "any helper" weekday pattern
# (the zh wiki module stores "×" for these four equipment blocks).
EN_ANY_HELPER_MODULES = {
    2: "Module:Data/Equipment/12.7cm_Twin_Gun_Mount",
    4: "Module:Data/Equipment/14cm_Single_Gun_Mount",
    14: "Module:Data/Equipment/61cm_Quadruple_Torpedo_Mount",
    44: "Module:Data/Equipment/Type_94_Depth_Charge_Projector",
}

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lua import find_table  # noqa: E402


# --------------------------------------------------------------------------- #
# wiki access
# --------------------------------------------------------------------------- #
def _get(url, timeout=120):
    req = urllib.request.Request(url, headers=HDR)
    with urllib.request.urlopen(req, timeout=timeout, context=CTX) as r:
        return r.read().decode("utf-8", "replace")


def fetch_module(refresh=False):
    """Return (wikitext, revision_timestamp). Cached on disk unless refresh."""
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, "equip_improvement_module.txt")
    ts_path = os.path.join(CACHE, "equip_improvement_module.ts.txt")
    if not refresh and os.path.exists(path):
        text = io.open(path, encoding="utf-8").read()
        ts = io.open(ts_path, encoding="utf-8").read().strip() if os.path.exists(ts_path) else "?"
        return text, ts + " (cached)"

    last = None
    for n in range(4):
        try:
            text = _get(INDEX + "?" + urllib.parse.urlencode(
                {"title": MODULE_TITLE, "action": "raw"}))
            break
        except Exception as e:  # noqa: BLE001
            last = e
            time.sleep(1.5 * (n + 1))
    else:
        raise RuntimeError("cannot download %s: %s" % (MODULE_TITLE, last))

    ts = "?"
    try:
        d = json.loads(_get(API + "?" + urllib.parse.urlencode({
            "action": "query", "format": "json", "formatversion": "2",
            "prop": "revisions", "rvprop": "timestamp", "titles": MODULE_TITLE,
        })))
        pages = d.get("query", {}).get("pages", [])
        if pages and pages[0].get("revisions"):
            ts = pages[0]["revisions"][0]["timestamp"]
    except Exception:  # noqa: BLE001
        pass

    io.open(path, "w", encoding="utf-8").write(text)
    io.open(ts_path, "w", encoding="utf-8").write(ts)
    return text, ts


# --------------------------------------------------------------------------- #
# ids / names
# --------------------------------------------------------------------------- #
def to_int(v, default=0):
    if isinstance(v, bool):
        return int(v)
    if isinstance(v, (int, float)):
        return int(v)
    if v is None:
        return default
    m = re.search(r"-?\d+", str(v))
    return int(m.group(0)) if m else default


def norm(s):
    return re.sub(r"\s+", "", str(s or ""))


# zh wiki occasionally uses a homoglyph/variant the app data writes differently
ALIASES = [
    ("比睿", "比叡"),
    ("伊號", "伊号"),
]


def build_ship_index(ships):
    """name -> ship id, for player ships only (< 1500)."""
    idx = {}
    for s in ships:
        sid = s.get("id")
        if not isinstance(sid, int) or sid >= 1500:
            continue
        nm = s.get("name") or {}
        keys = [nm.get("zh_cn"), nm.get("zh_tw"), nm.get("ja"),
                nm.get("en"), s.get("name_for_search")]
        for k in keys:
            if not k:
                continue
            for tok in str(k).split(","):
                tok = norm(tok)
                if tok:
                    idx.setdefault(tok, sid)
    return idx


def resolve_ship(name, ship_idx, to_sc):
    """Return (ship id, how) or (None, None); how is exact/alias/zhconv."""
    n = norm(name)
    if not n:
        return None, None
    if n in ship_idx:
        return ship_idx[n], "exact"
    for a, b in ALIASES:
        if a in n and norm(n.replace(a, b)) in ship_idx:
            return ship_idx[norm(n.replace(a, b))], "alias"
        if b in n and norm(n.replace(b, a)) in ship_idx:
            return ship_idx[norm(n.replace(b, a))], "alias"
    if to_sc:
        try:
            c = norm(to_sc(n))
            if c in ship_idx:
                return ship_idx[c], "zhconv"
        except Exception:  # noqa: BLE001
            pass
    return None, None


# --------------------------------------------------------------------------- #
# en.kancollewiki.net fallback for the weekday pattern of "any helper" equipment
# --------------------------------------------------------------------------- #
def _en_module(equip_id, log, refresh=False):
    """Return (wikitext|None, url).  Cached under gen/cache/en_equipment/."""
    title = EN_ANY_HELPER_MODULES.get(equip_id)
    if not title:
        return None, None
    d = os.path.join(CACHE, "en_equipment")
    os.makedirs(d, exist_ok=True)
    path = os.path.join(d, "%d.txt" % equip_id)
    url = EN_MODULE_URL + title.split("/", 2)[-1]
    if os.path.exists(path) and not refresh:
        return io.open(path, encoding="utf-8").read(), url
    try:
        text = _get(EN_API + urllib.parse.quote(title, safe="") + "&action=raw", timeout=60)
    except Exception as e:  # noqa: BLE001
        log.append("EN FAIL %s (%s)" % (title, e))
        return None, url
    io.open(path, "w", encoding="utf-8").write(text)
    return text, url


def _parse_en_any_days(text):
    """Return the weekday indexes marked true for the "any ship" helper."""
    if not text:
        return None
    m = re.search(r"_ships\s*=\s*\{", text)
    if not m:
        return None
    body = text[m.end():]
    mt = re.search(r"\[true\]\s*=\s*\{", body)
    if not mt:
        return None
    i = mt.end()
    depth = 1
    start = i
    while i < len(body) and depth:
        if body[i] == "{":
            depth += 1
        elif body[i] == "}":
            depth -= 1
        i += 1
    block = body[start:i - 1]
    days = []
    for name, idx in EN_DAYS.items():
        mm = re.search(re.escape(name) + r"\s*=\s*(true|false)", block)
        if mm and mm.group(1) == "true":
            days.append(idx)
    return sorted(days)


def any_helper_days(equip_id, log, refresh=False):
    """Weekday indexes on which an equipment is improvable with any helper."""
    text, url = _en_module(equip_id, log, refresh)
    days = _parse_en_any_days(text)
    if days:
        return days, ("en.kancollewiki.net: " + url) if url else "en.kancollewiki.net"
    days = FALLBACK_ANY_DAYS.get(equip_id)
    if days:
        return list(days), "fallback table (en wiki unavailable)"
    return None, None


# --------------------------------------------------------------------------- #
# build
# --------------------------------------------------------------------------- #
def build(equip_tb, equip_ids, ship_idx, to_sc, ship_names, equip_names, log, refresh=False):
    entries = []
    seen = set()
    stats = {"blocks": 0, "status0": [], "dropped_no_ships": 0,
             "any_all_days": [], "unknown_days": 0, "unresolved": {},
             "upgrade_none": 0, "upgrade_bad": 0, "closed_cells": 0,
             "any_cells": 0, "name_cells": 0, "resolved_names": 0,
             "alias": {}, "zhconv": {}}

    def add_entry(eid, upgrade, data):
        key = (eid, tuple(upgrade) if upgrade else None,
               tuple(sorted((k, tuple(v)) for k, v in data.items())))
        if key in seen:
            return False
        seen.add(key)
        entries.append({"id": eid, "upgrade_to": upgrade, "data": data})
        return True

    for _key, e in sorted(equip_tb.items(), key=lambda kv: to_int(
            (kv[1] or {}).get("ID") if isinstance(kv[1], dict) else kv[0], 0)):
        if not isinstance(e, dict):
            continue
        eid = to_int(e.get("ID"))
        if eid not in equip_ids:
            log.append("SKIP block: equip id %s not in Equip.json (%s)" % (eid, e.get("中文名")))
            continue
        status = (e.get("状态") or {}).get("改修")
        for field in FIELDS:
            imp = e.get(field)
            if not isinstance(imp, dict) or not imp:
                continue
            stats["blocks"] += 1
            if to_int(status) != 1:
                # 状态.改修 == 0 with a filled 日期 table happens for equipment
                # added through the "軽" factory expansion (see 改修备注: 最高改修★+4).
                # They are still improvable on the listed weekdays, so keep them.
                stats["status0"].append((eid, equip_names.get(eid, ""), field))

            data = {}
            closed = 0
            for i, day in enumerate(DAYS):
                arr = (imp.get("日期") or {}).get(day)
                flag = 1 << i
                if not isinstance(arr, list) or not arr:
                    log.append("MISS %s %s %s: day %s empty" % (
                        eid, equip_names.get(eid, ""), field, day))
                    continue
                first = str(arr[0]).strip()
                if first == "×":
                    closed += 1
                    stats["closed_cells"] += 1
                    continue
                if first == "〇":
                    data[flag] = [0]
                    stats["any_cells"] += 1
                    continue
                if first == "?":
                    stats["unknown_days"] += 1
                    log.append("SKIP day %s of %s %s: helper unknown '?'" % (
                        day, eid, equip_names.get(eid, "")))
                    continue
                ids = []
                for nm in arr:
                    nm = str(nm).strip()
                    if nm in ("", "×", "〇", "?"):
                        continue
                    sid, how = resolve_ship(nm, ship_idx, to_sc)
                    if sid is None:
                        stats["unresolved"][nm] = stats["unresolved"].get(nm, 0) + 1
                        log.append("UNRESOLVED ship '%s' (%s %s, day %s)" % (
                            nm, eid, equip_names.get(eid, ""), day))
                        continue
                    if how == "alias":
                        stats["alias"][nm] = stats["alias"].get(nm, 0) + 1
                        log.append("ALIAS ship '%s' -> %s (%s, day %s)" % (
                            nm, ship_names.get(sid, sid), equip_names.get(eid, ""), day))
                    elif how == "zhconv":
                        stats["zhconv"][nm] = stats["zhconv"].get(nm, 0) + 1
                    if sid not in ids:
                        ids.append(sid)
                        stats["resolved_names"] += 1
                if ids:
                    data[flag] = ids
                    stats["name_cells"] += 1

            if closed == 7 and not data:
                # wiki quirk: an improvable equipment with "×" on every weekday
                # carries no helper/day information.  For the four vanilla
                # equipments concerned the helper is "any ship", but the
                # weekdays still differ, so take them from the en wiki data
                # module (which also matches the original 2016 app data).
                days, src = any_helper_days(eid, log, refresh)
                if days:
                    data = {1 << d: [0] for d in days}
                    stats["any_all_days"].append(
                        (eid, equip_names.get(eid, ""), field,
                         [DAY_NAMES[d] for d in days], src))

            if not data:
                stats["dropped_no_ships"] += 1
                log.append("DROP %s %s %s: no resolvable ships on any day" % (
                    eid, equip_names.get(eid, ""), field))
                continue

            upgrade = None
            up = imp.get("更新装备")
            if isinstance(up, dict) and up:
                tid = to_int(up.get("装备"))
                if tid in equip_ids:
                    upgrade = [tid, to_int(up.get("等级"))]
                else:
                    stats["upgrade_bad"] += 1
                    log.append("UPGRADE target %s of %s not in Equip.json -> null" % (
                        tid, equip_names.get(eid, "")))
            else:
                stats["upgrade_none"] += 1

            if not add_entry(eid, upgrade, data):
                log.append("DUP %s %s %s: identical entry already present" % (
                    eid, equip_names.get(eid, ""), field))
    return entries, stats


# --------------------------------------------------------------------------- #
# verify
# --------------------------------------------------------------------------- #
def verify(entries, equip_ids, ship_ids, lines):
    ok = True
    if not isinstance(entries, list) or not entries:
        lines.append("FAIL: empty output")
        return False
    seen = set()
    for i, it in enumerate(entries):
        if set(it.keys()) != {"id", "upgrade_to", "data"}:
            lines.append("FAIL entry %d keys %s" % (i, sorted(it.keys())))
            ok = False
        eid = it.get("id")
        if not isinstance(eid, int) or eid not in equip_ids:
            lines.append("FAIL entry %d: equip id %r not in Equip.json" % (i, eid))
            ok = False
        up = it.get("upgrade_to")
        if up is not None:
            if (not isinstance(up, list) or len(up) != 2
                    or not all(isinstance(x, int) for x in up)):
                lines.append("FAIL entry %d: bad upgrade_to %r" % (i, up))
                ok = False
            elif up[0] not in equip_ids:
                lines.append("FAIL entry %d: upgrade target %r not in Equip.json" % (i, up[0]))
                ok = False
        data = it.get("data")
        if not isinstance(data, dict) or not data:
            lines.append("FAIL entry %d: empty data" % i)
            ok = False
            continue
        for k, v in data.items():
            flag = int(k)
            if not (1 <= flag <= 127) or (flag & (flag - 1)) != 0:
                lines.append("FAIL entry %d: bad day flag %r" % (i, k))
                ok = False
            if not isinstance(v, list) or not v:
                lines.append("FAIL entry %d flag %s: empty ship list" % (i, k))
                ok = False
            for sid in v:
                if not isinstance(sid, int) or (sid != 0 and sid not in ship_ids):
                    lines.append("FAIL entry %d flag %s: ship %r not in Ship.json" % (i, k, sid))
                    ok = False
        key = (eid, tuple(up) if up else None, json.dumps(data, sort_keys=True))
        if key in seen:
            lines.append("WARN entry %d: duplicate" % i)
        seen.add(key)
    return ok


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--refresh", action="store_true", help="re-download the wiki module")
    ap.add_argument("--out", default=DEFAULT_OUT,
                    help="output path (default: gen/imp/EquipImprovement_wiki.json; "
                         "the shipped app asset is maintained from akashi-list.me)")
    args = ap.parse_args()

    os.makedirs(RAW, exist_ok=True)
    log = []

    ships = json.load(io.open(os.path.join(ASSETS, "Ship.json"), encoding="utf-8"))
    equips = json.load(io.open(os.path.join(ASSETS, "Equip.json"), encoding="utf-8"))
    equip_ids = {e["id"] for e in equips}
    equip_names = {e["id"]: (e.get("name") or {}).get("zh_cn", "") for e in equips}
    ship_ids = {s["id"] for s in ships if s["id"] < 1500}
    ship_names = {s["id"]: (s.get("name") or {}).get("zh_cn", "") for s in ships}
    ship_idx = build_ship_index(ships)

    to_sc = None
    try:
        import zhconv  # local pygen module
        if os.path.isdir(PROPS):
            to_sc = lambda x: zhconv.to_sc(x, PROPS)  # noqa: E731
    except Exception:  # noqa: BLE001
        pass

    text, rev_ts = fetch_module(args.refresh)
    tb = find_table(text, "equipDataTb")

    entries, stats = build(tb, equip_ids, ship_idx, to_sc, ship_names, equip_names,
                           log, args.refresh)
    entries.sort(key=lambda e: (e["id"],
                                e["upgrade_to"][0] if e["upgrade_to"] else -1,
                                e["upgrade_to"][1] if e["upgrade_to"] else -1))

    # ---- verification -----------------------------------------------------
    vlines = []
    ok = verify(entries, equip_ids, ship_ids, vlines)
    if not ok:
        ok = verify(json.loads(json.dumps(entries, ensure_ascii=False)),
                    equip_ids, ship_ids, vlines)

    # ---- report -----------------------------------------------------------
    per_day = {i: 0 for i in range(7)}
    distinct_ships = set()
    for e in entries:
        for k, v in e["data"].items():
            per_day[int(k).bit_length() - 1] += 1
            distinct_ships.update(v)
    with_upgrade = sum(1 for e in entries if e["upgrade_to"])

    rep = []
    rep.append("EquipImprovement.json (wiki source) regeneration report")
    rep.append("=" * 60)
    rep.append("generated: %s" % time.strftime("%Y-%m-%d %H:%M:%S"))
    rep.append("sources:")
    rep.append("  wiki module : %s (rev timestamp %s)" % (MODULE_URL, rev_ts))
    rep.append("  wiki table  : %s (rendered by 模块:改修列表渲染 -> 模块:舰娘装备数据改)" % LIST_URL)
    rep.append("  cross-check : en.kancollewiki.net Module:Data/Equipment/<en name> for the")
    rep.append("                any-helper weekday patterns of 12.7cm連装砲 / 14cm単装砲 /")
    rep.append("                61cm四連装魚雷 / 九四式爆雷投射機 (zh module stores '×' there)")
    rep.append("  ids         : %s, %s" % (
        os.path.join(ASSETS, "Equip.json"), os.path.join(ASSETS, "Ship.json")))
    rep.append("")
    rep.append("counts:")
    rep.append("  equipDataTb entries     : %d" % len(tb))
    rep.append("  improvement blocks seen : %d (状态.改修=0 blocks kept: %d)" % (
        stats["blocks"], len(stats["status0"])))
    rep.append("    status0 blocks: %s" % ["%s %s %s" % x for x in stats["status0"]])
    rep.append("  entries written         : %d" % len(entries))
    rep.append("  dropped (no ships)      : %d" % stats["dropped_no_ships"])
    rep.append("  entries with upgrade_to : %d / %d" % (with_upgrade, len(entries)))
    rep.append("  upgrade target missing  : %d" % stats["upgrade_bad"])
    rep.append("  day name cells          : %d (any-ship cells %d, closed cells %d)" % (
        stats["name_cells"], stats["any_cells"], stats["closed_cells"]))
    rep.append("  day cells '?' skipped   : %d" % stats["unknown_days"])
    rep.append("  ship ids resolved       : %d distinct, %d name occurrences" % (
        len(distinct_ships), stats["resolved_names"]))
    rep.append("  flags per weekday (Sun..Sat): %s" % [per_day[i] for i in range(7)])
    rep.append("  unresolved ship names   : %d %s" % (
        len(stats["unresolved"]), sorted(stats["unresolved"].items())))
    rep.append("  name normalisation      : alias %s, zhconv %s" % (
        sorted(stats["alias"].items()), sorted(stats["zhconv"].items())))
    rep.append("  all-closed blocks filled from en wiki (any helper): %s" % (
        ["%s %s %s days=%s src=%s" % x for x in stats["any_all_days"]]))
    rep.append("")
    rep.append("verification: %s" % ("PASS" if ok else "FAIL"))
    rep.extend("  " + x for x in vlines if x.startswith("FAIL"))
    rep.append("")
    rep.append("sanity samples (weekday -> resolved ship names):")
    for eid in (1, 2, 3, 8, 14, 36, 44, 88, 328):
        got = [e for e in entries if e["id"] == eid]
        if not got:
            rep.append("  id=%d %s: no entry" % (eid, equip_names.get(eid, "")))
            continue
        for e in got:
            rep.append("  id=%d %s upgrade_to=%s" % (eid, equip_names.get(eid, ""), e["upgrade_to"]))
            for i, day in enumerate(DAYS):
                v = e["data"].get(1 << i)
                if not v:
                    rep.append("    %s: -" % DAY_NAMES[i])
                    continue
                names = ["any#0" if s == 0 else "%s#%d" % (ship_names.get(s, "?"), s) for s in v]
                rep.append("    %s: %s" % (DAY_NAMES[i], " / ".join(names)))
    rep.append("")
    rep.append("log (first 120 lines):")
    rep.extend("  " + x for x in log[:120])

    io.open(REPORT, "w", encoding="utf-8").write("\n".join(rep))
    io.open(os.path.join(BASE, "equip_improvement_wiki_report.txt"), "w",
            encoding="utf-8").write("\n".join(rep))

    if ok:
        payload = json.dumps(entries, ensure_ascii=False, separators=(",", ":"))
        with io.open(args.out, "w", encoding="utf-8") as f:
            f.write(payload)
        print("written %s (%d entries, %d bytes)" % (args.out, len(entries), len(payload.encode("utf-8"))))
    else:
        print("VERIFICATION FAILED, output not written")
    print("report: %s" % REPORT)
    print("entries=%d blocks=%d dropped=%d unresolved_names=%d upgrade=%d" % (
        len(entries), stats["blocks"], stats["dropped_no_ships"],
        len(stats["unresolved"]), with_upgrade))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
