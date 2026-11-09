"""Processor pipeline assembly and isolation of user-provided processors.

Pipeline order for both spans and logs is:

1. The global attributes processor.
2. User processors, in the order they were configured.
3. The SDK's exporting processors.

User processors are wrapped so an exception raised by one of them is logged and
never prevents the remaining processors (including export) from running.
"""

import logging
import typing

from opentelemetry.context import Context
from opentelemetry.sdk._logs import LogData, LoggerProvider, LogRecordProcessor
from opentelemetry.sdk.trace import ReadableSpan, Span, SpanProcessor, TracerProvider

from ldobserve._otel.global_attributes import (
    GlobalAttributesLogRecordProcessor,
    GlobalAttributesSpanProcessor,
)

_logger = logging.getLogger(__name__)


class SafeSpanProcessor(SpanProcessor):
    """Delegates to a user span processor, logging instead of raising."""

    def __init__(self, processor: SpanProcessor):
        self._processor = processor

    def on_start(
        self, span: Span, parent_context: typing.Optional[Context] = None
    ) -> None:
        try:
            self._processor.on_start(span, parent_context=parent_context)
        except Exception:
            _logger.exception("Span processor %r raised in on_start", self._processor)

    def on_end(self, span: ReadableSpan) -> None:
        try:
            self._processor.on_end(span)
        except Exception:
            _logger.exception("Span processor %r raised in on_end", self._processor)

    def shutdown(self) -> None:
        try:
            self._processor.shutdown()
        except Exception:
            _logger.exception("Span processor %r raised in shutdown", self._processor)

    def force_flush(self, timeout_millis: int = 30000) -> bool:
        try:
            # Processors that don't implement force_flush return None; only an
            # explicit False is a failed flush.
            return self._processor.force_flush(timeout_millis) is not False
        except Exception:
            _logger.exception(
                "Span processor %r raised in force_flush", self._processor
            )
            # The provider stops flushing at the first False; keep going so the
            # exporting processors still flush.
            return True


class SafeLogRecordProcessor(LogRecordProcessor):
    """Delegates to a user log record processor, logging instead of raising."""

    def __init__(self, processor: LogRecordProcessor):
        self._processor = processor

    def emit(self, log_data: LogData) -> None:
        try:
            self._processor.emit(log_data)
        except Exception:
            _logger.exception("Log processor %r raised in emit", self._processor)

    def shutdown(self) -> None:
        try:
            self._processor.shutdown()
        except Exception:
            _logger.exception("Log processor %r raised in shutdown", self._processor)

    def force_flush(self, timeout_millis: int = 30000) -> bool:
        try:
            # Processors that don't implement force_flush return None; only an
            # explicit False is a failed flush.
            return self._processor.force_flush(timeout_millis) is not False
        except Exception:
            _logger.exception("Log processor %r raised in force_flush", self._processor)
            # The provider stops flushing at the first False; keep going so the
            # exporting processors still flush.
            return True


def add_span_processors(
    tracer_provider: TracerProvider,
    user_processors: typing.Sequence[SpanProcessor],
    export_processors: typing.Sequence[SpanProcessor],
) -> None:
    tracer_provider.add_span_processor(GlobalAttributesSpanProcessor())
    for processor in user_processors:
        tracer_provider.add_span_processor(SafeSpanProcessor(processor))
    for processor in export_processors:
        tracer_provider.add_span_processor(processor)


def add_log_processors(
    logger_provider: LoggerProvider,
    user_processors: typing.Sequence[LogRecordProcessor],
    export_processors: typing.Sequence[LogRecordProcessor],
) -> None:
    logger_provider.add_log_record_processor(GlobalAttributesLogRecordProcessor())
    for processor in user_processors:
        logger_provider.add_log_record_processor(SafeLogRecordProcessor(processor))
    for processor in export_processors:
        logger_provider.add_log_record_processor(processor)
