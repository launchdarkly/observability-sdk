import contextlib
import datetime
import logging
import typing
from opentelemetry.context import Context
from opentelemetry.instrumentation.logging import LEVELS
from opentelemetry.sdk._logs import LoggingHandler
from opentelemetry.trace import Span, Tracer
import opentelemetry.trace as trace
from opentelemetry.util.types import Attributes
from opentelemetry._logs import get_logger_provider
from ldobserve._otel.configuration import _OTELConfiguration
from ldobserve._util.dict import flatten_dict

from opentelemetry.metrics import (
    _Gauge as APIGauge,
    Histogram as APIHistogram,
    Counter as APICounter,
    UpDownCounter as APIUpDownCounter,
)

_name = "launchdarkly-observability"


class _ObserveInstance:
    _project_id: str
    _tracer: Tracer

    _provider = get_logger_provider()
    _logger = logging.getLogger(__name__)
    _otel_configuration: _OTELConfiguration

    _gauges: dict[str, APIGauge] = dict()
    _counters: dict[str, APICounter] = dict()
    _histograms: dict[str, APIHistogram] = dict()
    _up_down_counters: dict[str, APIUpDownCounter] = dict()

    @property
    def log_handler(self) -> logging.Handler:
        return self._otel_configuration.log_handler

    def __init__(self, project_id: str, otel_configuration: _OTELConfiguration):
        self._otel_configuration = otel_configuration

        # Logger that will only log to OpenTelemetry.
        self._logger.propagate = False
        self._logger.addHandler(otel_configuration.log_handler)
        self._project_id = project_id
        self._tracer = trace.get_tracer(_name)

    def record_exception(
        self, error: Exception, attributes: typing.Optional[Attributes] = None
    ):
        span = trace.get_current_span()
        if not span:
            raise RuntimeError("H.record_exception called without a span context")

        attrs = {}
        if attributes:
            addedAttributes = flatten_dict(attributes, sep=".")
            attrs.update(addedAttributes)

        span.record_exception(error, attrs)

    def record_metric(
        self, name: str, value: float, attributes: typing.Optional[Attributes] = None
    ):
        if name not in self._gauges:
            self._gauges[name] = self._otel_configuration.meter.create_gauge(name)
        self._gauges[name].set(value, attributes=attributes)

    def record_count(
        self, name: str, value: int, attributes: typing.Optional[Attributes] = None
    ):
        if name not in self._counters:
            self._counters[name] = self._otel_configuration.meter.create_counter(name)
        self._counters[name].add(value, attributes=attributes)

    def record_incr(self, name: str, attributes: typing.Optional[Attributes] = None):
        return self.record_count(name, 1, attributes)

    def record_histogram(
        self, name: str, value: float, attributes: typing.Optional[Attributes] = None
    ):
        if name not in self._histograms:
            self._histograms[name] = self._otel_configuration.meter.create_histogram(
                name
            )
        self._histograms[name].record(value, attributes=attributes)

    def record_up_down_counter(
        self, name: str, value: int, attributes: typing.Optional[Attributes] = None
    ):
        if name not in self._up_down_counters:
            self._up_down_counters[name] = (
                self._otel_configuration.meter.create_up_down_counter(name)
            )
        self._up_down_counters[name].add(value, attributes=attributes)

    def log(
        self, message: str, level: int, attributes: typing.Optional[Attributes] = None
    ):
        self._logger.log(level, message, extra=attributes)


_instance: typing.Optional[_ObserveInstance] = None


def _use_instance(func_name: str, *args, **kwargs):
    """Helper function to delegate calls to the instance if it exists."""
    if not _instance:
        # TODO: Log usage error.
        return
    method = getattr(_instance, func_name)
    return method(*args, **kwargs)


def record_exception(error: Exception, attributes: typing.Optional[Attributes] = None):
    """
    Record arbitrary exceptions raised within your app.

    Example:
        import ldobserve.observe as observe
        # Observability plugin must be initialized.

        def my_fn():
            try:
                for i in range(20):
                    result = 100 / (10 - i)
                    print(f'dangerous: {result}')
            except Exception as e:
                observe.record_exception(e)


    :param e: the exception to record. the contents and stacktrace will be recorded.
    :param attributes: additional metadata to attribute to this error.
    :return: None
    """
    _use_instance("record_exception", error, attributes)


def record_metric(
    name: str, value: float, attributes: typing.Optional[Attributes] = None
):
    """
    Record arbitrary metric values via as a Gauge.
    A Gauge records any point-in-time measurement, such as the current CPU utilization %.
    Values with the same metric name and attributes are aggregated via the OTel SDK.
    See https://opentelemetry.io/docs/specs/otel/metrics/data-model/ for more details.
    :param name: the name of the metric.
    :param value: the float value of the metric.
    :param attributes: additional metadata which can be used to filter and group values.
    :return: None
    """
    _use_instance("record_metric", name, value, attributes)


def record_count(name: str, value: int, attributes: typing.Optional[Attributes] = None):
    """
    Record arbitrary metric values via as a Counter.
    A Counter efficiently records an increment in a metric, such as number of cache hits.
    Values with the same metric name and attributes are aggregated via the OTel SDK.
    See https://opentelemetry.io/docs/specs/otel/metrics/data-model/ for more details.
    :param name: the name of the metric.
    :param value: the float value of the metric.
    :param attributes: additional metadata which can be used to filter and group values.
    :return: None
    """
    _use_instance("record_count", name, value, attributes)


def record_incr(name: str, attributes: typing.Optional[Attributes] = None):
    """
    Record arbitrary metric +1 increment via as a Counter.
    A Counter efficiently records an increment in a metric, such as number of cache hits.
    Values with the same metric name and attributes are aggregated via the OTel SDK.
    See https://opentelemetry.io/docs/specs/otel/metrics/data-model/ for more details.
    :param name: the name of the metric.
    :param attributes: additional metadata which can be used to filter and group values.
    :return: None
    """
    _use_instance("record_incr", name, attributes)


def record_histogram(
    name: str, value: float, attributes: typing.Optional[Attributes] = None
):
    """
    Record arbitrary metric values via as a Histogram.
    A Histogram efficiently records near-by point-in-time measurement into a bucketed aggregate.
    Values with the same metric name and attributes are aggregated via the OTel SDK.
    See https://opentelemetry.io/docs/specs/otel/metrics/data-model/ for more details.
    :param name: the name of the metric.
    :param value: the float value of the metric.
    :param attributes: additional metadata which can be used to filter and group values.
    :return: None
    """
    _use_instance("record_histogram", name, value, attributes)


def record_up_down_counter(
    name: str, value: int, attributes: typing.Optional[Attributes] = None
):
    """
    Record arbitrary metric values via as a UpDownCounter.
    A UpDownCounter efficiently records an increment or decrement in a metric, such as number of paying customers.
    Values with the same metric name and attributes are aggregated via the OTel SDK.
    See https://opentelemetry.io/docs/specs/otel/metrics/data-model/ for more details.
    :param name: the name of the metric.
    :param value: the float value of the metric.
    :param attributes: additional metadata which can be used to filter and group values.
    :return: None
    """
    _use_instance("record_up_down_counter", name, value, attributes)


def record_log(
    message: str,
    level: int,
    attributes: typing.Optional[Attributes] = None,
):
    """
    Records a log. This log will be recorded to LaunchDarkly, but will not be send to other log handlers.
    A Log records a message with a level and optional attributes.
    :param message: the message to record.
    :param level: the level of the log.
    :param attributes: additional metadata which can be used to filter and group values.
    :return: None
    """
    _use_instance("log", message, level, attributes)


def logging_handler() -> logging.Handler:
    """A logging handler implementing `logging.Handler` that allows plugging LaunchDarkly Observability
    into your existing logging setup. Standard logging will be automatically instrumented unless
    :class:`ObservabilityConfig.instrument_logging <ldobserve.config.ObservabilityConfig.instrument_logging>` is set to False.

    Example:
        import ldobserve.observe as observe
        from loguru import logger

        # Observability plugin must be initialized.
        # If the Observability plugin is not initialized, then a NullHandler will be returned.

        logger.add(
            observe.logging_handler(),
            format="{message}",
            level="INFO",
            backtrace=True,
        )
    """
    if not _instance:
        return logging.NullHandler()
    return _instance.log_handler


def is_initialized() -> bool:
    return _instance != None
