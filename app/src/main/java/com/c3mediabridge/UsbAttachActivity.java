package com.c3mediabridge;

import android.app.Activity;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;

public class UsbAttachActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        UsbDevice device = getIntent() == null
                ? null
                : getIntent().getParcelableExtra(UsbManager.EXTRA_DEVICE);

        if (device != null) {
            Intent serviceIntent = new Intent(this, UsbMediaService.class);
            serviceIntent.putExtra(UsbMediaService.EXTRA_ATTACHED_DEVICE, device);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        }

        finish();
        overridePendingTransition(0, 0);
    }
}
