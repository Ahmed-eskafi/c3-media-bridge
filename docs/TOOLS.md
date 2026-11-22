# Development Tools

The Android companion application in this repository is built with the following tools and technologies.

| Tool / technology | Purpose |
| --- | --- |
| Android Studio | Recommended IDE for opening, editing, and building the Android app |
| Java | Android application source code |
| Android SDK | Android platform APIs and build tools |
| Gradle | Build automation |
| USB Serial for Android | USB serial communication with the ESP32-C3 |
| Git and GitHub | Version control and source hosting |
| GitHub Actions | Automated debug APK builds |
| Arduino IDE | Intended for the separate ESP32-C3 firmware; firmware is not included in this repository |

## Build command

```bash
gradle :app:assembleDebug
```

## Build artifact

The GitHub Actions workflow is configured to upload a debug APK artifact named **C3-Media-Bridge-APK** after a successful build.

> Note: A tool listed here is a development tool or dependency, not an attached installer. Use the official vendor websites to download software. Only validated application builds should be published as GitHub Releases.
