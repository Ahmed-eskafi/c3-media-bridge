# C3 Media Bridge

<p align="center">
  <strong>Use a BLE media remote to control an Android car head unit through an ESP32-C3 and USB.</strong>
</p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white">
  <img alt="Hardware" src="https://img.shields.io/badge/Hardware-ESP32--C3-E7352C?logo=espressif&logoColor=white">
  <img alt="Language" src="https://img.shields.io/badge/Language-Java-ED8B00?logo=openjdk&logoColor=white">
  <img alt="Build" src="https://img.shields.io/badge/Build-Gradle-02303A?logo=gradle&logoColor=white">
</p>

C3 Media Bridge is an Android companion application for an **ESP32-C3 Super Mini** media-control bridge. A BLE media remote sends control commands to the ESP32-C3. The board forwards those commands to an Android device over USB serial, where the app translates them into media and volume actions. The intended Android device is an **Android car stereo / head unit**, not a phone.

## System diagram

<p align="center">
  <img src="docs/c3-media-bridge-diagram.svg" alt="BLE media remote to ESP32-C3 to USB serial to Android car head unit" width="100%">
</p>

**Signal path:** BLE media remote → ESP32-C3 Super Mini → USB Serial → Android car head unit.

## Features

- USB serial communication with the ESP32-C3 (USB VID `0x303A`, PID `0x1001`).
- Music volume up and down.
- Play/pause, next track, and previous track controls.
- Background foreground-service to keep the bridge available while the app UI is closed.
- USB connection monitoring and reconnect handling.
- Optional media-session access for controlling the active media session.
- Test buttons for volume up and play/pause.
- GitHub Actions workflow to build a debug APK.

## Supported serial commands

Send one command per line, terminated with a newline:

| Command | Android action |
| --- | --- |
| `VOL_UP` | Increase music volume |
| `VOL_DOWN` | Decrease music volume |
| `PLAY_PAUSE` | Toggle playback |
| `NEXT` | Skip to next track |
| `PREVIOUS` | Go to previous track |

Serial settings: **115200 baud, 8 data bits, no parity, 1 stop bit (8N1)**.

## Android car head unit setup

1. Build and install the debug APK on an Android car head unit that supports USB host mode and USB accessories.
2. Connect the ESP32-C3 to the head unit using a suitable USB data cable or adapter.
3. Open the app and grant notification permission if Android requests it.
4. Enable **Media / Notification Access** in Android settings when active media-session control is required.
5. Pair the BLE media remote with the ESP32-C3 firmware.
6. Send one of the supported serial commands and test playback and volume.

Compatibility depends on the head unit's Android version, USB host implementation, permissions, and media-session behavior. Some head units may require a USB adapter or manual permission approval.

## Build

The project uses Gradle and Java. Build a debug APK locally with:

```bash
gradle :app:assembleDebug
```

Or download the latest debug APK from the [Build Android APK workflow](https://github.com/Ahmed-eskafi/c3-media-bridge/actions/workflows/build-apk.yml). Open the latest successful run, then download the **C3-Media-Bridge-APK** artifact.

## Tools and technologies

- **Android Studio** — recommended IDE for opening, editing, and building the Android project.
- **Java** — Android application source language.
- **Android SDK** — platform APIs and build tools.
- **Gradle** — project build system.
- **USB Serial for Android** — serial communication with the ESP32-C3.
- **Git and GitHub** — source control and project hosting.
- **GitHub Actions** — automated debug APK build workflow.
- **Arduino IDE** — useful for developing/flashing the separate ESP32-C3 firmware; the firmware is not included in this Android application repository.

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

docs/
└── c3-media-bridge-diagram.svg
```

## Project status

The Android debug APK build completed successfully in GitHub Actions on 22 November 2026. This confirms compilation and packaging only; end-to-end operation on a physical car head unit still requires hardware validation. The repository contains the Android receiver application. ESP32-C3 firmware for BLE remote pairing and serial command transmission is separate and is not included here.

See [Testing and Compatibility](docs/TESTING.md) for the manual hardware-validation checklist.

## Release downloads

Check the [Releases page](https://github.com/Ahmed-eskafi/c3-media-bridge/releases) for published builds. A release APK should only be listed after a successful build and validation.

## Author

**Ahmed Eskafi** · [GitHub profile](https://github.com/Ahmed-eskafi)

---

If you find a bug or have an improvement idea, feel free to open an issue.
