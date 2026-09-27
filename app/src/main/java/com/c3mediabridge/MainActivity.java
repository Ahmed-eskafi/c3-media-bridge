package com.c3mediabridge;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

public class MainActivity extends android.app.Activity implements SerialInputOutputManager.Listener {

    private static final String ACTION_USB_PERMISSION = "com.c3mediabridge.USB_PERMISSION";
    private static final int ESPRESSIF_VID = 0x303A;
    private static final int ESP32_C3_USB_SERIAL_JTAG_PID = 0x1001;
    private static final int BAUD = 115200;

    private UsbManager usbManager;
    private UsbSerialPort serialPort;
    private SerialInputOutputManager ioManager;

    private AudioManager audioManager;
    private MediaSessionManager mediaSessionManager;

    private TextView statusView;
    private TextView logView;
    private final StringBuilder serialText = new StringBuilder();

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (ACTION_USB_PERMISSION.equals(action)) {
                UsbDevice device = getUsbDevice(intent);
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);

                if (device != null && granted) {
                    appendLog("USB permission granted");
                    openUsb(device);
                } else {
                    setStatus("USB permission denied");
                    appendLog("USB permission was not granted.");
                }
            }

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = getUsbDevice(intent);
                if (device != null && isC3(device)) {
                    closeUsb();
                    setStatus("ESP32-C3 disconnected");
                    appendLog("ESP32-C3 disconnected");
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        buildUi();
        registerUsbReceiver();

        appendLog("Expected ESP32-C3: VID 303A / PID 1001");
        appendLog("Close any serial-terminal app before connecting.");

        UsbDevice attached = getUsbDevice(getIntent());
        if (attached != null && isC3(attached)) {
            connectToDevice(attached);
        } else {
            findAndConnect();
        }
    }

    @SuppressWarnings("deprecation")
    private UsbDevice getUsbDevice(Intent intent) {
        if (intent == null) return null;
        return intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 28, 28, 28);

        TextView title = new TextView(this);
        title.setText("ESP32-C3 USB Media Bridge");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        statusView = new TextView(this);
        statusView.setText("Starting...");
        statusView.setTextSize(18);
        statusView.setPadding(0, 24, 0, 20);
        root.addView(statusView);

        Button connectButton = new Button(this);
        connectButton.setText("Connect ESP32-C3 USB");
        connectButton.setOnClickListener(v -> findAndConnect());
        root.addView(connectButton);

        Button mediaAccessButton = new Button(this);
        mediaAccessButton.setText("Enable Media / Notification Access");
        mediaAccessButton.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (Exception e) {
                appendLog("Cannot open media-access settings: " + e.getMessage());
            }
        });
        root.addView(mediaAccessButton);

        Button testVolUp = new Button(this);
        testVolUp.setText("TEST: Volume +");
        testVolUp.setOnClickListener(v -> executeCommand("VOL_UP"));
        root.addView(testVolUp);

        Button testVolDown = new Button(this);
        testVolDown.setText("TEST: Volume -");
        testVolDown.setOnClickListener(v -> executeCommand("VOL_DOWN"));
        root.addView(testVolDown);

        Button testPlay = new Button(this);
        testPlay.setText("TEST: Play / Pause");
        testPlay.setOnClickListener(v -> executeCommand("PLAY_PAUSE"));
        root.addView(testPlay);

        Button testNext = new Button(this);
        testNext.setText("TEST: Next");
        testNext.setOnClickListener(v -> executeCommand("NEXT"));
        root.addView(testNext);

        logView = new TextView(this);
        logView.setTextSize(15);
        logView.setPadding(0, 20, 0, 20);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(logView);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private void registerUsbReceiver() {
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(usbReceiver, filter);
        }
    }

    private boolean isC3(UsbDevice d) {
        return d.getVendorId() == ESPRESSIF_VID &&
                d.getProductId() == ESP32_C3_USB_SERIAL_JTAG_PID;
    }

    private void findAndConnect() {
        UsbDevice target = null;

        for (UsbDevice device : usbManager.getDeviceList().values()) {
            appendLog(String.format(Locale.US, "USB device: %04X:%04X %s",
                    device.getVendorId(), device.getProductId(), device.getDeviceName()));

            if (isC3(device)) {
                target = device;
                break;
            }
        }

        if (target == null) {
            setStatus("ESP32-C3 not found");
            appendLog("Check USB host/OTG support and use a data cable.");
            return;
        }

        connectToDevice(target);
    }

    private void connectToDevice(UsbDevice device) {
        if (usbManager.hasPermission(device)) {
            openUsb(device);
            return;
        }

        PendingIntent permissionIntent = PendingIntent.getBroadcast(
                this,
                0,
                new Intent(ACTION_USB_PERMISSION).setPackage(getPackageName()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        setStatus("Requesting USB permission...");
        usbManager.requestPermission(device, permissionIntent);
    }

    private UsbSerialDriver getDriver(UsbDevice device) {
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if (driver != null) return driver;

        ProbeTable table = new ProbeTable();
        table.addProduct(ESPRESSIF_VID, ESP32_C3_USB_SERIAL_JTAG_PID, CdcAcmSerialDriver.class);
        return new UsbSerialProber(table).probeDevice(device);
    }

    private void openUsb(UsbDevice device) {
        closeUsb();

        UsbSerialDriver driver = getDriver(device);
        if (driver == null || driver.getPorts().isEmpty()) {
            setStatus("ESP32 found, but no USB serial driver");
            appendLog("Could not create a CDC serial driver for 303A:1001.");
            return;
        }

        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            setStatus("Unable to open ESP32-C3");
            appendLog("Another app may already own the USB device.");
            return;
        }

        try {
            serialPort = driver.getPorts().get(0);
            serialPort.open(connection);
            serialPort.setParameters(
                    BAUD,
                    8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE);

            try {
                serialPort.setDTR(true);
                serialPort.setRTS(true);
            } catch (Exception ignored) {
            }

            ioManager = new SerialInputOutputManager(serialPort, this);
            ioManager.start();

            setStatus("ESP32-C3 connected — waiting for SmartRemote");
            appendLog("USB serial connected at 115200 baud.");
        } catch (Exception e) {
            appendLog("USB open error: " + e.getMessage());
            setStatus("USB connection failed");
            try {
                connection.close();
            } catch (Exception ignored) {
            }
            closeUsb();
        }
    }

    @Override
    public void onNewData(byte[] data) {
        if (data == null || data.length == 0) return;
        String chunk = new String(data, StandardCharsets.UTF_8);
        parseSerial(chunk);
    }

    @Override
    public void onRunError(Exception e) {
        runOnUiThread(() -> {
            appendLog("USB reader stopped: " + e.getMessage());
            setStatus("USB disconnected/error");
            closeUsb();
        });
    }

    private synchronized void parseSerial(String chunk) {
        serialText.append(chunk);

        while (true) {
            int newline = serialText.indexOf("\n");
            if (newline < 0) break;

            String line = serialText.substring(0, newline).replace("\r", "").trim();
            serialText.delete(0, newline + 1);

            if (!line.isEmpty()) {
                final String command = line;
                runOnUiThread(() -> {
                    appendLog("RX: " + command);
                    executeCommand(command);
                });
            }
        }
    }

    private void executeCommand(String command) {
        switch (command) {
            case "VOL_UP":
                changeMusicVolume(AudioManager.ADJUST_RAISE);
                setStatus("Volume +");
                break;

            case "VOL_DOWN":
                changeMusicVolume(AudioManager.ADJUST_LOWER);
                setStatus("Volume -");
                break;

            case "NEXT":
                if (!controlActiveSession("NEXT")) {
                    sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT);
                }
                setStatus("Next track");
                break;

            case "PREVIOUS":
                if (!controlActiveSession("PREVIOUS")) {
                    sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                }
                setStatus("Previous track");
                break;

            case "PLAY_PAUSE":
                if (!controlActiveSession("PLAY_PAUSE")) {
                    sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                }
                setStatus("Play / Pause");
                break;

            default:
                appendLog("Ignored serial line: " + command);
                break;
        }
    }

    private void changeMusicVolume(int direction) {
        try {
            audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    direction,
                    AudioManager.FLAG_SHOW_UI);
        } catch (Exception e) {
            appendLog("STREAM_MUSIC volume failed, using general volume.");
            try {
                audioManager.adjustVolume(direction, AudioManager.FLAG_SHOW_UI);
            } catch (Exception e2) {
                appendLog("Volume error: " + e2.getMessage());
            }
        }
    }

    private boolean controlActiveSession(String command) {
        try {
            ComponentName listener = new ComponentName(this, MediaNotificationListener.class);
            List<MediaController> sessions = mediaSessionManager.getActiveSessions(listener);

            if (sessions == null || sessions.isEmpty()) {
                return false;
            }

            MediaController controller = chooseSession(sessions);
            if (controller == null) return false;

            MediaController.TransportControls controls = controller.getTransportControls();

            switch (command) {
                case "NEXT":
                    controls.skipToNext();
                    return true;

                case "PREVIOUS":
                    controls.skipToPrevious();
                    return true;

                case "PLAY_PAUSE":
                    PlaybackState state = controller.getPlaybackState();
                    if (state != null &&
                            (state.getState() == PlaybackState.STATE_PLAYING ||
                             state.getState() == PlaybackState.STATE_BUFFERING ||
                             state.getState() == PlaybackState.STATE_CONNECTING)) {
                        controls.pause();
                    } else {
                        controls.play();
                    }
                    return true;

                default:
                    return false;
            }
        } catch (SecurityException e) {
            appendLog("Media-session access not enabled; using media-key fallback.");
        } catch (Exception e) {
            appendLog("Media-session error: " + e.getMessage());
        }

        return false;
    }

    private MediaController chooseSession(List<MediaController> sessions) {
        MediaController fallback = sessions.get(0);

        for (MediaController controller : sessions) {
            PlaybackState state = controller.getPlaybackState();
            if (state == null) continue;

            int s = state.getState();
            if (s == PlaybackState.STATE_PLAYING ||
                    s == PlaybackState.STATE_BUFFERING ||
                    s == PlaybackState.STATE_CONNECTING) {
                return controller;
            }
        }

        return fallback;
    }

    private void sendMediaKey(int keyCode) {
        try {
            long now = SystemClock.uptimeMillis();

            KeyEvent down = new KeyEvent(
                    now, now,
                    KeyEvent.ACTION_DOWN,
                    keyCode,
                    0);

            KeyEvent up = new KeyEvent(
                    now, now,
                    KeyEvent.ACTION_UP,
                    keyCode,
                    0);

            audioManager.dispatchMediaKeyEvent(down);
            audioManager.dispatchMediaKeyEvent(up);
        } catch (Exception e) {
            appendLog("Media-key error: " + e.getMessage());
        }
    }

    private void setStatus(String text) {
        runOnUiThread(() -> statusView.setText(text));
    }

    private void appendLog(String text) {
        runOnUiThread(() -> logView.append(text + "\n"));
    }

    private void closeUsb() {
        if (ioManager != null) {
            try {
                ioManager.stop();
            } catch (Exception ignored) {
            }
            ioManager = null;
        }

        if (serialPort != null) {
            try {
                serialPort.close();
            } catch (Exception ignored) {
            }
            serialPort = null;
        }
    }

    @Override
    protected void onDestroy() {
        closeUsb();

        try {
            unregisterReceiver(usbReceiver);
        } catch (Exception ignored) {
        }

        super.onDestroy();
    }
}
