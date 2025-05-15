import { getRecordSequentialIdPlugin } from '@rrweb/rrweb-plugin-sequential-id-record'
import { eventWithTime, listenerHandler } from '@rrweb/types'
import { print } from 'graphql'
import { GraphQLClient } from 'graphql-request'
import stringify from 'json-stringify-safe'
import { addCustomEvent as rrwebAddCustomEvent, record } from 'rrweb'
import {
	getSdk,
	PushPayloadDocument,
	PushPayloadMutationVariables,
	Sdk,
} from '../client/graph/generated/operations'
import { PathListener } from '../client/listeners/path-listener'
import {
	DebugOptions,
	MetricCategory,
	MetricName,
	SessionShortcutOptions,
} from '../client/types/client'
import {
	type Metadata,
	PrivacySettingOption,
	SamplingStrategy,
	type SessionDetails,
	StartOptions,
} from '../client/types/types'
import { determineMaskInputOptions } from '../client/utils/privacy'

import {
	FIRST_SEND_FREQUENCY,
	HIGHLIGHT_URL,
	LAUNCHDARKLY_BACKEND_REGEX,
	LAUNCHDARKLY_ENV_APPS,
	LAUNCHDARKLY_PATH_PREFIX,
	LAUNCHDARKLY_URL,
	MAX_SESSION_LENGTH,
	SEND_FREQUENCY,
	SNAPSHOT_SETTINGS,
	VISIBILITY_DEBOUNCE_MS,
} from '../client/constants/sessions'
import { ReplayEventsInput } from '../client/graph/generated/schemas'
import { ClickListener } from '../client/listeners/click-listener/click-listener'
import { FocusListener } from '../client/listeners/focus-listener/focus-listener'
import { PageVisibilityListener } from '../client/listeners/page-visibility-listener'
import { SegmentIntegrationListener } from '../client/listeners/segment-integration-listener'
import SessionShortcutListener from '../client/listeners/session-shortcut/session-shortcut-listener'
import {
	ViewportResizeListener,
	type ViewportResizeListenerArgs,
} from '../client/listeners/viewport-resize-listener'
import { WebVitalsListener } from '../client/listeners/web-vitals-listener/web-vitals-listener'
import {
	NetworkPerformanceListener,
	NetworkPerformancePayload,
} from '../client/listeners/network-listener/performance-listener'
import { Logger } from '../client/logger'
import {
	HighlightIframeMessage,
	HighlightIframeReponse,
	IFRAME_PARENT_READY,
	IFRAME_PARENT_RESPONSE,
} from '../client/types/iframe'
import { Source } from '../client/types/shared-types'
import { getSimpleSelector } from '../client/utils/dom'
import { getGraphQLRequestWrapper } from '../client/utils/graph'
import {
	clearHighlightLogs,
	getHighlightLogs,
} from '../client/utils/highlight-logging'
import { getPerformanceMethods } from '../client/utils/performance/performance'
import { GenerateSecureID } from '../client/utils/secure-id'
import {
	getPreviousSessionData,
	SessionData,
	setSessionData,
	setSessionSecureID,
} from '../client/utils/sessionStorage/highlightSession'
import { SESSION_STORAGE_KEYS } from '../client/utils/sessionStorage/sessionStorageKeys'
import {
	getItem,
	LocalStorageKeys,
	removeItem,
	setItem,
} from '../client/utils/storage'
import { getDefaultDataURLOptions } from '../client/utils/utils'
import type { HighlightClientRequestWorker } from '../client/workers/highlight-client-worker'
import HighlightClientWorker from '../client/workers/highlight-client-worker?worker&inline'
import { MessageType, PropertyType } from '../client/workers/types'
import { Attributes } from '@opentelemetry/api'
import { IntegrationClient } from '../integrations'
import { Record } from '../api/record'
import { HighlightWarning } from './util'
import type { HighlightClassOptions } from '../client'
import { Highlight } from '../client'
import type { Hook, LDClient } from '../integrations/launchdarkly'
import { LaunchDarklyIntegration } from '../integrations/launchdarkly'
import { LDObserve } from './LDObserve'
import { LDPluginEnvironmentMetadata } from '../plugins/plugin'
import { RecordOptions } from '../client/types/record'

interface HighlightWindow extends Window {
	Highlight: Highlight
	Intercom?: any
	electron?: {
		ipcRenderer: {
			on: (channel: string, listener: (...args: any[]) => void) => {}
		}
	}
	Cypress?: any
}

declare var window: HighlightWindow

export class RecordSDK implements Record {
	options!: HighlightClassOptions
	/** Determines if the client is running on a Highlight property (e.g. frontend). */
	isRunningOnHighlight!: boolean
	/** Verbose project ID that is exposed to users. Legacy users may still be using ints. */
	organizationID!: string
	graphqlSDK!: Sdk
	events!: eventWithTime[]
	sessionData!: SessionData
	ready!: boolean
	manualStopped!: boolean
	state!: 'NotRecording' | 'Recording'
	logger!: Logger
	enableSegmentIntegration!: boolean
	privacySetting!: PrivacySettingOption
	enableCanvasRecording!: boolean
	samplingStrategy!: SamplingStrategy
	inlineImages!: boolean
	inlineVideos!: boolean
	inlineStylesheet!: boolean
	debugOptions!: DebugOptions
	listeners!: listenerHandler[]
	firstloadVersion!: string
	environment!: string
	sessionShortcut!: SessionShortcutOptions
	/** The end-user's app version. This isn't Highlight's version. */
	appVersion!: string | undefined
	serviceName!: string
	_worker!: HighlightClientRequestWorker
	_optionsInternal!: Omit<RecordOptions, 'firstloadVersion'>
	_backendUrl!: string
	_recordingStartTime!: number
	_isOnLocalHost!: boolean
	_onToggleFeedbackFormVisibility!: () => void
	_isCrossOriginIframe!: boolean
	_eventBytesSinceSnapshot!: number
	_lastSnapshotTime!: number
	_lastVisibilityChangeTime!: number
	pushPayloadTimerId!: ReturnType<typeof setTimeout> | undefined
	hasSessionUnloaded!: boolean
	hasPushedData!: boolean
	reloaded!: boolean
	_hasPreviouslyInitialized!: boolean
	_recordStop!: listenerHandler | undefined
	_integrations: IntegrationClient[] = []

	constructor(options: HighlightClassOptions) {
		this.options = options
		if (typeof this.options?.debug === 'boolean') {
			this.debugOptions = this.options.debug
				? { clientInteractions: true }
				: {}
		} else {
			this.debugOptions = this.options?.debug ?? {}
		}
		this.logger = new Logger(this.debugOptions.clientInteractions)
		this._worker =
			new HighlightClientWorker() as HighlightClientRequestWorker
		this._worker.onmessage = (e) => {
			if (e.data.response?.type === MessageType.AsyncEvents) {
				this._eventBytesSinceSnapshot += e.data.response.eventsSize
				this.logger.log(
					`Web worker sent payloadID ${e.data.response.id} size ${
						e.data.response.eventsSize
					} bytes, compression ratio ${
						e.data.response.eventsSize /
						e.data.response.compressedSize
					}.
                Total since snapshot: ${(
					this._eventBytesSinceSnapshot / 1000000
				).toFixed(1)}MB`,
				)
			} else if (e.data.response?.type === MessageType.CustomEvent) {
				this.addCustomEvent(
					e.data.response.tag,
					e.data.response.payload,
				)
			} else if (e.data.response?.type === MessageType.Stop) {
				HighlightWarning(
					'Stopping recording due to worker failure',
					e.data.response,
				)
				this.stop(false)
			}
		}

		let storedSessionData = getPreviousSessionData()
		this.reloaded = false
		// only fetch session data from local storage on the first `initialize` call
		if (
			!this.sessionData?.sessionSecureID &&
			storedSessionData?.sessionSecureID
		) {
			this.sessionData = storedSessionData
			this.options.sessionSecureID = storedSessionData.sessionSecureID
			this.reloaded = true
			this.logger.log(
				`Tab reloaded, continuing previous session: ${this.sessionData.sessionSecureID}`,
			)
		} else {
			// new session. we should clear any session storage data
			for (const storageKeyName of Object.values(SESSION_STORAGE_KEYS)) {
				removeItem(storageKeyName)
			}
			this.sessionData = {
				sessionSecureID: this.options.sessionSecureID,
				projectID: 0,
				payloadID: 1,
				sessionStartTime: Date.now(),
			}
		}
		// these should not be in initMembers since we want them to
		// persist across session resets
		this._hasPreviouslyInitialized = false
		try {
			// throws if parent is cross-origin
			if (window.parent.document) {
				this._isCrossOriginIframe = false
			}
		} catch (e) {
			// if recordCrossOriginIframe is set to false, operate as if highlight is only recording the iframe as a dedicated web app.
			// this is useful if you are running highlight on your app that is used in a cross-origin iframe with no access to the parent page.
			this._isCrossOriginIframe =
				this.options.recordCrossOriginIframe ?? true
		}
		this._initMembers(this.options)
	}

	// Start a new session
	async _reset({ forceNew }: { forceNew?: boolean }) {
		if (this.pushPayloadTimerId) {
			clearTimeout(this.pushPayloadTimerId)
			this.pushPayloadTimerId = undefined
		}

		let user_identifier, user_object
		if (!forceNew) {
			try {
				user_identifier = getItem(SESSION_STORAGE_KEYS.USER_IDENTIFIER)
				const user_object_string = getItem(
					SESSION_STORAGE_KEYS.USER_OBJECT,
				)
				if (user_object_string) {
					user_object = JSON.parse(user_object_string)
				}
			} catch (err) {}
		}
		for (const storageKeyName of Object.values(SESSION_STORAGE_KEYS)) {
			removeItem(storageKeyName)
		}

		// no need to set the sessionStorage value here since firstload won't call
		// init again after a reset, and `this.initialize()` will set sessionStorage
		this.sessionData.sessionSecureID = GenerateSecureID()
		this.sessionData.sessionStartTime = Date.now()
		this.options.sessionSecureID = this.sessionData.sessionSecureID
		this.stop()
		await this.start()
		if (user_identifier && user_object) {
			this.identify(user_identifier, user_object)
		}
	}

	_initMembers(options: HighlightClassOptions) {
		this.sessionShortcut = false
		this._recordingStartTime = 0
		this._isOnLocalHost =
			window.location.hostname === 'localhost' ||
			window.location.hostname === '127.0.0.1' ||
			window.location.hostname === ''

		this.ready = false
		this.state = 'NotRecording'
		this.manualStopped = false
		this.enableSegmentIntegration = !!options.enableSegmentIntegration
		this.privacySetting = options.privacySetting ?? 'default'
		this.enableCanvasRecording = options.enableCanvasRecording ?? false
		// default to inlining stylesheets/images locally to help with recording accuracy
		this.inlineImages = options.inlineImages ?? this._isOnLocalHost
		this.inlineVideos = options.inlineVideos ?? this._isOnLocalHost
		this.inlineStylesheet = options.inlineStylesheet ?? this._isOnLocalHost
		this.samplingStrategy = {
			canvasFactor: 0.5,
			canvasMaxSnapshotDimension: 360,
			canvasClearWebGLBuffer: true,
			dataUrlOptions: getDefaultDataURLOptions(),
			...(options.samplingStrategy ?? {
				canvas: 2,
			}),
		}
		this._backendUrl =
			options?.backendUrl ??
			'https://pub.observability.app.launchdarkly.com'

		// If _backendUrl is a relative URL, convert it to an absolute URL
		// so that it's usable from a web worker.
		if (this._backendUrl[0] === '/') {
			this._backendUrl = new URL(this._backendUrl, document.baseURI).href
		}

		const client = new GraphQLClient(`${this._backendUrl}`, {
			headers: {},
		})
		this.graphqlSDK = getSdk(
			client,
			getGraphQLRequestWrapper(
				this.sessionData?.sessionSecureID ||
					this.options?.sessionSecureID,
			),
		)
		this.environment = options.environment ?? 'production'
		this.appVersion = options.appVersion
		this.serviceName = options.serviceName ?? ''

		if (typeof options.organizationID === 'string') {
			this.organizationID = options.organizationID
		} else {
			this.organizationID = options.organizationID.toString()
		}
		this.isRunningOnHighlight =
			this.organizationID === '1' || this.organizationID === '1jdkoe52'
		this.firstloadVersion = options.firstloadVersion || 'unknown'
		this.sessionShortcut = options.sessionShortcut || false
		this._onToggleFeedbackFormVisibility = () => {}
		// We only want to store a subset of the options for debugging purposes. Firstload version is stored as another field so we don't need to store it here.
		const { firstloadVersion: _, ...optionsInternal } = options
		this._optionsInternal = optionsInternal
		this.listeners = []

		this.events = []
		this.hasSessionUnloaded = false
		this.hasPushedData = false

		if (window.Intercom) {
			window.Intercom('onShow', () => {
				window.Intercom('update', {
					highlightSessionURL:
						this.getCurrentSessionURLWithTimestamp(),
				})
				this.addProperties({ event: 'Intercom onShow' })
			})
		}

		this._eventBytesSinceSnapshot = 0
		this._lastSnapshotTime = new Date().getTime()
		this._lastVisibilityChangeTime = new Date().getTime()
	}

	identify(user_identifier: string, user_object = {}, source?: Source) {
		if (!user_identifier || user_identifier === '') {
			console.warn(
				`Highlight's identify() call was passed an empty identifier.`,
				{ user_identifier, user_object },
			)
			return
		}
		this.sessionData.userIdentifier = user_identifier.toString()
		this.sessionData.userObject = user_object
		setItem(
			SESSION_STORAGE_KEYS.USER_IDENTIFIER,
			user_identifier.toString(),
		)
		setItem(SESSION_STORAGE_KEYS.USER_OBJECT, JSON.stringify(user_object))
		this._worker.postMessage({
			message: {
				type: MessageType.Identify,
				userIdentifier: user_identifier,
				userObject: user_object,
				source,
			},
		})
	}

	track(event: string, metadata?: Metadata) {
		this.addProperties({ ...metadata, event: event })
	}

	addProperties(properties_obj = {}, typeArg?: PropertyType) {
		// Remove any properties which throw on structuredClone
		// (structuredClone is used when posting messages to the worker)
		const obj = { ...properties_obj } as any
		Object.entries(obj).forEach(([key, val]) => {
			try {
				structuredClone(val)
			} catch {
				delete obj[key]
			}
		})
		this._worker.postMessage({
			message: {
				type: MessageType.Properties,
				propertiesObject: obj,
				propertyType: typeArg,
			},
		})
	}

	async start(options?: StartOptions) {
		if (
			(navigator?.webdriver && !window.Cypress) ||
			navigator?.userAgent?.includes('Googlebot') ||
			navigator?.userAgent?.includes('AdsBot')
		) {
			return
		}

		try {
			if (options?.forceNew) {
				await this._reset(options)
				return
			}

			this.logger.log(
				`Initializing...`,
				options,
				this.sessionData,
				this.options,
			)

			this.sessionData =
				getPreviousSessionData(this.sessionData.sessionSecureID) ??
				this.sessionData
			if (!this.sessionData?.sessionStartTime) {
				this._recordingStartTime = new Date().getTime()
				this.sessionData.sessionStartTime = this._recordingStartTime
			} else {
				this._recordingStartTime = this.sessionData?.sessionStartTime
			}

			let clientID = getItem(LocalStorageKeys['CLIENT_ID'])

			if (!clientID) {
				clientID = GenerateSecureID()
				setItem(LocalStorageKeys['CLIENT_ID'], clientID)
			}

			// Duplicate of logic inside FirstLoadListeners.setupNetworkListener,
			// needed for initializeSession
			let enableNetworkRecording
			if (this.options.disableSessionRecording) {
				enableNetworkRecording = false
			} else if (this.options.disableNetworkRecording !== undefined) {
				enableNetworkRecording = false
			} else if (typeof this.options.networkRecording === 'boolean') {
				enableNetworkRecording = false
			} else {
				enableNetworkRecording =
					this.options.networkRecording?.recordHeadersAndBody || false
			}

			let destinationDomains: string[] = []
			if (
				typeof this.options.networkRecording === 'object' &&
				this.options.networkRecording.destinationDomains?.length
			) {
				destinationDomains =
					this.options.networkRecording.destinationDomains
			}
			if (this._isCrossOriginIframe) {
				// wait for 'cross-origin iframe ready' message
				await this._setupCrossOriginIframe()
			} else {
				const gr = await this.graphqlSDK.initializeSession({
					organization_verbose_id: this.organizationID,
					enable_strict_privacy: this.privacySetting === 'strict',
					privacy_setting: this.privacySetting,
					enable_recording_network_contents: enableNetworkRecording,
					clientVersion: this.firstloadVersion,
					firstloadVersion: this.firstloadVersion,
					clientConfig: JSON.stringify(this._optionsInternal),
					environment: this.environment,
					id: clientID,
					appVersion: this.appVersion,
					serviceName: this.serviceName,
					session_secure_id: this.sessionData.sessionSecureID,
					client_id: clientID,
					network_recording_domains: destinationDomains,
					disable_session_recording:
						this.options.disableSessionRecording,
				})
				if (
					gr.initializeSession.secure_id !==
					this.sessionData.sessionSecureID
				) {
					this.logger.log(
						`Unexpected secure id returned by initializeSession: ${gr.initializeSession.secure_id}, ` +
							`expected ${this.sessionData.sessionSecureID}`,
					)
				}
				this.sessionData.sessionSecureID =
					gr.initializeSession.secure_id
				this.sessionData.projectID = parseInt(
					gr?.initializeSession?.project_id || '0',
				)

				if (
					!this.sessionData.projectID ||
					!this.sessionData.sessionSecureID
				) {
					console.error(
						'Failed to initialize Highlight; an error occurred on our end.',
						this.sessionData,
					)
					return
				}
			}

			// To handle the 'Duplicate Tab' function, remove id from storage until page unload
			setSessionSecureID('')
			setSessionData(this.sessionData)
			this.logger.log(
				`Loaded Highlight
Remote: ${this._backendUrl}
Project ID: ${this.sessionData.projectID}
SessionSecureID: ${this.sessionData.sessionSecureID}`,
			)
			this.options.sessionSecureID = this.sessionData.sessionSecureID
			this._worker.postMessage({
				message: {
					type: MessageType.Initialize,
					sessionSecureID: this.sessionData.sessionSecureID,
					backend: this._backendUrl,
					debug: !!this.debugOptions.clientInteractions,
					recordingStartTime: this._recordingStartTime,
				},
			})
			for (const integration of this._integrations) {
				integration.init(this.sessionData.sessionSecureID)
			}

			if (this.sessionData.userIdentifier) {
				this.identify(
					this.sessionData.userIdentifier,
					this.sessionData.userObject,
				)
			}

			if (this.pushPayloadTimerId) {
				clearTimeout(this.pushPayloadTimerId)
				this.pushPayloadTimerId = undefined
			}
			if (!this._isCrossOriginIframe) {
				this.pushPayloadTimerId = setTimeout(() => {
					this._save()
				}, FIRST_SEND_FREQUENCY)
			}

			// if disabled, do not record session events / rrweb events.
			// we still use firstload listeners to record frontend js console logs and errors.
			if (this.options.disableSessionRecording) {
				this.logger.log(
					`Highlight is NOT RECORDING a session replay per H.init setting.`,
				)
				this.ready = true
				this.state = 'Recording'
				this.manualStopped = false
				return
			}

			const { getDeviceDetails } = getPerformanceMethods()
			if (getDeviceDetails) {
				LDObserve.recordGauge({
					name: MetricName.DeviceMemory,
					value: getDeviceDetails().deviceMemory,
					attributes: {
						category: MetricCategory.Device,
						group: window.location.href,
					},
				})
			}

			const emit = (
				event: eventWithTime,
				isCheckout?: boolean | undefined,
			) => {
				if (isCheckout) {
					this.logger.log('received isCheckout emit', { event })
				}
				this.events.push(event)
			}
			emit.bind(this)

			const alreadyRecording = !!this._recordStop
			// if we were already recording, stop recording to reset rrweb state (eg. reset _sid)
			if (this._recordStop) {
				this._recordStop()
				this._recordStop = undefined
			}

			const [maskAllInputs, maskInputOptions] = determineMaskInputOptions(
				this.privacySetting,
			)

			this._recordStop = record({
				ignoreClass: 'highlight-ignore',
				blockClass: 'highlight-block',
				emit,
				recordCrossOriginIframes: this.options.recordCrossOriginIframe,
				privacySetting: this.privacySetting,
				maskAllInputs,
				maskInputOptions: maskInputOptions,
				recordCanvas: this.enableCanvasRecording,
				sampling: {
					canvas: {
						fps: this.samplingStrategy.canvas,
						fpsManual: this.samplingStrategy.canvasManualSnapshot,
						resizeFactor: this.samplingStrategy.canvasFactor,
						clearWebGLBuffer:
							this.samplingStrategy.canvasClearWebGLBuffer,
						initialSnapshotDelay:
							this.samplingStrategy.canvasInitialSnapshotDelay,
						dataURLOptions: this.samplingStrategy.dataUrlOptions,
						maxSnapshotDimension:
							this.samplingStrategy.canvasMaxSnapshotDimension,
					},
				},
				keepIframeSrcFn: (_src: string) => {
					return !this.options.recordCrossOriginIframe
				},
				inlineImages: this.inlineImages,
				inlineVideos: this.inlineVideos,
				collectFonts: this.inlineImages,
				inlineStylesheet: this.inlineStylesheet,
				plugins: [getRecordSequentialIdPlugin()],
				logger:
					(typeof this.options.debug === 'boolean' &&
						this.options.debug) ||
					(typeof this.options.debug === 'object' &&
						this.options.debug.domRecording)
						? {
								debug: this.logger.log,
								warn: HighlightWarning,
							}
						: undefined,
			})

			// recordStop is not part of listeners because we do not actually want to stop rrweb
			// rrweb has some bugs that make the stop -> restart workflow broken (eg iframe listeners)
			if (!alreadyRecording) {
				if (this.options.recordCrossOriginIframe) {
					this._setupCrossOriginIframeParent()
				}
			}

			if (document.referrer) {
				// Don't record the referrer if it's the same origin.
				// Non-single page apps might have the referrer set to the same origin.
				// If we record this then the referrer data will not be useful.
				// Most users will want to see referrers outside of their website/app.
				// This will be a configuration set in `H.init()` later.
				if (
					!(
						window &&
						document.referrer.includes(window.location.origin)
					)
				) {
					this.addCustomEvent<string>('Referrer', document.referrer)
					this.addProperties(
						{ referrer: document.referrer },
						{ type: 'session' },
					)
				}
			}

			this._setupWindowListeners()
			this.ready = true
			this.state = 'Recording'
			this.manualStopped = false
		} catch (e) {
			if (this._isOnLocalHost) {
				console.error(e)
				HighlightWarning('initializeSession', e)
			}
		}
	}

	async _visibilityHandler(hidden: boolean) {
		if (this.manualStopped) {
			this.logger.log(`Ignoring visibility event due to manual stop.`)
			return
		}
		if (
			new Date().getTime() - this._lastVisibilityChangeTime <
			VISIBILITY_DEBOUNCE_MS
		) {
			return
		}
		this._lastVisibilityChangeTime = new Date().getTime()
		this.logger.log(`Detected window ${hidden ? 'hidden' : 'visible'}.`)
		if (!hidden) {
			if (this.options.disableBackgroundRecording) {
				await this.start()
			}
			this.addCustomEvent('TabHidden', false)
		} else {
			this.addCustomEvent('TabHidden', true)
			if (this.options.disableBackgroundRecording) {
				this.stop()
			}
		}
	}

	async _setupCrossOriginIframe() {
		this.logger.log(`highlight in cross-origin iframe is waiting `)
		// wait until we get a initialization message from the parent window
		await new Promise<void>((r) => {
			const listener = (message: MessageEvent) => {
				if (message.data.highlight === IFRAME_PARENT_READY) {
					const msg = message.data as HighlightIframeMessage
					this.logger.log(`highlight got window message `, msg)
					this.sessionData.projectID = msg.projectID
					this.sessionData.sessionSecureID = msg.sessionSecureID
					// reply back that we got the message and are set up
					window.parent.postMessage(
						{
							highlight: IFRAME_PARENT_RESPONSE,
						} as HighlightIframeReponse,
						'*',
					)
					// stop listening to parent messages
					window.removeEventListener('message', listener)
					r()
				}
			}
			window.addEventListener('message', listener)
		})
	}

	_setupCrossOriginIframeParent() {
		this.logger.log(
			`highlight setting up cross origin iframe parent notification`,
		)
		// notify iframes that highlight is ready
		setInterval(() => {
			window.document.querySelectorAll('iframe').forEach((iframe) => {
				iframe.contentWindow?.postMessage(
					{
						highlight: IFRAME_PARENT_READY,
						projectID: this.sessionData.projectID,
						sessionSecureID: this.sessionData.sessionSecureID,
					} as HighlightIframeMessage,
					'*',
				)
			})
		}, FIRST_SEND_FREQUENCY)
		window.addEventListener('message', (message: MessageEvent) => {
			if (message.data.highlight === IFRAME_PARENT_RESPONSE) {
				this.logger.log(
					`highlight got response from initialized iframe`,
				)
			}
		})
	}

	_setupWindowListeners() {
		try {
			const highlightThis = this
			if (this.enableSegmentIntegration) {
				this.listeners.push(
					SegmentIntegrationListener((obj: any) => {
						if (obj.type === 'track') {
							const properties: { [key: string]: string } = {}
							properties['segment-event'] = obj.event
							highlightThis.addProperties(properties, {
								type: 'track',
								source: 'segment',
							})
						} else if (obj.type === 'identify') {
							// Removes the starting and end quotes
							// Example: "boba" -> boba
							const trimmedUserId = obj.userId.replace(
								/^"(.*)"$/,
								'$1',
							)

							highlightThis.identify(
								trimmedUserId,
								obj.traits,
								'segment',
							)
						}
					}),
				)
			}
			this.listeners.push(
				PathListener((url: string) => {
					if (this.reloaded) {
						this.addCustomEvent<string>('Reload', url)
						this.reloaded = false
						highlightThis.addProperties(
							{ reload: true },
							{ type: 'session' },
						)
					} else {
						this.addCustomEvent<string>('Navigate', url)
					}
				}),
			)

			this.listeners.push(
				ViewportResizeListener(
					(viewport: ViewportResizeListenerArgs) => {
						this.addCustomEvent('Viewport', viewport)
						this.submitViewportMetrics(viewport)
					},
				),
			)
			this.listeners.push(
				ClickListener((clickTarget, event) => {
					let selector = null
					let textContent = null
					if (event && event.target) {
						const t = event.target as HTMLElement
						selector = getSimpleSelector(t)
						textContent = t.textContent
						// avoid sending huge strings here
						if (textContent && textContent.length > 2000) {
							textContent = textContent.substring(0, 2000)
						}
					}
					this.addCustomEvent('Click', {
						clickTarget,
						clickTextContent: textContent,
						clickSelector: selector,
					})
				}),
			)
			this.listeners.push(
				FocusListener((focusTarget) => {
					if (focusTarget) {
						this.addCustomEvent('Focus', focusTarget)
					}
				}),
			)

			this.listeners.push(
				WebVitalsListener((data) => {
					const { name, value } = data
					LDObserve.recordGauge({
						name,
						value,
						attributes: {
							group: window.location.href,
							category: MetricCategory.WebVital,
						},
					})
				}),
			)

			this.listeners.push(
				NetworkPerformanceListener(
					(payload: NetworkPerformancePayload) => {
						const attributes: Attributes = {
							category: MetricCategory.Performance,
							group: window.location.href,
						}
						if (payload.saveData !== undefined) {
							attributes['saveData'] = payload.saveData.toString()
						}
						if (payload.effectiveType !== undefined) {
							attributes['effectiveType'] =
								payload.effectiveType.toString()
						}
						if (payload.type !== undefined) {
							attributes['type'] = payload.type.toString()
						}
						Object.entries(payload).forEach(
							([name, value]) =>
								value &&
								typeof value === 'number' &&
								LDObserve.recordGauge({
									name,
									value: value as number,
									attributes,
								}),
						)
					},
					this._recordingStartTime,
				),
			)

			if (this.sessionShortcut) {
				SessionShortcutListener(this.sessionShortcut, () => {
					window.open(
						this.getCurrentSessionURLWithTimestamp(),
						'_blank',
					)
				})
			}

			// only do this once, since we want to keep the visibility listener attached even when recoding is stopped
			if (!this._hasPreviouslyInitialized) {
				// setup electron main thread window visiblity events listener
				if (window.electron?.ipcRenderer) {
					window.electron.ipcRenderer.on(
						'highlight.run',
						({ visible }: { visible: boolean }) => {
							this._visibilityHandler(!visible)
						},
					)
					this.logger.log('Set up Electron highlight.run events.')
				} else {
					// Send the payload every time the page is no longer visible - this includes when the tab is closed, as well
					// as when switching tabs or apps on mobile. Non-blocking.
					PageVisibilityListener((isTabHidden) =>
						this._visibilityHandler(isTabHidden),
					)
					this.logger.log('Set up document visibility listener.')
				}
				this._hasPreviouslyInitialized = true
			}

			// Clear the timer so it doesn't block the next page navigation.
			const unloadListener = () => {
				this.hasSessionUnloaded = true
				if (this.pushPayloadTimerId) {
					clearTimeout(this.pushPayloadTimerId)
					this.pushPayloadTimerId = undefined
				}
			}
			window.addEventListener('beforeunload', unloadListener)
			this.listeners.push(() =>
				window.removeEventListener('beforeunload', unloadListener),
			)
		} catch (e) {
			if (this._isOnLocalHost) {
				console.error(e)
				HighlightWarning('initializeSession _setupWindowListeners', e)
			}
		}

		const unloadListener = () => {
			this.addCustomEvent('Page Unload', '')
			setSessionSecureID(this.sessionData.sessionSecureID)
			setSessionData(this.sessionData)
		}
		window.addEventListener('beforeunload', unloadListener)
		this.listeners.push(() =>
			window.removeEventListener('beforeunload', unloadListener),
		)

		// beforeunload is not supported on iOS on Safari. Apple docs recommend using `pagehide` instead.
		const isOnIOS =
			navigator.userAgent.match(/iPad/i) ||
			navigator.userAgent.match(/iPhone/i)
		if (isOnIOS) {
			const unloadListener = () => {
				this.addCustomEvent('Page Unload', '')
				setSessionSecureID(this.sessionData.sessionSecureID)
				setSessionData(this.sessionData)
			}
			window.addEventListener('pagehide', unloadListener)
			this.listeners.push(() =>
				window.removeEventListener('beforeunload', unloadListener),
			)
		}
	}

	submitViewportMetrics({
		height,
		width,
		availHeight,
		availWidth,
	}: ViewportResizeListenerArgs) {
		LDObserve.recordGauge({
			name: MetricName.ViewportHeight,
			value: height,
			attributes: {
				category: MetricCategory.Device,
				group: window.location.href,
			},
		})
		LDObserve.recordGauge({
			name: MetricName.ViewportWidth,
			value: width,
			attributes: {
				category: MetricCategory.Device,
				group: window.location.href,
			},
		})
		LDObserve.recordGauge({
			name: MetricName.ScreenHeight,
			value: availHeight,
			attributes: {
				category: MetricCategory.Device,
				group: window.location.href,
			},
		})
		LDObserve.recordGauge({
			name: MetricName.ScreenWidth,
			value: availWidth,
			attributes: {
				category: MetricCategory.Device,
				group: window.location.href,
			},
		})
		LDObserve.recordGauge({
			name: MetricName.ViewportArea,
			value: height * width,
			attributes: {
				category: MetricCategory.Device,
				group: window.location.href,
			},
		})
	}

	/**
	 * Stops Highlight from recording.
	 * @param manual The end user requested to stop recording.
	 */
	stop(manual?: boolean) {
		this.manualStopped = !!manual
		if (this.manualStopped) {
			this.addCustomEvent(
				'Stop',
				'H.stop() was called which stops Highlight from recording.',
			)
		}
		this.state = 'NotRecording'
		// stop rrweb recording mutation observers
		if (manual && this._recordStop) {
			this._recordStop()
			this._recordStop = undefined
		}
		// stop all other event listeners, to be restarted on initialize()
		this.listeners.forEach((stop) => stop())
		this.listeners = []
	}

	/**
	 * Returns the current timestamp for the current session.
	 */
	getCurrentSessionURLWithTimestamp() {
		const now = new Date().getTime()
		const { projectID, sessionSecureID } = this.sessionData
		const relativeTimestamp = (now - this._recordingStartTime) / 1000
		return `https://${HIGHLIGHT_URL}/${projectID}/sessions/${sessionSecureID}?ts=${relativeTimestamp}`
	}

	getCurrentSessionURL() {
		const projectID = this.sessionData.projectID
		const sessionSecureID = this.sessionData.sessionSecureID
		if (projectID && sessionSecureID) {
			return `https://${HIGHLIGHT_URL}/${projectID}/sessions/${sessionSecureID}`
		}
		return null
	}

	async snapshot(element: HTMLCanvasElement) {
		await record.snapshotCanvas(element)
	}

	addSessionFeedback({
		timestamp,
		verbatim,
		user_email,
		user_name,
	}: {
		verbatim: string
		timestamp: string
		user_name?: string
		user_email?: string
	}) {
		this._worker.postMessage({
			message: {
				type: MessageType.Feedback,
				verbatim,
				timestamp,
				userName: user_name || this.sessionData.userIdentifier,
				userEmail:
					user_email || (this.sessionData.userObject as any)?.name,
			},
		})
	}

	// Reset the events array and push to a backend.
	async _save() {
		try {
			if (
				this.state === 'Recording' &&
				this.listeners &&
				this.sessionData.sessionStartTime &&
				Date.now() - this.sessionData.sessionStartTime >
					MAX_SESSION_LENGTH
			) {
				this.logger.log(`Resetting session`, {
					start: this.sessionData.sessionStartTime,
				})
				await this._reset({})
			}
			let sendFn = undefined
			if (this.options?.sendMode === 'local') {
				sendFn = async (payload: any) => {
					let blob = new Blob(
						[
							JSON.stringify({
								query: print(PushPayloadDocument),
								variables: payload,
							}),
						],
						{
							type: 'application/json',
						},
					)
					await window.fetch(`${this._backendUrl}`, {
						method: 'POST',
						body: blob,
					})
					return 0
				}
			}
			await this._sendPayload({ sendFn })
			this.hasPushedData = true
			this.sessionData.lastPushTime = Date.now()
			setSessionData(this.sessionData)
		} catch (e) {
			if (this._isOnLocalHost) {
				console.error(e)
				HighlightWarning('_save', e)
			}
		}
		if (this.state === 'Recording') {
			if (this.pushPayloadTimerId) {
				clearTimeout(this.pushPayloadTimerId)
				this.pushPayloadTimerId = undefined
			}
			this.pushPayloadTimerId = setTimeout(() => {
				this._save()
			}, SEND_FREQUENCY)
		}
	}

	/**
	 * This proxy should be used instead of rrweb's native addCustomEvent.
	 * The proxy makes sure recording has started before emitting a custom event.
	 */
	addCustomEvent<T>(tag: string, payload: T): void {
		if (this.state === 'NotRecording') {
			let intervalId: ReturnType<typeof setInterval>
			const worker = () => {
				clearInterval(intervalId)
				if (this.state === 'Recording' && this.events.length > 0) {
					rrwebAddCustomEvent(tag, payload)
				} else {
					intervalId = setTimeout(worker, 500)
				}
			}
			intervalId = setTimeout(worker, 500)
		} else if (
			this.state === 'Recording' &&
			(this.events.length > 0 || this.hasPushedData)
		) {
			rrwebAddCustomEvent(tag, payload)
		}
	}

	async _sendPayload({
		sendFn,
	}: {
		sendFn?: (payload: PushPayloadMutationVariables) => Promise<number>
	}) {
		const events = [...this.events]

		// if it is time to take a full snapshot,
		// ensure the snapshot is at the beginning of the next payload
		// After snapshot thresholds have been met,
		// take a full snapshot and reset the counters
		const { bytes, time } = this.enableCanvasRecording
			? SNAPSHOT_SETTINGS.canvas
			: SNAPSHOT_SETTINGS.normal
		if (
			this._eventBytesSinceSnapshot >= bytes &&
			new Date().getTime() - this._lastSnapshotTime >= time
		) {
			this.takeFullSnapshot()
		}

		this.logger.log(
			`Sending: ${events.length} events, \nTo: ${this._backendUrl}\nOrg: ${this.organizationID}\nSessionSecureID: ${this.sessionData.sessionSecureID}`,
		)
		const highlightLogs = getHighlightLogs()
		if (sendFn) {
			await sendFn({
				session_secure_id: this.sessionData.sessionSecureID,
				payload_id: (this.sessionData.payloadID++).toString(),
				events: { events } as ReplayEventsInput,
				messages: stringify({ messages: [] }),
				resources: JSON.stringify({ resources: [] }),
				web_socket_events: JSON.stringify({
					webSocketEvents: [],
				}),
				errors: [],
				is_beacon: false,
				has_session_unloaded: this.hasSessionUnloaded,
				highlight_logs: highlightLogs || undefined,
			})
		} else {
			this._worker.postMessage({
				message: {
					type: MessageType.AsyncEvents,
					id: this.sessionData.payloadID++,
					events,
					messages: [],
					errors: [],
					resourcesString: JSON.stringify({ resources: [] }),
					webSocketEventsString: JSON.stringify({
						webSocketEvents: [],
					}),
					hasSessionUnloaded: this.hasSessionUnloaded,
					highlightLogs: highlightLogs,
				},
			})
		}
		setSessionData(this.sessionData)

		// We are creating a weak copy of the events. rrweb could have pushed more events to this.events while we send the request with the events as a payload.
		// Originally, we would clear this.events but this could lead to a race condition.
		// Example Scenario:
		// 1. Create the events payload from this.events (with N events)
		// 2. rrweb pushes to this.events (with M events)
		// 3. Network request made to push payload (Only includes N events)
		// 4. this.events is cleared (we lose M events)
		this.events = this.events.slice(events.length)

		clearHighlightLogs(highlightLogs)
	}

	private takeFullSnapshot() {
		if (!this._recordStop) {
			this.logger.log(`skipping full snapshot as rrweb is not running`)
			return
		}
		this.logger.log(`taking full snapshot`, {
			bytesSinceSnapshot: this._eventBytesSinceSnapshot,
			lastSnapshotTime: this._lastSnapshotTime,
		})
		record.takeFullSnapshot()
		this._eventBytesSinceSnapshot = 0
		this._lastSnapshotTime = new Date().getTime()
	}

	register(client: LDClient, metadata: LDPluginEnvironmentMetadata) {
		this._integrations.push(new LaunchDarklyIntegration(client, metadata))
	}

	getHooks(metadata: LDPluginEnvironmentMetadata): Hook[] {
		return this._integrations.flatMap((i) => i.getHooks?.(metadata) ?? [])
	}

	getRecordingState() {
		return this?.state ?? 'NotRecording'
	}

	getSession() {
		const secureID = this.sessionData.sessionSecureID
		const sessionData = getPreviousSessionData(secureID)
		if (!sessionData) {
			return null
		}

		const baseUrl = `${this.getFrontendUrl()}/sessions/${secureID}`
		if (!baseUrl) {
			return null
		}

		const currentSessionTimestamp = sessionData?.sessionStartTime
		if (!currentSessionTimestamp) {
			return null
		}

		const now = new Date().getTime()
		const url = new URL(baseUrl)
		const urlWithTimestamp = new URL(baseUrl)
		const relativeTimestamp = (now - this._recordingStartTime) / 1000
		urlWithTimestamp.searchParams.set('ts', relativeTimestamp.toString())

		return {
			url: url.toString(),
			urlWithTimestamp: urlWithTimestamp.toString(),
			sessionSecureID: secureID,
		} as SessionDetails
	}

	private getFrontendUrl() {
		const regexMatch = this._backendUrl.match(LAUNCHDARKLY_BACKEND_REGEX)
		if (regexMatch && regexMatch?.groups?.domain) {
			const domain = (regexMatch.groups.domain ??
				'') as keyof typeof LAUNCHDARKLY_ENV_APPS
			const appUrl = LAUNCHDARKLY_ENV_APPS[domain]
			return `https://${appUrl}${LAUNCHDARKLY_PATH_PREFIX}`
		}
		return LAUNCHDARKLY_URL
	}
}
