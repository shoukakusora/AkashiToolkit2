"""Generate EquipImprovement.json (daily improvement page) from akashi-list.me.

akashi-list.me renders one block per weapon:

    <div class=weapon id=w001>
      <div class=wimage><img data-src=.../weapon001.png alt="001: 12cm単装砲">...
      <div class=support-ship><img data-src=.../s031.png>
        <div class=weeks-container><div class=weeks>
          <span class=enable>日</span><span class=enable>月</span>... 7 spans (日月火水木金土)

Each support ship lists the weekdays it can improve that weapon.
The app's 7 tabs are Sunday..Saturday, filtered with `(flag & (1 << tab)) > 0`,
so bit 0 = Sunday ... bit 6 = Saturday.
"""
import json
import os
import re
import ssl
import time
import urllib.request

BASE = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\apkwork"
ASSETS = os.path.join(BASE, "appbuild", "app", "src", "main", "assets")
CACHE = os.path.join(BASE, "gen", "cache")
SITE = "https://akashi-list.me/"

DAY_ORDER = ["日", "月", "火", "水", "木", "金", "土"]


def fetch(url, cache_name, retries=4):
    path = os.path.join(CACHE, cache_name)
    if os.path.exists(path) and os.path.getsize(path) > 10000:
        return open(path, encoding="utf-8", errors="replace").read()
    ctx = ssl._create_unverified_context()
    hdr = {"User-Agent": "Mozilla/5.0 (AkashiToolkit data pipeline)"}
    last = None
    for i in range(retries):
        try:
            req = urllib.request.Request(url, headers=hdr)
            with urllib.request.urlopen(req, timeout=120, context=ctx) as r:
                body = r.read().decode("utf-8", "replace")
            open(path, "w", encoding="utf-8").write(body)
            return body
        except Exception as e:  # noqa: BLE001
            last = e
            time.sleep(2 * (i + 1))
    raise last


def norm(s):
    s = (s or "").replace(" ", "").replace("\u3000", "")
    s = s.replace("\uff08", "(").replace("\uff09", ")")
    for a, b in (("\u5f3e", "\u5f39"), ("\u9ad9", "\u9ad8"), ("\u59eb", "\u59ec")):
        s = s.replace(a, b)
    return s


def load_json(name):
    return json.load(open(os.path.join(ASSETS, name), encoding="utf-8"))


def equip_lookup():
    table = {}
    for e in load_json("Equip.json"):
        for n in (e["name"].get("zh_cn"), e["name"].get("ja")):
            if n:
                table.setdefault(norm(n), e["id"])
    return table


def ship_lookup():
    """ship 図鑑番号 (akashi uses s031) -> Ship.json id"""
    by_no = {}
    by_base = {}
    for s in load_json("Ship.json"):
        wid = str(s.get("wiki_id") or "")
        m = re.match(r"^(\d+)([a-z]*)$", wid)
        if not m:
            continue
        no = int(m.group(1))
        suffix = m.group(2)
        if s["id"] >= 1500:
            continue
        if not suffix:
            by_no.setdefault(no, s["id"])
        else:
            by_base.setdefault(no, s["id"])
    for no, sid in by_base.items():
        by_no.setdefault(no, sid)
    return by_no


def parse_weapons(html):
    """Yield (weapon_name, [(ship_no, day_mask), ...])."""
    blocks = re.split(r"<div class=weapon id=w\d+", html)
    for block in blocks[1:]:
        alt = re.search(r'alt="(\d+):\s*([^"]+)"', block)
        if not alt:
            continue
        name = alt.group(2).strip()

        ships = []
        for sm in re.finditer(r'class=support-ship><img data-src=[^>]*?/s(\d{3,4})\.png', block):
            ship_no = int(sm.group(1))
            rest = block[sm.end():sm.end() + 2000]
            weeks = re.search(r"<div class=weeks>(.*?)</div>", rest, re.S)
            mask = 0
            if weeks:
                spans = re.findall(r'<span class=(enable)?\s*>([日火水木金土月])</span>', weeks.group(1))
                for enabled, day in spans:
                    if enabled == "enable" and day in DAY_ORDER:
                        mask |= 1 << DAY_ORDER.index(day)
            if mask == 0:
                # 未标注曜日视为全周可改修
                mask = 0x7F
            ships.append((ship_no, mask))
        if ships:
            yield name, ships


def main():
    html = fetch(SITE, "akashi_list.html")
    equips = equip_lookup()
    ships = ship_lookup()

    out = []
    unresolved_equip = []
    unresolved_ship = set()
    for name, ship_days in parse_weapons(html):
        eid = equips.get(norm(name))
        if not eid:
            unresolved_equip.append(name)
            continue

        grouped = {}
        for ship_no, mask in ship_days:
            sid = ships.get(ship_no)
            if not sid:
                unresolved_ship.add(ship_no)
                continue
            grouped.setdefault(mask, [])
            if sid not in grouped[mask]:
                grouped[mask].append(sid)

        if not grouped:
            continue

        out.append({
            "id": eid,
            "upgrade_to": None,
            "data": {str(k): sorted(v) for k, v in sorted(grouped.items())},
        })

    out.sort(key=lambda x: x["id"])
    path = os.path.join(ASSETS, "EquipImprovement.json")
    json.dump(out, open(path, "w", encoding="utf-8"), ensure_ascii=False)

    print("entries:", len(out))
    print("unresolved equips:", len(unresolved_equip), unresolved_equip[:5])
    print("unresolved ships:", len(unresolved_ship), sorted(unresolved_ship)[:5])

    # 抽查 w001 12cm単装砲
    for e in out:
        if e["id"] == equips.get(norm("12cm単装砲")):
            days = {}
            for flag, ids in e["data"].items():
                days[bin(int(flag))] = len(ids)
            print("12cm単装砲:", "groups", len(e["data"]), "ships", sum(len(v) for v in e["data"].values()))
            break


if __name__ == "__main__":
    main()
