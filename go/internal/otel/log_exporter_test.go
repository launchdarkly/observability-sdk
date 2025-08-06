package otel

import (
	"context"
	"testing"

	"go.opentelemetry.io/otel/log"
	sdklog "go.opentelemetry.io/otel/sdk/log"

	"github.com/launchdarkly/observability-sdk/go/internal/gql"
)

type testLogExporter struct {
	exportedRecords []sdklog.Record
}

// Shutdown implements sdklog.Exporter.
func (e *testLogExporter) Shutdown(ctx context.Context) error {
	return nil
}

func (e *testLogExporter) Export(ctx context.Context, records []sdklog.Record) error {
	e.exportedRecords = append(e.exportedRecords, records...)
	return nil
}

func (e *testLogExporter) ForceFlush(ctx context.Context) error {
	return nil
}

var _ sdklog.Exporter = &testLogExporter{}

func TestLogExporter_NoSamplingConfig(t *testing.T) {
	// Test that logs with no sampling configuration are always sampled
	customSampler := NewCustomSampler(alwaysSampler)
	exporter := &testLogExporter{}
	logExporter := newLogExporter(exporter, customSampler)

	// Create a test log record
	record := sdklog.Record{}
	record.SetBody(log.StringValue("unmatched message"))

	// Export the log record
	err := logExporter.Export(context.Background(), []sdklog.Record{record})
	if err != nil {
		t.Fatalf("Export failed: %v", err)
	}

	// Verify the log was exported (should always be sampled when no config matches)
	if len(exporter.exportedRecords) != 1 {
		t.Errorf("expected 1 exported log, got %d", len(exporter.exportedRecords))
	}

	// Verify no sampling ratio attribute was added (since no config matched)
	exportedRecord := exporter.exportedRecords[0]
	foundSamplingRatio := false
	exportedRecord.WalkAttributes(func(kv log.KeyValue) bool {
		if kv.Key == "launchdarkly.sampling.ratio" {
			foundSamplingRatio = true
			return false // stop walking
		}
		return true
	})
	if foundSamplingRatio {
		t.Error("unexpected sampling ratio attribute on log with no matching config")
	}
}

func TestLogExporter_WithMatchingConfigSampledIn(t *testing.T) {
	// Test that logs with matching configuration and sampler returning true are exported with sampling ratio
	customSampler := NewCustomSampler(alwaysSampler)
	exporter := &testLogExporter{}
	logExporter := newLogExporter(exporter, customSampler)

	// Set up a configuration that matches our log
	config := &gql.GetSamplingConfigSamplingSamplingConfig{
		Logs: []gql.GetSamplingConfigSamplingSamplingConfigLogsLogSamplingConfig{
			{
				Message: gql.GetSamplingConfigSamplingSamplingConfigLogsLogSamplingConfigMessageMatchConfig{
					MatchParts: gql.MatchParts{
						MatchValue: "test message",
					},
				},
				SamplingRatio: 1,
			},
		},
	}
	customSampler.SetConfig(config)

	// Create a test log record
	record := sdklog.Record{}
	record.SetBody(log.StringValue("test message"))

	// Export the log record
	err := logExporter.Export(context.Background(), []sdklog.Record{record})
	if err != nil {
		t.Fatalf("Export failed: %v", err)
	}

	// Verify the log was exported
	if len(exporter.exportedRecords) != 1 {
		t.Errorf("expected 1 exported log, got %d", len(exporter.exportedRecords))
	}

	// Verify the sampling ratio attribute was added with correct value
	exportedRecord := exporter.exportedRecords[0]
	foundSamplingRatio := false
	expectedRatio := int64(1) // From the config we set
	exportedRecord.WalkAttributes(func(kv log.KeyValue) bool {
		if kv.Key == "launchdarkly.sampling.ratio" {
			foundSamplingRatio = true
			if kv.Value.Kind() != log.KindInt64 {
				t.Errorf("expected sampling ratio attribute to be INT64 type, got %v", kv.Value.Kind())
			}
			if kv.Value.AsInt64() != expectedRatio {
				t.Errorf("expected sampling ratio to be %d, got %d", expectedRatio, kv.Value.AsInt64())
			}
			return false // stop walking
		}
		return true
	})
	if !foundSamplingRatio {
		t.Error("expected sampling ratio attribute to be added to exported log")
	}
}

func TestLogExporter_WithMatchingConfigSampledOut(t *testing.T) {
	// Test that logs with matching configuration but sampler returning false are not exported
	customSampler := NewCustomSampler(neverSampler)
	exporter := &testLogExporter{}
	logExporter := newLogExporter(exporter, customSampler)

	// Set up a configuration that matches our log
	config := &gql.GetSamplingConfigSamplingSamplingConfig{
		Logs: []gql.GetSamplingConfigSamplingSamplingConfigLogsLogSamplingConfig{
			{
				Message: gql.GetSamplingConfigSamplingSamplingConfigLogsLogSamplingConfigMessageMatchConfig{
					MatchParts: gql.MatchParts{
						MatchValue: "test message",
					},
				},
				SamplingRatio: 1,
			},
		},
	}
	customSampler.SetConfig(config)

	// Create a test log record
	record := sdklog.Record{}
	record.SetBody(log.StringValue("test message"))

	// Export the log record
	err := logExporter.Export(context.Background(), []sdklog.Record{record})
	if err != nil {
		t.Fatalf("Export failed: %v", err)
	}

	// Verify no logs were exported (since neverSampler returns false)
	if len(exporter.exportedRecords) != 0 {
		t.Errorf("expected 0 exported logs, got %d", len(exporter.exportedRecords))
	}
}

func TestLogExporter_WithDifferentSamplingRatio(t *testing.T) {
	// Test that logs with different sampling ratios get the correct value
	customSampler := NewCustomSampler(alwaysSampler)
	exporter := &testLogExporter{}
	logExporter := newLogExporter(exporter, customSampler)

	// Set up a configuration with a different sampling ratio
	expectedRatio := int64(10)
	config := &gql.GetSamplingConfigSamplingSamplingConfig{
		Logs: []gql.GetSamplingConfigSamplingSamplingConfigLogsLogSamplingConfig{
			{
				Message: gql.GetSamplingConfigSamplingSamplingConfigLogsLogSamplingConfigMessageMatchConfig{
					MatchParts: gql.MatchParts{
						MatchValue: "test message",
					},
				},
				SamplingRatio: int(expectedRatio),
			},
		},
	}
	customSampler.SetConfig(config)

	// Create a test log record
	record := sdklog.Record{}
	record.SetBody(log.StringValue("test message"))

	// Export the log record
	err := logExporter.Export(context.Background(), []sdklog.Record{record})
	if err != nil {
		t.Fatalf("Export failed: %v", err)
	}

	// Verify the log was exported
	if len(exporter.exportedRecords) != 1 {
		t.Errorf("expected 1 exported log, got %d", len(exporter.exportedRecords))
	}

	// Verify the sampling ratio attribute was added with correct value
	exportedRecord := exporter.exportedRecords[0]
	foundSamplingRatio := false
	exportedRecord.WalkAttributes(func(kv log.KeyValue) bool {
		if kv.Key == "launchdarkly.sampling.ratio" {
			foundSamplingRatio = true
			if kv.Value.Kind() != log.KindInt64 {
				t.Errorf("expected sampling ratio attribute to be INT64 type, got %v", kv.Value.Kind())
			}
			if kv.Value.AsInt64() != expectedRatio {
				t.Errorf("expected sampling ratio to be %d, got %d", expectedRatio, kv.Value.AsInt64())
			}
			return false // stop walking
		}
		return true
	})
	if !foundSamplingRatio {
		t.Error("expected sampling ratio attribute to be added to exported log")
	}
}

func TestLogExporter_MixedLogs(t *testing.T) {
	// Test that a mix of sampled and non-sampled logs are handled correctly
	customSampler := NewCustomSampler(alwaysSampler)
	exporter := &testLogExporter{}
	logExporter := newLogExporter(exporter, customSampler)

	// Create test log records
	record1 := sdklog.Record{}
	record1.SetBody(log.StringValue("sampled log"))

	record2 := sdklog.Record{}
	record2.SetBody(log.StringValue("another sampled log"))

	// Export the log records
	err := logExporter.Export(context.Background(), []sdklog.Record{record1, record2})
	if err != nil {
		t.Fatalf("Export failed: %v", err)
	}

	// Verify both logs were exported
	if len(exporter.exportedRecords) != 2 {
		t.Errorf("expected 2 exported logs, got %d", len(exporter.exportedRecords))
	}
}

func TestLogExporter_MultipleLogsWithDifferentRatios(t *testing.T) {
	// Test that multiple logs with different sampling ratios get the correct values
	customSampler := NewCustomSampler(alwaysSampler)
	exporter := &testLogExporter{}
	logExporter := newLogExporter(exporter, customSampler)

	// Set up a configuration with multiple log configs
	config := &gql.GetSamplingConfigSamplingSamplingConfig{
		Logs: []gql.GetSamplingConfigSamplingSamplingConfigLogsLogSamplingConfig{
			{
				Message: gql.GetSamplingConfigSamplingSamplingConfigLogsLogSamplingConfigMessageMatchConfig{
					MatchParts: gql.MatchParts{
						MatchValue: "log-1",
					},
				},
				SamplingRatio: 5,
			},
			{
				Message: gql.GetSamplingConfigSamplingSamplingConfigLogsLogSamplingConfigMessageMatchConfig{
					MatchParts: gql.MatchParts{
						MatchValue: "log-2",
					},
				},
				SamplingRatio: 10,
			},
		},
	}
	customSampler.SetConfig(config)

	// Create first log record
	record1 := sdklog.Record{}
	record1.SetBody(log.StringValue("log-1"))

	// Create second log record
	record2 := sdklog.Record{}
	record2.SetBody(log.StringValue("log-2"))

	// Export the log records
	err := logExporter.Export(context.Background(), []sdklog.Record{record1, record2})
	if err != nil {
		t.Fatalf("Export failed: %v", err)
	}

	// Verify both logs were exported
	if len(exporter.exportedRecords) != 2 {
		t.Errorf("expected 2 exported logs, got %d", len(exporter.exportedRecords))
	}

	// Verify the first log has the correct sampling ratio
	exportedLog1 := exporter.exportedRecords[0]
	foundSamplingRatio1 := false
	exportedLog1.WalkAttributes(func(kv log.KeyValue) bool {
		if kv.Key == "launchdarkly.sampling.ratio" {
			foundSamplingRatio1 = true
			if kv.Value.AsInt64() != 5 {
				t.Errorf("expected first log sampling ratio to be 5, got %d", kv.Value.AsInt64())
			}
			return false // stop walking
		}
		return true
	})
	if !foundSamplingRatio1 {
		t.Error("expected sampling ratio attribute to be added to first exported log")
	}

	// Verify the second log has the correct sampling ratio
	exportedLog2 := exporter.exportedRecords[1]
	foundSamplingRatio2 := false
	exportedLog2.WalkAttributes(func(kv log.KeyValue) bool {
		if kv.Key == "launchdarkly.sampling.ratio" {
			foundSamplingRatio2 = true
			if kv.Value.AsInt64() != 10 {
				t.Errorf("expected second log sampling ratio to be 10, got %d", kv.Value.AsInt64())
			}
			return false // stop walking
		}
		return true
	})
	if !foundSamplingRatio2 {
		t.Error("expected sampling ratio attribute to be added to second exported log")
	}
}

func TestLogExporter_EmptyLogsSlice(t *testing.T) {
	// Test that empty logs slice is handled correctly
	customSampler := NewCustomSampler(alwaysSampler)
	exporter := &testLogExporter{}
	logExporter := newLogExporter(exporter, customSampler)

	// Export empty slice
	err := logExporter.Export(context.Background(), []sdklog.Record{})
	if err != nil {
		t.Fatalf("Export failed: %v", err)
	}

	// Verify no logs were exported
	if len(exporter.exportedRecords) != 0 {
		t.Errorf("expected 0 exported logs, got %d", len(exporter.exportedRecords))
	}
}
