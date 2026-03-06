#!/usr/bin/env bash
# Build LaunchDarklyObservability.xcframework, LaunchDarklySessionReplay.xcframework,
# and all required transitive-dependency xcframeworks from swift-launchdarkly-observability,
# then place them in ios/Frameworks/.
#
# Usage:
#   bash scripts/build-xcframeworks.sh [<version>]
#
# Strategy:
#
#   BUILD_LIBRARY_FOR_DISTRIBUTION=YES
#       Emits .swiftinterface files (text-based, forward-compatible).
#
#   OTHER_SWIFT_FLAGS=-no-verify-emitted-module-interface
#       Skips post-emit verification of the generated .swiftinterface.
#       Avoids a Swift 6.2 / Xcode 26 bug where the NetworkStatus module name
#       conflicts with class NetworkStatus.NetworkStatus inside it.
#
#   We use 'xcodebuild build' (not 'archive') because SPM schemes under
#   'archive' install products to Products/Users/<user>/Objects/ instead of
#   the expected Products/Library/Frameworks/ location.
#
#   After building LaunchDarklyObservability + LaunchDarklySessionReplay:
#     1. All transitive-dep .o files are merged (libtool) into the
#        LaunchDarklyObservability binary so consumers don't need to provide them.
#     2. xcframeworks for the public Swift deps (LaunchDarkly, OpenTelemetry*,
#        SwiftProtobuf) are assembled from the same DerivedData so consumers'
#        Xcode projects can resolve the module names referenced in our
#        .swiftinterface files.
#     3. .swiftinterface files for our two main frameworks are post-processed to
#        remove imports that are implementation-only (KSCrash*, Common,
#        URLSessionInstrumentation, LDSwiftEventSource) and whose types don't
#        appear in the public API — eliminating the need to ship those modules
#        as separate xcframeworks.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="${SCRIPT_DIR}/.."
DEST="${ROOT}/ios/Frameworks"
SPM_URL="https://github.com/launchdarkly/swift-launchdarkly-observability"

# Resolve version: CLI arg → package.json spmVersion → hardcoded fallback
if [[ $# -ge 1 ]]; then
  SPM_VERSION="$1"
else
  SPM_VERSION="$(node -p "require('${ROOT}/package.json').spmVersion || '0.18.1'" 2>/dev/null || echo '0.18.1')"
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "Building XCFrameworks from swift-launchdarkly-observability @ ${SPM_VERSION}"
echo "Output → ${DEST}"

# ── Clone ──────────────────────────────────────────────────────────────────────
git clone --depth 1 --branch "${SPM_VERSION}" "${SPM_URL}" "${TMP}/pkg"
cd "${TMP}/pkg"
mkdir -p "${DEST}"

# Shared DerivedData directories (reused across schemes so the second scheme
# benefits from already-compiled dependencies).
IOS_DD="${TMP}/DD-iOS"
SIM_DD="${TMP}/DD-Sim"

# ── Helper: locate or create a static archive from build products ──────────────
# resolve_archive <scheme> <products_dir> <out_a>
resolve_archive() {
  local SCHEME="$1" PRODS="$2" OUT_A="$3"
  if   [[ -f "${PRODS}/${SCHEME}.a"    ]]; then cp "${PRODS}/${SCHEME}.a"    "${OUT_A}"
  elif [[ -f "${PRODS}/lib${SCHEME}.a" ]]; then cp "${PRODS}/lib${SCHEME}.a" "${OUT_A}"
  else
    local OBJ="${PRODS}/${SCHEME}.o"
    if [[ ! -f "${OBJ}" ]]; then
      echo "  ✗ No .a or .o found for ${SCHEME} under ${PRODS}"
      find "${PRODS}" -maxdepth 4 2>/dev/null | head -40 || true
      exit 1
    fi
    libtool -static -o "${OUT_A}" "${OBJ}"
  fi
}

# ── Helper: assemble a minimal .framework bundle ───────────────────────────────
# make_fw_slice <scheme> <fw_dir> <arch_a> <products_dir>
make_fw_slice() {
  local SCHEME="$1" FW_DIR="$2" ARCH_A="$3" PRODS_DIR="$4"
  mkdir -p "${FW_DIR}/Modules" "${FW_DIR}/Headers"

  cp "${ARCH_A}" "${FW_DIR}/${SCHEME}"

  # Copy the full .swiftmodule directory.
  local SWIFTMOD="${PRODS_DIR}/${SCHEME}.swiftmodule"
  if [[ -d "${SWIFTMOD}" ]]; then
    cp -r "${SWIFTMOD}" "${FW_DIR}/Modules/${SCHEME}.swiftmodule"
  fi

  # Minimal module map (pure-Swift framework; no umbrella header needed)
  printf 'framework module %s {\n  export *\n}\n' "${SCHEME}" \
    > "${FW_DIR}/Modules/module.modulemap"

  /usr/libexec/PlistBuddy \
    -c "Add :CFBundleExecutable  string ${SCHEME}" \
    -c "Add :CFBundleIdentifier  string com.launchdarkly.${SCHEME}" \
    -c "Add :CFBundleName        string ${SCHEME}" \
    -c "Add :CFBundlePackageType string FMWK" \
    -c "Add :CFBundleVersion     string 1" \
    -c "Add :MinimumOSVersion    string 16.0" \
    "${FW_DIR}/Info.plist"
}

# ── Helper: strip implementation-only imports from all .swiftinterface files ──
# strip_imports <xcframework_dir> <import1> [<import2> ...]
strip_imports() {
  local XCF="$1"; shift
  local IMPORTS=("$@")

  # Build a sed expression that removes each listed import line
  local SED_EXPR=""
  for IMP in "${IMPORTS[@]}"; do
    SED_EXPR="${SED_EXPR}/^import ${IMP}$/d;"
  done

  find "${XCF}" -name "*.swiftinterface" | while read -r F; do
    sed -i '' "${SED_EXPR}" "${F}"
  done
}

# ── Build one XCFramework (runs xcodebuild for the named scheme) ───────────────
# build_xcframework <scheme> [<extra_ios_objs...>]
#
# Extra .o files (space-separated names without .o) are merged into the binary.
# This is used to fold implementation-only deps whose imports we strip.
build_xcframework() {
  local SCHEME="$1"; shift
  local EXTRA_DEPS=("$@")
  local OUT="${DEST}/${SCHEME}.xcframework"
  local IOS_PRODS="${IOS_DD}/Build/Products/Release-iphoneos"
  local SIM_PRODS="${SIM_DD}/Build/Products/Release-iphonesimulator"

  echo ""
  echo "  → Building ${SCHEME} for iOS device..."
  xcodebuild build \
    -scheme "${SCHEME}" \
    -destination "generic/platform=iOS" \
    -derivedDataPath "${IOS_DD}" \
    -configuration Release \
    BUILD_LIBRARY_FOR_DISTRIBUTION=YES \
    ENABLE_BITCODE=NO \
    OTHER_SWIFT_FLAGS="-no-verify-emitted-module-interface" \
    SWIFT_TREAT_WARNINGS_AS_ERRORS=NO \
    -quiet

  echo "  → Building ${SCHEME} for iOS Simulator..."
  xcodebuild build \
    -scheme "${SCHEME}" \
    -destination "generic/platform=iOS Simulator" \
    -derivedDataPath "${SIM_DD}" \
    -configuration Release \
    BUILD_LIBRARY_FOR_DISTRIBUTION=YES \
    ENABLE_BITCODE=NO \
    OTHER_SWIFT_FLAGS="-no-verify-emitted-module-interface" \
    SWIFT_TREAT_WARNINGS_AS_ERRORS=NO \
    -quiet

  echo "  → Packaging ${SCHEME}.xcframework..."

  local IOS_A="${TMP}/${SCHEME}-ios.a"
  local SIM_A="${TMP}/${SCHEME}-sim.a"
  resolve_archive "${SCHEME}" "${IOS_PRODS}" "${IOS_A}"
  resolve_archive "${SCHEME}" "${SIM_PRODS}" "${SIM_A}"

  # Merge extra dep .o files into the archive so consumers don't need them
  if [[ ${#EXTRA_DEPS[@]} -gt 0 ]]; then
    local IOS_EXTRA=()
    local SIM_EXTRA=()
    for DEP in "${EXTRA_DEPS[@]}"; do
      [[ -f "${IOS_PRODS}/${DEP}.o" ]] && IOS_EXTRA+=("${IOS_PRODS}/${DEP}.o")
      [[ -f "${IOS_PRODS}/${DEP}.a" ]] && IOS_EXTRA+=("${IOS_PRODS}/${DEP}.a")
      [[ -f "${SIM_PRODS}/${DEP}.o" ]] && SIM_EXTRA+=("${SIM_PRODS}/${DEP}.o")
      [[ -f "${SIM_PRODS}/${DEP}.a" ]] && SIM_EXTRA+=("${SIM_PRODS}/${DEP}.a")
    done
    if [[ ${#IOS_EXTRA[@]} -gt 0 ]]; then
      libtool -static -o "${IOS_A}" "${IOS_A}" "${IOS_EXTRA[@]}"
    fi
    if [[ ${#SIM_EXTRA[@]} -gt 0 ]]; then
      libtool -static -o "${SIM_A}" "${SIM_A}" "${SIM_EXTRA[@]}"
    fi
  fi

  local IOS_FW="${TMP}/${SCHEME}-iOS/${SCHEME}.framework"
  local SIM_FW="${TMP}/${SCHEME}-Sim/${SCHEME}.framework"
  make_fw_slice "${SCHEME}" "${IOS_FW}" "${IOS_A}" "${IOS_PRODS}"
  make_fw_slice "${SCHEME}" "${SIM_FW}" "${SIM_A}" "${SIM_PRODS}"

  rm -rf "${OUT}"
  xcodebuild -create-xcframework \
    -framework "${IOS_FW}" \
    -framework "${SIM_FW}" \
    -output "${OUT}"

  echo "  ✓ ${OUT}"
}

# ── Package a dep XCFramework from already-built DerivedData (no xcodebuild) ──
# package_dep_xcframework <module> [<extra_dep_name...>]
#
# Assembles an xcframework for a module that was compiled as a transitive dep
# when building LaunchDarklyObservability. DerivedData (IOS_DD / SIM_DD) must
# already be populated.
package_dep_xcframework() {
  local MODULE="$1"; shift
  local EXTRA_DEPS=("$@")
  local OUT="${DEST}/${MODULE}.xcframework"
  local IOS_PRODS="${IOS_DD}/Build/Products/Release-iphoneos"
  local SIM_PRODS="${SIM_DD}/Build/Products/Release-iphonesimulator"

  echo "  → Packaging dep ${MODULE}.xcframework..."

  local IOS_A="${TMP}/${MODULE}-ios.a"
  local SIM_A="${TMP}/${MODULE}-sim.a"
  resolve_archive "${MODULE}" "${IOS_PRODS}" "${IOS_A}"
  resolve_archive "${MODULE}" "${SIM_PRODS}" "${SIM_A}"

  # Merge extra deps
  if [[ ${#EXTRA_DEPS[@]} -gt 0 ]]; then
    local IOS_EXTRA=()
    local SIM_EXTRA=()
    for DEP in "${EXTRA_DEPS[@]}"; do
      [[ -f "${IOS_PRODS}/${DEP}.o" ]] && IOS_EXTRA+=("${IOS_PRODS}/${DEP}.o")
      [[ -f "${IOS_PRODS}/${DEP}.a" ]] && IOS_EXTRA+=("${IOS_PRODS}/${DEP}.a")
      [[ -f "${SIM_PRODS}/${DEP}.o" ]] && SIM_EXTRA+=("${SIM_PRODS}/${DEP}.o")
      [[ -f "${SIM_PRODS}/${DEP}.a" ]] && SIM_EXTRA+=("${SIM_PRODS}/${DEP}.a")
    done
    if [[ ${#IOS_EXTRA[@]} -gt 0 ]]; then
      libtool -static -o "${IOS_A}" "${IOS_A}" "${IOS_EXTRA[@]}"
    fi
    if [[ ${#SIM_EXTRA[@]} -gt 0 ]]; then
      libtool -static -o "${SIM_A}" "${SIM_A}" "${SIM_EXTRA[@]}"
    fi
  fi

  local IOS_FW="${TMP}/${MODULE}-iOS/${MODULE}.framework"
  local SIM_FW="${TMP}/${MODULE}-Sim/${MODULE}.framework"
  make_fw_slice "${MODULE}" "${IOS_FW}" "${IOS_A}" "${IOS_PRODS}"
  make_fw_slice "${MODULE}" "${SIM_FW}" "${SIM_A}" "${SIM_PRODS}"

  rm -rf "${OUT}"
  xcodebuild -create-xcframework \
    -framework "${IOS_FW}" \
    -framework "${SIM_FW}" \
    -output "${OUT}"

  echo "  ✓ ${OUT}"
}

# ── Step 1: Build the two main frameworks ─────────────────────────────────────
#
# LaunchDarklyObservability is built first (SessionReplay depends on it).
# We fold in all internal / ObjC-only deps whose imports we will strip:
#   Common, NetworkStatus, ObjCBridge, URLSessionInstrumentation,
#   KSCrash* (KSCrashCore, KSCrashDemangleFilter, KSCrashFilters,
#            KSCrashInstallations, KSCrashRecording, KSCrashRecordingCore,
#            KSCrashReportingCore, KSCrashSinks)
build_xcframework "LaunchDarklyObservability" \
  Common \
  NetworkStatus \
  ObjCBridge \
  URLSessionInstrumentation \
  KSCrashCore \
  KSCrashDemangleFilter \
  KSCrashFilters \
  KSCrashInstallations \
  KSCrashRecording \
  KSCrashRecordingCore \
  KSCrashReportingCore \
  KSCrashSinks

build_xcframework "LaunchDarklySessionReplay"

# ── Step 2: Post-process swiftinterfaces — strip implementation-only imports ──
#
# These imports appear in the generated .swiftinterface even though the types
# from those modules are NOT part of the public API (they're used only in
# implementation files). Stripping them means consumers no longer need to
# resolve those modules at compile time.  The compiled code is already folded
# into LaunchDarklyObservability.xcframework (step 1) so link-time is fine.

echo ""
echo "  → Stripping implementation-only imports from swiftinterfaces..."

# LaunchDarklyObservability: strip KSCrash*, Common, URLSessionInstrumentation
strip_imports "${DEST}/LaunchDarklyObservability.xcframework" \
  Common \
  KSCrashDemangleFilter \
  KSCrashFilters \
  KSCrashInstallations \
  KSCrashRecording \
  URLSessionInstrumentation

# LaunchDarklySessionReplay: strip Common
strip_imports "${DEST}/LaunchDarklySessionReplay.xcframework" \
  Common

# ── Step 3: Build xcframeworks for public transitive deps ──────────────────────
#
# These modules ARE referenced by types in the public API of our frameworks
# (e.g. @_exported import LaunchDarkly, OpenTelemetryApi; OtlpConfiguration;
# ReadableLogRecord; SwiftProtobuf.Message).  We fold LDSwiftEventSource (a
# LaunchDarkly implementation detail) into the LaunchDarkly binary and strip
# its import from LaunchDarkly's .swiftinterface.

echo ""
echo "Building transitive-dependency xcframeworks..."

package_dep_xcframework "LaunchDarkly" LDSwiftEventSource
# Strip LDSwiftEventSource from LaunchDarkly's swiftinterface —
# it's not part of the public LaunchDarkly API.
strip_imports "${DEST}/LaunchDarkly.xcframework" LDSwiftEventSource

package_dep_xcframework "OpenTelemetryApi"
package_dep_xcframework "OpenTelemetrySdk"
package_dep_xcframework "OpenTelemetryProtocolExporterCommon"
package_dep_xcframework "SwiftProtobuf"

echo ""
echo "✓ XCFrameworks ready in ${DEST}"
echo ""
echo "Next steps:"
echo "  1. Run: cd example/ios && pod install"
echo "  2. Build the example app on device and simulator"
