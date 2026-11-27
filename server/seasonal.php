<?php
/**
 * 季节性页面数据（API v2）
 *
 * 当前季节性：2026年 秋刀鱼祭典
 * 数据来源：https://zh.kcwiki.cn/wiki/季节性/2026年秋刀鱼祭典
 * 立绘文件名与舰娘名对应：app/src/main/assets/Home.json（seasonal 条目）
 * 舰娘 id：app/src/main/assets/Ship.json（按 wiki_id 反查，取 id / name.zh_cn）
 *
 * 更新季节性时，只需要改本文件的 TITLE / GALLERY / CONTENT / URL 四类区块。
 */
header('Content-Type: application/json; charset=utf-8');

$api_version = isset($_GET['api_version']) ? intval($_GET['api_version']) : 0;

$TYPE_TITLE = 0;
$TYPE_GALLERY = 1;
$TYPE_CONTENT = 2;
$TYPE_URL = 3;
$TYPE_VOICE = 4;

$json = array();

// —— TITLE：当前季节性标题 + 提示 ——
$data = null;
$data['type'] = $TYPE_TITLE;
$data['object']['title']['zh_cn'] = "2026年 秋刀鱼祭典";
$data['object']['summary']['zh_cn'] = "更多内容在“活动”中查看";
array_push($json, $data);

// —— GALLERY：新立绘（wiki File 名 + 舰娘中文名） ——
// 文件名中的数字是 wiki 舰娘编号，ids 是 Ship.json 的 id（点击可跳转舰娘详情）
$data = null;
$data['type'] = $TYPE_GALLERY;
$data['not_safe'] = true;
$data['object']['title']['zh_cn'] = "新立绘";
$data['object']['urls'] = array(
"KanMusu645HDIllustSanma2026.png",
"KanMusu650HDIllustSanma2026.png",
"KanMusu651HDIllustSanma2026.png",
"KanMusu613HDIllustSanma2026.png",
"KanMusu618HDIllustSanma2026.png",
"KanMusu626HDIllustSanma2026.png",
"KanMusu018HDIllustSanma2026.png",
"KanMusu427HDIllustSanma2026.png",
);
$data['object']['names'] = array(
"贝阿恩",
"贝阿恩改",
"贝阿恩 amélioration",
"独立",
"独立改",
"独立 Flight II",
"敷波",
"敷波改二",
);
$data['object']['action_type'] = 1;
$data['object']['ids'] = array(
	1055,
	1060,
	1061,
	1023,
	1028,
	1036,
	14,
	627);
array_push($json, $data);

// —— CONTENT：秋刀鱼活动介绍 ——
$data = null;
$data['type'] = $TYPE_CONTENT;
$data['object']['title']['zh_cn'] = "秋刀鱼活动介绍";
$data['object']['content']['zh_cn'] = "每年10月下旬至11月中旬的秋刀鱼活动期间，出击特定海域将有几率在战斗结算时获得秋刀鱼（2015~2019年、2021~2026年），反复出击收集秋刀鱼达到一定数量，可换取装备、家具及各类道具资材等奖励。
一张海图随着短时间内获得的秋刀鱼数量增多，掉率会逐渐下降（渔场枯竭），但会随着时间推移逐渐恢复（具体间隔时间待测试）。
舰队中有特定舰种或特定装备可增加掉率，但部分装备可能会加速渔场的枯竭。
2026年秋刀鱼祭的持续时间相对较短，任务所需的打捞量也相对较少。";
array_push($json, $data);

// —— CONTENT：掉落海域 ——
$data = null;
$data['type'] = $TYPE_CONTENT;
$data['object']['title']['zh_cn'] = "掉落海域";
$data['object']['content']['zh_cn'] = "秋刀鱼可在镇守府海域（1图）、北方海域（3图）、中部海域（6图）与南西海域（7图）的部分海域打捞。
有掉落的通常海域：1-1～1-6、3-1～3-5、6-1、6-5、7-4。
红字代表Boss点；加粗字代表A胜也有秋刀鱼掉落。
2026年秋刀鱼掉率统计可参考「明石の改修工廠」（https://akashi-list.me/）。";
array_push($json, $data);

// —— CONTENT：兑换说明 ——
$data = null;
$data['type'] = $TYPE_CONTENT;
$data['object']['title']['zh_cn'] = "兑换说明";
$data['object']['content']['zh_cn'] = "兑换完全部限量奖励共需要41条，期间限定任务共消耗28条，合计需要打捞69条秋刀鱼；若未持有「试制 震电(局地战斗机)」则需要49条。
秋刀鱼×27：「12.7cm连装炮D型改三」×1+工厂资源×4 或「熟练见张员」★max×4+格纳库增设×6，限兑换1次。
秋刀鱼×7：「秋刀鱼罐头」×1+高速修复材×3 或 高速建造材×25，限兑换2次。
秋刀鱼×5：改修资材×1+开发资材×3，不限兑换次数。
秋刀鱼×3：弹药×300+钢材×150，不限兑换次数。
限时任务「秋刀鱼祭【扩张任务】新秋刀鱼美味料理！」消耗8条，另需熟练见张员×4+开发资材×32+高速建造材×8。
限时任务「超秋刀鱼祭【局战震电持有专用】特别扩张任务」消耗20条，奖励「震电改二(舰战型改二)」。
捕获的秋刀鱼暂存在仓库中，进入仓库点击「使用する」后选择要兑换的物品。";
array_push($json, $data);

// —— URL：详情攻略 ——
// 可读形式的链接：https://zh.kcwiki.cn/wiki/季节性/2026年秋刀鱼祭典
$data = null;
$data['type'] = $TYPE_URL;
$data['object']['title']['zh_cn'] = "详情攻略";
$data['object']['content']['zh_cn'] = "打捞配置、限时任务与兑换一览";
$data['object']['url_text']['zh_cn'] = "kcwiki:季节性/2026年秋刀鱼祭典";
$data['object']['url'] = "https://zh.kcwiki.cn/wiki/%E5%AD%A3%E8%8A%82%E6%80%A7/2026%E5%B9%B4%E7%A7%8B%E5%88%80%E9%B1%BC%E7%A5%AD%E5%85%B8";
array_push($json, $data);

echo json_encode($json);
