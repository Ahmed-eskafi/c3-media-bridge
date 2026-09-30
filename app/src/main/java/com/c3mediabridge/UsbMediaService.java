package com.c3mediabridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class UsbMediaService extends Service implements SerialInputOutputManager.Listener {

    public static final String ACTION_STOP = "com.c3mediabridge.STOP";
    public static final String ACTION_TEST_VOL_UP = "com.c3mediabridge.TEST_VOL_UP";
    public static final String ACTION_TEST_PLAY_PAUSE = "com.c3mediabridge.TEST_PLAY_PAUSE";
    public static final String EXTRA_ATTACHED_DEVICE = "com.c3mediabridge.EXTRA_ATTACHED_DEVICE";

    private static final String CHANNEL_ID = "c3_bridge_channel";
    private static final int NOTIFICATION_ID = 30;

    private static final int ESPRESSIF_VID = 0x303A;
    private static final int ESP32_C3_USB_SERIAL_JTAG_PID = 0x1001;
    private static final int BAUD = 115200;

    private UsbManager usbManager;
    private AudioManager audioManager;
    private MediaSessionManager mediaSessionManager;

    private UsbSerialPort serialPort;
    private SerialInputOutputManager ioManager;
    private UsbDevice currentDevice;

    private final StringBuilder serialText = new StringBuilder();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable usbPoll = new Runnable() {
        @Override
        public void run() {
            checkUsbConnection();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, makeNotification("Waiting for ESP32-C3"));
        handler.post(usbPoll);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_TEST_VOL_UP.equals(action)) {
            executeCommand("VOL_UP");
        } else if (ACTION_TEST_PLAY_PAUSE.equals(action)) {
            executeCommand("PLAY_PAUSE");
        }

        UsbDevice attached = getAttachedDevice(intent);

        if (attached != null && isC3(attached) && usbManager.hasPermission(attached)) {
            openUsb(attached);
        } else {
            checkUsbConnection();
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressWarnings("deprecation")
    private UsbDevice getAttachedDevice(Intent intent) {
        if (intent == null) {
            return null;
        }

        UsbDevice device = intent.getParcelableExtra(EXTRA_ATTACHED_DEVICE);
        if (device != null) {
            return device;
        }

        return intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
    }

    private void checkUsbConnection() {
        UsbDevice found = findC3();

        if (found == null) {
            if (currentDevice != null || serialPort != null) {
                closeUsb();
            }

            currentDevice = null;
            updateNotification("Waiting for ESP32-C3");
            return;
        }

        if (serialPort != null && currentDevice != null &&
                currentDevice.getDeviceName().equals(found.getDeviceName())) {
            return;
        }

        currentDevice = found;

        if (usbManager.hasPermission(found)) {
            openUsb(found);
        } else {
            // Intentionally do not call UsbManager.requestPermission().
            // That call is what creates the USB permission popup.
            // Android grants access when this app handles USB_DEVICE_ATTACHED.
            updateNotification("USB detected — waiting for Android USB handoff");
        }
    }

    private UsbDevice findC3() {
        for (UsbDevice device : usbManager.getDeviceList().values()) {
            if (isC3(device)) {
                return device;
            }
        }
        return null;
    }

    private boolean isC3(UsbDevice device) {
        return device.getVendorId() == ESPRESSIF_VID &&
                device.getProductId() == ESP32_C3_USB_SERIAL_JTAG_PID;
    }

    private UsbSerialDriver getDriver(UsbDevice device) {
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);

        if (driver != null) {
            return driver;
        }

        ProbeTable table = new ProbeTable();
        table.addProduct(
                ESPRESSIF_VID,
                ESP32_C3_USB_SERIAL_JTAG_PID,
                CdcAcmSerialDriver.class);

        return new UsbSerialProber(table).probeDevice(device);
    }

    private synchronized void openUsb(UsbDevice device) {
        if (!usbManager.hasPermission(device)) {
            updateNotification("USB access not granted");
            return;
        }

        if (serialPort != null) {
            if (currentDevice != null &&
                    currentDevice.getDeviceName().equals(device.getDeviceName())) {
                return;
            }

            closeUsb();
        }

        UsbSerialDriver driver = getDriver(device);

        if (driver == null || driver.getPorts().isEmpty()) {
            updateNotification("ESP32 found, but serial driver failed");
            return;
        }

        UsbDeviceConnection connection = usbManager.openDevice(device);

        if (connection == null) {
            updateNotification("Unable to open ESP32-C3 USB");
            return;
        }

        try {
            UsbSerialPort newPort = driver.getPorts().get(0);
            newPort.open(connection);
            newPort.setParameters(
                    BAUD,
                    8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE);

            try {
                newPort.setDTR(true);
                newPort.setRTS(true);
            } catch (Exception ignored) {
            }

            serialPort = newPort;
            currentDevice = device;

            ioManager = new SerialInputOutputManager(serialPort, this);
            ioManager.start();

            updateNotification("ESP32-C3 connected — bridge active");
        } catch (Exception e) {
            try {
                connection.close();
            } catch (Exception ignored) {
            }

            closeUsb();
            updateNotification("USB error — waiting to reconnect");
        }
    }

    @Override
    public void onNewData(byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }

        parseSerial(new String(data, StandardCharsets.UTF_8));
    }

    @Override
    public void onRunError(Exception e) {
        closeUsb();
        updateNotification("USB disconnected — waiting to reconnect");
    }

    private synchronized void parseSerial(String chunk) {
        serialText.append(chunk);

        while (true) {
            int newline = serialText.indexOf("\n");

            if (newline < 0) {
                break;
            }

            String line = serialText
                    .substring(0, newline)
                    .replace("\r", "")
                    .trim();

            serialText.delete(0, newline + 1);

            if (isMediaCommand(line)) {
                executeCommand(line);
            }
        }
    }

    private boolean isMediaCommand(String command) {
        return "VOL_UP".equals(command) ||
                "VOL_DOWN".equals(command) ||
                "NEXT".equals(command) ||
                "PREVIOUS".equals(command) ||
                "PLAY_PAUSE".equals(command);
    }

    private void executeCommand(String command) {
        switch (command) {
            case "VOL_UP":
                changeMusicVolume(AudioManager.ADJUST_RAISE);
                break;

            case "VOL_DOWN":
                changeMusicVolume(AudioManager.ADJUST_LOWER);
                break;

            case "NEXT":
                if (!controlActiveSession("NEXT")) {
                    sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT);
                }
                break;

            case "PREVIOUS":
                if (!controlActiveSession("PREVIOUS")) {
                    sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                }
                break;

            case "PLAY_PAUSE":
                if (!controlActiveSession("PLAY_PAUSE")) {
                    sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                }
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
            try {
                audioManager.adjustVolume(direction, AudioManager.FLAG_SHOW_UI);
            } catch (Exception ignored) {
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

            if (controller == null) {
                return false;
            }

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
        } catch (Exception ignored) {
            return false;
        }
    }

    private MediaController chooseSession(List<MediaController> sessions) {
        MediaController fallback = sessions.get(0);

        for (MediaController controller : sessions) {
            PlaybackState state = controller.getPlaybackState();

            if (state == null) {
                continue;
            }

            int currentState = state.getState();

            if (currentState == PlaybackState.STATE_PLAYING ||
                    currentState == PlaybackState.STATE_BUFFERING ||
                    currentState == PlaybackState.STATE_CONNECTING) {
                return controller;
            }
        }

        return fallback;
    }

    private void sendMediaKey(int keyCode) {
        try {
            long now = SystemClock.uptimeMillis();

            audioManager.dispatchMediaKeyEvent(
                    new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));

            audioManager.dispatchMediaKeyEvent(
                    new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
        } catch (Exception ignored) {
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "C3 Media Bridge",
                NotificationManager.IMPORTANCE_LOW);

        channel.setDescription(
                "Keeps the ESP32-C3 USB media bridge running in the background");

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        manager.createNotificationChannel(channel);
    }

    private Notification makeNotification(String status) {
        Intent openApp = new Intent(this, MainActivity.class);

        int immutableFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE
                : 0;

        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setContentTitle("C3 Media Bridge")
                .setContentText(status)
                .setSmallIcon(R.drawable.ic_bridge)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentIntent)
                .build();
    }

    private void updateNotification(String status) {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        manager.notify(NOTIFICATION_ID, makeNotification(status));
    }

    private synchronized void closeUsb() {
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

        serialText.setLength(0);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(usbPoll);
        closeUsb();
        super.onDestroy();
    }
}
