# Xtream

Living-room browser. The web app in this repo is the preview. The installable Android TV build lives in `android/`.

## APKs

Two sideloadable builds are published on every push to `main`:

- [xtream-tv.apk](https://github.com/admin-xtinex/xtream/releases/download/v2.4.0/xtream-tv.apk) for Android TV (landscape, TV launcher)
- [xtream-mobile.apk](https://github.com/admin-xtinex/xtream/releases/download/v2.4.0/xtream-mobile.apk) for phones

Install with `adb install -r xtream-tv.apk` or `adb install -r xtream-mobile.apk`. The package names are different, so both can be installed on the same device.

Arrows move, OK selects, and Back returns. On a phone, tap works the same way. Type a search or an `http`/`https` address. Bookmarks and recent pages stay on the device.

The phone app turns with the device. **Rotate** locks it to the other direction. When a video starts, the phone goes landscape and the picture fills the screen. Back leaves that view. The TV app stays landscape. This build uses the system WebView, so the APK stays small. **Blocking** stays on in the background. A page shows Loading only while it is actually opening, then appears. Ad videos inside the player are skipped without restarting the movie. **Download** sits on the video itself. It saves only a direct video file (`mp4`, `webm`, `mkv`, and the same short list). Streams, playlists, and every other format stay blocked. Pages are loaded the same way Chrome loads them, so a site is less likely to swap the player for an install prompt.

The home screen **Suggested** row is baked in when the APK is built.

Set it from GitHub without editing code:

1. Open **Settings → Secrets and variables → Actions → Variables**.
2. Add `XTREAM_URLS`. One link per line: `Name|https://example.com`
3. Push any commit, or run **Build APK** by hand.

The manual **Build APK** run also has a `suggested_urls` field. If you fill that in, it overrides `XTREAM_URLS` for that build only. Blank lines and lines starting with `#` are ignored. Only `http` and `https` addresses are kept.

Protected DRM streams are not bypassed.
