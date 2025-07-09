import { W3CTraceContextPropagator } from '@opentelemetry/core'
import * as api from '@opentelemetry/api'
import { shouldNetworkRequestBeTraced, shouldRecordRequest } from './utils'
import { RECORD_ATTRIBUTE } from './constants'
import { ReadableSpan } from '@opentelemetry/sdk-trace-web'
import type { TracingOrigins } from './types'

type CustomTraceContextPropagatorConfig = {
	otlpEndpoint: string
	tracingOrigins: TracingOrigins
	urlBlocklist: string[]
}

export class CustomTraceContextPropagator extends W3CTraceContextPropagator {
	private internalEndpoints: string[]
	private tracingOrigins: CustomTraceContextPropagatorConfig['tracingOrigins']
	private urlBlocklist: CustomTraceContextPropagatorConfig['urlBlocklist']

	constructor(config: CustomTraceContextPropagatorConfig) {
		super()

		this.internalEndpoints = [
			`${config.otlpEndpoint}/v1/traces`,
			`${config.otlpEndpoint}/v1/logs`,
			`${config.otlpEndpoint}/v1/metrics`,
		]
		this.tracingOrigins = config.tracingOrigins
		this.urlBlocklist = config.urlBlocklist
	}

	inject(
		context: api.Context,
		carrier: unknown,
		setter: api.TextMapSetter,
	): void {
		const span = api.trace.getSpan(context)
		if (!span || !(span as any).attributes) {
			return
		}

		const url = (span as unknown as ReadableSpan).attributes['http.url']
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
