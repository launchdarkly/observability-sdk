import { Attributes, Exception, Span } from '@opentelemetry/api'
import {
	ATTR_EXCEPTION_MESSAGE,
	ATTR_EXCEPTION_STACKTRACE,
	ATTR_EXCEPTION_TYPE,
} from '@opentelemetry/semantic-conventions'
import { internalLogOnce } from '../../sdk/util'

const EXCEPTION_EVENT_NAME = 'exception'

/**
 * The `span.recordException` method doesn't allow for additional attributes to be added to the event. This function
 * copies the logic from the `span.recordException` method and allows adding extra attributes to the event.
 *
 * @param span The span to add the event to. If the span is undefined, then the function does nothing.
 * @param exception The exception to record.
 * @param extraAttributes Additional attributes to add to the event.
 */
const recordException = function (
	span: Span | undefined,
	exception: Exception,
	extraAttributes: Attributes,
) {
	if (!span) {
		return
	}
	const attributes: Attributes = { ...extraAttributes }
	if (typeof exception === 'string') {
		attributes[ATTR_EXCEPTION_MESSAGE] = exception
	} else if (exception) {
		if (exception.code) {
			attributes[ATTR_EXCEPTION_TYPE] = exception.code.toString()
		} else if (exception.name) {
			attributes[ATTR_EXCEPTION_TYPE] = exception.name
		}
		if (exception.message) {
			attributes[ATTR_EXCEPTION_MESSAGE] = exception.message
		}
		if (exception.stack) {
			attributes[ATTR_EXCEPTION_STACKTRACE] = exception.stack
		}
	}
	// these are minimum requirements from spec
	if (attributes[ATTR_EXCEPTION_TYPE] || attributes[ATTR_EXCEPTION_MESSAGE]) {
		span.addEvent(EXCEPTION_EVENT_NAME, attributes)
	} else {
		internalLogOnce(
			'otel',
			'recordException',
			'warn',
			`Failed to record an exception ${exception}`,
		)
	}
}

export { recordException }
