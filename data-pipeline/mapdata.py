"""Generate Map.json / MapType.json from zh.kcwiki.cn (routing conditions) + kcwiki /maps."""
import html
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import ssl
import urllib.parse
import urllib.request

import zhconv
import routes as routes_mod

BASE = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\apkwork"
CACHE = os.path.join(BASE, "gen", "cache")
PAGES = os.path.join(CACHE, "pages")
PROPS = os.path.join(CACHE, "properties")
OUT = os.path.join(BASE, "gen", "out_assets")

CTX = ssl._create_unverified_context()
HDR = {"User-Agent": "Mozilla/5.0 (AkashiToolkit data pipeline)"}

# MapType 顺序（必须与 App 中的下标一致）
JA_AREA = {1: "鎮守府海域", 2: "南西諸島海域", 3: "北方海域", 4: "西方海域", 5: "南方海域", 6: "中部海域", 7: "南西海域"}

AREA_ORDER = ["镇守府海域", "南西群岛海域", "北方海域", "西方海域", "南方海域", "中部海域", "南西海域"]


def get(url, timeout=60):
    req = urllib.request.Request(url, headers=HDR)
    with urllib.request.urlopen(req, timeout=timeout, context=CTX) as r:
        return r.read()


def raw(title, cache_name=None):
    import time
    p = os.path.join(CACHE, (cache_name or ("page_" + re.sub(r"\W+", "_", title))) + ".txt")
    if os.path.exists(p):
        return open(p, encoding="utf-8").read()
    last = None
    for attempt in range(4):
        try:
            d = get("https://zh.kcwiki.cn/index.php?title=%s&action=raw" % urllib.parse.quote(title))
            t = d.decode("utf-8", "replace")
            open(p, "w", encoding="utf-8").write(t)
            return t
        except Exception as e:  # noqa: BLE001
            last = e
            time.sleep(1.5 * (attempt + 1))
    raise last


def strip_html(s):
    s = re.sub(r"<br\s*/?>", "\n", s, flags=re.I)
    s = re.sub(r"</(tr|li|p|div|table)>", "\n", s, flags=re.I)
    s = re.sub(r"</t[dh]>", " | ", s, flags=re.I)
    s = re.sub(r"<[^>]+>", "", s)
    s = html.unescape(s)
    lines = [re.sub(r"\s+", " ", ln).strip(" |\t ") for ln in s.split("\n")]
    lines = [ln for ln in lines if ln and not re.fullmatch(r"[|\- ]+", ln)]
    return lines


def parse_routes(title):
    """把 带路条件 页面的 HTML 表格转成可读文本行。"""
    try:
        t = raw(title)
    except Exception:  # noqa: BLE001
        return None
    if not t or len(t) < 20:
        return None
    if t.strip().startswith("#REDIRECT"):
        m = re.search(r"\[\[([^\]]+)\]\]", t)
        if m:
            return parse_routes(m.group(1).strip())
        return None
    lines = strip_html(t)
    cleaned = []
    for ln in lines:
        if ln in ("分歧点", "条件", "分歧点 | 条件") or re.fullmatch(r"-+", ln):
            continue
        if ln.startswith("转自nga") or ln.startswith("转自NGA"):
            continue
        cleaned.append(ln)
    # 把单独一行的分歧点字母与下一行合并成 "A: ..."
    out = []
    i = 0
    while i < len(cleaned):
        ln = cleaned[i]
        if re.fullmatch(r"[A-Z]{1,2}", ln) and i + 1 < len(cleaned):
            out.append("%s: %s" % (ln, cleaned[i + 1].replace("\n", " ")))
            i += 2
            continue
        out.append(ln)
        i += 1
    if not out:
        return None
    return out


def map_fields(text, template):
    """解析 {{模板|key=value|...}} 的第一个实例字段。"""
    st = text.find("{{" + template)
    if st < 0:
        return {}
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
    body = text[st + 2:end - 2] if end > 0 else ""
    fields = {}
    for part in body.split("|")[1:]:
        eq = part.find("=")
        if eq > 0:
            fields[part[:eq].strip()] = part[eq + 1:].strip()
    return fields


def airforce_of(text):
    """从敌方配置表里取 制空值，生成 [敵制空, 優勢, 確保]（三倍）。"""
    vals = re.findall(r"\|\s*制空值[0-9]*\s*=\s*(\d+)", text)
    nums = [int(v) for v in vals if int(v) > 0]
    if not nums:
        return [0, 0, 0]
    x = max(nums)
    return [x, x * 2, x * 3]




def discover_maps():
    """按 wiki 页面探测实际存在的海域：返回 [(sea, no, 日文原名)]。"""
    result = []
    for sea in range(1, 8):
        area_cn = AREA_ORDER[sea - 1]
        misses = 0
        for no in range(1, 9):
            try:
                page = raw("%s/%d-%d" % (area_cn, sea, no))
            except Exception:  # noqa: BLE001
                page = None
            if not page or len(page) < 200 or page.strip().startswith("#REDIRECT"):
                misses += 1
                if misses >= 2:
                    break
                continue
            misses = 0
            f = map_fields(page, "海域资料改")
            name = (f.get("海域原名") or ("%d-%d" % (sea, no))).strip()
            result.append((sea, no, name))
    return result




def guide_summary(page):
    """抽取「===海域概要===」小节文本（wiki 撰写内容）。"""
    if not page:
        return ""
    m = re.search(r"^=+\s*海域概要\s*=+\s*$", page, re.M)
    if not m:
        return ""
    rest = page[m.end():]
    nxt = re.search(r"^=+[^=\n]+=+\s*$", rest, re.M)
    body = rest[:nxt.start()] if nxt else rest
    lines = []
    for ln in body.split("\n"):
        ln = ln.strip()
        if not ln:
            continue
        if ln.startswith("{{") and ln.endswith("}}") and len(ln) < 60:
            continue
        ln = re.sub(r"^[*#]+\s*", "· ", ln)
        ln = re.sub(r"<br\s*/?>", " ", ln)
        ln = re.sub(r"\{\{[^{}|]*\|([^{}]*)\}\}", r"\1", ln)
        ln = re.sub(r"\[\[([^\]|]*)\|([^\]]*)\]\]", r"\2", ln)
        ln = re.sub(r"\[\[([^\]]*)\]\]", r"\1", ln)
        ln = re.sub(r"<[^>]+>", "", ln)
        ln = html.unescape(ln).strip()
        if ln:
            lines.append(ln)
    return "\n".join(lines[:12])


def main():
    os.makedirs(OUT, exist_ok=True)
    maps = discover_maps()

    entries = []
    stat = {"with_route": 0, "no_route": 0}
    for sea, no, name_ja in maps:
        area_cn = AREA_ORDER[sea - 1]
        title = "%s/%d-%d" % (area_cn, sea, no)
        routes = parse_routes(title + "/带路条件")
        if routes is None:
            stat["no_route"] += 1
            routes = []
        else:
            stat["with_route"] += 1

        air = [0, 0, 0]
        try:
            page = raw(title)
            air = airforce_of(page)
        except Exception:  # noqa: BLE001
            pass

        condition = "\n".join(routes) if routes else "暂无带路数据"

        route_html = None
        try:
            route_html = raw(title + "/带路条件")
        except Exception:  # noqa: BLE001
            route_html = None
        route_tables = routes_mod.parse_route_html(route_html) if route_html else []

        map_no = "%d-%d" % (sea, no)
        images = {
            "banner": "MapBannerHD%s.png" % map_no,
            "map": "MapHD%s.png" % map_no,
        }
        entries.append({
            "sea": sea,
            "area": no,
            "images": images,
            "routes": route_tables,
            "map": "%d-%d %s" % (sea, no, name_ja.strip()),
            "airforce": [air],
            "recommend": ["（见带路条件）"],
            "condition": [condition],
            "other": [guide_summary(page)],
        })

    json.dump(entries, open(os.path.join(OUT, "Map.json"), "w", encoding="utf-8"), ensure_ascii=False)

    # MapType.json
    types = []
    for i, name in enumerate(AREA_ORDER):
        types.append({
            "type": i + 1,
            "name": {
                "zh_cn": name,
                "zh_tw": zhconv.to_tc(name, PROPS),
                "ja": JA_AREA.get(i + 1, name),
            },
        })
    json.dump(types, open(os.path.join(OUT, "MapType.json"), "w", encoding="utf-8"), ensure_ascii=False)

    print("Map.json: %d maps (route %d, no route %d), MapType: %d"
          % (len(entries), stat["with_route"], stat["no_route"], len(types)))


if __name__ == "__main__":
    main()
