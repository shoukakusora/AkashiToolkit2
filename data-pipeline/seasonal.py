"""Generate ExtraIllustration.json (季节限定立绘) from zh.kcwiki.cn file list."""
import json
import os
import re
import ssl
import urllib.parse
import urllib.request

BASE = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\apkwork"
OUT = os.path.join(BASE, "gen", "out_assets")
CACHE = os.path.join(BASE, "gen", "cache")

CTX = ssl._create_unverified_context()
HDR = {"User-Agent": "Mozilla/5.0 (AkashiToolkit data pipeline)"}
API = "https://zh.kcwiki.cn/api.php"

# KanMusu224IllustTsuyu.png / KanMusu224DmgIllustTsuyu.png
PATTERN = re.compile(r"^(KanMusu|ShinkaiSeikan)(\d+[a-z]?)(Dmg)?(?:Banner)?(?:Illust)?(.+)\.(?:png|jpg|jpeg)$")
SKIP_SUFFIX = {
    "illust", "dmg", "damaged", "hd", "hdillust", "hddmg", "hddmgillust", "banner", ""
}


def api(params):
    url = API + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers=HDR)
    with urllib.request.urlopen(req, timeout=90, context=CTX) as r:
        return json.loads(r.read().decode("utf-8"))


def all_images(prefix="KanMusu"):
    images = []
    cont = {}
    for step in range(400):
        params = {
            "action": "query", "format": "json", "list": "allimages",
            "ailimit": "500", "aiprefix": prefix, "aiprop": "name",
        }
        params.update(cont)
        data = api(params)
        images.extend(x["name"] for x in data.get("query", {}).get("allimages", []))
        if "continue" not in data:
            break
        cont = data["continue"]
        if step % 10 == 0:
            print("  ...%d images" % len(images), flush=True)
    return images


def main():
    os.makedirs(OUT, exist_ok=True)
    cache_file = os.path.join(CACHE, "allimages_seasonal.json")
    if os.path.exists(cache_file):
        images = json.load(open(cache_file, encoding="utf-8"))
        print("cached images:", len(images))
    else:
        images = all_images("KanMusu") + all_images("ShinkaiSeikan")
        json.dump(images, open(cache_file, "w", encoding="utf-8"), ensure_ascii=False)
        print("downloaded images:", len(images))

    by_ship = {}
    seasons = {}
    for name in images:
        m = PATTERN.match(name)
        if not m:
            continue
        ship_id, dmg, suffix = m.group(2), m.group(3) or "", m.group(4)
        suf = suffix.strip()
        if (suf.lower() in SKIP_SUFFIX
                or re.fullmatch(r"\d+", suf)
                or len(suf) <= 1
                or suf.startswith(("HD", "Dmg", "Illust", "Banner"))
                or "Banner" in suf):
            continue
        by_ship.setdefault(ship_id, set()).add(name)
        seasons[suffix] = seasons.get(suffix, 0) + 1

    out = []
    for ship_id in sorted(by_ship, key=lambda x: (int(re.match(r"\d+", x).group() or 0), x)):
        files = sorted(by_ship[ship_id], key=lambda n: (("Dmg" in n), n))
        out.append({"id": ship_id, "image": files})

    json.dump(out, open(os.path.join(OUT, "ExtraIllustration.json"), "w", encoding="utf-8"), ensure_ascii=False)
    print("ExtraIllustration.json: %d ships, %d files, %d seasons"
          % (len(out), sum(len(v) for v in by_ship.values()), len(seasons)))
    top = sorted(seasons.items(), key=lambda x: -x[1])[:12]
    print("top suffixes:", top)


if __name__ == "__main__":
    main()
