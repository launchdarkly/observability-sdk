import logging
import threading
import typing

import pytest
from opentelemetry.attributes import BoundedAttributes
from opentelemetry.context import Context
from opentelemetry.sdk.util.instrumentation import InstrumentationScope
from opentelemetry.sdk._logs import (
    LogData,
    LoggerProvider,
    LogRecord,
    LogRecordProcessor,
)
from opentelemetry.sdk._logs.export import InMemoryLogExporter, SimpleLogRecordProcessor
from opentelemetry.sdk.trace import ReadableSpan, Span, SpanProcessor, TracerProvider
from opentelemetry.sdk.trace.export import SimpleSpanProcessor
from opentelemetry.sdk.trace.export.in_memory_span_exporter import InMemorySpanExporter

import ldobserve.observe as observe
from ldobserve._otel import configuration as otel_configuration
from ldobserve._otel.global_attributes import (
    GlobalAttributesLogRecordProcessor,
    clear_global_attributes,
    get_global_attributes,
)
from ldobserve._otel.logging_handler import LDLoggingHandler
from ldobserve._otel.processors import add_log_processors, add_span_processors
from ldobserve.config import ObservabilityConfig, _ProcessedConfig
from ldobserve.observe import _ObserveInstance
from ldobserve.test_observe_instance import OTELTestConfiguration

_raising_processors: list["_Raising"] = []


@pytest.fixture(autouse=True)
def reset_global_attributes():
    clear_global_attributes()
    yield
    clear_global_attributes()
    # Providers also shut down at interpreter exit; don't raise from there.
    for processor in _raising_processors:
        processor.armed = False
    _raising_processors.clear()
    _ObserveInstance._gauges.clear()
    _ObserveInstance._counters.clear()
    _ObserveInstance._histograms.clear()
    _ObserveInstance._up_down_counters.clear()


def _make_instance(**kwargs) -> tuple[_ObserveInstance, OTELTestConfiguration]:
    config = OTELTestConfiguration(**kwargs)
    return _ObserveInstance("test-project", config), config


def _finished_span(config: OTELTestConfiguration, name: str) -> ReadableSpan:
    spans = [s for s in config.span_exporter.get_finished_spans() if s.name == name]
    assert len(spans) == 1
    return spans[0]


def _finished_logs(config: OTELTestConfiguration, body: str) -> list[LogData]:
    return [
        log
        for log in config.log_exporter.get_finished_logs()
        if log.log_record.body == body
    ]


def _data_point(config: OTELTestConfiguration, name: str):
    config.force_flush_metrics()
    data = config.metric_reader.get_metrics_data()
    for resource_metrics in data.resource_metrics:
        for scope_metrics in resource_metrics.scope_metrics:
            for metric in scope_metrics.metrics:
                if metric.name == name:
                    return metric.data.data_points[0]
    raise AssertionError(f"metric {name} not found")


class RecordingSpanProcessor(SpanProcessor):
    def __init__(self, name: str, calls: list[str]):
        self.name = name
        self.calls = calls
        self.seen_attributes: list[dict] = []
        self.flushed = False
        self.shut_down = False

    def on_start(
        self, span: Span, parent_context: typing.Optional[Context] = None
    ) -> None:
        self.calls.append(self.name)
        self.seen_attributes.append(dict(span.attributes or {}))
        span.set_attribute(f"processed.{self.name}", True)
        span.set_attribute("processed.last", self.name)

    def on_end(self, span: ReadableSpan) -> None:
        self.calls.append(f"{self.name}.end")

    def force_flush(self, timeout_millis: int = 30000) -> bool:
        self.flushed = True
        return True

    def shutdown(self) -> None:
        self.shut_down = True


class _Raising:
    def __init__(self):
        self.armed = True
        _raising_processors.append(self)

    def _raise(self, message: str):
        if self.armed:
            raise RuntimeError(message)


class RaisingSpanProcessor(_Raising, SpanProcessor):
    def on_start(
        self, span: Span, parent_context: typing.Optional[Context] = None
    ) -> None:
        self._raise("on_start failed")

    def on_end(self, span: ReadableSpan) -> None:
        self._raise("on_end failed")

    def force_flush(self, timeout_millis: int = 30000) -> bool:
        self._raise("force_flush failed")
        return True

    def shutdown(self) -> None:
        self._raise("shutdown failed")


class RecordingLogProcessor(LogRecordProcessor):
    def __init__(self, name: str, calls: list[str]):
        self.name = name
        self.calls = calls
        self.seen_attributes: list[dict] = []
        self.flushed = False
        self.shut_down = False

    def emit(self, log_data: LogData) -> None:
        self.calls.append(self.name)
        attributes = log_data.log_record.attributes
        self.seen_attributes.append(dict(attributes or {}))
        attributes[f"processed.{self.name}"] = True
        attributes["processed.last"] = self.name

    def force_flush(self, timeout_millis: int = 30000) -> bool:
        self.flushed = True
        return True

    def shutdown(self) -> None:
        self.shut_down = True


class RaisingLogProcessor(_Raising, LogRecordProcessor):
    def emit(self, log_data: LogData) -> None:
        self._raise("emit failed")

    def force_flush(self, timeout_millis: int = 30000) -> bool:
        self._raise("force_flush failed")
        return True

    def shutdown(self) -> None:
        self._raise("shutdown failed")


# Store


def test_set_attributes_merges_by_key():
    observe.set_attributes({"a": "1", "b": 2})
    observe.set_attributes({"b": 3, "c": True})

    assert dict(get_global_attributes()) == {"a": "1", "b": 3, "c": True}


def test_remove_attributes():
    observe.set_attributes({"a": "1", "b": 2, "c": 3.5})
    observe.remove_attributes(["a", "c", "missing"])

    assert dict(get_global_attributes()) == {"b": 2}

    observe.remove_attributes(["b"])
    assert dict(get_global_attributes()) == {}


def test_remove_attributes_accepts_a_single_key_string():
    observe.set_attributes({"tenant": "acme", "t": "x"})
    observe.remove_attributes("tenant")

    assert dict(get_global_attributes()) == {"t": "x"}


def test_snapshot_is_immutable_and_replaced_on_change():
    observe.set_attributes({"a": "1"})
    snapshot = get_global_attributes()

    with pytest.raises(TypeError):
        snapshot["b"] = "2"  # type: ignore[index]

    observe.set_attributes({"b": "2"})
    assert dict(snapshot) == {"a": "1"}
    assert dict(get_global_attributes()) == {"a": "1", "b": "2"}


def test_set_attributes_ignores_invalid_values():
    observe.set_attributes(
        {
            "valid": "ok",
            "list": ["x", "y"],
            "object": object(),  # type: ignore[dict-item]
            "mixed": ["x", 1],  # type: ignore[list-item]
            "": "empty key",
        }
    )

    assert dict(get_global_attributes()) == {"valid": "ok", "list": ("x", "y")}


def test_set_attributes_before_init_does_not_warn(caplog):
    assert not observe.is_initialized()
    with caplog.at_level(logging.WARNING):
        observe.set_attributes({"before.init": "yes"})
        observe.remove_attributes(["unknown"])

    assert "before it was initialized" not in caplog.text
    assert dict(get_global_attributes()) == {"before.init": "yes"}


def test_concurrent_set_and_remove():
    def writer(index: int):
        for i in range(200):
            observe.set_attributes({f"key.{index}": i})
            observe.remove_attributes([f"key.{index}"])
        observe.set_attributes({f"key.{index}": "final"})

    threads = [threading.Thread(target=writer, args=(i,)) for i in range(8)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()

    assert dict(get_global_attributes()) == {f"key.{i}": "final" for i in range(8)}


# Spans


def test_globals_set_before_init_apply_to_spans():
    observe.set_attributes({"tenant.id": "acme"})
    instance, config = _make_instance()

    with instance.start_span("manual"):
        pass

    assert _finished_span(config, "manual").attributes["tenant.id"] == "acme"


def test_span_attributes_win_over_globals():
    instance, config = _make_instance()
    observe.set_attributes({"shared": "global", "only.global": "g"})

    with instance.start_span("manual", attributes={"shared": "span"}):
        pass

    attributes = _finished_span(config, "manual").attributes
    assert attributes["shared"] == "span"
    assert attributes["only.global"] == "g"


def test_span_set_attribute_after_start_wins_over_globals():
    instance, config = _make_instance()
    observe.set_attributes({"shared": "global"})

    with instance.start_span("manual") as span:
        span.set_attribute("shared", "explicit")

    assert _finished_span(config, "manual").attributes["shared"] == "explicit"


def test_span_uses_globals_current_at_start():
    instance, config = _make_instance()
    observe.set_attributes({"phase": "start"})

    with instance.start_span("manual"):
        observe.set_attributes({"phase": "later", "added.later": True})
        observe.remove_attributes(["phase"])

    attributes = _finished_span(config, "manual").attributes
    assert attributes["phase"] == "start"
    assert "added.later" not in attributes


def test_instrumented_spans_get_globals():
    _, config = _make_instance()
    observe.set_attributes({"tenant.id": "acme"})

    # Instrumentation libraries obtain their own tracer from the provider.
    tracer = config._tracer_provider.get_tracer("opentelemetry.instrumentation.test")
    with tracer.start_as_current_span("instrumented"):
        pass

    assert _finished_span(config, "instrumented").attributes["tenant.id"] == "acme"


def test_exception_spans_get_globals():
    instance, config = _make_instance()
    observe.set_attributes({"tenant.id": "acme"})

    instance.record_exception(ValueError("boom"))

    assert (
        _finished_span(config, "launchdarkly.error").attributes["tenant.id"] == "acme"
    )


def test_globals_do_not_displace_span_attributes_at_capacity():
    instance, config = _make_instance()
    config._tracer_provider._span_limits.max_span_attributes = 2
    observe.set_attributes({"global.a": "a", "global.b": "b"})

    with instance.start_span("full", attributes={"x": 1}):
        pass

    attributes = _finished_span(config, "full").attributes
    assert attributes["x"] == 1
    assert len(attributes) == 2


# Logs


def test_record_log_gets_globals_and_call_attributes_win():
    instance, config = _make_instance()
    observe.set_attributes({"shared": "global", "tenant.id": "acme"})

    instance.log("hello", logging.WARNING, {"shared": "call"})

    logs = _finished_logs(config, "hello")
    assert len(logs) == 1
    attributes = logs[0].log_record.attributes
    assert attributes["shared"] == "call"
    assert attributes["tenant.id"] == "acme"


def test_stdlib_logging_records_get_globals():
    exporter = InMemoryLogExporter()
    provider = LoggerProvider()
    add_log_processors(provider, [], [SimpleLogRecordProcessor(exporter)])
    handler = LDLoggingHandler(level=logging.NOTSET, logger_provider=provider)
    app_logger = logging.getLogger("ldobserve.test.stdlib")
    app_logger.addHandler(handler)
    app_logger.setLevel(logging.INFO)
    app_logger.propagate = False
    observe.set_attributes({"tenant.id": "acme", "shared": "global"})

    try:
        app_logger.info("bridged", extra={"shared": "extra"})
    finally:
        app_logger.removeHandler(handler)

    logs = exporter.get_finished_logs()
    assert len(logs) == 1
    attributes = logs[0].log_record.attributes
    assert attributes["tenant.id"] == "acme"
    assert attributes["shared"] == "extra"


def test_globals_win_over_resource_attributes_on_logs():
    instance, config = _make_instance(service_name="resource-service")
    observe.set_attributes({"service.name": "global-service"})

    instance.log("resource", logging.WARNING)

    log = _finished_logs(config, "resource")[0].log_record
    assert log.attributes["service.name"] == "global-service"
    assert log.resource.attributes["service.name"] == "resource-service"


@pytest.mark.parametrize(
    "attributes",
    [None, BoundedAttributes(maxlen=10, attributes={"shared": "record"})],
    ids=["none", "immutable"],
)
def test_log_processor_replaces_missing_or_immutable_attributes(attributes):
    observe.set_attributes({"shared": "global", "tenant.id": "acme"})
    record = LogRecord(body="replaced")
    record.attributes = attributes
    log_data = LogData(record, InstrumentationScope("test"))

    GlobalAttributesLogRecordProcessor().emit(log_data)

    expected = {"shared": "global" if attributes is None else "record"}
    expected["tenant.id"] = "acme"
    assert dict(log_data.log_record.attributes) == expected
    assert log_data.log_record.dropped_attributes == 0


# Metrics


@pytest.mark.parametrize(
    "record",
    [
        lambda i, name, attrs: i.record_metric(name, 1.0, attrs),
        lambda i, name, attrs: i.record_count(name, 1, attrs),
        lambda i, name, attrs: i.record_incr(name, attrs),
        lambda i, name, attrs: i.record_histogram(name, 1.0, attrs),
        lambda i, name, attrs: i.record_up_down_counter(name, 1, attrs),
    ],
    ids=["gauge", "count", "incr", "histogram", "up_down_counter"],
)
def test_metrics_get_globals_and_call_attributes_win(record):
    instance, config = _make_instance()
    observe.set_attributes({"shared": "global", "tenant.id": "acme"})

    record(instance, "metric.with.attrs", {"shared": "call"})
    point = _data_point(config, "metric.with.attrs")
    assert dict(point.attributes) == {"shared": "call", "tenant.id": "acme"}


def test_metrics_without_call_attributes_get_globals():
    instance, config = _make_instance()
    observe.set_attributes({"tenant.id": "acme"})

    instance.record_count("metric.no.attrs", 1)

    point = _data_point(config, "metric.no.attrs")
    assert dict(point.attributes) == {"tenant.id": "acme"}


def test_metrics_without_globals_are_unchanged():
    instance, config = _make_instance()

    instance.record_count("metric.plain", 1)

    assert not _data_point(config, "metric.plain").attributes


# Custom processors


def test_span_processors_run_in_order_and_mutations_are_exported():
    calls: list[str] = []
    first = RecordingSpanProcessor("first", calls)
    second = RecordingSpanProcessor("second", calls)
    instance, config = _make_instance(span_processors=[first, second])
    observe.set_attributes({"tenant.id": "acme"})

    with instance.start_span("processed"):
        pass

    assert calls == ["first", "second", "first.end", "second.end"]
    # Global attributes are applied before user processors run.
    assert first.seen_attributes[0]["tenant.id"] == "acme"
    assert second.seen_attributes[0]["processed.first"] is True

    attributes = _finished_span(config, "processed").attributes
    assert attributes["processed.first"] is True
    assert attributes["processed.second"] is True
    assert attributes["processed.last"] == "second"
    assert attributes["tenant.id"] == "acme"


def test_raising_span_processor_does_not_break_export(caplog):
    calls: list[str] = []
    after = RecordingSpanProcessor("after", calls)
    instance, config = _make_instance(span_processors=[RaisingSpanProcessor(), after])

    with caplog.at_level(logging.ERROR):
        with instance.start_span("survives"):
            pass

    assert "raised in on_start" in caplog.text
    assert "raised in on_end" in caplog.text

    assert _finished_span(config, "survives").attributes["processed.after"] is True
    assert calls == ["after", "after.end"]


def test_span_processor_flush_and_shutdown_are_forwarded():
    calls: list[str] = []
    recording = RecordingSpanProcessor("recording", calls)
    _, config = _make_instance(span_processors=[RaisingSpanProcessor(), recording])

    assert config._tracer_provider.force_flush() is True
    assert recording.flushed

    config._tracer_provider.shutdown()
    assert recording.shut_down


class NoFlushSpanProcessor(SpanProcessor):
    """Relies on the base class force_flush, which returns None."""


def test_force_flush_reaches_export_processors():
    calls: list[str] = []
    export = RecordingSpanProcessor("export", calls)
    provider = TracerProvider()
    add_span_processors(provider, [NoFlushSpanProcessor()], [export])

    assert provider.force_flush() is True
    assert export.flushed


def test_log_processors_run_in_order_and_mutations_are_exported():
    calls: list[str] = []
    first = RecordingLogProcessor("first", calls)
    second = RecordingLogProcessor("second", calls)
    instance, config = _make_instance(log_processors=[first, second])
    observe.set_attributes({"tenant.id": "acme"})

    instance.log("processed", logging.WARNING)

    assert calls == ["first", "second"]
    assert first.seen_attributes[0]["tenant.id"] == "acme"
    assert second.seen_attributes[0]["processed.first"] is True

    attributes = _finished_logs(config, "processed")[0].log_record.attributes
    assert attributes["processed.first"] is True
    assert attributes["processed.second"] is True
    assert attributes["processed.last"] == "second"
    assert attributes["tenant.id"] == "acme"


def test_raising_log_processor_does_not_break_export(caplog):
    calls: list[str] = []
    after = RecordingLogProcessor("after", calls)
    instance, config = _make_instance(log_processors=[RaisingLogProcessor(), after])

    with caplog.at_level(logging.ERROR, logger="ldobserve._otel.processors"):
        instance.log("survives", logging.WARNING)

    assert "raised in emit" in caplog.text

    logs = _finished_logs(config, "survives")
    assert len(logs) == 1
    assert logs[0].log_record.attributes["processed.after"] is True
    assert calls == ["after"]


def test_log_processor_flush_and_shutdown_are_forwarded():
    calls: list[str] = []
    recording = RecordingLogProcessor("recording", calls)
    _, config = _make_instance(log_processors=[RaisingLogProcessor(), recording])

    assert config._logger_provider.force_flush() is True
    assert recording.flushed

    config._logger_provider.shutdown()
    assert recording.shut_down


# Configuration


def test_processed_config_defaults_to_no_processors():
    config = _ProcessedConfig(ObservabilityConfig())

    assert config.span_processors == []
    assert config.log_processors == []


def test_otel_configuration_wires_user_processors_before_export(mocker):
    span_exporter = InMemorySpanExporter()
    log_exporter = InMemoryLogExporter()
    mocker.patch.object(otel_configuration, "get_sampling_config")
    mocker.patch.object(otel_configuration, "OTLPMetricExporter")
    mocker.patch.object(otel_configuration, "PeriodicExportingMetricReader")
    mocker.patch.object(otel_configuration.otel_trace, "set_tracer_provider")
    mocker.patch.object(otel_configuration._logs, "set_logger_provider")
    mocker.patch.object(otel_configuration.metrics, "set_meter_provider")
    mocker.patch.object(
        otel_configuration,
        "BatchSpanProcessor",
        lambda _exporter: SimpleSpanProcessor(span_exporter),
    )
    mocker.patch.object(
        otel_configuration,
        "BatchLogRecordProcessor",
        lambda _exporter: SimpleLogRecordProcessor(log_exporter),
    )

    calls: list[str] = []
    span_processor = RecordingSpanProcessor("span", calls)
    log_processor = RecordingLogProcessor("log", calls)
    otel_config = otel_configuration._OTELConfiguration(
        "test-project",
        _ProcessedConfig(
            ObservabilityConfig(
                instrument_logging=False,
                span_processors=[span_processor],
                log_processors=[log_processor],
            )
        ),
    )
    observe.set_attributes({"tenant.id": "acme"})

    with otel_config.tracer.start_as_current_span("wired"):
        pass
    otel_config._logger_provider.get_logger("test").emit(LogRecord(body="wired"))

    span = span_exporter.get_finished_spans()[0]
    assert span.attributes["processed.span"] is True
    assert span.attributes["tenant.id"] == "acme"
    log = log_exporter.get_finished_logs()[0].log_record
    assert log.attributes["processed.log"] is True
    assert log.attributes["tenant.id"] == "acme"
    assert span_processor.seen_attributes[0]["tenant.id"] == "acme"
    assert log_processor.seen_attributes[0]["tenant.id"] == "acme"

    otel_config._tracer_provider.shutdown()
    otel_config._logger_provider.shutdown()
