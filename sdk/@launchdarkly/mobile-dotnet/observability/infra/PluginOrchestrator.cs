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
        private bool _initialized;

        internal ObservabilityService? ObservabilityService { get; private set; }
        internal SessionReplayService? SessionReplayService { get; private set; }

        private PluginOrchestrator() { }

        private void InitializeAll()
        {
            if (_initialized || _registeredCount < _createdCount) return;
            _initialized = true;

            var metadata = ObservabilityService?.Metadata ?? SessionReplayService?.Metadata;
            if (metadata == null) return;

            var mobileKey = metadata.Credential;

            var observabilityOptions = ObservabilityService?.Options
                ?? new ObservabilityOptions(isEnabled: false);

            var replayOptions = SessionReplayService?.Options
                ?? new SessionReplayOptions(isEnabled: false);

            LDNative.Start(mobileKey, observabilityOptions, replayOptions);

            if (observabilityOptions.IsEnabled && ObservabilityService != null)
                LDObserve.Initialize(ObservabilityService);
        }

        internal void AddObservabilityService(ObservabilityService service)
        {
            ObservabilityService = service;
            _createdCount++;
        }

        internal void Register()
        {
            _registeredCount++;
            
            InitializeAll();
        }

        internal void AddSessionReplayService(SessionReplayService service)
        {
            SessionReplayService = service;
            _createdCount++;
        }
    }
}
