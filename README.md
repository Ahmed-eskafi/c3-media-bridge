# C3 Media Bridge

Android head-unit receiver for an ESP32-C3 Super Mini USB media bridge.

The ESP32 sends newline-delimited USB serial commands:

- `VOL_UP`
- `VOL_DOWN`
- `NEXT`
- `PREVIOUS`
- `PLAY_PAUSE`

The Android app opens the ESP32-C3 native USB Serial/JTAG device (VID 0x303A, PID 0x1001), reads those commands, and converts them into Android volume/media actions.

A GitHub Actions workflow builds a debug APK and uploads it as the artifact **C3-Media-Bridge-APK**.
