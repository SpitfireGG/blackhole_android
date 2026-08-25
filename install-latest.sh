#!/usr/bin/env bash
# One-shot installer: grabs the newest APK from the GitHub 'latest' release,
# removes the conflicting build, and installs on the connected phone.
# Run with:  bash install-latest.sh
set -euo pipefail

cd "$(dirname "$0")"

PKG="com.blackhole.downloader"
REPO="SpitfireGG/blackhole_android"

echo "==> Waiting for device"
adb wait-for-device
adb devices

echo
echo "==> Removing the old, differently-signed build"
# Fails harmlessly if it isn't installed. Videos in Movies/Blackhole survive.
adb uninstall "$PKG" 2>/dev/null || echo "    (not installed — skipping)"

echo
echo "==> Downloading the newest APK from the 'latest' release"
rm -rf .install-tmp && mkdir -p .install-tmp
# Prefer the arm64 APK for your Redmi Note 11 Pro 5G; fall back to universal.
gh release download latest --repo "$REPO" \
  --dir .install-tmp --clobber \
  --pattern "*arm64-v8a*.apk" 2>/dev/null || \
gh release download latest --repo "$REPO" \
  --dir .install-tmp --clobber --pattern "*.apk"

APK=$(ls .install-tmp/*.apk | head -n 1)
echo "    Got: $APK"

echo
echo "==> Installing"
if ! adb install -r "$APK"; then
  echo
  echo "!! Install rejected. On HyperOS/MIUI enable:"
  echo "   Developer options → USB debugging (Security settings)"
  echo "   …then re-run this script."
  exit 1
fi

rm -rf .install-tmp
echo
echo "==> Done. Blackhole is installed — copy a link and tap the void."
