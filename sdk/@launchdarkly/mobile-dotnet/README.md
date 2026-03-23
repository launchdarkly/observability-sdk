# LaunchDarkly Session Replay for .NET MAUI

The LaunchDarkly Session Replay SDK for .NET MAUI allows you to capture user interactions and screen recordings to understand how users interact with your application.

## Prerequisites

*   **.NET 9.0** or higher is required.
*   MAUI support for **iOS** and **Android**.

## Getting Started

To enable Session Replay, you need to configure both the `ObservabilityPlugin` and `SessionReplayPlugin` when initializing the LaunchDarkly client.

### Configure Session Replay

In your `MauiProgram.cs` (or wherever you initialize your application), register the plugins via `LdClient`:

```csharp
using LaunchDarkly.SessionReplay;
using LaunchDarkly.Sdk.Client;
using LaunchDarkly.Sdk.Client.Integrations;
using LaunchDarkly.Observability;

public static class MauiProgram
{
    public static MauiApp CreateMauiApp()
    {
        var builder = MauiApp.CreateBuilder();

        // ... other configuration ...

        var mobileKey = "your-mobile-key";

        var ldConfig = Configuration.Builder(mobileKey, ConfigurationBuilder.AutoEnvAttributes.Enabled)
            .Plugins(new PluginConfigurationBuilder()
                .Add(new ObservabilityPlugin(new ObservabilityOptions(
                    isEnabled: true,
                    serviceName: "maui-sample-app"
                )))
                .Add(new SessionReplayPlugin(new SessionReplayOptions(
                    isEnabled: true,
                    privacy: new SessionReplayOptions.PrivacyOptions(
                        maskTextInputs: true,
                        maskWebViews: false,
                        maskLabels: false
                    )
                )))
            ).Build();

        var context = LaunchDarkly.Sdk.Context.New("maui-user-key");
        var client = LdClient.Init(ldConfig, context, TimeSpan.FromSeconds(10));

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
