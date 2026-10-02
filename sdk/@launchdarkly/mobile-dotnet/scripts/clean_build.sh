#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="${SCRIPT_DIR%/scripts}"

echo "[clean] Repository root: $REPO_ROOT"

echo "[clean] Removing bin/ and obj/ folders..."
# Find and remove all bin/ and obj/ directories across the repo
find "$REPO_ROOT" -type d \( -name bin -o -name obj \) -prune -print -exec rm -rf {} +

echo "[clean] Removing native iOS build outputs (archives and xcframeworks)..."
IOS_NATIVE_DIR="$REPO_ROOT/macios/native/LDObserve"
rm -rf "$IOS_NATIVE_DIR/build/archives" || true
rm -rf "$IOS_NATIVE_DIR/build/outputs" || true

echo "[clean] Done."


