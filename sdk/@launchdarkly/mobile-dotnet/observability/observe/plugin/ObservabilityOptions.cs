using System;
using System.Collections.Generic;
using Microsoft.Maui.ApplicationModel;

namespace LaunchDarkly.Observability;

public class ObservabilityOptions
{
    public const string DefaultServiceName = "observability-maui";
    [Obsolete("ServiceVersion now defaults to the host app version (AppInfo.Current.VersionString). This constant is no longer used.")]
    public const string DefaultServiceVersion = "0.1.0";
    public const string DefaultOtlpEndpoint = "https://otel.observability.app.launchdarkly.com:4318";
    public const string DefaultBackendUrl = "https://pub.observability.app.launchdarkly.com";

    public bool IsEnabled { get; set; } = true;
    public string ServiceName { get; set; } = DefaultServiceName;

    /// <summary>
    /// The <c>service.version</c> resource attribute, such as a release version or Git SHA.
    /// When <c>null</c> (the default), the host app's version
    /// (<see cref="AppInfo.VersionString"/>) is reported.
    /// </summary>
    public string? ServiceVersion { get; set; }

    public string OtlpEndpoint { get; set; } = DefaultOtlpEndpoint;
    public string BackendUrl { get; set; } = DefaultBackendUrl;
    public string? ContextFriendlyName { get; set; }
    public IDictionary<string, object?>? Attributes { get; set; }

    /// <summary>
    /// Extra HTTP headers added to OTLP exports (e.g. for proxies or auth).
    /// </summary>
    public IDictionary<string, string>? CustomHeaders { get; set; }

    /// <summary>
    /// Minimum severity of logs exported. Use <see cref="ObservabilityLogLevel.None"/>
    /// to disable log export. Defaults to <see cref="ObservabilityLogLevel.Info"/>.
    /// </summary>
    public ObservabilityLogLevel LogsApiLevel { get; set; } = ObservabilityLogLevel.Info;

    public InstrumentationOptions Instrumentation { get; set; } = new();

    public ObservabilityOptions() { }

    public ObservabilityOptions(
        bool isEnabled = true,
        string serviceName = DefaultServiceName,
        string? serviceVersion = null,
        string? otlpEndpoint = null,
        string? backendUrl = null,
        string? contextFriendlyName = null,
        IDictionary<string, object?>? attributes = null,
        InstrumentationOptions? instrumentation = null,
        IDictionary<string, string>? customHeaders = null,
        ObservabilityLogLevel logsApiLevel = ObservabilityLogLevel.Info)
    {
        IsEnabled = isEnabled;
        ServiceName = serviceName;
        ServiceVersion = serviceVersion;
        OtlpEndpoint = otlpEndpoint ?? DefaultOtlpEndpoint;
        BackendUrl = backendUrl ?? DefaultBackendUrl;
        ContextFriendlyName = contextFriendlyName;
        Attributes = attributes;
        Instrumentation = instrumentation ?? new InstrumentationOptions();
        CustomHeaders = customHeaders;
        LogsApiLevel = logsApiLevel;
    }

    internal string ResolvedServiceVersion
    {
        get
        {
            if (!string.IsNullOrEmpty(ServiceVersion))
                return ServiceVersion;
            try
            {
                return AppInfo.Current.VersionString ?? string.Empty;
            }
            catch (Exception)
            {
                return string.Empty;
            }
        }
    }
}

/// <summary>
/// Severity threshold for exported logs. Values match the OpenTelemetry log severity numbers.
/// </summary>
public enum ObservabilityLogLevel
{
    Trace = 1,
    Trace2 = 2,
    Trace3 = 3,
    Trace4 = 4,
    Debug = 5,
    Debug2 = 6,
    Debug3 = 7,
    Debug4 = 8,
    Info = 9,
    Info2 = 10,
    Info3 = 11,
    Info4 = 12,
    Warn = 13,
    Warn2 = 14,
    Warn3 = 15,
    Warn4 = 16,
    Error = 17,
    Error2 = 18,
    Error3 = 19,
    Error4 = 20,
    Fatal = 21,
    Fatal2 = 22,
    Fatal3 = 23,
    Fatal4 = 24,

    /// <summary>Disables log export entirely.</summary>
    None = int.MaxValue,
}

public class InstrumentationOptions
{
    public bool NetworkRequests { get; set; } = true;
    public bool LaunchTimes { get; set; } = true;

    public InstrumentationOptions() { }

    public InstrumentationOptions(
        bool networkRequests = true,
        bool launchTimes = true)
    {
        NetworkRequests = networkRequests;
        LaunchTimes = launchTimes;
    }
}
