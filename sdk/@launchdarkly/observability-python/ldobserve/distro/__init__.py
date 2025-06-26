from importlib import metadata
import json
import logging
import os
from typing import Optional

from ldobserve.config import DEFAULT_INSTRUMENT_LOGGING, DEFAULT_LOG_LEVEL, DEFAULT_OTLP_ENDPOINT, ObservabilityConfig
from opentelemetry.environment_variables import (
    OTEL_LOGS_EXPORTER,
    OTEL_METRICS_EXPORTER,
    OTEL_TRACES_EXPORTER,
)
from opentelemetry.instrumentation.distro import BaseDistro
from opentelemetry.instrumentation.logging import LoggingInstrumentor
from opentelemetry.sdk._configuration import (
    _OTelSDKConfigurator,
)
from opentelemetry.sdk.environment_variables import (
    OTEL_EXPORTER_OTLP_PROTOCOL,
    OTEL_EXPORTER_OTLP_ENDPOINT,
    _OTEL_PYTHON_LOGGING_AUTO_INSTRUMENTATION_ENABLED,
)
from opentelemetry.sdk._logs import LoggingHandler

_project_id = None
_config = None


def _get_otel_log_handler():
    handlers = logging.getLogger().handlers
    for handler in handlers:
        if isinstance(handler, LoggingHandler):
            return handler
    return None


def init(project_id: str, config: Optional[ObservabilityConfig] = None):
    global _project_id, _config
    _project_id = project_id
    _config = config


class LaunchDarklyOpenTelemetryConfigurator(_OTelSDKConfigurator):
    def _configure(self, **kwargs):
        global _project_id, _config

        if _project_id is None:
            # TODO: Do something better.
            raise ValueError(
                "Project ID is not set. Please call init() before configuring OpenTelemetry.")
        if _config is None:
            _config = ObservabilityConfig()

        resource_attributes = kwargs.get('resource_attributes', {})

        # Add LaunchDarkly-specific resource attributes
        resource_attributes.update({
            "telemetry.distro.name": "launchdarkly-observability",
            "telemetry.distro.version": metadata.version("launchdarkly-observability"),
            "highlight.project_id": _project_id,
        })

        kwargs['resource_attributes'] = resource_attributes

        # kwargs['setup_logging_handler'] = _get_config_item(
        #     _config, 'instrument_logging', _OTEL_PYTHON_LOGGING_AUTO_INSTRUMENTATION_ENABLED, DEFAULT_INSTRUMENT_LOGGING)

        if _config.instrument_logging is not None:
            kwargs['setup_logging_handler'] = _config.instrument_logging

        if _config.otlp_endpoint is not None:
            os.environ[OTEL_EXPORTER_OTLP_ENDPOINT] = _config.otlp_endpoint;
        
        super()._configure(**kwargs)

        # The default implementation will set the log level to NOTSET, we then update that.
        log_handler = _get_otel_log_handler()
        if log_handler is not None:
            log_level = _config.log_level if _config.log_level is not None else DEFAULT_LOG_LEVEL
            log_handler.setLevel(log_level)

        # If logging has not been configured, then the OpenTelemetry logging instrumentation
        # will result in console logging being disabled. Calling this will restore it.
        # If the application configures logging before initializing OpenTelemetry, then that
        # configuration will be used.
        logging.basicConfig(level=logging.INFO)


class LaunchDarklyOpenTelemetryDistro(BaseDistro):
    """
    The OpenTelemetry provided Distro configures a default set of
    configuration out of the box.
    """

    # pylint: disable=no-self-use
    def _configure(self, **kwargs):
        os.environ.setdefault(OTEL_EXPORTER_OTLP_ENDPOINT, DEFAULT_OTLP_ENDPOINT)
        os.environ.setdefault(_OTEL_PYTHON_LOGGING_AUTO_INSTRUMENTATION_ENABLED, json.dumps(DEFAULT_INSTRUMENT_LOGGING))
        os.environ.setdefault(OTEL_TRACES_EXPORTER, "otlp")
        os.environ.setdefault(OTEL_METRICS_EXPORTER, "otlp")
        os.environ.setdefault(OTEL_LOGS_EXPORTER, "otlp")
        os.environ.setdefault(OTEL_EXPORTER_OTLP_PROTOCOL, "grpc")
