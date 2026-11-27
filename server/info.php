<?php
/**
 * 应用更新信息（API v2）
 *
 * 返回结构：
 *   update   —— 新版本信息（versionCode / versionName / url / url2 / change）
 *   messages —— 公告列表，无公告时为 []
 *
 * 每次发版时只需要改本文件的 versionCode、versionName、url、url2 与 change。
 */
header('Content-Type: application/json; charset=utf-8');

$api_version = isset($_GET['api_version']) ? intval($_GET['api_version']) : 0;
$api_channel = isset($_GET['api_channel']) ? intval($_GET['api_channel']) : 0;

$NOT_DISMISSIBLE = 1 << 0;
$HTML_CONTENT = 1 << 1;
$ACTION_VIEW_BUTTON = 1 << 2;
$COUNT_DOWN = 1 << 3;

$json['messages'] = array();

// —— 当前版本（稳定版）——
// APK 下载地址：GitHub Release
$update_stable['versionCode'] = 202645;
$update_stable['versionName'] = "1.3.2";
$update_stable['url'] = "https://github.com/Shoukakusora/AkashiToolkit2/releases/download/v1.3.2/AkashiToolkit-1.3.2-kcwiki.apk";
$update_stable['url2'] = $update_stable['url'];
$update_stable['change']['zh_cn'] = "• 刷新游戏数据：新增 2026 年秋刀鱼祭典与 2026 年夏季活动；• 完善统计页：开发 / 建造公式与掉落统计；• 立绘 / 海图 / 装备图片随包内置，支持离线浏览；• 修复问题并优化界面";
$update_stable['change']['zh_tw'] = "• 更新遊戲資料：新增 2026 年秋刀魚祭典與 2026 年夏季活動；• 完善統計頁：開發 / 建造公式與掉落統計；• 立繪 / 海圖 / 裝備圖片隨包內建，支援離線瀏覽；• 修正問題並最佳化介面";
$update_stable['change']['en'] = "• Data refresh: 2026 Sanma Festival and Summer 2026 Event; • Statistics: development / construction / drop tables; • Ship / map / equipment images bundled for offline browsing; • Bug fixes and UI improvements";

// —— 测试版（与稳定版同版本；如需灰度，把 versionCode 提高并换成测试包地址）——
$update_latest['versionCode'] = 202645;
$update_latest['versionName'] = "1.3.2";
$update_latest['url'] = "https://github.com/Shoukakusora/AkashiToolkit2/releases/download/v1.3.2/AkashiToolkit-1.3.2-kcwiki.apk";
$update_latest['url2'] = $update_latest['url'];
$update_latest['change']['zh_cn'] = "• 刷新游戏数据：新增 2026 年秋刀鱼祭典与 2026 年夏季活动；• 完善统计页：开发 / 建造公式与掉落统计；• 立绘 / 海图 / 装备图片随包内置，支持离线浏览；• 修复问题并优化界面";
$update_latest['change']['zh_tw'] = "• 更新遊戲資料：新增 2026 年秋刀魚祭典與 2026 年夏季活動；• 完善統計頁：開發 / 建造公式與掉落統計；• 立繪 / 海圖 / 裝備圖片隨包內建，支援離線瀏覽；• 修正問題並最佳化介面";
$update_latest['change']['en'] = "• Data refresh: 2026 Sanma Festival and Summer 2026 Event; • Statistics: development / construction / drop tables; • Ship / map / equipment images bundled for offline browsing; • Bug fixes and UI improvements";

if ($update_stable['versionCode'] >= $update_latest['versionCode']) {
	$update_latest = $update_stable;
}

/*
 * 公告示例（需要时取消注释并修改）：
 *
 * $message = null;
 * $message['title']['zh_cn'] = "标题";
 * $message['message']['zh_cn'] = "内容";
 * $message['title']['zh_tw'] = "標題";
 * $message['message']['zh_tw'] = "內容";
 * $message['title']['en'] = "Title";
 * $message['message']['en'] = "Message";
 * $message['action_name']['zh_cn'] = "查看详情";
 * $message['action_name']['en'] = "View details";
 * $message['link'] = "https://zh.kcwiki.cn/";
 * $message['type'] = $NOT_DISMISSIBLE | $ACTION_VIEW_BUTTON;
 * array_push($json['messages'], $message);
 */

// api_channel：0 = 稳定版（官网 / GitHub Release），其他 = 测试版
if ($api_channel == 0) {
	$json['update'] = $update_stable;
} else {
	$json['update'] = $update_latest;
}

echo json_encode($json);
