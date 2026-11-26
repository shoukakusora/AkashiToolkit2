package rikka.akashitoolkit.staticdata;

import android.content.Context;

import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

import rikka.akashitoolkit.model.DropStat;

/**
 * Drop.json (掉落统计).
 */
public class DropList {
    private static final String FILE_NAME = "Drop.json";
    private static List<DropStat> sList;

    public static synchronized List<DropStat> get(Context context) {
        if (sList == null) {
            sList = new BaseGSONList<DropStat>().get(
                    context,
                    FILE_NAME,
                    new TypeToken<ArrayList<DropStat>>() {}.getType());
        }
        return sList;
    }

    public static synchronized void clear() {
        sList = null;
    }
}
