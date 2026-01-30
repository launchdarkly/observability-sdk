using System;

namespace LaunchDarkly.SessionReplay;

public class ObservabilityOptions
{
    public string ServiceName { get; set; } = "observability-maui";
    public string ServiceVersion { get; set; } = "0.1.0";
    public string OtlpEndpoint { get; set; } = "https://otel.observability.app.launchdarkly.com:4318";
    public string BackendUrl { get; set; } = "https://pub.observability.app.launchdarkly.com";
    public string? ContextFriendlyName { get; set; }
    public SessionReplayOptions? SessionReplay { get; set; }

    public ObservabilityOptions() { }

    public ObservabilityOptions(
        string serviceName = "observability-maui",
        string serviceVersion = "0.1.0",
        string otlpEndpoint = "https://otel.observability.app.launchdarkly.com:4318",
        string backendUrl = "https://pub.observability.app.launchdarkly.com",
        string? contextFriendlyName = null,
        SessionReplayOptions? sessionReplay = null)
    {
        ServiceName = serviceName;
        ServiceVersion = serviceVersion;
        OtlpEndpoint = otlpEndpoint;
        BackendUrl = backendUrl;
        ContextFriendlyName = contextFriendlyName;
        SessionReplay = sessionReplay;
    }
}
