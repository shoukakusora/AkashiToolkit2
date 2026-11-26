"""Bundle equipment artwork (Soubi<id>Full.png) for the equip detail page."""
import concurrent.futures
import hashlib
import io
import json
import os
import ssl
import threading
import urllib.error
import urllib.request

from PIL import Image

BASE = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\apkwork"
ASSETS = os.path.join(BASE, "appbuild", "app", "src", "main", "assets")
OUT = os.path.join(ASSETS, "equipimg")

CTX = ssl._create_unverified_context()
HDR = {"User-Agent": "Mozilla/5.0 (AkashiToolkit data pipeline)",
       "Referer": "https://zh.kcwiki.cn/"}

WIDTH = 240
QUALITY = 74

_lock = threading.Lock()
stats = {"ok": 0, "missing": 0, "bytes": 0}


def fetch(name):
    md5 = hashlib.md5(name.encode()).hexdigest()
    url = "https://upload.kcwiki.org/commons/%s/%s/%s" % (md5[0], md5[:2], name)
    req = urllib.request.Request(url, headers=HDR)
    with urllib.request.urlopen(req, timeout=30, context=CTX) as r:
        return r.read()


def task(equip):
    eid = equip["id"]
    if eid >= 1500:
        return
    names = ["Soubi%03dFull.png" % eid, "Soubi%03dHDFull.png" % eid]
    target = os.path.join(OUT, ("Soubi%03dFull.jpg" % eid))
    if os.path.exists(target) and os.path.getsize(target) > 500:
        with _lock:
            stats["ok"] += 1
            stats["bytes"] += os.path.getsize(target)
        return
    data = None
    for name in names:
        try:
            data = fetch(name)
            break
        except urllib.error.HTTPError:
            continue
        except Exception:
            continue
    if data is None:
        with _lock:
            stats["missing"] += 1
        return

    try:
        im = Image.open(io.BytesIO(data))
        if im.mode != "RGB":
            im = im.convert("RGB")
        if im.width > WIDTH:
            ratio = WIDTH / float(im.width)
            im = im.resize((WIDTH, max(1, int(im.height * ratio))), Image.LANCZOS)
        buf = io.BytesIO()
        im.save(buf, "JPEG", quality=QUALITY, optimize=True, progressive=True)
        thumb = buf.getvalue()
        open(target, "wb").write(thumb)
        with _lock:
            stats["ok"] += 1
            stats["bytes"] += len(thumb)
    except Exception:
        with _lock:
            stats["missing"] += 1


def main():
    os.makedirs(OUT, exist_ok=True)
    equips = json.load(open(os.path.join(ASSETS, "Equip.json"), encoding="utf-8"))
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        list(pool.map(task, equips))
    print("equip images: ok=%d missing=%d total=%.2f MB" % (
        stats["ok"], stats["missing"], stats["bytes"] / 1048576.0))


if __name__ == "__main__":
    main()
