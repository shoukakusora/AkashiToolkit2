package rikka.akashitoolkit.sync;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rikka.akashitoolkit.model.Equip;
import rikka.akashitoolkit.model.Ship;
import rikka.akashitoolkit.staticdata.EquipList;
import rikka.akashitoolkit.staticdata.ShipList;
import rikka.akashitoolkit.support.ImageStore;

/**
 * 「刷新」时补齐缺失图片：把 APK 内置缺失的舰娘缩略图 / 装备立绘从 wiki 下载到
 * files/img/…，之后 ImageStore 会优先使用这些文件。
 */
public class ImageSyncManager {

    private static final String TAG = "ImageSyncManager";
    private static final String UA = "Mozilla/5.0 (AkashiToolkit)";

    public interface Callback {
        void onProgress(int done, int total, String current);

        void onFinished(int downloaded, int failed, int missing);
    }

    public static class Result {
        public int downloaded;
        public int failed;
        public int stillMissing;
    }

    public static Result syncMissing(Context context, Callback callback) {
        Set<String> haveShip = assets(context, ImageStore.DIR_SHIP);
        Set<String> haveEquip = assets(context, ImageStore.DIR_EQUIP);

        List<String[]> tasks = new ArrayList<>();  // [dir, targetName, wikiFileName...]

        List<Ship> ships = ShipList.get(context);
        if (ships != null) {
            for (Ship ship : ships) {
                if (ship == null) {
                    continue;
                }
                boolean enemy = ship.isEnemy();
                String name = ImageStore.shipName(enemy, ship.getWikiId(), ship.getId());
                File local = ImageStore.localFile(context, ImageStore.DIR_SHIP, name);
                if (haveShip.contains(name) || local.exists()) {
                    continue;
                }
                String wiki;
                if (enemy) {
                    wiki = String.format("ShinkaiSeikan%dBanner.png", ship.getId());
                } else {
                    wiki = String.format("KanMusu%sBanner.jpg", ship.getWikiId() == null ? "" : ship.getWikiId());
                }
                tasks.add(new String[]{ImageStore.DIR_SHIP, name, wiki,
                        enemy ? "" : String.format("KanMusu%sHDIllust.png",
                                ship.getWikiId() == null ? "" : stripLetters(ship.getWikiId()))});
            }
        }

        List<Equip> equips = EquipList.get(context);
        if (equips != null) {
            for (Equip equip : equips) {
                if (equip == null || equip.getId() >= 1500) {
                    continue;
                }
                String name = ImageStore.equipName(equip.getId());
                File local = ImageStore.localFile(context, ImageStore.DIR_EQUIP, name);
                if (haveEquip.contains(name) || local.exists()) {
                    continue;
                }
                tasks.add(new String[]{ImageStore.DIR_EQUIP, name,
                        String.format("Soubi%03dFull.png", equip.getId()),
                        String.format("Soubi%03dHDFull.png", equip.getId())});
            }
        }

        Result result = new Result();
        int total = tasks.size();
        for (int i = 0; i < total; i++) {
            String[] task = tasks.get(i);
            String dir = task[0];
            String name = task[1];

            boolean ok = false;
            for (int k = 2; k < task.length; k++) {
                if (task[k].isEmpty()) {
                    continue;
                }
                if (download(context, dir, name, task[k])) {
                    ok = true;
                    break;
                }
            }

            if (ok) {
                result.downloaded++;
            } else {
                result.failed++;
            }
            if (callback != null) {
                callback.onProgress(i + 1, total, name);
            }
        }

        result.stillMissing = result.failed;
        if (callback != null) {
            callback.onFinished(result.downloaded, result.failed, result.stillMissing);
        }
        return result;
    }

    private static boolean download(Context context, String dir, String saveName, String wikiName) {
        InputStream in = null;
        try {
            String md5 = md5(wikiName);
            String url = "https://upload.kcwiki.org/commons/" + md5.charAt(0) + "/"
                    + md5.substring(0, 2) + "/" + URLEncoder.encode(wikiName, "UTF-8").replace("+", "%20");
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Referer", "https://zh.kcwiki.cn/");
            if (conn.getResponseCode() >= 400) {
                return false;
            }
            in = conn.getInputStream();
            byte[] data = readAll(in);
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (bitmap == null) {
                return false;
            }

            int width = Math.min(bitmap.getWidth(), 360);
            if (bitmap.getWidth() > width) {
                bitmap = Bitmap.createScaledBitmap(bitmap,
                        width, Math.max(1, bitmap.getHeight() * width / bitmap.getWidth()), true);
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 78, buffer);

            File target = ImageStore.localFile(context, dir, saveName);
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return false;
            }
            FileOutputStream out = new FileOutputStream(target);
            try {
                out.write(buffer.toByteArray());
            } finally {
                out.close();
            }
            return true;
        } catch (Exception e) {
            Log.d(TAG, "download failed: " + wikiName, e);
            return false;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static Set<String> assets(Context context, String dir) {
        Set<String> out = new HashSet<>();
        try {
            AssetManager manager = context.getAssets();
            String[] list = manager.list(dir);
            if (list != null) {
                for (String name : list) {
                    out.add(name);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static String stripLetters(String wikiId) {
        Matcher m = Pattern.compile("^(\\d+)").matcher(wikiId);
        return m.find() ? m.group(1) : wikiId;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String md5(String text) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
        byte[] bytes = digest.digest(text.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
