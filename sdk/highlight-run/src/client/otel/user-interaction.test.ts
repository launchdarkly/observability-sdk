import {
	type MockInstance,
	afterEach,
	beforeEach,
	describe,
	expect,
	it,
	vi,
} from 'vitest'
import { UserInteractionInstrumentation } from './user-interaction'

describe('UserInteractionInstrumentation history span names', () => {
	let instrumentation: UserInteractionInstrumentation
	let updateInteractionName: MockInstance<(url: string) => void>

	const lastSpanNameUrl = () => {
		expect(updateInteractionName).toHaveBeenCalled()
		return updateInteractionName.mock.calls.at(-1)![0]
	}

	beforeEach(() => {
		window.history.replaceState({}, '', '/')
		instrumentation = new UserInteractionInstrumentation({ enabled: false })
		updateInteractionName = vi.spyOn(
			instrumentation,
			'_updateInteractionName',
		)
		instrumentation._patchHistoryApi()
	})

	afterEach(() => {
		instrumentation._unpatchHistoryApi()
		vi.restoreAllMocks()
		window.history.replaceState({}, '', '/')
	})

	it('strips a token-bearing fragment even when a benign query is present', () => {
		window.history.pushState(
			{},
			'',
			'/callback?returnTo=%2Fhome#access_token=SECRET',
		)

		const url = lastSpanNameUrl()
		expect(url).not.toContain('SECRET')
		expect(url).toBe('/callback?returnTo=%2Fhome')
	})

	it('redacts sensitive query params even when a fragment is present', () => {
		window.history.pushState({}, '', '/download?sig=SECRET#section')

		const url = lastSpanNameUrl()
		expect(url).not.toContain('SECRET')
		expect(url).toBe('/download?sig=REDACTED#section')
	})

	it('preserves benign paths, params, and anchors', () => {
		window.history.pushState({}, '', '/users?page=2&sort=name#section')

		expect(lastSpanNameUrl()).toBe('/users?page=2&sort=name#section')
	})

	it('does not rename the span when the URL is unchanged', () => {
		window.history.replaceState({}, '', '/users?page=2')
		updateInteractionName.mockClear()

		window.history.replaceState({}, '', '/users?page=2')

		expect(updateInteractionName).not.toHaveBeenCalled()
	})
})
