#!/bin/zsh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
OUT_DIR="$ROOT_DIR/nupkgs"

ANDROID_BINDING="$ROOT_DIR/android/LDObserve.Android.Binding/LDObserve.Android.Binding.csproj"
IOS_BINDING="$ROOT_DIR/macios/LDObserve.MaciOS.Binding/LDObserve.MaciOS.Binding.csproj"
FAT_PROJECT="$ROOT_DIR/observability/LDObservability.Fat.csproj"
AGGREGATOR="$ROOT_DIR/observability/LDObservability.csproj"

mkdir -p "$OUT_DIR"

echo "Building Android binding (Release)..."
dotnet build "$ANDROID_BINDING" -c Release

echo "Building iOS binding (Release)..."
dotnet build "$IOS_BINDING" -c Release

echo "Building aggregator (Release, include native deps)..."
dotnet build "$AGGREGATOR" -c Release -p:LD_INCLUDE_NATIVE_DEPS=true

echo "Packing fat package..."
dotnet pack "$FAT_PROJECT" -c Release -o "$OUT_DIR"

echo "Fat package created at: $OUT_DIR"


