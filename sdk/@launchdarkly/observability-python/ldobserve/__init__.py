import logging
from typing import Any, List, Optional
from ldclient.hook import Hook
from opentelemetry.instrumentation import auto_instrumentation
from ldobserve.config import ObservabilityConfig
from ldobserve.observe import _ObserveInstance
from ldobserve.config import _ProcessedConfig
import ldobserve.observe
from ldobserve.otel.configuration import _OTELConfiguration
from ldclient.plugin import (Plugin, EnvironmentMetadata, PluginMetadata)
from ldotel.tracing import Hook, HookOptions
from ldclient.client import LDClient

def ObservabilityPlugin(plugin: Plugin):
    def __init__(self, config: Optional[ObservabilityConfig] = None):
        self._config = config or ObservabilityConfig()

    def metadata(_self) -> PluginMetadata:
        return PluginMetadata(name='launchdarkly-observability')

    def register(self, client: LDClient, metadata: EnvironmentMetadata) -> None:
        init(metadata.sdk_key, self._config)

    def get_hooks(self, metadata: EnvironmentMetadata) -> List[Hook]:
        return [Hook(options=HookOptions(include_value=True))]

def init(project_id: str, config: Optional[ObservabilityConfig] = None):
    processed_config = _ProcessedConfig(config or ObservabilityConfig())
    logging.getLogger(__name__).debug("Processed config: %s", processed_config)
    otel_configuration = _OTELConfiguration(project_id, processed_config)
    auto_instrumentation.initialize()
    ldobserve.observe._instance = _ObserveInstance(project_id, otel_configuration)
