"""Build Event.json (活动/限定海域) and server/events.json from wiki event pages."""
import json
import os
import re
import ssl
import urllib.parse
import urllib.request

BASE = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\apkwork"
CACHE = os.path.join(BASE, "gen", "cache")
ASSETS = os.path.join(BASE, "appbuild", "app", "src", "main", "assets")
SERVER = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\akashi-toolkit\server"

CTX = ssl._create_unverified_context()
HDR = {"User-Agent": "Mozilla/5.0 (AkashiToolkit data pipeline)"}

SEASONS = ("冬季", "春季", "夏季", "秋季")
YEARS = ("2026", "2025", "2024")


def raw(title):
    path = os.path.join(CACHE, "page_" + re.sub(r"\W+", "_", title) + ".txt")
    if os.path.exists(path) and os.path.getsize(path) > 500:
        return open(path, encoding="utf-8").read()
    url = "https://zh.kcwiki.cn/index.php?title=%s&action=raw" % urllib.parse.quote(title)
    for _ in range(2):
        try:
            text = urllib.request.urlopen(
                urllib.request.Request(url, headers=HDR), timeout=120, context=CTX
            ).read().decode("utf-8", "replace")
            open(path, "w", encoding="utf-8").write(text)
            return text
        except Exception:  # noqa: BLE001
            continue
    return ""


def clean(s):
    s = s.replace("\u00a0", " ")
    s = re.sub(r"\[\[(?:File|文件|Image):[^\]]*\]\]", "", s, flags=re.I)
    s = re.sub(r"\[\[([^\[\]|]*)\|([^\[\]]*)\]\]", r"\2", s)
    s = re.sub(r"\[\[([^\[\]]*)\]\]", r"\1", s)
    s = re.sub(r"\{\{(?:Green|Red|Blue|Purple|Color|Colour)\s*\|([^{}]*)\}\}", r"\1", s, flags=re.I)
    s = re.sub(r"\{\{[^{}]*\}\}", "", s)
    s = re.sub(r"<[^>]+>", "\n", s)
    s = s.replace("'''", "").replace("''", "")
    return s


def text_of(page, limit_lines=40):
    body = clean(page)
    lines = []
    for line in body.split("\n"):
        line = line.strip()
        if not line:
            continue
        if line.lower().startswith("file:"):
            continue
        if line.startswith("|") or line.startswith("{|") or line.startswith("|-"):
            continue
        if re.fullmatch(r"=+.*=+", line):
            continue
        lines.append(line)
        if len(lines) >= limit_lines:
            break
    return "\n".join(lines)


def images_of(page, limit=10):
    out = []
    for m in re.finditer(r"\[\[(?:File|文件|Image):([^\]|]+)", page, re.I):
        name = m.group(1).strip().replace(" ", "_")
        if re.search(r"\.(png|jpg|jpeg)$", name, re.I) and name not in out:
            out.append(name)
        if len(out) >= limit:
            break
    return out


def main():
    titles = ["2026年夏季活动", "2025年冬季活动", "2025年秋季活动", "2025年夏季活动"]
    entries = []
    for title in titles:
        page = raw(title)
        print("fetch", title, "len", len(page))
        if not page or len(page) < 800:
            continue
        year = title[:4]
        entries.append({
            "id": title,
            "title": title,
            "date": year,
            "text": text_of(page),
            "link": "https://zh.kcwiki.cn/wiki/" + urllib.parse.quote(title.replace(" ", "_")),
            "images": images_of(page),
        })
        if len(entries) >= 6:
            break

    if not entries:
        print("no event pages found")
        return

    for path in (os.path.join(ASSETS, "Event.json"),
                 os.path.join(SERVER, "events.json")):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        json.dump(entries, open(path, "w", encoding="utf-8"), ensure_ascii=False)

    print("Event.json: %d entries" % len(entries))
    for e in entries:
        print("  %-16s text=%d imgs=%d" % (e["title"], len(e["text"]), len(e["images"])))


if __name__ == "__main__":
    main()
