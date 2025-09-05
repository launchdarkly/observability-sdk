import type {
	EvaluationSeriesContext,
	EvaluationSeriesData,
	LDEvaluationReason,
	LDFlagValue,
	Hook as LDHook,
} from '@launchdarkly/js-client-sdk'

export interface EvaluationSeriesContextWithMethod
	extends EvaluationSeriesContext {
	method?: string
}

/**
 * Interface for extending SDK functionality via hooks.
 */
export type Hook = Omit<LDHook, 'afterEvaluation'> & {
	/**
	 * This method is called during the execution of the variation method
	 * after the flag value has been determined. The method is executed synchronously.
	 *
	 * @param hookContext Contains read-only information about the evaluation
	 * being performed.
	 * @param data A record associated with each stage of hook invocations. Each
	 *  stage is called with the data of the previous stage for a series.
	 * @param detail The result of the evaluation. This value should not be
	 * modified.
	 * @returns Data to use when executing the next state of the hook in the evaluation series. It is
	 * recommended to expand the previous input into the return. This helps ensure your stage remains
	 * compatible moving forward as more stages are added.
	 * ```js
	 * return {...data, "my-new-field": /*my data/*}
	 * ```
	 */
	afterEvaluation?(
		hookContext: EvaluationSeriesContextWithMethod,
		data: EvaluationSeriesData,
		detail: {
			/**
			 * The result of the flag evaluation. This will be either one of the flag's variations or
			 * the default value that was passed to `LDClient.variationDetail`.
			 */
			value: LDFlagValue
			/**
			 * The index of the returned value within the flag's list of variations, e.g. 0 for the
			 * first variation-- or `null` if the default value was returned.
			 */
			variationIndex?: number | null
			/**
			 * An object describing the main factor that influenced the flag evaluation value.
			 */
			reason?: LDEvaluationReason | null
		},
	): EvaluationSeriesData
}
