"""Minimal Lua table parser for kcwiki data modules."""
import re


class LuaParser:
    def __init__(self, s):
        self.s = s
        self.i = 0
        self.n = len(s)

    def ws(self):
        s = self.s
        while self.i < self.n:
            c = s[self.i]
            if c in " \t\r\n":
                self.i += 1
                continue
            if s.startswith("--[[", self.i):
                j = s.find("]]", self.i)
                self.i = self.n if j < 0 else j + 2
                continue
            if s.startswith("--", self.i):
                j = s.find("\n", self.i)
                self.i = self.n if j < 0 else j + 1
                continue
            break

    def string(self):
        s = self.s
        q = s[self.i]
        self.i += 1
        out = []
        while self.i < self.n:
            c = s[self.i]
            if c == "\\":
                nxt = s[self.i + 1] if self.i + 1 < self.n else ""
                m = {"n": "\n", "t": "\t", "r": "\r", '"': '"', "'": "'", "\\": "\\"}
                if nxt in m:
                    out.append(m[nxt])
                    self.i += 2
                    continue
                if nxt == "u":
                    out.append(chr(int(s[self.i + 2:self.i + 6], 16)))
                    self.i += 6
                    continue
                out.append(nxt)
                self.i += 2
                continue
            if c == q:
                self.i += 1
                return "".join(out)
            out.append(c)
            self.i += 1
        raise ValueError("unterminated string at %d" % self.i)

    def longstring(self):
        s = self.s
        m = re.match(r"\[(=*)\[", s[self.i:])
        eq = m.group(1)
        self.i += len(m.group(0))
        end = "]" + eq + "]"
        j = s.find(end, self.i)
        if j < 0:
            raise ValueError("unterminated long string")
        v = s[self.i:j]
        self.i = j + len(end)
        return v

    def value(self):
        self.ws()
        s = self.s
        c = s[self.i]
        if c == "{":
            return self.table()
        if c in "\"'":
            return self.string()
        if c == "[":
            return self.longstring()
        m = re.match(r"-?\d+\.?\d*(?:[eE][-+]?\d+)?", s[self.i:])
        if m and m.group(0):
            g = m.group(0)
            self.i += len(g)
            return float(g) if ("." in g or "e" in g.lower()) else int(g)
        m = re.match(r"[A-Za-z_]\w*", s[self.i:])
        if m:
            w = m.group(0)
            self.i += len(w)
            if w == "true":
                return True
            if w == "false":
                return False
            if w == "nil":
                return None
            raise ValueError("bareword %r at %d" % (w, self.i))
        raise ValueError("bad value %r at %d" % (s[self.i:self.i + 40], self.i))

    def table(self):
        self.i += 1  # {
        arr = []
        d = {}
        while True:
            self.ws()
            if self.i >= self.n:
                raise ValueError("unterminated table")
            if self.s[self.i] == "}":
                self.i += 1
                break
            if self.s[self.i] == "[" and not self.s.startswith("[[", self.i):
                self.i += 1
                k = self.value()
                self.ws()
                if self.s[self.i] != "]":
                    raise ValueError("expected ] at %d" % self.i)
                self.i += 1
                self.ws()
                if self.s[self.i] != "=":
                    raise ValueError("expected = at %d" % self.i)
                self.i += 1
                d[str(k)] = self.value()
            else:
                m = re.match(r"([A-Za-z_]\w*)\s*=\s*", self.s[self.i:])
                if m:
                    self.i += len(m.group(0))
                    d[m.group(1)] = self.value()
                else:
                    arr.append(self.value())
            self.ws()
            if self.i < self.n and self.s[self.i] in ",;":
                self.i += 1
        if d and arr:
            # mixed table: keep numeric entries under indices
            for idx, v in enumerate(arr):
                d[str(idx + 1)] = v
            return d
        return arr if arr else d


def find_table(text, name):
    """Find `<name> = { ... }` and return the parsed table."""
    m = re.search(r"\b" + re.escape(name) + r"\s*=\s*", text)
    if not m:
        raise ValueError("assignment %s not found" % name)
    p = LuaParser(text)
    p.i = m.end()
    return p.value()
