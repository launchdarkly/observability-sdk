using System;
using System.Collections.Generic;
using LaunchDarkly.Sdk.Client.Hooks;
using LaunchDarkly.Sdk.Client.Interfaces;
using LaunchDarkly.Sdk.Integrations.Plugins;
using LaunchDarkly.SessionReplay;

namespace LaunchDarkly.Observability
{
    internal sealed class PluginOrchestrator
    {
        private static readonly Lazy<PluginOrchestrator> _instance =
            new Lazy<PluginOrchestrator>(() => new PluginOrchestrator());

        internal static PluginOrchestrator Instance => _instance.Value;

        private int _createdCount;
        private int _registeredCount;

        internal NativeObserve? Observe { get; private set; }
        internal NativeSessionReplay? SessionReplay { get; private set; }

        private PluginOrchestrator() { }

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
            LDObserve.Initialize(observabilityOptions.ServiceName);
        }

        internal void AddObserve(NativeObserve observe)
        {
            Observe = observe;
            _createdCount++;
        }

        internal void Register()
        {
            _registeredCount++;
            TryInitializeAll();
        }

        internal void AddSessionReplay(NativeSessionReplay sessionReplay)
        {
            SessionReplay = sessionReplay;
            _createdCount++;
        }
    }
}
