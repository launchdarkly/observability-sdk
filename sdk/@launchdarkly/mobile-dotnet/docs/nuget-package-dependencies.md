# Fat NuGet package dependencies (`LDObservability.Fat.csproj`)

This documents **why** [`LDObservability.Fat.csproj`](../observability/LDObservability.Fat.csproj) lists each `PackageReference`. These flow to **consumers** as package dependencies (unless narrowed later with explicit testing).

## All target frameworks

| Package | Version | Rationale |
|---------|---------|-----------|
| `Microsoft.Maui.Controls` | `[8.0.3, 11.0.0)` | MAUI surface expected by the managed SDK and sample apps |
| `Microsoft.Maui.Controls.Compatibility` | `[8.0.3, 11.0.0)` | Compatibility shims used by some MAUI stacks |
| `OpenTelemetry` | `1.11.2` | Tracing API used by `LDObserve` / `LDTracer` |

## LaunchDarkly Client SDK

| Package | When | Rationale |
|---------|------|-----------|
| `LaunchDarkly.ClientSdk` | `UseLocalClientSdk != true` | Default: NuGet Client SDK |
| `LaunchDarkly.CommonSdk`, `LaunchDarkly.EventSource`, `LaunchDarkly.InternalSdk`, `LaunchDarkly.Logging` | `UseLocalClientSdk == true` | Local dev pack embeds ClientSdk DLL but must still declare runtime dependencies |

## Android-only (`net9.0-android`)

| Package | Version | Rationale |
|---------|---------|-----------|
| `Org.Jetbrains.Kotlinx.KotlinxSerializationJson` | `1.9.0` | Kotlin serialization used by bundled Kotlin/Android pieces |
| `Xamarin.AndroidX.Collection.Jvm` | `[1.5.0.3, 2.0.0)` | Pin to avoid NU1107 / duplicate types with navigation splits |
| `Xamarin.AndroidX.Collection.Ktx` | `[1.5.0.3, 2.0.0)` | Aligned with Collection.Jvm |
| `Xamarin.AndroidX.Lifecycle.Runtime` | `[2.9.2.1, 3.0.0)` | Lifecycle alignment across artifacts |
| `Xamarin.AndroidX.Lifecycle.Runtime.Ktx` | `[2.9.2.1, 3.0.0)` | Aligned with Runtime |
| `Xamarin.AndroidX.Lifecycle.Common` | `[2.9.2.1, 3.0.0)` | NU1608 / binding alignment |
| `Xamarin.AndroidX.Lifecycle.LiveData` | `[2.9.2.1, 3.0.0)` | Same |
| `Xamarin.AndroidX.Lifecycle.LiveData.Core` | `[2.9.2.1, 3.0.0)` | Same |
| `Xamarin.AndroidX.Lifecycle.LiveData.Core.Ktx` | `[2.9.2.1, 3.0.0)` | Avoid duplicate LiveDataKt |
| `Xamarin.AndroidX.Lifecycle.Process` | `[2.9.2.1, 3.0.0)` | Same |
| `Xamarin.AndroidX.Lifecycle.ViewModelSavedState` | `[2.9.2.1, 3.0.0)` | Same |
| `Xamarin.AndroidX.SavedState` | `[1.3.1.1, 2.0.0)` | `ISavedStateRegistryOwner` / MAUI requirement (see comments in csproj) |
| `Xamarin.AndroidX.SavedState.SavedState.Ktx` | `[1.3.1.1, 2.0.0)` | Aligned with SavedState |

## Changing this list

- **Do not** add `PrivateAssets="all"` to AndroidX pins to “hide” them from consumers without a **matrix test** (multiple MAUI versions, clean restore, Android build). Those pins often exist to keep **consumer** builds free of NU11xx and duplicate-type failures.
- Prefer **documented** version floor bumps over silent removal.
- If restore time (not DEX/R8) is the bottleneck, prove it with a binary log (see [build-profiling.md](./build-profiling.md)) before large dependency experiments.
