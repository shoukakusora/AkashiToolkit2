package rikka.akashitoolkit.support;

import android.content.Context;
import android.util.Log;

import java.io.File;

import rikka.akashitoolkit.staticdata.ShipList;
import rikka.akashitoolkit.event.EventData;
import rikka.akashitoolkit.home.HomeData;
import rikka.akashitoolkit.staticdata.QuestList;

/**
 * 内置数据更新后，旧版本同步缓存（files/json/*.json）必须失效。
 * 启动时比较 CACHE_VERSION，低于当前值就清掉旧缓存，让应用回到内置数据。
 */
public final class CacheVersion {

    /** 每次内置数据有结构性更新时 +1。 */
    public static final int CACHE_VERSION = 7;

    private static final String KEY = "data_cache_version";

    private CacheVersion() {
    }

    public static void bumpIfNeeded(Context context) {
        BaseSetting settings = Settings.instance(context);
        int current = settings.getInt(KEY, 0);
        if (current >= CACHE_VERSION) {
            return;
        }

        File dir = new File(context.getFilesDir(), "json");
        int removed = 0;
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(".json") && file.delete()) {
                        removed++;
                    }
                }
            }
        }

        // 清掉内存缓存，下次读取会回落到 assets
        try {
            HomeData.clear();
        } catch (Exception ignored) {
        }
        try {
            EventData.clear();
        } catch (Exception ignored) {
        }
        try {
            QuestList.clear();
        } catch (Exception ignored) {
        }
        try {
            ShipList.clear();
        } catch (Exception ignored) {
        }

        settings.putInt(KEY, CACHE_VERSION);
        Log.d("CacheVersion", "cleared old caches: " + removed + " (version " + CACHE_VERSION + ")");
    }
}
