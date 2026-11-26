"""Parse kcwiki 带路条件 HTML tables into structured rows."""
import re
from html.parser import HTMLParser


class _Node:
    def __init__(self, tag):
        self.tag = tag
        self.children = []
        self.text = []

    def all_text(self):
        parts = list(self.text)
        for c in self.children:
            parts.append(c.all_text())
        return "".join(parts)


class _Parser(HTMLParser):
    VOID = {"br", "img", "hr", "wbr", "col", "input"}

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.root = _Node("#root")
        self.stack = [self.root]

    def handle_starttag(self, tag, attrs):
        node = _Node(tag)
        self.stack[-1].children.append(node)
        if tag not in self.VOID:
            self.stack.append(node)

    def handle_startendtag(self, tag, attrs):
        node = _Node(tag)
        self.stack[-1].children.append(node)

    def handle_endtag(self, tag):
        for i in range(len(self.stack) - 1, 0, -1):
            if self.stack[i].tag == tag:
                del self.stack[i:]
                return

    def handle_data(self, data):
        self.stack[-1].text.append(data)



def _text_lines(node, out=None, skip_tags=("table",)):
    """按直接文本节点收集文字行（跳过表格内部），避免父子重复。"""
    if out is None:
        out = []
    for t in node.text:
        s = _clean(t)
        if s and re.search(r"[0-9A-Za-z\u4e00-\u9fff]", s):
            out.append(s)
    for c in node.children:
        if c.tag in skip_tags:
            continue
        _text_lines(c, out, skip_tags)
    return out

def _clean(s):
    s = re.sub(r"\s+", " ", s or "")
    return s.strip(" \t\r\n|")


def _rows_of(table_node):
    """Table -> list of rows; every cell becomes text (nested tables flattened to text)."""
    rows = []
    for tr in _find_all(table_node, "tr"):
        cells = []
        for cell in tr.children:
            if cell.tag not in ("td", "th"):
                continue
            cells.append(_clean(cell.all_text()))
        if any(cells):
            rows.append(cells)
    return rows


def _find_all(node, tag):
    out = []
    for c in node.children:
        if c.tag == tag:
            out.append(c)
        out.extend(_find_all(c, tag))
    return out


def _find_direct(node, tag):
    return [c for c in node.children if c.tag == tag]


def parse_route_html(html):
    """Return [{point, notes, tables}] from a 带路条件 page."""
    if not html:
        return []
    parser = _Parser()
    parser.feed(html)
    root = parser.root

    tables = _find_all(root, "table")
    if not tables:
        return []

    # 最外层表格：取第一个包含「分歧点」的表
    outer = None
    for t in tables:
        rows = _rows_of(t)
        if rows and any("分歧点" in (c or "") for c in rows[0]):
            outer = t
            break
    if outer is None:
        outer = tables[0]

    result = []
    for tr in _find_direct(outer, "tr") or []:
        cells = [c for c in tr.children if c.tag in ("td", "th")]
        if len(cells) < 2:
            continue
        point = _clean(cells[0].all_text())
        if not point or point in ("分歧点",):
            continue
        # 去掉「转自...」之类尾巴
        point = re.sub(r"[^\w\-]", "", point)
        if not point:
            continue

        cell = cells[1]
        nested_tables = []
        for t in _find_all(cell, "table"):
            rows = _rows_of(t)
            if rows:
                nested_tables.append({"rows": rows})

        # 单元格里除了表格以外的文字：逐文本节点收集，天然避免父子重复
        notes = []
        for text in _text_lines(cell):
            if text and text not in notes and len(notes) < 40:
                notes.append(text)

        result.append({"point": point, "notes": notes, "tables": nested_tables})

    # 去重（同一分歧点只保留内容最多的一条）
    merged = {}
    for r in result:
        key = r["point"]
        old = merged.get(key)
        if old is None or _weight(r) > _weight(old):
            merged[key] = r
    return list(merged.values())


def _weight(r):
    return len(r.get("notes") or []) + sum(len(t["rows"]) for t in r.get("tables") or [])
