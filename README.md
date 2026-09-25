# Xtream

Living-room browser. The web app in this repo is the preview. The installable Android TV build lives in `android/`.

## APK

GitHub Actions builds a sideloadable debug APK on every push to `main`.

1. Open the **Actions** tab and the **Build APK** workflow.
2. Download the `xtream-apk` artifact.
3. Install it: `adb install app-debug.apk`

The app shows on a phone launcher and on an Android TV launcher. Arrows move, OK selects, and Back returns. Type a search or an `http`/`https` address, then open the page in the built-in player. Bookmarks and recent pages stay on the device.

The home screen **Suggested** row is baked in when the APK is built.

Set it from GitHub without editing code:

1. Open **Settings → Secrets and variables → Actions → Variables**.
2. Add `XTREAM_URLS`. One link per line: `Name|https://example.com`
3. Push any commit, or run **Build APK** by hand.

The manual **Build APK** run also has a `suggested_urls` field. If you fill that in, it overrides `XTREAM_URLS` for that build only. Blank lines and lines starting with `#` are ignored. Only `http` and `https` addresses are kept.

Protected DRM streams are not bypassed.
