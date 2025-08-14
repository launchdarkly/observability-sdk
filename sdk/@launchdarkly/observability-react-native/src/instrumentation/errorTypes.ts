import { Attributes } from '@opentelemetry/api'

export type ErrorType = 'unhandled_exception' | 'unhandled_rejection' | 'console_error' | 'react_error'
export type ErrorSource = 'javascript' | 'native' | 'react'

export interface ErrorContext {
	type: ErrorType
	source: ErrorSource
	fatal: boolean
	componentStack?: string
	appState?: 'active' | 'background' | 'inactive'
	timestamp: number
	sessionId?: string
}

export interface FormattedError {
	message: string
	name: string
	stack?: string
	context: ErrorContext
	attributes: Attributes
}

// Error deduplication
export interface ErrorFingerprint {
	message: string
	name: string
	stackHash: string
}

export class ErrorDeduplicator {
	private recentErrors: Map<string, number> = new Map()
	private readonly MAX_CACHE_SIZE = 100
	private readonly DEDUP_WINDOW_MS = 5000 // 5 seconds

	shouldReport(error: Error): boolean {
		const fingerprint = this.getFingerprint(error)
		const key = JSON.stringify(fingerprint)
		const now = Date.now()

		// Clean old entries
		this.cleanup(now)

		const lastSeen = this.recentErrors.get(key)
		if (lastSeen && now - lastSeen < this.DEDUP_WINDOW_MS) {
			return false
		}

		this.recentErrors.set(key, now)
		return true
	}

	private getFingerprint(error: Error): ErrorFingerprint {
		return {
			message: error.message,
			name: error.name,
			stackHash: this.hashStack(error.stack || ''),
		}
	}

	private hashStack(stack: string): string {
		// Simple hash of first few stack frames
		const lines = stack.split('\n').slice(0, 3)
		return lines.join('|').replace(/\d+:\d+/g, '') // Remove line:column numbers
	}

	private cleanup(now: number): void {
		if (this.recentErrors.size > this.MAX_CACHE_SIZE) {
			const entriesToDelete: string[] = []
			for (const [key, timestamp] of this.recentErrors) {
				if (now - timestamp > this.DEDUP_WINDOW_MS) {
					entriesToDelete.push(key)
				}
			}
			entriesToDelete.forEach(key => this.recentErrors.delete(key))
		}
	}
}