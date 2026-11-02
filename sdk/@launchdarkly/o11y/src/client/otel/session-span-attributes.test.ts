import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import type { Span } from '@opentelemetry/api'
import { setSessionData } from '../utils/sessionStorage/highlightSession'
import { setPreviousSessionSecureID } from '../utils/sessionStorage/highlightSession'
import { setSessionSpanAttributes } from './index'

describe('setSessionSpanAttributes', () => {
	beforeEach(() => {
		setSessionData({
			sessionSecureID: 'session-current',
			projectID: 1,
		})
	})

	afterEach(() => {
		setPreviousSessionSecureID(undefined)
		window.localStorage.clear()
		;(
			globalThis as { persistentSessionSecureID?: string }
		).persistentSessionSecureID = undefined
	})

	const createMockSpan = () => {
		const attributes: Record<string, unknown> = {}
		const span = {
			attributes,
			setAttribute: (key: string, value: unknown) => {
				attributes[key] = value
				return span
			},
		}
		return { span: span as unknown as Span, attributes }
	}

	it('stamps the current session id', () => {
		const { span, attributes } = createMockSpan()

		setSessionSpanAttributes(span)

		expect(attributes['highlight.session_id']).toBe('session-current')
	})

	it('stamps the previous session id when the session was replaced', () => {
		const { span, attributes } = createMockSpan()
		setPreviousSessionSecureID('session-previous')

		setSessionSpanAttributes(span)

		expect(attributes['highlight.session_id']).toBe('session-current')
		expect(attributes['session.previous_id']).toBe('session-previous')
	})

	it('omits the previous session id when there is none', () => {
		const { span, attributes } = createMockSpan()

		setSessionSpanAttributes(span)

		expect(attributes['session.previous_id']).toBeUndefined()
	})
})
