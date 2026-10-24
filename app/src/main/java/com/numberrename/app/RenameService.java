package com.numberrename.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;

import java.io.File;
import java.util.HashMap;

public class RenameService extends Service {

    public static volatile boolean running = false;

    private static final String CHANNEL = "rename";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final HashMap<String, Long> sizes = new HashMap<>();
    private long startTime;
    private int count = 0;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            check();
            if (running) handler.postDelayed(this, 1500);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "Auto rename", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                    .createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!running) {
            running = true;
            startTime = System.currentTimeMillis() - 2000;
            Notification n = buildNotification("Watching for new images");
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(1, n);
            }
            handler.post(poll);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(poll);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, open, PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("NumberRename is running")
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true);
        return b.build();
    }

    private void check() {
        File[] all = Renamer.getSrc(this).listFiles();
        if (all == null) return;
        for (File f : all) {
            if (!Renamer.isImage(f)) continue;
            if (f.lastModified() < startTime) continue;
            String key = f.getAbsolutePath();
            long size = f.length();
            Long prev = sizes.get(key);
            if (prev != null && prev == size && size > 0) {
                sizes.remove(key);
                int r = Renamer.renameOne(this, f);
                if (r > 0) {
                    count++;
                    ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(1,
                            buildNotification("Renamed " + count + " files. Next number: "
                                    + Renamer.getNext(this)));
                } else if (r == -1) {
                    Toast.makeText(this, "Last number reached. Auto mode stopped.",
                            Toast.LENGTH_LONG).show();
                    stopSelf();
                    return;
                }
            } else {
                sizes.put(key, size);
            }
        }
    }
}
