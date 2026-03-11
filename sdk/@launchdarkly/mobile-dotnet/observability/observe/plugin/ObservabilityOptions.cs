using System;
using System.Collections.Generic;

namespace LaunchDarkly.SessionReplay;

public class ObservabilityOptions
{
    public const string DefaultServiceName = "observability-maui";
    public const string DefaultServiceVersion = "0.1.0";
    public const string DefaultOtlpEndpoint = "https://otel.observability.app.launchdarkly.com:4318";
    public const string DefaultBackendUrl = "https://pub.observability.app.launchdarkly.com";

    public bool IsEnabled { get; set; } = true;
    public string ServiceName { get; set; } = DefaultServiceName;
    public string ServiceVersion { get; set; } = DefaultServiceVersion;
    public string OtlpEndpoint { get; set; } = DefaultOtlpEndpoint;
    public string BackendUrl { get; set; } = DefaultBackendUrl;
    public string? ContextFriendlyName { get; set; }
    public IDictionary<string, object>? Attributes { get; set; }

    public ObservabilityOptions() { }

    public ObservabilityOptions(
        bool isEnabled = true,
        string serviceName = DefaultServiceName,
        string serviceVersion = DefaultServiceVersion,
        string? otlpEndpoint = null,
        string? backendUrl = null,
        string? contextFriendlyName = null,
        IDictionary<string, object>? attributes = null)
    {
        IsEnabled = isEnabled;
        ServiceName = serviceName;
        ServiceVersion = serviceVersion;
        OtlpEndpoint = otlpEndpoint ?? DefaultOtlpEndpoint;
        BackendUrl = backendUrl ?? DefaultBackendUrl;
        ContextFriendlyName = contextFriendlyName;
        Attributes = attributes;
    }
}
