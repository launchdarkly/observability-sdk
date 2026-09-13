// This file is in the external test package on purpose: frames belonging to the
// SDK are dropped from the top of a recorded stack, so a test that wants to see
// itself reported has to sit outside the SDK, exactly where an application does.
package ldobserve_test

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"
	"testing"

	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/sdk/trace/tracetest"
	semconv "go.opentelemetry.io/otel/semconv/v1.34.0"

	ldobserve "github.com/launchdarkly/observability-sdk/go"
)

// frame mirrors the frame shape the SDK sends, so the test reads the payload the
// backend will read rather than the struct that produced it.
type frame struct {
	FileName     string `json:"fileName"`
	LineNumber   int    `json:"lineNumber"`
	FunctionName string `json:"functionName"`
	Error        string `json:"error"`
	LinesBefore  string `json:"linesBefore"`
	LineContent  string `json:"lineContent"`
	LinesAfter   string `json:"linesAfter"`
}

// recordPlainError records an error that carries no stack of its own, so the SDK
// has to capture one here.
func recordPlainError(ctx context.Context) {
	ldobserve.RecordError(ctx, fmt.Errorf("plain failure"))
}

func recordedFrames(t *testing.T) []frame {
	t.Helper()

	exporter := tracetest.NewInMemoryExporter()
	provider := sdktrace.NewTracerProvider(sdktrace.WithSpanProcessor(sdktrace.NewSimpleSpanProcessor(exporter)))
	defer provider.Shutdown(context.Background())

	ctx, span := provider.Tracer("test").Start(context.Background(), "test-span")
	recordPlainError(ctx)
	span.End()

	spans := exporter.GetSpans()
	if len(spans) != 1 {
		t.Fatalf("expected 1 span, got %d", len(spans))
	}
	events := spans[0].Events
	if len(events) != 1 || events[0].Name != semconv.ExceptionEventName {
		t.Fatalf("expected a single exception event, got %v", events)
	}

	var payload string
	for _, attr := range events[0].Attributes {
		if attr.Key == "exception.structured_stacktrace" {
			payload = attr.Value.AsString()
		}
	}
	if payload == "" {
		t.Fatal("expected the event to carry a structured stack trace")
	}

	var frames []frame
	if err := json.Unmarshal([]byte(payload), &frames); err != nil {
		t.Fatalf("could not decode frames: %v", err)
	}
	if len(frames) == 0 {
		t.Fatal("expected at least one frame")
	}
	return frames
}

// The frames of the recording itself say nothing about the failure, and used to
// be all the top of a Go trace had to offer.
func TestRecordErrorReportsTheCallerFirst(t *testing.T) {
	frames := recordedFrames(t)

	if !strings.HasSuffix(frames[0].FunctionName, ".recordPlainError") {
		t.Errorf("expected the recording caller as the first frame, got %q", frames[0].FunctionName)
	}
	for _, f := range frames {
		if strings.HasSuffix(f.FileName, "singleton.go") || strings.HasSuffix(f.FileName, "stacktrace.go") {
			t.Errorf("expected no SDK frames, got %q in %q", f.FunctionName, f.FileName)
		}
		if strings.Contains(f.FileName, "go.opentelemetry.io") {
			t.Errorf("expected no OTeL frames, got %q in %q", f.FunctionName, f.FileName)
		}
	}
}

// Recording a nil error is a no-op in the other SDKs, and used to be one here
// too: it reached span.RecordError, which ignores it. Building a structured
// stack trace for every error put an err.Error() in front of that.
func TestRecordErrorIgnoresANilError(t *testing.T) {
	exporter := tracetest.NewInMemoryExporter()
	provider := sdktrace.NewTracerProvider(sdktrace.WithSpanProcessor(sdktrace.NewSimpleSpanProcessor(exporter)))
	defer provider.Shutdown(context.Background())

	ctx, span := provider.Tracer("test").Start(context.Background(), "test-span")
	ldobserve.RecordError(ctx, nil)
	span.End()

	// Recording without a span in the context is the other path, and starts one
	// of its own. Neither may panic.
	ldobserve.RecordError(context.Background(), nil)

	spans := exporter.GetSpans()
	if len(spans) != 1 {
		t.Fatalf("expected 1 span, got %d", len(spans))
	}
	if len(spans[0].Events) != 0 {
		t.Errorf("expected nothing to be recorded, got %v", spans[0].Events)
	}
}

func TestRecordErrorReportsSourceContext(t *testing.T) {
	frames := recordedFrames(t)

	if !strings.HasSuffix(frames[0].FileName, "stacktrace_record_test.go") {
		t.Errorf("expected the frame to point at this file, got %q", frames[0].FileName)
	}
	if !strings.Contains(frames[0].LineContent, `fmt.Errorf("plain failure")`) {
		t.Errorf("expected the reported line to be the recorded one, got %q", frames[0].LineContent)
	}
	if !strings.Contains(frames[0].LinesBefore, "func recordPlainError(ctx context.Context) {") {
		t.Errorf("expected the lines before to include the function signature, got %q", frames[0].LinesBefore)
	}
	if frames[0].LinesAfter == "" {
		t.Error("expected lines after the reported line")
	}
	if frames[0].Error != "plain failure" {
		t.Errorf("expected the frame to carry the error message, got %q", frames[0].Error)
	}
}
