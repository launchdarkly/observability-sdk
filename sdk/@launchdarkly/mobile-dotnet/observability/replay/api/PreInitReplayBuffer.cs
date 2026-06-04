namespace LaunchDarkly.SessionReplay;

/// <summary>
/// Captures <see cref="LDReplay.IsEnabled"/> writes made before the native stack is wired up,
/// then applies them when <see cref="Bind"/> runs. Without this, calls during the init race
/// (e.g. a UI toggle while <see cref="LDObserve.Init"/> is still running on a background thread)
/// would silently no-op.
/// </summary>
internal sealed class PreInitReplayBuffer
{
    private readonly object _lock = new();

    private SessionReplayOptions? _liveReplay;
    private bool? _pendingEnabled;

    public bool IsEnabled
    {
        get
        {
            SessionReplayOptions? live;
            lock (_lock)
            {
                live = _liveReplay;
                if (live == null)
                    return _pendingEnabled ?? false;
            }

            return live.IsEnabled;
        }
    }

    public void SetEnabled(bool value)
    {
        SessionReplayOptions? live;
        lock (_lock)
        {
            live = _liveReplay;
            if (live == null)
            {
                _pendingEnabled = value;
                return;
            }
        }

        live.IsEnabled = value;
    }

    /// <summary>
    /// Wires the buffer to the live replay options and applies any buffered <see cref="IsEnabled"/> value.
    /// </summary>
    public void Bind(SessionReplayOptions replay)
    {
        lock (_lock)
        {
            if (_pendingEnabled.HasValue)
                replay.IsEnabled = _pendingEnabled.Value;

            _liveReplay = replay;
            _pendingEnabled = null;
        }
    }
}
