from importlib import metadata
import json
import logging
import os
from typing import Optional
import typing
from grpc import Compression
from ldobserve.otel.sampling.custom_sampler import CustomSampler
from ldobserve.otel.sampling_log_exporter import SamplingLogExporter
from ldobserve.otel.sampling_trace_exporter import SamplingTraceExporter
from opentelemetry.exporter.otlp.proto.grpc.metric_exporter import OTLPMetricExporter
from opentelemetry.metrics import Meter
from opentelemetry.sdk._logs.export import BatchLogRecordProcessor
from opentelemetry.sdk.metrics._internal.export import PeriodicExportingMetricReader
from opentelemetry.sdk.metrics.export import AggregationTemporality
from opentelemetry.sdk.trace.export import BatchSpanProcessor
import opentelemetry.semconv.attributes.service_attributes as service_attributes
import opentelemetry.semconv._incubating.attributes.deployment_attributes as deployment_attributes
from opentelemetry import trace as otel_trace, metrics
from opentelemetry.sdk.metrics import (
    MeterProvider,
    Counter,
    UpDownCounter,
    Histogram,
    ObservableCounter,
    ObservableUpDownCounter,
    ObservableGauge,
)

from ldobserve.config import (
    DEFAULT_INSTRUMENT_LOGGING,
    DEFAULT_LOG_LEVEL,
    DEFAULT_OTLP_ENDPOINT,
    ObservabilityConfig,
)
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
from opentelemetry.sdk._logs import LoggerProvider, LoggingHandler
from opentelemetry.sdk.resources import Resource
from opentelemetry.semconv.resource import ResourceAttributes
from opentelemetry.sdk.trace import TracerProvider
import opentelemetry._logs as _logs

_project_id = None
_config = None

_SCHEDULE_DELAY_MILLIS = 5_000
_MAX_EXPORT_BATCH_SIZE = 128 * 1024
_MAX_QUEUE_SIZE = 1024 * 1024


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


def _build_resource(
    project_id: str,
    service_name: typing.Optional[str],
    service_version: typing.Optional[str],
    environment: typing.Optional[str],
) -> Resource:
    attrs = {}

    if project_id:
        attrs["highlight.project_id"] = project_id
    if service_name:
        attrs[service_attributes.SERVICE_NAME] = service_name
    if service_version:
        attrs[service_attributes.SERVICE_VERSION] = service_version
    if environment:
        attrs[deployment_attributes.DEPLOYMENT_ENVIRONMENT] = environment
    if environment:
        attrs["telemetry.distro.name"] = "launchdarkly-observability"
        attrs["telemetry.distro.version"] = metadata.version("ldobserve")

    # Resource.create() will also look for standard OTEL attributes.
    return Resource.create(attrs)


class LaunchDarklyOpenTelemetryConfigurator(_OTelSDKConfigurator):
    _tracer_provider: TracerProvider
    _tracer: otel_trace.Tracer
    _logger_provider: LoggerProvider
    _logger: _logs.Logger
    _meter_provider: MeterProvider
    _meter: Meter

    def _configure(self, **kwargs):
        global _project_id, _config

        if _project_id is None:
            # TODO: Do something better.
            raise ValueError(
                "Project ID is not set. Please call init() before configuring OpenTelemetry."
            )
        if _config is None:
            _config = ObservabilityConfig()

        resource = _build_resource(
            project_id=_project_id,
            service_name=_config.service_name,
            service_version=_config.service_version,
            environment=_config.environment,
        )

        sampler = CustomSampler()
        # TODO: Get and set config.

        otlp_endpoint = _config.otlp_endpoint or os.getenv(
            OTEL_EXPORTER_OTLP_ENDPOINT, DEFAULT_OTLP_ENDPOINT
        )

        self._tracer_provider = TracerProvider(resource=resource)
        self._tracer_provider.add_span_processor(
            BatchSpanProcessor(
                SamplingTraceExporter(
                    sampler=sampler,
                    endpoint=f"{otlp_endpoint}/v1/traces",
                    compression=Compression.Gzip,
                    timeout=_SCHEDULE_DELAY_MILLIS,
                )
            )
        )
        otel_trace.set_tracer_provider(self._tracer_provider)
        self._tracer = self._tracer_provider.get_tracer(__name__)

        self._logger_provider = LoggerProvider(resource=resource)

        self._logger_provider.add_log_record_processor(
            BatchLogRecordProcessor(
                SamplingLogExporter(
                    sampler=sampler,
                    endpoint=f"{otlp_endpoint}/v1/logs",
                    compression=Compression.Gzip,
                    timeout=_SCHEDULE_DELAY_MILLIS,
                )
            )
        )
        _logs.set_logger_provider(self._logger_provider)

        log_level = _config.log_level or DEFAULT_LOG_LEVEL
        instrument_logging = (
            _config.instrument_logging
            if _config.instrument_logging != None
            else os.getenv(
                _OTEL_PYTHON_LOGGING_AUTO_INSTRUMENTATION_ENABLED,
                DEFAULT_INSTRUMENT_LOGGING,
            )
        )
        if instrument_logging:
            handler = LoggingHandler(
                level=log_level, logger_provider=self._logger_provider
            )

            # Must configure basic logging before adding a handler.
            logging.basicConfig(level=logging.NOTSET)
            logging.getLogger().addHandler(handler)

        self._logger = self._logger_provider.get_logger(__name__)

        metric_reader = PeriodicExportingMetricReader(
            exporter=OTLPMetricExporter(
                f"{otlp_endpoint}/v1/metrics",
                compression=Compression.Gzip,
                timeout=_SCHEDULE_DELAY_MILLIS,
                max_export_batch_size=_MAX_EXPORT_BATCH_SIZE,
                preferred_temporality={
                    Counter: AggregationTemporality.DELTA,
                    UpDownCounter: AggregationTemporality.CUMULATIVE,
                    Histogram: AggregationTemporality.DELTA,
                    ObservableCounter: AggregationTemporality.DELTA,
                    ObservableUpDownCounter: AggregationTemporality.CUMULATIVE,
                    ObservableGauge: AggregationTemporality.CUMULATIVE,
                },
            ),
            export_interval_millis=_SCHEDULE_DELAY_MILLIS,
            export_timeout_millis=_SCHEDULE_DELAY_MILLIS,
        )

        self._meter_provider = MeterProvider(
            resource=resource, metric_readers=[metric_reader]
        )
        metrics.set_meter_provider(self._meter_provider)
        self._meter = self._meter_provider.get_meter(__name__)


class LaunchDarklyOpenTelemetryDistro(BaseDistro):
    """
    The OpenTelemetry provided Distro configures a default set of
    configuration out of the box.
    """

    # pylint: disable=no-self-use
    def _configure(self, **kwargs):
        os.environ.setdefault(OTEL_EXPORTER_OTLP_ENDPOINT, DEFAULT_OTLP_ENDPOINT)
        os.environ.setdefault(
            _OTEL_PYTHON_LOGGING_AUTO_INSTRUMENTATION_ENABLED,
            json.dumps(DEFAULT_INSTRUMENT_LOGGING),
        )
        os.environ.setdefault(OTEL_TRACES_EXPORTER, "otlp")
        os.environ.setdefault(OTEL_METRICS_EXPORTER, "otlp")
        os.environ.setdefault(OTEL_LOGS_EXPORTER, "otlp")
        os.environ.setdefault(OTEL_EXPORTER_OTLP_PROTOCOL, "grpc")
