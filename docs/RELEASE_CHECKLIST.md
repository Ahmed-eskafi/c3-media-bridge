# Release Checklist

Use this checklist before publishing a version intended for general use.

- [ ] Run the GitHub Actions Android APK build and confirm it succeeds.
- [ ] Complete the physical head-unit checks in [TESTING.md](TESTING.md).
- [ ] Confirm USB attach permission and reconnect behavior on the target head unit.
- [ ] Confirm all five serial commands work with the actual ESP32-C3 firmware.
- [ ] Increment `versionCode` and `versionName` in `app/build.gradle`.
- [ ] Build a release variant with a protected signing key; never commit keystore files or passwords.
- [ ] Install and test the signed release APK on the target hardware.
- [ ] Publish release notes that state the tested head-unit model and Android version.
- [ ] Attach the signed APK and its SHA-256 checksum to the GitHub Release.

## Current release readiness

The checked CI output is a **debug APK**. The automated build passed, but physical head-unit testing and production signing have not been confirmed. Do not label the current debug artifact as a stable production release.
