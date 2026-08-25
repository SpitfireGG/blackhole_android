#!/usr/bin/env bash
# Commits the quality/reliability hardening batch and pushes to origin/main.
# CI (.github/workflows/build.yml) then builds the APKs and republishes the
# "latest" GitHub release with them attached. Run with:  bash release.sh
set -euo pipefail

cd "$(dirname "$0")"

echo "==> Preflight"
if ! git config user.name >/dev/null 2>&1 || ! git config user.email >/dev/null 2>&1; then
  echo "ERROR: git identity is not configured. Run these first:"
  echo "  git config user.name \"Your Name\""
  echo "  git config user.email \"you@example.com\""
  exit 1
fi

# Deterministic signing: without a committed keystore, every CI build signs
# with a fresh throwaway key and the APK won't install over the previous one.
if [ ! -f keystore/blackhole.jks ]; then
  echo "==> No release keystore yet — generating one"
  bash make-keystore.sh
fi

echo "==> Working tree before staging"
git status --short
echo

echo "==> Staging changed sources"
git add keystore/blackhole.jks \
  app/build.gradle.kts \
  make-keystore.sh \
  release.sh \
  app/src/main/java/com/blackhole/downloader/ClipboardBridgeActivity.kt \
  app/src/main/java/com/blackhole/downloader/core/Downloader.kt \
  app/src/main/java/com/blackhole/downloader/core/MediaLibrary.kt \
  app/src/main/java/com/blackhole/downloader/core/Thumbs.kt \
  app/src/main/java/com/blackhole/downloader/core/YtdlEngine.kt \
  app/src/main/java/com/blackhole/downloader/service/DownloadService.kt \
  app/src/main/java/com/blackhole/downloader/service/FloatingOverlayService.kt \
  app/src/main/java/com/blackhole/downloader/ui/MainViewModel.kt

echo "==> Committing"
git commit -F - <<'MSG'
fix: real 1080p downloads, auto-retry, and crash/hardening pass (v1.2)

Quality:
- Drop the pinned android_vr,tv player clients (logged-out tv serves
  DRM'd/SABR-limited ladders); rely on nightly-tuned yt-dlp defaults.
- YouTube format selection now prefers resolution over codec with a full
  fallback chain, so 1080p VP9 wins where the H.264 ladder stops short.
  The height cap from Settings still bounds every branch.

Reliability:
- One automatic retry after refreshing yt-dlp; transient extractor
  breakage self-heals instead of telling the user to update manually.
- Engine gate mutex: yt-dlp updates can no longer swap binaries while a
  download is executing them.
- TikTok format chain gains an unrestricted last resort so a missing
  clean stream degrades to watermarked instead of failing.

Crash fixes:
- Vampire mode no longer crashes on Android 12+ when background FGS
  starts are rejected; enqueue() reports failure and callers degrade
  gracefully.
- Cancelling a download no longer raises a "Download failed" notification.

Correctness:
- Library query anchored to Movies/Blackhole (no substring false hits).
- Thumbnail ffmpeg fallback uses unique temp files and skips videos
  over 256 MB instead of copying them wholesale under shared names.

Packaging:
- Deterministic release signing via repo-local keystore/blackhole.jks.
  CI previously signed each build with a fresh ephemeral debug key, so
  every new "latest" APK refused to install over the old one. From now
  on, releases update in place. NOTE: one uninstall of the currently
  installed build is required to migrate to this key.

Version bumped to 1.2 (versionCode 3).
MSG

echo "==> Pushing to origin/main"
git push origin main

echo
echo "==> Pushed. CI is building; watch it with:"
echo "    gh run watch"
echo "    or the Actions tab:"
echo "    https://github.com/SpitfireGG/blackhole_android/actions"
echo
echo "==> When the run goes green, the 'latest' release is refreshed:"
echo "    https://github.com/SpitfireGG/blackhole_android/releases/latest"
