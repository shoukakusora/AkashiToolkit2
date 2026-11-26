"""Generate Construction.json (建造公式) from zh.kcwiki.cn/wiki/建造.

Entries:
  * ordinary recipes (`*30/30/30/30` + `**` notes)
  * per-ship "常用公式" table rows (舰娘 / 建造时间 / 公式 / 备注)
  * large-ship construction secretary table (目标舰 / 需要秘书舰)
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

FORMULA = re.compile(r"^\*+\s*(\d{1,4})\s*/\s*(\d{1,4})\s*/\s*(\d{1,4})\s*/\s*(\d{1,4})\s*$")
FORMULA4 = re.compile(r"(\d{1,4})\s*/\s*(\d{1,4})\s*/\s*(\d{1,4})\s*/\s*(\d{1,4})")


def raw(title):
    path = os.path.join(CACHE, "page_" + re.sub(r"\W+", "_", title) + ".txt")
    if os.path.exists(path) and os.path.getsize(path) > 1000:
        return open(path, encoding="utf-8").read()
    url = "https://zh.kcwiki.cn/index.php?title=%s&action=raw" % urllib.parse.quote(title)
    last = None
    for _ in range(3):
        try:
            text = urllib.request.urlopen(
                urllib.request.Request(url, headers=HDR), timeout=180, context=CTX
            ).read().decode("utf-8", "replace")
            open(path, "w", encoding="utf-8").write(text)
            return text
        except Exception as e:  # noqa: BLE001
            last = e
    raise last


def clean(s):
    if not s:
        return ""
    s = s.replace("\u00a0", " ")
    s = re.sub(r"\[\[(?:File|文件|Image):[^\]]*\]\]", "", s, flags=re.I)
    s = re.sub(r"\[\[([^\[\]|]*)\|([^\[\]]*)\]\]", r"\2", s)
    s = re.sub(r"\[\[([^\[\]]*)\]\]", r"\1", s)
    s = re.sub(r"\{\{[^{}|]*\|([^{}]*)\}\}", r"\1", s)
    s = re.sub(r"\{\{[^{}]*\}\}", "", s)
    s = re.sub(r"<[^>]+>", "", s)
    s = s.replace("'''", "").replace("''", "")
    s = re.sub(r'^(?:rowspan|colspan)="\d+"\s*\|\s*', "", s)
    s = s.replace("[[", "").replace("]]", "")
    s = s.replace("||", "、")
    return s.strip()


def split_sections(text):
    parts = re.split(r"^=+\s*([^=\n]+?)\s*=+\s*$", text, flags=re.M)
    out = []
    for i in range(1, len(parts) - 1, 2):
        out.append((parts[i].strip(), parts[i + 1]))
    return out


def parse_wiki_table(segment):
    """把一段 wiki 表格解析为 [[cell, cell], ...]（跳过表头）。"""
    rows = []
    row = []
    in_table = False
    header = False
    for line in segment.split("\n"):
        line = line.rstrip()
        stripped = line.strip()
        if stripped.startswith("{|"):
            in_table = True
            continue
        if not in_table:
            continue
        if stripped.startswith("|}"):
            break
        if stripped.startswith("|-"):
            if row:
                rows.append(row)
                row = []
            header = False
            continue
        if stripped.startswith("!"):
            header = True
            continue
        if stripped.startswith("|") and not header:
            for part in stripped[1:].split("||"):
                row.append(clean(part))
    if row:
        rows.append(row)
    return rows


def parse_ordinary(body):
    items = []
    current = None
    for line in body.split("\n"):
        m = FORMULA.match(line.strip())
        if m:
            current = {
                "fuel": int(m.group(1)),
                "ammo": int(m.group(2)),
                "steel": int(m.group(3)),
                "bauxite": int(m.group(4)),
                "target": "",
                "note": [],
                "large": False,
            }
            items.append(current)
            continue
        if current is not None and line.strip().startswith("**"):
            text = clean(line.strip().lstrip("*").strip())
            if text:
                current["note"].append(text)
    for item in items:
        note = item.pop("note")
        item["note"] = "\n".join(note)
        if note:
            item["target"] = note[0].split("。")[0][:40]
    return items


def parse_targets(body):
    items = []
    for cells in parse_wiki_table(body):
        if len(cells) < 3:
            continue
        ship, time_text, formula_text = cells[0], cells[1], cells[2]
        notes = [c for c in cells[3:] if c]
        m = FORMULA4.search(formula_text)
        if not m or not ship:
            continue
        items.append({
            "fuel": int(m.group(1)),
            "ammo": int(m.group(2)),
            "steel": int(m.group(3)),
            "bauxite": int(m.group(4)),
            "target": ship,
            "note": "\n".join(
                ([time_text] if time_text else []) + [formula_text] + notes),
            "large": True,
        })
    return items


def parse_secretary(segment):
    items = []
    for cells in parse_wiki_table(segment):
        if len(cells) < 2:
            continue
        target = cells[0].strip()
        secretary = cells[1].strip()
        if not target or not secretary or target.startswith("目标舰"):
            continue
        items.append({
            "fuel": 0,
            "ammo": 0,
            "steel": 0,
            "bauxite": 0,
            "target": target,
            "note": "需要秘书舰：" + secretary,
            "large": True,
        })
    return items


def main():
    os.makedirs(OUT, exist_ok=True)
    text = raw("建造")

    ordinary = []
    targets = []
    for title, body in split_sections(text):
        if "建造公式" in title or "普通建造" in title:
            ordinary.extend(parse_ordinary(body))
        elif "目标舰娘" in title:
            targets.extend(parse_targets(body))

    secretary = []
    idx = text.find("大建时需要指定秘书舰")
    if idx >= 0:
        table_start = text.rfind("{|", 0, idx)
        table_end = text.find("|}", idx)
        if table_start >= 0 and table_end > table_start:
            secretary = parse_secretary(text[table_start:table_end + 2])

    data = ordinary + targets + secretary
    if not data:
        data = parse_ordinary(text)

    for path in (os.path.join(OUT, "Construction.json"),
                 os.path.join(ASSETS, "Construction.json")):
        json.dump(data, open(path, "w", encoding="utf-8"), ensure_ascii=False)

    print("Construction.json: %d entries (ordinary %d, targets %d, secretary %d)" % (
        len(data), len(ordinary), len(targets), len(secretary)))
    for item in data[:2] + data[-2:]:
        print("  ", item["fuel"], item["ammo"], item["steel"], item["bauxite"],
              "|", item["target"][:24], "|", item["note"][:40])


if __name__ == "__main__":
    main()
