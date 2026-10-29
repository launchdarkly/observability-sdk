import {
	AggregationTemporalityPreference,
	OTLPMetricExporter,
} from '@opentelemetry/exporter-metrics-otlp-http'

// Counters and histograms are exported as deltas: each export carries only the
// increments recorded since the previous one. An app session is a short-lived
// stream, so cumulative totals would leave the backend nothing but a baseline
// point for most series. Up-down counters stay cumulative and gauges are
// unaffected, per the OTel DELTA preference.
export const createMetricExporter = (
	headers: Record<string, string>,
	url: string,
) =>
	new OTLPMetricExporter({
		headers,
		url,
		temporalityPreference: AggregationTemporalityPreference.DELTA,
	})
