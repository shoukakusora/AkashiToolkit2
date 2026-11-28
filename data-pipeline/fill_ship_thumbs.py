"""Fill every ship thumbnail referenced by Ship.json (real art if available, else bundled placeholder)."""
import concurrent.futures
import hashlib
import io
import json
import os
import shutil
import ssl
import threading
import urllib.error
import urllib.request

from PIL import Image

BASE = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\apkwork"
ASSETS = os.path.join(BASE, "appbuild", "app", "src", "main", "assets")
OUT = os.path.join(ASSETS, "shipimg")
PLACEHOLDER = os.path.join(BASE, "appbuild", "app", "src", "main", "res", "drawable-nodpi",
                           "ship_thumb_placeholder.jpg")

CTX = ssl._create_unverified_context()
HDR = {"User-Agent": "Mozilla/5.0 (AkashiToolkit data pipeline)",
       "Referer": "https://zh.kcwiki.cn/"}
WIDTH = 360
QUALITY = 72

lock = threading.Lock()
stats = {"real": 0, "placeholder": 0}


def fetch(name):
    md5 = hashlib.md5(name.encode()).hexdigest()
    url = "https://upload.kcwiki.org/commons/%s/%s/%s" % (md5[0], md5[:2], name)
    with urllib.request.urlopen(urllib.request.Request(url, headers=HDR), timeout=30, context=CTX) as r:
        return r.read()


def save_jpeg(data, target):
    im = Image.open(io.BytesIO(data)).convert("RGB")
    if im.width > WIDTH:
        im = im.resize((WIDTH, max(1, im.height * WIDTH // im.width)), Image.LANCZOS)
    buf = io.BytesIO()
    im.save(buf, "JPEG", quality=QUALITY, optimize=True, progressive=True)
    open(target, "wb").write(buf.getvalue())


def task(ship):
    sid = ship["id"]
    if sid < 1500:
        wiki = ship.get("wiki_id") or ""
        base = "".join(ch for ch in wiki if ch.isdigit()) or wiki
        sources = ["KanMusu%sBanner.jpg" % wiki, "KanMusu%sBanner.png" % wiki,
                   "KanMusu%sHDIllust.png" % wiki, "KanMusu%sIllust.png" % wiki,
                   "KanMusu%sBanner.jpg" % base, "KanMusu%sHDIllust.png" % base]
        target_name = "KanMusu%sBanner.jpg" % wiki
    else:
        sources = ["ShinkaiSeikan%dBanner.png" % sid, "ShinkaiSeikan%d.png" % sid,
                   "ShinkaiSeikan%dIllust.png" % sid, "ShinkaiSeikan%dHDBanner.png" % sid,
                   "ShinkaiSeikan%dBanner.png" % (sid - 1000)]
        target_name = "ShinkaiSeikan%dBanner.jpg" % sid

    target = os.path.join(OUT, target_name)
    if os.path.exists(target) and os.path.getsize(target) > 500:
        with lock:
            stats["real"] += 1
        return

    for name in sources:
        try:
            data = fetch(name)
        except urllib.error.HTTPError:
            continue
        except Exception:
            continue
        try:
            save_jpeg(data, target)
            with lock:
                stats["real"] += 1
            return
        except Exception:
            continue

    # 没有官方图：用统一占位立绘补齐，保证不出现空白
    shutil.copyfile(PLACEHOLDER, target)
    with lock:
        stats["placeholder"] += 1


def main():
    ships = json.load(open(os.path.join(ASSETS, "Ship.json"), encoding="utf-8"))
    missing = []
    for ship in ships:
        sid = ship["id"]
        name = ("KanMusu%sBanner.jpg" % ship["wiki_id"]) if sid < 1500 else ("ShinkaiSeikan%dBanner.jpg" % sid)
        if not os.path.exists(os.path.join(OUT, name)):
            missing.append(ship)
    print("missing before:", len(missing))

    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        list(pool.map(task, missing))

    total = 0
    for ship in ships:
        sid = ship["id"]
        name = ("KanMusu%sBanner.jpg" % ship["wiki_id"]) if sid < 1500 else ("ShinkaiSeikan%dBanner.jpg" % sid)
        if not os.path.exists(os.path.join(OUT, name)):
            total += 1
    print("real=%d placeholder=%d | still missing=%d" % (stats["real"], stats["placeholder"], total))


if __name__ == "__main__":
    main()
