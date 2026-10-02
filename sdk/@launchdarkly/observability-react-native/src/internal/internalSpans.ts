import { Span as OtelSpan, SpanOptions, trace } from '@opentelemetry/api'

const DEFAULT_SERVICE_NAME = 'launchdarkly-observability-react-native'

/**
 * SDK-internal spans bypass {@link ReactNativeOptions.disableTraces}, which
 * applies only to public custom tracing APIs.
 */
export function startInternalActiveSpan<T>(
	serviceName: string | undefined,
	name: string,
	fn: (span: OtelSpan) => T,
	options?: SpanOptions,
): T {
	return trace
		.getTracerProvider()
		.getTracer(serviceName ?? DEFAULT_SERVICE_NAME)
		.startActiveSpan(name, options ?? {}, fn)
}
