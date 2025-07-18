import { W3CTraceContextPropagator } from '@opentelemetry/core'
import * as api from '@opentelemetry/api'
import { shouldNetworkRequestBeTraced, shouldRecordRequest } from './utils'
import { RECORD_ATTRIBUTE } from './constants'
export class CustomTraceContextPropagator extends W3CTraceContextPropagator {
	constructor(config) {
		super()
		this.internalEndpoints = config.internalEndpoints
		this.tracingOrigins = config.tracingOrigins
		this.urlBlocklist = config.urlBlocklist
	}
	inject(context, carrier, setter) {
		const span = api.trace.getSpan(context)
		if (!span || !span.attributes) {
			return
		}
		const url = span.attributes['http.url']
		if (typeof url === 'string') {
			const shouldRecord = shouldRecordRequest(
				url,
				this.internalEndpoints,
				this.tracingOrigins,
				this.urlBlocklist,
			)
			if (!shouldRecord) {
				span.setAttribute(RECORD_ATTRIBUTE, false) // used later to avoid additional processing
			}
			const shouldTrace = shouldNetworkRequestBeTraced(
				url,
				this.tracingOrigins ?? [],
				this.urlBlocklist,
			)
			if (!shouldTrace) {
				return // return early to prevent headers from being injected
			}
		}
		super.inject(context, carrier, setter)
	}
}
