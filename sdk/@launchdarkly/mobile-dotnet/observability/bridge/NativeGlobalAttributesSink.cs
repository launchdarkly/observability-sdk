using System.Collections.Generic;
using System.Linq;

namespace LaunchDarkly.Observability;

#if ANDROID
/// <summary>
/// Forwards global attribute writes into the native SDK's process-wide store, converting values
/// the same way as <see cref="LDObserve.RecordLog"/> attributes.
/// </summary>
internal sealed class NativeGlobalAttributesSink : IGlobalAttributesSink
{
    private readonly LDObserveAndroid.ObservabilityBridge _bridge;

    internal NativeGlobalAttributesSink(LDObserveAndroid.ObservabilityBridge bridge)
    {
        _bridge = bridge;
    }

    public void SetAttributes(IDictionary<string, object?> attributes)
    {
        var map = DictionaryTypeConverters.ToJavaDictionary(attributes);
        if (map is { Count: > 0 })
            _bridge.SetAttributes(map);
    }

    public void RemoveAttributes(IReadOnlyCollection<string> keys)
        => _bridge.RemoveAttributes(keys.ToList());
}
#elif IOS
/// <summary>
/// Forwards global attribute writes into the native SDK's process-wide store, converting values
/// the same way as <see cref="LDObserve.RecordLog"/> attributes.
/// </summary>
internal sealed class NativeGlobalAttributesSink : IGlobalAttributesSink
{
    public void SetAttributes(IDictionary<string, object?> attributes)
    {
        var dict = DictionaryTypeConverters.ToNSDictionary(attributes);
        if (dict is { Count: > 0 })
            LDObserveMaciOS.ObservabilityBridge.SetAttributes(dict);
    }

    public void RemoveAttributes(IReadOnlyCollection<string> keys)
        => LDObserveMaciOS.ObservabilityBridge.RemoveAttributes(keys.ToArray());
}
#endif
