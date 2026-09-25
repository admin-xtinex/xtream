# Xtream

Living-room browser. The web app in this repo is the preview. The installable Android TV build lives in `android/`.

## APK

GitHub Actions builds a sideloadable debug APK on every push to `main`.

1. Open the **Actions** tab and the **Build APK** workflow.
2. Download the `xtream-apk` artifact.
3. Install it: `adb install app-debug.apk`

The app shows on a phone launcher and on an Android TV launcher. Arrows move, OK selects, and Back returns. Type a search or an `http`/`https` address, then open the page in the built-in player. Bookmarks and recent pages stay on the device.

Protected DRM streams are not bypassed.
