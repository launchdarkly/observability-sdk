#!/usr/bin/env bash
# Validate a packed fat nupkg: runtimes/android/native must match native-manifest.txt
# (generated next to buildTransitive targets). Optional max size check.
#
# Usage:
#   ./scripts/validate_fat_nupkg.sh path/to/LaunchDarkly.SessionReplay.*.nupkg
#   MANIFEST=/path/to/native-manifest.txt ./scripts/validate_fat_nupkg.sh foo.nupkg
#   MAX_NATIVE_MB=512 ./scripts/validate_fat_nupkg.sh foo.nupkg

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
MANIFEST="${MANIFEST:-$ROOT_DIR/observability/buildTransitive/native-manifest.txt}"
MAX_NATIVE_MB="${MAX_NATIVE_MB:-768}"

if [[ $# -lt 1 ]]; then
	echo "Usage: $0 <path-to.nupkg>" >&2
	exit 1
fi

NUPKG="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"

if [[ ! -f "$NUPKG" ]]; then
	echo "Not a file: $NUPKG" >&2
	exit 1
fi

if [[ ! -f "$MANIFEST" ]]; then
	echo "Manifest not found: $MANIFEST (set MANIFEST=... or run GenerateConsumerBuildTransitive first)" >&2
	exit 1
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

unzip -q -o "$NUPKG" -d "$TMP"

NATIVE_DIR="$TMP/runtimes/android/native"
if [[ ! -d "$NATIVE_DIR" ]]; then
	echo "nupkg missing runtimes/android/native/" >&2
	exit 1
fi

SIZE_KB="$(du -sk "$NATIVE_DIR" | awk '{print $1}')"
SIZE_BYTES=$((SIZE_KB * 1024))
MAX_BYTES=$((MAX_NATIVE_MB * 1024 * 1024))
if [[ "$SIZE_BYTES" -gt "$MAX_BYTES" ]]; then
	echo "runtimes/android/native total size (${SIZE_BYTES} bytes) exceeds MAX_NATIVE_MB=${MAX_NATIVE_MB}" >&2
	exit 1
fi

GOT_FILE="$TMP/got.txt"
EXP_FILE="$TMP/exp.txt"
find "$NATIVE_DIR" -maxdepth 1 -type f \( -name '*.aar' -o -name '*.jar' \) -exec basename {} \; | LC_ALL=C sort >"$GOT_FILE"
grep -v '^[[:space:]]*$' "$MANIFEST" | grep -v '^#' | sed 's/[[:space:]]//g' | LC_ALL=C sort >"$EXP_FILE"

if ! cmp -s "$GOT_FILE" "$EXP_FILE"; then
	echo "native-manifest.txt does not match runtimes/android/native/ in nupkg" >&2
	echo "--- expected (manifest) ---" >&2
	cat "$EXP_FILE" >&2
	echo "--- got (nupkg) ---" >&2
	cat "$GOT_FILE" >&2
	exit 1
fi

COUNT="$(wc -l <"$GOT_FILE" | tr -d ' ')"
SIZE_HR="$(du -sh "$NATIVE_DIR" | awk '{print $1}')"
echo "OK: $NUPKG matches $MANIFEST (${COUNT} files, ${SIZE_HR})"
