"""A hardened OpenTelemetry logging handler.

The SDK attaches a :class:`~opentelemetry.sdk._logs.LoggingHandler` to the root
logger so that application logs are exported to LaunchDarkly. Because the handler
lives on the root logger it sees *every* log record in the process, including
records produced by other logging instrumentation (the New Relic agent, Datadog,
Sentry, ...) and records produced synchronously by our own export pipeline.

Two failure modes follow from that, both of which this handler defends against:

1. **Foreign record attributes.** Other logging instrumentation stashes
   arbitrary objects on the :class:`logging.LogRecord` (for example the New Relic
   agent leaves ``record._nr_original_message``, a function wrapper). When the
   stock handler forwards those attributes, OpenTelemetry's attribute validation
   rejects the non-serializable value and emits its own ``WARNING`` log. If
   another agent re-dispatches that warning back through the root logger, it is
   re-captured here, producing a new warning, and so on -- an unbounded feedback
   loop. We drop attribute values that are not valid OpenTelemetry types before
   they ever reach validation.

2. **Re-entrancy.** Any log emitted *while we are inside* ``emit`` (an attribute
   warning, an exporter error, a gRPC log) must not be captured again by this
   handler, or a single application log can recurse. A context-local guard makes
   re-entrant calls a no-op.

Together these make the handler safe to run alongside any other logging
instrumentation without disabling log capture.
"""

import contextvars
import logging
import typing

from opentelemetry.sdk._logs import LoggingHandler

# Set while this handler is emitting a record. Any log produced synchronously by
# the emit/export pipeline re-enters ``emit`` with this flag set and is dropped,
# which breaks otherwise-unbounded recursion. A module-level ContextVar (rather
# than per-instance state) also guards the case where more than one LD handler
# ends up on the root logger.
_in_emit: contextvars.ContextVar[bool] = contextvars.ContextVar(
    "ldobserve_in_emit", default=False
)

# Types OpenTelemetry accepts as attribute values (or sequences thereof).
_VALID_ATTRIBUTE_TYPES = (bool, bytes, int, float, str)


def _is_valid_attribute_value(value: typing.Any) -> bool:
    if isinstance(value, _VALID_ATTRIBUTE_TYPES):
        return True
    if isinstance(value, (list, tuple)):
        return all(isinstance(item, _VALID_ATTRIBUTE_TYPES) for item in value)
    return False


class LDLoggingHandler(LoggingHandler):
    """An :class:`~opentelemetry.sdk._logs.LoggingHandler` that is safe to
    install on the root logger alongside other logging instrumentation."""

    def emit(self, record: logging.LogRecord) -> None:
        # Re-entrancy guard: drop records produced while we are already emitting.
        if _in_emit.get():
            return
        token = _in_emit.set(True)
        try:
            super().emit(record)
        finally:
            _in_emit.reset(token)

    def _get_attributes(
        self, record: logging.LogRecord
    ) -> typing.Dict[str, typing.Any]:
        # Never forward foreign / non-serializable record attributes (e.g. the
        # New Relic agent's ``_nr_original_message`` function wrapper); they would
        # fail OpenTelemetry's attribute validation and trigger a warning that can
        # feed back into the root logger.
        attributes = super()._get_attributes(record)
        return {
            key: value
            for key, value in attributes.items()
            if _is_valid_attribute_value(value)
        }


def install_on_root_logger(handler: LDLoggingHandler) -> None:
    """Install ``handler`` on the root logger, idempotently.

    Any previously installed :class:`LDLoggingHandler` is removed first, so that
    repeated SDK initialization in the same process never stacks duplicate
    handlers on the root logger (which would double-export every log record).
    """
    root_logger = logging.getLogger()
    for existing in list(root_logger.handlers):
        if isinstance(existing, LDLoggingHandler):
            root_logger.removeHandler(existing)
    root_logger.addHandler(handler)
