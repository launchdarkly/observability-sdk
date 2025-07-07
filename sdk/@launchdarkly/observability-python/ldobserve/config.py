from dataclasses import dataclass
import logging
import os
from typing import Optional

from opentelemetry.sdk.environment_variables import (
    OTEL_EXPORTER_OTLP_ENDPOINT,
    _OTEL_PYTHON_LOGGING_AUTO_INSTRUMENTATION_ENABLED,
)

DEFAULT_OTLP_ENDPOINT = "https://otel.observability.app.launchdarkly.com:4317"
DEFAULT_INSTRUMENT_LOGGING = True
DEFAULT_LOG_LEVEL = logging.INFO
DEFAULT_DISABLE_EXPORT_ERROR_LOGGING = False


@dataclass(kw_only=True)
class ObservabilityConfig:
    otlp_endpoint: Optional[str] = None
    """
    Used to set a custom OTLP endpoint.

    Alternatively, set the OTEL_EXPORTER_OTLP_ENDPOINT environment variable.
    """

    instrument_logging: Optional[bool] = None
    """
    If True, the OpenTelemetry logging instrumentation will be enabled.

    If False, the OpenTelemetry logging instrumentation will be disabled.

    Alternatively, set the OTEL_PYTHON_LOGGING_AUTO_INSTRUMENTATION_ENABLED environment variable.

    If a custom logging configuration is desired, then it should be configured before initializing the 
    Observability plugin. The Observability plugin will configure default logging prior to adding the 
    OpenTelemetry logging instrumentation.

    For example:
    >>> import logging
    >>> logging.basicConfig(level=logging.INFO)

    Defaults to True.
    """

    log_level: Optional[int] = None
    """
    The log level to use for the OpenTelemetry logging instrumentation.

    This does not affect the log level of the default logging configuration (stdout).

    Defaults to logging.INFO.
    """

    service_name: Optional[str] = None
    """
    The name of the service to use for the OpenTelemetry resource.
    """

    service_version: Optional[str] = None
    """
    The version of the service to use for the OpenTelemetry resource.
    """

    environment: Optional[str] = None
    """
    The environment of the service to use for the OpenTelemetry resource.
    """

    disable_export_error_logging: Optional[bool] = None
    """
    If True, the OpenTelemetry export error logging will be disabled.

    Defaults to False.
    """

    def __getitem__(self, key: str):
        return getattr(self, key)


@dataclass(kw_only=True)
class _ProcessedConfig:
    otlp_endpoint: str
    instrument_logging: bool
    log_level: int
    service_name: Optional[str] = None
    service_version: Optional[str] = None
    environment: Optional[str] = None
    disable_export_error_logging: bool

    def __init__(self, config: ObservabilityConfig):
        self.otlp_endpoint = config.otlp_endpoint or os.getenv(
            OTEL_EXPORTER_OTLP_ENDPOINT, DEFAULT_OTLP_ENDPOINT
        )
        env_instrument_logging = os.getenv(
            _OTEL_PYTHON_LOGGING_AUTO_INSTRUMENTATION_ENABLED, None
        )

        self.instrument_logging = (
            config.instrument_logging
            if config.instrument_logging is not None
            else (
                env_instrument_logging.lower().strip() == "true"
                if env_instrument_logging is not None
                else DEFAULT_INSTRUMENT_LOGGING
            )
        )
        self.log_level = (
            config.log_level if config.log_level is not None else DEFAULT_LOG_LEVEL
        )
        self.service_name = config.service_name
        self.service_version = config.service_version
        self.environment = config.environment
        self.disable_export_error_logging = (
            config.disable_export_error_logging
            if config.disable_export_error_logging is not None
            else DEFAULT_DISABLE_EXPORT_ERROR_LOGGING
        )
