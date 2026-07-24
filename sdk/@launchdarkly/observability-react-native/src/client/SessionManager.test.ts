import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppState } from 'react-native'
import { SessionManager } from './SessionManager'
import { SessionStore } from './storage/sessionStore'
import {
	SESSION_RESUME_THRESHOLD_MS,
	SESSION_STORAGE_KEY,
} from '../constants/sessions'

vi.mock('react-native', () => ({
	AppState: {
		addEventListener: vi.fn(() => ({ remove: vi.fn() })),
		currentState: 'active',
	},
}))

class MemoryStore implements SessionStore {
	readonly isPersistent = true
	private map = new Map<string, string>()

	async getItem(key: string): Promise<string | null> {
		return this.map.get(key) ?? null
	}
	async setItem(key: string, value: string): Promise<void> {
		this.map.set(key, value)
	}
	async removeItem(key: string): Promise<void> {
		this.map.delete(key)
	}

	seed(value: object): void {
		this.map.set(SESSION_STORAGE_KEY, JSON.stringify(value))
	}
	read(): any {
		const raw = this.map.get(SESSION_STORAGE_KEY)
		return raw ? JSON.parse(raw) : undefined
	}
}

const noOpStore: SessionStore = {
	isPersistent: false,
	getItem: async () => null,
	setItem: async () => {},
	removeItem: async () => {},
}

describe('SessionManager session preservation', () => {
	beforeEach(() => {
		vi.useRealTimers()
	})
	afterEach(() => {
		vi.restoreAllMocks()
	})

	it('mints a new session when nothing is persisted', async () => {
		const store = new MemoryStore()
		const mgr = new SessionManager({}, store)
		await mgr.initialize()

		expect(mgr.wasReloaded()).toBe(false)
		expect(mgr.getResumeInfo()).toEqual({
			reloaded: false,
			elapsedMs: 0,
			reloadCount: 0,
		})
		// Persisted for the next load.
		expect(store.read().sessionId).toBe(mgr.getSessionInfo().sessionId)
		expect(store.read().reloadCount).toBe(0)
	})

	it('resumes the same session id when within the resume window', async () => {
		const store = new MemoryStore()
		const now = Date.now()
		store.seed({
			sessionId: 'prev-session-id',
			startTime: now - 60_000,
			lastActivityTime: now - 1_000,
			reloadCount: 0,
		})

		const mgr = new SessionManager({}, store)
		await mgr.initialize()

		expect(mgr.getSessionInfo().sessionId).toBe('prev-session-id')
		expect(mgr.wasReloaded()).toBe(true)
		const info = mgr.getResumeInfo()
		expect(info.reloaded).toBe(true)
		expect(info.reloadCount).toBe(1)
		expect(info.elapsedMs).toBeGreaterThanOrEqual(0)
		// startTime is preserved from the previous session.
		expect(mgr.getSessionInfo().startTime).toBe(now - 60_000)
		// Reload count is persisted incrementally.
		expect(store.read().reloadCount).toBe(1)
	})

	it('starts a fresh session on a cold start even within the resume window', async () => {
		const store = new MemoryStore()
		const now = Date.now()
		store.seed({
			sessionId: 'prev-session-id',
			startTime: now - 60_000,
			lastActivityTime: now - 1_000,
			reloadCount: 0,
		})

		// Process started *after* the previous session's last activity → that
		// activity came from a prior process, i.e. a cold restart.
		const mgr = new SessionManager(
			{ getProcessStartTimeMs: () => now - 500 },
			store,
		)
		await mgr.initialize()

		expect(mgr.getSessionInfo().sessionId).not.toBe('prev-session-id')
		expect(mgr.wasReloaded()).toBe(false)
		expect(mgr.getResumeInfo().reloadCount).toBe(0)
	})

	it('resumes on a surviving process (soft reload) within the window', async () => {
		const store = new MemoryStore()
		const now = Date.now()
		store.seed({
			sessionId: 'prev-session-id',
			startTime: now - 60_000,
			lastActivityTime: now - 1_000,
			reloadCount: 0,
		})

		// Process started *before* the last activity → the process stayed alive
		// across the reload, so the session may resume.
		const mgr = new SessionManager(
			{ getProcessStartTimeMs: () => now - 5_000 },
			store,
		)
		await mgr.initialize()

		expect(mgr.getSessionInfo().sessionId).toBe('prev-session-id')
		expect(mgr.wasReloaded()).toBe(true)
	})

	it('starts a fresh session when the previous one is stale', async () => {
		const store = new MemoryStore()
		const now = Date.now()
		store.seed({
			sessionId: 'stale-session-id',
			startTime: now - SESSION_RESUME_THRESHOLD_MS * 2,
			lastActivityTime: now - SESSION_RESUME_THRESHOLD_MS - 1_000,
			reloadCount: 3,
		})

		const mgr = new SessionManager({}, store)
		await mgr.initialize()

		expect(mgr.getSessionInfo().sessionId).not.toBe('stale-session-id')
		expect(mgr.wasReloaded()).toBe(false)
		expect(mgr.getResumeInfo().reloadCount).toBe(0)
	})

	it('does not resume without a persistent store', async () => {
		const mgr = new SessionManager({}, noOpStore)
		await mgr.initialize()
		expect(mgr.wasReloaded()).toBe(false)
	})

	it('never rotates the session id in-process on background/foreground', async () => {
		// The native (custom) session is frozen for the process lifetime and can't
		// follow an in-process rotation, so the JS side must not rotate either —
		// even with a tiny timeout that a background gap would exceed. Session
		// boundaries only happen at the next load (see resolveSession).
		const store = new MemoryStore()
		const mgr = new SessionManager({ sessionTimeout: 1 }, store)
		await mgr.initialize()
		const id = mgr.getSessionInfo().sessionId

		const calls = (
			AppState.addEventListener as unknown as {
				mock: { calls: [string, (s: string) => void][] }
			}
		).mock.calls
		const handler = calls[calls.length - 1][1]

		handler('background')
		await new Promise((resolve) => setTimeout(resolve, 5))
		handler('active')

		expect(mgr.getSessionInfo().sessionId).toBe(id)
		expect(mgr.wasReloaded()).toBe(false)
	})

	it('ignores corrupt persisted data', async () => {
		const store = new MemoryStore()
		await store.setItem(SESSION_STORAGE_KEY, '{ not valid json')

		const mgr = new SessionManager({}, store)
		await mgr.initialize()

		expect(mgr.wasReloaded()).toBe(false)
		expect(mgr.getSessionInfo().sessionId).toBeTruthy()
	})

	it('touch refreshes the persisted lastActivityTime (throttled)', async () => {
		const store = new MemoryStore()
		const mgr = new SessionManager({}, store)
		await mgr.initialize()

		const firstActivity = store.read().lastActivityTime
		// Throttled: an immediate touch should not rewrite.
		mgr.touch()
		await Promise.resolve()
		expect(store.read().lastActivityTime).toBe(firstActivity)
	})
})
