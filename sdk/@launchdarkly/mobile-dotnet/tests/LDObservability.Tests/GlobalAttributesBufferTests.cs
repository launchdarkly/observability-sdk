using LaunchDarkly.Observability;
using Xunit;

namespace LDObservability.Tests;

public class GlobalAttributesBufferTests
{
    private sealed class RecordingSink : IGlobalAttributesSink
    {
        public List<object> Calls { get; } = new();

        public void SetAttributes(IDictionary<string, object?> attributes)
            => Calls.Add(new Dictionary<string, object?>(attributes));

        public void RemoveAttributes(IReadOnlyCollection<string> keys)
            => Calls.Add(keys.ToList());
    }

    private static Dictionary<string, object?> Attrs(params (string Key, object? Value)[] entries)
        => entries.ToDictionary(e => e.Key, e => e.Value);

    [Fact]
    public void BindWithNothingBufferedMakesNoCalls()
    {
        var buffer = new GlobalAttributesBuffer();
        var sink = new RecordingSink();

        buffer.Bind(sink);

        Assert.Empty(sink.Calls);
    }

    [Fact]
    public void SetBeforeBindIsMergedByKeyAndReplayed()
    {
        var buffer = new GlobalAttributesBuffer();
        buffer.Set(Attrs(("a", 1), ("b", "x")));
        buffer.Set(Attrs(("b", "y"), ("c", true)));

        var sink = new RecordingSink();
        buffer.Bind(sink);

        var call = Assert.Single(sink.Calls);
        var set = Assert.IsType<Dictionary<string, object?>>(call);
        Assert.Equal(Attrs(("a", 1), ("b", "y"), ("c", true)), set);
    }

    [Fact]
    public void RemoveBeforeBindDropsBufferedValueAndIsReplayed()
    {
        var buffer = new GlobalAttributesBuffer();
        buffer.Set(Attrs(("a", 1), ("b", 2)));
        buffer.Remove(new[] { "a", "z" });

        var sink = new RecordingSink();
        buffer.Bind(sink);

        Assert.Equal(2, sink.Calls.Count);
        var removed = Assert.IsType<List<string>>(sink.Calls[0]);
        Assert.Equal(new[] { "a", "z" }, removed.OrderBy(k => k));
        var set = Assert.IsType<Dictionary<string, object?>>(sink.Calls[1]);
        Assert.Equal(Attrs(("b", 2)), set);
    }

    [Fact]
    public void SetAfterRemoveBeforeBindWins()
    {
        var buffer = new GlobalAttributesBuffer();
        buffer.Remove(new[] { "a" });
        buffer.Set(Attrs(("a", "back")));

        var sink = new RecordingSink();
        buffer.Bind(sink);

        var call = Assert.Single(sink.Calls);
        Assert.Equal(Attrs(("a", "back")), Assert.IsType<Dictionary<string, object?>>(call));
    }

    [Fact]
    public void NullValuesBeforeBindDoNotEraseEarlierValues()
    {
        var buffer = new GlobalAttributesBuffer();
        buffer.Set(Attrs(("a", 1)));
        buffer.Set(Attrs(("a", null), ("b", null)));

        var sink = new RecordingSink();
        buffer.Bind(sink);

        var call = Assert.Single(sink.Calls);
        Assert.Equal(Attrs(("a", 1)), Assert.IsType<Dictionary<string, object?>>(call));
    }

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public void ReservedSessionKeysAreIgnored(bool bound)
    {
        var buffer = new GlobalAttributesBuffer();
        var sink = new RecordingSink();
        if (bound) buffer.Bind(sink);

        buffer.Set(Attrs(("session.id", "spoof"), ("highlight.session_id", "spoof"), ("k", "v")));
        buffer.Set(Attrs(("session.id", "only-reserved")));
        if (!bound) buffer.Bind(sink);

        var call = Assert.Single(sink.Calls);
        Assert.Equal(Attrs(("k", "v")), Assert.IsType<Dictionary<string, object?>>(call));
    }

    [Fact]
    public void WritesAfterBindAreForwardedDirectly()
    {
        var buffer = new GlobalAttributesBuffer();
        var sink = new RecordingSink();
        buffer.Bind(sink);

        buffer.Set(Attrs(("a", 1)));
        buffer.Remove(new[] { "a", "a" });

        Assert.Equal(2, sink.Calls.Count);
        Assert.Equal(Attrs(("a", 1)), Assert.IsType<Dictionary<string, object?>>(sink.Calls[0]));
        Assert.Equal(new[] { "a" }, Assert.IsType<List<string>>(sink.Calls[1]));
    }

    [Fact]
    public void BufferedStateIsClearedAfterBind()
    {
        var buffer = new GlobalAttributesBuffer();
        buffer.Set(Attrs(("a", 1)));
        buffer.Bind(new RecordingSink());

        var second = new RecordingSink();
        buffer.Bind(second);

        Assert.Empty(second.Calls);
    }

    [Fact]
    public void NullAndEmptyInputsAreNoOps()
    {
        var buffer = new GlobalAttributesBuffer();
        var sink = new RecordingSink();
        buffer.Bind(sink);

        buffer.Set(null);
        buffer.Set(new Dictionary<string, object?>());
        buffer.Remove(null);
        buffer.Remove(Array.Empty<string>());

        Assert.Empty(sink.Calls);
    }

    [Fact]
    public void BufferedSetIsSnapshotOfCallerDictionary()
    {
        var buffer = new GlobalAttributesBuffer();
        var attrs = Attrs(("a", 1));
        buffer.Set(attrs);
        attrs["a"] = 2;
        attrs["b"] = 3;

        var sink = new RecordingSink();
        buffer.Bind(sink);

        var call = Assert.Single(sink.Calls);
        Assert.Equal(Attrs(("a", 1)), Assert.IsType<Dictionary<string, object?>>(call));
    }

    [Fact]
    public async Task ConcurrentWritesDuringBindAreNotLostOrOverwrittenByReplay()
    {
        var buffer = new GlobalAttributesBuffer();
        var sink = new RecordingSink();
        buffer.Set(Attrs(("k", -1)));

        var writers = Enumerable.Range(0, 8).Select(t => Task.Run(() =>
        {
            for (var i = 0; i < 500; i++)
                buffer.Set(Attrs(($"t{t}", i), ("k", i)));
        })).ToArray();
        buffer.Bind(sink);
        await Task.WhenAll(writers);

        // Fold the recorded calls the way the native store would. Sink calls are serialized by the
        // buffer's lock, so the recorded order is the order native would see.
        var state = new Dictionary<string, object?>();
        foreach (var call in sink.Calls)
        {
            if (call is Dictionary<string, object?> set)
                foreach (var (key, value) in set) state[key] = value;
        }

        for (var t = 0; t < 8; t++)
            Assert.Equal(499, state[$"t{t}"]);
    }
}
