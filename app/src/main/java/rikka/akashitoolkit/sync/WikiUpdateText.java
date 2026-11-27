package rikka.akashitoolkit.sync;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把「游戏更新」wiki 页面按小节解析成可读正文（与打包数据中的格式一致）：
 * 小节名 + 该节的正文行（如「青叶改侦 / 青叶改侦 (Lv.77+…) / 共五装备格…」）。
 */
final class WikiUpdateText {

    private static final Pattern HEADING = Pattern.compile("^=+\\s*([^=\\n]+?)\\s*=+$");
    private static final Pattern GALLERY = Pattern.compile("<gallery[^>]*>.*?</gallery>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private WikiUpdateText() {
    }

    static String updateText(String page) {
        if (page == null) {
            return "";
        }
        String text = GALLERY.matcher(page).replaceAll("");

        StringBuilder out = new StringBuilder();
        String name = null;
        StringBuilder block = new StringBuilder();

        for (String rawLine : text.split("\n")) {
            String line = rawLine.trim();
            Matcher heading = HEADING.matcher(line);
            if (heading.find()) {
                flush(out, name, block);
                name = heading.group(1).trim();
                block = new StringBuilder();
                continue;
            }
            if (name == null) {
                continue;
            }
            String cleaned = stripInline(line);
            if (cleaned.isEmpty()) {
                continue;
            }
            block.append(cleaned).append('\n');
        }
        flush(out, name, block);
        return out.toString();
    }

    private static void flush(StringBuilder out, String name, StringBuilder block) {
        if (name == null || block.length() == 0) {
            return;
        }
        String body = block.toString().trim();
        if (body.isEmpty()) {
            return;
        }
        if (out.length() > 0) {
            out.append("\n\n");
        }
        out.append(name).append('\n').append(body);
    }

    private static String stripInline(String s) {
        if (s == null) {
            return "";
        }
        String v = s.replace("\u00a0", " ");
        v = v.replaceAll("\\[\\[(?:File|\u6587\u4ef6|Image):[^\\]]*\\]\\]", "");
        v = v.replaceAll("\\[\\[([^\\]|]*)\\|([^\\]]*)\\]\\]", "$2");
        v = v.replaceAll("\\[\\[([^\\]]*)\\]\\]", "$1");
        v = v.replaceAll("\\{\\{(?:Green|Red|Blue|Purple|Color|Colour)\\s*\\|([^{}]*)\\}\\}", "$1");
        v = v.replaceAll("\\{\\{[^{}]*\\}\\}", "");
        v = v.replaceAll("<[^>]+>", "");

        String q3 = new String(new char[]{0x27, 0x27, 0x27});
        String q2 = new String(new char[]{0x27, 0x27});
        v = v.replace(q3, "").replace(q2, "");
        v = v.trim();

        if (v.isEmpty()) {
            return "";
        }
        char first = v.charAt(0);
        if (first == '|' || first == '!' || first == '/') {
            return "";
        }
        if (v.toLowerCase().startsWith("file:")) {
            return "";
        }
        if (v.matches("^\\{?[A-Z]{2,}\\}?$")) {
            return "";
        }
        if (!v.matches(".*[\\u4e00-\\u9fff0-9A-Za-z].*")) {
            return "";
        }
        return v;
    }
}
