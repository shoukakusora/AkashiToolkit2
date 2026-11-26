"""Generate Expedition.json from the wiki 远征列表 page (same rules as the in-app sync)."""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import zhconv

BASE = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\apkwork"
CACHE = os.path.join(BASE, "gen", "cache")
PROPS = os.path.join(CACHE, "properties")
OUT = os.path.join(BASE, "gen", "out_assets")

AREA_HEADINGS = ["镇守府海域", "南西诸岛海域", "北方海域", "西方海域", "南方海域", "中部海域", "南西海域"]
RES_NAMES = ["燃料", "弹药", "钢铁", "铝"]


def find_template_end(t, start):
    depth = 0
    i = start
    n = len(t)
    while i < n - 1:
        if t[i] == "{" and t[i + 1] == "{":
            depth += 1
            i += 2
            continue
        if t[i] == "}" and t[i + 1] == "}":
            depth -= 1
            i += 2
            if depth == 0:
                return i
            continue
        i += 1
    return -1


def split_top(s, sep):
    out = []
    link = 0
    tmpl = 0
    buf = []
    i = 0
    while i < len(s):
        c = s[i]
        nxt = s[i + 1] if i + 1 < len(s) else ""
        if c == "[" and nxt == "[":
            link += 1
        elif c == "]" and nxt == "]":
            if link > 0:
                link -= 1
        elif c == "{" and nxt == "{":
            tmpl += 1
        elif c == "}" and nxt == "}":
            if tmpl > 0:
                tmpl -= 1
        if c == sep and link == 0 and tmpl == 0:
            out.append("".join(buf))
            buf = []
            i += 1
            continue
        buf.append(c)
        i += 1
    out.append("".join(buf))
    return out


def resolve_templates(s):
    pat = re.compile(r"\{\{([^{}]*)\}\}")
    for _ in range(5):
        m = pat.search(s)
        if not m:
            break
        def repl(mm):
            parts = mm.group(1).split("|")
            return parts[-1].strip()
        s = pat.sub(repl, s)
    return s


def clean(s):
    if not s:
        return ""
    s = s.replace("'''", "").replace("''", "")
    s = re.sub(r"<br\s*/?>", " ", s)
    s = re.sub(r"\[\[([^\]|]*)\|([^\]]*)\]\]", r"\2", s)
    s = re.sub(r"\[\[([^\]]*)\]\]", r"\1", s)
    s = resolve_templates(s)
    s = re.sub(r"<[^>]+>", "", s)
    s = re.sub(r"\s+", " ", s).strip()
    return s


def collect(text, name):
    res = {}
    pos = 0
    needle = "{{" + name
    while True:
        st = text.find(needle, pos)
        if st < 0:
            break
        en = find_template_end(text, st)
        if en < 0:
            break
        pos = en
        body = text[st + 2:en - 2]
        fields = {}
        for part in split_top(body, "|")[1:]:
            eq = part.find("=")
            if eq <= 0:
                continue
            fields[part[:eq].strip()] = part[eq + 1:].strip()
        code = re.sub(r"\s+", "", clean(fields.get("编号", "")))
        code = re.sub(r"(月常|交战型|交战II型|交战|季常|限时).*$", "", code)
        if code:
            fields["__area"] = area_of(text, st)
            res[code] = fields
    return res


def area_of(text, pos):
    best = -1
    name = ""
    for h in AREA_HEADINGS:
        idx = max(text.rfind("== " + h + " ==", 0, pos), text.rfind("==" + h + "==", 0, pos))
        if idx > best:
            best = idx
            name = h
    return name


def num(s):
    m = re.search(r"-?\d+", s or "")
    return int(m.group()) if m else 0


def duration(s):
    s = clean(s)
    if ":" in s:
        p = s.split(":")
        try:
            return int(p[0]) * 60 + int(p[1])
        except Exception:
            return 0
    return num(s)


def leading_number(s):
    m = re.match(r"[-+]?\d+(?:\.\d+)?", (s or "").strip())
    return float(m.group()) if m else 0.0


def numeric_id(code):
    if not code:
        return 0
    if code[0].isdigit():
        return num(code)
    base = (ord(code[0].upper()) - ord("A") + 1) * 1000
    return base + num(code[1:])


def main():
    text = open(os.path.join(CACHE, "expedition.txt"), encoding="utf-8").read()
    rewards = collect(text, "远征报酬表")
    requires = collect(text, "远征需求表")
    out = []
    for code, f in rewards.items():
        req = requires.get(code, {})
        minutes = duration(f.get("耗时", ""))
        res = []
        res_str = []
        for key in RES_NAMES:
            v = clean(f.get(key, ""))
            normal = great = 0
            if v:
                parts = v.split("/")
                normal = num(parts[0])
                great = num(parts[1]) if len(parts) > 1 else 0
            res.append(normal)
            if normal <= 0:
                res_str.append("")
            elif minutes > 0:
                per_hour = int(round(normal * 60.0 / minutes))
                res_str.append("%d<br><b>%d/h</b>" % (normal, per_hour))
            else:
                res_str.append(str(normal))

        area_idx = AREA_HEADINGS.index(f["__area"]) if f.get("__area") in AREA_HEADINGS else len(AREA_HEADINGS) - 1
        name_cn = clean(f.get("中文名字", ""))
        name_ja = clean(f.get("日文名字", ""))
        entry = {
            "id": numeric_id(code),
            "name": {"zh_cn": name_cn, "zh_tw": zhconv.to_tc(name_cn, PROPS), "ja": name_ja, "en": ""},
            "time": minutes,
            "type": area_idx,
            "timeString": "%02d:%02d" % (minutes // 60, minutes % 60),
            "reward": {
                "playerXP": clean(f.get("提督经验值", "")),
                "shipXP": clean(f.get("舰娘经验值", "")),
                "resourceString": res_str,
                "resource": res,
            },
            "require": {
                "totalLevel": num(clean(req.get("舰队总等级", ""))),
                "flagshipLevel": num(clean(req.get("旗舰等级", ""))),
                "minShips": num(clean(req.get("最低舰娘数", ""))),
                "essentialShip": clean(req.get("必要舰娘", "")),
                "bucket": "" if clean(req.get("输送桶", "")) in ("", "0") else clean(req.get("输送桶", "")),
                "consume": [leading_number(clean(req.get("燃料消耗", ""))), leading_number(clean(req.get("弹药消耗", "")))],
                "consumeString": [clean(req.get("燃料消耗", "")), clean(req.get("弹药消耗", ""))],
            },
        }
        award = clean(f.get("奖励", ""))
        if award:
            entry["reward"]["award"] = award
        award2 = clean(f.get("大成功奖励", ""))
        if award2:
            entry["reward"]["award2"] = award2
        out.append(entry)

    os.makedirs(OUT, exist_ok=True)
    json.dump(out, open(os.path.join(OUT, "Expedition.json"), "w", encoding="utf-8"), ensure_ascii=False)
    print("Expedition.json: %d entries (reward %d / require %d)" % (len(out), len(rewards), len(requires)))
    types = {}
    for e in out:
        types[e["type"]] = types.get(e["type"], 0) + 1
    print("types:", types)


if __name__ == "__main__":
    main()
