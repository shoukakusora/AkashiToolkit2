"""Bundle ship thumbnails into the APK (assets/shipimg) so lists never touch the network.

Player ships: KanMusu<wiki_id>Banner.jpg
Enemy ships:  ShinkaiSeikan<id>Banner.png

Images are resized to a thumbnail width and stored as JPEG to keep the APK small.
"""
import concurrent.futures
import hashlib
import io
import json
import os
import ssl
import threading
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
_stats = {"ok": 0, "fail": 0, "bytes": 0}


import time


def fetch(name, retries=4):
    md5 = hashlib.md5(name.encode()).hexdigest()
    url = "https://upload.kcwiki.org/commons/%s/%s/%s" % (md5[0], md5[:2], name)
    last = None
    for i in range(retries):
        try:
            req = urllib.request.Request(url, headers=HDR)
            with urllib.request.urlopen(req, timeout=60, context=CTX) as r:
                return r.read()
        except Exception as e:  # noqa: BLE001
            last = e
            time.sleep(1.5 * (i + 1))
    raise last


def to_thumb(data):
    im = Image.open(io.BytesIO(data))
    if im.mode in ("P", "RGBA", "LA"):
        im = im.convert("RGB")
    elif im.mode != "RGB":
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
        wiki = ship.get("wiki_id")
        if not wiki:
            return
        source = "KanMusu%sBanner.jpg" % wiki
    else:
        source = "ShinkaiSeikan%dBanner.png" % sid

    target = os.path.join(OUT, source.rsplit(".", 1)[0] + ".jpg")
    if os.path.exists(target) and os.path.getsize(target) > 500:
        with _lock:
            _stats["ok"] += 1
            _stats["bytes"] += os.path.getsize(target)
        return

    candidates = [source]
    # 改造舰可能没有单独的横幅，退回基础编号
    if sid < 1500:
        base = "".join(ch for ch in (ship.get("wiki_id") or "") if ch.isdigit())
        if base:
            candidates.append("KanMusu%sBanner.jpg" % base)

    for name in candidates:
        try:
            data = fetch(name)
            thumb = to_thumb(data)
            open(target, "wb").write(thumb)
            with _lock:
                _stats["ok"] += 1
                _stats["bytes"] += len(thumb)
            return
        except Exception:  # noqa: BLE001
            continue

    with _lock:
        _stats["fail"] += 1
        with open(os.path.join(BASE, "gen", "shipimg_fail.txt"), "a", encoding="utf-8") as fh:
            fh.write(source + "\n")


def main():
    os.makedirs(OUT, exist_ok=True)
    ships = json.load(open(os.path.join(ASSETS, "Ship.json"), encoding="utf-8"))

    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        list(pool.map(task, ships))

    print("thumbnails: ok=%d fail=%d total=%.2f MB" % (
        _stats["ok"], _stats["fail"], _stats["bytes"] / 1048576.0))


if __name__ == "__main__":
    main()
