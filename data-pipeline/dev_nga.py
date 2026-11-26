"""Merge NGA development formulas (pid=660679928) into Development.json.

Transcribed from the screenshots the user supplied. Each entry maps to the app's
Development schema:
  ship        = 秘书舰条件
  fuel/ammo/steel/bauxite = 基础公式
  target      = 主要目标（含概率）
  extra       = 追加条件
  extraResult = 追加出货
"""
import json
import os

BASE = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\apkwork"
OUT = os.path.join(BASE, "gen", "out_assets", "Development.json")
ASSETS = os.path.join(BASE, "appbuild", "app", "src", "main", "assets", "Development.json")

SOURCE = "NGA pid=660679928"

FORMULAS = [
    {
        "ship": "炮战系秘书舰",
        "fuel": 10, "ammo": 30, "steel": 90, "bauxite": 10,
        "target": "九一式彻甲弹 4%",
        "extra": "弹提高到90，铝提高到30",
        "extraResult": "三式弹 4%",
    },
    {
        "ship": "炮战系秘书舰",
        "fuel": 10, "ammo": 251, "steel": 250, "bauxite": 10,
        "target": "46cm三连装炮 4%\n41cm连装炮 8%\n九一式彻甲弹 2%",
        "extra": "铝提高到30；铝提高到30且黎塞留级秘书舰；大和级秘书舰（不包括大和改二重）；纳尔逊秘书舰",
        "extraResult": "三式弹 4%；38cm四连装炮 2%；46cm三连装炮 6%、试制46cm连装炮 2%；16inch Mk.I三连装炮 8%（此时46会降到2%）",
    },
    {
        "ship": "Littorio、Roma、Zara、Pola 秘书舰",
        "fuel": 10, "ammo": 10, "steel": 10, "bauxite": 20,
        "target": "Ro.43水侦 6%",
        "extra": "",
        "extraResult": "",
    },
    {
        "ship": "水雷系秘书舰",
        "fuel": 10, "ammo": 10, "steel": 10, "bauxite": 10,
        "target": "ドラム缶(输送用) 4%",
        "extra": "驱逐秘书舰；潜水舰秘书舰",
        "extraResult": "发烟装置(烟雾) 2%；ドラム缶(输送用) 8%",
    },
    {
        "ship": "陆军水上舰（あきつ丸、神州丸、宗谷特务舰）秘书舰",
        "fuel": 10, "ammo": 10, "steel": 10, "bauxite": 10,
        "target": "大发动艇 8%",
        "extra": "",
        "extraResult": "",
    },
    {
        "ship": "陆军水上舰（あきつ丸、神州丸、宗谷特务舰）秘书舰",
        "fuel": 10, "ammo": 20, "steel": 10, "bauxite": 10,
        "target": "大发动艇 2%\n二式12cm迫击炮改 2%",
        "extra": "",
        "extraResult": "",
    },
    {
        "ship": "水雷系秘书舰",
        "fuel": 10, "ammo": 30, "steel": 10, "bauxite": 31,
        "target": "三式水中探信仪 2%\n三式爆雷投射机 2%\n九五式爆雷 4%",
        "extra": "意大利DD秘书舰",
        "extraResult": "Ro.43水侦 2%",
    },
    {
        "ship": "炮战系秘书舰",
        "fuel": 10, "ammo": 10, "steel": 250, "bauxite": 250,
        "target": "32号对水上电探 2%\n13号对空电探 2%\n33号对水上电探 2%\n21号对空电探 2%\n42号对空电探 2%\n增设バルジ(中型舰) 4%",
        "extra": "弹提高到30；弹提高到90；油提高到100；钢提高到300",
        "extraResult": "九一式彻甲弹 4%；三式弹 4%；改良型舰本式タービン 4%、强化型舰本式缶 2%；增设バルジ(大型舰) 2%",
    },
    {
        "ship": "空母系秘书舰",
        "fuel": 10, "ammo": 10, "steel": 250, "bauxite": 250,
        "target": "32号对水上电探 2%\n13号对空电探 2%\n22号对水上电探 2%\n33号对水上电探 2%\n21号对空电探 2%\n42号对空电探 2%\n零式水上观测机 2%",
        "extra": "弹提高到40；油提高到100",
        "extraResult": "12cm30连装喷进炮 2%；改良型舰本式タービン 6%、强化型舰本式缶 4%",
    },
    {
        "ship": "空母系秘书舰",
        "fuel": 10, "ammo": 40, "steel": 20, "bauxite": 20,
        "target": "12cm30连装喷进炮 2%",
        "extra": "陆军水上舰（あきつ丸、神州丸、宗谷特务舰形态）秘书舰；油或钢提高到40",
        "extraResult": "大发动艇 2%；大发动艇 4%",
    },
    {
        "ship": "空母系秘书舰",
        "fuel": 20, "ammo": 60, "steel": 10, "bauxite": 110,
        "target": "试制烈风 后期型 2%\n紫电改二 2%\n流星改 2%\n彗星一二型甲 2%\n彩云 4%\n紫电一一型 2%",
        "extra": "弹提高到90、钢提高到60；油提高到30、铝提高到130；伊势级改/改二秘书舰；放弃副产物省资源：铝降到100不出彩云，弹降到20、铝降到90不出高级攻击机，弹降到20、铝降到70不出烈风（狙击紫电）",
        "extraResult": "三式弹 6%；二式舰上侦察机 2%；瑞云(六三一空) 2%；对应副产物减少",
    },
    {
        "ship": "陆军水上舰（あきつ丸、神州丸、宗谷特务舰）秘书舰",
        "fuel": 20, "ammo": 40, "steel": 10, "bauxite": 120,
        "target": "力号观测机 4%\n三式指挥联络机(对潜) 4%\n一式战 隼II型 8%\n二式复战 屠龙 12%\n二式12cm迫击炮改 2%",
        "extra": "陆军-马鹿-秘书舰：不出烈风、紫电、流改、彗星一二甲、彩云、二式舰侦",
        "extraResult": "",
    },
    {
        "ship": "空母系秘书舰",
        "fuel": 240, "ammo": 260, "steel": 10, "bauxite": 261,
        "target": "九六式陆攻 8%\n试制烈风 后期型 2%\n紫电改二 2%\n流星改 2%\n彗星一二型甲 2%\n彩云 4%\n紫电一一型 2%",
        "extra": "钢提高到60；铝降到250（不出烈风/紫电/彩云等高级舰载机，不出紫电一一型，节省资材）；陆军水上舰秘书舰；铝降到250可出大发动艇（陆航出率下降）；同时钢提高到20",
        "extraResult": "三式弹 6%；12cm30连装喷进炮 2%；力号观测机 4%、三式指挥联络机(对潜) 4%、一式战 隼II型 8%、二式复战 屠龙 12%；大发动艇 8%；12cm30连装喷进炮 2%",
    },
    {
        "ship": "非美潜水系秘书舰",
        "fuel": 20, "ammo": 30, "steel": 20, "bauxite": 20,
        "target": "后期型舰首鱼雷(6门) 8%\n后期型舰首鱼雷(4门) 6%\n潜水舰后部鱼雷发射管4门(初期型) 4%\n21inch舰首鱼雷发射管4门(初期型) 6%",
        "extra": "",
        "extraResult": "",
    },
]


def main():
    base = json.load(open(OUT, encoding="utf-8")) if os.path.exists(OUT) else []
    seen = {(e.get("fuel"), e.get("ammo"), e.get("steel"), e.get("bauxite"),
             e.get("ship", ""), e.get("target", "")) for e in base}

    added = 0
    for item in FORMULAS:
        entry = {
            "target": item["target"],
            "ship": item["ship"],
            "fuel": item["fuel"],
            "ammo": item["ammo"],
            "steel": item["steel"],
            "bauxite": item["bauxite"],
            "result": "",
            "extra": item["extra"],
            "extraResult": item["extraResult"],
            "source": SOURCE,
        }
        key = (entry["fuel"], entry["ammo"], entry["steel"], entry["bauxite"],
               entry["ship"], entry["target"])
        if key in seen:
            continue
        seen.add(key)
        base.append(entry)
        added += 1

    for path in (OUT, ASSETS):
        json.dump(base, open(path, "w", encoding="utf-8"), ensure_ascii=False)

    print("NGA formulas: %d, added: %d, total: %d" % (len(FORMULAS), added, len(base)))


if __name__ == "__main__":
    main()
