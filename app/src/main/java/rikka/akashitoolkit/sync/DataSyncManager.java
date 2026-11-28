package rikka.akashitoolkit.sync;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rikka.akashitoolkit.model.Equip;
import rikka.akashitoolkit.model.Ship;
import rikka.akashitoolkit.staticdata.EquipImprovementList;
import rikka.akashitoolkit.staticdata.EquipList;
import rikka.akashitoolkit.home.HomeData;
import rikka.akashitoolkit.staticdata.ShipList;

/**
 * 应用内数据刷新：
 *  - 主页（最近更新 + 季节性，来自 zh.kcwiki.cn）
 *  - 每日改修（来自 akashi-list.me，按天/按舰娘）
 * 结果写入 files/json/*.json，离线时优先读取。
 */
public class DataSyncManager {

    private static final String TAG = "DataSyncManager";
    private static final String HDR_UA = "Mozilla/5.0 (AkashiToolkit)";

    public static class Result {
        public int homeEntries;
        public int improvementEntries;
        public final List<String> messages = new ArrayList<>();
    }

    // ---------------------------------------------------------------- network

    private static String http(String url, String referer) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", HDR_UA);
        if (referer != null) {
            conn.setRequestProperty("Referer", referer);
        }
        conn.setInstanceFollowRedirects(true);

        int code = conn.getResponseCode();
        InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (is == null) {
            throw new IllegalStateException("HTTP " + code);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        reader.close();
        return sb.toString();
    }

    private static String wikiRaw(String title) throws Exception {
        return http("https://zh.kcwiki.cn/index.php?title="
                + URLEncoder.encode(title, "UTF-8") + "&action=raw", "https://zh.kcwiki.cn/");
    }

    private static void writeJson(Context context, String name, String json) throws Exception {
        File dir = new File(context.getFilesDir(), "json");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("cannot create json dir");
        }
        FileOutputStream out = new FileOutputStream(new File(dir, name));
        try {
            out.write(json.getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    // ------------------------------------------------------------------- home

    /** 刷新主页：最近更新 + 季节性（图片与链接一并更新）。 */
    public static int syncHome(Context context) throws Exception {
        String seasonalPage = wikiRaw("季节性");

        // 先按年份收集（页面顺序是从旧到新），之后按年份倒序、同年内倒序
        String curYear = "";
        List<String[]> items = new ArrayList<>();
        List<Object[]> blocks = new ArrayList<>();   // [year, items]
        for (String rawLine : seasonalPage.split("\n")) {
            String line = rawLine.trim();
            Matcher hm = Pattern.compile("^==\\s*(\\d{4})年\\s*==$").matcher(line);
            if (hm.find()) {
                if (!curYear.isEmpty() && !items.isEmpty()) {
                    blocks.add(new Object[]{curYear, items});
                }
                curYear = hm.group(1);
                items = new ArrayList<>();
                continue;
            }
            Matcher lm = Pattern.compile("^\\*\\s*\\[\\[(/[^\\]|]+)\\|([^\\]]+)\\]\\]").matcher(line);
            if (lm.find() && !curYear.isEmpty()) {
                items.add(new String[]{lm.group(2).trim(), lm.group(1).trim()});
            }
        }
        if (!curYear.isEmpty() && !items.isEmpty()) {
            blocks.add(new Object[]{curYear, items});
        }

        // 年份倒序（2026 在最前）
        Collections.sort(blocks, new Comparator<Object[]>() {
            @Override
            public int compare(Object[] a, Object[] b) {
                return ((String) b[0]).compareTo((String) a[0]);
            }
        });

        JSONArray seasonal = new JSONArray();
        for (Object[] block : blocks) {
            String year = (String) block[0];
            @SuppressWarnings("unchecked")
            List<String[]> yearItems = (List<String[]>) block[1];
            // 同年内按页面顺序倒序（最新季节在前）
            for (int i = yearItems.size() - 1; i >= 0; i--) {
                JSONObject entry = buildSeasonalEntry(context, year,
                        yearItems.get(i)[0], yearItems.get(i)[1]);
                if (entry != null) {
                    seasonal.put(entry);
                }
                if (seasonal.length() >= 14) {
                    break;
                }
            }
            if (seasonal.length() >= 14) {
                break;
            }
        }

        // 再次按年份倒序（防止抓取顺序异常），并校验最新年份
        JSONArray sorted = HomeRefreshHelper.sortSeasonal(seasonal);
        if (sorted != null) {
            seasonal = sorted;
        }
        if (!HomeRefreshHelper.looksRecent(seasonal)) {
            throw new IllegalStateException("季节性数据异常（最新年份不是近年），已保留旧数据");
        }

        // 仅最新季节展示内容，历史季节只保留标题
        for (int i = 0; i < seasonal.length(); i++) {
            if (i == 0) {
                continue;
            }
            JSONObject entry = seasonal.optJSONObject(i);
            if (entry != null) {
                entry.put("text", "");
                entry.put("images", new JSONArray());
            }
        }

        JSONArray updates = syncUpdates(context);

        JSONObject home = new JSONObject();
        // 以当前内置数据为基调
        JSONArray baseline = new JSONArray();
        try {
            String old = HomeRefreshHelper.readAsset(context, "Home4.json");
            if (old != null) {
                JSONObject oldHome = new JSONObject(old);
                JSONArray bundled = oldHome.optJSONArray("game_info");
                if (bundled != null) {
                    baseline = bundled;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "read bundled game info failed", e);
        }

        JSONArray wikiInfo = null;
        try {
            wikiInfo = WikiGameInfo.load(context);
        } catch (Exception e) {
            Log.w(TAG, "game info sync failed, keep bundled", e);
        }

        JSONArray gameInfo = mergeGameInfo(wikiInfo, baseline);
        if (gameInfo.length() == 0) {
            throw new IllegalStateException("游戏情报抓取为空，已保留旧数据");
        }
        home.put("game_info", gameInfo);
        home.put("updates", updates);
        home.put("seasonal", seasonal);

        writeJson(context, "Home4.json", home.toString());
        HomeData.clear();
        return seasonal.length() + updates.length();
    }


    /** 以内置条目为基调：wiki 抓到的新条目放最前，其余保留内置内容。 */
    private static JSONArray mergeGameInfo(JSONArray wiki, JSONArray baseline) throws Exception {
        JSONArray out = new JSONArray();
        java.util.Set<String> seen = new java.util.HashSet<>();
        if (wiki != null) {
            for (int i = 0; i < wiki.length() && out.length() < 20; i++) {
                JSONObject item = wiki.optJSONObject(i);
                if (item == null) continue;
                String title = item.optString("title", "");
                if (title.isEmpty() || seen.contains(title)) continue;
                seen.add(title);
                out.put(item);
            }
        }
        if (baseline != null) {
            for (int i = 0; i < baseline.length() && out.length() < 20; i++) {
                JSONObject item = baseline.optJSONObject(i);
                if (item == null) continue;
                String title = item.optString("title", "");
                if (title.isEmpty() || seen.contains(title)) continue;
                seen.add(title);
                out.put(item);
            }
        }
        return out;
    }

    /** 构建单个季节条目（失败返回 null）。 */
    private static JSONObject buildSeasonalEntry(Context context, String year, String title, String path) {
        String pageTitle = "季节性" + path;
        JSONArray images = new JSONArray();
        String text = "";
        try {
            String page = wikiRaw(pageTitle);
            for (String name : imagesOf(page)) {
                images.put(name);
                if (images.length() >= 6) {
                    break;
                }
            }
            text = stripWiki(page);
        } catch (Exception e) {
            Log.w(TAG, "seasonal page failed: " + pageTitle, e);
        }

        try {
            JSONObject entry = new JSONObject();
            entry.put("id", path.replace("/", "-"));
            entry.put("title", title);
            entry.put("date", year);
            entry.put("text", text);
            entry.put("link", "https://zh.kcwiki.cn/wiki/"
                    + URLEncoder.encode(pageTitle.replace(" ", "_"), "UTF-8").replace("+", "%20"));
            entry.put("images", images);
            return entry;
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONArray syncUpdates(Context context) throws Exception {
        JSONArray out = new JSONArray();
        String api = "https://zh.kcwiki.cn/api.php?action=query&format=json&list=allpages"
                + "&apprefix=" + URLEncoder.encode("游戏更新/", "UTF-8") + "&aplimit=100&apdir=descending";
        JSONObject res = new JSONObject(http(api, "https://zh.kcwiki.cn/"));
        JSONArray pages = res.getJSONObject("query").getJSONArray("allpages");
        List<String> titles = new ArrayList<>();
        for (int i = 0; i < pages.length(); i++) {
            titles.add(pages.getJSONObject(i).getString("title"));
        }
        // 按日期倒序
        java.util.Collections.sort(titles, new java.util.Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return keyOf(b).compareTo(keyOf(a));
            }
        });

        for (int i = 0; i < titles.size() && out.length() < 3; i++) {
            String title = titles.get(i);
            String page;
            try {
                page = wikiRaw(title);
            } catch (Exception e) {
                continue;
            }
            JSONObject entry = new JSONObject();
            entry.put("title", title.substring(title.lastIndexOf('/') + 1) + " 游戏更新");
            String text = WikiUpdateText.updateText(page);
            entry.put("text", text);
            entry.put("link", "https://zh.kcwiki.cn/wiki/"
                    + URLEncoder.encode(title.replace(" ", "_"), "UTF-8").replace("+", "%20"));
            JSONArray images = new JSONArray();
            for (String name : imagesOf(page)) {
                images.put(name);
                if (images.length() >= 6) {
                    break;
                }
            }
            entry.put("images", images);
            out.put(entry);
        }
        return out;
    }

    private static String keyOf(String title) {
        Matcher m = Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日").matcher(title);
        if (!m.find()) {
            return "0000-00-00";
        }
        return String.format("%04d-%02d-%02d", Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
    }

    private static List<String> imagesOf(String wikitext) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("\\[\\[(?:File|文件|Image):([^\\]|]+)").matcher(wikitext);
        while (m.find()) {
            String name = m.group(1).trim().replace(' ', '_');
            if (name.matches("(?i).+\\.(png|jpg|jpeg)") && !out.contains(name)) {
                out.add(name);
            }
        }
        Matcher g = Pattern.compile("<gallery[^>]*>(.*?)</gallery>", Pattern.DOTALL).matcher(wikitext);
        while (g.find()) {
            for (String line : g.group(1).split("\n")) {
                String name = line.split("\\|")[0].trim().replace(' ', '_');
                if (name.isEmpty() || name.startsWith("#")) {
                    continue;
                }
                if (name.toLowerCase().startsWith("file:")) {
                    name = name.substring(5).trim();
                }
                if (name.matches("(?i).+\\.(png|jpg|jpeg)") && !out.contains(name)) {
                    out.add(name);
                }
            }
        }
        return out;
    }

    private static String stripWiki(String s) {
        if (s == null) {
            return "";
        }
        s = s.replaceAll("\\[\\[(?:File|文件|Image):[^\\]]*\\]\\]", "");
        s = s.replaceAll("\\[\\[[^\\]|]*\\|([^\\]]*)\\]\\]", "$1");
        s = s.replaceAll("\\[\\[([^\\]]*)\\]\\]", "$1");
        s = s.replaceAll("<[^>]+>", "");
        s = s.replace("'''", "").replace("''", "");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String line : s.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("|") || line.startsWith("{|") || line.startsWith("=")) {
                continue;
            }
            sb.append(line).append('\n');
            // 保留全文，不再截断
        }
        return sb.toString().trim();
    }

    // ------------------------------------------------------------ improvement

    private static final String[] WEEKDAYS = {"日", "月", "火", "水", "木", "金", "土"};

    /**
     * 刷新每日改修：来源 https://akashi-list.me/
     * 每个装备块里逐个 support-ship 读取「可改修曜日」，按天（bit0=周日…bit6=周六）输出。
     */
    public static int syncImprovement(Context context) throws Exception {
        String html = http("https://akashi-list.me/", "https://akashi-list.me/");

        Map<String, Integer> equipByName = new LinkedHashMap<>();
        List<Equip> equips = EquipList.get(context);
        if (equips != null) {
            for (Equip e : equips) {
                if (e == null || e.getName() == null) {
                    continue;
                }
                if (e.getName().get() != null) {
                    equipByName.put(normalize(e.getName().get()), e.getId());
                }
                if (e.getName().getJa() != null) {
                    equipByName.put(normalize(e.getName().getJa()), e.getId());
                }
            }
        }

        Map<Integer, Integer> shipByWikiNo = new LinkedHashMap<>();
        List<Ship> ships = ShipList.get(context);
        if (ships != null) {
            for (Ship s : ships) {
                if (s == null || s.isEnemy() || s.getWikiId() == null) {
                    continue;
                }
                Matcher m = Pattern.compile("^(\\d+)([a-z]*)$").matcher(s.getWikiId().trim());
                if (m.find()) {
                    int no = Integer.parseInt(m.group(1));
                    if (m.group(2).isEmpty()) {
                        shipByWikiNo.put(no, s.getId());
                    } else if (!shipByWikiNo.containsKey(no)) {
                        shipByWikiNo.put(no, s.getId());
                    }
                }
            }
        }

        String[] blocks = html.split("<div class=weapon id=w\\d+");
        JSONArray result = new JSONArray();
        for (int bi = 1; bi < blocks.length; bi++) {
            String block = blocks[bi];
            Matcher alt = Pattern.compile("alt=\"(\\d+):\\s*([^\"]+)\"").matcher(block);
            if (!alt.find()) {
                continue;
            }
            Integer equipId = equipByName.get(normalize(alt.group(2).trim()));
            if (equipId == null) {
                continue;
            }

            Map<Integer, List<Integer>> grouped = new LinkedHashMap<>();
            Matcher sup = Pattern.compile(
                    "class=support-ship><img[^>]*?/s(\\d{3,4})\\.png").matcher(block);
            while (sup.find()) {
                int shipNo = Integer.parseInt(sup.group(1));
                Integer shipId = shipByWikiNo.get(shipNo);
                if (shipId == null) {
                    continue;
                }
                String rest = block.substring(sup.end(), Math.min(block.length(), sup.end() + 2000));
                int mask = 0;
                Matcher day = Pattern.compile("class=enable>([日月火水木金土])<").matcher(rest);
                while (day.find()) {
                    for (int d = 0; d < WEEKDAYS.length; d++) {
                        if (WEEKDAYS[d].equals(day.group(1))) {
                            mask |= 1 << d;
                        }
                    }
                }
                if (mask == 0) {
                    mask = 0x7F;  // 未标注视为每天
                }
                List<Integer> list = grouped.get(mask);
                if (list == null) {
                    list = new ArrayList<>();
                    grouped.put(mask, list);
                }
                if (!list.contains(shipId)) {
                    list.add(shipId);
                }
            }
            if (grouped.isEmpty()) {
                continue;
            }

            JSONObject entry = new JSONObject();
            entry.put("id", equipId);
            entry.put("upgrade_to", JSONObject.NULL);
            JSONObject data = new JSONObject();
            for (Map.Entry<Integer, List<Integer>> e : grouped.entrySet()) {
                JSONArray ids = new JSONArray();
                for (Integer id : e.getValue()) {
                    ids.put(id);
                }
                data.put(String.valueOf(e.getKey()), ids);
            }
            entry.put("data", data);
            result.put(entry);
        }

        if (result.length() == 0) {
            throw new IllegalStateException("akashi-list parse produced no entries");
        }
        writeJson(context, "EquipImprovement.json", result.toString());
        EquipImprovementList.clear();
        return result.length();
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        s = s.replace(" ", "").replace("\u3000", "");
        s = s.replace("\uff08", "(").replace("\uff09", ")");
        s = s.replace("弾", "弹").replace("髙", "高").replace("姫", "姬");
        return s.trim();
    }
}
