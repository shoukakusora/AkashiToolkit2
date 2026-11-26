# 明石工具箱 · Akashi Toolkit（kcwiki 数据同步版）

> 舰队 Collection（艦これ）的离线资料查询工具：舰娘、深海栖姬、装备、任务、远征、海域、改修、统计。
> 本项目是基于 [kcwikizh/Akashi-Toolkit](https://github.com/kcwikizh/Akashi-Toolkit)（作者 Rikka）的**现代化重建 + 数据重制+功能的衍生**分支：
> 升级到 Android 14 构建链，重新从 [舰娘百科](https://zh.kcwiki.cn/) 等来源生成全部数据，并补上原版缺失的功能。

---

## 功能概览

| 模块 | 说明 |
|---|---|
| **舰娘 / 深海栖姬** | 按舰种/舰级分组展开，头像与名称离线显示；支持收藏、搜索、排序 |
| **装备** | 列表 + 详情（改修、属性、图鉴立绘） |
| **任务** | 全任务表（含期间限定/最新任务），奖励结构化显示：`名称x数量`，三选一合并为 `A or B or C`，保留装备星级（★+2） |
| **远征** | 报酬/需求表，支持在线同步 |
| **海域** | 卡片展开：`MapBannerHD`/`MapHD` 两张海图 + 路线分歧表格；详情页含「路线」「掉落」 |
| **改修** | 每日改修（按星期/舰娘，数据对齐 akashi-list.me） |
| **统计** | 开发公式、建造公式（含大建完整表）、掉落统计，支持搜索过滤 |
| **主页** | 游戏情报 / 最新更新 / 季节性活动（含缩略图，可点开跳转） |
| **离线** | 海图、舰娘/深海缩略图、装备立绘随包内置；「关于」页可一键补齐 wiki 上缺失的图，并可刷新各数据表 |

## 数据来源与致谢

| 来源 | 用途 |
|---|---|
| [舰娘百科 zh.kcwiki.cn](https://zh.kcwiki.cn/) | 舰娘、装备、任务、远征、海域、带路条件、季节性、游戏更新 |
| [api.kcwiki.org](https://api.kcwiki.org/) | 游戏母数据、舰娘详情、海域目录 |
| [upload.kcwiki.org](https://upload.kcwiki.org/) | 立绘与活动图片 |
| [akashi-list.me](https://akashi-list.me/) | 每日改修（可改修舰娘与曜日） |
| [db.kcwiki.cn](https://db.kcwiki.cn/) | 开发公式 / 掉落统计参考 |
| [kcwikizh/Akashi-Toolkit](https://github.com/kcwikizh/Akashi-Toolkit) | 应用本体（原作者 Rikka） |

数据与图片版权归舰娘百科及其贡献者所有；**本应用为非商业同人工具**，不提供任何游戏本体资源。
数据表内容遵循舰娘百科的授权条款（CC BY-NC-SA），二次使用请保留出处。

## 构建

环境要求：**JDK 17**、Android SDK（compileSdk / targetSdk 34）、Gradle 7.6.4（仓库自带 wrapper）。

```bash
# 1) 配置 SDK 路径
echo "sdk.dir=/path/to/Android/sdk" > local.properties

# 2) 构建
./gradlew assembleDebug      # 调试包
./gradlew assembleRelease    # 未签名 release 包（见下）
```

### 签名（可选）

仓库**不包含**任何密钥。需要签名的 release 包时：

1. 把 `signing.gradle.sample` 复制为 `signing.gradle`；
2. 设置环境变量 `AKASHI_KEYSTORE`、`AKASHI_KEYSTORE_PASSWORD`、`AKASHI_KEY_ALIAS`、`AKASHI_KEY_PASSWORD`；
3. `./gradlew assembleRelease`。

没有 `signing.gradle` 时，构建照常进行，只是产出未签名 APK。

### 版本号规则

`MAJOR.MINOR.PATCH`，构建成功后才自增（`app/build.gradle` 自动处理）：

- `PATCH` 取 1–10，超过 10 → `MINOR+1, PATCH=1`
- `MINOR` 取 1–14，需要变成 15 时 → `MAJOR+1, MINOR=1, PATCH=1`
- `versionCode` 每次构建 +1，保证覆盖安装

## 数据流水线（`data-pipeline/`）

`app/src/main/assets/` 里的 JSON 由 `data-pipeline/` 下的 Python 脚本从 wiki / API 生成：

| 脚本 | 产出 |
|---|---|
| `ship.py` / `enemy.py` / `equip.py` | `Ship.json`、`Equip.json`（玩家 + 深海） |
| `quest.py` / `quest_reward.py` | `Quest.json`（含奖励结构化与最新任务） |
| `expedition.py` | `Expedition.json` |
| `mapdata.py` / `mapdetail.py` / `routes.py` | `Map.json`（含带路表格）、`MapDetail.json` |
| `seasonal.py` / `home.py` | `Home.json`、`Event.json` |
| `stats.py` / `construction.py` / `dev_nga.py` | `Development.json`、`Drop.json`、`Construction.json` |
| `equip_improvement.py` / `equip_improvement_wiki.py` | `EquipImprovement.json`（每日改修） |
| `map_images.py` / `ship_images_fast.py` / `equip_images.py` | 海图、头像、装备图（打包进 assets） |

运行前需要 Python 3.8+ 与 `Pillow`；脚本会把抓取内容缓存在 `gen/cache/`。

## 目录结构

```
app/src/main/java/rikka/akashitoolkit/   业务代码（ship、equip、quest、map、stats、sync…）
app/src/main/assets/                     数据 JSON 与内置图片（mapimg、shipimg、equipimg）
data-pipeline/                           数据生成脚本
.github/workflows/build.yml              CI：自动构建 debug APK
```

## 已知限制

- 部分深海栖姬在 wiki 上没有官方立绘，显示统一占位图；可在「关于 → 补齐缺失图片」后补下载（若 wiki 已补图）。
- 掉落表本身没有百分比列，因此按「点 → 舰娘」列出，不展示概率。
- 应用内「刷新」依赖网络；首次安装后的内置数据即可离线浏览。


## 构建与发布（CI）

- uild：push/PR 时构建 debug APK 作为 artifact
- 
elease：推送 * 标签时用仓库 Secrets 里的 JKS 打签名包并创建 Release
- 详细步骤见 [docs/CI-AND-SIGNING.md](docs/CI-AND-SIGNING.md)

## 许可证

- 应用代码：**Apache License 2.0**（见 `LICENSE`），原始版本由 Rikka 开发。
- 数据与图片：版权归舰娘百科及原始作者，按 CC BY-NC-SA 使用，仅限非商业用途。
- 本项目与 DMM / 角川 / C2 机关无关。

## 数据来源与致谢

- 数据与内容来自以下来源：
-  舰娘百科 https://zh.kcwiki.cn/wiki/（内容版权归原作者，CC 授权）
-  kcwiki 开放接口 https://api.kcwiki.org/（舰娘、装备、任务、远征、海域等资料）
-  poi-statistics@舰娘百科 https://db.kcwiki.cn/（开发公式与掉落统计）
-  明石改修工厂 https://akashi-list.me（可改修舰娘与曜日
- 原项目 https://github.com/kcwikizh/Akashi-Toolkit
- 感谢舰娘百科编辑组与素材整理者的长期维护，感谢原开发者Rikka及所有贡献者，感谢大肥鱼deepseek及其团队。本构建仅做数据刷新与系统适配，不含商业用途。




