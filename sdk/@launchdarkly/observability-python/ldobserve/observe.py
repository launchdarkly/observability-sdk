import contextlib
import typing
from ldobserve.request_context import RequestContext
from opentelemetry.context import Context
from opentelemetry.trace import Span, Tracer
import opentelemetry.trace as trace
from opentelemetry.util.types import Attributes
from ldobserve.utils.lru_cache import _LRUCache
from opentelemetry._logs import LogRecord, get_logger

_name = "launchdarkly-observability"


class _ObserveInstance:
    _project_id: str
    _tracer: Tracer
    # TODO: Comments. Original implementation had some comments which I am not certain hold true.
    _context_map = _LRUCache(1000)
    _logger = get_logger(_name)

    def __init__(self, project_id: str):
        self._project_id = project_id
        self._tracer = trace.get_tracer(_name)


_instance: typing.Optional[_ObserveInstance] = None


def record_exception(error: Exception, attributes: typing.Optional[Attributes] = None):
    pass


def record_metric(
    name: str, value: float, attributes: typing.Optional[Attributes] = None
):
    pass


def record_count(name: str, value: int, attributes: typing.Optional[Attributes] = None):
    pass


def record_incr(name: str, attributes: typing.Optional[Attributes] = None):
    pass


def record_histogram(
    name: str, value: float, attributes: typing.Optional[Attributes] = None
):
    pass


def record_up_down_counter(
    name: str, value: int, attributes: typing.Optional[Attributes] = None
):
    pass


def is_initialized() -> bool:
    return False


def record_log(
    message: str,
    level: str,
    secureSessionId: typing.Optional[str] = None,
    requestId: typing.Optional[str] = None,
    attributes: typing.Optional[Attributes] = None,
):
    if not _instance:
        # TODO: Log usage error.
        return

    # TODO: Implement.
    # _instance._logger.emit(LogRecord(
    #     timestamp=datetime.now(),
    #     level=level,
    #     message=message,
    #     attributes=attributes,
    # ))


@contextlib.contextmanager
def start_span(
    span_name: str,
    session_id: typing.Optional[str] = None,
    request_id: typing.Optional[str] = None,
    attributes: typing.Optional[Attributes] = None,
    context: typing.Optional[Context] = None,
) -> typing.Iterator[Span]:
    if not _instance:
        # TODO: Log usage error.
        return

    with _instance._tracer.start_as_current_span(
        span_name,
        record_exception=False,
        set_status_on_exception=False,
        context=context,
    ) as span:
        if attributes:
            span.set_attributes(attributes)

        span.set_attributes({"highlight.project_id": _instance._project_id})

        if session_id:
            span.set_attributes({"highlight.session_id": session_id})
        if request_id:
            span.set_attributes({"highlight.trace_id": request_id})

        _instance._context_map.put(
            span.get_span_context().trace_id, (session_id, request_id)
        )

        try:
            yield span
        except Exception as e:
            record_exception(e, attributes=attributes)
            raise
