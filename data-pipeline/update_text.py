"""Extract readable body text from a 游戏更新 page (per-ship sections)."""
import io
import os
import re

BASE = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\apkwork"


def clean_inline(s):
    s = s.replace("\u00a0", " ")
    s = re.sub(r"\[\[(?:File|文件|Image):[^\]]*\]\]", "", s, flags=re.I)
    s = re.sub(r"\[\[([^\[\]|]*)\|([^\[\]]*)\]\]", r"\2", s)
    s = re.sub(r"\[\[([^\[\]]*)\]\]", r"\1", s)
    s = re.sub(r"\{\{(?:Green|Red|Blue|Purple|Color|Colour)\s*\|([^{}]*)\}\}", r"\1", s, flags=re.I)
    s = re.sub(r"\{\{[^{}]*\}\}", "", s)
    s = re.sub(r"<[^>]+>", "\n", s)
    s = s.replace("'''", "").replace("''", "")
    return s


def sections(page):
    """Yield (section_name, body)."""
    parts = re.split(r"^=+\s*([^=\n]+?)\s*=+\s*$", page, flags=re.M)
    for i in range(1, len(parts) - 1, 2):
        yield parts[i].strip(), parts[i + 1]


def body_text(body):
    text = clean_inline(body)
    lines = []
    for line in text.split("\n"):
        line = line.strip()
        if not line:
            continue
        if line.lower().startswith("file:") or line.startswith("File:"):
            continue
        if line.startswith("|") or line.startswith("{|") or line.startswith("|-"):
            continue
        if re.fullmatch(r"=+.*=+", line):
            continue
        lines.append(line)
    return lines


def update_text(page):
    out = []
    for name, body in sections(page):
        if name in ("游戏更新",) or "游戏更新" in name:
            continue
        lines = body_text(body)
        if not lines:
            continue
        block = [name]
        block.extend(lines)
        out.append("\n".join(block))
    if not out:
        return "\n\n".join(body_text(page))
    return "\n\n".join(out)


if __name__ == "__main__":
    p = os.path.join(BASE, "gen", "cache", "page_游戏更新_2026年11月24日.txt")
    t = io.open(p, encoding="utf-8").read()
    print(update_text(t)[:900])
    print("----- 10-14 -----")
    p2 = os.path.join(BASE, "gen", "cache", "page_游戏更新_2026年10月14日.txt")
    if os.path.exists(p2):
        print(update_text(io.open(p2, encoding="utf-8").read())[:600])
