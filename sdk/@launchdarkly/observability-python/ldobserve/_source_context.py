"""Source context for recorded exceptions.

A Python traceback carries exactly one source line per frame -- whatever
``traceback.format_exception`` chose to print -- so an error recorded with only
the standard ``exception.stacktrace`` attribute can never show more than that
single line, no matter what the errors UI is willing to render.

This module reads the surrounding lines off disk at capture time and reports them
as ``exception.structured_stacktrace``: a JSON array of frames, innermost first,
each carrying ``linesBefore`` / ``lineContent`` / ``linesAfter``. Ingestion
prefers that attribute over the raw text stacktrace, so the frames land in the
errors UI as-is -- and so the whole exception chain has to be described here,
because the text form that would otherwise have carried the root cause is no
longer read. The Ruby SDK reports the same attribute in the same shape.

Everything here is best-effort. Frames whose source is not on the running host
(``exec``-compiled code, source-stripped or frozen bundles, bytecode compiled at
a different path) simply report no context, which is the behavior callers already
get today. No failure in this module is allowed to interfere with recording the
exception itself.
"""

import json
import linecache
import traceback
import types
import typing

# Lines of context on each side of the reported line. Matches the window the
# backend produces for every other language it symbolicates.
_CONTEXT_LINES = 5

# Deepest frames reported for each exception in the chain, so that a deep stack
# on the raised exception cannot crowd out its root cause entirely.
_MAX_FRAMES_PER_EXCEPTION = 20

# Total frames reported. Matches the backend's own frame cap: it keeps the
# leading frames (the innermost ones, see _structured_stacktrace) and drops the
# rest, so anything past this would be discarded on arrival.
_MAX_FRAMES = 64

# Per-line cap. The backend trims each field to the same length, so anything
# longer is bytes on the wire that would be discarded on arrival.
_MAX_LINE_LENGTH = 1000

_STRUCTURED_STACKTRACE_ATTRIBUTE = "exception.structured_stacktrace"


def exception_attributes(error: BaseException) -> typing.Dict[str, str]:
    """Build the span attributes describing ``error``'s frames and source.

    Returns an empty dict when no structured stacktrace can be built, so the
    result is safe to merge directly into an attribute dict.
    """
    try:
        frames = _structured_stacktrace(error)
        if not frames:
            return {}
        return {_STRUCTURED_STACKTRACE_ATTRIBUTE: json.dumps(frames)}
    except Exception:
        # Source context is a nicety; recording the exception is not. Any failure
        # here (unreadable file, unexpected frame shape, non-serializable value)
        # degrades to the plain text stacktrace.
        return {}


def _structured_stacktrace(
    error: BaseException,
) -> typing.Optional[typing.List[typing.Dict[str, typing.Any]]]:
    collected: typing.List[typing.Tuple[types.FrameType, int, str]] = []
    for exception in _exception_chain(error):
        message = _error_message(exception)
        frames = list(_walk_traceback(exception.__traceback__))
        collected.extend(
            (frame, lineno, message)
            for frame, lineno in frames[-_MAX_FRAMES_PER_EXCEPTION:]
        )
    if not collected:
        return None

    # ``collected`` now runs the way a traceback prints: root cause first, each
    # exception outermost -> innermost. The backend stores and displays the
    # reverse of that (it reverses the parsed text form to get there), so flip
    # the whole list. The raise site of the final exception leads, root cause
    # frames trail, and the backend's tail truncation drops the least relevant
    # frames rather than the most relevant ones.
    collected.reverse()
    return [
        _build_frame(frame, lineno, message)
        for frame, lineno, message in collected[:_MAX_FRAMES]
    ]


def _exception_chain(error: BaseException) -> typing.List[BaseException]:
    """The exceptions to describe, root cause first.

    An exception raised while handling another one carries the first as its
    ``__cause__`` (explicit ``raise ... from``) or ``__context__`` (implicit),
    and ``traceback.format_exception`` prints the whole chain. Ingestion prefers
    the structured stacktrace over that text, so leaving the chain out here would
    drop the root cause frames from the errors UI entirely.
    """
    chain: typing.List[BaseException] = []
    seen: typing.Set[int] = set()
    current: typing.Optional[BaseException] = error
    while current is not None and id(current) not in seen:
        seen.add(id(current))
        chain.append(current)
        current = _chained_cause(current)
    # Printed order: deepest cause first, the exception that was raised last.
    chain.reverse()
    return chain


def _chained_cause(error: BaseException) -> typing.Optional[BaseException]:
    # The same precedence ``traceback`` applies: an explicit "raise ... from"
    # wins, and an implicit context is skipped once "from None" suppressed it.
    if error.__cause__ is not None:
        return error.__cause__
    if error.__context__ is not None and not error.__suppress_context__:
        return error.__context__
    return None


def _walk_traceback(
    tb: typing.Optional[types.TracebackType],
) -> typing.Iterator[typing.Tuple[types.FrameType, int]]:
    while tb is not None:
        # tb_lineno, not frame.f_lineno: the former is where this frame was
        # executing when the exception passed through it, the latter is wherever
        # the frame is now.
        yield tb.tb_frame, tb.tb_lineno
        tb = tb.tb_next


def _build_frame(
    frame: types.FrameType, lineno: int, message: str
) -> typing.Dict[str, typing.Any]:
    code = frame.f_code
    built: typing.Dict[str, typing.Any] = {
        "fileName": code.co_filename,
        "lineNumber": lineno,
        "functionName": code.co_name,
        "error": message,
    }
    context = _read_source_context(frame, lineno)
    if context:
        built.update(context)
    return built


def _read_source_context(
    frame: types.FrameType, lineno: int
) -> typing.Optional[typing.Dict[str, str]]:
    if lineno <= 0:
        return None

    lines = _file_lines(frame)
    if not lines or lineno > len(lines):
        return None

    index = lineno - 1
    context = {"lineContent": _clean_line(lines[index])}
    before = [
        _clean_line(line) for line in lines[max(0, index - _CONTEXT_LINES) : index]
    ]
    if before:
        context["linesBefore"] = "\n".join(before)
    after = [
        _clean_line(line) for line in lines[index + 1 : index + 1 + _CONTEXT_LINES]
    ]
    if after:
        context["linesAfter"] = "\n".join(after)
    return context


def _file_lines(frame: types.FrameType) -> typing.List[str]:
    """Return the raw source lines of ``frame``'s file, or an empty list.

    ``linecache.checkcache`` is deliberately not called. Leaving the cache alone
    keeps the text captured when the module was imported, so a file rewritten
    under a long-running process (an in-place deploy, a dev reloader) cannot pair
    an old frame's line numbers with the new file's contents and report source
    that never raised anything.
    """
    lines = linecache.getlines(frame.f_code.co_filename)
    if lines:
        return lines
    return _loader_lines(frame)


def _loader_lines(frame: types.FrameType) -> typing.List[str]:
    """Source for frames whose file cannot be opened by path but whose module can
    still produce it -- code imported from an archive (zipapp, PEX, shiv), where
    ``co_filename`` names a path *inside* the zip.
    """
    loader = frame.f_globals.get("__loader__")
    module = frame.f_globals.get("__name__")
    if loader is None or module is None or not hasattr(loader, "get_source"):
        return []
    try:
        source = loader.get_source(module)
    except Exception:
        return []
    if not source:
        return []
    return source.splitlines()


def _clean_line(line: str) -> str:
    # Strip only the line terminator: leading whitespace is indentation, and the
    # rendered preview needs it.
    return line.rstrip("\r\n")[:_MAX_LINE_LENGTH]


def _error_message(error: BaseException) -> str:
    """The exception's type and message, as the trailing line of a traceback."""
    try:
        formatted = traceback.format_exception_only(type(error), error)
    except Exception:
        formatted = []
    if formatted:
        return formatted[-1].strip()
    return str(error)
