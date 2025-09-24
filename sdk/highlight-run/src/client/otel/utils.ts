import {
	Attributes,
	AttributeValue,
	Link,
	Span,
	SpanStatus,
	TimeInput,
} from '@opentelemetry/api'
import { parse } from 'graphql'
import { BrowserXHR } from '../listeners/network-listener/utils/xhr-listener'

export const getNoopSpan = () => {
	const noopSpan: Span = {
		end: () => {},
		spanContext: () => ({
			traceId: '',
			spanId: '',
			traceFlags: 0,
		}),
		setAttribute: (_key: string, _value: AttributeValue) => noopSpan,
		setAttributes: (_attributes: Attributes) => noopSpan,
		addEvent: (
			_name: string,
			_attributesOrStartTime?: Attributes | TimeInput,
			_startTime?: TimeInput,
		) => noopSpan,
		addLinks: (_links: Link[]) => noopSpan,
		setStatus: (_status: SpanStatus) => noopSpan,
		recordException: () => {},
		addLink: () => noopSpan,
		updateName: () => noopSpan,
		isRecording: () => false,
	}

	return noopSpan
}

export const getSpanName = (
	url: string,
	method: string,
	body: Request['body'] | BrowserXHR['_body'],
) => {
	let parsedBody
	const urlObject = new URL(url)
	const pathname = urlObject.pathname

	// Extract meaningful operation name from GraphQL requests
	try {
		parsedBody = typeof body === 'string' ? JSON.parse(body) : body

		if (parsedBody && parsedBody.query) {
			let operationName = 'unknown'
			// If operation name is provided in the body, use it
			if (parsedBody.operationName) {
				operationName = parsedBody.operationName
			}

			// Try to parse the query to extract operation name and type
			const query = parse(parsedBody.query)
			const operation = query.definitions[0]

			if (
				operation?.kind === 'OperationDefinition' &&
				operation.name?.value
			) {
				operationName = operation.name?.value
			}

			// Fallback for cases where parsing fails
			return `${method.toUpperCase()} ${pathname} (${operationName})`
		}
	} catch {
		// Ignore errors from JSON parsing
	}

	// Improve REST API naming
	const pathSegments = pathname.split('/').filter(Boolean)

	if (pathSegments.length > 0) {
		// Process each segment and replace IDs with parameter placeholders
		const processedSegments = pathSegments.map((segment, index) => {
			// Check if the segment looks like an ID (number, UUID, etc.)
			if (
				/^\d+$/.test(segment) || // Pure numbers
				/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
					segment,
				) || // UUIDs
				/^[0-9a-f]{8,}$/i.test(segment) // Long hex strings
			) {
				// Get the previous segment to create a meaningful parameter name
				const previousSegment =
					index > 0 ? pathSegments[index - 1] : 'resource'
				return `:${previousSegment}Id`
			}
			return segment
		})

		return `${method.toUpperCase()} /${processedSegments.join('/')}`
	} else {
		// Root path
		return `${method.toUpperCase()} /`
	}
}
