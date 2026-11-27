"""Bundle home / event thumbnails into assets/homeimg (offline)."""
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
OUT = os.path.join(ASSETS, "homeimg")
CTX = ssl._create_unverified_context()
HDR = {"User-Agent": "Mozilla/5.0 (AkashiToolkit data pipeline)",
       "Referer": "https://zh.kcwiki.cn/"}
WIDTH = 720
QUALITY = 80

lock = threading.Lock()
stats = {"ok": 0, "fail": 0, "bytes": 0}


def collect():
    names = []
    for f in ("Home2.json", "Event2.json"):
        p = os.path.join(ASSETS, f)
        if not os.path.exists(p):
            continue
        data = json.load(open(p, encoding="utf-8"))
        entries = data if isinstance(data, list) else (
            data.get("game_info", []) + data.get("updates", []) + data.get("seasonal", []))
        for e in entries:
            for n in (e.get("images") or []):
                if n and n not in names:
                    names.append(n)
    return names


def fetch(name):
    md5 = hashlib.md5(name.encode()).hexdigest()
    url = "https://upload.kcwiki.org/commons/%s/%s/%s" % (md5[0], md5[:2], name)
    with urllib.request.urlopen(urllib.request.Request(url, headers=HDR), timeout=40, context=CTX) as r:
        return r.read()


def task(name):
    target = os.path.join(OUT, name.rsplit(".", 1)[0] + ".jpg")
    if os.path.exists(target) and os.path.getsize(target) > 500:
        with lock:
            stats["ok"] += 1
            stats["bytes"] += os.path.getsize(target)
        return
    try:
        data = fetch(name)
    except urllib.error.HTTPError:
        with lock:
            stats["fail"] += 1
        return
    except Exception:
        with lock:
            stats["fail"] += 1
        return
    try:
        im = Image.open(io.BytesIO(data)).convert("RGB")
        if im.width > WIDTH:
            im = im.resize((WIDTH, max(1, im.height * WIDTH // im.width)), Image.LANCZOS)
        buf = io.BytesIO()
        im.save(buf, "JPEG", quality=QUALITY, optimize=True, progressive=True)
        blob = buf.getvalue()
        open(target, "wb").write(blob)
        with lock:
            stats["ok"] += 1
            stats["bytes"] += len(blob)
    except Exception:
        with lock:
            stats["fail"] += 1


def main():
    os.makedirs(OUT, exist_ok=True)
    names = collect()
    print("images referenced:", len(names))
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        list(pool.map(task, names))
    print("ok=%d fail=%d total=%.2f MB" % (stats["ok"], stats["fail"], stats["bytes"] / 1048576.0))


if __name__ == "__main__":
    main()
