/**
 * Optimized entry point for LaunchDarkly Observability SDK
 * O11Y-393: Uses dynamic imports to reduce initial bundle size
 */

import type { HighlightClassOptions, RequestResponsePair } from './client'
import { GenerateSecureID, Highlight } from './client'
import { FirstLoadListeners } from './client/listeners/first-load-listeners'
import type {
	HighlightOptions,
	HighlightPublicInterface,
	Metadata,
	Metric,
	OnHighlightReadyOptions,
	SessionDetails,
} from './client/types/types'
import { HIGHLIGHT_URL } from './client/constants/sessions.js'
import type { ErrorMessageType, Source } from './client/types/shared-types'
import {
	getPreviousSessionData,
	loadCookieSessionData,
} from './client/utils/sessionStorage/highlightSession.js'
import { setCookieWriteEnabled } from './client/utils/storage'
import version from './version.js'
import { listenToChromeExtensionMessage } from './browserExtension/extensionListener.js'
import { ViewportResizeListenerArgs } from './client/listeners/viewport-resize-listener'

// Lazy imports for OpenTelemetry
import type { Attributes, Context, Span, SpanOptions } from '@opentelemetry/api'
import { otelLoader, lazyStartSpan, isOTelLoaded } from './client/otel/lazy-loader'

// Types for lazy-loaded modules
type AmplitudeAPI = any
type MixpanelAPI = any
type LaunchDarklyIntegration = any
type IntegrationClient = any

enum MetricCategory {
	Device = 'Device',
	WebVital = 'WebVital',
	Frontend = 'Frontend',
	Backend = 'Backend',
}

const HighlightWarning = (context: string, msg: any) => {
	console.warn(`highlight.run warning: (${context}): `, msg)
}

interface HighlightWindow extends Window {
	HighlightIO: new (
		options: HighlightClassOptions,
		firstLoadListeners: FirstLoadListeners,
	) => Highlight
	H: HighlightPublicInterface
	mixpanel?: MixpanelAPI
	amplitude?: AmplitudeAPI
	Intercom?: any
}
declare var window: HighlightWindow

const READY_WAIT_LOOP_MS = 200

let onHighlightReadyQueue: {
	options?: OnHighlightReadyOptions
	func: () => void | Promise<void>
}[] = []
let onHighlightReadyTimeout: ReturnType<typeof setTimeout> | undefined =
	undefined

let highlight_obj: Highlight
let first_load_listeners: FirstLoadListeners
let integrations: IntegrationClient[] = []
let init_called = false
type Callback = (span?: Span) => any

// Lazy loading helpers
const lazyModules = {
	amplitude: null as any,
	mixpanel: null as any,
	segment: null as any,
	electron: null as any,
	launchdarkly: null as any,
	fetchListener: null as any,
	webSocketListener: null as any,
	otelTracer: null as any,
}

async function loadIntegration(name: string): Promise<any> {
	switch (name) {
		case 'amplitude':
			if (!lazyModules.amplitude) {
				const module = await import('./integrations/amplitude.js')
				lazyModules.amplitude = module
			}
			return lazyModules.amplitude
		case 'mixpanel':
			if (!lazyModules.mixpanel) {
				const module = await import('./integrations/mixpanel.js')
				lazyModules.mixpanel = module
			}
			return lazyModules.mixpanel
		case 'segment':
			if (!lazyModules.segment) {
				const module = await import('./integrations/segment.js')
				lazyModules.segment = module
			}
			return lazyModules.segment
		case 'launchdarkly':
			if (!lazyModules.launchdarkly) {
				const module = await import('./integrations/launchdarkly')
				lazyModules.launchdarkly = module
			}
			return lazyModules.launchdarkly
		case 'electron':
			if (!lazyModules.electron) {
				const module = await import('./environments/electron.js')
				lazyModules.electron = module.default
			}
			return lazyModules.electron
		default:
			throw new Error(`Unknown integration: ${name}`)
	}
}

async function loadListener(name: string): Promise<any> {
	switch (name) {
		case 'fetch':
			if (!lazyModules.fetchListener) {
				const module = await import('./listeners/fetch')
				lazyModules.fetchListener = module
			}
			return lazyModules.fetchListener
		case 'websocket':
			if (!lazyModules.webSocketListener) {
				const module = await import('./listeners/web-socket')
				lazyModules.webSocketListener = module
			}
			return lazyModules.webSocketListener
		default:
			throw new Error(`Unknown listener: ${name}`)
	}
}

// Get tracer with lazy loading
async function getTracer(): Promise<any> {
	if (!lazyModules.otelTracer) {
		const module = await import('./client/otel')
		lazyModules.otelTracer = module.getTracer
	}
	return lazyModules.otelTracer()
}

// Create noop span for when OTEL is not loaded
function getNoopSpan(): Span {
	return {
		end: () => {},
		setAttribute: () => {},
		setAttributes: () => {},
		addEvent: () => {},
		setStatus: () => {},
		updateName: () => {},
		isRecording: () => false,
		recordException: () => {},
		spanContext: () => ({
			traceId: '',
			spanId: '',
			traceFlags: 0,
		}),
	} as any
}

const H: HighlightPublicInterface = {
	options: undefined,
	init: (projectID?: string | number, options?: HighlightOptions) => {
		try {
			H.options = options

			// Don't run init when called outside of the browser.
			if (typeof window === 'undefined') {
				return
			}

			if (options?.skipCookieSessionDataLoad !== true) {
				loadCookieSessionData()
			}

			setCookieWriteEnabled(!options?.disableSessionRecording)

			if (init_called) {
				console.warn(
					'Highlight.init was already called. Aborting new init.',
				)
				return
			}

			init_called = true

			// Initialize listeners
			first_load_listeners = new FirstLoadListeners()

			// Set backendUrl
			const backendUrl = options?.backendUrl || HIGHLIGHT_URL

			// Create Highlight instance
			highlight_obj = new window.HighlightIO(
				{
					...options,
					projectID: projectID,
					backendUrl,
				} as HighlightClassOptions,
				first_load_listeners,
			)

			// Load integrations asynchronously
			if (options?.integrations) {
				Promise.all(
					options.integrations.map(async (integration) => {
						if (integration.name === 'mixpanel' && window.mixpanel) {
							const module = await loadIntegration('mixpanel')
							module.setupMixpanelIntegration()
						} else if (integration.name === 'amplitude' && window.amplitude) {
							const module = await loadIntegration('amplitude')
							module.setupAmplitudeIntegration()
						} else if (integration.name === 'launchdarkly') {
							const module = await loadIntegration('launchdarkly')
							const ldIntegration = new module.LaunchDarklyIntegration(integration.options)
							integrations.push(ldIntegration)
							ldIntegration.init(highlight_obj)
						}
					})
				).catch(err => {
					console.error('Failed to load integrations:', err)
				})
			}

			// Load listeners asynchronously with delay
			if (!options?.disableNetworkRecording) {
				setTimeout(async () => {
					try {
						const fetchModule = await loadListener('fetch')
						fetchModule.initializeFetchListener(highlight_obj)
					} catch (err) {
						console.debug('Failed to initialize fetch listener:', err)
					}

					try {
						const wsModule = await loadListener('websocket')
						wsModule.initializeWebSocketListener(highlight_obj)
					} catch (err) {
						console.debug('Failed to initialize WebSocket listener:', err)
					}
				}, 1000)
			}

			// Initialize OpenTelemetry asynchronously
			if (options?.enableOtelInstrumentation) {
				setTimeout(async () => {
					await otelLoader.initialize({
						serviceName: options.serviceName || 'default',
						serviceVersion: options.serviceVersion,
						backendUrl: options.otlpEndpoint || backendUrl,
						enableInstrumentation: true,
					})
				}, 2000)
			}

			// Setup Chrome extension listener if needed
			if (options?.enableBrowserExtensionRecording) {
				listenToChromeExtensionMessage()
			}

			// Setup Electron if in Electron environment
			if (typeof window !== 'undefined' && (window as any).electron) {
				loadIntegration('electron').then(configureElectronHighlight)
			}

			// Start the Highlight client
			if (!options?.manualStart) {
				highlight_obj.start()
			}

			// Assign to window
			window.H = H

			// Process ready queue
			processReadyQueue()

		} catch (e) {
			HighlightWarning('init', e)
		}
	},

	start: (options?: { silent?: boolean }) => {
		if (highlight_obj) {
			highlight_obj.start(options)
		}
	},

	stop: () => {
		if (highlight_obj) {
			highlight_obj.stop()
		}
	},

	identify: (identifier: string, metadata?: Metadata) => {
		if (highlight_obj) {
			highlight_obj.identify(identifier, metadata)
		}
	},

	track: (event: string, metadata?: Metadata) => {
		if (highlight_obj) {
			highlight_obj.track(event, metadata)
		}
	},

	consumeError: (
		error: Error,
		message?: string,
		source?: Source,
		metadata?: Metadata,
	) => {
		if (highlight_obj) {
			highlight_obj.consumeError(error, message, source, metadata)
		}
	},

	error: (message: string, metadata?: Metadata) => {
		if (highlight_obj) {
			highlight_obj.error(message, metadata)
		}
	},

	metrics: (metrics: Metric[]) => {
		if (highlight_obj) {
			highlight_obj.metrics(metrics)
		}
	},

	getSessionURL: (): string => {
		if (highlight_obj) {
			return highlight_obj.getSessionURL()
		}
		return ''
	},

	getSessionDetails: (): SessionDetails | undefined => {
		if (highlight_obj) {
			return highlight_obj.getSessionDetails()
		}
		return undefined
	},

	getRecordingState: () => {
		if (highlight_obj) {
			return highlight_obj.getRecordingState()
		}
		return 'NotRecording'
	},

	onHighlightReady: (
		func: () => void | Promise<void>,
		options?: OnHighlightReadyOptions,
	) => {
		onHighlightReadyQueue.push({ func, options })
		processReadyQueue()
	},

	// OpenTelemetry methods with lazy loading
	startSpan: async (
		name: string,
		options?: SpanOptions,
		fn?: Callback,
	): Promise<any> => {
		if (!isOTelLoaded()) {
			// If OTEL not loaded, return noop span
			const noopSpan = getNoopSpan()
			if (fn) {
				const result = await fn(noopSpan)
				noopSpan.end()
				return result
			}
			return noopSpan
		}

		const span = await lazyStartSpan(name, options)
		if (fn) {
			try {
				const result = await fn(span)
				span.end()
				return result
			} catch (error) {
				span.recordException(error as Error)
				span.end()
				throw error
			}
		}
		return span
	},

	// Additional methods...
	addSessionFeedback: () => {},
	getViewportResizeListener: () => ({} as ViewportResizeListenerArgs),
	enableCanvasRecording: () => {},
	enableSegmentIntegration: () => {},
	enableAmplitudeIntegration: () => {},
	enableMixpanelIntegration: () => {},
	getSegmentMiddleware: () => null,
}

function processReadyQueue() {
	if (!highlight_obj || !highlight_obj.ready) {
		if (!onHighlightReadyTimeout) {
			onHighlightReadyTimeout = setTimeout(() => {
				onHighlightReadyTimeout = undefined
				processReadyQueue()
			}, READY_WAIT_LOOP_MS)
		}
		return
	}

	const queue = onHighlightReadyQueue
	onHighlightReadyQueue = []
	queue.forEach(({ func, options }) => {
		if (options?.waitForReady === false || highlight_obj.ready) {
			func()
		}
	})
}

// Export everything
export default H
export { H }
export { MetricCategory }
export { getPreviousSessionData }
export { HighlightSegmentMiddleware } from './integrations/segment.js'

// Export types
export type {
	HighlightOptions,
	HighlightPublicInterface,
	Metadata,
	Metric,
	SessionDetails,
	OnHighlightReadyOptions,
	RequestResponsePair,
	ErrorMessageType,
	Source,
}

// Preload critical modules in the background
if (typeof window !== 'undefined') {
	// Preload OTEL after a delay
	setTimeout(() => {
		otelLoader.preload().catch(() => {
			// Silently fail preloading
		})
	}, 5000)
}