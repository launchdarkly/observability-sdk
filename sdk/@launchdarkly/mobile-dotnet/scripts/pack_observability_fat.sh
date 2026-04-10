#!/bin/bash
# Builds bindings + aggregator, regenerates consumer buildTransitive targets (requires python3),
# packs LDObservability.Fat, and validates runtimes/android/native vs native-manifest.txt.
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
# CLIENT_SDK_KEY="$ROOT_DIR/../../../../dotnet-core/LaunchDarkly.ClientSdk.snk"
# if [[ ! -f "$CLIENT_SDK_KEY" ]]; then
#   echo "Signing key not found at $CLIENT_SDK_KEY; building unsigned."
#   DOTNET_ARGS+=("-p:SignAssembly=false" "-p:PublicSign=false")
# fi

echo "Building Android binding (Release)..."
dotnet build "$ANDROID_BINDING" -c Release ${DOTNET_ARGS[@]+"${DOTNET_ARGS[@]}"}

echo "Building iOS binding (Release)..."
dotnet build "$IOS_BINDING" -c Release ${DOTNET_ARGS[@]+"${DOTNET_ARGS[@]}"}

echo "Building aggregator (Release, include native deps)..."
dotnet build "$AGGREGATOR" -c Release -p:LD_INCLUDE_NATIVE_DEPS=true ${DOTNET_ARGS[@]+"${DOTNET_ARGS[@]}"}

echo "Regenerating buildTransitive targets + native-manifest from resolved natives..."
# dotnet msbuild does not accept -c; use -p:Configuration=Release
dotnet msbuild "$FAT_PROJECT" -t:_GenerateConsumerBuildTransitiveCore \
	-p:Configuration=Release -p:TargetFramework=net9.0-android ${DOTNET_ARGS[@]+"${DOTNET_ARGS[@]}"}

echo "Packing fat package..."
dotnet pack "$FAT_PROJECT" -c Release -o "$OUT_DIR" ${DOTNET_ARGS[@]+"${DOTNET_ARGS[@]}"}

# NuGet's PackTask can include JARs that MSBuild glob Exclude/Remove failed to suppress.
# Strip excluded OTel JARs directly from the nupkg (zip) — this is the only fully reliable method.
EXCLUDED_JAR_PATTERNS=(
	"opentelemetry-sdk-extension-autoconfigure"
	"opentelemetry-sdk-extension-incubator"
	"opentelemetry-sdk-testing"
)
for f in "$OUT_DIR"/*.nupkg; do
	[[ -e "$f" ]] || continue
	for pat in "${EXCLUDED_JAR_PATTERNS[@]}"; do
		# List matching entries, strip them if present
		matches="$(unzip -Z1 "$f" "runtimes/android/native/${pat}*" 2>/dev/null || true)"
		if [[ -n "$matches" ]]; then
			echo "Stripping excluded JARs matching '${pat}' from $(basename "$f"):"
			echo "$matches"
			# zip -d returns 0 on success; entries are removed in-place
			zip -d "$f" $matches
		fi
	done
done

VALIDATE_SCRIPT="$SCRIPT_DIR/validate_fat_nupkg.sh"
if [[ -x "$VALIDATE_SCRIPT" ]]; then
	echo "Validating nupkg native payload vs native-manifest.txt..."
	for f in "$OUT_DIR"/*.nupkg; do
		[[ -e "$f" ]] || continue
		"$VALIDATE_SCRIPT" "$f"
	done
fi

echo "Fat package created at: $OUT_DIR"


