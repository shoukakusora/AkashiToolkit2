"""Generate Quest.json from the wiki 任务 page (same rules as the in-app sync)."""
import json
import os
import re
import ssl
import time
import sys
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import zhconv
import quest_reward

BASE = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\apkwork"
CACHE = os.path.join(BASE, "gen", "cache")
PROPS = os.path.join(CACHE, "properties")
OUT = os.path.join(BASE, "gen", "out_assets")

WIKI_RAW = "https://zh.kcwiki.cn/index.php?title=%E4%BB%BB%E5%8A%A1&action=raw"


def fetch():
    p = os.path.join(CACHE, "quest_page.txt")
    if os.path.exists(p):
        return open(p, encoding="utf-8").read()
    ctx = ssl._create_unverified_context()
    last = None
    for attempt in range(6):
        try:
            req = urllib.request.Request(WIKI_RAW, headers={"User-Agent": "Mozilla/5.0 (AkashiToolkit)"})
            with urllib.request.urlopen(req, timeout=90, context=ctx) as r:
                t = r.read().decode("utf-8", "replace")
            open(p, "w", encoding="utf-8").write(t)
            return t
        except Exception as e:  # noqa: BLE001
            last = e
            time.sleep(3 * (attempt + 1))
    raise last


def fetch_latest():
    p = os.path.join(CACHE, "quest_latest.txt")
    if os.path.exists(p):
        return open(p, encoding="utf-8").read()
    return ""


def strip_date_prefix(code):
    return re.sub(r"^\d+", "", code)


def split_blocks(text, template):
    needle = "{{" + template
    pos = 0
    while True:
        st = text.find(needle, pos)
        if st < 0:
            return
        depth = 0
        i = st
        end = -1
        while i < len(text) - 1:
            if text[i] == "{" and text[i + 1] == "{":
                depth += 1
                i += 2
                continue
            if text[i] == "}" and text[i + 1] == "}":
                depth -= 1
                i += 2
                if depth == 0:
                    end = i
                    break
                continue
            i += 1
        if end < 0:
            return
        yield st, text[st + 2:end - 2]
        pos = end


def split_top(s, sep):
    out, buf = [], []
    link = tmpl = 0
    i = 0
    while i < len(s):
        c = s[i]
        nxt = s[i + 1] if i + 1 < len(s) else ""
        if c == "[" and nxt == "[":
            link += 1
        elif c == "]" and nxt == "]":
            link = max(0, link - 1)
        elif c == "{" and nxt == "{":
            tmpl += 1
        elif c == "}" and nxt == "}":
            tmpl = max(0, tmpl - 1)
        if c == sep and link == 0 and tmpl == 0:
            out.append("".join(buf))
            buf = []
            i += 1
            continue
        buf.append(c)
        i += 1
    out.append("".join(buf))
    return out


def fields_of(body):
    f = {}
    for part in split_top(body, "|")[1:]:
        eq = part.find("=")
        if eq > 0:
            f[part[:eq].strip()] = part[eq + 1:].strip()
    return f


def clean(s):
    if not s:
        return ""
    s = s.replace("'''", "").replace("''", "")
    s = re.sub(r"\[\[([^\]|]*)\|([^\]]*)\]\]", r"\2", s)
    s = re.sub(r"\[\[([^\]]*)\]\]", r"\1", s)
    s = re.sub(r"<br\s*/?>", "\n", s)
    s = re.sub(r"<[^>]+>", "", s)
    return s.strip()


def num(s):
    m = re.search(r"-?\d+", s or "")
    return int(m.group()) if m else 0


def type_of(code):
    code = strip_date_prefix(code)
    if code.startswith("SF"):
        return 19
    if code.startswith("W") and len(code) > 1:
        return {"A": 1, "B": 2, "C": 3, "D": 4, "E": 5, "F": 6, "G": 7}.get(code[1].upper(), 0)
    if not code:
        return 0
    return {"A": 1, "B": 2, "C": 3, "D": 4, "E": 5, "F": 6, "G": 7}.get(code[0].upper(), 0)


def period_of(code):
    code = strip_date_prefix(code)
    if len(code) < 2:
        return 0
    c = code[1].lower()
    return {"d": 1, "w": 2, "m": 3}.get(c, 0)


def main():
    global MAPS
    MAPS = quest_reward.load_maps()
    text = fetch()
    quests = {}
    order = []
    for pos, body in split_blocks(text, "任务表"):
        f = fields_of(body)
        code = re.sub(r"\s+", "", clean(f.get("编号", "")))
        if not code or not re.match(r"^[A-Za-z]", code):
            continue
        q = {
            "code": code,
            "newMission": False,
            "period": period_of(code),
            "type": type_of(code),
            "title": {
                "zh_cn": clean(f.get("中文任务名字", "")),
                "zh_tw": zhconv.to_tc(clean(f.get("中文任务名字", "")), PROPS),
                "ja": clean(f.get("日文任务名字", "")),
                "en": "",
            },
            "content": {
                "zh_cn": clean(f.get("中文任务说明", "")),
                "zh_tw": zhconv.to_tc(clean(f.get("中文任务说明", "")), PROPS),
                "ja": clean(f.get("日文任务说明", "")),
                "en": "",
            },
            "reward": {
                "resource": [num(f.get("燃料")), num(f.get("弹药")), num(f.get("钢铁")), num(f.get("铝"))],
                "ship": [],
                "equip": [],
                "item": [],
                "str": quest_reward.clean_reward_text(f.get("奖励", "")),
                "entries": quest_reward.build_entries(f.get("奖励", ""), MAPS),
            },
            "note": clean(f.get("备注", "")),
            "unlock": [x for x in re.split(r"[,，、&;；\s]+", clean(f.get("前置", ""))) if x and x != "-"],
            "after": [],
        }
        if code not in quests:
            order.append(code)
            quests[code] = q
        else:
            # 同一编号多条时取内容更完整的一条
            score_new = len(q["title"]["zh_cn"]) + len(q["content"]["zh_cn"]) + len(q["note"])
            old = quests[code]
            score_old = len(old["title"]["zh_cn"]) + len(old["content"]["zh_cn"]) + len(old["note"])
            if score_new > score_old:
                quests[code] = q

    # 最新任务：任务页里的「任务/最新任务」子页面（主页面不包含这些编号）
    latest_codes = []
    for pos, body in split_blocks(fetch_latest(), "任务表"):
        f = fields_of(body)
        code = re.sub(r"\s+", "", clean(f.get("编号", "")))
        if not code or not re.match(r"^[A-Za-z0-9]", code):
            continue
        latest_codes.append(code)
        if code in quests:
            quests[code]["newMission"] = True
            continue
        q = {
            "code": code,
            "newMission": True,
            "period": period_of(code),
            "type": type_of(code),
            "title": {
                "zh_cn": clean(f.get("中文任务名字", "")),
                "zh_tw": zhconv.to_tc(clean(f.get("中文任务名字", "")), PROPS),
                "ja": clean(f.get("日文任务名字", "")),
                "en": "",
            },
            "content": {
                "zh_cn": clean(f.get("中文任务说明", "")),
                "zh_tw": zhconv.to_tc(clean(f.get("中文任务说明", "")), PROPS),
                "ja": clean(f.get("日文任务说明", "")),
                "en": "",
            },
            "reward": {
                "resource": [num(f.get("燃料")), num(f.get("弹药")), num(f.get("钢铁")), num(f.get("铝"))],
                "ship": [],
                "equip": [],
                "item": [],
                "str": quest_reward.clean_reward_text(f.get("奖励", "")),
                "entries": quest_reward.build_entries(f.get("奖励", ""), MAPS),
            },
            "note": clean(f.get("备注", "")),
            "unlock": [x for x in re.split(r"[,，、&;；\s]+", clean(f.get("前置", ""))) if x and x != "-"],
            "after": [],
        }
        quests[code] = q
        order.insert(0, code)

    # 由「前置」推导 after
    for code in order:
        for pre in quests[code]["unlock"]:
            if pre in quests and code not in quests[pre]["after"]:
                quests[pre]["after"].append(code)

    out = []
    for i, code in enumerate(order):
        q = quests[code]
        q["id"] = i
        out.append(q)

    os.makedirs(OUT, exist_ok=True)
    json.dump(out, open(os.path.join(OUT, "Quest.json"), "w", encoding="utf-8"), ensure_ascii=False)
    types = {}
    for q in out:
        types[q["type"]] = types.get(q["type"], 0) + 1
    print("Quest.json: %d quests, types %s" % (len(out), dict(sorted(types.items()))))


if __name__ == "__main__":
    main()
