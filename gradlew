#!/usr/bin/env sh
set -eu

# Text-only bootstrap wrapper so the repository remains fully reproducible even
# when gradle-wrapper.jar is not committed by the GitHub text connector.
GRADLE_VERSION="8.7"

if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi

CACHE_ROOT="${GRADLE_USER_HOME:-$HOME/.gradle}/voicecut-bootstrap"
DIST_DIR="$CACHE_ROOT/gradle-$GRADLE_VERSION"
ZIP_FILE="$CACHE_ROOT/gradle-$GRADLE_VERSION-bin.zip"
mkdir -p "$CACHE_ROOT"

if [ ! -x "$DIST_DIR/bin/gradle" ]; then
  URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  if command -v curl >/dev/null 2>&1; then
    curl -L --fail --retry 3 "$URL" -o "$ZIP_FILE"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ZIP_FILE" "$URL"
  else
    echo "VoiceCut MP3: curl ou wget est nécessaire pour initialiser Gradle." >&2
    exit 1
  fi
  rm -rf "$DIST_DIR"
  unzip -q "$ZIP_FILE" -d "$CACHE_ROOT"
fi

exec "$DIST_DIR/bin/gradle" "$@"
