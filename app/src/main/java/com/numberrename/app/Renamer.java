package com.numberrename.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.os.Environment;

import java.io.File;
import java.util.Locale;

public class Renamer {

    public static final String DEST_NAME = "NumberedImages";
    public static final File ROOT = Environment.getExternalStorageDirectory();

    public static boolean isImage(File f) {
        if (!f.isFile()) return false;
        String n = f.getName().toLowerCase(Locale.US);
        if (n.startsWith(".")) return false;
        return n.endsWith(".jpg") || n.endsWith(".jpeg");
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("numberrename", Context.MODE_PRIVATE);
    }

    public static File getSrc(Context c) {
        String def = new File(ROOT, "Download").getAbsolutePath();
        return new File(prefs(c).getString("src", def));
    }

    public static void setSrc(Context c, String path) {
        prefs(c).edit().putString("src", path).apply();
    }

    public static int getNext(Context c) {
        return prefs(c).getInt("next", getStart(c));
    }

    public static void setNext(Context c, int n) {
        prefs(c).edit().putInt("next", n).apply();
    }

    public static int getStart(Context c) {
        return prefs(c).getInt("start", 1);
    }

    public static void setStart(Context c, int n) {
        prefs(c).edit().putInt("start", n).apply();
    }

    // last allowed number; 0 means unlimited
    public static int getLimit(Context c) {
        return prefs(c).getInt("limit", 3000);
    }

    public static void setLimit(Context c, int n) {
        prefs(c).edit().putInt("limit", n).apply();
    }

    public static File destDir() {
        File d = new File(ROOT, DEST_NAME);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /**
     * Moves one file into NumberedImages with the next free number.
     * Returns the number used, 0 if it failed, or -1 if the limit is reached.
     */
    public static int renameOne(Context c, File f) {
        int limit = getLimit(c);
        int n = getNext(c);
        File dir = destDir();
        while ((limit == 0 || n <= limit) && new File(dir, n + ".jpg").exists()) n++;
        if (limit != 0 && n > limit) {
            setNext(c, limit + 1);
            return -1;
        }
        File target = new File(dir, n + ".jpg");
        String oldPath = f.getAbsolutePath();
        if (f.renameTo(target) && target.exists() && !f.exists()) {
            setNext(c, n + 1);
            try {
                MediaScannerConnection.scanFile(c,
                        new String[]{oldPath, target.getAbsolutePath()}, null, null);
            } catch (Exception ignored) {
            }
            return n;
        }
        return 0;
    }
}
