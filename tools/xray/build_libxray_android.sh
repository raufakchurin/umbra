#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SDK_DIR="${ANDROID_HOME:-/Users/shibzuko/Library/Android/sdk}"
NDK_DIR="${ANDROID_NDK_HOME:-$SDK_DIR/ndk/27.2.12479018}"
LIBXRAY_DIR="$ROOT_DIR/third_party/libXray"

export PATH="/opt/homebrew/bin:$HOME/go/bin:$PATH"
export ANDROID_HOME="$SDK_DIR"
export ANDROID_NDK_HOME="$NDK_DIR"

if ! command -v go >/dev/null 2>&1; then
  echo "go is required. On macOS: brew install go" >&2
  exit 1
fi

if [ ! -d "$NDK_DIR" ]; then
  echo "Android NDK not found at $NDK_DIR" >&2
  echo "Install it with: sdkmanager 'ndk;27.2.12479018'" >&2
  exit 1
fi

if [ ! -d "$LIBXRAY_DIR/.git" ]; then
  rm -rf "$LIBXRAY_DIR"
  git clone --depth 1 https://github.com/XTLS/libXray "$LIBXRAY_DIR"
fi

cd "$LIBXRAY_DIR"
python3 build/main.py android

mkdir -p "$ROOT_DIR/app/libs"
cp "$LIBXRAY_DIR/libXray.aar" "$ROOT_DIR/app/libs/libXray.aar"

echo "Copied official libXray AAR to app/libs/libXray.aar"
