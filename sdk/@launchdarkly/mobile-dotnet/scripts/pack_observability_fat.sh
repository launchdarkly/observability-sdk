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

DOTNET_ARGS=()
CLIENT_SDK_KEY="$ROOT_DIR/../../../../dotnet-core/LaunchDarkly.ClientSdk.snk"
if [[ ! -f "$CLIENT_SDK_KEY" ]]; then
  echo "Signing key not found at $CLIENT_SDK_KEY; building unsigned."
  DOTNET_ARGS+=("-p:SignAssembly=false" "-p:PublicSign=false")
fi

echo "Building Android binding (Release)..."
dotnet build "$ANDROID_BINDING" -c Release "${DOTNET_ARGS[@]}"

echo "Building iOS binding (Release)..."
dotnet build "$IOS_BINDING" -c Release "${DOTNET_ARGS[@]}"

echo "Building aggregator (Release, include native deps)..."
dotnet build "$AGGREGATOR" -c Release -p:LD_INCLUDE_NATIVE_DEPS=true "${DOTNET_ARGS[@]}"

echo "Packing fat package..."
dotnet pack "$FAT_PROJECT" -c Release -o "$OUT_DIR" "${DOTNET_ARGS[@]}"

echo "Fat package created at: $OUT_DIR"


