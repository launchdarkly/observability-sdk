#!/usr/bin/env bash
# Profile a MAUI Android build with an MSBuild binary log for hotspot analysis.
# Requires: .NET SDK with MAUI workload, Android SDK (same as normal sample build).
#
# Usage (from repo root or mobile-dotnet):
#   ./scripts/profile_android_build.sh
#   BINLOG=~/tmp/sample-android.binlog ./scripts/profile_android_build.sh
#
# After the build, open the .binlog in:
#   https://live.msbuildlog.com/  or  https://msbuildstructuredlogviewer.com/
# Look for long-running tasks (R8, D8, CompileToDalvik, Java, ResolvePackageAssets, etc.).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SAMPLE="$ROOT_DIR/sample/MauiSample9.csproj"
BINLOG="${BINLOG:-$ROOT_DIR/sample/android-build.binlog}"

echo "Project: $SAMPLE"
echo "Binary log: $BINLOG"
echo ""

dotnet build "$SAMPLE" -c Release -f net9.0-android -bl:"$BINLOG" "$@"

echo ""
echo "Done. Open the binary log to inspect target/task duration:"
echo "  $BINLOG"
