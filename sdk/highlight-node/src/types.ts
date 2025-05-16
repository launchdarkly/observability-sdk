import { Attributes } from '@opentelemetry/api'
import { HighlightOptions } from 'highlight.run'

export interface NodeOptions extends HighlightOptions {
	/**
	 * ID used to associate payloads with a Highlight project.
	 */
	projectID: string

	/**
	 * The endpoint string to send OTLP HTTP data to.
	 * @default https://otel.highlight.io:4318
	 */
	otlpEndpoint?: string

	/**
	 * This app's service name.
	 */
	serviceName?: string

	/**
	 * This app's version ideally set to the latest deployed git SHA.
	 */
	serviceVersion?: string

	/**
	 * Specifies the environment your application is running in.
	 * This is useful to distinguish whether your session was recorded on localhost or in production.
	 */
	environment?: 'development' | 'staging' | 'production' | string

	/**
	 * Enables node fs instrumentation @default false
	 * see .
	 * {@link https://opentelemetry.io/docs/instrumentation/js/libraries/#registration}
	 */
	enableFsInstrumentation?: boolean

	/**
	 * Attributes to be added to the OpenTelemetry Resource.
	 */
	attributes?: Attributes

	/**
	 * Set to try to serialize console object arguments into the message body.
	 */
	serializeConsoleAttributes?: boolean

	/**
	 * Turn off enhanced attribute reporting for pg instrumentation.
	 * See the following for additional details.
	 * https://github.com/open-telemetry/opentelemetry-js-contrib/blob/64fcbf3b70e7293e143266838ff94b94cf2c30da/plugins/node/opentelemetry-instrumentation-pg/src/types.ts#L52
	 */
	disablePgInstrumentationAttributes?: boolean
}

export interface HighlightContext {
	secureSessionId: string | undefined
	requestId: string | undefined
}

export declare interface Metric {
	name: string
	value: number
	tags?: { name: string; value: string }[]
}
