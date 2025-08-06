using System.Diagnostics;
using System.Text.RegularExpressions;
using LaunchDarkly.Sdk.Integrations.Plugins;
using LaunchDarkly.Sdk.Server.Interfaces;
using LaunchDarkly.Sdk.Server.Plugins;
using OpenTelemetry;
using OpenTelemetry.Exporter;
using OpenTelemetry.Logs;
using OpenTelemetry.Metrics;
using OpenTelemetry.Resources;
using OpenTelemetry.Trace;
using static ObservabilityPlugin;

public class ObservabilityPluginConfig
{
    public required string ProjectId { get; set; }
    public required string ServiceName { get; set; }
    public required string OtlpEndpoint { get; set; }
    public required OtlpExportProtocol OtlpProtocol { get; set; }
    public IExportSampler? Sampler { get; set; }
}

#region Custom Sampling

/// <summary>
/// Represents the result of sampling a span or log
/// </summary>
public class SamplingResult
{
    public bool Sample { get; set; }
    public Dictionary<string, object> Attributes { get; set; } = new();
}

/// <summary>
/// Represents the result of sampling a log record
/// </summary>
public class LogSamplingResult
{
    public bool Sample { get; set; }
    public Dictionary<string, object> Attributes { get; set; } = new();
}

/// <summary>
/// Match configuration for attributes and values
/// </summary>
public class MatchConfig
{
    public object? MatchValue { get; set; }
    public string? RegexValue { get; set; }
}

/// <summary>
/// Attribute matching configuration
/// </summary>
public class AttributeMatchConfig
{
    public MatchConfig Key { get; set; } = new();
    public MatchConfig Attribute { get; set; } = new();
}

/// <summary>
/// Event matching configuration
/// </summary>
public class EventMatchConfig
{
    public MatchConfig Name { get; set; } = new();
    public List<AttributeMatchConfig> Attributes { get; set; } = new();
}

/// <summary>
/// Span sampling configuration
/// </summary>
public class SpanSamplingConfig
{
    public MatchConfig Name { get; set; } = new();
    public List<AttributeMatchConfig> Attributes { get; set; } = new();
    public List<EventMatchConfig> Events { get; set; } = new();
    public int SamplingRatio { get; set; } = 1;
}

/// <summary>
/// Log sampling configuration
/// </summary>
public class LogSamplingConfig
{
    public MatchConfig SeverityText { get; set; } = new();
    public MatchConfig Message { get; set; } = new();
    public List<AttributeMatchConfig> Attributes { get; set; } = new();
    public int SamplingRatio { get; set; } = 1;
}

/// <summary>
/// Overall sampling configuration
/// </summary>
public class SamplingConfig
{
    public List<SpanSamplingConfig> Spans { get; set; } = new();
    public List<LogSamplingConfig> Logs { get; set; } = new();
}

/// <summary>
/// Interface for export samplers
/// </summary>
public interface IExportSampler
{
    SamplingResult SampleSpan(Activity span);
    LogSamplingResult SampleLog(LogRecord record);
    bool IsSamplingEnabled();
    void SetConfig(SamplingConfig config);
}

/// <summary>
/// Function type for sampling decisions
/// </summary>
public delegate bool SamplerFunc(int ratio);

/// <summary>
/// Default sampler function similar to the Go implementation
/// </summary>
public static class DefaultSampler
{
    private static readonly Random _random = new();

    public static bool Sample(int ratio)
    {
        // A ratio of 1 means 1 in 1. So that will always sample.
        if (ratio == 1) return true;
        
        // A ratio of 0 means 0 in 1. So that will never sample.
        if (ratio == 0) return false;

        // Similar logic to Go implementation
        return _random.Next(ratio) == 0;
    }
}

/// <summary>
/// Custom sampler implementation similar to the Go version
/// </summary>
public class CustomSampler : IExportSampler
{
    private readonly SamplerFunc _sampler;
    private SamplingConfig? _config;
    private readonly Dictionary<string, Regex> _regexCache = new();
    private readonly ReaderWriterLockSlim _configLock = new();
    private readonly ReaderWriterLockSlim _regexLock = new();

    public CustomSampler(SamplerFunc? sampler = null)
    {
        _sampler = sampler ?? DefaultSampler.Sample;
    }

    public void SetConfig(SamplingConfig config)
    {
        _configLock.EnterWriteLock();
        try
        {
            _config = config;
        }
        finally
        {
            _configLock.ExitWriteLock();
        }
    }

    public bool IsSamplingEnabled()
    {
        _configLock.EnterReadLock();
        try
        {
            return _config != null && (_config.Spans.Count > 0 || _config.Logs.Count > 0);
        }
        finally
        {
            _configLock.ExitReadLock();
        }
    }

    private Regex? GetCachedRegex(string pattern)
    {
        _regexLock.EnterReadLock();
        try
        {
            if (_regexCache.TryGetValue(pattern, out var cachedRegex))
                return cachedRegex;
        }
        finally
        {
            _regexLock.ExitReadLock();
        }

        _regexLock.EnterWriteLock();
        try
        {
            // Double-check after acquiring write lock
            if (_regexCache.TryGetValue(pattern, out var cachedRegex))
                return cachedRegex;

            try
            {
                var regex = new Regex(pattern, RegexOptions.Compiled);
                _regexCache[pattern] = regex;
                return regex;
            }
            catch
            {
                return null;
            }
        }
        finally
        {
            _regexLock.ExitWriteLock();
        }
    }

    private bool IsMatchConfigEmpty(MatchConfig? matchConfig)
    {
        return matchConfig == null || 
               (matchConfig.MatchValue == null && string.IsNullOrEmpty(matchConfig.RegexValue));
    }

    private bool MatchesValue(MatchConfig matchConfig, object? value)
    {
        if (IsMatchConfigEmpty(matchConfig) || value == null)
            return false;

        // Check basic match value
        if (matchConfig.MatchValue != null)
        {
            return matchConfig.MatchValue.Equals(value);
        }

        // Check regex match
        if (!string.IsNullOrEmpty(matchConfig.RegexValue) && value is string stringValue)
        {
            var regex = GetCachedRegex(matchConfig.RegexValue);
            return regex?.IsMatch(stringValue) ?? false;
        }

        return false;
    }

    private bool MatchesAttributes(List<AttributeMatchConfig> attributeConfigs, ActivityTagsCollection tags)
    {
        if (attributeConfigs.Count == 0) return true;
        if (tags == null || !tags.Any()) return false;

        foreach (var attrConfig in attributeConfigs)
        {
            bool configMatched = false;
            foreach (var tag in tags)
            {
                if (MatchesValue(attrConfig.Key, tag.Key))
                {
                    if (MatchesValue(attrConfig.Attribute, tag.Value))
                    {
                        configMatched = true;
                        break;
                    }
                }
            }
            if (!configMatched) return false;
        }

        return true;
    }

    private bool MatchesEvents(List<EventMatchConfig> eventConfigs, IEnumerable<ActivityEvent> events)
    {
        if (eventConfigs.Count == 0) return true;

        foreach (var eventConfig in eventConfigs)
        {
            bool matched = false;
            foreach (var activityEvent in events)
            {
                if (MatchEvent(eventConfig, activityEvent))
                {
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }

        return true;
    }

    private bool MatchEvent(EventMatchConfig eventConfig, ActivityEvent activityEvent)
    {
        // Match by event name if specified
        if (!IsMatchConfigEmpty(eventConfig.Name))
        {
            if (!MatchesValue(eventConfig.Name, activityEvent.Name))
                return false;
        }

        // Match by event attributes if specified
        if (eventConfig.Attributes.Count > 0)
        {
            var eventTags = new ActivityTagsCollection();
            foreach (var tag in activityEvent.Tags)
            {
                eventTags.Add(tag.Key, tag.Value);
            }
            
            if (!MatchesAttributes(eventConfig.Attributes, eventTags))
                return false;
        }

        return true;
    }

    private bool MatchesSpanConfig(SpanSamplingConfig config, Activity span)
    {

        // Check span name if defined
        if (!IsMatchConfigEmpty(config.Name) && !MatchesValue(config.Name, span.DisplayName))
        {
            return false;
        }

        // Check attributes
        var spanTags = new ActivityTagsCollection();
        foreach (var tag in span.Tags)
        {
            spanTags.Add(tag.Key, tag.Value);
        }
        if (!MatchesAttributes(config.Attributes, spanTags))
            return false;

        // Check events  
        if (!MatchesEvents(config.Events, span.Events))
            return false;

        return true;
    }

    public SamplingResult SampleSpan(Activity span)
    {
        _configLock.EnterReadLock();
        try
        {
            if (_config?.Spans.Count > 0)
            {
                foreach (var spanConfig in _config.Spans)
                {
                    if (MatchesSpanConfig(spanConfig, span))
                    {

                        return new SamplingResult
                        {
                            Sample = _sampler(spanConfig.SamplingRatio),
                            Attributes = new Dictionary<string, object>
                            {
                                ["sampling.ratio"] = spanConfig.SamplingRatio
                            }
                        };
                    }
                }
            }
        }
        finally
        {
            _configLock.ExitReadLock();
        }
        
        // Default to sampling if no config matches
        return new SamplingResult { Sample = true };
    }

    private bool MatchesLogConfig(LogSamplingConfig config, LogRecord record)
    {
        // Check severity text if defined
        if (!IsMatchConfigEmpty(config.SeverityText))
        {
            if (!MatchesValue(config.SeverityText, record.LogLevel.ToString()))
                return false;
        }

        // Check message if defined
        if (!IsMatchConfigEmpty(config.Message))
        {
            var message = record.FormattedMessage ?? record.Body?.ToString();
            if (!MatchesValue(config.Message, message))
                return false;
        }

        // Check attributes if defined
        if (config.Attributes.Count > 0)
        {
            // Convert log record attributes to format we can check
            var logAttributes = new ActivityTagsCollection();
            if (record.Attributes != null)
            {
                foreach (var attr in record.Attributes)
                {
                    logAttributes.Add(attr.Key, attr.Value);
                }
            }
            
            if (!MatchesAttributes(config.Attributes, logAttributes))
                return false;
        }

        return true;
    }

    public LogSamplingResult SampleLog(LogRecord record)
    {
        _configLock.EnterReadLock();
        try
        {
            if (_config?.Logs.Count > 0)
            {
                foreach (var logConfig in _config.Logs)
                {
                    if (MatchesLogConfig(logConfig, record))
                    {
                        return new LogSamplingResult
                        {
                            Sample = _sampler(logConfig.SamplingRatio),
                            Attributes = new Dictionary<string, object>
                            {
                                ["sampling.ratio"] = logConfig.SamplingRatio
                            }
                        };
                    }
                }
            }
        }
        finally
        {
            _configLock.ExitReadLock();
        }

        // Default to sampling if no config matches
        return new LogSamplingResult { Sample = true };
    }

    public void Dispose()
    {
        _configLock?.Dispose();
        _regexLock?.Dispose();
    }
}

/// <summary>
/// Custom trace exporter that applies sampling before exporting
/// </summary>
public class SamplingTraceExporter : BaseExporter<Activity>
{
    private readonly BaseExporter<Activity> _innerExporter;
    private readonly IExportSampler _sampler;

    public SamplingTraceExporter(BaseExporter<Activity> innerExporter, IExportSampler sampler)
    {
        _innerExporter = innerExporter;
        _sampler = sampler;
    }

    public override ExportResult Export(in Batch<Activity> batch)
    {
        var sampled = new List<Activity>();
        
        foreach (var activity in batch)
        {
            var result = _sampler.SampleSpan(activity);
            if (result.Sample)
            {
                // Add sampling attributes if specified
                if (result.Attributes.Count > 0)
                {
                    foreach (var attr in result.Attributes)
                    {
                        activity.SetTag(attr.Key, attr.Value);
                    }
                }
                sampled.Add(activity);
            }
        }

        if (sampled.Count == 0)
            return ExportResult.Success;

        // Create a new batch with only the sampled activities
        using var sampledBatch = new Batch<Activity>(sampled.ToArray(), sampled.Count);
        return _innerExporter.Export(sampledBatch);
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            _innerExporter?.Dispose();
        }
        base.Dispose(disposing);
    }
}

/// <summary>
/// Custom log exporter that applies sampling before exporting
/// </summary>
public class SamplingLogExporter : BaseExporter<LogRecord>
{
    private readonly BaseExporter<LogRecord> _innerExporter;
    private readonly IExportSampler _sampler;

    public SamplingLogExporter(BaseExporter<LogRecord> innerExporter, IExportSampler sampler)
    {
        _innerExporter = innerExporter;
        _sampler = sampler;
    }

    public override ExportResult Export(in Batch<LogRecord> batch)
    {
        var sampled = new List<LogRecord>();
        
        foreach (var logRecord in batch)
        {
            var result = _sampler.SampleLog(logRecord);
            if (result.Sample)
            {
                // Create a copy if we need to add attributes
                if (result.Attributes.Count > 0)
                {
                    // Note: LogRecord doesn't have a direct way to add attributes after creation
                    // This would typically be handled during log record creation or through processors
                    // For now, we'll just pass the original record
                }
                sampled.Add(logRecord);
            }
        }

        if (sampled.Count == 0)
            return ExportResult.Success;

        // Create a new batch with only the sampled log records
        using var sampledBatch = new Batch<LogRecord>(sampled.ToArray(), sampled.Count);
        return _innerExporter.Export(sampledBatch);
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            _innerExporter?.Dispose();
        }
        base.Dispose(disposing);
    }
}

#endregion

public class ObservabilityPlugin : Plugin
{
    public const string ObservabilityHeader = "x-highlight-request";
    private static ObservabilityPluginConfig? _config;
    public static IExportSampler? Sampler => _config?.Sampler;
    
    public ObservabilityPlugin(ObservabilityPluginConfig config) : base(config.ServiceName) {
        _config = config ?? throw new ArgumentNullException(nameof(config));
    }

    public override void Register(ILdClient client, EnvironmentMetadata metadata)
    {
        // OpenTelemetry setup is handled via the static configuration methods
    }

    public static Dictionary<string, string?> GetSessionContext()
    {
        if (_config == null)
            return new Dictionary<string, string?>();
            
        var ctx = new Dictionary<string, string?>
        {
            {
                "highlight.project_id", _config.ProjectId
            },
            {
                "service.name", _config.ServiceName
            },
        };

        var headerValue = Baggage.Current.GetBaggage(ObservabilityHeader);
        if (headerValue == null) return ctx;

        string?[] parts = headerValue.Split("/");
        if (parts.Length < 2) return ctx;

        ctx["highlight.session_id"] = parts[0];
        // rely on `traceparent` w3c parent context propagation instead of highlight.trace_id
        return ctx;
    }

    public class TraceProcessor : BaseProcessor<Activity> {
        public override void OnStart(Activity data)
        {
            var ctx = GetSessionContext();
            foreach (var entry in ctx)
            {
                data.SetTag(entry.Key, entry.Value);
            }

            base.OnStart(data);
        }
    }

    public class LogProcessor : BaseProcessor<LogRecord> {
        public override void OnStart(LogRecord data)
        {
            var ctx = GetSessionContext();
            var attributes = ctx.Select(entry => new KeyValuePair<string, object?>(entry.Key, entry.Value)).ToList();
            if (data.Attributes != null)
            {
                attributes = attributes.Concat(data.Attributes).ToList();
            }

            data.Attributes = attributes;
            base.OnStart(data);
        }
    }
}

/// <summary>
/// Static class containing extension methods for configuring observability
/// </summary>
public static class ObservabilityExtensions
{
    /// <summary>
    /// Extension method to configure OpenTelemetry for the web application builder.
    /// This encapsulates all OpenTelemetry setup including logging, tracing, and metrics.
    /// </summary>
    /// <param name="builder">The web application builder</param>
    /// <param name="config">The observability configuration</param>
    /// <returns>The web application builder for method chaining</returns>
    public static WebApplicationBuilder AddObservability(this WebApplicationBuilder builder, ObservabilityPluginConfig config)
    {
        // Configure OpenTelemetry Logging
        builder.Logging.AddOpenTelemetry(options =>
        {
            var resourceBuilder = ResourceBuilder.CreateDefault().AddService(config.ServiceName);
            options.SetResourceBuilder(resourceBuilder);
            
            // Add console exporter
            options.AddConsoleExporter();
            
            options.AddProcessor(new LogProcessor());
            
            // Add OTLP log exporter
            if (config.Sampler != null)
            {
                // When custom sampling is enabled: create OTLP exporter and wrap with sampling logic
                var otlpExporter = new OtlpLogExporter(new OtlpExporterOptions
                {
                    Endpoint = new Uri(config.OtlpEndpoint + "/v1/logs"),
                    Protocol = config.OtlpProtocol
                });
                
                // Wrap the OTLP exporter with our custom sampling exporter
                var samplingExporter = new SamplingLogExporter(otlpExporter, config.Sampler);
                options.AddProcessor(new SimpleLogRecordExportProcessor(samplingExporter));
            }
            else
            {
                // When no custom sampling: use standard OTLP exporter directly
                options.AddOtlpExporter(otlpOptions =>
                {
                    otlpOptions.Endpoint = new Uri(config.OtlpEndpoint + "/v1/logs");
                    otlpOptions.Protocol = config.OtlpProtocol;
                });
            }
        });

        // Configure OpenTelemetry Tracing and Metrics
        builder.Services.AddOpenTelemetry()
            .ConfigureResource(resource => resource.AddService(config.ServiceName))
            .WithTracing(tracing =>
            {
                tracing
                    .AddAspNetCoreInstrumentation()
                    .AddSource(config.ServiceName)
                    .AddConsoleExporter()
                    .AddProcessor(new TraceProcessor());

                // Add OTLP trace exporter
                if (config.Sampler != null)
                {
                    // When custom sampling is enabled: create OTLP exporter and wrap with sampling logic
                    var otlpExporter = new OtlpTraceExporter(new OtlpExporterOptions
                    {
                        Endpoint = new Uri(config.OtlpEndpoint + "/v1/traces"),
                        Protocol = config.OtlpProtocol
                    });
                    
                    // Wrap the OTLP exporter with our custom sampling exporter
                    var samplingExporter = new SamplingTraceExporter(otlpExporter, config.Sampler);
                    tracing.AddProcessor(new SimpleActivityExportProcessor(samplingExporter));
                }
                else
                {
                    // When no custom sampling: use standard OTLP exporter directly
                    tracing.AddOtlpExporter(otlpOptions =>
                    {
                        otlpOptions.Endpoint = new Uri(config.OtlpEndpoint + "/v1/traces");
                        otlpOptions.Protocol = config.OtlpProtocol;
                    });
                }
            })
            .WithMetrics(metrics => metrics
                .AddAspNetCoreInstrumentation()
                .AddConsoleExporter()
                .AddOtlpExporter(otlpOptions =>
                {
                    otlpOptions.Endpoint = new Uri(config.OtlpEndpoint + "/v1/metrics");
                    otlpOptions.Protocol = config.OtlpProtocol;
                }));

        return builder;
    }

    /// <summary>
    /// Creates a default CustomSampler instance
    /// </summary>
    /// <param name="config">Optional sampling configuration</param>
    /// <returns>A configured CustomSampler</returns>
    public static CustomSampler CreateDefaultSampler(SamplingConfig? config = null)
    {
        var sampler = new CustomSampler();
        if (config != null)
        {
            sampler.SetConfig(config);
        }
        return sampler;
    }
}