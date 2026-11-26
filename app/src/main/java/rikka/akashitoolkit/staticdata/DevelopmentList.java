package rikka.akashitoolkit.staticdata;

import android.content.Context;

import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

import rikka.akashitoolkit.model.Development;

/**
 * Development.json (开发公式统计).
 */
public class DevelopmentList {
    private static final String FILE_NAME = "Development.json";
    private static List<Development> sList;

    public static synchronized List<Development> get(Context context) {
        if (sList == null) {
            sList = new BaseGSONList<Development>().get(
                    context,
                    FILE_NAME,
                    new TypeToken<ArrayList<Development>>() {}.getType());
        }
        return sList;
    }

    public static synchronized void clear() {
        sList = null;
    }
}
