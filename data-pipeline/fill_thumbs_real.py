"""Fetch real art only (no placeholders) for ships that still lack a bundled thumbnail."""
import concurrent.futures
import hashlib
import io
import json
import os
import re
import ssl
import threading
import urllib.error
import urllib.request

from PIL import Image

BASE = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\apkwork"
ASSETS = os.path.join(BASE, "appbuild", "app", "src", "main", "assets")
OUT = os.path.join(ASSETS, "shipimg")
CTX = ssl._create_unverified_context()
HDR = {"User-Agent": "Mozilla/5.0 (AkashiToolkit data pipeline)",
       "Referer": "https://zh.kcwiki.cn/"}
lock = threading.Lock()
stat = {"ok": 0, "fail": 0}


def fetch(name):
    md5 = hashlib.md5(name.encode()).hexdigest()
    url = "https://upload.kcwiki.org/commons/%s/%s/%s" % (md5[0], md5[:2], name)
    with urllib.request.urlopen(urllib.request.Request(url, headers=HDR), timeout=30, context=CTX) as r:
        return r.read()


def save(data, target):
    im = Image.open(io.BytesIO(data)).convert("RGB")
    if im.width > 360:
        im = im.resize((360, max(1, im.height * 360 // im.width)), Image.LANCZOS)
    buf = io.BytesIO()
    im.save(buf, "JPEG", quality=72, optimize=True, progressive=True)
    open(target, "wb").write(buf.getvalue())


def names_for(ship):
    if ship["id"] >= 1500:
        sid = ship["id"]
        return "ShinkaiSeikan%dBanner.jpg" % sid, [
            "ShinkaiSeikan%dBanner.png" % sid, "ShinkaiSeikan%d.png" % sid,
            "ShinkaiSeikan%dIllust.png" % sid, "ShinkaiSeikan%dHDBanner.png" % sid]
    digits = re.sub(r"\D", "", str(ship.get("wiki_id") or ""))
    no = int(digits or 0)
    return "KanMusu%03dHDBanner.jpg" % no, [
        "KanMusu%03dHDBanner.png" % no, "KanMusu%03dHDBanner.jpg" % no,
        "KanMusu%03dBanner.png" % no, "KanMusu%03dBanner.jpg" % no,
        "KanMusu%03dHDIllust.png" % no, "KanMusu%03dIllust.png" % no]


def task(ship):
    target_name, sources = names_for(ship)
    target = os.path.join(OUT, target_name)
    if os.path.exists(target) and os.path.getsize(target) > 500:
        with lock:
            stat["ok"] += 1
        return
    for name in sources:
        try:
            data = fetch(name)
        except urllib.error.HTTPError:
            continue
        except Exception:
            continue
        try:
            save(data, target)
            with lock:
                stat["ok"] += 1
            return
        except Exception:
            continue
    with lock:
        stat["fail"] += 1


def main():
    ships = json.load(open(os.path.join(ASSETS, "Ship.json"), encoding="utf-8"))
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        list(pool.map(task, ships))

    need = {names_for(s)[0] for s in ships}
    for name in os.listdir(OUT):
        if name not in need:
            os.remove(os.path.join(OUT, name))

    files = os.listdir(OUT)
    total = sum(os.path.getsize(os.path.join(OUT, n)) for n in files)
    print("ships=%d ok=%d missing=%d files=%d size=%.2f MB" % (
        len(ships), stat["ok"], len(need) - len(files), len(files), total / 1048576.0))


if __name__ == "__main__":
    main()
