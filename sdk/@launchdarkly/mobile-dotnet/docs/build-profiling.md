# Profiling MAUI Android builds

Use this when investigating slow builds or high memory use after adding the Observability SDK or changing native Android dependencies.

## Binary log (recommended)

From [`sdk/@launchdarkly/mobile-dotnet`](../):

```bash
chmod +x scripts/profile_android_build.sh
./scripts/profile_android_build.sh
```

Or set a custom log path:

```bash
BINLOG=/tmp/my-build.binlog ./scripts/profile_android_build.sh
```

The script builds [`sample/MauiSample9.csproj`](../sample/MauiSample9.csproj) for `net9.0-android` in **Release** and writes an MSBuild binary log (`.binlog`).

### Viewing the log

- [Structured Log Viewer](https://github.com/KirillOsenkov/MSBuildStructuredLog) (desktop) — best for search and timeline.
- [Live MSBuild Log](https://live.msbuildlog.com/) — upload the `.binlog` in a browser.

### What to look for

Sort or filter by **duration** and **CPU time**. Common hotspots when many AAR/JAR libraries are referenced:

- Tasks involving **R8**, **D8**, **dex**, **Java**, **CompileToDalvik**
- **ResolvePackageAssets** / long **restore** (NuGet graph size)

Compare a build **with** and **without** the Observability package reference to see incremental cost.

## Command-line only

```bash
dotnet build sample/MauiSample9.csproj -c Release -f net9.0-android -bl:android.binlog
```
