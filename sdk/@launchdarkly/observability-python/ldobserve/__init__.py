import os
from typing import List, Optional
from ldclient.hook import Hook
from opentelemetry.instrumentation import auto_instrumentation
from ldobserve.config import ObservabilityConfig
from ldobserve.observe import _ObserveInstance
import ldobserve.observe as observe
from ldobserve.config import _ProcessedConfig
import ldobserve.observe
from ldobserve._otel.configuration import _OTELConfiguration
from ldclient.plugin import Plugin, EnvironmentMetadata, PluginMetadata
from ldotel.tracing import Hook, HookOptions
from ldclient.client import LDClient
from opentelemetry.instrumentation.environment_variables import (
    OTEL_PYTHON_DISABLED_INSTRUMENTATIONS,
)


def _extend_disabled_instrumentations(*instrumentations: str) -> None:
    """
    Extend the OTEL_PYTHON_DISABLED_INSTRUMENTATIONS environment variable with additional instrumentations.

    This function reads the current value of OTEL_PYTHON_DISABLED_INSTRUMENTATIONS,
    adds the provided instrumentations to the comma-separated list, and sets the
    environment variable with the extended list.

    Args:
        *instrumentations: Variable number of instrumentation names to add to the disabled list.
                          These should be the names of OpenTelemetry instrumentations to disable.

    Example:
        >>> extend_disabled_instrumentations("redis", "kafka")
        # If OTEL_PYTHON_DISABLED_INSTRUMENTATIONS was "grpc_client",
        # it will become "grpc_client,redis,kafka"
    """
    current_value = os.getenv(OTEL_PYTHON_DISABLED_INSTRUMENTATIONS, "")

    # Split the current value by comma and strip whitespace
    current_list = [item.strip() for item in current_value.split(",") if item.strip()]

    # Add new instrumentations, avoiding duplicates
    for instrumentation in instrumentations:
        if instrumentation.strip() and instrumentation.strip() not in current_list:
            current_list.append(instrumentation.strip())

    # Join the list back into a comma-separated string
    new_value = ",".join(current_list)

    # Set the environment variable
    os.environ[OTEL_PYTHON_DISABLED_INSTRUMENTATIONS] = new_value


class ObservabilityPlugin(Plugin):
    def __init__(self, config: Optional[ObservabilityConfig] = None):
        processed_config = _ProcessedConfig(config or ObservabilityConfig())
        self._config = processed_config
        # Instruct auto-instrumentation to not instrument logging.
        # We will either have already done it, or it is disabled.
        _extend_disabled_instrumentations("logging")

        if self._config.disabled_instrumentations:
            _extend_disabled_instrumentations(*self._config.disabled_instrumentations)

        auto_instrumentation.initialize()

    def metadata(_self) -> PluginMetadata:
        return PluginMetadata(name="launchdarkly-observability")

    def register(self, _client: LDClient, metadata: EnvironmentMetadata) -> None:
        _init(metadata.sdk_key, self._config)

    def get_hooks(_self, _metadata: EnvironmentMetadata) -> List[Hook]:
        return [Hook(options=HookOptions(include_value=True))]


def _init(project_id: str, config: _ProcessedConfig):
    otel_configuration = _OTELConfiguration(project_id, config)
    ldobserve.observe._instance = _ObserveInstance(project_id, otel_configuration)


__all__ = ["ObservabilityPlugin", "ObservabilityConfig", "observe"]
