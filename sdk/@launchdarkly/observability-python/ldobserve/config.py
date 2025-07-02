from dataclasses import dataclass
import logging
from typing import Optional


DEFAULT_OTLP_ENDPOINT = "https://otel.observability.app.launchdarkly.com:4317"
DEFAULT_INSTRUMENT_LOGGING = True
DEFAULT_LOG_LEVEL = logging.INFO
DEFAULT_SERVICE_NAME = ""
DEFAULT_SERVICE_VERSION = ""
DEFAULT_ENVIRONMENT = ""
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
