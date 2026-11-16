#!/usr/bin/env sh
# Pinned Gradle bootstrap for Android Studio / command-line builds.
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION=8.7
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
DIST_DIR="$GRADLE_USER_HOME/upmerge-bootstrap/gradle-$GRADLE_VERSION"
GRADLE_BIN="$DIST_DIR/bin/gradle"
if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$(dirname "$DIST_DIR")"
  TMP_ZIP="${TMPDIR:-/tmp}/upmerge-gradle-$GRADLE_VERSION.zip"
  URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  echo "Gradle $GRADLE_VERSION is not cached; downloading pinned distribution..."
  if command -v curl >/dev/null 2>&1; then curl -fL --retry 2 "$URL" -o "$TMP_ZIP"
  elif command -v wget >/dev/null 2>&1; then wget -O "$TMP_ZIP" "$URL"
  else echo "ERROR: curl or wget is required." >&2; exit 1; fi
  command -v unzip >/dev/null 2>&1 || { echo "ERROR: unzip is required." >&2; exit 1; }
  unzip -q -o "$TMP_ZIP" -d "$(dirname "$DIST_DIR")"
  rm -f "$TMP_ZIP"
fi
exec "$GRADLE_BIN" -p "$APP_HOME" "$@"
