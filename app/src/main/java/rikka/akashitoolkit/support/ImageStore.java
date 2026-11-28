package rikka.akashitoolkit.support;

import android.content.Context;
import android.text.TextUtils;

import java.io.File;

/**
 * 图片路径解析：优先使用「刷新」时补下载到 files/img 的图片，
 * 其次使用 APK 内置的 assets 图片。
 */
public class ImageStore {

    public static final String DIR_SHIP = "shipimg";
    public static final String DIR_EQUIP = "equipimg";
    public static final String DIR_MAP = "mapimg";

    private ImageStore() {
    }

    public static File localFile(Context context, String dir, String name) {
        File base = new File(context.getFilesDir(), "img");
        return new File(new File(base, dir), name);
    }

    /** 返回 Glide 可用的路径：本地文件优先，其次 assets。 */
    public static String resolve(Context context, String dir, String name) {
        if (TextUtils.isEmpty(name)) {
            return null;
        }
        File file = localFile(context, dir, name);
        if (file.exists() && file.length() > 500) {
            return "file://" + file.getAbsolutePath();
        }
        return "file:///android_asset/" + dir + "/" + name;
    }

    public static String shipName(boolean enemy, String wikiId, int id) {
        if (enemy) {
            return "ShinkaiSeikan" + id + "Banner.jpg";
        }
        // 舰娘缩略图统一使用 KanMusu<3位图鉴号>HDBanner（如八丈 384HDBanner）
        String digits = wikiId == null ? "" : wikiId.replaceAll("\\D", "");
        int no = 0;
        try {
            no = digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (Exception ignored) {
        }
        return String.format("KanMusu%03dHDBanner.jpg", no);
    }

    public static String equipName(int equipId) {
        return String.format("Soubi%03dFull.jpg", equipId);
    }

    public static String mapName(boolean banner, int mapId) {
        return (banner ? "MapBannerHD" : "MapHD") + (mapId / 10) + "-" + (mapId % 10) + ".jpg";
    }
}
