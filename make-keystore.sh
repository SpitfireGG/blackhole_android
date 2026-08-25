#!/usr/bin/env bash
# One-time setup: creates the repo-local release keystore so every build —
# laptop or CI — signs the app identically and APKs install over each other.
# Passwords/alias are intentionally simple defaults; override them in
# gradle.properties (BLACKHOLE_STORE_PASSWORD / BLACKHOLE_KEY_ALIAS /
# BLACKHOLE_KEY_PASSWORD) if you ever want to change them.
#
# NOTE: written to be errexit-safe — every probe failure is expected and
# handled, so the script can never die silently halfway through.
set -uo pipefail

cd "$(dirname "$0")"

KS="keystore/blackhole.jks"

if [ -f "$KS" ]; then
  echo "==> $KS already exists, nothing to do"
  exit 0
fi

echo "==> Hunting for a JDK's keytool"

# ---------------------------------------------------------- collect candidates
candidates=()

if command -v keytool >/dev/null 2>&1; then
  candidates+=("$(command -v keytool)")
fi

if [ -n "${JAVA_HOME:-}" ]; then
  candidates+=("$JAVA_HOME/bin/keytool")
fi

# Android Studio ships its own JetBrains Runtime — no separate JDK required.
fixed=(
  "$HOME/android-studio/jbr/bin/keytool"
  "$HOME/AndroidStudio/jbr/bin/keytool"
  "/opt/android-studio/jbr/bin/keytool"
  "/usr/local/android-studio/jbr/bin/keytool"
  "/snap/android-studio/current/android-studio/jbr/bin/keytool"
  "$HOME/.local/share/JetBrains/Toolbox/apps/android-studio/jbr/bin/keytool"
  "$HOME/.local/share/google/android-studio/jbr/bin/keytool"
  "$HOME/.sdkman/candidates/java/current/bin/keytool"
  "/etc/profiles/per-user/${USER:-$(id -un)}/bin/keytool"
  "$HOME/.nix-profile/bin/keytool"
  "/run/current-system/sw/bin/keytool"
  "/nix/var/nix/profiles/default/bin/keytool"
)

shopt -s nullglob
globbed=(
  "$HOME"/*[Aa]ndroid*/jbr/bin/keytool
  "/opt/"*[Aa]ndroid*/jbr/bin/keytool
  /usr/lib/jvm/*/bin/keytool
)
shopt -u nullglob

for c in "${fixed[@]}" "${globbed[@]}"; do
  candidates+=("$c")
done

# ------------------------------------------------------------------ pick first
KEYTOOL=""
for c in "${candidates[@]}"; do
  if [ -n "$c" ] && [ -x "$c" ]; then
    KEYTOOL="$c"
    break
  fi
done

if [ -z "$KEYTOOL" ]; then
  echo "ERROR: no keytool found anywhere obvious."
  echo
  echo "Quickest fixes:"
  echo "  • NixOS (ephemeral):   nix shell nixpkgs#openjdk -c bash $0"
  echo "  • NixOS (persistent):  nix profile install nixpkgs#openjdk"
  echo "  • Debian/Ubuntu:       sudo apt install openjdk-17-jre-headless"
  echo "  • Fedora:              sudo dnf install java-17-openjdk"
  exit 1
fi

echo "==> Using keytool: $KEYTOOL"

mkdir -p keystore

"$KEYTOOL" -genkeypair -v \
  -keystore "$KS" \
  -alias blackhole \
  -keyalg RSA -keysize 2048 -validity 10950 \
  -storepass blackhole -keypass blackhole \
  -dname "CN=Blackhole, OU=Blackhole, O=Blackhole, L=Internet, ST=Internet, C=XX"

echo
echo "==> Created $KS"
echo "==> Commit it so CI signs releases with the same key:"
echo "     git add keystore/blackhole.jks"
