package rikka.akashitoolkit.event;

import java.util.ArrayList;
import java.util.List;

/**
 * One card of Event.json.
 *
 * <pre>
 * {"id":"...","title":"...","date":"2026","text":"...","link":"...","images":["file.png", ...]}
 * </pre>
 */
public class EventEntry {

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
