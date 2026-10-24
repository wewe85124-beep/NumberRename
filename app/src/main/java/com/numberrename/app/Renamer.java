package com.numberrename.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import java.io.File;
import java.util.Locale;

public class Renamer {

    public static final int MAX = 300;
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
        return prefs(c).getInt("next", 1);
    }

    public static void setNext(Context c, int n) {
        prefs(c).edit().putInt("next", n).apply();
    }

    public static File destDir() {
        File d = new File(ROOT, DEST_NAME);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static int renameOne(Context c, File f) {
        int n = getNext(c);
        File dir = destDir();
        while (n <= MAX && new File(dir, n + ".jpg").exists()) n++;
        if (n > MAX) {
            setNext(c, MAX + 1);
            return -1;
        }
        File target = new File(dir, n + ".jpg");
        if (f.renameTo(target)) {
            setNext(c, n + 1);
            return n;
        }
        return 0;
    }
}
