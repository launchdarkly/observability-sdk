import json
import os

from ldobserve._source_context import (
    _CONTEXT_LINES,
    _MAX_FRAMES,
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
        _recurse(_MAX_FRAMES + 10)
    except RuntimeError as error:
        frames = _frames(error)

    assert len(frames) == _MAX_FRAMES
    # Innermost first, so the raise survives the cap and this test's own frame
    # (the outermost one) is what gets dropped.
    assert frames[0]["lineContent"].strip() == 'raise RuntimeError("deep")'
    assert all(frame["functionName"] == "_recurse" for frame in frames)


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
