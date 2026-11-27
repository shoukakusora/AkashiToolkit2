package rikka.akashitoolkit.support;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import rikka.akashitoolkit.BuildConfig;

/**
 * 更新检查：读取 GitHub Release，发现新版本时提示用户去 GitHub Release 下载。
 */
public final class UpdateChecker {

    private static final String TAG = "UpdateChecker";

    public static final String API_URL =
            "https://api.github.com/repos/Shoukakusora/AkashiToolkit2/releases/latest";
    public static final String RELEASE_URL =
            "https://github.com/Shoukakusora/AkashiToolkit2/releases/latest";

    private static final String KEY_LAST_CHECK = "update_last_check";
    private static final long CHECK_INTERVAL = 12L * 60 * 60 * 1000;

    private UpdateChecker() {
    }

    public static class Info {
        public String version;
        public String releaseUrl;
        public String apkUrl;
        public String notes;
    }

    public interface Callback {
        void onResult(Info info);
    }

    /** 启动时检查（12 小时内只查一次，静默失败）。 */
    public static void checkOnStart(final Activity activity) {
        BaseSetting settings = Settings.instance(activity);
        long last = settings.getLong(KEY_LAST_CHECK, 0);
        if (System.currentTimeMillis() - last < CHECK_INTERVAL) {
            return;
        }
        settings.putLong(KEY_LAST_CHECK, System.currentTimeMillis());

        new Thread(new Runnable() {
            @Override
            public void run() {
                final Info info;
                try {
                    info = check();
                } catch (Exception e) {
                    Log.d(TAG, "check failed", e);
                    return;
                }
                if (!isNewer(info.version, BuildConfig.VERSION_NAME)) {
                    return;
                }
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!activity.isFinishing()) {
                            showDialog(activity, info);
                        }
                    }
                });
            }
        }).start();
    }

    /** 手动检查（后台线程调用），返回最新版本信息，失败抛异常。 */
    public static Info check() throws Exception {
        try {
            return checkViaApi();
        } catch (Exception e) {
            Log.d(TAG, "api failed, fallback to release redirect", e);
            return checkViaRedirect();
        }
    }

    /** API 不可达时的兜底：读 releases/latest 的重定向地址取版本号。 */
    private static Info checkViaRedirect() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(RELEASE_URL).openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(20000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "AkashiToolkit/" + BuildConfig.VERSION_NAME);
        int code = conn.getResponseCode();
        if (code != 200) {
            throw new IllegalStateException("HTTP " + code);
        }
        String finalUrl = conn.getURL().toString();
        Info info = new Info();
        int idx = finalUrl.lastIndexOf("/tag/");
        info.version = idx >= 0 ? finalUrl.substring(idx + 5).replaceFirst("^[vV]", "") : "";
        info.releaseUrl = finalUrl;
        info.apkUrl = "";
        return info;
    }

    private static Info checkViaApi() throws Exception {
        String json = http(API_URL);
        JSONObject object = new JSONObject(json);

        Info info = new Info();
        info.version = object.optString("tag_name", "").replaceFirst("^[vV]", "");
        info.releaseUrl = object.optString("html_url", RELEASE_URL);
        info.notes = object.optString("body", "");

        JSONArray assets = object.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) {
                    continue;
                }
                String name = asset.optString("name", "");
                if (name.endsWith(".apk")) {
                    info.apkUrl = asset.optString("browser_download_url", "");
                    break;
                }
            }
        }
        return info;
    }

    public static void showDialog(final Activity activity, final Info info) {
        String url = info.apkUrl != null && !info.apkUrl.isEmpty() ? info.apkUrl : info.releaseUrl;
        String message = "发现新版本 v" + info.version + "（当前 v" + BuildConfig.VERSION_NAME + "）\n\n"
                + "下载地址：GitHub Release\n" + info.releaseUrl;

        new AlertDialog.Builder(activity)
                .setTitle("发现新版本")
                .setMessage(message)
                .setPositiveButton("去下载", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                            activity.startActivity(intent);
                        } catch (Exception ignored) {
                        }
                    }
                })
                .setNegativeButton("稍后", null)
                .show();
    }

    public static boolean isNewer(String latest, String current) {
        if (latest == null || latest.isEmpty() || current == null) {
            return false;
        }
        String[] a = latest.split("\\.");
        String[] b = current.split("\\.");
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int x = i < a.length ? parseInt(a[i]) : 0;
            int y = i < b.length ? parseInt(b[i]) : 0;
            if (x != y) {
                return x > y;
            }
        }
        return false;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9].*$", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String http(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("User-Agent", "AkashiToolkit/" + BuildConfig.VERSION_NAME);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
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
}
