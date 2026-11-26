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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 舰娘百科 (zh.kcwiki.cn) 抓取「远征列表」并生成本地 Expedition.json。
 *
 * 数据来源: https://zh.kcwiki.cn/index.php?title=远征列表&action=raw
 * 页面里有成对的模板:
 * {{远征报酬表|编号=1|日文名字=...|中文名字=...|耗时=00:15|...}}
 * {{远征需求表|编号=1|...|旗舰等级=1|最低舰娘数=2|必要舰娘=任意|燃料消耗=-3|弹药消耗=}}
 */
public class ExpeditionSyncManager {

    private static final String TAG = "ExpeditionSync";

    public static final String WIKI_RAW_URL =
            "https://zh.kcwiki.cn/index.php?title=%E8%BF%9C%E5%BE%81%E5%88%97%E8%A1%A8&action=raw";

    public static final String OUTPUT_FILE = "Expedition.json";

    private static final String[] RES_NAMES = {"燃料", "弹药", "钢铁", "铝"};

    /** 页面上的海域小节，顺序即 type */
    private static final String[] AREA_HEADINGS = {
            "镇守府海域", "南西诸岛海域", "北方海域", "西方海域", "南方海域", "中部海域", "南西海域"
    };

    public static class Result {
        public int count;
        public File file;
    }

    public static Result sync(Context context) throws Exception {
        String text = download(WIKI_RAW_URL);
        if (text == null || text.length() < 1000) {
            throw new IllegalStateException("wiki 返回内容异常");
        }

        Map<String, JSONObject> rewards = parseTemplates(text, "远征报酬表");
        Map<String, JSONObject> requires = parseTemplates(text, "远征需求表");
        if (rewards.isEmpty()) {
            throw new IllegalStateException("没有解析到远征数据");
        }

        JSONArray array = new JSONArray();
        for (Map.Entry<String, JSONObject> e : rewards.entrySet()) {
            String code = e.getKey();
            JSONObject rewardRaw = e.getValue();
            JSONObject requireRaw = requires.get(code);

            JSONObject item = new JSONObject();
            item.put("id", toNumericId(code));
            item.put("type", areaTypeOf(rewardRaw.optString("__area")));

            JSONObject name = new JSONObject();
            name.put("zh_cn", clean(rewardRaw.optString("中文名字")));
            name.put("zh_tw", "");
            name.put("ja", clean(rewardRaw.optString("日文名字")));
            name.put("en", "");
            item.put("name", name);

            int minutes = parseDuration(rewardRaw.optString("耗时"));
            item.put("time", minutes);
            item.put("timeString", String.format("%02d:%02d", minutes / 60, minutes % 60));

            JSONObject reward = new JSONObject();
            reward.put("playerXP", clean(rewardRaw.optString("提督经验值")));
            reward.put("shipXP", clean(rewardRaw.optString("舰娘经验值")));

            JSONArray res = new JSONArray();
            JSONArray resStr = new JSONArray();
            for (String key : RES_NAMES) {
                String v = clean(rewardRaw.optString(key));
                int normal = 0;
                int great = 0;
                if (!v.isEmpty()) {
                    String[] parts = v.split("/");
                    normal = num(parts[0]);
                    great = parts.length > 1 ? num(parts[1]) : 0;
                }
                res.put(normal);
                if (normal <= 0) {
                    resStr.put("");
                } else if (minutes > 0) {
                    int perHour = (int) Math.round(normal * 60.0 / minutes);
                    resStr.put(String.format("%d<br><b>%d/h</b>", normal, perHour));
                } else {
                    resStr.put(String.valueOf(normal));
                }
            }
            reward.put("resource", res);
            reward.put("resourceString", resStr);
            String award = clean(rewardRaw.optString("奖励"));
            if (!award.isEmpty()) {
                reward.put("award", award);
            }
            String award2 = clean(rewardRaw.optString("大成功奖励"));
            if (!award2.isEmpty()) {
                reward.put("award2", award2);
            }
            item.put("reward", reward);

            JSONObject require = new JSONObject();
            require.put("totalLevel", num(requireRaw == null ? "" : requireRaw.optString("舰队总等级")));
            require.put("flagshipLevel", num(requireRaw == null ? "" : requireRaw.optString("旗舰等级")));
            require.put("minShips", num(requireRaw == null ? "" : requireRaw.optString("最低舰娘数")));
            require.put("essentialShip", clean(requireRaw == null ? "" : requireRaw.optString("必要舰娘")));
            String bucket = clean(requireRaw == null ? "" : requireRaw.optString("输送桶"));
            require.put("bucket", bucket.equals("0") ? "" : bucket);

            JSONArray consume = new JSONArray();
            JSONArray consumeStr = new JSONArray();
            for (String key : new String[]{"燃料消耗", "弹药消耗"}) {
                String v = clean(requireRaw == null ? "" : requireRaw.optString(key));
                double d = 0;
                if (!v.isEmpty()) {
                    java.util.regex.Matcher dm = java.util.regex.Pattern
                            .compile("^[-+]?\\d+(?:\\.\\d+)?")
                            .matcher(v.trim());
                    if (dm.find()) {
                        try {
                            d = Double.parseDouble(dm.group());
                        } catch (Exception ignored) {
                        }
                    }
                }
                consume.put(d);
                consumeStr.put(v.isEmpty() ? "" : v);
            }
            require.put("consume", consume);
            require.put("consumeString", consumeStr);
            item.put("require", require);

            array.put(item);
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
        if (out.exists()) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
        }
        if (!tmp.renameTo(out)) {
            throw new IllegalStateException("无法写入 " + out);
        }

        Result r = new Result();
        r.count = array.length();
        r.file = out;
        Log.d(TAG, "synced " + r.count + " expeditions -> " + out);
        return r;
    }

    /** 收集某个模板的所有实例，按出现顺序；并记录其所在的海域小节。 */
    private static Map<String, JSONObject> parseTemplates(String text, String template) throws Exception {
        Map<String, JSONObject> out = new LinkedHashMap<>();
        int from = 0;
        String needle = "{{" + template;
        while (true) {
            int start = text.indexOf(needle, from);
            if (start < 0) {
                break;
            }
            int end = findTemplateEnd(text, start);
            if (end < 0) {
                break;
            }
            from = end;
            String body = text.substring(start + 2, end - 2);
            List<String> parts = splitTopLevel(body, '|');
            Map<String, String> fields = new LinkedHashMap<>();
            for (int i = 1; i < parts.size(); i++) {
                String part = parts.get(i);
                int eq = part.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                fields.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
            }
            String code = clean(fields.get("编号")).replaceAll("\\s+", "");
            if (code.isEmpty()) {
                continue;
            }
            // "A4月常" -> "A4"
            code = code.replaceAll("(月常|交战型|交战II型|交战|季常|限时).*$", "");
            JSONObject obj = new JSONObject();
            for (Map.Entry<String, String> f : fields.entrySet()) {
                obj.put(f.getKey(), f.getValue());
            }
            obj.put("__area", areaOf(text, start));
            out.put(code, obj);
        }
        return out;
    }

    /** 记录所有二级标题的位置，用于判断模板属于哪个海域小节。 */
    private static String areaOf(String text, int pos) {
        int last = -1;
        String heading = "";
        for (String h : AREA_HEADINGS) {
            int idx = text.lastIndexOf("== " + h + " ==", pos);
            if (idx < 0) {
                idx = text.lastIndexOf("==" + h + "==", pos);
            }
            if (idx > last) {
                last = idx;
                heading = h;
            }
        }
        return heading;
    }

    private static int areaTypeOf(String area) {
        for (int i = 0; i < AREA_HEADINGS.length; i++) {
            if (AREA_HEADINGS[i].equals(area)) {
                return i;
            }
        }
        return AREA_HEADINGS.length - 1;
    }

    private static int parseDuration(String s) {
        if (s == null) {
            return 0;
        }
        s = clean(s);
        if (s.contains(":")) {
            String[] p = s.split(":");
            try {
                return Integer.parseInt(p[0].trim()) * 60 + Integer.parseInt(p[1].trim());
            } catch (Exception ignored) {
                return 0;
            }
        }
        return num(s);
    }

    private static int toNumericId(String code) {
        if (code == null || code.isEmpty()) {
            return 0;
        }
        char c = code.charAt(0);
        if (Character.isDigit(c)) {
            return num(code);
        }
        // A1 -> 1001, B2 -> 2002 (与原版数据的 id 约定一致)
        int base = (Character.toUpperCase(c) - 'A' + 1) * 1000;
        return base + num(code.substring(1));
    }

    private static int num(String s) {
        if (s == null) {
            return 0;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("-?\\d+").matcher(s);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group());
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

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


    /** {{名字}} -> 名字；{{名字|参数}} -> 参数（取最后一个非空参数） */
    private static String resolveTemplates(String s) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\{\\{([^{}]*)\\}\\}");
        for (int guard = 0; guard < 5; guard++) {
            java.util.regex.Matcher m = p.matcher(s);
            if (!m.find()) {
                break;
            }
            StringBuffer sb = new StringBuffer(s.length());
            m.reset();
            while (m.find()) {
                String inner = m.group(1);
                String[] parts = inner.split("\\|");
                String value = parts[parts.length - 1].trim();
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(value));
            }
            m.appendTail(sb);
            s = sb.toString();
        }
        return s;
    }

    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        s = s.replace("'''", "").replace("''", "");
        s = s.replaceAll("<br\\s*/?>", " ");
        s = s.replaceAll("\\[\\[([^\\]|]*)\\|([^\\]]*)\\]\\]", "$2");
        s = s.replaceAll("\\[\\[([^\\]]*)\\]\\]", "$1");
        s = resolveTemplates(s);
        s = s.replaceAll("<[^>]+>", "");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    private static String download(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(20000);
        con.setReadTimeout(60000);
        con.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android) AkashiToolkit/0.14.3 (+https://zh.kcwiki.cn/)");
        con.setRequestProperty("Accept", "text/html,application/xhtml+xml");
        if (con.getResponseCode() != 200) {
            throw new IllegalStateException("HTTP " + con.getResponseCode());
        }
        StringBuilder sb = new StringBuilder(1 << 20);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(con.getInputStream(), "UTF-8"));
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
}
