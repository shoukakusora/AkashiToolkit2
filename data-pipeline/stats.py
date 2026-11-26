"""Generate Development.json (开发公式) and Drop.json (掉落统计).

Sources:
  - zh.kcwiki.cn/wiki/开发  (公式表, db.kcwiki.cn/development 的同源数据)
  - appbuild assets MapDetail.json (各海域掉落表, db.kcwiki.cn/drop 的同源数据)
"""
import json
import os
import re
import ssl
import urllib.parse
import urllib.request

BASE = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\apkwork"
CACHE = os.path.join(BASE, "gen", "cache")
OUT = os.path.join(BASE, "gen", "out_assets")
ASSETS = os.path.join(BASE, "appbuild", "app", "src", "main", "assets")

CTX = ssl._create_unverified_context()
HDR = {"User-Agent": "Mozilla/5.0 (AkashiToolkit data pipeline)"}


def raw(title):
    path = os.path.join(CACHE, "page_" + re.sub(r"\W+", "_", title) + ".txt")
    if os.path.exists(path):
        return open(path, encoding="utf-8").read()
    url = "https://zh.kcwiki.cn/index.php?title=%s&action=raw" % urllib.parse.quote(title)
    text = urllib.request.urlopen(
        urllib.request.Request(url, headers=HDR), timeout=90, context=CTX
    ).read().decode("utf-8", "replace")
    open(path, "w", encoding="utf-8").write(text)
    return text


def clean(s):
    if not s:
        return ""
    s = re.sub(r"\[\[(?:File|文件|Image):[^\]]*\]\]", "", s, flags=re.I)
    s = re.sub(r"\[\[([^\]|]*)\|([^\]]*)\]\]", r"\2", s)
    s = re.sub(r"\[\[([^\]]*)\]\]", r"\1", s)
    s = re.sub(r"\{\{[^{}|]*\|([^{}]*)\}\}", r"\1", s)
    s = re.sub(r"\{\{[^{}]*\}\}", "", s)
    s = re.sub(r"<[^>]+>", "", s)
    s = s.replace("'''", "").replace("''", "")
    s = re.sub(r"\s+", " ", s)
    return s.strip()


def num(s):
    m = re.search(r"-?\d+", clean(s).replace(",", ""))
    return int(m.group(0)) if m else 0


def tables(text):
    """Yield raw table blocks (list of row strings)."""
    for m in re.finditer(r"\{\|.*?\n\|\}", text, re.S):
        body = m.group(0)
        rows = re.split(r"\n\|-", body)
        yield rows


def split_cells(row):
    cells = []
    for line in row.split("\n"):
        line = line.strip()
        if not line:
            continue
        if line.startswith("|") or line.startswith("!"):
            line = line[1:]
        parts = re.split(r"\|\||!!", line)
        for p in parts:
            p = p.strip()
            # 形如 style="..."|10 的单元格，取最后一个 | 之后的内容
            if "|" in p:
                p = p.rsplit("|", 1)[-1]
            cells.append(p.strip())
    return cells


def development():
    text = raw("开发")
    out = []
    for body in re.findall(r"\{\|.*?\n\|\}", text, re.S):
        lines = body.split("\n")
        header = None
        row = []
        rows = []
        for line in lines:
            line = line.rstrip()
            if line.strip().startswith("|-") or line.strip() == "|}":
                if row:
                    rows.append(row)
                    row = []
                continue
            if line.strip().startswith("!"):
                if header is None:
                    header = [clean(c) for c in split_cells(line)]
                continue
            if line.strip().startswith("|") and header is not None:
                row.append(line)
        if row:
            rows.append(row)

        for row_lines in rows:
            cells = []
            for ln in row_lines:
                cells.extend(split_cells(ln))
            if len(cells) < 6:
                continue
            target = clean(cells[0])
            ship = clean(cells[1])
            fuel, ammo, steel, bauxite = (num(cells[2]), num(cells[3]), num(cells[4]), num(cells[5]))
            if not (fuel or ammo or steel or bauxite):
                continue
            out.append({
                "target": target,
                "ship": ship,
                "fuel": fuel,
                "ammo": ammo,
                "steel": steel,
                "bauxite": bauxite,
                "result": clean(cells[6]) if len(cells) > 6 else "",
                "extra": clean(cells[7]) if len(cells) > 7 else "",
                "extraResult": clean(cells[8]) if len(cells) > 8 else "",
            })

    seen = set()
    unique = []
    for r in out:
        key = (r["target"], r["fuel"], r["ammo"], r["steel"], r["bauxite"], r["ship"])
        if key in seen:
            continue
        seen.add(key)
        unique.append(r)
    return unique


RATE_RE = re.compile(r"([\d.]+)\s*%")


def drop():
    detail = json.load(open(os.path.join(ASSETS, "MapDetail.json"), encoding="utf-8"))
    ships = {s["id"]: s["name"]["zh_cn"] for s in json.load(
        open(os.path.join(ASSETS, "Ship.json"), encoding="utf-8"))}

    out = []
    for entry in detail:
        map_name = (entry.get("name") or {}).get("zh_cn") or ""
        if not map_name:
            continue
        nodes = []
        for node in entry.get("drop") or []:
            ids = node.get("ship") or []
            names = [ships.get(i) for i in ids if ships.get(i)]
            if names:
                nodes.append({"name": node.get("name") or "", "ships": names})
        if not nodes:
            continue
        map_id = entry.get("id") or 0
        label = ("%d-%d %s" % (map_id // 10, map_id % 10, map_name)) if map_id else map_name
        out.append({"map": label, "nodes": nodes})
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    dev = development()
    dr = drop()
    json.dump(dev, open(os.path.join(OUT, "Development.json"), "w", encoding="utf-8"),
              ensure_ascii=False)
    json.dump(dr, open(os.path.join(OUT, "Drop.json"), "w", encoding="utf-8"),
              ensure_ascii=False)
    for name in ("Development.json", "Drop.json"):
        json.dump(json.load(open(os.path.join(OUT, name), encoding="utf-8")),
                  open(os.path.join(ASSETS, name), "w", encoding="utf-8"),
                  ensure_ascii=False)
    print("Development.json: %d formulas" % len(dev))
    print("Drop.json: %d maps, %d nodes" % (len(dr), sum(len(x["nodes"]) for x in dr)))
    if dev:
        print("sample formula:", json.dumps(dev[0], ensure_ascii=False))


if __name__ == "__main__":
    main()
