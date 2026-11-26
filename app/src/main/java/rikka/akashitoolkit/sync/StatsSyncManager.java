package rikka.akashitoolkit.sync;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rikka.akashitoolkit.model.DropStat;
import rikka.akashitoolkit.model.MapDetail;
import rikka.akashitoolkit.model.Ship;
import rikka.akashitoolkit.staticdata.DevelopmentList;
import rikka.akashitoolkit.staticdata.DropList;
import rikka.akashitoolkit.staticdata.MapDetailList;
import rikka.akashitoolkit.staticdata.ShipList;

/**
 * 「统计」页数据刷新：
 *  - 开发公式：抓 zh.kcwiki.cn/wiki/开发 的公式表（db.kcwiki.cn/development 同源）
 *  - 掉落：由 MapDetail（各海域掉落表）重建（db.kcwiki.cn/drop 同源）
 * 结果写入 files/json/，离线优先读取。
 */
public class StatsSyncManager {

    public static int syncDevelopment(Context context) throws Exception {
        String page = DataSyncHelper.wikiRaw("开发");

        JSONArray result = new JSONArray();
        Matcher table = Pattern.compile("\\{\\|.*?\\n\\|\\}", Pattern.DOTALL).matcher(page);
        while (table.find()) {
            String[] lines = table.group(0).split("\n");
            List<List<String>> rows = new ArrayList<>();
            List<String> row = new ArrayList<>();
            boolean headerSeen = false;
            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.startsWith("|-") || line.equals("|}")) {
                    if (!row.isEmpty()) {
                        rows.add(row);
                        row = new ArrayList<>();
                    }
                    continue;
                }
                if (line.startsWith("!")) {
                    headerSeen = true;
                    continue;
                }
                if (line.startsWith("|") && headerSeen) {
                    row.add(line.substring(1));
                }
            }
            if (!row.isEmpty()) {
                rows.add(row);
            }

            for (List<String> cellsRaw : rows) {
                List<String> cells = new ArrayList<>();
                for (String cell : cellsRaw) {
                    for (String part : cell.split("\\|\\||!!")) {
                        part = part.trim();
                        int cut = part.lastIndexOf('|');
                        if (cut >= 0) {
                            part = part.substring(cut + 1);
                        }
                        cells.add(DataSyncHelper.stripWikiInline(part));
                    }
                }
                if (cells.size() < 6) {
                    continue;
                }
                int fuel = firstInt(cells.get(2));
                int ammo = firstInt(cells.get(3));
                int steel = firstInt(cells.get(4));
                int bauxite = firstInt(cells.get(5));
                if (fuel == 0 && ammo == 0 && steel == 0 && bauxite == 0) {
                    continue;
                }
                JSONObject entry = new JSONObject();
                entry.put("target", cells.get(0));
                entry.put("ship", cells.get(1));
                entry.put("fuel", fuel);
                entry.put("ammo", ammo);
                entry.put("steel", steel);
                entry.put("bauxite", bauxite);
                entry.put("result", cells.size() > 6 ? cells.get(6) : "");
                entry.put("extra", cells.size() > 7 ? cells.get(7) : "");
                entry.put("extraResult", cells.size() > 8 ? cells.get(8) : "");
                result.put(entry);
            }
        }

        if (result.length() == 0) {
            throw new IllegalStateException("开发公式解析结果为空");
        }
        DataSyncHelper.writeJson(context, "Development.json", result.toString());
        DevelopmentList.clear();
        return result.length();
    }

    public static int syncDrop(Context context) throws Exception {
        List<MapDetail> maps = MapDetailList.get(context);
        if (maps == null) {
            throw new IllegalStateException("没有海域数据");
        }

        Map<Integer, String> shipNames = new LinkedHashMap<>();
        List<Ship> ships = ShipList.get(context);
        if (ships != null) {
            for (Ship s : ships) {
                if (s != null && s.getName() != null) {
                    shipNames.put(s.getId(), s.getName().get());
                }
            }
        }

        JSONArray result = new JSONArray();
        for (MapDetail detail : maps) {
            if (detail == null || detail.getName() == null) {
                continue;
            }
            JSONArray nodes = new JSONArray();
            for (MapDetail.DropEntity node : detail.getDrop()) {
                if (node == null || node.getShip() == null || node.getShip().isEmpty()) {
                    continue;
                }
                JSONArray names = new JSONArray();
                for (Integer id : node.getShip()) {
                    String name = shipNames.get(id);
                    if (name != null) {
                        names.put(name);
                    }
                }
                if (names.length() == 0) {
                    continue;
                }
                JSONObject n = new JSONObject();
                n.put("name", node.getName() == null ? "" : node.getName());
                n.put("ships", names);
                nodes.put(n);
            }
            if (nodes.length() == 0) {
                continue;
            }
            JSONObject entry = new JSONObject();
            entry.put("map", detail.getName().get());
            entry.put("nodes", nodes);
            result.put(entry);
        }

        if (result.length() == 0) {
            throw new IllegalStateException("掉落数据为空");
        }
        DataSyncHelper.writeJson(context, "Drop.json", result.toString());
        DropList.clear();
        return result.length();
    }


    /** 刷新建造公式：抓 zh.kcwiki.cn/wiki/建造（普通公式 + 目标舰娘常用公式）。 */
    public static int syncConstruction(Context context) throws Exception {
        String page = DataSyncHelper.wikiRaw("建造");

        JSONArray result = new JSONArray();
        String section = "";
        String currentTarget = "";
        List<String> notes = new ArrayList<>();
        int fuel = -1, ammo = 0, steel = 0, bauxite = 0;

        String[] lines = page.split("\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();

            Matcher heading = Pattern.compile("^=+\\s*([^=\\n]+?)\\s*=+$").matcher(line);
            if (heading.find()) {
                section = heading.group(1);
                continue;
            }
            if (!(section.contains("建造公式") || section.contains("普通建造") || section.contains("目标舰娘"))) {
                continue;
            }
            Matcher f = Pattern.compile("^\\*+\\s*(\\d{1,4})\\s*/\\s*(\\d{1,4})\\s*/\\s*(\\d{1,4})\\s*/\\s*(\\d{1,4})\\s*$")
                    .matcher(line);
            if (f.find()) {
                if (fuel >= 0) {
                    result.put(buildConstruction(fuel, ammo, steel, bauxite, currentTarget,
                            join(notes), false));
                }
                fuel = Integer.parseInt(f.group(1));
                ammo = Integer.parseInt(f.group(2));
                steel = Integer.parseInt(f.group(3));
                bauxite = Integer.parseInt(f.group(4));
                currentTarget = "";
                notes = new ArrayList<>();
                continue;
            }
            if (fuel >= 0 && line.startsWith("**")) {
                String text = DataSyncHelper.stripWikiInline(line.replaceFirst("^\\*+", ""));
                if (!text.isEmpty()) {
                    notes.add(text);
                    if (currentTarget.isEmpty()) {
                        currentTarget = text.split("。")[0];
                    }
                }
            }
        }
        if (fuel >= 0) {
            result.put(buildConstruction(fuel, ammo, steel, bauxite, currentTarget, join(notes), false));
        }

        // 目标舰娘常用公式表 + 大建秘书舰表
        for (List<String> row : parseWikiTables(page)) {
            if (row.size() >= 3) {
                String formula = row.get(2);
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("(\\d{1,4})\\s*/\\s*(\\d{1,4})\\s*/\\s*(\\d{1,4})\\s*/\\s*(\\d{1,4})")
                        .matcher(formula);
                if (m.find()) {
                    StringBuilder note = new StringBuilder();
                    if (!row.get(1).isEmpty()) {
                        note.append(row.get(1));
                    }
                    if (!formula.isEmpty()) {
                        if (note.length() > 0) {
                            note.append('\n');
                        }
                        note.append(formula);
                    }
                    for (int k = 3; k < row.size(); k++) {
                        if (!row.get(k).isEmpty()) {
                            note.append('\n').append(row.get(k));
                        }
                    }
                    result.put(buildConstruction(Integer.parseInt(m.group(1)),
                            Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)),
                            Integer.parseInt(m.group(4)), row.get(0), note.toString(), true));
                    continue;
                }
            }
            if (row.size() == 2 && !row.get(0).isEmpty() && !row.get(1).isEmpty()
                    && !row.get(0).startsWith("目标舰")) {
                result.put(buildConstruction(0, 0, 0, 0, row.get(0),
                        "需要秘书舰：" + row.get(1), true));
            }
        }

        if (result.length() == 0) {
            throw new IllegalStateException("建造公式解析结果为空");
        }
        DataSyncHelper.writeJson(context, "Construction.json", result.toString());
        rikka.akashitoolkit.staticdata.ConstructionList.clear();
        return result.length();
    }

    private static JSONObject buildConstruction(int fuel, int ammo, int steel, int bauxite,
                                                String target, String note, boolean large)
            throws Exception {
        JSONObject entry = new JSONObject();
        entry.put("fuel", fuel);
        entry.put("ammo", ammo);
        entry.put("steel", steel);
        entry.put("bauxite", bauxite);
        entry.put("target", target == null ? "" : target);
        entry.put("note", note == null ? "" : note);
        entry.put("large", large);
        return entry;
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(part);
        }
        return sb.toString();
    }

    /** 解析页面里所有 wiki 表格，返回「行 -> 已清洗单元格」。 */
    private static List<List<String>> parseWikiTables(String page) {
        List<List<String>> rows = new ArrayList<>();
        java.util.regex.Matcher table = java.util.regex.Pattern
                .compile("\\{\\|.*?\\n\\|\\}", java.util.regex.Pattern.DOTALL).matcher(page);
        while (table.find()) {
            List<String> row = new ArrayList<>();
            boolean header = false;
            for (String rawLine : table.group(0).split("\n")) {
                String line = rawLine.trim();
                if (line.startsWith("|-")) {
                    if (row.size() >= 2) {
                        rows.add(row);
                    }
                    row = new ArrayList<>();
                    header = false;
                    continue;
                }
                if (line.startsWith("!")) {
                    header = true;
                    continue;
                }
                if (line.startsWith("|") && !header) {
                    for (String part : line.substring(1).split("\\|\\|")) {
                        row.add(DataSyncHelper.stripWikiInline(part));
                    }
                }
            }
            if (row.size() >= 2) {
                rows.add(row);
            }
        }
        return rows;
    }

    private static int firstInt(String s) {
        Matcher m = Pattern.compile("[-\\d]+").matcher(s == null ? "" : s.replace(",", ""));
        return m.find() ? Integer.parseInt(m.group(0)) : 0;
    }
}
