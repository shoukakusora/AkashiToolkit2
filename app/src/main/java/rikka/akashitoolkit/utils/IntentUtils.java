package rikka.akashitoolkit.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import java.util.List;

/**
 * Created by Rikka on 2016/3/13.
 */
public class IntentUtils {
    public static int getSize(Context context, Intent intent) {
        PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> activities = packageManager.queryIntentActivities(intent, 0);
        return activities.size();
    }

    public static boolean isValid(Context context, Intent intent) {
        return isValid(context, intent, 0);
    }

    public static boolean isValid(Context context, Intent intent, int minSize) {
        PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> activities = packageManager.queryIntentActivities(intent, 0);
        return activities.size() > minSize;
    }

    public static boolean startActivity(Context context, Intent intent) {
        if (isValid(context, intent)) {
            context.startActivity(intent);
            return true;
        }
        return false;
    }

    /**
     * Open an http(s) link in the user's browser. Null / empty / malformed links are ignored.
     */
    public static boolean openUrl(Context context, String url) {
        if (context == null || url == null || url.trim().length() == 0) {
            return false;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()));
            return startActivity(context, intent);
        } catch (Exception e) {
            return false;
        }
    }
}
