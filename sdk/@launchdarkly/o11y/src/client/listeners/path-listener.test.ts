import { afterEach, describe, expect, it, vi } from 'vitest'
import { PathListener } from './path-listener'

describe('PathListener', () => {
	const unsubscribes: Array<() => void> = []
	const subscribe = (callback: (url: string) => void) => {
		const unsubscribe = PathListener(callback)
		unsubscribes.push(unsubscribe)
		return unsubscribe
	}

	afterEach(() => {
		unsubscribes.splice(0).forEach((unsubscribe) => unsubscribe())
		window.history.replaceState({}, '', '/')
	})

	it('calls back with the current URL and on pushState, replaceState and popstate', () => {
		const callback = vi.fn()
		subscribe(callback)
		window.history.pushState({}, '', '/a')
		window.history.replaceState({}, '', '/b')
		window.dispatchEvent(new PopStateEvent('popstate'))
		expect(
			callback.mock.calls.map(([url]) => new URL(url).pathname),
		).toEqual(['/', '/a', '/b', '/b'])
	})

	it('wraps history once however many subscribers there are', () => {
		const originalPushState = window.history.pushState
		const locationChanges = vi.fn()
		window.addEventListener('locationchange', locationChanges)
		const first = vi.fn()
		const second = vi.fn()
		const third = vi.fn()
		subscribe(first)
		const wrapped = window.history.pushState
		expect(wrapped).not.toBe(originalPushState)
		subscribe(second)
		subscribe(third)
		expect(window.history.pushState).toBe(wrapped)

		window.history.pushState({}, '', '/a')

		expect(locationChanges).toHaveBeenCalledTimes(1)
		for (const callback of [first, second, third]) {
			expect(callback).toHaveBeenCalledTimes(2)
		}
		window.removeEventListener('locationchange', locationChanges)
	})

	it('restores history once the last subscriber unsubscribes', () => {
		const originalPushState = window.history.pushState
		const originalReplaceState = window.history.replaceState
		const first = vi.fn()
		const unsubscribeFirst = subscribe(first)
		const unsubscribeSecond = subscribe(vi.fn())

		unsubscribeFirst()
		unsubscribeFirst()
		expect(window.history.pushState).not.toBe(originalPushState)
		window.history.pushState({}, '', '/a')
		expect(first).toHaveBeenCalledTimes(1)

		unsubscribeSecond()
		expect(window.history.pushState).toBe(originalPushState)
		expect(window.history.replaceState).toBe(originalReplaceState)
	})

	it('leaves a wrap installed on top in place and stops dispatching', () => {
		const original = window.history.pushState
		const locationChanges = vi.fn()
		window.addEventListener('locationchange', locationChanges)
		const unsubscribe = subscribe(vi.fn())
		const ours = window.history.pushState
		const theirs = vi.fn(function (this: History, ...args: any[]) {
			return (ours as any).apply(this, args)
		})
		window.history.pushState = theirs as any

		unsubscribe()
		expect(window.history.pushState).toBe(theirs)
		window.history.pushState({}, '', '/a')
		expect(theirs).toHaveBeenCalledTimes(1)
		expect(locationChanges).not.toHaveBeenCalled()
		expect(window.location.pathname).toBe('/a')

		window.removeEventListener('locationchange', locationChanges)
		window.history.pushState = original
	})
})
