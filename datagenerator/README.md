# datagenerator

本目录是 **Akashi Toolkit 的离线数据源**：保留旧版 Java 数据生成器工程（`src/`、`build.gradle`）以及它使用的原始数据。当前 App 的 `assets`（`Ship.json`、`Equip.json`、`ShipType.json` 等）由仓库根目录的 `data-pipeline/`（与 `apkwork/pygen` 对应）从这些数据生成；本目录中的文件已按当前管线缓存（`apkwork/gen/cache`）刷新，便于离线查阅与复现。本目录不参与 `settings.gradle` 构建，需单独用 Gradle 编译。

## 文件来源

| 文件 | 来源 | 说明 |
| --- | --- | --- |
| `api_start2.json` | `apkwork/gen/cache/api_start2.json` | kcwiki 接口原始数据（单行压缩 JSON），含舰娘、装备、舰种、任务等 master 表 |
| `ships_detail.json` | `apkwork/gen/cache/ships_detail.json` | 舰娘 / 深海栖舰详情（图鉴号、假名、立绘、图鉴说明等） |
| `ship.lua` | `apkwork/gen/cache/ship_module.lua` | 舰娘数据 Lua 模块（`d.shipDataTb`，867 条） |
| `equip.lua` | `apkwork/gen/cache/equip_improvement_module.txt` | 装备数据改 Lua 模块（`d.equipDataTb`，585 条，含改修信息；为 cache 中最大最完整的装备模块） |
| `ship.txt` | 由 `app/src/main/assets/Ship.json` 重新生成 | 旧版 wiki 抓取格式，仅舰娘（867 条）；图鉴号 / 假名取自 `ships_detail.json` |
| `ships.csv` | 由 `Ship.json` + `ShipType.json` + `ShipClass.json` 重新生成 | 旧版 CSV 格式，舰娘 + 深海栖舰共 1664 行 |
| `zh2Hans.properties` / `zh2Hant.properties` | 原样保留 | 简繁转换表（`zhconv.py` 使用） |
| `src/`、`build.gradle`、`.gitignore` | 原样保留 | 旧版 Java 生成器源码与构建脚本 |

## 刷新方法

1. 运行 `data-pipeline/`（即 `apkwork/pygen`）抓取并生成缓存，产物在 `apkwork/gen/cache/`。
2. 直接覆盖复制：
   - `api_start2.json` ← `cache/api_start2.json`
   - `ships_detail.json` ← `cache/ships_detail.json`
   - `ship.lua` ← `cache/ship_module.lua`
   - `equip.lua` ← `cache/equip_improvement_module.txt`
3. `ship.txt` / `ships.csv` 由 `app/src/main/assets/Ship.json`（1664 条，含敌舰）重新生成，列映射见下。
4. 校验：两个 JSON 用 `python -c "import json;json.load(open(...))"` 解析；`ship.txt` 同样可 `json.load`；`ships.csv` 为「表头 + 0..57 索引行 + 数据行」。

## ship.txt / ships.csv 生成规则

- `ship.txt`：键名与顺序、缩进风格与原文件一致（`wiki_id, id, 图鉴号, 日文名, 假名, 中文名, stype, 级别, 数据, equip, 获得, 消耗, 改修, 解体, 改造, 画师, 声优`），UTF-8、无结尾换行。仅含舰娘（`id < 1500`），与原文件一致（原 426 条全为舰娘，敌舰没有画师 / 声优 / 改造数据）。
- `ships.csv`：沿用原表头（58 列）与索引行，LF 换行，含舰娘 + 敌舰共 1664 行。
  - 図鑑番号 ← `ships_detail.sort_no`；読み ← `ships_detail.yomi`（敌舰为空）
  - 耐久 / 火力 / 雷装 / 対空 / 装甲 / 対潜 / 回避 / 索敵 / 運 的「最大」= 初期 + 成长值（`attr + attr_max`）
  - 初期装備：`Ship.json` 中 0 表示无装备，写为 `-1`（与原文件一致），不足 5 格补 `-1`
  - 改装弾薬 / 改装鋼材 ← `remodel.cost`（弹药、钢材）
  - **留空列**：`耐久結婚`、`对潜155推测`、`回避155推测`、`索敌155推测` —— 当前数据没有对应字段（`attr_99` 全为 0），按原列保留但不填值
- `ships.csv` 编码：原文件为 GBK；当前舰名含 GBK 之外的字符（`・`、`㭴`、`䲠`），故用 **GB18030**（GBK 超集，原有 GBK 字节完全一致）落盘，避免丢字。
