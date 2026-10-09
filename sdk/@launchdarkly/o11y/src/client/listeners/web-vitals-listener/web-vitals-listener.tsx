import {
	CLSMetricWithAttribution,
	FCPMetricWithAttribution,
	FIDMetricWithAttribution,
	INPMetricWithAttribution,
	LCPMetricWithAttribution,
	onCLS,
	onFCP,
	onFID,
	onINP,
	onLCP,
	onTTFB,
	TTFBMetricWithAttribution,
} from 'web-vitals/attribution'

/**
 * Discriminated union of all web-vitals metrics with their per-metric
 * attribution shape populated. Use `switch (metric.name)` in consumers to
 * narrow to the specific attribution type.
 */
export type WebVitalMetric =
	| CLSMetricWithAttribution
	| FCPMetricWithAttribution
	| FIDMetricWithAttribution
	| INPMetricWithAttribution
	| LCPMetricWithAttribution
	| TTFBMetricWithAttribution

export const WebVitalsListener = (
	callback: (metric: WebVitalMetric) => void,
) => {
	onCLS(callback)
	onFCP(callback)
	onFID(callback)
	onLCP(callback)
	onTTFB(callback)
	onINP(callback)

	return () => {}
}
