import {
	LDClientMin,
	LDPlugin,
	LDPluginMetadata,
	LDPluginEnvironmentMetadata,
} from './plugin'
import { ReactNativeOptions } from '../api/Options'
import { ObservabilityClient } from '../client/ObservabilityClient'
import { _LDObserve } from '../sdk/LDObserve'
import type {
	EvaluationSeriesContext,
	EvaluationSeriesData,
	Hook,
} from '@launchdarkly/js-server-sdk-common/dist/integrations'
import type { LDEvaluationDetail } from '@launchdarkly/js-sdk-common'

// TODO: Implement this hook, or update the server TracingHook so we can use it.
class TracingHook implements Hook {
	getMetadata(): LDPluginMetadata {
		return {
			name: '@launchdarkly/observability-react-native',
		}
	}

	beforeEvaluation(
		hookContext: EvaluationSeriesContext,
		data: EvaluationSeriesData,
	): EvaluationSeriesData {
		return data
	}

	afterEvaluation(
		hookContext: EvaluationSeriesContext,
		data: EvaluationSeriesData,
		detail: LDEvaluationDetail,
	): EvaluationSeriesData {
		return data
	}
}

export class Observability implements LDPlugin {
	private readonly _tracingHook: Hook = new TracingHook()
	constructor(private readonly _options?: ReactNativeOptions) {}

	getMetadata(): LDPluginMetadata {
		return {
			name: '@launchdarkly/observability-react-native',
		}
	}

	register(
		_client: LDClientMin,
		environmentMetadata: LDPluginEnvironmentMetadata,
	): void {
		const sdkKey =
			environmentMetadata.sdkKey || environmentMetadata.mobileKey || ''

		// TODO: Add service name and version to the options, if available.
		_LDObserve._init(new ObservabilityClient(sdkKey, this._options || {}))
	}

	getHooks?(_metadata: LDPluginEnvironmentMetadata): Hook[] {
		return [this._tracingHook]
	}
}
