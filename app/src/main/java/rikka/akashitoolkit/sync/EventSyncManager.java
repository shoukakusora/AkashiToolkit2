package rikka.akashitoolkit.sync;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import rikka.akashitoolkit.event.EventData;

/**
 * 活动页数据同步：从 GitHub 上的 server/events.json 拉取（对应 server/event.php 的静态镜像）。
 * 失败时保留本地数据。
 */
public class EventSyncManager {

    private static final String TAG = "EventSyncManager";

    /** server/events.json（可直接替换为你自己部署的 PHP 接口地址）。 */
    public static final String EVENTS_URL =
            "https://raw.githubusercontent.com/Shoukakusora/AkashiToolkit2/main/server/events.json";

    public static int sync(Context context) throws Exception {
        String json = http(EVENTS_URL);
        JSONArray array = new JSONArray(json);
        if (array.length() == 0) {
            throw new IllegalStateException("events.json 为空");
        }
        write(context, "Event2.json", array.toString());
        EventData.clear();
        return array.length();
    }

    private static String http(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (AkashiToolkit)");
        conn.setInstanceFollowRedirects(true);
        int code = conn.getResponseCode();
        if (code != 200) {
            throw new IllegalStateException("HTTP " + code);
        }
        InputStream is = conn.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } finally {
            reader.close();
        }
        return sb.toString();
    }

    private static void write(Context context, String name, String json) throws Exception {
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
}
