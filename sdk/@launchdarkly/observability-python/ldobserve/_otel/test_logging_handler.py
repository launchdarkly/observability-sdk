import logging

import pytest
from opentelemetry.sdk._logs import LoggerProvider
from opentelemetry.sdk._logs.export import (
    InMemoryLogExporter,
    SimpleLogRecordProcessor,
)

from ldobserve._otel.logging_handler import (
    LDLoggingHandler,
    install_on_root_logger,
)


def _make_handler(provider: LoggerProvider) -> LDLoggingHandler:
    return LDLoggingHandler(level=logging.NOTSET, logger_provider=provider)


def test_get_attributes_drops_non_serializable_values():
    """Foreign / non-serializable record attributes (e.g. the New Relic agent's
    ``_nr_original_message`` function wrapper) must not be forwarded -- they fail
    OpenTelemetry attribute validation and seed a logging feedback loop."""
    handler = _make_handler(LoggerProvider())

    record = logging.LogRecord(
        name="app",
        level=logging.WARNING,
        pathname=__file__,
        lineno=1,
        msg="hello",
        args=None,
        exc_info=None,
    )
    # mimic the attributes a coexisting logging instrumentation leaves behind
    record._nr_original_message = lambda: "wrapped"  # function wrapper
    record.some_object = object()
    record.ok_str = "value"
    record.ok_int = 7
    record.ok_seq = ["a", "b"]

    attributes = handler._get_attributes(record)

    assert "_nr_original_message" not in attributes
    assert "some_object" not in attributes
    assert attributes["ok_str"] == "value"
    assert attributes["ok_int"] == 7
    assert attributes["ok_seq"] == ["a", "b"]


def test_emit_is_not_reentrant():
    """A log produced synchronously by the export pipeline must not be captured
    again by the handler, otherwise a single application log recurses."""
    exporter = InMemoryLogExporter()
    provider = LoggerProvider()
    provider.add_log_record_processor(SimpleLogRecordProcessor(exporter))
    handler = _make_handler(provider)

    export_calls = {"count": 0}
    original_export = exporter.export

    def logging_export(batch):
        # Simulate a pipeline component that logs synchronously during export
        # (the real OTLP exporter does exactly this on connection errors).
        export_calls["count"] += 1
        logging.getLogger("pipeline.exporter").warning(
            "synchronous export log %d", export_calls["count"]
        )
        return original_export(batch)

    exporter.export = logging_export

    root = logging.getLogger()
    install_on_root_logger(handler)
    old_level = root.level
    root.setLevel(logging.DEBUG)
    try:
        logging.getLogger("app").warning("application log line")
    finally:
        root.setLevel(old_level)
        root.removeHandler(handler)

    # Without the re-entrancy guard the synchronous export log re-enters emit and
    # recurses unboundedly. With it, only the bounded set of distinct records is
    # exported. The exact count is small and finite -- never a runaway loop.
    assert export_calls["count"] <= 3
    assert exporter.export.__name__ == "logging_export"


def test_install_on_root_logger_is_idempotent():
    """Repeated installation must not stack duplicate LD handlers on the root
    logger (which would double-export every record)."""
    root = logging.getLogger()
    before = [h for h in root.handlers if isinstance(h, LDLoggingHandler)]
    handlers = [_make_handler(LoggerProvider()) for _ in range(3)]
    try:
        for handler in handlers:
            install_on_root_logger(handler)
        ld_handlers = [h for h in root.handlers if isinstance(h, LDLoggingHandler)]
        # exactly one LD handler remains -- the most recently installed one
        assert len(ld_handlers) == len(before) + 1
        assert ld_handlers[-1] is handlers[-1]
    finally:
        for handler in handlers:
            root.removeHandler(handler)
