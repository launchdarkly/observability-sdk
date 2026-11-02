import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
	InMemorySpanExporter,
	SimpleSpanProcessor,
	WebTracerProvider,
} from '@opentelemetry/sdk-trace-web'
import { UserInteractionInstrumentation } from './user-interaction'

// vitest's jsdom environment gives `window` its own addEventListener, which
// skips the EventTarget.prototype patch, so `document` stands in for the
// outer target that bubbled events reach last.

describe('UserInteractionInstrumentation addEventListener patch', () => {
	let instrumentation: UserInteractionInstrumentation
	let exporter: InMemorySpanExporter
	let cleanups: (() => void)[]

	// Adds a listener and removes it after the test so listeners don't leak
	// onto document between tests.
	const listen = (
		target: EventTarget,
		type: string,
		listener: EventListener,
		options?: boolean | AddEventListenerOptions,
	) => {
		target.addEventListener(type, listener, options)
		cleanups.push(() => target.removeEventListener(type, listener))
	}

	const mouseup = (target: EventTarget) =>
		target.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }))

	beforeEach(() => {
		vi.useFakeTimers({ toFake: ['Date'] })
		vi.setSystemTime(1_000_000)
		cleanups = []
		exporter = new InMemorySpanExporter()
		const provider = new WebTracerProvider({
			spanProcessors: [new SimpleSpanProcessor(exporter)],
		})
		instrumentation = new UserInteractionInstrumentation({
			enabled: false,
		})
		instrumentation.setTracerProvider(provider)
		instrumentation.enable()
	})

	afterEach(() => {
		cleanups.forEach((cleanup) => cleanup())
		instrumentation.disable()
		document.body.innerHTML = ''
		vi.useRealTimers()
	})

	it('re-adds a once listener after an earlier listener for the same event type ran', () => {
		let fired = 0
		const onUp = () => {
			fired += 1
		}
		// Runs before the document listener on every dispatch, like React's
		// root listener does before a window listener.
		listen(document.body, 'mouseup', () => {})

		for (let i = 0; i < 3; i++) {
			listen(document, 'mouseup', onUp, { once: true })
			mouseup(document.body)
			vi.advanceTimersByTime(100)
		}

		expect(fired).toBe(3)
	})

	it('cleans up a once listener whose own invocation is throttled', () => {
		let fired = 0
		const onUp = () => {
			fired += 1
		}
		const other = document.createElement('div')
		document.body.appendChild(other)
		listen(other, 'mouseup', () => {})

		listen(document, 'mouseup', onUp, { once: true })
		// Doesn't bubble, so only the listener on `other` runs.
		other.dispatchEvent(new MouseEvent('mouseup'))
		expect(fired).toBe(0)
		vi.advanceTimersByTime(5)
		// Within the throttle window of the previous mouseup.
		mouseup(document)
		expect(fired).toBe(1)

		vi.advanceTimersByTime(5)
		listen(document, 'mouseup', onUp, { once: true })
		mouseup(document)
		expect(fired).toBe(2)
	})

	it('cleans up a once listener invoked without an event type', () => {
		let fired = 0
		const onEvent = () => {
			fired += 1
		}

		for (let i = 0; i < 2; i++) {
			listen(document, '', onEvent, { once: true })
			document.dispatchEvent(new Event(''))
		}

		expect(fired).toBe(2)
	})

	it('handles removeEventListener after a once listener fired', () => {
		let fired = 0
		const onUp = () => {
			fired += 1
		}
		listen(document.body, 'mouseup', () => {})

		document.addEventListener('mouseup', onUp, { once: true })
		mouseup(document.body)
		expect(fired).toBe(1)
		expect(() =>
			document.removeEventListener('mouseup', onUp),
		).not.toThrow()

		vi.advanceTimersByTime(100)
		document.addEventListener('mouseup', onUp)
		mouseup(document.body)
		expect(fired).toBe(2)

		vi.advanceTimersByTime(100)
		document.removeEventListener('mouseup', onUp)
		mouseup(document.body)
		expect(fired).toBe(2)
	})

	it('still dedupes a listener added twice without once', () => {
		let fired = 0
		const onUp = () => {
			fired += 1
		}

		document.addEventListener('mouseup', onUp)
		document.addEventListener('mouseup', onUp)
		mouseup(document)
		expect(fired).toBe(1)

		vi.advanceTimersByTime(100)
		document.removeEventListener('mouseup', onUp)
		mouseup(document)
		expect(fired).toBe(1)
	})

	it('throttles span creation for events of the same type', () => {
		let fired = 0
		const button = document.createElement('button')
		document.body.appendChild(button)
		listen(button, 'click', () => {
			fired += 1
		})
		listen(
			document,
			'click',
			() => {
				fired += 1
			},
			{ once: true },
		)

		// The document listener runs within the throttle window of the button
		// listener, so only the button listener creates a span.
		button.click()
		vi.advanceTimersByTime(5)
		button.click()
		expect(fired).toBe(3)
		expect(exporter.getFinishedSpans()).toHaveLength(1)

		vi.advanceTimersByTime(100)
		button.click()
		expect(fired).toBe(4)
		expect(exporter.getFinishedSpans()).toHaveLength(2)
		expect(exporter.getFinishedSpans()[0].name).toBe('click')
	})
})
