package com.numberrename.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private TextView status;
    private Button startStop;
    private Button orderBtn;
    private ListView list;
    private ArrayAdapter<String> adapter;
    private final ArrayList<File> files = new ArrayList<>();
    private final ArrayList<String> labels = new ArrayList<>();
    private boolean oldestFirst = true;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            handler.postDelayed(this, 1000);
        }
    };

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private Button btn(String text, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(click);
        return b;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(12), dp(12), dp(12));

        TextView info = new TextView(this);
        info.setTextSize(14);
        info.setText("Moves .jpg images from the main storage folder into "
                + "NumberedImages and names them 1.jpg, 2.jpg ... 300.jpg.");
        box.addView(info);

        box.addView(btn("Grant file access", v -> askPermission()));

        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(0, dp(8), 0, dp(8));
        box.addView(status);

        startStop = btn("Start", v -> toggle());
        box.addView(startStop);

        box.addView(btn("Reset counter to 1", v -> {
            Renamer.setNext(this, 1);
            refreshStatus();
        }));

        TextView manual = new TextView(this);
        manual.setTextSize(16);
        manual.setPadding(0, dp(12), 0, dp(4));
        manual.setText("Manual mode (numbers follow the list order)");
        box.addView(manual);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams w = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        Button load = btn("Load", v -> loadImages());
        orderBtn = btn("Oldest first", v -> {
            oldestFirst = !oldestFirst;
            orderBtn.setText(oldestFirst ? "Oldest first" : "Newest first");
            loadImages();
        });
        Button all = btn("Select all", v -> {
            for (int i = 0; i < files.size(); i++) list.setItemChecked(i, true);
        });
        row.addView(load, w);
        row.addView(orderBtn, w);
        row.addView(all, w);
        box.addView(row);

        list = new ListView(this);
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_multiple_choice, labels);
        list.setAdapter(adapter);
        box.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        box.addView(btn("Rename selected", v -> renameSelected()));

        setContentView(box);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
        if (hasAccess()) loadImages();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    private boolean hasAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        }
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void askPermission() {
        if (hasAccess()) {
            Toast.makeText(this, "Access already granted", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            startActivity(new Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } else {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (hasAccess()) loadImages();
    }

    private void toggle() {
        if (RenameService.running) {
            stopService(new Intent(this, RenameService.class));
            RenameService.running = false;
        } else {
            if (!hasAccess()) {
                Toast.makeText(this, "Grant file access first", Toast.LENGTH_LONG).show();
                askPermission();
                return;
            }
            Intent i = new Intent(this, RenameService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        }
        refreshStatus();
    }

    private void refreshStatus() {
        boolean on = RenameService.running;
        startStop.setText(on ? "Stop" : "Start");
        status.setText((on ? "Running" : "Stopped")
                + "   |   Next number: " + Renamer.getNext(this)
                + "   |   Access: " + (hasAccess() ? "OK" : "NOT GRANTED"));
    }

    private void loadImages() {
        files.clear();
        labels.clear();
        File[] all = Renamer.SRC.listFiles();
        if (all != null) {
            for (File f : all) if (Renamer.isImage(f)) files.add(f);
        }
        Collections.sort(files, (a, b) -> oldestFirst
                ? Long.compare(a.lastModified(), b.lastModified())
                : Long.compare(b.lastModified(), a.lastModified()));
        SimpleDateFormat fmt = new SimpleDateFormat("dd MMM HH:mm:ss", Locale.US);
        for (File f : files) {
            labels.add(f.getName() + "\n" + fmt.format(new Date(f.lastModified())));
        }
        list.clearChoices();
        adapter.notifyDataSetChanged();
    }

    private void renameSelected() {
        if (!hasAccess()) {
            Toast.makeText(this, "Grant file access first", Toast.LENGTH_LONG).show();
            askPermission();
            return;
        }
        if (RenameService.running) {
            Toast.makeText(this, "Stop auto mode first", Toast.LENGTH_LONG).show();
            return;
        }
        SparseBooleanArray sel = list.getCheckedItemPositions();
        int done = 0;
        boolean limit = false;
        for (int i = 0; i < files.size(); i++) {
            if (!sel.get(i)) continue;
            int r = Renamer.renameOne(this, files.get(i));
            if (r > 0) done++;
            else if (r == -1) {
                limit = true;
                break;
            }
        }
        Toast.makeText(this, "Renamed " + done + " files" + (limit ? " (limit 300 reached)" : ""),
                Toast.LENGTH_LONG).show();
        loadImages();
        refreshStatus();
    }
}
