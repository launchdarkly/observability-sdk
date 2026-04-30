#!/usr/bin/env bash
# Launch the session-replay-react-native example app on an Android emulator.
#
# Usage:
#   ./scripts/run-android.sh                 # boot first AVD if no device, then build+install
#   ./scripts/run-android.sh -a Pixel_9      # boot a specific AVD
#   ./scripts/run-android.sh --no-metro      # don't auto-start Metro (assume already running)
#   ./scripts/run-android.sh --reset-cache   # restart Metro with --reset-cache
#
# Requires: Android Studio with SDK at $ANDROID_SDK_ROOT (default: ~/Library/Android/sdk),
# Java 17+ (defaults to Android Studio's bundled JBR), Node + Corepack/Yarn 4.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PKG_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
MONOREPO_ROOT="$(cd "$PKG_DIR/../../.." && pwd)"

AVD_NAME=""
START_METRO=1
RESET_CACHE=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    -a|--avd)        AVD_NAME="${2:-}"; shift 2 ;;
    --no-metro)      START_METRO=0; shift ;;
    --reset-cache)   RESET_CACHE=1; shift ;;
    -h|--help)
      sed -n '2,12p' "$0"; exit 0 ;;
    *)
      echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

# --- Environment ---
: "${ANDROID_SDK_ROOT:=$HOME/Library/Android/sdk}"
export ANDROID_SDK_ROOT
export ANDROID_HOME="$ANDROID_SDK_ROOT"
if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME}/bin/java" ]]; then
  if [[ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]]; then
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  fi
fi
export PATH="$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator:${JAVA_HOME:+$JAVA_HOME/bin:}$PATH"

ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
EMULATOR="$ANDROID_SDK_ROOT/emulator/emulator"

[[ -x "$ADB" ]]      || { echo "adb not found at $ADB. Install Android platform-tools." >&2; exit 1; }
[[ -x "$EMULATOR" ]] || { echo "emulator not found at $EMULATOR. Install Android Emulator via SDK Manager." >&2; exit 1; }
command -v node >/dev/null || { echo "node not found on PATH. brew install node" >&2; exit 1; }

# --- Pick / boot device ---
device_count() { "$ADB" devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' '; }

if [[ "$(device_count)" -eq 0 ]]; then
  if [[ -z "$AVD_NAME" ]]; then
    AVD_NAME="$("$EMULATOR" -list-avds | head -1 || true)"
    [[ -n "$AVD_NAME" ]] || { echo "No AVDs found. Create one in Android Studio (Device Manager)." >&2; exit 1; }
  fi
  echo ">>> Booting AVD: $AVD_NAME"
  nohup "$EMULATOR" -avd "$AVD_NAME" -netdelay none -netspeed full \
    > "/tmp/emulator-${AVD_NAME}.log" 2>&1 &
  echo ">>> Waiting for device..."
  "$ADB" wait-for-device
  until [[ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
    sleep 2
  done
  echo ">>> Device ready"
else
  echo ">>> Using already-connected device(s)"
  "$ADB" devices
fi

# --- Metro ---
metro_running() { curl -fsS --max-time 1 http://localhost:8081/status 2>/dev/null | grep -q "packager-status:running"; }

if [[ "$START_METRO" -eq 1 ]]; then
  if metro_running; then
    echo ">>> Metro already running on :8081"
  else
    echo ">>> Starting Metro in background (logs: /tmp/metro-session-replay-rn.log)"
    cd "$MONOREPO_ROOT"
    METRO_ARGS=()
    [[ "$RESET_CACHE" -eq 1 ]] && METRO_ARGS+=(--reset-cache)
    nohup yarn workspace session-replay-react-native-example start "${METRO_ARGS[@]}" \
      > /tmp/metro-session-replay-rn.log 2>&1 &
    for _ in $(seq 1 30); do
      sleep 1
      metro_running && break
    done
    metro_running || { echo "Metro failed to start; see /tmp/metro-session-replay-rn.log" >&2; exit 1; }
    echo ">>> Metro is up"
  fi
fi

# --- Build & install ---
echo ">>> Running: yarn workspace session-replay-react-native-example android"
cd "$MONOREPO_ROOT"
yarn workspace session-replay-react-native-example android
