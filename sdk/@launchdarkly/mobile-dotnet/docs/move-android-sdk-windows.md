# Moving Android SDK to a User-Writable Location on Windows

## Step 1 — Copy the SDK

Open PowerShell **as Administrator**:

```powershell
Copy-Item -Path "C:\Program Files (x86)\Android\android-sdk" -Destination "C:\Android\sdk" -Recurse
```

## Step 2 — Set Environment Variables

1. Press **Win + R**, type `sysdm.cpl`, press Enter.
2. Go to **Advanced** tab → **Environment Variables**.
3. Under **User variables**:
   - Create or edit `ANDROID_HOME` → `C:\Android\sdk`
   - Create or edit `ANDROID_SDK_ROOT` → `C:\Android\sdk`
   - Edit `Path` and ensure it includes:
     - `%ANDROID_HOME%\platform-tools`
     - `%ANDROID_HOME%\cmdline-tools\latest\bin`
     - `%ANDROID_HOME%\tools` (if it exists)
   - Remove any old entries pointing to `C:\Program Files (x86)\Android\android-sdk`.

## Step 3 — Update Visual Studio

1. Open Visual Studio.
2. Go to **Tools → Options → Xamarin → Android Settings**.
3. Set **Android SDK Location** to `C:\Android\sdk`.
4. Set **Android NDK Location** to `C:\Android\sdk\ndk\<version>` (if applicable).

## Step 4 — Accept Licenses

```powershell
C:\Android\sdk\cmdline-tools\latest\bin\sdkmanager.bat --licenses
```

Type `y` for each prompt.

## Step 5 — Verify

Restart PowerShell, then run:

```powershell
echo $env:ANDROID_HOME
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" --list
```

## Step 6 — Clean Up (Optional)

Once everything works, remove the old SDK:

```powershell
Remove-Item -Path "C:\Program Files (x86)\Android\android-sdk" -Recurse -Force
```
