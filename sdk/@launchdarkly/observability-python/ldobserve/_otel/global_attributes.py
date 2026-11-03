"""Process-wide global attributes applied to spans, logs, and metrics.

The store is a module-level, immutable snapshot that is rebuilt under a lock on
every change. Readers (span/log processors and metric recording) only read the
current snapshot reference, so they never contend with writers and never observe
a partially-applied update. The store is usable before the SDK is initialized.
"""

import threading
import types
import typing

from opentelemetry.attributes import BoundedAttributes, _clean_attribute
from opentelemetry.context import Context
from opentelemetry.sdk._logs import LogData, LogRecordProcessor
from opentelemetry.sdk.trace import Span, SpanProcessor
from opentelemetry.util.types import Attributes, AttributeValue

_lock = threading.Lock()
_attributes: typing.Mapping[str, AttributeValue] = types.MappingProxyType({})


def get_global_attributes() -> typing.Mapping[str, AttributeValue]:
    """Return the current immutable snapshot of the global attributes."""
    return _attributes


def set_global_attributes(attributes: typing.Mapping[str, AttributeValue]) -> None:
    global _attributes
    if not attributes:
        return

    cleaned: dict[str, AttributeValue] = {}
    for key, value in attributes.items():
        # Normalizes sequences to tuples; logs a warning for invalid keys/values.
        cleaned_value = _clean_attribute(key, value, None)
        if cleaned_value is None:
            continue
        cleaned[key] = typing.cast(AttributeValue, cleaned_value)

    if not cleaned:
        return

    with _lock:
        merged = dict(_attributes)
        merged.update(cleaned)
        _attributes = types.MappingProxyType(merged)


def remove_global_attributes(keys: typing.Iterable[str]) -> None:
    global _attributes
    if isinstance(keys, str):
        keys = (keys,)
    keys = tuple(keys)
    if not keys:
        return

    with _lock:
        if not any(key in _attributes for key in keys):
            return
        remaining = {k: v for k, v in _attributes.items() if k not in keys}
        _attributes = types.MappingProxyType(remaining)


def clear_global_attributes() -> None:
    global _attributes
    with _lock:
        _attributes = types.MappingProxyType({})


def merge_global_attributes(
    attributes: typing.Optional[Attributes],
) -> typing.Optional[Attributes]:
    """Return ``attributes`` with any missing global attributes filled in.

    Explicitly provided attributes always win over global attributes.
    """
    snapshot = _attributes
    if not snapshot:
        return attributes
    if not attributes:
        return dict(snapshot)
    merged = dict(snapshot)
    merged.update(attributes)
    return merged


def _missing_attributes(
    existing: typing.Optional[typing.Mapping[str, typing.Any]],
    snapshot: typing.Mapping[str, AttributeValue],
    maxlen: typing.Optional[int],
) -> dict[str, AttributeValue]:
    existing = existing or {}
    missing = {k: v for k, v in snapshot.items() if k not in existing}
    # BoundedAttributes evicts its oldest entry once full; only fill the
    # remaining capacity so global attributes never displace explicit ones.
    if maxlen is not None:
        capacity = max(0, maxlen - len(existing))
        if len(missing) > capacity:
            missing = dict(list(missing.items())[:capacity])
    return missing


class GlobalAttributesSpanProcessor(SpanProcessor):
    """Fills in global attributes that are not already set when a span starts."""

    def on_start(
        self, span: Span, parent_context: typing.Optional[Context] = None
    ) -> None:
        snapshot = _attributes
        if not snapshot:
            return
        # `Span.attributes` is a read-only view; the limit lives on the
        # underlying BoundedAttributes.
        maxlen = getattr(getattr(span, "_attributes", None), "maxlen", None)
        missing = _missing_attributes(span.attributes, snapshot, maxlen)
        if missing:
            span.set_attributes(missing)

    def force_flush(self, timeout_millis: int = 30000) -> bool:
        # The base implementation returns None, which the provider treats as a
        # failed flush and stops before reaching the exporting processors.
        return True


class GlobalAttributesLogRecordProcessor(LogRecordProcessor):
    """Fills in global attributes that are not already set on each log record."""

    def emit(self, log_data: LogData) -> None:
        snapshot = _attributes
        if not snapshot:
            return
        record = log_data.log_record
        existing = record.attributes
        missing = _missing_attributes(
            existing, snapshot, getattr(existing, "maxlen", None)
        )
        if not missing:
            return

        if isinstance(existing, BoundedAttributes) and not getattr(
            existing, "_immutable", False
        ):
            for key, value in missing.items():
                existing[key] = value
            return

        merged: dict[str, typing.Any] = dict(missing)
        if existing:
            merged.update(existing)
        record.attributes = BoundedAttributes(
            maxlen=getattr(existing, "maxlen", None),
            attributes=merged,
            immutable=False,
            max_value_len=getattr(existing, "max_value_len", None),
            extended_attributes=True,
        )

    def shutdown(self) -> None:
        pass

    def force_flush(self, timeout_millis: int = 30000) -> bool:
        return True
