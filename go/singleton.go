package ldobserve

import (
	"context"
	"fmt"
	"reflect"

	"github.com/pkg/errors"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/log"
	semconv "go.opentelemetry.io/otel/semconv/v1.34.0"
	"go.opentelemetry.io/otel/trace"

	o "github.com/launchdarkly/observability-sdk/go/internal/otel"
)

// StartSpan is used to start a new span.
// To start a span with a specific timestamp, use the trace.WithTimestamp option.
func StartSpan(ctx context.Context, name string, opts []trace.SpanStartOption, tags ...attribute.KeyValue) (context.Context, trace.Span) {
	ctx, span := o.GetTracer().Start(ctx, name, opts...)
	span.SetAttributes(tags...)
	return ctx, span
}

// EndSpan is used to end a span.
// The span will include a stack trace when this function is used to end the span.
func EndSpan(span trace.Span) {
	span.End(trace.WithStackTrace(true))
}

// RecordError is used to record an error in the current span.
// If there is an active recording span, then the error is recorded in the span.
// If there is no active recording span, then a new span is created and the error is recorded in it.
// If this function starts a span, then that span will be ended after the error is recorded.
func RecordError(ctx context.Context, err error, tags ...attribute.KeyValue) context.Context {
	span := trace.SpanFromContext(ctx)
	if span.IsRecording() {
		recordSpanError(span, err, tags...)
	} else {
		ctx, span = StartSpan(ctx, "error", []trace.SpanStartOption{trace.WithSpanKind(trace.SpanKindInternal)}, tags...)
		recordSpanError(span, err, tags...)
		EndSpan(span)
	}
	return ctx
}

func recordSpanError(span trace.Span, err error, tags ...attribute.KeyValue) {
	type withStackTrace interface {
		StackTrace() errors.StackTrace
	}

	if stackErr, ok := err.(withStackTrace); ok {
		stackTrace := fmt.Sprintf("%+v", stackErr.StackTrace())
		attributes := []attribute.KeyValue{
			semconv.ExceptionTypeKey.String(reflect.TypeOf(err).String()),
			semconv.ExceptionMessageKey.String(err.Error()),
			semconv.ExceptionStacktraceKey.String(stackTrace),
		}
		attributes = append(attributes, tags...)
		span.AddEvent(semconv.ExceptionEventName, trace.WithAttributes(attributes...))
	} else {
		span.RecordError(err, trace.WithStackTrace(true))
	}

}

func RecordLog(ctx context.Context, record log.Record, tags ...log.KeyValue) error {
	o.GetLogger().Emit(ctx, record)
	return nil
}

func RecordMetric(ctx context.Context, name string, value float64, tags ...attribute.KeyValue) {
	o.RecordMetric(ctx, name, value, tags...)
}

func RecordHistogram(ctx context.Context, name string, value float64, tags ...attribute.KeyValue) {
	o.RecordHistogram(ctx, name, value, tags...)
}

func RecordCount(ctx context.Context, name string, value int64, tags ...attribute.KeyValue) {
	o.RecordCount(ctx, name, value, tags...)
}
