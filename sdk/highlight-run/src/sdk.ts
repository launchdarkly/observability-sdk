import type { IntegrationClient } from './integrations'
import type { Client } from './api/client'
import type { HighlightOptions, LDClientMin } from 'client'
import type { ErrorMessageType, Source } from 'client/types/shared-types'
import type {
	Metadata,
	OTelMetric as Metric,
	StartOptions,
} from 'client/types/types'
import { Attributes } from '@opentelemetry/api'
import { LDPluginEnvironmentMetadata } from 'plugins/plugin'

class SDKCore implements Client {
	static _instance: SDKCore
	static _integrations: IntegrationClient[] = []

	constructor() {
		if (SDKCore._instance) {
			return SDKCore._instance
		}
		SDKCore._instance = this
		this.load()
		return this
	}

	// TODO(vkorolik) buffer all calls
	init(projectID?: string | number, debug?: HighlightOptions) {
		return undefined
	}
	identify(identifier: string, metadata?: Metadata, source?: Source) {}
	track(event: string, metadata?: Metadata) {}
	getSession() {
		return null
	}
	async start(options?: StartOptions) {}
	stop(options?: StartOptions) {}
	getRecordingState() {
		return 'NotRecording' as const
	}
	snapshot(element: HTMLCanvasElement) {
		return Promise.resolve(undefined)
	}
	error(message: string, payload?: { [key: string]: string }) {}
	metrics(metrics: Metric[]) {}
	recordGauge(metric: Metric) {}
	recordCount(metric: Metric) {}
	recordIncr(metric: Omit<Metric, 'value'>) {}
	recordHistogram(metric: Metric) {}
	recordUpDownCounter(metric: Metric) {}
	startSpan() {}
	startManualSpan() {}
	consumeError(
		error: Error,
		message?: string,
		payload?: { [key: string]: string },
	) {}
	consume(
		error: Error,
		opts: {
			message?: string
			payload?: object
			source?: string
			type?: ErrorMessageType
		},
	) {}
	register(
		client: LDClientMin,
		environmentMetadata: LDPluginEnvironmentMetadata,
	) {}
	recordLog(message: any, level: string, metadata?: Attributes) {}
	recordError(
		error: Error,
		message?: string,
		payload?: { [key: string]: string },
		source?: string,
		type?: ErrorMessageType,
	) {}

	async load() {
		// TODO(vkorolik) does this work...?
		await Promise.all(
			[import('./sdk/record'), import('./sdk/observe')].map((m) =>
				m.then((m) =>
					Object.defineProperties(
						this,
						Object.getOwnPropertyDescriptors(m),
					),
				),
			),
		)
	}
}

const LD = new SDKCore()
export default LD
