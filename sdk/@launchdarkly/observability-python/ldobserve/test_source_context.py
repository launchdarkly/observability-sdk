import json
import os
import sys

import pytest

from ldobserve._source_context import (
    _CONTEXT_LINES,
    _MAX_FRAMES,
    _MAX_FRAMES_PER_EXCEPTION,
    _MAX_LINE_LENGTH,
    _STRUCTURED_STACKTRACE_ATTRIBUTE,
    exception_attributes,
)


def _frames(error: BaseException):
    """The structured frames reported for ``error``."""
    attributes = exception_attributes(error)
    assert _STRUCTURED_STACKTRACE_ATTRIBUTE in attributes
    return json.loads(attributes[_STRUCTURED_STACKTRACE_ATTRIBUTE])


def _raise_with_context():
    important_value = 42
    doubled = important_value * 2
    raise ValueError("context failure")


def test_includes_source_context():
    try:
        _raise_with_context()
    except ValueError as error:
        frames = _frames(error)

    innermost = frames[0]
    assert innermost["functionName"] == "_raise_with_context"
    assert os.path.basename(innermost["fileName"]) == "test_source_context.py"
    assert innermost["lineContent"].strip() == 'raise ValueError("context failure")'
    assert "important_value = 42" in innermost["linesBefore"]
    assert innermost["error"] == "ValueError: context failure"


def test_frames_are_innermost_first():
    try:
        _raise_with_context()
    except ValueError as error:
        frames = _frames(error)

    assert [frame["functionName"] for frame in frames] == [
        "_raise_with_context",
        "test_frames_are_innermost_first",
    ]


def test_line_content_keeps_indentation():
    try:
        _raise_with_context()
    except ValueError as error:
        frames = _frames(error)

    # The preview is rendered as code, so leading whitespace has to survive.
    assert frames[0]["lineContent"].startswith("    ")


def _raise_in_padded_region():
    first = 1
    second = 2
    third = 3
    fourth = 4
    fifth = 5
    raise ValueError("padded")


def test_context_window_is_bounded():
    try:
        _raise_in_padded_region()
    except ValueError as error:
        frames = _frames(error)

    innermost = frames[0]
    assert len(innermost["linesBefore"].splitlines()) == _CONTEXT_LINES
    assert len(innermost["linesAfter"].splitlines()) == _CONTEXT_LINES
    assert innermost["linesBefore"].splitlines()[0].strip() == "first = 1"


def _recurse(depth: int):
    if depth == 0:
        raise RuntimeError("deep")
    _recurse(depth - 1)


def test_limits_frame_count_keeping_deepest():
    try:
        _recurse(_MAX_FRAMES_PER_EXCEPTION + 10)
    except RuntimeError as error:
        frames = _frames(error)

    assert len(frames) == _MAX_FRAMES_PER_EXCEPTION
    # Innermost first, so the raise survives the cap and this test's own frame
    # (the outermost one) is what gets dropped.
    assert frames[0]["lineContent"].strip() == 'raise RuntimeError("deep")'
    assert all(frame["functionName"] == "_recurse" for frame in frames)


def _raise_root_cause():
    marker = "root cause marker"
    raise KeyError("root cause")


def test_explicit_cause_frames_are_reported():
    try:
        try:
            _raise_root_cause()
        except KeyError as cause:
            raise ValueError("wrapper") from cause
    except ValueError as error:
        frames = _frames(error)

    # The raised exception leads, innermost first; the cause's frames trail it.
    assert frames[0]["functionName"] == "test_explicit_cause_frames_are_reported"
    assert frames[0]["lineContent"].strip() == 'raise ValueError("wrapper") from cause'
    assert [frame["functionName"] for frame in frames[1:]] == [
        "_raise_root_cause",
        "test_explicit_cause_frames_are_reported",
    ]
    assert frames[1]["lineContent"].strip() == 'raise KeyError("root cause")'
    assert "root cause marker" in frames[1]["linesBefore"]


def test_each_frame_carries_its_own_exception_message():
    try:
        try:
            _raise_root_cause()
        except KeyError as cause:
            raise ValueError("wrapper") from cause
    except ValueError as error:
        frames = _frames(error)

    assert frames[0]["error"] == "ValueError: wrapper"
    assert frames[1]["error"] == "KeyError: 'root cause'"


def test_implicit_context_frames_are_reported():
    try:
        try:
            _raise_root_cause()
        except KeyError:
            raise ValueError("during handling")
    except ValueError as error:
        frames = _frames(error)

    assert [frame["functionName"] for frame in frames] == [
        "test_implicit_context_frames_are_reported",
        "_raise_root_cause",
        "test_implicit_context_frames_are_reported",
    ]


def test_suppressed_context_is_not_reported():
    try:
        try:
            _raise_root_cause()
        except KeyError:
            raise ValueError("standalone") from None
    except ValueError as error:
        frames = _frames(error)

    # "from None" means the context is deliberately hidden, as in a traceback.
    assert [frame["functionName"] for frame in frames] == [
        "test_suppressed_context_is_not_reported"
    ]


def test_chain_frame_count_is_capped_in_total():
    try:
        try:
            _recurse(_MAX_FRAMES)
        except RuntimeError as cause:
            raise ValueError("wrapper") from cause
    except ValueError as error:
        frames = _frames(error)

    assert len(frames) <= _MAX_FRAMES
    # The cause is capped per exception, so it still gets its deepest frames in
    # rather than being crowded out by the raised exception's stack.
    assert any(frame["functionName"] == "_recurse" for frame in frames)


def test_cyclic_exception_chain_terminates():
    error = ValueError("self-referential")
    try:
        raise error
    except ValueError:
        pass
    # A cycle cannot arise from normal raising, but the attributes are writable
    # and a walk that trusted them would not terminate.
    error.__context__ = error

    frames = _frames(error)
    assert [frame["functionName"] for frame in frames] == [
        "test_cyclic_exception_chain_terminates"
    ]


def test_missing_file_reports_frame_without_source():
    source = "def explode():\n    raise RuntimeError('missing file')\n"
    namespace: dict = {}
    exec(compile(source, "/definitely/missing/file.py", "exec"), namespace)

    try:
        namespace["explode"]()
    except RuntimeError as error:
        frames = _frames(error)

    innermost = frames[0]
    assert innermost["fileName"] == "/definitely/missing/file.py"
    assert innermost["lineNumber"] == 2
    assert innermost["functionName"] == "explode"
    assert "lineContent" not in innermost
    assert "linesBefore" not in innermost
    assert "linesAfter" not in innermost


def test_truncates_long_source_lines(tmp_path):
    long_line = "padding = '" + "y" * (_MAX_LINE_LENGTH * 2) + "'"
    module = tmp_path / "long_lines.py"
    module.write_text(
        "def explode():\n" f"    {long_line}\n" "    raise RuntimeError('long')\n"
    )

    namespace: dict = {}
    exec(compile(module.read_text(), str(module), "exec"), namespace)

    try:
        namespace["explode"]()
    except RuntimeError as error:
        frames = _frames(error)

    innermost = frames[0]
    assert len(innermost["lineContent"]) <= _MAX_LINE_LENGTH
    assert innermost["linesBefore"]
    for line in innermost["linesBefore"].splitlines():
        assert len(line) <= _MAX_LINE_LENGTH


def test_exception_without_traceback_reports_nothing():
    # Never raised, so there are no frames to describe.
    assert exception_attributes(ValueError("unraised")) == {}


def test_error_message_uses_exception_type_and_message():
    try:
        raise KeyError("missing-key")
    except KeyError as error:
        frames = _frames(error)

    assert frames[0]["error"] == "KeyError: 'missing-key'"


def test_syntax_error_message_survives_its_source_preview():
    try:
        compile("x ===\n", "<string>", "exec")
    except SyntaxError as error:
        frames = _frames(error)

    # A SyntaxError is formatted with its file, source and caret before the
    # message, so the message is not the first line printed.
    assert frames[0]["error"].startswith("SyntaxError:")


@pytest.mark.skipif(
    sys.version_info < (3, 11), reason="add_note was added in Python 3.11"
)
def test_note_does_not_replace_the_exception_message():
    try:
        raise KeyError("noted")
    except KeyError as error:
        error.add_note("worker was retrying the config load")
        frames = _frames(error)

    # Notes are formatted after the message, so the message is not the last line
    # printed either.
    assert frames[0]["error"] == "KeyError: 'noted'"


def _two_failures():
    """Two exceptions that have been raised, ready to be put in a group."""
    caught = []
    for raiser in (_raise_root_cause, _raise_with_context):
        try:
            raiser()
        except (KeyError, ValueError) as error:
            caught.append(error)
    return caught


@pytest.mark.skipif(
    sys.version_info < (3, 11), reason="exception groups were added in Python 3.11"
)
def test_exception_group_members_are_reported():
    try:
        raise ExceptionGroup("worker failures", _two_failures())
    except BaseException as error:
        frames = _frames(error)

    functions = [frame["functionName"] for frame in frames]
    assert "_raise_root_cause" in functions
    assert "_raise_with_context" in functions

    # A member's frames carry that member's message, not the group's.
    messages = {frame["error"] for frame in frames}
    assert "KeyError: 'root cause'" in messages
    assert "ValueError: context failure" in messages
    assert any(message.startswith("ExceptionGroup:") for message in messages)


@pytest.mark.skipif(
    sys.version_info < (3, 11), reason="exception groups were added in Python 3.11"
)
def test_exception_group_members_report_source_context():
    try:
        raise ExceptionGroup("worker failures", _two_failures())
    except BaseException as error:
        frames = _frames(error)

    member = next(
        frame for frame in frames if frame["functionName"] == "_raise_root_cause"
    )
    assert member["lineContent"].strip() == 'raise KeyError("root cause")'
    assert "root cause marker" in member["linesBefore"]


@pytest.mark.skipif(
    sys.version_info < (3, 11), reason="exception groups were added in Python 3.11"
)
def test_nested_exception_group_members_are_reported():
    inner = ExceptionGroup("inner failures", _two_failures())
    try:
        raise ExceptionGroup("outer failures", [inner])
    except BaseException as error:
        frames = _frames(error)

    functions = [frame["functionName"] for frame in frames]
    assert "_raise_root_cause" in functions
    assert "_raise_with_context" in functions


class _BackportedGroup(Exception):
    """A group as the ``exceptiongroup`` backport presents one.

    That backport is how anyio and trio raise groups on 3.10, where the builtin
    does not exist, and it is why members are found by shape.
    """

    def __init__(self, message, exceptions):
        super().__init__(message)
        self.exceptions = tuple(exceptions)


def test_group_members_are_found_by_shape():
    try:
        raise _BackportedGroup("worker failures", _two_failures())
    except _BackportedGroup as error:
        frames = _frames(error)

    functions = [frame["functionName"] for frame in frames]
    assert "_raise_root_cause" in functions
    assert "_raise_with_context" in functions


def test_group_member_count_is_capped_in_total():
    members = _two_failures() * 100
    try:
        raise _BackportedGroup("many failures", members)
    except _BackportedGroup as error:
        frames = _frames(error)

    assert len(frames) <= _MAX_FRAMES


def test_cyclic_group_membership_terminates():
    try:
        raise _BackportedGroup("self-referential", [])
    except _BackportedGroup as error:
        group = error
    # A group cannot normally contain itself, but the attribute is writable and a
    # walk that trusted it would not terminate.
    group.exceptions = (group,)

    frames = _frames(group)
    assert [frame["functionName"] for frame in frames] == [
        "test_cyclic_group_membership_terminates"
    ]


def test_non_group_exceptions_attribute_is_ignored():
    class Confusing(Exception):
        exceptions = "not a group at all"

    try:
        raise Confusing("just an exception")
    except Confusing as error:
        frames = _frames(error)

    assert [frame["functionName"] for frame in frames] == [
        "test_non_group_exceptions_attribute_is_ignored"
    ]
