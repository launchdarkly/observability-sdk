/**
 * Ultra-minimal LaunchDarkly observability SDK
 * Target: <100KB combined for observability + session replay
 * 
 * This build:
 * - Excludes OpenTelemetry (can be loaded separately if needed)
 * - Uses minimal rrweb configuration
 * - Removes unnecessary features
 * - Optimizes for LaunchDarkly use case
 */

import type { eventWithTime } from '@rrweb/types'
import { record as rrwebRecord } from 'rrweb'

// Minimal types
export interface MinimalObserveOptions {
	backendUrl?: string
	serviceName?: string
	serviceVersion?: string
	environment?: string
	enableConsoleRecording?: boolean
	enableNetworkRecording?: boolean
	enablePerformanceRecording?: boolean
	networkRecordingOptions?: {
		initiatorTypes?: string[]
		urlAllowlist?: string[]
	}
}

export interface MinimalRecordOptions {
	backendUrl?: string
	privacySetting?: 'strict' | 'default' | 'none'
	enableCanvasRecording?: boolean
	enableInlineStylesheet?: boolean
	samplingStrategy?: {
		canvas?: number
		input?: 'all' | 'last'
		media?: number
	}
}

// Minimal error tracking
class MinimalErrorTracker {
	private errors: Array<{ error: Error; timestamp: number }> = []
	private maxErrors = 100

	constructor(private options: MinimalObserveOptions) {
		this.setupErrorListeners()
	}

	private setupErrorListeners() {
		if (typeof window === 'undefined') return

		window.addEventListener('error', (event) => {
			this.captureError(event.error || new Error(event.message), {
				filename: event.filename,
				lineno: event.lineno,
				colno: event.colno,
			})
		})

		window.addEventListener('unhandledrejection', (event) => {
			this.captureError(
				new Error(`Unhandled Promise Rejection: ${event.reason}`),
				{ type: 'unhandledrejection' }
			)
		})
	}

	captureError(error: Error, metadata?: any) {
		const errorData = {
			error,
			timestamp: Date.now(),
			metadata,
		}

		this.errors.push(errorData)
		if (this.errors.length > this.maxErrors) {
			this.errors.shift()
		}

		// Send to backend if configured
		if (this.options.backendUrl) {
			this.sendError(errorData)
		}
	}

	private async sendError(errorData: any) {
		// Minimal error sending implementation
		try {
			await fetch(`${this.options.backendUrl}/errors`, {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({
					...errorData,
					service: this.options.serviceName,
					environment: this.options.environment,
				}),
			})
		} catch {
			// Silently fail
		}
	}

	getErrors() {
		return this.errors
	}
}

// Minimal console recorder
class MinimalConsoleRecorder {
	private logs: Array<{ level: string; args: any[]; timestamp: number }> = []
	private maxLogs = 100
	private originalMethods: Record<string, any> = {}

	constructor(private options: MinimalObserveOptions) {
		if (options.enableConsoleRecording) {
			this.setupConsoleInterception()
		}
	}

	private setupConsoleInterception() {
		const levels = ['log', 'info', 'warn', 'error', 'debug']
		
		levels.forEach(level => {
			this.originalMethods[level] = console[level as keyof Console]
			
			;(console as any)[level] = (...args: any[]) => {
				this.logs.push({
					level,
					args: args.map(arg => {
						try {
							return typeof arg === 'object' ? JSON.stringify(arg) : arg
						} catch {
							return String(arg)
						}
					}),
					timestamp: Date.now(),
				})

				if (this.logs.length > this.maxLogs) {
					this.logs.shift()
				}

				// Call original method
				this.originalMethods[level].apply(console, args)
			}
		})
	}

	getLogs() {
		return this.logs
	}

	destroy() {
		// Restore original console methods
		Object.keys(this.originalMethods).forEach(level => {
			;(console as any)[level] = this.originalMethods[level]
		})
	}
}

// Minimal performance monitor
class MinimalPerformanceMonitor {
	private metrics: any[] = []

	constructor(private options: MinimalObserveOptions) {
		if (options.enablePerformanceRecording) {
			this.setupPerformanceObserver()
		}
	}

	private setupPerformanceObserver() {
		if (typeof window === 'undefined' || !window.PerformanceObserver) return

		// Observe navigation timing
		const navObserver = new PerformanceObserver((list) => {
			for (const entry of list.getEntries()) {
				this.metrics.push({
					type: 'navigation',
					data: entry.toJSON(),
					timestamp: Date.now(),
				})
			}
		})

		try {
			navObserver.observe({ entryTypes: ['navigation'] })
		} catch {
			// Some browsers don't support all entry types
		}

		// Observe resource timing (limited)
		if (this.options.networkRecordingOptions?.initiatorTypes) {
			const resourceObserver = new PerformanceObserver((list) => {
				for (const entry of list.getEntries()) {
					const resourceEntry = entry as PerformanceResourceTiming
					if (
						this.options.networkRecordingOptions?.initiatorTypes?.includes(
							resourceEntry.initiatorType
						)
					) {
						this.metrics.push({
							type: 'resource',
							data: {
								name: entry.name,
								duration: entry.duration,
								transferSize: resourceEntry.transferSize,
								initiatorType: resourceEntry.initiatorType,
							},
							timestamp: Date.now(),
						})
					}
				}
			})

			try {
				resourceObserver.observe({ entryTypes: ['resource'] })
			} catch {
				// Fallback for older browsers
			}
		}
	}

	getMetrics() {
		return this.metrics
	}
}

// Minimal session recorder
class MinimalSessionRecorder {
	private events: eventWithTime[] = []
	private stopRecording?: () => void
	private maxEvents = 1000

	constructor(private options: MinimalRecordOptions) {
		this.startRecording()
	}

	private startRecording() {
		if (typeof window === 'undefined') return

		// Minimal rrweb configuration for smallest bundle
		this.stopRecording = rrwebRecord({
			emit: (event) => {
				this.events.push(event)
				if (this.events.length > this.maxEvents) {
					this.events.shift()
				}

				// Optional: send events in batches
				if (this.options.backendUrl && this.events.length % 100 === 0) {
					this.sendEvents()
				}
			},
			// Minimal configuration for size
			sampling: {
				canvas: this.options.samplingStrategy?.canvas || 0,
				input: this.options.samplingStrategy?.input || 'last',
				media: this.options.samplingStrategy?.media || 0,
			},
			// Privacy settings
			maskAllInputs: this.options.privacySetting === 'strict',
			maskTextContent: this.options.privacySetting === 'strict',
			// Disable heavy features by default
			recordCanvas: this.options.enableCanvasRecording || false,
			inlineStylesheet: this.options.enableInlineStylesheet || false,
			// Disable plugins for minimal size
			plugins: [],
			// Minimal mouse interaction
			mousemoveWait: 50,
			// Don't record cross-origin iframes
			recordCrossOriginIframes: false,
		})
	}

	private async sendEvents() {
		if (!this.options.backendUrl || this.events.length === 0) return

		const eventsToSend = [...this.events]
		this.events = []

		try {
			await fetch(`${this.options.backendUrl}/events`, {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ events: eventsToSend }),
			})
		} catch {
			// Re-add events on failure
			this.events = [...eventsToSend, ...this.events].slice(-this.maxEvents)
		}
	}

	stop() {
		if (this.stopRecording) {
			this.stopRecording()
			this.sendEvents()
		}
	}

	getEvents() {
		return this.events
	}
}

// Main exports matching LaunchDarkly plugin interface
export class LDObserve {
	private errorTracker?: MinimalErrorTracker
	private consoleRecorder?: MinimalConsoleRecorder
	private performanceMonitor?: MinimalPerformanceMonitor

	constructor(private options: MinimalObserveOptions = {}) {}

	init() {
		this.errorTracker = new MinimalErrorTracker(this.options)
		this.consoleRecorder = new MinimalConsoleRecorder(this.options)
		this.performanceMonitor = new MinimalPerformanceMonitor(this.options)
	}

	captureError(error: Error, metadata?: any) {
		this.errorTracker?.captureError(error, metadata)
	}

	getState() {
		return {
			errors: this.errorTracker?.getErrors() || [],
			logs: this.consoleRecorder?.getLogs() || [],
			metrics: this.performanceMonitor?.getMetrics() || [],
		}
	}

	destroy() {
		this.consoleRecorder?.destroy()
	}
}

export class LDRecord {
	private recorder?: MinimalSessionRecorder

	constructor(private options: MinimalRecordOptions = {}) {}

	init() {
		this.recorder = new MinimalSessionRecorder(this.options)
	}

	stop() {
		this.recorder?.stop()
	}

	getEvents() {
		return this.recorder?.getEvents() || []
	}
}

// Simplified plugin exports for LaunchDarkly SDK
export const Observe = (options?: MinimalObserveOptions) => {
	const instance = new LDObserve(options)
	return {
		name: 'observability',
		init: () => instance.init(),
		destroy: () => instance.destroy(),
		captureError: (error: Error, metadata?: any) =>
			instance.captureError(error, metadata),
		getState: () => instance.getState(),
	}
}

export const Record = (options?: MinimalRecordOptions) => {
	const instance = new LDRecord(options)
	return {
		name: 'session-replay',
		init: () => instance.init(),
		stop: () => instance.stop(),
		getEvents: () => instance.getEvents(),
	}
}

// Default exports
export default {
	Observe,
	Record,
	LDObserve,
	LDRecord,
}