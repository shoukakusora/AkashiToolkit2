"""Rebuild player thumbnails from KanMusu<3-digit>HDBanner.png (per user request)."""
import concurrent.futures
import hashlib
import io
import json
import os
import re
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


def thumb_name(ship):
    digits = re.sub(r"\D", "", str(ship.get("wiki_id") or ""))
    no = int(digits) if digits else 0
    return "KanMusu%03dHDBanner.jpg" % no


def task(ship):
    digits = re.sub(r"\D", "", str(ship.get("wiki_id") or ""))
    no = int(digits) if digits else 0
    if no <= 0:
        target = os.path.join(OUT, thumb_name(ship))
        shutil.copyfile(PLACEHOLDER, target)
        with lock:
            stats["placeholder"] += 1
        return

    sources = ["KanMusu%03dHDBanner.png" % no,
               "KanMusu%03dHDBanner.jpg" % no,
               "KanMusu%03dBanner.png" % no,
               "KanMusu%03dBanner.jpg" % no,
               "KanMusu%03dHDIllust.png" % no,
               "KanMusu%03dIllust.png" % no]
    target = os.path.join(OUT, thumb_name(ship))
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

    shutil.copyfile(PLACEHOLDER, target)
    with lock:
        stats["placeholder"] += 1


def main():
    ships = json.load(open(os.path.join(ASSETS, "Ship.json"), encoding="utf-8"))
    players = [s for s in ships if s["id"] < 1500]

    # 清掉旧的 KanMusu*.jpg（改用 HDBanner 命名）
    for name in os.listdir(OUT):
        if name.startswith("KanMusu") and name.endswith(".jpg"):
            os.remove(os.path.join(OUT, name))

    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        list(pool.map(task, players))

    print("players=%d real=%d placeholder=%d" % (len(players), stats["real"], stats["placeholder"]))
    total = sum(os.path.getsize(os.path.join(OUT, n)) for n in os.listdir(OUT))
    print("shipimg files=%d total=%.2f MB" % (len(os.listdir(OUT)), total / 1048576.0))


if __name__ == "__main__":
    main()
