"""Fast pass: bundle missing ship thumbnails (no retry storm on 404)."""
import concurrent.futures
import hashlib
import io
import json
import os
import ssl
import threading
import time
import urllib.error
import urllib.request

from PIL import Image

BASE = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\apkwork"
ASSETS = os.path.join(BASE, "appbuild", "app", "src", "main", "assets")
OUT = os.path.join(ASSETS, "shipimg")

CTX = ssl._create_unverified_context()
HDR = {"User-Agent": "Mozilla/5.0 (AkashiToolkit data pipeline)",
       "Referer": "https://zh.kcwiki.cn/"}

THUMB_WIDTH = 360
QUALITY = 72

_lock = threading.Lock()
stats = {"ok": 0, "missing": 0, "error": 0}
missing_names = []


def fetch(name, timeout=25):
    md5 = hashlib.md5(name.encode()).hexdigest()
    url = "https://upload.kcwiki.org/commons/%s/%s/%s" % (md5[0], md5[:2], name)
    req = urllib.request.Request(url, headers=HDR)
    with urllib.request.urlopen(req, timeout=timeout, context=CTX) as r:
        return r.read()


def to_thumb(data):
    im = Image.open(io.BytesIO(data))
    if im.mode != "RGB":
        im = im.convert("RGB")
    if im.width > THUMB_WIDTH:
        ratio = THUMB_WIDTH / float(im.width)
        im = im.resize((THUMB_WIDTH, max(1, int(im.height * ratio))), Image.LANCZOS)
    buf = io.BytesIO()
    im.save(buf, "JPEG", quality=QUALITY, optimize=True, progressive=True)
    return buf.getvalue()


def task(ship):
    sid = ship["id"]
    if sid < 1500:
        wiki = ship.get("wiki_id") or ""
        sources = ["KanMusu%sBanner.jpg" % wiki]
        base = "".join(ch for ch in wiki if ch.isdigit())
        if base and base != wiki:
            sources.append("KanMusu%sBanner.jpg" % base)
        # 横幅缺失时退回立绘
        sources.append("KanMusu%sHDIllust.png" % wiki)
        sources.append("KanMusu%sIllust.png" % wiki)
    else:
        sources = ["ShinkaiSeikan%dBanner.png" % sid]

    target = os.path.join(OUT, sources[0].rsplit(".", 1)[0] + ".jpg")
    if os.path.exists(target) and os.path.getsize(target) > 500:
        with _lock:
            stats["ok"] += 1
        return

    for name in sources:
        try:
            data = fetch(name)
        except urllib.error.HTTPError:
            continue  # 404：换下一个候选
        except Exception:
            with _lock:
                stats["error"] += 1
            return
        try:
            thumb = to_thumb(data)
            open(target, "wb").write(thumb)
            with _lock:
                stats["ok"] += 1
            return
        except Exception:
            continue

    with _lock:
        stats["missing"] += 1
        missing_names.append(sources[0])


def main():
    os.makedirs(OUT, exist_ok=True)
    ships = json.load(open(os.path.join(ASSETS, "Ship.json"), encoding="utf-8"))

    start = time.time()
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        list(pool.map(task, ships))

    open(os.path.join(BASE, "gen", "shipimg_missing.txt"), "w", encoding="utf-8").write(
        "\n".join(missing_names))
    print("ok=%d missing=%d error=%d elapsed=%.1fs" % (
        stats["ok"], stats["missing"], stats["error"], time.time() - start))


if __name__ == "__main__":
    main()
