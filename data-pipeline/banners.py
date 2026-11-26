"""Resolve the exact banner file name for every ship via the wiki API."""
import json
import os
import ssl
import time
import urllib.parse
import urllib.request

BASE = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\apkwork"
ASSETS = os.path.join(BASE, "appbuild", "app", "src", "main", "assets")
CACHE = os.path.join(BASE, "gen", "cache")
API = "https://zh.kcwiki.cn/api.php"
CTX = ssl._create_unverified_context()
HDR = {"User-Agent": "Mozilla/5.0 (AkashiToolkit data pipeline)"}


def api(params, retries=4):
    url = API + "?" + urllib.parse.urlencode(params)
    last = None
    for i in range(retries):
        try:
            req = urllib.request.Request(url, headers=HDR)
            with urllib.request.urlopen(req, timeout=90, context=CTX) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception as e:  # noqa: BLE001
            last = e
            time.sleep(2 * (i + 1))
    raise last


def main():
    path = os.path.join(ASSETS, "Ship.json")
    ships = json.load(open(path, encoding="utf-8"))
    players = [s for s in ships if s["id"] < 1500]

    titles = {}
    for s in players:
        name = (s["name"].get("zh_cn") or "").strip()
        if name:
            titles[name] = s

    names = list(titles.keys())
    found = {}
    for start in range(0, len(names), 50):
        batch = names[start:start + 50]
        data = api({
            "action": "query", "format": "json", "formatversion": "2",
            "prop": "images", "imlimit": "500", "titles": "|".join(batch),
        })
        for page in data.get("query", {}).get("pages", []):
            title = page.get("title", "")
            ship = titles.get(title)
            if ship is None:
                # 规范化后的标题可能带下划线/空格差异
                for k, v in titles.items():
                    if k.replace(" ", "_") == title.replace(" ", "_"):
                        ship = v
                        break
            if ship is None:
                continue
            for im in page.get("images") or []:
                name = im["title"].split(":", 1)[-1]
                low = name.lower()
                if "banner" in low and (low.startswith("kanmusu") or low.startswith("shinkaiseikan")):
                    found[ship["id"]] = name
                    break
        print("banners: %d / %d" % (len(found), len(names)), flush=True)

    for s in ships:
        s["banner"] = found.get(s["id"], "")

    json.dump(ships, open(path, "w", encoding="utf-8"), ensure_ascii=False)
    print("resolved %d banners for %d player ships" % (len(found), len(names)))


if __name__ == "__main__":
    main()
