<?php
/**
 * 活动页面数据（API v2）
 *
 * 最近一次活动限定海域：2026年夏季活动
 *   反击！第三十一战队的战斗 / 法兰西舰队的跃动
 * 数据来源：https://zh.kcwiki.cn/wiki/2026年夏季活动
 * 新舰娘 id：app/src/main/assets/Ship.json（按 wiki_id 反查，取 id / name.zh_cn）
 *
 * 每次活动变更时，只需要改本文件的 TITLE（标题 / 副标题）、URL（攻略链接）、
 * CONTENT（活动概要 / 海域 / 新舰娘）与 GALLERY（新舰娘立绘）区块。
 */
header('Content-Type: application/json; charset=utf-8');

$api_version = isset($_GET['api_version']) ? intval($_GET['api_version']) : 0;

$TYPE_TITLE = 0;
$TYPE_GALLERY = 1;
$TYPE_CONTENT = 2;
$TYPE_URL = 3;
$TYPE_VOICE = 4;

$json = array();

// —— TITLE：活动标题 + 副标题 ——
$data = null;
$data['type'] = $TYPE_TITLE;
$data['object']['title']['zh_cn'] = "2026年夏季活动";
$data['object']['content']['zh_cn'] = "反击！第三十一战队的战斗 / 法兰西舰队的跃动";
array_push($json, $data);

// —— URL：详细攻略 ——
// 可读形式的链接：https://zh.kcwiki.cn/wiki/2026年夏季活动
$data = null;
$data['type'] = $TYPE_URL;
$data['object']['title']['zh_cn'] = "详细攻略";
$data['object']['content']['zh_cn'] = "海域地图、贴条、奖励与倍卡表";
$data['object']['url_text']['zh_cn'] = "kcwiki:2026年夏季活动";
$data['object']['url'] = "https://zh.kcwiki.cn/wiki/2026%E5%B9%B4%E5%A4%8F%E5%AD%A3%E6%B4%BB%E5%8A%A8";
array_push($json, $data);

// —— TITLE：活动时间 ——
$data = null;
$data['type'] = $TYPE_TITLE;
$data['object']['title']['zh_cn'] = "活动时间";
$data['object']['content']['zh_cn'] = "2026年7月8日 21:00 — 2026年9月10日 10:00（已结束）";
array_push($json, $data);

// —— CONTENT：概述 ——
$data = null;
$data['type'] = $TYPE_CONTENT;
$data['object']['title']['zh_cn'] = "概述";
$data['object']['content']['zh_cn'] = "2026年夏季活动「反击！第三十一战队的战斗 / 法兰西舰队的跃动」为期间限定海域作战，共5张海图，分为前段（E-1～E-3）与后段（E-4～E-5）。
前段围绕第三十一战队（五十铃、酒匂、卯月、潮、初霜、雪风、长波、高波、岸波、冲波、朝霜、凉月等）展开，自圣贝纳迪诺海峡向乌利西泊地发起全面反攻；参战部队还有多号作战部队、联合舰队、乌利西攻击部队与第六舰队。
后段转向地中海方面，由法第3舰队、法地中海舰队与欧洲联合舰队实施「瓦多作战」，以及法兰西舰队/欧洲联合舰队的跃动。
活动已结束。E-5乙丙丁难度无贴条限制；通关后全部海域各难度均无限制。";
array_push($json, $data);

// —— CONTENT：海域 ——
$data = null;
$data['type'] = $TYPE_CONTENT;
$data['object']['title']['zh_cn'] = "海域";
$data['object']['content']['zh_cn'] = "E-1 第三十一战队驱逐舰的出击（P1解谜+血条 / P2解谜+绿条 / P3血条+削甲）
E-2 通过 圣贝纳迪诺海峡（P1解谜1+解谜2+绿条 / P2血条 / P3血条+削甲）
E-3 对乌利西泊地的全面反攻（P1解谜+血条 / P2绿条 / P3血条 / P4血条+削甲）
E-4 瓦多作战（P1血条 / P2解谜+绿条 / P3血条 / P4血条 / P5血条+削甲）
E-5 法兰西舰队/欧洲联合舰队的跃动（P1解谜1+解谜2+血条 / P2绿条 / P3解谜1+解谜2+血条 / P4血条+削甲）";
array_push($json, $data);

// —— GALLERY：新舰娘立绘（wiki File 名 + 舰娘中文名） ——
// 文件名中的数字是 wiki 舰娘编号，ids 是 Ship.json 的 id（点击可跳转舰娘详情）
$data = null;
$data['type'] = $TYPE_GALLERY;
$data['not_safe'] = true;
$data['object']['title']['zh_cn'] = "新舰娘";
$data['object']['urls'] = array(
"KanMusu634HDBanner.png",
"KanMusu631HDBanner.png",
"KanMusu591HDBanner.png",
"KanMusu532HDBanner.png",
"KanMusu613HDBanner.png",
"KanMusu643HDBanner.png",
"KanMusu641HDBanner.png",
"KanMusu652HDBanner.png",
"KanMusu655HDBanner.png",
"KanMusu645HDBanner.png",
);
$data['object']['names'] = array(
"桐",
"花月",
"里诺",
"印第安纳",
"独立",
"秃鹫",
"阿尔及利亚",
"维斯比",
"日枝丸",
"贝阿恩",
);
$data['object']['action_type'] = 1;
$data['object']['ids'] = array(
	1044,
	1041,
	991,
	932,
	1023,
	1053,
	1051,
	1062,
	1065,
	1055);
array_push($json, $data);

// —— CONTENT：新舰娘获取 ——
$data = null;
$data['type'] = $TYPE_CONTENT;
$data['object']['title']['zh_cn'] = "新舰娘获取";
$data['object']['content']['zh_cn'] = "桐（驱逐舰）E-1 打捞
花月（驱逐舰）E-1、E-2 打捞
里诺(Reno)（轻巡洋舰）E-2 通关奖励
印第安纳(Indiana)（战舰）E-2、E-3 打捞
独立(Independence)（轻空母）E-3 通关奖励
秃鹫(Vautour)（驱逐舰）E-4 打捞
阿尔及利亚(Algérie)（重巡洋舰）E-4 通关奖励
维斯比(Visby)（驱逐舰）E-5 打捞
日枝丸（潜水母舰）E-5 打捞
贝阿恩(Béarn)（正规空母）E-5 通关奖励";
array_push($json, $data);

// —— CONTENT：稀有掉落（甲级S胜） ——
$data = null;
$data['type'] = $TYPE_CONTENT;
$data['object']['title']['zh_cn'] = "稀有掉落（甲级S胜）";
$data['object']['content']['zh_cn'] = "以下为各海域Boss点甲级S胜的部分稀有掉落，其他难度与多号机掉落请在掉落统计中筛选。
E-1：矢矧、酒匂、凉月、冬月、花月、桐、秋津洲、明石
E-2：马里兰、华盛顿、南达科他、印第安纳、萨拉托加、突击者、大黄蜂、塔斯卡卢萨
E-3：南达科他、印第安纳、衣阿华、列克星敦、萨拉托加、大凤、伊13、伊14
E-4：黎塞留、天鹰、扎拉、波拉、加富尔伯爵、利托里奥、罗马、秃鹫
E-5：纳尔逊、罗德尼、厌战、英勇、光荣(Glorious)、让·巴尔、皇家方舟、齐柏林伯爵、维斯比、日枝丸";
array_push($json, $data);

// —— URL：掉落统计 ——
$data = null;
$data['type'] = $TYPE_URL;
$data['object']['title']['zh_cn'] = "掉落统计";
$data['object']['content']['zh_cn'] = "POI2026年夏季活动统计";
$data['object']['url'] = "https://db.kcwiki.cn/drop/";
$data['object']['url_text']['zh_cn'] = "poi-statistics掉落统计";
array_push($json, $data);

echo json_encode($json);
