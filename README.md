# C3 Media Bridge

<p align="center">
  <strong>Control Android media playback and music volume using an ESP32-C3 over USB serial.</strong>
</p>

<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white">
  <img alt="Hardware" src="https://img.shields.io/badge/Hardware-ESP32--C3-E7352C?logo=espressif&logoColor=white">
  <img alt="Language" src="https://img.shields.io/badge/Language-Java-ED8B00?logo=openjdk&logoColor=white">
  <img alt="Build" src="https://img.shields.io/badge/Build-GitHub%20Actions-2088FF?logo=githubactions&logoColor=white">
</p>

C3 Media Bridge is an Android companion app for an **ESP32-C3 Super Mini** media-control bridge. It listens for newline-delimited commands over the ESP32-C3 USB Serial/JTAG interface and translates them into Android volume and media actions.

## Features

- USB serial communication with the ESP32-C3 (VID `0x303A`, PID `0x1001`).
- Music volume up and down.
- Play/pause, next track, and previous track controls.
- Background foreground-service to keep the bridge available while the app UI is closed.
- USB connection monitoring and reconnect handling.
- Optional media-session access for controlling the active media session.
- Test buttons for volume up and play/pause.
- GitHub Actions workflow to build a debug APK.

## How it works

```text
Remote / Controller
       |
       v
   ESP32-C3
       |  USB Serial (115200 baud)
       |  newline-delimited commands
       v
 Android C3 Media Bridge
       |
       +----> Volume controls
       |
       +----> Active media session
       |
       +----> Android media-key fallback
```

## Supported serial commands

Send one command per line, terminated with a newline:

| Command | Android action |
| --- | --- |
| `VOL_UP` | Increase music volume |
| `VOL_DOWN` | Decrease music volume |
| `PLAY_PAUSE` | Toggle playback |
| `NEXT` | Skip to next track |
| `PREVIOUS` | Go to previous track |

The serial connection is configured for **115200 baud, 8 data bits, no parity, and 1 stop bit (8N1)**.

## Android setup

1. Build and install the debug APK on an Android device that supports USB host mode.
2. Connect the ESP32-C3 to the Android device using a suitable USB data cable or adapter.
3. Open the app and allow notification permission if Android requests it.
4. Enable **Media / Notification Access** in Android settings when you want the app to control the active media session.
5. Connect the ESP32-C3 and send one of the supported serial commands.

The app displays its current bridge status in an ongoing notification. Android may require USB-device permission or user confirmation depending on the device and USB handoff flow.

## Build

The project uses Gradle and Java. You can build a debug APK locally with:

```bash
gradle :app:assembleDebug
```

Or run the **Build Android APK** workflow from the repository's Actions tab. The workflow uploads an artifact named **C3-Media-Bridge-APK** when the build succeeds.

## Project structure

```text
app/src/main/
├── AndroidManifest.xml
├── java/com/c3mediabridge/
│   ├── MainActivity.java
│   ├── UsbMediaService.java
│   ├── UsbAttachActivity.java
│   ├── BootReceiver.java
│   └── MediaNotificationListener.java
└── res/
```

## Tech stack

- Android SDK
- Java
- Gradle
- USB Serial for Android
- Android MediaSession and AudioManager APIs
- GitHub Actions

## Status

This repository contains the Android receiver application and its automated debug-build workflow. The ESP32-C3 firmware that sends the serial commands is a separate part of the hardware setup.

## Author

**Ahmed Eskafi** · [GitHub profile](https://github.com/Ahmed-eskafi)

---

If you find a bug or have an improvement idea, feel free to open an issue.
