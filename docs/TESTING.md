# Testing and Compatibility

## Automated build

The GitHub Actions workflow builds the Android debug variant with:

```bash
gradle :app:assembleDebug
```

The latest checked workflow run completed successfully on 22 November 2026 and uploaded the `C3-Media-Bridge-APK` artifact. This confirms that the source compiles and the debug APK is packaged; it does not prove compatibility with every car head unit.

## Manual hardware validation

Complete these checks on the target Android car head unit before publishing a production release:

- [ ] Install the APK on the target head unit.
- [ ] Connect the ESP32-C3 using a known-good USB data cable.
- [ ] Confirm that Android detects the USB serial device and grants permission.
- [ ] Confirm that the app reconnects after USB disconnect/reconnect.
- [ ] Send `VOL_UP` and `VOL_DOWN`; verify the head unit's media volume changes.
- [ ] Send `PLAY_PAUSE`, `NEXT`, and `PREVIOUS`; verify the active media session responds.
- [ ] Verify behavior with the app UI closed and after a head-unit reboot.
- [ ] Check notification and media-session access behavior on the head unit's Android version.

## Known scope and limitations

- The repository contains the Android USB-serial receiver app.
- ESP32-C3 BLE scanning, remote pairing, and serial-command transmitter firmware are separate and are not included.
- Head-unit compatibility depends on USB host support, Android permissions, and media-session implementation.
- The checked build is a debug APK, not a signed production release.

Do not treat a successful CI build as a completed end-to-end hardware test.
