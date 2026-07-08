/**
 * Portable, asynchronous key/value persistence for the session so it can be
 * resumed across a JS reload. Works across React Native targets with a single
 * code path:
 *
 *   - Native (iOS/Android): `@react-native-async-storage/async-storage`. It is a
 *     *required* peer dependency — session preservation (and keeping the JS
 *     `session.id` aligned with native session replay across a reload) depends
 *     on it. The `require` still lives in a `try/catch` so Metro tolerates it on
 *     targets where the native module is absent (e.g. web) and bundling never
 *     hard-fails.
 *   - React Native for Web / browser: `localStorage` (also what AsyncStorage is
 *     backed by on web), wrapped in the async interface.
 *   - Anything else / not installed: a no-op store, so session preservation is
 *     simply disabled and id generation never hard-fails. This is a degraded
 *     fallback; callers warn when they land here (see SessionManager).
 */
export interface SessionStore {
	getItem(key: string): Promise<string | null>
	setItem(key: string, value: string): Promise<void>
	removeItem(key: string): Promise<void>
	/**
	 * Whether this store actually persists. `false` for the no-op store, which
	 * lets callers skip reload detection entirely.
	 */
	readonly isPersistent: boolean
}

const noOpStore: SessionStore = {
	getItem: async () => null,
	setItem: async () => {},
	removeItem: async () => {},
	isPersistent: false,
}

type AsyncStorageLike = {
	getItem(key: string): Promise<string | null>
	setItem(key: string, value: string): Promise<void>
	removeItem(key: string): Promise<void>
}

function resolveAsyncStorage(): AsyncStorageLike | undefined {
	try {
		// Optional dependency. The try/catch marks this as an optional require
		// for Metro, so apps that do not install AsyncStorage still bundle.
		const mod = require('@react-native-async-storage/async-storage')
		const candidate = (mod?.default ?? mod) as AsyncStorageLike | undefined
		if (
			candidate &&
			typeof candidate.getItem === 'function' &&
			typeof candidate.setItem === 'function' &&
			typeof candidate.removeItem === 'function'
		) {
			return candidate
		}
	} catch {
		// Not installed — fall through to other backends.
	}
	return undefined
}

type WebLocalStorage = {
	getItem(key: string): string | null
	setItem(key: string, value: string): void
	removeItem(key: string): void
}

function resolveLocalStorage(): WebLocalStorage | undefined {
	try {
		const candidate = (globalThis as { localStorage?: WebLocalStorage })
			.localStorage
		if (
			candidate &&
			typeof candidate.getItem === 'function' &&
			typeof candidate.setItem === 'function' &&
			typeof candidate.removeItem === 'function'
		) {
			return candidate
		}
	} catch {
		// Accessing localStorage can throw (e.g. disabled cookies) — ignore.
	}
	return undefined
}

/**
 * Resolves the best available persistent store for this runtime, falling back to
 * a no-op store when none is available.
 */
export function createSessionStore(): SessionStore {
	const asyncStorage = resolveAsyncStorage()
	if (asyncStorage) {
		return {
			isPersistent: true,
			getItem: (key) => asyncStorage.getItem(key),
			setItem: (key, value) => asyncStorage.setItem(key, value),
			removeItem: (key) => asyncStorage.removeItem(key),
		}
	}

	const localStorage = resolveLocalStorage()
	if (localStorage) {
		return {
			isPersistent: true,
			getItem: async (key) => localStorage.getItem(key),
			setItem: async (key, value) => localStorage.setItem(key, value),
			removeItem: async (key) => localStorage.removeItem(key),
		}
	}

	return noOpStore
}

export { noOpStore }
