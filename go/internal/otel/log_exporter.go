package otel

import (
	"context"

	sdklog "go.opentelemetry.io/otel/sdk/log"
)

type logExporter struct {
	sdklog.Exporter
	sampler ExportSampler
}

func newLogExporter(exporter sdklog.Exporter, sampler ExportSampler) *logExporter {
	return &logExporter{Exporter: exporter, sampler: sampler}
}

func (e *logExporter) Export(ctx context.Context, records []sdklog.Record) error {
	exportedRecords := make([]sdklog.Record, 0, len(records))
	for _, record := range records {
		res := e.sampler.SampleLog(record)
		if res.Sample {
			if len(res.Attributes) > 0 {
				// In the case we need to modify the record we modify a clone
				// to avoid modifying the original record.
				recordCopy := record.Clone()
				recordCopy.AddAttributes(res.Attributes...)
				exportedRecords = append(exportedRecords, recordCopy)
			} else {
				exportedRecords = append(exportedRecords, record)
			}
		}
	}
	return e.Exporter.Export(ctx, exportedRecords)
}

var _ sdklog.Exporter = &logExporter{}
