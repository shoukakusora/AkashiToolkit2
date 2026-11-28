package rikka.akashitoolkit.event;

import android.content.Context;

import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

import rikka.akashitoolkit.staticdata.BaseGSONList;

/**
 * Local Event.json (newest first), cached for the lifetime of the process.
 * Replaces the old dead "api_v2/event.php" network data.
 */
public class EventData {

    private static List<EventEntry> sList;

    public static synchronized List<EventEntry> get(Context context) {
        if (sList == null) {
            List<EventEntry> list = new BaseGSONList<EventEntry>()
                    .get(context, "Event3.json", new TypeToken<ArrayList<EventEntry>>() {
                    }.getType());
            sList = list == null ? new ArrayList<EventEntry>() : list;
        }
        return sList;
    }

    public static synchronized void clear() {
        sList = null;
    }
}
