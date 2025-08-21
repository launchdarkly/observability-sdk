/**
 * Ultra-minimal LaunchDarkly observability SDK
 * Target: <100KB combined for observability + session replay
 * 
 * This build uses a custom minimal recorder instead of rrweb
 */

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
	recordInteractions?: boolean
	recordNavigation?: boolean
	recordErrors?: boolean
	samplingRate?: number
}

interface MinimalEvent {
	type: 'interaction' | 'navigation' | 'error' | 'custom'
	timestamp: number
	data: any
}

// Ultra-light DOM recorder (not full session replay, but interaction tracking)
class UltraLightRecorder {
	private events: MinimalEvent[] = []
	private maxEvents = 500
	private listeners: Array<() => void> = []

	constructor(private options: MinimalRecordOptions) {
		this.startRecording()
	}

	private startRecording() {
		if (typeof window === 'undefined') return

		// Record page navigation
		if (this.options.recordNavigation) {
			this.recordEvent({
				type: 'navigation',
				timestamp: Date.now(),
				data: {
					url: window.location.href,
					title: document.title,
					referrer: document.referrer,
				},
			})

			// Listen for navigation changes
			const handleNavigation = () => {
				this.recordEvent({
					type: 'navigation',
					timestamp: Date.now(),
					data: {
						url: window.location.href,
						title: document.title,
					},
				})
			}

			window.addEventListener('popstate', handleNavigation)
			this.listeners.push(() =>
				window.removeEventListener('popstate', handleNavigation)
			)

			// Intercept pushState/replaceState
			const originalPushState = history.pushState
			const originalReplaceState = history.replaceState

			history.pushState = (...args) => {
				const result = originalPushState.apply(history, args)
				handleNavigation()
				return result
			}

			history.replaceState = (...args) => {
				const result = originalReplaceState.apply(history, args)
				handleNavigation()
				return result
			}
		}

		// Record interactions
		if (this.options.recordInteractions) {
			const recordInteraction = (event: Event) => {
				// Sample events
				if (
					this.options.samplingRate &&
					Math.random() > this.options.samplingRate
				) {
					return
				}

				const target = event.target as HTMLElement
				const data: any = {
					type: event.type,
					targetTag: target.tagName,
				}

				// Add relevant attributes based on element type
				if (target.id) data.targetId = target.id
				if (target.className) data.targetClass = target.className

				// Special handling for specific elements
				if (target.tagName === 'BUTTON' || target.tagName === 'A') {
					data.text = this.sanitizeText(target.textContent || '')
				}

				if (target.tagName === 'INPUT') {
					const input = target as HTMLInputElement
					data.inputType = input.type
					data.inputName = input.name
					// Don't record sensitive input values
					if (this.options.privacySetting !== 'none') {
						data.value = '[REDACTED]'
					}
				}

				// Add position for click/touch events
				if (event instanceof MouseEvent || event instanceof TouchEvent) {
					const coords =
						event instanceof MouseEvent
							? { x: event.clientX, y: event.clientY }
							: event.touches[0]
							? {
									x: event.touches[0].clientX,
									y: event.touches[0].clientY,
							  }
							: null

					if (coords) {
						data.position = coords
					}
				}

				this.recordEvent({
					type: 'interaction',
					timestamp: Date.now(),
					data,
				})
			}

			// Listen to key interaction events
			const events = ['click', 'submit', 'change', 'focus', 'blur']
			events.forEach((eventType) => {
				const handler = (e: Event) => recordInteraction(e)
				document.addEventListener(eventType, handler, true)
				this.listeners.push(() =>
					document.removeEventListener(eventType, handler, true)
				)
			})
		}

		// Record errors
		if (this.options.recordErrors) {
			const errorHandler = (event: ErrorEvent) => {
				this.recordEvent({
					type: 'error',
					timestamp: Date.now(),
					data: {
						message: event.message,
						filename: event.filename,
						lineno: event.lineno,
						colno: event.colno,
						stack: event.error?.stack,
					},
				})
			}

			window.addEventListener('error', errorHandler)
			this.listeners.push(() =>
				window.removeEventListener('error', errorHandler)
			)
		}
	}

	private sanitizeText(text: string): string {
		if (this.options.privacySetting === 'strict') {
			return '[REDACTED]'
		}
		// Truncate long text
		return text.slice(0, 100)
	}

	private recordEvent(event: MinimalEvent) {
		this.events.push(event)
		if (this.events.length > this.maxEvents) {
			this.events.shift()
		}

		// Send in batches
		if (this.options.backendUrl && this.events.length % 50 === 0) {
			this.sendEvents()
		}
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
			// Re-add events on failure (up to max)
			this.events = [...eventsToSend, ...this.events].slice(-this.maxEvents)
		}
	}

	stop() {
		// Remove all listeners
		this.listeners.forEach((cleanup) => cleanup())
		this.listeners = []
		// Send remaining events
		this.sendEvents()
	}

	getEvents() {
		return this.events
	}
}

// Minimal error tracking
class MinimalErrorTracker {
	private errors: Array<{ error: Error; timestamp: number; metadata?: any }> = []
	private maxErrors = 50

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
			error: {
				message: error.message,
				stack: error.stack,
				name: error.name,
			},
			timestamp: Date.now(),
			metadata,
		}

		this.errors.push(errorData as any)
		if (this.errors.length > this.maxErrors) {
			this.errors.shift()
		}

		// Send to backend if configured
		if (this.options.backendUrl) {
			this.sendError(errorData)
		}
	}

	private async sendError(errorData: any) {
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
	private logs: Array<{ level: string; message: string; timestamp: number }> = []
	private maxLogs = 50
	private originalMethods: Record<string, any> = {}

	constructor(private options: MinimalObserveOptions) {
		if (options.enableConsoleRecording) {
			this.setupConsoleInterception()
		}
	}

	private setupConsoleInterception() {
		const levels = ['log', 'warn', 'error']
		
		levels.forEach(level => {
			this.originalMethods[level] = console[level as keyof Console]
			
			;(console as any)[level] = (...args: any[]) => {
				// Simplify args to string
				const message = args
					.map(arg => {
						try {
							return typeof arg === 'object' 
								? JSON.stringify(arg).slice(0, 200) 
								: String(arg).slice(0, 200)
						} catch {
							return '[Object]'
						}
					})
					.join(' ')

				this.logs.push({
					level,
					message,
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
			this.collectBasicMetrics()
		}
	}

	private collectBasicMetrics() {
		if (typeof window === 'undefined' || !window.performance) return

		// Collect navigation timing once
		setTimeout(() => {
			const nav = performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming
			if (nav) {
				this.metrics.push({
					type: 'navigation',
					data: {
						domContentLoaded: nav.domContentLoadedEventEnd - nav.domContentLoadedEventStart,
						loadComplete: nav.loadEventEnd - nav.loadEventStart,
						domInteractive: nav.domInteractive - nav.fetchStart,
						ttfb: nav.responseStart - nav.requestStart,
					},
					timestamp: Date.now(),
				})
			}

			// Collect basic resource metrics
			if (this.options.networkRecordingOptions?.initiatorTypes) {
				const resources = performance.getEntriesByType('resource') as PerformanceResourceTiming[]
				const summary = {
					script: { count: 0, totalDuration: 0, totalSize: 0 },
					css: { count: 0, totalDuration: 0, totalSize: 0 },
					img: { count: 0, totalDuration: 0, totalSize: 0 },
					fetch: { count: 0, totalDuration: 0, totalSize: 0 },
				}

				resources.forEach(resource => {
					const type = resource.initiatorType as keyof typeof summary
					if (summary[type]) {
						summary[type].count++
						summary[type].totalDuration += resource.duration
						summary[type].totalSize += resource.transferSize || 0
					}
				})

				this.metrics.push({
					type: 'resources',
					data: summary,
					timestamp: Date.now(),
				})
			}
		}, 2000) // Wait for page to load
	}

	getMetrics() {
		return this.metrics
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
	private recorder?: UltraLightRecorder

	constructor(private options: MinimalRecordOptions = {}) {}

	init() {
		this.recorder = new UltraLightRecorder(this.options)
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

// Combined plugin for both observability and recording
export const ObservabilityPlugin = (
	observeOptions?: MinimalObserveOptions,
	recordOptions?: MinimalRecordOptions
) => {
	const observe = new LDObserve(observeOptions)
	const record = new LDRecord(recordOptions)

	return {
		name: 'observability-complete',
		init: () => {
			observe.init()
			record.init()
		},
		destroy: () => {
			observe.destroy()
			record.stop()
		},
		captureError: (error: Error, metadata?: any) =>
			observe.captureError(error, metadata),
		getState: () => ({
			...observe.getState(),
			events: record.getEvents(),
		}),
	}
}

// Default exports
export default {
	Observe,
	Record,
	ObservabilityPlugin,
	LDObserve,
	LDRecord,
}