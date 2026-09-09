import { beforeEach, describe, expect, it, vi } from 'vitest'
import { LDObserve } from '../sdk/LDObserve'
import { LDRecord } from '../sdk/LDRecord'
import type { OTelMetric as Metric } from '../client/types/types'
import type { Attributes } from '@opentelemetry/api'
import { ObserveSDK } from '../sdk/observe'
import { RecordSDK } from '../sdk/record'
import { globalStorage } from '../client/utils/storage'
import { Record } from '../plugins/record'
import * as otel from '../client/otel'

// `graphqlSDK` is an ECMAScript-private field on the SDK classes (so its
// GraphQL types don't leak into the published declarations), which means tests
// can no longer assign it directly. Stub the only seam — the generated
// `getSdk` factory — so the classes never hit the network during unit tests.
vi.mock('../client/graph/generated/operations', async (importOriginal) => {
	const actual =
		await importOriginal<
			typeof import('../client/graph/generated/operations')
		>()
	return {
		...actual,
		getSdk: () => ({
			initializeSession: vi.fn().mockResolvedValue({
				initializeSession: {
					secure_id: 'test-session',
					project_id: '1',
				},
			}),
			GetSamplingConfig: vi
				.fn()
				.mockResolvedValue({ sampling: undefined }),
			PushSessionEvents: vi.fn().mockResolvedValue({}),
			pushMetrics: vi.fn().mockResolvedValue({}),
			identifySession: vi.fn().mockResolvedValue({}),
			addSessionProperties: vi.fn().mockResolvedValue({}),
			addSessionFeedback: vi.fn().mockResolvedValue({}),
		}),
	}
})

describe('SDK', () => {
	let observe: typeof LDObserve
	let record: typeof LDRecord
	let observeImpl: ObserveSDK
	let recordImpl: RecordSDK

	beforeEach(() => {
		// Reset the instances before each test
		observe = LDObserve
		record = LDRecord
		observeImpl = new ObserveSDK({
			backendUrl: 'https://pub.highlight.io',
			otel: {
				otlpEndpoint: 'https://otel.highlight.io',
			},
			projectId: '1',
			sessionSecureId: 'test-session',
			environment: 'test',
		})
		recordImpl = new RecordSDK({
			organizationID: '1',
			environment: 'test',
			sessionSecureID: 'test-session',
		})

		// graphqlSDK is stubbed via the vi.mock of getSdk above.
		observe.load(observeImpl)
		record.load(recordImpl)
	})

	describe('Record Methods', () => {
		it('should handle start and stop', async () => {
			const mockStart = vi.spyOn(recordImpl, 'start')
			const mockStop = vi.spyOn(recordImpl, 'stop')

			await record.start()
			record.stop()

			expect(mockStart).toHaveBeenCalled()
			expect(mockStop).toHaveBeenCalled()
		})

		it('should handle snapshot', async () => {
			const mockSnapshot = vi.spyOn(recordImpl, 'snapshot')

			const canvas = document.createElement('canvas')
			await record.snapshot(canvas)

			expect(mockSnapshot).toHaveBeenCalledWith(canvas)
		})

		it('should construct despite error with storage fallback', async () => {
			vi.stubGlobal('localStorage', {
				getItem: vi.fn(() => {
					throw new Error('get error')
				}),
				setItem: vi.fn(() => {
					throw new Error('set error')
				}),
			})
			const sdk = new Record()
			// trigger sdk to initialize
			sdk.getHooks?.({ sdkKey: 'abc123', sdk: { name: '', version: '' } })
			expect(sdk.record).toBeDefined()
			vi.unstubAllGlobals()
		})

		it('should construct despite error', async () => {
			vi.spyOn(globalStorage, 'getItem').mockImplementation(() => {
				throw new Error('get error')
			})
			vi.stubGlobal('localStorage', {
				getItem: vi.fn(() => {
					throw new Error('get error')
				}),
				setItem: vi.fn(() => {
					throw new Error('set error')
				}),
			})
			const sdk = new Record()
			expect(sdk.record).toBeUndefined()
			vi.unstubAllGlobals()
		})
	})

	describe('Observe Methods', () => {
		it('should handle metric recording', async () => {
			const mockRecordGauge = vi.spyOn(observeImpl, 'recordGauge')
			const mockRecordCount = vi.spyOn(observeImpl, 'recordCount')
			const mockRecordIncr = vi.spyOn(observeImpl, 'recordIncr')
			const mockRecordHistogram = vi.spyOn(observeImpl, 'recordHistogram')
			const mockRecordUpDownCounter = vi.spyOn(
				observeImpl,
				'recordUpDownCounter',
			)

			const metric: Metric = {
				name: 'test.metric',
				value: 100,
				attributes: { test: 'value' },
			}

			observe.recordGauge(metric)
			observe.recordCount(metric)
			observe.recordIncr({
				name: 'test.metric',
				attributes: { test: 'value' } as Attributes,
			})
			observe.recordHistogram(metric)
			observe.recordUpDownCounter(metric)

			expect(mockRecordGauge).toHaveBeenCalledWith(metric)
			expect(mockRecordCount).toHaveBeenCalledWith(metric)
			expect(mockRecordIncr).toHaveBeenCalledWith({
				name: 'test.metric',
				attributes: { test: 'value' } as Attributes,
			})
			expect(mockRecordHistogram).toHaveBeenCalledWith(metric)
			expect(mockRecordUpDownCounter).toHaveBeenCalledWith(metric)
		})

		it('should include LD context keys in metric attributes', () => {
			const gauge = { record: vi.fn() }
			const counter = { add: vi.fn() }
			const histogram = { record: vi.fn() }
			const upDownCounter = { add: vi.fn() }
			vi.spyOn(otel, 'getMeter').mockReturnValue({
				createGauge: vi.fn().mockReturnValue(gauge),
				createCounter: vi.fn().mockReturnValue(counter),
				createHistogram: vi.fn().mockReturnValue(histogram),
				createUpDownCounter: vi.fn().mockReturnValue(upDownCounter),
			} as any)

			observeImpl.setLDContextKeyAttributes({ user: 'alice' })

			observeImpl.recordGauge({
				name: 'gauge.test',
				value: 1,
				attributes: { foo: 'bar' },
			})
			observeImpl.recordCount({
				name: 'count.test',
				value: 2,
				attributes: { foo: 'bar' },
			})
			observeImpl.recordHistogram({
				name: 'hist.test',
				value: 3,
				attributes: { foo: 'bar' },
			})
			observeImpl.recordUpDownCounter({
				name: 'updown.test',
				value: 4,
				attributes: { foo: 'bar' },
			})

			const expected = expect.objectContaining({
				'context.contextKeys.user': 'alice',
				foo: 'bar',
			})
			expect(gauge.record).toHaveBeenCalledWith(1, expected)
			expect(counter.add).toHaveBeenCalledWith(2, expected)
			expect(histogram.record).toHaveBeenCalledWith(3, expected)
			expect(upDownCounter.add).toHaveBeenCalledWith(4, expected)
		})

		it('caller attributes win over LD context keys with the same name', () => {
			const gauge = { record: vi.fn() }
			vi.spyOn(otel, 'getMeter').mockReturnValue({
				createGauge: vi.fn().mockReturnValue(gauge),
				createCounter: vi.fn(),
				createHistogram: vi.fn(),
				createUpDownCounter: vi.fn(),
			} as any)

			observeImpl.setLDContextKeyAttributes({ user: 'alice' })
			observeImpl.recordGauge({
				name: 'gauge.override',
				value: 1,
				attributes: { 'context.contextKeys.user': 'bob' },
			})

			expect(gauge.record).toHaveBeenCalledWith(
				1,
				expect.objectContaining({
					'context.contextKeys.user': 'bob',
				}),
			)
		})

		it('should handle error recording', async () => {
			const mockRecordError = vi.spyOn(observeImpl, 'recordError')

			const error = new Error('Test error')
			const payload = { errorCode: 'E123' }

			observe.recordError(error, 'Error message', payload)

			expect(mockRecordError).toHaveBeenCalledWith(
				error,
				'Error message',
				payload,
				undefined,
				undefined,
			)
		})

		describe('LD context keys propagation', () => {
			const mockTracer = (span: any) => {
				const tracer = {
					startActiveSpan: vi.fn((_name: string, ...args: any[]) => {
						const callback = args[args.length - 1]
						return callback(span)
					}),
				}
				vi.spyOn(otel, 'getTracer').mockReturnValue(tracer as any)
				return tracer
			}

			const makeSpan = () => ({
				setAttributes: vi.fn(),
				setAttribute: vi.fn(),
				addEvent: vi.fn(),
				recordException: vi.fn(),
				setStatus: vi.fn(),
				end: vi.fn(),
				spanContext: vi.fn(() => ({
					traceId: 'trace',
					spanId: 'span',
					traceFlags: 1,
				})),
				updateName: vi.fn(),
				isRecording: vi.fn(() => true),
			})

			it('attaches context keys to error span attributes', () => {
				const span = makeSpan()
				mockTracer(span)

				observeImpl.setLDContextKeyAttributes({
					pathTemplate: '/projects/:projectKey/flags',
					owner: 'team-fm-foundations',
				})

				observeImpl.recordError(new Error('boom'), 'oops', {
					errorCode: 'E123',
				})

				// First call: contextKeys injected by the startSpan wrapper.
				expect(span.setAttributes).toHaveBeenNthCalledWith(1, {
					'context.contextKeys.pathTemplate':
						'/projects/:projectKey/flags',
					'context.contextKeys.owner': 'team-fm-foundations',
				})
				// Second call: error-specific attributes from _recordErrorMessage.
				expect(span.setAttributes).toHaveBeenNthCalledWith(
					2,
					expect.objectContaining({
						event: expect.stringContaining('oops'),
						errorCode: 'E123',
					}),
				)
			})

			it('caller payload wins over context keys with the same name', () => {
				const span = makeSpan()
				mockTracer(span)

				observeImpl.setLDContextKeyAttributes({ owner: 'team-a' })

				observeImpl.recordError(new Error('boom'), 'oops', {
					'context.contextKeys.owner': 'team-override',
				})

				// Second setAttributes call wins on the wire because OTel
				// merges with last-write-wins semantics for matching keys.
				expect(span.setAttributes).toHaveBeenLastCalledWith(
					expect.objectContaining({
						'context.contextKeys.owner': 'team-override',
					}),
				)
			})

			it('attaches context keys to log addEvent attributes', () => {
				const span = makeSpan()
				mockTracer(span)

				observeImpl.setLDContextKeyAttributes({
					pathTemplate: '/login',
				})

				observeImpl.recordLog('hello world', 'info', {
					traceId: 'tid',
				})

				expect(span.addEvent).toHaveBeenCalledWith(
					'log',
					expect.objectContaining({
						'context.contextKeys.pathTemplate': '/login',
						traceId: 'tid',
					}),
				)
			})

			it('caller metadata wins over context keys on logs', () => {
				const span = makeSpan()
				mockTracer(span)

				observeImpl.setLDContextKeyAttributes({
					pathTemplate: '/login',
				})

				observeImpl.recordLog('hello', 'info', {
					'context.contextKeys.pathTemplate': '/override',
				})

				expect(span.addEvent).toHaveBeenCalledWith(
					'log',
					expect.objectContaining({
						'context.contextKeys.pathTemplate': '/override',
					}),
				)
			})

			it('attaches context keys to user-created spans via startSpan', () => {
				const span = makeSpan()
				mockTracer(span)

				observeImpl.setLDContextKeyAttributes({
					pathTemplate: '/projects/:projectKey/flags',
					accountId: 'acct-123',
				})

				observeImpl.startSpan('user.custom.span', (s) => {
					s?.setAttribute('foo', 'bar')
				})

				expect(span.setAttributes).toHaveBeenCalledWith({
					'context.contextKeys.pathTemplate':
						'/projects/:projectKey/flags',
					'context.contextKeys.accountId': 'acct-123',
				})
				// Caller's setAttribute call is also issued.
				expect(span.setAttribute).toHaveBeenCalledWith('foo', 'bar')
			})

			it('does not call setAttributes when no context keys are set', () => {
				const span = makeSpan()
				mockTracer(span)

				observeImpl.startSpan('user.custom.span', () => {})

				// startSpan should not inject anything when contextKeys are unset.
				expect(span.setAttributes).not.toHaveBeenCalled()
			})
		})
	})
})
