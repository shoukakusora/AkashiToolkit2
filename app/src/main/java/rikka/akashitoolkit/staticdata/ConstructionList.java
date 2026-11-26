package rikka.akashitoolkit.staticdata;

import android.content.Context;

import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

import rikka.akashitoolkit.model.Construction;

public class ConstructionList {
    private static final String FILE_NAME = "Construction.json";
    private static List<Construction> sList;

    public static synchronized List<Construction> get(Context context) {
        if (sList == null) {
            sList = new BaseGSONList<Construction>().get(
                    context,
                    FILE_NAME,
                    new TypeToken<ArrayList<Construction>>() {}.getType());
        }
        return sList;
    }

    public static synchronized void clear() {
        sList = null;
    }
}
