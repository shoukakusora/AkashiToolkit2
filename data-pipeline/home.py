"""Generate Home.json (three wiki columns) and Event.json (seasonal events) from zh.kcwiki.cn."""
import json
import os
import re
import ssl
import time
import urllib.parse
import urllib.request

import update_text as update_text_mod

BASE = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\apkwork"
CACHE = os.path.join(BASE, "gen", "cache")
OUT = os.path.join(BASE, "gen", "out_assets")

CTX = ssl._create_unverified_context()
HDR = {"User-Agent": "Mozilla/5.0 (AkashiToolkit data pipeline)"}
API = "https://zh.kcwiki.cn/api.php"


def _get(url, retries=5, timeout=90):
    last = None
    for i in range(retries):
        try:
            req = urllib.request.Request(url, headers=HDR)
            with urllib.request.urlopen(req, timeout=timeout, context=CTX) as r:
                return r.read().decode("utf-8", "replace")
        except Exception as e:  # noqa: BLE001
            last = e
            time.sleep(2 * (i + 1))
    raise last


def raw(title):
    p = os.path.join(CACHE, "page_" + re.sub(r"\W+", "_", title) + ".txt")
    if os.path.exists(p):
        return open(p, encoding="utf-8").read()
    t = _get("https://zh.kcwiki.cn/index.php?title=%s&action=raw" % urllib.parse.quote(title))
    open(p, "w", encoding="utf-8").write(t)
    return t


def api(params):
    return json.loads(_get(API + "?" + urllib.parse.urlencode(params)))


def strip_wiki(s, keep_lines=True):
    if not s:
        return ""
    s = re.sub(r"<ref[^>]*>.*?</ref>", "", s, flags=re.S)
    s = re.sub(r"<ref[^>]*/>", "", s)
    s = re.sub(r"\[\[File:[^\]]*\]\]", "", s, flags=re.I)
    s = re.sub(r"\[\[([^\]|]*)\|([^\]]*)\]\]", r"\2", s)
    s = re.sub(r"\[\[([^\]]*)\]\]", r"\1", s)
    s = re.sub(r"\{\{[^{}|]*\|([^{}]*)\}\}", r"\1", s)
    s = re.sub(r"\{\{[^{}]*\}\}", "", s)
    s = re.sub(r"<br\s*/?>", "\n", s, flags=re.I)
    s = re.sub(r"<[^>]+>", "", s)
    s = s.replace("'''", "").replace("''", "")
    lines = [ln.strip() for ln in s.split("\n")]
    lines = [ln for ln in lines if ln and not ln.startswith("|") and not ln.startswith("{|")]
    return "\n".join(lines[:20]) if keep_lines else " ".join(lines)




def clean_text(body, limit=None):
    """去掉 gallery/标题/表格/文件行，只留可读文字（limit=None 保留全文）。"""
    body = re.sub(r"<gallery[^>]*>.*?</gallery>", "", body, flags=re.S | re.I)
    keep = []
    for ln in body.split("\n"):
        ln = ln.strip()
        if not ln:
            continue
        if re.fullmatch(r"=+.*=+", ln) or re.fullmatch(r"\[=]+\]", ln):
            continue
        if ln.lower().startswith("file:") or ln.startswith("[[File:") or ln.startswith("文件:"):
            continue
        if ln.startswith("|") or ln.startswith("{|") or ln.startswith("|-"):
            continue
        if len(ln) < 2:
            continue
        if not re.search(r"[\u4e00-\u9fff0-9A-Za-z]", ln):
            continue
        keep.append(ln)
        if limit is not None and len(keep) >= limit:
            break
    return "\n".join(keep)


def images_of(text, limit=8):
    """收集文件：支持 [[File:..]]、[[文件:..]] 以及 <gallery> 里的 File: 行。"""
    if not text:
        return []
    candidates = []
    for m in re.finditer(r"\[\[(?:File|文件|Image):([^\]|]+)", text, re.I):
        candidates.append(m.group(1).strip())
    for gallery in re.finditer(r"<gallery[^>]*>(.*?)</gallery>", text, re.S | re.I):
        for line in gallery.group(1).split("\n"):
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            name = line.split("|")[0].strip()
            if name.lower().startswith(("file:", "文件:", "image:")):
                name = name.split(":", 1)[1].strip()
            if name:
                candidates.append(name)

    # 优先 HDBanner / HDIllust，其次普通 Banner / Illust
    def score(n):
        low = n.lower()
        if "hdbanner" in low:
            return 0
        if "hdillust" in low:
            return 1
        if "banner" in low:
            return 2
        if "illust" in low:
            return 3
        if low.startswith("2026") or "item" in low:
            return 4
        return 5

    seen = []
    for name in sorted(candidates, key=score):
        # MediaWiki 文件名等价于把空格换成下划线；含空格/控制字符的条目是解析噪声
        name = name.replace(" ", "_").strip()
        if not name or re.search(r"[\s\x00-\x1f]", name):
            continue
        if not re.search(r"\.(png|jpg|jpeg)$", name, re.I):
            continue
        if name not in seen:
            seen.append(name)
        if len(seen) >= limit:
            break
    return seen


def page_image(title):
    """通过 API 取页面首图（gallery 解析不到时的兜底）。"""
    try:
        data = api({"action": "query", "format": "json", "formatversion": "2",
                    "prop": "pageimages", "piprop": "original", "titles": title})
        for page in data.get("query", {}).get("pages", []):
            orig = page.get("original") or {}
            src = orig.get("source") or ""
            if src:
                name = urllib.parse.unquote(src.rsplit("/", 1)[-1])
                if name:
                    return [name]
    except Exception:  # noqa: BLE001
        pass
    return []


def section(text, name):
    """Extract a level-2/3 section body by heading name."""
    m = re.search(r"^=+\s*" + re.escape(name) + r"\s*=+\s*$", text, re.M)
    if not m:
        return ""
    rest = text[m.end():]
    nxt = re.search(r"^=+[^=\n]+=+\s*$", rest, re.M)
    return rest[:nxt.start()] if nxt else rest


def bullets(text):
    items = []
    for m in re.finditer(r"^[*#]+\s*(.+)$", text, re.M):
        line = m.group(1).strip()
        if not line or line.startswith("|"):
            continue
        t = strip_wiki(line, keep_lines=False)
        if not t:
            continue
        link = ""
        lm = re.search(r"\[\[([^\]|]+)", line)
        if lm:
            link = "https://zh.kcwiki.cn/wiki/" + urllib.parse.quote(lm.group(1).replace(" ", "_"))
        items.append({"title": t, "text": t, "link": link, "images": images_of(line, 2)})
    return items


def game_info():
    tpl = raw("模板:游戏情报")
    m = re.search(r"#lsth:([^|}\n]+)\|([^}\n]+)", tpl)
    page = m.group(1).strip() if m else "游戏情报"
    sec = m.group(2).strip() if m else "最新情报"
    text = raw(page)
    body = section(text, sec) or text
    return bullets(body)


def updates():
    data = api({"action": "query", "format": "json", "list": "allpages",
                "apprefix": "游戏更新/", "aplimit": "200", "apdir": "descending"})
    titles = [p["title"] for p in data.get("query", {}).get("allpages", [])]

    def key(t):
        m = re.search(r"(\d{4})年(\d{1,2})月(\d{1,2})日", t)
        return (int(m.group(1)), int(m.group(2)), int(m.group(3))) if m else (0, 0, 0)

    titles.sort(key=key, reverse=True)
    items = []
    for title in titles[:4]:
        text = raw(title)
        date = title.split("/")[-1]
        body = update_text_mod.update_text(text)
        if not body:
            body = clean_text(strip_wiki(text))
        if not body:
            continue
        imgs = images_of(text, 6)
        if not imgs:
            imgs = page_image(title)
        items.append({
            "title": date + " 游戏更新",
            "text": body,
            "link": "https://zh.kcwiki.cn/wiki/" + urllib.parse.quote(title.replace(" ", "_")),
            "images": imgs,
        })
        if len(items) >= 3:
            break
    return items


def seasonal():
    """按「季节性」页的年份小节解析，最新的季节排在最前。"""
    text = raw("季节性")

    years = []          # [(year, [(title, path), ...])]
    cur_year = ""
    cur_items = []
    for line in text.split("\n"):
        line = line.strip()
        hm = re.match(r"^==\s*(\d{4})年\s*==$", line)
        if hm:
            if cur_year and cur_items:
                years.append((cur_year, cur_items))
            cur_year = hm.group(1)
            cur_items = []
            continue
        lm = re.match(r"^\*\s*\[\[(/[^\]|]+)\|([^\]]+)\]\]", line)
        if lm and cur_year:
            cur_items.append((lm.group(2).strip(), lm.group(1).strip()))
    if cur_year and cur_items:
        years.append((cur_year, cur_items))

    years.sort(key=lambda x: x[0], reverse=True)

    events = []
    for year, items in years:
        # 同年内 wiki 按时间顺序排列，倒序让最新季节在前
        for title, path in reversed(items):
            page_title = "季节性" + path
            try:
                page = raw(page_title)
            except Exception:  # noqa: BLE001
                page = ""
            imgs = images_of(page, 8)
            if not imgs:
                imgs = page_image(page_title)
            body = clean_text(strip_wiki(page)) if page else ""
            events.append({
                "id": path.strip("/").replace("/", "-"),
                "title": title,
                "date": year,
                "text": body,
                "link": "https://zh.kcwiki.cn/wiki/" + urllib.parse.quote(page_title.replace(" ", "_")),
                "images": imgs,
            })
            if len(events) >= 14:
                break
    events = events[:14]
    # 仅最新季节展示完整内容；已过的季节只保留标题
    for i, event in enumerate(events):
        if i == 0:
            continue
        event["text"] = ""
        event["images"] = []
    return events


def main():
    os.makedirs(OUT, exist_ok=True)
    gi = game_info()
    up = updates()
    se = seasonal()
    home = {
        "generated": time.strftime("%Y-%m-%d"),
        "game_info": gi,
        "updates": up,
        "seasonal": se,
    }
    json.dump(home, open(os.path.join(OUT, "Home3.json"), "w", encoding="utf-8"), ensure_ascii=False)
    print("Home.json: game_info=%d updates=%d seasonal=%d" % (len(gi), len(up), len(se)))


if __name__ == "__main__":
    main()
