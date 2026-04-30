#!/usr/bin/env bash
# Launch the session-replay-react-native example app on an iOS simulator.
#
# Usage:
#   ./scripts/run-ios.sh                       # default sim, build+install
#   ./scripts/run-ios.sh -s "iPhone 16 Pro"    # pick a simulator by name
#   ./scripts/run-ios.sh --no-pods             # skip pod install (faster if Pods are fresh)
#   ./scripts/run-ios.sh --no-metro            # don't auto-start Metro
#   ./scripts/run-ios.sh --reset-cache         # restart Metro with --reset-cache
#
# Requires: macOS with Xcode + Command Line Tools, CocoaPods (brew install cocoapods),
# Node + Corepack/Yarn 4.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PKG_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
EXAMPLE_DIR="$PKG_DIR/example"
MONOREPO_ROOT="$(cd "$PKG_DIR/../../.." && pwd)"

SIM_NAME=""
RUN_PODS=1
START_METRO=1
RESET_CACHE=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    -s|--simulator)  SIM_NAME="${2:-}"; shift 2 ;;
    --no-pods)       RUN_PODS=0; shift ;;
    --no-metro)      START_METRO=0; shift ;;
    --reset-cache)   RESET_CACHE=1; shift ;;
    -h|--help)
      sed -n '2,12p' "$0"; exit 0 ;;
    *)
      echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

# --- Sanity checks ---
[[ "$(uname)" == "Darwin" ]] || { echo "iOS builds require macOS." >&2; exit 1; }
command -v xcodebuild >/dev/null || { echo "Xcode not found. Install Xcode + Command Line Tools." >&2; exit 1; }
command -v xcrun      >/dev/null || { echo "xcrun not found." >&2; exit 1; }
command -v pod        >/dev/null || { echo "CocoaPods not found. Install via: brew install cocoapods" >&2; exit 1; }
command -v node       >/dev/null || { echo "node not found on PATH. brew install node" >&2; exit 1; }

# --- Boot a simulator if none is booted ---
if [[ -z "$(xcrun simctl list devices booted | awk '/\(Booted\)/' )" ]]; then
  if [[ -z "$SIM_NAME" ]]; then
    SIM_NAME="$(xcrun simctl list devices available 2>/dev/null \
      | awk -F '[()]' '/iPhone .* \([A-F0-9-]{36}\) \(Shutdown\)/ {gsub(/^ +| +$/,"",$1); print $1; exit}')"
    [[ -n "$SIM_NAME" ]] || SIM_NAME="iPhone 16 Pro"
  fi
  echo ">>> Booting iOS simulator: $SIM_NAME"
  xcrun simctl boot "$SIM_NAME" 2>/dev/null || true
  open -a Simulator
  for _ in $(seq 1 30); do
    if xcrun simctl list devices booted | grep -q "(Booted)"; then break; fi
    sleep 1
  done
else
  echo ">>> Using already-booted iOS simulator"
fi

# --- Pod install ---
cd "$EXAMPLE_DIR"
if [[ "$RUN_PODS" -eq 1 ]]; then
  needs_pods=0
  if [[ ! -d ios/Pods ]] || [[ ios/Podfile -nt ios/Pods/Manifest.lock ]] \
     || [[ ios/Podfile.lock -nt ios/Pods/Manifest.lock ]]; then
    needs_pods=1
  fi
  if [[ "$needs_pods" -eq 1 ]]; then
    echo ">>> Running pod install --repo-update"
    pod install --project-directory=ios --repo-update
  else
    echo ">>> Pods are up-to-date (use --no-pods to always skip, or rerun if you changed deps)"
  fi
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

# --- Build & install on simulator ---
echo ">>> Running: yarn workspace session-replay-react-native-example ios"
cd "$MONOREPO_ROOT"
if [[ -n "$SIM_NAME" ]]; then
  yarn workspace session-replay-react-native-example ios --simulator "$SIM_NAME"
else
  yarn workspace session-replay-react-native-example ios
fi
