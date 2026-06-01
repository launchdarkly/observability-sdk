import { describe, it, expect, beforeEach, vi } from 'vitest'
import { _LDObserve } from './LDObserve'
import { ObservabilityClient } from '../client/ObservabilityClient'
import {
	LD_TRACK_SPAN_NAME,
	FEATURE_FLAG_PROVIDER_ATTR,
	FEATURE_FLAG_SET_ID_ATTR,
	FEATURE_FLAG_APP_ID_ATTR,
	FEATURE_FLAG_APP_VERSION_ATTR,
} from '../constants/featureFlags'
import {
	ATTR_TELEMETRY_SDK_NAME,
	ATTR_TELEMETRY_SDK_VERSION,
} from '@opentelemetry/semantic-conventions'

type CapturedSpan = {
	name: string
	attributes: Record<string, unknown>
	ended: boolean
}

function makeFakeSpan(captured: CapturedSpan) {
	return {
		setAttributes: (attrs: Record<string, unknown>) => {
			Object.assign(captured.attributes, attrs)
		},
		setStatus: (_status: { code: number }) => {},
		end: () => {
			captured.ended = true
		},
	}
}

function installFakeTracer(): {
	capturedSpans: CapturedSpan[]
	restore: () => void
} {
	const capturedSpans: CapturedSpan[] = []
	const sdkAny = (_LDObserve as any)._sdk
	const originalStartSpan = sdkAny.startSpan.bind(sdkAny)
	sdkAny.startSpan = (
		spanName: string,
		_options?: unknown,
		_ctx?: unknown,
	) => {
		const captured: CapturedSpan = {
			name: spanName,
			attributes: {},
			ended: false,
		}
		capturedSpans.push(captured)
		return makeFakeSpan(captured)
	}
	return {
		capturedSpans,
		restore: () => {
			sdkAny.startSpan = originalStartSpan
		},
	}
}

describe('LDObserve Buffering', () => {
	beforeEach(() => {
		_LDObserve._resetForTesting()
	})

	describe('Method Calls Before Initialization', () => {
		it('should buffer recordError calls when not initialized', async () => {
			const error = new Error('Test error')
			const attributes = { test: 'value' }

			_LDObserve.recordError(error, attributes)

			let bufferStatus = _LDObserve._getBufferStatus()
			expect(bufferStatus.bufferSize).toBe(1)
			expect(bufferStatus.isLoaded).toBe(false)
			expect(bufferStatus.buffer[0].method).toBe('consumeCustomError')

			const client = new ObservabilityClient('sdkKey', {})
			_LDObserve._init(client)

			bufferStatus = _LDObserve._getBufferStatus()
			expect(bufferStatus.bufferSize).toBe(0)
		})

		it('should buffer recordLog calls when not initialized', async () => {
			const message = 'Test log message'
			const level = 'info'
			const attributes = { test: 'value' }

			_LDObserve.recordLog(message, level, attributes)

			let bufferStatus = _LDObserve._getBufferStatus()
			expect(bufferStatus.bufferSize).toBe(1)
			expect(bufferStatus.isLoaded).toBe(false)
			expect(bufferStatus.buffer[0].method).toBe('recordLog')

			_LDObserve._init(new ObservabilityClient('sdkKey', {}))

			bufferStatus = _LDObserve._getBufferStatus()
			expect(bufferStatus.bufferSize).toBe(0)
		})

		it('should buffer startSpan calls when not initialized', async () => {
			const spanName = 'Test span'
			const attributes = { test: 'value' }

			_LDObserve.startSpan(spanName, { attributes })

			let bufferStatus = _LDObserve._getBufferStatus()
			expect(bufferStatus.bufferSize).toBe(1)
			expect(bufferStatus.isLoaded).toBe(false)
			expect(bufferStatus.buffer[0].method).toBe('startSpan')

			_LDObserve._init(new ObservabilityClient('sdkKey', {}))

			bufferStatus = _LDObserve._getBufferStatus()
			expect(bufferStatus.bufferSize).toBe(0)
		})

		it('should return a functional no-op span when not initialized', () => {
			const spanName = 'Test span'
			const attributes = { test: 'value' }

			const span = _LDObserve.startSpan(spanName, { attributes })

			expect(() => {
				span.setAttribute('key', 'value')
				span.setAttributes({ multiple: 'attributes' })
				span.addEvent('test-event')
				span.setStatus({ code: 1 })
				span.updateName('new-name')
				span.recordException(new Error('test'))
				span.end()
			}).not.toThrow()

			expect(span.isRecording()).toBe(false)

			const context = span.spanContext()
			expect(context.traceId).toBe('00000000000000000000000000000000')
			expect(context.spanId).toBe('0000000000000000')
			expect(context.traceFlags).toBe(0)
		})

		it('should handle startActiveSpan callback with no-op span when not initialized', () => {
			const spanName = 'Test active span'
			let callbackSpan: any = null

			const result = _LDObserve.startActiveSpan(spanName, (span) => {
				callbackSpan = span

				span.setAttribute('key', 'value')
				span.setStatus({ code: 1 })
				span.end()

				return 'callback-result'
			})

			expect(result).toBe('callback-result')

			expect(callbackSpan).toBeTruthy()
			expect(callbackSpan.isRecording()).toBe(false)
		})

		it('should buffer track calls when not initialized', () => {
			_LDObserve.track('button.click', { foo: 'bar' }, 1)

			let bufferStatus = _LDObserve._getBufferStatus()
			expect(bufferStatus.bufferSize).toBe(1)
			expect(bufferStatus.isLoaded).toBe(false)
			expect(bufferStatus.buffer[0].method).toBe('track')

			_LDObserve._init(new ObservabilityClient('sdkKey', {}))

			bufferStatus = _LDObserve._getBufferStatus()
			expect(bufferStatus.bufferSize).toBe(0)
		})
	})

	describe('track', () => {
		beforeEach(() => {
			_LDObserve._resetForTesting()
			_LDObserve._init(new ObservabilityClient('sdkKey', {}))
		})

		it('emits a launchdarkly.track span with required attributes', () => {
			;(_LDObserve as any)._sdk._setMetaAttributes({
				[ATTR_TELEMETRY_SDK_NAME]:
					'@launchdarkly/observability-react-native',
				[ATTR_TELEMETRY_SDK_VERSION]: '1.2.3',
				[FEATURE_FLAG_SET_ID_ATTR]: 'mobile-key-1',
				[FEATURE_FLAG_PROVIDER_ATTR]: 'LaunchDarkly',
				[FEATURE_FLAG_APP_ID_ATTR]: 'my-app',
				[FEATURE_FLAG_APP_VERSION_ATTR]: '2.0.0',
			})

			const { capturedSpans, restore } = installFakeTracer()
			try {
				_LDObserve.track('button.click')
			} finally {
				restore()
			}

			expect(capturedSpans).toHaveLength(1)
			const span = capturedSpans[0]
			expect(span.name).toBe(LD_TRACK_SPAN_NAME)
			expect(span.ended).toBe(true)
			expect(span.attributes).toMatchObject({
				key: 'button.click',
				[ATTR_TELEMETRY_SDK_NAME]:
					'@launchdarkly/observability-react-native',
				[ATTR_TELEMETRY_SDK_VERSION]: '1.2.3',
				[FEATURE_FLAG_SET_ID_ATTR]: 'mobile-key-1',
				[FEATURE_FLAG_PROVIDER_ATTR]: 'LaunchDarkly',
				[FEATURE_FLAG_APP_ID_ATTR]: 'my-app',
				[FEATURE_FLAG_APP_VERSION_ATTR]: '2.0.0',
			})
		})

		it('omits the value attribute when metricValue is undefined', () => {
			const { capturedSpans, restore } = installFakeTracer()
			try {
				_LDObserve.track('feature.opened')
			} finally {
				restore()
			}

			expect(capturedSpans).toHaveLength(1)
			expect('value' in capturedSpans[0].attributes).toBe(false)
		})

		it('includes the value attribute when metricValue is provided', () => {
			const { capturedSpans, restore } = installFakeTracer()
			try {
				_LDObserve.track('purchase', undefined, 42)
			} finally {
				restore()
			}

			expect(capturedSpans).toHaveLength(1)
			expect(capturedSpans[0].attributes['value']).toBe(42)
		})

		it('does not spread non-object data and does not throw on null/undefined', () => {
			const { capturedSpans, restore } = installFakeTracer()
			try {
				expect(() => _LDObserve.track('e1', null)).not.toThrow()
				expect(() => _LDObserve.track('e2', undefined)).not.toThrow()
				expect(() => _LDObserve.track('e3', 'a-string')).not.toThrow()
				expect(() => _LDObserve.track('e4', 7 as unknown)).not.toThrow()
			} finally {
				restore()
			}

			expect(capturedSpans).toHaveLength(4)
			for (const s of capturedSpans) {
				expect('foo' in s.attributes).toBe(false)
			}
		})

		it('spreads data properties as individual attributes when data is an object', () => {
			const { capturedSpans, restore } = installFakeTracer()
			try {
				_LDObserve.track('thing.happened', { foo: 'bar', n: 42 })
			} finally {
				restore()
			}

			expect(capturedSpans).toHaveLength(1)
			expect(capturedSpans[0].attributes).toMatchObject({
				key: 'thing.happened',
				foo: 'bar',
				n: 42,
			})
		})

		it('emits the cached context-key attributes set via _setLDContextKeyAttributes', () => {
			;(_LDObserve as any)._sdk._setLDContextKeyAttributes({
				user: 'alice',
				org: 'team-a',
			})

			const { capturedSpans, restore } = installFakeTracer()
			try {
				_LDObserve.track('event')
			} finally {
				restore()
			}

			expect(capturedSpans).toHaveLength(1)
			expect(capturedSpans[0].attributes).toMatchObject({
				user: 'alice',
				org: 'team-a',
				key: 'event',
			})
		})

		it('omits url.full on mobile', () => {
			const { capturedSpans, restore } = installFakeTracer()
			try {
				_LDObserve.track('event')
			} finally {
				restore()
			}

			expect(capturedSpans).toHaveLength(1)
			expect('url.full' in capturedSpans[0].attributes).toBe(false)
		})

		it('does not emit a span when productAnalytics is false', () => {
			_LDObserve._resetForTesting()
			_LDObserve._init(
				new ObservabilityClient('sdkKey', {
					productAnalytics: false,
				}),
			)

			const { capturedSpans, restore } = installFakeTracer()
			try {
				_LDObserve.track('event')
			} finally {
				restore()
			}

			expect(capturedSpans).toHaveLength(0)
		})

		it('does not emit a span when productAnalytics.trackEvents is false', () => {
			_LDObserve._resetForTesting()
			_LDObserve._init(
				new ObservabilityClient('sdkKey', {
					productAnalytics: { trackEvents: false },
				}),
			)

			const { capturedSpans, restore } = installFakeTracer()
			try {
				_LDObserve.track('event')
			} finally {
				restore()
			}

			expect(capturedSpans).toHaveLength(0)
		})

		it('emits a span when productAnalytics is true', () => {
			_LDObserve._resetForTesting()
			_LDObserve._init(
				new ObservabilityClient('sdkKey', {
					productAnalytics: true,
				}),
			)

			const { capturedSpans, restore } = installFakeTracer()
			try {
				_LDObserve.track('event')
			} finally {
				restore()
			}

			expect(capturedSpans).toHaveLength(1)
		})

		it('emits a span when productAnalytics.trackEvents is true', () => {
			_LDObserve._resetForTesting()
			_LDObserve._init(
				new ObservabilityClient('sdkKey', {
					productAnalytics: { trackEvents: true },
				}),
			)

			const { capturedSpans, restore } = installFakeTracer()
			try {
				_LDObserve.track('event')
			} finally {
				restore()
			}

			expect(capturedSpans).toHaveLength(1)
		})

		it('returns normally when the tracer throws inside startSpan', () => {
			const sdkAny = (_LDObserve as any)._sdk
			const originalStartSpan = sdkAny.startSpan.bind(sdkAny)
			sdkAny.startSpan = () => {
				throw new Error('tracer-boom')
			}

			try {
				expect(() => _LDObserve.track('event')).not.toThrow()
			} finally {
				sdkAny.startSpan = originalStartSpan
			}
		})
	})
})
