"""Bulk fetch wikitext from zh.kcwiki.cn MediaWiki API with on-disk cache."""
import hashlib
import json
import os
import ssl
import sys
import time
import urllib.parse
import urllib.request

API = "https://zh.kcwiki.cn/api.php"
HDR = {
    "User-Agent": "AkashiToolkitDataSync/1.0 (https://zh.kcwiki.cn/)",
    "Accept": "application/json",
}
CTX = ssl._create_unverified_context()


def _get(params, retries=4):
    url = API + "?" + urllib.parse.urlencode(params)
    last = None
    for n in range(retries):
        try:
            req = urllib.request.Request(url, headers=HDR)
            with urllib.request.urlopen(req, timeout=90, context=CTX) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception as e:  # noqa: BLE001
            last = e
            time.sleep(1.5 * (n + 1))
    raise RuntimeError("API failed: %s (%s)" % (url[:160], last))


def fetch_wikitext(titles, cache_dir, chunk=50, progress=True):
    """Return {title: wikitext} for the given titles (redirects followed)."""
    os.makedirs(cache_dir, exist_ok=True)
    out = {}
    todo = []
    for t in titles:
        key = hashlib.sha1(t.encode("utf-8")).hexdigest()[:20]
        p = os.path.join(cache_dir, key + ".json")
        if os.path.exists(p):
            try:
                with open(p, encoding="utf-8") as f:
                    rec = json.load(f)
                out[t] = rec.get("text")
                continue
            except Exception:  # noqa: BLE001
                pass
        todo.append(t)

    if progress:
        print("wikitext: %d cached, %d to fetch" % (len(out), len(todo)), flush=True)

    for start in range(0, len(todo), chunk):
        batch = todo[start:start + chunk]
        params = {
            "action": "query",
            "format": "json",
            "formatversion": "2",
            "prop": "revisions",
            "rvprop": "content",
            "rvslots": "main",
            "redirects": "1",
            "titles": "|".join(batch),
        }
        data = _get(params)
        q = data.get("query", {})
        # map normalized/redirects back to requested titles
        alias = {}
        for item in q.get("normalized", []) or []:
            alias[item["to"]] = item["from"]
        for item in q.get("redirects", []) or []:
            alias[item["to"]] = alias.get(item["from"], item["from"])
        pages = q.get("pages", [])
        got = {}
        for p in pages:
            title = p.get("title")
            text = None
            if "revisions" in p:
                rev = p["revisions"][0]
                slots = rev.get("slots", {})
                if isinstance(slots, dict) and slots:
                    text = list(slots.values())[0].get("content")
                elif "content" in rev:
                    text = rev["content"]
            got[title] = text
        for t in batch:
            text = got.get(t)
            if text is None:
                found = None
                for k, v in got.items():
                    if v is not None and (k == t or alias.get(k) == t or k.replace("_", " ") == t):
                        found = k
                        break
                if found:
                    text = got[found]
                else:
                    # redirect source page: try its own key
                    text = None
            key = hashlib.sha1(t.encode("utf-8")).hexdigest()[:20]
            with open(os.path.join(cache_dir, key + ".json"), "w", encoding="utf-8") as f:
                json.dump({"title": t, "text": text}, f, ensure_ascii=False)
            out[t] = text
        if progress:
            done = min(start + chunk, len(todo))
            print("  fetched %d/%d" % (done, len(todo)), flush=True)
    return out
