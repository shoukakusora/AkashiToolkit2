package rikka.akashitoolkit.sync;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 刷新主页「游戏情报」栏：从 wiki 的 模板:游戏情报 指定的页面/章节抓取
 * （维护与活动、新舰娘与改造、新装备等条目）。
 */
final class WikiGameInfo {

    private static final Pattern LSTH = Pattern.compile("#lsth:([^|}\\n]+)\\|([^}\\n]+)");
    private static final Pattern HEADING = Pattern.compile("^=+\\s*([^=\\n]+?)\\s*=+$", Pattern.MULTILINE);
    private static final Pattern BULLET = Pattern.compile("^[*#]+\\s*(.+)$", Pattern.MULTILINE);
    private static final Pattern LINK = Pattern.compile("\\[\\[([^\\]|]+)");
    private static final Pattern IMAGE = Pattern.compile("\\[\\[(?:File|\u6587\u4ef6|Image):([^\\]|]+)", Pattern.CASE_INSENSITIVE);

    private WikiGameInfo() {
    }

    static JSONArray load(Context context) throws Exception {
        String tpl = DataSyncHelper.wikiRaw("模板:游戏情报");
        String page = "游戏情报";
        String section = "最新情报";

        Matcher m = LSTH.matcher(tpl == null ? "" : tpl);
        if (m.find()) {
            page = m.group(1).trim();
            section = m.group(2).trim();
        }

        String text = DataSyncHelper.wikiRaw(page);
        String body = extractSection(text, section);
        if (body == null || body.trim().isEmpty()) {
            body = text;
        }

        JSONArray out = new JSONArray();
        Matcher b = BULLET.matcher(body == null ? "" : body);
        while (b.find() && out.length() < 20) {
            String line = b.group(1).trim();
            if (line.isEmpty() || line.startsWith("|")) {
                continue;
            }
            String title = DataSyncHelper.stripWikiInline(line);
            if (title.isEmpty()) {
                continue;
            }

            JSONObject entry = new JSONObject();
            entry.put("title", title);
            entry.put("text", title);

            Matcher link = LINK.matcher(line);
            if (link.find()) {
                entry.put("link", "https://zh.kcwiki.cn/wiki/"
                        + URLEncoder.encode(link.group(1).replace(" ", "_"), "UTF-8").replace("+", "%20"));
            } else {
                entry.put("link", "");
            }

            JSONArray images = new JSONArray();
            Matcher image = IMAGE.matcher(line);
            while (image.find() && images.length() < 2) {
                String name = image.group(1).trim().replace(' ', '_');
                if (name.matches("(?i).+\\.(png|jpg|jpeg)")) {
                    images.put(name);
                }
            }
            entry.put("images", images);
            out.put(entry);
        }

        Log.d("WikiGameInfo", "game info entries: " + out.length());
        return out;
    }

    /** 取 `= 标题 =` 小节正文（到下一个同级或更高级标题为止）。 */
    private static String extractSection(String text, String section) {
        if (text == null || section == null) {
            return "";
        }
        Matcher m = HEADING.matcher(text);
        int start = -1;
        while (m.find()) {
            if (m.group(1).trim().equals(section)) {
                start = m.end();
                break;
            }
        }
        if (start < 0) {
            return "";
        }
        Matcher next = HEADING.matcher(text);
        next.region(start, text.length());
        int end = text.length();
        if (next.find()) {
            end = next.start();
        }
        return text.substring(start, end);
    }
}
