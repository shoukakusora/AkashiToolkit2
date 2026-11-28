package rikka.akashitoolkit.sync;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 供各同步器复用的网络/文件/清洗工具。 */
final class DataSyncHelper {

    private static final String HDR_UA = "Mozilla/5.0 (AkashiToolkit)";

    private DataSyncHelper() {
    }

    static String http(String url, String referer) throws Exception {
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

    static String wikiRaw(String title) throws Exception {
        return http("https://zh.kcwiki.cn/index.php?title="
                + URLEncoder.encode(title, "UTF-8") + "&action=raw", "https://zh.kcwiki.cn/");
    }

    static void writeJson(Context context, String name, String json) throws Exception {
        File dir = new File(context.getFilesDir(), "json");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("无法创建缓存目录");
        }
        FileOutputStream out = new FileOutputStream(new File(dir, name));
        try {
            out.write(json.getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    static String stripWikiInline(String s) {
        if (s == null) {
            return "";
        }
        s = s.replaceAll("\\[\\[(?:File|文件|Image):[^\\]]*\\]\\]", "");
        // 颜色模板先取出内部文字，避免 [[a|{{Red|b}}]] 被整体清空
        s = s.replaceAll("\\{\\{(?:Green|Red|Blue|Purple|Color|Colour)\\s*\\|([^{}]*)\\}\\}", "$1");
        s = s.replaceAll("\\[\\[[^\\]|]*\\|([^\\]]*)\\]\\]", "$1");
        s = s.replaceAll("\\[\\[([^\\]]*)\\]\\]", "$1");
        s = s.replaceAll("\\{\\{[^{}|]*\\|([^{}]*)\\}\\}", "$1");
        s = s.replaceAll("\\{\\{[^{}]*\\}\\}", "");
        s = s.replaceAll("<[^>]+>", "");
        s = s.replace("'''", "").replace("''", "");
        s = s.replaceAll("\\s+", " ");
        return s.trim();
    }

    static String firstImage(String wikitext) {
        Matcher m = Pattern.compile("\\[\\[(?:File|文件|Image):([^\\]|]+)").matcher(wikitext);
        while (m.find()) {
            String name = m.group(1).trim().replace(' ', '_');
            if (name.matches("(?i).+\\.(png|jpg|jpeg)")) {
                return name;
            }
        }
        return null;
    }
}
