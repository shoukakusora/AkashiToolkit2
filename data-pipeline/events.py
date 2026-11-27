"""Build Event.json (活动/限定海域) and server/events.json from wiki event pages."""
import html
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
    s = html.unescape(s)
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
        # 跳过倒计时模板残留
        if re.search(r"\{[A-Z]+\}", line) or "countdown" in line.lower():
            continue
        if line in ("活动已经开始", "活动已经结束"):
            continue
        if line.startswith("!") or line.startswith("/"):
            continue
        if not re.search(r"[\u4e00-\u9fff0-9A-Za-z]", line):
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


TITLES = {
    "2026年夏季活动": (
        "2026年夏季活动 反击！第三十一战队的战斗 / 法兰西舰队的跃动",
        "2026年7月8日 21:00 — 2026年9月10日 10:00（已结束）",
        "共 5 张海域（E-1～E-5）。新舰娘：桐、花月、里诺、印第安纳、独立、秃鹫、阿尔及利亚、维斯比、日枝丸、贝阿恩。",
    ),
}


def main():
    titles = ["2026年夏季活动", "2025年冬季活动", "2025年秋季活动", "2025年夏季活动"]
    entries = []
    for title in titles:
        page = raw(title)
        print("fetch", title, "len", len(page))
        if not page or len(page) < 800:
            continue
        year = title[:4]
        override = TITLES.get(title)
        full_title = override[0] if override else title
        date_line = override[1] if override else ""
        if override and len(override) > 2:
            body = (date_line + "\n" + override[2]).strip()
        else:
            body = date_line
        entries.append({
            "id": title,
            "title": full_title,
            "date": year,
            "text": body,
            "link": "https://zh.kcwiki.cn/wiki/" + urllib.parse.quote(title.replace(" ", "_")),
            "images": images_of(page),
        })
        if len(entries) >= 6:
            break

    if not entries:
        print("no event pages found")
        return

    for path in (os.path.join(ASSETS, "Event2.json"),
                 os.path.join(SERVER, "events.json")):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        json.dump(entries, open(path, "w", encoding="utf-8"), ensure_ascii=False)

    print("Event.json: %d entries" % len(entries))
    for e in entries:
        print("  %-16s text=%d imgs=%d" % (e["title"], len(e["text"]), len(e["images"])))


if __name__ == "__main__":
    main()
