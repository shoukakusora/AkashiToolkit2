package rikka.akashitoolkit.home;

import android.content.Context;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

import rikka.akashitoolkit.staticdata.BaseGSON;

/**
 * Parsed Home.json, cached for the lifetime of the process.
 *
 * <pre>
 * {"generated":"...",
 *  "game_info":[{"title":"...","text":"...","link":"...","images":[...]}],
 *  "updates":[...],
 *  "seasonal":[{"id":"...","title":"...","date":"...","text":"...","link":"...","images":[...]}]}
 * </pre>
 */
public class HomeData {

    public static final String SECTION_GAME_INFO = "game_info";
    public static final String SECTION_UPDATES = "updates";
    public static final String SECTION_SEASONAL = "seasonal";

    private static HomeData sInstance;

    private List<WikiEntry> game_info;
    private List<WikiEntry> updates;
    private List<WikiEntry> seasonal;

    public static class WikiEntry {
        public String id;
        public String title;
        public String date;
        public String text;
        public String link;
        public List<String> images;

        public List<String> getImages() {
            if (images == null) {
                return new ArrayList<>();
            }
            return images;
        }
    }

    public static synchronized HomeData get(Context context) {
        if (sInstance == null) {
            HomeData data = null;
            try {
                data = (HomeData) new BaseGSON().get(context, "Home.json", HomeData.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
            sInstance = data == null ? new HomeData() : data;
        }
        return sInstance;
    }

    public static synchronized void clear() {
        sInstance = null;
    }

    public List<WikiEntry> getEntries(String section) {
        List<WikiEntry> list;
        if (TextUtils.equals(SECTION_GAME_INFO, section)) {
            list = game_info;
        } else if (TextUtils.equals(SECTION_UPDATES, section)) {
            list = updates;
        } else if (TextUtils.equals(SECTION_SEASONAL, section)) {
            list = seasonal;
        } else {
            list = null;
        }
        return list == null ? new ArrayList<WikiEntry>() : list;
    }
}
