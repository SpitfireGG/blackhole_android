# Blackhole

A one-tap video downloader for Android. Copy a link from YouTube, TikTok, X/Twitter,
or Pinterest, open the app, tap the circle. The video is saved to `Movies/Blackhole`
and shows up in your gallery.

No ads, no accounts, no server. yt-dlp and ffmpeg run on the phone itself.

---

## Build it

1. Install [Android Studio](https://developer.android.com/studio) (Ladybug or newer).
2. **File → Open** → select this folder. Let it sync; the first sync downloads Gradle
   and the yt-dlp libraries and takes a few minutes.
3. Plug in your phone with USB debugging on, hit **Run**.

Or from a terminal, with JDK 17 on your path:

```bash
./gradlew assembleRelease
```

The APKs land in `app/build/outputs/apk/release/`. Most modern phones want
`app-arm64-v8a-release.apk`. If you're unsure, `app-universal-release.apk` works
everywhere but is about three times the size.

Release builds are signed with the debug key so you can sideload immediately.
Generate a proper keystore before you give the APK to anyone else.

## How it works

| Piece | What it does |
| --- | --- |
| `core/Downloader.kt` | Builds the yt-dlp command per platform, runs it, publishes the result |
| `core/MediaLibrary.kt` | Writes into `Movies/Blackhole` via MediaStore, reads the list back |
| `core/YtdlEngine.kt` | Unpacks and updates the bundled python + yt-dlp |
| `service/DownloadService.kt` | Foreground service, so downloads survive you leaving the app |
| `ui/VoidButton.kt` | The circle. Progress ring while working, tap again to cancel |

Downloads land in the app's private folder first, then get copied into MediaStore.
That detour exists because Android 11+ only lets apps write directly to `Download/`
and `Documents/`, and because it means the app needs **no storage permission at all**
on Android 10 and up.

### About the TikTok watermark

TikTok serves two versions of every video: `play_addr`, which is clean, and
`download_addr`, which has the username stamped on it. yt-dlp exposes the stamped one
under a format id containing `download`, so the format selector excludes it:

```
-f "bv*+ba/b[format_id!*=download]/b"
```

Nothing is being scrubbed off the pixels — the app is just asking for the file that
never had the stamp. If TikTok renames its format ids, the trailing `/b` falls back to
whatever is best, and you'll get a watermark until yt-dlp is updated.

### Three input routes

- **Clipboard** — copy, open, tap the circle.
- **Share sheet** — share a link to Blackhole from any app; the download starts on its own.
- **Open with** — tap a supported link and pick Blackhole.

Turn on *Start on open* in settings if you want the clipboard route to skip the tap too.

## Keeping it working

Platforms change their video players constantly, and a stale yt-dlp is the reason
roughly nine out of ten downloads fail. The app checks for a newer yt-dlp on every
launch (Settings → Engine), defaulting to the nightly channel because it picks up
YouTube fixes days before stable does. If a download starts failing, hit
**Update yt-dlp now** before doing anything else.

## Things worth knowing

**Size.** The APK bundles a Python 3.8 runtime and ffmpeg, so per-ABI builds run
~45 MB and the universal build ~150 MB. That's the cost of not using a server.

**Licence.** `youtubedl-android` is GPL-3.0, so anything you distribute that links
against it must be GPL-3.0 too, with source available. Fine for personal use and for
open source; it rules out shipping this as a closed-source app.

**Terms of service.** YouTube, TikTok, X, and Pinterest all prohibit downloading in
their terms, and Play Store policy bans downloader apps outright — this will be
removed if you publish it. It's built for sideloading. What you download is also
subject to copyright law where you live; your own uploads, licensed material, and
personal use are the safe ground.

**Age-restricted and private posts** need a logged-in session. yt-dlp supports
`--cookies`, but wiring up a cookie file is not built in here.

## Adding another site

yt-dlp already supports over a thousand sites, and anything not listed in
`Platform` still downloads through the generic path. To give a site its own
handling, add a case to `Platform`, a host match in `UrlUtils.platformOf`, and a
branch in `Downloader.buildRequest`.
