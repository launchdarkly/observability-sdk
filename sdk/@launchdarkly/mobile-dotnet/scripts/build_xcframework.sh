#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="${SCRIPT_DIR%/scripts}"
IOS_PROJ_DIR="$REPO_ROOT/macios/native/LDObserve"
DERIVED_DATA="$(mktemp -d -t ldobserve-derived-XXXXXXXX)"
cleanup() { rm -rf "$DERIVED_DATA" >/dev/null 2>&1 || true; }
trap cleanup EXIT

echo "[build_xcframework] Working directory: $IOS_PROJ_DIR"
cd "$IOS_PROJ_DIR"

echo "[build_xcframework] Cleaning previous outputs..."
rm -rf build/outputs/LDObserveBridge.xcframework build/archives

echo "[build_xcframework] Archiving iOS device..."
xcodebuild archive \
  -project LDObserve.xcodeproj \
  -scheme LDObserveBridge \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -derivedDataPath "$DERIVED_DATA" \
  -archivePath build/archives/LDObserveBridge-iOS \
  SKIP_INSTALL=NO \
  BUILD_LIBRARY_FOR_DISTRIBUTION=YES \
  ENABLE_BITCODE=NO \
  OTHER_SWIFT_FLAGS="-no-verify-emitted-module-interface"

echo "[build_xcframework] Archiving iOS simulator..."
xcodebuild archive \
  -project LDObserve.xcodeproj \
  -scheme LDObserveBridge \
  -configuration Release \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath "$DERIVED_DATA" \
  -archivePath build/archives/LDObserveBridge-Sim \
  SKIP_INSTALL=NO \
  BUILD_LIBRARY_FOR_DISTRIBUTION=YES \
  ENABLE_BITCODE=NO \
  OTHER_SWIFT_FLAGS="-no-verify-emitted-module-interface"

echo "[build_xcframework] Creating XCFramework (device + simulator)..."
xcodebuild -create-xcframework \
  -framework build/archives/LDObserveBridge-iOS.xcarchive/Products/Library/Frameworks/LDObserveBridge.framework \
  -framework build/archives/LDObserveBridge-Sim.xcarchive/Products/Library/Frameworks/LDObserveBridge.framework \
  -output build/outputs/LDObserveBridge.xcframework

echo "[build_xcframework] Done. Output: build/outputs/LDObserveBridge.xcframework"
