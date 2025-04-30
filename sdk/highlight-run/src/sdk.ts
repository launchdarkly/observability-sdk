import {
	LaunchDarklyIntegration,
	setupLaunchDarklyIntegration,
} from './integrations/launchdarkly'
import { IntegrationClient } from './integrations'
import type { Client } from './api/client'
import type { LDClientMin } from './integrations/launchdarkly/types/LDClient'
import { HighlightOptions, Metric } from 'client'
import { ErrorMessageType, Source } from 'client/types/shared-types'
import { StartOptions } from 'client/types/types'

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

	init(projectID?: string | number, debug?: HighlightOptions) {
		return undefined
	}
	identify(identifier: string, metadata?: Metadata, source?: Source) {}
	track(event: string, metadata?: Metadata) {}
	getSession() {
		return null
	}
	start(options?: StartOptions) {}
	stop(options?: StartOptions) {}
	getRecordingState() {
		return 'NotRecording' as const
	}
	snapshot(element: HTMLCanvasElement) {
		return Promise.resolve(undefined)
	}
	error(message: string, payload?: { [key: string]: string }) {}
	metrics(metrics: Metric[]) {}
	recordMetric(metric: Metric) {}
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

	async load() {
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
	registerLD(client: LDClientMin) {
		// TODO(vkorolik)
		SDKCore._integrations.push(new LaunchDarklyIntegration(client))
	}
}

const instance = new SDKCore()
export default instance
