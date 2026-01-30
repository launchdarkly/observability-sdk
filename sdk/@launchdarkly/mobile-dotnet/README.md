# LaunchDarkly Session Replay for .NET MAUI

The LaunchDarkly Session Replay SDK for .NET MAUI allows you to capture user interactions and screen recordings to understand how users interact with your application.

## Prerequisites

*   **.NET 9.0** or higher is required.
*   MAUI support for **iOS** and **Android**.

## Getting Started

To enable Session Replay, you need to configure both the `ObservabilityOptions` and `SessionReplayOptions` when starting the SDK.

### Configure Session Replay

In your `MauiProgram.cs` (or wherever you initialize your application), use the `LDNative.Start` method:

```csharp
using LaunchDarkly.SessionReplay;

public static class MauiProgram
{
    public static MauiApp CreateMauiApp()
    {
        var builder = MauiApp.CreateBuilder();
        
        // ... other configuration ...

        var mobileKey = "your-mobile-key";

        var ldNative = LDNative.Start(
            mobileKey: mobileKey,
            observability: new(
                serviceName: "maui-sample-app"
            ),
            replay: new(
                isEnabled: true,
                privacy: new(
                    maskTextInputs: true,
                    maskWebViews: false,
                    maskLabels: false,
                    maskImages: false
                )
            )
        );

        return builder.Build();
    }
}
```

## Privacy Options

You can control what information is captured during a session using `PrivacyOptions`:

*   `MaskTextInputs`: (Default: `true`) Masks all text input fields.
*   `MaskWebViews`: (Default: `false`) Masks all web view content.
*   `MaskLabels`: (Default: `false`) Masks all text labels.
*   `MaskImages`: (Default: `false`) Masks all images.

## Manual Masking

You can manually mask or unmask specific UI components using the provided extension methods on any MAUI `View`.

```csharp
using LaunchDarkly.SessionReplay;

// Mask a specific view
mySensitiveView.LDMask();

// Unmask a specific view
myPublicView.LDUnmask();
```
