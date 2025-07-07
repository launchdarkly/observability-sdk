from importlib import metadata
import logging
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

from opentelemetry.sdk._logs import LoggerProvider, LoggingHandler
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
import opentelemetry._logs as _logs

from ldobserve.config import _ProcessedConfig

_SCHEDULE_DELAY_MILLIS = 5_000
_MAX_EXPORT_BATCH_SIZE = 128 * 1024
_MAX_QUEUE_SIZE = 1024 * 1024


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


class _OTELConfiguration:
    _tracer_provider: TracerProvider
    _tracer: otel_trace.Tracer
    _logger_provider: LoggerProvider
    _meter_provider: MeterProvider
    _meter: Meter
    _log_handler: LoggingHandler

    @property
    def log_handler(self) -> LoggingHandler:
        return self._log_handler

    @property
    def meter(self) -> Meter:
        return self._meter

    def __init__(self, project_id: str, config: _ProcessedConfig):

        if project_id is None:
            # TODO: Do something better.
            raise ValueError(
                "Project ID is not set. Please call init() before configuring OpenTelemetry."
            )

        self._project_id = project_id
        self._config = config

        resource = _build_resource(
            project_id=self._project_id,
            service_name=self._config.service_name,
            service_version=self._config.service_version,
            environment=self._config.environment,
        )

        sampler = CustomSampler()
        # TODO: Get and set config.

        self._tracer_provider = TracerProvider(resource=resource)
        self._tracer_provider.add_span_processor(
            BatchSpanProcessor(
                SamplingTraceExporter(
                    sampler=sampler,
                    endpoint=f"{self._config.otlp_endpoint}/v1/traces",
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
                    endpoint=f"{self._config.otlp_endpoint}/v1/logs",
                    compression=Compression.Gzip,
                    timeout=_SCHEDULE_DELAY_MILLIS,
                )
            )
        )
        _logs.set_logger_provider(self._logger_provider)

        # The log handler is always created even if logging is not instrumented.
        # This allows directly logging to OpenTelemetry.
        self._log_handler = LoggingHandler(
            level=self._config.log_level, logger_provider=self._logger_provider
        )

        if self._config.instrument_logging:
            # Must configure basic logging before adding a handler.
            logging.basicConfig(level=logging.NOTSET)
            logging.getLogger().addHandler(self._log_handler)

        metric_reader = PeriodicExportingMetricReader(
            exporter=OTLPMetricExporter(
                f"{self._config.otlp_endpoint}/v1/metrics",
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
