package ldobserve

import (
	"bufio"
	"encoding/json"
	"os"
	"runtime"
	"strings"
	"unicode/utf8"

	"github.com/pkg/errors"
	"go.opentelemetry.io/otel/attribute"
)

// exceptionStructuredStacktraceKey holds the frames of an error as JSON,
// including the source lines around each reported line. Ingestion prefers it
// over the text stack trace in exception.stacktrace, which has nowhere to put
// source and which a Go trace is only readable enough to guess at.
const exceptionStructuredStacktraceKey = "exception.structured_stacktrace"

const (
	// Lines of context on each side of the reported line. Matches the window the
	// backend produces for the languages it symbolicates itself.
	sourceContextLines = 5
	// Longest source line reported, matching the backend's own limit. Longer
	// lines are cut rather than dropped.
	maxSourceLineLength = 1000
	// Longest source line read at all. A line this long is generated code, and
	// reading it costs more than the context is worth.
	maxSourceLineBuffer = 1 << 20
	// Frames reported. Matches the backend's frame cap: it keeps the leading
	// frames and drops the rest, so anything past this is discarded on arrival.
	maxStructuredFrames = 64
	// Depth captured when the error carries no stack of its own. Deeper than the
	// frames reported, so that dropping the instrumentation frames below still
	// leaves a full trace.
	maxCallers = maxStructuredFrames + 16
)

// instrumentationPackages are the packages that do the recording. Their frames
// sit on top of a stack captured at record time and describe the recording, not
// the failure: they are why an OTeL-captured Go trace opens with
// recordStackTrace and RecordError rather than with the code that broke.
//
//nolint:gochecknoglobals
var instrumentationPackages = []string{
	"github.com/launchdarkly/observability-sdk/go",
	"go.opentelemetry.io/otel",
}

// structuredFrame is one frame as ingestion expects it. The JSON names are the
// GraphQL ones and have to stay in step with the backend's ErrorTrace.
type structuredFrame struct {
	FileName     string `json:"fileName,omitempty"`
	LineNumber   int    `json:"lineNumber,omitempty"`
	FunctionName string `json:"functionName,omitempty"`
	Error        string `json:"error,omitempty"`
	LinesBefore  string `json:"linesBefore,omitempty"`
	LineContent  string `json:"lineContent,omitempty"`
	LinesAfter   string `json:"linesAfter,omitempty"`
}

// structuredStacktraceAttribute encodes frames as the attribute ingestion reads.
// It reports false when there is nothing to describe, leaving the error to be
// recorded with its text stack trace alone.
func structuredStacktraceAttribute(frames []structuredFrame) (attribute.KeyValue, bool) {
	if len(frames) == 0 {
		return attribute.KeyValue{}, false
	}
	encoded, marshalErr := json.Marshal(frames)
	if marshalErr != nil {
		return attribute.KeyValue{}, false
	}
	return attribute.String(exceptionStructuredStacktraceKey, string(encoded)), true
}

// stackTraceOf returns the stack of the innermost error in the chain that
// carries one.
//
// Wrapping captures a stack at every wrap, and the innermost is the one taken
// closest to the failure: it names the call that actually went wrong, which the
// wrap sites above it no longer reach. Taking the outermost instead loses the
// root cause, in the same way that reporting only the last exception of a
// Python chain does.
func stackTraceOf(err error) (errors.StackTrace, bool) {
	type withStackTrace interface {
		StackTrace() errors.StackTrace
	}

	var deepest errors.StackTrace
	for wrapped := err; wrapped != nil; wrapped = errors.Unwrap(wrapped) {
		if carrier, ok := wrapped.(withStackTrace); ok {
			deepest = carrier.StackTrace()
		}
	}
	return deepest, len(deepest) > 0
}

// callersFromStackTrace converts the stack an error captured when it was created
// into program counters. Those counters are the values runtime.Callers returned
// at that point, so they can be resolved the same way as a fresh capture.
func callersFromStackTrace(stack errors.StackTrace) []uintptr {
	callers := make([]uintptr, 0, len(stack))
	for _, frame := range stack {
		callers = append(callers, uintptr(frame))
	}
	return callers
}

// framesAtRecordTime describes the stack of the caller, for an error that
// carries no stack of its own.
//
// The frames on top of that stack are this SDK and the OTeL API doing the
// recording; they say nothing about the failure and are dropped, so the trace
// starts at the code that recorded the error. Only the leading run goes: once
// the stack reaches the application, a package of ours appearing again is a
// real caller.
//
// They are dropped from the resolved frames rather than from the counters,
// which also resolves the stack once instead of twice. runtime.Callers emits a
// counter per frame today, inlined calls included, but runtime.CallersFrames
// documents that several frames can share one counter -- so a counter index
// taken from a frame count is only as good as that behavior.
func framesAtRecordTime(message string) []structuredFrame {
	callers := make([]uintptr, maxCallers)
	// 2 skips runtime.Callers and this function.
	captured := runtime.Callers(2, callers)
	return framesFromCallers(callers[:captured], message, true)
}

func isInstrumentationFunction(name string) bool {
	for _, pkg := range instrumentationPackages {
		if strings.HasPrefix(name, pkg+".") || strings.HasPrefix(name, pkg+"/") {
			return true
		}
	}
	return false
}

// structuredFrames describes the stack an error carried. Every frame of it is
// reported: that stack was captured where the error was created, so all of it
// describes the failure and none of it is the recording.
func structuredFrames(callers []uintptr, message string) []structuredFrame {
	return framesFromCallers(callers, message, false)
}

// framesFromCallers resolves callers to frames, reading the source around each
// one where the file can be read.
func framesFromCallers(
	callers []uintptr, message string, dropInstrumentation bool,
) []structuredFrame {
	if len(callers) == 0 {
		return nil
	}

	frames := make([]structuredFrame, 0, len(callers))
	iterator := runtime.CallersFrames(callers)
	instrumentation := dropInstrumentation
	for len(frames) < maxStructuredFrames {
		frame, more := iterator.Next()
		instrumentation = instrumentation && isInstrumentationFunction(frame.Function)
		if !instrumentation && (frame.Function != "" || frame.File != "") {
			structured := structuredFrame{
				FileName:     frame.File,
				LineNumber:   frame.Line,
				FunctionName: shortFunctionName(frame.Function),
				Error:        message,
			}
			structured.LinesBefore, structured.LineContent, structured.LinesAfter = sourceContext(frame.File, frame.Line)
			frames = append(frames, structured)
		}
		if !more {
			break
		}
	}
	return frames
}

// shortFunctionName drops the module path from a fully qualified Go function
// name: "github.com/acme/worker.(*Kafka).Run.func1" becomes
// "worker.(*Kafka).Run.func1". The full path is too long to read in a frame,
// while the last segment alone is ambiguous for closures, where it is just an
// ordinal.
func shortFunctionName(name string) string {
	if slash := strings.LastIndex(name, "/"); slash >= 0 {
		return name[slash+1:]
	}
	return name
}

// sourceContext returns the source around line in path, split the way the
// backend stores it: the lines before, the reported line, and the lines after.
//
// All three are empty when the source cannot be read, which is the normal case
// for a binary running without its source tree, a container built from scratch
// being the obvious one. Frames then carry what they always have.
func sourceContext(path string, line int) (before string, content string, after string) {
	if path == "" || line <= 0 {
		return "", "", ""
	}

	first := line - sourceContextLines
	if first < 1 {
		first = 1
	}
	lines, ok := readSourceLines(path, first, line+sourceContextLines)
	if !ok {
		return "", "", ""
	}

	// The file can be shorter than the trace claims, if it changed since the
	// binary was built.
	reported := line - first
	if reported >= len(lines) {
		return "", "", ""
	}
	return strings.Join(lines[:reported], "\n"), lines[reported], strings.Join(lines[reported+1:], "\n")
}

// readSourceLines returns lines first through last of path, stopping at the end
// of the file. It reports false when the file cannot be read to that point,
// which covers a missing file, an unreadable one, and one whose lines are too
// long to be worth reading.
func readSourceLines(path string, first, last int) ([]string, bool) {
	//nolint:gosec // The path is one the runtime reported for a frame, not input.
	file, err := os.Open(path)
	if err != nil {
		return nil, false
	}
	defer func() { _ = file.Close() }()

	scanner := bufio.NewScanner(file)
	scanner.Buffer(nil, maxSourceLineBuffer)

	lines := make([]string, 0, last-first+1)
	for number := 1; number <= last && scanner.Scan(); number++ {
		if number >= first {
			lines = append(lines, truncateSourceLine(scanner.Text()))
		}
	}
	if scanner.Err() != nil {
		return nil, false
	}
	return lines, true
}

func truncateSourceLine(line string) string {
	if len(line) <= maxSourceLineLength {
		return line
	}
	// Cutting a fixed number of bytes can land inside a rune.
	truncated := line[:maxSourceLineLength]
	for len(truncated) > 0 && !utf8.ValidString(truncated) {
		truncated = truncated[:len(truncated)-1]
	}
	return truncated
}
