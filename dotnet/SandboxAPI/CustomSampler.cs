using System.Diagnostics;
using System.Text.RegularExpressions;
using OpenTelemetry;
using OpenTelemetry.Exporter;
using OpenTelemetry.Logs;

namespace SandboxAPI;

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
        if (attributeConfigs == null || attributeConfigs.Count == 0) return true;
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
        if (eventConfigs == null || eventConfigs.Count == 0) return true;

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
        if (eventConfig.Attributes != null && eventConfig.Attributes.Count > 0)
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

        // Check attributes if defined
        if (config.Attributes != null && config.Attributes.Count > 0)
        {
            var spanTags = new ActivityTagsCollection();
            foreach (var tag in span.Tags)
            {
                spanTags.Add(tag.Key, tag.Value);
            }
            if (!MatchesAttributes(config.Attributes, spanTags))
                return false;
        }

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
            if (_config?.Spans?.Count > 0)
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
        if (config.Attributes != null && config.Attributes.Count > 0)
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
            if (_config?.Logs?.Count > 0)
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

    /// <summary>
    /// Fetches sampling configuration from the network and updates the sampler
    /// </summary>
    /// <param name="client">HTTP client for making the request</param>
    /// <param name="backendUrl">Backend URL to fetch from</param>
    /// <param name="projectId">Project ID (organization verbose ID)</param>
    /// <param name="cancellationToken">Cancellation token</param>
    public async Task UpdateConfigFromNetworkAsync(HttpClient client, string backendUrl, string projectId, CancellationToken cancellationToken = default)
    {
        try
        {
            var samplingClient = new SamplingConfigClient(client, backendUrl);
            var config = await samplingClient.GetSamplingConfigAsync(projectId, cancellationToken);
            
            if (config != null)
            {
                SetConfig(config);
                Console.WriteLine($"Successfully updated sampling config from network. Spans: {config.Spans.Count}, Logs: {config.Logs.Count}");
            }
            else
            {
                Console.WriteLine("No sampling config received from network");
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Failed to update sampling config from network: {ex.Message}");
        }
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
public class SamplingTraceExporter : OtlpTraceExporter
{
    // private readonly BaseExporter<Activity> _innerExporter;
    private readonly IExportSampler _sampler;

    public SamplingTraceExporter(IExportSampler sampler, OtlpExporterOptions options): base(options)
    {
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
                if (result.Attributes != null && result.Attributes.Count > 0)
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
        return base.Export(sampledBatch);
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
                if (result.Attributes != null && result.Attributes.Count > 0)
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
