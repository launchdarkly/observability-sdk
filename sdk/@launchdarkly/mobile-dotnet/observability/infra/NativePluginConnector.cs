using System;
using System.Collections.Generic;
using LaunchDarkly.Sdk.Client.Hooks;
using LaunchDarkly.Sdk.Client.Interfaces;
using LaunchDarkly.Sdk.Integrations.Plugins;
using LaunchDarkly.SessionReplay;

namespace LaunchDarkly.Observability
{
    internal sealed class NativePluginConnector
    {
        private static readonly Lazy<NativePluginConnector> _instance =
            new Lazy<NativePluginConnector>(() => new NativePluginConnector());

        internal static NativePluginConnector Instance => _instance.Value;

        private readonly List<INativePlugin> _nativePlugins = new List<INativePlugin>();
        private int _createdCount;
        private int _registeredCount;

        internal NativeObserve? Observe { get; private set; }
        internal NativeSessionReplay? SessionReplay { get; private set; }

        private NativePluginConnector() { }

        private void TryInitializeAll()
        {
            if (_registeredCount < _createdCount) return;

            var metadata = Observe?.Metadata ?? SessionReplay?.Metadata;
            if (metadata == null) return;

            var mobileKey = metadata.Credential;

            var observabilityOptions = Observe?.Options
                ?? new ObservabilityOptions(isEnabled: false);

            var replayOptions = SessionReplay?.Options
                ?? new SessionReplayOptions(isEnabled: false);

            LDNative.Start(mobileKey, observabilityOptions, replayOptions);
        }

        internal void CreateObserve(ObservabilityOptions options)
        {
            if (options == null) throw new ArgumentNullException(nameof(options));
            Observe = new NativeObserve(options);
            _nativePlugins.Add(Observe);
            _createdCount++;
        }

        internal void RegisterObserve(ILdClient client, EnvironmentMetadata metadata)
        {
            if (Observe == null) return;
            Observe.Client = client;
            Observe.Metadata = metadata;
            _registeredCount++;
            TryInitializeAll();
        }

        internal IList<Hook> GetHooksObserve(EnvironmentMetadata metadata)
        {
            if (Observe == null) return new List<Hook>();

            Observe.Metadata = metadata;
            return new List<Hook> { new ObservabilityHook(Observe) };
        }

        internal void CreateSessionReplay(SessionReplayOptions options)
        {
            if (options == null) throw new ArgumentNullException(nameof(options));
            SessionReplay = new NativeSessionReplay(options);
            _nativePlugins.Add(SessionReplay);
            _createdCount++;
        }

        internal void RegisterSessionReplay(ILdClient client, EnvironmentMetadata metadata)
        {
            if (SessionReplay == null) return;
            SessionReplay.Client = client;
            SessionReplay.Metadata = metadata;
            _registeredCount++;
            TryInitializeAll();
        }

        internal IList<Hook> GetHooksSessionReplay(EnvironmentMetadata metadata)
        {
            if (SessionReplay != null)
            {
                SessionReplay.Metadata = metadata;
                return new List<Hook> { new SessionReplayHook(SessionReplay) };
            }
            return new List<Hook>();
        }
    }
}
