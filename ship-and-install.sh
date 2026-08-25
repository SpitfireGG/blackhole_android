#!/usr/bin/env bash
# All-in-one: push the release, wait for CI to build & publish it, then
# install onto the connected phone. Run with:  bash ship-and-install.sh
set -euo pipefail

cd "$(dirname "$0")"
REPO="SpitfireGG/blackhole_android"

echo "==> [1/4] Pushing the release"
bash release.sh

echo
echo "==> [2/4] Waiting for CI to build the APKs (this takes a few minutes)"
gh run watch --repo "$REPO" --exit-status || {
  echo "!! CI failed — open https://github.com/$REPO/actions for the log"
  exit 1
}

echo
echo "==> [3/4] Installing on the phone"
bash install-latest.sh

echo
echo "==> [4/4] Shipped. Copy a link, tap the void."
