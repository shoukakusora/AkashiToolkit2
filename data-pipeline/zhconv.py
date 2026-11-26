"""Simplified<->Traditional conversion compatible with spreada ZHConverter."""
import os
import re

_CACHE = {}


def _unescape(s):
    def repl(m):
        return chr(int(m.group(1), 16))
    s = re.sub(r"\\u([0-9a-fA-F]{4})", repl, s)
    return s.replace("\\\\", "\\")


def load_map(path):
    if path in _CACHE:
        return _CACHE[path]
    m = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line or line.lstrip().startswith("#"):
                continue
            # split on first unescaped '=' or ':'
            idx = -1
            esc = False
            for i, ch in enumerate(line):
                if esc:
                    esc = False
                    continue
                if ch == "\\":
                    esc = True
                    continue
                if ch in "=:":
                    idx = i
                    break
            if idx <= 0:
                continue
            key = _unescape(line[:idx]).strip()
            val = _unescape(line[idx + 1:]).strip()
            if key and val:
                m[key] = val
    _CACHE[path] = m
    return m


class Converter:
    def __init__(self, prop_path):
        self.map = load_map(prop_path)
        self.maxlen = max((len(k) for k in self.map), default=1)

    def convert(self, text):
        if not text:
            return text
        out = []
        i = 0
        n = len(text)
        while i < n:
            hit = None
            upper = min(self.maxlen, n - i)
            for ln in range(upper, 0, -1):
                if text[i:i + ln] in self.map:
                    hit = text[i:i + ln]
                    break
            if hit:
                out.append(self.map[hit])
                i += len(hit)
            else:
                out.append(text[i])
                i += 1
        return "".join(out)


def to_tc(text, props_dir):
    return Converter(os.path.join(props_dir, "zh2Hant.properties")).convert(text)


def to_sc(text, props_dir):
    return Converter(os.path.join(props_dir, "zh2Hans.properties")).convert(text)
