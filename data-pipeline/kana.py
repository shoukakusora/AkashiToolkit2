"""Kana -> Hepburn romaji (no external deps)."""

_DIGRAPHS = {
    "きゃ": "kya", "きゅ": "kyu", "きょ": "kyo", "きぇ": "kye",
    "しゃ": "sha", "しゅ": "shu", "しょ": "sho", "しぇ": "she",
    "ちゃ": "cha", "ちゅ": "chu", "ちょ": "cho", "ちぇ": "che",
    "にゃ": "nya", "にゅ": "nyu", "にょ": "nyo",
    "ひゃ": "hya", "ひゅ": "hyu", "ひょ": "hyo",
    "みゃ": "mya", "みゅ": "myu", "みょ": "myo",
    "りゃ": "rya", "りゅ": "ryu", "りょ": "ryo",
    "ぎゃ": "gya", "ぎゅ": "gyu", "ぎょ": "gyo",
    "じゃ": "ja", "じゅ": "ju", "じょ": "jo", "じぇ": "je",
    "びゃ": "bya", "びゅ": "byu", "びょ": "byo",
    "ぴゃ": "pya", "ぴゅ": "pyu", "ぴょ": "pyo",
    "ぢゃ": "ja", "ぢゅ": "ju", "ぢょ": "jo",
    "ふぁ": "fa", "ふぃ": "fi", "ふぇ": "fe", "ふぉ": "fo",
    "てぃ": "ti", "でぃ": "di", "とぅ": "tu", "どぅ": "du",
    "うぃ": "wi", "うぇ": "we", "うぉ": "wo",
    "ゔ": "vu",
}

_SINGLE = {
    "あ": "a", "い": "i", "う": "u", "え": "e", "お": "o",
    "か": "ka", "き": "ki", "く": "ku", "け": "ke", "こ": "ko",
    "さ": "sa", "し": "shi", "す": "su", "せ": "se", "そ": "so",
    "た": "ta", "ち": "chi", "つ": "tsu", "て": "te", "と": "to",
    "な": "na", "に": "ni", "ぬ": "nu", "ね": "ne", "の": "no",
    "は": "ha", "ひ": "hi", "ふ": "fu", "へ": "he", "ほ": "ho",
    "ま": "ma", "み": "mi", "む": "mu", "め": "me", "も": "mo",
    "や": "ya", "ゆ": "yu", "よ": "yo",
    "ら": "ra", "り": "ri", "る": "ru", "れ": "re", "ろ": "ro",
    "わ": "wa", "ゐ": "i", "ゑ": "e", "を": "o", "ん": "n",
    "が": "ga", "ぎ": "gi", "ぐ": "gu", "げ": "ge", "ご": "go",
    "ざ": "za", "じ": "ji", "ず": "zu", "ぜ": "ze", "ぞ": "zo",
    "だ": "da", "ぢ": "ji", "づ": "zu", "で": "de", "ど": "do",
    "ば": "ba", "び": "bi", "ぶ": "bu", "べ": "be", "ぼ": "bo",
    "ぱ": "pa", "ぴ": "pi", "ぷ": "pu", "ぺ": "pe", "ぽ": "po",
    "ぁ": "a", "ぃ": "i", "ぅ": "u", "ぇ": "e", "ぉ": "o",
    "ゃ": "ya", "ゅ": "yu", "ょ": "yo", "ゎ": "wa", "ゕ": "ka", "ゖ": "ke",
    "ー": "", "・": " ", "　": " ",
}


def katakana_to_hiragana(s):
    out = []
    for ch in s:
        code = ord(ch)
        if 0x30A1 <= code <= 0x30F6:  # katakana letters
            out.append(chr(code - 0x60))
        elif code == 0x30FC:  # long vowel mark
            out.append("ー")
        else:
            out.append(ch)
    return "".join(out)


def to_romaji(text):
    if not text:
        return ""
    s = katakana_to_hiragana(text)
    out = []
    i = 0
    n = len(s)
    while i < n:
        pair = s[i:i + 2]
        if pair in _DIGRAPHS:
            out.append(_DIGRAPHS[pair])
            i += 2
            continue
        ch = s[i]
        if ch == "っ":  # sokuon: double following consonant
            nxt = s[i + 1:i + 3]
            if nxt[:2] in _DIGRAPHS:
                out.append(_DIGRAPHS[nxt[:2]][:1])
                i += 1
                continue
            nxt1 = s[i + 1:i + 2]
            rom = _SINGLE.get(nxt1, "")
            if rom:
                out.append(rom[0] if rom[0] not in "aeiou" else "")
            i += 1
            continue
        if ch in _SINGLE:
            out.append(_SINGLE[ch])
            i += 1
            continue
        out.append(ch)
        i += 1
    s = "".join(out)
    s = s.replace(" ", "").replace("ー", "")
    return s


def title_case(s):
    if not s:
        return s
    return s[0].upper() + s[1:]
