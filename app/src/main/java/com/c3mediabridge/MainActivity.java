package com.c3mediabridge;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestNotificationPermissionIfNeeded();
        startBridgeService();
        buildUi();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private void startBridgeService() {
        Intent serviceIntent = new Intent(this, UsbMediaService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void sendServiceAction(String action) {
        Intent intent = new Intent(this, UsbMediaService.class);
        intent.setAction(action);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !UsbMediaService.ACTION_STOP.equals(action)) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText("C3 Media Bridge");
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView info = new TextView(this);
        info.setText(
                "Background mode is enabled.\n\n" +
                "You can close this app. Connecting the ESP32-C3 by USB will NOT open this screen. " +
                "The bridge keeps running as a background foreground-service.\n\n" +
                "A small persistent notification is normal and keeps Android from killing the USB bridge.");
        info.setTextSize(17);
        info.setPadding(0, 28, 0, 28);
        root.addView(info);

        Button start = new Button(this);
        start.setText("Start Background Bridge");
        start.setOnClickListener(v -> startBridgeService());
        root.addView(start);

        Button mediaAccess = new Button(this);
        mediaAccess.setText("Enable Media / Notification Access");
        mediaAccess.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (Exception ignored) {
            }
        });
        root.addView(mediaAccess);

        Button testVolume = new Button(this);
        testVolume.setText("TEST: Volume +");
        testVolume.setOnClickListener(v -> sendServiceAction(UsbMediaService.ACTION_TEST_VOL_UP));
        root.addView(testVolume);

        Button testPlay = new Button(this);
        testPlay.setText("TEST: Play / Pause");
        testPlay.setOnClickListener(v -> sendServiceAction(UsbMediaService.ACTION_TEST_PLAY_PAUSE));
        root.addView(testPlay);

        Button stop = new Button(this);
        stop.setText("Stop Background Bridge");
        stop.setOnClickListener(v -> {
            Intent intent = new Intent(this, UsbMediaService.class);
            intent.setAction(UsbMediaService.ACTION_STOP);
            startService(intent);
        });
        root.addView(stop);

        setContentView(root);
    }
}
