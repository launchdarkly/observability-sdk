#!/bin/zsh
set -euo pipefail

ROOT_DIR="/Users/abelonogov/work/mobile-dotnet"
ANDROID_BINDING="$ROOT_DIR/android/LDObserve.Android.Binding/LDObserve.Android.Binding.csproj"
IOS_BINDING="$ROOT_DIR/macios/LDObserve.MaciOS.Binding/LDObserve.MaciOS.Binding.csproj"
AGGREGATOR="$ROOT_DIR/observability/LDObservability.csproj"
OUT_DIR="$ROOT_DIR/nupkgs"

mkdir -p "$OUT_DIR"

# Pack binding projects first so the aggregator can declare them as dependencies
dotnet pack "$ANDROID_BINDING" -c Debug -o "$OUT_DIR"
dotnet pack "$IOS_BINDING" -c Debug -o "$OUT_DIR"
dotnet pack "$AGGREGATOR" -c Debug -o "$OUT_DIR" -p:LD_INCLUDE_NATIVE_DEPS=true

echo "Packed LDObservability to: $OUT_DIR"


