import { MetricName } from '../types/client'

/**
 * UCUM / OTel units for first-party browser instruments.
 * Consulted at instrument creation; explicit `metric.unit` overrides these.
 */
export const FIRST_PARTY_METRIC_UNITS: Readonly<Record<string, string>> = {
	TTFB: 'ms',
	FCP: 'ms',
	LCP: 'ms',
	INP: 'ms',
	FID: 'ms',
	Jank: 'ms',
	CLS: '1',
	usedJSHeapSize: 'By',
	jsHeapSizeLimit: 'By',
	totalJSHeapSize: 'By',
	fps: '{frames}/s',
	[MetricName.ViewportHeight]: '{px}',
	[MetricName.ViewportWidth]: '{px}',
	[MetricName.ScreenHeight]: '{px}',
	[MetricName.ScreenWidth]: '{px}',
	[MetricName.ViewportArea]: '{px2}',
	// Value is 1024 * navigator.deviceMemory (GiB → MiB), not decimal megabytes.
	[MetricName.DeviceMemory]: 'MiBy',
	downlink: 'Mbit/s',
	downlinkMax: 'Mbit/s',
	rtt: 'ms',
	'long_task.duration': 'ms',
}

/** Options for Meter.create* when a unit is known. */
export function metricInstrumentOptions(
	name: string,
	unit?: string,
): { unit: string } | undefined {
	const resolved = unit ?? FIRST_PARTY_METRIC_UNITS[name]
	return resolved ? { unit: resolved } : undefined
}
