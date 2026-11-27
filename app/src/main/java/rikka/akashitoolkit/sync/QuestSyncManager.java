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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 舰娘百科 (zh.kcwiki.cn) 抓取「任务」页面并生成本地 Quest.json。
 *
 * 数据来源: https://zh.kcwiki.cn/index.php?title=任务&action=raw
 * 输出: filesDir/json/Quest.json (BaseGSONList 会优先读取该文件)
 */
public class QuestSyncManager {

    private static final String TAG = "QuestSyncManager";

    private static android.content.Context sContext;

    public static final String WIKI_RAW_URL =
            "https://zh.kcwiki.cn/index.php?title=%E4%BB%BB%E5%8A%A1&action=raw";

    /** 任务页顶部的「新任务群」子页面（主页不含这些编号）。 */
    private static final String WIKI_LATEST_URL =
            "https://zh.kcwiki.cn/index.php?title=%E4%BB%BB%E5%8A%A1%2F%E6%9C%80%E6%96%B0%E4%BB%BB%E5%8A%A1&action=raw";

    public static final String OUTPUT_FILE = "Quest.json";

    public static class Result {
        public int count;
        public File file;
    }

    /** 同步任务数据。会阻塞，请在后台线程调用。 */
    public static Result sync(Context context) throws Exception {
        sContext = context == null ? null : context.getApplicationContext();
        String text = download(WIKI_RAW_URL);
        if (text == null || text.length() < 1000) {
            throw new IllegalStateException("wiki 返回内容异常");
        }

        List<JSONObject> quests = parse(text);
        if (quests.isEmpty()) {
            throw new IllegalStateException("没有解析到任务");
        }

        // 最新任务群：任务页顶部的「任务/最新任务」子页面（主页不包含这些编号）
        try {
            String latestText = download(WIKI_LATEST_URL);
            if (latestText != null && latestText.length() > 200) {
                List<JSONObject> latest = parse(latestText);
                if (!latest.isEmpty()) {
                    java.util.Map<String, JSONObject> existing = new java.util.HashMap<>();
                    for (JSONObject q : quests) {
                        existing.put(q.optString("code"), q);
                    }
                    int added = 0;
                    for (int i = latest.size() - 1; i >= 0; i--) {
                        JSONObject q = latest.get(i);
                        q.put("newMission", true);
                        String code = q.optString("code");
                        JSONObject old = existing.get(code);
                        if (old != null) {
                            old.put("newMission", true);
                        } else {
                            quests.add(0, q);
                            existing.put(code, q);
                            added++;
                        }
                    }
                    Log.d(TAG, "latest quests merged: " + added);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "latest quests page failed", e);
        }

        // 由「前置」推导 unlock / after
        Map<String, JSONObject> byCode = new HashMap<>();
        for (JSONObject q : quests) {
            byCode.put(q.optString("code"), q);
        }
        for (JSONObject q : quests) {
            JSONArray unlock = q.optJSONArray("unlock");
            if (unlock == null) {
                continue;
            }
            for (int i = 0; i < unlock.length(); i++) {
                JSONObject before = byCode.get(unlock.optString(i));
                if (before == null) {
                    continue;
                }
                JSONArray after = before.optJSONArray("after");
                if (after == null) {
                    after = new JSONArray();
                    before.put("after", after);
                }
                boolean exists = false;
                for (int j = 0; j < after.length(); j++) {
                    if (after.optString(j).equals(q.optString("code"))) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    after.put(q.optString("code"));
                }
            }
        }

        JSONArray array = new JSONArray();
        for (int i = 0; i < quests.size(); i++) {
            JSONObject q = quests.get(i);
            q.put("id", i);
            array.put(q);
        }

        File dir = new File(context.getFilesDir(), "json");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("无法创建数据目录");
        }
        File out = new File(dir, OUTPUT_FILE);
        File tmp = new File(dir, OUTPUT_FILE + ".tmp");
        FileOutputStream fos = new FileOutputStream(tmp);
        try {
            fos.write(array.toString().getBytes("UTF-8"));
            fos.flush();
        } finally {
            fos.close();
        }
        if (out.exists() && !out.delete()) {
            Log.w(TAG, "cannot delete old file");
        }
        if (!tmp.renameTo(out)) {
            throw new IllegalStateException("无法写入 " + out);
        }

        Result r = new Result();
        r.count = array.length();
        r.file = out;
        Log.d(TAG, "synced " + r.count + " quests -> " + out);
        return r;
    }

    private static String download(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(20000);
        con.setReadTimeout(60000);
        con.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android) AkashiToolkit/0.15 (+https://zh.kcwiki.cn/)");
        con.setRequestProperty("Accept", "text/html,application/xhtml+xml");
        int code = con.getResponseCode();
        if (code != 200) {
            throw new IllegalStateException("HTTP " + code);
        }
        InputStream is = con.getInputStream();
        StringBuilder sb = new StringBuilder(1 << 22);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        try {
            char[] buf = new char[8192];
            int n;
            while ((n = reader.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
        } finally {
            reader.close();
        }
        return sb.toString();
    }

    /** 解析页面中所有 {{任务表|...}}。 */
    private static List<JSONObject> parse(String text) throws Exception {
        List<JSONObject> list = new ArrayList<>();
        Map<String, JSONObject> seen = new LinkedHashMap<>();
        int from = 0;
        while (true) {
            int start = text.indexOf("{{任务表", from);
            if (start < 0) {
                break;
            }
            int end = findTemplateEnd(text, start);
            if (end < 0) {
                break;
            }
            from = end;
            String body = text.substring(start + 2, end - 2);
            try {
                JSONObject quest = parseBlock(body);
                if (quest != null) {
                    String code = quest.optString("code");
                    // 同一编号可能出现多次（限时/复刻），保留字段更完整的一条
                    JSONObject old = seen.get(code);
                    if (old == null || fieldCount(quest) > fieldCount(old)) {
                        seen.put(code, quest);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "parse block failed: " + e);
            }
        }
        for (JSONObject q : seen.values()) {
            list.add(q);
        }
        return list;
    }

    private static int fieldCount(JSONObject o) {
        int n = 0;
        String[] keys = {"title", "content", "note"};
        for (String k : keys) {
            JSONObject obj = o.optJSONObject(k);
            if (obj != null && obj.length() > 0) {
                n++;
            }
        }
        return n;
    }

    /** 从 "{{" 开始找到匹配的 "}}" 之后的位置。 */
    private static int findTemplateEnd(String text, int start) {
        int depth = 0;
        int i = start;
        int n = text.length();
        while (i < n - 1) {
            if (text.charAt(i) == '{' && text.charAt(i + 1) == '{') {
                depth++;
                i += 2;
                continue;
            }
            if (text.charAt(i) == '}' && text.charAt(i + 1) == '}') {
                depth--;
                i += 2;
                if (depth == 0) {
                    return i;
                }
                continue;
            }
            i++;
        }
        return -1;
    }

    /** body 形如 "任务表| type =编制| 编号 =A1|..."，按顶层 '|' 切分。 */
    private static JSONObject parseBlock(String body) throws Exception {
        List<String> parts = splitTopLevel(body, '|');
        if (parts.size() < 2) {
            return null;
        }
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 1; i < parts.size(); i++) {
            String part = parts.get(i);
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = part.substring(0, eq).trim();
            String value = part.substring(eq + 1).trim();
            if (key.startsWith("<!--")) {
                continue;
            }
            fields.put(key, value);
        }

        String code = fields.get("编号");
        if (code == null || code.isEmpty()) {
            return null;
        }
        code = code.trim();

        JSONObject quest = new JSONObject();
        quest.put("code", code);
        quest.put("newMission", false);
        quest.put("period", periodOf(code));
        quest.put("type", typeOf(code));

        JSONObject title = new JSONObject();
        title.put("zh_cn", clean(fields.get("中文任务名字")));
        title.put("zh_tw", "");
        title.put("ja", clean(fields.get("日文任务名字")));
        title.put("en", "");
        quest.put("title", title);

        JSONObject content = new JSONObject();
        content.put("zh_cn", clean(fields.get("中文任务说明")));
        content.put("zh_tw", "");
        content.put("ja", clean(fields.get("日文任务说明")));
        content.put("en", "");
        quest.put("content", content);

        JSONArray resource = new JSONArray();
        resource.put(toInt(fields.get("燃料")));
        resource.put(toInt(fields.get("弹药")));
        resource.put(toInt(fields.get("钢铁")));
        resource.put(toInt(fields.get("铝")));

        JSONObject reward = new JSONObject();
        reward.put("resource", resource);
        reward.put("ship", new JSONArray());
        reward.put("equip", new JSONArray());
        reward.put("item", new JSONArray());
        reward.put("str", clean(fields.get("奖励")));
        reward.put("entries", buildEntries(fields.get("奖励")));
        quest.put("reward", reward);

        quest.put("note", clean(fields.get("备注")));

        JSONArray unlock = new JSONArray();
        String pre = fields.get("前置");
        if (pre != null) {
            for (String s : pre.split("[,，、&;；\\s]+")) {
                s = s.trim();
                if (!s.isEmpty() && !s.equals("-")) {
                    unlock.put(s);
                }
            }
        }
        quest.put("unlock", unlock);
        quest.put("after", new JSONArray());
        return quest;
    }

    /** 顶层按分隔符切分，忽略 [[ ]] 和 {{ }} 内部的分隔符。 */
    private static List<String> splitTopLevel(String s, char sep) {
        List<String> out = new ArrayList<>();
        int link = 0;
        int tmpl = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            char next = i + 1 < s.length() ? s.charAt(i + 1) : '\0';
            if (c == '[' && next == '[') {
                link++;
            } else if (c == ']' && next == ']') {
                if (link > 0) {
                    link--;
                }
            } else if (c == '{' && next == '{') {
                tmpl++;
            } else if (c == '}' && next == '}') {
                if (tmpl > 0) {
                    tmpl--;
                }
            }
            if (c == sep && link == 0 && tmpl == 0) {
                out.add(sb.toString());
                sb.setLength(0);
                continue;
            }
            sb.append(c);
        }
        out.add(sb.toString());
        return out;
    }

    /** A/B/C/D/E/F/G, SF, WA/WB/WC/WF -> 1..7 / 19 */
    private static int typeOf(String code) {
        if (code.startsWith("SF")) {
            return 19;
        }
        if (code.startsWith("W") && code.length() > 1) {
            char c = Character.toUpperCase(code.charAt(1));
            switch (c) {
                case 'A':
                    return 1;
                case 'B':
                    return 2;
                case 'C':
                    return 3;
                case 'D':
                    return 4;
                case 'E':
                    return 5;
                case 'F':
                    return 6;
                case 'G':
                    return 7;
                default:
                    return 0;
            }
        }
        if (code.isEmpty()) {
            return 0;
        }
        switch (Character.toUpperCase(code.charAt(0))) {
            case 'A':
                return 1;
            case 'B':
                return 2;
            case 'C':
                return 3;
            case 'D':
                return 4;
            case 'E':
                return 5;
            case 'F':
                return 6;
            case 'G':
                return 7;
            default:
                return 0;
        }
    }

    /** 日常/周常/月常: B d1 / B w1 / B m1 这类编号后缀。 */
    private static int periodOf(String code) {
        if (code.length() < 2) {
            return 0;
        }
        char c = Character.toLowerCase(code.charAt(1));
        if (c == 'd') {
            return 1;
        }
        if (c == 'w') {
            return 2;
        }
        if (c == 'm') {
            return 3;
        }
        return 0;
    }

    private static int toInt(String s) {
        if (s == null) {
            return 0;
        }
        try {
            return Integer.parseInt(s.replaceAll("[^0-9-]", "").trim());
        } catch (Exception e) {
            return 0;
        }
    }


    /** 把 wiki 奖励文本解析成结构化条目：装备/道具/舰娘 + 数量。 */
    private static JSONArray buildEntries(String raw) {
        JSONArray entries = new JSONArray();
        if (raw == null || raw.trim().isEmpty()) {
            return entries;
        }

        raw = raw.replaceAll("\\[\\[(?:File|文件|Image):[^\\]]*\\]\\]", "");
        // 颜色模板只是内联标注，保留其中文字（如星级 {{Green|★+2}}）
        raw = raw.replaceAll("\\{\\{(?:Green|Red|Blue|Purple|Color|Colour)\\s*\\|([^{}]*)\\}\\}", "$1");
        raw = raw.replace("'''", "").replace("''", "");

        StringBuilder text = new StringBuilder();
        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == '[' && i + 1 < raw.length() && raw.charAt(i + 1) == '[') {
                int end = raw.indexOf("]]", i + 2);
                if (end < 0) {
                    break;
                }
                String inner = raw.substring(i + 2, end).trim();
                i = end + 2;
                if (inner.toLowerCase().startsWith("file:") || inner.startsWith("文件:")
                        || inner.toLowerCase().startsWith("image:")) {
                    continue;
                }
                flushTextEntry(entries, text);
                String name = inner.contains("|")
                        ? inner.substring(inner.lastIndexOf('|') + 1).trim() : inner;
                if (name.isEmpty()) {
                    name = inner;
                }
                int[] cursor = new int[]{i};
                int count = readCount(raw, cursor);
                i = cursor[0];
                addEntityEntry(entries, name, count);
                continue;
            }
            if (c == '{' && i + 1 < raw.length() && raw.charAt(i + 1) == '{') {
                int end = raw.indexOf("}}", i + 2);
                if (end < 0) {
                    break;
                }
                String inner = raw.substring(i + 2, end).trim();
                i = end + 2;
                flushTextEntry(entries, text);
                String name = inner.split("\\|")[0].trim();
                int[] cursor = new int[]{i};
                int count = readCount(raw, cursor);
                i = cursor[0];
                addEntityEntry(entries, name, count);
                continue;
            }
            if (c == '<') {
                int end = raw.indexOf('>', i);
                if (end > 0) {
                    i = end + 1;
                    continue;
                }
            }
            text.append(c);
            i++;
        }
        flushTextEntry(entries, text);
        return entries;
    }

    private static void flushTextEntry(JSONArray entries, StringBuilder text) {
        String value = text.toString();
        text.setLength(0);
        for (String line : value.split("\\n")) {
            line = clean(line).trim();
            if (line.isEmpty()) {
                continue;
            }
            // 单独的星级（★+2）并到上一条奖励名称里
            java.util.regex.Matcher star = java.util.regex.Pattern
                    .compile("^\\s*[\\u2605\\u2606]\\s*(?:[mM][aA][xX]|[+\\uff0b]?\\s*\\d+)").matcher(line);
            if (star.find() && entries.length() > 0) {
                String starText = star.group(0).replace(" ", "");
                String rest = line.substring(star.end()).trim();
                JSONObject prev = entries.optJSONObject(entries.length() - 1);
                if (prev != null) {
                    try {
                        if ("text".equals(prev.optString("type"))) {
                            prev.put("text", prev.optString("text") + starText + rest);
                        } else {
                            prev.put("name", prev.optString("name") + starText + rest);
                        }
                        continue;
                    } catch (Exception ignored) {
                    }
                }
            }
            try {
                JSONObject o = new JSONObject();
                o.put("type", "text");
                o.put("text", line);
                o.put("id", 0);
                o.put("count", 0);
                o.put("name", "");
                entries.put(o);
            } catch (Exception ignored) {
            }
        }
    }

    private static int readCount(String raw, int[] cursor) {
        int i = cursor[0];
        while (i < raw.length() && (raw.charAt(i) == ' ' || raw.charAt(i) == '\u00a0')) {
            i++;
        }
        if (i >= raw.length()) {
            cursor[0] = i;
            return 1;
        }
        char c = raw.charAt(i);
        if (c == '\u00d7' || c == 'x' || c == 'X' || c == '*') {
            int j = i + 1;
            while (j < raw.length() && (raw.charAt(j) == ' ' || raw.charAt(j) == '\u00a0')) {
                j++;
            }
            int start = j;
            while (j < raw.length() && Character.isDigit(raw.charAt(j))) {
                j++;
            }
            if (j > start) {
                cursor[0] = j;
                return toInt(raw.substring(start, j));
            }
        }
        cursor[0] = i;
        return 1;
    }

    private static void addEntityEntry(JSONArray entries, String name, int count) {
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        name = name.trim();
        if (count <= 0) {
            count = 1;
        }

        int id;
        String type;

        id = findShipIdByName(name);
        if (id > 0) {
            type = "ship";
        } else {
            id = findItemIdByName(name);
            if (id > 0) {
                type = "item";
            } else {
                id = findEquipIdByName(name);
                if (id > 0) {
                    type = "equip";
                } else {
                    try {
                        JSONObject o = new JSONObject();
                        o.put("type", "text");
                        o.put("text", name);
                        o.put("id", 0);
                        o.put("count", 0);
                        o.put("name", "");
                        entries.put(o);
                    } catch (Exception ignored) {
                    }
                    return;
                }
            }
        }

        try {
            JSONObject o = new JSONObject();
            o.put("type", type);
            o.put("id", id);
            o.put("count", count);
            o.put("name", name);
            entries.put(o);
        } catch (Exception ignored) {
        }
    }

    private static int findShipIdByName(String name) {
        try {
            java.util.List<rikka.akashitoolkit.model.Ship> list =
                    rikka.akashitoolkit.staticdata.ShipList.get(sContext);
            if (list == null) {
                return 0;
            }
            for (rikka.akashitoolkit.model.Ship ship : list) {
                if (ship == null || ship.getName() == null) {
                    continue;
                }
                if (name.equals(ship.getName().get())) {
                    return ship.getId();
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static int findItemIdByName(String name) {
        try {
            java.util.List<rikka.akashitoolkit.model.Item> list =
                    rikka.akashitoolkit.staticdata.ItemList.get(sContext);
            if (list == null) {
                return 0;
            }
            for (rikka.akashitoolkit.model.Item item : list) {
                if (item == null || item.getName() == null) {
                    continue;
                }
                if (name.equals(item.getName().get())) {
                    return item.getId();
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static int findEquipIdByName(String name) {
        try {
            java.util.List<rikka.akashitoolkit.model.Equip> list =
                    rikka.akashitoolkit.staticdata.EquipList.get(sContext);
            if (list == null) {
                return 0;
            }
            for (rikka.akashitoolkit.model.Equip equip : list) {
                if (equip == null || equip.getName() == null) {
                    continue;
                }
                if (name.equals(equip.getName().get())) {
                    return equip.getId();
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        s = s.replace("'''", "").replace("''", "");
        // 文件链接（图标）整段移除，避免残留 80px / link= 文本
        s = s.replaceAll("\\[\\[(?:File|文件|Image):[^\\]]*\\]\\]", "");
        s = s.replaceAll("\\[\\[([^\\]|]*)\\|([^\\]]*)\\]\\]", "$2");
        s = s.replaceAll("\\[\\[([^\\]]*)\\]\\]", "$1");
        s = s.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n");
        s = s.replaceAll("<[^>]+>", "");
        return s.trim();
    }
}
