"""Parse quest reward wikitext into structured entries (icon-able) + groups."""
import json
import os
import re

BASE = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\apkwork"
ASSETS = os.path.join(BASE, "appbuild", "app", "src", "main", "assets")

CHOICE_RE = re.compile(r"以下奖励([二三四两一二三])选一[：:]?")


def clean_name(s):
    s = re.sub(r"<[^>]+>", "", s or "")
    s = s.replace("'''", "").replace("''", "")
    return s.strip()


def normalize(name):
    """归一化名称：去星级/加号/空白/全角括号等，用于模糊匹配。"""
    s = clean_name(name)
    s = re.sub(r"[\u2605\u2606]\s*[+\uff0b]?\d+", "", s)
    s = re.sub(r"[+\uff0b]\d+$", "", s)
    s = s.replace("\uff08", "(").replace("\uff09", ")")
    # 日文异体字/旧字形 -> 中文常用字
    for a, b in (("\u5f3e", "\u5f39"), ("\u9ad9", "\u9ad8"), ("\ufa11", "\u5d0e"),
                 ("\u59eb", "\u59ec"), ("\u3005", "")):
        s = s.replace(a, b)
    s = re.sub(r"\s+", "", s)
    return s


def lookup(name, mapping):
    if not name:
        return None
    if name in mapping:
        return mapping[name]
    key = normalize(name)
    if key in mapping:
        return mapping[key]
    stripped = re.sub(r"\([^)]*\)$", "", key)
    if stripped and stripped in mapping:
        return mapping[stripped]
    # 最后的兜底：包含关系（取唯一命中）
    hits = [v for k, v in mapping.items() if len(k) >= 4 and (k in key or key in k)]
    hits = list(dict.fromkeys(hits))
    if len(hits) == 1:
        return hits[0]
    return None


def clean_reward_text(text):
    """把奖励 wikitext 清洗成可读文本（结构化数据缺失时的兜底）。"""
    if not text:
        return ""
    s = re.sub(r"\[\[(?:File|\u6587\u4ef6|Image):[^\]]*\]\]", "", text, flags=re.I)
    s = s.replace("'''", "").replace("''", "")
    s = re.sub(r"\[\[([^\]|]*)\|([^\]]*)\]\]", r"\2", s)
    s = re.sub(r"\[\[([^\]]*)\]\]", r"\1", s)
    s = re.sub(r"\{\{([^{}|]*)\|[^{}]*\}\}", r"\1", s)
    s = re.sub(r"\{\{([^{}]*)\}\}", r"\1", s)
    s = re.sub(r"<[^>]+>", "", s)
    s = s.replace("|", " ")
    s = re.sub(r"\s+", " ", s)
    return s.strip()


def load_maps():
    def load(name):
        p = os.path.join(ASSETS, name)
        if not os.path.exists(p):
            return []
        return json.load(open(p, encoding="utf-8"))

    ship, equip, item = {}, {}, {}
    for s in load("Ship.json"):
        for n in (s["name"]["zh_cn"], s["name"]["ja"]):
            if n:
                ship.setdefault(n.strip(), s["id"])
    for e in load("Equip.json"):
        for n in (e["name"]["zh_cn"], e["name"]["ja"]):
            if n:
                equip.setdefault(n.strip(), e["id"])
    for i in load("Item.json"):
        for n in (i["name"]["zh_cn"], i["name"]["ja"]):
            if n:
                item.setdefault(n.strip(), i["id"])

    # 归一化键，便于模糊匹配（★+5、全角括号、空格差异、简繁差异）
    for src in (ship, equip, item):
        extra = {}
        for k, v in src.items():
            keys = [normalize(k)]
            try:
                import zhconv
                keys.append(normalize(zhconv.to_tc(k)))
                keys.append(normalize(zhconv.to_sc(k)))
            except Exception:
                pass
            for key in keys:
                if key:
                    extra.setdefault(key, v)
        src.update(extra)
    return ship, equip, item


def parse_reward(text, ship_map, equip_map, item_map):
    """Return list of entries: {type,id,count,text}."""
    entries = []
    if not text:
        return entries
    text = text.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n")
    text = re.sub(r"\[\[File:[^\]]*\]\]", "", text, flags=re.I)
    # 颜色模板只是内联标注，保留其中文字（如星級 {{Green|\u2605+2}}）
    text = re.sub(r"\{\{(?:Green|Red|Blue|Purple|Color|Colour)\s*\|([^{}]*)\}\}", r"\1", text, flags=re.I)

    pos = 0
    for m in list(CHOICE_RE.finditer(text)) + [None]:
        end = m.start() if m else len(text)
        chunk = text[pos:end]
        if chunk.strip():
            entries.extend(_tokenize(chunk, ship_map, equip_map, item_map))
        if m:
            entries.append({"type": "text", "text": m.group(0).replace(":", "：")})
            pos = m.end()
    return _merge_stars(entries)


STAR_RE = re.compile(r"^\s*[\u2605\u2606]\s*(?:[mM][aA][xX]|[+\uff0b]?\s*\d+)")


def _merge_stars(entries):
    """把单独的「★+N」文本并到前一个奖励名称里（12.7cm…★+2）。"""
    merged = []
    for e in entries:
        if merged and e.get("type") == "text":
            text = (e.get("text") or "").strip()
            m = STAR_RE.match(text)
            if m:
                prev = merged[-1]
                star = m.group(0).replace(" ", "")
                rest = text[m.end():].strip()

                # 「★+4×2」：把数量并回 count，不要写进名称
                cm = re.match(r"^[×xX]\s*(\d+)$", rest)
                if cm and prev.get("type") != "text":
                    prev["count"] = int(cm.group(1))
                    rest = ""
                elif cm:
                    rest = ""

                if prev.get("type") == "text":
                    prev["text"] = (prev.get("text") or "") + star + rest
                else:
                    prev["name"] = (prev.get("name") or "") + star
                    if rest:
                        prev["name"] = prev["name"] + rest
                continue
        merged.append(e)
    return merged


def _tokenize(chunk, ship_map, equip_map, item_map):
    out = []
    pattern = re.compile(
        r"\[\[([^\]|]+)(?:\|([^\]]+))?\]\]"      # [[name]] / [[a|b]]
        r"|\{\{([^{}|]+)(?:\|([^{}]+))?\}\}"      # {{name}} / {{tpl|name}}
    )
    pos = 0
    for m in pattern.finditer(chunk):
        if m.start() > pos:
            _add_text(out, chunk[pos:m.start()])
        if m.group(1) is not None:
            name = clean_name(m.group(2) or m.group(1))
        else:
            name = clean_name(m.group(4) or m.group(3))
        # 数量: 紧跟其后的 ×N / xN
        tail = chunk[m.end():m.end() + 12]
        cm = re.match(r"\s*[×xX*]\s*(\d+)", tail)
        count = int(cm.group(1)) if cm else 1
        if cm:
            pos = m.end() + cm.end()
        else:
            pos = m.end()

        if not name:
            continue
        entry = None
        equip_id = lookup(name, equip_map)
        ship_id = None if equip_id else lookup(name, ship_map)
        item_id = None if equip_id else lookup(name, item_map)

        if equip_id:
            entry = {"type": "equip", "id": equip_id, "count": count, "name": name}
        elif ship_id:
            entry = {"type": "ship", "id": ship_id, "count": count, "name": name}
        elif item_id:
            entry = {"type": "item", "id": item_id, "count": count, "name": name}
        else:
            entry = {"type": "text", "text": name + ("x%d" % count if count > 1 else "")}
        out.append(entry)
    if pos < len(chunk):
        _add_text(out, chunk[pos:])
    return out


def _add_text(out, s):
    s = clean_name(s)
    s = re.sub(r"^[\s,，、;；]+|[\s,，、;；]+$", "", s)
    if not s:
        return
    out.append({"type": "text", "text": s})


def build_entries(wikitext, maps=None):
    if maps is None:
        maps = load_maps()
    return parse_reward(wikitext, *maps)
