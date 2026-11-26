package rikka.akashitoolkit.support;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 记录未捕获异常，写入 files/crash/last_crash.txt，便于用户反馈或在「关于」页查看。
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "CrashHandler";
    private static final String DIR = "crash";
    private static final String FILE = "last_crash.txt";

    private final Context mContext;
    private final Thread.UncaughtExceptionHandler mDefault;

    private CrashHandler(Context context, Thread.UncaughtExceptionHandler handler) {
        mContext = context.getApplicationContext();
        mDefault = handler;
    }

    public static void install(Context context) {
        Thread.UncaughtExceptionHandler current = Thread.getDefaultUncaughtExceptionHandler();
        if (current instanceof CrashHandler) {
            return;
        }
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(context, current));
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            write(throwable);
        } catch (Throwable ignored) {
        }
        if (mDefault != null) {
            mDefault.uncaughtException(thread, throwable);
        }
    }

    private void write(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        pw.flush();

        File dir = new File(mContext.getFilesDir(), DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            return;
        }
        File out = new File(dir, FILE);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(out, false);
            fos.write(sw.toString().getBytes("UTF-8"));
            fos.flush();
        } catch (Exception e) {
            Log.w(TAG, "write crash log failed", e);
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (Exception ignored) {
                }
            }
        }
        Log.e(TAG, sw.toString());
    }

    /** 读取上次崩溃日志，没有则返回 null。 */
    public static String readLast(Context context) {
        File out = new File(new File(context.getFilesDir(), DIR), FILE);
        if (!out.exists()) {
            return null;
        }
        try {
            byte[] data = new byte[(int) out.length()];
            java.io.FileInputStream in = new java.io.FileInputStream(out);
            try {
                int read = 0;
                while (read < data.length) {
                    int n = in.read(data, read, data.length - read);
                    if (n <= 0) {
                        break;
                    }
                    read += n;
                }
                return new String(data, 0, read, "UTF-8");
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static void clear(Context context) {
        File out = new File(new File(context.getFilesDir(), DIR), FILE);
        if (out.exists()) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
        }
    }
}
