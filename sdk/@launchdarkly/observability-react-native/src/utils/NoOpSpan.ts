import {
	Attributes,
	Span as OtelSpan,
	SpanOptions,
	SpanContext,
	SpanStatus,
	TimeInput,
	Link,
} from '@opentelemetry/api'

/**
 * No-op span implementation that safely handles all span methods
 * Used when the observability client is not yet initialized
 */
export class NoOpSpan implements OtelSpan {
	constructor() {
		this.setAttribute('noop', true)
	}

	spanContext(): SpanContext {
		return {
			traceId: '00000000000000000000000000000000',
			spanId: '0000000000000000',
			traceFlags: 0,
		}
	}

	setAttribute(key: string, value: any): this {
		return this
	}

	setAttributes(attributes: Attributes): this {
		return this
	}

	addEvent(name: string, attributes?: Attributes): this {
		return this
	}

	addLink(link: Link): this {
		return this
	}

	addLinks(links: Link[]): this {
		return this
	}

	setStatus(status: SpanStatus): this {
		return this
	}

	updateName(name: string): this {
		return this
	}

	end(endTime?: TimeInput): void {
		// No-op
	}

	recordException(exception: Error, time?: TimeInput): void {
		// No-op
	}

	isRecording(): boolean {
		return false
	}
}

// Singleton no-op span instance
export const noOpSpan = new NoOpSpan()
