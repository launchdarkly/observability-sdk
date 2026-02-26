using System;

namespace LaunchDarkly.SessionReplay;

public class ObservabilityOptions
{
    public const string DefaultServiceName = "observability-maui";
    public const string DefaultServiceVersion = "0.1.0";
    public const string DefaultOtlpEndpoint = "https://otel.observability.app.launchdarkly.com:4318";
    public const string DefaultBackendUrl = "https://pub.observability.app.launchdarkly.com";

    public string ServiceName { get; set; } = DefaultServiceName;
    public string ServiceVersion { get; set; } = DefaultServiceVersion;
    public string OtlpEndpoint { get; set; } = DefaultOtlpEndpoint;
    public string BackendUrl { get; set; } = DefaultBackendUrl;
    public string? ContextFriendlyName { get; set; }
    public SessionReplayOptions? SessionReplay { get; set; }

    public ObservabilityOptions() { }

    public ObservabilityOptions(
        string serviceName = DefaultServiceName,
        string serviceVersion = DefaultServiceVersion,
        string? otlpEndpoint = null,
        string? backendUrl = null,
        string? contextFriendlyName = null,
        SessionReplayOptions? sessionReplay = null)
    {
        ServiceName = serviceName;
        ServiceVersion = serviceVersion;
        OtlpEndpoint = otlpEndpoint ?? DefaultOtlpEndpoint;
        BackendUrl = backendUrl ?? DefaultBackendUrl;
        ContextFriendlyName = contextFriendlyName;
        SessionReplay = sessionReplay;
    }
}
