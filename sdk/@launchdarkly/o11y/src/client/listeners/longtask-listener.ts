export interface LongtaskEntry {
	/** Duration of the long task in ms. */
	duration: number
	/** Entry name — usually `self` for same-origin tasks. */
	name: string
	/** `iframe`, `embed`, `object`, or empty when originating from the host page. */
	containerType?: string
	/** `src`/`id`/`name` of the container, when one is present. */
	containerSrc?: string
	containerId?: string
	containerName?: string
	/** startTime relative to navigationStart in ms. */
	startTime: number
}

const isLongtaskSupported = (): boolean => {
	if (typeof window === 'undefined') return false
	if (typeof PerformanceObserver === 'undefined') return false
	const supported = (
		PerformanceObserver as unknown as { supportedEntryTypes?: string[] }
	).supportedEntryTypes
	return Array.isArray(supported) && supported.includes('longtask')
}

/**
 * Subscribes to the browser PerformanceObserver for `longtask` entries
 * (main-thread blocks > 50ms) and invokes the callback for each entry.
 *
 * Returns a disconnect function. No-ops on environments that do not
 * support the longtask entry type.
 */
export const LongtaskListener = (
	callback: (entry: LongtaskEntry) => void,
): (() => void) => {
	if (!isLongtaskSupported()) return () => {}

	let observer: PerformanceObserver
	try {
		observer = new PerformanceObserver((list) => {
			for (const entry of list.getEntries()) {
				// PerformanceLongTaskTiming exposes attribution[] but we
				// surface the first attribution's container context which is
				// the common useful dimension.
				const attribution = (
					entry as PerformanceEntry & {
						attribution?: ReadonlyArray<{
							containerType?: string
							containerSrc?: string
							containerId?: string
							containerName?: string
						}>
					}
				).attribution?.[0]
				callback({
					duration: entry.duration,
					name: entry.name,
					startTime: entry.startTime,
					containerType: attribution?.containerType,
					containerSrc: attribution?.containerSrc,
					containerId: attribution?.containerId,
					containerName: attribution?.containerName,
				})
			}
		})
		observer.observe({ type: 'longtask', buffered: true })
	} catch {
		return () => {}
	}

	return () => {
		try {
			observer.disconnect()
		} catch {
			// ignore
		}
	}
}
