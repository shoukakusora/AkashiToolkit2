"""Bundle map images (MapBannerHD<no>.png / MapHD<no>.png) into the APK.

Downloaded once from upload.kcwiki.org, re-encoded to WebP and stored in
`appbuild/app/src/main/assets/mapimg/` so the app never needs the network for them.
"""
import hashlib
import io
import json
import os
import ssl
import time
import urllib.request

from PIL import Image

BASE = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\apkwork"
ASSETS = os.path.join(BASE, "appbuild", "app", "src", "main", "assets")
OUT = os.path.join(ASSETS, "mapimg")

CTX = ssl._create_unverified_context()
HDR = {"User-Agent": "Mozilla/5.0 (AkashiToolkit data pipeline)",
       "Referer": "https://zh.kcwiki.cn/"}


def fetch(name, retries=4):
    md5 = hashlib.md5(name.encode()).hexdigest()
    url = "https://upload.kcwiki.org/commons/%s/%s/%s" % (md5[0], md5[:2], name)
    last = None
    for i in range(retries):
        try:
            req = urllib.request.Request(url, headers=HDR)
            with urllib.request.urlopen(req, timeout=120, context=CTX) as r:
                return r.read()
        except Exception as e:  # noqa: BLE001
            last = e
            time.sleep(2 * (i + 1))
    raise last


def to_jpeg(data, max_width, quality):
    im = Image.open(io.BytesIO(data))
    if im.mode in ("P", "RGBA", "LA"):
        im = im.convert("RGB")
    elif im.mode != "RGB":
        im = im.convert("RGB")
    if im.width > max_width:
        ratio = max_width / float(im.width)
        im = im.resize((max_width, max(1, int(im.height * ratio))), Image.LANCZOS)
    buf = io.BytesIO()
    im.save(buf, "JPEG", quality=quality, optimize=True, progressive=True)
    return buf.getvalue()


def main():
    os.makedirs(OUT, exist_ok=True)
    maps = json.load(open(os.path.join(ASSETS, "Map.json"), encoding="utf-8"))

    total = 0
    done = 0
    for entry in maps:
        no = "%d-%d" % (entry["sea"], entry["area"])
        for kind, max_w, q in (("MapBannerHD", 1000, 82), ("MapHD", 1400, 78)):
            name = "%s%s.png" % (kind, no)
            target = os.path.join(OUT, name.replace(".png", ".jpg"))
            if os.path.exists(target) and os.path.getsize(target) > 1000:
                total += os.path.getsize(target)
                done += 1
                continue
            try:
                data = fetch(name)
                webp = to_jpeg(data, max_w, q)
                open(target, "wb").write(webp)
                total += len(webp)
                done += 1
            except Exception as e:  # noqa: BLE001
                print("FAIL", name, str(e)[:60])
        print("maps done: %d, size %.1f MB" % (done, total / 1048576.0), flush=True)

    print("images: %d, total %.2f MB" % (done, total / 1048576.0))


if __name__ == "__main__":
    main()
