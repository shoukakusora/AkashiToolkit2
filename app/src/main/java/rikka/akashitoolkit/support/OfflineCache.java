package rikka.akashitoolkit.support;

import android.content.Context;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.request.FutureTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import rikka.akashitoolkit.model.Ship;
import rikka.akashitoolkit.staticdata.ShipList;
import rikka.akashitoolkit.utils.Utils;

/**
 * 离线缓存：把舰娘与深海栖姬的头像预下载进 Glide 磁盘缓存，
 * 之后断网也能显示（Glide 优先读磁盘缓存）。
 */
public class OfflineCache {

    public interface Callback {
        void onProgress(int done, int total);

        void onFinished(int success, int failed);
    }

    public static class Result {
        public int success;
        public int failed;
        public int total;
    }

    /** 在后台线程调用。 */
    public static Result warmUp(Context context, Callback callback) {
        Context app = context.getApplicationContext();

        List<String> urls = new ArrayList<>();

        List<Ship> ships = ShipList.get(context);
        if (ships != null) {
            for (Ship ship : ships) {
                if (ship == null) {
                    continue;
                }
                String url;
                if (ship.isEnemy()) {
                    url = Utils.getKCWikiFileUrl(String.format("ShinkaiSeikan%dBanner.png", ship.getId()));
                } else if (ship.getWikiId() != null) {
                    url = Utils.getKCWikiFileUrl(String.format("KanMusu%sBanner.jpg", ship.getWikiId()));
                } else {
                    url = null;
                }
                if (url != null) {
                    urls.add(url);
                }
            }
        }

        Result result = new Result();
        result.total = urls.size();

        for (int i = 0; i < urls.size(); i++) {
            GlideUrl glideUrl;
            try {
                glideUrl = Utils.getGlideUrl(urls.get(i));
            } catch (Exception e) {
                glideUrl = null;
            }

            boolean ok = false;
            FutureTarget<File> target = null;
            if (glideUrl != null) {
                try {
                    target = Glide.with(app).load(glideUrl).downloadOnly(100, 100);
                    ok = target != null && target.get() != null;
                } catch (Exception ignored) {
                    ok = false;
                } finally {
                    if (target != null) {
                        try {
                            Glide.clear(target);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

            if (ok) {
                result.success++;
            } else {
                result.failed++;
            }

            if (callback != null && (i % 10 == 0 || i == urls.size() - 1)) {
                callback.onProgress(i + 1, urls.size());
            }
        }

        if (callback != null) {
            callback.onFinished(result.success, result.failed);
        }
        return result;
    }
}
