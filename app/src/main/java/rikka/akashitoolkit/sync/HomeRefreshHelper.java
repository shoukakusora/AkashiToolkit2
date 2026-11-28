package rikka.akashitoolkit.sync;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 主页刷新相关的小工具：读取内置 assets、按年份排序校验。
 */
final class HomeRefreshHelper {

    private HomeRefreshHelper() {
    }

    /** 读取 APK 内置 assets 文件（HttpURLConnection 不支持 file://，必须用 AssetManager）。 */
    static String readAsset(Context context, String name) {
        InputStream is = null;
        try {
            is = context.getAssets().open(name);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            Log.w("HomeRefreshHelper", "read asset failed: " + name, e);
            return null;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** 按 date（年份）倒序排列季节条目。 */
    static JSONArray sortSeasonal(JSONArray input) {
        if (input == null || input.length() < 2) {
            return input;
        }
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < input.length(); i++) {
            JSONObject item = input.optJSONObject(i);
            if (item != null) {
                list.add(item);
            }
        }
        Collections.sort(list, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                return yearOf(b).compareTo(yearOf(a));
            }
        });
        JSONArray out = new JSONArray();
        for (JSONObject item : list) {
            out.put(item);
        }
        return out;
    }

    private static String yearOf(JSONObject item) {
        String date = item.optString("date", "");
        return date == null ? "" : date;
    }

    /** 最新季节的年份是否合理（避免写回 2013 之类的旧数据）。 */
    static boolean looksRecent(JSONArray seasonal) {
        if (seasonal == null || seasonal.length() == 0) {
            return false;
        }
        JSONObject first = seasonal.optJSONObject(0);
        if (first == null) {
            return false;
        }
        String year = first.optString("date", "");
        try {
            return Integer.parseInt(year) >= 2020;
        } catch (Exception e) {
            return false;
        }
    }
}
