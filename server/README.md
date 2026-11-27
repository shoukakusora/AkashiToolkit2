# server —— 活动 / 季节性 / 更新信息接口

本目录存放 AkashiToolkit 的 **服务端 JSON 接口**（原 `api_v2` 三个 PHP 脚本的维护版）。
应用通过 `RetrofitAPI` 请求以下路径：

| 文件 | 应用内请求路径 | 对应页面 |
| --- | --- | --- |
| `seasonal.php` | `/api_v2/seasonal.php` | 季节性（当前季节性立绘、机制、兑换说明） |
| `event.php` | `/api_v2/event.php` | 活动（最近一次活动限定海域的标题、攻略、概要） |
| `info.php` | `/api_v2/info.php` | 主页消息 / 检查更新 |

> 说明：本仓库版本的应用已把「活动」页改为读取内置 `assets/Event.json`，但三个接口仍保持原有 JSON 契约，
> 供旧版本客户端与在线刷新使用；修改本目录不会影响应用内置数据。

三个脚本都接受 `api_version` 查询参数（`info.php` 另有 `api_channel`），返回值统一为 **JSON 数组 / 对象**，
不带 BOM，`Content-Type: application/json; charset=utf-8`。

---

## 1. 数据结构

### 1.1 通用「区块」对象（seasonal.php / event.php）

顶层是一个 `Container` 数组，元素结构：

```json
{
  "type": 0,
  "object": { "...": "..." },
  "not_safe": true
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `type` | int | 区块类型，见下表 |
| `object` | object | 该类型对应的内容，见 1.2 |
| `not_safe` | bool | 可选。Google Play 版本会隐藏该区块（立绘类通常为 `true`） |
| `min_api` / `max_api` | int | 可选。按客户端 API 版本过滤 |
| `only_play` / `only_china` | bool | 可选。按渠道过滤 |

`type` 常量（与 `app/.../event/Event.java` 一致）：

| 值 | 常量 | 用途 |
| --- | --- | --- |
| 0 | `TYPE_TITLE` | 大标题 + 副标题 / 说明 |
| 1 | `TYPE_GALLERY` | 图片画廊（可点击跳转舰娘详情） |
| 2 | `TYPE_CONTENT` | 标题 + 正文 |
| 3 | `TYPE_URL` | 标题 + 说明 + 跳转按钮 |
| 4 | `TYPE_VOICE` | 限定语音（本期未使用） |

### 1.2 各 type 的 `object`

**TITLE（0）**

```json
{ "title": {"zh_cn": "2026年夏季活动"},
  "content": {"zh_cn": "反击！第三十一战队的战斗 / 法兰西舰队的跃动"},
  "summary": {"zh_cn": "更多内容在“活动”中查看"} }
```

- `title`：主标题；`content`：副标题（活动名）；`summary`：小字提示。
- 三个字段都可只提供 `zh_cn`，语言键支持 `zh_cn` / `zh_tw` / `ja` / `en`。

**GALLERY（1）**

```json
{ "title": {"zh_cn": "新立绘"},
  "urls":  ["KanMusu645HDIllustSanma2026.png"],
  "names": ["贝阿恩"],
  "action_type": 1,
  "ids": [1055] }
```

- `urls`：wiki 文件名（不含 `File:` 前缀）。**不带 `http` 前缀**，客户端会自动拼成 wiki 文件地址；
  带 `http` 前缀则按完整 URL 使用。
- `names`：与 `urls` 一一对应的显示名。
- `action_type` / `ids`：点击图片后的行为。`1` = 打开舰娘详情，`ids` 必须是
  `assets/Ship.json` 的 `id`（不是 `wiki_id`），与 `urls` 一一对应。
- 建议加 `"not_safe": true`，Play 版会隐藏立绘。

**CONTENT（2）**

```json
{ "title": {"zh_cn": "概述"}, "content": {"zh_cn": "多行正文……"} }
```

正文直接用 `\n` 换行。

**URL（3）**

```json
{ "title": {"zh_cn": "详细攻略"},
  "content": {"zh_cn": "海域地图、贴条、奖励与倍卡表"},
  "url_text": {"zh_cn": "kcwiki:2026年夏季活动"},
  "url": "https://zh.kcwiki.cn/wiki/2026%E5%B9%B4%E5%A4%8F%E5%AD%A3%E6%B4%BB%E5%8A%A8" }
```

`url_text` 是按钮文字，`url` 必须是完整 `http(s)` 地址（客户端直接交给浏览器 / Custom Tabs）。

### 1.3 info.php

```json
{
  "messages": [],
  "update": {
    "versionCode": 202645,
    "versionName": "1.3.2",
    "url":  "https://github.com/…/AkashiToolkit-1.3.2-kcwiki.apk",
    "url2": "https://github.com/…/AkashiToolkit-1.3.2-kcwiki.apk",
    "change": {
      "zh_cn": "• 数据刷新…；• 统计页…；• 离线图片…",
      "zh_tw": "• 資料更新…；• 統計頁…；• 離線圖片…",
      "en": "• Data refresh…; • Statistics…; • Offline images…"
    }
  }
}
```

| 字段 | 说明 |
| --- | --- |
| `update.versionCode` | 客户端比对用；**必须大于**已发布版本才会提示更新 |
| `update.versionName` | 展示用版本名 |
| `update.url` | 通用下载地址 |
| `update.url2` | `firebase` 渠道使用的下载地址；目前与 `url` 相同，**不要删**（缺省会导致点击下载崩溃） |
| `update.change` | 更新说明，中文 / 繁体 / 英文各一行（需要多行时可用 `\n`） |
| `messages[]` | 公告列表，可为空数组；字段：`title`、`message`、`type`（位标志）、`link`、`action_name`、`show_first`、`only_play`、`only_china`、`min_api`、`max_api`、`min_version`、`max_version` |

`api_channel`：`0` 返回稳定版（`$update_stable`），其他值返回测试版（`$update_latest`）。

---

## 2. 部署

要求：**PHP 7.0+**（无扩展依赖，纯数组 + `json_encode`），任意 Web 服务器。

1. 把 `seasonal.php`、`event.php`、`info.php` 上传到站点目录，例如 `/api_v2/`；
2. 确保 PHP 已启用，且文件为 UTF-8 无 BOM（有 BOM 会让 JSON 解析失败）；
3. 检查可访问：

   ```bash
   curl "https://example.com/api_v2/seasonal.php?api_version=1"
   curl "https://example.com/api_v2/event.php?api_version=1"
   curl "https://example.com/api_v2/info.php?api_version=2&api_channel=0"
   ```

4. 客户端 `MessageFragment` 里 `baseUrl` 为 `https://app.kcwiki.org/`，接口路径固定为 `/api_v2/xxx.php`，
   若要换域名需同时修改客户端；纯静态托管可用「构建时把 PHP 输出存成同名 `.json`」的方式，但需自己配置 URL 重写。

Apache / Nginx 默认即可，无需额外配置；如需跨域，另加 `Access-Control-Allow-Origin` 头。

本地语法检查（有 PHP 时）：

```bash
php -l seasonal.php && php -l event.php && php -l info.php
```

---

## 3. 每次更新要改哪里

### 3.1 季节性更新（新季节 → 改 `seasonal.php`）

1. **TITLE 区块**：`title.zh_cn` 改成新季节名（如 `"2026年 秋刀鱼祭典"`）；
2. **GALLERY 区块**：替换 `urls` / `names` / `ids`
   - `urls`：wiki 页面「新立绘」里的 File 名（含 `HD`、年份后缀）；
   - `names`：舰娘中文名；
   - `ids`：用 `wiki_id`（文件名里的三位数字）去 `assets/Ship.json` 查 `id`，顺序与 `urls` 一致；
3. **CONTENT 区块**：按 wiki 页面更新「活动介绍 / 掉落海域 / 兑换说明」正文与数量；
4. **URL 区块**：`url_text` 与 `url` 指向 `https://zh.kcwiki.cn/wiki/季节性/<季节名>`（URL 需转义）；
5. 若当季没有新立绘，删掉整个 GALLERY 区块即可，其余区块不受影响。

### 3.2 活动更新（新活动 → 改 `event.php`）

> 本文件始终只放**最新一次活动**，新活动直接覆盖旧内容。

1. **TITLE（第一个）**：`title.zh_cn` = 年份 + 季节（如 `"2026年夏季活动"`），
   `content.zh_cn` = 活动副标题（如 `"反击！第三十一战队的战斗 / 法兰西舰队的跃动"`）；
2. **URL（第一个）**：`url_text` / `url` 指向 `https://zh.kcwiki.cn/wiki/<年份>年<季节>活动`；
3. **TITLE（活动时间）**：`content.zh_cn` 写开催 / 结束时间；
4. **CONTENT「概述」「海域」「新舰娘获取」「稀有掉落（甲级S胜）」**：更新活动概要、E-x 作战名与阶段、新舰娘掉落 / 奖励、各图 Boss 点稀有掉落；
5. **GALLERY「新舰娘」**：替换 `urls`（`KanMusu<wiki_id>HDBanner.png`）/ `names` / `ids`；
6. **URL「掉落统计」**：一般保持 `https://db.kcwiki.cn/drop/` 不变，仅改 `content.zh_cn` 的年份描述。

### 3.3 发版更新（新 APK → 改 `info.php`）

只改这几个字段（`$update_stable` 与 `$update_latest` 两处都要改，保持一致）：

- `versionCode`：与 `app/build.gradle` 生成的版本号一致，必须递增；
- `versionName`：如 `"1.3.2"`；
- `url` / `url2`：GitHub Release 的 APK 直链；
- `change.zh_cn` / `change.zh_tw` / `change.en`：本次更新说明（数据刷新、统计页、离线图片等）。

需要临时公告时，取消 `info.php` 末尾注释块并修改后 `array_push($json['messages'], $message);` 即可。

---

## 4. 当前内容与数据来源

| 文件 | 当前内容 | 来源 |
| --- | --- | --- |
| `seasonal.php` | 2026年 秋刀鱼祭典 | `https://zh.kcwiki.cn/wiki/季节性/2026年秋刀鱼祭典` |
| `event.php` | 2026年夏季活动：反击！第三十一战队的战斗 / 法兰西舰队的跃动 | `https://zh.kcwiki.cn/wiki/2026年夏季活动` |
| `info.php` | v1.3.2（versionCode 202645） | `version.properties`、GitHub Release |

舰娘名与 `id` 反查自 `app/src/main/assets/Ship.json`；季节性立绘清单与 `Home.json` 的 `seasonal` 条目一致。
