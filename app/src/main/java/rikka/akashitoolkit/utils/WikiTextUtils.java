package rikka.akashitoolkit.utils;

import android.text.TextUtils;

/**
 * Small helper turning the raw kcwiki text found in Home.json / Event.json into
 * something readable inside a TextView.
 *
 * <p>The source text is wiki markup, e.g. {@code ==游戏更新==},
 * {@code File:KanMusu054bHD.png|青叶改晴} or {@code * item}. Headings lose their
 * {@code =} markers, image lines keep only their caption (image-only lines are
 * dropped) and list bullets become "·".</p>
 */
public class WikiTextUtils {

    public static String format(String text) {
        if (TextUtils.isEmpty(text)) {
            return "";
        }

        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n");

        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            String value = line.trim();
            if (value.length() == 0) {
                continue;
            }

            if (value.startsWith("File:")) {
                int index = value.indexOf('|');
                if (index < 0 || index + 1 >= value.length()) {
                    // image without caption
                    continue;
                }
                value = value.substring(index + 1).trim();
                while (value.startsWith("|")) {
                    value = value.substring(1).trim();
                }
                if (value.length() == 0) {
                    continue;
                }
            } else if (value.startsWith("=")) {
                int start = 0;
                while (start < value.length() && value.charAt(start) == '=') {
                    start++;
                }
                int end = value.length();
                while (end > start && value.charAt(end - 1) == '=') {
                    end--;
                }
                value = value.substring(start, end).trim();
                if (value.length() == 0) {
                    continue;
                }
            } else if (value.startsWith("*")) {
                while (value.startsWith("*")) {
                    value = value.substring(1);
                }
                value = value.trim();
                if (value.length() == 0) {
                    continue;
                }
                value = "· " + value;
            }

            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(value);
        }

        if (builder.length() == 0) {
            return normalized.trim();
        }
        return builder.toString();
    }
}
