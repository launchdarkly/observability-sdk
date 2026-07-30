package ldobserve

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/pkg/errors"
)

// failInHelper returns an error carrying the stack of the line below, so that a
// frame under test points at source this test can predict.
func failInHelper() error {
	return errors.New("boom")
}

func framesFrom(t *testing.T, err error, callers []uintptr) []structuredFrame {
	t.Helper()

	attr, ok := structuredStacktraceAttribute(structuredFrames(callers, err.Error()))
	if !ok {
		t.Fatal("expected a structured stack trace")
	}
	var frames []structuredFrame
	if err := json.Unmarshal([]byte(attr.Value.AsString()), &frames); err != nil {
		t.Fatalf("could not decode frames: %v", err)
	}
	if len(frames) == 0 {
		t.Fatal("expected at least one frame")
	}
	return frames
}

func numberedFile(t *testing.T, lines int) string {
	t.Helper()

	var content strings.Builder
	for line := 1; line <= lines; line++ {
		fmt.Fprintf(&content, "line %d\n", line)
	}
	path := filepath.Join(t.TempDir(), "source.go")
	if err := os.WriteFile(path, []byte(content.String()), 0o600); err != nil {
		t.Fatalf("could not write source: %v", err)
	}
	return path
}

func TestStructuredStacktraceUsesTheStackTheErrorCarries(t *testing.T) {
	err := failInHelper()
	stack, ok := stackTraceOf(err)
	if !ok {
		t.Fatal("expected the error to carry a stack")
	}

	frames := framesFrom(t, err, callersFromStackTrace(stack))

	// The stack was captured where the error was created, so the innermost frame
	// is the helper rather than this test.
	if !strings.HasSuffix(frames[0].FunctionName, ".failInHelper") {
		t.Errorf("expected the innermost frame to be failInHelper, got %q", frames[0].FunctionName)
	}
	if !strings.HasSuffix(frames[0].FileName, "stacktrace_test.go") {
		t.Errorf("expected the frame to point at this file, got %q", frames[0].FileName)
	}
	if frames[0].Error != "boom" {
		t.Errorf("expected the frame to carry the error message, got %q", frames[0].Error)
	}
	if len(frames) < 2 {
		t.Fatalf("expected the caller to be reported too, got %d frame(s)", len(frames))
	}
	if !strings.HasSuffix(frames[1].FunctionName, ".TestStructuredStacktraceUsesTheStackTheErrorCarries") {
		t.Errorf("expected the caller as the second frame, got %q", frames[1].FunctionName)
	}
}

// Wrapping captures a stack at every wrap, and only the innermost one reaches
// the call that failed. Reporting the outermost would drop the root cause, which
// is the whole point of having wrapped the error.
func TestStructuredStacktracePrefersTheInnermostStack(t *testing.T) {
	err := errors.Wrap(failInHelper(), "could not start the worker")

	stack, ok := stackTraceOf(err)
	if !ok {
		t.Fatal("expected a stack somewhere in the chain")
	}
	frames := framesFrom(t, err, callersFromStackTrace(stack))

	if !strings.HasSuffix(frames[0].FunctionName, ".failInHelper") {
		t.Errorf("expected the root cause frame first, got %q", frames[0].FunctionName)
	}
	if frames[0].Error != "could not start the worker: boom" {
		t.Errorf("expected the frame to carry the whole chain's message, got %q", frames[0].Error)
	}
}

// Wrapping with %w carries no stack of its own, so the chain has to be followed
// past it to find one.
func TestStructuredStacktraceLooksPastStandardWrapping(t *testing.T) {
	err := fmt.Errorf("loading worker config: %w", failInHelper())

	stack, ok := stackTraceOf(err)
	if !ok {
		t.Fatal("expected a stack somewhere in the chain")
	}
	frames := framesFrom(t, err, callersFromStackTrace(stack))

	if !strings.HasSuffix(frames[0].FunctionName, ".failInHelper") {
		t.Errorf("expected the root cause frame first, got %q", frames[0].FunctionName)
	}
}

func TestStructuredStacktraceIgnoresAnErrorWithoutAStack(t *testing.T) {
	if _, ok := stackTraceOf(fmt.Errorf("no stack here")); ok {
		t.Error("expected no stack to be found")
	}
}

func TestStructuredStacktraceReadsSourceAroundTheFrame(t *testing.T) {
	err := failInHelper()
	stack, _ := stackTraceOf(err)

	frame := framesFrom(t, err, callersFromStackTrace(stack))[0]

	if strings.TrimSpace(frame.LineContent) != `return errors.New("boom")` {
		t.Errorf("expected the reported line to be the failing one, got %q", frame.LineContent)
	}
	// Indentation is kept, so the source reads as written.
	if !strings.HasPrefix(frame.LineContent, "\t") {
		t.Errorf("expected the reported line to keep its indentation, got %q", frame.LineContent)
	}
	before := strings.Split(frame.LinesBefore, "\n")
	if last := before[len(before)-1]; last != "func failInHelper() error {" {
		t.Errorf("expected the line before to be the function signature, got %q", last)
	}
	if first := strings.SplitN(frame.LinesAfter, "\n", 2)[0]; first != "}" {
		t.Errorf("expected the line after to close the function, got %q", first)
	}
}

func TestSourceContextReportsAWindowOnEachSide(t *testing.T) {
	path := numberedFile(t, 20)

	before, content, after := sourceContext(path, 10)

	if content != "line 10" {
		t.Errorf("expected line 10, got %q", content)
	}
	if want := "line 5\nline 6\nline 7\nline 8\nline 9"; before != want {
		t.Errorf("expected %q before, got %q", want, before)
	}
	if want := "line 11\nline 12\nline 13\nline 14\nline 15"; after != want {
		t.Errorf("expected %q after, got %q", want, after)
	}
}

func TestSourceContextStopsAtTheEdgesOfTheFile(t *testing.T) {
	path := numberedFile(t, 6)

	before, content, after := sourceContext(path, 2)
	if content != "line 2" || before != "line 1" {
		t.Errorf("expected the window to start at the first line, got %q before %q", before, content)
	}
	if want := "line 3\nline 4\nline 5\nline 6"; after != want {
		t.Errorf("expected %q after, got %q", want, after)
	}

	before, content, after = sourceContext(path, 6)
	if content != "line 6" || after != "" {
		t.Errorf("expected nothing after the last line, got %q after %q", after, content)
	}
	if want := "line 1\nline 2\nline 3\nline 4\nline 5"; before != want {
		t.Errorf("expected %q before, got %q", want, before)
	}
}

// A binary outlives the source it was built from, so a frame can name a line
// that no longer exists.
func TestSourceContextIgnoresALineBeyondTheFile(t *testing.T) {
	before, content, after := sourceContext(numberedFile(t, 6), 25)

	if before != "" || content != "" || after != "" {
		t.Errorf("expected no source, got %q %q %q", before, content, after)
	}
}

// The normal case for a compiled binary: the source is not on the host.
func TestSourceContextIgnoresAMissingFile(t *testing.T) {
	before, content, after := sourceContext(filepath.Join(t.TempDir(), "absent.go"), 3)

	if before != "" || content != "" || after != "" {
		t.Errorf("expected no source, got %q %q %q", before, content, after)
	}
}

func TestSourceContextIgnoresAFrameWithoutAFile(t *testing.T) {
	if before, content, after := sourceContext("", 3); before != "" || content != "" || after != "" {
		t.Errorf("expected no source for an empty path, got %q %q %q", before, content, after)
	}
	if before, content, after := sourceContext("/tmp/whatever.go", 0); before != "" || content != "" || after != "" {
		t.Errorf("expected no source for an unknown line, got %q %q %q", before, content, after)
	}
}

func TestTruncateSourceLine(t *testing.T) {
	short := "package main"
	if got := truncateSourceLine(short); got != short {
		t.Errorf("expected a short line to be kept, got %q", got)
	}

	long := truncateSourceLine(strings.Repeat("a", maxSourceLineLength+10))
	if len(long) != maxSourceLineLength {
		t.Errorf("expected a long line to be cut to %d bytes, got %d", maxSourceLineLength, len(long))
	}

	// The cut lands inside the last rune, which has to be dropped whole.
	runes := strings.Repeat("ф", maxSourceLineLength)
	cut := truncateSourceLine(runes)
	if len(cut) > maxSourceLineLength {
		t.Errorf("expected at most %d bytes, got %d", maxSourceLineLength, len(cut))
	}
	if !strings.HasPrefix(runes, cut) || strings.ContainsRune(cut, '\uFFFD') {
		t.Errorf("expected a valid prefix of the line, got %q", cut)
	}
}

func TestShortFunctionName(t *testing.T) {
	for _, tc := range []struct{ name, expected string }{
		{"github.com/acme/worker.(*Kafka).Run", "worker.(*Kafka).Run"},
		{"github.com/acme/worker.(*Kafka).Run.func1.2", "worker.(*Kafka).Run.func1.2"},
		{"net/http.HandlerFunc.ServeHTTP", "http.HandlerFunc.ServeHTTP"},
		{"main.main", "main.main"},
		{"", ""},
	} {
		if got := shortFunctionName(tc.name); got != tc.expected {
			t.Errorf("shortFunctionName(%q) = %q, expected %q", tc.name, got, tc.expected)
		}
	}
}

func TestIsInstrumentationFunction(t *testing.T) {
	for _, name := range []string{
		"github.com/launchdarkly/observability-sdk/go.RecordError",
		"github.com/launchdarkly/observability-sdk/go/internal/otel.GetTracer",
		"go.opentelemetry.io/otel/sdk/trace.(*recordingSpan).RecordError",
	} {
		if !isInstrumentationFunction(name) {
			t.Errorf("expected %q to be recording machinery", name)
		}
	}
	for _, name := range []string{
		// The external test package for this SDK, and anything else that merely
		// starts the same way, is application code.
		"github.com/launchdarkly/observability-sdk/go_test.TestRecordError",
		"github.com/acme/worker.(*Kafka).Run",
		"main.main",
	} {
		if isInstrumentationFunction(name) {
			t.Errorf("expected %q to be application code", name)
		}
	}
}

// Every function in this package is recording machinery by the same rule that
// applies to the SDK proper, so a stack captured here holds nothing else until
// it reaches the test harness underneath.
func TestFramesAtRecordTimeDropsTheRecordingFrames(t *testing.T) {
	frames := framesAtRecordTime("boom")

	if len(frames) == 0 {
		t.Fatal("expected the frames beneath the recording to survive")
	}
	if frames[0].FunctionName != "testing.tRunner" {
		t.Errorf("expected the first frame beneath this package, got %q", frames[0].FunctionName)
	}
}
