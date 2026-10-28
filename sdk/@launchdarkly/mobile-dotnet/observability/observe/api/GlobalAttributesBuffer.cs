using System.Collections.Generic;
using System.Linq;

namespace LaunchDarkly.Observability;

/// <summary>
/// Receives global attribute writes once the native stack is running. The native SDK owns the
/// process-wide map; implementations only convert and forward.
/// </summary>
internal interface IGlobalAttributesSink
{
    void SetAttributes(IDictionary<string, object?> attributes);
    void RemoveAttributes(IReadOnlyCollection<string> keys);
}

/// <summary>
/// Captures <see cref="LDObserve.SetAttributes"/> and <see cref="LDObserve.RemoveAttributes"/> calls
/// made before the native stack starts, then replays them when <see cref="Bind"/> runs. After that,
/// writes are forwarded straight to the sink. Forwarding happens under the same lock as the replay so
/// a write racing with <see cref="Bind"/> can't be overtaken by older buffered values.
/// </summary>
internal sealed class GlobalAttributesBuffer
{
    /// <summary>
    /// Keys owned by native session management. Android ignores both, iOS only <c>session.id</c>,
    /// so they are dropped here to behave the same on both platforms.
    /// </summary>
    internal static readonly IReadOnlySet<string> ReservedKeys = new HashSet<string>
    {
        "session.id",
        "highlight.session_id",
    };

    private readonly object _lock = new();

    private readonly Dictionary<string, object> _pendingSet = new();
    private readonly HashSet<string> _pendingRemove = new();
    private IGlobalAttributesSink? _sink;

    public void Set(IDictionary<string, object?>? attributes)
    {
        if (attributes is null) return;
        if (attributes.Keys.Any(ReservedKeys.Contains))
            attributes = attributes.Where(kv => !ReservedKeys.Contains(kv.Key))
                .ToDictionary(kv => kv.Key, kv => kv.Value);
        if (attributes.Count == 0) return;

        lock (_lock)
        {
            if (_sink != null)
            {
                _sink.SetAttributes(attributes);
                return;
            }

            foreach (var (key, value) in attributes)
            {
                // Native drops null values rather than treating them as removals; match that here so
                // a buffered null can't erase a value set earlier.
                if (value is null) continue;
                _pendingSet[key] = value;
                _pendingRemove.Remove(key);
            }
        }
    }

    public void Remove(IEnumerable<string>? keys)
    {
        if (keys is null) return;
        var list = keys.Where(k => k != null).Distinct().ToList();
        if (list.Count == 0) return;

        lock (_lock)
        {
            if (_sink != null)
            {
                _sink.RemoveAttributes(list);
                return;
            }

            foreach (var key in list)
            {
                _pendingSet.Remove(key);
                _pendingRemove.Add(key);
            }
        }
    }

    /// <summary>
    /// Replays buffered writes into <paramref name="sink"/> and routes all later writes to it.
    /// Removals are replayed first; they never overlap with buffered sets.
    /// </summary>
    public void Bind(IGlobalAttributesSink sink)
    {
        lock (_lock)
        {
            if (_pendingRemove.Count > 0)
                sink.RemoveAttributes(_pendingRemove.ToList());
            if (_pendingSet.Count > 0)
                sink.SetAttributes(_pendingSet.ToDictionary(kv => kv.Key, kv => (object?)kv.Value));

            _pendingRemove.Clear();
            _pendingSet.Clear();
            _sink = sink;
        }
    }
}
